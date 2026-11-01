package com.duoglass.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the whole-screen fold is drawn. */
enum class Engine {
    /** Live glass where the system allows cross-window blur, snapshot otherwise. */
    AUTO,

    /** SurfaceFlinger blur over the live screen: instant, content keeps moving. */
    LIVE,

    /** A screenshot bent through the shader: perspective, refraction, smooth frost. */
    SNAPSHOT,
}

/** Fold-line orientation in the inner panel's natural (rotation 0) orientation. */
enum class AxisMode { AUTO, VERTICAL, HORIZONTAL }

/** Look presets; each sets several tuning values at once. */
enum class Preset(
    val title: String,
    val intensity: Float,
    val frost: Float,
    val darkening: Float,
    val sheen: Float,
    val refraction: Float,
) {
    LIQUID("Liquid", 1.0f, 0.12f, 0.012f, 0.85f, 0.8f),
    FROST("Frost", 1.15f, 0.16f, 0.016f, 0.45f, 0.35f),
    CLEAR("Clear", 0.8f, 0.07f, 0.006f, 1.0f, 1.0f),
    NOIR("Noir", 1.1f, 0.13f, 0.026f, 0.35f, 0.5f),
}

/**
 * Everything the app, wallpaper and overlay share.
 *
 * @param intensity Multiplier on the pane tilt (1 = physically faithful).
 * @param frost Blur radius gained per px of glass-to-screen separation.
 * @param darkening Light lost per px of blur radius, at 6 px/mm (renormalized per display).
 * @param eyeDistanceMm Viewer distance from the flat screen plane (perspective).
 * @param sheen Specular highlight strength along the glass edges, 0..1.
 * @param refraction Lens bend at the moving pane's far edge, 0..1.
 * @param movingSide Which half swings: -1 left/top, +1 right/bottom, 0 both.
 * @param coverFrostFromRight Cover screen: the spine is on the left, frost grows from the right.
 * @param engine Whole-screen drawing engine.
 * @param instantStart Bridge a freshly lit panel with its last picture while the capture lands.
 * @param axisMode Fold orientation override; AUTO uses [learnedFoldVertical].
 * @param learnedFoldVertical From Jetpack WindowManager: fold is vertical at rotation 0.
 * @param learnedFoldCenter Fold position along its axis as a 0..1 fraction (rotation 0).
 * @param learnedSwapAngle Hinge angle at which this phone swaps panels (learned per open).
 * @param learnedCloseSwapAngle Where a close swaps back to the cover, learned while [coverHold] is on
 *   (opens then swap at [coverHoldAngle], so only closes show the system's own angle).
 * @param haptics Light ticks on controls and at the panel swap preview.
 * @param coverHold Keep the cover screen lit until [coverHoldAngle] (shell/root device-state hold).
 * @param coverHoldAngle Hinge angle at which the inner screen takes over while [coverHold] is on.
 * @param holdViaRoot Run the hold through `su` rather than Shizuku.
 * @param glassTint App appearance, like iOS 27's Liquid Glass slider: 0 = Ultra Clear,
 *   1 = Fully Tinted, 0.5 = Apple's default midpoint.
 * @param imageVersion Bumped whenever the wallpaper image changes.
 */
data class GlassConfig(
    val intensity: Float = Preset.LIQUID.intensity,
    val frost: Float = Preset.LIQUID.frost,
    val darkening: Float = Preset.LIQUID.darkening,
    val eyeDistanceMm: Float = 420f,
    val sheen: Float = Preset.LIQUID.sheen,
    val refraction: Float = Preset.LIQUID.refraction,
    val preset: Preset? = Preset.LIQUID,
    val movingSide: Int = -1,
    val coverFrostFromRight: Boolean = true,
    val engine: Engine = Engine.AUTO,
    val instantStart: Boolean = true,
    val axisMode: AxisMode = AxisMode.AUTO,
    val learnedFoldVertical: Boolean = true,
    val learnedFoldCenter: Float = 0.5f,
    val learnedSwapAngle: Float = Float.NaN,
    val swapSamples: Int = 0,
    val learnedCloseSwapAngle: Float = Float.NaN,
    val closeSwapSamples: Int = 0,
    val haptics: Boolean = true,
    val glassTint: Float = 0.5f,
    val coverHold: Boolean = false,
    val coverHoldAngle: Float = 90f,
    val holdViaRoot: Boolean = false,
    val onboarded: Boolean = false,
    val imageVersion: Long = 0L,
) {
    /** Fold orientation at rotation 0 after the override. */
    val foldVerticalNatural: Boolean
        get() = when (axisMode) {
            AxisMode.AUTO -> learnedFoldVertical
            AxisMode.VERTICAL -> true
            AxisMode.HORIZONTAL -> false
        }

    fun withPreset(p: Preset) = copy(
        intensity = p.intensity,
        frost = p.frost,
        darkening = p.darkening,
        sheen = p.sheen,
        refraction = p.refraction,
        preset = p,
    )
}

object GlassSettings {
    private const val PREFS = "duo_glass"

    private var prefs: SharedPreferences? = null
    private val _config = MutableStateFlow(GlassConfig())
    val config: StateFlow<GlassConfig> = _config.asStateFlow()
    val value: GlassConfig get() = _config.value

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val d = GlassConfig()
        _config.value = GlassConfig(
            intensity = p.getFloat("intensity", d.intensity),
            frost = p.getFloat("frost", d.frost),
            darkening = p.getFloat("darkening", d.darkening),
            eyeDistanceMm = p.getFloat("eyeDistanceMm", d.eyeDistanceMm),
            sheen = p.getFloat("sheen", d.sheen),
            refraction = p.getFloat("refraction", d.refraction),
            preset = p.getString("preset", d.preset?.name)?.let { n -> Preset.entries.firstOrNull { it.name == n } },
            movingSide = p.getInt("movingSide", d.movingSide),
            coverFrostFromRight = p.getBoolean("coverFrostFromRight", d.coverFrostFromRight),
            engine = enumOr(p.getString("engine", null), d.engine),
            instantStart = p.getBoolean("instantStart", d.instantStart),
            axisMode = enumOr(p.getString("axisMode", null), d.axisMode),
            learnedFoldVertical = p.getBoolean("learnedFoldVertical", d.learnedFoldVertical),
            learnedFoldCenter = p.getFloat("learnedFoldCenter", d.learnedFoldCenter),
            learnedSwapAngle = p.getFloat("learnedSwapAngle", d.learnedSwapAngle),
            swapSamples = p.getInt("swapSamples", d.swapSamples),
            learnedCloseSwapAngle = p.getFloat("learnedCloseSwapAngle", d.learnedCloseSwapAngle),
            closeSwapSamples = p.getInt("closeSwapSamples", d.closeSwapSamples),
            haptics = p.getBoolean("haptics", d.haptics),
            glassTint = p.getFloat("glassTint", d.glassTint),
            coverHold = p.getBoolean("coverHold", d.coverHold),
            coverHoldAngle = p.getFloat("coverHoldAngle", d.coverHoldAngle),
            holdViaRoot = p.getBoolean("holdViaRoot", d.holdViaRoot),
            onboarded = p.getBoolean("onboarded", d.onboarded),
            imageVersion = p.getLong("imageVersion", d.imageVersion),
        )
    }

    fun update(transform: (GlassConfig) -> GlassConfig) {
        val next = transform(_config.value)
        if (next == _config.value) return
        _config.value = next
        prefs?.edit {
            putFloat("intensity", next.intensity)
            putFloat("frost", next.frost)
            putFloat("darkening", next.darkening)
            putFloat("eyeDistanceMm", next.eyeDistanceMm)
            putFloat("sheen", next.sheen)
            putFloat("refraction", next.refraction)
            putString("preset", next.preset?.name)
            putInt("movingSide", next.movingSide)
            putBoolean("coverFrostFromRight", next.coverFrostFromRight)
            putString("engine", next.engine.name)
            putBoolean("instantStart", next.instantStart)
            putString("axisMode", next.axisMode.name)
            putBoolean("learnedFoldVertical", next.learnedFoldVertical)
            putFloat("learnedFoldCenter", next.learnedFoldCenter)
            putFloat("learnedSwapAngle", next.learnedSwapAngle)
            putInt("swapSamples", next.swapSamples)
            putFloat("learnedCloseSwapAngle", next.learnedCloseSwapAngle)
            putInt("closeSwapSamples", next.closeSwapSamples)
            putBoolean("haptics", next.haptics)
            putFloat("glassTint", next.glassTint)
            putBoolean("coverHold", next.coverHold)
            putFloat("coverHoldAngle", next.coverHoldAngle)
            putBoolean("holdViaRoot", next.holdViaRoot)
            putBoolean("onboarded", next.onboarded)
            putLong("imageVersion", next.imageVersion)
        }
    }

    /** A tuning slider moved: the look is now custom. */
    fun tune(transform: (GlassConfig) -> GlassConfig) = update { transform(it).copy(preset = null) }

    /**
     * Folds one observed panel-swap angle into the learned value. The first
     * few samples move it a lot, later ones only nudge it, so one odd open
     * (a flick, a pause right at the swap) can't throw it off.
     */
    fun learnSwap(angle: Float) {
        if (!angle.isFinite() || angle !in 2f..160f) return
        update {
            val next = blend(it.learnedSwapAngle, it.swapSamples, angle)
            it.copy(learnedSwapAngle = next, swapSamples = (it.swapSamples + 1).coerceAtMost(1000))
        }
    }

    /** A close's swap while the cover hold is on (see [GlassConfig.learnedCloseSwapAngle]). */
    fun learnCloseSwap(angle: Float) {
        if (!angle.isFinite() || angle !in 2f..160f) return
        update {
            val next = blend(it.learnedCloseSwapAngle, it.closeSwapSamples, angle)
            it.copy(learnedCloseSwapAngle = next, closeSwapSamples = (it.closeSwapSamples + 1).coerceAtMost(1000))
        }
    }

    /** Running mean that never stops adapting (at least 20% of each new sample). */
    private fun blend(prev: Float, n: Int, sample: Float): Float {
        val alpha = if (n == 0 || !prev.isFinite()) 1f else maxOf(0.2f, 1f / (n + 1))
        return if (alpha >= 1f) sample else prev + (sample - prev) * alpha
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
