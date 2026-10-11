import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import collect_android_emulator_evidence as collector


class HeaderScreenshotCollectorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "evidence"
        collector.OUT = self.output
        collector.ADB = "/sdk/platform-tools/adb"

    def test_creates_destination_before_pull_and_records_hashes(self):
        payload = b"synthetic-png-bytes"

        def fake_command(args, timeout=20):
            if args[3:6] == ["shell", "ls", "-l"]:
                return subprocess.CompletedProcess(args, 0, "-rw-r--r-- screenshot.png\n", "")
            if args[3] == "pull":
                destination = Path(args[-1])
                self.assertTrue(destination.parent.is_dir())
                destination.write_bytes(payload)
                return subprocess.CompletedProcess(args, 0, "1 file pulled\n", "")
            self.fail(f"unexpected command: {args}")

        with patch.object(collector, "command", side_effect=fake_command):
            copied = collector.collect_header_screenshots("emulator-5554")

        self.assertEqual(2, len(copied))
        report = json.loads((self.output / "ui-diagnostics/screenshot-pull.json").read_text())
        self.assertEqual(2, len(report["screenshots"]))
        for record, path in zip(report["screenshots"], copied):
            self.assertEqual(0, record["remoteCheckExitCode"])
            self.assertTrue(record["remoteFilePresent"])
            self.assertEqual(0, record["pullExitCode"])
            self.assertTrue(record["localFilePresent"])
            self.assertEqual(len(payload), record["bytes"])
            self.assertEqual(hashlib.sha256(payload).hexdigest(), record["sha256"])
            self.assertEqual(path.read_bytes(), payload)

    def test_missing_remote_and_local_files_are_recorded_without_claiming_capture(self):
        def fake_command(args, timeout=20):
            if args[3:6] == ["shell", "ls", "-l"]:
                return subprocess.CompletedProcess(args, 1, "", "No such file\n")
            if args[3] == "pull":
                return subprocess.CompletedProcess(args, 1, "", "remote object does not exist\n")
            self.fail(f"unexpected command: {args}")

        with patch.object(collector, "command", side_effect=fake_command):
            copied = collector.collect_header_screenshots("emulator-5554")

        self.assertEqual([], copied)
        report = json.loads((self.output / "ui-diagnostics/screenshot-pull.json").read_text())
        for record in report["screenshots"]:
            self.assertEqual(1, record["remoteCheckExitCode"])
            self.assertFalse(record["remoteFilePresent"])
            self.assertEqual(1, record["pullExitCode"])
            self.assertFalse(record["localFilePresent"])
            self.assertEqual(0, record["bytes"])
            self.assertIsNone(record["sha256"])


if __name__ == "__main__":
    unittest.main()
