package com.duoglass.core.fold

import android.view.Surface
import com.duoglass.core.settings.AxisMode
import com.duoglass.core.settings.GlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldGeometryTest {

    private val config = GlassConfig()

    @Test
    fun pixelFoldInnerAtRotation0SplitsWidth() {
        // Original Pixel Fold inner panel: 2208×1840, natural landscape, crease vertical.
        val f = FoldGeometry.inner(2208f, 1840f, Surface.ROTATION_0, config)
        assertTrue(f.splitsX)
        assertEquals(1104f, f.position, 0.01f)
        assertEquals(-1, f.movingSide)
    }

    @Test
    fun pixelFoldInnerRotatedSplitsHeight() {
        val f = FoldGeometry.inner(1840f, 2208f, Surface.ROTATION_90, config)
        assertFalse(f.splitsX)
        assertEquals(1104f, f.position, 0.01f)
        // Natural left half is now at the bottom.
        assertEquals(+1, f.movingSide)
    }

    @Test
    fun onePlusOpenPortraitNaturalAlsoSplitsWidth() {
        // OnePlus Open inner 2268×2440 (portrait natural): the crease is still vertical at rotation 0.
        val f = FoldGeometry.inner(2268f, 2440f, Surface.ROTATION_0, config)
        assertTrue(f.splitsX)
        assertEquals(1134f, f.position, 0.01f)
    }

    @Test
    fun overrideForcesHorizontal() {
        val f = FoldGeometry.inner(2208f, 1840f, Surface.ROTATION_0, config.copy(axisMode = AxisMode.HORIZONTAL))
        assertFalse(f.splitsX)
        assertEquals(920f, f.position, 0.01f)
    }

    @Test
    fun learnRoundTripsEveryRotation() {
        for (vertical in listOf(true, false)) {
            val c = config.copy(learnedFoldVertical = vertical, learnedFoldCenter = 0.42f)
            for (rot in 0..3) {
                val (w, h) = if (rot % 2 == 0) 2208f to 1840f else 1840f to 2208f
                val f = FoldGeometry.inner(w, h, rot, c)
                val (v, frac) = FoldGeometry.learn(f.splitsX, f.position, w, h, rot)
                assertEquals("rot $rot vertical $vertical", vertical, v)
                assertEquals("rot $rot vertical $vertical", 0.42f, frac, 0.0001f)
            }
        }
    }

    @Test
    fun coverSpineFollowsRotation() {
        val r0 = FoldGeometry.cover(1080f, 2092f, Surface.ROTATION_0, config)
        assertTrue(r0.splitsX)
        assertEquals(0f, r0.position, 0f)
        assertEquals(+1, r0.movingSide)
        val r90 = FoldGeometry.cover(2092f, 1080f, Surface.ROTATION_90, config)
        assertFalse(r90.splitsX)
        assertEquals(1080f, r90.position, 0f) // natural left edge is now the bottom
        assertEquals(-1, r90.movingSide)
        val mirrored = FoldGeometry.cover(1080f, 2092f, Surface.ROTATION_0, config.copy(coverFrostFromRight = false))
        assertEquals(1080f, mirrored.position, 0f)
        assertEquals(-1, mirrored.movingSide)
    }
}
