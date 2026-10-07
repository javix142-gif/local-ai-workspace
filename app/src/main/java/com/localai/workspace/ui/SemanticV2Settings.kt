package com.localai.workspace.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.*
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Technical controls are nested, never part of the regular model cards or search workflow. */
@Composable fun SemanticV2Settings(projectId:String?=null) {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph
    val layer=graph.semanticV2;val diagnostics=graph.semanticDiagnostics
    val models by layer.models.collectAsState(initial=emptyList());val state by layer.manager.status.collectAsState()
    val revision by layer.configurationRevision.collectAsState();val attempts by graph.semanticImports.attempts.collectAsState()
    val legacyDiagnostics by graph.embeddingModels.diagnostics.collectAsState();val legacyStatus by graph.embeddingModels.status.collectAsState()
    val benchmark by diagnostics.progress.collectAsState();val benchmarkRunning by diagnostics.running.collectAsState()
    val externalBusy by graph.validationBusy.collectAsState()
    var open by remember { mutableStateOf(false) };var advanced by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SemanticModelRecord?>(null) };var iterations by remember { mutableStateOf("5") }
    var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) };var expected by remember { mutableStateOf<SemanticProviderChoice?>(null) }
    var validation by remember { mutableStateOf<SemanticValidationSummary?>(null) }
    var eg2Installed by remember { mutableStateOf(false) }
    var legacyInstalled by remember { mutableStateOf(false) };var legacyConfigured by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(models,revision,legacyStatus,attempts) {
        withContext(Dispatchers.IO) {
            layer.restore();graph.semanticImports.restore();diagnostics.recover()
            eg2Installed=(models.firstOrNull{it.id==layer.manager.selected?.id} ?: models.firstOrNull())?.let{File(it.privatePath).isFile}==true
            val prefs=context.getSharedPreferences("local_embedding_model",android.content.Context.MODE_PRIVATE)
            legacyConfigured=graph.embeddingModels.id!=null
            legacyInstalled=prefs.getString("path",null)?.let{path->val f=File(path);f.isFile && f.inputStream().use{input->String(ByteArray(4).also{input.read(it)},Charsets.US_ASCII)=="GGUF"}}==true
            validation=SemanticValidationSummary.read(context,layer.manager.selected?.sha256)
        }
    }
    fun run(block:suspend()->Unit){if(externalBusy){error="Diagnostics are running";return};operation=scope.launch{busy=true;error=null;try{block()}catch(cancel:CancellationException){throw cancel}catch(failure:Throwable){error=semanticUserError(failure)}finally{busy=false}}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)run{graph.semanticImports.import(uri,expected)}}
    val chosen=remember(revision,state,models){layer.selection.choice}
    val model=models.firstOrNull{it.id==layer.manager.selected?.id} ?: models.firstOrNull()
    val legacyHealth=SemanticModelPresentation.legacyStatus(legacyConfigured,legacyInstalled,legacyDiagnostics?.status=="FAILED")
    val eg2Health=SemanticModelPresentation.status(eg2Installed,model?.validationStatus in setOf("TEXT_PROBE_PASSED","PROBED_TEXT_NOT_DEVICE_VALIDATED"),state.state)
    val disabled=busy || externalBusy
    Column {
        TextButton(onClick={open=true}) { Text("Semantic search") }
        if(open) SemanticPanel("Embedding models",{open=false}) {
            Text(if(chosen==SemanticProviderChoice.EG2)model?.displayName ?: "Choose an embedding model" else "EmbeddingGemma 300M",style=MaterialTheme.typography.titleMedium)
            Text(if(chosen==SemanticProviderChoice.EG2)eg2Health else legacyHealth,style=MaterialTheme.typography.bodyMedium)
            if(chosen==SemanticProviderChoice.EG2 && model!=null)Text("${model.fileSize/1024/1024} MiB · ${if(state.requested==SemanticBackend.CPU)"CPU"else"GPU experimental"} · ${layer.dimension}D",style=MaterialTheme.typography.bodySmall)
            Text("Provider",style=MaterialTheme.typography.labelLarge)
            SemanticProviderCard("EmbeddingGemma 300M","GGUF · 768D",legacyHealth,chosen==SemanticProviderChoice.EG1,legacyInstalled,!disabled,
                {run{layer.chooseProvider(SemanticProviderChoice.EG1)}},{expected=SemanticProviderChoice.EG1;picker.launch(arrayOf("*/*"))})
            SemanticProviderCard("EmbeddingGemma 2 740M","LiteRT-LM${model?.let{" · ${it.fileSize/1024/1024} MiB"} ?: ""}",eg2Health,chosen==SemanticProviderChoice.EG2,eg2Installed,!disabled,
                {model?.let{run{layer.activate(it.id)}}},{expected=SemanticProviderChoice.EG2;picker.launch(arrayOf("*/*"))})
            TextButton(enabled=!disabled,onClick={expected=null;picker.launch(arrayOf("*/*"))}){Text("Import model")}
            TextButton(onClick={advanced=true}){Text("Advanced / Diagnostics")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={operation?.cancel()}){Text("Cancel")}}
        }
        if(advanced) SemanticPanel("Semantic diagnostics",{advanced=false}) {
            Text("Text probe: ${if(model!=null)"passed"else"unknown"}")
            Text("Device suite: ${validation?.status ?: "UNKNOWN"}${validation?.let{" · ${it.passed}/${it.total} · saved ${DateFormat.getDateTimeInstance().format(Date(it.savedAt))}"} ?: ""}")
            Text("Engine: ${state.state} · Requested ${state.requested} · Effective ${state.effective ?: "unavailable"}",style=MaterialTheme.typography.bodySmall)
            Text(state.backendEvidence,style=MaterialTheme.typography.bodySmall);state.fallbackReason?.let{Text(it)}
            model?.let { selected ->
                Text("SHA-256: ${selected.sha256}",style=MaterialTheme.typography.labelSmall)
                Text("Model: ${selected.declaredModalities}\nRuntime: ${selected.runtimeModalities}\nVerified: ${selected.verifiedModalities}",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(768,256).forEach{d->FilterChip(selected=layer.dimension==d,onClick={run{layer.setDimension(d)}},enabled=!disabled,label={Text("${d}D")})} }
                Text("256D is experimental. Changing dimension requires reindexing; the engine loads on first use.",style=MaterialTheme.typography.bodySmall)
                Text("Backend",style=MaterialTheme.typography.labelLarge)
                SemanticBackend.entries.forEach { backend -> Row { RadioButton(state.requested==backend,onClick={run{layer.activate(selected.id,EmbeddingRuntimeProfile(backend))}},enabled=!disabled);TextButton(enabled=!disabled,onClick={run{layer.activate(selected.id,EmbeddingRuntimeProfile(backend))}}){Text(if(backend==SemanticBackend.CPU)"CPU"else"GPU experimental")} } }
                TextButton(enabled=!disabled,onClick={run{layer.validate(selected.id)}}){Text("Revalidate model")}
                TextButton(enabled=!disabled,onClick={run{layer.manager.unload()}}){Text("Unload engine")}
                TextButton(enabled=!disabled,onClick={removing=selected}){Text("Remove model…")}
            }
            attempts.values.forEach { a->Text("${a.provider} import · ${a.status}\nSelected: ${a.originalSelectedFilename ?: "unknown"}\nSize: ${a.detectedSize ?: "unavailable"} · Detected ${a.detectedProvider ?: "unknown"}\nStage: ${a.failureStage} · ${a.reasonCode ?: ""}\nSHA: ${a.sha256 ?: "not computed"}",style=MaterialTheme.typography.bodySmall) }
            if(legacyDiagnostics?.status=="FAILED")Text("EG1 previous import: ${legacyDiagnostics?.stage} · ${legacyDiagnostics?.code}\nThis is import history, not EG2 health.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(iterations,{iterations=it.filter(Char::isDigit).take(2)},label={Text("Iterations (1–20)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            for(d in listOf(768,256))TextButton(enabled=!disabled&&model!=null&&iterations.toIntOrNull() in 1..20,onClick={try{diagnostics.start(iterations.toInt(),dimension=d)}catch(e:Exception){error=semanticUserError(e)}}){Text("Benchmark CPU · ${d}D")}
            TextButton(enabled=!disabled&&model!=null&&iterations.toIntOrNull() in 1..20,onClick={try{diagnostics.start(iterations.toInt(),compare=true)}catch(e:Exception){error=semanticUserError(e)}}){Text("Compare 768D vs 256D")}
            benchmark?.let { run ->
                Text("Benchmark · ${run.status}")
                Text("CPU · ${run.currentDimension}D · ${run.lastCompletedIteration} / ${run.iterations}\n${run.currentPhase}",style=MaterialTheme.typography.bodySmall)
                run.currentMs?.let{Text("Current: $it ms")}
                if(run.warmSamplesMs.isNotEmpty())Text("P50: ${run.warmSamplesMs.sorted()[(run.warmSamplesMs.size-1)/2]} ms")
                run.records.forEach { r->Text("${r.dimension}D · P50 ${r.p50Ms} ms · P95 ${r.p95Ms} ms\nRecall@1 ${r.quality["recall@1"]} · MRR ${r.quality["mrr"]}") }
                run.failureKind?.let{Text(it)}
                if(run.records.isNotEmpty())Text("No automatic recommendation",style=MaterialTheme.typography.bodySmall)
                if(benchmarkRunning)TextButton(onClick=diagnostics::cancel){Text("Cancel benchmark")}
                TextButton(onClick={copySemanticReport(context,diagnostics.report())}){Text("Copy detailed report")}
            }
            TextButton(enabled=!disabled&&model!=null,onClick={run{com.localai.workspace.semantic.v2.SemanticValidation(layer,context,graph).run();validation=SemanticValidationSummary.read(context,model?.sha256)}}){Text("Run Semantic V2 validation")}
            TextButton(onClick={scope.launch {copySemanticReport(context,withContext(Dispatchers.IO){File(context.filesDir,"semantic-v2-reports/latest.json").takeIf{it.isFile}?.readText()})}}){Text("Copy validation report")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={operation?.cancel()}){Text("Cancel")}}
        }
        removing?.let { selected->AlertDialog(onDismissRequest={removing=null},title={Text("Remove ${selected.displayName}?")},text={Text("The model file will be removed. Removal is blocked while any retained index references it; existing vectors are preserved.")},confirmButton={TextButton(enabled=!disabled,onClick={removing=null;run{layer.remove(selected.id)}}){Text("Remove")}},dismissButton={TextButton(onClick={removing=null}){Text("Cancel")}}) }
    }
}
@Composable private fun SemanticProviderCard(name:String,details:String,status:String,selected:Boolean,installed:Boolean,enabled:Boolean,select:()->Unit,import:()->Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Row { RadioButton(selected,onClick=select,enabled=enabled&&installed);Column(Modifier.weight(1f)){Text(name,style=MaterialTheme.typography.titleSmall);Text(details,style=MaterialTheme.typography.bodySmall);Text("$status${if(selected&&installed)" · Active"else""}",style=MaterialTheme.typography.labelMedium)} }
        TextButton(enabled=enabled,onClick=import){Text(if(installed)"Change model"else"Import")}
    } }
}
@Composable internal fun SemanticPanel(title:String,dismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
            Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.widthIn(max=560.dp).fillMaxWidth().heightIn(max=maxHeight*.92f)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(title,style=MaterialTheme.typography.titleLarge);content();TextButton(onClick=dismiss,modifier=Modifier.fillMaxWidth()){Text("Close")}
                }
            }
        }
    }
}
internal fun copySemanticReport(context:android.content.Context,text:String?){if(text!=null)context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Semantic report",text))}
internal fun semanticUserError(error:Throwable):String=when((error as? SemanticFailure)?.code){SemanticError.SPACE_MISMATCH,SemanticError.REINDEX_REQUIRED->"The index needs rebuilding for this configuration.";SemanticError.MODEL_METADATA_UNSUPPORTED->"This file is not compatible with the selected embedding provider.";SemanticError.MODEL_FILE_INVALID->"Install or select a compatible embedding model.";else->if(error.message?.startsWith("Model has retained indexes")==true)"This model is still used by retained indexes. No data was removed."else "Operation failed. See Diagnostics for details."}
