package com.localai.workspace.python
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.*
import com.localai.workspace.domain.tools.*
import com.localai.workspace.security.*
import kotlinx.coroutines.*
import org.json.JSONObject

class PythonTool(private val app:Context):LocalTool {
 override val descriptor=ToolDescriptor("python.execute",setOf(ToolPermission.READ_LOCAL),ConfirmationPolicy.NEVER)
 override val timeoutMs=50_000L
 override fun validate(arguments:JSONObject):String?=if(arguments.opt("code") !is String || arguments.optString("code").length !in 1..8000 || arguments.keys().asSequence().any { it!="code" }) "Expected code string, at most 8000 characters" else null
 override suspend fun execute(arguments:JSONObject,context:ToolContext):String {
  if(!PythonAssets.available())throw ToolExecutionException("PYTHON_UNAVAILABLE","Android WebView with WASM is unavailable")
  val response=try { PythonClient(app).execute(arguments.getString("code")) }catch(_:TimeoutCancellationException){throw ToolExecutionException("TOOL_TIMEOUT","Python request timed out")}
  val json=JSONObject(response)
  if(json.optString("status")!="SUCCESS")throw ToolExecutionException(if(json.optString("status").contains("TIMEOUT"))"TOOL_TIMEOUT" else json.optString("status","PYTHON_ERROR"),json.optString("error","Python did not complete"))
  val stdout=json.optString("stdout");json.put("stdout",stdout.take(3000)).put("stdoutTruncated",stdout.length>3000)
  return json.toString()
 }
}
internal class PythonClient(private val app:Context) {
 suspend fun execute(code:String):String=withContext(Dispatchers.Main.immediate) {
  val result=CompletableDeferred<String>()
  val replies=Messenger(Handler(Looper.getMainLooper()){message->if(message.what==2)result.complete(message.data.getString("result")?:"{\"status\":\"INVALID_RESULT\"}");true})
  val connection=object:ServiceConnection {
   override fun onServiceConnected(name:ComponentName,binder:IBinder) { try { Messenger(binder).send(Message.obtain(null,1).apply { replyTo=replies;data=Bundle().apply{putString("code",code)} }) }catch(e:Exception){result.completeExceptionally(e)} }
   override fun onServiceDisconnected(name:ComponentName){if(!result.isCompleted)result.completeExceptionally(IllegalStateException("Python worker disconnected"))}
  }
  var bound=false
  try { bound=app.bindService(Intent(app,PythonService::class.java),connection,Context.BIND_AUTO_CREATE);check(bound){"Python worker unavailable"};withTimeout(45_000){result.await()} }
  finally { withContext(NonCancellable+Dispatchers.Main.immediate) { if(bound)app.unbindService(connection) } }
 }
}
