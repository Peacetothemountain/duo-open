package com.duoglass.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.duoglass.core.fold.FoldGeometry
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.fold.SpringFollower
import com.duoglass.core.fold.isInnerPanel
import com.duoglass.core.hinge.HingeSensor
import com.duoglass.core.render.FoldShader
import com.duoglass.core.settings.GlassConfig
import com.duoglass.core.settings.GlassSettings
import com.duoglass.overlay.OverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live wallpaper that plays the glass fold on the home and lock screen,
 * without an accessibility service (icons stay sharp; only the wallpaper
 * folds). Draws only on hinge movement and surface changes, never idle.
 * Steps aside while the whole-screen overlay is showing.
 */
class GlassWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = GlassEngine()

    private inner class GlassEngine : Engine(), HingeSensor.Listener {
        private val scope = MainScope()
        private val ctx = this@GlassWallpaperService
        private val shader: RuntimeShader? = FoldShader.create(ctx)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val follower = SpringFollower { draw() }

        private var config: GlassConfig = GlassSettings.value
        private var bitmap: Bitmap? = null
        private var bitmapVersion = -1L
        private var image: BitmapShader? = null
        private var pxPerMm = FoldShader.pxPerMm(ctx)
        private var ready = false
        private var width = 0f
        private var height = 0f

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            displayContext?.let { pxPerMm = FoldShader.pxPerMm(it) }
            // Listen for the engine's whole life: some hinge HALs send nothing on
            // registration, so a listener started at unfold time misses fast opens.
            HingeSensor.init(ctx)
            HingeSensor.addListener(this)
            scope.launch {
                GlassSettings.config.collect { c ->
                    config = c
                    if (c.imageVersion != bitmapVersion) loadImage(c.imageVersion)
                    follower.snap(tilt(HingeSensor.angle))
                    draw()
                }
            }
            scope.launch { OverlayState.running.collect { draw() } }
        }

        override fun onDestroy() {
            HingeSensor.removeListener(this)
            follower.cancel()
            scope.cancel()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                follower.snap(follower.target)
                draw()
            } else {
                follower.cancel()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            ready = true
            width = w.toFloat()
            height = h.toFloat()
            displayContext?.let { pxPerMm = FoldShader.pxPerMm(it) }
            rebuildImage()
            follower.snap(follower.target)
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            ready = false
            follower.cancel()
            super.onSurfaceDestroyed(holder)
        }

        override fun onHinge(angle: Float) {
            val t = tilt(angle)
            if (inner() && isVisible && ready) follower.setTarget(t) else follower.snap(t)
        }

        private fun tilt(angle: Float) = FoldModel.innerTilt(angle, config)
        private fun inner(): Boolean = displayContext?.display.isInnerPanel()

        private fun loadImage(version: Long) {
            bitmapVersion = version
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { WallpaperImage.load(ctx, version) }
                if (version != bitmapVersion) return@launch
                bitmap = bmp
                rebuildImage()
                draw()
            }
        }

        private fun rebuildImage() {
            val bmp = bitmap ?: return
            if (width <= 0f || height <= 0f) return
            val (scale, dx, dy) = WallpaperImage.centerCrop(bmp.width, bmp.height, width, height)
            image = BitmapShader(bmp, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
                setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(dx, dy)
                })
            }
        }

        private fun draw() {
            if (!ready) return
            val holder = surfaceHolder
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                Log.w("GlassWallpaper", "lockHardwareCanvas failed", e)
                null
            } ?: return
            try {
                canvas.drawColor(Color.BLACK)
                val img = image ?: return
                val s = shader
                val t = if (inner() && !OverlayState.running.value) follower.value else 0f
                if (s == null || t < FoldModel.FLAT_EPSILON) {
                    paint.shader = img
                } else {
                    val rotation = displayContext?.display?.rotation ?: 0
                    val fold = FoldGeometry.inner(width, height, rotation, config)
                    FoldShader.setUniforms(s, width, height, t, config, pxPerMm, fold)
                    s.setInputShader("content", img)
                    paint.shader = s
                }
                canvas.drawRect(0f, 0f, width, height, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
