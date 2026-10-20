# H1 corrective verification — 2026-10-20

- Feature checkout HEAD tested: `16287d649eabf063fb4482d302060f378b2ff35a`.
- Product source SHA: `01161491acc2a7c836222a84b7bd6f778292f8f3` (documentation-only commits between product source and checkout HEAD).
- Command: see `command.txt`; offline, existing `/workspace/.gradle`, JDK 17 Gradle runtime, JDK 21 test worker, Android SDK API 35 and NDK 27.0.12077973.
- Start/finish: `2026-10-20T23:34:19Z` / `2026-10-20T23:37:49Z`; duration 210 seconds; exit 0; `BUILD SUCCESSFUL`, 63/63 actionable tasks executed.
- Fresh JUnit XML only: `H1PreferenceContextTest` 24/24 and `H1MemoryApprovalUiTest` 1/1; 25 total, 25 passed, 0 failures, 0 errors, 0 skipped. XML SHA values are in `SHA256SUMS`.
- AT-04 and AT-17 cover 15% budget enforcement, pre-persistence rejection, pending records, explicit selection and confirmation before unpin approval, retained `originalPinned` provenance, intact query/chat, and the 1,000-record roundtrip. The implementation was already present at `1f44f88`; this run is verification, not a new product change.
- This focused run is not a full HOST gate. Prior full HOST, connected emulator and QA evidence are summarized in `../../RESULTS.md`. `PHYSICAL_DEVICE=NOT_RUN`; the six model-backed tests remain SKIPPED; H1 stays device-pending as defined in the v1.2 contract.
