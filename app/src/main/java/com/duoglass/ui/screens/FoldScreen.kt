package com.duoglass.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.settings.Engine
import com.duoglass.core.settings.GlassConfig
import com.duoglass.core.settings.GlassSettings
import com.duoglass.ui.AppModel
import com.duoglass.ui.FoldPreview
import com.duoglass.ui.components.GlassButton
import com.duoglass.ui.components.GlassSwitch
import com.duoglass.ui.components.ListRow
import com.duoglass.ui.components.Section
import com.duoglass.ui.components.SliderRow
import com.duoglass.ui.components.Symbols
import com.duoglass.ui.components.Txt
import com.duoglass.ui.glass.GlassStyle
import com.duoglass.ui.glass.LocalGlassAppearance
import com.duoglass.ui.glass.backdropSource
import com.duoglass.ui.glass.liquidGlass
import com.duoglass.ui.glass.rememberBackdrop
import com.duoglass.ui.theme.Glass
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun FoldScreen(
    scroll: ScrollState,
    model: AppModel,
    config: GlassConfig,
    image: androidx.compose.ui.graphics.ImageBitmap?,
    onPlay: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val angle = model.angle
    val target = if (angle.isNaN()) 0f else FoldModel.innerTilt(angle, config)
    val tilt by animateFloatAsState(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f), label = "tilt")

    Page(
        scroll = scroll,
        title = "Duo Glass",
        subtitle = "Liquid glass, driven by your hinge",
        paneA = {
            Hero(image, { tilt }, config, model, onPlay)
            Spacer(Modifier.height(22.dp))
            HingeCard(model, angle)
        },
        paneB = {
            StatusSection(model, config, onOpenSetup)
        },
    )
}

@Composable
private fun Hero(
    image: androidx.compose.ui.graphics.ImageBitmap?,
    tilt: () -> Float,
    config: GlassConfig,
    model: AppModel,
    onPlay: () -> Unit,
) {
    val hero = rememberBackdrop()
    val style = GlassStyle.resolve(LocalGlassAppearance.current, dark = true)
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(34.dp)),
    ) {
        FoldPreview(image, tilt, config, Modifier.fillMaxSize().backdropSource(hero))
        // Live readout, as a glass chip floating over the folding picture.
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .liquidGlass(hero, CircleShape, style)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val a = model.angle
            Txt(if (a.isNaN()) "—" else "${a.roundToInt()}°", Glass.type.headline.copy(fontFeatureSettings = "tnum"), Color.White)
            Spacer(Modifier.width(8.dp))
            Txt(if (model.simulate || !model.hasSensor) "Simulated" else "Hinge", Glass.type.footnote, Color.White.copy(alpha = 0.75f))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassButton("Play on screen", hero, onPlay, Modifier.weight(1f), icon = Symbols.Play, prominent = true)
        }
    }
}

/** Side view of the hinge: two glass panes opening around the crease. */
@Composable
private fun HingeCard(model: AppModel, angle: Float) {
    val c = Glass.colors
    val config = GlassSettings.value
    val swap = FoldModel.swapAngle(config)
    Section(header = "Hinge") {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(if (angle.isNaN()) "—" else "${angle.roundToInt()}°", Glass.type.numeral.copy(fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 10.dp)) {
                    val phase = when {
                        angle.isNaN() -> "Waiting for the sensor"
                        angle < swap -> "Cover screen"
                        angle >= FoldModel.FLAT_HINGE -> "Open flat"
                        else -> "Inner screen, unfolding"
                    }
                    Txt(phase, Glass.type.headline)
                    Txt("Screens swap near ${swap.roundToInt()}°", Glass.type.footnote, c.secondaryLabel)
                }
            }
            Spacer(Modifier.height(8.dp))
            val a = if (angle.isNaN()) 180f else angle
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val pivot = Offset(size.width / 2f, size.height * 0.82f)
                val len = size.width * 0.38f
                val th = Math.toRadians((180f - a).toDouble())
                val end = Offset(pivot.x - len * cos(th).toFloat(), pivot.y - len * sin(th).toFloat())
                val stroke = 10.dp.toPx()
                // Fixed pane.
                drawLine(
                    Brush.horizontalGradient(listOf(c.blue.copy(alpha = 0.9f), c.indigo.copy(alpha = 0.5f)), pivot.x, pivot.x + len),
                    pivot, Offset(pivot.x + len, pivot.y), stroke, StrokeCap.Round,
                )
                // Moving pane: frosted as it tilts.
                drawLine(c.purple.copy(alpha = 0.55f + 0.4f * (1f - a / 180f)), pivot, end, stroke, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.7f), pivot, end, 1.2.dp.toPx(), StrokeCap.Round)
                // Swap threshold tick.
                val sw = Math.toRadians((180f - swap).toDouble())
                val tick = Offset(pivot.x - len * 1.08f * cos(sw).toFloat(), pivot.y - len * 1.08f * sin(sw).toFloat())
                drawCircle(c.orange, 3.dp.toPx(), tick)
                // Angle arc.
                val r = len * 0.32f
                drawArc(
                    c.secondaryLabel, startAngle = 180f + (180f - a), sweepAngle = a, useCenter = false,
                    topLeft = Offset(pivot.x - r, pivot.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(Color.White, 4.dp.toPx(), pivot)
            }
        }
        ListRow(
            "Simulate the hinge",
            subtitle = if (model.hasSensor) "Drive the preview by hand" else "No hinge sensor here — preview only",
            divider = model.simulate || !model.hasSensor,
        ) {
            GlassSwitch(model.simulate || !model.hasSensor, { model.simulate = it }, enabled = model.hasSensor)
        }
        if (model.simulate || !model.hasSensor) {
            SliderRow(
                "Angle",
                model.simAngle,
                { model.simAngle = it },
                range = 0f..180f,
                valueText = "${model.simAngle.roundToInt()}°",
                marker = swap,
                divider = false,
            )
        }
    }
}

@Composable
private fun StatusSection(model: AppModel, config: GlassConfig, onOpenSetup: () -> Unit) {
    val c = Glass.colors
    Section(header = "Status", footer = "The whole-screen fold covers apps, the launcher and the lock screen. The live wallpaper alone needs no special permission.") {
        ListRow(
            "Whole-screen fold",
            icon = Symbols.Fold,
            iconColor = c.blue,
            value = if (model.overlayEnabled) "On" else "Set up",
            chevron = true,
            onClick = onOpenSetup,
        )
        ListRow(
            "Live wallpaper",
            icon = Symbols.Wallpaper,
            iconColor = c.purple,
            value = if (model.wallpaperActive) "Active" else "Off",
            chevron = !model.wallpaperActive,
            onClick = if (model.wallpaperActive) null else ({ model.setWallpaper() }),
        )
        val engine = when (config.engine) {
            Engine.SNAPSHOT -> "Snapshot"
            Engine.LIVE -> if (model.blurSupported) "Live glass" else "Snapshot (blur off)"
            Engine.AUTO -> if (model.blurSupported) "Live glass" else "Snapshot"
        }
        ListRow("Engine", icon = Symbols.Bolt, iconColor = c.orange, value = engine, onClick = onOpenSetup, chevron = true)
        val learned = config.learnedSwapAngle
        ListRow(
            "Screen swap",
            icon = Symbols.Angle,
            iconColor = c.teal,
            value = if (learned.isFinite()) "${learned.roundToInt()}° · learned" else "${FoldModel.DEFAULT_SWAP.roundToInt()}° · default",
            divider = false,
        )
    }
    Section(footer = "Fold the phone part-way and open it. Play on screen replays the effect over whatever is showing.") {
        Box(Modifier.fillMaxWidth().background(Color.Transparent).padding(16.dp)) {
            Column {
                Txt("How it works", Glass.type.headline.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                Txt(
                    "Each half of the screen is a pane of glass hinged at the crease. While the phone is part-folded the moving half frosts, darkens and bends light at its rim; as the hinge reaches flat it settles into focus. The cover screen frosts in as you start opening, then the inner screen picks it up and clears.",
                    Glass.type.subhead,
                    c.secondaryLabel,
                )
            }
        }
    }
}
