package com.duoglass.core.fold

import android.view.Display
import android.view.Surface
import com.duoglass.core.settings.GlassConfig
import kotlin.math.max
import kotlin.math.min

/** Where the hinge sits in a surface, and how the eye looks at it. */
data class FoldLine(
    /** True when the hinge is a vertical line (splits the x axis). */
    val splitsX: Boolean,
    /** Hinge position in px along the split axis. */
    val position: Float,
    /** Eye position in px along the split axis. */
    val eyePos: Float,
    /** Which side swings: -1 left/top, +1 right/bottom, 0 both. */
    val movingSide: Int,
)

/**
 * Fold placement for the current rotation.
 *
 * Every book-style foldable so far (OnePlus Open, Pixel Fold, Pixel 9/10 Pro
 * Fold, Galaxy Z Fold) has its crease running *vertically* through the inner
 * panel in that panel's natural orientation — whether the panel is natively
 * portrait (OnePlus, 2268×2440) or landscape (original Pixel Fold, 2208×1840).
 * So the axis is derived from the display rotation, not from "long or short
 * side" (which is what put the crease the wrong way on the Pixel Fold). Jetpack
 * WindowManager confirms it whenever a window spans the fold, and the user can
 * override it.
 *
 * Rotation convention (Display.getRotation): ROTATION_90 means the device was
 * turned 90° counter-clockwise, so the natural left edge is now at the bottom.
 */
object FoldGeometry {

    private fun odd(rotation: Int) = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

    /** The inner panel: hinge across the middle, one or both halves swinging. */
    fun inner(w: Float, h: Float, rotation: Int, config: GlassConfig): FoldLine {
        val verticalNatural = config.foldVerticalNatural
        val frac = config.learnedFoldCenter.coerceIn(0.3f, 0.7f)
        val splitsX = verticalNatural != odd(rotation)
        // Natural coordinate along the split axis -> current, per rotation.
        val pos = if (verticalNatural) {
            when (rotation) {
                Surface.ROTATION_90 -> h * (1f - frac)
                Surface.ROTATION_180 -> w * (1f - frac)
                Surface.ROTATION_270 -> h * frac
                else -> w * frac
            }
        } else {
            when (rotation) {
                Surface.ROTATION_90 -> w * frac
                Surface.ROTATION_180 -> h * (1f - frac)
                Surface.ROTATION_270 -> w * (1f - frac)
                else -> h * frac
            }
        }
        return FoldLine(splitsX, pos, pos, mapSide(config.movingSide, rotation, verticalNatural))
    }

    /**
     * The cover screen as one pane hinged at the spine edge, seen from its
     * centre. With [GlassConfig.coverFrostFromRight] the spine is the natural
     * left edge (true on Pixel Fold and OnePlus Open), so frost is heaviest at
     * the right edge and grows inward.
     */
    fun cover(w: Float, h: Float, rotation: Int, config: GlassConfig): FoldLine {
        // Natural edge index: 0 left, 1 top, 2 right, 3 bottom. Rotating CCW by
        // 90° moves left -> bottom -> right -> top.
        val natural = if (config.coverFrostFromRight) 0 else 2
        val steps = when (rotation) {
            Surface.ROTATION_90 -> 1
            Surface.ROTATION_180 -> 2
            Surface.ROTATION_270 -> 3
            else -> 0
        }
        // left(0) -> bottom(3) -> right(2) -> top(1) as rotation steps increase.
        val order = intArrayOf(0, 3, 2, 1)
        val start = order.indexOf(natural)
        return when (order[(start + steps) % 4]) {
            0 -> FoldLine(true, 0f, w * 0.5f, +1)
            2 -> FoldLine(true, w, w * 0.5f, -1)
            1 -> FoldLine(false, 0f, h * 0.5f, +1)
            else -> FoldLine(false, h, h * 0.5f, -1)
        }
    }

    fun forPanel(inner: Boolean, w: Float, h: Float, rotation: Int, config: GlassConfig): FoldLine =
        if (inner) inner(w, h, rotation, config) else cover(w, h, rotation, config)

    /** Natural-orientation moving side -> current side after rotation. */
    private fun mapSide(side: Int, rotation: Int, verticalNatural: Boolean): Int {
        if (side == 0) return 0
        val flips = if (verticalNatural) {
            rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_180
        } else {
            rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270
        }
        return if (flips) -side else side
    }

    /**
     * Converts a FoldingFeature seen in the current rotation back to natural
     * orientation: (vertical at rotation 0, centre fraction along its axis).
     */
    fun learn(verticalNow: Boolean, centerNow: Float, w: Float, h: Float, rotation: Int): Pair<Boolean, Float> {
        val verticalNatural = verticalNow != odd(rotation)
        val fracNow = (if (verticalNow) centerNow / w else centerNow / h).coerceIn(0f, 1f)
        val frac = if (verticalNatural) {
            when (rotation) {
                Surface.ROTATION_90, Surface.ROTATION_180 -> 1f - fracNow
                else -> fracNow
            }
        } else {
            when (rotation) {
                Surface.ROTATION_180, Surface.ROTATION_270 -> 1f - fracNow
                else -> fracNow
            }
        }
        return verticalNatural to frac
    }
}

/** Book-style inner panels are near-square; cover screens are tall. */
private const val INNER_MAX_ASPECT = 1.45f

/**
 * True when this display is driving the inner (unfolded) panel. Uses the
 * physical mode, which flips the instant the phone swaps panels on display 0
 * (before any window has resized).
 */
fun Display?.isInnerPanel(): Boolean {
    val mode = runCatching { this?.mode }.getOrNull() ?: return false
    val a = mode.physicalWidth.toFloat()
    val b = mode.physicalHeight.toFloat()
    if (a <= 0f || b <= 0f) return false
    return max(a, b) / min(a, b) < INNER_MAX_ASPECT
}

/**
 * A built-in panel that is lit: the default display, or another non-virtual
 * display in any state but off (doze counts: the cover plays the effect from
 * its always-on clock). Samsung exposes the inner panel as a second logical
 * display flagged PRESENTATION but sharing display 0's name.
 */
fun Display.isLivePanel(defaultName: String?, allowPresentation: Boolean): Boolean {
    if (!isValid) return false
    val rejected = if (allowPresentation) Display.FLAG_PRIVATE else Display.FLAG_PRESENTATION or Display.FLAG_PRIVATE
    val sameName = defaultName != null && name == defaultName && (flags and Display.FLAG_PRIVATE) == 0
    val builtIn = displayId == Display.DEFAULT_DISPLAY || sameName || (flags and rejected) == 0
    return builtIn && state != Display.STATE_OFF && state != Display.STATE_UNKNOWN
}
