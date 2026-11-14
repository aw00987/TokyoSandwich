package com.github.aw00987.tokyosandwich.core

import com.github.aw00987.tokyosandwich.core.ui.YearChartPage
import com.github.aw00987.tokyosandwich.core.ui.yearPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CoreTest {
    @Test
    fun parsesPaddedValues() {
        assertEquals(1234, " 1,234　".toAmount())
        assertEquals(12.34, "12.34".toDecimal()!!, 1e-9)
        assertEquals(YearMonth.of(2026, 5), "202605".toYearMonth())
        assertNull("2026-5".toYearMonth())
        assertEquals(LocalDate.of(2026, 11, 7), "20261107".toLocalDate())
        assertNull("20261332".toLocalDate())
        assertNull("07".toLocalDate())
    }

    @Test
    fun yearPagesAreNewestFirstWithTwelveSlots() {
        val pages = yearPages(mapOf(YearMonth.of(2024, 12) to "a", YearMonth.of(2026, 2) to "b"))
        assertEquals(listOf(2026, 2025, 2024), pages.map { it.year })
        assertTrue(pages.all { it.points.size == 12 })
        assertEquals("b", pages[0].points[1])
        assertTrue(pages[1].points.all { it == null })
        assertEquals("a", pages[2].points[11])
        assertEquals(YearMonth.of(2024, 12), pages[2].month(11))
        assertTrue(yearPages(emptyMap<YearMonth, String>()).isEmpty())
        assertEquals(
            YearChartPage(2026, List(12) { null }),
            YearChartPage<String>(2026, List(12) { null })
        )
    }
}
