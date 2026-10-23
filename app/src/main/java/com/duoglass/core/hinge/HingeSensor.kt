package com.duoglass.core.hinge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The phone's hinge angle (0 = shut, 180 = flat), shared by everything in the
 * process: the app, the live wallpaper and the overlay service all listen to
 * one registration instead of three, so they always agree on the angle.
 *
 * Every sensor that looks like a hinge *angle* is registered: the platform
 * `TYPE_HINGE_ANGLE` (non-wake-up first; the Pixel Fold has both) plus vendor
 * sensors named for the hinge/fold with a ~180° range. The finest one that
 * actually reports wins and stays chosen unless a strictly finer one appears.
 *
 * Stops-only sensors (Galaxy Z Fold 7 and earlier: 0/90/180) are detected as
 * [isCoarse]; the overlay then plays timed eases instead of tracking.
 *
 * Main thread only.
 */
object HingeSensor {

    fun interface Listener {
        fun onHinge(angle: Float)
    }

    private class Candidate(val sensor: Sensor) {
        var events = 0
        var last = Float.NaN
        var registered = false
        val distinct = LinkedHashSet<Int>()
        val resolution: Float get() = sensor.resolution.takeIf { it.isFinite() && it > 0f } ?: 1f
        val standard: Boolean get() = sensor.type == Sensor.TYPE_HINGE_ANGLE

        fun observe(v: Float) {
            events++
            last = v
            if (distinct.size < 8) distinct += v.roundToInt()
        }

        val looksCoarse: Boolean
            get() = resolution >= 45f ||
                (events >= 6 && distinct.size <= 3 && distinct.all { d -> STOPS.any { abs(d - it) <= 2 } })
    }

    private val main = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null
    private var candidates: List<Candidate> = emptyList()
    private var active: Candidate? = null
    private val listeners = LinkedHashSet<Listener>()
    private var registered = false

    /** Latest angle, NaN until the first reading. */
    var angle: Float = Float.NaN
        private set

    /** Signed angular speed in °/s, smoothed; 0 when still. */
    var velocity: Float = 0f
        private set

    var lastEventUptime: Long = 0L
        private set

    /** Event rate over the last ~half second. */
    var rateHz: Float = 0f
        private set

    private var rateWindowStart = 0L
    private var rateWindowCount = 0

    val available: Boolean get() = candidates.isNotEmpty()
    val activeSensor: Sensor? get() = active?.sensor ?: candidates.firstOrNull()?.sensor
    val isCoarse: Boolean get() = (active ?: candidates.firstOrNull())?.looksCoarse == true

    fun init(context: Context) {
        if (sensorManager != null) return
        val sm = context.applicationContext.getSystemService(SensorManager::class.java) ?: return
        sensorManager = sm
        candidates = discover(sm).map(::Candidate)
    }

    /** Adds [l]; the sensors stay registered while anyone listens. */
    fun addListener(l: Listener) {
        listeners += l
        if (!registered) register()
    }

    fun removeListener(l: Listener) {
        listeners -= l
        if (listeners.isEmpty() && registered) unregister()
    }

    fun ageMs(): Long =
        if (lastEventUptime == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - lastEventUptime

    /** Feeds an angle as if the sensor had reported it (simulator, adb test hook). */
    fun inject(value: Float) = deliver(value.coerceIn(0f, 180f), SystemClock.uptimeMillis())

    private fun register() {
        val sm = sensorManager ?: return
        registered = true
        for (c in candidates) {
            c.registered = try {
                sm.registerListener(sensorListener, c.sensor, SAMPLING_US, main)
            } catch (e: SecurityException) {
                // Samsung's folding_angle needs a system permission.
                Log.w(TAG, "register denied for ${c.sensor.name}: ${e.message}")
                false
            }
        }
        Log.i(TAG, "listening: " + candidates.joinToString { "${it.sensor.name}(res ${it.resolution}, wake ${it.sensor.isWakeUpSensor}, ok ${it.registered})" })
    }

    private fun unregister() {
        registered = false
        sensorManager?.unregisterListener(sensorListener)
        candidates.forEach { it.registered = false }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values.firstOrNull() ?: return
            val c = candidates.firstOrNull { it.sensor == event.sensor } ?: return
            if (!v.isFinite() || v < -5f || v > 185f) return // not degrees
            c.observe(v)
            if (choose() !== c) return
            deliver(v.coerceIn(0f, 180f), SystemClock.uptimeMillis())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun deliver(value: Float, now: Long) {
        val prev = angle
        val dt = (now - lastEventUptime) / 1000f
        if (prev.isFinite() && lastEventUptime != 0L && dt in 0.001f..0.25f) {
            val inst = (value - prev) / dt
            velocity += (inst - velocity) * 0.35f
        } else {
            velocity = 0f
        }
        tickRate(now)
        lastEventUptime = now
        angle = value
        for (l in listeners.toList()) l.onHinge(value)
    }

    /** Finest reporting sensor wins (standard type on ties), sticky unless strictly finer. */
    private fun choose(): Candidate? {
        val best = candidates.filter { it.events > 0 }
            .minWithOrNull(compareBy<Candidate> { it.resolution }.thenBy { !it.standard }) ?: return null
        val cur = active
        if (cur == null || best.resolution < cur.resolution) {
            if (cur !== best) Log.i(TAG, "angle source: ${best.sensor.name} res ${best.resolution}")
            active = best
        }
        return active
    }

    private fun tickRate(now: Long) {
        if (rateWindowStart == 0L || now - lastEventUptime > 1_000L) {
            rateWindowStart = now
            rateWindowCount = 0
        }
        rateWindowCount++
        val elapsed = now - rateWindowStart
        if (elapsed >= 500L) {
            rateHz = rateWindowCount * 1000f / elapsed
            rateWindowStart = now
            rateWindowCount = 0
        }
    }

    /** One line for the UI. */
    fun statusLine(): String {
        val c = active ?: candidates.firstOrNull() ?: return "No hinge sensor on this device"
        val res = if (c.resolution >= 1f) "${c.resolution.roundToInt()}°" else "%.2f°".format(c.resolution)
        return "${c.sensor.name.trim()} · $res steps${if (c.looksCoarse) " (stops only)" else ""}"
    }

    /** Full dump for a bug report. */
    fun report(): String = buildString {
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} build=${Build.ID}")
        appendLine("active=${active?.sensor?.name ?: "none"} coarse=$isCoarse rate=${"%.1f".format(rateHz)}Hz angle=$angle")
        for (c in candidates) {
            val s = c.sensor
            appendLine(
                "- ${s.name} type=${s.type} (${s.stringType}) vendor=${s.vendor} wake=${s.isWakeUpSensor} " +
                    "res=${s.resolution} range=${s.maximumRange} mode=${s.reportingMode} " +
                    "registered=${c.registered} events=${c.events} distinct=${c.distinct} last=${c.last}",
            )
        }
        if (candidates.isEmpty()) appendLine("- (no hinge sensors)")
    }

    private fun discover(sm: SensorManager): List<Sensor> {
        val all = runCatching { sm.getSensorList(Sensor.TYPE_ALL) }.getOrNull().orEmpty()
        val standard = all.filter { it.type == Sensor.TYPE_HINGE_ANGLE }
            .ifEmpty { listOfNotNull(sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)) }
            .sortedBy { it.isWakeUpSensor }
        val vendor = all.filter { s ->
            val text = "${s.name} ${s.stringType}".lowercase()
            s.type >= Sensor.TYPE_DEVICE_PRIVATE_BASE &&
                (text.contains("hinge") || text.contains("fold")) &&
                s.maximumRange.isFinite() && s.maximumRange in 150f..360f &&
                (s.reportingMode == Sensor.REPORTING_MODE_CONTINUOUS || s.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE)
        }
        return (standard + vendor).distinctBy { "${it.type}|${it.name}|${it.isWakeUpSensor}" }
    }

    private const val TAG = "GlassHinge"
    private const val SAMPLING_US = 8_000
    private val STOPS = intArrayOf(0, 90, 180)
}
