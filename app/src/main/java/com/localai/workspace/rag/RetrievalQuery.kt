package com.localai.workspace.rag

import java.text.Normalizer

/** Lexical policy for the shipped hash embedding, not a semantic-model claim. */
object RetrievalQuery {
    fun normalize(text: String) = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    fun greeting(text: String) = normalize(text).trim().trim('!', '?', '.', '¡', '¿')
        .matches(Regex("(hola|hi|hello|hey|gracias|thanks|buenos dias|buenas tardes|buenas noches|como estas)([ !?.]*)"))
    private val ignored = setOf("el", "la", "los", "las", "de", "del", "en", "con", "por", "para", "que", "cual", "cuales", "como", "una", "uno", "un", "me", "mi", "mis", "es", "son", "se", "si", "no", "puedes", "puede", "quiero", "sobre", "the", "of", "and", "this", "that", "what", "is", "my", "can", "you", "file", "document", "archivo", "archivos", "documento", "documentos", "adjunto", "adjuntos", "resume", "resumen", "analiza", "leer", "lee", "contiene", "dice")
    fun terms(text: String) = Regex("[\\p{L}\\p{N}]{2,}").findAll(normalize(text)).map { it.value }
        .filter { it !in ignored }.distinct().take(24).toList()
    fun refersToDocument(text: String) = Regex("\\b(archivo[s]?|documento[s]?|adjunto[s]?|pdf|informe|file[s]?|document[s]?|resume|resumen|summari[sz]e)\\b")
        .containsMatchIn(normalize(text))
}
