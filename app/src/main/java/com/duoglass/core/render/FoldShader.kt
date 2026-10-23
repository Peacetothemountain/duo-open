package com.duoglass.core.render

import android.content.Context
import android.graphics.RuntimeShader
import android.util.Log
import com.duoglass.R
import com.duoglass.core.fold.FoldLine
import com.duoglass.core.settings.GlassConfig

/** Glue for res/raw/fold_glass.agsl, shared by the app preview, wallpaper and overlay. */
object FoldShader {
    private const val TAG = "GlassShader"

    /** The darkening setting is authored at this density. */
    const val REFERENCE_PX_PER_MM = 6f

    @Volatile
    private var source: String? = null

    fun source(context: Context): String =
        source ?: context.resources.openRawResource(R.raw.fold_glass)
            .bufferedReader().use { it.readText() }
            .also { source = it }

    fun create(context: Context): RuntimeShader? = try {
        RuntimeShader(source(context))
    } catch (e: Exception) {
        Log.e(TAG, "AGSL compile failed: ${e.message}", e)
        null
    }

    fun pxPerMm(context: Context): Float {
        val xdpi = context.resources.displayMetrics.xdpi
        return if (xdpi.isFinite() && xdpi > 0f) xdpi / 25.4f else REFERENCE_PX_PER_MM
    }

    /**
     * @param pxPerMm Density of the surface being drawn (divide by the render
     *   scale when drawing at reduced resolution).
     */
    fun setUniforms(
        shader: RuntimeShader,
        width: Float,
        height: Float,
        tilt: Float,
        config: GlassConfig,
        pxPerMm: Float,
        fold: FoldLine,
    ) {
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("tiltDegrees", tilt)
        shader.setFloatUniform("eyeDistancePx", config.eyeDistanceMm * pxPerMm)
        shader.setFloatUniform("hingePos", fold.position)
        shader.setFloatUniform("eyeX", fold.eyePos)
        shader.setFloatUniform("axisSwap", if (fold.splitsX) 0f else 1f)
        shader.setFloatUniform("paneSide", fold.movingSide.toFloat())
        shader.setFloatUniform("blurSpread", config.frost)
        // Blur radius is in device px; renormalize so dense panels don't crush to black.
        shader.setFloatUniform("darkening", config.darkening * REFERENCE_PX_PER_MM / pxPerMm)
        shader.setFloatUniform("sheen", config.sheen)
        shader.setFloatUniform("refraction", config.refraction)
        shader.setFloatUniform("pxPerMm", pxPerMm)
    }
}
