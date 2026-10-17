# Automatic CI follow-up for the PPTX image increment

Source code SHA: `6231732ad8557465abfc8155925fdcbc90d1eb87` (published evidence HEAD at workflow checkout: `06bd56e84a4a8dab2a907d136245e68761d75430`). The workflow used the documentation-only descendant for both jobs; no product files changed after the tested code SHA.

## Emulator

- Run `38988880055`: [GitHub Actions run](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38988880055); job `117266559214`; workflow `.github/workflows/android-emulator-instrumented.yml`.
- Result: **EMULATOR FAIL after real instrumentation**. API 35 / Android 15, x86_64 `sdk_gphone64_x86_64`; ADB connected. The command `./gradlew :app:connectedDebugAndroidTest --no-daemon --no-parallel --max-workers=2 --stacktrace --console=plain` exited 1.
- JUnit XML: 16 total, 9 passed, 1 failed, 0 errors, 6 skipped, 10 executed. The failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`: `WorkspaceDatabase/1.json` is unavailable. The schema was not fabricated and the migration test was not weakened. The six skipped cases are the existing real-model inference checks; no models were downloaded.
- The collector's post-task `applicationApkInstalled=false` acceptance check also failed. The XML proves the connected test task ran; this package check is retained as a separate diagnostic, with its cause **NOT_DETERMINED**. Gradle progress text later reported `22/16`; counts here use the JUnit XML, which is internally consistent.
- Physical Motorola: `NOT_RUN`.

## Official QA gate

- Run `38988880049`: [GitHub Actions run](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38988880049); job `117266559052`, same source SHA.
- Result: **FAIL_CLOSED**. The gate reported `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL` for matching emulator run `38988880055`; APK build and artifact upload steps were skipped. The run has no downloadable APK artifact.

## Preserved evidence

The exact 151,505-byte emulator artifact ZIP from GitHub artifact `12084179250` is preserved as `github-artifact-12084179250.zip`; its API digest and local SHA-256 are both `cec74c1ae48df16f49e5910b4e882d39653a3dc80a43682f5956b173d2a14013`. The ZIP's internal `SHA256SUMS` verifies all payloads, including the JUnit XML and Gradle log. The decoded QA job log is preserved losslessly as `qa-gate-job.log.gz`; compressed SHA-256 `7b972c786546fd05d40aab85502428d17a3432a42618cb77e09b832c8b9b3f93`, decompressed SHA-256 `6a84de1664b419d374ef49fcfc1f1d9d42016ae8be06a45a3b0b35fd650da24c`.

This run does not validate a physical device, does not pass the global Android gate, and does not close M03-05. Source task M03-05 remains `TODO`, requires a device, and depends on M03-01 (`TODO`).
