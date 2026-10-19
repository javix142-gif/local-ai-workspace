package com.localai.workspace.artifacts

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.data.AssistantRouting
import com.localai.workspace.domain.tools.ToolContext
import com.localai.workspace.domain.tools.ToolExecutionResult
import com.localai.workspace.domain.tools.ToolRegistry
import com.localai.workspace.security.ToolRequest
import com.localai.workspace.security.TrustOrigin
import com.localai.workspace.documents.StructuredDocuments
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class XlsxArtifactRevisionTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

    @Test fun explicitSpreadsheetCellChangeRoutesToBoundedCellEditTool() {
        val enabled=setOf("artifacts.xlsxSetCells")
        assertEquals(enabled,AssistantRouting.tools(enabled,"Actualiza la celda A2 del Excel",documents=false))
    }

    @Test fun formulaEditIntentRoutesToBoundedXlsxEditTool() {
        val enabled=setOf("artifacts.xlsxSetCells")
        assertEquals(enabled,AssistantRouting.tools(enabled,"Cambia la fórmula de A3 del Excel para sumar A1 y A2",documents=false))
    }

    @Test fun formulaCellEditCreatesVersionWithoutStaleCacheAndRequestsReaderRecalculation()=runBlocking {
        val project="xlsx-formula-edit-${UUID.randomUUID()}"
        val store=ArtifactStore(context)
        val fixture=JSONObject().put("format","XLSX").put("name","budget.xlsx").put("sheets",JSONArray()
            .put(JSONObject().put("name","Hoja1").put("cells",JSONArray()
                .put(JSONObject().put("address","A1").put("value","1").put("type","number"))
                .put(JSONObject().put("address","A2").put("value","5").put("type","number"))
                .put(JSONObject().put("address","A3").put("formula","$A$2-$A$1").put("value","4").put("type","number"))))
        try {
            val first=store.create(project,ArtifactSpec.parse(fixture))
            val originalHash=first.sha256
            val tool=ArtifactTool(context,"artifacts.xlsxSetCells")
            val registry=ToolRegistry(listOf(tool))
            val toolContext=ToolContext(project,"chat",File(context.cacheDir,"xlsx-formula-edit-work"))
            val args=JSONObject().put("id",first.id).put("expectedVersion",first.version)
                .put("edits",JSONArray().put(JSONObject().put("sheet","Hoja1").put("address","A3").put("formula","SUM(A1:A2)")))
            val request=ToolRequest("artifacts.xlsxSetCells",args.toString(),TrustOrigin.MODEL)
            val result=registry.execute(request,ArtifactTool.IDS,toolContext,ToolRegistry.ToolLoopState())
            assertTrue("A formula edit should create a verified immutable revision",result is ToolExecutionResult.Success)
            val response=JSONObject((result as ToolExecutionResult.Success).output)
            assertTrue(response.getString("note").contains("not recalculated"))

            val versions=store.history(project,first.id)
            assertEquals(2,versions.size)
            val latest=versions.maxBy{it.version}
            assertEquals(originalHash,ArtifactStore.fileHash(store.verifiedFile(project,first)))
            val originalCell=StructuredDocuments.xlsx(store.verifiedFile(project,first)).cells.single{it.sheet=="Hoja1"&&it.address=="A3"}
            assertEquals("\$A\$2-\$A\$1",originalCell.formula)
            assertEquals("4",originalCell.value)

            val latestSpec=store.spec(project,latest).json()
            val formulaCell=latestSpec.getJSONArray("sheets").getJSONObject(0).getJSONArray("cells").getJSONObject(2)
            assertEquals("SUM(A1:A2)",formulaCell.getString("formula"))
            assertFalse("Do not keep the previous formula's cached result",formulaCell.has("value"))
            assertTrue(store.spec(project,latest).preview().contains("cached result unavailable"))
            assertTrue(store.spec(project,latest).preview().contains("formula: SUM(A1:A2); not recalculated"))

            ZipFile(store.verifiedFile(project,latest)).use{zip->
                val workbook=zip.getInputStream(zip.getEntry("xl/workbook.xml")).bufferedReader().use{it.readText()}
                val sheet=zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().use{it.readText()}
                assertTrue(workbook.contains("fullCalcOnLoad=\"1\""))
                val a3=Regex("<c r=\"A3\"[^>]*>.*?</c>").find(sheet)?.value
                assertNotNull("Expected the edited formula cell in worksheet XML",a3)
                assertTrue(a3!!.contains("<f>SUM(A1:A2)</f>"))
                assertFalse("No stale cached <v> value may follow a changed formula",a3.contains("<v>"))
            }

            args.getJSONObject("edits").getJSONObject(0).put("formula","WEBSERVICE(\"https://invalid.example\")")
            val rejected=registry.execute(ToolRequest("artifacts.xlsxSetCells",args.toString(),TrustOrigin.MODEL),ArtifactTool.IDS,toolContext,ToolRegistry.ToolLoopState())
            assertFalse("External/active formulas must remain rejected",rejected is ToolExecutionResult.Success)
            assertEquals("Rejected formula input must not create another version",2,store.history(project,first.id).size)
        } finally {
            File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}").deleteRecursively()
        }
    }

    @Test fun boundedCellEditToolCreatesImmutableVersionAndPreservesFormulaCacheAndScope()=runBlocking {
        val project="xlsx-cell-edit-${UUID.randomUUID()}"
        val store=ArtifactStore(context)
        val fixture=JSONObject().put("format","XLSX").put("name","budget.xlsx").put("sheets",JSONArray()
            .put(JSONObject().put("name","Hoja1").put("cells",JSONArray()
                .put(JSONObject().put("address","A1").put("value","1").put("type","number"))
                .put(JSONObject().put("address","A2").put("value","5").put("type","number"))
                .put(JSONObject().put("address","A3").put("formula","\$A\$2-\$A\$1").put("value","4").put("type","number"))))
            .put(JSONObject().put("name","Hoja2").put("cells",JSONArray()
                .put(JSONObject().put("address","B14").put("value","scope remains")))))
        try {
            val first=store.create(project,ArtifactSpec.parse(fixture))
            val originalHash=first.sha256
            val tool=ArtifactTool(context,"artifacts.xlsxSetCells")
            val registry=ToolRegistry(listOf(tool))
            val context=ToolContext(project,"chat",File(this@XlsxArtifactRevisionTest.context.cacheDir,"xlsx-cell-edit-work"))
            val edits=JSONArray()
                .put(JSONObject().put("sheet","hoja1").put("address","A2").put("value","7").put("type","number"))
                .put(JSONObject().put("sheet","Hoja2").put("address","B14").put("value","updated"))
            val args=JSONObject().put("id",first.id).put("expectedVersion",first.version).put("edits",edits).toString()
            val request=ToolRequest("artifacts.xlsxSetCells",args,TrustOrigin.MODEL)

            val readOnly=registry.execute(request,ArtifactTool.IDS,context.copy(grantedCapabilities=setOf(com.localai.workspace.capabilities.Capability.LOCAL_READ)),ToolRegistry.ToolLoopState())
            assertTrue(readOnly is ToolExecutionResult.Rejected)
            val disabled=registry.execute(request,emptySet(),context,ToolRegistry.ToolLoopState())
            assertTrue(disabled is ToolExecutionResult.Rejected)
            val otherProject=registry.execute(request,ArtifactTool.IDS,context.copy(projectId="$project-other"),ToolRegistry.ToolLoopState())
            assertTrue(otherProject is ToolExecutionResult.Failed)
            assertEquals(1,store.history(project,first.id).size)
            val result=registry.execute(request,ArtifactTool.IDS,context,ToolRegistry.ToolLoopState()) as ToolExecutionResult.Success
            val response=JSONObject(result.output)
            assertEquals("PRIVATE_DRAFT_VERIFIED",response.getString("state"))
            assertTrue(response.getString("note").contains("not recalculated"))
            assertTrue(com.localai.workspace.domain.tools.ChatToolSchemas.available.contains("artifacts.xlsxSetCells"))
            val schema=JSONObject(com.localai.workspace.domain.tools.ChatToolSchemas.schema("artifacts.xlsxSetCells"))
            assertEquals(64,schema.getJSONObject("parameters").getJSONObject("properties").getJSONObject("edits").getInt("maxItems"))

            val latest=store.history(project,first.id).maxBy{it.version}
            assertEquals(2,latest.version)
            val oldFile=store.verifiedFile(project,first)
            val newFile=store.verifiedFile(project,latest)
            assertEquals(originalHash,ArtifactStore.fileHash(oldFile))
            val table=StructuredDocuments.xlsx(newFile)
            assertEquals("7",table.cells.single{it.sheet=="Hoja1"&&it.address=="A2"}.value)
            assertEquals("updated",table.cells.single{it.sheet=="Hoja2"&&it.address=="B14"}.value)
            val formula=table.cells.single{it.sheet=="Hoja1"&&it.address=="A3"}
            assertEquals("\$A\$2-\$A\$1",formula.formula)
            assertEquals("4",formula.value)

            val formulaEdit=JSONObject().put("id",latest.id).put("expectedVersion",latest.version)
                .put("edits",JSONArray().put(JSONObject().put("sheet","Hoja1").put("address","A3").put("value","11").put("type","number"))).toString()
            val rejectedFormula=registry.execute(ToolRequest("artifacts.xlsxSetCells",formulaEdit,TrustOrigin.MODEL),ArtifactTool.IDS,context,ToolRegistry.ToolLoopState())
            assertTrue(rejectedFormula is ToolExecutionResult.Failed)
            assertTrue((rejectedFormula as ToolExecutionResult.Failed).message.contains("XLSX_FORMULA_CELL_REQUIRES_EXPLICIT_REVISION"))
            assertEquals(2,store.history(project,first.id).size)
            assertEquals(originalHash,ArtifactStore.fileHash(store.verifiedFile(project,first)))
        } finally {
            File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}").deleteRecursively()
        }
    }

    @Test fun editingDependencyRetainsFormulaAndCachedValueAcrossSavedVersionsWithoutClaimingRecalculation() {
        val root=File(context.cacheDir,"xlsx-revision-${UUID.randomUUID()}")
        val project="xlsx-revision-${UUID.randomUUID()}"
        val original=JSONObject().put("format","XLSX").put("name","budget.xlsx").put("sheets",JSONArray()
            .put(JSONObject().put("name","Hoja1").put("cells",JSONArray()
                .put(JSONObject().put("address","A1").put("value","1").put("type","number"))
                .put(JSONObject().put("address","A2").put("value","5").put("type","number"))
                .put(JSONObject().put("address","A3").put("formula","\$A\$2-\$A\$1").put("value","4").put("type","number"))))
            .put(JSONObject().put("name","Hoja2").put("cells",JSONArray()
                .put(JSONObject().put("address","B14").put("value","scope remains")))))
        try {
            val firstStore=ArtifactStore(context,root)
            val first=firstStore.create(project,ArtifactSpec.parse(original))
            val originalFile=firstStore.verifiedFile(project,first)
            val originalHash=first.sha256

            val editedSpec=firstStore.spec(project,first).json()
            editedSpec.getJSONArray("sheets").getJSONObject(0).getJSONArray("cells")
                .getJSONObject(1).put("value","7")
            val second=firstStore.revise(project,first.id,first.version,ArtifactSpec.parse(editedSpec))

            val reopened=ArtifactStore(context,root)
            val reopenedHistory=reopened.history(project,first.id)
            val reopenedFirst=reopenedHistory.single{it.version==1}
            val reopenedSecond=reopenedHistory.single{it.version==2}
            val firstTable=StructuredDocuments.xlsx(reopened.verifiedFile(project,reopenedFirst))
            val secondFile=reopened.verifiedFile(project,reopenedSecond)
            val secondTable=StructuredDocuments.xlsx(secondFile)
            val formulaCell=secondTable.cells.single{it.sheet=="Hoja1"&&it.address=="A3"}

            assertEquals(2,reopenedHistory.size)
            assertEquals(originalHash,ArtifactStore.fileHash(originalFile))
            assertEquals("5",firstTable.cells.single{it.sheet=="Hoja1"&&it.address=="A2"}.value)
            assertEquals("7",secondTable.cells.single{it.sheet=="Hoja1"&&it.address=="A2"}.value)
            assertEquals("\$A\$2-\$A\$1",formulaCell.formula)
            assertEquals("4",formulaCell.value)
            assertEquals("scope remains",secondTable.cells.single{it.sheet=="Hoja2"&&it.address=="B14"}.value)
            assertTrue(reopened.spec(project,reopenedSecond).preview()
                .contains("A3 = 4 [formula: \$A\$2-\$A\$1; not recalculated]"))
            ZipFile(secondFile).use{zip->
                val workbook=zip.getInputStream(zip.getEntry("xl/workbook.xml")).bufferedReader().use{it.readText()}
                assertTrue(workbook.contains("fullCalcOnLoad=\"1\""))
                val sheet=zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().use{it.readText()}
                assertTrue(sheet.contains("<f>\$A\$2-\$A\$1</f><v>4</v>"))
            }
            assertEquals(second.sha256,ArtifactStore.fileHash(secondFile))
        } finally {
            root.deleteRecursively()
        }
    }
}
