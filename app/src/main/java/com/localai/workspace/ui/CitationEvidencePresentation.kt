package com.localai.workspace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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

/** Displays only evidence records already validated and attached to this assistant message. */
@Composable
internal fun CitationEvidenceReferences(
    evidence: List<CitationEvidenceEntity>,
    onCitation: (String) -> Unit,
) {
    if (evidence.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Evidence references", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.semantics { heading() })
        evidence.forEach { item ->
            val accessibilityLabel = buildString {
                append("Evidence ${item.evidenceId}")
                item.sourceLabel.takeIf { it.isNotBlank() }?.let { append(", source $it") }
                item.pageStart?.let { append(", page $it") }
                item.lineStart?.let { start ->
                    val end = item.lineEnd?.takeIf { it != start }
                    append(if (end == null) ", line $start" else ", lines $start–$end")
                }
            }
            OutlinedCard(
                onClick = { onCitation(item.evidenceId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = accessibilityLabel
                    }
                    .testTag("citation-reference-${item.evidenceId}"),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(item.evidenceId, style = MaterialTheme.typography.labelLarge)
                    if (item.sourceLabel.isNotBlank()) {
                        Text(item.sourceLabel, style = MaterialTheme.typography.bodySmall)
                    }
                    item.pageStart?.let { page ->
                        Text("Page $page", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item.lineStart?.let { start ->
                        val end = item.lineEnd?.takeIf { it != start }
                        Text(if (end == null) "Line $start" else "Lines $start–$end",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("citation-lines"))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
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
                        Text("Source", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(item.sourceLabel, modifier = Modifier.testTag("citation-source"))
                    }
                }
                Text(item.pageStart?.let { "Page $it" } ?: "Local source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("citation-page"))
                item.lineStart?.let { start ->
                    val end = item.lineEnd?.takeIf { it != start }
                    Text(if (end == null) "Line $start" else "Lines $start–$end",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("citation-detail-lines"))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Excerpt", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics { heading() })
                    Text(item.excerpt, modifier = Modifier.testTag("citation-excerpt"))
                }
                Text(
                    "Excerpt only; it does not represent a full-document review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Metadata comes from the local index, not from model-generated citation text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onOpenSource,
                    modifier = Modifier.fillMaxWidth().testTag("citation-open-source"),
                ) { Text("Open indexed source segment") }
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
        title = { Text("Indexed source segment") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onBackToCitation) { Text("Back to citation") } },
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
