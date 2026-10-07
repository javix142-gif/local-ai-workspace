package com.localai.workspace.data

/** Transparent local policy: explicit overrides win; no second model or hidden classifier. */
object AssistantRouting {
    fun thinking(mode: ThinkingMode, supported: Boolean, prompt: String): Boolean = supported && when(mode) {
        ThinkingMode.OFF -> false
        ThinkingMode.ON -> true
        ThinkingMode.AUTO -> Regex("(?i)(analiza|analyse|analyze|inconsisten|paso a paso|step.by.step|razona|reason|depura|debug|implementa|write.{0,15}code|escribe.{0,15}código)").containsMatchIn(prompt)
    }
    fun tools(enabled: Set<String>, prompt: String, documents: Boolean): Set<String> {
        val p = prompt.lowercase()
        return enabled.filter { id -> when(id) {
            "calculator.evaluate" -> Regex("[0-9]\\s*[+*/%()-]|calcula|calculate|aritm|cuánto es|cuanto es").containsMatchIn(p)
            "files.list" -> documents || Regex("archivo|file|documento|document|hoja|sheet|columna|column").containsMatchIn(p)
            "files.read" -> documents || Regex("archivo|file|documento|document|hoja|sheet|columna|column").containsMatchIn(p)
            "python.execute" -> Regex("python|desviación|desviacion|standard deviation|estadíst|statistics|promedio|average").containsMatchIn(p)
            else -> false
        } }.toSet()
    }
}
