package com.localai.workspace.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun ChatComposer(input: String, generating: Boolean, onInputChange: (String) -> Unit,
    onAttach: () -> Unit, onAttachImage: () -> Unit, imageEnabled: Boolean,
    onFiles: (() -> Unit)? = null, onStop: () -> Unit, onSend: () -> Unit,
    contextualControls: (@Composable () -> Unit)? = null,
    onAttachAudio: (() -> Unit)? = null, audioEnabled: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            contextualControls?.invoke() // Empty today; future functional chips can occupy this slot.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { menu = true }, enabled = !generating) { Icon(Icons.Default.Add, "Add attachment") }
                    DropdownMenu(expanded = menu && !generating, onDismissRequest = { menu = false }) {
                        if (imageEnabled) DropdownMenuItem(text = { Text("Image") }, leadingIcon = { Icon(Icons.Default.Image, null) },
                            onClick = { menu = false; onAttachImage() })
                        if (audioEnabled && onAttachAudio != null) DropdownMenuItem(text = { Text("Audio") }, leadingIcon = { Icon(Icons.Default.AudioFile, null) }, onClick = { menu = false; onAttachAudio() })
                        DropdownMenuItem(text = { Text("File") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) },
                            onClick = { menu = false; onAttach() })
                        onFiles?.let { action -> DropdownMenuItem(text = { Text("Workspace files") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { menu = false; action() }) }
                    }
                }
                OutlinedTextField(input, onInputChange, Modifier.weight(1f), placeholder = { Text("Ask anything…") },
                    maxLines = 5, enabled = !generating, shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                IconButton(onClick = if (generating) onStop else onSend, enabled = generating || input.isNotBlank()) {
                    Icon(if (generating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (generating) "Stop" else "Send")
                }
            }
        }
    }
}

@Composable
fun ChatRichText(content: String) {
    val blocks = remember(content) { BasicMarkdown.blocks(content) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.Paragraph -> InlineText(block.text)
                is MarkdownBlock.Heading -> InlineText(block.text, true)
                is MarkdownBlock.ListItem -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(block.marker, style = MaterialTheme.typography.bodyLarge)
                    Box(Modifier.weight(1f)) { InlineText(block.text) }
                }
            }
        }
    }
}

@Composable
private fun InlineText(text: String, heading: Boolean = false) {
    val runs = remember(text) { BasicMarkdown.inline(text) }
    val codeColor = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(runs, codeColor) { buildAnnotatedString {
        runs.forEach { run -> withStyle(SpanStyle(fontWeight = if (run.bold) FontWeight.SemiBold else null,
            fontFamily = if (run.code) FontFamily.Monospace else null,
            background = if (run.code) codeColor else androidx.compose.ui.graphics.Color.Unspecified)) { append(run.text) } }
    } }
    SelectionContainer { Text(annotated, style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(block.text) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.language ?: "Code", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { clipboard.setText(AnnotatedString(block.text)); copied = true }) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, "Copy code")
                }
            }
            SelectionContainer {
                Text(block.text, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}
