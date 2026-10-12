package com.localai.workspace.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.localai.workspace.ui.theme.LocalAiRadii
import com.localai.workspace.ui.theme.LocalAiSpacing

/** A Material 3 chat app bar whose height follows its wrapped title content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.containerColor,
        contentColor = colors.titleContentColor,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .defaultMinSize(minHeight = TopAppBarDefaults.TopAppBarExpandedHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(start = LocalAiSpacing.x1), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides colors.navigationIconContentColor) {
                    navigationIcon()
                }
            }
            Box(
                Modifier.weight(1f).padding(horizontal = LocalAiSpacing.x1, vertical = LocalAiSpacing.x2),
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.titleContentColor) {
                    title()
                }
            }
            Row(
                Modifier.padding(end = LocalAiSpacing.x1),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.actionIconContentColor) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun ChatHeaderTitle(
    agentTitle: @Composable () -> Unit,
    conversationTitle: String,
    modelStatus: String,
    onModelClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onModelClick)
            .padding(vertical = LocalAiSpacing.x1)
            .testTag("chat-header-title"),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth().testTag("chat-header-agent")) { agentTitle() }
        Text(conversationTitle, Modifier.fillMaxWidth().testTag("chat-header-conversation"),
            style = MaterialTheme.typography.titleSmall)
        Text(modelStatus, Modifier.fillMaxWidth().testTag("chat-header-model"),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LocalAiSpacing.x2, vertical = LocalAiSpacing.x1),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x1),
            ) {
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
                    maxLines = 5, enabled = !generating, shape = RoundedCornerShape(LocalAiRadii.large))
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
    Column(Modifier.fillMaxWidth().testTag("chat-rich-text")) {
        blocks.forEachIndexed { index, block ->
            blocks.getOrNull(index - 1)?.let { previous ->
                Spacer(Modifier.height(markdownBlockSpacing(previous, block)))
            }
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(block, Modifier.testTag("chat-rich-text-block-$index"))
                is MarkdownBlock.Table -> MarkdownTable(block, index)
                is MarkdownBlock.Paragraph -> InlineText(block.text, Modifier.testTag("chat-rich-text-block-$index"))
                is MarkdownBlock.Heading -> InlineText(
                    block.text,
                    Modifier.testTag("chat-rich-text-block-$index"),
                    headingLevel = block.level,
                )
                is MarkdownBlock.ListItem -> Row(
                    Modifier.fillMaxWidth().testTag("chat-rich-text-block-$index"),
                    horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
                ) {
                    Text(block.marker, style = MaterialTheme.typography.bodyLarge)
                    Box(Modifier.weight(1f)) { InlineText(block.text) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(block: MarkdownBlock.Table, blockIndex: Int) {
    val rows = remember(block) { listOf(block.headers) + block.rows }
    val scrollState = rememberScrollState()
    SelectionContainer {
        Column(Modifier.fillMaxWidth().testTag("chat-rich-text-block-$blockIndex")) {
            Column(
                Modifier.fillMaxWidth().horizontalScroll(scrollState)
                    .semantics {
                        collectionInfo = CollectionInfo(rows.size, block.headers.size)
                        contentDescription = "Table with ${block.headers.size} columns and ${block.rows.size} data rows. Scroll horizontally to view all columns."
                    }
                    .testTag("chat-markdown-table-$blockIndex"),
            ) {
                rows.forEachIndexed { rowIndex, cells ->
                    Row {
                        cells.forEachIndexed { columnIndex, value ->
                            val header = block.headers[columnIndex]
                            val isHeader = rowIndex == 0
                            val accessibleValue = if (isHeader) "Column header: $value" else "$header: $value"
                            Surface(
                                modifier = Modifier.width(176.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
                                shape = RectangleShape,
                                color = if (isHeader) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    text = inlineAnnotated(value, MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("chat-markdown-cell-$rowIndex-$columnIndex")
                                        .semantics {
                                            contentDescription = accessibleValue
                                            collectionItemInfo = CollectionItemInfo(rowIndex, 1, columnIndex, 1)
                                            if (isHeader) heading()
                                        },
                                    style = if (isHeader) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    else MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun markdownBlockSpacing(previous: MarkdownBlock, current: MarkdownBlock) = when {
    current is MarkdownBlock.Heading -> LocalAiSpacing.x4
    previous is MarkdownBlock.Heading -> LocalAiSpacing.x1
    previous is MarkdownBlock.ListItem && current is MarkdownBlock.ListItem -> LocalAiSpacing.x1
    else -> LocalAiSpacing.x3
}

@Composable
private fun InlineText(text: String, modifier: Modifier = Modifier, headingLevel: Int? = null) {
    val runs = remember(text) { BasicMarkdown.inline(text) }
    val codeColor = MaterialTheme.colorScheme.surfaceVariant
    val textStyle = when (headingLevel) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        4 -> MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        5, 6 -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        else -> MaterialTheme.typography.bodyLarge
    }
    val annotated = remember(runs, codeColor) { inlineAnnotated(runs, codeColor) }
    SelectionContainer {
        Text(
            annotated,
            modifier = modifier.semantics { if (headingLevel != null) heading() },
            style = textStyle,
        )
    }
}

private fun inlineAnnotated(text: String, codeColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    inlineAnnotated(BasicMarkdown.inline(text), codeColor)

private fun inlineAnnotated(runs: List<InlineRun>, codeColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        runs.forEach { run ->
            withStyle(SpanStyle(
                fontWeight = if (run.bold) FontWeight.SemiBold else null,
                fontFamily = if (run.code) FontFamily.Monospace else null,
                background = if (run.code) codeColor else androidx.compose.ui.graphics.Color.Unspecified,
            )) { append(run.text) }
        }
    }

@Composable
private fun CodeBlock(block: MarkdownBlock.Code, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(block.text) { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.language ?: "Code", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { clipboard.setText(AnnotatedString(block.text)); copied = true }) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, "Copy code")
                }
            }
            SelectionContainer {
                Text(block.text, Modifier.fillMaxWidth().testTag("chat-rich-text-code")
                    .horizontalScroll(rememberScrollState()).padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}
