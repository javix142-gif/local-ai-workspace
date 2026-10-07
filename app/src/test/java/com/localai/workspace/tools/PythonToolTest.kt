package com.localai.workspace.tools
import com.localai.workspace.python.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],application=android.app.Application::class)
class PythonToolTest {
 @Test fun onlyLocalPinnedAssetsCanBeLoaded() {
  assertEquals("pyodide.asm.wasm",PythonAssets.name(Uri.parse("https://python.local.invalid/py/pyodide.asm.wasm")))
  listOf("https://example.com/py/index.html","file:///data/data/app/secret","https://python.local.invalid/py/../private","https://python.local.invalid/py/index.html?secret=1","https://python.local.invalid:443/py/index.html").forEach { assertNull(PythonAssets.name(Uri.parse(it))) }
 }
 @Test fun typedBoundedCodeAndNoUnknownArguments() {
  val tool=PythonTool(ApplicationProvider.getApplicationContext())
  assertNull(tool.validate(JSONObject().put("code","print(1+1)")))
  assertNotNull(tool.validate(JSONObject().put("code",4)))
  assertNotNull(tool.validate(JSONObject().put("code","x".repeat(8001))))
  assertNotNull(tool.validate(JSONObject().put("code","print(1)").put("network",true)))
 }
}
