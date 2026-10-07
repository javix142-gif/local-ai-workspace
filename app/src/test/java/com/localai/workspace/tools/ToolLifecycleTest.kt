package com.localai.workspace.tools
import android.app.Application
import androidx.room.Room
import com.localai.workspace.data.*
import com.localai.workspace.domain.tools.*
import com.localai.workspace.security.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],application=Application::class)
class ToolLifecycleTest {
 @Test fun callsAreBoundedRepeatedCallsRejectedAndTimeoutReported()=runBlocking {
  val tool=object:LocalTool {
   override val descriptor=ToolDescriptor("slow",setOf(ToolPermission.READ_LOCAL),ConfirmationPolicy.NEVER)
   override val timeoutMs=20L
   override fun validate(arguments:JSONObject):String?=null
   override suspend fun execute(arguments:JSONObject,context:ToolContext):String {delay(1000);return "never"}
  }
  val r=ToolRegistry(listOf(tool),maxCallsPerTurn=1);val state=ToolRegistry.ToolLoopState();val context=ToolContext("p","c",File("/tmp"))
  assertEquals("TIMEOUT",(r.execute(ToolRequest("slow","{}",TrustOrigin.MODEL),setOf("slow"),context,state) as ToolExecutionResult.Failed).code)
  assertTrue(r.execute(ToolRequest("slow","{}",TrustOrigin.MODEL),setOf("slow"),context,state) is ToolExecutionResult.Rejected)
  val calc=ToolRegistry(listOf(CalculatorTool()));val repeated=ToolRegistry.ToolLoopState();val call=ToolRequest("calculator.evaluate","{\"expression\":\"3+4\"}",TrustOrigin.MODEL)
  assertTrue(calc.execute(call,setOf("calculator.evaluate"),context,repeated) is ToolExecutionResult.Success)
  assertTrue(calc.execute(call,setOf("calculator.evaluate"),context,repeated) is ToolExecutionResult.Rejected)
 }
 @Test fun projectReadCannotEscapeScopeAndStructuredSumIsReal()=runBlocking {
  val context=RuntimeEnvironment.getApplication();val db=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
  val file=File(context.filesDir,"documents/tool.csv").apply{parentFile!!.mkdirs();writeText("name,value\na,12\nb,4\n")}
  try {
   db.documentDao().insert(DocumentEntity("d","p","tool.csv",localPath=file.path,mimeType="text/csv",fileHash="a",byteSize=file.length(),extractionStatus="READY",indexingStatus="READY",importedAt=1,updatedAt=1))
   val read=ProjectFileReadTool(db);val args=JSONObject("{\"documentId\":\"d\",\"column\":\"B\",\"aggregate\":\"sum\"}")
   val result=JSONObject(read.execute(args,ToolContext("p","c",File("/tmp"))));assertEquals("16",result.getString("value"));assertEquals(2,result.getInt("numericCells"))
   file.writeText("name,value\na,1e99999999\n"); try { read.execute(args,ToolContext("p","c",File("/tmp")));fail("Unbounded exponent") }catch(_:IllegalArgumentException){}
   try { read.execute(args,ToolContext("other","c",File("/tmp")));fail("Scope escaped") } catch(_:IllegalArgumentException){}
   assertNotNull(read.validate(JSONObject("{\"documentId\":\"d\",\"path\":\"/etc/passwd\"}")))
   assertTrue(JSONObject(ProjectFileListTool(db).execute(JSONObject(),ToolContext("other","c",File("/tmp")))).getJSONArray("files").length()==0)
  }finally{db.close();file.delete()}
 }
}
