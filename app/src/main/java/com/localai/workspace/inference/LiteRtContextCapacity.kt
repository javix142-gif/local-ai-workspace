package com.localai.workspace.inference

/** Conservative admission, not tokenization. The cache count is native; the rendered
 * UTF-8 byte budget plus 128 template/BOS tokens bounds the next text turn. Unknown
 * counts/rendering reject reuse. Vision/thinking never enter this text-only path. */
internal object LiteRtContextCapacity {
    fun canContinue(cachedTokens: Int?, renderedNextTurn: String?, maxOutput: Int, context: Int): Boolean {
        if (cachedTokens == null || cachedTokens < 0 || renderedNextTurn.isNullOrEmpty()) return false
        return cachedTokens.toLong() + renderedNextTurn.toByteArray(Charsets.UTF_8).size + 128L +
            maxOutput.coerceIn(1, 8192) <= context
    }
}
