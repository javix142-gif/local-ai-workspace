import base64
import hashlib
import json
import struct
import subprocess
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

import collect_android_emulator_evidence as collector


def png_chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def valid_png(width: int, height: int) -> bytes:
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    raw = b"".join(b"\x00" + (b"\x22\x44\x66\xff" * width) for _ in range(height))
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", ihdr)
        + png_chunk(b"IDAT", zlib.compress(raw))
        + png_chunk(b"IEND", b"")
    )


def transport_record(label: str, payload: bytes, width: int, height: int, chunk_size: int = 512) -> str:
    encoded = base64.b64encode(payload).decode("ascii")
    chunks = [encoded[i:i + chunk_size] for i in range(0, len(encoded), chunk_size)]
    digest = hashlib.sha256(payload).hexdigest()
    lines = [f"M00PNG|BEGIN|{label}|{width}|{height}|{len(payload)}|{digest}|{len(chunks)}"]
    lines.extend(f"M00PNG|CHUNK|{label}|{index}|{chunk}" for index, chunk in enumerate(chunks))
    lines.append(f"M00PNG|END|{label}|{digest}")
    return "\n".join(lines)


class HeaderScreenshotCollectorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "evidence"
        collector.OUT = self.output
        collector.ADB = "/sdk/platform-tools/adb"

    def run_collector_with_log(self, log: str, exit_code: int = 0):
        def fake_command(args, timeout=20):
            self.assertEqual(
                [collector.ADB, "-s", "emulator-5554", "logcat", "-d", "-v", "raw", "-s", "M00HeaderPng:I"],
                args,
            )
            self.assertEqual(60, timeout)
            return subprocess.CompletedProcess(args, exit_code, log, "logcat diagnostic\n" if exit_code else "")

        with patch.object(collector, "command", side_effect=fake_command):
            copied = collector.collect_header_screenshots("emulator-5554")
        report = json.loads((self.output / "ui-diagnostics/screenshot-pull.json").read_text())
        return copied, report

    def test_reconstructs_both_valid_pngs_with_hashes_and_dimensions(self):
        first = valid_png(2, 1)
        second = valid_png(1, 2)
        log = transport_record("standard", first, 2, 1) + "\n" + transport_record("large-font", second, 1, 2)

        copied, report = self.run_collector_with_log(log)

        self.assertEqual(2, len(copied))
        self.assertEqual(["standard", "large-font"], [Path(path).stem.removeprefix("chat-header-") for path in copied])
        for record, payload, size in zip(report["screenshots"], (first, second), ((2, 1), (1, 2))):
            self.assertEqual(0, record["logcatExitCode"])
            self.assertEqual("logcat_base64_chunks_v1", record["transport"])
            self.assertTrue(record["localFilePresent"])
            self.assertTrue(record["pngValid"])
            self.assertTrue(record["endMarkerReceived"])
            self.assertEqual(size, (record["widthPixels"], record["heightPixels"]))
            self.assertEqual(len(payload), record["bytes"])
            self.assertEqual(hashlib.sha256(payload).hexdigest(), record["sha256"])
            self.assertEqual(payload, (self.output / record["localPath"]).read_bytes())

    def test_missing_chunk_is_not_saved_or_counted(self):
        payload = valid_png(2, 1)
        lines = transport_record("standard", payload, 2, 1).splitlines()
        del lines[1]
        log = "\n".join(lines) + "\n" + transport_record("large-font", payload, 2, 1)

        copied, report = self.run_collector_with_log(log)

        self.assertEqual(1, len(copied))
        standard = report["screenshots"][0]
        self.assertFalse(standard["localFilePresent"])
        self.assertIn("CHUNK_SET_INCOMPLETE", standard["errors"])
        self.assertFalse((self.output / standard["localPath"]).exists())

    def test_png_with_bad_crc_is_rejected(self):
        payload = bytearray(valid_png(2, 1))
        payload[-5] ^= 0x01
        log = transport_record("standard", bytes(payload), 2, 1) + "\n" + transport_record("large-font", valid_png(1, 1), 1, 1)

        copied, report = self.run_collector_with_log(log)

        self.assertEqual(1, len(copied))
        self.assertIn("PNG_INVALID", report["screenshots"][0]["errors"])
        self.assertFalse(report["screenshots"][0]["localFilePresent"])

    def test_duplicate_capture_for_a_label_is_ambiguous_and_rejected(self):
        payload = valid_png(1, 1)
        one = transport_record("standard", payload, 1, 1)
        log = one + "\n" + one + "\n" + transport_record("large-font", payload, 1, 1)

        copied, report = self.run_collector_with_log(log)

        self.assertEqual(1, len(copied))
        self.assertIn("DUPLICATE_CAPTURE", report["screenshots"][0]["errors"])
        self.assertFalse(report["screenshots"][0]["localFilePresent"])

    def test_logcat_failure_is_recorded_and_no_capture_is_claimed(self):
        copied, report = self.run_collector_with_log("", exit_code=1)

        self.assertEqual([], copied)
        self.assertEqual(1, report["screenshots"][0]["logcatExitCode"])
        self.assertIn("LOGCAT_READ_FAILED", report["screenshots"][0]["errors"])
        self.assertIn("CAPTURE_MISSING", report["screenshots"][0]["errors"])

    def test_no_emulator_creates_missing_capture_records(self):
        with patch.object(collector, "command") as run:
            copied = collector.collect_header_screenshots(None)
        self.assertEqual([], copied)
        run.assert_not_called()
        report = json.loads((self.output / "ui-diagnostics/screenshot-pull.json").read_text())
        self.assertEqual(2, len(report["screenshots"]))
        self.assertTrue(all(item["reason"] == "NO_CONNECTED_EMULATOR" for item in report["screenshots"]))

    def test_instrumentation_uses_chunk_transport_and_no_run_as_or_external_redirect(self):
        source = (
            Path(__file__).resolve().parents[2]
            / "app/src/androidTest/java/com/localai/workspace/ui/ChatHeaderLayoutTest.kt"
        ).read_text()
        self.assertIn('"M00PNG|BEGIN|$label|', source)
        self.assertIn('"M00PNG|CHUNK|$label|$index|$chunk"', source)
        self.assertIn("cacheFileRetained=true", source)
        self.assertNotIn("run-as", source)
        self.assertNotIn("/sdcard/Download", source)
        self.assertNotIn("captureFile.delete()", source)


if __name__ == "__main__":
    unittest.main()
