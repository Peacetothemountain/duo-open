package com.duoglass.overlay.surface

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import com.duoglass.core.fold.FoldLine
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.render.FoldShader
import com.duoglass.core.settings.GlassConfig
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The windows behind live glass, created once per display and kept hidden
 * between folds — creating a dozen windows takes a few hundred ms, which is
 * most of a fold, while showing existing ones is one relayout each.
 *
 * Each strip uses *background* blur ([android.view.Window.setBackgroundBlurRadius]),
 * which blurs only inside that window's bounds and updates with a
 * SurfaceControl transaction. `FLAG_BLUR_BEHIND` can't do this: WindowManager
 * implements it with one display-wide dim layer that takes a single radius
 * from the topmost blurring window, so a gradient of strips collapses into one
 * uniform blur over the whole screen, the still half included. Background blur
 * needs a real Window, hence one bare Dialog-backed overlay window per strip.
 *
 * The strips are *nested*: strip i covers from its band's inner edge all the
 * way out to the screen edge and adds only the blur the strips inside it
 * haven't (blurs compose roughly in quadrature). The picture under each band
 * boundary is then continuous, so the bands don't show as hard seams, and no
 * single window needs a big radius (SurfaceFlinger drops per-window background
 * blur above ~80 px).
 */
class LiveGlassRig(private val context: Context, private val windowManager: WindowManager) {

    internal class Strip(val dialog: Dialog) {
        var dMid = 0f
        var dPrev = -1f
        var radius = -1
        var used = false
    }

    internal val strips = ArrayList<Strip>()
    internal var sheen: SheenView? = null
    /** The fold currently using the windows; an older one's late fade-out mustn't hide them. */
    private var owner: LiveGlassSurface? = null
    var broken = false
        private set

    /** Creates the windows now (hidden), so the first fold doesn't pay for it. */
    fun warm() {
        if (broken) return
        while (strips.size < STRIPS) {
            val d = blurWindow() ?: run { broken = true; return }
            strips += Strip(d)
        }
        if (sheen == null) {
            val v = SheenView(context).apply { visibility = View.GONE }
            val ok = runCatching {
                windowManager.addView(v, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, PixelFormat.TRANSLUCENT))
            }.onFailure { Log.e(TAG, "sheen addView failed", it) }.isSuccess
            if (ok) sheen = v else broken = true
        }
    }

    /** Lays the strips out for [fold] and shows them; null if the windows couldn't be made. */
    fun acquire(config: GlassConfig, pxPerMm: Float, fold: FoldLine): LiveGlassSurface? {
        warm()
        if (broken) return null
        val b = windowManager.maximumWindowMetrics.bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val len = if (fold.splitsX) w else h
        val panes = buildList {
            if (fold.movingSide <= 0 && fold.position > 1f) add(fold.position to 0f)
            if (fold.movingSide >= 0 && len - fold.position > 1f) add(fold.position to len)
        }
        val n = if (panes.size == 2) STRIPS / 2 else STRIPS
        var k = 0
        for ((crease, far) in panes) {
            var prev = -1f
            for (i in 0 until n) {
                val f0 = band(i, n)
                val f1 = band(i + 1, n)
                val start = crease + (far - crease) * f0
                val lo = minOf(start, far)
                val hi = maxOf(start, far)
                val s = strips[k++]
                s.dMid = abs(far - crease) * (f0 + f1) * 0.5f
                s.dPrev = prev
                s.radius = -1
                s.used = true
                prev = s.dMid
                place(
                    s.dialog,
                    x = if (fold.splitsX) lo.toInt() else 0,
                    y = if (fold.splitsX) 0 else lo.toInt(),
                    width = if (fold.splitsX) ceil(hi - lo).toInt() else w.toInt(),
                    height = if (fold.splitsX) h.toInt() else ceil(hi - lo).toInt(),
                )
            }
        }
        for (i in k until strips.size) strips[i].used = false
        for (s in strips) {
            s.dialog.window?.setBackgroundBlurRadius(0)
            if (s.used) s.dialog.window?.decorView?.visibility = View.VISIBLE
        }
        val sv = sheen ?: return null
        sv.configure(fold, config, pxPerMm)
        sv.visibility = View.VISIBLE
        owner?.abandon()
        return LiveGlassSurface(this, config).also { owner = it }
    }

    internal fun release(by: LiveGlassSurface) {
        if (owner !== by) return
        owner = null
        for (s in strips) {
            s.dialog.window?.setBackgroundBlurRadius(0)
            s.dialog.window?.decorView?.visibility = View.GONE
            s.used = false
        }
        sheen?.let {
            it.set(0f, 0f)
            it.visibility = View.GONE
        }
    }

    fun destroy() {
        for (s in strips) runCatching { s.dialog.dismiss() }
        strips.clear()
        sheen?.let { v -> runCatching { windowManager.removeViewImmediate(v) } }
        sheen = null
    }

    private fun place(d: Dialog, x: Int, y: Int, width: Int, height: Int) {
        val win = d.window ?: return
        val a = win.attributes
        if (a.x == x && a.y == y && a.width == width && a.height == height) return
        // getAttributes() is the window's live object: mutating it and setting
        // it back diffs it against itself and never reaches WindowManager.
        win.attributes = WindowManager.LayoutParams().apply {
            copyFrom(a)
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }
    }

    /** A bare, untouchable overlay window whose only job is to blur what's behind its bounds. */
    private fun blurWindow(): Dialog? = runCatching {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        val win = dialog.window ?: return null
        win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        win.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        win.setDimAmount(0f)
        win.attributes = win.attributes.apply {
            width = 1
            height = 1
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            windowAnimations = 0
            title = "DuoGlassBlur"
        }
        win.decorView.setPadding(0, 0, 0, 0)
        dialog.setCancelable(false)
        dialog.show()
        win.setBackgroundBlurRadius(0)
        win.decorView.visibility = View.GONE
        dialog
    }.onFailure { Log.e(TAG, "blur window failed", it) }.getOrNull()

    /**
     * Darkening (same law as the shader: blur ∝ gap, light lost ∝ blur), rim
     * light at the moving pane's outer edge and a soft crease light.
     */
    internal class SheenView(context: Context) : View(context) {
        private val dark = Paint()
        private val rim = Paint()
        private val crease = Paint()
        private var fold: FoldLine? = null
        private var config: GlassConfig? = null
        private var pxPerMm = 6f
        private var sinTilt = 0f
        private var tiltN = 0f

        fun configure(fold: FoldLine, config: GlassConfig, pxPerMm: Float) {
            this.fold = fold
            this.config = config
            this.pxPerMm = pxPerMm
            invalidate()
        }

        fun set(sinTilt: Float, tiltN: Float) {
            if (abs(sinTilt - this.sinTilt) < 0.002f && abs(tiltN - this.tiltN) < 0.002f) return
            this.sinTilt = sinTilt
            this.tiltN = tiltN
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val fold = fold ?: return
            val config = config ?: return
            if (sinTilt <= 0f) return
            val w = width.toFloat()
            val h = height.toFloat()
            val len = if (fold.splitsX) w else h
            val darkPerPx = config.darkening * FoldShader.REFERENCE_PX_PER_MM / pxPerMm
            val sides = buildList {
                if (fold.movingSide <= 0) add(-1)
                if (fold.movingSide >= 0) add(+1)
            }
            for (side in sides) {
                val edge = if (side < 0) 0f else len
                val span = abs(edge - fold.position)
                if (span < 1f) continue
                val farAlpha = (darkPerPx * config.frost * span * sinTilt).coerceIn(0f, MAX_DARK)
                dark.shader = gradient(fold, fold.position, edge, Color.TRANSPARENT, Color.argb(alpha(farAlpha), 0, 0, 0))
                rect(canvas, fold, fold.position, edge, dark)

                val light = config.sheen * tiltN
                val rimW = 1.6f * pxPerMm
                val rimStart = edge - side * rimW
                rim.shader = gradient(fold, rimStart, edge, Color.TRANSPARENT, Color.argb(alpha(light * 0.5f), 255, 255, 255))
                rect(canvas, fold, rimStart, edge, rim)

                val creaseW = 8f * pxPerMm
                val creaseEnd = fold.position + side * creaseW
                crease.shader = gradient(fold, fold.position, creaseEnd, Color.argb(alpha(light * 0.3f), 255, 255, 255), Color.TRANSPARENT)
                rect(canvas, fold, fold.position, creaseEnd, crease)
            }
        }

        private fun alpha(a: Float) = (a.coerceIn(0f, 1f) * 255).roundToInt()

        private fun gradient(fold: FoldLine, from: Float, to: Float, c0: Int, c1: Int): Shader =
            if (fold.splitsX) LinearGradient(from, 0f, to, 0f, c0, c1, Shader.TileMode.CLAMP)
            else LinearGradient(0f, from, 0f, to, c0, c1, Shader.TileMode.CLAMP)

        private fun rect(canvas: Canvas, fold: FoldLine, a: Float, b: Float, paint: Paint) {
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            if (fold.splitsX) canvas.drawRect(lo, 0f, hi, height.toFloat(), paint)
            else canvas.drawRect(0f, lo, width.toFloat(), hi, paint)
        }
    }

    companion object {
        private const val TAG = "GlassOverlay"
        /** Strips across one moving pane (half each when both halves move). */
        const val STRIPS = 10
        /** Largest radius any single window asks for; see the class comment. */
        const val MAX_STEP_PX = 64f
        const val MAX_DARK = 0.85f

        /**
         * Band edges as a fraction of the pane, 0 at the crease. Denser near the
         * crease: a step from sharp to 6 px shows, one from 50 to 60 px doesn't.
         */
        fun band(i: Int, n: Int): Float {
            val u = i.toFloat() / n
            return u * (0.55f + 0.45f * u)
        }
    }
}

/** One fold's use of a [LiveGlassRig]; detaching hides the windows for next time. */
class LiveGlassSurface internal constructor(
    private val rig: LiveGlassRig,
    private val config: GlassConfig,
) : FoldSurface {

    private var fade: ValueAnimator? = null
    private var fadeScale = 1f
    private var released = false
    override val attached: Boolean = true

    override var tilt: Float = 0f
        set(value) {
            field = value
            apply()
        }

    private fun apply() {
        if (released) return
        val t = tilt.coerceIn(0f, FoldModel.MAX_TILT)
        val s = sin(Math.toRadians(t.toDouble())).toFloat() * fadeScale
        for (strip in rig.strips) {
            if (!strip.used) continue
            val target = config.frost * strip.dMid * s
            val inner = if (strip.dPrev < 0f) 0f else config.frost * strip.dPrev * s
            val r = sqrt(max(target * target - inner * inner, 0f)).coerceIn(0f, LiveGlassRig.MAX_STEP_PX)
            val q = r.roundToInt()
            if (q != strip.radius) {
                strip.radius = q
                strip.dialog.window?.setBackgroundBlurRadius(q)
            }
        }
        rig.sheen?.set(s, t / FoldModel.MAX_TILT * fadeScale)
    }

    override fun fadeIn(durationMs: Long) = animate(0f, 1f, durationMs, null)

    override fun fadeOut(durationMs: Long, onEnd: () -> Unit) = animate(fadeScale, 0f, durationMs, onEnd)

    private fun animate(from: Float, to: Float, durationMs: Long, onEnd: (() -> Unit)?) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
            addUpdateListener {
                fadeScale = it.animatedValue as Float
                apply()
            }
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = onEnd()
                })
            }
            start()
        }
    }

    override fun detach() {
        if (released) return
        released = true
        fade?.cancel()
        rig.release(this)
    }

    /** Another fold took the windows over: stop touching them. */
    internal fun abandon() {
        released = true
        fade?.cancel()
    }
}
