package com.localai.workspace.artifacts

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Exact, bounded replacements for a private CODE draft. No path or executable payload is accepted. */
data class CodePatchEdit(val before: String, val after: String) {
    fun json() = JSONObject().put("before", before).put("after", after)
}

data class CodePatchPreview(
    val id: String,
    val artifactId: String,
    val fileName: String,
    val baseVersion: Int,
    val baseSha256: String,
    val proposedSha256: String,
    val createdAt: Long,
    val before: String,
    val after: String,
    val edits: List<CodePatchEdit>,
) {
    fun json() = JSONObject().put("id", id).put("artifactId", artifactId).put("fileName", fileName)
        .put("baseVersion", baseVersion).put("baseSha256", baseSha256).put("proposedSha256", proposedSha256)
        .put("createdAt", createdAt).put("before", before).put("after", after)
        .put("edits", JSONArray().apply { edits.forEach { put(it.json()) } })

    companion object {
        fun parse(json: JSONObject): CodePatchPreview {
            require(json.keys().asSequence().toSet() == setOf("id", "artifactId", "fileName", "baseVersion", "baseSha256", "proposedSha256", "createdAt", "before", "after", "edits")) { "CODE_PATCH_PREVIEW_INVALID" }
            val edits = json.getJSONArray("edits").let { array -> (0 until array.length()).map { index ->
                val edit = array.getJSONObject(index)
                require(edit.keys().asSequence().toSet() == setOf("before", "after")) { "CODE_PATCH_EDIT_INVALID" }
                CodePatchEdit(edit.getString("before"), edit.getString("after"))
            } }
            val preview = CodePatchPreview(json.getString("id"), json.getString("artifactId"), json.getString("fileName"),
                json.getInt("baseVersion"), json.getString("baseSha256"), json.getString("proposedSha256"),
                json.getLong("createdAt"), json.getString("before"), json.getString("after"), edits)
            CodePatchPlanner.validatePreview(preview)
            return preview
        }
    }
}

/** No fuzzy matching: every old snippet must occur exactly once in the current body. */
object CodePatchPlanner {
    const val MAX_EDITS = 32
    const val MAX_BODY_BYTES = 128 * 1024
    const val MAX_SNIPPET_BYTES = 32 * 1024

    fun apply(source: String, expectedSha256: String, edits: List<CodePatchEdit>): String {
        require(Regex("[0-9a-f]{64}").matches(expectedSha256)) { "CODE_PATCH_HASH_INVALID" }
        require(hash(source) == expectedSha256) { "CODE_PATCH_BASE_HASH_MISMATCH" }
        validateEdits(edits)
        require(bytes(source).size <= MAX_BODY_BYTES) { "CODE_PATCH_BODY_TOO_LARGE" }
        val located = edits.map { edit ->
            val first = source.indexOf(edit.before)
            require(first >= 0) { "CODE_PATCH_MATCH_NOT_FOUND" }
            require(source.indexOf(edit.before, first + 1) < 0) { "CODE_PATCH_MATCH_AMBIGUOUS" }
            LocatedEdit(first, first + edit.before.length, edit.after)
        }.sortedBy { it.start }
        located.zipWithNext().forEach { (left, right) ->
            require(left.end <= right.start) { "CODE_PATCH_MATCH_OVERLAP" }
        }
        var result = source
        located.asReversed().forEach { edit ->
            result = result.replaceRange(edit.start, edit.end, edit.after)
            require(bytes(result).size <= MAX_BODY_BYTES) { "CODE_PATCH_BODY_TOO_LARGE" }
        }
        return result
    }

    private data class LocatedEdit(val start: Int, val end: Int, val replacement: String)

    fun parseEdits(array: JSONArray): List<CodePatchEdit> {
        require(array.length() in 1..MAX_EDITS) { "CODE_PATCH_EDIT_COUNT_INVALID" }
        return (0 until array.length()).map { index ->
            val item=array.getJSONObject(index)
            require(item.keys().asSequence().toSet()==setOf("before","after")) { "CODE_PATCH_EDIT_INVALID" }
            CodePatchEdit(item.getString("before"),item.getString("after"))
        }.also(::validateEdits)
    }

    fun validateEdits(edits: List<CodePatchEdit>) {
        require(edits.size in 1..MAX_EDITS) { "CODE_PATCH_EDIT_COUNT_INVALID" }
        edits.forEach { edit ->
            require(edit.before.isNotEmpty()) { "CODE_PATCH_EMPTY_MATCH" }
            require(bytes(edit.before).size <= MAX_SNIPPET_BYTES && bytes(edit.after).size <= MAX_SNIPPET_BYTES) { "CODE_PATCH_SNIPPET_TOO_LARGE" }
            require(edit.before != edit.after) { "CODE_PATCH_NO_CHANGE" }
        }
    }

    fun validatePreview(preview: CodePatchPreview) {
        require(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(preview.id) &&
            Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(preview.artifactId)) { "CODE_PATCH_ID_INVALID" }
        require(preview.fileName.length in 1..100 && Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}._ -]*").matches(preview.fileName) && !preview.fileName.contains("..") && preview.baseVersion in 1..1000 && preview.createdAt > 0) { "CODE_PATCH_PREVIEW_INVALID" }
        require(Regex("[0-9a-f]{64}").matches(preview.baseSha256) && Regex("[0-9a-f]{64}").matches(preview.proposedSha256)) { "CODE_PATCH_HASH_INVALID" }
        require(bytes(preview.before).size <= MAX_BODY_BYTES && bytes(preview.after).size <= MAX_BODY_BYTES) { "CODE_PATCH_BODY_TOO_LARGE" }
        require(hash(preview.before) == preview.baseSha256 && hash(preview.after) == preview.proposedSha256) { "CODE_PATCH_PREVIEW_HASH_MISMATCH" }
        validateEdits(preview.edits)
        require(apply(preview.before, preview.baseSha256, preview.edits) == preview.after) { "CODE_PATCH_TRANSFORM_MISMATCH" }
    }

    fun newPreviewId() = UUID.randomUUID().toString()
    fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(bytes(value)).joinToString("") { "%02x".format(it) }
    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
}
