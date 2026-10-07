package com.localai.workspace.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localai.workspace.AppGraph
import com.localai.workspace.validation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceValidationScreen(graph:AppGraph,back:()->Unit) {
    val validation=remember(graph) { graph.deviceValidation }
    val current by validation.current.collectAsStateWithLifecycle()
    val history by validation.history.collectAsStateWithLifecycle()
    val running by validation.running.collectAsStateWithLifecycle()
    val initialized by validation.initialized.collectAsStateWithLifecycle()
    val status by validation.status.collectAsStateWithLifecycle()
    var iterations by rememberSaveable { mutableIntStateOf(1) }
    var exportSnapshot by remember { mutableStateOf<ValidationReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri->
        val snapshot=exportSnapshot
        if(uri!=null && snapshot!=null)scope.launch {
            message=withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(validation.export(snapshot).toByteArray()) } ?: error("Export destination unavailable")
            }.fold({"JSON exported"},{"Export failed"}) }
        }
    }
    val report=current
    val previous=history.lastOrNull { it.runId!=report?.runId && report!=null && it.startedAt<report.startedAt && it.phase==ValidationPhase.COMPLETED && ValidationRules.compatible(it,report) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item { TopAppBar(title={Text("Device Validation")},navigationIcon={TextButton(onClick=back){Text("Back")}}) }
        item { Text("Developer / Diagnostics · isolated synthetic data",style=MaterialTheme.typography.bodySmall) }
        item { Text(status,style=MaterialTheme.typography.titleSmall) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=initialized && !running,onClick={validation.start(ValidationMode.QUICK)},modifier=Modifier.weight(1f)) { Text("Run Quick Validation") }
            OutlinedButton(enabled=initialized && !running,onClick={validation.start(ValidationMode.FULL,iterations)},modifier=Modifier.weight(1f)) { Text("Run Full Validation") }
        } }
        item { OutlinedButton(enabled=initialized && !running,onClick={validation.start(ValidationMode.THINKING_BUDGETS)}) {
            Text("Thinking budget probes · 256 / 512 / 1024")
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Full iterations",modifier=Modifier.padding(top=12.dp))
            (1..3).forEach { n->FilterChip(selected=iterations==n,enabled=!running,onClick={iterations=n},label={Text(n.toString())}) }
        } }
        if(running) item {
            val selected=report?.tests.orEmpty().filter { it.selected };val finished=selected.count { it.status!=ValidationStatus.NOT_TESTED }
            LinearProgressIndicator(progress={if(selected.isEmpty())0f else finished.toFloat()/selected.size},modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick=validation::cancel) { Text("Cancel validation") }
        }
        if(report!=null) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("DEVICE VALIDATION · "+report.appVersion,style=MaterialTheme.typography.titleSmall)
                Text(report.device+" · API "+report.androidApi)
                Text((report.model?.name ?: "Model unavailable")+" · "+report.runtime)
                Text(report.environment.name+" · "+report.phase.name,style=MaterialTheme.typography.bodySmall)
                Text("Overall: "+ValidationRules.summary(report).overall,style=MaterialTheme.typography.titleMedium)
                Text(ValidationRules.summary(report).counts.entries.filter { it.value>0 }.joinToString(" · ") { it.key+": "+it.value },style=MaterialTheme.typography.bodySmall)
                Text("Cleanup: "+report.cleanupStatus+" · restart: "+report.restartPersistence,style=MaterialTheme.typography.bodySmall)
            } } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Device Validation",validation.summary(report)));message="Summary copied"}) { Text("Copy summary") }
                TextButton(onClick={exportSnapshot=report;export.launch("device-validation-"+report.runId+".json")}) { Text("Export JSON") }
            } }
            if(message!=null)item { Text(message!!,style=MaterialTheme.typography.bodySmall) }
            if(!running && report.phase in setOf(ValidationPhase.INTERRUPTED,ValidationPhase.CANCELLED))item {
                Text("The native session cannot survive process death. Restart safely runs a new isolated suite.",style=MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick={validation.restart(report)}) { Text("Restart safely") };TextButton(onClick={validation.discard(report)}) { Text("Discard") } }
            }
            if(previous!=null)item { ValidationComparison(previous,report) }
            items(report.tests.filter { it.selected },key={it.id+":"+it.iteration}) { result->
                ValidationResultCard(result,report.activeTestId==result.id+":"+result.iteration && running)
            }
        }
        if(history.isNotEmpty()) {
            item { Text("Recent validations · up to 10",style=MaterialTheme.typography.titleSmall) }
            items(history.asReversed(),key={"history:"+it.runId}) { saved->
                TextButton(enabled=!running,onClick={validation.select(saved)},modifier=Modifier.fillMaxWidth()) {
                    Text(saved.appVersion+" · "+saved.suiteType+" · "+ValidationRules.summary(saved).overall+" · "+saved.startedAt.take(16))
                }
            }
        }
    }
}
@Composable
private fun ValidationResultCard(result:ValidationResult,active:Boolean) {
    var expanded by rememberSaveable(result.id,result.iteration) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded=!expanded }) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(result.name+(if(result.iteration>1)" · "+result.iteration else ""),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                Text(if(active)"RUNNING" else result.status.name,color=if(result.status==ValidationStatus.FAIL)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
            }
            if(expanded) {
                Text("Expected: "+result.expected,style=MaterialTheme.typography.bodySmall)
                Text("Duration: "+(result.durationMs?.let { "$it ms" } ?: "unavailable")+" · "+result.evidence,style=MaterialTheme.typography.bodySmall)
                result.reasonCode?.let { Text("Reason: "+it,style=MaterialTheme.typography.bodySmall) }
                result.metrics.entrySet().forEach { (key,value)->Text(key+": "+if(value.isJsonNull)"unavailable" else value.asString,style=MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
@Composable
private fun ValidationComparison(previous:ValidationReport,current:ValidationReport) {
    val before=previous.tests.firstOrNull { it.id=="text" && it.iteration==1 }?.metrics
    val after=current.tests.firstOrNull { it.id=="text" && it.iteration==1 }?.metrics
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text("Previous / Current · text, iteration 1",style=MaterialTheme.typography.titleSmall)
        listOf("requestTimeToFirstTokenMs" to "TTFT ms","decodeTokensPerSecond" to "Decode tok/s","appPssAfterBytes" to "App PSS bytes").forEach { (key,label)->
            fun value(objectValue:com.google.gson.JsonObject?)=objectValue?.get(key)?.takeUnless { it.isJsonNull }?.asString ?: "unavailable"
            Text(label+": "+value(before)+" / "+value(after),style=MaterialTheme.typography.bodySmall)
        }
        ValidationRules.regressions(previous,current).forEach { Text(it,color=MaterialTheme.colorScheme.error) }
    } }
}
