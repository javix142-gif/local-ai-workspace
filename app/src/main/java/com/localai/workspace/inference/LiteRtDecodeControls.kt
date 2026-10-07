package com.localai.workspace.inference

import com.google.ai.edge.litertlm.NoRepeatNgramConfig
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig

/** Actual native logit controls; the fallback text-loop watchdog remains separate. */
internal data class LiteRtDecodeControls(val penalty: RepetitionPenaltyConfig, val noRepeat: NoRepeatNgramConfig?) {
    companion object {
        fun create(repeatPenalty: Float, ordinaryFinalText: Boolean): LiteRtDecodeControls {
            require(repeatPenalty.isFinite()) { "Repeat penalty must be finite" }
            val effective = repeatPenalty.coerceAtLeast(1f)
            val preventLoops = ordinaryFinalText && effective > 1f
            return LiteRtDecodeControls(
                RepetitionPenaltyConfig(repetitionPenalty = effective, windowSize = if (preventLoops) 256 else null),
                if (preventLoops) NoRepeatNgramConfig(noRepeatNgramSize = 8, windowSize = 256) else null,
            )
        }
    }
}
