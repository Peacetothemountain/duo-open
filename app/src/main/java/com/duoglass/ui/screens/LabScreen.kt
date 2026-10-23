package com.duoglass.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duoglass.core.fold.FoldModel
import com.duoglass.core.hinge.HingeSensor
import com.duoglass.core.settings.GlassConfig
import com.duoglass.core.settings.GlassSettings
import com.duoglass.core.trace.FoldTrace
import com.duoglass.overlay.OverlayState
import com.duoglass.ui.AppModel
import com.duoglass.ui.components.ListRow
import com.duoglass.ui.components.Section
import com.duoglass.ui.components.Symbols
import com.duoglass.ui.components.Txt
import com.duoglass.ui.theme.Glass
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun LabScreen(scroll: ScrollState, model: AppModel, config: GlassConfig) {
    val c = Glass.colors
    val context = LocalContext.current
    val version by FoldTrace.version.collectAsStateWithLifecycle()
    val connected by OverlayState.connected.collectAsStateWithLifecycle()
    // Tick so the graph scrolls even when the hinge is still.
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.uptimeMillis()
            delay(50)
        }
    }
    Page(
        scroll = scroll,
        title = "Lab",
        subtitle = "What the hinge and the glass are doing",
        paneA = {
            Section(header = "Last 12 seconds", footer = "Blue: hinge angle. Purple: glass tilt on screen. Shaded: inner screen lit.") {
                val samples = remember(version) { FoldTrace.samples() }
                Canvas(Modifier.fillMaxWidth().height(180.dp).padding(16.dp)) {
                    val window = 12_000f
                    val w = size.width
                    val h = size.height
                    fun x(t: Long) = w - (now - t) / window * w
                    for (i in 0..4) {
                        val y = h * i / 4f
                        drawLine(c.separator, Offset(0f, y), Offset(w, y), 0.5.dp.toPx())
                    }
                    val swapY = h - FoldModel.swapAngle(config) / 180f * h
                    drawLine(c.orange.copy(alpha = 0.7f), Offset(0f, swapY), Offset(w, swapY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    // The last reading before the window anchors the left edge, so a
                    // still hinge draws as a flat line instead of nothing.
                    val inWindow = samples.filter { now - it.t <= window }
                    val anchor = samples.lastOrNull { now - it.t > window }?.copy(t = (now - window).toLong())
                    val visible = listOfNotNull(anchor) + inWindow
                    for (s in visible) if (s.inner) drawLine(c.blue.copy(alpha = 0.06f), Offset(x(s.t), 0f), Offset(x(s.t), h), 3.dp.toPx())
                    val angle = Path()
                    val tilt = Path()
                    visible.forEachIndexed { i, s ->
                        val px = x(s.t)
                        val ay = h - s.angle / 180f * h
                        val ty = h - s.tilt / FoldModel.MAX_TILT * h
                        if (i == 0) { angle.moveTo(px, ay); tilt.moveTo(px, ty) } else { angle.lineTo(px, ay); tilt.lineTo(px, ty) }
                    }
                    if (visible.isNotEmpty()) {
                        val last = visible.last()
                        angle.lineTo(w, h - last.angle / 180f * h)
                        tilt.lineTo(w, h - last.tilt / FoldModel.MAX_TILT * h)
                    }
                    drawPath(tilt, c.purple, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(angle, c.blue, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Section(header = "Readings") {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Angle", if (model.hingeAngle.isNaN()) "—" else "${model.hingeAngle.roundToInt()}°")
                    Metric("Rate", "${HingeSensor.rateHz.roundToInt()} Hz")
                    Metric("Swap", "${FoldModel.swapAngle(config).roundToInt()}°")
                    Metric("Opens", "${config.swapSamples}")
                }
                ListRow("Sensor", subtitle = HingeSensor.statusLine(), divider = true)
                ListRow("Service", value = if (connected) "Connected" else "Not running", divider = false)
            }
        },
        paneB = {
            Section(header = "Events") {
                val events = remember(version) { FoldTrace.events().takeLast(14).reversed() }
                if (events.isEmpty()) {
                    Txt("Nothing yet — fold the phone.", Glass.type.subhead, c.secondaryLabel, Modifier.padding(16.dp))
                }
                Column(Modifier.padding(vertical = 6.dp)) {
                    for (e in events) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Txt("${((now - e.t) / 1000f).let { if (it < 60) "%.1fs".format(it) else "${(it / 60).roundToInt()}m" }}", Glass.type.caption1.copy(fontFeatureSettings = "tnum"), c.tertiaryLabel, Modifier.width(48.dp))
                            Txt(e.text, Glass.type.footnote, c.label, Modifier.weight(1f))
                        }
                    }
                }
            }
            Section(footer = "The report lists every hinge sensor, the learned values and the recent events — paste it into a GitHub issue.") {
                ListRow("Copy report", icon = Symbols.Copy, iconColor = c.blue, onClick = {
                    val text = buildString {
                        appendLine("Duo Glass report")
                        append(HingeSensor.report())
                        appendLine("config=${GlassSettings.value}")
                        append(FoldTrace.dump())
                    }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Duo Glass report", text))
                    model.banner = "Report copied"
                })
                ListRow("Forget learned values", icon = Symbols.Reset, iconColor = c.red, titleColor = c.red, divider = false, onClick = {
                    GlassSettings.update { it.copy(learnedSwapAngle = Float.NaN, swapSamples = 0, learnedFoldVertical = true, learnedFoldCenter = 0.5f) }
                    model.banner = "Learned values cleared"
                })
            }
        },
    )
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(value, Glass.type.title2.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.size(2.dp))
        Txt(label, Glass.type.caption1, Glass.colors.secondaryLabel)
    }
}
