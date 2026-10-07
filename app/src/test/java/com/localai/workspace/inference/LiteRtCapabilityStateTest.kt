package com.localai.workspace.inference

import org.junit.Assert.*
import org.junit.Test

class LiteRtCapabilityStateTest {
    private fun legacy() = requireNotNull(javaClass.classLoader?.getResourceAsStream("litert/gemma4-legacy-metadata.pb")).use { it.readBytes() }
    @Test fun realPublishedBundleHasAbsentFlagsAndAuditedContract() {
        val m=LiteRtBundleMetadata.fromProto(legacy())
        assertNull(m.toolsDeclared); assertNull(m.thinkingDeclared)
        assertEquals(8,m.modelProcessor); assertTrue(m.legacyGemmaContract)
        assertEquals("02b3091acf53c0b722e3db0c7a1b4980363edcc2d85549dafa339ff5dbfff629",m.templateSha256)
        val state=LiteRtCapabilityResolver.resolve(m.toolsDeclared,false,m.legacyGemmaContract,true)
        assertTrue(state.available);assertTrue(state.enabled)
        assertEquals("ABSENT",state.metadata("tools")["tools.bundleDeclared"])
    }
    @Test fun explicitTrueIsHonoredWithoutLegacyTemplate() {
        val m=LiteRtBundleMetadata.fromProto(byteArrayOf(88,1,96,1))
        assertEquals(true,m.thinkingDeclared);assertEquals(true,m.toolsDeclared)
        assertTrue(LiteRtCapabilityResolver.resolve(m.toolsDeclared,false,false).available)
    }
    @Test fun explicitFalseOverridesAuditedLegacyContract() {
        val m=LiteRtBundleMetadata.fromProto(legacy()+byteArrayOf(88,0,96,0))
        assertTrue(m.legacyGemmaContract)
        assertFalse(LiteRtCapabilityResolver.resolve(m.toolsDeclared,true,true,true).available)
        assertFalse(LiteRtCapabilityResolver.resolve(m.thinkingDeclared,true,true,true).enabled)
    }
    @Test fun missingFlagsInUnknownBundleRemainUnavailable() {
        val m=LiteRtBundleMetadata.fromProto(byteArrayOf(50,2,10,0))
        assertFalse(m.legacyGemmaContract)
        val s=LiteRtCapabilityResolver.resolve(m.toolsDeclared,false,m.legacyGemmaContract,true)
        assertFalse(s.available);assertFalse(s.enabled);assertEquals("BUNDLE_CAPABILITY_MISSING",s.reason)
    }
    @Test fun alternateSchemaDoesNotAssumeCapabilities() {
        val m=LiteRtBundleMetadata.fromProto(byteArrayOf(50,2,122,0))
        assertNull(m.modelProcessor?.takeIf { it == 8 });assertFalse(m.legacyGemmaContract)
        assertNull(m.toolsDeclared);assertNull(m.thinkingDeclared)
    }
    @Test fun modifiedTemplateCannotInheritAuditedSupport() {
        val bytes=legacy(); val start=bytes.indices.first { i -> i+10<bytes.size && String(bytes,i,10,Charsets.UTF_8)=="{%- macro " }
        bytes[start]='!'.code.toByte()
        assertFalse(LiteRtBundleMetadata.fromProto(bytes).legacyGemmaContract)
    }
    @Test fun runtimeAndAppAreDistinctFromModelSupport() {
        val runtime=LiteRtCapabilityResolver.resolve(true,true,false,true,runtime=false)
        assertEquals(true,runtime.modelSupport);assertEquals("RUNTIME_CAPABILITY_MISSING",runtime.reason);assertFalse(runtime.enabled)
        val app=LiteRtCapabilityResolver.resolve(true,true,false,true,app=false)
        assertEquals("APP_CAPABILITY_MISSING",app.reason);assertFalse(app.available)
    }
    @Test fun availabilityDoesNotImplyEnabled() {
        val s=LiteRtCapabilityResolver.resolve(true,true,false)
        assertTrue(s.available);assertFalse(s.enabled)
    }
}
