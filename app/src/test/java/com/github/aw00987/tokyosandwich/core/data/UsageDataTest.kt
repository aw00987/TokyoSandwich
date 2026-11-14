package com.github.aw00987.tokyosandwich.core.data

import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import com.github.aw00987.tokyosandwich.core.data.ledger.defaultTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class UsageDataTest {
    private val contract = ContractInfo("ID-PLACEHOLDER", "No. 0000000000", "テスト区1-2-3")
    private fun record(month: Int) =
        MonthlyRecord(YearMonth.of(2026, month), 100.0 + month, 3000L + month, 30, "note\nline")

    private val soFar =
        PeriodSoFar(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 7), 15.9, 1605.0)
    private val data = UsageData(contract, listOf(contract), listOf(record(9), record(10)), soFar)

    @Test
    fun settingsRoundTripWithTheTree() {
        val settings = AppSettings(
            costs = defaultTree(),
            providers = ProvidersSettings(electricity = ProviderSettings("0000000000000002")),
        )
        val bytes = encodeStored(AppSettings.serializer(), settings)
        assertEquals(settings, decodeStored(AppSettings.serializer(), bytes))
    }

    @Test
    fun syncStateRoundTripsWithTheSyncedDataAndNoRecords() {
        val state = SyncState(
            electricity = ProviderSync(clientId = "00000000-0000-0000-0000-000000000000"),
            gas = ProviderSync(synced = data.synced(1_790_000_000_000)),
        )
        val bytes = encodeStored(SyncState.serializer(), state)
        assertEquals(state, decodeStored(SyncState.serializer(), bytes))
        assertEquals(data, state[ProviderKind.Gas].synced!!.withRecords(data.records))
        val json = bytes.decodeToString()
        assertTrue(json.contains("\"start\":\"2026-10-20\""))
        assertFalse(json.contains("records"))
        assertEquals(ProviderSync(), state.with(ProviderKind.Gas, ProviderSync())[ProviderKind.Gas])
    }

    @Test
    fun missingSettingsAreDefaultsAndUnknownOnesIgnored() {
        assertEquals(
            AppSettings(),
            decodeStored(AppSettings.serializer(), "{}".encodeToByteArray())
        )
        assertEquals(SyncState(), decodeStored(SyncState.serializer(), "{}".encodeToByteArray()))
        val json = """{"later":1,"providers":{"water":{"selectedContract":"W-1","later":2}}}"""
        assertEquals(
            AppSettings(providers = ProvidersSettings(water = ProviderSettings(selectedContract = "W-1"))),
            decodeStored(AppSettings.serializer(), json.encodeToByteArray()),
        )
    }

    @Test
    fun eachProviderHasItsOwnSettings() {
        val water = ProviderSettings(selectedContract = "W-1")
        val providers = ProvidersSettings().with(ProviderKind.Water, water)
        assertEquals(water, providers[ProviderKind.Water])
        assertEquals(ProviderSettings(), providers[ProviderKind.Electricity])
        assertEquals(ProviderSettings(), providers[ProviderKind.Gas])
    }

    @Test
    fun dailyAveragesNeedDays() {
        assertEquals(109.0 / 30, record(9).dailyAverage!!, 1e-12)
        assertNull(record(9).copy(days = null).dailyAverage)
        assertEquals(19, soFar.days)
        assertEquals(15.9 / 19, soFar.dailyAverage!!, 1e-12)
        assertNull(soFar.copy(through = null).dailyAverage)
    }
}
