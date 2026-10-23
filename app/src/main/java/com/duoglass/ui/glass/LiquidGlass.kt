package com.duoglass.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min

/**
 * Something glass can see through: content recorded once per frame into a
 * GraphicsLayer (a RenderNode) that any number of glass elements then sample.
 * Glass must never sit *inside* the content of the backdrop it samples.
 */
@Stable
class Backdrop internal constructor(val layer: GraphicsLayer) {
    /** Window position of the recorded content's origin. */
    var origin by mutableStateOf(Offset.Zero)
        internal set
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Records this content into [backdrop] (and draws it as normal). */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier =
    onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }

/**
 * One look for a glass element, already resolved for light/dark and the app's
 * Liquid Glass appearance (see [GlassStyle.resolve]).
 */
@Stable
data class GlassStyle(
    val blurDp: Float,
    val tint: Color,
    val refraction: Float,
    val bezelDp: Float,
    val dispersion: Float,
    val saturation: Float,
    val rimLight: Color,
    val edgeShade: Color,
    val lens: Boolean = true,
) {
    companion object {
        /**
         * Interpolates iOS 27's continuum from Ultra Clear (0) through the
         * default midpoint (0.5, Regular) to Fully Tinted (1). Values follow
         * published Liquid Glass emulation kits: frost 2 → 6 → 12, refraction
         * 0.8 → 0.7 → 0.6, white 8% → #4D4D52 45% → #38383D 86% (dark).
         */
        fun resolve(appearance: Float, dark: Boolean, accent: Color? = null): GlassStyle {
            val a = appearance.coerceIn(0f, 1f)
            val clearTint = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
            val regularTint = if (dark) Color(0xFF4D4D52).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.32f)
            val tintedTint = if (dark) Color(0xFF38383D).copy(alpha = 0.86f) else Color(0xFFF7F7FA).copy(alpha = 0.86f)
            val tint = if (a < 0.5f) lerp(clearTint, regularTint, a * 2f) else lerp(regularTint, tintedTint, (a - 0.5f) * 2f)
            val blur = if (a < 0.5f) 4f + (10f - 4f) * a * 2f else 10f + (20f - 10f) * (a - 0.5f) * 2f
            val refraction = if (a < 0.5f) 0.8f - 0.1f * a * 2f else 0.7f - 0.1f * (a - 0.5f) * 2f
            val withAccent = accent?.let { lerp(tint, it.copy(alpha = 0.78f), 0.85f) } ?: tint
            return GlassStyle(
                blurDp = blur,
                tint = withAccent,
                refraction = refraction,
                bezelDp = 14f,
                dispersion = 0.06f,
                saturation = 1.45f,
                rimLight = Color.White.copy(alpha = if (dark) 0.30f else 0.75f),
                edgeShade = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
            )
        }
    }
}

/** The app-wide appearance (iOS 27 Liquid Glass slider), 0 Ultra Clear … 1 Fully Tinted. */
val LocalGlassAppearance = staticCompositionLocalOf { 0.5f }

/**
 * Draws a pane of liquid glass in [shape] behind this element's content,
 * seeing [backdrop] through it:
 *  - frost: a real blur of what's behind (RenderEffect);
 *  - lensing: an AGSL pass bends the rim outward like thick glass, with a
 *    touch of chromatic dispersion, and lifts saturation (vibrancy);
 *  - tint, then iOS 27's crisp definition: a hairline border plus static
 *    light on the top and bottom edges (no motion-driven shimmer).
 *
 * [pressed] (0..1) brightens the glass, the way it "energizes" under a finger.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    style: GlassStyle,
    pressed: () -> Float = { 0f },
): Modifier = this then GlassElement(backdrop, shape, style, pressed)

private data class GlassElement(
    val backdrop: Backdrop,
    val shape: Shape,
    val style: GlassStyle,
    val pressed: () -> Float,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(backdrop, shape, style, pressed)

    override fun update(node: GlassNode) {
        node.backdrop = backdrop
        node.shape = shape
        node.style = style
        node.pressed = pressed
        node.invalidateDraw()
    }
}

private class GlassNode(
    var backdrop: Backdrop,
    var shape: Shape,
    var style: GlassStyle,
    var pressed: () -> Float,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var layer: GraphicsLayer? = null
    private val lens: RuntimeShader? = runCatching { RuntimeShader(LENS_AGSL) }.getOrNull()
    private var position = Offset.Zero

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInWindow()
        if (p != position) {
            position = p
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val layer = layer
        val w = size.width
        val h = size.height
        if (layer == null || w < 1f || h < 1f) {
            drawContent()
            return
        }
        val style = style
        val blurPx = style.blurDp.dp.toPx()
        val pad = ceil(blurPx * 2f + style.bezelDp.dp.toPx())
        val outline = shape.createOutline(size, layoutDirection, this)
        val radius = cornerRadius(outline, size)
        val rel = position - backdrop.origin
        val source = backdrop.layer

        layer.record(size = IntSize(ceil(w + pad * 2).toInt(), ceil(h + pad * 2).toInt())) {
            translate(-rel.x + pad, -rel.y + pad) { drawLayer(source) }
        }
        val p = pressed().coerceIn(0f, 1f)
        val blur = if (blurPx > 0.5f) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP) else null
        val lens = lens
        val effect = if (lens != null && style.lens) {
            lens.setFloatUniform("size", w, h)
            lens.setFloatUniform("pad", pad)
            lens.setFloatUniform("radius", radius)
            lens.setFloatUniform("bezel", min(style.bezelDp.dp.toPx(), min(w, h) * 0.5f))
            lens.setFloatUniform("refraction", style.refraction)
            lens.setFloatUniform("dispersion", style.dispersion)
            lens.setFloatUniform("saturation", style.saturation)
            lens.setFloatUniform("boost", p)
            val l = RenderEffect.createRuntimeShaderEffect(lens, "content")
            if (blur != null) RenderEffect.createChainEffect(l, blur) else l
        } else {
            blur
        }
        layer.renderEffect = effect?.asComposeRenderEffect()

        clipPath(outlinePath(outline)) {
            translate(-pad, -pad) { drawLayer(layer) }
        }
        drawOutline(outline, style.tint)
        if (p > 0f) drawOutline(outline, Color.White.copy(alpha = 0.10f * p))

        // iOS 27 definition: crisp hairline + static top/bottom edge light.
        val hair = 1.dp.toPx()
        drawOutline(outline, style.edgeShade, style = Stroke(hair))
        drawOutline(
            outline,
            Brush.verticalGradient(
                0f to style.rimLight,
                0.22f to Color.Transparent,
                0.78f to Color.Transparent,
                1f to style.rimLight.copy(alpha = style.rimLight.alpha * 0.55f),
            ),
            style = Stroke(hair * 1.2f),
        )
        drawContent()
    }
}

private fun cornerRadius(outline: Outline, size: Size): Float = when (outline) {
    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
    is Outline.Rectangle -> 0f
    is Outline.Generic -> min(size.width, size.height) * 0.5f
}

private fun outlinePath(outline: Outline): Path = when (outline) {
    is Outline.Generic -> outline.path
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
}

/**
 * The lens. Runs on the blurred backdrop in layer space (element + [pad] on
 * every side). Inside the rim band of width [bezel] the sample point is pushed
 * outward along the edge normal with a circular (thick-glass) profile, so the
 * rim shows what lies just beyond the element bent into it.
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float pad;
uniform float radius;
uniform float bezel;
uniform float refraction;
uniform float dispersion;
uniform float saturation;
uniform float boost;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 c = float2(pad) + halfSize;
    float2 p = coord - c;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = sdRoundRect(p, halfSize, r);

    float2 sp = coord;
    float2 n = float2(0.0);
    float amount = 0.0;
    if (d < 0.0 && d > -bezel) {
        float e = 1.0;
        float dx = sdRoundRect(p + float2(e, 0.0), halfSize, r) - sdRoundRect(p - float2(e, 0.0), halfSize, r);
        float dy = sdRoundRect(p + float2(0.0, e), halfSize, r) - sdRoundRect(p - float2(0.0, e), halfSize, r);
        n = normalize(float2(dx, dy) + 1e-5);
        float t = 1.0 + d / bezel;              // 0 inner edge of the rim .. 1 at the edge
        float circle = 1.0 - sqrt(max(1.0 - t * t, 0.0));
        amount = circle * bezel * refraction;
        sp = coord + n * amount;
    }

    half4 base;
    if (amount > 0.01) {
        half rC = content.eval(coord + n * amount * (1.0 + dispersion)).r;
        half4 gC = content.eval(sp);
        half bC = content.eval(coord + n * amount * (1.0 - dispersion)).b;
        base = half4(rC, gC.g, bC, gC.a);
    } else {
        base = content.eval(sp);
    }

    // Premultiplied throughout: where the backdrop has nothing (beyond the
    // window edge) the glass stays clear instead of turning black.
    half luma = dot(base.rgb, half3(0.2126, 0.7152, 0.0722));
    half3 col = mix(half3(luma), base.rgb, half(saturation));
    col = col * half(1.0 + 0.06 * boost) + half3(0.04 * boost) * base.a;
    return half4(clamp(col, half3(0.0), half3(base.a)), base.a);
}
"""
