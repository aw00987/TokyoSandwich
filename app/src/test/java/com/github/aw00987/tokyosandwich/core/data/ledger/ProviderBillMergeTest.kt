package com.github.aw00987.tokyosandwich.core.data.ledger

import com.github.aw00987.tokyosandwich.core.data.MonthlyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ProviderBillMergeTest {
    private fun record(month: Int, cost: Long, usage: Double = 10.0, note: String = "") =
        MonthlyRecord(YearMonth.of(2026, month), usage, cost, 30, note)

    private fun saved(id: Long, month: Int, cost: Long, usage: Double = 10.0, note: String = "") =
        record(month, cost, usage, note).toBill(now = 1, itemId = 3).copy(id = id)

    private fun synced(vararg records: MonthlyRecord) =
        records.map { it.toBill(now = 2, itemId = 3) }

    @Test
    fun monthsNotReturnedAreKept() {
        val merge = mergeProviderBills(
            saved = listOf(saved(1, 1, 1000), saved(2, 2, 2000)),
            synced = synced(record(2, 2000), record(3, 3000)),
        )
        // January is not in the sync and is not touched; February is unchanged; March is new.
        assertTrue(merge.updated.isEmpty())
        assertEquals(listOf(YearMonth.of(2026, 3)), merge.added.map { it.month })
        assertEquals(0L, merge.added.single().id)
        assertEquals(2L, merge.added.single().createdAt)
    }

    @Test
    fun aRecomputedMonthIsUpdatedInPlace() {
        val merge = mergeProviderBills(
            saved = listOf(saved(7, 2, 2000, usage = 10.0, note = "provisional")),
            synced = synced(record(2, 2100, usage = 10.5, note = "confirmed")),
        )
        assertTrue(merge.added.isEmpty())
        val bill = merge.updated.single()
        assertEquals(7L, bill.id)
        assertEquals(1L, bill.createdAt)
        assertEquals(2100L, bill.amount)
        assertEquals(10.5, bill.usage!!, 0.0)
        assertEquals("confirmed", bill.note)
        assertEquals(BillOrigin.Provider, bill.origin)
    }

    @Test
    fun nothingSavedAddsEverything() {
        val merge = mergeProviderBills(emptyList(), synced(record(1, 1000), record(2, 2000)))
        assertTrue(merge.updated.isEmpty())
        assertEquals(2, merge.added.size)
    }
}
