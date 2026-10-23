package com.duoglass.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.duoglass.core.settings.GlassSettings
import com.duoglass.ui.glass.Backdrop
import com.duoglass.ui.glass.GlassStyle
import com.duoglass.ui.glass.LocalGlassAppearance
import com.duoglass.ui.glass.liquidGlass
import com.duoglass.ui.theme.Glass
import com.duoglass.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- Haptics -------------------------------------------------------------------

class Haptics(private val view: View) {
    private fun go(c: Int) {
        if (GlassSettings.value.haptics) view.performHapticFeedback(c)
    }
    fun tick() = go(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
    fun select() = go(HapticFeedbackConstants.SEGMENT_TICK)
    fun toggle(on: Boolean) = go(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
    fun confirm() = go(HapticFeedbackConstants.CONFIRM)
    fun reject() = go(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ---- Text & symbols ------------------------------------------------------------

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = Glass.colors.label,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Symbol(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

// ---- Glass buttons -------------------------------------------------------------

/**
 * A capsule of liquid glass. [prominent] tints the glass with the accent (iOS:
 * only the primary action is tinted). Pressing swells it slightly and
 * brightens the glass, the "flex and energize" response.
 */
@Composable
fun GlassButton(
    text: String,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    prominent: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.press(), label = "press")
    val style = GlassStyle.resolve(LocalGlassAppearance.current, c.dark, accent = if (prominent) c.blue else null)
    val labelColor = when {
        !enabled -> c.tertiaryLabel
        prominent -> Color.White
        else -> c.label
    }
    val haptics = rememberHaptics()
    Row(
        modifier
            .graphicsLayer {
                val s = 1f + 0.05f * press
                scaleX = s
                scaleY = s
            }
            .heightIn(min = 50.dp)
            .then(
                // On a sheet there's nothing distinct to refract: a solid capsule,
                // which is what tinted glass resolves to over a plain surface.
                if (backdrop != null) Modifier.liquidGlass(backdrop, CircleShape, style, pressed = { press })
                else Modifier.clip(CircleShape).background(if (prominent) c.blue else c.fill),
            )
            .clickable(interaction, indication = null, enabled = enabled) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Symbol(icon, labelColor, size = 19.dp)
            Spacer(Modifier.width(8.dp))
        }
        Txt(text, Glass.type.headline, labelColor, maxLines = 1)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Glass.colors.label,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.press(), label = "press")
    val style = GlassStyle.resolve(LocalGlassAppearance.current, Glass.colors.dark)
    val haptics = rememberHaptics()
    Box(
        modifier
            .graphicsLayer {
                val s = 1f + 0.08f * press
                scaleX = s
                scaleY = s
            }
            .size(size)
            .liquidGlass(backdrop, CircleShape, style, pressed = { press })
            .clickable(interaction, indication = null) {
                haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Symbol(icon, tint, size = size * 0.46f)
    }
}

// ---- Switch --------------------------------------------------------------------

/**
 * iOS 26/27 switch: a wide capsule track with a capsule knob. While touched
 * the knob swells and turns to glass; it can be dragged across.
 */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Glass.colors
    val haptics = rememberHaptics()
    val current by rememberUpdatedState(checked)
    val onChange by rememberUpdatedState(onCheckedChange)
    var pressed by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.press(), label = "knob")
    val pos by animateFloatAsState(dragFrac ?: if (checked) 1f else 0f, Motion.bouncy(), label = "pos")
    val track by animateColorAsState(if (checked) c.green else c.switchOff, Motion.smooth(), label = "track")
    val trackW = 64.dp
    val trackH = 28.dp
    val density = LocalDensity.current
    Box(
        modifier
            .size(trackW + 8.dp, trackH + 10.dp)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var moved = 0f
                    val travel = with(density) { (trackW - 38.dp).toPx() }
                    var frac = if (current) 1f else 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val dx = ch.positionChange().x
                        moved += abs(dx)
                        if (moved > 6f) {
                            frac = (frac + dx / travel).coerceIn(0f, 1f)
                            dragFrac = frac
                        }
                        ch.consume()
                    }
                    pressed = false
                    val next = if (dragFrac != null) frac > 0.5f else !current
                    dragFrac = null
                    if (next != current) {
                        haptics.toggle(next)
                        onChange(next)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(trackW, trackH)) {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        }
        val knobW = 38.dp + 8.dp * press
        val knobH = 24.dp + 8.dp * press
        val travel = trackW - 38.dp - 4.dp
        Box(
            Modifier
                .offset { IntOffset(with(density) { ((-(trackW - 38.dp - 4.dp) / 2) + travel * pos).roundToPx() }, 0) }
                .size(knobW, knobH)
                .knobShadow(1f - press)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 1f - 0.7f * press))
                .border(0.5.dp, Color.Black.copy(alpha = if (c.dark) 0f else 0.07f * (1f - press)), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.55f * press), CircleShape),
        )
    }
}

/**
 * The soft shadow iOS puts under white knobs (0 3 8 black 15% plus a 0.5 pt
 * contact line) so they read on a white cell. [strength] 0 removes it, as
 * when the knob has turned to glass under a finger.
 */
private fun Modifier.knobShadow(strength: Float): Modifier =
    if (strength <= 0.01f) this
    else this.shadow(
        elevation = (4f * strength).dp,
        shape = CircleShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.10f * strength),
        spotColor = Color.Black.copy(alpha = 0.22f * strength),
    )

// ---- Slider --------------------------------------------------------------------

/**
 * A capsule track with a filled run and a capsule thumb that turns to glass
 * and grows while dragged. Taps jump; a tick plays at [marker] (the default).
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    marker: Float? = null,
    accent: Color = Glass.colors.blue,
) {
    val c = Glass.colors
    val haptics = rememberHaptics()
    val onChange by rememberUpdatedState(onValueChange)
    val currentRange by rememberUpdatedState(range)
    var dragging by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (dragging) 1f else 0f, Motion.press(), label = "thumb")
    var widthPx by remember { mutableFloatStateOf(1f) }
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    val markerFrac = marker?.let { ((it - range.start) / span).coerceIn(0f, 1f) }
    val currentMarker by rememberUpdatedState(markerFrac)
    val density = LocalDensity.current
    val thumbW = 38.dp
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    val inset = with(density) { (thumbW / 2).toPx() }
                    fun fracAt(x: Float) = ((x - inset) / (widthPx - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    var last = fracAt(down.position.x)
                    val r = currentRange
                    onChange(r.start + last * (r.endInclusive - r.start))
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val f = fracAt(ch.position.x)
                        val m = currentMarker
                        if (m != null && (last - m) * (f - m) < 0f) haptics.tick()
                        if ((f == 0f || f == 1f) && f != last) haptics.tick()
                        last = f
                        onChange(r.start + f * (r.endInclusive - r.start))
                        ch.consume()
                    }
                    dragging = false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(6.dp).padding(horizontal = thumbW / 2 - 3.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(c.fill, cornerRadius = r)
            drawRoundRect(accent, size = Size(size.width * frac, size.height), cornerRadius = r)
            if (markerFrac != null) {
                drawCircle(c.secondaryLabel, radius = 1.6.dp.toPx(), center = Offset(size.width * markerFrac, size.height / 2))
            }
        }
        val tw = thumbW + 10.dp * press
        val th = 24.dp + 10.dp * press
        Box(
            Modifier
                .offset {
                    val usable = widthPx - with(density) { thumbW.toPx() }
                    val x = usable * frac - with(density) { (10.dp * press / 2).toPx() }
                    IntOffset(x.roundToInt(), 0)
                }
                .size(tw, th)
                .knobShadow(1f - press)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 1f - 0.72f * press))
                .border(0.5.dp, Color.Black.copy(alpha = if (c.dark) 0f else 0.07f * (1f - press)), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.6f * press), CircleShape),
        )
    }
}

// ---- Segmented control -----------------------------------------------------------

/** Capsule segmented control with a glass pill that springs between options. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val haptics = rememberHaptics()
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val pos = remember { Animatable(index.toFloat()) }
    val scope = rememberCoroutineScope()
    var pressedIdx by remember { mutableStateOf(-1) }
    val press by animateFloatAsState(if (pressedIdx >= 0) 1f else 0f, Motion.press(), label = "seg")
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    androidx.compose.runtime.LaunchedEffect(index) { pos.animateTo(index.toFloat(), Motion.bouncy()) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .background(c.tertiaryFill),
    ) {
        val n = options.size.coerceAtLeast(1)
        val segW = maxWidth / n
        Box(
            Modifier
                .offset { IntOffset((segW * pos.value).roundToPx(), 0) }
                .width(segW)
                .fillMaxHeight()
                .padding(2.dp)
                .scale(1f + 0.04f * press)
                .knobShadow(if (c.dark) 0f else 0.6f)
                .clip(CircleShape)
                .background(if (c.dark) Color(0xFF636366) else Color.White)
                .border(1.dp, Color.White.copy(alpha = if (c.dark) 0.12f else 0.6f), CircleShape),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, (value, label) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(value) {
                            awaitEachGesture {
                                awaitFirstDown()
                                pressedIdx = i
                                val up = waitForUpOrCancellation()
                                pressedIdx = -1
                                if (up != null && value != currentSelected) {
                                    haptics.select()
                                    scope.launch { pos.animateTo(i.toFloat(), Motion.bouncy()) }
                                    select(value)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(
                        label,
                        Glass.type.subhead.copy(fontWeight = if (i == index) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                        c.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---- Inset grouped lists -----------------------------------------------------------

val SectionShape = RoundedCornerShape(26.dp)

@Composable
fun Section(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val c = Glass.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Txt(
                header,
                Glass.type.headline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                c.label,
                Modifier.padding(start = 16.dp, bottom = 8.dp, top = 6.dp),
            )
        }
        Column(Modifier.fillMaxWidth().clip(SectionShape).background(c.cell)) {
            content()
        }
        if (footer != null) {
            Txt(footer, Glass.type.footnote, c.secondaryLabel, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
        Spacer(Modifier.height(22.dp))
    }
}

/** Coloured rounded-square tile behind a white symbol, like Settings rows. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, Color.White, size = size * 0.62f) }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = Glass.colors.blue,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    divider: Boolean = true,
    titleColor: Color = Glass.colors.label,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .background(if (pressed) c.cellPressed else Color.Transparent)
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null) {
                    haptics.tick()
                    onClick()
                } else Modifier,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon, iconColor)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Txt(title, Glass.type.body, titleColor, maxLines = 2)
                if (subtitle != null) Txt(subtitle, Glass.type.footnote, c.secondaryLabel, Modifier.padding(top = 2.dp))
            }
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Txt(value, Glass.type.body, c.secondaryLabel, maxLines = 1)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(6.dp))
                Symbol(Symbols.ChevronRight, c.tertiaryLabel, size = 14.dp)
            }
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (icon != null) 60.dp else 16.dp)
                    .height(0.5.dp)
                    .background(c.separator),
            )
        }
    }
}

/** A labelled slider inside a section. */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    marker: Float? = null,
    divider: Boolean = true,
    caption: String? = null,
) {
    val c = Glass.colors
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(title, Glass.type.body, c.label, Modifier.weight(1f))
                Txt(valueText, Glass.type.body.copy(fontFeatureSettings = "tnum"), c.secondaryLabel)
            }
            if (caption != null) Txt(caption, Glass.type.footnote, c.secondaryLabel, Modifier.padding(top = 2.dp))
            GlassSlider(value, onValueChange, range = range, marker = marker)
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(c.separator))
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color = Glass.colors.blue,
    subtitle: String? = null,
    divider: Boolean = true,
    enabled: Boolean = true,
) {
    ListRow(title, icon = icon, iconColor = iconColor, subtitle = subtitle, divider = divider) {
        GlassSwitch(checked, onCheckedChange, enabled = enabled)
    }
}
