package com.localai.workspace.artifacts

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ArtifactPatchRecoveryTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

    @Test fun listingCleansOnlyAbandonedRevisionStagingDirectoriesAndKeepsCommittedBytes() {
        val root=File(context.cacheDir,"artifact-stage-recovery-${UUID.randomUUID()}")
        val project="stage-recovery-${UUID.randomUUID()}"
        val body="fun answer() = 42\n"
        try {
            val store=ArtifactStore(context,root)
            val revision=store.create(project,ArtifactSpec.parse(JSONObject()
                .put("format","CODE").put("name","answer.kt").put("body",body)))
            val owner=File(root,"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}")
            val item=File(owner,revision.id)
            val abandoned=File(item,".pending-${UUID.randomUUID()}").apply { check(mkdir()) }
            File(abandoned,"answer.kt").writeText("partial bytes")
            val unrelated=File(item,"scratch.tmp").apply { writeText("leave unrelated entries alone") }

            assertEquals(listOf(revision),store.list(project))
            assertFalse(abandoned.exists())
            assertTrue(unrelated.isFile)
            assertEquals(1,store.history(project,revision.id).size)
            assertEquals(body,store.verifiedFile(project,revision).readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun listingLeavesStagingAloneWhileAnotherWriterHoldsProjectLock() {
        val root=File(context.cacheDir,"artifact-live-stage-${UUID.randomUUID()}")
        val project="live-stage-${UUID.randomUUID()}"
        try {
            val store=ArtifactStore(context,root)
            val revision=store.create(project,ArtifactSpec.parse(JSONObject()
                .put("format","CODE").put("name","answer.kt").put("body","fun answer() = 42\n")))
            val owner=File(root,"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}")
            val pending=File(File(owner,revision.id),".pending-${UUID.randomUUID()}").apply { check(mkdir()) }

            RandomAccessFile(File(owner,".write.lock"),"rw").channel.use { channel->
                channel.lock().use {
                    assertEquals(listOf(revision),store.list(project))
                    assertTrue(pending.isDirectory)
                }
            }
            assertEquals(listOf(revision),store.list(project))
            assertFalse(pending.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun listingNeverFollowsAbandonedStagingSymlinksOutsideArtifactItem() {
        val root=File(context.cacheDir,"artifact-stage-symlink-${UUID.randomUUID()}")
        val external=File(context.cacheDir,"artifact-stage-external-${UUID.randomUUID()}").apply { check(mkdir()) }
        val externalFile=File(external,"keep.txt").apply { writeText("outside staging must survive") }
        val project="stage-symlink-${UUID.randomUUID()}"
        var nestedLink:File?=null
        var pendingLink:File?=null
        try {
            val store=ArtifactStore(context,root)
            val body="fun answer() = 42\n"
            val revision=store.create(project,ArtifactSpec.parse(JSONObject()
                .put("format","CODE").put("name","answer.kt").put("body",body)))
            val owner=File(root,"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}")
            val item=File(owner,revision.id)

            val abandoned=File(item,".pending-${UUID.randomUUID()}").apply { check(mkdir()) }
            nestedLink=File(abandoned,"external-link").apply {
                Files.createSymbolicLink(toPath(),externalFile.toPath())
            }
            pendingLink=File(item,".pending-${UUID.randomUUID()}").apply {
                Files.createSymbolicLink(toPath(),external.toPath())
            }

            assertEquals(listOf(revision),store.list(project))
            assertFalse("eligible staging directory and its symlink are removed",abandoned.exists())
            assertTrue("a staging symlink whose canonical target escapes the artifact item is left alone",Files.isSymbolicLink(pendingLink!!.toPath()))
            assertTrue("the symlink target is never traversed or removed",externalFile.isFile)
            assertEquals("outside staging must survive",externalFile.readText())
            assertEquals("committed revision bytes remain unchanged",body,store.verifiedFile(project,revision).readText())
        } finally {
            nestedLink?.let { Files.deleteIfExists(it.toPath()) }
            pendingLink?.let { Files.deleteIfExists(it.toPath()) }
            root.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test fun applyingAfterCommittedRevisionAndProcessRestartCleansPendingPreviewIdempotently() {
        val root=File(context.cacheDir,"artifact-recovery-${UUID.randomUUID()}")
        val project="recovery-${UUID.randomUUID()}"
        val before="fun answer() = 41\n"
        val after="fun answer() = 42\n"
        try {
            val originalStore=ArtifactStore(context,root)
            val first=originalStore.create(project,ArtifactSpec.parse(JSONObject()
                .put("format","CODE").put("name","answer.kt").put("body",before)))
            val preview=originalStore.stageCodePatch(project,first.id,first.version,first.sha256,
                listOf(CodePatchEdit(before.trimEnd(),after.trimEnd())))

            // Model a process interruption after the immutable revision is committed but
            // before applyCodePatchPreview removes its persisted preview record.
            val committedSpec=originalStore.spec(project,first).json().put("body",preview.after)
            val committed=originalStore.revise(project,first.id,first.version,ArtifactSpec.parse(committedSpec))
            assertEquals(preview.proposedSha256,committed.sha256)
            assertEquals(1,originalStore.codePatchPreviews(project).size)

            val reopenedStore=ArtifactStore(context,root)
            val recovered=reopenedStore.applyCodePatchPreview(project,preview.id)
            assertEquals(committed,recovered)
            assertEquals(emptyList<CodePatchPreview>(),reopenedStore.codePatchPreviews(project))
            assertEquals(2,reopenedStore.history(project,first.id).size)
            assertEquals(before,reopenedStore.verifiedFile(project,first).readText())
            assertEquals(after,reopenedStore.verifiedFile(project,recovered).readText())
            assertTrue(reopenedStore.verifiedFile(project,recovered).isFile)
            assertFalse(reopenedStore.codePatchPreviews("other-$project").any{it.id==preview.id})
        } finally {
            root.deleteRecursively()
        }
    }
}
