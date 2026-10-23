package com.duoglass.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duoglass.core.hinge.HingeSensor
import com.duoglass.core.settings.GlassSettings
import com.duoglass.overlay.OverlayState
import com.duoglass.ui.components.AmbientBackground
import com.duoglass.ui.components.Banner
import com.duoglass.ui.components.FloatingTabBar
import com.duoglass.ui.components.GlassButton
import com.duoglass.ui.components.GlassSheet
import com.duoglass.ui.components.IconTile
import com.duoglass.ui.components.Symbols
import com.duoglass.ui.components.TabItem
import com.duoglass.ui.components.TopBar
import com.duoglass.ui.components.Txt
import com.duoglass.ui.components.VerticalTabBar
import com.duoglass.ui.components.rememberHaptics
import com.duoglass.ui.glass.LocalGlassAppearance
import com.duoglass.ui.glass.backdropSource
import com.duoglass.ui.glass.rememberBackdrop
import com.duoglass.ui.screens.FoldScreen
import com.duoglass.ui.screens.LabScreen
import com.duoglass.ui.screens.LocalPageLayout
import com.duoglass.ui.screens.LookScreen
import com.duoglass.ui.screens.PageLayout
import com.duoglass.ui.screens.SetupScreen
import com.duoglass.ui.theme.Glass
import com.duoglass.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Tabs = listOf(
    TabItem("Fold", Symbols.Fold, Symbols.FoldFill),
    TabItem("Look", Symbols.Sparkles, Symbols.SparklesFill),
    TabItem("Setup", Symbols.Sliders, Symbols.Sliders),
    TabItem("Lab", Symbols.Waveform, Symbols.Waveform),
)

@Composable
fun DuoGlassRoot() {
    val context = LocalContext.current
    val config by GlassSettings.config.collectAsStateWithLifecycle()
    val model = remember { AppModel(context.applicationContext) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    DisposableEffect(model) {
        HingeSensor.init(context)
        HingeSensor.addListener(model)
        onDispose { HingeSensor.removeListener(model) }
    }
    LifecycleResumeEffect(model) {
        model.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(model.banner) {
        if (model.banner != null) {
            delay(2_400)
            model.banner = null
        }
    }

    val image = rememberWallpaper(config.imageVersion)
    val overlayRunning by OverlayState.running.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scrolls = remember { List(Tabs.size) { ScrollState(0) } }
    val scroll = scrolls[tab]
    var minimized by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        var last = scroll.value
        snapshotFlow { scroll.value }.collect { v ->
            val d = v - last
            last = v
            if (d > 12 && v > 160) minimized = true else if (d < -12 || v < 40) minimized = false
        }
    }

    val playOnScreen: () -> Unit = {
        if (!OverlayState.playDemo()) {
            haptics.reject()
            model.banner = "Turn on the whole-screen fold first"
            tab = 2
        } else {
            haptics.confirm()
        }
    }

    val screen = rememberBackdrop()
    CompositionLocalProvider(LocalGlassAppearance provides config.glassTint) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 600.dp
            val layout = PageLayout(expanded = expanded, leading = if (expanded) 100.dp else 0.dp)
            CompositionLocalProvider(LocalPageLayout provides layout) {
                // Everything the floating chrome refracts.
                Box(Modifier.fillMaxSize().backdropSource(screen)) {
                    AmbientBackground()
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(220)) + scaleIn(Motion.smooth(), initialScale = 0.985f)) togetherWith fadeOut(tween(120))
                        },
                        label = "tab",
                    ) { t ->
                        val s = scrolls[t]
                        when (t) {
                            0 -> FoldScreen(s, model, config, if (overlayRunning) null else image, playOnScreen, onOpenSetup = { tab = 2 })
                            1 -> LookScreen(s, model, config, image)
                            2 -> SetupScreen(s, model, config)
                            else -> LabScreen(s, model, config)
                        }
                    }
                }

                val density = LocalDensity.current
                val scrolled = with(density) { ((scroll.value - 24.dp.toPx()) / 40.dp.toPx()) }
                TopBar(Tabs[tab].title.let { if (tab == 0) "Duo Glass" else it }, scrolled, screen)

                if (expanded) {
                    VerticalTabBar(
                        Tabs, tab, { tab = it }, screen,
                        Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                    )
                } else {
                    FloatingTabBar(
                        Tabs, tab,
                        onSelect = {
                            if (it == tab) scope.launch { scroll.animateScrollTo(0) }
                            tab = it
                            minimized = false
                        },
                        backdrop = screen,
                        minimized = minimized,
                        onExpand = { minimized = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                Banner(model.banner, screen, modifier = Modifier.align(Alignment.TopCenter))

                GlassSheet(
                    visible = !config.onboarded,
                    onDismiss = { GlassSettings.update { it.copy(onboarded = true) } },
                    backdrop = screen,
                ) {
                    Onboarding(onContinue = {
                        haptics.confirm()
                        GlassSettings.update { it.copy(onboarded = true) }
                    })
                }
            }
        }
    }
}

@Composable
private fun Onboarding(onContinue: () -> Unit) {
    val c = Glass.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp)) {
        Txt("Welcome to\nDuo Glass", Glass.type.largeTitle)
        Spacer(Modifier.height(22.dp))
        Feature(Symbols.Fold, c.blue, "A fold made of glass", "Open your phone and the screen frosts, bends and clears like a pane of liquid glass, following the real hinge.")
        Feature(Symbols.Bolt, c.orange, "Live, not a screenshot", "On Pixel Fold the glass blurs the live screen, so it starts the instant you open.")
        Feature(Symbols.Shield, c.green, "Private by design", "No internet permission. Nothing is stored or sent anywhere.")
        Spacer(Modifier.height(18.dp))
        GlassButton("Continue", null, onContinue, Modifier.fillMaxWidth(), prominent = true)
    }
}

@Composable
private fun Feature(icon: ImageVector, color: androidx.compose.ui.graphics.Color, title: String, text: String) {
    Row(Modifier.padding(vertical = 9.dp)) {
        IconTile(icon, color, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Txt(title, Glass.type.headline.copy(fontWeight = FontWeight.SemiBold))
            Txt(text, Glass.type.subhead, Glass.colors.secondaryLabel, Modifier.padding(top = 2.dp))
        }
    }
}
