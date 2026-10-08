# Local AI Workspace 0.5.0 — delivery and validation

## A–C. Git baseline and changes

Started on `feat/0.5.0-skills-agents`, clean, at
`de615b772cf37a99e7e08a647c59607b271de606`.
Annotated tag `v0.4.2` peels to that commit. `main` and the tag remain unchanged.
No `v0.5.0` tag, merge, history rewrite or force push is part of this delivery.
Version is 0.5.0 / 27; application IDs and runtime dependencies are preserved.

Logical commits separate retrieval, schema export, domains/routing, prompt, context,
UI, tests and validation cleanup; release metadata/documentation form the final commit.
The final Git log and diff stat are recorded in the local delivery artifact
`dist/apk/local-ai-workspace-0.5.0-git-changes.txt` (excluded from Git).
The files in that comparison are the authoritative production/test/documentation list.
A grouped manifest is versioned at `validation/skills050/changed-files.json`.
The implementation commits are local on the feature branch; this task does not claim
a GitHub push. An optional delivery Git bundle preserves the exact local commits.
No authentication secret is embedded in source or artifacts.

## D–F. Retrieval prelude — IMPLEMENTED / TESTED_HOST

The actual `AppGraph.retrieval` is `SemanticRetrievalService`, not the legacy hash
retriever. Changes cover that production service and `DocumentDao`:

- Embed the complete stored passage; return the complete excerpt and corresponding
  character offsets. Old partial document vectors are lazily replaced using cache
  record version 2. Memory vectors and Semantic V2 spaces are not erased.
- EG1 really limits native input to 512 tokens. An explicit TOKEN_LIMIT uses
  contiguous Unicode-safe UTF-8 windows, length-weighted centroid and normalization.
  This is not represented as one native full-document call. Cancellation is checked
  between windows. Native JNI and model configuration are unchanged.
- Query selected document IDs in SQL before applying the candidate bound. All bounded
  candidate queries have deterministic document/index/id ordering. Lexical queries
  are scoped before their own bound and can recover matches outside the semantic cap.
- Remove arbitrary selected-document fallback. Zero relevant evidence is valid;
  chat reports that no passage was found and can use existing enabled file tools.

Tests cover complete/late passage coverage, stale cache invalidation, >1,000 segments,
selected late documents, cross-project isolation, no-match, cancellation and Unicode
windows. Four old file-chat fixtures now use genuine lexical matches instead of
depending on the removed arbitrary fallback. No tests were deleted.

## G–I. Skills, logical Agents and routing — IMPLEMENTED / TESTED_HOST

`skills/`, `agents/`, `capabilities/` and `sources/` contain focused domains and
registries. Four versioned built-ins have stable IDs: General Writing, Document
Analysis, Code Assistant and Spreadsheet Analysis. General Agent is always available.
Custom Agents are configurations over the existing physical Gemma/runtime pool.

Installed, enabled, eligible, active and executed are separate trace fields. Origin
is independent from enabled. Inactive bodies enter neither the prompt nor its budget.
EXECUTED requires a recorded successful associated tool for the assistant turn; it
does not assert that the Skill caused the call.

Agent precedence is explicit enabled choice → project preference → General. A
deleted/disabled Agent safely falls back.
General offers all installed Skills without forcing activation; custom Agents retain
explicit assignments. Configured custom keyword routing requires a concrete task
intent; imported procedures default to explicit-only. Skill routing is pure deterministic metadata:
explicit choice, strong task keywords, task intent, MIME/extensions and availability.
Greetings and ambiguous requests abstain. Sources are allowances, not an obligation
to use data for instruction-only tasks. No EG2 or Gemma routing call is introduced.

SKILL.md import accepts a bounded UTF-8 frontmatter/Markdown subset; export preserves
supported metadata and instructions. Unknown resource/script metadata is inert.
Scripts, hooks, binaries and imported commands are never executed. User edits survive
registry initialization. Built-ins can be disabled but cannot be deleted or overwritten.

117 EN/ES exact-outcome routing fixtures run on JVM. Their metadata-only report is
`validation/skills050/routing-summary.json`. This is fixture coverage, not a claim of
universal natural-language routing accuracy.

## J–K. Context, Tools and Sources — IMPLEMENTED / TESTED_HOST

Text LiteRT chat defaults to Context Builder when no setting is stored. Explicitly
stored OFF remains OFF; legacy/multimodal/GGUF paths are retained. The selected Agent,
active Skill instructions, project instructions and allowed memory scopes are passed
through the existing ContextRequest. Existing tool selection stays separate.

Order of request preparation is routing → retrieval/memory → tool availability →
context → generation gate → Gemma. Both V1 and legacy paths prepare embeddings before
the non-reentrant gate. A fake-runtime/controller regression observes this ordering.

Context keeps core policy/query, then Agent and complete active Skill bodies. Overflow
never silently truncates instructions: oversized Skills are omitted with
SKILL_CONTEXT_BUDGET; Agent/project overflow is explicit. Diagnostics identify
ESTIMATED_UTF8_BYTES_V1 and per-block costs. Sources preserve provenance and filenames
in model context; safe metadata exports omit source names and all private content.

The native system policy now requests task-appropriate detail and says local device,
without a universal sentence cap or CPU-only claim. Sampling, thinking configuration,
KV rules, warm-up, embedding dimensions and runtime libraries are unchanged.

Existing `AssistantRouting`, native tool protocol and `ToolRegistry` remain in use.
App/user/Agent/active-Skill availability filters tool exposure, and execution rechecks
capabilities. PERMISSIVE_PERSONAL keeps local read/compute without new confirmations.
No new executable tools or connectors are added. Future Sources report UNAVAILABLE.

## L. Databases and rollback — IMPLEMENTED / TESTED_HOST

Independent `agents_skills.db` v1 stores Skills, Agents and project preferences.
`workspace.db` v8, `semantic_v2.db` v1 and `memory_context.db` v1 have unchanged schemas
and versions. All four current Room schemas are exported/versioned. No destructive
migration or reset is configured. Existing projects default to General with no new
workspace column. Sidecar seeding is serialized and does not overwrite user choices.

Host tests reopen existing workspace data after sidecar creation and compare its
identity hash, project, chat/message, model registration and legacy vector. These are
host persistence checks; an upgrade of a real user's populated device is still a
device test. Returning to v0.4.2 data is preserved by additive storage, but Android's
versionCode downgrade policy may require a separate controlled installation procedure.

## M–P. Host/build/lint/Android evidence

Final gate: **661 app + 2 compatibility tests executed, 0 failures/errors/skips**.
Debug, release, lint and instrumented APK builds PASS. Lint: 0 errors, 40 existing
warnings and 1 information. Final gate duration: 8m 47s.

The final command (with local Java/SDK environment configured) was:

```bash
./gradlew -Parm64Only=true --no-daemon --no-parallel --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.daemon.jvmargs=-Xmx2048m \
  -I docs/validation/skills050/force-test-execution.gradle \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:lintDebug \
  :app:assembleDebugAndroidTest --offline --console=plain
```

All six tasks ran together with ARM64, offline, no-daemon/no-parallel/max-workers=1,
Gradle and Kotlin heaps capped at 2 GiB. The small force-test init script under
`validation/skills050/` prevents Test actions being satisfied only by cache.
An earlier parallel retry lost its Gradle daemon; the host recorded an OOM-kill event.
This is not evidence about Android crashes. Sequential retries completed successfully
without changing runtime settings or permanent Gradle memory configuration.

Final counts, commands, lint severities and artifact metadata are recorded in
`validation/skills050/test-summary.json`. Builds do not imply native device execution.
Android navigation tests are built for instrumented execution, not claimed executed
on this host. Existing warnings are documented; unrelated warning cleanup is deferred.

Installable ARM64 artifact: `dist/apk/local-ai-workspace-0.5.0-skills-agents-arm64.apk`.
Application ID com.localai.workspace, version 0.5.0 / 27, verified signature with
the same certificate as the 0.4.2 artifact (Android Debug certificate). Exact size and
SHA-256 are in `validation/skills050/apk-verification.json`. No APK/key is tracked.
Seven of nine native shared libraries are byte-identical to the baseline APK. The
rebuilt ggml-base/llama-common files differ; their extracted strings change only
the generated Git build identifiers (99b9548[-dirty] → 5ce6c88). Native/vendor source
and LiteRT dependencies have no diff. This is not a claim of complete APK-library
byte identity or device revalidation.

## Q. Physical evidence — NOT_TESTED / PHYSICAL_DEVICE_TEST_REQUIRED

No Android device is attached to this environment. Historical/Semantic V2 catalogs
and their expectations were not changed. No historical, Semantic V2, benchmark or
native performance run is reported as a physical PASS by this implementation.

New scoped suite: Settings → Developer / Diagnostics → Agents / Skills Inspector &
Validation → Run AGENTS_SKILLS_V1. Its twelve checks use isolated synthetic databases
and the owner's shared physical runtime. Four checks use native chat/stop/context
preparation. Missing infrastructure is BLOCKED; native/assertion errors and timeouts
are FAIL. Cancellation produces CANCELLED, not PASS. Its report is persisted and
can be copied from the same panel. Startup failures and cancellation release the
validation reservation; cancel is not reported as PASS. Targeted retrieval/context validation on Motorola
remains required.

## R–S. Limitations and deferred items

- Deterministic V1 can abstain on unrecognized wording; explicit Skill selection is
  available. No semantic/LLM routing quality claim is made.
- Import is one SKILL.md file, not a directory/ZIP resource installer. Frontmatter is
  a subset, not a full YAML engine. Referenced assets remain metadata.
- Context estimates remain conservative UTF-8 byte counts. Full evidence may be
  excluded with a visible budget notice. No exact tokenizer or summarization is added.
- Manual Agent changes in the same chat retain its allowed conversation history.
  Direct memory retrieval is scoped, but previously shared conversation text is not
  retroactively redacted. Separate chats provide separate conversational histories.
- Semantic candidates remain bounded; no ANN or vector-store consolidation is added.
  Adaptive full-passage EG1 representation quality/latency needs physical evaluation.
- No autonomous loops, handoffs, MCP, real cloud connectors, workflows, marketplace,
  executable imports, plugins or second physical LLM.
- Python io.open can bypass the existing builtins.open wrapper; isolation still rests
  on WebView/WebWorker/MEMFS. This known debt is documented and not redesigned here.
- No unrelated native runtime, EG2-default, UI-wide, accessibility or security refactor.

## T. Motorola manual smoke — maximum four checks

First install the same-applicationId/same-certificate 0.5.0 artifact without uninstalling
0.4.2, then run AGENTS_SKILLS_V1 and copy its report. Manual checks:

1. New chat: "hola". Normal response; Diagnostics shows General and zero active Skills.
2. Select an XLSX and ask "Revisa este Excel y dime si los totales cuadran". Spreadsheet
   Analysis is active; inspect actual supplied sources or the no-relevant-passage notice.
3. Create/select a custom Agent with a recognizable harmless instruction, ask a simple
   prompt; inspect Agent resolution and response. No extra physical model is loaded.
4. Disable that Agent/Skill; retry in a NEW chat. General fallback or disabled Skill
   exclusion is shown and the custom behavior disappears.

The scoped suite includes stop/cancel and preparation-before-gate checks; no extensive
manual benchmark matrix is requested for this release.

## U–V. Exact final comparison

See the local delivery artifact
`dist/apk/local-ai-workspace-0.5.0-git-changes.txt` (excluded from Git) for `git diff --stat v0.4.2..HEAD` and
`git log --oneline --decorate v0.4.2..HEAD` captured at delivery. Runtime/vendor and
historical catalog file diffs are checked separately; source models, secrets, APKs
and build outputs remain outside Git.
