package com.localai.workspace.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class LiteRtChatDefaultsUpgradeTest {
    private lateinit var database: WorkspaceDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun open() {
        for (name in listOf("litert_cpu_defaults_v016", "litert_chat_defaults_v017")) context.getSharedPreferences(name, 0).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
    }
    @After fun close() = database.close()
    private fun model() = ModelEntity("qwen", "Qwen", "/private/models/qwen.litertlm", fileHash = "original-hash",
        fileSize = 2_116_592_816, format = "LITERT_LM", runtimeId = "litert-lm-android", compatibilityStatus = "COMPATIBLE",
        configuredContext = 4096, importedAt = 123, sourceRepository = "owner/repo", lastMetrics = "old metrics")

    @Test fun originalDefaultsUpgradeToLargerCeilingWithoutChangingFilesContextOrRoomSchema() = runBlocking {
        val schemaBefore = database.openHelper.readableDatabase.version
        val old = model(); database.modelDao().insert(old)
        val updated = LiteRtChatDefaultsUpgrade(context, database.modelDao()).applyOnce(old.id)!!
        assertEquals(old.copy(maxOutputTokens = 256, repeatPenalty = 1.1f, updatedAt = updated.updatedAt), updated)
        assertEquals(schemaBefore, database.openHelper.readableDatabase.version)
    }

    @Test fun previousUpdateDefaultIsCorrectedButLaterDeliberateSmallLimitIsPreserved() = runBlocking {
        val old = model(); database.modelDao().insert(old)
        assertEquals(128, LiteRtDefaultsUpgrade(context, database.modelDao()).applyOnce(old.id)!!.maxOutputTokens)
        val fresh = LiteRtChatDefaultsUpgrade(context, database.modelDao()).applyOnce(old.id)!!
        assertEquals(256, fresh.maxOutputTokens)
        val chosen = fresh.copy(maxOutputTokens = 128, updatedAt = 456)
        database.modelDao().update(chosen)
        assertEquals(chosen, LiteRtChatDefaultsUpgrade(context, database.modelDao()).applyOnce(old.id))
    }

    @Test fun customizedTuplesAndGgufKeepAllSettings() = runBlocking {
        val rows = listOf(model().copy(id = "limit", fileHash = "a", maxOutputTokens = 64),
            model().copy(id = "sampler", fileHash = "b", maxOutputTokens = 128, repeatPenalty = 1.1f, temperature = .6f),
            model().copy(id = "gguf", fileHash = "c", format = "GGUF", runtimeId = "llama.cpp-android"))
        val upgrade = LiteRtChatDefaultsUpgrade(context, database.modelDao())
        for (row in rows) { database.modelDao().insert(row); assertEquals(row, upgrade.applyOnce(row.id)) }
    }

    @Test fun deletedModelDoesNotReappearAndMissingIdsStayMissing() = runBlocking {
        val old = model(); database.modelDao().insert(old)
        val upgrade = LiteRtChatDefaultsUpgrade(context, database.modelDao())
        val fresh = upgrade.applyOnce(old.id)!!
        database.modelDao().delete(fresh)
        assertNull(upgrade.applyOnce(old.id))
        assertNull(upgrade.applyOnce("missing"))
    }
}
