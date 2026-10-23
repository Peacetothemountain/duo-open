package com.duoglass.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A small symbol set drawn in the SF Symbols manner (24-unit grid, round caps
 * and joins, 1.8-unit regular weight), tinted at the call site.
 */
object Symbols {
    private fun symbol(name: String, vararg strokes: String, fills: List<String> = emptyList(), weight: Float = 1.8f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in fills) {
                addPath(addPathNodes(d), fill = SolidColor(Color.Black))
            }
            for (d in strokes) {
                addPath(
                    addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = weight,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** Two panes meeting at a crease, the left one tilted toward you. */
    val Fold = symbol(
        "fold",
        "M12 5.2v13.6",
        "M12 5.2 6 3.6C4.9 3.3 4 4 4 5.1v13.8c0 1.1.9 1.8 2 1.5l6-1.6",
        "M12 5.2h6c1.1 0 2 .9 2 2v9.6c0 1.1-.9 2-2 2h-6",
    )
    val FoldFill = symbol(
        "fold.fill",
        "M12 5.2v13.6",
        "M12 5.2h6c1.1 0 2 .9 2 2v9.6c0 1.1-.9 2-2 2h-6",
        fills = listOf("M12 5.2 6 3.6C4.9 3.3 4 4 4 5.1v13.8c0 1.1.9 1.8 2 1.5l6-1.6z"),
    )
    val Sparkles = symbol(
        "sparkles",
        "M10 4.5 11.4 9 15.9 10.4 11.4 11.8 10 16.3 8.6 11.8 4.1 10.4 8.6 9z",
        "M17.5 14.2l.7 2.1 2.1.7-2.1.7-.7 2.1-.7-2.1-2.1-.7 2.1-.7z",
        "M17 3.5v3M15.5 5h3",
    )
    val SparklesFill = symbol(
        "sparkles.fill",
        "M17 3.5v3M15.5 5h3",
        fills = listOf(
            "M10 4.5 11.4 9 15.9 10.4 11.4 11.8 10 16.3 8.6 11.8 4.1 10.4 8.6 9z",
            "M17.5 14.2l.7 2.1 2.1.7-2.1.7-.7 2.1-.7-2.1-2.1-.7 2.1-.7z",
        ),
    )
    val Sliders = symbol(
        "slider.horizontal.3",
        "M3.5 7H12.5M18.5 7H20.5",
        "M12.5 7a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M3.5 17H5.5M11.5 17H20.5",
        "M5.5 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    )
    val Waveform = symbol(
        "waveform",
        "M3.5 11v2M7 8.5v7M10.5 4.5v15M14 7.5v9M17.5 9.5v5M21 11v2",
    )
    val Play = symbol("play.fill", fills = listOf("M8 5.4c0-.8.9-1.3 1.6-.9l9.6 6.2c.6.4.6 1.3 0 1.7l-9.6 6.2c-.7.4-1.6-.1-1.6-.9z"))
    val Photo = symbol(
        "photo",
        "M5.5 4.5h13a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z",
        "M3.8 16.5l4.7-4.7 4 4 2.5-2.5 5.2 5.2",
        "M14.5 9a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0",
    )
    val ChevronRight = symbol("chevron.right", "M9.5 5.5 16 12l-6.5 6.5", weight = 2.2f)
    val ChevronDown = symbol("chevron.down", "M5.5 9.5 12 16l6.5-6.5", weight = 2.2f)
    val Check = symbol("checkmark", "M5 12.6l4.4 4.4L19 7.4", weight = 2.4f)
    val Xmark = symbol("xmark", "M6.5 6.5l11 11M17.5 6.5l-11 11", weight = 2.2f)
    val Info = symbol(
        "info.circle",
        "M12 3a9 9 0 1 0 0.01 0z",
        "M12 11v5.5M10.5 16.5h3",
        fills = listOf("M12 6.6a1.2 1.2 0 1 0 .01 0z"),
    )
    val Angle = symbol(
        "angle",
        "M4 19.5h16.5",
        "M4 19.5 15.5 6.5",
        "M9.5 19.5a5.5 5.5 0 0 0-1.9-4.1",
    )
    val Copy = symbol(
        "doc.on.doc",
        "M9 8.5h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z",
        "M5 15.5V5.5a2 2 0 0 1 2-2h8",
    )
    val Reset = symbol(
        "arrow.counterclockwise",
        "M5 12a7 7 0 1 0 2.1-5",
        "M5 4.5V8.5h4",
    )
    val Hand = symbol(
        "hand.tap",
        "M9.5 13V5.8a1.5 1.5 0 0 1 3 0V11",
        "M12.5 10.5a1.5 1.5 0 0 1 3 0v1",
        "M15.5 11a1.5 1.5 0 0 1 3 0v3.5c0 3.6-2.5 6-6 6h-.6c-2 0-3.4-.8-4.6-2.4L5 14.8a1.5 1.5 0 0 1 2.3-1.9L9.5 15",
    )
    val Wallpaper = symbol(
        "photo.on.rectangle",
        "M6.5 3.5h11a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2z",
        "M4.5 15.5l4-4 3.5 3.5 2-2 5.5 5.5",
    )
    val Shield = symbol(
        "accessibility",
        "M12 3a1.6 1.6 0 1 0 .01 0z",
        "M5 7.8c2.3.7 4.6 1 7 1s4.7-.3 7-1",
        "M12 8.8v4.7l-3 7M12 13.5l3 7",
    )
    val Bolt = symbol("bolt", "M13 3 5.5 13.5H12l-1 7.5 7.5-10.5H12z")
    val Rotate = symbol(
        "rotate",
        "M7.5 3.5h9a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2z",
        "M10.5 17.5h3",
    )
    val Haptic = symbol(
        "iphone.radiowaves",
        "M9 4.5h6a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5H9A1.5 1.5 0 0 1 7.5 18V6A1.5 1.5 0 0 1 9 4.5z",
        "M4.5 9v6M19.5 9v6",
    )
}
