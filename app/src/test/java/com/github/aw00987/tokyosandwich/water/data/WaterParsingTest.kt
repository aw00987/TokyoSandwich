package com.github.aw00987.tokyosandwich.water.data

import com.github.aw00987.tokyosandwich.core.isJwtExpired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Base64

class WaterParsingTest {
    @Test
    fun `meter detail resolves years and splits fixed and volume charges`() {
        val period = MeterDataDto(
            seiDateSt1 = "202604", seiDateEnd1 = "202605",
            useStDate = " 3月 3日", useEndDate = " 5月 2日", sryoUsed = "4",
            jysChAm = "1892", jysKiAm = "1720", jysJuAm = "0",
            gesChAm = "1232", gesKiAm = "1120", gesJuAm = "0", toChAm = "3124",
        ).toPeriod()!!
        assertEquals(LocalDate.of(2026, 3, 3), period.start)
        assertEquals(LocalDate.of(2026, 5, 2), period.endInclusive)
        assertEquals(61, period.days)
        assertEquals(1892.0, period.cost.waterFixed, 1e-9)
        assertEquals(1232.0, period.cost.sewerFixed, 1e-9)
        assertEquals(0.0, period.cost.volume, 1e-9)
        assertEquals(3124.0, period.cost.total, 1e-9)
        assertFalse(period.approximate)
    }

    @Test
    fun `tax is allocated proportionally and totals are preserved`() {
        // 20mm, 59 m³ / 2 months example from the official calculation page: water 9,038 yen.
        val (fixed, volume, other) = splitCharge(total = 9038, basic = 2340, volume = 5877)
        assertEquals(9038.0, fixed + volume, 1e-9)
        assertEquals(9038.0 * 2340 / 8217, fixed, 1e-9)
        assertEquals(0.0, other, 1e-9)
        assertEquals(
            Triple(0.0, 0.0, 1562.0),
            splitCharge(total = 1562, basic = null, volume = null)
        )
        assertEquals(Triple(0.0, 0.0, 0.0), splitCharge(total = 0, basic = 0, volume = 0))
    }

    @Test
    fun `reading dates across new year`() {
        val (start, end) = readingDates(" 11月 3日", " 1月 2日", YearMonth.of(2027, 1))!!
        assertEquals(LocalDate.of(2026, 11, 3), start)
        assertEquals(LocalDate.of(2027, 1, 2), end)
        // Ends in December, billed for Dec–Jan: the end stays in the billing end month's previous year.
        val (s2, e2) = readingDates("10月 5日", "12月 4日", YearMonth.of(2027, 1))!!
        assertEquals(LocalDate.of(2026, 10, 5), s2)
        assertEquals(LocalDate.of(2026, 12, 4), e2)
        assertNull(readingDates("", "9月2日", YearMonth.of(2026, 9)))
    }

    @Test
    fun `history item without detail becomes an estimated period`() {
        val period = UsedDataDto(
            seiDateStart = "202606",
            seiDateEnd = "202607",
            sryo = "4",
            toChAm = "1232",
            remark2 = "1"
        ).toEstimatedPeriod()!!
        assertEquals(LocalDate.of(2026, 5, 1), period.start)
        assertEquals(LocalDate.of(2026, 6, 30), period.endInclusive)
        assertEquals(1232.0, period.cost.unclassified, 1e-9)
        assertTrue(period.approximate)
        assertNull(UsedDataDto(seiDateStart = "202608", sryo = "").toEstimatedPeriod())
    }

    @Test
    fun `estimated periods are clipped to their exact neighbours`() {
        val exact = WaterPeriod(
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 9, 2),
            5.0,
            CostBreakdown.Zero,
            YearMonth.of(2026, 8),
            YearMonth.of(2026, 9)
        )
        val estimated = UsedDataDto(
            seiDateStart = "202608",
            seiDateEnd = "202609",
            sryo = "5"
        ).toEstimatedPeriod()!!
            .copy(billedFrom = YearMonth.of(2026, 10), billedTo = YearMonth.of(2026, 11))
            .copy(start = LocalDate.of(2026, 9, 1), endInclusive = LocalDate.of(2026, 10, 31))
        val resolved = resolveOverlaps(listOf(estimated, exact))
        assertEquals(exact, resolved[0])
        assertEquals(LocalDate.of(2026, 9, 3), resolved[1].start)
    }

    @Test
    fun `jwt expiry uses exp claim`() {
        val payload =
            Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1000}""".toByteArray())
        val token = "header.$payload.signature"
        assertFalse(isJwtExpired(token, nowSeconds = 900))
        assertTrue(isJwtExpired(token, nowSeconds = 950))
        assertTrue(isJwtExpired("not-a-jwt", nowSeconds = 0))
    }
}

class ContractTest {
    private fun contract(key: String) = Contract(key, key, "", ContractStatus.Active)

    @Test
    fun `defaults to the first contract`() {
        assertEquals("a", chooseContract(listOf(contract("a"), contract("b")), null)?.key)
    }

    @Test
    fun `uses the saved contract`() {
        assertEquals("b", chooseContract(listOf(contract("a"), contract("b")), "b")?.key)
    }

    @Test
    fun `falls back to the first contract when the saved one is gone`() {
        assertEquals("a", chooseContract(listOf(contract("a"), contract("b")), "gone")?.key)
    }

    @Test
    fun `no contracts means no choice`() {
        assertNull(chooseContract(emptyList(), "a"))
    }

    @Test
    fun `contract maps status and builds the address`() {
        val c = ContractDto(
            wKey = "k",
            wNumber = " 123 ",
            wStatus = "04",
            address1 = "新宿区",
            address2 = "西新宿",
            address3 = "2",
            address4 = "8",
            address5 = "1",
            address6 = "都庁ハイツ",
            address8 = "101",
        ).toDomain()
        assertEquals(
            Contract(
                "k",
                "123",
                "新宿区西新宿2丁目8-1 都庁ハイツ 101",
                ContractStatus.Active
            ), c
        )
        assertEquals(
            ContractStatus.Stopped,
            ContractDto(wKey = "k", wStatus = "09").toDomain().status
        )
    }
}
