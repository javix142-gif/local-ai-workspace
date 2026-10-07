package com.localai.workspace.data

import com.localai.workspace.domain.model.ModelFormat
import com.localai.workspace.domain.model.ModelFormatDetector
import java.net.URI
import java.net.URLEncoder

data class HuggingFaceFile(
    val repositoryId: String,
    val path: String,
    val sizeBytes: Long,
    val sha256: String? = null,
    val format: ModelFormat = ModelFormatDetector.fromFilename(path),
    val quantization: String? = ModelFormatDetector.inferQuantization(path),
    val isAuxiliary: Boolean = path.substringAfterLast('/').contains("mmproj", true) ||
        path.substringAfterLast('/').contains("projector", true),
) {
    val runtimeLabel: String
        get() = when (format) {
            ModelFormat.GGUF -> "llama.cpp"
            ModelFormat.LITERT_LM -> "LiteRT-LM"
            ModelFormat.UNKNOWN -> "Unknown"
        }
}

data class HuggingFaceRepository(
    val repositoryId: String,
    val files: List<HuggingFaceFile>,
    val gated: Boolean = false,
)

data class HuggingFaceSearchResult(
    val repositoryId: String,
    val pipelineTag: String? = null,
    val downloads: Long? = null,
)

object HuggingFaceReferenceParser {
    private val repositoryPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,95}/[A-Za-z0-9][A-Za-z0-9_.-]{0,95}$")

    fun parseRepositoryId(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        if (value.isBlank() || value.contains("..") || value.contains('\\')) return null
        val candidate = if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", true) || !uri.host.equals("huggingface.co", true)) return null
            uri.path.trim('/').split('/').take(2).joinToString("/")
        } else {
            value.substringBefore('?').substringBefore('#')
        }
        return candidate.takeIf { repositoryPattern.matches(it) }
    }

    fun resolveUrl(repositoryId: String, path: String, revision: String = "main"): String {
        require(parseRepositoryId(repositoryId) == repositoryId) { "Invalid Hugging Face repository ID" }
        require(path.isNotBlank() && !path.split('/').any { it == ".." || it == "." }) { "Invalid model path" }
        require(revision.matches(Regex("^[A-Za-z0-9._/-]+$")) && !revision.contains("..")) { "Invalid revision" }
        val encodedPath = path.split('/').joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return "https://huggingface.co/$repositoryId/resolve/$revision/$encodedPath?download=true"
    }

    fun apiTreeUrl(repositoryId: String, revision: String = "main"): String {
        require(parseRepositoryId(repositoryId) == repositoryId) { "Invalid Hugging Face repository ID" }
        require(revision.matches(Regex("^[A-Za-z0-9._/-]+$")) && !revision.contains("..")) { "Invalid revision" }
        return "https://huggingface.co/api/models/$repositoryId/tree/$revision?recursive=true&expand=true"
    }
}

enum class ModelDownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, CANCELED, FAILED }

data class ModelDownloadProgress(
    val file: HuggingFaceFile,
    val state: ModelDownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long = 0L,
    val message: String? = null,
)

class HuggingFaceAuthenticationRequiredException(message: String) : Exception(message)
