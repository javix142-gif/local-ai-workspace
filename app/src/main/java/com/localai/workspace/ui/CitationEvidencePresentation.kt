package com.localai.workspace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.localai.workspace.data.CitationEvidenceEntity
import com.localai.workspace.data.CitationCellReferenceCodec
import com.localai.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.localai.workspace.ui.theme.LocalAiRadii

/** Displays only evidence records already validated and attached to this assistant message. */
@Composable
internal fun CitationEvidenceReferences(
    evidence: List<CitationEvidenceEntity>,
    onCitation: (String) -> Unit,
) {
    if (evidence.isEmpty()) return
    val countLabel = pluralStringResource(R.plurals.chat_sources_count, evidence.size, evidence.size)
    val countDescription = pluralStringResource(
        R.plurals.chat_sources_accessibility,
        evidence.size,
        evidence.size,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Default.Description, contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_sources_title), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() })
                Text(countLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = countDescription })
            }
        }
        Text(stringResource(R.string.chat_sources_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("citation-sources-note"))
        Text(stringResource(R.string.chat_sources_metadata_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("citation-sources-metadata-note"))
        evidence.forEach { item ->
            val pageDescription = item.pageStart?.let { stringResource(R.string.chat_source_page, it) }
            val lineDescription = item.lineStart?.let { start ->
                val end = item.lineEnd?.takeIf { it != start }
                if (end == null) stringResource(R.string.chat_source_lines_one, start)
                else stringResource(R.string.chat_source_lines_range, start, end)
            }
            val cellRefs = CitationCellReferenceCodec.decode(item.cellReferencesJson)?.takeIf { it.isNotEmpty() }
            val cellDescription = cellRefs?.let { "${stringResource(R.string.chat_source_cells)} ${it.joinToString(", ")}" }
            val location = listOfNotNull(pageDescription, lineDescription, cellDescription).joinToString(", ")
            val source = if (item.sourceLabel.isBlank()) stringResource(R.string.chat_source_local) else item.sourceLabel
            val accessibilityLabel = stringResource(R.string.chat_source_accessibility, item.evidenceId, listOfNotNull(source, location.takeIf(String::isNotBlank)).joinToString(", "))
            OutlinedCard(
                onClick = { onCitation(item.evidenceId) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(LocalAiRadii.card),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = accessibilityLabel
                    }
                    .testTag("citation-reference-${item.evidenceId}"),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(source, style = MaterialTheme.typography.titleSmall)
                    Text(item.evidenceId, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.pageStart?.let { page -> Text(stringResource(R.string.chat_source_page, page), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item.lineStart?.let { start ->
                        val end = item.lineEnd?.takeIf { it != start }
                        Text(if (end == null) stringResource(R.string.chat_source_lines_one, start) else stringResource(R.string.chat_source_lines_range, start, end),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("citation-lines"))
                    }
                    CitationCellReferenceCodec.decode(item.cellReferencesJson)?.takeIf { it.isNotEmpty() }?.let { refs ->
                        Text("${stringResource(R.string.chat_source_cells)} ${refs.joinToString(", ")}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("citation-cells"))
                    }
                }
            }
        }
    }
}

/** Shows source metadata and the stored excerpt without implying that a full document was read. */
@Composable
internal fun CitationEvidenceDialog(
    item: CitationEvidenceEntity,
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_source_close)) } },
        title = {
            Text(item.evidenceId, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("citation-detail-title"))
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("citation-detail-scroll"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (item.sourceLabel.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.chat_source_label), style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(item.sourceLabel, modifier = Modifier.testTag("citation-source"))
                    }
                }
                Text(item.pageStart?.let { stringResource(R.string.chat_source_page, it) } ?: stringResource(R.string.chat_source_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("citation-page"))
                item.lineStart?.let { start ->
                    val end = item.lineEnd?.takeIf { it != start }
                    Text(if (end == null) stringResource(R.string.chat_source_lines_one, start) else stringResource(R.string.chat_source_lines_range, start, end),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("citation-detail-lines"))
                }
                when (val refs = CitationCellReferenceCodec.decode(item.cellReferencesJson)) {
                    null -> Text(stringResource(R.string.chat_source_cell_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("citation-cell-location-unavailable"))
                    else -> if (refs.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.chat_source_cells), style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(refs.joinToString(", "), modifier = Modifier.testTag("citation-detail-cells"))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.chat_source_excerpt), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics { heading() })
                    Text(item.excerpt, modifier = Modifier.testTag("citation-excerpt"))
                }
                Text(
                    stringResource(R.string.chat_sources_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("citation-detail-sources-note"),
                )
                Text(
                    stringResource(R.string.chat_sources_metadata_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("citation-detail-metadata-note"),
                )
                TextButton(
                    onClick = onOpenSource,
                    modifier = Modifier.fillMaxWidth().testTag("citation-open-source"),
                ) { Text(stringResource(R.string.chat_source_open_indexed)) }
            }
        },
    )
}

/** Opens only the exact local segment linked to this citation, with the saved excerpt as fallback. */
@Composable
internal fun CitationSourcePreviewDialog(
    preview: CitationSourcePreview,
    onBackToCitation: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_source_preview_title)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_source_close)) } },
        dismissButton = { TextButton(onClick = onBackToCitation) { Text(stringResource(R.string.chat_source_back_to_citation)) } },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    .testTag("citation-source-preview-scroll"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(preview.sourceName, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("citation-source-preview-name"))
                Text(preview.location, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("citation-source-preview-location"))
                Text(preview.text, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("citation-source-preview-text"))
                Text(preview.notice, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("citation-source-preview-notice"))
            }
        },
    )
}
