package com.duoglass.core.fold

import com.duoglass.core.settings.GlassConfig
import com.duoglass.core.settings.GlassSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldModelTest {

    @Test
    fun innerIsClearAtFlatAndFullAtSwap() {
        val c = GlassConfig(learnedSwapAngle = 14f)
        assertEquals(0f, FoldModel.innerTilt(175f, c), 0f)
        assertEquals(FoldModel.peakTilt(c), FoldModel.innerTilt(14f, c), 0.001f)
        assertTrue(FoldModel.innerTilt(90f, c) in 1f..FoldModel.MAX_TILT)
    }

    @Test
    fun coverReachesFullFrostExactlyAtTheLearnedSwap() {
        val c = GlassConfig(learnedSwapAngle = 14f)
        assertEquals(0f, FoldModel.coverTilt(0f, c), 0f)
        assertEquals(FoldModel.peakTilt(c), FoldModel.coverTilt(14f, c), 0.001f)
    }

    @Test
    fun tiltIsMonotonicWhileUnfolding() {
        val c = GlassConfig()
        var prev = Float.MAX_VALUE
        var a = FoldModel.swapAngle(c)
        while (a <= 180f) {
            val t = FoldModel.innerTilt(a, c)
            assertTrue("tilt rose at $a", t <= prev + 1e-4f)
            prev = t
            a += 0.5f
        }
    }

    @Test
    fun unknownSwapUsesDefault() {
        assertEquals(FoldModel.DEFAULT_SWAP, FoldModel.swapAngle(GlassConfig()), 0f)
    }

    @Test
    fun learningSettlesAndIgnoresNonsense() {
        GlassSettings.update { it.copy(learnedSwapAngle = Float.NaN, swapSamples = 0) }
        GlassSettings.learnSwap(12f)
        assertEquals(12f, GlassSettings.value.learnedSwapAngle, 0.001f)
        GlassSettings.learnSwap(200f) // not a plausible swap angle
        assertEquals(12f, GlassSettings.value.learnedSwapAngle, 0.001f)
        repeat(20) { GlassSettings.learnSwap(16f) }
        assertEquals(16f, GlassSettings.value.learnedSwapAngle, 0.3f)
    }
}
