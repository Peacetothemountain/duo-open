package com.duoglass.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duoglass.ui.LocalCrease
import com.duoglass.ui.components.LargeTitle

/** Layout facts every page needs. */
data class PageLayout(
    /** Wide window (the unfolded inner screen): two panes, vertical tab bar. */
    val expanded: Boolean,
    /** Leading inset taken by the vertical tab bar. */
    val leading: Dp,
)

val LocalPageLayout = staticCompositionLocalOf { PageLayout(expanded = false, leading = 0.dp) }

/**
 * A scrolling page: room for the top bar, the large title, then either one
 * column (cover screen / phone) or two panes split at the crease.
 */
@Composable
fun Page(
    scroll: ScrollState,
    title: String,
    subtitle: String? = null,
    paneA: @Composable ColumnScope.() -> Unit,
    paneB: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val layout = LocalPageLayout.current
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = layout.leading),
    ) {
        Spacer(Modifier.height(status + 40.dp))
        LargeTitle(title, subtitle)
        if (layout.expanded && paneB != null) {
            DuoPanes(paneA, paneB)
        } else {
            Column(Modifier.fillMaxWidth()) {
                paneA()
                paneB?.invoke(this)
            }
        }
        Spacer(Modifier.height(nav + if (layout.expanded) 24.dp else 100.dp))
    }
}

/**
 * Two panes either side of the hinge (iOS 27's two-screen "Duo" layouts):
 * split exactly at the crease when the window spans it, else in the middle.
 */
@Composable
fun DuoPanes(a: @Composable ColumnScope.() -> Unit, b: @Composable ColumnScope.() -> Unit) {
    val crease = LocalCrease.current
    val density = LocalDensity.current
    var rowX by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { rowX = it.positionInWindow().x }) {
        val total = constraints.maxWidth.toFloat()
        val gapPx = with(density) { 24.dp.toPx() } + (crease?.width ?: 0f)
        var split = if (crease != null && crease.height >= crease.width) crease.center.x - rowX else total / 2f
        val minPane = with(density) { 260.dp.toPx() }
        if (split - gapPx / 2 < minPane || total - split - gapPx / 2 < minPane) split = total / 2f
        val left = with(density) { (split - gapPx / 2).toDp() }
        val right = with(density) { (total - split - gapPx / 2).toDp() }
        Row {
            Column(Modifier.width(left)) { a() }
            Spacer(Modifier.width(with(density) { gapPx.toDp() }))
            Column(Modifier.width(right)) { b() }
        }
    }
}
