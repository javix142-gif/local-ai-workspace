package com.localai.workspace.inference

import com.google.ai.edge.litertlm.ThinkingConfig
import com.localai.workspace.domain.model.RuntimeMetrics

/** Both channels share the decode cap. Bound reasoning to at most half of the cap,
 * and at most 128 tokens, leaving margin for final/control tokens via the SDK API. Does not change the persisted/user output cap or sampling. */
internal object ThinkingOutputPolicy {
    fun config(enabled: Boolean, maxOutput: Int, validationBudget: Int? = null) = ThinkingConfig(enabled,
        if (enabled && validationBudget == -1) -1 else if (enabled) minOf(128, maxOutput.coerceIn(1, 8192) / 2) else -1)
}

/** Scalar-only observer. No reasoning string is retained after a callback returns. */
internal class ThinkingCallbackMetrics {
    private var raw = 0
    private var thought = 0
    private var final = 0
    private var unknown = 0
    private var thoughtChars = 0
    private var finalChars = 0
    private var visibleChars = 0
    private var first: Long? = null
    private var firstThought: Long? = null
    private var firstFinal: Long? = null
    @Synchronized fun accept(text: String, channels: Map<String, String>, elapsedMs: Long): String {
        raw++
        if (first == null) first = elapsedMs
        val hiddenSize = channels.filterKeys { it in setOf("thought", "analysis") }.values.sumOf { it.length }
        if (hiddenSize > 0) { thought++; thoughtChars += hiddenSize; if (firstThought == null) firstThought = elapsedMs }
        if (channels.keys.any { it !in setOf("thought", "analysis", "final") }) unknown++
        // SDK 0.17.1 puts primary/final text in contents, not necessarily a channel named final.
        val finalSize = if (channels.containsKey("final")) channels["final"].orEmpty().length else text.length
        if (finalSize > 0) { final++; finalChars += finalSize; if (firstFinal == null) firstFinal = elapsedMs }
        return VisibleModelOutput.select(text, channels).also { visibleChars += it.length }
    }
    @Synchronized fun applyTo(metrics: RuntimeMetrics, output: Int, enabled: Boolean, validationBudget: Int? = null) = metrics.copy(
        rawCallbackCount=raw, thoughtCallbackCount=thought, finalCallbackCount=final,
        unknownChannelCallbackCount=unknown, thoughtCharacterCount=thoughtChars,
        finalCharacterCount=finalChars, visibleOutputLength=visibleChars,
        firstCallbackMs=first, timeToFirstThoughtMs=firstThought, timeToFirstFinalMs=firstFinal,
        configuredMaxOutput=output, effectiveMaxOutput=output,
        thinkingTokenBudget=ThinkingOutputPolicy.config(enabled, output, validationBudget).thinkingTokenBudget,
    )
}
internal class ThinkingOutputException(val reasonCode: String) : IllegalStateException(reasonCode)
internal object ThinkingFailure {
    fun reason(m: RuntimeMetrics): String? = when {
        m.finishState == "TIMEOUT" -> "THINKING_TIMEOUT"
        m.thinkingEffective != true && (m.visibleOutputLength ?: 0) > 0 -> null
        (m.visibleOutputLength ?: 0) > 0 -> null
        (m.finalCharacterCount ?: 0) > 0 -> "THINKING_CALLBACK_FILTERED"
        m.outputLimitReached == true -> "THINKING_OUTPUT_BUDGET_EXHAUSTED"
        (m.thoughtCharacterCount ?: 0) > 0 -> "THINKING_NO_FINAL_CHANNEL"
        m.thinkingEffective != null -> "THINKING_NO_VISIBLE_OUTPUT"
        else -> "NO_TOKENS"
    }
}
