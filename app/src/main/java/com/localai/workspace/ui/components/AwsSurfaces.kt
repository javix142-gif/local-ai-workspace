package com.localai.workspace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.localai.workspace.ui.LocalAiStatusColors
import com.localai.workspace.ui.theme.LocalAiSpacing

enum class StatusTone { BRAND, SUCCESS, WARNING, ERROR, DISABLED }

/** Shared surface used by the Home and Memory dashboards. */
@Composable
fun AwsCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        content = content,
    )
}

/** A touch-sized category/action row with its count and expansion state kept visible. */
@Composable
fun AwsListRow(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    contentDescription: String = "$title, $count",
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = LocalAiSpacing.x2, vertical = LocalAiSpacing.x1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Status component shared by Home and the existing Android-action cards. */
@Composable
fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.BRAND,
) {
    val status = LocalAiStatusColors.current
    val foreground = when (tone) {
        StatusTone.BRAND -> MaterialTheme.colorScheme.primary
        StatusTone.SUCCESS -> status.success
        StatusTone.WARNING -> status.warning
        StatusTone.ERROR -> MaterialTheme.colorScheme.error
        StatusTone.DISABLED -> status.disabled
    }
    val background = when (tone) {
        StatusTone.BRAND -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.SUCCESS -> status.successContainer
        StatusTone.WARNING -> status.warningContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        StatusTone.DISABLED -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when (tone) {
        StatusTone.BRAND -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.SUCCESS -> status.onSuccessContainer
        StatusTone.WARNING -> status.onWarningContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = background,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(horizontal = LocalAiSpacing.x2, vertical = LocalAiSpacing.x1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x1),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = foreground, modifier = Modifier.size(8.dp)) {}
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
