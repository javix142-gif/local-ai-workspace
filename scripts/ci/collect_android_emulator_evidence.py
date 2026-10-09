#!/usr/bin/env python3
"""Collect bounded, privacy-conscious evidence for connected Android tests."""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
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
    ):
        safe_copy(RUNNER_TEMP / filename, OUT / "logs" / filename)

    if serial:
        logcat = command([ADB, "-s", serial, "logcat", "-d", "-t", "2000", "-v", "time",
                          "-s", "AndroidRuntime:E", "TestRunner:I", "Instrumentation:I"], timeout=60)
        write_text(OUT / "logs/logcat-filtered-sanitized.txt", redact_log(logcat.stdout + logcat.stderr))
        write_text(OUT / "adb-after-tests.txt", redact_log(command([ADB, "devices", "-l"]).stdout))
    else:
        write_text(OUT / "logs/logcat-filtered-sanitized.txt", "NOT_AVAILABLE: no booted emulator detected\n")
        write_text(OUT / "adb-after-tests.txt", "NOT_AVAILABLE: no booted emulator detected\n")

    xml_files = collect_xml()
    counts, skipped_cases = parse_results(xml_files)
    gradle_exit_text = os.environ.get("GRADLE_EXIT_CODE", "").strip()
    try:
        gradle_exit: int | None = int(gradle_exit_text)
    except ValueError:
        gradle_exit = None
    facts = adb_facts(serial)
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com").rstrip("/")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    summary: dict[str, object] = {
        "label": "EMULATOR",
        "sourceCommit": sha,
        "runId": run_id,
        "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
        "runUrl": f"{server}/{repository}/actions/runs/{run_id}" if run_id and repository else None,
        "expected": {"api": 35, "abi": "x86_64", "systemImage": "google_apis"},
        "device": facts,
        "physicalDeviceValidation": "NOT_RUN",
        "gradleExitCode": gradle_exit,
        "instrumentationXmlCount": len(xml_files),
        "counts": counts,
        "skippedCases": skipped_cases,
        "status": "PASS" if serial and gradle_exit == 0 and xml_files and counts["executed"] > 0 and counts["failed"] == 0 and counts["errors"] == 0 else "FAIL",
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
