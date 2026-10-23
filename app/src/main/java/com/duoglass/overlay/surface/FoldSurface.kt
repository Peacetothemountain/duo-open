package com.duoglass.overlay.surface

import android.view.Gravity
import android.view.WindowManager

/**
 * Something on screen whose frost follows a pane tilt: a warped screenshot
 * ([SnapshotSurface]) or the system blur over the live screen ([LiveGlassSurface]).
 */
interface FoldSurface {
    val attached: Boolean
    var tilt: Float
    fun fadeIn(durationMs: Long)
    fun fadeOut(durationMs: Long, onEnd: () -> Unit)
    fun detach()
}

internal fun overlayParams(width: Int, height: Int, format: Int, extraFlags: Int = 0) =
    WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or extraFlags,
        format,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        title = "DuoGlassFold"
    }
