package com.duoglass.ui.components

import android.graphics.RuntimeShader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.duoglass.ui.glass.Backdrop
import com.duoglass.ui.glass.GlassStyle
import com.duoglass.ui.glass.LocalGlassAppearance
import com.duoglass.ui.glass.liquidGlass
import com.duoglass.ui.theme.Glass
import com.duoglass.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class TabItem(val title: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * The floating capsule tab bar: inset from the edges, a glass pill under the
 * selected tab that springs between tabs, and — like iOS 26/27's
 * minimize-on-scroll — it shrinks to just the selected tab while you scroll
 * down through content, expanding again on scroll up or tap.
 */
@Composable
fun FloatingTabBar(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    minimized: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val haptics = rememberHaptics()
    val style = GlassStyle.resolve(LocalGlassAppearance.current, c.dark)
    val pill = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { pill.animateTo(selected.toFloat(), Motion.bouncy()) }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 21.dp, end = 21.dp, bottom = nav + 12.dp)) {
        val full = maxWidth.coerceAtMost(520.dp)
        val width by animateDpAsState(if (minimized) 62.dp else full, Motion.snappy(), label = "tabW")
        val labels by animateFloatAsState(if (minimized) 0f else 1f, Motion.smooth(), label = "labels")
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .width(width)
                .height(62.dp)
                .liquidGlass(backdrop, CircleShape, style),
        ) {
            if (minimized) {
                Box(
                    Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onExpand() },
                    contentAlignment = Alignment.Center,
                ) { Symbol(tabs[selected].selectedIcon, c.blue, size = 26.dp) }
            } else {
                val segW = width / tabs.size
                // iOS 27 dark mode: the selected pill is darker than the bar.
                val pillColor = if (c.dark) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.06f)
                Box(
                    Modifier
                        .offset { IntOffset((segW * pill.value).roundToPx(), 0) }
                        .width(segW)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(pillColor)
                        .border(1.dp, Color.White.copy(alpha = if (c.dark) 0.08f else 0.5f), CircleShape),
                )
                Row(Modifier.fillMaxSize().graphicsLayer { alpha = labels }) {
                    tabs.forEachIndexed { i, tab ->
                        val on = i == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(remember { MutableInteractionSource() }, null) {
                                    if (i != selected) haptics.select()
                                    onSelect(i)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Symbol(if (on) tab.selectedIcon else tab.icon, if (on) c.blue else c.label, size = 25.dp)
                            Spacer(Modifier.height(2.dp))
                            Txt(tab.title, Glass.type.caption2, if (on) c.blue else c.label, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * iOS 27's vertical tab bar for a phone that's open like a book (the
 * "iPhone Duo" layouts): a glass capsule standing at the leading edge.
 */
@Composable
fun VerticalTabBar(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val haptics = rememberHaptics()
    val style = GlassStyle.resolve(LocalGlassAppearance.current, c.dark)
    val pill = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { pill.animateTo(selected.toFloat(), Motion.bouncy()) }
    val itemH = 66.dp
    Box(
        modifier
            .width(72.dp)
            .height(itemH * tabs.size + 8.dp)
            .liquidGlass(backdrop, RoundedCornerShape(36.dp), style)
            .padding(vertical = 4.dp),
    ) {
        val pillColor = if (c.dark) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.06f)
        Box(
            Modifier
                .offset { IntOffset(0, (itemH * pill.value).roundToPx()) }
                .fillMaxWidth()
                .height(itemH)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(pillColor),
        )
        Column {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(itemH)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            if (i != selected) haptics.select()
                            onSelect(i)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Symbol(if (on) tab.selectedIcon else tab.icon, if (on) c.blue else c.label, size = 25.dp)
                    Spacer(Modifier.height(3.dp))
                    Txt(tab.title, Glass.type.caption2, if (on) c.blue else c.label, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Top bar over scrolling content. At the top it's invisible and the page
 * shows its large title; once content scrolls under it, an inline title fades
 * in over frosted glass with iOS 27's "hard" scroll edge — a crisp hairline
 * rather than a soft fade.
 */
@Composable
fun TopBar(
    title: String,
    scrolled: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val c = Glass.colors
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val p = scrolled.coerceIn(0f, 1f)
    val style = GlassStyle.resolve((LocalGlassAppearance.current + 0.25f).coerceAtMost(1f), c.dark).copy(lens = false)
    Box(modifier.fillMaxWidth().height(status + 52.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = p }
                .liquidGlass(backdrop, RectangleShape, style.copy(rimLight = Color.Transparent, edgeShade = Color.Transparent))
                .drawBehind {
                    drawRect(c.separator, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx()))
                },
        )
        Txt(
            title,
            Glass.type.headline,
            c.label,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 15.dp)
                .graphicsLayer { alpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f) },
            maxLines = 1,
        )
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 4.dp)) { trailing() }
    }
}

/** The large title as it sits at the top of a page. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp)) {
        Txt(title, Glass.type.largeTitle)
        if (subtitle != null) Txt(subtitle, Glass.type.subhead, Glass.colors.secondaryLabel, Modifier.padding(top = 2.dp))
    }
}

/**
 * A slow aurora behind everything: the "environment" the glass refracts.
 * Rendered by a tiny AGSL field, updated at ~30 fps so the glass above it
 * isn't re-blurred every display frame.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val c = Glass.colors
    val shader = remember { runCatching { RuntimeShader(AURORA_AGSL) }.getOrNull() }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time = (now - start) / 1e9f }
            delay(33)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(c.groupedBackground)
            .drawBehind {
                val s = shader ?: return@drawBehind
                s.setFloatUniform("resolution", size.width, size.height)
                s.setFloatUniform("time", time)
                s.setFloatUniform("dark", if (c.dark) 1f else 0f)
                drawRect(ShaderBrush(s))
            },
    )
}

private const val AURORA_AGSL = """
uniform float2 resolution;
uniform float time;
uniform float dark;

half3 blob(float2 uv, float2 c, float r, half3 col) {
    float d = length(uv - c);
    return col * half(exp(-d * d / (r * r)));
}

half4 main(float2 fc) {
    float2 uv = fc / resolution;
    uv.x *= resolution.x / resolution.y;
    float t = time * 0.05;
    float aspect = resolution.x / resolution.y;
    half3 col = half3(0.0);
    col += blob(uv, float2(aspect * (0.2 + 0.08 * sin(t * 1.3)), 0.12 + 0.06 * cos(t)), 0.42, half3(0.0, 0.57, 1.0));
    col += blob(uv, float2(aspect * (0.85 + 0.06 * cos(t * 0.9)), 0.22 + 0.08 * sin(t * 1.1)), 0.38, half3(0.86, 0.2, 0.95));
    col += blob(uv, float2(aspect * (0.5 + 0.12 * sin(t * 0.7)), 0.62 + 0.05 * cos(t * 1.7)), 0.45, half3(0.43, 0.49, 1.0));
    col += blob(uv, float2(aspect * (0.15 + 0.05 * cos(t * 1.5)), 0.95), 0.36, half3(0.0, 0.85, 0.76));
    col += blob(uv, float2(aspect * (0.9 + 0.04 * sin(t * 1.2)), 0.9 + 0.04 * cos(t)), 0.33, half3(1.0, 0.57, 0.19));
    half3 base = dark > 0.5 ? half3(0.012, 0.014, 0.035) : half3(0.95, 0.95, 0.97);
    half k = dark > 0.5 ? 0.42 : 0.30;
    half3 outc = dark > 0.5 ? base + col * k : mix(base, base * (half3(1.0) - col * 0.25) + col * 0.12, 0.9);
    return half4(outc, 1.0);
}
"""

/** A glass banner that drops in from the top, for short confirmations. */
@Composable
fun Banner(text: String?, backdrop: Backdrop, icon: ImageVector = Symbols.Check, modifier: Modifier = Modifier) {
    val c = Glass.colors
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier.fillMaxWidth().padding(top = status + 8.dp),
        enter = slideInVertically(Motion.bouncy()) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(Motion.smooth()) { -it * 2 } + fadeOut(),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .widthIn(max = 420.dp)
                    .liquidGlass(backdrop, CircleShape, GlassStyle.resolve(LocalGlassAppearance.current + 0.2f, c.dark))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(icon, c.blue, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Txt(text ?: "", Glass.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), c.label)
            }
        }
    }
}

/**
 * iOS 27 sheet: floats with a gap from the screen edges and fully rounded
 * corners, frosted glass over a dimmed screen. Tap outside to dismiss.
 */
@Composable
fun GlassSheet(visible: Boolean, onDismiss: () -> Unit, backdrop: Backdrop, content: @Composable () -> Unit) {
    val c = Glass.colors
    val dim by animateFloatAsState(if (visible) 1f else 0f, Motion.smooth(), label = "dim")
    if (dim > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f * dim))
                .clickable(remember { MutableInteractionSource() }, null, enabled = visible) { onDismiss() },
        )
    }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(Motion.snappy()) { it },
        exit = slideOutVertically(Motion.smooth()) { it },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = nav + 8.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .liquidGlass(backdrop, RoundedCornerShape(38.dp), GlassStyle.resolve(0.85f, c.dark).copy(lens = false))
                    .clickable(remember { MutableInteractionSource() }, null) { },
            ) { content() }
        }
    }
}
