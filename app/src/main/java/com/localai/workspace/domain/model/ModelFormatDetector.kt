package com.localai.workspace.domain.model

object ModelFormatDetector {
    fun fromFilename(name: String): ModelFormat = when {
        name.trim().lowercase().endsWith(".gguf") -> ModelFormat.GGUF
        name.trim().lowercase().endsWith(".litertlm") -> ModelFormat.LITERT_LM
        else -> ModelFormat.UNKNOWN
    }

    fun hasGgufMagic(bytes: ByteArray): Boolean = bytes.size >= 4 &&
        bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))

    fun hasLiteRtLmMagic(bytes: ByteArray): Boolean = bytes.size >= 8 &&
        bytes.copyOfRange(0, 8).contentEquals("LITERTLM".toByteArray(Charsets.US_ASCII))

    fun inferQuantization(name: String): String? = Regex("(?i)(Q[2-8](?:_[KkMm0-9]+)?|INT8|INT4|BF16|F16)")
        .find(name)?.value?.uppercase()

    fun sanitizeFilename(name: String, fallback: String): String {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        val safe = leaf.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_', '-')
        return safe.ifBlank { fallback }
    }
}
