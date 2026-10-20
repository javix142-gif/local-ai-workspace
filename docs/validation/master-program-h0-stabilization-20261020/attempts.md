# Attempts retained for H0 host stabilization

- Initial app JVM suite: five failures; raw log /tmp/aws-h0-jvm-e83322d.log. A later full run had three failures (one asynchronous document-cleanup race and two Robolectric platform-dialog idler failures); raw log /tmp/aws-h0-final-jvm-e83322d.log. The final exact-tree rerun is represented in logs/full-jvm.log.gz and passed 918/918 across app and compat.
- Focused DocumentRemoval initially failed because cleanup was asynchronous; the focused final run and combined Workspace/Removal run passed. Logs remain at /tmp/aws-h0-document-removal-race-e83322d.log and /tmp/aws-h0-workspace-removal-final-e83322d.log.
- AndroidTest compile offline stopped on missing uncached artifacts. The online attempt without Java proxy timed out before Kotlin compilation. The final system-truststore run completed successfully.
- Lint without the ARM64 property timed out while CMake configured another ABI. ARM64-scoped lint completed successfully.
- ARM64 assembly offline failed on absent listenablefuture-1.0.jar; the attempt without Java proxy failed with Network is unreachable; the proxy attempt using the Temurin truststore failed certificate validation. The final attempt used the already-installed system truststore and succeeded without disabling TLS checks.
- All failed Gradle logs available at evidence creation are preserved losslessly compressed under logs/. Earlier raw logs outside this bundle remain in executor /tmp; they are not represented as committed evidence files.
