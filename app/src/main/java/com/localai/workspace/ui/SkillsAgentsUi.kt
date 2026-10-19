package com.localai.workspace.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.agents.*
import com.localai.workspace.skills.*
import com.localai.workspace.sources.*
import com.localai.workspace.semantic.v2.ScopeType
import kotlinx.coroutines.*
import java.util.UUID

@Composable
fun SkillProposalReviewDialog(
    markdown: String,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (SkillDefinition) -> Unit,
) {
    var editableMarkdown by remember(markdown) { mutableStateOf(markdown) }
    var proposal by remember(markdown) { mutableStateOf(runCatching { SkillDraftProposalParser.parse(markdown) }.getOrNull()) }
    var parseError by remember(markdown) { mutableStateOf<String?>(runCatching { SkillDraftProposalParser.parse(markdown) }.exceptionOrNull()?.message) }
    var name by remember(markdown) { mutableStateOf(proposal?.definition?.name.orEmpty()) }
    var description by remember(markdown) { mutableStateOf(proposal?.definition?.description.orEmpty()) }
    var version by remember(markdown) { mutableStateOf(proposal?.definition?.version.orEmpty()) }
    var tags by remember(markdown) { mutableStateOf(proposal?.definition?.tags.orEmpty().joinToString(", ")) }
    var instructions by remember(markdown) { mutableStateOf(proposal?.definition?.instructions.orEmpty()) }
    var activation by remember(markdown) { mutableStateOf(proposal?.activationConditions.orEmpty()) }
    var responseFormat by remember(markdown) { mutableStateOf(proposal?.responseFormat.orEmpty()) }
    var restrictions by remember(markdown) { mutableStateOf(proposal?.restrictions.orEmpty()) }
    var useCases by remember(markdown) { mutableStateOf(proposal?.useCases.orEmpty()) }
    var selectedTools by remember(markdown) { mutableStateOf(emptySet<String>()) }
    var selectedCapabilities by remember(markdown) { mutableStateOf(emptySet<com.localai.workspace.capabilities.Capability>()) }
    var selectedSources by remember(markdown) { mutableStateOf(emptySet<SourceType>()) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (proposal == null) "Review SKILL.md draft" else "Review Skill proposal") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (proposal == null) {
                    Text("The response is not valid SKILL.md yet. Edit the draft or ask Gemma to return the required frontmatter; nothing has been saved.")
                    parseError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(editableMarkdown, { editableMarkdown = it }, label = { Text("SKILL.md draft") }, modifier = Modifier.fillMaxWidth(), minLines = 8)
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } else {
                    val parsed = requireNotNull(proposal)
                    Text("Review and edit the proposal. Tool, source and capability suggestions start unselected. Saving keeps the Skill disabled.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(version, { version = it }, label = { Text("Version") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tags · comma separated") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(activation, { activation = it }, label = { Text("Activation conditions") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(instructions, { instructions = it }, label = { Text("Procedural instructions") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                    OutlinedTextField(responseFormat, { responseFormat = it }, label = { Text("Response format") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(restrictions, { restrictions = it }, label = { Text("Restrictions") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(useCases, { useCases = it }, label = { Text("Use cases") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Text("Available tools · select explicitly", style = MaterialTheme.typography.titleSmall)
                    Text(if (parsed.suggestedTools.isEmpty()) "Gemma suggested no available tools." else "Gemma suggested: ${parsed.suggestedTools.sorted().joinToString()}. Suggestions are not selected automatically.", style = MaterialTheme.typography.bodySmall)
                    SkillDefinition.STANDARD_TOOLS.sorted().forEach { id -> Choice(id, id in selectedTools) { selectedTools = if (it) selectedTools + id else selectedTools - id } }
                    Text("Capabilities · select requirements explicitly", style = MaterialTheme.typography.titleSmall)
                    Text("Available locally: ${com.localai.workspace.capabilities.CapabilityPolicy.localCapabilities.map { it.name }.sorted().joinToString()}", style = MaterialTheme.typography.bodySmall)
                    if (parsed.suggestedCapabilities.isNotEmpty()) Text("Gemma suggested: ${parsed.suggestedCapabilities.map { it.name }.sorted().joinToString()}. Suggestions are not selected automatically.", style = MaterialTheme.typography.bodySmall)
                    com.localai.workspace.capabilities.Capability.entries.forEach { capability ->
                        val availableLocally = capability in com.localai.workspace.capabilities.CapabilityPolicy.localCapabilities
                        Choice(capability.name + if (availableLocally) "" else " · unavailable locally", capability in selectedCapabilities) {
                            selectedCapabilities = if (it) selectedCapabilities + capability else selectedCapabilities - capability
                        }
                    }
                    Text("Local sources · select explicitly", style = MaterialTheme.typography.titleSmall)
                    if (parsed.suggestedSources.isNotEmpty()) Text("Gemma suggested: ${parsed.suggestedSources.map { it.name }.sorted().joinToString()}. Suggestions are not selected automatically.", style = MaterialTheme.typography.bodySmall)
                    SourceType.local.sortedBy { it.name }.forEach { source ->
                        Choice(source.name, source in selectedSources) { selectedSources = if (it) selectedSources + source else selectedSources - source }
                    }
                    val unavailable = buildList {
                        parsed.unavailableTools.takeIf { it.isNotEmpty() }?.let { add("Tools not available: ${it.sorted().joinToString()}") }
                        val unavailableCapabilities = parsed.unavailableCapabilities + selectedCapabilities
                            .filter { it !in com.localai.workspace.capabilities.CapabilityPolicy.localCapabilities }
                            .map { it.name }
                        unavailableCapabilities.takeIf { it.isNotEmpty() }?.let { add("Capabilities not available in this local workspace: ${it.sorted().joinToString()}") }
                        parsed.unavailableSources.takeIf { it.isNotEmpty() }?.let { add("Sources not available: ${it.sorted().joinToString()}") }
                    }
                    unavailable.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (proposal == null) TextButton(enabled = !saving, onClick = {
                runCatching { SkillDraftProposalParser.parse(editableMarkdown) }
                    .onSuccess { parsed ->
                        proposal = parsed
                        name = parsed.definition.name
                        description = parsed.definition.description
                        version = parsed.definition.version
                        tags = parsed.definition.tags.joinToString(", ")
                        instructions = parsed.definition.instructions
                        activation = parsed.activationConditions
                        responseFormat = parsed.responseFormat
                        restrictions = parsed.restrictions
                        useCases = parsed.useCases
                        parseError = null
                    }
                    .onFailure { parseError = it.message ?: "SKILL_METADATA_INVALID" }
            }) { Text("Validate SKILL.md") }
            else TextButton(enabled = !saving && name.isNotBlank() && description.isNotBlank() && version.isNotBlank() && instructions.isNotBlank(), onClick = {
                val parsed = requireNotNull(proposal)
                onSave(parsed.configured(name, description, instructions, activation, responseFormat, restrictions, useCases,
                    selectedTools, selectedCapabilities, selectedSources, version = version,
                    tags = tags.split(',').map(String::trim).filter(String::isNotEmpty)))
            }) { Text(if (saving) "Saving…" else "Save disabled Skill") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable fun SkillsAgentsEntry(onCreateSkill: () -> Unit = {}) {
    var panel by remember{mutableStateOf<String?>(null)}
    Row {TextButton(onClick={panel="skills"}){Text("Skills")};TextButton(onClick={panel="agents"}){Text("Logical Agents")}}
    if(panel=="skills")SkillsManagement(onCreateSkill){panel=null}
    if(panel=="agents")AgentsManagement{panel=null}
}
@Composable private fun SkillsManagement(onCreateSkill:()->Unit,close:()->Unit) {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph
    val registry=graph.skillRegistry;val skills by registry.changes.collectAsState(initial=emptyList());val scope=rememberCoroutineScope()
    var editing by remember{mutableStateOf<SkillDefinition?>(null)};var error by remember{mutableStateOf<String?>(null)};var exportId by remember{mutableStateOf<String?>(null)}
    var removeId by remember{mutableStateOf<String?>(null)}
    fun run(block:suspend()->Unit){scope.launch{error=null;try{withContext(Dispatchers.IO){block()}}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}}}
    LaunchedEffect(Unit){registry.initialize()}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)run{
        val bytes=context.contentResolver.openInputStream(uri)?.use{stream->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true){currentCoroutineContext().ensureActive();val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=SkillPackageParser.MAX_BYTES){"SKILL_TOO_LARGE"};out.write(buffer,0,n)};out.toByteArray()
        } ?: error("SKILL_READ_FAILED")
        registry.install(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString())
    }}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")){uri->val id=exportId;if(uri!=null&&id!=null)run{context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(registry.export(id))} ?: error("SKILL_EXPORT_FAILED")}}
    SemanticPanel("Skills",close) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick=onCreateSkill,modifier=Modifier.fillMaxWidth()){Text("CREAR SKILL")}
            Text("Start a local Gemma conversation and review its SKILL.md proposal before saving.", style=MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick={importer.launch(arrayOf("text/*","application/octet-stream"))}){Text("Import SKILL.md")}
                Spacer(Modifier.weight(1f))
                TextButton(onClick={error=null;editing=SkillDefinition("skill.user-${UUID.randomUUID()}","","",instructions="")}){Text("Create manually")}
            }
        }
        skills.forEach{s->Card(Modifier.fillMaxWidth().clickable{error=null;editing=s}){Row(Modifier.padding(12.dp)){Column(Modifier.weight(1f)){Text(s.name);Text(s.description,style=MaterialTheme.typography.bodySmall);Text(s.origin.name.replace('_',' '),style=MaterialTheme.typography.labelSmall)};Switch(s.enabled,{run{registry.enable(s.id,it)}})}}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    editing?.let{s->SkillEditor(s,errorMessage=error,save={value->run{registry.update(value);editing=null}},export={exportId=s.id;exporter.launch("${s.name.replace(' ','-')}-SKILL.md")},remove={removeId=s.id},close={editing=null})}
    removeId?.let{id->AlertDialog(onDismissRequest={removeId=null},title={Text("Remove skill?")},text={Text("Existing chats and saved data remain. Agent assignments will ignore the missing skill.")},confirmButton={TextButton(onClick={run{registry.remove(id);removeId=null;editing=null}}){Text("Remove")}},dismissButton={TextButton(onClick={removeId=null}){Text("Cancel")}})}
}
@Composable private fun SkillEditor(skill:SkillDefinition,errorMessage:String?,save:(SkillDefinition)->Unit,export:()->Unit,remove:()->Unit,close:()->Unit) {
    val readOnly=skill.origin==SkillOrigin.BUILT_IN
    var name by remember(skill.id){mutableStateOf(skill.name)};var description by remember(skill.id){mutableStateOf(skill.description)};var instructions by remember(skill.id){mutableStateOf(skill.instructions)}
    var keywords by remember(skill.id){mutableStateOf(skill.routingProfile.keywords.joinToString(", "))};var explicit by remember(skill.id){mutableStateOf(skill.routingProfile.explicitOnly)}
    SemanticPanel(if(readOnly)skill.name else "Edit skill",close) {
        OutlinedTextField(name,{name=it},readOnly=readOnly,label={Text("Name")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(description,{description=it},readOnly=readOnly,label={Text("Description")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(instructions,{instructions=it},readOnly=readOnly,label={Text("Instructions")},modifier=Modifier.fillMaxWidth(),minLines=4)
        if(!readOnly){OutlinedTextField(keywords,{keywords=it},label={Text("Routing keywords, comma separated")},modifier=Modifier.fillMaxWidth());Row{Checkbox(explicit,{explicit=it});Text("Explicit selection only")}}
        if(skill.externalMetadata.isNotEmpty())Text("Additional metadata preserved; resources and scripts are not executed.",style=MaterialTheme.typography.bodySmall)
        errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Row {TextButton(onClick=export){Text("Export")};if(!readOnly){TextButton(onClick=remove){Text("Remove")};TextButton(enabled=name.isNotBlank()&&description.isNotBlank()&&instructions.isNotBlank(),onClick={save(skill.copy(name=name,description=description,instructions=instructions,routingProfile=skill.routingProfile.copy(keywords=keywords.split(',').map{it.trim()}.filter{it.isNotEmpty()},explicitOnly=explicit)))}){Text("Save")}}}
    }
}
@Composable private fun AgentsManagement(close:()->Unit) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph;val scope=rememberCoroutineScope()
    val agents by graph.agentRegistry.changes.collectAsState(initial=emptyList());val skills by graph.skillRegistry.changes.collectAsState(initial=emptyList())
    var editing by remember{mutableStateOf<AgentDefinition?>(null)};var error by remember{mutableStateOf<String?>(null)};var remove by remember{mutableStateOf<String?>(null)}
    fun run(block:suspend()->Unit){scope.launch{try{withContext(Dispatchers.IO){block()};error=null}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}}}
    LaunchedEffect(Unit){graph.agentRegistry.initialize();graph.skillRegistry.initialize()}
    SemanticPanel("Logical Agents",close) {
        TextButton(onClick={error=null;editing=AgentDefinition("agent.user-${UUID.randomUUID()}","",skillIds=skills.map{it.id}.toSet())}){Text("Create agent")}
        agents.forEach{a->Card(Modifier.fillMaxWidth().clickable{error=null;editing=a}){Row(Modifier.padding(12.dp)){Column(Modifier.weight(1f)){Text(a.name);Text(a.description,style=MaterialTheme.typography.bodySmall)};if(a.id!=AgentResolver.GENERAL)Switch(a.enabled,{run{graph.agentRegistry.enable(a.id,it)}})}}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    editing?.let{a->AgentEditor(a,skills,errorMessage=error,save={value->run{graph.agentRegistry.update(value);editing=null}},remove={remove=a.id},close={editing=null})}
    remove?.let{id->AlertDialog(onDismissRequest={remove=null},title={Text("Remove agent?")},text={Text("Projects referencing it will use General Agent. Saved memory is preserved.")},confirmButton={TextButton(onClick={run{graph.agentRegistry.remove(id);remove=null;editing=null}}){Text("Remove")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})}
}
@Composable private fun AgentEditor(agent:AgentDefinition,skills:List<SkillDefinition>,errorMessage:String?,save:(AgentDefinition)->Unit,remove:()->Unit,close:()->Unit) {
    val readOnly=agent.id==AgentResolver.GENERAL
    var name by remember(agent.id){mutableStateOf(agent.name)};var description by remember(agent.id){mutableStateOf(agent.description)};var role by remember(agent.id){mutableStateOf(agent.systemRole)}
    var assigned by remember(agent.id){mutableStateOf(agent.skillIds)};var tools by remember(agent.id){mutableStateOf(agent.allowedTools)};var sources by remember(agent.id){mutableStateOf(agent.allowedSourceTypes)};var scopes by remember(agent.id){mutableStateOf(agent.memoryScopes)}
    SemanticPanel(if(readOnly)agent.name else "Edit agent",close) {
        OutlinedTextField(name,{name=it},readOnly=readOnly,label={Text("Name")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(description,{description=it},readOnly=readOnly,label={Text("Description")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(role,{role=it},readOnly=readOnly,label={Text("Agent instructions")},modifier=Modifier.fillMaxWidth(),minLines=3)
        Text("Available Skills");skills.forEach{s->Choice(s.name,s.id in assigned,!readOnly){assigned=if(it)assigned+s.id else assigned-s.id}}
        Text("Tools");SkillDefinition.STANDARD_TOOLS.sorted().forEach{id->Choice(id,id in tools,!readOnly){tools=if(it)tools+id else tools-id}}
        Text("Sources");SourceType.local.forEach{type->Choice(type.name.replace('_',' '),type in sources,!readOnly){sources=if(it)sources+type else sources-type}}
        Text("Memory scopes");ScopeType.entries.forEach{type->Choice(type.name,type in scopes,!readOnly){scopes=if(it)scopes+type else scopes-type}}
        errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(!readOnly)Row{TextButton(onClick=remove){Text("Remove")};TextButton(enabled=name.isNotBlank(),onClick={save(agent.copy(name=name,description=description,systemRole=role,skillIds=assigned,allowedTools=tools,allowedSourceTypes=sources,memoryScopes=scopes))}){Text("Save")}}
    }
}
@Composable private fun Choice(label:String,checked:Boolean,enabled:Boolean=true,change:(Boolean)->Unit) {Row(Modifier.fillMaxWidth()){Checkbox(checked,change,enabled=enabled);Text(label,Modifier.padding(top=12.dp))}}

@Composable fun ProjectAgentPreference(projectId:String) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph;val scope=rememberCoroutineScope()
    val agents by graph.agentRegistry.changes.collectAsState(initial=emptyList());val preference by graph.agentsSkillsDatabase.dao().observePreference(projectId).collectAsState(initial=null)
    var open by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){graph.agentRegistry.initialize()}
    Box{TextButton(onClick={open=true}){Text("Preferred agent: ${agents.firstOrNull{it.id==preference?.preferredAgentId&&it.enabled}?.name ?: "General Agent"}")};DropdownMenu(open,{open=false}){
        DropdownMenuItem(text={Text("General Agent (default)")},onClick={open=false;scope.launch{graph.agentRegistry.prefer(projectId,null)}})
        agents.filter{it.enabled&&it.id!=AgentResolver.GENERAL}.forEach{a->DropdownMenuItem(text={Text(a.name)},onClick={open=false;scope.launch{graph.agentRegistry.prefer(projectId,a.id)}})}
    }}
}
@Composable fun ChatAgentSelector(vm:ChatViewModel,enabled:Boolean) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph
    val agents by graph.agentRegistry.changes.collectAsState(initial=emptyList());val explicit by vm.selectedAgentId.collectAsState();
    val preferred by graph.agentsSkillsDatabase.dao().observePreference(vm.agentProjectId).collectAsState(initial=null)
    var open by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){graph.agentRegistry.initialize()}
    val current=agents.firstOrNull{it.id==(explicit ?: preferred?.preferredAgentId)&&it.enabled} ?: agents.firstOrNull{it.id==AgentResolver.GENERAL}
    Box{TextButton(onClick={open=true},enabled=enabled){Text(current?.name ?: "General Agent",style=MaterialTheme.typography.labelSmall)};DropdownMenu(open,{open=false}){
        DropdownMenuItem(text={Text("Project / General default")},onClick={vm.selectAgent(null);open=false})
        agents.filter{it.enabled}.forEach{a->DropdownMenuItem(text={Text(a.name)},onClick={vm.selectAgent(a.id);open=false})}
    }}
}
@Composable fun ChatSkillSelection(vm:ChatViewModel,close:()->Unit) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph;val skills by graph.skillRegistry.changes.collectAsState(initial=emptyList());val ids by vm.explicitSkillIds.collectAsState()
    LaunchedEffect(Unit){graph.skillRegistry.initialize()}
    SemanticPanel("Skills for next turn",close){Text("Automatic deterministic selection remains active. Explicit selections still require agent and capability availability.",style=MaterialTheme.typography.bodySmall);skills.filter{it.enabled}.forEach{s->Choice(s.name,s.id in ids){vm.selectSkills(if(it)ids+s.id else ids-s.id)}};TextButton(onClick={vm.selectSkills(emptySet())}){Text("Clear explicit selections")}}
}
@Composable fun AgentRoutingDetails(vm:ChatViewModel) {
    val trace by vm.agentTrace.collectAsState();val calls by vm.toolCalls.collectAsState()
    trace?.let{t->Text("Agent: ${t.resolution.agent.name} · ${t.resolution.reason}");Text("Routing: ${t.selection.routingMs} ms · ${t.selection.active.size} active Skills")
        t.selection.evaluations.forEach{s->Text("${s.skillId}: enabled=${s.enabled} eligible=${s.eligible} active=${s.active} · ${s.reasons.joinToString()}${if(s.active&&calls.any{it.messageId==t.messageId&&it.status=="SUCCESS"&&it.toolId in t.selection.active.first{a->a.id==s.skillId}.allowedTools})" · associated tool executed" else ""}",style=MaterialTheme.typography.bodySmall)}
        t.tools.forEach{Text("${it.toolId} · ${it.reason}",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable fun ChatAgentLabel(vm:ChatViewModel) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph
    val agents by graph.agentRegistry.changes.collectAsState(initial=emptyList());val explicit by vm.selectedAgentId.collectAsState()
    val preferred by graph.agentsSkillsDatabase.dao().observePreference(vm.agentProjectId).collectAsState(initial=null)
    LaunchedEffect(Unit){graph.agentRegistry.initialize()}
    Text(agents.firstOrNull{it.id==(explicit ?: preferred?.preferredAgentId)&&it.enabled}?.name ?: "General Agent",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun AgentsSkillsDiagnosticsEntry() {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph;val scope=rememberCoroutineScope()
    var open by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var report by remember{mutableStateOf<String?>(null)};var error by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)}
    TextButton(onClick={open=true}){Text("Agents / Skills Inspector & Validation")}
    if(open)SemanticPanel("Agents / Skills V1",{if(!busy)open=false}) {
        val trace by graph.agentSkills.last.collectAsState()
        trace?.let{Text("${it.resolution.agent.name} · ${it.selection.active.size} active Skills · ${it.selection.routingMs} ms");TextButton(onClick={report=com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(it.safeReport())}){Text("Last routing metadata")}}
        TextButton(enabled=!busy,onClick={job=scope.launch{busy=true;error=null;try{report=AgentsSkillsValidation(graph).run()}catch(c:CancellationException){error="CANCELLED";throw c}catch(e:Exception){error=e.message ?: e.javaClass.simpleName}finally{busy=false}}}){Text("Run AGENTS_SKILLS_V1 · 12 checks")}
        if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}){Text("Cancel")}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        report?.let{Text(it.take(12000),style=MaterialTheme.typography.bodySmall);TextButton(onClick={copySemanticReport(context,it)}){Text("Copy metadata report")}}
    }
}
