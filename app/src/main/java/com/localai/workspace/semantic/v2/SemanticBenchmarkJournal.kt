package com.localai.workspace.semantic.v2

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import java.io.File

class SemanticBenchmarkJournal(private val root:File) {
    private val gson=GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private val current=AtomicFile(File(root,"benchmark-current.json"))
    @Synchronized fun read():SemanticBenchmarkProgress? = runCatching { current.openRead().bufferedReader().use{gson.fromJson(it,SemanticBenchmarkProgress::class.java)} }.getOrNull()
    @Synchronized fun write(value:SemanticBenchmarkProgress) {
        root.mkdirs();val stream=current.startWrite()
        try { stream.write(json(value).toByteArray(Charsets.UTF_8));current.finishWrite(stream) }catch(error:Throwable){current.failWrite(stream);throw error}
    }
    fun json(value:SemanticBenchmarkProgress)=gson.toJson(value)
    fun archive(value:SemanticBenchmarkProgress) { val output=AtomicFile(File(root,"benchmark-${value.benchmarkRunId}.json"));val stream=output.startWrite();try{stream.write(json(value).toByteArray(Charsets.UTF_8));output.finishWrite(stream)}catch(error:Throwable){output.failWrite(stream);throw error} }
    fun recover(evidence:ProcessExitEvidence?=null):SemanticBenchmarkProgress? {
        val value=read() ?: return null
        if(value.status!="RUNNING")return value
        val recovered=value.copy(status="ABORTED_PROCESS_RESTART",completedAt=System.currentTimeMillis(),failureKind=ProcessExitClassification.classify(evidence),exitEvidence=evidence)
        write(recovered);archive(recovered);return recovered
    }
}
object ProcessExitClassification {
    fun classify(value:ProcessExitEvidence?):String = when(value?.reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "ANDROID_LMK_SUSPECTED"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_PROCESS_DEATH"
        ApplicationExitInfo.REASON_CRASH -> "JAVA_EXCEPTION"
        ApplicationExitInfo.REASON_SIGNALED -> if(value.status==6)"NATIVE_ABORT_SUSPECTED"else"UNKNOWN_PROCESS_RESTART"
        ApplicationExitInfo.REASON_USER_REQUESTED,ApplicationExitInfo.REASON_USER_STOPPED -> "USER_REQUESTED_STOP"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATE_PROCESS_RESTART"
        else -> "UNKNOWN_PROCESS_RESTART"
    }
    fun find(context:Context,run:SemanticBenchmarkProgress):ProcessExitEvidence? {
        if(Build.VERSION.SDK_INT<30)return null
        return runCatching { context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName,run.processId,8).filter { it.processName==context.packageName && it.timestamp>=run.benchmarkStartedAt }.maxByOrNull{it.timestamp}?.let { ProcessExitEvidence(it.reason,it.status,it.timestamp) } }.getOrNull()
    }
}
