package com.localai.workspace.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.context.*
import com.localai.workspace.context.ContextBundle
import com.localai.workspace.semantic.v2.*
import com.localai.workspace.ui.theme.LocalAiSpacing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.google.gson.GsonBuilder

@Composable fun ContextMemoryEntry(projectId:String?=null) {
    var open by remember{mutableStateOf(false)}
    TextButton(onClick={open=true}){Text("Structured memory")}
    if(open) StructuredMemoryPanel(projectId){open=false}
}
@Composable private fun StructuredMemoryPanel(projectId:String?,close:()->Unit) {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph;val foundation=graph.contextFoundation;val scope=rememberCoroutineScope()
    val all by foundation.memory.items.collectAsState(initial=emptyList());val access=ScopeAccess(projectId=projectId)
    var query by remember{mutableStateOf("")};var showInactive by remember{mutableStateOf(false)};var projectOnly by remember{mutableStateOf(projectId!=null)};var kindFilter by remember{mutableStateOf<MemoryKind?>(null)};var filters by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<MemoryRecord?>(null)};var editor by remember{mutableStateOf(false)};var text by remember{mutableStateOf("")};var ttl by remember{mutableStateOf("")};var kind by remember{mutableStateOf(MemoryKind.FACT)};var choosingKind by remember{mutableStateOf(false)};var scopeMenu by remember{mutableStateOf(false)};var writingScope by remember(projectId){mutableStateOf(projectId?.let{SemanticScope(ScopeType.PROJECT,it)} ?: SemanticScope(ScopeType.USER,"local"))}
    var details by remember{mutableStateOf<MemoryRecord?>(null)};var removal by remember{mutableStateOf<MemoryRecord?>(null)};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var brief by remember{mutableStateOf<ProjectBrief?>(null)}
    fun run(block:suspend()->Unit){scope.launch{busy=true;error=null;try{withContext(Dispatchers.IO){block()}}catch(cancel:CancellationException){throw cancel}catch(e:Exception){error=e.message ?: e.javaClass.simpleName}finally{busy=false}}}
    SemanticPanel("Structured memory",close) {
        OutlinedTextField(query,{query=it},label={Text("Search saved memory")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row { TextButton(onClick={editing=null;text="";ttl="";editor=true}){Text("Add")};TextButton(onClick={filters=!filters}){Text("Filter")};TextButton(onClick={run{context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Memory export",foundation.memory.exportJson(access)))}}){Text("Export JSON")} }
        if(filters){Row{Checkbox(showInactive,{showInactive=it});Text("Include inactive records")};if(projectId!=null)Row{Checkbox(projectOnly,{projectOnly=it});Text("Project only")};Box{TextButton(onClick={choosingKind=true}){Text(kindFilter?.name ?: "All types")};DropdownMenu(choosingKind,{choosingKind=false}){DropdownMenuItem(text={Text("All types")},onClick={kindFilter=null;choosingKind=false});MemoryKind.entries.forEach{k->DropdownMenuItem(text={Text(k.name)},onClick={kindFilter=k;choosingKind=false})}}}}
        val visible=all.filter{access.permits(it.scopeType,it.scopeId)&&(!projectOnly||it.scopeType=="PROJECT")&&(showInactive||it.status=="ACTIVE"&&it.expiresAt?.let{t->t>System.currentTimeMillis()}!=false)&&(kindFilter==null||it.kind==kindFilter?.name)&&it.text.contains(query,true)}
        if(visible.isEmpty())Text("No saved memory")
        if(visible.size>100)Text("Showing 100 of ${visible.size}; refine the search to see more")
        visible.take(100).forEach{m->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(m.text,maxLines=4,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text("${m.kind.lowercase()} · ${m.scopeType.lowercase()}${if(m.pinned)" · pinned"else""}",style=MaterialTheme.typography.labelSmall);TextButton(onClick={details=m}){Text("Details")}}}}
        TextButton(onClick={run{foundation.importApprovedLegacy(access)}}){Text("Copy approved legacy memory")}
        TextButton(onClick={run{foundation.memory.recover()}}){Text("Rebuild pending embeddings")}
        if(projectId!=null)TextButton(onClick={run{brief=foundation.database.dao().brief(projectId) ?: ProjectBrief(projectId,graph.database.projectDao().get(projectId)?.name.orEmpty())}}){Text("Project brief")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    if(editor)SemanticPanel(if(editing==null)"Add memory"else"Edit memory",{editor=false}) {
        OutlinedTextField(text,{text=it},label={Text("Content")},modifier=Modifier.fillMaxWidth(),minLines=3)
        if(MemoryContent.secret(text))Text("This may contain a credential. Saving is explicit; avoid storing secrets.",color=MaterialTheme.colorScheme.error)
        if(editing==null)Box{TextButton(onClick={choosingKind=true}){Text(kind.name)};DropdownMenu(choosingKind,{choosingKind=false}){MemoryKind.entries.forEach{k->DropdownMenuItem(text={Text(k.name)},onClick={kind=k;choosingKind=false})}}}
        if(editing!=null)Text("Scope: ${editing!!.scopeType.lowercase()}") else Box {
            TextButton(onClick={scopeMenu=true}){Text("Scope: ${writingScope.type.name.lowercase()}")}
            DropdownMenu(scopeMenu,{scopeMenu=false}) {
                DropdownMenuItem(text={Text("User")},onClick={writingScope=SemanticScope(ScopeType.USER,"local");scopeMenu=false})
                DropdownMenuItem(text={Text("Global · this device")},onClick={writingScope=SemanticScope(ScopeType.GLOBAL);scopeMenu=false})
                if(projectId!=null)DropdownMenuItem(text={Text("This project")},onClick={writingScope=SemanticScope(ScopeType.PROJECT,projectId);scopeMenu=false})
            }
        }
        OutlinedTextField(ttl,{ttl=it},label={Text("Expires in days (optional)")},singleLine=true)
        Button(enabled=text.isNotBlank()&&!busy,onClick={run{val expires=ttl.takeIf{it.isNotBlank()}?.let{val days=it.toLong();require(days in 1..36500);System.currentTimeMillis()+days*86_400_000};val old=editing;if(old==null)foundation.memory.create(text,writingScope,kind,expiresAt=expires)else foundation.memory.update(old.id,text,expires);editor=false}}){Text("Save")};error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    details?.let{m->SemanticPanel("Memory details",{details=null}){Text(m.text);Text("${m.kind} · ${m.status}\nVersion ${m.version}\nSource: ${m.sourceType}\nReference: ${m.sourceId ?: "none"}\nMessage: ${m.sourceMessageId ?: "none"}\nEmbedding: ${m.indexState}\nExpires: ${m.expiresAt?.let{java.util.Date(it)} ?: "never"}")
        if(m.status in setOf("ACTIVE","PENDING")){TextButton(onClick={editing=m;text=m.text;ttl=m.expiresAt?.let{((it-System.currentTimeMillis())/86_400_000).coerceAtLeast(1).toString()}.orEmpty();details=null;editor=true}){Text("Edit")};TextButton(onClick={run{foundation.memory.pin(m.id);details=null}}){Text(if(m.pinned)"Unpin"else"Pin")};if(m.status=="PENDING")TextButton(onClick={run{foundation.memory.approve(m.id);details=null}}){Text("Approve")}}
        TextButton(onClick={removal=m;details=null}){Text("Delete")}
    }}
    removal?.let{m->AlertDialog(onDismissRequest={removal=null},title={Text("Delete memory?")},text={Text("It will no longer be retrieved. Its vectors will be removed; existing source files and chats remain.")},confirmButton={TextButton(onClick={run{foundation.memory.delete(m.id);removal=null}}){Text("Delete")}},dismissButton={TextButton(onClick={removal=null}){Text("Cancel")}})}
    brief?.let{initial->var b by remember(initial){mutableStateOf(initial)};SemanticPanel("Project brief",{brief=null}){listOf("Goal" to b.goal,"Current state" to b.currentState,"Constraints" to b.constraints,"Decisions" to b.keyDecisions,"Active tasks" to b.activeTasks,"Architecture" to b.architecture).forEachIndexed{i,pair->OutlinedTextField(pair.second,{v->b=when(i){0->b.copy(goal=v);1->b.copy(currentState=v);2->b.copy(constraints=v);3->b.copy(keyDecisions=v);4->b.copy(activeTasks=v);else->b.copy(architecture=v)}},label={Text(pair.first)},modifier=Modifier.fillMaxWidth())};Button(onClick={run{foundation.saveBrief(b);brief=null}}){Text("Save")}}}
}

@Composable fun ContextDiagnosticsEntry() {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph;val f=graph.contextFoundation
    val enabled by f.enabled.collectAsState();var open by remember{mutableStateOf(false)}
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Context Builder V1 · Experimental",Modifier.weight(1f));Switch(enabled,{f.enable(it)})}
    TextButton(onClick={open=true}){Text("Context Inspector / Memory Validation")}
    if(open)ContextInspector{open=false}
}

@Composable
internal fun ContextBundleSummary(
    includedCount: Int,
    omittedCount: Int,
    estimatedInputTokens: Int,
    inputBudget: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (inputBudget > 0) (estimatedInputTokens.toFloat() / inputBudget).coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier.fillMaxWidth().testTag("context-inspector-summary"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(LocalAiSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Context budget", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(progress * 100).toInt()}%",
                    Modifier.testTag("context-budget-percent"),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                "$estimatedInputTokens / $inputBudget estimated input tokens",
                Modifier.testTag("context-budget-estimate"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                ContextCountPill("$includedCount included", warning = false)
                if (omittedCount > 0) ContextCountPill("$omittedCount omitted", warning = true)
            }
        }
    }
}

@Composable
private fun ContextCountPill(label: String, warning: Boolean) {
    val status = LocalAiStatusColors.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (warning) status.warningContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (warning) status.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(label, Modifier.padding(horizontal = LocalAiSpacing.x2, vertical = LocalAiSpacing.x1),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ContextInspectorSection(title: String, count: Int, omitted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = LocalAiSpacing.x2),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
    ) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        ContextCountPill(count.toString(), warning = omitted)
    }
}

@Composable
internal fun ContextInspectorEntry(
    title: String,
    detail: String,
    omitted: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = LocalAiStatusColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag(if (omitted) "context-inspector-omitted-entry" else "context-inspector-included-entry"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (omitted) status.warningContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (omitted) status.onWarningContainer else MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            1.dp,
            if (omitted) status.warning.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = LocalAiSpacing.x3, vertical = LocalAiSpacing.x2),
            verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x1),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ContextInspector(close:()->Unit) {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph;val f=graph.contextFoundation;val scope=rememberCoroutineScope()
    val projects by graph.workspace.activeProjects.collectAsState(initial=emptyList())
    var projectId by remember{mutableStateOf<String?>(null)};var picking by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")};var conversationId by remember{mutableStateOf("")};var bundle by remember{mutableStateOf<ContextBundle?>(null)};var full by remember{mutableStateOf(false)};var report by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)}
    fun run(block:suspend()->Unit){job=scope.launch{busy=true;error=null;try{withContext(Dispatchers.IO){block()}}catch(cancel:CancellationException){throw cancel}catch(e:Exception){error=e.message ?: e.javaClass.simpleName}finally{busy=false}}}
    SemanticPanel("Context Inspector",close) {
        Box{TextButton(onClick={picking=true}){Text(projects.firstOrNull{it.id==projectId}?.name ?: "User / Global")};DropdownMenu(picking,{picking=false}){DropdownMenuItem(text={Text("User / Global")},onClick={projectId=null;picking=false});projects.filter{it.workspaceKind=="PROJECT"}.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={projectId=p.id;picking=false})}}}
        OutlinedTextField(query,{query=it},label={Text("Query")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(conversationId,{conversationId=it},label={Text("Conversation ID (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy&&query.isNotBlank(),onClick={run{bundle=f.build(ContextRequest(query,ScopeAccess(projectId=projectId,sessionId=conversationId.takeIf{it.isNotBlank()}),conversationId=conversationId.takeIf{it.isNotBlank()}))}}){Text("Build context · no generation")}
        if(conversationId.isNotBlank())TextButton(enabled=!busy,onClick={run{val owner=graph.database.conversationDao().get(conversationId);check(owner!=null&&(owner.projectId==projectId||(projectId==null&&owner.projectId?.let{graph.database.projectDao().get(it)?.workspaceKind}=="CHAT"))){"Select the conversation's project first"};report="Indexed ${f.indexConversation(conversationId)} conversation source messages"}}){Text("Index this conversation")}
        bundle?.let{b->
            Column(verticalArrangement=Arrangement.spacedBy(LocalAiSpacing.x2)) {
                ContextBundleSummary(b.included.size,b.dropped.size,b.estimatedInputTokens,b.inputBudget)
                Text("${ContextTokenEstimator.METHOD} · ${b.timings.totalMs} ms · Output ${b.request.reservedOutput} · Safety ${b.safetyMargin}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(b.blockCosts.isNotEmpty()) {
                    Text("Budget by section", style=MaterialTheme.typography.titleSmall, modifier=Modifier.semantics { heading() })
                    b.blockCosts.forEach{(section,tokens)->Text("$section · $tokens estimated tokens",style=MaterialTheme.typography.bodySmall)}
                }
                if(b.included.isNotEmpty()) {
                    ContextInspectorSection("Included context",b.included.size)
                    b.included.forEach{ContextInspectorEntry("${it.id} · ${it.kind}","${it.scope.type} · ${it.estimatedTokens} estimated tokens",omitted=false)}
                }
                if(b.dropped.isNotEmpty()) {
                    ContextInspectorSection("Omitted context",b.dropped.size,omitted=true)
                    b.dropped.forEach{ContextInspectorEntry(it.item.id,it.reason,omitted=true)}
                }
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(full,{full=it});Text("Expand private context text")}
                if(full)b.included.forEach{Text(it.text)}
                TextButton(enabled=!busy,onClick={run{report=ContextInterop(graph).answer(b).answer}}){Text("Run with Gemma · explicit")}
                TextButton(onClick={report=GsonBuilder().setPrettyPrinting().create().toJson(b.safeReport())}){Text("Metadata report")}
            }
        }
        TextButton(enabled=!busy,onClick={run{report=ContextMemoryValidation(graph).run()}}){Text("Run Context / Memory V1 suite")}
        TextButton(enabled=!busy,onClick={run{report=ContextCapacityDiagnostics(graph).run()}}){Text("Probe context capacity · 4096 / 8192 candidate")}
        if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}){Text("Cancel")}}
        if(report.isNotEmpty()){Text(report.take(12000));TextButton(onClick={context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Context diagnostics",report))}){Text("Copy report")}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
