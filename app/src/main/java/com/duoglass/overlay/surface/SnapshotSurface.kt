package com.duoglass.overlay.surface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import com.duoglass.core.fold.FoldLine
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.render.FoldShader
import com.duoglass.core.settings.GlassConfig
import kotlin.math.ceil

/**
 * One screenshot bent through the fold shader: smooth frost, darkening,
 * perspective, rim lensing and edge light per pixel. Frozen while it plays.
 */
class SnapshotSurface(
    context: Context,
    private val windowManager: WindowManager,
    bitmap: Bitmap,
    config: GlassConfig,
    fold: FoldLine,
) : FoldSurface {

    private val view = SnapshotView(context, bitmap, config, fold)
    override val attached: Boolean = runCatching {
        windowManager.addView(
            view,
            overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, PixelFormat.OPAQUE),
        )
    }.onFailure { Log.e("GlassOverlay", "addView failed", it) }.isSuccess

    override var tilt: Float
        get() = view.tilt
        set(value) {
            view.tilt = value
        }

    /** Swap a bridging picture for the fresh capture, keeping the tilt. */
    fun replace(bitmap: Bitmap) = view.setSnapshot(bitmap)

    override fun fadeIn(durationMs: Long) {
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(durationMs).start()
    }

    override fun fadeOut(durationMs: Long, onEnd: () -> Unit) {
        view.animate().alpha(0f).setDuration(durationMs)
            .setInterpolator(PathInterpolator(0.25f, 0.1f, 0.25f, 1f))
            .withEndAction(onEnd).start()
    }

    override fun detach() {
        runCatching { windowManager.removeViewImmediate(view) }
    }
}

/**
 * The shader runs per output pixel with up to 28 taps — too much for a 4 MP
 * panel at 120 Hz — so the shaded layer is laid out at 1/[RENDER_SCALE] size
 * and scaled back up (the frost hides it). At tilt 0 the plain snapshot is
 * drawn 1:1 so the hand-over to the live screen is pixel-identical.
 */
private class SnapshotView(
    context: Context,
    snapshot: Bitmap,
    config: GlassConfig,
    fold: FoldLine,
) : ViewGroup(context) {

    private val shaded = ShadedView(context, snapshot, config, fold)
    private val flat = FlatView(context, snapshot)

    var tilt: Float
        get() = shaded.tilt
        set(value) {
            shaded.tilt = value
            val on = value >= FoldModel.FLAT_EPSILON
            shaded.visibility = if (on) VISIBLE else INVISIBLE
            flat.visibility = if (on) INVISIBLE else VISIBLE
        }

    fun setSnapshot(bitmap: Bitmap) {
        shaded.setSnapshot(bitmap)
        flat.setSnapshot(bitmap)
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(flat)
        addView(shaded)
        shaded.pivotX = 0f
        shaded.pivotY = 0f
        shaded.scaleX = RENDER_SCALE
        shaded.scaleY = RENDER_SCALE
        shaded.setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        flat.measure(exactly(w), exactly(h))
        shaded.measure(exactly(ceil(w / RENDER_SCALE).toInt()), exactly(ceil(h / RENDER_SCALE).toInt()))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        flat.layout(0, 0, r - l, b - t)
        shaded.layout(0, 0, shaded.measuredWidth, shaded.measuredHeight)
    }

    private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(px, MeasureSpec.EXACTLY)

    private class FlatView(context: Context, private var snapshot: Bitmap) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val matrix = Matrix()

        fun setSnapshot(bitmap: Bitmap) {
            snapshot = bitmap
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            matrix.setScale(width / snapshot.width.toFloat(), height / snapshot.height.toFloat())
            canvas.drawBitmap(snapshot, matrix, paint)
        }
    }

    private class ShadedView(
        context: Context,
        private var snapshot: Bitmap,
        private val config: GlassConfig,
        private val foldFull: FoldLine,
    ) : View(context) {
        private val shader: RuntimeShader? = FoldShader.create(context)
        private val pxPerMm = FoldShader.pxPerMm(context) / RENDER_SCALE
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var image = makeImage(snapshot)
        private val matrix = Matrix()

        // Geometry was computed at full size; this view is RENDER_SCALE smaller.
        private val fold = foldFull.copy(position = foldFull.position / RENDER_SCALE, eyePos = foldFull.eyePos / RENDER_SCALE)

        var tilt = 0f
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }

        private fun makeImage(b: Bitmap) = BitmapShader(b, Shader.TileMode.DECAL, Shader.TileMode.DECAL)

        fun setSnapshot(bitmap: Bitmap) {
            snapshot = bitmap
            image = makeImage(bitmap)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 1f || h <= 1f) return
            matrix.setScale(w / snapshot.width, h / snapshot.height)
            image.setLocalMatrix(matrix)
            val s = shader
            if (s == null) {
                paint.shader = image
            } else {
                FoldShader.setUniforms(s, w, h, tilt, config, pxPerMm, fold)
                s.setInputShader("content", image)
                paint.shader = s
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    companion object {
        const val RENDER_SCALE = 2f
    }
}
