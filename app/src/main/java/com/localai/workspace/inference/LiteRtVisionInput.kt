package com.localai.workspace.inference

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import java.io.File

/** Never convert a failed image request into a text-only turn. No paths are logged. */
internal object LiteRtVisionInput {
    fun validate(filesDir: File, path: String): File {
        val root = File(filesDir, "attachments").canonicalFile
        val file = File(path).canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile && file.canRead() && file.length() > 0) {
            "Prepared image is unavailable in private attachment storage"
        }
        return file
    }
    fun contents(filesDir: File, text: String, imagePath: String?, visionInitialized: Boolean, audioPath: String? = null, audioInitialized: Boolean = false): Contents {
        if (audioPath != null) {
            require(imagePath == null) { "Only one media attachment per request" }
            check(audioInitialized) { "Audio backend not initialized; no text-only fallback" }
            val (file, _) = com.localai.workspace.audio.WavInput.validate(filesDir, audioPath)
            return Contents.of(Content.AudioFile(file.path), Content.Text(text))
        }
        if (imagePath == null) return Contents.of(Content.Text(text))
        check(visionInitialized) { "Image input requires an initialized vision backend; no text-only fallback" }
        val image = validate(filesDir, imagePath)
        return Contents.of(Content.ImageFile(image.path), Content.Text(text))
    }
}
