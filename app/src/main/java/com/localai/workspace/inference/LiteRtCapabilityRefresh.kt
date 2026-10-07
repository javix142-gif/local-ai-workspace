package com.localai.workspace.inference

import com.localai.workspace.data.ModelDao
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** Additive repair of imported declarations; never changes config, identities or multimodal bits. */
object LiteRtCapabilityRefresh {
    suspend fun refresh(dao: ModelDao, runtime: LiteRtLmInferenceRuntime) = withContext(Dispatchers.IO) {
        dao.observeAll().first().filter { it.runtimeId.contains("litert",true) }.forEach { model ->
            try {
                val (detected,evidence)=runtime.inspectCapabilityEvidence(ModelSource(model.localPath,model.displayName,sha256=model.fileHash))
                val previous=ModelDescriptorCodec.decodeCapabilities(model.capabilities)
                val features=setOf(ModelCapability.TOOL_CALLING,ModelCapability.THINKING)
                val caps=(previous-features)+(detected intersect features)
                dao.updateCapabilityEvidence(model.id,ModelDescriptorCodec.encodeCapabilities(caps),
                    ModelDescriptorCodec.encodeMetadata(ModelDescriptorCodec.decodeMetadata(model.metadataJson)+evidence-"capability.detectionError"))
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) {
                dao.updateCapabilityEvidence(model.id,model.capabilities,ModelDescriptorCodec.encodeMetadata(
                    ModelDescriptorCodec.decodeMetadata(model.metadataJson)+mapOf("capability.detectionError" to "CAPABILITY_DETECTION_ERROR")))
            }
        }
    }
}
