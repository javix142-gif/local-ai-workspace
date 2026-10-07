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
class LiteRtDefaultsUpgradeTest {
    private lateinit var database: WorkspaceDatabase
    private lateinit var upgrade: LiteRtDefaultsUpgrade
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun open() {
        context.getSharedPreferences("litert_cpu_defaults_v016", 0).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        upgrade = LiteRtDefaultsUpgrade(context, database.modelDao())
    }
    @After fun close() = database.close()

    private fun model(id: String = "litert") = ModelEntity(id, "Qwen", "/private/models/qwen.litertlm",
        fileHash = "hash-$id", fileSize = 2_116_592_816, format = "LITERT_LM", runtimeId = "litert-lm-android",
        configuredContext = 4096, compatibilityStatus = "COMPATIBLE", importedAt = 123,
        lastMetrics = "old benchmark", lastTestedAt = 124, auxiliaryFiles = "unchanged", sourceRepository = "owner/repo")

    @Test fun exactOldDefaultsChangeOnceWithoutResettingModelFilesHistoryOrContext() = runBlocking {
        val schemaBefore = database.openHelper.readableDatabase.version
        val old = model()
        database.modelDao().insert(old)
        val fresh = checkNotNull(upgrade.applyOnce(old.id))
        assertEquals(128, fresh.maxOutputTokens)
        assertEquals(1.1f, fresh.repeatPenalty, 0f)
        assertEquals(old.copy(maxOutputTokens = 128, repeatPenalty = 1.1f, updatedAt = fresh.updatedAt), fresh)
        assertEquals(schemaBefore, database.openHelper.readableDatabase.version)
        assertEquals(fresh, database.modelDao().get(old.id))
    }

    @Test fun explicitReturnToOldTupleIsRespectedAfterUpgradeAndNewHelperInstance() = runBlocking {
        val old = model()
        database.modelDao().insert(old)
        upgrade.applyOnce(old.id)
        database.modelDao().update(old.copy(updatedAt = 987))
        val reopened = LiteRtDefaultsUpgrade(context, database.modelDao())
        assertEquals(old.copy(updatedAt = 987), reopened.applyOnce(old.id))
    }

    @Test fun customizedParametersAndGgufRecordsArePreserved() = runBlocking {
        val records = listOf(model("tokens").copy(maxOutputTokens = 256), model("repeat").copy(repeatPenalty = 1.2f),
            model("sampler").copy(temperature = .4f), model("seed").copy(seed = 42),
            model("gguf").copy(format = "GGUF", runtimeId = "llama.cpp-android"))
        for (record in records) {
            database.modelDao().insert(record)
            assertEquals(record, upgrade.applyOnce(record.id))
        }
        assertEquals(records.size, database.openHelper.readableDatabase.query("SELECT id FROM models").use { it.count })
    }

    @Test fun customizationCheckedFirstIsNotOverwrittenByLaterLegacyTuple() = runBlocking {
        val custom = model().copy(maxOutputTokens = 64)
        database.modelDao().insert(custom)
        assertEquals(custom, upgrade.applyOnce(custom.id))
        val chosen = custom.copy(maxOutputTokens = 512, updatedAt = 432)
        database.modelDao().update(chosen)
        assertEquals(chosen, upgrade.applyOnce(chosen.id))
        assertNull(upgrade.applyOnce("missing"))
    }
}
