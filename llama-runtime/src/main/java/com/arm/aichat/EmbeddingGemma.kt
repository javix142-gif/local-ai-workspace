package com.arm.aichat

/** Real llama.cpp Gemma-embedding encoder. Private handle; weights released after indexing/search. */
class EmbeddingGemma(path: String, nativeLibraryDir: String) : AutoCloseable {
 private var handle: Long
 init { System.loadLibrary("local-embedding");handle=nativeLoad(path,nativeLibraryDir) }
 val dimension get() = nativeDimension(handle)
 val architecture get() = nativeArchitecture(handle)
 @Synchronized fun embed(text: String): FloatArray { check(handle!=0L);return nativeEmbed(handle,text.toByteArray(Charsets.UTF_8)) }
 @Synchronized override fun close() { if(handle!=0L) { nativeClose(handle);handle=0 } }
 private external fun nativeLoad(path: String, nativeLibraryDir: String): Long
 private external fun nativeEmbed(handle: Long, text: ByteArray): FloatArray
 private external fun nativeClose(handle: Long)
 private external fun nativeDimension(handle: Long): Int
 private external fun nativeArchitecture(handle: Long): String
}

class EmbeddingNativeException(message: String) : IllegalStateException(message) {
 val stage: String get() = message.orEmpty().substringBefore('|')
 val code: String get() = message.orEmpty().split('|').getOrNull(1) ?: "NATIVE_ERROR"
 val safeDetail: String get() = message.orEmpty().split('|',limit=3).getOrNull(2) ?: "Native operation failed"
}
