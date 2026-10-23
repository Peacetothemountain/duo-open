package com.duoglass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.duoglass.BuildConfig
import com.duoglass.core.settings.AxisMode
import com.duoglass.core.settings.Engine
import com.duoglass.core.settings.GlassConfig
import com.duoglass.core.settings.GlassSettings
import com.duoglass.ui.AppModel
import com.duoglass.ui.components.ListRow
import com.duoglass.ui.components.Section
import com.duoglass.ui.components.Segmented
import com.duoglass.ui.components.SwitchRow
import com.duoglass.ui.components.Symbol
import com.duoglass.ui.components.Symbols
import com.duoglass.ui.components.Txt
import com.duoglass.ui.theme.Glass
import kotlin.math.roundToInt

@Composable
fun SetupScreen(scroll: ScrollState, model: AppModel, config: GlassConfig) {
    val c = Glass.colors
    var help by rememberSaveable { mutableStateOf(false) }
    Page(
        scroll = scroll,
        title = "Setup",
        paneA = {
            Section(
                header = "Whole-screen fold",
                footer = "Runs as an accessibility service so the glass can draw over every app and the lock screen. It reads nothing on screen, has no internet access, and only screenshots while a fold plays (Snapshot engine).",
            ) {
                ListRow(
                    "Duo Glass fold",
                    icon = Symbols.Shield,
                    iconColor = if (model.overlayEnabled) c.green else c.blue,
                    subtitle = if (model.overlayEnabled) "Turned on" else "Off — turn it on in Accessibility",
                    trailing = { StatusDot(model.overlayEnabled) },
                )
                ListRow("Open Accessibility settings", titleColor = c.blue, chevron = true, onClick = model::openAccessibility)
                ListRow("Toggle greyed out?", titleColor = c.blue, divider = help, onClick = { help = !help }, trailing = {
                    Symbol(if (help) Symbols.ChevronDown else Symbols.ChevronRight, c.tertiaryLabel, size = 14.dp)
                })
                AnimatedVisibility(help, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(Modifier.padding(16.dp)) {
                        Txt(
                            "Android blocks Accessibility for apps installed from a file until you allow it. On Android 15–17:",
                            Glass.type.subhead,
                            c.secondaryLabel,
                        )
                        Spacer(Modifier.height(10.dp))
                        Step(1, "In Accessibility, tap “Duo Glass whole-screen fold” once, so Android shows the “Restricted setting” notice.")
                        Step(2, "Open App info below → ⋮ (top right) → Allow restricted settings, and confirm.")
                        Step(3, "Back in Accessibility, turn it on.")
                        Spacer(Modifier.height(6.dp))
                        Txt(
                            "Or over USB, which skips the check:\nadb shell settings put secure enabled_accessibility_services com.duoglass/com.duoglass.overlay.FoldService",
                            Glass.type.footnote,
                            c.tertiaryLabel,
                        )
                        Spacer(Modifier.height(8.dp))
                        ListRow("Open App info", titleColor = c.blue, chevron = true, divider = false, onClick = model::openAppInfo)
                    }
                }
            }
            Section(header = "Wallpaper only", footer = "No accessibility needed: the home and lock screen wallpaper folds; icons stay sharp.") {
                ListRow(
                    if (model.wallpaperActive) "Live wallpaper is set" else "Set live wallpaper",
                    icon = Symbols.Wallpaper,
                    iconColor = c.purple,
                    titleColor = if (model.wallpaperActive) c.label else c.blue,
                    chevron = !model.wallpaperActive,
                    divider = false,
                    onClick = if (model.wallpaperActive) null else ({ model.setWallpaper() }),
                    trailing = { if (model.wallpaperActive) StatusDot(true) },
                )
            }
        },
        paneB = {
            Section(
                header = "Engine",
                footer = when {
                    !model.blurSupported -> "Window blur is off on this phone right now (battery saver, or the system disables it), so the glass uses snapshots."
                    config.engine == Engine.SNAPSHOT -> "Snapshot: one screenshot per fold phase, bent through the shader — perspective and smooth frost, but frozen while it plays and ~0.3 s late on a screen that just lit up."
                    else -> "Live glass: the system's own blur over the live screen. Starts instantly, nothing is captured and the content keeps moving. Best on Pixel Fold."
                },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Segmented(
                        listOf(Engine.AUTO to "Auto", Engine.LIVE to "Live glass", Engine.SNAPSHOT to "Snapshot"),
                        config.engine,
                        { v -> GlassSettings.update { it.copy(engine = v) } },
                    )
                }
                SwitchRow(
                    "Start instantly after a swap",
                    config.instantStart,
                    { v -> GlassSettings.update { it.copy(instantStart = v) } },
                    subtitle = "Snapshot: bridge with the panel's last picture",
                    divider = false,
                )
            }
            Section(
                header = "Crease",
                footer = "Auto uses the fold Android reports (learned the first time the screen spans it). Book-style foldables — Pixel Fold included — have it vertical in the inner screen's natural orientation.",
            ) {
                Column(Modifier.padding(16.dp)) {
                    Segmented(
                        listOf(AxisMode.AUTO to "Auto", AxisMode.VERTICAL to "Vertical", AxisMode.HORIZONTAL to "Horizontal"),
                        config.axisMode,
                        { v -> GlassSettings.update { it.copy(axisMode = v) } },
                    )
                }
                ListRow(
                    "Detected",
                    value = "${if (config.learnedFoldVertical) "Vertical" else "Horizontal"} · ${(config.learnedFoldCenter * 100).roundToInt()}%",
                    divider = false,
                )
            }
            Section(header = "Feel") {
                SwitchRow("Haptics", config.haptics, { v -> GlassSettings.update { it.copy(haptics = v) } }, icon = Symbols.Haptic, iconColor = c.pink, divider = false)
            }
            Section(
                header = "About",
                footer = "A ground-up rebuild of Duo Open by marcoazeem, after the Duo-Fold shader by Atomicx7. MIT licensed. Inter typeface © The Inter Project Authors (OFL).",
            ) {
                ListRow("Version", value = BuildConfig.VERSION_NAME)
                ListRow("Device", value = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", divider = false)
            }
        },
    )
}

@Composable
private fun Step(n: Int, text: String) {
    val c = Glass.colors
    Row(Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(c.blue), contentAlignment = Alignment.Center) {
            Txt("$n", Glass.type.caption1.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Txt(text, Glass.type.subhead, c.label, Modifier.weight(1f))
    }
}

@Composable
private fun StatusDot(on: Boolean) {
    val c = Glass.colors
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (on) c.green else c.fill),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Symbol(Symbols.Check, Color.White, size = 14.dp)
    }
}
