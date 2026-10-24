package com.duoglass.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.duoglass.core.fold.FoldLearning
import com.duoglass.core.fold.isLivePanel
import com.duoglass.core.hinge.HingeSensor
import com.duoglass.core.trace.FoldTrace
import com.duoglass.overlay.surface.LiveGlassRig
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * System-wide liquid-glass fold. An accessibility service may draw above every
 * window (launcher, lock screen, apps) and screenshot any display, which is
 * what lets the glass cover the whole phone rather than just the wallpaper.
 *
 * Runs one [PanelDirector] per lit built-in display. Phones that light one
 * panel at a time (Pixel Fold, OnePlus Open) run one director whose display
 * flips mode at the swap; phones that keep both lit run two side by side.
 */
class FoldService : AccessibilityService(), HingeSensor.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private lateinit var displayManager: DisplayManager
    private val directors = LinkedHashMap<Int, PanelDirector>()
    private val cache = SnapshotCache(maxAgeMs = 15 * 60_000L)
    /** Live-glass windows per display; they survive the Pixel Fold's display-0 blink at the swap. */
    private val rigs = HashMap<Int, LiveGlassRig>()
    private var receiverRegistered = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = sync()
        override fun onDisplayRemoved(displayId: Int) = sync()
        override fun onDisplayChanged(displayId: Int) = sync()
    }

    /**
     * `adb shell am broadcast -a com.duoglass.DEMO` plays the glass over whatever
     * is on screen; `--el hold 3000` holds full frost that long first, and
     * `--ef tilt 30` freezes the glass at that tilt for the hold (for tuning).
     */
    private val demoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) =
            playDemo(intent.getLongExtra("hold", 0L), intent.getFloatExtra("tilt", Float.NaN))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(displayListener, handler)
        if (!receiverRegistered) {
            registerReceiver(demoReceiver, IntentFilter(ACTION_DEMO), RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        HingeSensor.init(this)
        HingeSensor.addListener(this)
        watchFoldFeature()
        CoverHold.init(this)
        sync()
        OverlayState.attach(this)
        FoldTrace.event("service connected; hinge=${HingeSensor.activeSensor?.name ?: "none"}; displays=${directors.keys}")
    }

    override fun onDestroy() {
        CoverHold.releaseNow("service stopped")
        OverlayState.detach(this)
        HingeSensor.removeListener(this)
        if (receiverRegistered) unregisterReceiver(demoReceiver)
        if (::displayManager.isInitialized) displayManager.unregisterDisplayListener(displayListener)
        directors.values.forEach { it.destroy() }
        directors.clear()
        rigs.values.forEach { it.destroy() }
        rigs.clear()
        updateRunning()
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onHinge(angle: Float) {
        val main0 = directors[Display.DEFAULT_DISPLAY]
        CoverHold.onHinge(angle, coverLit = main0 != null && !main0.inner)
        sync()
        for (d in directors.values.toList()) d.onHinge(angle)
        val main = directors[Display.DEFAULT_DISPLAY] ?: directors.values.firstOrNull()
        FoldTrace.sample(angle, main?.displayedTilt ?: 0f, main?.inner == true)
    }

    /**
     * Keeps a window context on the default display listening for the fold
     * feature, so the crease orientation is learned even if the app is never
     * opened unfolded.
     */
    private fun watchFoldFeature() {
        val ctx = runCatching {
            createDisplayContext(displayManager.getDisplay(Display.DEFAULT_DISPLAY))
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        }.getOrNull() ?: return
        scope.launch {
            runCatching {
                WindowInfoTracker.getOrCreate(ctx).windowLayoutInfo(ctx).collect { info ->
                    val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() ?: return@collect
                    val wm = ctx.getSystemService(WindowManager::class.java)
                    val bounds = wm.maximumWindowMetrics.bounds
                    FoldLearning.learn(feature, bounds.width(), bounds.height(), ctx.display.rotation, source = "service")
                }
            }.onFailure { FoldTrace.event("fold feature unavailable to service: ${it.javaClass.simpleName}") }
        }
    }

    /** Starts directors for panels that lit, stops those that went dark, then lets each re-evaluate. */
    private fun sync() {
        // `adb shell settings put global duoglass_test_displays 1` lets an emulator's
        // simulated secondary display stand in for a second panel.
        val test = Settings.Global.getInt(contentResolver, TEST_DISPLAYS, 0) != 0
        val defaultName = runCatching { displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.name }.getOrNull()
        val live = displayManager.displays
            .filter { it.isLivePanel(defaultName, allowPresentation = test) }
            .associateBy { it.displayId }
        val gone = directors.keys.filter { it !in live }
        for (id in gone) directors.remove(id)?.destroy()
        // A display that's truly gone (not just blinking off) drops its windows.
        val present = displayManager.displays.map { it.displayId }.toSet()
        for (id in rigs.keys.filter { it !in present }) rigs.remove(id)?.destroy()
        for ((id, display) in live) {
            if (id !in directors) {
                directors[id] = PanelDirector(
                    service = this,
                    display = display,
                    handler = handler,
                    scope = scope,
                    innerLitElsewhere = { directors.any { (other, d) -> other != id && d.inner } },
                    onShowingChanged = ::updateRunning,
                    cache = cache,
                    rig = { ctx, wm -> rigs.getOrPut(id) { LiveGlassRig(ctx, wm) } },
                )
            }
        }
        if (gone.isNotEmpty()) updateRunning()
        for (d in directors.values.toList()) d.evaluate()
    }

    private fun updateRunning() = OverlayState.setRunning(directors.values.any { it.showing })

    /** Plays the glass on the display you're looking at. */
    fun playDemo(holdMs: Long = 0L, tilt: Float = Float.NaN) {
        sync()
        (directors[Display.DEFAULT_DISPLAY] ?: directors.values.firstOrNull())?.playDemo(holdMs, tilt)
    }

    companion object {
        const val ACTION_DEMO = "com.duoglass.DEMO"
        private const val TEST_DISPLAYS = "duoglass_test_displays"

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val self = ComponentName(context, FoldService::class.java)
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { s -> ComponentName(s.packageName, s.name) } == self }
        }
    }
}
