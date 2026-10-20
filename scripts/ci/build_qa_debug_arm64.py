#!/usr/bin/env python3
"""Build and validate a QA-only ARM64 Debug APK for the GitHub artifact."""

from __future__ import annotations

import hashlib
import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNNER_TEMP = Path(os.environ.get("RUNNER_TEMP", "/tmp"))
OUT = Path(os.environ.get("QA_ARTIFACT_DIR", RUNNER_TEMP / "local-ai-workspace-qa-arm64"))
APK_NAME = "local-ai-workspace-0.5.0-debug-arm64.apk"
PREVIOUS_UPDATE_CERTIFICATE_SHA256 = "f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b"
EXPECTED_COMMIT = os.environ.get("GITHUB_SHA", "")


class QaBuildError(RuntimeError):
    def __init__(self, stage: str, message: str):
        super().__init__(message)
        self.stage = stage


def sanitize(value: str) -> str:
    replacements = [
        (r"(?i)(bearer\s+)[A-Za-z0-9._~+/-]+=*", r"\1[REDACTED]"),
        (r"(?i)((?:token|password|api[_-]?key|authorization|secret)(?:\s*[=:]\s*|\s+))[^\s,;]+", r"\1[REDACTED]"),
        (r"(?i)(https?://)[^/\s:@]+:[^@\s]+@", r"\1[REDACTED]@"),
        (r"/home/runner(?:/[A-Za-z0-9_.-]+)*", "[RUNNER_PATH]"),
    ]
    for pattern, replacement in replacements:
        value = re.sub(pattern, replacement, value)
    for name in ("QA_KEYSTORE_B64", "QA_KEYSTORE_PASSWORD", "QA_KEY_ALIAS", "QA_KEY_PASSWORD"):
        secret = os.environ.get(name)
        if secret:
            value = value.replace(secret, "[REDACTED]")
    return value


LOG_PATH = OUT / "logs" / "qa-build-sanitized.log"


def log_block(title: str, command: list[str], output: str, return_code: int) -> None:
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a", encoding="utf-8") as stream:
        stream.write(f"\n===== {title} (exit {return_code}) =====\n")
        stream.write("$ " + " ".join(command) + "\n")
        stream.write(sanitize(output))
        if not output.endswith("\n"):
            stream.write("\n")


def run(title: str, command: list[str], *, input_text: str | None = None, timeout: int = 1200) -> str:
    try:
        result = subprocess.run(
            command,
            cwd=ROOT,
            input=input_text,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as error:
        output = error.stdout or ""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        log_block(title, command, output + "\nCOMMAND_TIMEOUT\n", 124)
        raise QaBuildError(title, "COMMAND_TIMEOUT") from error
    log_block(title, command, result.stdout, result.returncode)
    if result.returncode != 0:
        raise QaBuildError(title, f"COMMAND_EXIT_{result.returncode}")
    return result.stdout


def version_from_badging(output: str) -> tuple[str, str, str]:
    match = re.search(
        r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'",
        output,
        re.MULTILINE,
    )
    if not match:
        raise QaBuildError("APK_METADATA", "AAPT_PACKAGE_METADATA_MISSING")
    return match.group(1), match.group(2), match.group(3)


def write_report(data: dict[str, object]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "RESULTS.json").write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def prepare_persistent_signing() -> Path | None:
    names = ("QA_KEYSTORE_B64", "QA_KEYSTORE_PASSWORD", "QA_KEY_ALIAS", "QA_KEY_PASSWORD")
    values = {name: os.environ.get(name, "") for name in names}
    present = [bool(value) for value in values.values()]
    if not any(present):
        if os.environ.get("QA_REQUIRE_PERSISTENT_SIGNING", "").lower() == "true":
            raise QaBuildError("SIGNING_CONFIG", "QA_PERSISTENT_SIGNING_SECRETS_REQUIRED")
        return None
    if not all(present):
        missing = ",".join(name for name, value in values.items() if not value)
        raise QaBuildError("SIGNING_CONFIG", f"QA_SIGNING_SECRETS_INCOMPLETE:{missing}")

    encoded = "".join(values["QA_KEYSTORE_B64"].split())
    try:
        keystore_bytes = base64.b64decode(encoded, validate=True)
    except (ValueError, base64.binascii.Error) as error:
        raise QaBuildError("SIGNING_CONFIG", "QA_KEYSTORE_B64_INVALID") from error
    if not keystore_bytes or len(keystore_bytes) > 1024 * 1024:
        raise QaBuildError("SIGNING_CONFIG", "QA_KEYSTORE_SIZE_INVALID")
    if keystore_bytes.startswith(b"\xfe\xed\xfe\xed"):
        keystore_type = "JKS"
    elif keystore_bytes.startswith(b"\x30"):
        keystore_type = "PKCS12"
    else:
        raise QaBuildError("SIGNING_CONFIG", "QA_KEYSTORE_FORMAT_UNRECOGNIZED")

    RUNNER_TEMP.mkdir(parents=True, exist_ok=True)
    signing_dir = Path(tempfile.mkdtemp(prefix="aws-personal-qa-signing-", dir=RUNNER_TEMP))
    signing_dir.chmod(0o700)
    keystore_path = signing_dir / "qa-signing.keystore"
    keystore_path.write_bytes(keystore_bytes)
    keystore_path.chmod(0o600)
    os.environ["QA_KEYSTORE_FILE"] = str(keystore_path)
    os.environ["QA_KEYSTORE_TYPE"] = keystore_type
    return signing_dir


def certificate_sha256_from_keytool(output: str) -> str | None:
    match = re.search(r"SHA256:\s*([0-9a-fA-F:]+)", output)
    return match.group(1).replace(":", "").lower() if match else None


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    if LOG_PATH.exists():
        LOG_PATH.unlink()

    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    report: dict[str, object] = {
        "status": "RUNNING",
        "artifactType": "QA_DEBUG_ARM64_NOT_FOR_PRODUCTION",
        "sourceCommit": commit,
        "eventCommit": EXPECTED_COMMIT or None,
        "branchRef": os.environ.get("GITHUB_REF"),
        "runId": os.environ.get("GITHUB_RUN_ID"),
        "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "runUrl": f"{os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/{os.environ.get('GITHUB_REPOSITORY', '')}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}",
        "startedAtUtc": datetime.now(timezone.utc).isoformat(),
        "buildCommand": "./gradlew :app:clean :app:assembleDebug -Parm64Only=true --no-daemon --no-parallel --max-workers=2 --stacktrace --console=plain",
        "buildEnvironment": {
            "runner": "GitHub-hosted ubuntu-24.04",
            "jdk": "Temurin 17",
            "gradleWrapper": "8.10.2",
            "androidPlatform": "35",
            "buildTools": "35.0.0",
            "ndk": "27.0.12077973",
            "cmake": "3.31.6",
            "requestedAbi": "arm64-v8a",
        },
        "emulator": "NOT_RUN",
        "physicalDevice": "NOT_RUN",
        "debugKeystoreExported": False,
    }

    signing_dir: Path | None = None
    try:
        signing_dir = prepare_persistent_signing()
        parallel_install = os.environ.get("QA_PARALLEL_INSTALL", "").lower() == "true"
        expected_package = "com.localai.workspace.parallel" if parallel_install else "com.localai.workspace.debug"
        apk_name = (
            "local-ai-workspace-0.5.0-debug-arm64-parallel.apk"
            if parallel_install else APK_NAME
        )
        report["parallelInstall"] = parallel_install
        if EXPECTED_COMMIT and commit != EXPECTED_COMMIT:
            raise QaBuildError("SOURCE_SHA", "CHECKED_OUT_SHA_DIFFERS_FROM_EVENT_SHA")

        checkout_status = run(
            "Verify clean Git checkout",
            ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        )
        if checkout_status.strip():
            raise QaBuildError("SOURCE_CHECKOUT", "CHECKOUT_NOT_CLEAN_BEFORE_BUILD")
        run(
            "Verify required vendored native source is tracked",
            ["git", "ls-files", "--error-unmatch", "third_party/llama.cpp/src/models/models.h"],
        )

        sdk_value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk_value:
            raise QaBuildError("SDK_SETUP", "ANDROID_SDK_ROOT_UNAVAILABLE")
        sdk = Path(sdk_value).resolve()
        sdkmanager = sdk / "cmdline-tools" / "latest" / "bin" / "sdkmanager"
        if not sdkmanager.is_file():
            raise QaBuildError("SDK_SETUP", "SDKMANAGER_NOT_FOUND")

        packages = [
            "platforms;android-35",
            "build-tools;35.0.0",
            "ndk;27.0.12077973",
            "cmake;3.31.6",
        ]
        run("Accept installed SDK licenses", [str(sdkmanager), f"--sdk_root={sdk}", "--licenses"], input_text="y\n" * 100)
        run("Install pinned build SDK components", [str(sdkmanager), f"--sdk_root={sdk}", "--install", *packages], timeout=1800)

        expected_tools = [
            sdk / "platforms" / "android-35" / "android.jar",
            sdk / "build-tools" / "35.0.0" / "apksigner",
            sdk / "build-tools" / "35.0.0" / "aapt",
            sdk / "ndk" / "27.0.12077973" / "source.properties",
            sdk / "cmake" / "3.31.6" / "bin" / "cmake",
        ]
        if any(not path.exists() for path in expected_tools):
            raise QaBuildError("SDK_SETUP", "PINNED_ANDROID_BUILD_COMPONENT_MISSING")

        gradle_version = run("Verify Gradle wrapper", ["./gradlew", "--version"], timeout=300)
        gradle_match = re.search(r"Gradle\s+(\d+\.\d+(?:\.\d+)?)", gradle_version)
        if not gradle_match or gradle_match.group(1) != "8.10.2":
            raise QaBuildError("BUILD_CONFIGURATION", "UNEXPECTED_GRADLE_WRAPPER_VERSION")

        gradle = [
            "./gradlew",
            ":app:clean",
            ":app:assembleDebug",
            "-Parm64Only=true",
            "--no-daemon",
            "--no-parallel",
            "--max-workers=2",
            "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8",
            "--stacktrace",
            "--console=plain",
        ]
        if parallel_install:
            gradle.append("-PqaParallelInstall=true")
        run("Build Debug ARM64 APK", gradle, timeout=3300)

        apk_path = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
        if not apk_path.is_file():
            raise QaBuildError("APK_OUTPUT", "EXPECTED_DEBUG_APK_MISSING")

        apksigner = sdk / "build-tools" / "35.0.0" / "apksigner"
        signature_output = run(
            "Verify Debug APK signature",
            [str(apksigner), "verify", "--verbose", "--print-certs", str(apk_path)],
        )
        cert_match = re.search(
            r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", signature_output
        )
        dn_match = re.search(r"Signer #1 certificate DN:\s*(.+)", signature_output)
        if not cert_match or not dn_match:
            raise QaBuildError("APK_SIGNATURE", "DEBUG_SIGNER_METADATA_MISSING")
        certificate_sha256 = cert_match.group(1).replace(":", "").lower()
        certificate_dn = dn_match.group(1).strip()
        if len(certificate_sha256) != 64:
            raise QaBuildError("APK_SIGNATURE", "SIGNER_CERTIFICATE_FINGERPRINT_INVALID")
        if signing_dir is not None:
            keytool = shutil.which("keytool")
            if not keytool:
                raise QaBuildError("APK_SIGNATURE", "KEYTOOL_NOT_FOUND")
            keytool_output = run(
                "Verify persistent QA keystore certificate",
                [keytool, "-list", "-v", "-keystore", str(os.environ["QA_KEYSTORE_FILE"]), "-alias", os.environ["QA_KEY_ALIAS"]],
                input_text=os.environ["QA_KEYSTORE_PASSWORD"] + "\n",
            )
            expected_certificate_sha256 = certificate_sha256_from_keytool(keytool_output)
            if not expected_certificate_sha256 or certificate_sha256 != expected_certificate_sha256:
                raise QaBuildError("APK_SIGNATURE", "APK_CERTIFICATE_DOES_NOT_MATCH_PERSISTENT_QA_KEYSTORE")
            if not parallel_install and certificate_sha256 != PREVIOUS_UPDATE_CERTIFICATE_SHA256:
                raise QaBuildError("APK_SIGNATURE", "PERSISTENT_QA_CERTIFICATE_DIFFERS_FROM_PREVIOUS_UPDATE_KEY; USE_PARALLEL_INSTALL_OR_RESTORE_THE_COMPATIBLE_KEY")
            signing_variant = "Persistent personal QA keystore supplied through Actions secrets"
        else:
            if "Android Debug" not in certificate_dn:
                raise QaBuildError("APK_SIGNATURE", "NOT_SIGNED_BY_STANDARD_ANDROID_DEBUG_KEY")
            signing_variant = "Gradle standard Android Debug keystore"

        aapt = sdk / "build-tools" / "35.0.0" / "aapt"
        badging = run("Inspect package and version", [str(aapt), "dump", "badging", str(apk_path)])
        package_name, version_code, version_name = version_from_badging(badging)
        expected_metadata = (expected_package, "27", "0.5.0-debug")
        if (package_name, version_code, version_name) != expected_metadata:
            raise QaBuildError("APK_METADATA", "UNEXPECTED_PACKAGE_OR_VERSION")

        with zipfile.ZipFile(apk_path) as archive:
            abis = sorted({
                name.split("/")[1]
                for name in archive.namelist()
                if name.startswith("lib/") and name.endswith(".so") and len(name.split("/")) >= 3
            })
        if abis != ["arm64-v8a"]:
            raise QaBuildError("APK_ABI", "APK_MUST_CONTAIN_ONLY_ARM64_V8A_LIBRARIES")

        destination = OUT / apk_name
        shutil.copy2(apk_path, destination)
        apk_digest = hashlib.sha256(destination.read_bytes()).hexdigest()
        with zipfile.ZipFile(destination) as archive:
            native_library_count = sum(
                1 for name in archive.namelist()
                if name.startswith("lib/arm64-v8a/") and name.endswith(".so")
            )
        report.update({
            "completedAtUtc": datetime.now(timezone.utc).isoformat(),
            "status": "PASS",
            "stage": "APK_VERIFIED",
            "apk": {
                "filename": apk_name,
                "bytes": destination.stat().st_size,
                "sha256": apk_digest,
                "packageName": package_name,
                "versionCode": int(version_code),
                "versionName": version_name,
                "abis": abis,
                "nativeLibraryCount": native_library_count,
                "signatureVerified": True,
                "signingVariant": signing_variant,
                "certificateDn": certificate_dn,
                "certificateSha256": certificate_sha256,
            },
            "validation": {
                "apksigner": "PASS",
                "packageAndVersion": "PASS",
                "arm64Only": "PASS",
                "noReleaseSigningKeyExported": True,
            },
        })
    except QaBuildError as error:
        report.update({"completedAtUtc": datetime.now(timezone.utc).isoformat(), "status": "FAIL", "failedStage": error.stage, "failureReason": sanitize(str(error))})
    except Exception as error:  # Keep failure evidence without exposing a traceback or environment.
        report.update({"completedAtUtc": datetime.now(timezone.utc).isoformat(), "status": "FAIL", "failedStage": "UNEXPECTED", "failureReason": sanitize(type(error).__name__ + ": " + str(error))})
    finally:
        if signing_dir is not None:
            shutil.rmtree(signing_dir, ignore_errors=True)

    write_report(report)
    tail = LOG_PATH.read_text(encoding="utf-8", errors="replace")[-12000:] if LOG_PATH.exists() else ""
    print(tail)
    if report["status"] != "PASS":
        print("QA_ARM64_APK_VALIDATION_FAILED", file=sys.stderr)
        return 1
    print(json.dumps({"status": report["status"], "sourceCommit": commit, "apk": report.get("apk")}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
