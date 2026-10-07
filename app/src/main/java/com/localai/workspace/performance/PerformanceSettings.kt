package com.localai.workspace.performance

import android.content.Context
import com.localai.workspace.domain.model.AcceleratorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PerformanceProfile(
    val backend: AcceleratorType = AcceleratorType.CPU,
    val warmup: Boolean = true,
    val speculative: Boolean = false,
    val measureUiDelivery: Boolean = false,
)

/** Separate opt-in diagnostics; does not replace model sampling or existing preferences. */
class PerformanceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("local_ai_performance", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(PerformanceProfile(
        backend = if (preferences.getBoolean("gpu", false)) AcceleratorType.GPU else AcceleratorType.CPU,
        warmup = preferences.getBoolean("warmup", true),
        speculative = preferences.getBoolean("speculative", false),
        measureUiDelivery = preferences.getBoolean("ui_delivery", false),
    ))
    val state = mutable.asStateFlow()
    fun update(profile: PerformanceProfile) {
        require(profile.backend in setOf(AcceleratorType.CPU, AcceleratorType.GPU))
        preferences.edit().putBoolean("gpu", profile.backend == AcceleratorType.GPU)
            .putBoolean("warmup", profile.warmup).putBoolean("speculative", profile.speculative)
            .putBoolean("ui_delivery", profile.measureUiDelivery).apply()
        mutable.value = profile
    }
}

enum class WarmupState { NOT_REQUESTED, LOADED, WARMING, WARMED, FAILED, TIMEOUT, CANCELLED }

/** One attempt per engine lifetime. The service owns the temporary native conversation. */
internal class EngineWarmupState {
    var state = WarmupState.NOT_REQUESTED
        private set
    var durationMs: Long? = null
        private set
    fun loaded() { state = WarmupState.LOADED; durationMs = null }
    fun shouldRun(enabled: Boolean) = enabled && state == WarmupState.LOADED
    fun start() { check(state == WarmupState.LOADED); state = WarmupState.WARMING }
    fun finish(result: WarmupState, duration: Long) {
        check(state == WarmupState.WARMING)
        require(result in setOf(WarmupState.WARMED, WarmupState.FAILED, WarmupState.TIMEOUT, WarmupState.CANCELLED))
        state = result; durationMs = duration.coerceAtLeast(0)
    }
}
