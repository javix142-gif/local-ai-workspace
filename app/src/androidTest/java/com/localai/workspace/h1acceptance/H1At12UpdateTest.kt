package com.localai.workspace.h1acceptance

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.context.ContextRequest
import com.localai.workspace.context.MemoryContent
import com.localai.workspace.context.MemoryKind
import com.localai.workspace.context.MemoryRecord
import com.localai.workspace.context.ScopeAccess
import com.localai.workspace.data.ConversationEntity
import com.localai.workspace.data.MemoryItemEntity
import com.localai.workspace.data.MessageEntity
import com.localai.workspace.data.ProjectEntity
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val AT12_PREFS = "h1_at12_update_fixture"

@RunWith(AndroidJUnit4::class)
class H1At12SeedTest {
    @Test fun H1_AT_12_seedPreviousVersionData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = context.getSharedPreferences(AT12_PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean("seeded", false)) return@runBlocking

        val graph = (context.applicationContext as LocalAiApplication).graph
        val projectId = "h1-at12-project"
        val conversationId = "h1-at12-conversation"
        val now = System.currentTimeMillis()
        graph.database.projectDao().upsert(ProjectEntity(
            id = projectId, name = "H1 AT12 synthetic project", createdAt = now, updatedAt = now,
        ))
        graph.database.conversationDao().upsert(ConversationEntity(
            id = conversationId, projectId = projectId, title = "H1 AT12 synthetic chat",
            createdAt = now, updatedAt = now,
        ))
        graph.database.messageDao().insert(MessageEntity(
            id = "h1-at12-message", conversationId = conversationId, role = "USER",
            content = "H1_AT12_CHAT_MARKER_Ñ", createdAt = now,
        ))

        val expected = JSONArray()
        repeat(10) { index ->
            val number = index + 1
            val scopeType = if (number <= 5) "USER" else "PROJECT"
            val scopeId = if (scopeType == "USER") "local" else projectId
            val kind = when (number) { 1 -> "PREFERENCE"; 2 -> "CONTEXT"; else -> "FACT" }
            val text = if (number == 1) "H1_AT12_NEXT_CHAT_Ñ responde en español" else "H1_AT12_LEGACY_${number}_Ñ"
            val id = "h1-at12-legacy-$number"
            graph.database.memoryDao().upsert(MemoryItemEntity(
                id = id, scopeType = scopeType, scopeId = scopeId, content = text,
                sourceType = "H1_AT12_FIXTURE", sourceReference = "fixture://legacy/$number",
                relevance = .8f, status = "ACTIVE", createdAt = now + number, updatedAt = now + number,
                kind = kind,
            ))
            expected.put(JSONObject().put("id", "legacy-$id").put("kind", if (kind == "CONTEXT") "PROJECT_CONTEXT" else kind)
                .put("scopeType", scopeType).put("scopeId", scopeId).put("text", text))
        }

        repeat(10) { index ->
            val number = index + 1
            val isProject = number > 5
            val scope = if (isProject) SemanticScope(ScopeType.PROJECT, projectId) else SemanticScope(ScopeType.USER, "local")
            val kind = if (number == 1) MemoryKind.PREFERENCE else MemoryKind.FACT
            val id = "h1-at12-sidecar-$number"
            val text = "H1_AT12_SIDECAR_${number}_Ñ"
            graph.contextFoundation.memory.database.dao().put(MemoryRecord(
                id = id, kind = kind.name, scopeType = scope.type.name, scopeId = scope.id,
                title = "H1 AT12 sidecar $number", text = text, status = "ACTIVE",
                createdAt = now + 100 + number, updatedAt = now + 100 + number,
                sourceType = "USER_EXPLICIT", sourceId = "h1-at12-seed-$number",
                contentHash = MemoryContent.hash(text, kind),
            ))
            expected.put(JSONObject().put("id", id).put("kind", kind.name)
                .put("scopeType", scope.type.name).put("scopeId", scope.id).put("text", text))
        }

        check(state.edit().putString("projectId", projectId).putString("conversationId", conversationId)
            .putString("expectedMemories", expected.toString()).putBoolean("seeded", true).commit()) {
            "H1_AT12_FIXTURE_STATE_WRITE_FAILED"
        }
        assertEquals(20, expected.length())
    }
}

@RunWith(AndroidJUnit4::class)
class H1At12AfterUpdateTest {
    @Test fun H1_AT_12_verifyMemoriesProjectChatAndNextContextAfterUpdate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = context.getSharedPreferences(AT12_PREFS, Context.MODE_PRIVATE)
        assertTrue("The prior-version seeding step must complete before the app update", state.getBoolean("seeded", false))

        val graph = (context.applicationContext as LocalAiApplication).graph
        val projectId = requireNotNull(state.getString("projectId", null))
        val conversationId = requireNotNull(state.getString("conversationId", null))
        assertNotNull("Project survives update", graph.database.projectDao().get(projectId))
        assertNotNull("Conversation survives update", graph.database.conversationDao().get(conversationId))
        assertTrue("Chat message survives update", graph.database.messageDao().recent(conversationId, 10)
            .any { it.content == "H1_AT12_CHAT_MARKER_Ñ" })

        withTimeout(30_000) {
            while (true) {
                val migratedCount = graph.contextFoundation.memory.items.first().count {
                    it.status == "ACTIVE" && (it.id.startsWith("legacy-h1-at12-") || it.id.startsWith("h1-at12-sidecar-"))
                }
                val legacyRowsRemaining = graph.database.memoryDao().allForMigration().size
                if (migratedCount == 20 && legacyRowsRemaining == 0) break
                delay(100)
            }
        }

        val access = ScopeAccess(projectId = projectId)
        val prompt = graph.contextFoundation.build(
            ContextRequest("hola", access = access, includeConversation = false,
                sourceRetrievalMode = com.localai.workspace.context.SourceRetrievalMode.DISABLED),
            evidence = emptyList(),
        ).conversation().systemInstruction
        assertTrue(
            "The migrated preference is applied to the next chat context",
            prompt?.contains("H1_AT12_NEXT_CHAT_Ñ") == true,
        )

        val expected = JSONArray(requireNotNull(state.getString("expectedMemories", null)))
        val active = graph.contextFoundation.memory.items.first().filter { it.status == "ACTIVE" }
        assertEquals("All 10 legacy and 10 sidecar memories remain active", 20, active.count {
            it.id.startsWith("legacy-h1-at12-") || it.id.startsWith("h1-at12-sidecar-")
        })
        repeat(expected.length()) { index ->
            val row = expected.getJSONObject(index)
            val actual = requireNotNull(graph.contextFoundation.memory.get(row.getString("id")))
            assertEquals(row.getString("text"), actual.text)
            assertEquals(row.getString("kind"), actual.kind)
            assertEquals(row.getString("scopeType"), actual.scopeType)
            assertEquals(row.getString("scopeId"), actual.scopeId)
            assertEquals("ACTIVE", actual.status)
        }
        assertTrue("Verified legacy source rows are removed only after migration", graph.database.memoryDao().allForMigration().isEmpty())
    }
}
