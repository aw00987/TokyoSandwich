package com.github.aw00987.tokyosandwich.cost.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.BillResult
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.DeletionCheck
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemResult
import com.github.aw00987.tokyosandwich.core.data.ledger.LedgerStore
import com.github.aw00987.tokyosandwich.core.data.ledger.RecurringBill
import com.github.aw00987.tokyosandwich.core.data.ledger.TakenMonths
import com.github.aw00987.tokyosandwich.core.data.ledger.childrenOf
import com.github.aw00987.tokyosandwich.core.data.ledger.nameClash
import com.github.aw00987.tokyosandwich.core.data.ledger.pathTo
import com.github.aw00987.tokyosandwich.core.data.ledger.root
import com.github.aw00987.tokyosandwich.core.data.ledger.takenMonths
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** A sub-item as its row shows it. */
data class CostChild(val item: CostItem, val childCount: Int)

/** One level of the cost tree: the item that is open, the way to it from the root, and its sub-items. */
data class CostUiState(
    val current: CostItem,
    /** From the root down to [current], both included. */
    val path: List<CostItem>,
    val children: List<CostChild>,
    /** The whole tree, for the rule that names of one level do not clash. */
    val items: List<CostItem>,
) {
    /** Null on the root. */
    val parent: CostItem? get() = path.getOrNull(path.size - 2)
}

/** Something that just happened, to tell the user once. */
sealed interface CostEvent {
    /**
     * [itemId] was moved into [target]; moving it back to [previousParentId], before [previousBeforeId] (null: at the
     * end), undoes it.
     */
    data class Moved(
        val itemId: Long,
        val target: CostItem,
        val previousParentId: Long,
        val previousBeforeId: Long?,
    ) : CostEvent

    /** Nothing was changed: [level] already has [clash], whose name is the same. */
    data class NameTaken(val clash: CostItem, val level: CostItem) : CostEvent

    /** [count] bills were added. */
    data class BillsAdded(val count: Int) : CostEvent

    /** A recurring bill was saved; [count] of its months had come and got their bills (none when it starts later). */
    data class RecurringAdded(val count: Int) : CostEvent

    /** A recurring bill's amount was changed from [from] on. */
    data class RecurringChanged(val from: YearMonth) : CostEvent

    /** A recurring bill was stopped from [from] on, and [count] of its bills deleted. */
    data class RecurringStopped(val from: YearMonth, val count: Int) : CostEvent

    /** [count] bills were deleted (one, or the months a stopped recurring bill had from then on). */
    data class BillsDeleted(val count: Int) : CostEvent

    /** Nothing was saved: the cost has a bill for [months] already, which the user edits instead. */
    data class BillConflict(val months: List<YearMonth>) : CostEvent
}

/** What the "add bill" page hands back: one bill of [amount] for each of [months]. */
data class BillEntry(val amount: Long, val note: String, val months: List<YearMonth>)

/** A deletion waiting for the user: a confirmation, or why it cannot be done yet ([DeletionCheck.allowed]). */
data class PendingDeletion(val item: CostItem, val check: DeletionCheck)

/**
 * Browses the cost tree one level at a time, like a file manager. Only the id of the open item is kept (it survives
 * process death); everything else follows from the saved tree, so an item that is deleted or moved elsewhere can never
 * leave the screen pointing at something that is gone.
 */
class CostViewModel(
    private val ledger: LedgerStore,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val currentId: StateFlow<Long?> = saved.getStateFlow(KEY_CURRENT, null)

    /** Null until the tree is loaded. */
    val state: StateFlow<CostUiState?> = combine(ledger.items, currentId) { items, id ->
        val current = items.firstOrNull { it.id == id } ?: items.root() ?: return@combine null
        CostUiState(
            current = current,
            path = items.pathTo(current.id),
            children = items.childrenOf(current.id)
                .map { CostChild(it, items.childrenOf(it.id).size) },
            items = items,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val openCostId = state
        .map { shown -> shown?.current?.takeIf { it.kind == ItemKind.Cost }?.id }
        .distinctUntilChanged()

    /** The open cost's bills, newest month first; empty on a set. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val bills: StateFlow<List<BillEntity>> = openCostId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else ledger.bills(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The months the open cost has a bill for, or a recurring bill will add: a new bill cannot take them. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val taken: StateFlow<TakenMonths> = openCostId
        .flatMapLatest { id ->
            if (id == null) flowOf(TakenMonths())
            else combine(ledger.bills(id), ledger.recurringBills(id)) { bills, recurring ->
                takenMonths(bills, recurring)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TakenMonths())

    /** The open cost's recurring bills, oldest first; empty on a set. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recurring: StateFlow<List<RecurringBill>> = openCostId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else ledger.recurringBills(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<CostEvent>(Channel.BUFFERED)
    val events: Flow<CostEvent> = _events.receiveAsFlow()

    private val _pendingDeletion = MutableStateFlow<PendingDeletion?>(null)
    val pendingDeletion: StateFlow<PendingDeletion?> = _pendingDeletion.asStateFlow()

    fun open(id: Long) {
        saved[KEY_CURRENT] = id
    }

    /** Goes to the parent of the open item; false on the root. */
    fun up(): Boolean {
        val parent = state.value?.parent ?: return false
        open(parent.id)
        return true
    }

    /**
     * The item of level [parentId] whose name clashes with [title] (null = [item]'s own name), leaving out [item]
     * itself; null when there is none. For the dialogs; the ledger checks again when saving.
     */
    fun nameClash(parentId: Long, title: String?, item: CostItem? = null): CostItem? {
        val items = state.value?.items ?: return null
        val probe = item?.let { if (title != null) it.copy(title = title, builtIn = null) else it }
            ?: CostItem(
                id = -1,
                parentId = parentId,
                title = title.orEmpty(),
                kind = ItemKind.Cost,
                createdAt = 0
            )
        return items.nameClash(parentId, probe, ledger.names)
    }

    /** Adds a cost or a set at the end of the open item (a set). */
    fun addItem(kind: ItemKind, title: String, note: String, icon: String?) {
        val parentId = state.value?.current?.id ?: return
        viewModelScope.launch { report(ledger.addItem(parentId, kind, title, note, icon)) }
    }

    private suspend fun report(result: ItemResult) {
        if (result !is ItemResult.NameTaken) return
        val level = state.value?.items?.firstOrNull { it.id == result.clash.parentId } ?: return
        _events.send(CostEvent.NameTaken(result.clash, level))
    }

    // These take the item's id: an edit is saved when its field closes, which may be after another item was opened.

    /** Renames the item (unless [title] is null: unchanged) and sets its icon (null = the default). */
    fun edit(id: Long, title: String?, icon: String?) {
        viewModelScope.launch { report(ledger.editItem(id, title, icon)) }
    }

    fun setNote(id: Long, note: String) {
        viewModelScope.launch { ledger.setNote(id, note) }
    }

    /** Moves the sub-item [id] into [targetId] (at the end), and offers to undo it. */
    fun move(id: Long, targetId: Long) {
        val shown = state.value ?: return
        val target =
            (shown.children.map { it.item } + shown.path).firstOrNull { it.id == targetId }
                ?: return
        val siblings = shown.children.map { it.item.id }
        val next = siblings.getOrNull(siblings.indexOf(id) + 1)
        viewModelScope.launch {
            when (val result = ledger.moveItem(id, targetId)) {
                is ItemResult.Done -> _events.send(
                    CostEvent.Moved(id, target, shown.current.id, previousBeforeId = next)
                )

                else -> report(result)
            }
        }
    }

    /** Puts the sub-item [id] of the open item just before its sibling [beforeId] (null: at the end). */
    fun reorder(id: Long, beforeId: Long?) {
        val parentId = state.value?.current?.id ?: return
        viewModelScope.launch { ledger.moveItem(id, parentId, beforeId) }
    }

    fun undoMove(event: CostEvent.Moved) {
        viewModelScope.launch {
            report(ledger.moveItem(event.itemId, event.previousParentId, event.previousBeforeId))
        }
    }

    /** Asks to delete the open item: checks whether it can be, for the confirmation or the reason it cannot. */
    fun requestDeletion() {
        val item = state.value?.current ?: return
        viewModelScope.launch {
            ledger.deletionCheck(item.id)
                ?.let { _pendingDeletion.value = PendingDeletion(item, it) }
        }
    }

    fun cancelDeletion() {
        _pendingDeletion.value = null
    }

    fun confirmDeletion() {
        val pending = _pendingDeletion.value ?: return
        _pendingDeletion.value = null
        if (!pending.check.allowed) return
        pending.item.parentId?.let(::open)
        viewModelScope.launch { ledger.deleteItem(pending.item.id) }
    }

    /** Adds [entry]'s bills to the open item. */
    fun addBill(entry: BillEntry) {
        val itemId = state.value?.current?.id ?: return
        viewModelScope.launch {
            report(
                ledger.addBills(
                    itemId,
                    entry.amount,
                    entry.note,
                    entry.months
                )
            ) { CostEvent.BillsAdded(it) }
        }
    }

    /** Adds a bill of [amount] every month from [start] through [end] (null = no end) to the open item. */
    fun addRecurring(amount: Long, note: String, start: YearMonth, end: YearMonth?) {
        val itemId = state.value?.current?.id ?: return
        viewModelScope.launch {
            report(
                ledger.addRecurring(
                    itemId,
                    amount,
                    note,
                    start,
                    end
                )
            ) { CostEvent.RecurringAdded(it) }
        }
    }

    /** Changes the recurring bill's amount from [from] on; its earlier bills stay. */
    fun changeRecurringAmount(recurring: RecurringBill, from: YearMonth, amount: Long) {
        viewModelScope.launch {
            report(
                ledger.changeRecurringAmount(
                    recurring.id,
                    from,
                    amount
                )
            ) { CostEvent.RecurringChanged(from) }
        }
    }

    /** Stops the recurring bill from [from] on: its bills from then are deleted, and no more are added. */
    fun stopRecurring(recurring: RecurringBill, from: YearMonth) {
        viewModelScope.launch {
            val count = ledger.stopRecurring(recurring.id, from)
            _events.send(CostEvent.RecurringStopped(from, count))
        }
    }

    private suspend fun report(result: BillResult, done: (Int) -> CostEvent) {
        when (result) {
            is BillResult.Done -> _events.send(done(result.count))
            is BillResult.Conflict -> _events.send(CostEvent.BillConflict(result.months))
            BillResult.Refused -> Unit
        }
    }

    /** Changes a bill's amount and note, and its month if it is a manual bill (not to a month that has a bill). */
    fun updateBill(bill: BillEntity, month: YearMonth, amount: Long, note: String) {
        viewModelScope.launch {
            val result = ledger.updateBill(bill.id, month, amount, note)
            if (result is BillResult.Conflict) _events.send(CostEvent.BillConflict(result.months))
        }
    }

    /** Deletes a manual bill; a recurring one is stopped from its month on (that month and the later ones go). */
    fun deleteBill(bill: BillEntity) {
        viewModelScope.launch {
            val recurringId = bill.recurringId
            val count = if (recurringId != null) ledger.stopRecurring(recurringId, bill.month)
            else if (ledger.deleteBill(bill.id)) 1 else 0
            if (count > 0) _events.send(CostEvent.BillsDeleted(count))
        }
    }

    private companion object {
        const val KEY_CURRENT = "current"
    }
}
