# Automatic workflow runs after UX-01 publication

Both existing workflows were triggered automatically by the push of commit
`11f5ae4fe6596b63ebac8e56f8762420f6e5eb50`. That commit contains UX-01 code
from `4f6ea6faf3d541d366af8ce7284a32fa58f8c1e8` plus its first evidence
commit. No workflow was manually dispatched.

## Android emulator instrumentation

- [Run 38206516624](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38206516624), job `114671083972`, concluded **cancelled**.
- The runner checked out the exact event SHA and set up JDK 17. SDK installation was interrupted while downloading the API 35 x86_64 system image (the job log reached 38%); the log records `The operation was canceled` at `2026-10-12T05:07:06Z`.
- KVM setup, AVD creation/boot and `:app:connectedDebugAndroidTest` were skipped. The collector reports no connected ADB device, zero XML files, and zero tests executed. `getprop`, installation and instrumentation therefore did not run. This is **EMULATOR: NOT_RUN**, not a test failure/pass for UX-01.
- The run's evidence artifact is [11727751535](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38206516624/artifacts/11727751535), 21,744 bytes; GitHub API digest and locally downloaded bytes both have SHA-256 `a44b9ae36b73bd1f099df98ca8ac462a65be67fafb20db90d9cde13ff5d36583`. The ZIP CRC and all 9 entries in its internal SHA-256 manifest verified. The original ZIP and a verification record are retained in this directory.
- The artifact's `summary.json` records expected API 35/x86_64 only; it reports `device.connected=false`, `gradleExitCode=null`, `instrumentationXmlCount=0`, and total/passed/failed/errors/skipped/executed all zero.

## Fail-closed QA APK workflow

- [Run 38206516667](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38206516667), job `114671084004`, concluded **failure** at the prerequisite gate.
- The workflow waited for emulator run `38206516624` on the exact same SHA. Its log reports `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL` with the upstream conclusion `cancelled`.
- QA APK build and artifact upload were skipped; GitHub reports no artifacts for this QA run. This is the expected fail-closed gate outcome, not an APK build failure and not a UX test result. The separate PERSONAL_QA bundle already linked in the main UX-01 report was produced locally before this automatic workflow run.

## Evidence files

- `android-emulator-evidence-38206516624.zip` — original GitHub Actions evidence artifact.
- `emulator-job-log-excerpt.txt` and `qa-gate-job-log-excerpt.txt` — selected lines from the remote job logs; the run and job URLs above link to the full logs.
- `artifact-verification.txt` — local ZIP/internal-manifest verification.
- `SHA256SUMS` — SHA-256 for all files in this directory except the manifest itself.

`PHYSICAL_DEVICE` remains **NOT_RUN**. The earlier instrumented result on the older SHA remains historical and unchanged; it is not replaced by this zero-test attempt.
