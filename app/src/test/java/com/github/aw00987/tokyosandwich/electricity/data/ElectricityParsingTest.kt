package com.github.aw00987.tokyosandwich.electricity.data

import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.FakeTexts
import com.github.aw00987.tokyosandwich.core.data.PeriodSoFar
import com.github.aw00987.tokyosandwich.core.data.decodeStored
import com.github.aw00987.tokyosandwich.core.data.encodeStored
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Response shapes from poc/api/东京电力api.md, with placeholder values. */
class ElectricityParsingTest {
    @Test
    fun keepsOnlyElectricContracts() {
        val list = TepcoApi.json.decodeFromString<ContractListDto>(
            """
            {"commonInfo":{"timestamp":"t"},"readOffset":"0","contractStatus":"01","displayLimit":"00",
             "contracts":[
               {"contractClass":"03","contractNum":"0000000000000001","planName":"ガス","address":"東京都","mainContractFlag":"0"},
               {"contractClass":"01","contractNum":"0000000000000002","planName":"従量電灯B","address":"東京都新宿区","mainContractFlag":"1","customerNum":"x"},
               {"contractClass":"02","contractNum":"0000000000000003","planName":"スタンダードS","address":"東京都","mainContractFlag":"0"}
             ]}
            """.trimIndent()
        )
        val electric = list.electricContracts
        assertEquals(
            listOf("0000000000000002", "0000000000000003"),
            electric.map { it.contractNum })
        assertEquals("0000000000000002", chooseContract(electric, null)?.contractNum)
        assertEquals("0000000000000003", chooseContract(electric, "0000000000000003")?.contractNum)
        assertEquals("0000000000000002", chooseContract(electric, "gone")?.contractNum)
        assertNull(chooseContract(emptyList(), null))
    }

    @Test
    fun currentPeriodUsesActualUsageNotTheForecast() {
        val meter = TepcoApi.json.decodeFromString<BillingMeterDto>(
            """{"commonInfo":{"timestamp":"t"},"meterInfo":{"month":"202611","startDate":"20261020","endDate":"20261118"}}"""
        ).meterInfo
        val month = TepcoApi.json.decodeFromString<BillingMonthDto>(
            """
            {"commonInfo":{"timestamp":"t"},"billInfo":{"usedMonth":"202611","billingStatus":"01",
             "electricRateCategory":"P000000000","timezonePrice":"0",
             "meterInfo":{"startDate":"20261020","endDate":"20261118"},
             "usedInfo":{"startDate":"20261020","endDate":"20261107","charge":"1,605","power":"15.90","unit":"kWh"},
             "predictionInfo":{"charge":"5400","power":"190.00","unit":"kWh"}}}
            """.trimIndent()
        )
        val period = month.toPeriod(meter)!!
        assertEquals(
            CurrentPeriod(
                YearMonth.of(2026, 11), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 7), usageKwh = 15.90, costYen = 1605,
            ),
            period,
        )
        assertEquals(19, period.toSoFar().days)
        assertEquals(15.90 / 19, period.toSoFar().dailyAverage!!, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 20) to LocalDate.of(2026, 11, 18), month.periodDates())
    }

    @Test
    fun periodWithoutDataYet() {
        val month = TepcoApi.json.decodeFromString<BillingMonthDto>(
            """{"commonInfo":{"timestamp":"t"},"billInfo":{"billingStatus":"00"}}"""
        )
        val period = month.toPeriod(MeterInfoDto("202611", "20261020", "20261118"))!!
        assertNull(period.usageKwh)
        assertEquals(0, period.toSoFar().days)
        assertNull(period.toSoFar().dailyAverage)
        assertNull(month.periodDates())
    }

    @Test
    fun monthHistorySumsDetailsAndSkipsMonthsWithoutData() {
        val history = TepcoApi.json.decodeFromString<MonthHistoryDto>(
            """
            {"commonInfo":{"timestamp":"t"},"billInfos":[
              {"usedMonth":"202409","billingStatus":"99"},
              {"usedMonth":"202410","billingStatus":"00"},
              {"usedMonth":"202411","billingStatus":"01","contractClass":"50","detailInfos":[
                {"billingClass":"01","usedCharge":"1,234","usedPowerInfo":{"power":"100.50","unit":"kWh"}},
                {"billingClass":"01","usedCharge":"766","usedPowerInfo":{"power":"49.50","unit":"kWh"}}]},
              {"usedMonth":"202412","billingStatus":"01","detailInfos":[
                {"billingClass":"00","usedCharge":"3000","usedPowerInfo":{"power":"999.00","unit":"kWh"},
                 "historyInfos":[{"usedPowerInfo":{"power":"60.00","unit":"kWh"}},{"usedPowerInfo":{"power":"40.00","unit":"kWh"}}],
                 "paymentDateInfo":{"nextMeterReading":"12月14日"}}]},
              {"usedMonth":"202501","billingStatus":"01","detailInfos":[
                {"billingClass":"01","usedCharge":"500","usedPowerInfo":{"power":"10.00","unit":"m3"}}]}
            ]}
            """.trimIndent()
        )
        val months = history.toMonths()
        assertEquals(
            listOf(YearMonth.of(2024, 11), YearMonth.of(2024, 12)),
            months.map { it.month })
        assertEquals(BilledMonth(YearMonth.of(2024, 11), 150.0, 2000, confirmed = true), months[0])
        assertEquals(BilledMonth(YearMonth.of(2024, 12), 100.0, 3000, confirmed = false), months[1])
    }

    @Test
    fun periodDatesFollowFromNeighboursAndTheCurrentPeriod() {
        val current = CurrentPeriod(
            YearMonth.of(2026, 11),
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 11, 18)
        )
        // B3 gives no dates; B2 gave September's.
        val months = listOf(billed(2026, 8), billed(2026, 9), billed(2026, 10))
        val known = mapOf(
            YearMonth.of(2026, 9) to (LocalDate.of(2026, 8, 20) to LocalDate.of(2026, 9, 18))
        )
        val filled = fillPeriodDates(months, current, known)
        assertEquals(30, filled[1].days)
        assertEquals(null to LocalDate.of(2026, 8, 19), filled[0].start to filled[0].end)
        assertEquals(
            LocalDate.of(2026, 9, 19) to LocalDate.of(2026, 10, 19),
            filled[2].start to filled[2].end
        )
        assertEquals(31, filled[2].days)
        assertNull(filled[0].days)
        assertNull(fillPeriodDates(months, null)[2].days)
    }

    @Test
    fun recordsAreBillingMonthsWithANote() {
        val october = billed(2026, 10).copy(
            start = LocalDate.of(2026, 9, 19), end = LocalDate.of(2026, 10, 19), confirmed = false,
        )
        val records = tepcoRecords(listOf(october, billed(2026, 9)), FakeTexts)
        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            records.map { it.month })
        assertEquals(109.0, records[0].usage, 0.0)
        assertEquals(3009L, records[0].cost)
        assertNull(records[0].days)
        assertEquals("#${R.string.elec_bill_confirmed}", records[0].note)
        assertEquals(31, records[1].days)
        assertEquals(
            "#${R.string.elec_bill_provisional}\n#${R.string.period_range}|9|19|10|19 · #${R.string.gas_days}|31",
            records[1].note,
        )
    }

    @Test
    fun currentPeriodBecomesThePeriodSoFar() {
        val current = CurrentPeriod(
            YearMonth.of(2026, 11), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 18),
            LocalDate.of(2026, 11, 7), 15.9, 1605,
        )
        assertEquals(
            PeriodSoFar(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 7), 15.9, 1605.0),
            current.toSoFar(),
        )
    }

    @Test
    fun settingsFileIgnoresUnknownFields() {
        val json = """{"email":"user@example.com","later":true}"""
        assertEquals(
            TepcoSettings(email = "user@example.com"),
            decodeStored(TepcoSettings.serializer(), json.encodeToByteArray()),
        )
        assertEquals(
            TepcoSettings(),
            decodeStored(TepcoSettings.serializer(), "{}".encodeToByteArray())
        )
        assertNull(TepcoSettings().token)
    }

    @Test
    fun savedLoginRoundTrips() {
        val saved = TepcoSettings(
            accessToken = "token-placeholder",
            tokenExpiresAt = 1_800_000_000_000,
            email = "user@example.com",
        )
        assertEquals(
            saved,
            decodeStored(
                TepcoSettings.serializer(),
                encodeStored(TepcoSettings.serializer(), saved)
            ),
        )
        assertEquals(TepcoToken("token-placeholder", 1_800_000_000_000), saved.token)
    }

    private fun billed(year: Int, month: Int, cost: Int = 3000 + month) =
        BilledMonth(YearMonth.of(year, month), 100.0 + month, cost)
}
