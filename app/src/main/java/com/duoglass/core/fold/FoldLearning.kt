package com.duoglass.core.fold

import androidx.window.layout.FoldingFeature
import com.duoglass.core.settings.GlassSettings
import com.duoglass.core.trace.FoldTrace
import kotlin.math.abs

/** Records where the crease is, from Jetpack WindowManager, in natural orientation. */
object FoldLearning {
    fun learn(feature: FoldingFeature, width: Int, height: Int, rotation: Int, source: String) {
        if (width <= 0 || height <= 0) return
        val verticalNow = feature.orientation == FoldingFeature.Orientation.VERTICAL
        val center = if (verticalNow) feature.bounds.exactCenterX() else feature.bounds.exactCenterY()
        val (vertical, frac) = FoldGeometry.learn(verticalNow, center, width.toFloat(), height.toFloat(), rotation)
        val c = GlassSettings.value
        if (c.learnedFoldVertical == vertical && abs(c.learnedFoldCenter - frac) < 0.005f) return
        GlassSettings.update { it.copy(learnedFoldVertical = vertical, learnedFoldCenter = frac) }
        FoldTrace.event("learned crease ($source): ${if (vertical) "vertical" else "horizontal"} at ${"%.3f".format(frac)} (natural)")
    }
}
