package com.github.aw00987.tokyosandwich.gas.data

import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.FakeTexts
import com.github.aw00987.tokyosandwich.core.data.decodeStored
import com.github.aw00987.tokyosandwich.core.data.encodeStored
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Response shapes from poc/api/东京燃气api.md, with placeholder values. */
class GasParsingTest {
    private fun bill(
        from: String,
        to: String,
        usage: Int,
        charge: Number,
        basic: Number?,
        volume: Number?
    ) = """
        {"chargeDate":"${to}T15:00:00.000Z","totalCharge":$charge,"consumptionTax":100,
         "gasCharge":{"subtotalCharge":$charge,"from":"${from}T15:00:00.000Z","to":"${to}T15:00:00.000Z",
          "details":[{"usage":$usage,"usageDays":30,"charge":$charge,
           "from":"${from}T15:00:00.000Z","to":"${to}T15:00:00.000Z","meterReadingDate":"${to}T15:00:00.000Z",
           "nextMeterReadingDate":"2026-12-13T15:00:00.000Z","isEstimated":false,
           "breakdownList":[${
        listOfNotNull(
            basic?.let { """{"charge":$it,"displayName":"ガス基本料金"}""" },
            volume?.let { """{"charge":$it,"displayName":"ガス従量料金"}""" },
        ).joinToString(",")
    }]}]}}
    """.trimIndent()

    @Test
    fun parsesBillsAsTokyoDatesOldestFirst() {
        val data = TokyoGasApi.parseEnvelope(
            """
            {"data":{"gasElectricityCharge":{"bills":[
              ${bill("2026-10-14", "2026-11-12", 25, 5000, 1056.0, 3943.48)},
              ${bill("2026-09-13", "2026-10-13", 20, 4200, 1056, null)},
              {"chargeDate":"2026-10-13T15:00:00.000Z","totalCharge":3000,"gasCharge":null}
            ]}}}
            """.trimIndent()
        )
        val dto = TokyoGasApi.json.decodeFromJsonElement(GasChargeDto.serializer(), data)
        val periods = dto.toPeriods()
        assertEquals(
            listOf(
                GasPeriod(
                    LocalDate.of(2026, 9, 14),
                    LocalDate.of(2026, 10, 14),
                    20.0,
                    4200.0,
                    fixedYen = 1056.0
                ),
                GasPeriod(
                    LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 13), 25.0, 5000.0,
                    fixedYen = 1056.0, volumeYen = 3943.48,
                ),
            ),
            periods,
        )
        val latest = periods.last()
        assertEquals(30, latest.days)
        assertEquals(YearMonth.of(2026, 11), latest.month)
        // Breakdown lines carry fractions of a yen (seen on a real account: 684.52); the total is whole yen.
        assertEquals(0.52, latest.unclassifiedYen!!, 1e-9)
        assertEquals(3144.0, periods.first().unclassifiedYen!!, 1e-9)
    }

    @Test
    fun periodWithoutBreakdownHasNoSplit() {
        val period = GasPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 31.0, 3100.0)
        assertNull(period.unclassifiedYen)
        assertEquals(31, period.days)
    }

    @Test
    fun keepsOnlyGasContracts() {
        val dto = TokyoGasApi.json.decodeFromJsonElement(
            CustomerNumbersDto.serializer(),
            TokyoGasApi.parseEnvelope(
                """
                {"data":{"customerNumbers":[
                  {"indexNumber":1,"contractType":"ELECTRICITY","endDate":null,"displayCustomerNumber":"0000-000-0001"},
                  {"indexNumber":2,"contractType":"GAS","endDate":"2025-03-31T15:00:00.000Z","displayCustomerNumber":"0000-000-0002"},
                  {"indexNumber":3,"contractType":"GAS","endDate":null,"displayCustomerNumber":"0000-000-0003"}
                ]}}
                """.trimIndent()
            ),
        )
        val gas = dto.gasContracts
        assertEquals(
            listOf(GasContract(2, "0000-000-0002", ended = true), GasContract(3, "0000-000-0003")),
            gas,
        )
        assertEquals(3, chooseContract(gas, null)?.index)
        assertEquals(2, chooseContract(gas, 2)?.index)
        assertEquals(3, chooseContract(gas, 99)?.index)
        assertNull(chooseContract(emptyList(), null))
    }

    @Test
    fun unauthenticatedErrorMeansSessionExpired() {
        assertThrows(GasSessionExpiredException::class.java) {
            TokyoGasApi.parseEnvelope(
                """
                {"errors":[{"message":"ログイン認証エラー","extensions":{"code":"LOGIN_AUTH_UNAUTHENTICATED","mtgErrorMessage":"x"}}],
                 "data":{"customerNumbers":null}}
                """.trimIndent()
            )
        }
        val other = assertThrows(GasApiException::class.java) {
            TokyoGasApi.parseEnvelope("""{"errors":[{"message":"boom"}],"data":null}""")
        }
        assertEquals("boom", other.code)
    }

    @Test
    fun recordsUseTheReadingMonthAndCarryTheBreakdownAsANote() {
        val a = GasPeriod(
            LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14), 20.0, 4200.0, 1056.0, 3143.52,
        )
        val b = GasPeriod(
            LocalDate.of(2026, 10, 15),
            LocalDate.of(2026, 10, 31),
            5.0,
            900.0,
            estimated = true
        )
        val september =
            GasPeriod(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 14), 18.0, 3900.0)
        val records = gasRecords(listOf(b, a, september), FakeTexts)
        // Two periods end in October: the later one wins.
        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            records.map { it.month })
        assertEquals(5.0, records[1].usage, 0.0)
        assertEquals(900L, records[1].cost)
        assertEquals(17, records[1].days)
        assertEquals(
            "#${R.string.period_range}|10|15|10|31 · #${R.string.gas_days}|17 · #${R.string.gas_estimated}",
            records[1].note,
        )
        val lines = gasNote(a, FakeTexts).lines()
        assertEquals("#${R.string.period_range}|9|15|10|14 · #${R.string.gas_days}|30", lines[0])
        assertEquals("#${R.string.note_item}|#${R.string.gas_basic_charge}|¥1,056", lines[1])
        assertEquals("#${R.string.note_item}|#${R.string.gas_volume_charge}|¥3,144", lines[2])
        assertEquals(3, lines.size) // the 0.48 yen of rounding is not worth a line
    }

    @Test
    fun contractInfoShowsTheCustomerNumber() {
        assertEquals(
            com.github.aw00987.tokyosandwich.core.data.ContractInfo(
                "2",
                "#${R.string.gas_customer_number}|0000-000-0002 · #${R.string.gas_contract_ended}",
            ),
            GasContract(2, "0000-000-0002", ended = true).toInfo(FakeTexts),
        )
    }

    @Test
    fun sessionNeedsTheSessionCookieAndFollowsSetCookie() {
        assertNull(GasSession.fromCookieHeader(null))
        assertNull(GasSession.fromCookieHeader("mtg-hmid=a; other=b"))
        val session =
            GasSession.fromCookieHeader("mtg-session-id=s1; mtg-hmid=h1; mtg-login-context-id=c1")!!
        assertEquals("mtg-session-id=s1; mtg-hmid=h1; mtg-login-context-id=c1", session.header())
        session.update(
            "https://members.tokyo-gas.co.jp/graphql".toHttpUrl(),
            headersOf(
                "Set-Cookie", "mtg-session-id=s2; Path=/; HttpOnly; Secure",
                "Set-Cookie", "mtg-login-context-id=; Max-Age=0; Path=/",
            ),
        )
        assertEquals("mtg-session-id=s2; mtg-hmid=h1", session.header())
    }

    @Test
    fun recognisesTheLoginRedirect() {
        val redirect = "https://members.tokyo-gas.co.jp/api/mtg/v1/auth/redirect?code=x&state=y"
        assertTrue(TokyoGasAuth.isRedirect(redirect))
        assertFalse(TokyoGasAuth.isMembersPage(redirect))
        assertNull(TokyoGasAuth.redirectError(redirect))
        assertEquals(
            "access_denied",
            TokyoGasAuth.redirectError("https://members.tokyo-gas.co.jp/api/mtg/v1/auth/redirect?error=access_denied"),
        )
        assertFalse(TokyoGasAuth.isMembersPage(TokyoGasAuth.LOGIN_URL))
        assertTrue(TokyoGasAuth.isMembersPage("https://members.tokyo-gas.co.jp/"))
        assertFalse(TokyoGasAuth.isMembersPage("https://login.tokyo-gas.co.jp/"))
        assertFalse(TokyoGasAuth.isRedirect("not a url"))
    }

    @Test
    fun accountFileIgnoresUnknownFieldsAndRoundTrips() {
        val older = decodeStored(
            GasAccount.serializer(),
            """{"later":true,"loginId":"user@example.com","password":"placeholder"}""".encodeToByteArray(),
        )
        assertEquals(GasAccount("user@example.com", "placeholder"), older)
        assertEquals(
            older,
            decodeStored(GasAccount.serializer(), encodeStored(GasAccount.serializer(), older))
        )
    }

    @Test
    fun queriesAreNotMutations() {
        assertFalse(CUSTOMER_NUMBERS_QUERY.trimStart().startsWith("mutation"))
        assertTrue(GAS_CHARGE_QUERY.startsWith("query GasElectricityCharge(\$contractIndexNumber: Int!)"))
    }
}
