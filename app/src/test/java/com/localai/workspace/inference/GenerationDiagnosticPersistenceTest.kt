package com.localai.workspace.inference

import android.app.Application
import com.localai.workspace.data.GenerationDiagnosticCodec
import com.localai.workspace.domain.inference.GenerationError
import com.localai.workspace.domain.inference.GenerationStage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class GenerationDiagnosticPersistenceTest {
    @Test fun reopeningAFailedMessageKeepsCheckpointAndNativeStackOutsideBenchmarkFields() {
        val saved = GenerationDiagnosticCodec.encode(GenerationError(GenerationStage.LOADING_MODEL,
            "MODEL_LOAD_TIMEOUT", "Engine did not initialize", 240_000,
            technicalDetail = "workerStack=nativeCreateEngine\navailableRam=3210000000",
            checkpoint = "LITERT_ENGINE_INITIALIZE_START"))
        val restored = GenerationDiagnosticCodec.decode(saved)!!
        assertTrue(restored.displayText().contains("LITERT_ENGINE_INITIALIZE_START"))
        assertTrue(restored.technicalDetail!!.contains("nativeCreateEngine"))
        assertEquals("MODEL_LOAD_TIMEOUT", restored.code)
        assertFalse(saved.contains("decodeTps"))
    }

    @Test fun existingBenchmarksAndMalformedRecordsDoNotBecomeFakeDiagnostics() {
        assertNull(GenerationDiagnosticCodec.decode("load=820ms · decode=3.2 tok/s"))
        assertNull(GenerationDiagnosticCodec.decode("{invalid"))
        assertNull(GenerationDiagnosticCodec.decode("{\"type\":\"benchmark\"}"))
        assertNull(GenerationDiagnosticCodec.decode("{\"type\":\"generation_error\",\"version\":1,\"stage\":\"bad\"}"))
    }
}
