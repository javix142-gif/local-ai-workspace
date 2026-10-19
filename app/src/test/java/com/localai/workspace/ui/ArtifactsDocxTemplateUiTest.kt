package com.localai.workspace.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ArtifactsDocxTemplateUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun selectingTemplateUpdatesTheVisibleDraftDescriptor() {
        compose.setContent { LocalAiTheme { ArtifactsDialog("docx-template-ui",onDismiss={}) } }

        compose.onNodeWithText("DOCX").performClick()
        compose.onNodeWithText("Built-in DOCX template").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("docx-template-report").performScrollTo().performClick()
        compose.onNodeWithTag("docx-selected-template").assertTextEquals("Selected template: Report")
        assertTrue(compose.onNodeWithTag("artifact-spec-editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.contains("\"template\": \"report\""))
        compose.onNodeWithTag("docx-template-meeting_minutes").performScrollTo().performClick()
        compose.onNodeWithTag("docx-selected-template").assertTextEquals("Selected template: Meeting minutes")
        assertTrue(compose.onNodeWithTag("artifact-spec-editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.contains("\"template\": \"meeting_minutes\""))
    }
}
