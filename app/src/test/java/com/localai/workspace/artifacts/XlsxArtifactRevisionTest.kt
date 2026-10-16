package com.localai.workspace.artifacts

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.documents.StructuredDocuments
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class XlsxArtifactRevisionTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

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
