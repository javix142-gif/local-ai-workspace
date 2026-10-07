package com.localai.workspace.domain.tools

import com.localai.workspace.security.ConfirmationPolicy
import com.localai.workspace.security.InputSafety
import com.localai.workspace.security.ToolDecision
import com.localai.workspace.security.ToolDescriptor
import com.localai.workspace.security.ToolPermission
import com.localai.workspace.security.ToolPolicy
import com.localai.workspace.security.ToolRequest
import com.localai.workspace.security.TrustOrigin
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class ToolContext(
    val projectId: String,
    val conversationId: String,
    val workingDirectory: File,
)

sealed interface ToolExecutionResult {
    data class Success(val output: String) : ToolExecutionResult
    data class Rejected(val reason: String) : ToolExecutionResult
    data class NeedsConfirmation(val descriptor: ToolDescriptor, val validatedArguments: JSONObject) : ToolExecutionResult
    data class Failed(val code: String, val message: String) : ToolExecutionResult
}

class ToolExecutionException(val code: String, message: String):Exception(message)

interface LocalTool {
    val timeoutMs: Long get() = 5_000
    val descriptor: ToolDescriptor
    fun validate(arguments: JSONObject): String?
    suspend fun execute(arguments: JSONObject, context: ToolContext): String
}

/**
 * Application-controlled tool loop. Tools receive parsed JSON, never free-form model text.
 * A write or external side effect stops at NeedsConfirmation and cannot continue by itself.
 */
class ToolRegistry(
    tools: List<LocalTool>,
    private val maxCallsPerTurn: Int = 8,
    private val timeoutMs: Long = 60_000,
) {
    private val toolsById = tools.associateBy { it.descriptor.id }
    private val policy = ToolPolicy(toolsById.mapValues { it.value.descriptor })

    suspend fun execute(
        request: ToolRequest,
        enabledToolIds: Set<String>,
        context: ToolContext,
        state: ToolLoopState,
    ): ToolExecutionResult {
        if (state.calls >= maxCallsPerTurn) return ToolExecutionResult.Rejected("Tool-call limit reached")
        val tool = toolsById[request.toolId] ?: return ToolExecutionResult.Rejected("Unknown tool")
        val rawArguments = request.argumentsJson.trim()
        if (rawArguments.length > 32000) return ToolExecutionResult.Rejected("Arguments exceed size limit")
        if (rawArguments.length < 2 || rawArguments.first() != '{' || rawArguments.last() != '}') {
            return ToolExecutionResult.Rejected("Malformed JSON arguments")
        }
        val arguments = try {
            JSONObject(rawArguments)
        } catch (_: Throwable) {
            return ToolExecutionResult.Rejected("Malformed JSON arguments")
        }
        val validationError = tool.validate(arguments)
        if (validationError != null) return ToolExecutionResult.Rejected(validationError)
        // Use the original validated JSON bytes for repeat detection. This avoids
        // re-serializing platform JSON and preserves the exact-call boundary.
        val fingerprint = fingerprint(request.toolId, request.argumentsJson)
        if (!state.fingerprints.add(fingerprint)) return ToolExecutionResult.Rejected("Repeated identical tool call")
        when (val decision = policy.decide(request, enabledToolIds)) {
            is ToolDecision.Deny -> return ToolExecutionResult.Rejected(decision.reason)
            is ToolDecision.NeedsConfirmation -> return ToolExecutionResult.NeedsConfirmation(decision.descriptor, arguments)
            is ToolDecision.Allow -> Unit
        }
        state.calls++
        return try {
            ToolExecutionResult.Success(withTimeout(minOf(timeoutMs,tool.timeoutMs)) { tool.execute(arguments, context) }.take(MAX_OUTPUT_CHARS))
        } catch (failure: ToolExecutionException) {
            ToolExecutionResult.Failed(failure.code, failure.message ?: failure.code)
        } catch (_: TimeoutCancellationException) {
            ToolExecutionResult.Failed("TIMEOUT", "Tool exceeded its time limit")
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (error: Throwable) {
            ToolExecutionResult.Failed("EXECUTION_ERROR", error.message ?: "Tool failed")
        }
    }

    data class ToolLoopState(
        var calls: Int = 0,
        val fingerprints: MutableSet<String> = mutableSetOf(),
    )

    private fun fingerprint(toolId: String, arguments: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$toolId:$arguments".toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_OUTPUT_CHARS = 16_000
    }
}

class CalculatorTool : LocalTool {
    override val descriptor = ToolDescriptor(
        id = "calculator.evaluate",
        permissions = setOf(ToolPermission.READ_LOCAL),
        confirmationPolicy = ConfirmationPolicy.NEVER,
    )

    override fun validate(arguments: JSONObject): String? {
        if (arguments.keys().asSequence().any { it != "expression" } || arguments.opt("expression") !is String) return "expression must be a string; unknown arguments are rejected"
        val expression = arguments.getString("expression")
        if (expression.isBlank() || expression.length > 256) return "Expression is empty or too long"
        return if (Regex("^[0-9+*/().%\\s-]+$").matches(expression)) null else "Only arithmetic characters are allowed"
    }

    override suspend fun execute(arguments: JSONObject, context: ToolContext): String =
        SafeArithmeticEvaluator.evaluate(arguments.getString("expression")).toString()
}

class LocalFileReadTool : LocalTool {
    override val descriptor = ToolDescriptor(
        id = "files.read",
        permissions = setOf(ToolPermission.READ_LOCAL),
        confirmationPolicy = ConfirmationPolicy.NEVER,
    )

    override fun validate(arguments: JSONObject): String? {
        val path = arguments.optString("path", "")
        return if (InputSafety.isSafeRelativePath(path)) null else "Path must stay relative to the project workspace"
    }

    override suspend fun execute(arguments: JSONObject, context: ToolContext): String {
        val relative = arguments.getString("path")
        val root = context.workingDirectory.canonicalFile
        val target = File(root, relative).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + File.separator)) { "Path escapes workspace" }
        require(target.isFile) { "File does not exist" }
        return target.inputStream().bufferedReader().use { it.readTextBounded() }
    }
}

internal object SafeArithmeticEvaluator {
    fun evaluate(source: String): Double = DecimalExpressionParser(source).parse()
}

private class DecimalExpressionParser(private val source: String) {
    private var index = 0

    fun parse(): Double {
        val result = parseExpression()
        skipSpaces()
        require(index == source.length) { "Unexpected input" }
        require(result.isFinite()) { "Non-finite result" }
        return result
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            value = when {
                consume('+') -> value + parseTerm()
                consume('-') -> value - parseTerm()
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (true) {
            skipSpaces()
            value = when {
                consume('*') -> value * parseFactor()
                consume('/') -> value / parseFactor().also { require(it != 0.0) { "Division by zero" } }
                consume('%') -> value % parseFactor()
                else -> return value
            }
        }
    }

    private fun parseFactor(): Double {
        skipSpaces()
        if (consume('+')) return parseFactor()
        if (consume('-')) return -parseFactor()
        if (consume('(')) return parseExpression().also { require(consume(')')) { "Missing closing parenthesis" } }
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
        require(index > start) { "Expected number" }
        return source.substring(start, index).toDouble()
    }

    private fun consume(character: Char): Boolean = if (index < source.length && source[index] == character) {
        index++
        true
    } else false

    private fun skipSpaces() { while (index < source.length && source[index].isWhitespace()) index++ }
}

private fun java.io.Reader.readTextBounded(): String { val buffer = CharArray(16000); val n = read(buffer); return if (n < 0) "" else String(buffer, 0, n) }
