package com.localai.workspace.artifacts

import android.app.Application
import com.localai.workspace.documents.StructuredDocuments
import com.localai.workspace.data.DocumentParser
import com.localai.workspace.domain.tools.*
import com.localai.workspace.security.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class ArtifactRoundtripTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun spec(format:String,content:String)=ArtifactSpec.parse(JSONObject(content).put("format",format).put("name","fixture.${if(format=="CODE")"kt"else format.lowercase()}"))
    private val xlsx="""{"sheets":[{"name":"Hoja1","cells":[{"address":"A1","value":"1","type":"number"},{"address":"A2","value":"5","type":"number"},{"address":"A3","formula":"${'$'}A${'$'}2-${'$'}A${'$'}1","value":"4","type":"number"},{"address":"B1","value":"España & <valor>","style":"header"}]},{"name":"Hoja2","cells":[{"address":"B14","value":"Exacto 界"}]}]}"""
    private val prose="""{"paragraphs":[{"text":"Informe español","style":"title"},{"text":"Primera sección","style":"heading"},{"text":"Párrafo con ñ y €.","style":"body"},{"text":"Elemento de lista","style":"bullet"},{"text":"Segunda página","style":"pageBreak"}],"tables":[{"rows":[["Nombre","Valor"],["Álvaro","42"]]}]}"""
    private fun root()=File(context.cacheDir,"artifact-${UUID.randomUUID()}")
    private fun capture(revision:ArtifactRevision,store:ArtifactStore,project:String,suffix:String="") {
        val dir=File("build/artifact-roundtrip").apply{mkdirs()}
        store.verifiedFile(project,revision).copyTo(File(dir,"v${revision.version}$suffix-${revision.name}"),overwrite=true)
        File(dir,"v${revision.version}$suffix-${revision.name}.metadata.json").writeText(revision.json().toString())
    }
    @Test fun xlsxRoundtripPreservesSheetsCellsFormulasCachedValuesAndOriginalVersion() {
        val root=root();val store=ArtifactStore(context,root)
        try {
            val first=store.create("p",spec("XLSX",xlsx));capture(first,store,"p")
            val table=StructuredDocuments.xlsx(store.verifiedFile("p",first))
            assertEquals(listOf("Hoja1","Hoja2"),table.sheetNames)
            assertEquals("Exacto 界",table.cells.single{it.sheet=="Hoja2"&&it.address=="B14"}.value)
            assertEquals("${'$'}A${'$'}2-${'$'}A${'$'}1",table.cells.single{it.address=="A3"}.formula)
            assertEquals("4",table.cells.single{it.address=="A3"}.value)
            val changed=store.spec("p",first).json();changed.getJSONArray("sheets").getJSONObject(0).getJSONArray("cells").getJSONObject(1).put("value","7")
            val second=store.revise("p",first.id,1,ArtifactSpec.parse(changed));capture(second,store,"p")
            assertEquals("7",StructuredDocuments.xlsx(store.verifiedFile("p",second)).cells.single{it.address=="A2"}.value)
            assertEquals("5",StructuredDocuments.xlsx(store.verifiedFile("p",first)).cells.single{it.address=="A2"}.value)
            assertEquals(2,store.history("p",first.id).size)
        }finally{root.deleteRecursively()}
    }
    @Test fun docxPdfPptxAndCodeCreateReopenEditAndKeepOlderBytes()=runBlocking {
        val root=root();val store=ArtifactStore(context,root)
        try {
            for(format in listOf("DOCX","PDF","PPTX","CODE")) {
                val source=when(format){"DOCX","PDF"->prose;"PPTX"->"""{"slides":[{"title":"Informe español","body":"Contenido primero\nSegunda línea"},{"title":"Resumen","body":"Álvaro: 42"}]}""";else->"""{"body":"// España 界\nfun answer() = 42\n"}"""}
                val first=store.create("p",spec(format,source));capture(first,store,"p")
                val oldHash=first.sha256
                if(format=="DOCX"||format=="PDF") {
                    val parsed=DocumentParser(context).parse(store.verifiedFile("p",first),first.format.mime,first.name)
                    assertTrue(parsed.pages.joinToString{it.text}.contains("Informe español"))
                    assertTrue(parsed.pages.joinToString{it.text}.contains("Álvaro"))
                    if(format=="PDF")assertEquals(2,parsed.pages.size)
                    if(format=="DOCX")ZipFile(store.verifiedFile("p",first)).use{z->assertTrue(z.getInputStream(z.getEntry("word/document.xml")).bufferedReader().use{it.readText()}.contains("<w:tblGrid>"))}
                }
                if(format=="PPTX")ZipFile(store.verifiedFile("p",first)).use{z->assertNotNull(z.getEntry("ppt/slides/slide2.xml"));assertNotNull(z.getEntry("ppt/slides/_rels/slide1.xml.rels"));assertNotNull(z.getEntry("ppt/slideLayouts/slideLayout1.xml"));assertNotNull(z.getEntry("ppt/slideMasters/slideMaster1.xml"));assertNotNull(z.getEntry("ppt/theme/theme1.xml"));z.entries().asSequence().filter{it.name.endsWith(".xml")}.forEach{e->z.getInputStream(e).use{DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)}}}
                if(format=="CODE")assertEquals(JSONObject(source).getString("body"),store.verifiedFile("p",first).readText())
                val changed=store.spec("p",first).json()
                when(format){"DOCX","PDF"->changed.getJSONArray("paragraphs").getJSONObject(0).put("text","Informe revisado");"PPTX"->changed.getJSONArray("slides").getJSONObject(0).put("title","Informe revisado");else->changed.put("body","fun answer() = 43\n")}
                val second=store.revise("p",first.id,1,ArtifactSpec.parse(changed));capture(second,store,"p")
                assertNotEquals(oldHash,second.sha256);assertEquals(oldHash,ArtifactStore.fileHash(store.verifiedFile("p",first)))
                assertTrue(store.spec("p",second).preview().contains(if(format=="CODE")"43"else "revisado"))
                val restored=store.revise("p",first.id,2,store.spec("p",first));assertEquals(3,restored.version)
                assertEquals(store.spec("p",first).json().toString(),store.spec("p",restored).json().toString())
            }
        }finally{root.deleteRecursively()};Unit
    }
    @Test fun immutableVersionsDetectConflictCorruptionAndProjectIsolation() {
        val root=root();val store=ArtifactStore(context,root)
        try {
            val first=store.create("p",spec("CODE","""{"body":"hello"}"""))
            val specFile=File(store.verifiedFile("p",first).parentFile,"spec.json");val savedSpec=specFile.readBytes();specFile.writeText("{}")
            try{store.spec("p",first);fail("blueprint integrity")}catch(e:IllegalArgumentException){assertEquals("ARTIFACT_SPEC_CHANGED",e.message)}
            specFile.writeBytes(savedSpec)
            assertTrue(store.list("other").isEmpty())
            try{store.revise("p",first.id,0,store.spec("p",first));fail("conflict must reject")}catch(e:IllegalArgumentException){assertEquals("ARTIFACT_VERSION_CONFLICT",e.message)}
            try{store.verifiedFile("other",first);fail("must stay in project")}catch(e:IllegalArgumentException){}
            val file=store.verifiedFile("p",first);file.writeText("wrong")
            try{store.verifiedFile("p",first);fail("must verify bytes")}catch(e:IllegalArgumentException){assertEquals("ARTIFACT_BYTES_CHANGED",e.message)}
        }finally{root.deleteRecursively()}
    }
    @Test fun rejectsTraversalUnknownFieldsDuplicateCellsAndActiveExternalFormula() {
        for(source in listOf("""{"format":"CODE","name":"../bad.kt","body":"x"}""","""{"format":"CODE","name":"safe.kt","body":"x","scripts":["x"]}""", """{"format":"XLSX","name":"safe.xlsx","sheets":[{"name":"S","cells":[{"address":"A1","value":"x"},{"address":"A1","value":"y"}]}]}""", """{"format":"XLSX","name":"safe.xlsx","sheets":[{"name":"S","cells":[{"address":"A1","formula":"WEBSERVICE(\"https://example.com\")"}]}]}""")) {
            try{ArtifactSpec.parse(JSONObject(source));fail("must reject $source")}catch(e:IllegalArgumentException){}
        }
    }
    @Test fun unicodeFileNamesAndBodyRemainExact() {
        val dir=root();val store=ArtifactStore(context,dir)
        try {
            val spec=ArtifactSpec.parse(JSONObject().put("format","CODE").put("name","reunión-界.kt").put("body","// ñ 界\n"))
            val revision=store.create("p",spec)
            assertEquals("reunión-界.kt",revision.name);assertEquals("// ñ 界\n",store.verifiedFile("p",revision).readText())
        }finally{dir.deleteRecursively()}
    }
    @Test fun emptyCodeDraftIsAnActualVersionedFile() {
        val dir=root();val store=ArtifactStore(context,dir)
        try {
            val revision=store.create("p",spec("CODE","""{"body":""}"""))
            assertEquals(0L,revision.bytes);assertTrue(store.verifiedFile("p",revision).isFile)
            assertEquals("",ArtifactStore(context,dir).spec("p",revision).json().getString("body"))
        }finally{dir.deleteRecursively()}
    }
    @Test fun toolCreatesVerifiedBytesOnlyWhenExplicitlyEnabledAndCapable()=runBlocking {
        val tool=ArtifactTool(context,"artifacts.create");val registry=ToolRegistry(listOf(tool));val tc=ToolContext("isolated-tool-${UUID.randomUUID()}","chat",root())
        val args=JSONObject().put("spec",spec("CODE","""{"body":"fun result() = 42"}""").json()).toString()
        assertTrue(registry.execute(ToolRequest("artifacts.create",args,TrustOrigin.MODEL),emptySet(),tc,ToolRegistry.ToolLoopState()) is ToolExecutionResult.Rejected)
        assertTrue(registry.execute(ToolRequest("artifacts.create",args,TrustOrigin.MODEL),ArtifactTool.IDS,tc.copy(grantedCapabilities=setOf(com.localai.workspace.capabilities.Capability.LOCAL_READ)),ToolRegistry.ToolLoopState()) is ToolExecutionResult.Rejected)
        val result=registry.execute(ToolRequest("artifacts.create",args,TrustOrigin.MODEL),ArtifactTool.IDS,tc,ToolRegistry.ToolLoopState()) as ToolExecutionResult.Success
        val data=JSONObject(result.output);assertEquals("PRIVATE_DRAFT_VERIFIED",data.getString("state"))
        val store=ArtifactStore(context);val revision=store.list(tc.projectId).single();assertEquals("fun result() = 42",store.verifiedFile(tc.projectId,revision).readText())
        val reader=ArtifactTool(context,"artifacts.read")
        val read=ToolRegistry(listOf(reader)).execute(ToolRequest("artifacts.read",JSONObject().put("id",revision.id).toString(),TrustOrigin.MODEL),ArtifactTool.IDS,tc.copy(grantedCapabilities=setOf(com.localai.workspace.capabilities.Capability.LOCAL_READ)),ToolRegistry.ToolLoopState()) as ToolExecutionResult.Success
        assertEquals("fun result() = 42",JSONObject(read.output).getJSONObject("spec").getString("body"))
        assertEquals(1,ArtifactStore(context).history(tc.projectId,revision.id).size)
        val schema=JSONObject(ChatToolSchemas.schema("artifacts.create"));assertEquals("spec",schema.getJSONObject("parameters").getJSONArray("required").getString(0));Unit
    }
}
