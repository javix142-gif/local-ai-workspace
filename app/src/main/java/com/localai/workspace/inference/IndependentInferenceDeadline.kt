package com.localai.workspace.inference

import com.localai.workspace.domain.inference.GenerationError
import com.localai.workspace.domain.inference.InferenceWatchdog
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A deadline must keep running even if the event consumer is suspended or its dispatcher is busy. */
internal class IndependentInferenceDeadline(
    watchdog: InferenceWatchdog,
    onExpired: (GenerationError) -> Unit,
    onFailure: (Throwable) -> Unit,
    threadFactory: java.util.concurrent.ThreadFactory = java.util.concurrent.ThreadFactory { task ->
        Thread(task, "LocalAI-LiteRT-deadline").apply { isDaemon = true }
    },
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory)

    init {
        scheduler.scheduleWithFixedDelay({
            if (!closed.get()) {
                val failure = try {
                    watchdog.expired()
                } catch (error: Throwable) {
                    if (closed.compareAndSet(false, true)) {
                        try { onFailure(error) } finally { scheduler.shutdown() }
                    }
                    null
                }
                if (failure != null && closed.compareAndSet(false, true)) {
                    try { onExpired(failure) }
                    catch (error: Throwable) { onFailure(error) }
                    finally { scheduler.shutdown() }
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS)
    }

    override fun close() {
        closed.set(true)
        scheduler.shutdownNow()
    }
}
