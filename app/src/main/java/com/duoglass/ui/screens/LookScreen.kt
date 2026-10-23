package com.duoglass.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.settings.GlassConfig
import com.duoglass.core.settings.GlassSettings
import com.duoglass.core.settings.Preset
import com.duoglass.ui.AppModel
import com.duoglass.ui.FoldPreview
import com.duoglass.ui.components.ListRow
import com.duoglass.ui.components.Section
import com.duoglass.ui.components.Segmented
import com.duoglass.ui.components.SliderRow
import com.duoglass.ui.components.Symbols
import com.duoglass.ui.components.Txt
import com.duoglass.ui.components.rememberHaptics
import com.duoglass.ui.theme.Glass
import com.duoglass.ui.theme.Motion
import com.duoglass.wallpaper.WallpaperImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun LookScreen(scroll: ScrollState, model: AppModel, config: GlassConfig, image: ImageBitmap?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { WallpaperImage.import(context.applicationContext, uri) }.isSuccess }
            model.banner = if (ok) "Picture updated" else "Couldn't load that picture"
        }
    }

    Page(
        scroll = scroll,
        title = "Look",
        subtitle = "Tune the glass. Fold the phone to see it live.",
        paneA = {
            Presets(config, image)
            Section(header = "Glass", footer = "Frost is how much the glass scatters as it lifts off the screen; darkening is how much light it swallows. Rim light and lensing are the Liquid Glass touches.") {
                SliderRow("Intensity", config.intensity, { v -> GlassSettings.tune { it.copy(intensity = v) } }, 0.3f..1.6f, "${(config.intensity * 100).roundToInt()}%", marker = 1f)
                SliderRow("Frost", config.frost, { v -> GlassSettings.tune { it.copy(frost = v) } }, 0.03f..0.25f, "%.2f".format(config.frost), marker = 0.12f)
                SliderRow("Darkening", config.darkening, { v -> GlassSettings.tune { it.copy(darkening = v) } }, 0f..0.04f, "%.3f".format(config.darkening))
                SliderRow("Rim light", config.sheen, { v -> GlassSettings.tune { it.copy(sheen = v) } }, 0f..1f, "${(config.sheen * 100).roundToInt()}%")
                SliderRow("Lensing", config.refraction, { v -> GlassSettings.tune { it.copy(refraction = v) } }, 0f..1f, "${(config.refraction * 100).roundToInt()}%")
                SliderRow(
                    "Perspective",
                    config.eyeDistanceMm,
                    { v -> GlassSettings.update { it.copy(eyeDistanceMm = v) } },
                    250f..700f,
                    "${config.eyeDistanceMm.roundToInt()} mm",
                    caption = "How far your eyes are from the screen",
                    marker = 420f,
                    divider = false,
                )
            }
        },
        paneB = {
            Section(header = "Motion", footer = "Which half of the inner screen swings while you hold the other, and which edge the cover-screen frost grows from (the spine side stays clear).") {
                Column(Modifier.padding(16.dp)) {
                    Txt("Moving half", Glass.type.subhead, Glass.colors.secondaryLabel, Modifier.padding(bottom = 8.dp))
                    Segmented(
                        listOf(-1 to "Left", 0 to "Both", 1 to "Right"),
                        config.movingSide,
                        { v -> GlassSettings.update { it.copy(movingSide = v) } },
                    )
                    Spacer(Modifier.height(16.dp))
                    Txt("Cover frost grows from", Glass.type.subhead, Glass.colors.secondaryLabel, Modifier.padding(bottom = 8.dp))
                    Segmented(
                        listOf(false to "Left edge", true to "Right edge"),
                        config.coverFrostFromRight,
                        { v -> GlassSettings.update { it.copy(coverFrostFromRight = v) } },
                    )
                }
            }
            Section(header = "Picture", footer = "Used by the preview and the live wallpaper. The whole-screen fold uses whatever is on screen.") {
                ListRow("Choose a picture", icon = Symbols.Photo, iconColor = Glass.colors.green, chevron = true, onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                ListRow("Use the aurora", icon = Symbols.Sparkles, iconColor = Glass.colors.indigo, divider = false, onClick = {
                    WallpaperImage.reset(context.applicationContext)
                    model.banner = "Back to the aurora"
                })
            }
            Section(header = "Appearance", footer = "Like iOS 27's Liquid Glass setting: from Ultra Clear to Fully Tinted. The middle is Apple's default.") {
                SliderRow(
                    "Liquid Glass",
                    config.glassTint,
                    { v -> GlassSettings.update { it.copy(glassTint = v) } },
                    0f..1f,
                    when {
                        config.glassTint < 0.2f -> "Ultra Clear"
                        config.glassTint < 0.4f -> "Clear"
                        config.glassTint <= 0.6f -> "Default"
                        config.glassTint <= 0.8f -> "Frosted"
                        else -> "Tinted"
                    },
                    marker = 0.5f,
                    divider = false,
                )
            }
        },
    )
}

/** Preset tiles, each a live miniature at mid-fold. */
@Composable
private fun Presets(config: GlassConfig, image: ImageBitmap?) {
    val c = Glass.colors
    val haptics = rememberHaptics()
    Section(header = "Style") {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in Preset.entries) {
                val on = config.preset == p
                val look = remember(p, config.movingSide, config.eyeDistanceMm) { config.withPreset(p) }
                val ring = animateFloatAsState(if (on) 1f else 0f, Motion.snappy(), label = "ring")
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            haptics.select()
                            GlassSettings.update { it.withPreset(p) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FoldPreview(
                        image,
                        { FoldModel.MAX_TILT * 0.55f },
                        look,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.82f)
                            .graphicsLayer { scaleX = 1f - 0.04f * (1f - ring.value); scaleY = scaleX }
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.5.dp, c.blue.copy(alpha = ring.value), RoundedCornerShape(16.dp)),
                        showIcons = false,
                    )
                    Spacer(Modifier.height(8.dp))
                    Txt(
                        p.title,
                        Glass.type.footnote.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        if (on) c.blue else c.label,
                    )
                }
            }
        }
        if (config.preset == null) {
            Txt("Custom", Glass.type.footnote, c.secondaryLabel, Modifier.padding(start = 16.dp, bottom = 12.dp))
        }
    }
}
