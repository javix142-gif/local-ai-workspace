# Instrumentation run 39511660788

- Tested SHA: `01161491acc2a7c836222a84b7bd6f778292f8f3`; job `118933907267`, source matches.
- Result: EMULATOR PASS for the included connected suite, API 35 / Android 15 / x86_64; `:app:connectedDebugAndroidTest` exit 0.
- XML: 22 cases, 16 passed, 0 failed, 0 errors, 6 skipped, 16 executed. The skips require real LiteRT/Gemma model files, which were not installed or downloaded.
- H1-AT-12: PASS. `adb install -r` updated `com.localai.workspace.debug` from source `9cfb0d8` to `0116149` with the same signer and preserved 20 synthetic memory rows, project, chat/message and a preference used by the next context request.
- The app is absent from the post-task package list because Android Gradle Plugin may uninstall tested APKs at task exit; the connected tests and acceptance checks recorded installation during the run.
- Room coverage here is authentic schema 8→current plus H1 memory schemas 8–10. It does not validate missing historical Room v1–7 schemas. `PHYSICAL_DEVICE=NOT_RUN`.
- GitHub artifact `12306559020`, 166,480 bytes, SHA-256 `60a4da7a524da48ab8f40c9b16c761f2c3e96858df20a362ed253fba27188bfe`; artifact ZIP and its internal 50-file manifest were verified. Raw logs, XML, screenshots and summary are retained in `github-artifact.zip`; `github-run-log.txt.gz` retains the complete GitHub job log.
