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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ArtifactPatchRecoveryTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

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
