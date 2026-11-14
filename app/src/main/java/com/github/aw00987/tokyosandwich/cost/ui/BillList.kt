package com.github.aw00987.tokyosandwich.cost.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.BillOrigin
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import com.github.aw00987.tokyosandwich.core.data.ledger.editable
import com.github.aw00987.tokyosandwich.core.data.ledger.monthEditable
import com.github.aw00987.tokyosandwich.core.formatDecimal
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * The open cost's bills, on a card of its own below its note: a header (how many, their total, "添加账单" when the user
 * adds this cost's bills), then the bills, newest month first. The card is about two thirds of the screen high
 * whatever the number of bills, and the list scrolls inside it, with a scroll bar that can be dragged.
 *
 * Tapping a bill edits it (a utility's bills are only shown: their sync replaces them). The edit dialog also deletes
 * it; a recurring bill is stopped from that month on instead, since its months come back otherwise.
 */
@Composable
fun BillListCard(
    bills: List<BillEntity>,
    /** Null on an item whose bills the user adds; a utility's bills come from its sync. */
    provider: ProviderKind?,
    onAdd: () -> Unit,
    onUpdate: (bill: BillEntity, month: YearMonth, amount: Long, note: String) -> Unit,
    onDelete: (BillEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The id of the bill being edited or shown, while its dialog is open.
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = bills.firstOrNull { it.id == openId }
    val deleting = bills.firstOrNull { it.id == deletingId }

    when {
        open == null -> Unit
        open.editable -> BillEditDialog(
            bill = open,
            onDismiss = { openId = null },
            onSave = { month, amount, note ->
                onUpdate(open, month, amount, note)
                openId = null
            },
            onDelete = {
                openId = null
                deletingId = open.id
            },
        )

        else -> BillDetailDialog(open, provider, onDismiss = { openId = null })
    }
    deleting?.let { bill ->
        BillDeleteDialog(
            bill = bill,
            // A stopped recurring bill loses this month and every later one.
            count = bill.recurringId?.let { id -> bills.count { it.recurringId == id && it.month >= bill.month } }
                ?: 1,
            onConfirm = {
                deletingId = null
                onDelete(bill)
            },
            onDismiss = { deletingId = null },
        )
    }

    val density = LocalDensity.current
    val height = with(density) { (LocalWindowInfo.current.containerSize.height * 2 / 3).toDp() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(height),
    ) {
        Column {
            BillListHeader(bills, canAdd = provider == null, onAdd = onAdd)
            HorizontalDivider()
            if (bills.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp), contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(if (provider == null) R.string.cost_bills_hint else R.string.cost_provider_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(bills, key = { it.id }) { bill ->
                            BillRow(bill, modifier = Modifier.clickable { openId = bill.id })
                        }
                    }
                    VerticalScrollbar(
                        listState,
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun BillListHeader(bills: List<BillEntity>, canAdd: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.bill_list_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        if (bills.isNotEmpty()) {
            Text(
                stringResource(
                    R.string.bill_list_count,
                    bills.size,
                    "%,d".format(bills.sumOf { it.amount })
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (canAdd) {
            TextButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cost_add_bill))
            }
        }
    }
}

/** A bill's row: its month and amount; how it was added, unless typed in; the first line of its note. */
@Composable
private fun BillRow(bill: BillEntity, modifier: Modifier = Modifier) {
    val origin = when (bill.origin) {
        BillOrigin.Manual -> null
        BillOrigin.Recurring -> stringResource(R.string.bill_origin_recurring)
        BillOrigin.Provider -> stringResource(R.string.bill_origin_provider)
    }
    val supporting = listOfNotNull(origin, bill.note.lineSequence().firstOrNull()?.ifBlank { null })
        .joinToString(" · ")
        .ifEmpty { null }
    ListItem(
        headlineContent = { Text(monthText(bill.month)) },
        supportingContent = supporting?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Text(
                "¥" + "%,d".format(bill.amount),
                style = MaterialTheme.typography.titleMedium,
                // Clear of the scroll bar.
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    )
}

/**
 * A scroll bar for [state], at the end of the list: a thumb sized by how much of the list is in view. Dragging or
 * tapping along it jumps there, which is the quick way through many bills. The rows are about the same height, so the
 * list's length is estimated from the rows in view.
 */
@Composable
private fun VerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val scrollable by remember { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    if (!scrollable) return
    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    var dragging by remember { mutableStateOf(false) }

    fun scrollTo(y: Float, trackHeight: Float) {
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty() || trackHeight <= 0f) return
        val rowHeight = visible.sumOf { it.size }.toFloat() / visible.size
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val maxScroll = (rowHeight * info.totalItemsCount - viewport).coerceAtLeast(0f)
        val target = (y / trackHeight).coerceIn(0f, 1f) * maxScroll
        val index = (target / rowHeight).toInt().coerceIn(0, info.totalItemsCount - 1)
        scope.launch { state.scrollToItem(index, (target - index * rowHeight).toInt()) }
    }

    Box(
        modifier
            .width(20.dp)
            .pointerInput(state) {
                detectTapGestures { scrollTo(it.y, size.height.toFloat()) }
            }
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragging = true
                        scrollTo(it.y, size.height.toFloat())
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        scrollTo(change.position.y, size.height.toFloat())
                    },
                )
            }
            .drawWithContent {
                val info = state.layoutInfo
                val visible = info.visibleItemsInfo
                if (visible.isEmpty()) return@drawWithContent
                val rowHeight = visible.sumOf { it.size }.toFloat() / visible.size
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                val content = (rowHeight * info.totalItemsCount).coerceAtLeast(viewport)
                val scrolled =
                    state.firstVisibleItemIndex * rowHeight + state.firstVisibleItemScrollOffset
                val track = size.height - 8.dp.toPx()
                val thumb =
                    (track * viewport / content).coerceIn(32.dp.toPx().coerceAtMost(track), track)
                val maxScroll = (content - viewport).coerceAtLeast(1f)
                val top = 4.dp.toPx() + (scrolled / maxScroll).coerceIn(0f, 1f) * (track - thumb)
                val width = (if (dragging) 8.dp else 4.dp).toPx()
                drawRoundRect(
                    color = color.copy(alpha = if (dragging) 0.8f else 0.5f),
                    topLeft = Offset(size.width - width - 4.dp.toPx(), top),
                    size = Size(width, thumb),
                    cornerRadius = CornerRadius(width / 2),
                )
            },
    )
}

/**
 * Edits a bill: amount and note, and the month of a bill typed in by hand. A recurring bill's month stays: the change
 * is to that month's bill only. "删除" asks to delete it (a recurring one: to stop it from that month).
 */
@Composable
private fun BillEditDialog(
    bill: BillEntity,
    onDismiss: () -> Unit,
    onSave: (month: YearMonth, amount: Long, note: String) -> Unit,
    onDelete: () -> Unit,
) {
    val thisMonth = remember { YearMonth.now() }
    var month by rememberSaveable { mutableStateOf(bill.month) }
    var amountText by rememberSaveable { mutableStateOf(bill.amount.toString()) }
    var note by rememberSaveable { mutableStateOf(bill.note) }
    val amount = amountText.toLongOrNull() ?: 0L
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_edit)) },
        text = {
            Column {
                if (bill.monthEditable) {
                    MonthButton(
                        month,
                        { month = it },
                        modifier = Modifier.fillMaxWidth(),
                        max = thisMonth
                    )
                } else {
                    Text(monthText(bill.month), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.bill_edit_recurring_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text ->
                        amountText = text.filter(Char::isDigit).trimStart('0').take(10)
                    },
                    label = { Text(stringResource(R.string.bill_amount)) },
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.bill_note)) },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(month, amount, note) }, enabled = amount > 0) {
                Text(stringResource(R.string.cost_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(if (bill.recurringId != null) R.string.bill_stop else R.string.cost_delete_item),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) }
            }
        },
    )
}

/** A utility's bill, as its sync wrote it: shown, not edited. */
@Composable
private fun BillDetailDialog(bill: BillEntity, provider: ProviderKind?, onDismiss: () -> Unit) {
    val unit = when (provider) {
        ProviderKind.Electricity -> "kWh"
        ProviderKind.Water, ProviderKind.Gas -> "m³"
        null -> ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(monthText(bill.month)) },
        text = {
            Column {
                Text(
                    "¥" + "%,d".format(bill.amount),
                    style = MaterialTheme.typography.headlineSmall
                )
                bill.usage?.let {
                    Text(
                        stringResource(R.string.bill_usage, formatDecimal(it), unit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (bill.note.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(bill.note, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.bill_provider_readonly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_ok)) } },
    )
}

/** Confirms deleting a manual bill, or stopping a recurring one from its month ([count] bills go). */
@Composable
private fun BillDeleteDialog(
    bill: BillEntity,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val recurring = bill.recurringId != null
    val month = monthText(bill.month)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (recurring) R.string.bill_stop_title else R.string.bill_delete_title)) },
        text = {
            Text(
                if (recurring) stringResource(R.string.bill_stop_text, month, count)
                else stringResource(R.string.bill_delete_text, month, "%,d".format(bill.amount))
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(if (recurring) R.string.bill_stop else R.string.cost_delete_item),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}
