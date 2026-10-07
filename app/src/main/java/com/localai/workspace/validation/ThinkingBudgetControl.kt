package com.localai.workspace.validation

import com.google.gson.JsonObject

/** QA-only characterization of the deliberately unbounded 256-token control.
 * A correct final answer is also valid; a thought-only result passes ONLY with exact evidence.
 * Does not configure, retry or otherwise influence inference.
 */
internal object ThinkingBudgetControl {
    const val EXHAUSTED = "THINKING_OUTPUT_BUDGET_EXHAUSTED"

    fun evaluate(observed: JsonObject, internalReason: String?, correctFinal: Boolean,
        executionStarted: Boolean = true): ValidationOutcome {
        val data = observed.deepCopy()
        fun flag(key: String) = data[key]?.takeUnless { it.isJsonNull }?.asBoolean == true
        fun count(key: String) = data[key]?.takeUnless { it.isJsonNull }?.asLong
        val configured = data["thinkingRequested"]?.asString == "ON" && flag("thinkingEffective") &&
            count("thinkingTokenBudget") == -1L && count("configuredMaxOutput") == 256L &&
            count("effectiveMaxOutput") == 256L
        val thought = (count("thoughtCallbackCount") ?: 0) > 0 && (count("thoughtCharacterCount") ?: 0) > 0
        val countsConsistent = (count("rawCallbackCount") ?: -1) >=
            maxOf(count("thoughtCallbackCount") ?: 0, count("finalCallbackCount") ?: 0) &&
            count("unknownChannelCallbackCount") == 0L
        val exhausted = executionStarted && configured && thought && countsConsistent &&
            flag("nativeCompletionObserved") && flag("outputLimitReached") && count("outputTokens") == 256L &&
            count("finalCallbackCount") == 0L && count("finalCharacterCount") == 0L &&
            count("visibleOutputLength") == 0L && internalReason == EXHAUSTED
        val finalValid = executionStarted && configured && thought && countsConsistent &&
            flag("nativeCompletionObserved") && internalReason == null && correctFinal &&
            (count("finalCallbackCount") ?: 0) > 0 && (count("visibleOutputLength") ?: 0) > 0 &&
            count("outputTokens")?.let { it in 1L..256L } == true
        data.addProperty("executionStarted", executionStarted)
        data.addProperty("expectedExhaustionObserved", exhausted)
        data.addProperty("observedCondition", when {
            exhausted -> EXHAUSTED
            finalValid -> "CORRECT_FINAL_ANSWER"
            else -> internalReason ?: "THINKING_VALIDATION_ERROR"
        })
        return ValidationOutcome(when {
            exhausted || finalValid -> ValidationStatus.PASS
            !executionStarted -> ValidationStatus.BLOCKED
            else -> ValidationStatus.FAIL
        }, if(exhausted || finalValid) null else internalReason ?: "THINKING_VALIDATION_ERROR", data)
    }

    /** A request that started but did not complete is a failed control, never expected exhaustion. */
    fun timeout(partial: JsonObject): ValidationOutcome {
        val started = partial["ON_REASONING.executionStarted"]?.asBoolean == true
        return ValidationOutcome(if(started) ValidationStatus.FAIL else ValidationStatus.BLOCKED,
            "THINKING_TIMEOUT", partial.deepCopy().apply {
                addProperty("expectedExhaustionObserved", false)
                addProperty("observedCondition", "THINKING_TIMEOUT")
            })
    }
}
