package com.localai.workspace.data

import com.localai.workspace.domain.inference.GenerationError
import com.localai.workspace.domain.inference.GenerationStage
import org.json.JSONException
import org.json.JSONObject

/** Small local diagnostic stored in the existing generationMetrics column, distinct from benchmarks. */
object GenerationDiagnosticCodec {
    fun encode(error: GenerationError): String = JSONObject().apply {
        put("type", "generation_error")
        put("version", 1)
        put("stage", error.stage.name)
        put("code", error.code)
        put("message", error.message)
        put("elapsedMs", error.elapsedMs)
        error.checkpoint?.let { put("checkpoint", it) }
        error.technicalDetail?.let { put("detail", it.take(24_000)) }
    }.toString()

    fun decode(value: String?): GenerationError? {
        if (value == null || value.length > 32_000 || !value.startsWith("{")) return null
        return try {
            val data = JSONObject(value)
            if (data.optString("type") != "generation_error" || data.optInt("version") != 1) return null
            GenerationError(GenerationStage.valueOf(data.getString("stage")), data.getString("code"),
                data.getString("message"), data.getLong("elapsedMs"),
                technicalDetail = data.optString("detail").takeIf { it.isNotEmpty() },
                checkpoint = data.optString("checkpoint").takeIf { it.isNotEmpty() })
        } catch (_: JSONException) { null }
        catch (_: IllegalArgumentException) { null }
    }
}
