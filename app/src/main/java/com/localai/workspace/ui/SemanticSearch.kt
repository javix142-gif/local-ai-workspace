package com.localai.workspace.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

/** User workflow lives in Workspace/Project; model setup contains no query or media picker. */
@Composable fun SemanticSearchEntry(projectId:String?=null) {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph
    val layer=graph.semanticV2;val scope=rememberCoroutineScope()
    val jobs by layer.jobs.collectAsState(initial=emptyList());val revision by layer.configurationRevision.collectAsState()
    val externalBusy by graph.validationBusy.collectAsState()
    var open by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var results by remember{mutableStateOf<List<Pair<String,String>>>(emptyList())};var operation by remember{mutableStateOf<Job?>(null)}
    var previous by remember{mutableStateOf(false)}
    val target=if(projectId==null)SemanticScope(ScopeType.GLOBAL)else SemanticScope(ScopeType.PROJECT,projectId)
    val eg2=remember(revision){layer.selection.choice==SemanticProviderChoice.EG2}
    fun run(block:suspend()->Unit){operation=scope.launch{busy=true;error=null;try{withContext(Dispatchers.IO){block()}}catch(cancel:CancellationException){throw cancel}catch(failure:Throwable){error=semanticUserError(failure)}finally{busy=false}}}
    val media=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)run{
        val mime=context.contentResolver.getType(uri).orEmpty()
        val modality=when{mime.startsWith("image/")->SemanticModality.IMAGE;mime.startsWith("audio/")->SemanticModality.AUDIO;mime.startsWith("video/")->SemanticModality.VIDEO;else->throw SemanticFailure(SemanticError.MODALITY_UNSUPPORTED)}
        layer.indexMedia(uri,target,modality)
    }}
    TextButton(onClick={open=true}){Text("Semantic search")}
    if(open)SemanticPanel(if(projectId==null)"Semantic search"else"Search project",{open=false}){
        Text(if(eg2)"EmbeddingGemma 2"else"EmbeddingGemma 300M",style=MaterialTheme.typography.bodySmall)
        if(!eg2&&projectId==null)Text("Open a project to search its documents with EmbeddingGemma 300M.")
        OutlinedTextField(query,{query=it},label={Text("Search")},singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(enabled=!busy&&!externalBusy&&query.isNotBlank()&&(eg2||projectId!=null),onClick={run{
            results=if(eg2)layer.search(SemanticInput.Text(query),target).map{hit->hit.sourceName to buildString{hit.page?.let{append("Page $it · ")};hit.startMs?.let{append("${it/1000}s–${(hit.endMs ?: it)/1000}s · ")};append(hit.content?.take(500) ?: hit.modality.name)}}
            else graph.retrieval.retrieve(projectId!!,query).map{it.documentTitle to it.excerpt.take(500)}
        }}){Text("Search")}
        results.forEach{(name,excerpt)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(name,style=MaterialTheme.typography.titleSmall);Text(excerpt,style=MaterialTheme.typography.bodySmall)}}}
        if(projectId!=null)TextButton(enabled=!busy&&!externalBusy,onClick={run{if(eg2)layer.indexProject(projectId)else graph.inferenceGate.withLock{graph.retrieval.indexProject(projectId)}}}){Text("Index project files")}
        if(eg2){
            TextButton(enabled=!busy&&!externalBusy,onClick={media.launch(arrayOf("image/*","audio/*","video/*"))}){Text("Index media · experimental")}
            val scopedJobs=jobs.filter{it.scopeKey==target.key}
            scopedJobs.firstOrNull()?.let{job->Text("${indexLabel(job.state)} · ${job.sources} sources · ${job.segments} segments",style=MaterialTheme.typography.bodySmall)}
            if(scopedJobs.count{it.state in setOf("READY","NEEDS_REINDEX")}>1){
                TextButton(onClick={previous=!previous}){Text("Previous indexes")}
                if(previous)scopedJobs.filter{it.state in setOf("READY","NEEDS_REINDEX")}.forEach{job->TextButton(enabled=!busy&&!externalBusy,onClick={run{
                    layer.restore();val model=layer.manager.selected ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
                    val dim=listOf(768,256).firstOrNull{EmbeddingEngineManager.space(model,it).id==job.spaceKey} ?: throw SemanticFailure(SemanticError.SPACE_MISMATCH)
                    layer.setDimension(dim);layer.switchIndex(job.id)
                }}){Text("Restore ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(job.startedAt))}")}}
            }
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={operation?.cancel()}){Text("Cancel")}}
    }
}
internal fun indexLabel(state:String)=when(state){"READY"->"Indexed";"NEEDS_REINDEX"->"Needs reindex";"INDEXING"->"Indexing";"FAILED"->"Failed";"CANCELLED"->"Cancelled";else->"Not indexed"}
