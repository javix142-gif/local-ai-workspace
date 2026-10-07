# Storage cleanup — 2026-10-05

Later0.1.8 build follow-up: compilation regenerated the required build/cache files. Gradle's release packaging cleans its output directory and removed the old local0.1.6/0.1.7 distribution APKs; their original Drive copies remain. Local re-download returned HTTP403. The signed0.1.8 APK now lives in `dist/apk` to survive future packaging. This note does not change the cleanup snapshot/measurements below. [Follow-up evidence](CHAT_DELETION_018.md).

User authorized removing regenerable/unneeded files while preserving development progress. This operation changes cloud storage only. Production source, Room schema, SDK/toolchains, recorded validation, signed releases and the original Qwen3.5 model are retained.

## Measured outcome

| Measurement | Before preparation | After cleanup/verification |
| --- | --- | --- |
| Project folder | 5.24 GB | 256.9 MB |
| Workspace including tools/model/backup | 20.44 GB | 6.45 GB |
| Free filesystem storage | 5.71 GB | 19.75 GB |

Net disk recovery: 14.03 GB. Units are decimal bytes; apparent directory sizes and physically free disk differ. Initial figures were measured before copying reports/creating the backup. Final figures include preservation overhead. Small subsequent report files may change exact byte totals.

## Preserved and verified

- 2226 pre-existing source/documentation/evidence files verified against SHA-256/link targets after cleanup.
- Original signed0.1.6 and0.1.7 APKs kept at their existing paths under app/build/outputs/apk/release. The0.1.7 APK remains 91,355,065 bytes, SHA-256 c4d539fb6e15293a320e52673d622281c6c6ada2ebcd63bfc34ac2886e28703a. Drive delivery is unchanged.
- Original Qwen3.5-2B_int8.litertlm retained outside the project and verified against SHA-256 8ae5027136e0b89039ed76eedfb61f0cf4b7277024d99bf765359264295b86b1.
- Unit/lint reports and test results copied from module build folders to docs/validation/storage-cleanup-2026-10-05/build-evidence before deletion. Release native debug symbols and build logs were copied too. All prior docs/validation evidence remains intact.
- SDK, NDK, CMake, JDKs, Gradle and native validation environments retained. No home-directory cache, credential/signing-key location, user attachment, phone file or Drive object is deleted.
- Source backup: /workspace/backups/local-ai-workspace-source-2026-10-05.tar.gz, 42.30 MB, SHA-256 4d91735d6b92c7ed34f1ad380d2b7bc4d9485b6d3b1389af3de843e00d6fd37e. Every protected member was verified in a single sequential read before cleaning. The backup includes source, vendored dependency/git metadata, documentation and copied evidence; generated build folders and APKs are excluded. APKs remain separately at their original paths/Drive.

## Removed

- App generated intermediates/debug/instrumented/unsigned APKs, preserving both signed releases.
- litert-compat/build, llama-runtime/build, llama-runtime/.cxx and local project Gradle/Kotlin caches.
- Three regenerable XNNPACK CPU cache directories used by host reference tests.
- Discarded Qwen3-1.7B LiteRT and Qwen2.5-1.5B GGUF reference weights, after checking their known authoritative hashes. Their pinned sources, hashes, scripts, literal outputs and negative results remain archived.
- Detached stock llama.cpp reference worktree, after checking it had no changes, removed through git worktree remove without force; its host compilation outputs were removed. Vendored app llama.cpp and the cached parent repository are retained.

No new application build/tests are run for this storage-only operation, which would recreate the removed intermediates/caches. This does not supersede the preserved99-test/debug/release/lint results or the known semantic quality failures. Subsequent builds/native tests regenerate their intermediates and caches and may take longer on the first run. The two discarded models can be re-downloaded using pinned provenance; the host reference worktree/binaries must be re-created to run that comparison again.

Full retained-file hashes, approved targets, before/after measurements and verification status are recorded in [cleanup.json](validation/storage-cleanup-2026-10-05/cleanup.json). The scoped [cleanup script](validation/storage-cleanup-2026-10-05/cleanup-script.py) is retained as an audit trail, not a general-purpose deletion command.
