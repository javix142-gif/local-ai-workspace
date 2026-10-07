package com.localai.workspace.diagnostics

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class NormalGenerationTraceTest {
 private fun file()=File(RuntimeEnvironment.getApplication().filesDir,"trace-test.json")
 @Test fun partial_trace_is_durable_and_restart_is_not_success()=runBlocking<Unit>{
  var t=100L;val journal=NormalGenerationTrace(file(),{t++},{1000L})
  val id=journal.begin("opaque-id",false);journal.mark(id,"GENERATION_START"){it.copy(gateLocked=true,nativeInFlight=true)}
  val restored=NormalGenerationTrace(file(),{t++},{1001L}).state.value!!
  assertEquals(id,restored.runId);assertEquals("GENERATION_START",restored.phase);assertTrue(restored.processRestartObserved);assertNull(restored.completedAt);assertNull(restored.nativeInFlight);assertFalse(restored.gateLocked)
 }
 @Test fun timestamps_monotonic_and_report_contains_no_prompt_answer_or_path()=runBlocking<Unit>{
  var t=100L;val journal=NormalGenerationTrace(file(),{t++},{1000L})
  val id=journal.begin("uuid",true);journal.mark(id,"SDK_CALLBACK_FIRST");journal.mark(id,"REQUEST_COMPLETE"){it.copy(completedAt=1001L)}
  val state=journal.state.value!!;assertEquals(state.events.sortedBy{it.elapsedRealtimeMs},state.events)
  val report=journal.report();listOf("prompt","answer","/private","SQLite","Nebula").forEach{assertFalse(report.contains(it))}
  assertFalse(NormalGenerationTrace(file()).state.value!!.processRestartObserved)
 }
 @Test fun old_request_cannot_overwrite_newer_trace()=runBlocking<Unit>{
  val journal=NormalGenerationTrace(file());val a=journal.begin("a",false);val b=journal.begin("b",false)
  journal.mark(a,"REQUEST_CANCELLED"){it.copy(cancelled=true)}
  assertEquals(b,journal.state.value!!.runId);assertFalse(journal.state.value!!.cancelled)
 }
 @Test fun corrupt_trace_does_not_block_new_request()=runBlocking<Unit>{
  file().writeText("invalid");val journal=NormalGenerationTrace(file());val id=journal.begin(null,false);journal.mark(id,"REQUEST_COMPLETE"){it.copy(completedAt=1)};assertEquals(id,journal.state.value!!.runId)
 }
}
