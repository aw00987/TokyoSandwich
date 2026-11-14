package com.github.aw00987.tokyosandwich.core.data.ledger

import java.text.Normalizer
import java.time.YearMonth
import java.util.Locale

// The rules of the cost tree, as pure functions over all of its items.

/** The root: the one item without a parent. */
fun List<CostItem>.root(): CostItem? = firstOrNull { it.parentId == null }

fun List<CostItem>.childrenOf(id: Long): List<CostItem> = filter { it.parentId == id }

/** From the root down to the item [id], both included; empty when [id] is unknown. */
fun List<CostItem>.pathTo(id: Long): List<CostItem> {
    val byId = associateBy { it.id }
    val path = ArrayDeque<CostItem>()
    var item = byId[id]
    // A tree has no cycles, but a broken file must not hang the app.
    while (item != null && path.size <= size) {
        path.addFirst(item)
        item = item.parentId?.let(byId::get)
    }
    return path
}

/** The item [id] and everything below it. */
fun List<CostItem>.subtreeIds(id: Long): Set<Long> {
    val children = groupBy { it.parentId }
    val ids = linkedSetOf<Long>()
    val pending = ArrayDeque(listOf(id))
    while (pending.isNotEmpty()) {
        val next = pending.removeFirst()
        if (ids.add(next)) children[next]?.forEach { pending += it.id }
    }
    return ids
}

/** Whether sub-items can be put into the item: only sets hold sub-items. */
val CostItem.acceptsChildren: Boolean get() = kind == ItemKind.Set

/** Whether the user adds this item's bills: only costs have bills, and the utilities' come from their sync. */
val CostItem.acceptsBills: Boolean get() = kind == ItemKind.Cost && provider == null

/**
 * Whether the item can ever be deleted: not the root, not the utilities' built-in items. Whether it can be deleted
 * now is [deletionCheck]'s answer.
 */
val CostItem.deletable: Boolean get() = parentId != null && provider == null

/**
 * What stands in the way of deleting an item, and what would go with it. A cost can be deleted once it has no bills;
 * a set once no cost is left anywhere below it (the empty sets below it are deleted with it).
 */
data class DeletionCheck(
    val kind: ItemKind,
    /** The bills of the item and everything below it. */
    val bills: Int,
    /** The costs below a set, at any depth. */
    val costs: Int,
    /** The sets below a set, at any depth; all empty when the set can be deleted. */
    val sets: Int,
) {
    val allowed: Boolean get() = bills == 0 && costs == 0
}

/** [DeletionCheck] for the item [id], given the number of [bills] of its subtree. */
fun List<CostItem>.deletionCheck(id: Long, bills: Int): DeletionCheck {
    val item = first { it.id == id }
    val below = subtreeIds(id) - id
    val belowItems = filter { it.id in below }
    return DeletionCheck(
        kind = item.kind,
        bills = bills,
        costs = belowItems.count { it.kind == ItemKind.Cost },
        sets = belowItems.count { it.kind == ItemKind.Set },
    )
}

/**
 * Whether the item [id] can become a sub-item of [targetId]: both exist, the item is not the root, the target is not
 * the item itself or below it (that would cut the subtree off the tree), takes sub-items and is not where it already
 * is.
 */
fun List<CostItem>.canMove(id: Long, targetId: Long): Boolean {
    val item = firstOrNull { it.id == id } ?: return false
    val target = firstOrNull { it.id == targetId } ?: return false
    return item.parentId != null &&
            item.parentId != targetId &&
            target.acceptsChildren &&
            targetId !in subtreeIds(id)
}

/**
 * The names a built-in item ([CostItem.builtIn]) is shown by, in every language of the app: its name follows the
 * language, so it must not clash with a sibling's in any of them.
 */
fun interface BuiltInNames {
    fun of(title: BuiltInTitle): Set<String>

    companion object {
        /** Built-in items clash only with built-in items of the same title (for tests). */
        val None = BuiltInNames { emptySet() }
    }
}

/**
 * The form two names are compared in: Unicode NFKC (full- and half-width forms, composed characters), blank space
 * collapsed and trimmed, case folded. Two items of one level whose names have the same key clash.
 */
fun nameKey(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKC)
        .trim().replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

/** The keys of the names [item] is shown by: the user's name, or a built-in item's names in every language. */
private fun BuiltInNames.keysOf(item: CostItem): Set<String> = when (val builtIn = item.builtIn) {
    null -> setOf(nameKey(item.title))
    // The enum itself as well, so two built-in items of one title clash even without the names.
    else -> of(builtIn).mapTo(hashSetOf("\u0000$builtIn"), ::nameKey)
}

/**
 * The item below [parentId] whose name clashes with [item]'s (one that is, or would be, of that level), or null.
 * [item] itself is left out, so it is not its own clash.
 */
fun List<CostItem>.nameClash(parentId: Long, item: CostItem, names: BuiltInNames): CostItem? {
    val keys = names.keysOf(item)
    return firstOrNull {
        it.parentId == parentId && it.id != item.id && names.keysOf(it).any(keys::contains)
    }
}

/** Whether two items of one level have names that clash (a broken tree, e.g. an import). */
fun List<CostItem>.hasNameClash(names: BuiltInNames): Boolean =
    any { item -> item.parentId != null && nameClash(item.parentId, item, names) != null }

/**
 * The items with [id] moved below [parentId], just before its sibling [beforeId] (null, or not a sibling: after the
 * last one; the item itself: where it is). The items of one level are listed in the order of [CostTree.items], so this is the one way the order
 * changes; it does not check the rules ([canMove], [nameClash]).
 */
fun List<CostItem>.placed(id: Long, parentId: Long, beforeId: Long? = null): List<CostItem> {
    val item = firstOrNull { it.id == id } ?: return this
    if (beforeId == id) return if (item.parentId == parentId) this else placed(id, parentId)
    val rest = filter { it.id != id }
    val moved = item.copy(parentId = parentId)
    val before = rest.indexOfFirst { it.id == beforeId && it.parentId == parentId }
    val last = rest.indexOfLast { it.parentId == parentId }
    val at = when {
        before >= 0 -> before
        last >= 0 -> last + 1
        else -> rest.size
    }
    return rest.toMutableList().apply { add(at, moved) }
}

/** Whether the user can change or delete the bill: a utility's bills come from its sync, which replaces them. */
val BillEntity.editable: Boolean get() = origin != BillOrigin.Provider

/**
 * Whether the bill can move to another month: only a bill typed in by hand. A recurring bill's month is its place in
 * the series (one bill per month), and a utility's is the provider's.
 */
val BillEntity.monthEditable: Boolean get() = origin == BillOrigin.Manual

/**
 * The end month a recurring bill from [start] has once it is stopped [from] that month on; null when nothing is left
 * of it (stopped from its first month or earlier), and the recurring bill is deleted.
 */
fun stoppedEnd(start: YearMonth, from: YearMonth): YearMonth? =
    from.minusMonths(1).takeIf { it >= start }

/** The months from [start] through [end] (null = no end), both included. */
data class MonthSpan(val start: YearMonth, val end: YearMonth? = null) {
    operator fun contains(month: YearMonth): Boolean =
        month >= start && (end == null || month <= end)

    /** The first month both spans have, or null when they have none. */
    fun firstShared(other: MonthSpan): YearMonth? =
        maxOf(start, other.start).takeIf { it in this && it in other }

    /** The month of the span nearest to [month]: [month] itself when it is in the span. */
    fun nearest(month: YearMonth): YearMonth = when {
        month < start -> start
        end != null && month > end -> end
        else -> month
    }
}

/** The months a recurring bill runs: from its first month through its last (null = no end). */
val RecurringBill.span: MonthSpan get() = MonthSpan(startMonth, endMonth)

/**
 * The months a cost already has a bill for, or will have: the months of its bills and the spans of its recurring bills
 * (which add their later months as they come). A cost has at most one bill per month, so a new bill for any of these
 * months is refused, and the user edits the bill that is there instead.
 */
data class TakenMonths(
    val billed: Set<YearMonth> = emptySet(),
    val recurring: List<MonthSpan> = emptyList()
) {
    /** Which of [months] are taken, oldest first. */
    fun conflicts(months: Collection<YearMonth>): List<YearMonth> =
        months.filter { month -> month in billed || recurring.any { month in it } }.distinct()
            .sorted()

    /**
     * Which months of a new recurring bill over [span] are taken, oldest first: the billed months in it, and the
     * first month it shares with each recurring bill.
     */
    fun conflicts(span: MonthSpan): List<YearMonth> =
        (billed.filter { it in span } + recurring.mapNotNull { it.firstShared(span) }).distinct()
            .sorted()
}

/** The months [bills] and [recurring] (all of one cost) take, leaving out the bill [except] (the one being edited). */
fun takenMonths(
    bills: List<BillEntity>,
    recurring: List<RecurringBill>,
    except: Long? = null,
): TakenMonths = TakenMonths(
    billed = bills.filter { it.id != except }.mapTo(HashSet()) { it.month },
    recurring = recurring.map { it.span },
)

/** What a change to the bills did. */
sealed interface BillResult {
    /** [count] bills were added or changed. */
    data class Done(val count: Int) : BillResult

    /** Nothing was changed: the cost has a bill for [months] already (oldest first). */
    data class Conflict(val months: List<YearMonth>) : BillResult

    /** Nothing was changed: the item or bill is gone, or the rules forbid the change. */
    data object Refused : BillResult
}

/** What a change to the tree's items did. */
sealed interface ItemResult {
    /** Done; [id] is the item changed or added. */
    data class Done(val id: Long) : ItemResult

    /** Nothing was changed: the level already has an item of that name, [clash]. */
    data class NameTaken(val clash: CostItem) : ItemResult

    /** Nothing was changed: the item is gone, or the rules forbid the change. */
    data object Refused : ItemResult
}

/** The id of the root of the default tree. */
const val ROOT_ID = 1L

/** The tree the app starts with: the root (a set) and the three utilities (costs) under it. */
fun defaultTree(): CostTree {
    val utilities = listOf(
        ProviderKind.Electricity to BuiltInTitle.Electricity,
        ProviderKind.Gas to BuiltInTitle.Gas,
        ProviderKind.Water to BuiltInTitle.Water,
    ).mapIndexed { index, (kind, title) ->
        CostItem(
            id = ROOT_ID + 1 + index,
            parentId = ROOT_ID,
            title = "",
            kind = ItemKind.Cost,
            provider = kind,
            createdAt = index + 1L,
            builtIn = title,
        )
    }
    val root = CostItem(
        id = ROOT_ID,
        parentId = null,
        title = "",
        kind = ItemKind.Set,
        createdAt = 0,
        builtIn = BuiltInTitle.Root,
    )
    return CostTree(nextId = ROOT_ID + 1 + utilities.size, items = listOf(root) + utilities)
}

/**
 * The bills that do not fit the tree, which is what counts (there is no foreign key between the two): a bill whose
 * item is not a cost of the tree; a provider's bill not on its utility's item, or the user's bill on one; a recurring
 * bill whose recurring bill is gone, belongs to another item or does not run in its month. Such bills are left by a
 * change that stopped halfway, and are deleted when the app starts.
 */
fun orphanBills(tree: CostTree, bills: List<BillEntity>): List<BillEntity> {
    val costs = tree.items.filter { it.kind == ItemKind.Cost }.associateBy { it.id }
    val recurring = tree.recurring.associateBy { it.id }
    return bills.filter { bill ->
        val item = costs[bill.itemId] ?: return@filter true
        when (bill.origin) {
            BillOrigin.Provider -> item.provider == null
            BillOrigin.Manual -> item.provider != null
            BillOrigin.Recurring -> {
                val template = bill.recurringId?.let(recurring::get)
                item.provider != null || template == null || template.itemId != bill.itemId ||
                        bill.month !in template.span
            }
        }
    }
}

/** The tree without the recurring bills of items that are gone or take no bills. */
fun CostTree.withoutOrphanRecurring(): CostTree {
    val takers = items.filter { it.acceptsBills }.mapTo(HashSet()) { it.id }
    return copy(recurring = recurring.filter { it.itemId in takers })
}
