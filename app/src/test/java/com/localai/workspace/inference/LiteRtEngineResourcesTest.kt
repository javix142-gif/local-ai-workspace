package com.localai.workspace.inference

import org.junit.Assert.*
import org.junit.Test

/** Exercises the production lifecycle owner; no model, JNI or speed measurements are simulated. */
class LiteRtEngineResourcesTest {
    @Test fun backendAndSpeculativeAreEngineIdentityRatherThanCapabilityLabels() {
        val fixture = Fixture()
        fixture.prepare(key)
        assertFalse(fixture.resources.canReuse(key.copy(accelerator = "GPU")))
        assertFalse(fixture.resources.canReuse(key.copy(speculative = true)))
        assertTrue(fixture.resources.canReuse(key))
        fixture.resources.close()
    }
    private val key = LiteRtEngineKey(
        canonicalPath = "/private/models/qwen.litertlm", sizeBytes = 2048, lastModifiedMs = 1000,
        expectedSizeBytes = 2048, sha256 = "a".repeat(64), contextSize = 1024, threads = 4,
        vision = false, cacheDirectory = "/private/cache/litert", backendVersion = "0.17.1",
        diagnosticMode = false,
    )

    @Test fun verifiedConversationMayBePreservedButAChangedEngineMustAlwaysDiscardIt() {
        val fixture = Fixture()
        fixture.prepare(key)
        fixture.resources.createConversation { Session(fixture.log) }
        val session = fixture.resources.conversation!!
        assertTrue(fixture.resources.prepareEngine(key, preserveConversation = true,
            create = { error("Must not construct another engine") }, initialize = { error("Must not initialize again") }))
        assertSame(session, fixture.resources.conversation)
        assertFalse(session.closed)
        assertFalse(fixture.resources.prepareEngine(key.copy(contextSize = 2048), preserveConversation = true,
            create = { Engine(fixture.log) }, initialize = { it.ready = true }))
        assertTrue(session.closed)
        assertNull(fixture.resources.conversation)
        fixture.resources.close()
    }

    @Test fun completedTurnsReuseOneEngineWithFreshConversationAndUpdatedSampler() {
        val fixture = Fixture()
        assertFalse(fixture.prepare(key))
        fixture.resources.createConversation { Session(fixture.log, temperature = .3) }
        val first = fixture.resources.conversation!!
        first.history.add("previous full app prompt")

        assertTrue(fixture.prepare(key))
        assertTrue(first.closed)
        assertNull(fixture.resources.conversation)
        fixture.resources.createConversation { Session(fixture.log, temperature = .8) }
        val second = fixture.resources.conversation!!
        assertNotSame(first, second)
        assertTrue(second.history.isEmpty())
        assertEquals(.8, second.temperature, 0.0)
        assertEquals(1, fixture.initializations)
        assertEquals(1, fixture.engines.size)
        assertFalse(fixture.engines.single().closed)
        fixture.resources.close()
        assertTrue(second.closed)
        assertTrue(fixture.engines.single().closed)
        assertEquals(listOf("initialize", "session-close", "session-close", "engine-close"), fixture.log)
    }

    @Test fun changedFileIdentityOrEngineConfigurationForcesColdInitialization() {
        val changes = listOf(
            key.copy(canonicalPath = "/private/models/other.litertlm"),
            key.copy(sizeBytes = 4096), key.copy(lastModifiedMs = 2000),
            key.copy(expectedSizeBytes = 4096), key.copy(sha256 = "b".repeat(64)),
            key.copy(contextSize = 2048), key.copy(threads = 2), key.copy(vision = true),
            key.copy(cacheDirectory = "/private/cache/other"), key.copy(backendVersion = "other"),
        )
        for (changed in changes) {
            val fixture = Fixture()
            fixture.prepare(key)
            fixture.resources.createConversation { Session(fixture.log) }
            assertFalse("Changed key must invalidate reuse: $changed", fixture.prepare(changed))
            assertEquals(2, fixture.initializations)
            assertTrue(fixture.engines.first().closed)
            assertNull(fixture.resources.conversation)
            assertEquals(listOf("initialize", "session-close", "engine-close", "initialize"), fixture.log)
            fixture.resources.close()
        }
    }

    @Test fun smokeTestsAlwaysInitializeFreshWithoutUsingNormalChatEngine() {
        val fixture = Fixture()
        fixture.prepare(key)
        val smoke = key.copy(diagnosticMode = true, cacheDirectory = ":nocache")
        assertFalse(fixture.prepare(smoke))
        assertFalse(fixture.prepare(smoke))
        assertFalse(fixture.prepare(key))
        assertEquals(4, fixture.initializations)
        fixture.resources.close()
    }

    @Test fun missingOrInvalidRecordedHashCannotQualifyAnEngineForReuse() {
        for (hash in listOf(null, "", "not-a-sha256")) {
            val fixture = Fixture()
            val unverified = key.copy(sha256 = hash)
            fixture.prepare(unverified)
            assertFalse(fixture.resources.canReuse(unverified))
            assertFalse(fixture.prepare(unverified))
            assertEquals(2, fixture.initializations)
            fixture.resources.close()
        }
    }

    @Test fun unloadOrLostNativeHandleInvalidatesReuseAndAllowsColdRetry() {
        val fixture = Fixture()
        fixture.prepare(key)
        fixture.resources.createConversation { Session(fixture.log) }
        fixture.resources.close()
        fixture.resources.close() // Cleanup is idempotent.
        assertNull(fixture.resources.engine)
        assertNull(fixture.resources.conversation)
        assertFalse(fixture.resources.canReuse(key))
        assertFalse(fixture.prepare(key))
        fixture.resources.engine!!.ready = false
        assertFalse(fixture.resources.canReuse(key))
        assertFalse(fixture.prepare(key))
        assertEquals(3, fixture.initializations)
        fixture.resources.close()
    }

    @Test fun failedInitializationCannotBeReusedAndOwnedHandleCanBeReleasedBeforeRetry() {
        val fixture = Fixture()
        val failure = IllegalStateException("native initialization failed")
        val thrown = assertThrows(IllegalStateException::class.java) {
            fixture.resources.prepareEngine(key, create = { Engine(fixture.log).also { fixture.engines.add(it) } }) {
                it.ready = true // Allocation succeeded before the failure.
                throw failure
            }
        }
        assertSame(failure, thrown)
        assertFalse(fixture.resources.canReuse(key))
        fixture.resources.close() // Same cleanup path used by the service on error/timeout/unload.
        assertTrue(fixture.engines.first().closed)
        assertFalse(fixture.prepare(key))
        fixture.resources.close()
    }

    @Test fun failedConversationReplacementNeverLeavesPreviousHistoryOrSessionAttached() {
        val fixture = Fixture()
        fixture.prepare(key)
        fixture.resources.createConversation { Session(fixture.log) }
        val old = fixture.resources.conversation!!
        assertTrue(fixture.prepare(key))
        val failure = IllegalArgumentException("conversation configuration rejected")
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) {
            fixture.resources.createConversation { throw failure }
        })
        assertTrue(old.closed)
        assertNull(fixture.resources.conversation)
        fixture.resources.close()
        assertFalse(fixture.resources.canReuse(key))
        assertFalse(fixture.prepare(key))
        fixture.resources.close()
    }

    @Test fun acknowledgedStopCanDiscardConversationWithoutUnloadingVerifiedEngine() {
        val fixture = Fixture()
        fixture.prepare(key)
        fixture.resources.createConversation { Session(fixture.log) }
        val stopped = fixture.resources.conversation!!
        fixture.resources.closeConversation()
        assertTrue(stopped.closed)
        assertNull(fixture.resources.conversation)
        assertTrue(fixture.resources.canReuse(key))
        assertTrue(fixture.prepare(key))
        fixture.resources.createConversation { Session(fixture.log) }
        assertEquals(1, fixture.initializations)
        fixture.resources.close()
    }

    private class Fixture {
        val log = mutableListOf<String>()
        val engines = mutableListOf<Engine>()
        var initializations = 0
        val resources = LiteRtEngineResources<Engine, Session>(Any(), { it.ready }, { !it.closed })
        fun prepare(key: LiteRtEngineKey) = resources.prepareEngine(key,
            create = { Engine(log).also { engines.add(it) } },
            initialize = { initializations++; it.ready = true; log.add("initialize") },
        )
    }

    private class Engine(private val log: MutableList<String>) : AutoCloseable {
        var ready = false
        var closed = false
        override fun close() { check(!closed); closed = true; ready = false; log.add("engine-close") }
    }

    private class Session(private val log: MutableList<String>, val temperature: Double = .3) : AutoCloseable {
        var closed = false
        val history = mutableListOf<String>()
        override fun close() { check(!closed); closed = true; log.add("session-close") }
    }
}
