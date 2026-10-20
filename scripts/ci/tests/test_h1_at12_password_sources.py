from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "run_h1_at12_upgrade.py"
SPEC = importlib.util.spec_from_file_location("run_h1_at12_upgrade", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class H1At12PasswordSourceTest(unittest.TestCase):
    def test_keystore_and_key_passwords_use_distinct_private_sources(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            keystore = MODULE.write_password_source(root / "keystore-password.txt", "synthetic-test-secret")
            key = MODULE.write_password_source(root / "key-password.txt", "synthetic-test-secret")

            self.assertNotEqual(keystore, key)
            self.assertEqual("synthetic-test-secret\n", keystore.read_text(encoding="utf-8"))
            self.assertEqual("synthetic-test-secret\n", key.read_text(encoding="utf-8"))
            self.assertEqual(0o600, keystore.stat().st_mode & 0o777)
            self.assertEqual(0o600, key.stat().st_mode & 0o777)

    def test_aapt_parser_accepts_android_test_apk_blank_version_fields(self) -> None:
        output = "package: name='com.localai.workspace.debug.test' versionCode='' versionName='' platformBuildVersionCode='35'\n"
        self.assertEqual(
            ("com.localai.workspace.debug.test", "", ""),
            MODULE.parse_package_badging(output),
        )

    def test_aapt_parser_preserves_application_version_fields(self) -> None:
        output = "package: name='com.localai.workspace.debug' versionCode='27' versionName='0.5.0-debug' platformBuildVersionCode='35'\n"
        self.assertEqual(
            ("com.localai.workspace.debug", "27", "0.5.0-debug"),
            MODULE.parse_package_badging(output),
        )

    def test_aapt_parser_still_rejects_missing_package_metadata(self) -> None:
        with self.assertRaisesRegex(MODULE.Failure, "APK_PACKAGE_METADATA_NOT_FOUND"):
            MODULE.parse_package_badging("application: label='test'\n")


if __name__ == "__main__":
    unittest.main()
