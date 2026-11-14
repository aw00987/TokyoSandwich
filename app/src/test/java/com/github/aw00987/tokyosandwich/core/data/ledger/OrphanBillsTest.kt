package com.github.aw00987.tokyosandwich.core.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class OrphanBillsTest {

    // The default tree (1 root, 2 electricity, 3 gas, 4 water), plus 5 a set with 6 a cost, and a recurring bill 7 of
    // 6 from March through June.
    private val tree = defaultTree().let { base ->
        base.copy(
            nextId = 8,
            items = base.items + listOf(
                CostItem(5, ROOT_ID, "set", kind = ItemKind.Set, createdAt = 5),
                CostItem(6, 5, "cost", kind = ItemKind.Cost, createdAt = 6),
            ),
            recurring = listOf(
                RecurringBill(
                    7,
                    6,
                    100,
                    startMonth = YearMonth.of(2026, 3),
                    endMonth = YearMonth.of(2026, 6)
                ),
            ),
        )
    }

    private var nextBill = 1L
    private fun bill(
        item: Long,
        month: Int,
        origin: BillOrigin = BillOrigin.Manual,
        recurring: Long? = null
    ) =
        BillEntity(
            id = nextBill++,
            itemId = item,
            month = YearMonth.of(2026, month),
            amount = 100,
            origin = origin,
            ext = recurring?.let { BillExt(recurringId = it) },
            createdAt = 0,
        )

    @Test
    fun theDefaultTreeHasTheRootAndTheUtilities() {
        val default = defaultTree()
        assertEquals(listOf(null, ROOT_ID, ROOT_ID, ROOT_ID), default.items.map { it.parentId })
        assertEquals(
            listOf(null, ProviderKind.Electricity, ProviderKind.Gas, ProviderKind.Water),
            default.items.map { it.provider })
        assertEquals(5L, default.nextId)
    }

    @Test
    fun billsThatFitTheTreeStay() {
        val fitting = listOf(
            bill(6, 1),
            bill(6, 3, BillOrigin.Recurring, recurring = 7),
            bill(2, 4, BillOrigin.Provider),
        )
        assertEquals(emptyList<BillEntity>(), orphanBills(tree, fitting))
    }

    @Test
    fun billsThatDoNotFitTheTreeGo() {
        val orphans = listOf(
            bill(99, 1), // no such item
            bill(5, 1), // a set
            bill(2, 1), // the user's bill on a utility's item
            bill(6, 2, BillOrigin.Provider), // a utility's bill on the user's cost
            bill(6, 4, BillOrigin.Recurring, recurring = 99), // its recurring bill is gone
            bill(6, 4, BillOrigin.Recurring), // no recurring bill at all
            bill(6, 7, BillOrigin.Recurring, recurring = 7), // after its recurring bill ends
        )
        assertEquals(orphans, orphanBills(tree, orphans))
    }

    @Test
    fun recurringBillsOfItemsThatAreGoneGo() {
        val broken = tree.copy(
            recurring = tree.recurring + listOf(
                RecurringBill(8, 99, 100, startMonth = YearMonth.of(2026, 1)),
                RecurringBill(
                    9,
                    2,
                    100,
                    startMonth = YearMonth.of(2026, 1)
                ), // a utility takes no user bills
            ),
        )
        assertEquals(tree.recurring, broken.withoutOrphanRecurring().recurring)
    }
}
