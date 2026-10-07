package com.localai.workspace.inference

/** A false legacy SDK flag is not proof of unsupported weights. Explicit false is respected. */
data class CapabilityState(val modelSupport: Boolean?, val bundleDeclared: Boolean?, val runtimeSupport: Boolean,
    val appImplemented: Boolean, val source: String, val enabled: Boolean) {
    val available get() = modelSupport == true && runtimeSupport && appImplemented
    val reason: String? get() = when {
        !runtimeSupport -> "RUNTIME_CAPABILITY_MISSING"
        !appImplemented -> "APP_CAPABILITY_MISSING"
        bundleDeclared == false -> "BUNDLE_CAPABILITY_MISSING"
        modelSupport == false -> "MODEL_CAPABILITY_MISSING"
        modelSupport == null -> "BUNDLE_CAPABILITY_MISSING"
        else -> null
    }
    fun metadata(prefix: String) = mapOf("$prefix.modelSupport" to (modelSupport?.toString() ?: "UNKNOWN"),
        "$prefix.bundleDeclared" to (bundleDeclared?.toString() ?: "ABSENT"), "$prefix.runtimeSupport" to runtimeSupport.toString(),
        "$prefix.appImplemented" to appImplemented.toString(), "$prefix.source" to source, "$prefix.available" to available.toString())
}
internal object LiteRtCapabilityResolver {
    fun resolve(declared: Boolean?, sdk: Boolean, legacyContract: Boolean, enabled: Boolean = false,
        runtime: Boolean = true, app: Boolean = true): CapabilityState {
        val supported = when { declared != null -> declared; sdk -> true; legacyContract -> true; else -> null }
        val source = when { declared != null -> "EXPLICIT_PROTO"; sdk -> "SDK_CAPABILITIES"; legacyContract -> "AUDITED_PROCESSOR_TEMPLATE_0171"; else -> "UNDETERMINED_BUNDLE" }
        return CapabilityState(supported,declared,runtime,app,source,enabled && supported==true && runtime && app)
    }
}
