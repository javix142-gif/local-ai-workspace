package com.localai.workspace.performance

import com.localai.workspace.domain.model.RuntimeMetrics

/** Monotonic host measurements, independent of native clocks and tokenizer statistics. */
data class RequestTimings(val acceptedAtMs: Long, val gateWaitMs: Long? = null,
    val contextBuildMs: Long? = null, val firstStateAfterAcceptMs: Long? = null,
    val callbackToStateMs: Long? = null) {
    fun merge(native: RuntimeMetrics, completedAtMs: Long, finish: String = "SUCCESS"): RuntimeMetrics {
        require(completedAtMs >= acceptedAtMs)
        return native.copy(sendStartedAt = acceptedAtMs, inferenceGateWaitMs = gateWaitMs,
            contextBuildMs = contextBuildMs, requestTimeToFirstTokenMs = firstStateAfterAcceptMs,
            callbackToUiStateMs = callbackToStateMs, endToEndTotalMs = completedAtMs - acceptedAtMs, finishState = finish)
    }
}
