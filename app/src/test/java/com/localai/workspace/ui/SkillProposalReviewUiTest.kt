package com.localai.workspace.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.localai.workspace.R
import com.localai.workspace.skills.SkillDefinition
import com.localai.workspace.skills.SkillOrigin
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class SkillProposalReviewUiTest {
    @get:Rule val compose = createComposeRule()

    private fun localized(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    @Test fun reviewedProposalSavesDisabledAndWithNoImplicitToolGrant() {
        var saved: SkillDefinition? = null
        compose.setContent {
            LocalAiTheme {
                SkillProposalReviewDialog(
                    markdown = """---
name: "Checklist Review"
description: "Reviews a checklist."
version: "1.0"
tags:
  - "checklist"
activation: "When the user explicitly selects it."
required-capabilities: "LOCAL_READ, NETWORK_READ"
allowed-tools: "files.read"
allowed-sources: "PROJECT_DOCUMENT"
response-format: "Numbered findings."
restrictions: "Do not invent findings."
use-cases: "Review a maintenance checklist."
---
## Instructions
Read the checklist and report supported findings.
""".trimIndent(),
                    saving = false,
                    errorMessage = null,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithText(localized(R.string.chat_skill_review_proposal_title)).assertIsDisplayed()
        assertEquals(3, compose.onAllNodesWithText("No se seleccionan automáticamente.", substring = true)
            .fetchSemanticsNodes().size)
        compose.onNodeWithText("NETWORK_READ${localized(R.string.chat_skill_unavailable_local)}")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.chat_skill_capabilities_unavailable, "NETWORK_READ"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.chat_skill_save_disabled)).performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertEquals(SkillOrigin.USER_AUTHORED, result.origin)
            assertFalse(result.enabled)
            assertTrue(result.allowedTools.isEmpty())
            assertTrue(result.requiredCapabilities.isEmpty())
            assertTrue(result.allowedSourceTypes.isEmpty())
        }
    }
}
