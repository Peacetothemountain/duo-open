package com.duoglass.overlay

import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.fold.isInnerPanel
import com.duoglass.core.settings.GlassSettings
import com.duoglass.core.trace.FoldTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Keeps the cover screen on until the hinge is [com.duoglass.core.settings.GlassConfig.coverHoldAngle]
 * open, instead of the Pixel Fold's ~5–10°.
 *
 * Android's book-style policy only keeps the outer screen until 90° when it
 * believes the phone is propped in tent/wedge mode; otherwise it lights the
 * inner screen as soon as the hinge leaves shut, and that threshold lives in
 * the system image. What a shell-level caller *can* do is override the device
 * state (`cmd device_state state 0`, i.e. CLOSED): while the phone is shut the
 * override is put in place (nothing visible changes), and once the hinge passes
 * the hold angle it is reset, so the system re-evaluates and switches to the
 * inner screen. Shizuku provides the shell (or root) identity, or `su` on a
 * rooted phone.
 *
 * Safety: the override lives in system_server and survives our process, so
 * every path that might leave it set releases it — any hinge reading past the
 * hold angle (also right after a restart, when we can't know whether a previous
 * instance left one), turning the feature off, and the service stopping. A
 * reboot clears it too, as does `adb shell cmd device_state state reset`.
 */
object CoverHold {

    sealed interface Status {
        val summary: String
        data object Off : Status { override val summary = "Off" }
        data object NoShizuku : Status { override val summary = "Shizuku isn't installed" }
        data object NotRunning : Status { override val summary = "Shizuku isn't running" }
        data object NeedsPermission : Status { override val summary = "Allow Duo Glass in Shizuku, or use root" }
        data object Ready : Status { override val summary = "Ready" }
        data object Holding : Status { override val summary = "Holding the cover screen" }
    }

    private const val TAG = "GlassCoverHold"
    private const val PERMISSION_CODE = 4027
    /** The hold re-arms only this far below its release angle, so it can't flap at the threshold. */
    private const val REARM_MARGIN = 20f
    /** At or below this the phone is shut: hold even with the screen off, so opening from sleep is held too. */
    private const val SHUT_DEG = 3f
    /** Opening a held phone past this wakes it (from sleep or the screensaver), as an unheld unfold would. */
    private const val WAKE_DEG = 8f

    private val thread = HandlerThread("cover-hold").apply { start() }
    private val worker = Handler(thread.looper)
    /** Waking runs apart from [worker] so it can never hold up a release. */
    private val waker = Handler(HandlerThread("cover-hold-wake").apply { start() }.looper)

    private val _status = MutableStateFlow<Status>(Status.NotRunning)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Assume a hold may exist until we've reset once (a previous process may have left one). */
    @Volatile
    private var holding = true
    private var startupReset = false
    /**
     * Armed when the phone is shut, spent by the wake: so only an open from
     * shut wakes, once — not a close passing through the same angles.
     */
    private var wakeArmed = false

    private var appContext: android.content.Context? = null
    private var listening = false

    /** Call once per process that drives the hold (the overlay service, and the app for status). */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        if (listening) return
        listening = true
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener { refresh() }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        }
        refresh()
    }

    private fun shizukuGranted(): Boolean = runCatching {
        Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Something can run the device-state command for us. */
    fun ready(): Boolean = shizukuGranted() || GlassSettings.value.holdViaRoot

    private fun shizukuInstalled(): Boolean = runCatching {
        appContext?.packageManager?.getPackageInfo("moe.shizuku.privileged.api", 0) != null
    }.getOrDefault(false)

    fun refresh() {
        val c = GlassSettings.value
        _status.value = when {
            ready() && !c.coverHold -> Status.Off
            ready() && holding -> Status.Holding
            ready() -> Status.Ready
            !shizukuInstalled() -> Status.NoShizuku
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> Status.NotRunning
            else -> Status.NeedsPermission
        }
        FoldModel.swapOverride = if (c.coverHold && ready()) c.coverHoldAngle else Float.NaN
        if (ready()) {
            if (!startupReset) {
                // Clear anything a previous process left, then decide afresh.
                startupReset = true
                worker.post {
                    exec("cmd", "device_state", "state", "reset")
                    holding = false
                    post { evaluateNow() }
                }
            } else {
                evaluateNow()
            }
        }
    }

    /** Applies or releases right away, without waiting for the next hinge reading (a still phone sends none). */
    private fun evaluateNow() {
        val ctx = appContext ?: return
        val display = runCatching {
            ctx.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }.getOrNull()
        val coverLit = display != null && !display.isInnerPanel()
        onHinge(com.duoglass.core.hinge.HingeSensor.angle, coverLit)
    }

    /**
     * Asks Magisk/KernelSU for root by running a harmless command; on success
     * the hold runs through `su` from then on. Blocking: call off the main thread.
     */
    fun tryRoot(): Boolean {
        val ok = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd device_state print-state"))
            p.waitFor() == 0
        }.getOrDefault(false)
        GlassSettings.update { it.copy(holdViaRoot = ok) }
        post { refresh() }
        return ok
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_CODE) }
            .onFailure { Log.w(TAG, "permission request failed", it) }
    }

    /** Called with every hinge reading by the overlay service. */
    fun onHinge(angle: Float, coverLit: Boolean) {
        if (!angle.isFinite() || !startupReset) return
        val c = GlassSettings.value
        val enabled = c.coverHold && ready()
        FoldModel.swapOverride = if (enabled) c.coverHoldAngle else Float.NaN
        if (angle <= SHUT_DEG) wakeArmed = true
        when {
            holding && (!enabled || angle >= c.coverHoldAngle) -> release("hinge ${angle.toInt()}°")
            // Re-arm whenever the front screen is the one lit, comfortably below
            // the hold angle — not only when fully shut: a close that stops at
            // 10° and reopens should be held too. Shut, hold whatever the screen
            // is doing: a phone that went to sleep closed must be held when it opens.
            enabled && !holding && angle <= c.coverHoldAngle - REARM_MARGIN &&
                (coverLit || angle <= SHUT_DEG) -> apply()
            // While held the system sees a shut phone, so opening it wakes
            // nothing: from sleep the screens stay dark until the release, and a
            // charging phone's screensaver carries on onto the inner panel.
            // Wake it here instead, once per open (a no-op if already awake).
            holding && enabled && wakeArmed && angle >= WAKE_DEG -> {
                wakeArmed = false
                waker.post {
                    if (exec("input", "keyevent", "KEYCODE_WAKEUP")) {
                        post { FoldTrace.event("cover hold: wake at ${angle.toInt()}°") }
                    }
                }
            }
        }
    }

    /** Service stopping or feature turned off: never leave the hold behind. */
    fun releaseNow(reason: String) {
        if (!holding) return
        holding = false
        if (!ready()) return
        // Synchronous: the process may be about to go away.
        exec("cmd", "device_state", "state", "reset")
        FoldTrace.event("cover hold released ($reason)")
    }

    private fun apply() {
        holding = true
        _status.value = Status.Holding
        worker.post {
            if (exec("cmd", "device_state", "state", "0")) {
                post { FoldTrace.event("cover hold on: front screen stays lit until ${GlassSettings.value.coverHoldAngle.toInt()}°") }
            } else {
                holding = false
            }
        }
    }

    private fun release(reason: String) {
        holding = false
        _status.value = if (GlassSettings.value.coverHold) Status.Ready else Status.Off
        worker.post {
            if (ready() && exec("cmd", "device_state", "state", "reset")) {
                post { FoldTrace.event("cover hold released ($reason)") }
            }
        }
    }

    private val main = Handler(android.os.Looper.getMainLooper())
    private fun post(block: () -> Unit) = main.post(block)

    /**
     * Runs a command with Shizuku's identity. `Shizuku.newProcess` is private
     * in API 13 (it nudges apps toward UserService); for a one-line `cmd` call
     * it's the simplest correct tool, so it's reached reflectively.
     */
    private fun exec(vararg cmd: String): Boolean =
        if (shizukuGranted()) execShizuku(*cmd) else if (GlassSettings.value.holdViaRoot) execRoot(*cmd) else false

    private fun execRoot(vararg cmd: String): Boolean = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd.joinToString(" ")))
        val code = p.waitFor()
        if (code != 0) Log.w(TAG, "su ${cmd.joinToString(" ")} exited $code")
        code == 0
    }.onFailure { Log.e(TAG, "su failed", it) }.getOrDefault(false)

    private fun execShizuku(vararg cmd: String): Boolean = runCatching {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf(*cmd), null, null) as Process
        val code = p.waitFor()
        if (code != 0) Log.w(TAG, "${cmd.joinToString(" ")} exited $code: ${p.errorStream.bufferedReader().readText().take(200)}")
        code == 0
    }.onFailure { Log.e(TAG, "exec failed: ${cmd.joinToString(" ")}", it) }.getOrDefault(false)
}
