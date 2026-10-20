#!/usr/bin/env python3
"""Exercise H1-AT-12 with a real prior APK install and in-place update."""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TEMP = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "h1-at12"
OLD_ROOT = Path(os.environ.get("H1_PREVIOUS_SOURCE_DIR", ROOT / "h1-previous"))
SDK = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "/nonexistent/android-sdk")
ADB = SDK / "platform-tools" / "adb"
APKSIGNER = SDK / "build-tools" / "35.0.0" / "apksigner"
AAPT = SDK / "build-tools" / "35.0.0" / "aapt"
KEYSTORE = ROOT / "keystore" / "aws-qa-dev.jks"
ALIAS = "awsqa"
PASSWORD = "awsqadev"
CERT_SHA256 = "b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b"
APP_ID = "com.localai.workspace.debug"
TEST_ID = f"{APP_ID}.test"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
SEED_CLASS = "com.localai.workspace.h1acceptance.H1At12SeedTest#H1_AT_12_seedPreviousVersionData"
VERIFY_CLASS = "com.localai.workspace.h1acceptance.H1At12AfterUpdateTest#H1_AT_12_verifyMemoriesProjectChatAndNextContextAfterUpdate"


class Failure(RuntimeError):
    pass


def run(name: str, args: list[str], cwd: Path | None = None, timeout: int = 1800) -> subprocess.CompletedProcess[str]:
    log = TEMP / f"{name}.txt"
    log.parent.mkdir(parents=True, exist_ok=True)
    try:
        result = subprocess.run(args, cwd=cwd, text=True, capture_output=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired as error:
        def output_text(value: str | bytes | None) -> str:
            if value is None:
                return ""
            return value.decode("utf-8", errors="replace") if isinstance(value, bytes) else value

        stdout = output_text(error.stdout)
        stderr = output_text(error.stderr)
        log.write_text(
            f"command={args!r}\ncwd={cwd or ROOT}\ntimeout_seconds={timeout}\nexit_code=124\ntimed_out=true\n"
            f"--- stdout (partial) ---\n{stdout}\n--- stderr (partial) ---\n{stderr}\n",
            encoding="utf-8",
        )
        raise Failure(f"{name.upper()}_TIMEOUT_AFTER_{timeout}_SECONDS") from error
    log.write_text(
        f"command={args!r}\ncwd={cwd or ROOT}\ntimeout_seconds={timeout}\nexit_code={result.returncode}\ntimed_out=false\n"
        f"--- stdout ---\n{result.stdout}\n--- stderr ---\n{result.stderr}\n",
        encoding="utf-8",
    )
    if result.returncode:
        raise Failure(f"{name.upper()}_EXIT_{result.returncode}")
    return result


def write_password_source(path: Path, password: str) -> Path:
    """Create one private apksigner password source; each option gets its own file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(password + "\n", encoding="utf-8")
    path.chmod(0o600)
    return path


def gradle(source: Path, *tasks: str) -> None:
    wrapper = source / "gradlew"
    run("gradle-" + ("old" if source == OLD_ROOT else "current") + "-build",
        [str(wrapper), *tasks, "--no-daemon", "--no-parallel", "--max-workers=2", "--console=plain"],
        cwd=source,
        timeout=3600,
    )


def signer_sha(apk: Path) -> str:
    output = run("apksigner-" + apk.stem, [str(APKSIGNER), "verify", "--verbose", "--print-certs", str(apk)]).stdout
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", output)
    if not match:
        raise Failure("APK_CERTIFICATE_SHA256_NOT_FOUND")
    return match.group(1).lower()


def package(apk: Path) -> tuple[str, str, str]:
    output = run("aapt-" + apk.stem, [str(AAPT), "dump", "badging", str(apk)]).stdout
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", output, re.M)
    if not match:
        raise Failure("APK_PACKAGE_METADATA_NOT_FOUND")
    return match.group(1), match.group(2), match.group(3)


def instrumentation(serial: str, test_filter: str, filename: str) -> None:
    result = run(filename, [str(ADB), "-s", serial, "shell", "am", "instrument", "-w", "-e", "class", test_filter,
                            f"{TEST_ID}/{RUNNER}"], timeout=240)
    if "OK (1 test)" not in result.stdout or "FAILURES!!!" in result.stdout:
        raise Failure(f"{filename.upper()}_TEST_RESULT_UNCONFIRMED")


def main() -> int:
    TEMP.mkdir(parents=True, exist_ok=True)
    summary: dict[str, object] = {
        "contract": "H1-AT-12",
        "status": "RUNNING",
        "startedAtUtc": datetime.now(timezone.utc).isoformat(),
        "previousSourceCommit": "9cfb0d8112342ee097ac64aa573e5d35b446f834",
        "currentSourceCommit": subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True, check=True).stdout.strip(),
        "applicationId": APP_ID,
        "versionCode": 27,
        "versionName": "0.5.0-debug",
        "fixture": "Synthetic only; 10 legacy rows, 10 sidecar rows, one project and one chat.",
        "physicalDevice": "NOT_RUN",
    }
    keystore_password_file = TEMP / "keystore-password.txt"
    key_password_file = TEMP / "key-password.txt"
    old_signed = TEMP / "previous-0.5.0-debug-signed.apk"
    current_apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    current_test = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    try:
        if not OLD_ROOT.is_dir() or not KEYSTORE.is_file() or not all(path.is_file() for path in (ADB, APKSIGNER, AAPT)):
            raise Failure("H1_AT12_REQUIRED_INPUT_MISSING")
        gradle(OLD_ROOT, ":app:assembleDebug")
        old_unsigned = OLD_ROOT / "app/build/outputs/apk/debug/app-debug.apk"
        if not old_unsigned.is_file():
            raise Failure("PREVIOUS_DEBUG_APK_MISSING")
        write_password_source(keystore_password_file, PASSWORD)
        write_password_source(key_password_file, PASSWORD)
        run("sign-previous-apk", [str(APKSIGNER), "sign", "--ks", str(KEYSTORE), "--ks-key-alias", ALIAS,
                                  "--ks-pass", f"file:{keystore_password_file}", "--key-pass", f"file:{key_password_file}",
                                  "--out", str(old_signed), str(old_unsigned)])
        old_identity = {"package": package(old_signed)[0], "versionCode": package(old_signed)[1],
                        "versionName": package(old_signed)[2], "certificateSha256": signer_sha(old_signed),
                        "apkSha256": hashlib.sha256(old_signed.read_bytes()).hexdigest()}
        if old_identity["package"] != APP_ID or old_identity["certificateSha256"] != CERT_SHA256:
            raise Failure("PREVIOUS_APK_PACKAGE_OR_SIGNER_MISMATCH")

        gradle(ROOT, ":app:assembleDebug", ":app:assembleDebugAndroidTest")
        if not current_apk.is_file() or not current_test.is_file():
            raise Failure("CURRENT_APP_OR_TEST_APK_MISSING")
        current_identity = {"package": package(current_apk)[0], "versionCode": package(current_apk)[1],
                            "versionName": package(current_apk)[2], "certificateSha256": signer_sha(current_apk),
                            "apkSha256": hashlib.sha256(current_apk.read_bytes()).hexdigest()}
        if current_identity["package"] != APP_ID or current_identity["certificateSha256"] != CERT_SHA256:
            raise Failure("CURRENT_APK_PACKAGE_OR_SIGNER_MISMATCH")
        current_test_package = package(current_test)[0]
        current_test_certificate = signer_sha(current_test)
        if current_test_package != TEST_ID or current_test_certificate != CERT_SHA256:
            raise Failure("CURRENT_TEST_APK_PACKAGE_OR_SIGNER_MISMATCH")
        current_identity["testPackage"] = current_test_package
        current_identity["testCertificateSha256"] = current_test_certificate
        current_identity["testApkSha256"] = hashlib.sha256(current_test.read_bytes()).hexdigest()

        device_result = run("adb-devices", [str(ADB), "devices", "-l"])
        devices = [line.split()[0] for line in device_result.stdout.splitlines()[1:] if len(line.split()) >= 2 and line.split()[1] == "device"]
        if len(devices) != 1 or not devices[0].startswith("emulator-"):
            raise Failure("H1_AT12_EXPECTED_EXACTLY_ONE_CONNECTED_EMULATOR")
        serial = devices[0]
        run("install-previous-app", [str(ADB), "-s", serial, "install", str(old_signed)])
        run("install-current-test-apk", [str(ADB), "-s", serial, "install", "-t", str(current_test)])
        instrumentation(serial, SEED_CLASS, "h1-at12-seed-test")
        run("update-app-install-r", [str(ADB), "-s", serial, "install", "-r", str(current_apk)])
        opened = run("open-updated-app", [str(ADB), "-s", serial, "shell", "am", "start", "-W", "-n", f"{APP_ID}/com.localai.workspace.MainActivity"])
        if "Status: ok" not in opened.stdout:
            raise Failure("UPDATED_APP_DID_NOT_OPEN")
        run("update-current-test-apk", [str(ADB), "-s", serial, "install", "-r", "-t", str(current_test)])
        instrumentation(serial, VERIFY_CLASS, "h1-at12-after-update-test")
        summary.update({
            "status": "PASS",
            "completedAtUtc": datetime.now(timezone.utc).isoformat(),
            "deviceSerial": serial,
            "oldApk": old_identity,
            "currentApk": current_identity,
            "installUpdateCommand": "adb install -r <current-debug-apk>",
            "seedTest": SEED_CLASS,
            "verifyTest": VERIFY_CLASS,
            "verified": ["20 memories and type/scope", "project", "chat/message", "preference in next ContextFoundation request", "updated app opens"],
        })
    except Exception as error:
        summary.update({"status": "FAIL", "completedAtUtc": datetime.now(timezone.utc).isoformat(),
                       "failureReason": str(error), "physicalDevice": "NOT_RUN"})
        print(f"H1_AT12_FAIL={error}", file=sys.stderr)
    finally:
        keystore_password_file.unlink(missing_ok=True)
        key_password_file.unlink(missing_ok=True)
        (TEMP / "h1-at12-summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))
    return 0 if summary["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
