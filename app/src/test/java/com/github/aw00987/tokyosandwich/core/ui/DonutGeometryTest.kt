package com.github.aw00987.tokyosandwich.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonutGeometryTest {
    private fun sweeps(vararg values: Double) = donutSweeps(values.toList())

    @Test
    fun slicesFollowTheShares() {
        assertEquals(listOf(180f, 90f, 90f), sweeps(2.0, 1.0, 1.0))
        assertEquals(listOf(360f), sweeps(5.0))
    }

    @Test
    fun aSmallShareKeepsTheMinimumAndTheOthersGiveItUp() {
        val result = sweeps(600.0, 399.0, 1.0)
        assertEquals(360f, result.sum(), 1e-3f)
        assertEquals(MIN_SWEEP, result[2], 1e-6f)
        // The other two keep their proportion.
        assertEquals(600.0 / 399.0, result[0].toDouble() / result[1], 1e-4)
    }

    @Test
    fun raisingOneSliceCanPushAnotherUnderTheMinimum() {
        // 2.25% of 360° is 8.1°, but only 352° are left to share once the smallest slice has its 8°.
        val result = sweeps(97.74, 2.25, 0.01)
        assertEquals(360f, result.sum(), 1e-3f)
        assertEquals(MIN_SWEEP, result[1], 1e-6f)
        assertEquals(MIN_SWEEP, result[2], 1e-6f)
        assertEquals(344f, result[0], 1e-3f)
    }

    @Test
    fun nineSlicesAlwaysFit() {
        val result = donutSweeps(listOf(1_000_000.0) + List(8) { 1.0 })
        assertEquals(360f, result.sum(), 1e-3f)
        assertTrue(result.all { it >= MIN_SWEEP - 1e-4f })
    }

    @Test
    fun nothingAboveZeroIsNoRingAndZeroIsNoSlice() {
        assertEquals(listOf(0f, 0f), sweeps(0.0, -3.0))
        assertEquals(emptyList<Float>(), sweeps())
        assertEquals(listOf(0f, 360f), sweeps(0.0, 4.0))
    }

    @Test
    fun tooManySlicesForTheMinimumAreEqual() {
        val result = donutSweeps(List(60) { it + 1.0 })
        assertTrue(result.all { it == 6f })
    }

    @Test
    fun aPointIsOnASliceTheCentreOrOutside() {
        val quarters = listOf(90f, 90f, 180f)
        fun hit(dx: Float, dy: Float) = donutHit(dx, dy, outer = 100f, inner = 60f, quarters)
        assertEquals(DonutHit.Centre, hit(0f, 0f))
        assertEquals(DonutHit.Centre, hit(30f, -30f))
        assertEquals(DonutHit.Outside, hit(80f, 80f))
        // Clockwise from twelve o'clock: up-right, down-right, then the left half.
        assertEquals(DonutHit.Slice(0), hit(56f, -56f))
        assertEquals(DonutHit.Slice(1), hit(56f, 56f))
        assertEquals(DonutHit.Slice(2), hit(-56f, 56f))
        assertEquals(DonutHit.Slice(2), hit(-56f, -56f))
        assertEquals(DonutHit.Slice(0), hit(1f, -80f))
        assertEquals(DonutHit.Slice(2), hit(-1f, -80f))
    }

    @Test
    fun slicesWithoutAnAngleAreNeverHit() {
        assertEquals(DonutHit.Slice(1), donutHit(1f, -80f, 100f, 60f, listOf(0f, 360f)))
        assertEquals(DonutHit.Outside, donutHit(1f, -80f, 100f, 60f, listOf(0f, 0f)))
    }
}
