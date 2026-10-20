from __future__ import annotations

import base64
import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "build_qa_debug_arm64.py"
SPEC = importlib.util.spec_from_file_location("build_qa_debug_arm64", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PersistentSigningPreparationTest(unittest.TestCase):
    def test_required_but_missing_secrets_fails_without_creating_key_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            with patch.object(MODULE, "RUNNER_TEMP", Path(temp)), patch.dict(
                os.environ, {"QA_REQUIRE_PERSISTENT_SIGNING": "true"}, clear=True
            ):
                with self.assertRaisesRegex(MODULE.QaBuildError, "QA_PERSISTENT_SIGNING_SECRETS_REQUIRED"):
                    MODULE.prepare_persistent_signing()
            self.assertEqual([], list(Path(temp).iterdir()))

    def test_partial_secret_set_fails_with_missing_names_only(self) -> None:
        values = {
            "QA_KEYSTORE_B64": base64.b64encode(b"test-keystore").decode("ascii"),
            "QA_KEYSTORE_PASSWORD": "test-store-password",
        }
        with patch.dict(os.environ, values, clear=True):
            with self.assertRaisesRegex(MODULE.QaBuildError, "QA_SIGNING_SECRETS_INCOMPLETE:QA_KEY_ALIAS,QA_KEY_PASSWORD"):
                MODULE.prepare_persistent_signing()

    def test_complete_payload_is_private_and_outside_artifact_directory(self) -> None:
        values = {
            "QA_KEYSTORE_B64": base64.b64encode(b"\x30\x03test").decode("ascii"),
            "QA_KEYSTORE_PASSWORD": "test-store-password",
            "QA_KEY_ALIAS": "test-alias",
            "QA_KEY_PASSWORD": "test-key-password",
        }
        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            artifact = temp_path / "artifact"
            artifact.mkdir()
            with patch.object(MODULE, "RUNNER_TEMP", temp_path), patch.object(MODULE, "OUT", artifact), patch.dict(os.environ, values, clear=True):
                signing_dir = MODULE.prepare_persistent_signing()
                self.assertIsNotNone(signing_dir)
                keystore = Path(os.environ["QA_KEYSTORE_FILE"])
                self.assertEqual(b"\x30\x03test", keystore.read_bytes())
                self.assertEqual("PKCS12", os.environ["QA_KEYSTORE_TYPE"])
                self.assertEqual(0o600, keystore.stat().st_mode & 0o777)
                self.assertEqual(0o700, keystore.parent.stat().st_mode & 0o777)
                self.assertFalse(keystore.is_relative_to(artifact))
                self.assertNotIn(keystore, artifact.rglob("*"))

    def test_keytool_fingerprint_parser_normalizes_colons(self) -> None:
        fingerprint = "AA:" * 31 + "BB"
        self.assertEqual("aa" * 31 + "bb", MODULE.certificate_sha256_from_keytool(f"SHA256: {fingerprint}"))
        self.assertIsNone(MODULE.certificate_sha256_from_keytool("no certificate fingerprint"))

    def test_unrecognized_keystore_format_is_rejected(self) -> None:
        values = {
            "QA_KEYSTORE_B64": base64.b64encode(b"not-a-keystore").decode("ascii"),
            "QA_KEYSTORE_PASSWORD": "store-test-password",
            "QA_KEY_ALIAS": "test-alias",
            "QA_KEY_PASSWORD": "key-test-password",
        }
        with tempfile.TemporaryDirectory() as temp, patch.object(MODULE, "RUNNER_TEMP", Path(temp)), patch.dict(os.environ, values, clear=True):
            with self.assertRaisesRegex(MODULE.QaBuildError, "QA_KEYSTORE_FORMAT_UNRECOGNIZED"):
                MODULE.prepare_persistent_signing()

    def test_log_sanitizer_redacts_all_signing_secret_values(self) -> None:
        values = {
            "QA_KEYSTORE_B64": "base64-test-secret",
            "QA_KEYSTORE_PASSWORD": "store-test-secret",
            "QA_KEY_ALIAS": "alias-test-secret",
            "QA_KEY_PASSWORD": "key-test-secret",
        }
        with patch.dict(os.environ, values, clear=True):
            sanitized = MODULE.sanitize(" ".join(values.values()))
        for secret in values.values():
            self.assertNotIn(secret, sanitized)


if __name__ == "__main__":
    unittest.main()
