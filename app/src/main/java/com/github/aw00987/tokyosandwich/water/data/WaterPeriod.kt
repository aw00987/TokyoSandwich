package com.github.aw00987.tokyosandwich.water.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Yen amounts of a bill, tax included, split into fixed and usage-based parts.
 *
 * Fixed = 水道基本料金 and the 下水道 minimum charge (≤ 8 m³/month); volume = 従量料金. The service reports
 * consumption tax per service only, so it is allocated to the two parts in proportion to their pre-tax amounts.
 * [unclassified] holds money whose split the service did not report (only the total is known).
 */
data class CostBreakdown(
    val waterFixed: Double = 0.0,
    val waterVolume: Double = 0.0,
    val sewerFixed: Double = 0.0,
    val sewerVolume: Double = 0.0,
    val unclassified: Double = 0.0,
) {
    val fixed: Double get() = waterFixed + sewerFixed
    val volume: Double get() = waterVolume + sewerVolume
    val total: Double get() = fixed + volume + unclassified

    operator fun plus(other: CostBreakdown) = CostBreakdown(
        waterFixed + other.waterFixed,
        waterVolume + other.waterVolume,
        sewerFixed + other.sewerFixed,
        sewerVolume + other.sewerVolume,
        unclassified + other.unclassified,
    )

    operator fun div(divisor: Double) = CostBreakdown(
        waterFixed / divisor,
        waterVolume / divisor,
        sewerFixed / divisor,
        sewerVolume / divisor,
        unclassified / divisor,
    )

    companion object {
        val Zero = CostBreakdown()
    }
}

/**
 * One billing period (normally two months between meter readings) with its real reading dates.
 * [billedFrom]–[billedTo] are the billing months the service labels it with (`seiDate*`), about a month after use.
 * [approximate] means the per-period detail could not be loaded, so the dates are estimated from the billing months.
 */
data class WaterPeriod(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val usageM3: Double,
    val cost: CostBreakdown,
    val billedFrom: YearMonth,
    val billedTo: YearMonth,
    val approximate: Boolean = false,
) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()
}

/**
 * Resolves the year of the service's year-less reading dates (`" 5月 3日"` – `" 7月 2日"`).
 * The period ends in the last billing month ([billedTo]), and starts before its end.
 */
internal fun readingDates(
    start: String,
    end: String,
    billedTo: YearMonth
): Pair<LocalDate, LocalDate>? {
    val (startMonth, startDay) = parseMonthDay(start) ?: return null
    val (endMonth, endDay) = parseMonthDay(end) ?: return null
    val endYear = if (endMonth > billedTo.monthValue) billedTo.year - 1 else billedTo.year
    val startYear =
        if (startMonth > endMonth || (startMonth == endMonth && startDay > endDay)) endYear - 1 else endYear
    return runCatching {
        LocalDate.of(startYear, startMonth, startDay) to LocalDate.of(
            endYear,
            endMonth,
            endDay
        )
    }.getOrNull()
}

private val MONTH_DAY = Regex("""(\d{1,2})月(\d{1,2})日""")

private fun parseMonthDay(text: String): Pair<Int, Int>? =
    MONTH_DAY.find(
        text.replace(
            Regex("[\\s　]"),
            ""
        )
    )?.destructured?.let { (m, d) -> m.toInt() to d.toInt() }

/**
 * Splits one service's charge (tax included) into fixed and volume parts by the pre-tax [basic] and [volume] amounts.
 * Returns `(fixed, volume, unclassified)`.
 */
internal fun splitCharge(total: Int?, basic: Int?, volume: Int?): Triple<Double, Double, Double> {
    val sum = total ?: return Triple(0.0, 0.0, 0.0)
    if (basic == null || volume == null || basic + volume <= 0) {
        return if (sum == 0) Triple(0.0, 0.0, 0.0) else Triple(0.0, 0.0, sum.toDouble())
    }
    val fixed = sum * basic.toDouble() / (basic + volume)
    return Triple(fixed, sum - fixed, 0.0)
}

/** Makes estimated periods (whose dates are guesses) not overlap their exact neighbours. */
internal fun resolveOverlaps(periods: List<WaterPeriod>): List<WaterPeriod> {
    val sorted = periods.sortedBy { it.billedFrom }
    return sorted.mapIndexedNotNull { i, period ->
        if (!period.approximate) return@mapIndexedNotNull period
        val previousEnd = sorted.getOrNull(i - 1)?.endInclusive
        val nextStart = sorted.getOrNull(i + 1)?.start
        val start =
            if (previousEnd != null && previousEnd >= period.start) previousEnd.plusDays(1) else period.start
        val end =
            if (nextStart != null && nextStart <= period.endInclusive) nextStart.minusDays(1) else period.endInclusive
        if (end < start) null else period.copy(start = start, endInclusive = end)
    }
}
