package com.duoglass.ui

import android.graphics.RenderEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.duoglass.core.fold.FoldLine
import com.duoglass.core.render.FoldShader
import com.duoglass.core.settings.GlassConfig
import com.duoglass.wallpaper.WallpaperImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun rememberWallpaper(version: Long): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val image by produceState<ImageBitmap?>(null, version) {
        value = withContext(Dispatchers.IO) { WallpaperImage.load(context, version).asImageBitmap() }
    }
    return image
}

/**
 * A miniature of the inner screen — your wallpaper plus a ghost home screen —
 * bent through the same fold shader the overlay uses. The crease is vertical
 * through the middle, as on a phone held open like a book. Density is scaled
 * so frost and darkening match what the real screen shows.
 *
 * @param tilt Pane tilt, read at draw time (no recomposition per frame).
 */
@Composable
fun FoldPreview(
    image: ImageBitmap?,
    tilt: () -> Float,
    config: GlassConfig,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
) {
    val context = LocalContext.current
    val shader = remember { FoldShader.create(context) }
    val screenPxPerMm = remember { FoldShader.pxPerMm(context) }
    val screenWidthPx = remember { context.resources.displayMetrics.widthPixels.coerceAtLeast(1).toFloat() }
    Box(
        modifier.graphicsLayer {
            val s = shader ?: return@graphicsLayer
            val t = tilt()
            val w = size.width
            val h = size.height
            if (t < 0.05f || w < 2f || h < 2f) {
                renderEffect = null
                return@graphicsLayer
            }
            val pxPerMm = screenPxPerMm * (w / screenWidthPx).coerceIn(0.15f, 1f)
            val fold = FoldLine(splitsX = true, position = w * 0.5f, eyePos = w * 0.5f, movingSide = config.movingSide)
            FoldShader.setUniforms(s, w, h, t, config, pxPerMm, fold)
            renderEffect = RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF05060F))
            val img = image
            if (img != null) {
                val scale = maxOf(size.width / img.width, size.height / img.height)
                val dw = img.width * scale
                val dh = img.height * scale
                drawImage(
                    img,
                    dstOffset = IntOffset(((size.width - dw) / 2).roundToInt(), ((size.height - dh) / 2).roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                )
            }
            if (showIcons) {
                // A ghost home screen so the frost reads as "the whole screen".
                val cols = 6
                val rows = 4
                val cell = size.width / (cols + 1)
                val icon = cell * 0.56f
                val top = size.height * 0.16f
                for (r in 0 until rows) for (col in 0 until cols) {
                    val cx = cell * (col + 1)
                    val cy = top + r * cell * 1.08f
                    drawRoundRect(
                        Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(cx - icon / 2, cy - icon / 2),
                        size = Size(icon, icon),
                        cornerRadius = CornerRadius(icon * 0.26f),
                    )
                }
                val dockY = size.height * 0.86f
                drawRoundRect(
                    Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(size.width * 0.18f, dockY - cell * 0.5f),
                    size = Size(size.width * 0.64f, cell),
                    cornerRadius = CornerRadius(cell * 0.5f),
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(size.width * 0.06f, size.height * 0.04f),
                    size = Size(size.width * 0.12f, size.height * 0.012f),
                    cornerRadius = CornerRadius(8f),
                )
            }
        }
    }
}
