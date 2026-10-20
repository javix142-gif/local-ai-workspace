#!/usr/bin/env python3
"""Collect bounded, privacy-conscious evidence for connected Android tests."""

from __future__ import annotations

import hashlib
import base64
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path


REPO = Path(__file__).resolve().parents[2]
OUT = Path(os.environ.get("ANDROID_CI_EVIDENCE_DIR", "/tmp/android-emulator-evidence"))
RUNNER_TEMP = Path(os.environ.get("RUNNER_TEMP", "/tmp"))
ANDROID_HOME = Path(os.environ.get("ANDROID_HOME", "/nonexistent/android-sdk"))
ADB = str(ANDROID_HOME / "platform-tools" / "adb") if (ANDROID_HOME / "platform-tools" / "adb").is_file() else (shutil.which("adb") or "adb")


def command(args: list[str], timeout: int = 20) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        return subprocess.CompletedProcess(args, 127, "", f"{type(exc).__name__}: {exc}")


def write_text(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8", errors="replace")


def redact_log(value: str) -> str:
    patterns = [
        (r"(?i)(bearer\s+)[A-Za-z0-9._~+/-]+=*", r"\1[REDACTED]"),
        (r"(?i)(token|password|api[_-]?key|authorization)(\s*[=:]\s*)[^\s,;]+", r"\1\2[REDACTED]"),
        (r"/data/user/\d+/[^\s]+", "[APP_PRIVATE_PATH]"),
        (r"/data/data/[^\s]+", "[APP_PRIVATE_PATH]"),
    ]
    for pattern, replacement in patterns:
        value = re.sub(pattern, replacement, value)
    return value


def safe_copy(source: Path, destination: Path) -> None:
    if source.is_file():
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)


def validate_png(payload: bytes) -> tuple[int, int] | None:
    """Validate the PNG chunk structure, CRCs, and compressed image stream."""
    signature = b"\x89PNG\r\n\x1a\n"
    if not payload.startswith(signature):
        return None
    offset = len(signature)
    width = height = 0
    saw_ihdr = saw_idat = saw_iend = False
    compressed = bytearray()
    while offset + 12 <= len(payload):
        length = struct.unpack_from(">I", payload, offset)[0]
        chunk_type = payload[offset + 4:offset + 8]
        end = offset + 12 + length
        if end > len(payload):
            return None
        chunk_data = payload[offset + 8:offset + 8 + length]
        expected_crc = struct.unpack_from(">I", payload, offset + 8 + length)[0]
        actual_crc = zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            return None
        if not saw_ihdr:
            if chunk_type != b"IHDR" or length != 13:
                return None
            width, height = struct.unpack_from(">II", chunk_data)
            saw_ihdr = width > 0 and height > 0
            if not saw_ihdr:
                return None
        elif chunk_type == b"IHDR":
            return None
        if chunk_type == b"IDAT":
            if saw_iend:
                return None
            saw_idat = True
            compressed.extend(chunk_data)
        elif chunk_type == b"IEND":
            if length != 0 or not saw_idat:
                return None
            saw_iend = True
            offset = end
            break
        offset = end
    if not saw_ihdr or not saw_idat or not saw_iend or offset != len(payload):
        return None
    try:
        if not zlib.decompress(compressed):
            return None
    except zlib.error:
        return None
    return width, height


def png_dimensions(path: Path) -> tuple[int, int] | None:
    try:
        return validate_png(path.read_bytes())
    except OSError:
        return None


def adb_facts(serial: str | None) -> dict[str, object]:
    facts: dict[str, object] = {"connected": serial is not None}
    if serial is None:
        return facts
    properties = {
        "api": "ro.build.version.sdk",
        "release": "ro.build.version.release",
        "abi": "ro.product.cpu.abi",
        "model": "ro.product.model",
        "product": "ro.product.name",
        "emulator": "ro.kernel.qemu",
    }
    for key, prop in properties.items():
        result = command([ADB, "-s", serial, "shell", "getprop", prop])
        facts[key] = result.stdout.strip() if result.returncode == 0 else None
    size = command([ADB, "-s", serial, "shell", "wm", "size"])
    density = command([ADB, "-s", serial, "shell", "wm", "density"])
    facts["screenSize"] = size.stdout.strip() if size.returncode == 0 else None
    facts["screenDensity"] = density.stdout.strip() if density.returncode == 0 else None
    return facts


def installed_package_facts(serial: str | None) -> dict[str, object]:
    expected = {
        "application": "com.localai.workspace.debug",
        "instrumentationTest": "com.localai.workspace.debug.test",
    }
    if serial is None:
        return {
            "checked": False,
            "reason": "NO_CONNECTED_EMULATOR",
            "packages": {key: {"packageName": value, "installed": None} for key, value in expected.items()},
        }
    result = command([ADB, "-s", serial, "shell", "pm", "list", "packages"])
    installed = {
        line.strip().removeprefix("package:")
        for line in result.stdout.splitlines()
        if line.strip().startswith("package:")
    }
    return {
        "checked": True,
        "listPackagesExitCode": result.returncode,
        "packages": {
            key: {"packageName": value, "installed": result.returncode == 0 and value in installed}
            for key, value in expected.items()
        },
    }


def application_install_verification(
    installed_after_tests: bool,
    *,
    gradle_exit_code: int | None,
    xml_count: int,
    executed_tests: int,
    adb_connected: bool,
) -> dict[str, object]:
    """Treat completed connected instrumentation as proof the target app was installed.

    Android Gradle Plugin can uninstall both APKs when the connected-test task exits.
    The post-task package list remains useful diagnostic evidence, but it cannot be
    the sole installation check after the test runner has already executed.
    """
    connected_execution = (
        adb_connected
        and gradle_exit_code == 0
        and xml_count > 0
        and executed_tests > 0
    )
    return {
        "installedAfterTests": installed_after_tests,
        "verifiedByConnectedInstrumentation": connected_execution,
        "verifiedForRun": installed_after_tests or connected_execution,
    }


def parse_results(xml_files: list[Path]) -> tuple[dict[str, int], list[dict[str, str]]]:
    counts = {"total": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0, "executed": 0}
    skipped_cases: list[dict[str, str]] = []
    for xml_file in xml_files:
        try:
            root = ET.parse(xml_file).getroot()
        except (ET.ParseError, OSError):
            counts["errors"] += 1
            continue
        cases = list(root.iter("testcase"))
        if cases:
            for case in cases:
                counts["total"] += 1
                skipped = case.find("skipped")
                failure = case.find("failure")
                error = case.find("error")
                if skipped is not None:
                    counts["skipped"] += 1
                    reason = skipped.get("message") or (skipped.text or "").strip()
                    skipped_cases.append({
                        "class": case.get("classname", ""),
                        "name": case.get("name", ""),
                        "reason": redact_log(reason)[:300],
                    })
                elif failure is not None:
                    counts["failed"] += 1
                elif error is not None:
                    counts["errors"] += 1
                else:
                    counts["passed"] += 1
        else:
            suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
            for suite in suites:
                try:
                    total = int(suite.get("tests", "0"))
                    failed = int(suite.get("failures", "0"))
                    errors = int(suite.get("errors", "0"))
                    skipped = int(suite.get("skipped", "0"))
                except ValueError:
                    counts["errors"] += 1
                    continue
                counts["total"] += total
                counts["failed"] += failed
                counts["errors"] += errors
                counts["skipped"] += skipped
                counts["passed"] += max(0, total - failed - errors - skipped)
    counts["executed"] = counts["total"] - counts["skipped"]
    return counts, skipped_cases


def read_json_if_available(path: Path) -> dict[str, object] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) else None


def read_int_if_available(path: Path) -> int | None:
    try:
        return int(path.read_text(encoding="utf-8").strip())
    except (OSError, ValueError):
        return None


def collect_xml() -> list[Path]:
    bases = [
        REPO / "app/build/outputs/androidTest-results/connected",
        REPO / "app/build/outputs/androidTest-results/connected/debug",
    ]
    sources: set[Path] = set()
    for base in bases:
        if base.exists():
            sources.update(p for p in base.rglob("*.xml") if p.is_file())
    destination = OUT / "instrumented-xml"
    copied: list[Path] = []
    for source in sorted(sources):
        relative = source.relative_to(REPO / "app/build/outputs/androidTest-results/connected")
        target = destination / relative
        safe_copy(source, target)
        copied.append(target)
    return copied


def collect_header_screenshots(serial: str | None) -> list[Path]:
    names = ("standard", "large-font")
    destination = OUT / "ui-diagnostics" / "screenshots"
    destination.mkdir(parents=True, exist_ok=True)
    records: list[dict[str, object]] = []
    copied: list[Path] = []
    if serial is None:
        for name in names:
            records.append({
                "name": name,
                "transport": "logcat_base64_chunks_v1",
                "logcatExitCode": None,
                "localPath": (destination / f"chat-header-{name}.png").relative_to(OUT).as_posix(),
                "localFilePresent": False,
                "pngValid": False,
                "widthPixels": None,
                "heightPixels": None,
                "bytes": 0,
                "sha256": None,
                "reason": "NO_CONNECTED_EMULATOR",
            })
        write_text(OUT / "ui-diagnostics/screenshot-pull.json", json.dumps({"screenshots": records}, indent=2) + "\n")
        write_text(OUT / "ui-diagnostics/screenshot-transport.txt", "NO_CONNECTED_EMULATOR\n")
        return copied

    result = command([ADB, "-s", serial, "logcat", "-d", "-v", "raw", "-s", "M00HeaderPng:I"], timeout=60)
    groups: dict[str, list[dict[str, object]]] = {name: [] for name in names}
    active: dict[str, dict[str, object]] = {}
    protocol_errors: list[str] = []
    for line in result.stdout.splitlines():
        fields = line.split("|")
        if len(fields) < 2 or fields[0] != "M00PNG":
            continue
        kind = fields[1]
        if kind == "BEGIN":
            if len(fields) != 8:
                protocol_errors.append("MALFORMED_BEGIN")
                continue
            _, _, name, width, height, byte_count, sha256, chunk_count = fields
            if name not in groups:
                protocol_errors.append("UNEXPECTED_CAPTURE_NAME")
                continue
            try:
                group: dict[str, object] = {
                    "width": int(width), "height": int(height), "byteCount": int(byte_count),
                    "sha256": sha256, "chunkCount": int(chunk_count), "chunks": {}, "ended": False,
                    "endSha256": None, "errors": [],
                }
            except ValueError:
                protocol_errors.append("INVALID_BEGIN_METADATA")
                continue
            groups[name].append(group)
            active[name] = group
        elif kind == "CHUNK":
            if len(fields) != 5:
                protocol_errors.append("MALFORMED_CHUNK")
                continue
            _, _, name, index = fields[:4]
            if name not in active:
                protocol_errors.append("CHUNK_WITHOUT_BEGIN")
                continue
            try:
                index_value = int(index)
            except ValueError:
                active[name]["errors"].append("INVALID_CHUNK_INDEX")
                continue
            chunks = active[name]["chunks"]
            assert isinstance(chunks, dict)
            if index_value in chunks:
                active[name]["errors"].append("DUPLICATE_CHUNK")
            chunks[index_value] = fields[4]
        elif kind == "END":
            if len(fields) != 4:
                protocol_errors.append("MALFORMED_END")
                continue
            _, _, name, sha256 = fields
            if name not in active:
                protocol_errors.append("END_WITHOUT_BEGIN")
                continue
            active[name]["ended"] = True
            active[name]["endSha256"] = sha256
            active.pop(name, None)

    for name in names:
        local = destination / f"chat-header-{name}.png"
        candidates = groups[name]
        record: dict[str, object] = {
            "name": name,
            "transport": "logcat_base64_chunks_v1",
            "collectorCommand": "adb -s <serial> logcat -d -v raw -s M00HeaderPng:I",
            "logcatExitCode": result.returncode,
            "logcatBytes": len(result.stdout.encode("utf-8")),
            "localPath": local.relative_to(OUT).as_posix(),
            "localFilePresent": False,
            "pngValid": False,
            "widthPixels": None,
            "heightPixels": None,
            "bytes": 0,
            "sha256": None,
            "captureRecordsFound": len(candidates),
        }
        errors = list(protocol_errors)
        if result.returncode != 0:
            errors.append("LOGCAT_READ_FAILED")
        if len(candidates) != 1:
            errors.append("CAPTURE_MISSING" if not candidates else "DUPLICATE_CAPTURE")
        if len(candidates) == 1:
            candidate = candidates[0]
            errors.extend(candidate["errors"])
            chunks = candidate["chunks"]
            assert isinstance(chunks, dict)
            expected_count = candidate["chunkCount"]
            expected_indices = set(range(expected_count)) if isinstance(expected_count, int) and expected_count > 0 else set()
            if candidate["ended"] is not True:
                errors.append("CAPTURE_END_MISSING")
            if not expected_indices or set(chunks) != expected_indices:
                errors.append("CHUNK_SET_INCOMPLETE")
            encoded = "".join(chunks[index] for index in sorted(chunks))
            try:
                payload = base64.b64decode(encoded, validate=True)
            except (ValueError, base64.binascii.Error):
                payload = b""
                errors.append("BASE64_INVALID")
            dimensions = validate_png(payload) if payload else None
            if dimensions is None:
                errors.append("PNG_INVALID")
            if len(payload) != candidate["byteCount"]:
                errors.append("BYTE_COUNT_MISMATCH")
            digest = hashlib.sha256(payload).hexdigest() if payload else None
            if digest != candidate["sha256"] or digest != candidate["endSha256"]:
                errors.append("SHA256_MISMATCH")
            if dimensions != (candidate["width"], candidate["height"]):
                errors.append("DIMENSION_MISMATCH")
            if not errors:
                local.write_bytes(payload)
                record.update({
                    "localFilePresent": True,
                    "pngValid": True,
                    "widthPixels": dimensions[0],
                    "heightPixels": dimensions[1],
                    "bytes": len(payload),
                    "sha256": digest,
                    "expectedChunkCount": expected_count,
                    "receivedChunkCount": len(chunks),
                    "endMarkerReceived": True,
                })
                copied.append(local)
        record["errors"] = sorted(set(errors))
        records.append(record)
    write_text(OUT / "ui-diagnostics/screenshot-pull.json", json.dumps({"screenshots": records}, indent=2) + "\n")
    write_text(OUT / "ui-diagnostics/screenshot-transport.txt", "\n".join(
        f"name={item['name']} transport={item['transport']} logcatExitCode={item['logcatExitCode']} "
        f"captureRecords={item['captureRecordsFound']} localFilePresent={item['localFilePresent']} "
        f"pngValid={item['pngValid']} dimensions={item['widthPixels']}x{item['heightPixels']} "
        f"bytes={item['bytes']} sha256={item['sha256']} errors={','.join(item['errors'])}"
        for item in records
    ) + "\n")
    return copied


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "workflow-started.json").unlink(missing_ok=True)
    sha = command(["git", "-C", str(REPO), "rev-parse", "HEAD"]).stdout.strip()
    devices = command([ADB, "devices", "-l"])
    write_text(OUT / "adb-devices.txt", redact_log(devices.stdout + devices.stderr))
    serial = None
    for line in devices.stdout.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[0].startswith("emulator-") and fields[1] == "device":
            serial = fields[0]
            break

    for filename in (
        "sdkmanager.log", "sdk-licenses.log", "avdmanager.log", "emulator.log",
        "emulator-boot.log", "gradle-connected.log", "adb-before-tests.txt", "android-toolchain.txt", "kvm-check.txt",
        "logcat-buffer-setup.txt",
        "avd-environment.txt", "avd-resolved-paths.txt", "avdmanager-create-exit-code.txt",
        "avdmanager-create-pipeline-exit-code.txt", "avdmanager-create-pipeline-status.txt",
        "avdmanager-list-avd.txt", "avdmanager-list-avd.txt.exit-code",
        "emulator-list-avds.txt", "emulator-list-avds.txt.exit-code", "avd-storage-inventory.txt",
        "avd-preflight-exit-code.txt",
    ):
        safe_copy(RUNNER_TEMP / filename, OUT / "logs" / filename)
    at12_dir = RUNNER_TEMP / "h1-at12"
    for path in sorted(at12_dir.glob("*.txt")):
        safe_copy(path, OUT / "h1-at12" / path.name)
    safe_copy(at12_dir / "h1-at12-summary.json", OUT / "h1-at12" / "summary.json")
    safe_copy(RUNNER_TEMP / "avd-prelaunch.json", OUT / "avd-prelaunch.json")
    safe_copy(RUNNER_TEMP / "avd-prelaunch-json-exit-code.txt", OUT / "avd-prelaunch-json-exit-code.txt")

    logcat_report: dict[str, object]
    if serial:
        logcat = command([ADB, "-s", serial, "logcat", "-d", "-v", "time",
                          "-s", "AndroidRuntime:E", "TestRunner:I", "Instrumentation:I", "ChatHeaderLayout:V"], timeout=60)
        sanitized_logcat = redact_log(logcat.stdout + logcat.stderr)
        write_text(OUT / "logs/logcat-filtered-sanitized.txt", sanitized_logcat)
        logcat_bytes = sanitized_logcat.encode("utf-8")
        logcat_report = {
            "exitCode": logcat.returncode,
            "tags": ["AndroidRuntime:E", "TestRunner:I", "Instrumentation:I", "ChatHeaderLayout:V"],
            "chatHeaderPrintToLogPriority": "DEBUG; ChatHeaderLayout:V includes DEBUG and INFO",
            "bytes": len(logcat_bytes),
            "sha256": hashlib.sha256(logcat_bytes).hexdigest(),
        }
        write_text(OUT / "adb-after-tests.txt", redact_log(command([ADB, "devices", "-l"]).stdout))
    else:
        write_text(OUT / "logs/logcat-filtered-sanitized.txt", "NOT_AVAILABLE: no booted emulator detected\n")
        write_text(OUT / "adb-after-tests.txt", "NOT_AVAILABLE: no booted emulator detected\n")
        logcat_report = {"exitCode": None, "tags": [], "reason": "NO_CONNECTED_EMULATOR"}

    header_screenshots = collect_header_screenshots(serial)
    screenshot_report = read_json_if_available(OUT / "ui-diagnostics/screenshot-pull.json")

    xml_files = collect_xml()
    counts, skipped_cases = parse_results(xml_files)
    gradle_exit_text = os.environ.get("GRADLE_EXIT_CODE", "").strip()
    try:
        gradle_exit: int | None = int(gradle_exit_text)
    except ValueError:
        gradle_exit = None
    facts = adb_facts(serial)
    avd_prelaunch = read_json_if_available(OUT / "avd-prelaunch.json")
    h1_at12 = read_json_if_available(OUT / "h1-at12" / "summary.json")
    packages = installed_package_facts(serial)
    application_installed_after_tests = (
        packages.get("packages", {}).get("application", {}).get("installed") is True
    )
    install_verification = application_install_verification(
        application_installed_after_tests,
        gradle_exit_code=gradle_exit,
        xml_count=len(xml_files),
        executed_tests=counts["executed"],
        adb_connected=serial is not None,
    )
    packages["applicationInstallVerification"] = install_verification
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com").rstrip("/")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    preflight_passed = bool(avd_prelaunch and avd_prelaunch.get("preflightPassed") is True)
    api_abi_valid = facts.get("api") == "35" and facts.get("abi") == "x86_64" and facts.get("emulator") == "1"
    application_installed = install_verification["verifiedForRun"] is True
    counts_consistent = counts["total"] == counts["passed"] + counts["failed"] + counts["errors"] + counts["skipped"]
    checks = {
        "avdPreflightPassed": preflight_passed,
        "adbConnected": serial is not None,
        "api35X86_64Emulator": api_abi_valid,
        "applicationApkInstalled": application_installed,
        "connectedTaskSucceeded": gradle_exit == 0,
        "junitXmlPresent": bool(xml_files),
        "junitCountsConsistent": counts_consistent,
        "testsExecuted": counts["executed"] > 0,
        "noTestFailuresOrErrors": counts["failed"] == 0 and counts["errors"] == 0,
        "headerUiDiagnosticsCaptured": len(header_screenshots) == 2,
        "h1At12RealUpdateVerified": bool(h1_at12 and h1_at12.get("status") == "PASS"
            and h1_at12.get("installUpdateCommand") == "adb install -r <current-debug-apk>"),
    }
    summary: dict[str, object] = {
        "label": "EMULATOR",
        "sourceCommit": sha,
        "runId": run_id,
        "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
        "runUrl": f"{server}/{repository}/actions/runs/{run_id}" if run_id and repository else None,
        "expected": {"api": 35, "abi": "x86_64", "systemImage": "google_apis"},
        "device": facts,
        "adbDevicesExitCode": devices.returncode,
        "avdPrelaunchDiagnostics": avd_prelaunch,
        "avdPreflightExitCode": read_int_if_available(RUNNER_TEMP / "avd-preflight-exit-code.txt"),
        "apkInstallState": packages,
        "physicalDeviceValidation": "NOT_RUN",
        "connectedTask": ":app:connectedDebugAndroidTest",
        "connectedTaskCommand": "./gradlew :app:connectedDebugAndroidTest --no-daemon --no-parallel --max-workers=2 --stacktrace --console=plain",
        "gradleExitCode": gradle_exit,
        "instrumentationXmlCount": len(xml_files),
        "counts": counts,
        "skippedCases": skipped_cases,
        "headerUiDiagnostics": {
            "screenshots": [path.relative_to(OUT).as_posix() for path in header_screenshots],
            "requiredScreenshots": 2,
            "captureReport": screenshot_report,
            "logcat": logcat_report,
        },
        "h1At12Update": h1_at12 or {"status": "NOT_RUN", "reason": "No H1-AT-12 summary was produced."},
        "acceptanceChecks": checks,
        "status": "PASS" if all(checks.values()) else "FAIL",
        "failureReasons": [key for key, passed in checks.items() if not passed],
        "apkOrModelArtifactsIncluded": False,
    }
    write_text(OUT / "summary.json", json.dumps(summary, indent=2, ensure_ascii=False) + "\n")
    write_text(OUT / "metadata.json", json.dumps({
        "sourceCommit": sha,
        "runId": run_id,
        "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
        "runUrl": summary["runUrl"],
        "runnerImage": os.environ.get("ImageOS", "unknown"),
        "runnerVersion": os.environ.get("ImageVersion", "unknown"),
        "expectedApi": 35,
        "expectedAbi": "x86_64",
        "connectedTask": summary["connectedTask"],
        "adbDevicesExitCode": devices.returncode,
        "apkInstallState": packages,
        "avdPreflightPassed": preflight_passed,
        "runnerClass": "GitHub-hosted standard ubuntu-24.04",
        "modelDownloads": "none",
        "label": "EMULATOR",
    }, indent=2, ensure_ascii=False) + "\n")

    forbidden = {".apk", ".aab", ".litertlm", ".gguf", ".safetensors", ".onnx", ".tflite"}
    included = [p for p in OUT.rglob("*") if p.is_file()]
    if any(p.suffix.lower() in forbidden for p in included):
        print("Evidence bundle contains a prohibited build/model artifact", file=sys.stderr)
        return 1
    hashes = []
    for path in sorted(included):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        hashes.append(f"{digest}  {path.relative_to(OUT).as_posix()}")
    write_text(OUT / "SHA256SUMS", "\n".join(hashes) + "\n")
    print(json.dumps({"status": summary["status"], "sourceCommit": sha,
                      "xmlFiles": len(xml_files), "counts": counts,
                      "adbDevice": serial is not None, "gradleExitCode": gradle_exit}, ensure_ascii=False))
    return 0 if summary["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
