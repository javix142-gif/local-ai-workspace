package com.localai.workspace.inference

/** Analysis/unknown channels are never exposed to UI, Room, logs or tool Details. */
internal object VisibleModelOutput {
    fun select(text: String, channels: Map<String, String>): String = when {
        channels.containsKey("final") -> channels["final"].orEmpty()
        channels.keys.any { it !in setOf("thought", "analysis") } -> ""
        else -> text
    }
}
