package com.localai.workspace.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.localai.workspace.ui.theme.LocalAiLayout
import com.localai.workspace.ui.theme.LocalAiSpacing

/** Shared centered reading frame for the top-level workspace, model, and settings surfaces. */
@Composable
fun AppScreenContent(
    modifier: Modifier = Modifier,
    columnModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = LocalAiLayout.maxReadingWidth + LocalAiSpacing.x4 * 2)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = LocalAiLayout.screenMargin)
                .then(columnModifier),
            content = content,
        )
    }
}

/** Applies the product's minimum touch area to custom controls; Material controls keep their own sizing. */
fun Modifier.minimumAppTouchTarget(): Modifier =
    sizeIn(minWidth = LocalAiLayout.minimumTouchTarget, minHeight = LocalAiLayout.minimumTouchTarget)
