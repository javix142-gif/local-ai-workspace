package com.localai.workspace.data

import android.content.Context
import com.localai.workspace.domain.model.RuntimeType
import com.localai.workspace.domain.model.toDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One-time correction of the old default tuple. All other parameter choices are preserved. */
class LiteRtDefaultsUpgrade(context: Context, private val models: ModelDao) {
    private val checked = context.getSharedPreferences("litert_cpu_defaults_v016", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun applyOnce(id: String): ModelEntity? = withContext(Dispatchers.IO) { mutex.withLock {
        val model = models.get(id) ?: return@withLock null
        if (model.toDescriptor().runtime != RuntimeType.LITERT_LM || checked.getBoolean(id, false)) return@withLock model
        if (isLegacyDefault(model)) {
            models.updateSettings(id, model.preferredAccelerator, model.configuredContext, 128,
                model.temperature, model.topP, model.topK, 1.1f, model.seed, System.currentTimeMillis())
        }
        // A later deliberate return to 1.0/512 must be respected.
        checked.edit().putBoolean(id, true).apply()
        models.get(id)
    } }

    companion object {
        internal fun isLegacyDefault(model: ModelEntity) = model.toDescriptor().runtime == RuntimeType.LITERT_LM &&
            model.preferredAccelerator == "CPU" && model.maxOutputTokens == 512 && model.temperature == .3f &&
            model.topP == .95f && model.topK == 40 && model.repeatPenalty == 1f && model.seed == 0
    }
}
