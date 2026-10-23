package com.duoglass.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import com.duoglass.core.fold.FoldGeometry
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.fold.SpringFollower
import com.duoglass.core.fold.isInnerPanel
import com.duoglass.core.hinge.HingeSensor
import com.duoglass.core.render.FoldShader
import com.duoglass.core.settings.Engine
import com.duoglass.core.settings.GlassSettings
import com.duoglass.core.trace.FoldTrace
import com.duoglass.overlay.surface.FoldSurface
import com.duoglass.overlay.surface.LiveGlassRig
import com.duoglass.overlay.surface.SnapshotSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The fold on one lit display.
 *
 * Inputs are two signals: the hinge angle, and which panel this display is
 * driving (its physical mode flips at the swap). Opening a one-panel-at-a-time
 * phone (Pixel Fold, OnePlus Open) is therefore: leave rest on the cover →
 * frost sweeps in → swap → the inner panel starts frosted → clears to flat.
 * Closing is the reverse.
 *
 * While the glass is up it *tracks* the hinge through a spring. Whenever the
 * hinge goes quiet it *eases* out on its own instead of freezing over the
 * screen: after [STALL_MS] mid-fold (you stopped in tent mode), or after only
 * [QUIET_AFTER_CLOSE_MS] on a cover that just lit up during a close (some
 * hinge HALs stop reporting there, so that's the cover settling into focus).
 * Moving the hinge again by [RESUME_DEG] replays it.
 *
 * Stops-only sensors (0/90/180) can't be tracked at all: each stop change plays
 * a timed ease to full frost, held until the next stop.
 */
class PanelDirector(
    private val service: AccessibilityService,
    val display: Display,
    private val handler: Handler,
    private val scope: CoroutineScope,
    /** True when another director is live on an inner panel right now. */
    private val innerLitElsewhere: () -> Boolean,
    private val onShowingChanged: () -> Unit,
    private val cache: SnapshotCache,
    /** This display's pre-made live-glass windows (owned by the service, outlive the director). */
    private val rig: (Context, WindowManager) -> LiveGlassRig,
) {
    private enum class Phase { IDLE, PREPARING, SHOWING }
    private enum class Drive { TRACK, EASE }

    private val id = display.displayId
    /**
     * The service's own WindowManager carries the accessibility connection's
     * window token; a window context made from a display context doesn't on
     * Android 17 ("token null is not valid"), so that path is only used for a
     * second panel, which the service's own manager can't reach.
     */
    private val uiContext: Context by lazy {
        if (id == Display.DEFAULT_DISPLAY) service
        else service.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
    }
    private val windowManager: WindowManager by lazy { uiContext.getSystemService(WindowManager::class.java) }
    private val pxPerMm: Float by lazy { FoldShader.pxPerMm(service.createDisplayContext(display)) }

    private var phase = Phase.IDLE
    private var drive = Drive.TRACK
    private var surface: FoldSurface? = null
    private var follower: SpringFollower? = null

    var inner = display.isInnerPanel()
        private set
    private var armed = true
    private var swapPending = false
    private var afterSwapPhase = false
    private var stallAngle = Float.NaN
    private var lastMoveMs = 0L
    private var demo = false
    private var gen = 0
    private var attempt = 0
    private var bridging = false
    private var destroyed = false

    val showing: Boolean get() = phase == Phase.SHOWING
    val displayedTilt: Float get() = surface?.tilt ?: 0f

    private val watchdog = object : Runnable {
        override fun run() {
            if (phase != Phase.SHOWING || drive != Drive.TRACK || demo) return
            val quiet = SystemClock.uptimeMillis() - lastMoveMs
            val limit = if (!inner && afterSwapPhase && !concurrentCover()) QUIET_AFTER_CLOSE_MS else STALL_MS
            if (quiet >= limit) {
                stallAngle = HingeSensor.angle
                FoldTrace.event("d$id: hinge quiet ${quiet}ms → ease out")
                easeOut()
            } else {
                handler.postDelayed(this, WATCHDOG_MS)
            }
        }
    }

    private val hold = Runnable { if (drive == Drive.EASE && !demo) easeOut() }

    init {
        // A panel lighting up mid-fold is the second half of a fold in progress.
        swapPending = currentTilt() >= FoldModel.FLAT_EPSILON
        armed = !swapPending
        // Pay for the live-glass windows now, not when the fold starts.
        if (useLive()) handler.post { if (!destroyed) rig(uiContext, windowManager).warm() }
        // On the Pixel Fold display 0 blinks off during the swap, so the
        // director is rebuilt rather than seeing its mode flip: this is the swap.
        if (swapPending && HingeSensor.ageMs() < 1_000) GlassSettings.learnSwap(HingeSensor.angle)
        FoldTrace.event("d$id: engine up, ${if (inner) "inner" else "cover"}${if (swapPending) ", mid-fold" else ""}")
    }

    private fun concurrentCover(): Boolean = !inner && innerLitElsewhere()

    private fun tiltFor(angle: Float): Float {
        val c = GlassSettings.value
        return when {
            inner -> FoldModel.innerTilt(angle, c)
            concurrentCover() -> FoldModel.concurrentCoverTilt(angle, c)
            else -> FoldModel.coverTilt(angle, c)
        }
    }

    private fun currentTilt() = tiltFor(HingeSensor.angle)

    fun onHinge(angle: Float) {
        lastMoveMs = SystemClock.uptimeMillis()
        evaluate()
        if (phase != Phase.SHOWING || demo) return
        val f = follower ?: return
        val tilt = tiltFor(angle)
        when (drive) {
            Drive.TRACK -> if (tilt < FoldModel.FLAT_EPSILON) dismiss(FADE_FLAT_MS) else f.setTarget(tilt)
            Drive.EASE -> if (HingeSensor.isCoarse && tilt < FoldModel.FLAT_EPSILON) {
                // The rest stop is a stops-only sensor's first news that the fold is done.
                handler.removeCallbacks(hold)
                easeOut()
            }
        }
    }

    /** Re-reads the panel and the hinge; starts a phase when one begins. */
    fun evaluate() {
        if (demo) return
        val nowInner = display.isInnerPanel()
        if (nowInner != inner) {
            val a = HingeSensor.angle
            FoldTrace.event("d$id: swap → ${if (nowInner) "inner" else "cover"} at ${fmt(a)}°")
            if (a.isFinite()) GlassSettings.learnSwap(a)
            inner = nowInner
            swapPending = true
            stallAngle = Float.NaN
            if (phase != Phase.IDLE) removeNow() // the old panel's picture means nothing now
        }
        val angle = HingeSensor.angle
        if (angle.isNaN()) return
        val tilt = tiltFor(angle)
        if (tilt < FoldModel.FLAT_EPSILON) {
            armed = true
            swapPending = false
            stallAngle = Float.NaN
            return
        }
        if (phase != Phase.IDLE) return
        when {
            swapPending -> {
                swapPending = false
                armed = false
                start(afterSwap = true)
            }
            armed && tilt >= REST_LEAVE_TILT -> {
                armed = false
                start(afterSwap = false)
            }
            stallAngle.isFinite() && abs(angle - stallAngle) >= RESUME_DEG -> {
                stallAngle = Float.NaN
                start(afterSwap = false)
            }
        }
    }

    private fun useLive(): Boolean {
        val engine = GlassSettings.value.engine
        if (engine == Engine.SNAPSHOT) return false
        return runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
    }

    private fun start(afterSwap: Boolean, startTilt: Float? = null) {
        afterSwapPhase = afterSwap
        val now = SystemClock.uptimeMillis()
        if (useLive()) {
            phase = Phase.PREPARING
            present(null, afterSwap, startTilt, now)
            return
        }
        // A panel that just lit takes ~0.3 s to capture: start at once with its
        // last picture and swap the fresh one in under the frost.
        if (afterSwap && startTilt == null && GlassSettings.value.instantStart) {
            val b = windowManager.maximumWindowMetrics.bounds
            cache.get(inner, b.width(), b.height())?.let { stale ->
                phase = Phase.PREPARING
                FoldTrace.event("d$id: bridging with ${cache.ageMs(inner)}ms-old picture")
                present(stale, afterSwap = true, startTilt = null, t0 = now)
                bridging = phase == Phase.SHOWING
            }
        }
        if (!bridging) phase = Phase.PREPARING
        capture(++gen, 1, afterSwap, startTilt)
    }

    private fun capture(g: Int, n: Int, afterSwap: Boolean, startTilt: Float?) {
        val t0 = SystemClock.uptimeMillis()
        attempt = n
        fun stale() = g != gen || (phase != Phase.PREPARING && !bridging)
        fun giveUp() {
            if (bridging) bridging = false else {
                phase = Phase.IDLE
                demo = false
            }
        }
        fun retry() {
            // The framework refuses captures closer than ~333 ms apart.
            val wait = (t0 + MIN_CAPTURE_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            handler.postDelayed({ if (!stale()) capture(g, n + 1, afterSwap, startTilt) }, wait)
        }
        handler.postDelayed({
            if (!stale() && attempt == n) {
                FoldTrace.event("d$id: capture $n timed out")
                giveUp()
            }
        }, CAPTURE_TIMEOUT_MS)

        service.takeScreenshot(id, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val buffer = result.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                buffer.close()
                if (stale() || bitmap == null) {
                    bitmap?.recycle()
                    if (!stale()) giveUp()
                    return
                }
                if (!afterSwap || n >= MAX_ATTEMPTS) {
                    onCaptured(bitmap, afterSwap, startTilt, t0)
                    return
                }
                scope.launch {
                    val black = withContext(Dispatchers.Default) { isMostlyBlack(bitmap) }
                    if (stale()) {
                        bitmap.recycle()
                        return@launch
                    }
                    if (black && !demo) {
                        bitmap.recycle()
                        FoldTrace.event("d$id: capture $n black after ${SystemClock.uptimeMillis() - t0}ms, retry")
                        retry()
                    } else {
                        onCaptured(bitmap, afterSwap, startTilt, t0)
                    }
                }
            }

            override fun onFailure(errorCode: Int) {
                if (stale()) return
                FoldTrace.event("d$id: screenshot failed ($errorCode)")
                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && n < MAX_ATTEMPTS) retry()
                else giveUp() // secure content (banking, DRM) lands here: the glass simply doesn't play
            }
        })
    }

    private fun onCaptured(bitmap: Bitmap, afterSwap: Boolean, startTilt: Float?, t0: Long) {
        cache.put(inner, bitmap)
        if (bridging) {
            bridging = false
            val s = surface as? SnapshotSurface
            if (s != null && phase == Phase.SHOWING) {
                FoldTrace.event("d$id: fresh capture (${SystemClock.uptimeMillis() - t0}ms) replaces bridge")
                s.replace(bitmap)
            }
            return
        }
        present(bitmap, afterSwap, startTilt, t0)
    }

    private fun present(bitmap: Bitmap?, afterSwap: Boolean, startTilt: Float?, t0: Long) {
        if (phase != Phase.PREPARING) return
        val config = GlassSettings.value
        val angle = HingeSensor.angle
        val coarse = startTilt == null && HingeSensor.isCoarse
        val peak = FoldModel.peakTilt(config)
        val tilt = startTilt ?: if (coarse) (if (afterSwap) peak else 0.06f) else tiltFor(angle)
        // A capture that lands with the fold nearly over isn't worth a pop-in.
        val late = bitmap != null && afterSwap && startTilt == null &&
            if (inner) angle > SKIP_INNER_ABOVE else angle < SKIP_COVER_BELOW
        if (tilt < FoldModel.FLAT_EPSILON || late) {
            FoldTrace.event("d$id: hinge ${fmt(angle)}° by capture time (${SystemClock.uptimeMillis() - t0}ms), skipped")
            phase = Phase.IDLE
            return
        }
        show(bitmap, tilt, fadeIn = afterSwap, easeTo = if (coarse) peak else null, t0 = t0)
    }

    private fun show(bitmap: Bitmap?, startTilt: Float, fadeIn: Boolean, easeTo: Float?, t0: Long) {
        val config = GlassSettings.value
        val b = windowManager.maximumWindowMetrics.bounds
        val fold = FoldGeometry.forPanel(inner, b.width().toFloat(), b.height().toFloat(), display.rotation, config)
        val s: FoldSurface? = if (bitmap != null) {
            SnapshotSurface(uiContext, windowManager, bitmap, config, fold)
        } else {
            rig(uiContext, windowManager).acquire(config, pxPerMm, fold)
        }
        if (s == null || !s.attached) {
            FoldTrace.event("d$id: overlay could not attach")
            phase = Phase.IDLE
            demo = false
            return
        }
        s.tilt = startTilt
        surface = s
        phase = Phase.SHOWING
        onShowingChanged()
        if (fadeIn) s.fadeIn(FADE_IN_MS)
        val f = SpringFollower { t ->
            s.tilt = t
            val target = follower?.target ?: 0f
            if (t < FoldModel.FLAT_EPSILON && target < FoldModel.FLAT_EPSILON && !demo) dismiss(FADE_FLAT_MS)
        }
        f.snap(startTilt)
        follower = f
        lastMoveMs = SystemClock.uptimeMillis()
        val how = if (bitmap != null) "snapshot ${bitmap.width}×${bitmap.height}" else "live glass"
        FoldTrace.event("d$id: $how at tilt ${fmt(startTilt)} after ${SystemClock.uptimeMillis() - t0}ms")
        when {
            demo -> runDemoScript(f)
            easeTo != null -> {
                drive = Drive.EASE
                f.response = SpringFollower.EASE_RESPONSE
                f.setTarget(easeTo)
                handler.postDelayed(hold, COARSE_HOLD_MS)
            }
            else -> {
                drive = Drive.TRACK
                f.response = SpringFollower.TRACK_RESPONSE
                handler.postDelayed(watchdog, WATCHDOG_MS)
            }
        }
    }

    private fun easeOut() {
        val f = follower ?: return
        drive = Drive.EASE
        handler.removeCallbacks(watchdog)
        f.response = SpringFollower.EASE_RESPONSE
        f.setTarget(0f)
    }

    private fun dismiss(fadeMs: Long) {
        val s = surface ?: return
        clearState()
        s.fadeOut(fadeMs) { s.detach() }
    }

    private fun removeNow() {
        val s = surface
        clearState()
        s?.detach()
    }

    private fun clearState() {
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(hold)
        follower?.cancel()
        follower = null
        surface = null
        bridging = false
        drive = Drive.TRACK
        phase = Phase.IDLE
        onShowingChanged()
    }

    fun destroy() {
        destroyed = true
        gen++ // orphan any capture in flight
        demo = false
        removeNow()
        FoldTrace.event("d$id: engine down")
    }

    /**
     * Plays what this panel shows during a fold, without folding: inner — an
     * unfold from full frost to clear; cover — frost sweeping in, then out.
     */
    private var demoHoldMs = 0L

    fun playDemo(holdMs: Long = 0L) {
        if (phase != Phase.IDLE || demo) return
        demo = true
        demoHoldMs = holdMs.coerceIn(0L, 10_000L)
        val peak = FoldModel.peakTilt(GlassSettings.value)
        start(afterSwap = false, startTilt = if (inner) peak else 0.06f)
    }

    private fun runDemoScript(f: SpringFollower) {
        val peak = FoldModel.peakTilt(GlassSettings.value)
        f.response = DEMO_RESPONSE
        val begin = (if (inner) 380L else 120L) + demoHoldMs
        handler.postDelayed({
            if (inner) {
                f.setTarget(0f)
                handler.postDelayed({ endDemo() }, 1_600)
            } else {
                f.setTarget(peak)
                handler.postDelayed({ f.setTarget(0f) }, 1_400)
                handler.postDelayed({ endDemo() }, 3_000)
            }
        }, begin)
    }

    private fun endDemo() {
        demo = false
        dismiss(FADE_FLAT_MS)
    }

    private fun isMostlyBlack(hw: Bitmap): Boolean {
        val sw = runCatching { hw.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return false
        try {
            val n = 24
            var max = 0
            for (iy in 0 until n) {
                val y = ((iy + 0.5f) * sw.height / n).toInt()
                for (ix in 0 until n) {
                    val c = sw.getPixel(((ix + 0.5f) * sw.width / n).toInt(), y)
                    val sum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                    if (sum > max) max = sum
                }
            }
            return max < BLACK_THRESHOLD
        } finally {
            sw.recycle()
        }
    }

    private fun fmt(v: Float) = if (v.isFinite()) "%.1f".format(v) else "?"

    private companion object {
        const val MIN_CAPTURE_INTERVAL_MS = 340L
        const val MAX_ATTEMPTS = 3
        const val CAPTURE_TIMEOUT_MS = 1_100L
        const val BLACK_THRESHOLD = 30
        const val REST_LEAVE_TILT = 2.5f
        const val RESUME_DEG = 8f
        const val SKIP_INNER_ABOVE = 140f
        const val SKIP_COVER_BELOW = 8f
        const val STALL_MS = 650L
        const val QUIET_AFTER_CLOSE_MS = 180L
        const val WATCHDOG_MS = 50L
        const val COARSE_HOLD_MS = 2_500L
        const val FADE_IN_MS = 140L
        const val FADE_FLAT_MS = 140L
        const val DEMO_RESPONSE = 0.9f
    }
}
