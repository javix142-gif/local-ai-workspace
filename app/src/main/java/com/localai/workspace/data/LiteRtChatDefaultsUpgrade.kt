package com.localai.workspace.data

import android.content.Context
import com.localai.workspace.domain.model.RuntimeType
import com.localai.workspace.domain.model.toDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Correct the previous small default ceiling once; preserve custom tuples and later changes. */
class LiteRtChatDefaultsUpgrade(context: Context, private val models: ModelDao, private val readOnly: Boolean = false) {
    private val previous = LiteRtDefaultsUpgrade(context, models)
    private val checked = context.getSharedPreferences("litert_chat_defaults_v017", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun applyOnce(id: String): ModelEntity? {
        if(readOnly) return models.get(id)
        previous.applyOnce(id) ?: return null
        return withContext(Dispatchers.IO) { mutex.withLock {
            val model = models.get(id) ?: return@withLock null
            if (model.toDescriptor().runtime != RuntimeType.LITERT_LM || checked.getBoolean(id, false)) return@withLock model
            if (model.preferredAccelerator == "CPU" && model.maxOutputTokens == 128 && model.temperature == .3f &&
                model.topP == .95f && model.topK == 40 && model.repeatPenalty == 1.1f && model.seed == 0) {
                models.updateSettings(id, model.preferredAccelerator, model.configuredContext, 256,
                    model.temperature, model.topP, model.topK, model.repeatPenalty, model.seed, System.currentTimeMillis())
            }
            checked.edit().putBoolean(id, true).apply()
            models.get(id)
        } }
    }
}
