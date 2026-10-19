package com.localai.workspace.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.localai.workspace.artifacts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Existing chat owns project scope. SAF user choice is the sole export authority. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ArtifactsDialog(projectId:String,onDismiss:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val store=remember(context){ArtifactStore(context.applicationContext)}
    var items by remember{mutableStateOf<List<ArtifactRevision>>(emptyList())}
    var versions by remember{mutableStateOf<List<ArtifactRevision>>(emptyList())}
    var patchPreviews by remember{mutableStateOf<List<CodePatchPreview>>(emptyList())}
    var selected by remember{mutableStateOf<ArtifactRevision?>(null)}
    var selectedPatch by remember{mutableStateOf<CodePatchPreview?>(null)}
    var descriptor by remember{mutableStateOf("")}
    var savedPreview by remember{mutableStateOf("")}
    var preview by remember{mutableStateOf<ArtifactSpec?>(null)}
    var previewText by remember{mutableStateOf("")}
    var imagePreviews by remember{mutableStateOf<List<PptxImagePreviewItem>>(emptyList())}
    var notice by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var exportIdentity by rememberSaveable{mutableStateOf<String?>(null)}
    var lastExport by remember{mutableStateOf<Pair<android.net.Uri,String>?>(null)}
    val selectedDocxTemplate = remember(descriptor) {
        runCatching {
            JSONObject(descriptor).takeIf { it.optString("format") == ArtifactFormat.DOCX.name }
                ?.optString("template", "standard")
                ?.takeIf { it in setOf("standard", "report", "meeting_minutes") }
        }.getOrNull()
    }
    suspend fun refresh(){
        items=withContext(Dispatchers.IO){store.list(projectId)}
        try { patchPreviews=withContext(Dispatchers.IO){store.codePatchPreviews(projectId)} }
        catch(e:Exception) { patchPreviews=emptyList();notice="A pending code preview could not be verified: ${e.message ?: "storage data invalid"}. Existing artifacts remain available; do not apply that preview." }
    }
    fun work(block:suspend()->Unit){busy=true;scope.launch{try{block()}catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(e:Exception){notice=e.message ?: e.javaClass.simpleName}finally{busy=false}}}
    suspend fun parsePreview(source:String):Triple<ArtifactSpec,String,List<PptxImagePreviewItem>> = withContext(Dispatchers.Default){
        val spec=ArtifactSpec.parse(JSONObject(source))
        Triple(spec,spec.preview(),decodePptxImagePreviews(spec))
    }
    DisposableEffect(imagePreviews) {
        onDispose { imagePreviews.forEach { if(!it.bitmap.isRecycled)it.bitmap.recycle() } }
    }
    LaunchedEffect(projectId){try{refresh()}catch(e:Exception){notice=e.message.orEmpty()}}
    val export=rememberLauncherForActivityResult(ArtifactCreateDocumentContract()){uri->
        val identity=exportIdentity;exportIdentity=null
        if(uri!=null&&identity!=null)work {
            val revision=withContext(Dispatchers.IO){val parts=identity.split(':');store.history(projectId,parts[0]).single{it.version==parts[1].toInt()}}
            withContext(Dispatchers.IO){
                val file=store.verifiedFile(projectId,revision)
                requireNotNull(context.contentResolver.openOutputStream(uri,"w")){"EXPORT_DESTINATION_UNAVAILABLE"}.use{out->file.inputStream().use{it.copyTo(out)}}
                val exportedHash=requireNotNull(context.contentResolver.openInputStream(uri)).use{input->val md=java.security.MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;md.update(buffer,0,n)};md.digest().joinToString(""){"%02x".format(it)}}
                check(exportedHash==revision.sha256){"EXPORT_READBACK_HASH_MISMATCH"}
            };lastExport=uri to revision.format.mime;notice="Exported and read back: ${revision.name} · v${revision.version}"
        }
    }
    fun load(revision:ArtifactRevision)=work {
        selectedPatch=null;selected=revision;preview=null;previewText="";imagePreviews=emptyList()
        val spec=withContext(Dispatchers.IO){store.verifiedFile(projectId,revision);store.spec(projectId,revision)}
        val prepared=withContext(Dispatchers.Default){Triple(spec,spec.preview(),decodePptxImagePreviews(spec))}
        descriptor=spec.json().toString(2);savedPreview=prepared.second;preview=spec;previewText=prepared.second;imagePreviews=prepared.third
        versions=withContext(Dispatchers.IO){store.history(projectId,revision.id)}
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Artifacts · local drafts")},confirmButton={TextButton(onClick=onDismiss){Text("Close")}},text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Actual local files. Originals are preserved. Enable artifact tools in Options to ask the model to create/edit a draft.",style=MaterialTheme.typography.bodySmall)
            if(notice.isNotBlank())Text(notice,Modifier.testTag("artifact-status"),style=MaterialTheme.typography.bodySmall)
            lastExport?.let{(uri,mime)->TextButton(onClick={try{context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))}catch(e:android.content.ActivityNotFoundException){notice="No compatible local reader installed"}}){Text("Open exported file")}}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow {ArtifactFormat.entries.forEach{format->TextButton(enabled=!busy,onClick={
                selected=null;selectedPatch=null;versions=emptyList();preview=null;previewText="";imagePreviews=emptyList();savedPreview=""
                val obj=JSONObject().put("format",format.name).put("name","draft.${format.extension}")
                when(format){
                    ArtifactFormat.CODE->obj.put("body","")
                    ArtifactFormat.XLSX->obj.put("sheets",org.json.JSONArray().put(JSONObject().put("name","Sheet1").put("cells",org.json.JSONArray())))
                    ArtifactFormat.PPTX->obj.put("slides",org.json.JSONArray().put(JSONObject().put("title","").put("body","")))
                    ArtifactFormat.DOCX->obj.put("template","standard").put("blocks",org.json.JSONArray().put(JSONObject().put("kind","paragraph").put("text","").put("style","body")))
                    ArtifactFormat.PDF->obj.put("paragraphs",org.json.JSONArray().put(JSONObject().put("text","").put("style","body")))
                }
                descriptor=obj.toString(2)
            }){Text(format.name,style=MaterialTheme.typography.labelSmall)}}}
            items.forEach{revision->TextButton(enabled=!busy,onClick={load(revision)},modifier=Modifier.testTag("artifact-${revision.id}")){Text("${revision.name} · v${revision.version} · ${revision.bytes} bytes")}}
            if(patchPreviews.isNotEmpty()) {
                Text("Code patch previews · not applied",style=MaterialTheme.typography.titleSmall)
                patchPreviews.forEach{patch->TextButton(enabled=!busy,onClick={selectedPatch=patch;selected=null;descriptor="";savedPreview="";preview=null;previewText="";imagePreviews=emptyList()},modifier=Modifier.testTag("artifact-patch-${patch.id}")){
                    Text("Review ${patch.fileName} · based on v${patch.baseVersion} · ${patch.edits.size} exact edit(s)")
                }}
            }
            selectedPatch?.let{patch->
                HorizontalDivider()
                Text("Review code patch · ${patch.fileName}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("artifact-patch-review-title"))
                Text("No code bytes or exported file have changed. Applying below creates a new immutable private draft version; exporting it remains a separate action.",style=MaterialTheme.typography.bodySmall)
                Text("Base v${patch.baseVersion} · ${patch.baseSha256}",style=MaterialTheme.typography.labelSmall)
                Text("Proposed · ${patch.proposedSha256}",style=MaterialTheme.typography.labelSmall)
                Text("Before · exact source body",style=MaterialTheme.typography.labelMedium)
                SelectionContainer{Text(patch.before,Modifier.fillMaxWidth().heightIn(max=120.dp).verticalScroll(rememberScrollState()).testTag("artifact-patch-before"),style=MaterialTheme.typography.bodySmall)}
                Text("After · proposed source body",style=MaterialTheme.typography.labelMedium)
                SelectionContainer{Text(patch.after,Modifier.fillMaxWidth().heightIn(max=120.dp).verticalScroll(rememberScrollState()).testTag("artifact-patch-after"),style=MaterialTheme.typography.bodySmall)}
                FlowRow {
                    TextButton(enabled=!busy,onClick={work {
                        val revision=withContext(Dispatchers.IO){store.applyCodePatchPreview(projectId,patch.id)}
                        val saved=withContext(Dispatchers.IO){store.spec(projectId,revision)}
                        selected=revision;selectedPatch=null;versions=withContext(Dispatchers.IO){store.history(projectId,revision.id)}
                        val rendered=withContext(Dispatchers.Default){saved.preview()}
                        descriptor=saved.json().toString(2);savedPreview=rendered;preview=saved;previewText=rendered;imagePreviews=emptyList()
                        refresh();notice="Patch verified as immutable private draft v${revision.version}. Earlier source version and external workspace files remain unchanged."
                    }},modifier=Modifier.testTag("artifact-patch-apply")){Text("Apply as new version")}
                    TextButton(enabled=!busy,onClick={work {
                        withContext(Dispatchers.IO){store.discardCodePatchPreview(projectId,patch.id)}
                        selectedPatch=null;refresh();notice="Patch preview discarded; no artifact bytes changed."
                    }},modifier=Modifier.testTag("artifact-patch-discard")){Text("Discard preview")}
                }
            }
            if(descriptor.isNotEmpty()) {
                OutlinedTextField(descriptor,{descriptor=it;preview=null;previewText="";imagePreviews=emptyList()},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=240.dp).testTag("artifact-spec-editor"),label={Text("Structured draft JSON")})
                if(selectedDocxTemplate != null) {
                    Text("Built-in DOCX template",style=MaterialTheme.typography.labelMedium)
                    FlowRow {
                        listOf("standard" to "Standard", "report" to "Report", "meeting_minutes" to "Meeting minutes").forEach { (value,label) ->
                            TextButton(enabled=!busy,onClick={
                                descriptor=JSONObject(descriptor).put("template",value).toString(2)
                                preview=null;previewText="";imagePreviews=emptyList()
                            },modifier=Modifier.testTag("docx-template-$value")) { Text(label) }
                        }
                    }
                    Text("Selected template: ${when(selectedDocxTemplate) { "standard"->"Standard";"report"->"Report";else->"Meeting minutes" }}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("docx-selected-template"))
                }
                TextButton(enabled=!busy,onClick={val source=descriptor;preview=null;previewText="";imagePreviews=emptyList();work {
                    val prepared=parsePreview(source)
                    preview=prepared.first;previewText=prepared.second;imagePreviews=prepared.third
                    notice="Structure and supplied-image preview (not page/slide layout rendering)"
                }}){Text("Preview changes")}
                preview?.let { spec->
                    if(selected!=null && savedPreview!=previewText) {
                        Text("Saved v${selected!!.version} · before")
                        SelectionContainer{Text(savedPreview,Modifier.testTag("artifact-saved-preview"),style=MaterialTheme.typography.bodySmall)}
                        Text("Proposed version · after")
                        ArtifactRevisionDiff(savedPreview,previewText)
                    }
                    SelectionContainer{Text(previewText,Modifier.testTag("artifact-preview"),style=MaterialTheme.typography.bodySmall)}
                    if(spec.format==ArtifactFormat.PPTX) {
                        PptxSlideLayoutPreview(spec,imagePreviews)
                        PptxImageVisualPreview(imagePreviews)
                    }
                    TextButton(enabled=!busy,onClick={work {
                        val current=selected
                        val result=withContext(Dispatchers.IO){if(current==null)store.create(projectId,spec)else store.revise(projectId,current.id,versions.maxOf{it.version},spec)}
                        selected=result;savedPreview=previewText;refresh();versions=withContext(Dispatchers.IO){store.history(projectId,result.id)};notice="Saved and verified v${result.version}; earlier versions unchanged"
                    }}){Text(if(selected==null)"Save new draft"else "Save new version")}
                }
                selected?.let{revision->TextButton(enabled=!busy&&preview!=null,onClick={exportIdentity="${revision.id}:${revision.version}";export.launch(ArtifactExportRequest(revision.name,revision.format))}){Text("Export saved v${revision.version}")}}
                versions.forEach{revision->TextButton(enabled=!busy,onClick={load(revision)}){Text("Inspect/restore v${revision.version} (save creates a new version)")}}
            }
        }
    })
}

private sealed interface ArtifactLineDiff {
    data class Lines(val entries:List<ArtifactDiffEntry>):ArtifactLineDiff
    data object TooLarge:ArtifactLineDiff
}

private enum class ArtifactDiffKind { UNCHANGED, REMOVED, ADDED }
private data class ArtifactDiffEntry(val kind:ArtifactDiffKind,val text:String)

/** Bounded LCS diff. Large drafts retain their full before/after previews without allocating an unbounded matrix. */
private fun artifactLineDiff(before:String,after:String):ArtifactLineDiff {
    if(before==after)return ArtifactLineDiff.Lines(emptyList())
    fun linesBounded(value:String):List<String>? {
        val lines=ArrayList<String>()
        val iterator=value.lineSequence().iterator()
        while(iterator.hasNext()) {
            lines+=iterator.next()
            if(lines.size>256)return null
        }
        return lines
    }
    val old=linesBounded(before)?:return ArtifactLineDiff.TooLarge
    val new=linesBounded(after)?:return ArtifactLineDiff.TooLarge
    if((old.size+1L)*(new.size+1L)>66_049L)return ArtifactLineDiff.TooLarge
    val lcs=Array(old.size+1){IntArray(new.size+1)}
    for(i in old.indices.reversed())for(j in new.indices.reversed()) {
        lcs[i][j]=if(old[i]==new[j])1+lcs[i+1][j+1] else maxOf(lcs[i+1][j],lcs[i][j+1])
    }
    val entries=ArrayList<ArtifactDiffEntry>(old.size+new.size)
    var i=0;var j=0
    while(i<old.size||j<new.size) {
        when {
            i<old.size&&j<new.size&&old[i]==new[j] -> { entries+=ArtifactDiffEntry(ArtifactDiffKind.UNCHANGED,old[i]);i++;j++ }
            i<old.size&&(j==new.size||lcs[i+1][j]>=lcs[i][j+1]) -> { entries+=ArtifactDiffEntry(ArtifactDiffKind.REMOVED,old[i]);i++ }
            else -> { entries+=ArtifactDiffEntry(ArtifactDiffKind.ADDED,new[j]);j++ }
        }
    }
    return ArtifactLineDiff.Lines(entries)
}

@Composable private fun ArtifactRevisionDiff(before:String,after:String) {
    val diff=remember(before,after){artifactLineDiff(before,after)}
    Text("Line-by-line changes",style=MaterialTheme.typography.titleSmall,modifier=Modifier.testTag("artifact-line-diff-title"))
    when(diff) {
        ArtifactLineDiff.TooLarge -> Text(
            "Line comparison is too large to compute safely. The complete before and after previews remain available.",
            style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("artifact-diff-unavailable"),
        )
        is ArtifactLineDiff.Lines -> if(diff.entries.isEmpty()) {
            Text("No line changes",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("artifact-diff-unchanged"))
        } else SelectionContainer {
            Column(Modifier.fillMaxWidth().testTag("artifact-line-diff")) {
                diff.entries.forEachIndexed { index,entry ->
                    val text=when(entry.kind) {
                        ArtifactDiffKind.UNCHANGED -> "Unchanged: ${entry.text}"
                        ArtifactDiffKind.REMOVED -> "Removed: ${entry.text}"
                        ArtifactDiffKind.ADDED -> "Added: ${entry.text}"
                    }
                    val color:Color=when(entry.kind) {
                        ArtifactDiffKind.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
                        ArtifactDiffKind.REMOVED -> MaterialTheme.colorScheme.error
                        ArtifactDiffKind.ADDED -> MaterialTheme.colorScheme.primary
                    }
                    Text(text,color=color,style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace),
                        modifier=Modifier.fillMaxWidth().testTag("artifact-diff-row-$index")
                            .semantics { contentDescription=text })
                }
            }
        }
    }
}
