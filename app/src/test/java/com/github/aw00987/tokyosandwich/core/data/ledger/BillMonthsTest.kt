package com.github.aw00987.tokyosandwich.core.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BillMonthsTest {
    private fun ym(year: Int, month: Int) = YearMonth.of(year, month)
    private fun range(fromYear: Int, from: Int, toYear: Int, to: Int) =
        MonthRange(ym(fromYear, from), ym(toYear, to))

    @Test
    fun aSingleMonthIsOneMonth() {
        assertEquals(listOf(ym(2026, 10)), expandMonths(listOf(range(2026, 10, 2026, 10))))
    }

    @Test
    fun aRangeCrossesTheYear() {
        val months = expandMonths(listOf(range(2025, 11, 2026, 2)))
        assertEquals(listOf(ym(2025, 11), ym(2025, 12), ym(2026, 1), ym(2026, 2)), months)
    }

    @Test
    fun overlappingIncludesCountEachMonthOnceAndExcludesAreRemoved() {
        val months = expandMonths(
            include = listOf(
                range(2025, 4, 2025, 9),
                range(2025, 8, 2026, 3),
                range(2024, 1, 2024, 1)
            ),
            exclude = listOf(range(2025, 8, 2025, 8), range(2026, 2, 2026, 12)),
        )
        assertEquals(ym(2024, 1), months.first())
        assertEquals(ym(2026, 1), months.last())
        assertEquals(1 + 10 - 1, months.size)
        assertTrue(ym(2025, 8) !in months)
        assertEquals(months.sorted().distinct(), months)
    }

    @Test
    fun excludingEverythingLeavesNothing() {
        assertEquals(
            emptyList<YearMonth>(),
            expandMonths(listOf(range(2025, 4, 2025, 6)), listOf(range(2025, 1, 2025, 12))),
        )
    }

    @Test
    fun aRangeThatEndsBeforeItStartsIsEmpty() {
        assertEquals(emptyList<YearMonth>(), expandMonths(listOf(range(2025, 6, 2025, 4))))
    }

    @Test
    fun aRecurringBillReachesTheCurrentMonthOrItsEnd() {
        assertEquals(
            listOf(ym(2026, 9), ym(2026, 10), ym(2026, 11)),
            recurringMonths(ym(2026, 9), end = null, through = ym(2026, 11)),
        )
        assertEquals(
            listOf(ym(2026, 9), ym(2026, 10)),
            recurringMonths(ym(2026, 9), end = ym(2026, 10), through = ym(2026, 11)),
        )
        // An end in the future does not bill the months to come.
        assertEquals(
            listOf(ym(2026, 11)),
            recurringMonths(ym(2026, 11), end = ym(2027, 3), through = ym(2026, 11)),
        )
        // Not started yet.
        assertEquals(
            emptyList<YearMonth>(),
            recurringMonths(ym(2026, 12), end = null, through = ym(2026, 11))
        )
    }

    @Test
    fun monthsAreShownAsRangesOfConsecutiveMonths() {
        val months =
            listOf(ym(2025, 12), ym(2026, 1), ym(2025, 4), ym(2025, 5), ym(2025, 6), ym(2026, 5))
        assertEquals(
            listOf(range(2025, 4, 2025, 6), range(2025, 12, 2026, 1), range(2026, 5, 2026, 5)),
            compressMonths(months),
        )
        assertEquals(emptyList<MonthRange>(), compressMonths(emptyList()))
    }
}
