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
        val codePatchIntent = Regex("parche|patch|diff").containsMatchIn(p) &&
            (Regex("c[oó]digo|code|archivo fuente|source file").containsMatchIn(p) || listOf(".kt", ".java", ".py", ".js", ".ts", ".rs", ".go", ".cpp").any(p::contains))
        val xlsxCellEditIntent = Regex("actualiza|modifica|edita|cambia|update|edit|set").containsMatchIn(p) &&
            Regex("excel|xlsx|planilla|hoja|sheet|celda|cell").containsMatchIn(p) &&
            Regex("(?<![a-z0-9_])\\$?[a-z]{1,3}\\$?[1-9][0-9]{0,6}(?![a-z0-9_])").containsMatchIn(p) &&
            !Regex("recalcula|recalculate").containsMatchIn(p)
        return enabled.filter { id -> when(id) {
            "calculator.evaluate" -> Regex("[0-9]\\s*[+*/%()-]|calcula|calculate|aritm|cuánto es|cuanto es").containsMatchIn(p)
            "files.list" -> documents || Regex("archivo|file|documento|document|hoja|sheet|columna|column").containsMatchIn(p)
            "files.read" -> documents || Regex("archivo|file|documento|document|hoja|sheet|columna|column").containsMatchIn(p)
            "python.execute" -> Regex("python|desviación|desviacion|standard deviation|estadíst|statistics|promedio|average").containsMatchIn(p)
            "artifacts.create","artifacts.revise" -> !codePatchIntent && !xlsxCellEditIntent && Regex("crea|genera|redacta|modifica|edita|actualiza|create|generate|write|edit|update").containsMatchIn(p) && Regex("word|docx|excel|xlsx|planilla|presupuesto|pdf|pptx|powerpoint|presentaci|archivo|file|código|codigo|code").containsMatchIn(p)
            "artifacts.xlsxSetCells" -> xlsxCellEditIntent
            "artifacts.patchPreview" -> codePatchIntent && Regex("corrige|modifica|edita|actualiza|fix|edit|prepara|prepare|aplica|apply").containsMatchIn(p)
            "artifacts.list","artifacts.read" -> Regex("artefact|artifact|versi[oó]n|version|borrador|draft|modifica|edita|actualiza|edit|update").containsMatchIn(p)
            else -> false
        } }.toSet()
    }
}
