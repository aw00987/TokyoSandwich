package com.github.aw00987.tokyosandwich.water.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Usage and cost of one calendar month. The meter is read only every two months, so these are the billing
 * periods' daily averages summed over the month ([estimated]). [coveredDays] < [totalDays] means part of the
 * month has no billed data yet (or predates the contract).
 */
data class UsagePoint(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val usageM3: Double,
    val cost: CostBreakdown,
    val coveredDays: Int,
    val totalDays: Int,
    val estimated: Boolean = true,
    val approximate: Boolean = false,
) {
    val partial: Boolean get() = coveredDays < totalDays
}

object UsageAggregator {
    private data class Day(val usageM3: Double, val cost: CostBreakdown, val approximate: Boolean)

    /** Spreads each period's usage and cost evenly over its days. Later periods win if dates overlap. */
    private fun daily(periods: List<WaterPeriod>): Map<LocalDate, Day> {
        val days = HashMap<LocalDate, Day>()
        for (period in periods.sortedBy { it.start }) {
            val count = period.days.takeIf { it > 0 } ?: continue
            val day =
                Day(period.usageM3 / count, period.cost / count.toDouble(), period.approximate)
            var date = period.start
            while (date <= period.endInclusive) {
                days[date] = day
                date = date.plusDays(1)
            }
        }
        return days
    }

    /**
     * All fully billed months, oldest first. A partial month at either end is dropped, since its total is not
     * meaningful: the latest while billing does not cover all of it yet, the oldest because the history starts in it.
     * Saved records are merged by month, so a partial oldest month would overwrite the full one saved when the history
     * still reached back further. (A contract that started mid-month loses that first month.)
     */
    fun months(periods: List<WaterPeriod>): List<UsagePoint> {
        val days = daily(periods)
        if (days.isEmpty()) return emptyList()
        val last = days.keys.max()
        val result = mutableListOf<UsagePoint>()
        var start = days.keys.min().withDayOfMonth(1)
        while (start <= last) {
            point(days, start, start.with(TemporalAdjusters.lastDayOfMonth()))?.let(result::add)
            start = start.plusMonths(1)
        }
        return result.dropWhile { it.partial }.dropLastWhile { it.partial }
    }

    private fun point(days: Map<LocalDate, Day>, start: LocalDate, end: LocalDate): UsagePoint? {
        var usage = 0.0
        var cost = CostBreakdown.Zero
        var covered = 0
        var approximate = false
        var date = start
        while (date <= end) {
            days[date]?.let {
                usage += it.usageM3
                cost += it.cost
                covered++
                approximate = approximate || it.approximate
            }
            date = date.plusDays(1)
        }
        if (covered == 0) return null
        val total = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        return UsagePoint(start, end, usage, cost, covered, total, approximate = approximate)
    }
}
