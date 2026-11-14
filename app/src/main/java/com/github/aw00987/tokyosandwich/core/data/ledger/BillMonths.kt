package com.github.aw00987.tokyosandwich.core.data.ledger

import java.time.YearMonth

// Which months a bill entry stands for. Pure functions: the screens use them for the preview, the repository for
// the rows it writes.

/** The months from [start] through [end], both included. */
data class MonthRange(val start: YearMonth, val end: YearMonth) {
    operator fun contains(month: YearMonth): Boolean = month in start..end
}

/** Every month of [range], oldest first; none when the range ends before it starts. */
fun monthsOf(range: MonthRange): List<YearMonth> =
    generateSequence(range.start) { it.plusMonths(1) }.takeWhile { it <= range.end }.toList()

/** The months in any of [include] and in none of [exclude], oldest first, each once. */
fun expandMonths(
    include: List<MonthRange>,
    exclude: List<MonthRange> = emptyList()
): List<YearMonth> =
    include.flatMap(::monthsOf).distinct().filter { month -> exclude.none { month in it } }.sorted()

/**
 * The months a recurring bill from [start] through [end] (null = no end) has reached by [through], oldest first.
 * Months after [through] are not billed yet.
 */
fun recurringMonths(start: YearMonth, end: YearMonth?, through: YearMonth): List<YearMonth> =
    monthsOf(MonthRange(start, if (end != null && end < through) end else through))

/** [months] as the fewest ranges of consecutive months, oldest first: how a list of months is shown. */
fun compressMonths(months: List<YearMonth>): List<MonthRange> {
    val ranges = mutableListOf<MonthRange>()
    for (month in months.distinct().sorted()) {
        val last = ranges.lastOrNull()
        if (last != null && last.end.plusMonths(1) == month) {
            ranges[ranges.lastIndex] = last.copy(end = month)
        } else {
            ranges += MonthRange(month, month)
        }
    }
    return ranges
}
