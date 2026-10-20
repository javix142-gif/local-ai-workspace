import unittest

from scripts.ci.collect_android_emulator_evidence import application_install_verification


class ApplicationInstallVerificationTest(unittest.TestCase):
    def test_connected_task_proves_install_even_when_gradle_removed_packages_afterward(self):
        result = application_install_verification(
            False,
            gradle_exit_code=0,
            xml_count=1,
            executed_tests=10,
            adb_connected=True,
        )

        self.assertFalse(result["installedAfterTests"])
        self.assertTrue(result["verifiedByConnectedInstrumentation"])
        self.assertTrue(result["verifiedForRun"])

    def test_live_package_list_remains_valid_install_evidence(self):
        result = application_install_verification(
            True,
            gradle_exit_code=None,
            xml_count=0,
            executed_tests=0,
            adb_connected=True,
        )

        self.assertTrue(result["installedAfterTests"])
        self.assertFalse(result["verifiedByConnectedInstrumentation"])
        self.assertTrue(result["verifiedForRun"])

    def test_no_completed_instrumentation_or_live_package_fails_install_check(self):
        result = application_install_verification(
            False,
            gradle_exit_code=0,
            xml_count=0,
            executed_tests=0,
            adb_connected=True,
        )

        self.assertFalse(result["verifiedForRun"])

    def test_unconnected_device_cannot_prove_instrumentation_install(self):
        result = application_install_verification(
            False,
            gradle_exit_code=0,
            xml_count=1,
            executed_tests=10,
            adb_connected=False,
        )

        self.assertFalse(result["verifiedByConnectedInstrumentation"])
        self.assertFalse(result["verifiedForRun"])


if __name__ == "__main__":
    unittest.main()
