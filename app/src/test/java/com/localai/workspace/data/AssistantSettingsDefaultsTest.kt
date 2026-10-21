package com.localai.workspace.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.domain.tools.ChatToolSchemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AssistantSettingsDefaultsTest {
    @Test fun legacyAutoToolSetGainsNewLocalReadToolsButCustomAndOffSelectionsStayUnchanged() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("local_assistant_profiles", 0)
        prefs.edit().putStringSet("legacy.tools", ChatToolSchemas.previousAutoDefaults).commit()
        prefs.edit().putStringSet("custom.tools", setOf("calculator.evaluate")).commit()
        prefs.edit().putStringSet("off.tools", emptySet()).commit()

        assertEquals(ChatToolSchemas.defaults, AssistantSettings(context).forProject("legacy").tools)
        assertTrue(AssistantSettings(context).forProject("legacy").tools.containsAll(ChatToolSchemas.personalDataToolIds))
        assertEquals(setOf("calculator.evaluate"), AssistantSettings(context).forProject("custom").tools)
        assertEquals(emptySet<String>(), AssistantSettings(context).forProject("off").tools)
    }
}
