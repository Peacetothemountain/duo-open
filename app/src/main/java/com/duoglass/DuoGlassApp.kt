package com.duoglass

import android.app.Application
import com.duoglass.core.hinge.HingeSensor
import com.duoglass.core.settings.GlassSettings

class DuoGlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The app, the wallpaper and the overlay service share this process,
        // the settings flow and one hinge registration.
        GlassSettings.init(this)
        HingeSensor.init(this)
        com.duoglass.overlay.CoverHold.init(this)
    }
}
