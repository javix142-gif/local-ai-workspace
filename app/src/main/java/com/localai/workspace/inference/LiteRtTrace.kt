package com.localai.workspace.inference

import android.os.Process
import android.util.Log
import com.localai.workspace.BuildConfig
import com.localai.workspace.domain.inference.GenerationStage
import org.json.JSONObject

/** Only structural metadata. No prompts, output text, file paths or raw exception messages. */
object LiteRtTrace {
    const val TAG = "LocalAI/LiteRT"

    fun event(
        event: String,
        stage: GenerationStage,
        model: String? = null,
        elapsedMs: Long = 0,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val json = JSONObject().apply {
            put("event", event)
            put("timestamp", System.currentTimeMillis())
            put("thread", Thread.currentThread().name)
            put("pid", Process.myPid())
            put("appVersion", BuildConfig.VERSION_NAME)
            put("stage", stage.name)
            put("elapsedMs", elapsedMs)
            put("model", model?.take(160) ?: JSONObject.NULL)
            put("accelerator", "UNAVAILABLE")
            fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
        }
        if (event.contains("ERROR") || event.contains("TIMEOUT")) Log.e(TAG, json.toString())
        else Log.i(TAG, json.toString())
    }
}
