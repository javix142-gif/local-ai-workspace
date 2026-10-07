package com.localai.workspace.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localai.workspace.data.*
import com.localai.workspace.domain.tools.ChatToolSchemas

@Composable
fun AssistantControls(profile: AssistantProfile, toolsAvailable: Boolean, thinkingAvailable: Boolean,
    enabled: Boolean, onChange: (AssistantProfile) -> Unit) {
    var tools by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (thinkingAvailable) AssistChip(onClick = { thinking = true }, enabled = enabled, label = { Text("Think: ${profile.thinking.name.lowercase().replaceFirstChar { it.uppercase() }}") })
        if (toolsAvailable) AssistChip(onClick = { tools = true }, enabled = enabled, label = { Text("Tools: ${if(profile.tools.isEmpty()) "Off" else "Auto"}") })
    }
    if (thinking) AlertDialog(onDismissRequest = { thinking = false }, title = { Text("Thinking") }, confirmButton = { TextButton(onClick = { thinking = false }) { Text("Done") } }, text = {
        Column { ThinkingMode.entries.forEach { mode -> TextButton(onClick = { onChange(profile.copy(thinking = mode)); thinking = false }) { Text(mode.name) } } }
    })
    if (tools) AlertDialog(onDismissRequest = { tools = false }, title = { Text("Tools") }, confirmButton = { TextButton(onClick = { tools = false }) { Text("Done") } }, text = {
        Column { TextButton(onClick = { onChange(profile.copy(tools = emptySet())) }) { Text("Off") }
            TextButton(onClick = { onChange(profile.copy(tools = ChatToolSchemas.defaults)) }) { Text("Auto") }
            ChatToolSchemas.available.filter { it != "python.execute" || com.localai.workspace.python.PythonAssets.available() }.forEach { id -> Row { Checkbox(id in profile.tools, { checked -> onChange(profile.copy(tools = if(checked) profile.tools + id else profile.tools - id)) }); Text(id, Modifier.padding(top = 12.dp)) } }
        }
    })
}
@Composable
fun ToolExecutionCard(call: ToolCallEntity) {
    var details by remember { mutableStateOf(false) }
    TextButton(onClick = { details = true }) { Text("${when(call.toolId) { "calculator.evaluate" -> "Calculator"; "python.execute" -> "Python"; else -> "Files" }} · ${if(call.status == "SUCCESS") "✓ Completed" else call.status}") }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(call.toolId) }, confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text("${call.status} · ${(call.finishedAt ?: call.startedAt) - call.startedAt} ms"); Text(call.validatedArgumentsJson); Text(call.resultJson.orEmpty().take(16000)) }
    })
}
