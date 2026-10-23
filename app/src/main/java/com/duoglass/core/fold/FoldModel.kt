package com.duoglass.core.fold

import com.duoglass.core.settings.GlassConfig
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hinge angle -> glass pane tilt, per panel.
 *
 * Opening a phone that lights one panel at a time is two phases: the cover
 * frosts in from shut until the swap, then the inner panel starts frosted and
 * clears as it reaches flat. Where the swap happens differs per phone (and per
 * speed), so it is learned from real opens ([GlassConfig.learnedSwapAngle])
 * instead of hard-coded: the cover then always reaches full frost exactly as
 * it switches off, and the inner panel always starts from full frost.
 */
object FoldModel {
    /** Pane tilt cap; the frost is saturated beyond this. */
    const val MAX_TILT = 45f

    /** Tilts below this draw the plain picture. */
    const val FLAT_EPSILON = 0.05f

    /**
     * Treated as fully open. Real hinges rest short of 180° (OnePlus Open
     * 176–179°, Pixel Fold 178–180°), which would otherwise leave a permanent
     * faint frost.
     */
    const val FLAT_HINGE = 172f

    /** Used until this phone's swap angle has been observed. */
    const val DEFAULT_SWAP = 20f

    fun swapAngle(config: GlassConfig): Float =
        config.learnedSwapAngle.takeIf { it.isFinite() }?.coerceIn(4f, 70f) ?: DEFAULT_SWAP

    /** A shut phone idles a few degrees open; below this the cover is at rest. */
    fun closedAngle(config: GlassConfig): Float = (swapAngle(config) * 0.3f).coerceIn(1.5f, 6f)

    /** 0 at flat → 1 at the swap. */
    fun innerProgress(angle: Float, config: GlassConfig): Float {
        val swap = swapAngle(config)
        return ((FLAT_HINGE - angle) / (FLAT_HINGE - swap)).coerceIn(0f, 1f)
    }

    /** 0 at shut → 1 at the swap. */
    fun coverProgress(angle: Float, config: GlassConfig): Float {
        val closed = closedAngle(config)
        val swap = swapAngle(config)
        return ((angle - closed) / (swap - closed).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    /**
     * A cover that stays lit with the inner panel (dual-screen phones) has no
     * swap to hand over to: it peaks mid-fold and clears again by itself.
     */
    fun concurrentCoverProgress(angle: Float, config: GlassConfig): Float {
        val closed = closedAngle(config)
        val p = ((angle - closed) / (FLAT_HINGE - closed)).coerceIn(0f, 1f)
        return sin(PI * p).toFloat()
    }

    /**
     * Progress -> tilt. A gentle ease-in keeps the last stretch before flat
     * almost clear, so the picture "lands" instead of lingering frosted.
     */
    fun tilt(progress: Float, config: GlassConfig, bothHalves: Boolean = config.movingSide == 0): Float {
        val p = progress.coerceIn(0f, 1f)
        val eased = p * (0.55f + 0.45f * p)
        val full = if (bothHalves) MAX_TILT * 0.6f else MAX_TILT
        return (eased * full * config.intensity).coerceIn(0f, MAX_TILT)
    }

    fun innerTilt(angle: Float, config: GlassConfig): Float =
        if (angle.isNaN()) 0f else tilt(innerProgress(angle, config), config)

    fun coverTilt(angle: Float, config: GlassConfig): Float =
        if (angle.isNaN()) 0f else tilt(coverProgress(angle, config), config, bothHalves = false)

    fun concurrentCoverTilt(angle: Float, config: GlassConfig): Float =
        if (angle.isNaN()) 0f else tilt(concurrentCoverProgress(angle, config), config, bothHalves = false)

    /** Full frost for the current look, used for timed eases. */
    fun peakTilt(config: GlassConfig): Float = tilt(1f, config, bothHalves = false)
}
