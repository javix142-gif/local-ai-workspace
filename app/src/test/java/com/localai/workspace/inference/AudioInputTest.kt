package com.localai.workspace.inference

import com.localai.workspace.audio.WavInput
import com.google.ai.edge.litertlm.Content
import com.localai.workspace.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AudioInputTest {
 @Test fun validAudioIsSameTurnAsTextAndNextRequestIsTextOnly() {
  val root=Files.createTempDirectory("audio-test").toFile()
  try {
   val file=File(root,"audio-attachments/test.wav").apply {parentFile!!.mkdirs();writeBytes(WavInput.header(16000,1,32000)+ByteArray(32000))}
   assertEquals(1000L,WavInput.inspect(file).durationMs)
   val request=GenerationRequest("Transcribe",audioPath=file.path)
   assertEquals(listOf(Content.AudioFile(file.canonicalPath),Content.Text("Transcribe")),LiteRtVisionInput.contents(root,request.prompt,null,false,request.audioPath,true).contents)
   assertNull(GenerationRequest("Hello").audioPath)
   assertEquals(listOf(Content.Text("Hello")),LiteRtVisionInput.contents(root,"Hello",null,false).contents)
   try {LiteRtVisionInput.contents(root,"Question",null,false,file.path,false);fail("No silent fallback")}catch(_:IllegalStateException){}
   try {LiteRtVisionInput.contents(root,"Question",null,false,File(root,"missing.wav").path,true);fail()}catch(_:IllegalArgumentException){}
  }finally{root.deleteRecursively()}
 }
 @Test fun invalidOversizedAndEscapedInputsAreRejected() {
  val root=Files.createTempDirectory("wav-bounds").toFile()
  try {
   val file=File(root,"bad.wav");file.writeText("not audio")
   try {WavInput.inspect(file);fail()}catch(_:IllegalArgumentException){}
   file.writeBytes(WavInput.header(8000,1,31*16000)+ByteArray(31*16000))
   try {WavInput.inspect(file);fail("Duration limit")}catch(_:IllegalArgumentException){}
   try {WavInput.validate(root,file.path);fail("Private root only")}catch(_:IllegalArgumentException){}
  }finally{root.deleteRecursively()}
 }
 @Test fun audioEffectiveTurnRemainsInOwnHistoryWithoutBecomingNextAttachment() {
  val tracker=LiteRtConversationReuse();val settings=LiteRtConversationReuse.Settings(null,.3f,.95f,40,0,128,"model",true,conversationId="a",modelIdentity="gemma")
  tracker.begin(settings,emptyList());tracker.completed("Transcribe","Hello",audioPath="private.wav")
  val history=listOf(ChatMessage(MessageRole.USER,"Transcribe",audioPath="private.wav"),ChatMessage(MessageRole.ASSISTANT,"Hello"))
  assertEquals("REUSED",tracker.reason(settings,history));assertEquals("CHAT_CHANGED",tracker.reason(settings.copy(conversationId="b"),history))
  assertEquals("HISTORY_MISMATCH",tracker.reason(settings,history.map{it.copy(audioPath=null)}))
 }
}
