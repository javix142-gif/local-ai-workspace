package com.localai.workspace.semantic.v2

import com.localai.workspace.semantic.EmbeddingModels

/** Adapter only: EG1's JNI, import preferences and semantic_vectors are unchanged. */
class LegacyEmbeddingProvider(private val models: EmbeddingModels) : EmbeddingProviderV2 {
    override suspend fun embed(input: SemanticInput, task: EmbeddingTask, outputDimension: Int): EmbeddingResult {
        if(outputDimension!=768)throw SemanticFailure(SemanticError.DIMENSION_UNSUPPORTED)
        val text=when(input) { is SemanticInput.Text->TaskPromptProfile.format(task,input.content,input.title);is SemanticInput.Code->TaskPromptProfile.format(task,input.content,input.filename);else->throw SemanticFailure(SemanticError.MODALITY_UNSUPPORTED) }
        return models.use { encoder ->
            val hash=encoder.modelId.split(':').getOrNull(1) ?: throw SemanticFailure(SemanticError.MODEL_HASH_FAILED)
            val start=System.nanoTime();val vector=SemanticVectors.normalize(encoder.embed(text))
            if(vector.size!=768)throw SemanticFailure(SemanticError.DIMENSION_UNSUPPORTED)
            EmbeddingResult(EmbeddingSpaceKey("gguf-jni","EMBEDDING_GEMMA_1",hash,"legacy-v1",768),vector,(System.nanoTime()-start)/1_000_000)
        } ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
    }
    override suspend fun unload() = Unit // Existing provider closes each scoped native use.
}
