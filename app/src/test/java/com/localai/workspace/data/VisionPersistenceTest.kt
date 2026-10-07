package com.localai.workspace.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisionPersistenceTest {
    @Test fun persistedReferenceSurvivesDatabaseReopeningAndDeletionCleansOnlyPrivateAttachment() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "vision-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).build()
        val file = File(context.filesDir, "attachments/${UUID.randomUUID()}.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        try {
            val repo = WorkspaceRepository(db, File(context.filesDir, "documents"))
            val project = repo.createChat()
            val conversation = db.conversationDao().getForProject(project)!!
            val row = MessageEntity("image", conversation.id, "USER", "Describe", 1, imagePath = file.path)
            db.messageDao().insert(row)
            db.close(); db = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).build()
            assertEquals(row, db.messageDao().recent(conversation.id, 10).single())
            assertTrue(file.isFile)
            WorkspaceRepository(db, File(context.filesDir, "documents")).deleteWorkspace(project)
            assertFalse(file.exists())
        } finally { db.close(); context.deleteDatabase(name); file.delete() }
    }
    @Test fun audioPersistsAndDeletionCleansItsPrivateFile() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); val name = "audio-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).build()
        val file = File(context.filesDir, "audio-attachments/${UUID.randomUUID()}.wav").apply { parentFile!!.mkdirs(); writeBytes(com.localai.workspace.audio.WavInput.header(16000, 1, 32000) + ByteArray(32000)) }
        try {
            val repo = WorkspaceRepository(db, File(context.filesDir, "documents")); val project = repo.createChat()
            val conversation = db.conversationDao().getForProject(project)!!
            val row = MessageEntity("audio", conversation.id, "USER", "Transcribe", 1, audioPath = file.path)
            db.messageDao().insert(row); db.close(); db = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).build()
            assertEquals(row, db.messageDao().recent(conversation.id, 10).single())
            WorkspaceRepository(db, File(context.filesDir, "documents")).deleteWorkspace(project); assertFalse(file.exists())
        } finally { db.close(); context.deleteDatabase(name); file.delete() }
    }
    @Test fun cameraExifOrientationIsBakedIntoPreparedJpeg() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "exif-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        ExifInterface(source.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        var prepared: File? = null
        try {
            prepared = ImagePreprocessor(context).prepare(Uri.fromFile(source))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(prepared.path, bounds)
            assertEquals(24, bounds.outWidth); assertEquals(32, bounds.outHeight)
        } finally { source.delete(); prepared?.delete() }
    }
}
