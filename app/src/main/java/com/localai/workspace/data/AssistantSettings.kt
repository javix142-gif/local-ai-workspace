package com.localai.workspace.data

import android.content.Context
import com.localai.workspace.domain.tools.ChatToolSchemas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThinkingMode { OFF, AUTO, ON }
data class AssistantProfile(val thinking: ThinkingMode = ThinkingMode.AUTO, val tools: Set<String> = ChatToolSchemas.defaults)
class AssistantSettings(context: Context) {
    private val prefs = context.getSharedPreferences("local_assistant_profiles", 0)
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()
    fun forProject(id: String) = AssistantProfile(
        runCatching { ThinkingMode.valueOf(prefs.getString("$id.thinking", "AUTO")!!) }.getOrDefault(ThinkingMode.AUTO),
        prefs.getStringSet("$id.tools", ChatToolSchemas.defaults)!!.toSet())
    fun update(id: String, profile: AssistantProfile) {
        prefs.edit().putString("$id.thinking", profile.thinking.name).putStringSet("$id.tools", profile.tools).apply()
        revision.value++
    }
}
