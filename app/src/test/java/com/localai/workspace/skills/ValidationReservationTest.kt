package com.localai.workspace.skills

import com.localai.workspace.agents.withValidationReservation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class ValidationReservationTest {
    @Test fun setupFailureAlwaysReleasesReservation()=runBlocking {
        val busy=MutableStateFlow(false)
        try{withValidationReservation(busy){assertTrue(busy.value);error("SETUP_FAILED")};fail()}
        catch(e:IllegalStateException){assertEquals("SETUP_FAILED",e.message)}
        assertFalse(busy.value)
    }
    @Test fun cancellationDuringStartupReleasesReservation()=runBlocking {
        val busy=MutableStateFlow(false);val entered=CompletableDeferred<Unit>()
        val job=launch{withValidationReservation(busy){entered.complete(Unit);awaitCancellation()}}
        entered.await();assertTrue(busy.value);job.cancelAndJoin();assertFalse(busy.value)
    }
    @Test fun concurrentRejectionDoesNotReleaseTheActiveOwner()=runBlocking {
        val busy=MutableStateFlow(true)
        try{withValidationReservation(busy){fail("Concurrent suite entered")};fail()}
        catch(e:IllegalStateException){assertEquals("VALIDATION_RUNNING",e.message)}
        assertTrue(busy.value)
    }
}
