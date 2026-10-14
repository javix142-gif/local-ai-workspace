#!/usr/bin/env python3
"""Verify APK ZIP integrity and reject model payloads in personal QA artifacts."""

from __future__ import annotations

import hashlib
import sys
import zipfile


MODEL_SUFFIXES = (
    ".bin",
    ".gguf",
    ".litertlm",
    ".onnx",
    ".tflite",
    ".safetensors",
    ".pt",
    ".pth",
    ".ckpt",
    ".ggml",
)

# The downloaded run 38491402535 APK contains this exact Kotlin coroutine
# debug-probe class as a .bin resource. Its path, class-file magic, class name,
# and digest were checked against the artifact; any changed or similarly named
# entry remains rejected by the model-payload check.
KNOWN_NON_MODEL_ENTRY = "DebugProbesKt.bin"
KNOWN_NON_MODEL_SHA256 = "4d13bd92f86a0b1415062b8ed57655c0738ccfbdf0786c1d730d988b27fd4400"
KNOWN_NON_MODEL_CLASS = b"kotlin/coroutines/jvm/internal/DebugProbesKt"


def is_verified_debug_probe_class(path: str, content: bytes) -> bool:
    return (
        path == KNOWN_NON_MODEL_ENTRY
        and content.startswith(b"\xca\xfe\xba\xbe")
        and KNOWN_NON_MODEL_CLASS in content
        and hashlib.sha256(content).hexdigest() == KNOWN_NON_MODEL_SHA256
    )


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_qa_debug_apk_integrity.py APK", file=sys.stderr)
        return 2

    apk_path = sys.argv[1]
    try:
        with zipfile.ZipFile(apk_path) as archive:
            damaged = archive.testzip()
            if damaged is not None:
                print(f"ZIP_CRC_FAIL: {damaged}")
                return 1

            suffix_matches = [
                info
                for info in archive.infolist()
                if info.filename.lower().endswith(MODEL_SUFFIXES)
            ]
            model_payloads: list[tuple[str, int]] = []
            recognized: list[tuple[str, int, str]] = []
            for info in suffix_matches:
                name = info.filename
                content = archive.read(info)
                digest = hashlib.sha256(content).hexdigest()
                if is_verified_debug_probe_class(name, content):
                    recognized.append((name, len(content), digest))
                else:
                    model_payloads.append((name, len(content)))
    except (OSError, zipfile.BadZipFile) as error:
        print(f"APK_ZIP_READ_FAIL: {type(error).__name__}")
        return 1

    print("ZIP_CRC=PASS")
    for name, size, digest in recognized:
        print(
            "KNOWN_NON_MODEL_SUFFIX_ENTRY="
            f"{name}; bytes={size}; sha256={digest}; "
            "classification=verified Kotlin coroutine debug-probe class"
        )
    if model_payloads:
        for name, size in model_payloads:
            print(f"MODEL_PAYLOAD_CANDIDATE={name}; bytes={size}")
        print("MODEL_PAYLOADS=FAIL")
        return 1

    print("MODEL_PAYLOADS=NONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
