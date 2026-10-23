package com.duoglass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import com.duoglass.core.fold.FoldLearning
import com.duoglass.ui.DuoGlassRoot
import com.duoglass.ui.LocalCrease
import com.duoglass.ui.theme.GlassTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val crease = MutableStateFlow<Rect?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    crease.value = f?.bounds?.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
                    if (f != null) {
                        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this@MainActivity).bounds
                        FoldLearning.learn(f, bounds.width(), bounds.height(), display.rotation, source = "app")
                    }
                }
            }
        }

        setContent {
            val creaseRect by crease.collectAsStateWithLifecycle()
            GlassTheme(dark = isSystemInDarkTheme()) {
                CompositionLocalProvider(LocalCrease provides creaseRect) {
                    DuoGlassRoot()
                }
            }
        }
    }
}
