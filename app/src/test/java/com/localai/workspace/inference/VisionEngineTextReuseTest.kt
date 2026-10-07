package com.localai.workspace.inference

import org.junit.Test
import org.junit.Assert.*

class VisionEngineTextReuseTest {
    private class Handle : AutoCloseable {
        var alive = true
        override fun close() { alive = false }
    }
    private fun key(vision: Boolean) = LiteRtEngineKey("/private/gemma.litertlm", 4096, 1, 4096,
        "a".repeat(64), 4096, 4, vision, "/private/cache", "0.17.1", false)
    @Test fun initializedVisionEngineAlsoServesTextWithoutReinitializingAndCanReturnToVision() {
        val resources = LiteRtEngineResources<Handle, Handle>(Any(), { it.alive }, { it.alive })
        var initialized = 0
        assertFalse(resources.prepareEngine(key(true), create = { Handle() }, initialize = { initialized++ }))
        assertTrue(resources.prepareEngine(key(false), create = { Handle() }, initialize = { initialized++ }))
        assertTrue(resources.prepareEngine(key(true), create = { Handle() }, initialize = { initialized++ }))
        assertEquals(1, initialized); resources.close()
    }
    @Test fun textEngineCannotPretendToSupportVisionAndOtherKeyChangesStillInvalidate() {
        val resources = LiteRtEngineResources<Handle, Handle>(Any(), { it.alive }, { it.alive })
        resources.prepareEngine(key(false), create = { Handle() }, initialize = {})
        assertFalse(resources.canReuse(key(true)))
        resources.prepareEngine(key(true), create = { Handle() }, initialize = {})
        assertFalse(resources.canReuse(key(false).copy(contextSize = 2048)))
        assertFalse(resources.canReuse(key(false).copy(sha256 = "b".repeat(64))))
        resources.close()
    }
}
