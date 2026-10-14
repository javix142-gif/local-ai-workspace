"""Regression checks for content preservation during personal QA repackaging."""
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

from prepare_personal_qa_update import compress_dex, payloads


class PersonalQaUpdateTest(unittest.TestCase):
    def test_dex_compression_preserves_every_payload_and_native_storage(self):
        with tempfile.TemporaryDirectory() as temporary:
            original = Path(temporary) / "input.apk"
            packed = Path(temporary) / "output.apk"
            with zipfile.ZipFile(original, "w") as archive:
                archive.writestr("classes.dex", b"dex\n039\0" + bytes(range(256)) * 400)
                archive.writestr("classes2.dex", b"second dex" * 400)
                archive.writestr("lib/arm64-v8a/libsample.so", b"ELF native library")
                archive.writestr("resources.arsc", b"compiled resources")
                archive.writestr("AndroidManifest.xml", b"binary manifest")
                archive.writestr("META-INF/LICENSE", b"retained license")
                archive.writestr("META-INF/CERT.RSA", b"old signature only")
            self.assertEqual(2, compress_dex(original, packed))
            self.assertEqual(payloads(original), payloads(packed))
            self.assertLess(packed.stat().st_size, original.stat().st_size)
            with zipfile.ZipFile(packed) as archive:
                self.assertIsNone(archive.testzip())
                self.assertEqual(zipfile.ZIP_DEFLATED, archive.getinfo("classes.dex").compress_type)
                self.assertEqual(zipfile.ZIP_STORED, archive.getinfo("lib/arm64-v8a/libsample.so").compress_type)
                self.assertEqual(zipfile.ZIP_STORED, archive.getinfo("resources.arsc").compress_type)
                self.assertEqual(b"binary manifest", archive.read("AndroidManifest.xml"))
                self.assertNotIn("META-INF/CERT.RSA", archive.namelist())
                self.assertIn("META-INF/LICENSE", archive.namelist())

    def test_duplicate_archive_cannot_hide_changed_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            original = Path(temporary) / "duplicate.apk"
            with zipfile.ZipFile(original, "w") as archive, warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr("classes.dex", b"original")
                archive.writestr("classes.dex", b"changed")
            with self.assertRaisesRegex(ValueError, "duplicate ZIP entries"):
                payloads(original)


if __name__ == "__main__":
    unittest.main()
