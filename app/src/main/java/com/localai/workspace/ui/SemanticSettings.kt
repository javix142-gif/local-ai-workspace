package com.localai.workspace.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.domain.model.toDescriptor
import kotlinx.coroutines.launch
import androidx.room.withTransaction

@Composable fun SemanticSettings(projectId: String? = null) { SemanticV2Settings(projectId) }
@Composable fun ProjectAssistantSettings(projectId:String) {
 val graph=(LocalContext.current.applicationContext as LocalAiApplication).graph
 val revision by graph.assistantSettings.changes.collectAsState()
 val profile=remember(revision,projectId){graph.assistantSettings.forProject(projectId)}
 val models by graph.workspace.allModels.collectAsState(initial=emptyList())
 val project by graph.workspace.observeProject(projectId).collectAsState(initial=null)
 val model=models.firstOrNull { it.id==project?.defaultModelId } ?: models.firstOrNull()
 var choosingModel by remember { mutableStateOf(false) }
 val pickerScope=rememberCoroutineScope()
 Box { TextButton(onClick={choosingModel=true}) { Text(model?.displayName?.let { com.localai.workspace.ui.humanModelName(it) } ?: "Choose model") }
  DropdownMenu(choosingModel,{choosingModel=false}) { models.forEach { candidate->DropdownMenuItem(text={Text(com.localai.workspace.ui.humanModelName(candidate.displayName))},onClick={choosingModel=false;pickerScope.launch { graph.database.projectDao().setDefaultModel(projectId,candidate.id,System.currentTimeMillis()) }}) } }
 }
 ProjectAgentPreference(projectId)
 val descriptor=model?.let { it.toDescriptor() }
 AssistantControls(profile,descriptor?.let { it.runtime==com.localai.workspace.domain.model.RuntimeType.LITERT_LM && com.localai.workspace.domain.model.ModelCapability.TOOL_CALLING in it.capabilities }==true,
  descriptor?.let { it.runtime==com.localai.workspace.domain.model.RuntimeType.LITERT_LM && com.localai.workspace.domain.model.ModelCapability.THINKING in it.capabilities }==true,true) { graph.assistantSettings.update(projectId,it) }
 val scope=rememberCoroutineScope();
 Row { Text("Approved memory",Modifier.weight(1f));Switch(project?.memoryEnabled==true,{ value->scope.launch { graph.database.withTransaction { graph.database.projectDao().get(projectId)?.let { graph.database.projectDao().upsert(it.copy(memoryEnabled=value,updatedAt=System.currentTimeMillis())) } } } }) }
 SemanticSettings(projectId)
}
