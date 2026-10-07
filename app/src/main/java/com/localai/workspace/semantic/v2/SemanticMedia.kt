package com.localai.workspace.semantic.v2

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.graphics.Bitmap
import com.localai.workspace.data.ImagePreprocessor
import com.localai.workspace.audio.AudioPreprocessor
import com.localai.workspace.audio.WavInput
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Explicit app-composed representative-frame method; never reported as native video. */
class SemanticMedia(private val context: Context) {
    data class Prepared(val reference: File, val segments: List<Pair<SemanticInput, Pair<Long?, Long?>>> , val method: String)
    suspend fun image(uri: Uri): Prepared { val f=ImagePreprocessor(context).prepare(uri);return Prepared(f,listOf(SemanticInput.Image(f.path) to (null to null)),"DIRECT_IMAGE") }
    suspend fun audio(uri: Uri): Prepared { val f=AudioPreprocessor(context).prepare(uri);val info=WavInput.inspect(f);return Prepared(f,listOf(SemanticInput.Audio(f.path,0,info.durationMs) to (0L to info.durationMs)),"DIRECT_AUDIO") }
    suspend fun video(uri: Uri): Prepared = withContext(Dispatchers.IO) {
        val root=File(context.filesDir,"semantic-media-v2/${UUID.randomUUID()}").apply { mkdirs() }
        val private=File(root,"source.video")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> private.outputStream().use { output ->
                val buffer=ByteArray(65536);var total=0L
                while(true) { ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=100*1024*1024) { "Video exceeds 100 MiB" };output.write(buffer,0,n) }
            } } ?: error("Video unavailable")
            val frames=MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(private.path)
                val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Video duration unavailable")
                require(duration in 1..60_000) { "Video limited to 60 seconds" }
                (0 until duration step 5000).map { start ->
                    ensureActive();val end=minOf(start+5000,duration)
                    val bitmap=retriever.getScaledFrameAtTime((start+end)*500,MediaMetadataRetriever.OPTION_CLOSEST,448,448) ?: error("Video frame unavailable")
                    val frame=File(root,"frame-$start.jpg")
                    try { frame.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)) } } finally { bitmap.recycle() }
                    (SemanticInput.Image(frame.path) as SemanticInput) to (start as Long? to end as Long?)
                }
            }
            Prepared(private,frames,"FRAME_SEQUENCE_REPRESENTATIVE_IMAGE")
        } catch(error:Throwable) { root.deleteRecursively();throw error }
    }
}
