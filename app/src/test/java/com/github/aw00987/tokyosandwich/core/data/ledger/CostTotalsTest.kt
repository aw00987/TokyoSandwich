package com.github.aw00987.tokyosandwich.core.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class CostTotalsTest {
    private fun item(id: Long, parent: Long?, kind: ItemKind) =
        CostItem(id = id, parentId = parent, title = "item $id", kind = kind, createdAt = id)

    // 1 ─┬─ 2
    //    ├─ 3 ─┬─ 5 ── 6
    //    │     └─ 7
    //    └─ 4 ── 8
    private val tree = listOf(
        item(1, null, ItemKind.Set),
        item(2, 1, ItemKind.Cost),
        item(3, 1, ItemKind.Set),
        item(4, 1, ItemKind.Set),
        item(5, 3, ItemKind.Set),
        item(6, 5, ItemKind.Cost),
        item(7, 3, ItemKind.Cost),
        item(8, 4, ItemKind.Cost),
    )

    @Test
    fun amountsAddUpTheTree() {
        val totals = rollUp(tree, mapOf(2L to 100L, 6L to 20L, 7L to 3L))
        assertEquals(
            mapOf(1L to 123L, 2L to 100L, 3L to 23L, 5L to 20L, 6L to 20L, 7L to 3L),
            totals,
        )
    }

    @Test
    fun itemsWithoutBillsAreLeftOutButAZeroBillCounts() {
        val totals = MonthTotals(YearMonth.of(2026, 10), rollUp(tree, mapOf(8L to 0L)))
        assertEquals(0L, totals[8])
        assertEquals(0L, totals[4])
        assertEquals(0L, totals[1])
        assertNull(totals[2])
        assertNull(totals[3])
    }

    @Test
    fun amountsOfUnknownItemsAreIgnored() {
        assertEquals(emptyMap<Long, Long>(), rollUp(tree, mapOf(99L to 5L)))
        assertEquals(emptyMap<Long, Long>(), rollUp(emptyList(), mapOf(1L to 5L)))
    }

    @Test
    fun theWarmMonthsAreTheLastCompleteOnes() {
        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2025, 12), YearMonth.of(2025, 11)),
            lastCompleteMonths(YearMonth.of(2026, 2)),
        )
    }

    @Test
    fun aSetIsSplitIntoItsLargestItemsAndTheRest() {
        val children = (1L..11L).map { item(it, 0, ItemKind.Cost) }
        // 1 and 2 cost nothing; 3..11 cost 30, 40, … 110; 5 costs as much as 6.
        val totals = mapOf(1L to 0L) + (3L..11L).associateWith { it * 10 } + (5L to 60L)
        val split = breakdown(children, totals)
        assertEquals(listOf(11L, 10L, 9L, 8L, 7L, 5L, 6L, 4L), split.top.map { it.item.id })
        assertEquals(listOf(3L), split.other.map { it.item.id })
        assertEquals(30L, split.otherAmount)
        assertEquals(listOf(1L, 2L), split.empty.map { it.id })
        assertEquals(30L + 40 + 60 + 60 + 70 + 80 + 90 + 100 + 110, split.total)
    }

    @Test
    fun eightItemsNeedNoOther() {
        val children = (1L..8L).map { item(it, 0, ItemKind.Cost) }
        val split = breakdown(children, children.associate { it.id to it.id })
        assertEquals(8, split.top.size)
        assertEquals(emptyList<ItemShare>(), split.other)
        assertEquals(
            SetBreakdown(0, emptyList(), emptyList(), children),
            breakdown(children, emptyMap())
        )
    }
}
