package com.github.aw00987.tokyosandwich.cost.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.RecurringBill
import com.github.aw00987.tokyosandwich.core.data.ledger.TakenMonths
import com.github.aw00987.tokyosandwich.core.data.ledger.acceptsBills
import com.github.aw00987.tokyosandwich.core.data.ledger.acceptsChildren
import com.github.aw00987.tokyosandwich.core.data.ledger.deletable
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * The cost tab: the user's costs as a tree, browsed like a file manager. Sets (费用集) are the folders, costs (费用) the
 * files; what an item is never changes. Every level is this same page for the item that is open: its icon and name in
 * the top bar, its note on a card, then a set's items. Long-pressing the name edits the name and icon, long-pressing
 * the note edits the note. A cost shows its recurring bills below the note (`RecurringCard.kt`), then its bills, in a
 * list of their own (`BillList.kt`), where they are added, edited and deleted. The house in the top bar puts the
 * item's card on the home tab, or takes it off.
 */
@Composable
fun CostScreen(
    viewModel: CostViewModel,
    /** Whether the item has a card on the home tab. */
    onHome: (CostItem) -> Boolean,
    /** Puts the item's card on the home tab, or takes it off. */
    onToggleHome: (CostItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingDeletion by viewModel.pendingDeletion.collectAsStateWithLifecycle()
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val taken by viewModel.taken.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    var addingBill by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CostEvent.Moved -> {
                    val result = snackbar.showSnackbar(
                        message = resources.getString(
                            R.string.cost_moved,
                            itemTitle(resources, event.target)
                        ),
                        actionLabel = resources.getString(R.string.cost_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoMove(event)
                }

                is CostEvent.NameTaken -> snackbar.showSnackbar(
                    resources.getString(
                        R.string.cost_name_clash,
                        itemTitle(resources, event.level),
                        itemTitle(resources, event.clash),
                    )
                )

                is CostEvent.BillsAdded -> snackbar.showSnackbar(
                    resources.getString(R.string.bill_added, event.count)
                )

                is CostEvent.RecurringAdded -> snackbar.showSnackbar(
                    if (event.count > 0) resources.getString(R.string.bill_added, event.count)
                    else resources.getString(R.string.bill_saved_recurring)
                )

                is CostEvent.RecurringChanged -> snackbar.showSnackbar(
                    resources.getString(
                        R.string.recurring_changed,
                        monthText(resources, event.from)
                    )
                )

                is CostEvent.RecurringStopped -> snackbar.showSnackbar(
                    resources.getString(
                        R.string.recurring_stopped,
                        monthText(resources, event.from)
                    )
                )

                is CostEvent.BillsDeleted -> snackbar.showSnackbar(
                    resources.getString(R.string.bill_deleted, event.count)
                )

                is CostEvent.BillConflict -> snackbar.showSnackbar(
                    resources.getString(R.string.bill_conflict, monthsText(resources, event.months))
                )
            }
        }
    }

    val shown = state ?: return
    BackHandler(enabled = addingBill || shown.parent != null) {
        if (addingBill) addingBill = false else viewModel.up()
    }

    pendingDeletion?.let { pending ->
        DeletionDialog(
            pending,
            viewModel::confirmDeletion,
            viewModel::cancelDeletion
        )
    }

    if (addingBill && shown.current.acceptsBills) {
        AddBillScreen(
            target = shown.path.joinToString(" › ") { itemTitle(resources, it) },
            taken = taken,
            onBack = { addingBill = false },
            onSave = {
                viewModel.addBill(it)
                addingBill = false
            },
        )
    } else {
        CostLevel(
            state = shown,
            bills = bills,
            recurring = recurring,
            taken = taken,
            snackbar = snackbar,
            onHome = onHome(shown.current),
            onToggleHome = {
                val added = !onHome(shown.current)
                onToggleHome(shown.current)
                scope.launch {
                    snackbar.showSnackbar(
                        resources.getString(if (added) R.string.home_added else R.string.home_removed),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onOpen = viewModel::open,
            onUp = { viewModel.up() },
            onAddItem = viewModel::addItem,
            onEdit = viewModel::edit,
            onSetNote = viewModel::setNote,
            onDelete = viewModel::requestDeletion,
            onMove = viewModel::move,
            onReorder = viewModel::reorder,
            nameClash = viewModel::nameClash,
            onAddBill = { addingBill = true },
            onUpdateBill = viewModel::updateBill,
            onDeleteBill = viewModel::deleteBill,
            onAddRecurring = viewModel::addRecurring,
            onChangeRecurring = viewModel::changeRecurringAmount,
            onStopRecurring = viewModel::stopRecurring,
        )
    }
}

/** Confirms a deletion, or says why the item cannot be deleted yet. */
@Composable
private fun DeletionDialog(pending: PendingDeletion, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val check = pending.check
    if (!check.allowed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(
                        R.string.cost_delete_blocked_title,
                        itemTitle(pending.item)
                    )
                )
            },
            text = {
                Text(
                    if (check.kind == ItemKind.Cost) stringResource(
                        R.string.cost_delete_blocked_bills,
                        check.bills
                    )
                    else stringResource(R.string.cost_delete_blocked_costs, check.costs)
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_ok)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cost_delete_title, itemTitle(pending.item))) },
        text = {
            Text(
                if (check.sets == 0) stringResource(R.string.cost_delete_plain)
                else stringResource(R.string.cost_delete_with_sets, check.sets)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.cost_delete_item),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}

/** One level of the tree. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostLevel(
    state: CostUiState,
    bills: List<BillEntity>,
    recurring: List<RecurringBill>,
    taken: TakenMonths,
    snackbar: SnackbarHostState,
    onHome: Boolean,
    onToggleHome: () -> Unit,
    onOpen: (Long) -> Unit,
    onUp: () -> Unit,
    onAddItem: (kind: ItemKind, title: String, note: String, icon: String?) -> Unit,
    onEdit: (id: Long, title: String?, icon: String?) -> Unit,
    onSetNote: (id: Long, note: String) -> Unit,
    onDelete: () -> Unit,
    onMove: (id: Long, targetId: Long) -> Unit,
    onReorder: (id: Long, beforeId: Long?) -> Unit,
    /** The item of level parentId whose name clashes with title (null: the item's own), leaving out item itself. */
    nameClash: (parentId: Long, title: String?, item: CostItem?) -> CostItem?,
    onAddBill: () -> Unit,
    onUpdateBill: (bill: BillEntity, month: YearMonth, amount: Long, note: String) -> Unit,
    onDeleteBill: (BillEntity) -> Unit,
    onAddRecurring: (amount: Long, note: String, start: YearMonth, end: YearMonth?) -> Unit,
    onChangeRecurring: (RecurringBill, from: YearMonth, amount: Long) -> Unit,
    onStopRecurring: (RecurringBill, from: YearMonth) -> Unit,
) {
    val current = state.current
    val parent = state.parent
    // The kind of the item being created, while its dialog is open.
    var creating by rememberSaveable { mutableStateOf<ItemKind?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    if (renaming) {
        ItemEditDialog(
            item = current,
            clash = { title -> current.parentId?.let { nameClash(it, title, current) } },
            onDismiss = { renaming = false },
            onSave = { title, icon ->
                onEdit(current.id, title, icon)
                renaming = false
            },
        )
    }
    if (editingNote) {
        TextEditDialog(
            heading = stringResource(R.string.cost_edit_note),
            initial = current.note,
            singleLine = false,
            onDismiss = { editingNote = false },
            onSave = {
                onSetNote(current.id, it)
                editingNote = false
            },
        )
    }

    creating?.let { kind ->
        NewItemDialog(
            kind = kind,
            clash = { title -> nameClash(current.id, title, null) },
            onDismiss = { creating = null },
            onSave = { title, note, icon ->
                onAddItem(kind, title, note, icon)
                creating = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (parent == null) {
                        Icon(
                            painterResource(R.drawable.ic_cost),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                                .size(28.dp),
                        )
                    } else {
                        IconButton(onClick = onUp) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                stringResource(R.string.back)
                            )
                        }
                    }
                },
                title = {
                    // Long-press the name to rename the item; the root keeps its name, which is what the whole tree
                    // is called.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (parent == null) Modifier
                                else Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        renaming = true
                                    },
                                )
                            ),
                    ) {
                        // The root's icon is already the tab's, on the left.
                        if (parent != null) {
                            Icon(
                                painterResource(itemIcon(current)),
                                null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(itemTitle(current), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    // Every item can have a card on the home tab: a cost its amount, a set its ring chart.
                    IconButton(onClick = onToggleHome) {
                        Icon(
                            painterResource(if (onHome) R.drawable.ic_home_filled else R.drawable.ic_home),
                            stringResource(if (onHome) R.string.home_remove else R.string.home_add),
                        )
                    }
                    if (current.acceptsChildren) {
                        IconButton(onClick = { creating = ItemKind.Cost }) {
                            Icon(
                                painterResource(R.drawable.ic_note_add),
                                stringResource(R.string.cost_add_cost)
                            )
                        }
                        IconButton(onClick = { creating = ItemKind.Set }) {
                            Icon(
                                painterResource(R.drawable.ic_create_new_folder),
                                stringResource(R.string.cost_add_set),
                            )
                        }
                    }
                    if (current.deletable) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.cost_delete_item)
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // A drag belongs to the level it started on.
        val drag = remember(current.id) { ItemDragState() }
        drag.droppable =
            state.children.filter { it.item.acceptsChildren }.mapTo(mutableSetOf()) { it.item.id }
        drag.order = state.children.map { it.item.id }
        val scroll = rememberScrollState()
        val density = LocalDensity.current

        // While a row is held near the top or bottom edge of the list, the list scrolls under it.
        LaunchedEffect(drag, drag.dragging) {
            if (!drag.dragging) return@LaunchedEffect
            val edge = with(density) { 56.dp.toPx() }
            val step = with(density) { 10.dp.toPx() }
            while (true) {
                withFrameNanos { }
                val bounds = drag.boundsOf(drag.list) ?: continue
                val y = drag.pointer.y
                val delta = when {
                    y in bounds.top..(bounds.top + edge) -> -step
                    y in (bounds.bottom - edge)..bounds.bottom -> step
                    else -> 0f
                }
                if (delta != 0f && scroll.scrollBy(delta) != 0f) drag.retarget()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .onGloballyPositioned { drag.area = it }
        ) {
            Column(Modifier.fillMaxSize()) {
                // The way here; during a drag the same strip is where to drop an item to move it one level up.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (drag.dragging && parent != null) {
                        MoveUpBar(
                            parentTitle = itemTitle(parent),
                            hovered = drag.target == DropTarget.Up,
                            modifier = Modifier.onGloballyPositioned { drag.upBar = it },
                        )
                    } else {
                        Breadcrumb(state.path, onOpen)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { drag.list = it }
                        .verticalScroll(scroll)
                        .padding(bottom = 16.dp),
                ) {
                    NoteCard(
                        note = current.note,
                        onEdit = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            editingNote = true
                        },
                    )
                    if (current.acceptsBills) {
                        RecurringCard(
                            recurring = recurring,
                            bills = bills,
                            taken = taken,
                            onAdd = onAddRecurring,
                            onChange = onChangeRecurring,
                            onStop = onStopRecurring,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    if (current.kind == ItemKind.Cost) {
                        // A fixed height, about two thirds of the screen, with its own scrolling: many bills do not
                        // make the page long.
                        BillListCard(
                            bills = bills,
                            provider = current.provider,
                            onAdd = onAddBill,
                            onUpdate = onUpdateBill,
                            onDelete = onDeleteBill,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    } else {
                        Text(
                            stringResource(R.string.cost_contents, state.children.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 20.dp,
                                bottom = 4.dp
                            ),
                        )
                    }
                    state.children.forEachIndexed { index, child ->
                        val id = child.item.id
                        val dragged = drag.draggedId == id
                        val gap = drag.target as? DropTarget.Before
                        CostItemRow(
                            child = child,
                            insertion = when {
                                gap == null -> Insertion.None
                                gap.beforeId == id -> Insertion.Above
                                gap.beforeId == null && index == state.children.lastIndex -> Insertion.Below
                                else -> Insertion.None
                            },
                            highlight = when {
                                drag.target == DropTarget.Item(id) -> RowHighlight.Hovered
                                drag.dragging && !dragged && id in drag.droppable -> RowHighlight.Droppable
                                else -> RowHighlight.None
                            },
                            modifier = Modifier
                                .alpha(if (dragged) 0.3f else 1f)
                                .clickable(enabled = !drag.dragging) { onOpen(id) }
                                // After the click: the drag sees the finger first, so letting go of a held row
                                // does not open it.
                                .dragSource(
                                    state = drag,
                                    id = id,
                                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDrop = { item, target ->
                                        when (target) {
                                            is DropTarget.Item -> onMove(item, target.id)
                                            DropTarget.Up -> parent?.let { onMove(item, it.id) }
                                            is DropTarget.Before -> onReorder(item, target.beforeId)
                                        }
                                    },
                                ),
                        )
                    }
                    when {
                        current.kind == ItemKind.Cost -> Unit
                        state.children.isEmpty() -> Hint(stringResource(R.string.cost_empty))
                        state.children.size > 1 || parent != null -> Hint(stringResource(R.string.cost_drag_hint))
                    }
                }
            }

            // The item in the user's hand: a small chip at the finger, so the rows and the bar it can be dropped
            // on stay visible around it.
            val lifted = state.children.firstOrNull { it.item.id == drag.draggedId }
            if (lifted != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.offset {
                        (drag.pointer - Offset(
                            LIFTED_GRIP.toPx(),
                            LIFTED_HEIGHT.toPx() / 2
                        )).round()
                    },
                ) {
                    Row(
                        Modifier
                            .height(LIFTED_HEIGHT)
                            .widthIn(max = 240.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(itemIcon(lifted.item)), null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            itemTitle(lifted.item),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The open item's note, on a card of its own above its items. Long-press it to edit it. */
@Composable
private fun NoteCard(note: String, onEdit: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = {}, onLongClick = onEdit),
    ) {
        Text(
            note.ifEmpty { stringResource(R.string.cost_note_placeholder) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (note.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Edits one text of the open item: its name (one line, not blank) or its note (any number of lines). */
@Composable
private fun TextEditDialog(
    heading: String,
    initial: String,
    singleLine: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 5,
                maxLines = if (singleLine) 1 else 12,
                keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { if (text.text.isNotBlank()) onSave(text.text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.text) },
                enabled = !singleLine || text.text.isNotBlank()
            ) {
                Text(stringResource(R.string.cost_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}

/** Changes the open item's name (one line, not blank) and its icon (the button at the start of the name). */
@Composable
private fun ItemEditDialog(
    item: CostItem,
    /** The sibling whose name clashes with the one typed (null: the name as it was), or null. */
    clash: (title: String?) -> CostItem?,
    onDismiss: () -> Unit,
    /** [title] is null when the name was left as it was, so an item with the app's own name keeps following the language. */
    onSave: (title: String?, icon: String?) -> Unit,
) {
    val shownTitle = itemTitle(item)
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(shownTitle, selection = TextRange(shownTitle.length)))
    }
    // An icon the app no longer knows shows, and is saved, as the default.
    var icon by rememberSaveable { mutableStateOf(ItemIcon.of(item.icon)?.key) }
    val newTitle = text.text.takeIf { it.trim() != shownTitle }
    val clashing = clash(newTitle)?.takeIf { newTitle != null }
    val canSave = text.text.isNotBlank() && clashing == null
    val save = { onSave(newTitle, icon) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cost_rename)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChoiceButton(icon, defaultItemIcon(item.kind, item.provider)) { icon = it }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) save() }),
                    isError = clashing != null,
                    supportingText = clashing?.let {
                        { Text(stringResource(R.string.cost_name_taken, itemTitle(it))) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = canSave) {
                Text(stringResource(R.string.cost_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}

/** Icon, name and note of a new cost or set. A set says it starts with "其他杂费". */
@Composable
private fun NewItemDialog(
    kind: ItemKind,
    /** The item of the level whose name clashes with the one typed, or null. */
    clash: (title: String) -> CostItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, note: String, icon: String?) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf<String?>(null) }
    val clashing = title.takeIf { it.isNotBlank() }?.let(clash)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (kind == ItemKind.Set) R.string.cost_add_set else R.string.cost_add_cost))
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChoiceButton(icon, defaultItemIcon(kind, provider = null)) { icon = it }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.cost_item_title)) },
                        isError = clashing != null,
                        supportingText = clashing?.let {
                            { Text(stringResource(R.string.cost_name_taken, itemTitle(it))) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.cost_item_note)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == ItemKind.Set) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.cost_new_set_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, note, icon) },
                enabled = title.isNotBlank() && clashing == null,
            ) {
                Text(stringResource(R.string.cost_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}

private val LIFTED_HEIGHT = 48.dp

private val INSERTION_LINE = 3.dp

/** How far from its left edge the finger holds the lifted chip: on its icon. */
private val LIFTED_GRIP = 28.dp

private enum class RowHighlight { None, Droppable, Hovered }

/** Where a drop would put the dragged item, drawn as a line on the row's edge. */
private enum class Insertion { None, Above, Below }

/** A sub-item's row, the same on every level: a set shows how many items it holds, both show their note's first line. */
@Composable
private fun CostItemRow(
    child: CostChild,
    highlight: RowHighlight,
    insertion: Insertion,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val item = child.item
    val firstLine = item.note.lineSequence().firstOrNull().orEmpty()
    val supporting = listOfNotNull(
        if (item.kind == ItemKind.Set) stringResource(
            R.string.cost_sub_items,
            child.childCount
        ) else null,
        firstLine.ifEmpty { null },
    ).joinToString(" · ").ifEmpty { null }
    ListItem(
        headlineContent = { Text(itemTitle(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let {
            {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = { Icon(painterResource(itemIcon(item)), null) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
        colors = ListItemDefaults.colors(
            containerColor = when (highlight) {
                RowHighlight.None -> MaterialTheme.colorScheme.surface
                RowHighlight.Droppable -> MaterialTheme.colorScheme.surfaceContainerHigh
                RowHighlight.Hovered -> MaterialTheme.colorScheme.primaryContainer
            },
        ),
        modifier = modifier.drawWithContent {
            drawContent()
            if (insertion == Insertion.None) return@drawWithContent
            val thickness = INSERTION_LINE.toPx()
            val y = if (insertion == Insertion.Above) 0f else size.height - thickness
            drawRect(lineColor, topLeft = Offset(0f, y), size = Size(size.width, thickness))
        },
    )
}

/** The path from the root to the open item; any level above can be jumped to. */
@Composable
private fun Breadcrumb(path: List<CostItem>, onOpen: (Long) -> Unit) {
    val scroll = rememberScrollState()
    // Keep the open item, at the end, in view.
    LaunchedEffect(path.lastOrNull()?.id, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(
        Modifier
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        path.forEachIndexed { index, item ->
            val last = index == path.lastIndex
            if (index > 0) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                itemTitle(item),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !last) { onOpen(item.id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/** Where a dragged item is dropped to move it one level up. */
@Composable
private fun MoveUpBar(parentTitle: String, hovered: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (hovered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxSize(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_arrow_upward), null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.cost_move_up, parentTitle),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
