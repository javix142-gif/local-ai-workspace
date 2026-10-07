package com.localai.workspace.inference

import com.google.ai.edge.litertlm.Role
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiteRtBundleMetadataTest {
    private fun fixture() = requireNotNull(javaClass.classLoader?.getResourceAsStream("litert/qwen35-header-metadata.bin")).use { it.readBytes() }
    private fun read(bytes: ByteArray): LiteRtBundleMetadata {
        val file = File.createTempFile("litert-metadata-", ".bin")
        try { file.writeBytes(bytes); return LiteRtBundleMetadata.read(file) }
        finally { file.delete() }
    }

    @Test fun publishedQwenMetadataUsesNativeAssistantHistoryWithoutReadingWeights() {
        val data = fixture()
        assertEquals(17113, data.size)
        val metadata = read(data)
        assertEquals(1, metadata.modelProcessor)
        assertEquals(4096, metadata.declaredContext)
        assertEquals(Role.ASSISTANT, metadata.historyRole)
    }

    @Test fun legacyModelTemplatesRemainModelWhileChatMlUsesAssistant() {
        fun template(role: String): ByteArray {
            val text = "{% if message.role == '$role' %}history{% endif %}".toByteArray()
            return byteArrayOf(50, 2, 10, 0, 58, text.size.toByte()) + text
        }
        assertEquals(Role.MODEL, LiteRtBundleMetadata.fromProto(template("model")).historyRole)
        assertEquals(Role.ASSISTANT, LiteRtBundleMetadata.fromProto(template("assistant")).historyRole)
    }

    @Test fun knownProcessorsKeepTheirNativeRolesAndUnknownIsNotGuessed() {
        assertEquals(Role.ASSISTANT, LiteRtBundleMetadata.fromProto(byteArrayOf(50, 2, 10, 0)).historyRole)
        assertEquals(Role.ASSISTANT, LiteRtBundleMetadata.fromProto(byteArrayOf(50, 2, 42, 0)).historyRole)
        assertEquals(Role.MODEL, LiteRtBundleMetadata.fromProto(byteArrayOf(50, 2, 34, 0)).historyRole)
        assertNull(LiteRtBundleMetadata.fromProto(byteArrayOf(50, 2, 122, 0)).historyRole)
        assertNull(LiteRtBundleMetadata.fromProto(byteArrayOf()).historyRole)
    }

    @Test fun corruptAndGiganticContainerOffsetsAreRejectedBeforeAllocatingPayload() {
        val data = fixture()
        for (end in listOf(0L, 32L, Long.MAX_VALUE, 2_116_592_816L)) {
            val damaged = data.copyOf()
            ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putLong(24, end)
            assertThrows(IllegalArgumentException::class.java) { read(damaged) }
        }
        val corrupt = data.copyOf().apply { this[32] = 127; this[33] = 127; this[34] = 127; this[35] = 127 }
        assertThrows(IllegalArgumentException::class.java) { read(corrupt) }
    }

    @Test fun truncatedOutOfBoundsAndUnsupportedProtoFieldsAreRecoverableErrors() {
        for (data in listOf(byteArrayOf(0), byteArrayOf(1), byteArrayOf(58, 99, 1), byteArrayOf(51), ByteArray(11) { 0xff.toByte() })) {
            assertThrows(IllegalArgumentException::class.java) { LiteRtBundleMetadata.fromProto(data) }
        }
        assertThrows(IllegalArgumentException::class.java) { LiteRtBundleMetadata.fromProto(ByteArray(1_048_577)) }
        assertThrows(java.io.EOFException::class.java) { read(fixture().copyOf(12)) }
    }
}
