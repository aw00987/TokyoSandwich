package com.github.aw00987.tokyosandwich.core.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BillRulesTest {
    private fun bill(origin: BillOrigin) =
        BillEntity(
            itemId = 1,
            month = YearMonth.of(2026, 5),
            amount = 100,
            origin = origin,
            createdAt = 0
        )

    @Test
    fun onlyTheUsersBillsCanBeEdited() {
        assertTrue(bill(BillOrigin.Manual).editable)
        assertTrue(bill(BillOrigin.Recurring).editable)
        assertFalse(bill(BillOrigin.Provider).editable)
    }

    @Test
    fun onlyManualBillsChangeMonth() {
        assertTrue(bill(BillOrigin.Manual).monthEditable)
        assertFalse(bill(BillOrigin.Recurring).monthEditable)
        assertFalse(bill(BillOrigin.Provider).monthEditable)
    }

    @Test
    fun stoppingEndsTheMonthBefore() {
        assertEquals(
            YearMonth.of(2026, 4),
            stoppedEnd(YearMonth.of(2026, 1), YearMonth.of(2026, 5))
        )
        assertEquals(
            YearMonth.of(2026, 1),
            stoppedEnd(YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        )
    }

    private fun month(m: Int, y: Int = 2026) = YearMonth.of(y, m)

    @Test
    fun billedMonthsAreTaken() {
        val bills = listOf(
            bill(BillOrigin.Manual).copy(id = 1, month = month(3)),
            bill(BillOrigin.Recurring).copy(id = 2, month = month(5)),
        )
        val taken = takenMonths(bills, emptyList())
        assertEquals(
            listOf(month(3), month(5)),
            taken.conflicts(listOf(month(5), month(4), month(3), month(3)))
        )
        assertEquals(emptyList<YearMonth>(), taken.conflicts(listOf(month(4), month(6))))
        // The bill being edited does not stand in its own way.
        assertEquals(
            emptyList<YearMonth>(),
            takenMonths(bills, emptyList(), except = 1).conflicts(listOf(month(3)))
        )
    }

    @Test
    fun recurringBillsTakeTheirLaterMonthsToo() {
        val recurring = listOf(
            RecurringBill(
                id = 1,
                itemId = 1,
                amount = 100,
                startMonth = month(1),
                endMonth = month(6)
            ),
            RecurringBill(id = 2, itemId = 1, amount = 100, startMonth = month(1, 2027)),
        )
        val taken = takenMonths(emptyList(), recurring)
        assertEquals(listOf(month(6)), taken.conflicts(listOf(month(6), month(7))))
        assertEquals(listOf(month(3, 2030)), taken.conflicts(listOf(month(3, 2030))))
    }

    @Test
    fun aNewRecurringBillMustNotMeetAnyBill() {
        val taken = TakenMonths(
            billed = setOf(month(2), month(9)),
            recurring = listOf(MonthSpan(month(1, 2027), null)),
        )
        // Open-ended: every billed month from its start, and the first month shared with a recurring bill.
        assertEquals(listOf(month(9), month(1, 2027)), taken.conflicts(MonthSpan(month(3), null)))
        assertEquals(emptyList<YearMonth>(), taken.conflicts(MonthSpan(month(3), month(8))))
        assertEquals(listOf(month(2)), taken.conflicts(MonthSpan(month(1), month(2))))
        assertEquals(
            listOf(month(5, 2027)),
            taken.conflicts(MonthSpan(month(5, 2027), month(5, 2027)))
        )
    }

    @Test
    fun theNearestMonthOfASpanIsTheMonthItselfOrAnEnd() {
        val span = MonthSpan(month(3), month(8))
        assertEquals(month(5), span.nearest(month(5)))
        assertEquals(month(3), span.nearest(month(1)))
        assertEquals(month(8), span.nearest(month(12)))
        // No end: a later month is always in the span.
        assertEquals(month(1, 2030), MonthSpan(month(3)).nearest(month(1, 2030)))
    }

    @Test
    fun aRecurringBillSpansItsStartThroughItsEnd() {
        val recurring =
            RecurringBill(id = 1, itemId = 1, amount = 100, startMonth = month(3), endMonth = null)
        assertEquals(MonthSpan(month(3), null), recurring.span)
        assertFalse(month(2) in recurring.span)
        assertTrue(month(3) in recurring.copy(endMonth = month(3)).span)
    }

    @Test
    fun stoppingFromTheFirstMonthLeavesNothing() {
        assertNull(stoppedEnd(YearMonth.of(2026, 1), YearMonth.of(2026, 1)))
        assertNull(stoppedEnd(YearMonth.of(2026, 3), YearMonth.of(2026, 1)))
    }
}
