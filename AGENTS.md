# Repository guidance

- Current development: Local AI Workspace 0.5.0, Android `versionCode` 27. Frozen baseline: `v0.4.2` / `de615b7`. Read `README.md` and `docs/ARCHITECTURE_OVERVIEW.md` before changing code; check `STATUS.md` for validation limits.
- Read `docs/SKILLS_AGENTS_ARCHITECTURE.md` for the Agent/Skill contracts. Keep deterministic discovery/retrieval before the generation gate; zero active Skills and zero evidence are valid.
- Keep changes on a dedicated branch. `main` and tag `v0.4.2` identify the frozen baseline; do not rewrite their history.
- Never add model weights, APK/AAB files, build output, caches, local SDK paths, credentials, signing keys or private user data. Review `git status` and the staged file list before committing.
- Preserve existing tests and small fixtures. Do not remove chats, projects, models, documents or user preferences. Room changes must be additive, include migrations/tests, and preserve upgrade data.
- Treat host tests, native probes and physical Android validation as distinct evidence. Never claim device validation unless the target device actually ran it.
- Run relevant tests/build/lint before merging functional changes, and record any device-only checks still required.
- Keep local model files outside the repository. Explicitly document new external artifacts and their provenance without copying weights into Git.
