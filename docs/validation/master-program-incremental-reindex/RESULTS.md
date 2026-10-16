# Preparatory M02-06 — stale-index reimport lifecycle

This is preparatory host evidence for the original **M02-06 “Reindex incremental sin evidencia stale”** DoD. The source task remains `TODO`: it depends on M02-05, which is still formally `TODO`. No task ID, status, ordering, dependency, roadmap, or backlog source was changed.

## Change and behavior covered

Code/test commit: `1433537fd1d46fb5d39622f63a02682f0c55a153` (parent `535468c166116cf0625f75f8006c53590f7bfc16`). The only product-tree path changed is `SemanticContextPostfixTest.kt`; no production source, Room schema, dependency, Gradle configuration, workflow, or runtime changed.

The new synthetic integration regression exercises actual `DocumentIngestionService` and `WorkspaceRepository` operations with the existing Semantic V2 implementation and a controlled embedding driver. It imports and indexes content in two projects, deletes the edited document from one project, reimports changed content, then rebuilds that project's index. Assertions verify that:

- deletion marks the old active generation `NEEDS_REINDEX` and semantic search rejects it;
- the prior generation remains as stale history, while a new `READY` generation is published after reindex;
- context uses the current lexical fallback during the stale interval and shows `REINDEX_REQUIRED`;
- the changed document and retained same-project document remain available, while deleted text and another project's content do not enter that context;
- the other project's active generation and search results remain unchanged.

All documents are synthetic text; this does not validate the user's private workbook or physical-device behavior.

## Exact-source host execution

Final executed command at the exact code SHA:

```text
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --rerun-tasks -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17-temurin,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.semantic.v2.SemanticContextPostfixTest.editDeleteReimportKeepsProjectGenerationsScopedAndNeverServesStaleHits
```

Result: exit `0`, Gradle `BUILD SUCCESSFUL`, 63 tasks executed, wall time 3m33s. The generated JUnit XML reports 1 test, 0 failures, 0 errors, 0 skipped; the test body took 8.944s. Gradle ran on Temurin 17.0.20.1, with Temurin 21.0.12.1 as the test worker, Gradle 8.10.2, Android API 35 and NDK 27.0.12077973. Existing toolchains/caches were used offline; nothing was installed or downloaded.

The four attempt directories preserve the sequence. Attempt 01 found no tests because the newly written Kotlin `@Test` method returned `Boolean`; JUnit therefore did not execute its body. The test was corrected to return `Unit`. Attempt 02 then passed 1/1 on the exact same test diff before it was committed. Attempt 03 used the committed SHA but Gradle marked the test `UP-TO-DATE`, so it is not counted as an execution. Attempt 04 used `--rerun-tasks` and is the exact-SHA result counted above.

The full app/LiteRT host suites, lint, APK build, AndroidTest compilation, emulator and Motorola were not run for this test-only increment. The known global emulator status remains a separate historical failure: Room v1 fixture unavailable and six actual-model tests skipped. Physical validation remains `NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`.

## Source-plan integrity

The preserved source backlog still has 97 unique IDs and 120 dependency edges, with no missing dependency and all 97 statuses `TODO`. Its SHA-256 remains `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`; the roadmap SHA-256 remains `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`.

The observed lifecycle provides focused host evidence for the invalidation/no-stale-results portion of M02-06. It does not close the original task or its dependency chain. The next preparatory review is the existing M03-01 Artifact API recovery test seam; no other source task is declared complete or selected by changing the backlog.
