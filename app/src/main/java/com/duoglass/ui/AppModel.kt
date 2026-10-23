package com.duoglass.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.hinge.HingeSensor
import com.duoglass.core.settings.GlassSettings
import com.duoglass.core.trace.FoldTrace
import com.duoglass.overlay.FoldService
import com.duoglass.overlay.OverlayState
import com.duoglass.wallpaper.GlassWallpaperService

/** UI state that isn't a setting: the live hinge, the simulator, system statuses. */
class AppModel(private val context: Context) : HingeSensor.Listener {
    var hingeAngle by mutableFloatStateOf(HingeSensor.angle)
        private set
    var simulate by mutableStateOf(!HingeSensor.available)
    var simAngle by mutableFloatStateOf(110f)

    var overlayEnabled by mutableStateOf(false)
        private set
    var wallpaperActive by mutableStateOf(false)
        private set
    var blurSupported by mutableStateOf(false)
        private set

    var banner by mutableStateOf<String?>(null)

    val angle: Float get() = if (simulate || !HingeSensor.available) simAngle else hingeAngle
    val hasSensor: Boolean get() = HingeSensor.available

    override fun onHinge(angle: Float) {
        hingeAngle = angle
        // Without the service the app keeps the Lab graph fed itself.
        if (!OverlayState.connected.value) {
            FoldTrace.sample(angle, FoldModel.innerTilt(angle, GlassSettings.value), inner = true)
        }
    }

    fun refresh() {
        overlayEnabled = FoldService.isEnabled(context)
        wallpaperActive = WallpaperManager.getInstance(context).wallpaperInfo?.component ==
            ComponentName(context, GlassWallpaperService::class.java)
        blurSupported = runCatching {
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        }.getOrDefault(false)
    }

    fun openAccessibility() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo() = launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    fun setWallpaper() {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, GlassWallpaperService::class.java))
        if (!launch(direct)) launch(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    }

    private fun launch(intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/** The crease in window coordinates while the app spans the fold, else null. */
val LocalCrease = staticCompositionLocalOf<Rect?> { null }
