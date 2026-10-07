package com.localai.workspace.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.localai.workspace.domain.inference.InferenceRuntimeRegistry
import com.localai.workspace.domain.model.AcceleratorType
import com.localai.workspace.domain.model.ModelBundleStatus
import com.localai.workspace.domain.model.ModelCapability
import com.localai.workspace.domain.model.ModelCompatibilityStatus
import com.localai.workspace.domain.model.ModelDescriptorCodec
import com.localai.workspace.domain.model.ModelFormat
import com.localai.workspace.domain.model.ModelFormatDetector
import com.localai.workspace.domain.model.ModelImportStatus
import com.localai.workspace.domain.model.ModelMetadata
import com.localai.workspace.domain.model.ModelSource
import com.localai.workspace.domain.model.ModelSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class ModelImportProgress(
    val operationId: String,
    val displayName: String,
    val status: ModelImportStatus,
    val bytesCompleted: Long = 0L,
    val totalBytes: Long? = null,
    val message: String? = null,
)

data class ModelImportResult(
    val id: String,
    val displayName: String,
    val warning: String?,
    val status: ModelImportStatus = ModelImportStatus.READY,
    val format: ModelFormat = ModelFormat.UNKNOWN,
)

class ModelImportException(
    message: String,
    val status: ModelImportStatus = ModelImportStatus.FAILED,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Imports model bytes into app-private storage. A URI is only read through the
 * Storage Access Framework; no model binary is stored in Room and no partial
 * file is ever exposed as a library entry.
 */
class ModelImportService(
    private val context: Context,
    private val modelDao: ModelDao,
    private val runtimes: InferenceRuntimeRegistry,
) {
    suspend fun import(
        uri: Uri,
        requestedFormat: ModelFormat = ModelFormat.UNKNOWN,
        sourceType: ModelSourceType = ModelSourceType.LOCAL_IMPORT,
        sourceRepository: String? = null,
        onProgress: (ModelImportProgress) -> Unit = {},
    ): Result<ModelImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val operationId = UUID.randomUUID().toString()
            val displayName = displayName(uri)
            val format = requestedFormat.takeUnless { it == ModelFormat.UNKNOWN }
                ?: ModelFormatDetector.fromFilename(displayName)
            requireSupportedFormat(format, displayName)
            val sourceSize = querySize(uri)
            validateStorage(sourceSize ?: 0L)
            validateSourceHeader(uri, format, displayName)
            emit(onProgress, operationId, displayName, ModelImportStatus.INSPECTING, 0L, sourceSize)

            val stagingDir = stagingDirectory(operationId)
            val staged = File(stagingDir, ModelFormatDetector.sanitizeFilename(displayName, "model.${format.extension}"))
            try {
                val copied = copyUri(uri, staged, sourceSize) { completed ->
                    emit(onProgress, operationId, displayName, ModelImportStatus.IMPORTING, completed, sourceSize)
                }
                val runtime = runtimes.runtimeFor(format)
                val metadata = runtime.inspectModel(
                    ModelSource(
                        absolutePath = staged.absolutePath,
                        displayName = displayName,
                        sourceUri = uri.toString(),
                        format = format,
                        sourceType = sourceType,
                    ),
                )
                if (metadata.compatibility == ModelCompatibilityStatus.CORRUPT_OR_INCOMPLETE) {
                    throw ModelImportException(
                        metadata.warning ?: "The model is corrupt or incomplete.",
                        ModelImportStatus.CORRUPT,
                    )
                }
                val existing = modelDao.findByHash(copied.sha256)
                if (existing != null) {
                    emit(
                        onProgress,
                        operationId,
                        displayName,
                        ModelImportStatus.DUPLICATE,
                        copied.bytes,
                        sourceSize,
                        "Model already exists.",
                    )
                    return@runCatching ModelImportResult(
                        id = existing.id,
                        displayName = existing.displayName,
                        warning = "Model already exists.",
                        status = ModelImportStatus.DUPLICATE,
                        format = format,
                    )
                }
                val id = UUID.randomUUID().toString()
                val modelDir = File(modelsDirectory(), id).apply { mkdirs() }
                val finalFile = File(modelDir, ModelFormatDetector.sanitizeFilename(displayName, "model.${format.extension}"))
                moveAtomically(staged, finalFile)
                val status = persistModel(
                    id = id,
                    displayName = displayName,
                    finalFile = finalFile,
                    sourceUri = uri.toString(),
                    sourceType = sourceType,
                    sourceRepository = sourceRepository,
                    originalFilename = displayName,
                    hash = copied.sha256,
                    size = copied.bytes,
                    format = format,
                    runtimeId = runtime.runtimeId,
                    metadata = metadata,
                )
                emit(onProgress, operationId, displayName, status, copied.bytes, sourceSize, metadata.warning)
                ModelImportResult(id, displayName, metadata.warning, status, format)
            } catch (error: Throwable) {
                stagingDir.deleteRecursively()
                throw error
            } finally {
                stagingDir.deleteRecursively()
            }
        }.recoverCatching { error ->
            if (error is CancellationException || error is ModelImportException) throw error
            throw ModelImportException(error.message ?: "Model import failed", cause = error)
        }
    }

    /** Imports a primary GGUF plus a projector as one library record. */
    suspend fun importMultimodal(
        primaryUri: Uri,
        projectorUri: Uri,
        sourceType: ModelSourceType = ModelSourceType.LOCAL_IMPORT,
        sourceRepository: String? = null,
        onProgress: (ModelImportProgress) -> Unit = {},
    ): Result<ModelImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val operationId = UUID.randomUUID().toString()
            val primaryName = displayName(primaryUri)
            val projectorName = displayName(projectorUri)
            requireSupportedFormat(ModelFormat.GGUF, primaryName)
            require(ModelFormatDetector.fromFilename(projectorName) == ModelFormat.GGUF) {
                "The projector must be a .gguf file."
            }
            require(projectorName.contains("mmproj", true) || projectorName.contains("projector", true)) {
                "Select the GGUF projector file (usually named mmproj*.gguf)."
            }
            validateStorage((querySize(primaryUri) ?: 0L) + (querySize(projectorUri) ?: 0L))
            validateSourceHeader(primaryUri, ModelFormat.GGUF, primaryName)
            validateSourceHeader(projectorUri, ModelFormat.GGUF, projectorName)
            emit(onProgress, operationId, primaryName, ModelImportStatus.INSPECTING, 0L, querySize(primaryUri))

            val stagingDir = stagingDirectory(operationId)
            val stagedPrimary = File(stagingDir, ModelFormatDetector.sanitizeFilename(primaryName, "model.gguf"))
            val stagedProjector = File(stagingDir, ModelFormatDetector.sanitizeFilename(projectorName, "mmproj.gguf"))
            try {
                val primary = copyUri(primaryUri, stagedPrimary, querySize(primaryUri)) { completed ->
                    emit(onProgress, operationId, primaryName, ModelImportStatus.IMPORTING, completed, querySize(primaryUri))
                }
                val projector = copyUri(projectorUri, stagedProjector, querySize(projectorUri)) { completed ->
                    emit(onProgress, operationId, projectorName, ModelImportStatus.IMPORTING, completed, querySize(projectorUri))
                }
                val runtime = runtimes.runtimeFor(ModelFormat.GGUF)
                val metadata = runtime.inspectModel(
                    ModelSource(
                        absolutePath = stagedPrimary.absolutePath,
                        displayName = primaryName,
                        sourceUri = primaryUri.toString(),
                        format = ModelFormat.GGUF,
                        auxiliaryFiles = listOf(stagedProjector.absolutePath),
                        sourceType = sourceType,
                    ),
                )
                if (metadata.compatibility == ModelCompatibilityStatus.CORRUPT_OR_INCOMPLETE) {
                    throw ModelImportException(metadata.warning ?: "The primary GGUF is corrupt.", ModelImportStatus.CORRUPT)
                }
                modelDao.findByHash(primary.sha256)?.let { existing ->
                    emit(onProgress, operationId, primaryName, ModelImportStatus.DUPLICATE, primary.bytes, primary.bytes, "Model already exists.")
                    return@runCatching ModelImportResult(existing.id, existing.displayName, "Model already exists.", ModelImportStatus.DUPLICATE, ModelFormat.GGUF)
                }
                val id = UUID.randomUUID().toString()
                val modelDir = File(modelsDirectory(), id).apply { mkdirs() }
                val finalPrimary = File(modelDir, ModelFormatDetector.sanitizeFilename(primaryName, "model.gguf"))
                val finalProjector = File(modelDir, ModelFormatDetector.sanitizeFilename(projectorName, "mmproj.gguf"))
                moveAtomically(stagedPrimary, finalPrimary)
                moveAtomically(stagedProjector, finalProjector)
                val auxiliary = ModelDescriptorCodec.encodeFiles(
                    listOf(
                        com.localai.workspace.domain.model.ModelFileDescriptor(
                            role = "projector",
                            displayName = projectorName,
                            absolutePath = finalProjector.absolutePath,
                            sizeBytes = projector.bytes,
                            sha256 = projector.sha256,
                        ),
                    ),
                )
                val status = persistModel(
                    id = id,
                    displayName = primaryName.substringBeforeLast('.'),
                    finalFile = finalPrimary,
                    sourceUri = primaryUri.toString(),
                    sourceType = sourceType,
                    sourceRepository = sourceRepository,
                    originalFilename = primaryName,
                    hash = primary.sha256,
                    size = primary.bytes,
                    format = ModelFormat.GGUF,
                    runtimeId = runtime.runtimeId,
                    metadata = metadata,
                    auxiliaryFiles = auxiliary,
                    bundleStatus = ModelBundleStatus.COMPLETE,
                    warningOverride = metadata.warning ?: "Text supported. Vision unsupported by the current llama.cpp runtime.",
                )
                emit(onProgress, operationId, primaryName, status, primary.bytes + projector.bytes, primary.bytes + projector.bytes, metadata.warning)
                ModelImportResult(id, primaryName.substringBeforeLast('.'), metadata.warning, status, ModelFormat.GGUF)
            } catch (error: Throwable) {
                stagingDir.deleteRecursively()
                throw error
            } finally {
                stagingDir.deleteRecursively()
            }
        }.recoverCatching { error ->
            if (error is CancellationException || error is ModelImportException) throw error
            throw ModelImportException(error.message ?: "Multimodal import failed", cause = error)
        }
    }

    /** Finalizes a verified download without putting its bytes in Room. */
    suspend fun importStagedFile(
        sourceFile: File,
        displayName: String = sourceFile.name,
        format: ModelFormat = ModelFormatDetector.fromFilename(sourceFile.name),
        sourceType: ModelSourceType = ModelSourceType.HUGGING_FACE,
        sourceRepository: String? = null,
        sourceUri: String? = null,
        onProgress: (ModelImportProgress) -> Unit = {},
    ): Result<ModelImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceFile.isFile) { "Downloaded model file is missing" }
            requireSupportedFormat(format, displayName)
            validateStorage(sourceFile.length())
            validateHeader(sourceFile, format, displayName)
            val operationId = UUID.randomUUID().toString()
            val stagingDir = stagingDirectory(operationId)
            val staged = File(stagingDir, ModelFormatDetector.sanitizeFilename(displayName, "model.${format.extension}"))
            try {
                val copied = copyFile(sourceFile, staged) { completed ->
                    emit(onProgress, operationId, displayName, ModelImportStatus.IMPORTING, completed, sourceFile.length())
                }
                val runtime = runtimes.runtimeFor(format)
                val metadata = runtime.inspectModel(
                    ModelSource(staged.absolutePath, displayName, sourceUri, format = format, sourceType = sourceType),
                )
                if (metadata.compatibility == ModelCompatibilityStatus.CORRUPT_OR_INCOMPLETE) {
                    throw ModelImportException(metadata.warning ?: "The downloaded model is corrupt.", ModelImportStatus.CORRUPT)
                }
                modelDao.findByHash(copied.sha256)?.let { existing ->
                    return@runCatching ModelImportResult(existing.id, existing.displayName, "Model already exists.", ModelImportStatus.DUPLICATE, format)
                }
                val id = UUID.randomUUID().toString()
                val modelDir = File(modelsDirectory(), id).apply { mkdirs() }
                val finalFile = File(modelDir, ModelFormatDetector.sanitizeFilename(displayName, "model.${format.extension}"))
                moveAtomically(staged, finalFile)
                val status = persistModel(
                    id, displayName, finalFile, sourceUri, sourceType, sourceRepository, displayName,
                    copied.sha256, copied.bytes, format, runtime.runtimeId, metadata,
                )
                emit(onProgress, operationId, displayName, status, copied.bytes, sourceFile.length(), metadata.warning)
                ModelImportResult(id, displayName, metadata.warning, status, format)
            } finally {
                stagingDir.deleteRecursively()
            }
        }.recoverCatching { error ->
            if (error is CancellationException || error is ModelImportException) throw error
            throw ModelImportException(error.message ?: "Downloaded model import failed", cause = error)
        }
    }

    suspend fun delete(model: ModelEntity) = withContext(Dispatchers.IO) {
        val root = modelsDirectory().canonicalFile
        val directory = File(model.localPath).canonicalFile
        require(directory.path.startsWith(root.path + File.separator)) { "Refusing to delete a file outside model storage" }
        directory.parentFile?.takeIf { it.parentFile?.canonicalFile == root }?.deleteRecursively()
            ?: directory.delete()
        modelDao.delete(model)
    }

    private suspend fun persistModel(
        id: String,
        displayName: String,
        finalFile: File,
        sourceUri: String?,
        sourceType: ModelSourceType,
        sourceRepository: String?,
        originalFilename: String?,
        hash: String,
        size: Long,
        format: ModelFormat,
        runtimeId: String,
        metadata: ModelMetadata,
        auxiliaryFiles: String = "",
        bundleStatus: ModelBundleStatus = ModelBundleStatus.COMPLETE,
        warningOverride: String? = metadata.warning,
    ): ModelImportStatus {
        val status = metadata.toImportStatus()
        val now = System.currentTimeMillis()
        val capabilities = metadata.capabilities.ifEmpty {
            buildSet {
                if (metadata.supportsTextGeneration) add(ModelCapability.TEXT)
                if (metadata.supportsVision) add(ModelCapability.VISION)
                if (metadata.supportsAudio) add(ModelCapability.AUDIO)
                if (metadata.supportsToolCalling) add(ModelCapability.TOOL_CALLING)
                if (metadata.supportsThinking) add(ModelCapability.THINKING)
                if (metadata.supportsEmbeddings) add(ModelCapability.EMBEDDING)
                if (metadata.supportsImageGeneration) add(ModelCapability.IMAGE_GENERATION)
                if (metadata.supportsSpeculativeDecoding) add(ModelCapability.SPECULATIVE_DECODING)
            }
        }
        modelDao.insert(
            ModelEntity(
                id = id,
                displayName = displayName,
                localPath = finalFile.absolutePath,
                sourceUri = sourceUri,
                fileHash = hash,
                fileSize = size,
                format = format.name,
                family = metadata.family,
                architecture = metadata.architecture,
                quantization = metadata.quantization,
                parameterLabel = metadata.parameterLabel,
                parameterCount = metadata.parameterCount,
                declaredContext = metadata.declaredContextLength,
                runtimeId = runtimeId,
                sourceType = sourceType.name,
                auxiliaryFiles = auxiliaryFiles,
                compatibilityStatus = metadata.compatibility.name,
                compatibilityWarning = warningOverride,
                capabilities = ModelDescriptorCodec.encodeCapabilities(capabilities),
                accelerators = ModelDescriptorCodec.encodeAccelerators(metadata.accelerators),
                preferredAccelerator = AcceleratorType.CPU.name,
                importStatus = status.name,
                bundleStatus = bundleStatus.name,
                metadataJson = ModelDescriptorCodec.encodeMetadata(
                    mapOfNotNull("tokenizer.chat_template" to metadata.tokenizerChatTemplate) + metadata.capabilityEvidence,
                ),
                sourceRepository = sourceRepository,
                originalFilename = originalFilename,
                backendVersion = metadata.backendVersion,
                recommendedContext = metadata.recommendedContextLength,
                importedAt = now,
                updatedAt = now,
            ),
        )
        if (format == ModelFormat.LITERT_LM) {
            com.localai.workspace.inference.LiteRtTrace.event(
                "LITERT_IMPORT_OK", com.localai.workspace.domain.inference.GenerationStage.IDLE,
                displayName, fields = mapOf("sizeBytes" to size, "hashAvailable" to hash.isNotBlank()),
            )
        }
        return status
    }

    private fun ModelMetadata.toImportStatus(): ModelImportStatus = when (compatibility) {
        ModelCompatibilityStatus.COMPATIBLE -> ModelImportStatus.READY
        ModelCompatibilityStatus.COMPATIBLE_WITH_WARNING,
        ModelCompatibilityStatus.INSUFFICIENT_MEMORY_RISK,
        ModelCompatibilityStatus.UNKNOWN,
        -> ModelImportStatus.COMPATIBLE_WARNING
        ModelCompatibilityStatus.UNSUPPORTED_ARCHITECTURE -> ModelImportStatus.UNSUPPORTED_ARCHITECTURE
        ModelCompatibilityStatus.UNSUPPORTED_FEATURE -> ModelImportStatus.UNSUPPORTED_FEATURE
        ModelCompatibilityStatus.CORRUPT_OR_INCOMPLETE -> ModelImportStatus.CORRUPT
    }

    private fun requireSupportedFormat(format: ModelFormat, name: String) {
        require(format != ModelFormat.UNKNOWN) {
            "Unsupported model format. Select a .gguf or .litertlm file: $name"
        }
        require(runtimes.supports(format)) { "No runtime is installed for ${format.name}" }
    }

    private fun validateStorage(bytes: Long) {
        val available = StatFs(context.filesDir.path).availableBytes
        val required = bytes + 64L * 1024L * 1024L
        if (bytes > 0 && available < required) {
            throw ModelImportException(
                "Not enough private storage. Required approximately $bytes bytes; available $available bytes.",
                ModelImportStatus.INSUFFICIENT_STORAGE,
            )
        }
    }

    private fun validateSourceHeader(uri: Uri, format: ModelFormat, name: String) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected model could not be opened" }
            val bytes = readPrefix(input, if (format == ModelFormat.LITERT_LM) 12 else 4)
            val count = bytes.size
            val valid = when (format) {
                ModelFormat.GGUF -> ModelFormatDetector.hasGgufMagic(bytes)
                ModelFormat.LITERT_LM -> count >= 8 && ModelFormatDetector.hasLiteRtLmMagic(bytes)
                ModelFormat.UNKNOWN -> false
            }
            if (!valid) throw ModelImportException("Invalid ${format.name} header: $name", ModelImportStatus.CORRUPT)
        }
    }

    private fun validateHeader(file: File, format: ModelFormat, name: String) {
        FileInputStream(file).use { input ->
            val bytes = readPrefix(input, if (format == ModelFormat.LITERT_LM) 12 else 4)
            val count = bytes.size
            val valid = when (format) {
                ModelFormat.GGUF -> ModelFormatDetector.hasGgufMagic(bytes)
                ModelFormat.LITERT_LM -> count >= 8 && ModelFormatDetector.hasLiteRtLmMagic(bytes)
                ModelFormat.UNKNOWN -> false
            }
            if (!valid) throw ModelImportException("Invalid ${format.name} header: $name", ModelImportStatus.CORRUPT)
        }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                return ModelFormatDetector.sanitizeFilename(cursor.getString(0), "model.gguf")
            }
        }
        return ModelFormatDetector.sanitizeFilename(uri.lastPathSegment ?: "model.gguf", "model.gguf")
    }

    private fun querySize(uri: Uri): Long? = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null).use { cursor ->
        if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }

    private fun stagingDirectory(operationId: String): File = File(modelsDirectory(), ".partial/$operationId").apply { mkdirs() }

    private fun modelsDirectory(): File = File(context.filesDir, "models").apply { mkdirs() }

    private suspend fun copyUri(uri: Uri, target: File, expectedSize: Long?, onProgress: (Long) -> Unit): CopyResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ModelImportException("The selected model could not be opened")
        return input.use { copyStream(it, target, expectedSize, onProgress) }
    }

    private suspend fun copyFile(source: File, target: File, onProgress: (Long) -> Unit): CopyResult =
        FileInputStream(source).use { copyStream(it, target, source.length(), onProgress) }

    private suspend fun copyStream(input: InputStream, target: File, expectedSize: Long?, onProgress: (Long) -> Unit): CopyResult {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                total += read
                onProgress(total)
            }
            output.fd.sync()
        }
        if (expectedSize != null && expectedSize >= 0 && expectedSize != total) {
            target.delete()
            throw ModelImportException("Model size changed during import", ModelImportStatus.CORRUPT)
        }
        return CopyResult(total, digest.digest().toHex())
    }

    private fun moveAtomically(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            throw ModelImportException("Could not finalize model file in private storage")
        }
    }

    private fun readPrefix(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read < 0) break
            if (read == 0) continue
            offset += read
        }
        return if (offset == length) bytes else bytes.copyOf(offset)
    }

    private fun emit(
        callback: (ModelImportProgress) -> Unit,
        operationId: String,
        name: String,
        status: ModelImportStatus,
        completed: Long,
        total: Long?,
        message: String? = null,
    ) = callback(ModelImportProgress(operationId, name, status, completed, total, message))

    private data class CopyResult(val bytes: Long, val sha256: String)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun <K, V> mapOfNotNull(pair: Pair<K, V?>): Map<K, V> =
        if (pair.second == null) emptyMap() else mapOf(pair.first to pair.second!!)

    private val ModelFormat.extension: String
        get() = when (this) {
            ModelFormat.GGUF -> "gguf"
            ModelFormat.LITERT_LM -> "litertlm"
            ModelFormat.UNKNOWN -> "model"
        }
}
