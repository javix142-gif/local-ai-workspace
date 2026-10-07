package com.localai.workspace.inference

import com.google.ai.edge.litertlm.Role
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded read of the official container header and LlmMetadata proto, never tensor sections. */
internal data class LiteRtBundleMetadata(
    val modelProcessor: Int?,
    val declaredContext: Int?,
    val historyRole: Role?,
    val thinkingDeclared: Boolean? = null,
    val toolsDeclared: Boolean? = null,
    val templateSha256: String? = null,
    val legacyGemmaContract: Boolean = false,
) {
    companion object {
        private const val MAX_METADATA = 1_048_576

        fun read(file: File): LiteRtBundleMetadata = RandomAccessFile(file, "r").use { input ->
            val prefix = ByteArray(32).also(input::readFully)
            require(String(prefix, 0, 8, Charsets.US_ASCII) == "LITERTLM") { "Invalid LiteRT metadata magic" }
            val headerEnd = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).getLong(24)
            require(headerEnd in 36..minOf(input.length(), MAX_METADATA.toLong() + 32)) { "Invalid LiteRT metadata header range" }
            val header = ByteArray((headerEnd - 32).toInt()).also(input::readFully)
            val tables = BoundedTables(header)
            val sections = tables.reference(tables.field(tables.reference(0), 1))
            val vector = tables.reference(tables.field(sections, 0))
            val count = tables.uint(vector)
            require(count in 1..256) { "Invalid LiteRT metadata section count" }
            tables.range(vector + 4, count * 4)
            val metadataSections = (0 until count).map { tables.reference(vector + 4 + it * 4) }
                .filter { tables.byte(tables.field(it, 3)) == 5 } // AnySectionDataType.LlmMetadataProto
            require(metadataSections.size == 1) { "Expected one LiteRT LlmMetadata section" }
            val section = metadataSections.single()
            val begin = tables.long(tables.field(section, 1))
            val end = tables.long(tables.field(section, 2))
            require(begin >= headerEnd && end >= begin && end <= input.length() && end - begin <= MAX_METADATA) {
                "Invalid LiteRT LlmMetadata range"
            }
            input.seek(begin)
            fromProto(ByteArray((end - begin).toInt()).also(input::readFully))
        }

        internal fun fromProto(bytes: ByteArray): LiteRtBundleMetadata {
            require(bytes.size <= MAX_METADATA) { "LiteRT metadata is too large" }
            val fields = WireReader(bytes).fields()
            val processor = fields.lastOrNull { it.id == 6 && it.bytes != null }?.bytes?.let {
                WireReader(it).fields().lastOrNull { field -> field.bytes != null }?.id
            }
            val context = fields.lastOrNull { it.id == 5 }?.number?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
            val template = fields.lastOrNull { it.id == 7 && it.bytes != null }?.bytes?.toString(Charsets.UTF_8)
            // Prefer explicit role branches in the actual embedded template. Both legacy
            // 'model' templates and ChatML 'assistant' templates must keep their convention.
            val roles = template?.let {
                Regex("(?:\\.role|\\[['\"]role['\"]\\])\\s*==\\s*(['\"])(assistant|model)\\1")
                    .findAll(it).map { match -> match.groupValues[2] }.toSet()
            }.orEmpty()
            val role = when {
                roles == setOf("model") -> Role.MODEL
                roles == setOf("assistant") -> Role.ASSISTANT
                processor in setOf(1, 5, 7, 9, 11, 12) -> Role.ASSISTANT // Official native processor conventions.
                processor in setOf(2, 3, 4, 8) -> Role.MODEL
                else -> null
            }
            val templateHash = template?.let { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8)).joinToString("") { b -> "%02x".format(b) } }
            // Audited official Gemma4 processor/template, NOT a model filename heuristic.
            // SDK 0.17.1 proto fields 11/12 were absent in this older export.
            val processorBytes = fields.lastOrNull { it.id == 6 }?.bytes?.let { WireReader(it).fields().lastOrNull { f -> f.id == 8 }?.bytes }
            val config = processorBytes?.let { WireReader(it).fields() }.orEmpty()
            fun stringField(id: Int) = config.lastOrNull { it.id == id }?.bytes?.toString(Charsets.UTF_8)
            val legacy = processor == 8 && templateHash == "02b3091acf53c0b722e3db0c7a1b4980363edcc2d85549dafa339ff5dbfff629" &&
                stringField(5) == "<|tool_call>" && stringField(6) == "<tool_call|>" &&
                stringField(12) == "<|tool_response>" && config.lastOrNull { it.id == 13 }?.number == 1L &&
                fields.filter { it.id == 8 }.any { channel -> channel.bytes?.let { WireReader(it).fields().any { f -> f.id == 1 && f.bytes?.toString(Charsets.UTF_8) == "thought" } } == true }
            fun declared(id: Int) = fields.lastOrNull { it.id == id && it.number != null }?.number?.let { it != 0L }
            return LiteRtBundleMetadata(processor, context, role, declared(11), declared(12), templateHash, legacy)
        }
    }
}

/** Only the four tables/offsets needed by the pinned official container schema. */
private class BoundedTables(private val bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun range(at: Int, size: Int) { require(at >= 0 && size >= 0 && at.toLong() + size <= bytes.size) { "Truncated LiteRT metadata table" } }
    private fun int(at: Int): Int { range(at, 4); return buffer.getInt(at) }
    fun uint(at: Int): Int = int(at).also { require(it >= 0) { "LiteRT metadata offset exceeds its bound" } }
    private fun short(at: Int): Int { range(at, 2); return buffer.getShort(at).toInt() and 0xffff }
    fun byte(at: Int): Int { range(at, 1); return buffer.get(at).toInt() and 0xff }
    fun long(at: Int): Long { range(at, 8); return buffer.getLong(at) }
    fun reference(at: Int): Int {
        val offset = uint(at)
        require(offset > 0 && at.toLong() + offset < bytes.size) { "Invalid LiteRT metadata reference" }
        return at + offset
    }
    fun field(table: Int, index: Int): Int {
        val vtableLong = table.toLong() - int(table)
        require(vtableLong in 0..bytes.size.toLong()) { "Invalid LiteRT metadata vtable" }
        val vtable = vtableLong.toInt()
        val length = short(vtable)
        range(vtable, length)
        val objectLength = short(vtable + 2)
        range(table, objectLength)
        val entry = 4 + index * 2
        require(entry + 2 <= length) { "Missing LiteRT metadata field" }
        val offset = short(vtable + entry)
        require(offset in 4 until objectLength) { "Missing LiteRT metadata field" }
        return table + offset
    }
}

private data class WireField(val id: Int, val number: Long? = null, val bytes: ByteArray? = null)
private class WireReader(private val bytes: ByteArray) {
    private var at = 0
    private fun varint(): Long {
        var result = 0L
        for (shift in 0..63 step 7) {
            require(at < bytes.size) { "Truncated LiteRT metadata varint" }
            val value = bytes[at++].toInt() and 0xff
            require(shift < 63 || value <= 1) { "Overflow in LiteRT metadata varint" }
            result = result or ((value and 127).toLong() shl shift)
            if (value and 128 == 0) return result
        }
        throw IllegalArgumentException("Overflow in LiteRT metadata varint")
    }
    fun fields(): List<WireField> = buildList {
        while (at < bytes.size) {
            val tag = varint()
            require(tag ushr 3 in 1..536_870_911) { "Invalid LiteRT metadata proto tag" }
            val id = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> add(WireField(id, number = varint()))
                1, 5 -> {
                    val count = if (tag and 7 == 1L) 8 else 4
                    require(at + count <= bytes.size) { "Truncated LiteRT metadata scalar" }; at += count
                }
                2 -> {
                    val length = varint()
                    require(length in 0..(bytes.size - at).toLong()) { "Truncated LiteRT metadata value" }
                    add(WireField(id, bytes = bytes.copyOfRange(at, at + length.toInt())))
                    at += length.toInt()
                }
                else -> throw IllegalArgumentException("Unsupported LiteRT metadata wire type")
            }
        }
    }
}
