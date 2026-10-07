package com.localai.workspace.inference
import com.localai.workspace.data.*
import org.junit.Test
import org.junit.Assert.*
class Part2ThinkingTest {
 @Test fun overridesAndAuto() {
  assertFalse(AssistantRouting.thinking(ThinkingMode.AUTO, true, "Hola"))
  assertFalse(AssistantRouting.thinking(ThinkingMode.AUTO, true, "¿Cuál es la capital de Japón?"))
  assertTrue(AssistantRouting.thinking(ThinkingMode.AUTO, true, "Analiza estos datos y encuentra inconsistencias"))
  assertTrue(AssistantRouting.thinking(ThinkingMode.ON, true, "Hola"))
  assertFalse(AssistantRouting.thinking(ThinkingMode.OFF, true, "Piensa paso a paso"))
  assertFalse(AssistantRouting.thinking(ThinkingMode.ON, false, "Hola"))
 }
 @Test fun analysisNeverLeaks() {
  assertEquals("", VisibleModelOutput.select("", mapOf("analysis" to "private")))
  assertEquals("answer", VisibleModelOutput.select("", mapOf("analysis" to "private", "final" to "answer")))
  assertEquals("answer", VisibleModelOutput.select("answer", emptyMap()))
 }
 @Test fun schemasAreRoutedAndDisabledStayDisabled() {
  assertEquals(emptySet<String>(), AssistantRouting.tools(setOf("calculator.evaluate", "files.read"), "Hola", false))
  assertEquals(setOf("calculator.evaluate"), AssistantRouting.tools(setOf("calculator.evaluate"), "Calcula 8*7", false))
  assertEquals(setOf("calculator.evaluate"), AssistantRouting.tools(setOf("calculator.evaluate"), "123 * 47", false))
  assertEquals(setOf("calculator.evaluate"), AssistantRouting.tools(setOf("calculator.evaluate"), "5+5", false))
  assertTrue(AssistantRouting.tools(emptySet(), "Lee el archivo", true).isEmpty())
 }
}
