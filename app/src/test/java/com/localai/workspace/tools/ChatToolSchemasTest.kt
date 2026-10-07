package com.localai.workspace.tools
import com.localai.workspace.domain.tools.*
import com.localai.workspace.security.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29], application=android.app.Application::class)
class ChatToolSchemasTest {
 @Test fun schemasAreRealTypedAndBounded() { for (id in ChatToolSchemas.defaults) { val s=JSONObject(ChatToolSchemas.schema(id));assertEquals(id,s.getString("name"));assertEquals("object",s.getJSONObject("parameters").getString("type")) } }
 @Test fun officialSdkToolAdapterReturnsActualCalculatorResult() {
  val registry=ToolRegistry(listOf(CalculatorTool()))
  val provider=com.google.ai.edge.litertlm.tool(object:com.google.ai.edge.litertlm.OpenApiTool {
   override fun getToolDescriptionJsonString()=ChatToolSchemas.schema("calculator.evaluate")
   override fun execute(paramsJsonString:String):String=runBlocking {
    val result=registry.execute(ToolRequest("calculator.evaluate",paramsJsonString,TrustOrigin.MODEL),ChatToolSchemas.defaults,ToolContext("p","c",java.io.File("/tmp")),ToolRegistry.ToolLoopState()) as ToolExecutionResult.Success
    JSONObject().put("status","SUCCESS").put("result",result.output).toString()
   }
  })
  val result=com.google.ai.edge.litertlm.ToolManager(listOf(provider)).execute("calculator.evaluate",com.google.gson.JsonParser.parseString("{\"expression\":\"8*7\"}").asJsonObject)
  assertEquals("56.0",JSONObject(result.asString).getString("result"))
 }
 @Test fun disabledAndInvalidToolsNeverExecute()=runBlocking { val r=ToolRegistry(listOf(CalculatorTool()));val c=ToolContext("p","c",java.io.File("/tmp"));assertTrue(r.execute(ToolRequest("calculator.evaluate","{\"expression\":\"1+2\"}",TrustOrigin.MODEL),emptySet(),c,ToolRegistry.ToolLoopState()) is ToolExecutionResult.Rejected);assertTrue(r.execute(ToolRequest("calculator.evaluate","{\"expression\":\"os.system(1)\"}",TrustOrigin.MODEL),ChatToolSchemas.defaults,c,ToolRegistry.ToolLoopState()) is ToolExecutionResult.Rejected) }
}
