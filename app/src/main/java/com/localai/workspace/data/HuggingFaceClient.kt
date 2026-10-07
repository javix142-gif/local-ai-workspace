package com.localai.workspace.data

import android.content.Context
import android.util.Base64
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Stores only an encrypted token blob; the key remains non-exportable in Android Keystore. */
class HuggingFaceTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun getToken(): String? = runCatching {
        val encoded = preferences.getString(TOKEN_KEY, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, IV_BYTES)
        val ciphertext = packed.copyOfRange(IV_BYTES, packed.size)
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(doFinal(ciphertext), Charsets.UTF_8)
        }
    }.getOrNull()

    fun setToken(token: String) {
        require(token.length <= MAX_TOKEN_LENGTH) { "Hugging Face token is too long" }
        val iv = ByteArray(IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            doFinal(token.trim().toByteArray(Charsets.UTF_8))
        }
        preferences.edit().putString(TOKEN_KEY, Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)).apply()
    }

    fun clearToken() = preferences.edit().remove(TOKEN_KEY).apply()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES = "hugging_face_secure"
        private const val TOKEN_KEY = "encrypted_token"
        private const val KEY_ALIAS = "local_ai_workspace_hf_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val MAX_TOKEN_LENGTH = 512
    }
}

class HuggingFaceClient(
    private val tokenStore: HuggingFaceTokenStore,
) {
    suspend fun listRepositoryFiles(repositoryId: String, showAll: Boolean = false): HuggingFaceRepository =
        withContext(Dispatchers.IO) {
            val normalized = HuggingFaceReferenceParser.parseRepositoryId(repositoryId)
                ?: throw IllegalArgumentException("Use an owner/repository ID or an https://huggingface.co URL")
            val response = request(HuggingFaceReferenceParser.apiTreeUrl(normalized))
            val root = JsonParser.parseString(response.body)
            if (!root.isJsonArray) throw IOException("Hugging Face returned an invalid file list")
            val files = root.asJsonArray.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val objectValue = element.asJsonObject
                if (objectValue.get("type")?.asString != "file") return@mapNotNull null
                val path = objectValue.get("path")?.asString ?: return@mapNotNull null
                if (path.contains("..")) return@mapNotNull null
                val format = com.localai.workspace.domain.model.ModelFormatDetector.fromFilename(path)
                val lowerName = path.substringAfterLast('/').lowercase()
                val auxiliary = lowerName.contains("mmproj") || lowerName.contains("projector")
                val recognizedAuxiliary = auxiliary || lowerName in setOf(
                    "tokenizer.json", "tokenizer.model", "tokenizer.model.v3", "config.json", "special_tokens_map.json",
                )
                if (!showAll && format == com.localai.workspace.domain.model.ModelFormat.UNKNOWN && !recognizedAuxiliary) {
                    return@mapNotNull null
                }
                val lfs = objectValue.getAsJsonObject("lfs")
                HuggingFaceFile(
                    repositoryId = normalized,
                    path = path,
                    sizeBytes = objectValue.get("size")?.asLong ?: lfs?.get("size")?.asLong ?: 0L,
                    sha256 = lfs?.get("sha256")?.asString,
                    format = format,
                    quantization = com.localai.workspace.domain.model.ModelFormatDetector.inferQuantization(path),
                    isAuxiliary = auxiliary,
                )
            }.sortedWith(compareByDescending<HuggingFaceFile> { it.format != com.localai.workspace.domain.model.ModelFormat.UNKNOWN }
                .thenByDescending { it.isAuxiliary }
                .thenBy { it.path })
            HuggingFaceRepository(normalized, files, gated = false)
        }

    suspend fun search(query: String, limit: Int = 20): List<HuggingFaceSearchResult> = withContext(Dispatchers.IO) {
        val safeQuery = query.trim().take(100)
        require(safeQuery.isNotBlank()) { "Enter a model name or repository search" }
        val encoded = java.net.URLEncoder.encode(safeQuery, Charsets.UTF_8.name())
        val response = request("https://huggingface.co/api/models?search=$encoded&limit=${limit.coerceIn(1, 50)}")
        val root = JsonParser.parseString(response.body)
        if (!root.isJsonArray) return@withContext emptyList()
        root.asJsonArray.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            if (HuggingFaceReferenceParser.parseRepositoryId(id) == null) return@mapNotNull null
            HuggingFaceSearchResult(
                repositoryId = id,
                pipelineTag = obj.get("pipeline_tag")?.takeUnless { it.isJsonNull }?.asString,
                downloads = obj.get("downloads")?.takeUnless { it.isJsonNull }?.asLong,
            )
        }
    }

    private fun request(urlString: String): HttpResponse {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeUnit.SECONDS.toMillis(20).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(60).toInt()
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            tokenStore.getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            val response = connection
            val status = response.responseCode
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw HuggingFaceAuthenticationRequiredException(
                    "Authentication required. Sign in to Hugging Face, accept any required license, and configure a token in the app.",
                )
            }
            val stream = if (status in 200..299) response.inputStream else response.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Hugging Face request failed ($status)")
            HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(val status: Int, val body: String)
}

class HuggingFaceDownloader(
    private val context: Context,
    private val tokenStore: HuggingFaceTokenStore,
) {
    suspend fun download(
        file: HuggingFaceFile,
        destinationName: String = file.path.substringAfterLast('/'),
        onProgress: (ModelDownloadProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val downloadsDirectory = java.io.File(context.filesDir, "models/.downloads").apply { mkdirs() }
        val safeName = com.localai.workspace.domain.model.ModelFormatDetector.sanitizeFilename(destinationName, "model.bin")
        val destination = java.io.File(downloadsDirectory, safeName)
        val partial = java.io.File(downloadsDirectory, ".$safeName.partial")
        val operationUrl = HuggingFaceReferenceParser.resolveUrl(file.repositoryId, file.path)
        var offset = partial.takeIf { it.isFile }?.length() ?: 0L
        var connection = open(operationUrl, offset)
        var responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
            connection.disconnect()
            throw HuggingFaceAuthenticationRequiredException("Authentication required")
        }
        if (offset > 0 && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect()
            offset = 0L
            partial.delete()
            connection = open(operationUrl, 0L)
            responseCode = connection.responseCode
        }
        try {
            val response = connection
            if (response.responseCode !in 200..299) throw IOException("Download failed (${response.responseCode})")
            val append = offset > 0 && response.responseCode == HttpURLConnection.HTTP_PARTIAL
            val startingBytes = if (append) offset else 0L
            val responseLength = response.contentLengthLong.takeIf { it >= 0L }
            val total = responseLength?.let { it + startingBytes } ?: file.sizeBytes.takeIf { it > 0L }
            onProgress(ModelDownloadProgress(file, ModelDownloadState.DOWNLOADING, startingBytes, total))
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            if (append) {
                java.io.FileInputStream(partial).use { existing ->
                    val buffer = ByteArray(1024 * 1024)
                    var read: Int
                    while (existing.read(buffer).also { read = it } >= 0) if (read > 0) digest.update(buffer, 0, read)
                }
            }
            response.inputStream.use { input ->
                java.io.FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var downloaded = startingBytes
                    var lastTime = System.nanoTime()
                    var lastBytes = downloaded
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        val now = System.nanoTime()
                        if (now - lastTime >= 250_000_000L) {
                            val speed = ((downloaded - lastBytes) * 1_000_000_000L / (now - lastTime)).coerceAtLeast(0L)
                            onProgress(ModelDownloadProgress(file, ModelDownloadState.DOWNLOADING, downloaded, total, speed))
                            lastTime = now
                            lastBytes = downloaded
                        }
                    }
                    output.fd.sync()
                    val finalDigest = digest.digest().joinToString("") { "%02x".format(it) }
                    val actualSize = partial.length()
                    if (file.sizeBytes > 0 && actualSize != file.sizeBytes) {
                        throw IOException("Downloaded size differs from the repository metadata")
                    }
                    if (file.sha256 != null && !file.sha256.equals(finalDigest, true)) {
                        partial.delete()
                        throw IOException("Downloaded SHA-256 does not match the repository metadata")
                    }
                    if (!partial.renameTo(destination)) throw IOException("Could not finalize the download")
                    onProgress(ModelDownloadProgress(file, ModelDownloadState.COMPLETED, actualSize, total, 0L))
                }
            }
        } finally {
            connection.disconnect()
        }
        destination
    }

    private fun open(urlString: String, offset: Long): HttpURLConnection =
        (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeUnit.SECONDS.toMillis(20).toInt()
            readTimeout = TimeUnit.MINUTES.toMillis(5).toInt()
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            tokenStore.getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
}
