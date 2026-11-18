package com.github.aw00987.tokyosandwich.core.data.ledger

import com.github.aw00987.tokyosandwich.core.data.AppSettings
import com.github.aw00987.tokyosandwich.core.data.MonthlyRecord
import com.github.aw00987.tokyosandwich.core.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

/**
 * The persistence API of the ledger: the cost tree and its recurring bills ([CostTree], in the [settings]) and every
 * bill (in the database, [dao]). The cost tab organises the tree and adds the user's bills; each provider saves its
 * monthly records here as the bills of its built-in item.
 *
 * The two places share no transaction. Every change takes one lock, checks the tree's rules (`CostTree.kt`), so a
 * change the rules forbid does nothing, and writes the tree first and the bills second. What a change that stopped
 * halfway leaves (bills that no longer fit the tree, months a recurring bill has not added yet) is put right by
 * [startUp].
 */
class LedgerStore(
    private val dao: LedgerDao,
    private val settings: SettingsStore,
    /** The names of the built-in items in every language, for the rule that names of one level do not clash. */
    val names: BuiltInNames = BuiltInNames.None,
    private val clock: () -> Long = System::currentTimeMillis,
    private val currentMonth: () -> YearMonth = YearMonth::now,
) {
    private val lock = Mutex()

    private val tree: Flow<CostTree?> = settings.data.map { it.costs }.distinctUntilChanged()

    /**
     * Run when the app starts: makes the default tree if there is none, deletes the bills that do not fit the tree
     * ([orphanBills]) and the recurring bills of items that are gone, then adds the months the recurring bills have
     * reached since the app last ran.
     */
    suspend fun startUp() = lock.withLock {
        settings.update { if (it.costs == null) it.copy(costs = defaultTree()) else it }
        repair()
    }

    private suspend fun repair() {
        settings.update {
            val costs = it.costs?.withoutOrphanRecurring()
            it.copy(
                costs = costs,
                home = costs?.let { tree -> it.home.pruned(tree.items) } ?: it.home)
        }
        val tree = currentTree() ?: return
        val orphans = orphanBills(tree, dao.allBills())
        if (orphans.isNotEmpty()) dao.deleteBillRows(orphans.map { it.id })
        tree.recurring.forEach { insertReached(it) }
    }

    private suspend fun currentTree(): CostTree? = settings.current().costs

    /**
     * Applies [change] to the tree in one write and returns its result; null (and no write) when there is no tree or
     * [change] returns null.
     */
    private suspend fun <R : Any> changeTree(change: (CostTree) -> Pair<CostTree, R>?): R? {
        var result: R? = null
        settings.update { saved ->
            val (tree, r) = saved.costs?.let(change) ?: return@update saved
            result = r
            // A card of the home tab goes with its item.
            saved.copy(costs = tree, home = saved.home.pruned(tree.items))
        }
        return result
    }

    private suspend fun editItems(edit: (CostItem) -> CostItem) {
        changeTree { tree -> tree.copy(items = tree.items.map(edit)) to Unit }
    }

    // The cost tree.

    val items: Flow<List<CostItem>> = tree.map { it?.items.orEmpty() }

    /**
     * Adds a cost or an empty set at the end of the set [parentId]. Refused when
     * the parent is gone or is not a set, or the title is blank; [ItemResult.NameTaken] when an item of that level has
     * the name already. [icon] is the picked icon's key, null for the default one.
     */
    suspend fun addItem(
        parentId: Long,
        kind: ItemKind,
        title: String,
        note: String,
        icon: String? = null
    ): ItemResult =
        lock.withLock {
            if (title.isBlank()) return@withLock ItemResult.Refused
            val now = clock()
            var result: ItemResult = ItemResult.Refused
            changeTree { tree ->
                val parent = tree.items.firstOrNull { it.id == parentId }
                if (parent == null || !parent.acceptsChildren) return@changeTree null
                val id = tree.nextId
                val item =
                    CostItem(id, parentId, title.trim(), note.trim(), kind, icon, createdAt = now)
                tree.items.nameClash(parentId, item, names)?.let {
                    result = ItemResult.NameTaken(it)
                    return@changeTree null
                }
                result = ItemResult.Done(id)
                tree.copy(nextId = id + 1, items = tree.items + item) to Unit
            }
            result
        }

    /**
     * Renames the item (unless [title] is null: the name stays as it is), which then keeps the name in every language,
     * and sets its icon by its key (null = the default), in one change. Refused for the root and a blank title;
     * [ItemResult.NameTaken], with nothing changed, when another item of its level has the name.
     */
    suspend fun editItem(id: Long, title: String?, icon: String?): ItemResult = lock.withLock {
        if (title != null && title.isBlank()) return@withLock ItemResult.Refused
        var result: ItemResult = ItemResult.Refused
        changeTree { tree ->
            val item = tree.items.firstOrNull { it.id == id }
            val parentId = item?.parentId ?: return@changeTree null
            val edited = item.copy(
                title = title?.trim() ?: item.title,
                builtIn = if (title != null) null else item.builtIn,
                icon = icon,
            )
            if (title != null) {
                tree.items.nameClash(parentId, edited, names)?.let {
                    result = ItemResult.NameTaken(it)
                    return@changeTree null
                }
            }
            result = ItemResult.Done(id)
            tree.copy(items = tree.items.map { if (it.id == id) edited else it }) to Unit
        }
        result
    }

    /** The note keeps its line breaks; only the blank space around it is dropped. */
    suspend fun setNote(id: Long, note: String) = lock.withLock {
        editItems { if (it.id == id) it.copy(note = note.trim()) else it }
    }

    /**
     * Puts [id] below [targetId], just before its sub-item [beforeId] (null: at the end). Within its own level this
     * only changes the order. Into another set it is a move, which [canMove] must allow and which is
     * [ItemResult.NameTaken] when an item there has a name that clashes with its own.
     */
    suspend fun moveItem(id: Long, targetId: Long, beforeId: Long? = null): ItemResult =
        lock.withLock {
            var result: ItemResult = ItemResult.Refused
            changeTree { tree ->
                val item = tree.items.firstOrNull { it.id == id } ?: return@changeTree null
                if (item.parentId == null) return@changeTree null
                if (item.parentId != targetId) {
                    if (!tree.items.canMove(id, targetId)) return@changeTree null
                    tree.items.nameClash(targetId, item, names)?.let {
                        result = ItemResult.NameTaken(it)
                        return@changeTree null
                    }
                }
                result = ItemResult.Done(id)
                tree.copy(items = tree.items.placed(id, targetId, beforeId)) to Unit
            }
            result
        }

    /** Whether [id] can be deleted now; null when it is gone. */
    suspend fun deletionCheck(id: Long): DeletionCheck? = lock.withLock { check(id) }

    private suspend fun check(id: Long): DeletionCheck? {
        val items = currentTree()?.items ?: return null
        if (items.none { it.id == id }) return null
        return items.deletionCheck(id, dao.billCount(items.subtreeIds(id)))
    }

    /**
     * Deletes [id] if it is [deletable] and its [deletionCheck] allows it: a cost without bills, or a set with no cost
     * below it (its empty sets go with it), and the recurring bills of what is deleted.
     */
    suspend fun deleteItem(id: Long): Boolean = lock.withLock {
        val items = currentTree()?.items ?: return@withLock false
        val item = items.firstOrNull { it.id == id } ?: return@withLock false
        if (!item.deletable || check(id)?.allowed != true) return@withLock false
        val ids = items.subtreeIds(id)
        changeTree { tree ->
            tree.copy(
                items = tree.items.filter { it.id !in ids },
                recurring = tree.recurring.filter { it.itemId !in ids },
            ) to Unit
        }
        dao.deleteBillsOf(ids) // none, by the check; the bills follow the tree in any case
        true
    }

    // What the tree cost in a month.

    /** The earliest month that has a bill, null when there is none. */
    val firstMonth: Flow<YearMonth?> = dao.firstMonth().distinctUntilChanged()

    /** What every item cost in [month], added up the tree as it is now ([rollUp]). */
    suspend fun monthTotals(month: YearMonth): MonthTotals {
        val items = currentTree()?.items.orEmpty()
        val own = dao.monthAmounts(month).associate { it.itemId to it.amount }
        return MonthTotals(month, rollUp(items, own))
    }

    // The user's bills.

    /** The item's bills, newest month first. */
    fun bills(itemId: Long): Flow<List<BillEntity>> = dao.bills(itemId)

    /** The item's recurring bills. */
    fun recurringBills(itemId: Long): Flow<List<RecurringBill>> =
        tree.map { tree -> tree?.recurring.orEmpty().filter { it.itemId == itemId } }
            .distinctUntilChanged()

    /** The months [itemId] has taken in [tree], leaving out the bill [except]. */
    private suspend fun taken(tree: CostTree, itemId: Long, except: Long? = null): TakenMonths =
        takenMonths(dao.billRows(itemId), tree.recurring.filter { it.itemId == itemId }, except)

    /** The cost [itemId] if the user adds its bills; null otherwise. */
    private fun CostTree.billTaker(itemId: Long): CostItem? =
        items.firstOrNull { it.id == itemId }?.takeIf { it.acceptsBills }

    /** One bill of [amount] yen for each of [months]; nothing when any of them has a bill already. */
    suspend fun addBills(
        itemId: Long,
        amount: Long,
        note: String,
        months: List<YearMonth>
    ): BillResult =
        lock.withLock {
            val tree = currentTree() ?: return@withLock BillResult.Refused
            tree.billTaker(itemId) ?: return@withLock BillResult.Refused
            taken(tree, itemId).conflicts(months).takeIf { it.isNotEmpty() }
                ?.let { return@withLock BillResult.Conflict(it) }
            val now = clock()
            BillResult.Done(dao.insertBills(months.distinct().map {
                BillEntity(
                    itemId = itemId,
                    month = it,
                    amount = amount,
                    note = note.trim(),
                    origin = BillOrigin.Manual,
                    createdAt = now
                )
            }).count { it != -1L })
        }

    /**
     * A bill of [amount] yen every month from [start] through [end] (null = no end). The months up to the current one
     * are added now (counted in the result), the later ones as they come, by [startUp]. Nothing is saved when any
     * month of it has a bill already, or belongs to another recurring bill.
     */
    suspend fun addRecurring(
        itemId: Long,
        amount: Long,
        note: String,
        start: YearMonth,
        end: YearMonth?
    ): BillResult = lock.withLock {
        val tree = currentTree() ?: return@withLock BillResult.Refused
        tree.billTaker(itemId) ?: return@withLock BillResult.Refused
        taken(tree, itemId).conflicts(MonthSpan(start, end)).takeIf { it.isNotEmpty() }
            ?.let { return@withLock BillResult.Conflict(it) }
        val recurring = addRecurringToTree(itemId, amount, note.trim(), start, end)
            ?: return@withLock BillResult.Refused
        BillResult.Done(insertReached(recurring))
    }

    private suspend fun addRecurringToTree(
        itemId: Long,
        amount: Long,
        note: String,
        start: YearMonth,
        end: YearMonth?,
    ): RecurringBill? = changeTree { tree ->
        val recurring = RecurringBill(tree.nextId, itemId, amount, note, start, end)
        tree.copy(nextId = tree.nextId + 1, recurring = tree.recurring + recurring) to recurring
    }

    /** Adds the months [recurring] has reached by now and that have no bill yet; returns how many. */
    private suspend fun insertReached(recurring: RecurringBill): Int {
        val now = clock()
        return dao.insertBills(
            recurringMonths(
                recurring.startMonth,
                recurring.endMonth,
                currentMonth()
            ).map {
                BillEntity(
                    itemId = recurring.itemId,
                    month = it,
                    amount = recurring.amount,
                    note = recurring.note,
                    origin = BillOrigin.Recurring,
                    ext = BillExt(recurringId = recurring.id),
                    createdAt = now,
                )
            }).count { it != -1L }
    }

    /**
     * Changes a bill the user can [editable] edit: its amount and note, and its month only if [monthEditable] (a
     * recurring bill keeps its month; the change is to that month's bill only). A month the cost has [TakenMonths]
     * already is refused; a utility's bills are not changed.
     */
    suspend fun updateBill(id: Long, month: YearMonth, amount: Long, note: String): BillResult =
        lock.withLock {
            val bill = dao.bill(id) ?: return@withLock BillResult.Refused
            if (!bill.editable || amount <= 0) return@withLock BillResult.Refused
            val newMonth = if (bill.monthEditable) month else bill.month
            if (newMonth != bill.month) {
                val tree = currentTree() ?: return@withLock BillResult.Refused
                taken(tree, bill.itemId, except = id).conflicts(listOf(newMonth))
                    .takeIf { it.isNotEmpty() }
                    ?.let { return@withLock BillResult.Conflict(it) }
            }
            dao.updateBillRow(bill.copy(month = newMonth, amount = amount, note = note.trim()))
            BillResult.Done(1)
        }

    /**
     * Deletes a bill typed in by hand. A recurring bill's month is not deleted alone, because the next catch-up would
     * add it again: it is stopped with [stopRecurring]. False when nothing was deleted.
     */
    suspend fun deleteBill(id: Long): Boolean = lock.withLock {
        val bill = dao.bill(id) ?: return@withLock false
        if (bill.origin != BillOrigin.Manual) return@withLock false
        dao.deleteBillRow(id)
        true
    }

    /**
     * Stops the recurring bill [recurringId] from [from] on: it ends the month before (see [stoppedEnd]), or is deleted
     * when stopped from its first month, and its bills of [from] and later are deleted. Returns how many bills were
     * deleted.
     */
    suspend fun stopRecurring(recurringId: Long, from: YearMonth): Int = lock.withLock {
        val recurring =
            currentTree()?.recurring?.firstOrNull { it.id == recurringId } ?: return@withLock 0
        stop(recurring, from)
    }

    private suspend fun stop(recurring: RecurringBill, from: YearMonth): Int {
        val end = stoppedEnd(recurring.startMonth, from)
        changeTree { tree ->
            val kept = when {
                end == null -> tree.recurring.filter { it.id != recurring.id }
                recurring.endMonth == null || recurring.endMonth > end ->
                    tree.recurring.map { if (it.id == recurring.id) it.copy(endMonth = end) else it }

                else -> tree.recurring
            }
            tree.copy(recurring = kept) to Unit
        }
        return dao.deleteRecurringBillsFrom(recurring.id, from)
    }

    /**
     * Changes the amount of the recurring bill [recurringId] from [from] (a month of its span) on: it is stopped from
     * [from] and a new one with [amount], the same note and the same end takes over from [from], with its months up to
     * now. Its bills before [from] stay as they are; those from [from] are written anew, so a change made to one of
     * them alone is gone. Refused when [from] is outside its span or the amount is not positive.
     */
    suspend fun changeRecurringAmount(
        recurringId: Long,
        from: YearMonth,
        amount: Long
    ): BillResult =
        lock.withLock {
            val old = currentTree()?.recurring?.firstOrNull { it.id == recurringId }
                ?: return@withLock BillResult.Refused
            if (amount <= 0 || from !in old.span) return@withLock BillResult.Refused
            stop(old, from)
            val next = addRecurringToTree(old.itemId, amount, old.note, from, old.endMonth)
                ?: return@withLock BillResult.Refused
            BillResult.Done(insertReached(next))
        }

    // The providers' synced records.

    private suspend fun providerItemId(provider: ProviderKind): Long? =
        currentTree()?.items?.firstOrNull { it.provider == provider }?.id

    /** The provider's saved monthly records, oldest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun providerRecords(provider: ProviderKind): Flow<List<MonthlyRecord>> =
        tree.map { tree -> tree?.items?.firstOrNull { it.provider == provider }?.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else dao.providerBills(id).map { bills -> bills.map { it.toRecord() } }
            }

    /**
     * Merges a sync's records into the provider's, in one transaction: the months it returned are written anew, the
     * others are kept ([mergeProviderBills]).
     */
    suspend fun mergeProviderRecords(provider: ProviderKind, records: List<MonthlyRecord>) =
        lock.withLock {
            val id = providerItemId(provider) ?: return@withLock
            val now = clock()
            dao.mergeProviderBills(id, records.map { it.toBill(now) })
        }

    suspend fun clearProvider(provider: ProviderKind) = lock.withLock {
        providerItemId(provider)?.let { dao.deleteProviderBills(it) }
    }

    // Export and import.

    /** The settings and the user's bills (not the providers'), read together. */
    suspend fun snapshot(): Pair<AppSettings, List<BillEntity>> = lock.withLock {
        settings.current() to dao.allBills().filter { it.origin != BillOrigin.Provider }
    }

    /**
     * Replaces everything with an import: the settings (the tree among them) and every bill (the providers' are
     * deleted; their next sync brings them back). Then the bills are checked against the tree, as at [startUp].
     */
    suspend fun replaceAll(imported: AppSettings, bills: List<BillEntity>) = lock.withLock {
        settings.update { imported }
        dao.replaceAllBills(bills)
        repair()
    }
}

/** A provider's record as a bill row; the repository puts it on the provider's item. */
internal fun MonthlyRecord.toBill(now: Long, itemId: Long = 0): BillEntity = BillEntity(
    itemId = itemId,
    month = month,
    amount = cost,
    note = note,
    origin = BillOrigin.Provider,
    ext = BillExt(usage = usage, days = days),
    createdAt = now,
)

/** A provider's bill row as its record. */
internal fun BillEntity.toRecord(): MonthlyRecord = MonthlyRecord(
    month = month,
    usage = usage ?: 0.0,
    cost = amount,
    days = days,
    note = note,
)
