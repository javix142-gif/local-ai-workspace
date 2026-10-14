package com.localai.workspace.artifacts

import android.content.Context
import com.localai.workspace.domain.tools.*
import com.localai.workspace.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import org.json.JSONArray

/** Creates private drafts/new revisions only. Export/overwriting originals is a user SAF action. */
class ArtifactTool(context:Context,private val operation:String):LocalTool {
    private val store=ArtifactStore(context)
    override val timeoutMs=20_000L
    override val descriptor=ToolDescriptor(operation,setOf(if(operation in setOf("artifacts.list","artifacts.read"))ToolPermission.READ_LOCAL else ToolPermission.WRITE_LOCAL),ConfirmationPolicy.NEVER)
    override fun validate(arguments:JSONObject):String?=try {
        val fields=when(operation){"artifacts.list"->emptySet();"artifacts.read"->setOf("id")+if(arguments.has("version"))setOf("version")else emptySet();"artifacts.create"->setOf("spec");"artifacts.revise"->setOf("id","expectedVersion","spec");else->error("ARTIFACT_OPERATION_UNSUPPORTED")}
        require(arguments.keys().asSequence().toSet()==fields){"ARTIFACT_ARGUMENTS_INVALID"}
        if(operation in setOf("artifacts.create","artifacts.revise"))ArtifactSpec.parse(arguments.getJSONObject("spec"))
        if(operation=="artifacts.read")require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&(!arguments.has("version")||arguments.getInt("version") in 1..1000)){"ARTIFACT_ID_OR_VERSION_INVALID"}
        if(operation=="artifacts.revise"){require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&arguments.getInt("expectedVersion")>0){"ARTIFACT_REVISION_INVALID"}}
        null
    }catch(e:Exception){e.message ?: "ARTIFACT_ARGUMENTS_INVALID"}
    override suspend fun execute(arguments:JSONObject,context:ToolContext):String=runInterruptible(Dispatchers.IO) {
        if(operation=="artifacts.list")JSONObject().put("artifacts",JSONArray(store.list(context.projectId).take(50).map{it.json()})).put("listingLimit",50).toString()
        else if(operation=="artifacts.read") {
            val history=store.history(context.projectId,arguments.getString("id"))
            val revision=if(arguments.has("version"))history.singleOrNull{it.version==arguments.getInt("version")}else history.firstOrNull()
            requireNotNull(revision){"ARTIFACT_VERSION_NOT_FOUND"};store.verifiedFile(context.projectId,revision)
            val output=JSONObject().put("artifact",revision.json()).put("spec",store.spec(context.projectId,revision).json()).toString()
            if(output.length>12000)throw ToolExecutionException("ARTIFACT_DESCRIPTOR_TOO_LARGE","Use the Artifacts editor for this large descriptor; it was not silently truncated.")
            output
        } else {
            val spec=ArtifactSpec.parse(arguments.getJSONObject("spec"))
            val revision=if(operation=="artifacts.create")store.create(context.projectId,spec)else store.revise(context.projectId,arguments.getString("id"),arguments.getInt("expectedVersion"),spec)
            store.verifiedFile(context.projectId,revision)
            JSONObject().put("artifact",revision.json()).put("state","PRIVATE_DRAFT_VERIFIED")
                .put("note","Actual bytes created locally. Open Artifacts in this chat to preview, edit versions and export. Original imports are unchanged. Formulas not recalculated; CODE not executed; PDF regenerated; PPTX text-only.").toString()
        }
    }
    companion object {val IDS=setOf("artifacts.create","artifacts.revise","artifacts.list","artifacts.read")}
}
