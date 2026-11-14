package com.github.aw00987.tokyosandwich.core.data.ledger

import java.time.YearMonth

/** What one item's bill of a month amounts to (whole yen). */
data class ItemAmount(val itemId: Long, val amount: Long)

/**
 * What every item of the tree cost in [month]: a cost's own bill, a set's costs added up. An item with no bill
 * anywhere at or below it is not in [totals], which is not the same as a bill of 0 yen.
 */
data class MonthTotals(val month: YearMonth, val totals: Map<Long, Long>) {
    operator fun get(itemId: Long): Long? = totals[itemId]
}

/**
 * Adds the items' [own] amounts up the tree: each item's total is its own amount plus those of everything below it.
 * Amounts of items the tree does not have are ignored, and items without any amount at or below them are left out.
 */
fun rollUp(items: List<CostItem>, own: Map<Long, Long>): Map<Long, Long> {
    val parents = items.associate { it.id to it.parentId }
    val totals = HashMap<Long, Long>()
    for ((id, amount) in own) {
        if (id !in parents) continue
        var at: Long? = id
        // The tree has no cycles (`canMove`); the guard only keeps a broken one from looping.
        val seen = HashSet<Long>()
        while (at != null && seen.add(at)) {
            totals[at] = (totals[at] ?: 0L) + amount
            at = parents[at]
        }
    }
    return totals
}

/** How many sub-items of a set get a slice of their own on its ring chart; the smaller ones share one ("other"). */
const val MAX_SLICES = 8

/** A sub-item and what it cost in the month. */
data class ItemShare(val item: CostItem, val amount: Long)

/**
 * What a set's cost of a month is made of: the [top] sub-items by amount (largest first), the smaller ones that are
 * shown together as "other" ([other], largest first), and the sub-items that cost nothing ([empty]: no bill, or
 * nothing above 0 yen), which have no part in the [total].
 */
data class SetBreakdown(
    val total: Long,
    val top: List<ItemShare>,
    val other: List<ItemShare>,
    val empty: List<CostItem>,
) {
    val otherAmount: Long get() = other.sumOf { it.amount }
}

/**
 * Splits the [children] of a set by what they cost ([totals], from [rollUp]). At most [top] of them stand alone;
 * equal amounts keep the order of [children].
 */
fun breakdown(
    children: List<CostItem>,
    totals: Map<Long, Long>,
    top: Int = MAX_SLICES,
): SetBreakdown {
    val (paid, empty) = children.partition { (totals[it.id] ?: 0L) > 0L }
    val shares = paid.map { ItemShare(it, totals.getValue(it.id)) }.sortedByDescending { it.amount }
    return SetBreakdown(shares.sumOf { it.amount }, shares.take(top), shares.drop(top), empty)
}
