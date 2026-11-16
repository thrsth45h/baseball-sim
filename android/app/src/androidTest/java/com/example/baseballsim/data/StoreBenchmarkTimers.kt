package com.example.baseballsim.data

import android.os.SystemClock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Test APK only. No timing observer or variant flag exists in the production store. */
internal object StoreBenchmarkTimers {
    internal class Samples : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Samples>
        private val nanos = linkedMapOf<String, Long>()
        @Synchronized fun record(phase: String, value: Long) { nanos[phase] = (nanos[phase] ?: 0L) + value }
        @Synchronized fun snapshot(): Map<String, Long> = LinkedHashMap(nanos)
    }
    suspend fun <T> measure(phase: String, action: suspend () -> T): T {
        val samples = coroutineContext[Samples] ?: return action()
        val started = SystemClock.elapsedRealtimeNanos()
        try { return action() } finally { samples.record(phase, SystemClock.elapsedRealtimeNanos() - started) }
    }
    suspend fun <T> capture(action: suspend () -> T): Pair<T, Map<String, Long>> {
        val samples = Samples()
        val value = withContext(samples) { action() }
        return value to samples.snapshot()
    }
}
