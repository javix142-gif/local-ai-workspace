package com.localai.workspace.performance

import com.localai.workspace.domain.model.AcceleratorType

/** Failed is not unsupported. Retry only on a new profile or an explicit diagnostic retry. */
internal class ExperimentalBackendPolicy {
    private val failures = mutableMapOf<String, String>()
    fun recordedFailure(identity: String): String? = failures[identity]
    fun recordFailure(identity: String, reason: String) { failures[identity] = reason }
    fun retry(identity: String) { failures.remove(identity) }
    fun backend(requested: AcceleratorType, identity: String): AcceleratorType =
        if (requested == AcceleratorType.GPU && identity in failures) AcceleratorType.CPU else requested
}

data class SpeculativeCapabilityState(val modelSupport: Boolean?, val runtimeSupport: Boolean,
    val enabled: Boolean, val engineInitializedWithFlag: Boolean?) {
    val label: String get() = when {
        modelSupport == false || !runtimeSupport -> "UNSUPPORTED"
        enabled && engineInitializedWithFlag == true -> "ACTIVE (engine configured; acceptance unavailable)"
        enabled -> "ENABLED / NOT TESTED"
        modelSupport == true -> "SUPPORTED / OFF"
        else -> "NOT TESTED"
    }
}
