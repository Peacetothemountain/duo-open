package com.duoglass.core.trace

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory flight recorder for the fold: hinge samples and engine events,
 * shown live in the Lab screen and copyable into a bug report. Nothing is
 * written to disk. Main thread only.
 */
object FoldTrace {

    data class Sample(val t: Long, val angle: Float, val tilt: Float, val inner: Boolean)
    data class Event(val t: Long, val text: String)

    private const val MAX_SAMPLES = 900
    private const val MAX_EVENTS = 120

    private val samples = ArrayDeque<Sample>()
    private val events = ArrayDeque<Event>()

    private val _version = MutableStateFlow(0L)

    /** Bumps on every change; collect it to redraw. */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun sample(angle: Float, tilt: Float, inner: Boolean) {
        samples.addLast(Sample(SystemClock.uptimeMillis(), angle, tilt, inner))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
        _version.value++
    }

    fun event(text: String) {
        Log.i("GlassFold", text)
        events.addLast(Event(SystemClock.uptimeMillis(), text))
        while (events.size > MAX_EVENTS) events.removeFirst()
        _version.value++
    }

    fun samples(): List<Sample> = samples.toList()
    fun events(): List<Event> = events.toList()

    fun dump(): String = buildString {
        val now = SystemClock.uptimeMillis()
        appendLine("events (ms ago):")
        for (e in events) appendLine("  -${now - e.t}  ${e.text}")
    }
}
