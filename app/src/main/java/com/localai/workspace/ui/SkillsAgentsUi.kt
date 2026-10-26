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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.R
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
    val context = LocalContext.current
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
        title = { Text(stringResource(if (proposal == null) R.string.chat_skill_review_draft_title else R.string.chat_skill_review_proposal_title)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (proposal == null) {
                    Text(stringResource(R.string.chat_skill_invalid_draft))
                    parseError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(editableMarkdown, { editableMarkdown = it }, label = { Text(stringResource(R.string.chat_skill_draft_label)) }, modifier = Modifier.fillMaxWidth(), minLines = 8)
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } else {
                    val parsed = requireNotNull(proposal)
                    Text(stringResource(R.string.chat_skill_review_guidance), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.chat_skill_name)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.chat_skill_description)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(version, { version = it }, label = { Text(stringResource(R.string.chat_skill_version)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.chat_skill_tags)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(activation, { activation = it }, label = { Text(stringResource(R.string.chat_skill_activation)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(instructions, { instructions = it }, label = { Text(stringResource(R.string.chat_skill_instructions)) }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                    OutlinedTextField(responseFormat, { responseFormat = it }, label = { Text(stringResource(R.string.chat_skill_response_format)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(restrictions, { restrictions = it }, label = { Text(stringResource(R.string.chat_skill_restrictions)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(useCases, { useCases = it }, label = { Text(stringResource(R.string.chat_skill_use_cases)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Text(stringResource(R.string.chat_skill_available_tools), style = MaterialTheme.typography.titleSmall)
                    Text(if (parsed.suggestedTools.isEmpty()) stringResource(R.string.chat_skill_no_tools_suggested) else stringResource(R.string.chat_skill_tools_suggested,parsed.suggestedTools.sorted().joinToString()), style = MaterialTheme.typography.bodySmall)
                    SkillDefinition.STANDARD_TOOLS.sorted().forEach { id -> Choice(id, id in selectedTools) { selectedTools = if (it) selectedTools + id else selectedTools - id } }
                    Text(stringResource(R.string.chat_skill_capabilities), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.chat_skill_local_capabilities,com.localai.workspace.capabilities.CapabilityPolicy.localCapabilities.map { it.name }.sorted().joinToString()), style = MaterialTheme.typography.bodySmall)
                    if (parsed.suggestedCapabilities.isNotEmpty()) Text(stringResource(R.string.chat_skill_capabilities_suggested,parsed.suggestedCapabilities.map { it.name }.sorted().joinToString()), style = MaterialTheme.typography.bodySmall)
                    com.localai.workspace.capabilities.Capability.entries.forEach { capability ->
                        val availableLocally = capability in com.localai.workspace.capabilities.CapabilityPolicy.localCapabilities
                        Choice(capability.name + if (availableLocally) "" else stringResource(R.string.chat_skill_unavailable_local), capability in selectedCapabilities) {
                            selectedCapabilities = if (it) selectedCapabilities + capability else selectedCapabilities - capability
                        }
                    }
                    Text(stringResource(R.string.chat_skill_local_sources), style = MaterialTheme.typography.titleSmall)
                    if (parsed.suggestedSources.isNotEmpty()) Text(stringResource(R.string.chat_skill_sources_suggested,parsed.suggestedSources.map { it.name }.sorted().joinToString()), style = MaterialTheme.typography.bodySmall)
                    SourceType.local.sortedBy { it.name }.forEach { source ->
                        Choice(source.name, source in selectedSources) { selectedSources = if (it) selectedSources + source else selectedSources - source }
                    }
                    val unavailable = buildList {
                        parsed.unavailableTools.takeIf { it.isNotEmpty() }?.let { add(context.getString(R.string.chat_skill_tools_unavailable,it.sorted().joinToString())) }
                        val unavailableCapabilities = parsed.unavailableCapabilities + selectedCapabilities
                            .filter { it !in com.localai.workspace.capabilities.CapabilityPolicy.localCapabilities }
                            .map { it.name }
                        unavailableCapabilities.takeIf { it.isNotEmpty() }?.let { add(context.getString(R.string.chat_skill_capabilities_unavailable,it.sorted().joinToString())) }
                        parsed.unavailableSources.takeIf { it.isNotEmpty() }?.let { add(context.getString(R.string.chat_skill_sources_unavailable,it.sorted().joinToString())) }
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
            }) { Text(stringResource(R.string.chat_skill_validate)) }
            else TextButton(enabled = !saving && name.isNotBlank() && description.isNotBlank() && version.isNotBlank() && instructions.isNotBlank(), onClick = {
                val parsed = requireNotNull(proposal)
                onSave(parsed.configured(name, description, instructions, activation, responseFormat, restrictions, useCases,
                    selectedTools, selectedCapabilities, selectedSources, version = version,
                    tags = tags.split(',').map(String::trim).filter(String::isNotEmpty)))
            }) { Text(stringResource(if (saving) R.string.chat_skill_saving else R.string.chat_skill_save_disabled)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable fun SkillsAgentsEntry(
    onCreateSkill: () -> Unit = {},
    canCreateSkill: Boolean = true,
    skillUnavailableReason: String? = null,
) {
    var panel by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val largeText = LocalDensity.current.fontScale >= 1.5f
        if (largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkillsAgentsEntryButton(R.string.navigation_skills, "more-skills-open") { panel = "skills" }
                SkillsAgentsEntryButton(R.string.skills_agents, "more-agents-open") { panel = "agents" }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkillsAgentsEntryButton(R.string.navigation_skills, "more-skills-open", Modifier.weight(1f)) { panel = "skills" }
                SkillsAgentsEntryButton(R.string.skills_agents, "more-agents-open", Modifier.weight(1f)) { panel = "agents" }
            }
        }
        if (panel == "skills") SkillsManagement(
            onCreateSkill = onCreateSkill,
            close = { panel = null },
            canCreateSkill = canCreateSkill,
            unavailableReason = skillUnavailableReason,
        )
        if (panel == "agents") AgentsManagement { panel = null }
    }
}

@Composable
private fun SkillsAgentsEntryButton(title: Int, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
    ) { Text(stringResource(title)) }
}

@Composable private fun SkillsManagement(
    onCreateSkill: () -> Unit,
    close: () -> Unit,
    canCreateSkill: Boolean,
    unavailableReason: String?,
) {
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
    SemanticPanel(stringResource(R.string.skills_manage_title), close) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCreateSkill,
                enabled = canCreateSkill,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("skills-create-conversation"),
            ) { Text(stringResource(R.string.skills_create)) }
            Text(stringResource(R.string.skills_create_summary), style = MaterialTheme.typography.bodySmall)
            if (!canCreateSkill && unavailableReason != null) {
                Text(unavailableReason, Modifier.testTag("skills-create-unavailable"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = { importer.launch(arrayOf("text/*", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.skills_import)) }
            OutlinedButton(
                onClick = { error = null; editing = SkillDefinition("skill.user-${UUID.randomUUID()}", "", "", instructions = "") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.skills_create_manual)) }
        }
        skills.forEach{s->Card(Modifier.fillMaxWidth().clickable{error=null;editing=s}){Row(Modifier.padding(12.dp)){Column(Modifier.weight(1f)){Text(s.name);Text(s.description,style=MaterialTheme.typography.bodySmall);Text(s.origin.name.replace('_',' '),style=MaterialTheme.typography.labelSmall)};Switch(s.enabled,{run{registry.enable(s.id,it)}})}}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    editing?.let{s->SkillEditor(s,errorMessage=error,save={value->run{registry.update(value);editing=null}},export={exportId=s.id;exporter.launch("${s.name.replace(' ','-')}-SKILL.md")},remove={removeId=s.id},close={editing=null})}
    removeId?.let{id->AlertDialog(onDismissRequest={removeId=null},title={Text(stringResource(R.string.skills_remove_title))},text={Text(stringResource(R.string.skills_remove_note))},confirmButton={TextButton(onClick={run{registry.remove(id);removeId=null;editing=null}}){Text(stringResource(R.string.skills_remove))}},dismissButton={TextButton(onClick={removeId=null}){Text(stringResource(R.string.common_cancel))}})}
}
@Composable private fun SkillEditor(skill:SkillDefinition,errorMessage:String?,save:(SkillDefinition)->Unit,export:()->Unit,remove:()->Unit,close:()->Unit) {
    val readOnly=skill.origin==SkillOrigin.BUILT_IN
    var name by remember(skill.id){mutableStateOf(skill.name)};var description by remember(skill.id){mutableStateOf(skill.description)};var instructions by remember(skill.id){mutableStateOf(skill.instructions)}
    var keywords by remember(skill.id){mutableStateOf(skill.routingProfile.keywords.joinToString(", "))};var explicit by remember(skill.id){mutableStateOf(skill.routingProfile.explicitOnly)}
    SemanticPanel(if(readOnly)skill.name else stringResource(R.string.skills_edit_title),close) {
        OutlinedTextField(name,{name=it},readOnly=readOnly,label={Text(stringResource(R.string.skills_name))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(description,{description=it},readOnly=readOnly,label={Text(stringResource(R.string.skills_description))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(instructions,{instructions=it},readOnly=readOnly,label={Text(stringResource(R.string.skills_instructions))},modifier=Modifier.fillMaxWidth(),minLines=4)
        if(!readOnly){OutlinedTextField(keywords,{keywords=it},label={Text(stringResource(R.string.skills_routing_keywords))},modifier=Modifier.fillMaxWidth());Row{Checkbox(explicit,{explicit=it});Text(stringResource(R.string.skills_explicit_only))}}
        if(skill.externalMetadata.isNotEmpty())Text(stringResource(R.string.skills_metadata_notice),style=MaterialTheme.typography.bodySmall)
        errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Row {TextButton(onClick=export){Text(stringResource(R.string.skills_export))};if(!readOnly){TextButton(onClick=remove){Text(stringResource(R.string.skills_remove))};TextButton(enabled=name.isNotBlank()&&description.isNotBlank()&&instructions.isNotBlank(),onClick={save(skill.copy(name=name,description=description,instructions=instructions,routingProfile=skill.routingProfile.copy(keywords=keywords.split(',').map{it.trim()}.filter{it.isNotEmpty()},explicitOnly=explicit)))}){Text(stringResource(R.string.skills_save))}}}
    }
}
@Composable private fun AgentsManagement(close:()->Unit) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph;val scope=rememberCoroutineScope()
    val agents by graph.agentRegistry.changes.collectAsState(initial=emptyList());val skills by graph.skillRegistry.changes.collectAsState(initial=emptyList())
    var editing by remember{mutableStateOf<AgentDefinition?>(null)};var error by remember{mutableStateOf<String?>(null)};var remove by remember{mutableStateOf<String?>(null)}
    fun run(block:suspend()->Unit){scope.launch{try{withContext(Dispatchers.IO){block()};error=null}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}}}
    LaunchedEffect(Unit){graph.agentRegistry.initialize();graph.skillRegistry.initialize()}
    SemanticPanel(stringResource(R.string.skills_agents),close) {
        TextButton(onClick={error=null;editing=AgentDefinition("agent.user-${UUID.randomUUID()}","",skillIds=skills.map{it.id}.toSet())}){Text(stringResource(R.string.agents_create))}
        agents.forEach{a->Card(Modifier.fillMaxWidth().clickable{error=null;editing=a}){Row(Modifier.padding(12.dp)){Column(Modifier.weight(1f)){Text(if(a.id==AgentResolver.GENERAL)stringResource(R.string.chat_agent_general) else a.name);Text(a.description,style=MaterialTheme.typography.bodySmall)};if(a.id!=AgentResolver.GENERAL)Switch(a.enabled,{run{graph.agentRegistry.enable(a.id,it)}})}}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    editing?.let{a->AgentEditor(a,skills,errorMessage=error,save={value->run{graph.agentRegistry.update(value);editing=null}},remove={remove=a.id},close={editing=null})}
    remove?.let{id->AlertDialog(onDismissRequest={remove=null},title={Text(stringResource(R.string.agents_remove_title))},text={Text(stringResource(R.string.agents_remove_note))},confirmButton={TextButton(onClick={run{graph.agentRegistry.remove(id);remove=null;editing=null}}){Text(stringResource(R.string.skills_remove))}},dismissButton={TextButton(onClick={remove=null}){Text(stringResource(R.string.common_cancel))}})}
}
@Composable private fun AgentEditor(agent:AgentDefinition,skills:List<SkillDefinition>,errorMessage:String?,save:(AgentDefinition)->Unit,remove:()->Unit,close:()->Unit) {
    val readOnly=agent.id==AgentResolver.GENERAL
    var name by remember(agent.id){mutableStateOf(agent.name)};var description by remember(agent.id){mutableStateOf(agent.description)};var role by remember(agent.id){mutableStateOf(agent.systemRole)}
    var assigned by remember(agent.id){mutableStateOf(agent.skillIds)};var tools by remember(agent.id){mutableStateOf(agent.allowedTools)};var sources by remember(agent.id){mutableStateOf(agent.allowedSourceTypes)};var scopes by remember(agent.id){mutableStateOf(agent.memoryScopes)}
    val displayedName=if(readOnly)stringResource(R.string.chat_agent_general) else name
    SemanticPanel(if(readOnly)displayedName else stringResource(R.string.skills_agents),close) {
        OutlinedTextField(displayedName,{name=it},readOnly=readOnly,label={Text(stringResource(R.string.skills_name))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(description,{description=it},readOnly=readOnly,label={Text(stringResource(R.string.skills_description))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(role,{role=it},readOnly=readOnly,label={Text(stringResource(R.string.agents_instructions))},modifier=Modifier.fillMaxWidth(),minLines=3)
        Text(stringResource(R.string.agents_available_skills));skills.forEach{s->Choice(s.name,s.id in assigned,!readOnly){assigned=if(it)assigned+s.id else assigned-s.id}}
        Text(stringResource(R.string.agents_tools));SkillDefinition.STANDARD_TOOLS.sorted().forEach{id->Choice(id,id in tools,!readOnly){tools=if(it)tools+id else tools-id}}
        Text(stringResource(R.string.agents_sources));SourceType.local.forEach{type->Choice(type.name.replace('_',' '),type in sources,!readOnly){sources=if(it)sources+type else sources-type}}
        Text(stringResource(R.string.agents_memory_scopes));ScopeType.entries.forEach{type->Choice(type.name,type in scopes,!readOnly){scopes=if(it)scopes+type else scopes-type}}
        errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(!readOnly)Row{TextButton(onClick=remove){Text(stringResource(R.string.skills_remove))};TextButton(enabled=name.isNotBlank(),onClick={save(agent.copy(name=name,description=description,systemRole=role,skillIds=assigned,allowedTools=tools,allowedSourceTypes=sources,memoryScopes=scopes))}){Text(stringResource(R.string.skills_save))}}
    }
}
@Composable private fun Choice(label:String,checked:Boolean,enabled:Boolean=true,change:(Boolean)->Unit) {Row(Modifier.fillMaxWidth()){Checkbox(checked,change,enabled=enabled);Text(label,Modifier.padding(top=12.dp))}}

@Composable fun ProjectAgentPreference(projectId:String) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph;val scope=rememberCoroutineScope()
    val agents by graph.agentRegistry.changes.collectAsState(initial=emptyList());val preference by graph.agentsSkillsDatabase.dao().observePreference(projectId).collectAsState(initial=null)
    var open by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){graph.agentRegistry.initialize()}
    val preferredAgent=agents.firstOrNull{it.id==preference?.preferredAgentId&&it.enabled}
    val preferredName=if(preferredAgent==null||preferredAgent.id==AgentResolver.GENERAL)stringResource(R.string.chat_agent_general) else preferredAgent.name
    Box{TextButton(onClick={open=true}){Text(stringResource(R.string.agent_preferred,preferredName))};DropdownMenu(open,{open=false}){
        DropdownMenuItem(text={Text(stringResource(R.string.agent_general_default))},onClick={open=false;scope.launch{graph.agentRegistry.prefer(projectId,null)}})
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
    val generalName=stringResource(R.string.chat_agent_general)
    Box{TextButton(onClick={open=true},enabled=enabled){Text(if(current?.id==AgentResolver.GENERAL)generalName else current?.name ?: generalName,style=MaterialTheme.typography.labelSmall)};DropdownMenu(open,{open=false}){
        DropdownMenuItem(text={Text(stringResource(R.string.chat_agent_project_default))},onClick={vm.selectAgent(null);open=false})
        agents.filter{it.enabled}.forEach{a->DropdownMenuItem(text={Text(if(a.id==AgentResolver.GENERAL)generalName else a.name)},onClick={vm.selectAgent(a.id);open=false})}
    }}
}
@Composable fun ChatSkillSelection(vm:ChatViewModel,close:()->Unit) {
    val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph;val skills by graph.skillRegistry.changes.collectAsState(initial=emptyList());val ids by vm.explicitSkillIds.collectAsState()
    LaunchedEffect(Unit){graph.skillRegistry.initialize()}
    SemanticPanel(stringResource(R.string.chat_skills_next_turn),close){Text(stringResource(R.string.chat_skills_auto_description),style=MaterialTheme.typography.bodySmall);skills.filter{it.enabled}.forEach{s->Choice(s.name,s.id in ids){vm.selectSkills(if(it)ids+s.id else ids-s.id)}};TextButton(onClick={vm.selectSkills(emptySet())}){Text(stringResource(R.string.chat_skills_clear_selection))}}
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
    val selected=agents.firstOrNull{it.id==(explicit ?: preferred?.preferredAgentId)&&it.enabled}
    Text(if(selected==null||selected.id==AgentResolver.GENERAL)stringResource(R.string.chat_agent_general) else selected.name,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
