package com.duoglass.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * In-process view of the overlay service. While the glass is on screen the
 * live wallpaper and the app preview skip their own effect (the overlay
 * already covers them; a second pass would frost the wallpaper twice).
 */
object OverlayState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var service = WeakReference<FoldService>(null)

    internal fun attach(s: FoldService) {
        service = WeakReference(s)
        _connected.value = true
    }

    internal fun detach(s: FoldService) {
        if (service.get() === s) service = WeakReference(null)
        _connected.value = false
    }

    internal fun setRunning(value: Boolean) {
        _running.value = value
    }

    /** Replays the glass over the current screen; false if the service isn't connected. */
    fun playDemo(): Boolean {
        val s = service.get() ?: return false
        s.playDemo()
        return true
    }
}
