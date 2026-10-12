# Environment setup attempts (not test results)

These setup attempts are kept separate from the UX-02e test counts.

1. At `d04b6b1aae77b397bddd96dd44c1baf53f6b9fc1`, the first focused Gradle invocation stopped at project configuration because no `ANDROID_HOME`/`local.properties` SDK location existed. Raw log: `sdk-missing-at-first-attempt.log.gz`.
2. A temporary Android SDK was created outside the repository with API 35 and Build Tools 35.0.0. The next focused Gradle attempt stopped because NDK 27.0.12077973 was not installed. Raw log: `gradle-attempt-missing-ndk.log.gz`.
3. The official SDK manager attempt to install the project's exact NDK version reported `No space left on device`; installation was incomplete. No fake NDK metadata or alternate version was used.
4. The final proportional gate at code SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7` again stopped before tasks. Its raw log is `../host/gradle-proportional-gate.log.gz`; AGP identified the incomplete NDK by the missing `source.properties`.

No item in this directory is a passing Gradle, Compose, lint, or AndroidTest result. The only executed test result is the separate 20/20 parser-only standalone JUnit run in `../host/parser-standalone.log.gz`.
