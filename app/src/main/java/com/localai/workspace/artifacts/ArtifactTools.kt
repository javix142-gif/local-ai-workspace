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
        val fields=when(operation){"artifacts.list"->emptySet();"artifacts.read"->setOf("id")+if(arguments.has("version"))setOf("version")else emptySet();"artifacts.create"->setOf("spec");"artifacts.revise"->setOf("id","expectedVersion","spec");"artifacts.patchPreview"->setOf("id","expectedVersion","expectedSha256","edits");"artifacts.xlsxSetCells"->setOf("id","expectedVersion","edits");"artifacts.xlsxAddSheet"->setOf("id","expectedVersion","name");"artifacts.xlsxSetTable"->setOf("id","expectedVersion","sheet","name","range");else->error("ARTIFACT_OPERATION_UNSUPPORTED")}
        require(arguments.keys().asSequence().toSet()==fields){"ARTIFACT_ARGUMENTS_INVALID"}
        if(operation in setOf("artifacts.create","artifacts.revise"))ArtifactSpec.parse(arguments.getJSONObject("spec"))
        if(operation=="artifacts.read")require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&(!arguments.has("version")||arguments.getInt("version") in 1..1000)){"ARTIFACT_ID_OR_VERSION_INVALID"}
        if(operation=="artifacts.revise"){require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&arguments.getInt("expectedVersion")>0){"ARTIFACT_REVISION_INVALID"}}
        if(operation=="artifacts.xlsxAddSheet") {
            require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&arguments.getInt("expectedVersion") in 1..1000){"XLSX_REVISION_INVALID"}
            require(arguments.opt("name") is String){"XLSX_SHEET_NAME_INVALID"}
            val candidate=JSONObject().put("format","XLSX").put("name","sheet.xlsx")
                .put("sheets",JSONArray().put(JSONObject().put("name",arguments.getString("name")).put("cells",JSONArray())))
            ArtifactSpec.parse(candidate) // Reuse the exact sheet-name and XLSX descriptor limits.
        }
        if(operation=="artifacts.xlsxSetCells") {
            require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&arguments.getInt("expectedVersion") in 1..1000){"XLSX_REVISION_INVALID"}
            val edits=arguments.getJSONArray("edits");require(edits.length() in 1..64){"XLSX_CELL_EDIT_LIMIT_64"}
            val seen=mutableSetOf<String>()
            for(index in 0 until edits.length()) {
                val edit=edits.getJSONObject(index)
                require(edit.keys().asSequence().toSet().all{it in setOf("sheet","address","value","formula","type","style")} &&
                    setOf("sheet","address").all(edit::has) && (edit.has("value") xor edit.has("formula"))){"XLSX_CELL_EDIT_FIELDS_INVALID"}
                val sheet=edit.getString("sheet");require(sheet.length in 1..31){"XLSX_SHEET_NAME_INVALID"}
                val address=edit.getString("address");ArtifactSpec.address(address)
                require(seen.add("${sheet.lowercase(java.util.Locale.ROOT)}!$address")){"XLSX_CELL_EDIT_DUPLICATE"}
                if(edit.has("value"))edit.getString("value")
                if(edit.has("formula"))edit.getString("formula")
                if(edit.has("type"))require(edit.getString("type") in setOf("string","number","boolean")){"CELL_TYPE_UNSUPPORTED"}
                if(edit.has("style"))require(edit.getString("style") in setOf("plain","header")){"CELL_STYLE_UNSUPPORTED"}
            }
        }
        if(operation=="artifacts.xlsxSetTable") {
            require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&arguments.getInt("expectedVersion") in 1..1000){"XLSX_REVISION_INVALID"}
            val sheet=arguments.getString("sheet");require(sheet.length in 1..31){"XLSX_SHEET_NAME_INVALID"}
            ArtifactSpec.tableName(arguments.getString("name"))
            ArtifactSpec.cellRange(arguments.getString("range"))
        }
        if(operation=="artifacts.patchPreview") {
            require(Regex("[0-9a-f-]{36}").matches(arguments.getString("id"))&&arguments.getInt("expectedVersion") in 1..1000){"ARTIFACT_REVISION_INVALID"}
            require(Regex("[0-9a-f]{64}").matches(arguments.getString("expectedSha256"))){"CODE_PATCH_HASH_INVALID"}
            CodePatchPlanner.parseEdits(arguments.getJSONArray("edits"))
        }
        null
    }catch(e:Exception){e.message ?: "ARTIFACT_ARGUMENTS_INVALID"}
    override suspend fun execute(arguments:JSONObject,context:ToolContext):String=runInterruptible(Dispatchers.IO) {
        if(operation=="artifacts.list") {
            val items=store.list(context.projectId)
            JSONObject().put("artifacts",JSONArray(items.take(10).map{it.json()})).put("listingLimit",10).put("moreAvailable",items.size>10).toString()
        }
        else if(operation=="artifacts.read") {
            val history=store.history(context.projectId,arguments.getString("id"))
            val revision=if(arguments.has("version"))history.singleOrNull{it.version==arguments.getInt("version")}else history.firstOrNull()
            requireNotNull(revision){"ARTIFACT_VERSION_NOT_FOUND"};store.verifiedFile(context.projectId,revision)
            val output=JSONObject().put("artifact",revision.json()).put("spec",store.spec(context.projectId,revision).json()).toString()
            if(output.length>12000)throw ToolExecutionException("ARTIFACT_DESCRIPTOR_TOO_LARGE","Use the Artifacts editor for this large descriptor; it was not silently truncated.")
            output
        } else if(operation=="artifacts.patchPreview") {
            val preview=store.stageCodePatch(context.projectId,arguments.getString("id"),arguments.getInt("expectedVersion"),
                arguments.getString("expectedSha256"),CodePatchPlanner.parseEdits(arguments.getJSONArray("edits")))
            JSONObject().put("state","PATCH_PREVIEW_STAGED_NOT_APPLIED").put("previewId",preview.id)
                .put("artifactId",preview.artifactId).put("fileName",preview.fileName).put("baseVersion",preview.baseVersion)
                .put("baseSha256",preview.baseSha256).put("proposedSha256",preview.proposedSha256).put("editCount",preview.edits.size)
                .put("note","No code draft bytes were changed. Open Artifacts in this chat, review the exact before/after content, then explicitly apply as a new immutable local version. Export remains a separate SAF action; code is never executed.").toString()
        } else if(operation=="artifacts.xlsxSetCells") {
            val edits=arguments.getJSONArray("edits").let{array->(0 until array.length()).map{index->
                val cell=array.getJSONObject(index)
                XlsxCellEdit(cell.getString("sheet"),cell.getString("address"),
                    cell.optString("value").takeIf{cell.has("value")},cell.optString("formula").takeIf{cell.has("formula")},
                    cell.optString("type").takeIf{cell.has("type")},cell.optString("style").takeIf{cell.has("style")})
            }}
            val revision=store.applyXlsxCellEdits(context.projectId,arguments.getString("id"),arguments.getInt("expectedVersion"),edits)
            store.verifiedFile(context.projectId,revision)
            JSONObject().put("artifact",revision.json()).put("state","PRIVATE_DRAFT_VERIFIED")
                .put("note","The selected cells were saved as a new immutable XLSX version. Formula edits remove old cached results and request recalculation by a compatible spreadsheet reader; formulas are not recalculated by this app. Re-read this version before another update; imported originals remain unchanged.").toString()
        } else if(operation=="artifacts.xlsxAddSheet") {
            val revision=store.addXlsxSheet(context.projectId,arguments.getString("id"),arguments.getInt("expectedVersion"),arguments.getString("name"))
            store.verifiedFile(context.projectId,revision)
            JSONObject().put("artifact",revision.json()).put("state","PRIVATE_DRAFT_VERIFIED")
                .put("note","The empty worksheet was added to a new immutable version of this app-generated workbook. Existing sheets, cells, formulas and cached values were preserved; this app does not recalculate formulas. Re-read the new version before editing cells. Imported files are unchanged and export remains a separate user-selected SAF action.").toString()
        } else if(operation=="artifacts.xlsxSetTable") {
            val revision=store.setXlsxTable(context.projectId,arguments.getString("id"),arguments.getInt("expectedVersion"),
                arguments.getString("sheet"),arguments.getString("name"),arguments.getString("range"))
            store.verifiedFile(context.projectId,revision)
            JSONObject().put("artifact",revision.json()).put("state","PRIVATE_DRAFT_VERIFIED")
                .put("note","The structured table was added or resized in a new immutable XLSX version. Header cells were validated; the app does not calculate formulas. Re-read the new version before further edits. Imported originals remain unchanged and export is a separate user-selected SAF action.").toString()
        } else {
            val spec=ArtifactSpec.parse(arguments.getJSONObject("spec"))
            val revision=if(operation=="artifacts.create")store.create(context.projectId,spec)else store.revise(context.projectId,arguments.getString("id"),arguments.getInt("expectedVersion"),spec)
            store.verifiedFile(context.projectId,revision)
            JSONObject().put("artifact",revision.json()).put("state","PRIVATE_DRAFT_VERIFIED")
                .put("note","Actual bytes created locally. Open Artifacts in this chat for the text/structure preview, version editing and explicit export. Original imports are unchanged. Formulas are not recalculated; CODE is not executed; PDF is regenerated. PPTX can embed only a supplied, validated PNG with alt text; the preview is not a slide canvas.").toString()
        }
    }
    companion object {val IDS=setOf("artifacts.create","artifacts.revise","artifacts.list","artifacts.read","artifacts.patchPreview","artifacts.xlsxSetCells","artifacts.xlsxAddSheet","artifacts.xlsxSetTable")}
}
