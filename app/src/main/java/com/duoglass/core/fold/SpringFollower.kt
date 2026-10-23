package com.duoglass.core.fold

import android.view.Choreographer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * A critically damped spring chasing a target, stepped once per vsync.
 * Unlike a plain exponential ease it carries velocity, so following a hinge
 * that reports in 1° steps reads as one continuous motion, and a timed ease
 * starts and lands softly. Idles when settled. Main thread only.
 */
class SpringFollower(private val onFrame: (value: Float) -> Unit) : Choreographer.FrameCallback {

    var target = 0f
        private set
    var value = 0f
        private set
    private var velocity = 0f

    /** Seconds for the spring to (almost) settle; smaller = snappier. */
    var response = TRACK_RESPONSE

    private var scheduled = false
    private var lastFrameNanos = 0L

    fun setTarget(t: Float) {
        target = t
        if (value != target || velocity != 0f) schedule()
    }

    /** Jumps to [t] without animating or calling back. */
    fun snap(t: Float) {
        target = t
        value = t
        velocity = 0f
        cancel()
    }

    fun cancel() {
        if (!scheduled) return
        scheduled = false
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        val dt = if (lastFrameNanos == 0L) 1f / 120f
        else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNanos = frameTimeNanos

        // Closed-form critically damped step: exact for any dt, never overshoots.
        val omega = (2f * PI.toFloat()) / response.coerceAtLeast(0.01f)
        val x = value - target
        val decay = exp(-omega * dt)
        val nx = (x + (velocity + omega * x) * dt) * decay
        val nv = (velocity - omega * (velocity + omega * x) * dt) * decay
        value = target + nx
        velocity = nv
        if (abs(nx) < 0.01f && abs(nv) < 0.05f) {
            value = target
            velocity = 0f
        }
        onFrame(value)
        if (value != target) schedule() else lastFrameNanos = 0L
    }

    companion object {
        /** Following the hinge: barely perceptible lag. */
        const val TRACK_RESPONSE = 0.12f

        /** Timed eases (stalls, quiet hinge after a swap, stops-only sensors). */
        const val EASE_RESPONSE = 0.32f
    }
}
