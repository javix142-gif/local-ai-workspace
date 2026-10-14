#!/usr/bin/env python3
"""Repackage a verified QA APK with identical payloads and a retained local key.

Only DEX ZIP compression changes. Native libraries and resources keep their
compression method. No key is generated or exported; callers must supply the
existing Android Debug keystore and the certificate used by their installed APK.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def signature_entry(name: str) -> bool:
    return bool(re.fullmatch(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", name.upper()))


def payloads(path: Path) -> dict[str, str]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("APK has duplicate ZIP entries or damaged CRC")
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in names if not signature_entry(name)}


def compress_dex(source: Path, destination: Path) -> int:
    count = 0
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(destination, "w") as output:
        for entry in original.infolist():
            if signature_entry(entry.filename):
                continue
            target = copy.copy(entry)
            if re.fullmatch(r"classes(?:[0-9]+)?\.dex", entry.filename):
                target.compress_type = zipfile.ZIP_DEFLATED
                count += 1
            output.writestr(target, original.read(entry), compresslevel=9)
    return count


def run(command: list[str], log: Path, key: Path) -> str:
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    with log.open("a", encoding="utf-8") as stream:
        stream.write("$ " + " ".join(command).replace(str(key), "[RETAINED_LOCAL_DEBUG_KEYSTORE]") + "\n")
        stream.write(result.stdout + result.stderr + f"\nexit_code={result.returncode}\n")
    if result.returncode:
        raise RuntimeError(f"{Path(command[0]).name} exited {result.returncode}; see validation log")
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--sdk-root", type=Path, required=True)
    parser.add_argument("--jdk-root", type=Path, required=True)
    parser.add_argument("--keystore", type=Path, required=True)
    parser.add_argument("--expected-certificate-sha256", required=True)
    args = parser.parse_args()
    if not args.keystore.is_file() or args.output.exists() or args.input.resolve() == args.output.resolve():
        raise ValueError("Existing keystore and a new, distinct output path are required")
    if digest(args.input) != args.source_sha256:
        raise ValueError("Source APK SHA-256 does not match verified artifact")
    before = payloads(args.input)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    log = args.output.with_suffix(".validation.log")
    log.write_text("", encoding="utf-8")
    cert = run([str(args.jdk_root / "bin/keytool"), "-list", "-v", "-keystore", str(args.keystore),
                "-storepass", "android", "-alias", "androiddebugkey"], log, args.keystore)
    match = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", cert)
    if not match or match[1].replace(":", "").lower() != args.expected_certificate_sha256.lower():
        raise ValueError("Retained key certificate does not match requested update certificate")
    build_tools = args.sdk_root / "build-tools/35.0.0"
    signer = str(build_tools / "apksigner")
    align = str(build_tools / "zipalign")
    run([signer, "verify", "--verbose", "--print-certs", str(args.input)], log, args.keystore)
    with tempfile.TemporaryDirectory(prefix="qa-update-", dir=args.output.parent) as temporary:
        compressed = Path(temporary) / "dex-compressed.apk"
        aligned = Path(temporary) / "aligned.apk"
        count = compress_dex(args.input, compressed)
        if count == 0 or payloads(compressed) != before:
            raise ValueError("Repackaging changed payloads or did not find DEX entries")
        run([align, "-P", "16", "-f", "4", str(compressed), str(aligned)], log, args.keystore)
        run([signer, "sign", "--ks", str(args.keystore), "--ks-key-alias", "androiddebugkey",
             "--ks-pass", "pass:android", "--key-pass", "pass:android", "--v1-signing-enabled", "false",
             "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
             "--out", str(args.output), str(aligned)], log, args.keystore)
    signature = run([signer, "verify", "--verbose", "--print-certs", str(args.output)], log, args.keystore)
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)", signature)
    if not match or match[1].lower() != args.expected_certificate_sha256.lower():
        raise ValueError("Output signer differs from update certificate")
    run([align, "-P", "16", "-c", "-v", "4", str(args.output)], log, args.keystore)
    badging = run([str(build_tools / "aapt"), "dump", "badging", str(args.output)], log, args.keystore)
    metadata = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not metadata or tuple(metadata.groups()) != ("com.localai.workspace.debug", "27", "0.5.0-debug"):
        raise ValueError("Package/version metadata changed")
    if payloads(args.output) != before:
        raise ValueError("Signed APK payloads differ from source")
    with zipfile.ZipFile(args.output) as archive:
        abis = sorted({name.split("/")[1] for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")})
        if abis != ["arm64-v8a"]:
            raise ValueError("Unexpected native ABI")
    report = {"artifactType": "PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION", "status": "PACKAGING_VERIFIED_DEVICE_NOT_RUN",
              "sourceCommit": args.source_commit, "sourceApkSha256": args.source_sha256,
              "apk": {"filename": args.output.name, "bytes": args.output.stat().st_size, "sha256": digest(args.output),
                      "applicationId": metadata[1], "versionCode": int(metadata[2]), "versionName": metadata[3],
                      "abis": abis, "certificateSha256": match[1].lower()},
              "compressedDexEntries": count, "identicalPayloadEntries": len(before),
              "checks": {"zipCrc": "PASS", "payloadPreservation": "PASS", "signature": "PASS v2/v3", "zipalign4AndNative16KiB": "PASS"},
              "upgradeCompatibility": "Same package/version and certificate as local-signed UX01/UX02a. Installed-device certificate not accessible; compatibility with other signers is not established.",
              "EMULATOR": "NOT_RUN", "PHYSICAL_DEVICE": "NOT_RUN"}
    args.output.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
