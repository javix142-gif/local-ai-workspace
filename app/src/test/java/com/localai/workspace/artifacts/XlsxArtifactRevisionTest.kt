package com.localai.workspace.artifacts

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.data.AssistantRouting
import com.localai.workspace.domain.tools.ToolContext
import com.localai.workspace.domain.tools.ToolExecutionResult
import com.localai.workspace.domain.tools.ToolRegistry
import com.localai.workspace.domain.tools.ChatToolSchemas
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

    private fun tableDescriptor(name:String="TablaVentas",range:String="A1:B2",headerA:String="Producto",headerB:String="Cantidad")=
        JSONObject().put("format","XLSX").put("name","sales.xlsx").put("sheets",JSONArray()
            .put(JSONObject().put("name","Ventas").put("cells",JSONArray()
                .put(JSONObject().put("address","A1").put("value",headerA))
                .put(JSONObject().put("address","B1").put("value",headerB))
                .put(JSONObject().put("address","A2").put("value","Cuaderno"))
                .put(JSONObject().put("address","B2").put("value","3").put("type","number")))
                .put("tables",JSONArray().put(JSONObject().put("name",name).put("range",range)))))

    @Test fun xlsxStructuredTableSpecRendersNativeOoxmlPartsAndReadPreview()=runBlocking {
        val parsed=runCatching { ArtifactSpec.parse(tableDescriptor(headerA="Producto & código")) }
        assertTrue("XLSX generation must accept a bounded structured table definition",parsed.isSuccess)
        val spec=parsed.getOrThrow()
        assertTrue(spec.preview().contains("Table TablaVentas · A1:B2"))
        val root=File(context.cacheDir,"xlsx-table-package-${UUID.randomUUID()}")
        val project="xlsx-table-package-${UUID.randomUUID()}"
        try {
            val store=ArtifactStore(context,root)
            val revision=store.create(project,spec)
            val file=store.verifiedFile(project,revision)
            ZipFile(file).use{zip->
                val contentTypes=zip.getInputStream(zip.getEntry("[Content_Types].xml")).bufferedReader().use{it.readText()}
                val worksheet=zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().use{it.readText()}
                val relationships=zip.getInputStream(zip.getEntry("xl/worksheets/_rels/sheet1.xml.rels")).bufferedReader().use{it.readText()}
                val table=zip.getInputStream(zip.getEntry("xl/tables/table1.xml")).bufferedReader().use{it.readText()}
                assertTrue(contentTypes.contains("/xl/tables/table1.xml"))
                assertTrue(contentTypes.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml"))
                assertTrue(worksheet.contains("<tableParts count=\"1\"><tablePart r:id=\"rIdTable1\"/></tableParts>"))
                assertTrue(relationships.contains("Target=\"../tables/table1.xml\""))
                assertTrue(relationships.contains("/relationships/table\""))
                assertTrue(table.contains("name=\"TablaVentas\" displayName=\"TablaVentas\" ref=\"A1:B2\""))
                assertTrue(table.contains("name=\"Producto &amp; código\""))
                assertTrue(table.contains("name=\"Cantidad\""))
            }
            val reopened=ArtifactStore(context,root).spec(project,ArtifactStore(context,root).history(project,revision.id).single())
            assertTrue(reopened.preview().contains("Table TablaVentas · A1:B2"))
            exportInteropFixture("structured-table.xlsx",file)
        } finally { root.deleteRecursively() }
    }

    @Test fun xlsxStructuredTablesRejectMissingDuplicateAndOverlappingHeaders() {
        val duplicate=runCatching { ArtifactSpec.parse(tableDescriptor(headerA="Producto",headerB="producto")) }.exceptionOrNull()
        assertEquals("XLSX_TABLE_HEADER_DUPLICATE",duplicate?.message)
        val missingHeader=tableDescriptor().also{it.getJSONArray("sheets").getJSONObject(0).getJSONArray("cells").remove(0)}
        assertEquals("XLSX_TABLE_HEADER_CELL_MISSING",runCatching{ArtifactSpec.parse(missingHeader)}.exceptionOrNull()?.message)
        val overlap=tableDescriptor().also{sheet->
            sheet.getJSONArray("sheets").getJSONObject(0).getJSONArray("tables").put(JSONObject().put("name","OtraTabla").put("range","A1:B2"))
        }
        assertEquals("XLSX_TABLE_RANGE_OVERLAP",runCatching{ArtifactSpec.parse(overlap)}.exceptionOrNull()?.message)
        assertTrue(runCatching{ArtifactSpec.cellRange("A1:ZZ100")}.isFailure)
        assertTrue(runCatching{ArtifactSpec.tableName("A1")}.isFailure)
        assertTrue(runCatching{ArtifactSpec.tableName("R1C1")}.isFailure)
    }

    @Test fun setTableToolAddsAndResizesImmutableWorkbookTable()=runBlocking {
        val project="xlsx-table-edit-${UUID.randomUUID()}"
        val spec=ArtifactSpec.parse(JSONObject().put("format","XLSX").put("name","sales.xlsx").put("sheets",JSONArray()
            .put(JSONObject().put("name","Ventas").put("cells",JSONArray()
                .put(JSONObject().put("address","A1").put("value","Producto"))
                .put(JSONObject().put("address","B1").put("value","Cantidad"))
                .put(JSONObject().put("address","C1").put("value","Importe"))
                .put(JSONObject().put("address","A2").put("value","Cuaderno"))
                .put(JSONObject().put("address","B2").put("formula","1+2").put("value","3").put("type","number"))
                .put(JSONObject().put("address","C2").put("value","9").put("type","number"))))))
        val toolId="artifacts.xlsxSetTable"
        try {
            val store=ArtifactStore(context)
            val original=store.create(project,spec)
            val originalHash=original.sha256
            val enabled=setOf(toolId)
            val routeTools=enabled+setOf("artifacts.list","artifacts.read")
            assertEquals(routeTools,AssistantRouting.tools(routeTools,"Agrega la tabla Ventas al libro Excel en A1:B2",documents=false))
            assertEquals(setOf(com.localai.workspace.capabilities.Capability.LOCAL_WRITE),
                com.localai.workspace.capabilities.CapabilityPolicy.requirements(toolId))
            assertTrue(com.localai.workspace.domain.tools.ChatToolSchemas.available.contains(toolId))
            val schema=JSONObject(com.localai.workspace.domain.tools.ChatToolSchemas.schema(toolId))
            assertEquals(setOf("id","expectedVersion","sheet","name","range"),schema.getJSONObject("parameters").getJSONArray("required").let{a->(0 until a.length()).map{a.getString(it)}.toSet()})

            val registry=ToolRegistry(listOf(ArtifactTool(context,toolId)))
            val toolContext=ToolContext(project,"chat",File(context.cacheDir,"xlsx-table-tool-work"))
            fun request(version:Int,range:String)=JSONObject().put("id",original.id).put("expectedVersion",version)
                .put("sheet","Ventas").put("name","TablaVentas").put("range",range).toString()
            val added=registry.execute(ToolRequest(toolId,request(1,"A1:B2"),TrustOrigin.MODEL),enabled,toolContext,ToolRegistry.ToolLoopState())
            assertTrue("A valid table should create a verified immutable workbook revision",added is ToolExecutionResult.Success)
            assertEquals(originalHash,ArtifactStore.fileHash(store.verifiedFile(project,original)))
            val second=store.history(project,original.id).single{it.version==2}
            assertTrue(store.spec(project,second).preview().contains("Table TablaVentas · A1:B2"))

            val resized=registry.execute(ToolRequest(toolId,request(2,"A1:C2"),TrustOrigin.MODEL),enabled,toolContext,ToolRegistry.ToolLoopState())
            assertTrue("Resizing a named table should create another verified immutable version",resized is ToolExecutionResult.Success)
            val latest=store.history(project,original.id).single{it.version==3}
            val zipFile=store.verifiedFile(project,latest)
            ZipFile(zipFile).use{zip->
                val table=zip.getInputStream(zip.getEntry("xl/tables/table1.xml")).bufferedReader().use{it.readText()}
                assertTrue(table.contains("ref=\"A1:C2\""))
            }
            val structured=StructuredDocuments.xlsx(zipFile)
            assertEquals("9",structured.cells.single{it.sheet=="Ventas"&&it.address=="C2"}.value)
            assertEquals("1+2",structured.cells.single{it.sheet=="Ventas"&&it.address=="B2"}.formula)
            assertEquals("3",structured.cells.single{it.sheet=="Ventas"&&it.address=="B2"}.value)
            assertEquals(3,store.history(project,original.id).size)
            assertEquals(originalHash,ArtifactStore.fileHash(store.verifiedFile(project,original)))
            exportInteropFixture("table-resized.xlsx",zipFile)

            val invalid=registry.execute(ToolRequest(toolId,request(3,"A1:D2"),TrustOrigin.MODEL),enabled,toolContext,ToolRegistry.ToolLoopState())
            assertTrue(invalid is ToolExecutionResult.Failed)
            assertEquals(3,store.history(project,original.id).size)
        } finally {
            File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}").deleteRecursively()
        }
    }

    @Test fun formulaCellEditPreservesNativeTableAndPriorWorkbookRevision()=runBlocking {
        val project="xlsx-table-cell-edit-${UUID.randomUUID()}"
        val store=ArtifactStore(context)
        try {
            val original=store.create(project,ArtifactSpec.parse(tableDescriptor()))
            val originalHash=original.sha256
            val toolId="artifacts.xlsxSetCells"
            val registry=ToolRegistry(listOf(ArtifactTool(context,toolId)))
            val toolContext=ToolContext(project,"chat",File(context.cacheDir,"xlsx-table-cell-edit-work"))
            val args=JSONObject().put("id",original.id).put("expectedVersion",original.version)
                .put("edits",JSONArray().put(JSONObject().put("sheet","Ventas").put("address","B2").put("formula","SUM(A2:A2)")))
            val result=registry.execute(ToolRequest(toolId,args.toString(),TrustOrigin.MODEL),setOf(toolId),toolContext,ToolRegistry.ToolLoopState())
            assertTrue("Editing a table body formula should create a verified immutable revision",result is ToolExecutionResult.Success)

            val latest=store.history(project,original.id).single{it.version==2}
            assertEquals(2,store.history(project,original.id).size)
            assertEquals(originalHash,ArtifactStore.fileHash(store.verifiedFile(project,original)))
            val originalFile=store.verifiedFile(project,original)
            val editedFile=store.verifiedFile(project,latest)
            ZipFile(originalFile).use{zip->assertNotNull("Original table part must remain available",zip.getEntry("xl/tables/table1.xml"))}
            ZipFile(editedFile).use{zip->
                val worksheet=zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().use{it.readText()}
                val relationships=zip.getInputStream(zip.getEntry("xl/worksheets/_rels/sheet1.xml.rels")).bufferedReader().use{it.readText()}
                val table=zip.getInputStream(zip.getEntry("xl/tables/table1.xml")).bufferedReader().use{it.readText()}
                val editedCell=requireNotNull(Regex("""<c\b[^>]*\br="B2"[^>]*>(.*?)</c>""").find(worksheet)?.groupValues?.get(1)) {
                    "Edited formula cell must remain in worksheet XML"
                }
                assertTrue("Formula remains serialized",editedCell.contains("<f>SUM(A2:A2)</f>"))
                assertFalse("Edited formula must not keep a stale cached result",editedCell.contains("<v>"))
                assertTrue(worksheet.contains("<tableParts count=\"1\"><tablePart r:id=\"rIdTable1\"/></tableParts>"))
                assertTrue(relationships.contains("Target=\"../tables/table1.xml\""))
                assertTrue(table.contains("name=\"TablaVentas\" displayName=\"TablaVentas\" ref=\"A1:B2\""))
                assertTrue(table.contains("name=\"Producto\""))
                assertTrue(table.contains("name=\"Cantidad\""))
            }
            val edited=StructuredDocuments.xlsx(editedFile).cells.single{it.sheet=="Ventas"&&it.address=="B2"}
            assertEquals("SUM(A2:A2)",edited.formula)
            assertEquals("The current structured reader represents an absent formula cache as an empty string","",edited.value)
            exportInteropFixture("table-cell-edited.xlsx",editedFile)
        } finally {
            File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}").deleteRecursively()
        }
    }

    private fun exportInteropFixture(name:String,file:File) {
        val configured=System.getenv("LOCALAI_XLSX_INTEROP_DIR")?.takeIf{it.isNotBlank()}?:return
        val directory=File(configured).canonicalFile
        check(directory.isDirectory||directory.mkdirs())
        val destination=File(directory,name).canonicalFile
        check(destination.parentFile==directory)
        file.copyTo(destination,overwrite=true)
    }

    @Test fun addingWorksheetRoutesAndPreservesExistingGeneratedWorkbookAsNewRevision()=runBlocking {
        val toolId="artifacts.xlsxAddSheet"
        val enabled=setOf(toolId)
        assertEquals(enabled,AssistantRouting.tools(enabled,"Agrega una hoja Resumen al libro Excel",documents=false))
        val project="xlsx-add-sheet-${UUID.randomUUID()}"
        val store=ArtifactStore(context)
        val fixture=JSONObject().put("format","XLSX").put("name","budget.xlsx").put("sheets",JSONArray()
            .put(JSONObject().put("name","Hoja1").put("cells",JSONArray()
                .put(JSONObject().put("address","A1").put("value","1").put("type","number"))
                .put(JSONObject().put("address","A2").put("value","5").put("type","number"))
                .put(JSONObject().put("address","A3").put("formula","\$A\$2-\$A\$1").put("value","4").put("type","number"))))
            .put(JSONObject().put("name","Hoja2").put("cells",JSONArray()
                .put(JSONObject().put("address","B14").put("value","preserve")))))
        try {
            val original=store.create(project,ArtifactSpec.parse(fixture))
            val originalHash=original.sha256
            val args=JSONObject().put("id",original.id).put("expectedVersion",original.version).put("name","Resumen")
            val registry=ToolRegistry(listOf(ArtifactTool(context,toolId)))
            val toolContext=ToolContext(project,"chat",File(context.cacheDir,"xlsx-add-sheet-work"))
            val result=registry.execute(ToolRequest(toolId,args.toString(),TrustOrigin.MODEL),setOf(toolId),toolContext,ToolRegistry.ToolLoopState())
            assertTrue("Adding a worksheet should return a verified new workbook revision",result is ToolExecutionResult.Success)
            val response=JSONObject((result as ToolExecutionResult.Success).output)
            assertEquals("PRIVATE_DRAFT_VERIFIED",response.getString("state"))
            assertTrue(ChatToolSchemas.available.contains(toolId))
            assertEquals(setOf(com.localai.workspace.capabilities.Capability.LOCAL_WRITE),
                com.localai.workspace.capabilities.CapabilityPolicy.requirements(toolId))

            val history=store.history(project,original.id)
            assertEquals(2,history.size)
            assertEquals(originalHash,ArtifactStore.fileHash(store.verifiedFile(project,original)))
            val latest=history.single{it.version==2}
            val updated=store.spec(project,latest).json().getJSONArray("sheets")
            assertEquals(3,updated.length())
            assertEquals("Hoja1",updated.getJSONObject(0).getString("name"))
            assertEquals("Hoja2",updated.getJSONObject(1).getString("name"))
            assertEquals("Resumen",updated.getJSONObject(2).getString("name"))
            assertEquals(3,updated.getJSONObject(0).getJSONArray("cells").length())
            assertEquals("A3",updated.getJSONObject(0).getJSONArray("cells").getJSONObject(2).getString("address"))
            assertEquals(0,updated.getJSONObject(2).getJSONArray("cells").length())
            val table=StructuredDocuments.xlsx(store.verifiedFile(project,latest))
            assertEquals("1",table.cells.single{it.sheet=="Hoja1"&&it.address=="A1"}.value)
            assertEquals("5",table.cells.single{it.sheet=="Hoja1"&&it.address=="A2"}.value)
            assertEquals("4",table.cells.single{it.sheet=="Hoja1"&&it.address=="A3"}.value)
            assertEquals("\$A\$2-\$A\$1",table.cells.single{it.sheet=="Hoja1"&&it.address=="A3"}.formula)
            assertEquals("preserve",table.cells.single{it.sheet=="Hoja2"&&it.address=="B14"}.value)
            exportInteropFixture("sheet-added.xlsx",store.verifiedFile(project,latest))

            val duplicateArgs=JSONObject().put("id",original.id).put("expectedVersion",latest.version).put("name","hoja1")
            val duplicate=registry.execute(ToolRequest(toolId,duplicateArgs.toString(),TrustOrigin.MODEL),setOf(toolId),toolContext,ToolRegistry.ToolLoopState())
            assertTrue("Sheet names must remain case-insensitively unique",duplicate is ToolExecutionResult.Failed)
            val staleArgs=JSONObject().put("id",original.id).put("expectedVersion",original.version).put("name","Stale")
            val stale=registry.execute(ToolRequest(toolId,staleArgs.toString(),TrustOrigin.MODEL),setOf(toolId),toolContext,ToolRegistry.ToolLoopState())
            assertTrue("A stale worksheet edit must not overwrite the latest revision",stale is ToolExecutionResult.Failed)
            assertEquals(2,store.history(project,original.id).size)

            val fullWorkbook=JSONObject().put("format","XLSX").put("name","full.xlsx").put("sheets",JSONArray().apply {
                (1..16).forEach{index->put(JSONObject().put("name","Sheet$index").put("cells",JSONArray()))}
            })
            val full=store.create(project,ArtifactSpec.parse(fullWorkbook))
            val limitArgs=JSONObject().put("id",full.id).put("expectedVersion",full.version).put("name","Sheet17")
            val limit=registry.execute(ToolRequest(toolId,limitArgs.toString(),TrustOrigin.MODEL),setOf(toolId),toolContext,ToolRegistry.ToolLoopState())
            assertTrue("The workbook sheet cap must be enforced",limit is ToolExecutionResult.Failed)
            assertEquals(1,store.history(project,full.id).size)

            val schema=JSONObject(ChatToolSchemas.schema(toolId))
            val parameters=schema.getJSONObject("parameters")
            assertEquals(setOf("id","expectedVersion","name"),parameters.getJSONArray("required").let{a->(0 until a.length()).map(a::getString).toSet()})
            assertEquals(31,parameters.getJSONObject("properties").getJSONObject("name").getInt("maxLength"))
        } finally {
            File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}").deleteRecursively()
        }
    }

    @Test fun explicitSpreadsheetCellChangeRoutesToBoundedCellEditTool() {
        val enabled=setOf("artifacts.xlsxSetCells")
        assertEquals(enabled,AssistantRouting.tools(enabled,"Actualiza la celda A2 del Excel",documents=false))
    }

    @Test fun formulaEditIntentRoutesToBoundedXlsxEditTool() {
        val enabled=setOf("artifacts.xlsxSetCells")
        assertEquals(enabled,AssistantRouting.tools(enabled,"Cambia la fórmula de A3 del Excel para sumar A1 y A2",documents=false))
        assertTrue(AssistantRouting.tools(enabled,"Recalcula la fórmula de A3 del Excel",documents=false).isEmpty())
    }

    @Test fun formulaCellEditCreatesVersionWithoutStaleCacheAndRequestsReaderRecalculation()=runBlocking {
        val project="xlsx-formula-edit-${UUID.randomUUID()}"
        val store=ArtifactStore(context)
        val fixture=JSONObject().put("format","XLSX").put("name","budget.xlsx").put("sheets",JSONArray()
            .put(JSONObject().put("name","Hoja1").put("cells",JSONArray()
                .put(JSONObject().put("address","A1").put("value","1").put("type","number"))
                .put(JSONObject().put("address","A2").put("value","5").put("type","number"))
                .put(JSONObject().put("address","A3").put("formula","\$A\$2-\$A\$1").put("value","4").put("type","number")))))
        try {
            val first=store.create(project,ArtifactSpec.parse(fixture))
            val originalHash=first.sha256
            exportInteropFixture("original.xlsx",store.verifiedFile(project,first))
            val tool=ArtifactTool(context,"artifacts.xlsxSetCells")
            val registry=ToolRegistry(listOf(tool))
            val toolContext=ToolContext(project,"chat",File(context.cacheDir,"xlsx-formula-edit-work"))
            val schema=JSONObject(com.localai.workspace.domain.tools.ChatToolSchemas.schema("artifacts.xlsxSetCells"))
                .getJSONObject("parameters").getJSONObject("properties").getJSONObject("edits").getJSONObject("items")
            assertTrue(schema.getJSONObject("properties").has("formula"))
            val required=schema.getJSONArray("required").let{array->(0 until array.length()).map{array.getString(it)}.toSet()}
            assertEquals(setOf("sheet","address"),required)
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
            exportInteropFixture("formula-edited.xlsx",store.verifiedFile(project,latest))
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

            args.getJSONArray("edits").getJSONObject(0).put("formula","WEBSERVICE(\"https://invalid.example\")")
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

    @Test fun formulaCachedValuesKeepTheirDeclaredSpreadsheetResultTypes() = runBlocking {
        val project = "xlsx-formula-cache-type-${UUID.randomUUID()}"
        val store = ArtifactStore(context)
        val spec = ArtifactSpec.parse(JSONObject().put("format", "XLSX").put("name", "formula-types.xlsx")
            .put("sheets", JSONArray().put(JSONObject().put("name", "Data").put("cells", JSONArray()
                .put(JSONObject().put("address", "A1").put("formula", "1+1").put("value", "2").put("type", "number"))
                .put(JSONObject().put("address", "A2").put("formula", "\"cached text\"").put("value", "cached text").put("type", "string"))
                .put(JSONObject().put("address", "A3").put("formula", "1=1").put("value", "1").put("type", "boolean"))))))
        try {
            val revision = store.create(project, spec)
            val file = store.verifiedFile(project, revision)
            val worksheet = ZipFile(file).use { zip ->
                zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().use { it.readText() }
            }
            assertTrue("A numeric formula cache must be serialized as numeric, not text",
                Regex("<c r=\"A1\"[^>]*\\bt=\"n\"[^>]*>").containsMatchIn(worksheet))
            assertTrue("A string formula cache must keep the string result type",
                Regex("<c r=\"A2\"[^>]*\\bt=\"str\"[^>]*>").containsMatchIn(worksheet))
            assertTrue("A boolean formula cache must keep the boolean result type",
                Regex("<c r=\"A3\"[^>]*\\bt=\"b\"[^>]*>").containsMatchIn(worksheet))
            assertTrue(worksheet.contains("<f>1+1</f><v>2</v>"))
            assertTrue(worksheet.contains("<f>&quot;cached text&quot;</f><v>cached text</v>"))
            assertTrue(worksheet.contains("<f>1=1</f><v>1</v>"))
            exportInteropFixture("formula-cache-types.xlsx", file)
        } finally {
            File(File(context.filesDir, "tool-workspaces"), "artifacts-${ArtifactStore.hash(project.toByteArray(Charsets.UTF_8))}").deleteRecursively()
        }
    }
}
