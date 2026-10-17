# Existing exact-SHA emulator run review

This review is a baseline record, not a test of the recovery change. It inspects run `38992803542`, whose source SHA is the parent `77f86c0756193184d269b98701753beac55c54f7`.

- Run: https://github.com/javix142-gif/local-ai-workspace/actions/runs/38992803542
- Workflow job: `117278654938`, API 35 / Android 15 / x86_64; AVD boot, ADB, connected test task, and evidence upload succeeded as steps; the Gradle task and collector concluded failure.
- Downloaded original artifact ID `12086157197`: https://github.com/javix142-gif/local-ai-workspace/actions/runs/38992803542/artifacts/12086157197
- Artifact ZIP: 152,249 bytes; locally calculated SHA-256 `ab8e1e02dca5aa947eed8bae0c2da097cc6f38753ca1c737aa45e7aaa6842e3a`.
- ZIP CRC check passed; all 32 entries in its internal SHA256SUMS matched the ZIP members (32/32).
- The JUnit XML reports 16 tests: 8 passed, 2 failed, 0 errors, 6 skipped; 10 executed. Gradle exit code was 1.
- Failure 1: `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` cannot load the authentic `WorkspaceDatabase/1.json`. No schema/test workaround is applied here.
- Failure 2: `AgentsSkillsNavigationTest.generalAgentIsProtectedAndValidationIsDeveloperOnly` cannot find the visible `General Agent` text required by `performScrollTo()`. This is recorded as an observed failure only; this change does not touch that test or agent-navigation UI and no root-cause claim is made.
- The six skips are model-dependent: two `LiteRtLmDeviceSmokeTest` methods and four `NormalChatNativeDeviceTest` methods. This workflow downloaded no models.
- Same-SHA official QA run `38992803632` failed at the exact-SHA instrumentation wait. Its job `117278655187` skipped APK build and upload; the Actions API reports no artifacts.
- Physical device: NOT_RUN.

This base run predates code commit `b48338c557a94351157de18378a5221d00a4ba43`; it does not validate or invalidate that code. Run results are not called emulator PASS.
