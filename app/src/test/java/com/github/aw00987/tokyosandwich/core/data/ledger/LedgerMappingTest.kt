package com.github.aw00987.tokyosandwich.core.data.ledger

import com.github.aw00987.tokyosandwich.core.data.MonthlyRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class LedgerMappingTest {
    private val record =
        MonthlyRecord(YearMonth.of(2026, 9), 12.5, 3124L, 31, "estimated\nfixed ¥1,892")

    @Test
    fun recordsAreProviderBills() {
        val bill = record.toBill(now = 42, itemId = 3)
        assertEquals(BillOrigin.Provider, bill.origin)
        assertEquals(3L, bill.itemId)
        assertEquals(YearMonth.of(2026, 9), bill.month)
        assertEquals(3124L, bill.amount)
        assertEquals(12.5, bill.usage!!, 0.0)
        assertEquals(31, bill.days)
        assertEquals(42L, bill.createdAt)
        assertEquals(null, bill.recurringId)
    }

    @Test
    fun extIsJsonWithoutNullFields() {
        val converters = CostConverters()
        val provider = record.toBill(now = 0).ext
        assertEquals("""{"usage":12.5,"days":31}""", converters.extToText(provider))
        assertEquals(provider, converters.textToExt(converters.extToText(provider)))
        assertEquals("""{"recurringId":7}""", converters.extToText(BillExt(recurringId = 7)))
        // Nothing to save is no JSON at all; unknown fields of a later format are ignored.
        assertEquals(null, converters.extToText(BillExt()))
        assertEquals(null, converters.extToText(null))
        assertEquals(BillExt(days = 30), converters.textToExt("""{"days":30,"later":true}"""))
    }

    @Test
    fun billsRoundTripToRecords() {
        assertEquals(record, record.toBill(now = 0).toRecord())
        assertEquals(
            record.copy(days = null, note = ""),
            record.copy(days = null, note = "").toBill(0).toRecord()
        )
    }
}
