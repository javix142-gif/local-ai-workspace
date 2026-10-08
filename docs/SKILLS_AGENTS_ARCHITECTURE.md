# Skills and logical Agents — 0.5.0

## Concepts and ownership

Gemma remains the existing shared physical runtime, leased through `ChatRuntimePool`.
Agents are configurations; neither registry, router nor UI instantiates a model.

| Concept | Responsibility |
| --- | --- |
| Skill | Procedural instructions; metadata routing and optional resource metadata |
| Agent | Role, available Skills/Tools/Sources, memory scopes |
| Tool | Executable capability through the existing `ToolRegistry` / SDK tool contract |
| Source | Data origin: project documents, conversation, structured memory, Semantic V2 |
| Memory | Explicit approved persistent records in existing databases |
| Context | Temporary budgeted composition, with provenance, sent to Gemma |

Domain packages: `skills/`, `agents/`, `capabilities/`, `sources/`. `AppGraph` supplies
one shared set of registries. `agents_skills.db` is Room v1; existing workspace v8,
semantic_v2 v1 and memory_context v1 schemas are unchanged. All four current schemas
are exported under `app/schemas/`; no destructive migration is configured.

## Lifecycle and routing

- INSTALLED: present in registry.
- ENABLED: user configuration permits it; this does not imply activation or trust.
- ELIGIBLE: selected Agent and currently available required capabilities permit it.
  Source allowances constrain access; they do not force instruction-only tasks to read data.
- ACTIVE: explicit choice or a strong deterministic task signal selected its instructions.
- EXECUTED: a successful tool associated with the active Skill was actually recorded
  for this assistant message. It does not assert the Skill caused that call.

Agent resolution: enabled explicit selection → enabled project preference → General.
A missing/deleted/disabled custom Agent falls back. General cannot be deleted/disabled.
Skill enablement remains independent from built-in/user/imported origin.
General offers all installed Skills without forcing activation; custom Agents retain
explicit assignments. Configured custom keyword routing requires a concrete task
intent; imported procedures default to explicit-only.
Four stable built-ins: General Writing, Document Analysis, Code Assistant, Spreadsheet
Analysis. Built-ins can be disabled, inspected and exported; only custom/imported
Skills can be edited/deleted. User changes are never overwritten by seeding.

The router uses namespaced IDs, keywords, task verbs, MIME/extensions, explicit
selection and availability. Ordinary greetings and ambiguous requests select zero.
Code/table attachments suppress generic document analysis. Multiple clear intents can
select multiple Skills. No router uses Gemma or EmbeddingGemma. Positive/negative
examples and routing text prepare a future descriptor index; V1 does not embed them.

Skill selection is separate from existing `AssistantRouting.tools`. Tool exposure is
bounded by actual app schemas, user enabled tools, Agent allowance and active Skill
allowances. For multiple active tasks their allowed tools/sources form a union, then
intersect the Agent/app/user boundary; Skills never grant new app capabilities.
Tool execution still rechecks enabled IDs, typed arguments, capability availability,
existing timeouts, repeat/call limits and confirmation policy in `ToolRegistry`.
`PERMISSIVE_PERSONAL` permits current local read/compute without additional dialogs.
Network read requires explicit policy enablement and an actual app provider; no network
provider is introduced in this release.
Future irreversible delete, external send, payments and sensitive system actions
have confirmation semantics; no executable providers for those are introduced.

## Progressive disclosure and SKILL.md

L1 contains small routing metadata. Only ACTIVE L2 bodies enter system instructions;
inactive bodies cost zero. L3 references/assets metadata remains inert and is not
loaded automatically. Import is a bounded UTF-8 SKILL.md file (64 KiB), not a ZIP
package installer. Supported frontmatter includes name, description, version, tags,
and preserved unknown fields; unknown fields never execute. Imported instructions
are explicit-only until their routing is configured by the user. Scripts/hooks,
commands, native binaries and WASM are not executed by the importer/registry.
Duplicate imported IDs with different names are rejected; matching imports update
the same record/version. User-authored IDs survive renaming.

## Composition, scope and concurrency

`ChatViewModel` resolves Agent/Skills, retrieves data, filters Tools and builds context
BEFORE acquiring `inferenceGate`. No new discovery or embedding runs under that gate.
Supported text LiteRT chat defaults to Context Builder if the existing switch has no
stored value; an explicitly stored OFF remains OFF. The rollback/diagnostic and
multimodal/GGUF paths remain available, and prepare retrieval before the gate too.

Context order: core policy → Agent → ACTIVE Skills → project instructions; retrieved
memory/source/history records are framed as data, followed by the current query.
Memory scopes remain explicit; the selected Agent ID only permits that Agent's own
approved records, and its allowed scope types are checked by memory selection.
No automatic memory creation/extraction or project-to-Agent copying occurs.
Switching an Agent manually inside an existing chat keeps that chat's conversation
history when its CONVERSATION Source is allowed. Memory scopes filter direct record
retrieval; they do not redact already shared conversation text. Use separate chats
when separate conversational histories are required.

The query and policy are not silently truncated. Agent/project instruction overflow
is explicit; an oversized Skill body is omitted WHOLE with `SKILL_CONTEXT_BUDGET` and
its ACTIVE trace is corrected. Low-priority data/history is budgeted by existing
selection. `ESTIMATED_UTF8_BYTES_V1` identifies estimates, not tokenizer counts.
Reports expose SYSTEM/AGENT/SKILLS/PROJECT/TOOLS/MEMORY/SOURCES/HISTORY/QUERY costs;
TOOLS is the reserved tool/schema allowance, not an exact tokenizer measurement.
Native sampling/context/default embedding dimensions remain unchanged.

## Retrieval integrity prelude

The production `SemanticRetrievalService` now embeds full passages and supplies full
text with consistent offsets. Its old partial document vectors are invalidated lazily
using record version 2; existing vectors/indexes are not wiped. EG1 JNI really caps
input at 512 tokens. Only its explicit TOKEN_LIMIT triggers complete UTF-8 bounded
windows (384 bytes of source plus framing), length-weighted centroid and normalization.
No character is skipped; other native failures retain explicit lexical fallback.
These vectors are full-passage representations, not claims of a single native call.

Selected document SQL scopes before the 1,000-candidate limit. Bounded project and
selected-document queries have stable document/index/id ordering; scoped lexical
search covers matching passages beyond the semantic candidate bound. No ANN is added.
An unmatched selected document produces zero evidence; the chat can proceed with a
notice and existing useful file tools. Full evidence can still be excluded by a small
context budget; that is reported instead of silently cutting a citation passage.

## Management and observability

Settings: Skills / Logical Agents. Project Settings: Preferred Agent. Chat overflow:
Agent / Skills (explicit selections apply to the next prepared turn). The header has a
passive compact Agent label; chat Diagnostics shows routing reasons and tool availability.
Developer / Diagnostics: Agents / Skills Inspector & Validation. Safe metadata exports
exclude query text, roles, Skill bodies, memory text and tool arguments/results.
Explicit Skill export writes the chosen procedure, not unrelated private context.

`AGENTS_SKILLS_V1` is a separate 12-case in-app suite using isolated fixture databases
and the existing shared model. Four cases use actual native chat/stop/context preparation;
missing models are BLOCKED, assertion/native failures are FAIL, cancellation is preserved.
It does not change the historical or Semantic V2 catalogs. The suite is NOT DEVICE
TESTED until explicitly run on Motorola. 117 deterministic JVM fixtures cover routing.

## Decisions

| ADR | Decision |
| --- | --- |
| 001 | One physical Gemma, N logical Agents |
| 002 | Skill != Tool != Source != Memory |
| 003 | SKILL.md external interoperability |
| 004 | Progressive disclosure L1/L2/L3 |
| 005 | Routing V1 deterministic |
| 006 | Semantic routing deferred |
| 007 | LLM routing deferred until useful benchmark evidence |
| 008 | Zero Skills is valid |
| 009 | Skill and Tool selection are separate |
| 010 | Imported Skill code is not executable in 0.5.0 |
| 011 | PERMISSIVE_PERSONAL capability profile |
| 012 | Additive sidecar persistence |
| 013 | Context Builder is the canonical composition direction |

## Deferred / technical debt

Semantic/LLM routing, second router model, Agent handoffs/loops, MCP, connectors,
plugins, marketplace, workflows/automations and executable Skill packages are deferred.
No tokenizer, ANN, vector consolidation or unrelated runtime/UI refactor is included.
Python `policy.py` wraps builtins.open but `io.open` can bypass that wrapper. Actual
isolation rests on WebView/WebWorker/MEMFS; this existing debt is not redesigned here.
Physical regression of changed retrieval/context and resource behavior is still required.
