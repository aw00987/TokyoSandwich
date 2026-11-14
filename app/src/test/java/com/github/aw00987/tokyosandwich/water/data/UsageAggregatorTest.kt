package com.github.aw00987.tokyosandwich.water.data

import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.FakeTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class UsageAggregatorTest {
    private val may = WaterPeriod(
        LocalDate.of(2026, 5, 3), LocalDate.of(2026, 7, 2), 4.0,
        CostBreakdown(sewerFixed = 1232.0), YearMonth.of(2026, 6), YearMonth.of(2026, 7),
    )
    private val july = WaterPeriod(
        LocalDate.of(2026, 7, 3),
        LocalDate.of(2026, 9, 2),
        5.0,
        CostBreakdown(waterFixed = 100.0, waterVolume = 50.0, sewerFixed = 1232.0),
        YearMonth.of(2026, 8),
        YearMonth.of(2026, 9),
    )
    private val periods = listOf(may, july)

    @Test
    fun `months sum the daily period averages`() {
        val months = UsageAggregator.months(periods)
        val june = months.first { it.start == LocalDate.of(2026, 6, 1) }
        assertEquals(30 * 4.0 / 61, june.usageM3, 1e-12)
        assertEquals(30 * 1232.0 / 61, june.cost.sewerFixed, 1e-9)
        assertFalse(june.partial)
        assertTrue(june.estimated)
    }

    @Test
    fun `month spanning two periods mixes both averages`() {
        // July 1–2 belong to the May period, July 3–31 to the July period.
        val july = UsageAggregator.months(periods).first { it.start == LocalDate.of(2026, 7, 1) }
        assertEquals(2 * 4.0 / 61 + 29 * 5.0 / 62, july.usageM3, 1e-12)
        assertFalse(july.partial)
    }

    @Test
    fun `partial months at both ends are dropped`() {
        // May 3–31 and September 1–2 are only partly billed.
        val months = UsageAggregator.months(periods)
        assertEquals(listOf(6, 7, 8), months.map { it.start.monthValue })
        assertTrue(months.none { it.partial })
    }

    @Test
    fun `records are calendar months with the covered days and a note`() {
        val records = waterRecords(periods, FakeTexts)
        assertEquals(
            listOf(6, 7, 8).map { YearMonth.of(2026, it) },
            records.map { it.month },
        )
        val june = records[0]
        assertEquals(30 * 4.0 / 61, june.usage, 1e-12)
        // 605.9… yen, rounded to the whole yen every bill is in.
        assertEquals(606L, june.cost)
        assertEquals(30, june.days)
        assertTrue(june.note.lines()[0] == "#${R.string.bubble_estimated}")
        assertTrue(
            june.note.lines()
                .any { it.startsWith("#${R.string.note_item}|#${R.string.bubble_fixed}|") })
    }

    @Test
    fun `a partly billed month's note says so`() {
        val point = UsagePoint(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), 1.0,
            CostBreakdown(sewerFixed = 100.0), coveredDays = 29, totalDays = 31,
        )
        assertTrue(
            waterNote(
                point,
                FakeTexts
            ).lines()[0].contains("#${R.string.bubble_partial}|29|31")
        )
    }

    @Test
    fun `contract info carries number, status, default and address`() {
        val info = Contract("KEY-PLACEHOLDER", "0000000000", "テスト区1-2-3", ContractStatus.Active)
            .toInfo(FakeTexts, isDefault = true)
        assertEquals("KEY-PLACEHOLDER", info.id)
        assertEquals(
            "#${R.string.contract_number}|0000000000 · #${R.string.contract_status_active} · #${R.string.contract_default}",
            info.title,
        )
        assertEquals("テスト区1-2-3", info.subtitle)
    }

    @Test
    fun `no periods give no records`() {
        assertTrue(UsageAggregator.months(emptyList()).isEmpty())
        assertTrue(waterRecords(emptyList(), FakeTexts).isEmpty())
    }
}
