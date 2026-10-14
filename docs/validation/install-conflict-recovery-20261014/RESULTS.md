# Environment cleanup and personal APK update — 2026-10-14

The requested cleanup recovered **14,422,118,400 disk bytes** (14.42 GB decimal).
Twelve clean ancestral worktrees, generated primary build output, obsolete APKs,
an incomplete duplicate SDK, redundant JDK archives and generated model/native
caches were removed. See `cleanup.json` for the path inventory. The main source
checkout, Git history, versioned evidence, authentic 97-task plan, unique model
files, complete toolchains/dependency cache and retained local debug key remain.
No Google Drive user documents or phone data were cleaned.

## Installer conflict and the corrected artifact

The supplied screenshot reports a generic package conflict. There is no connected
ADB device, so the installed certificate and precise installer failure code are
unavailable. A certificate mismatch **between delivered APKs is proven**:

- Current run 38492777065 (app payload source
  `0844159b1c8653c15da6a2f47c098e46d38478ba`) uses certificate
  `39e1d90e77864dfb824bfe716cffe3e9574441e7044db108762e8ed85923f455`.
- Local-signed UX-01/UX-02a deliveries and the retained debug key use
  `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`.

Android requires compatible signing certificates to update the same application
ID. The mismatch is consistent with the screenshot, but its device-specific cause
has not been independently established. A different installed signing certificate
would require its corresponding original key; this artifact cannot bypass that.

Tooling commit: `065ae36930d6f3dc7e72f039067c3065c9b057fd`.
`scripts/ci/prepare_personal_qa_update.py` verifies the original APK digest and
signature, requires a pre-existing matching Android Debug key, compresses only the
22 DEX ZIP entries, aligns native libraries at 16 KiB and other entries at 4 bytes,
then signs with the retained key. No key was generated/exported. All **284**
uncompressed payloads, including manifest, DEX, native libraries and resources,
match the source byte-for-byte. No app source or workflow was changed.

Corrected standalone APK:

- `local-ai-workspace-0.5.0-update-arm64.apk`, **65,566,569 bytes**.
- SHA-256: `566915521ead68c01925f88081d2df994e29685e87fe9a02a68d69c7223f23f1`.
- `com.localai.workspace.debug`, `0.5.0-debug` / versionCode 27, ARM64 only.
- Certificate: retained local key above; signature v2/v3 verified.
- Artifact type: `PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`.

## Checks and delivery

HOST: Python packaging regressions **2/2 PASS**; real artifact CRC, 284 payload
digests, APK signature, 4-byte/16-KiB alignment, package/version/ABI and model-payload
checks **PASS**. Commands and exit codes: `command.txt`, `packaging-tests.log`,
`apk-validation.log`, `model-payload-check.log`, `payload-preservation.json`.
No Gradle build/JVM Android suite was invoked: app payloads remain identical.
EMULATOR / PHYSICAL_DEVICE: **NOT_RUN**. No install, launch or inference claim.

Drive upload completed and metadata readback confirms the standalone APK MIME,
folder and 65,566,569-byte size:
https://drive.google.com/file/d/1V2evRNicMs66afgN0Lb3MhDCXP08v_Ro/view?usp=drivesdk

The local delivered APK hash was computed; Drive bytes were not downloaded for an
independent second hash. Existing historical Android Room/model-skip and UX-02e
statuses remain unchanged. M00-04 was not rerun after cleanup, and its prior
environment-blocked result is retained. M01-06 and the original plan remain pending.

Install over the previous local-signed Debug app to retain its data. Do not
uninstall or clear data to work around a remaining mismatch; first identify the
installed APK's actual signing certificate.
