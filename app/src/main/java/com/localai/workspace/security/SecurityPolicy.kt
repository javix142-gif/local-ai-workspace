package com.localai.workspace.security

import java.net.URI

enum class ToolPermission {
    READ_LOCAL,
    WRITE_LOCAL,
    NETWORK_READ,
    EXTERNAL_SIDE_EFFECT,
}

enum class ConfirmationPolicy { NEVER, REQUIRED_FOR_WRITE, ALWAYS }

data class ToolDescriptor(
    val id: String,
    val permissions: Set<ToolPermission>,
    val confirmationPolicy: ConfirmationPolicy,
)

data class ToolRequest(
    val toolId: String,
    val argumentsJson: String,
    val origin: TrustOrigin,
)

enum class TrustOrigin { APP, USER, MODEL, UNTRUSTED_EXTERNAL }

sealed interface ToolDecision {
    data class Allow(val descriptor: ToolDescriptor) : ToolDecision
    data class NeedsConfirmation(val descriptor: ToolDescriptor) : ToolDecision
    data class Deny(val reason: String) : ToolDecision
}

class ToolPolicy(private val registry: Map<String, ToolDescriptor>) {
    fun decide(request: ToolRequest, enabledToolIds: Set<String>): ToolDecision {
        val descriptor = registry[request.toolId]
            ?: return ToolDecision.Deny("Unknown tool")
        if (request.toolId !in enabledToolIds) {
            return ToolDecision.Deny("Tool is disabled for this project")
        }
        if (request.origin == TrustOrigin.UNTRUSTED_EXTERNAL) {
            return ToolDecision.Deny("Untrusted content cannot authorize tools")
        }
        return when (descriptor.confirmationPolicy) {
            ConfirmationPolicy.NEVER -> ToolDecision.Allow(descriptor)
            ConfirmationPolicy.REQUIRED_FOR_WRITE,
            ConfirmationPolicy.ALWAYS -> ToolDecision.NeedsConfirmation(descriptor)
        }
    }
}

object InputSafety {
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        return path.split('/', '\\').none { it == ".." || it.isBlank() && path.contains("//") }
    }

    fun isSafeHttpUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
