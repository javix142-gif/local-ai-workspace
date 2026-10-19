package com.localai.workspace.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets

/** Bounded JSON storage for resolved sheet-qualified cell identities attached to citations. */
internal object CitationCellReferenceCodec {
    const val MAX_REFERENCES = 64
    const val MAX_REFERENCE_CHARS = 256
    const val MAX_ENCODED_UTF8_BYTES = 8_192
    private val gson = Gson()

    fun encode(references: List<String>): String {
        require(references.size <= MAX_REFERENCES) { "Too many citation cell references" }
        require(references.distinct().size == references.size) { "Duplicate citation cell references" }
        require(references.all { it.isNotBlank() && it.length <= MAX_REFERENCE_CHARS }) {
            "Invalid citation cell reference"
        }
        val json = gson.toJson(references)
        require(json.toByteArray(StandardCharsets.UTF_8).size <= MAX_ENCODED_UTF8_BYTES) {
            "Citation cell references exceed the storage limit"
        }
        return json
    }

    /** Null signals corrupt or out-of-policy metadata; callers must keep the saved excerpt visible. */
    fun decode(json: String): List<String>? {
        if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_ENCODED_UTF8_BYTES) return null
        return try {
            val parsed = JsonParser.parseString(json)
            if (!parsed.isJsonArray) return null
            val array = parsed.asJsonArray
            if (array.size() > MAX_REFERENCES) return null
            val references = ArrayList<String>(array.size())
            for (element in array) {
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
                val value = element.asString
                if (value.isBlank() || value.length > MAX_REFERENCE_CHARS) return null
                references += value
            }
            references.takeIf { it.distinct().size == it.size }
        } catch (_: Exception) {
            null
        }
    }
}
