package com.github.aw00987.tokyosandwich.core.ui

import kotlin.math.atan2
import kotlin.math.hypot

/** The smallest slice of a ring chart, in degrees, so that a tiny share can still be seen and touched. */
const val MIN_SWEEP = 8f

/**
 * The angle of each slice of a ring, in degrees, 360 in all: by its share of [values], but never under [minSweep];
 * what the small slices gain is taken from the others in proportion. Values of 0 or less get no slice; with nothing
 * above 0 there is no ring (all 0). When the slices are too many for [minSweep] each, they are equal.
 */
fun donutSweeps(values: List<Double>, minSweep: Float = MIN_SWEEP): List<Float> {
    val shown = values.map { it.coerceAtLeast(0.0) }
    val count = shown.count { it > 0 }
    if (count == 0) return List(values.size) { 0f }
    if (count * minSweep >= 360f) return shown.map { if (it > 0) 360f / count else 0f }
    // Slices raised to the minimum; the rest share what is left, which can push more of them under it.
    val raised = BooleanArray(shown.size)
    while (true) {
        val free = 360.0 - raised.count { it } * minSweep
        val freeTotal = shown.indices.sumOf { if (raised[it]) 0.0 else shown[it] }
        val under = shown.indices.filter {
            !raised[it] && shown[it] > 0 && shown[it] / freeTotal * free < minSweep
        }
        if (under.isEmpty()) {
            return shown.indices.map {
                when {
                    raised[it] -> minSweep
                    shown[it] > 0 -> (shown[it] / freeTotal * free).toFloat()
                    else -> 0f
                }
            }
        }
        under.forEach { raised[it] = true }
    }
}

/** What a point of a ring chart is on. */
sealed interface DonutHit {
    data object Outside : DonutHit

    /** The hole in the middle. */
    data object Centre : DonutHit
    data class Slice(val index: Int) : DonutHit
}

/**
 * What is at ([dx], [dy]) from the centre of a ring with radii [inner] and [outer], whose slices ([sweeps], degrees)
 * start at the top and go clockwise.
 */
fun donutHit(dx: Float, dy: Float, outer: Float, inner: Float, sweeps: List<Float>): DonutHit {
    val radius = hypot(dx, dy)
    if (radius > outer) return DonutHit.Outside
    if (radius < inner) return DonutHit.Centre
    // atan2 is 0 at three o'clock and grows clockwise (y points down); the ring starts at twelve.
    val angle = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0) % 360.0 + 360.0) % 360.0
    var end = 0.0
    sweeps.forEachIndexed { index, sweep ->
        end += sweep
        if (sweep > 0f && angle < end) return DonutHit.Slice(index)
    }
    // Rounding at the very end of the last slice.
    val last = sweeps.indexOfLast { it > 0f }
    return if (last >= 0) DonutHit.Slice(last) else DonutHit.Outside
}
