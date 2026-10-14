package com.localai.workspace.artifacts

import android.app.Application
import androidx.room.Room
import com.localai.workspace.data.*
import com.localai.workspace.domain.tools.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class OfficeRetrievalTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun realFileToolRetrievesExactQualifiedCellsAndAbstainsWithinDocument()=runBlocking {
        val dir=File(context.cacheDir,"exact-${UUID.randomUUID()}").apply{mkdirs()};val db=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        try {
            val store=ArtifactStore(context,dir);val spec=ArtifactSpec.parse(JSONObject("""{"format":"XLSX","name":"exact.xlsx","sheets":[{"name":"Hoja1","cells":[{"address":"B14","value":"incorrecta"}]},{"name":"Hoja2","cells":[{"address":"B14","value":"valor exacto 界"},{"address":"A1","value":"1","type":"number"},{"address":"A2","value":"5","type":"number"},{"address":"A3","formula":"${'$'}A${'$'}2-${'$'}A${'$'}1","value":"4","type":"number"}]}]}"""))
            val r=store.create("p",spec);val file=store.verifiedFile("p",r)
            db.documentDao().insert(DocumentEntity("doc","p",r.name,localPath=file.path,mimeType=r.format.mime,fileHash=r.sha256,byteSize=r.bytes,extractionStatus="READY",indexingStatus="READY",importedAt=1,updatedAt=1))
            val tool=ProjectFileReadTool(db,dir);val tc=ToolContext("p","chat",dir)
            fun args(sheet:String,range:String)=JSONObject().put("documentId","doc").put("sheet",sheet).put("range",range)
            assertNull(tool.validate(args("Hoja2","${'$'}B${'$'}14")))
            val exact=JSONObject(tool.execute(args("hoja2","${'$'}B${'$'}14"),tc));assertTrue(exact.getBoolean("complete"));assertEquals("Hoja2",exact.getJSONArray("cells").getJSONObject(0).getString("sheet"));assertEquals("valor exacto 界",exact.getJSONArray("cells").getJSONObject(0).getString("value"))
            val range=JSONObject(tool.execute(args("Hoja2","A1:A3"),tc));assertEquals(3,range.getJSONArray("cells").length());assertEquals("${'$'}A${'$'}2-${'$'}A${'$'}1",range.getJSONArray("cells").getJSONObject(2).getString("formula"))
            val absent=JSONObject(tool.execute(args("missing","B14"),tc));assertFalse(absent.getBoolean("complete"));assertEquals(0,absent.getJSONArray("cells").length());assertEquals("B14",absent.getJSONArray("missingCellAddresses").getString(0))
            try{tool.execute(args("Hoja2","A1:XFD1048576"),tc);fail("range budget")}catch(e:IllegalArgumentException){assertEquals("EXACT_RANGE_LIMIT_64",e.message)}
            try{tool.execute(args("Hoja2","B14"),tc.copy(projectId="other"));fail("scope")}catch(e:IllegalArgumentException){}
        }finally{db.close();dir.deleteRecursively()};Unit
    }
    @Test fun pptxReopensAsOrderedSlidesAndDocxRunsKeepWordsAndTableBoundaries()=runBlocking {
        val dir=File(context.cacheDir,"office-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val ppt=File(dir,"slides.pptx");ArtifactRenderer(context).render(ArtifactSpec.parse(JSONObject("""{"format":"PPTX","name":"slides.pptx","slides":[{"title":"Primera","body":"España"},{"title":"Segunda","body":"Texto final"}]}""")),ppt)
            val parsed=DocumentParser(context).parse(ppt,ArtifactFormat.PPTX.mime,ppt.name)
            assertEquals(listOf(1,2),parsed.pages.map{it.pageNumber});assertTrue(parsed.pages[0].text.contains("España"));assertTrue(parsed.pages[1].text.contains("Texto final"))
            val doc=File(dir,"runs.docx")
            ZipOutputStream(doc.outputStream()).use{z->z.putNextEntry(ZipEntry("word/document.xml"));z.write("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>con</w:t></w:r><w:r><w:t>tenido</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>Nombre</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Valor</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>""".toByteArray());z.closeEntry()}
            val text=DocumentParser(context).parse(doc,ArtifactFormat.DOCX.mime,doc.name).pages.single().text
            assertTrue(text.startsWith("contenido\n"));assertTrue(text.contains("\t"));assertNull(DocumentParser(context).parse(doc,ArtifactFormat.DOCX.mime,doc.name).pages.single().pageNumber)
        }finally{dir.deleteRecursively()};Unit
    }
}
