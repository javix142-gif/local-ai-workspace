package com.localai.workspace.python

import android.app.Service
import android.content.Intent
import android.os.*
import android.webkit.*
import java.io.ByteArrayInputStream
import org.json.JSONObject

object PythonAssets {
 const val URL="https://python.local.invalid/py/index.html"
 private val files=setOf("index.html","main.js","worker.js","limits.js","policy.py","pyodide.js","pyodide.asm.js","pyodide.asm.wasm","python_stdlib.zip","pyodide-lock.json")
 fun name(uri:android.net.Uri):String? = uri.takeIf { it.scheme=="https" && it.host=="python.local.invalid" && it.port==-1 && it.query==null && it.fragment==null }?.path?.removePrefix("/py/")?.takeIf { it in files && uri.path=="/py/$it" }
 fun available(): Boolean=runCatching { WebView.getCurrentWebViewPackage()?.versionName?.substringBefore('.')?.toIntOrNull()?.let { it>=91 }==true }.getOrDefault(false)
}
/** Bound, private process. Python runs in a JS Worker/MEMFS, never as native app-UID Python. */
class PythonService:Service() {
 private val main=Handler(Looper.getMainLooper());private var view:WebView?=null;private var reply:Messenger?=null;private var started=0L
 private val timeout=Runnable { finish(JSONObject().put("status","TIMEOUT").put("error","Python request exceeded 45 seconds").toString()) }
 private val messenger=Messenger(Handler(Looper.getMainLooper()) { msg->
  if(msg.what==1 && view==null) {
   reply=msg.replyTo;started=SystemClock.elapsedRealtime()
   val code=msg.data.getString("code").orEmpty()
   if(code.length !in 1..8000)finish("{\"status\":\"INVALID_ARGUMENTS\"}") else start(code)
  };true
 })
 override fun onBind(intent:Intent):IBinder=messenger.binder
 @Suppress("SetJavaScriptEnabled") private fun start(code:String) {
  try {
   main.postDelayed(timeout,45_000)
   val web=WebView(this);view=web
   web.settings.apply { javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;domStorageEnabled=false;databaseEnabled=false;setGeolocationEnabled(false);javaScriptCanOpenWindowsAutomatically=false }
   web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED,true)
   web.webChromeClient=object:WebChromeClient(){override fun onConsoleMessage(message:ConsoleMessage)=true}
   web.addJavascriptInterface(object { @JavascriptInterface fun result(value:String) { main.post { finish(value) } } },"AppBridge")
   web.webViewClient=object:WebViewClient() {
    override fun shouldInterceptRequest(v:WebView,request:WebResourceRequest):WebResourceResponse {
     val name=PythonAssets.name(request.url)
     if(request.method!="GET"||name==null)return WebResourceResponse("text/plain","UTF-8",403,"Forbidden",emptyMap(),ByteArrayInputStream(ByteArray(0)))
     return try {
      val mime=when { name.endsWith(".wasm")->"application/wasm";name.endsWith(".js")->"application/javascript";name.endsWith(".html")->"text/html";name.endsWith(".json")->"application/json";else->"application/octet-stream" }
      WebResourceResponse(mime,if(mime=="application/wasm"||mime=="application/octet-stream") null else "UTF-8",200,"OK",mapOf("Content-Security-Policy" to "default-src 'none'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval'; worker-src 'self'; connect-src 'self'", "X-Content-Type-Options" to "nosniff"),assets.open("python/$name"))
     }catch(_:Exception){WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))}
    }
    override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=r.url.toString()!=PythonAssets.URL
    override fun onPageFinished(v:WebView,url:String){if(url==PythonAssets.URL)v.evaluateJavascript("window.start(${JSONObject.quote(code)})",null)}
    override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean { finish("{\"status\":\"RENDERER_GONE\",\"error\":\"Python renderer stopped\"}");return true }
   }
   web.loadUrl(PythonAssets.URL)
  }catch(_:Exception){finish("{\"status\":\"INITIALIZATION_ERROR\",\"error\":\"Python WebView could not initialize\"}")}
 }
 private fun finish(raw:String) {
  val destination=reply?:return;reply=null;main.removeCallbacks(timeout)
  val result=try { require(raw.length<=20000);JSONObject(raw).apply { put("requestMs",SystemClock.elapsedRealtime()-started);put("wasmHeapLimitBytes",256L*1024*1024) } }catch(_:Exception){JSONObject().put("status","INVALID_RESULT")}
  try { destination.send(Message.obtain(null,2).apply { data=Bundle().apply { putString("result",result.toString()) } }) }catch(_:RemoteException){ }
  view?.apply { removeJavascriptInterface("AppBridge");stopLoading();destroy() };view=null
 }
 override fun onDestroy(){main.removeCallbacks(timeout);view?.destroy();view=null;super.onDestroy();Process.killProcess(Process.myPid())}
}
