package com.localai.workspace.validation

import android.app.Application
import com.localai.workspace.audio.WavInput
import com.localai.workspace.data.*
import com.localai.workspace.documents.StructuredDocuments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class ValidationIsolationTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun temporaryOverridesNeverChangeNormalPreferences() {
        val original=AssistantSettings(context);original.update("project",AssistantProfile(ThinkingMode.OFF,emptySet()))
        val scoped=ValidationContext(context);val override=AssistantSettings(scoped);override.update("project",AssistantProfile(ThinkingMode.ON,setOf("python.execute")))
        assertEquals(ThinkingMode.OFF,original.forProject("project").thinking);assertTrue(original.forProject("project").tools.isEmpty())
        assertEquals(ThinkingMode.ON,override.forProject("project").thinking);assertSame(scoped,scoped.applicationContext)
    }
    @Test fun memoryPreferenceSetsAreDefensiveCopies() { val p=MemoryPreferences();val set=mutableSetOf("a");p.edit().putStringSet("s",set).commit();set.add("b");p.getStringSet("s",null)!!.add("c");assertEquals(setOf("a"),p.getStringSet("s",null)) }
    @Test fun parserCacheIsScopedWithoutChangingNormalCache() {
        val id=UUID.randomUUID().toString();val directory=File(ValidationResources.root(context,id),"cache")
        val scoped=ValidationContext(context,directory);assertEquals(directory,scoped.cacheDir);assertNotEquals(context.cacheDir,scoped.cacheDir)
        ValidationResources.clean(context,id);assertFalse(directory.exists())
    }
    @Test fun cleanupDeletesOnlyScopedFixturesAndNeverTraversesSymlinks() {
        val id=UUID.randomUUID().toString();val root=ValidationResources.root(context,id).apply { mkdirs() };File(root,"fixture").writeText("synthetic")
        val retained=File(context.filesDir,"user-retained.txt").apply { writeText("retained") }
        java.nio.file.Files.createSymbolicLink(File(root,"link").toPath(),retained.toPath())
        ValidationResources.clean(context,id);assertFalse(root.exists());assertEquals("retained",retained.readText());retained.delete()
    }
    @Test fun cleanupRejectsNonScopedPaths() { assertThrows(IllegalArgumentException::class.java) { ValidationResources.clean(context,"../documents") } }
    @Test fun recoveryRetainsNormalFilesAndDeletesOrphanedValidationMedia() {
        val id=UUID.randomUUID().toString();val orphan=File(context.filesDir,"attachments/self-test-"+id).apply { mkdirs() };File(orphan,"fixture.jpg").writeText("synthetic")
        val kept=File(context.filesDir,"attachments/user-image.jpg").apply { writeText("retained") };ValidationResources.recover(context)
        assertFalse(orphan.exists());assertTrue(kept.exists());kept.delete()
    }
    @Test fun fixturesExerciseActualParsersAndAudioContract() = runBlocking {
        val id=UUID.randomUUID().toString();val f=ValidationFixtures(ValidationResources.root(context,id));f.create(context)
        try {
            val xlsx=StructuredDocuments.xlsx(f.file("validation.xlsx"));assertEquals("12",xlsx.cells.single { it.address=="B2" }.value);assertTrue(xlsx.cells.all { it.sheet=="Sales" })
            val csv=StructuredDocuments.csv(f.file("validation.csv"));assertEquals("a;b",csv.cells.single { it.address=="A2" }.value);assertEquals("4",csv.cells.single { it.address=="B3" }.value)
            val parsed=DocumentParser(context).parse(f.file("validation.zip"),"application/zip","validation.zip");assertTrue(parsed.pages.any { "12345" in it.text })
            val error=runCatching { DocumentParser(context).parse(f.file("traversal.zip"),"application/zip","traversal.zip") }.exceptionOrNull();assertTrue(error is IllegalArgumentException);assertFalse(File(f.root.parentFile,"escape.txt").exists())
            val audio=WavInput.inspect(f.file("audio_fixture.wav"));assertTrue(audio.durationMs in 2000..5000)
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true };android.graphics.BitmapFactory.decodeFile(f.file("vision_fixture.png").path,bounds);assertEquals(640,bounds.outWidth);assertEquals(480,bounds.outHeight)
        } finally { ValidationResources.clean(context,id) }
    }
    @Test fun validationRoomCannotWriteIntoNormalWorkspace() = runBlocking {
        val normal=WorkspaceDatabase.create(context);val isolated=WorkspaceDatabase.createValidation(context)
        try {
            val id="self-test-"+UUID.randomUUID();isolated.projectDao().upsert(ProjectEntity(id,"synthetic",1,1))
            assertNotNull(isolated.projectDao().get(id));assertNull(normal.projectDao().get(id))
        } finally { isolated.close();normal.close();ValidationResources.deleteDatabase(context) }
        assertFalse(context.getDatabasePath(WorkspaceDatabase.VALIDATION_DATABASE).exists())
        assertTrue(context.getDatabasePath("local_ai_workspace.db").exists())
    }
}
