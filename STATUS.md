# Current source status — Local AI Workspace 0.4.2

- Android application baseline: versionName `0.4.2`, versionCode `26`, applicationId `com.localai.workspace`.
- Source and validation summary: [README](README.md), [architecture](docs/ARCHITECTURE_OVERVIEW.md), [0.4.2 implementation report](docs/MEMORY042_REPORT.md).
- Host verification recorded for this baseline: 511 app JVM tests + 2 LiteRT compatibility tests passed; debug/release builds, lint and instrumented APK packaging passed. Lint reported 40 warnings and 1 informational item, no errors. Instrumented tests were built, not run.
- The Motorola 0.4.1 chat/project checks were reported by the user. The 0.4.2 memory relevance/small-talk matrix is **not yet physically validated**.
- 0.4.2 selection policy includes a provisional semantic cosine floor; paraphrase recall and physical EG1/EG2 score calibration need device verification.

Generated APKs, models, build trees and caches stay outside Git. See [external artifacts](docs/EXTERNAL_ARTIFACTS.md).
