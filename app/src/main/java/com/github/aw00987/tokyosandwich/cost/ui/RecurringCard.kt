package com.github.aw00987.tokyosandwich.cost.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthSpan
import com.github.aw00987.tokyosandwich.core.data.ledger.RecurringBill
import com.github.aw00987.tokyosandwich.core.data.ledger.TakenMonths
import com.github.aw00987.tokyosandwich.core.data.ledger.recurringMonths
import com.github.aw00987.tokyosandwich.core.data.ledger.span
import java.time.YearMonth

private val MonthSaver = Saver<YearMonth?, String>(
    save = { it?.toString() ?: "" },
    restore = { it.ifEmpty { null }?.let(YearMonth::parse) },
)

/** What the open recurring bill's dialog is doing. */
private enum class RecurringAction { Menu, Change, Stop }

/**
 * The open cost's recurring bills (每月固定), on a card between its note and its bills: rules that add a bill of the
 * same amount every month, each shown with its amount, its months and whether it has started or ended. "添加" adds one;
 * tapping one changes its amount from a month on, or stops it from a month on. The bills a rule has added are in the
 * bill list like any other, marked "每月固定".
 */
@Composable
fun RecurringCard(
    recurring: List<RecurringBill>,
    /** The cost's bills, to count what stopping a rule deletes. */
    bills: List<BillEntity>,
    taken: TakenMonths,
    onAdd: (amount: Long, note: String, start: YearMonth, end: YearMonth?) -> Unit,
    onChange: (RecurringBill, from: YearMonth, amount: Long) -> Unit,
    onStop: (RecurringBill, from: YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thisMonth = remember { YearMonth.now() }
    var adding by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var action by rememberSaveable { mutableStateOf(RecurringAction.Menu) }
    val open = recurring.firstOrNull { it.id == openId }
    val close = { openId = null; action = RecurringAction.Menu }

    if (adding) {
        AddRecurringDialog(
            taken = taken,
            thisMonth = thisMonth,
            onDismiss = { adding = false },
            onSave = { amount, note, start, end ->
                onAdd(amount, note, start, end)
                adding = false
            },
        )
    }
    if (open != null) {
        when (action) {
            RecurringAction.Menu -> RecurringDialog(
                recurring = open,
                billed = bills.count { it.recurringId == open.id },
                thisMonth = thisMonth,
                onChange = { action = RecurringAction.Change },
                onStop = { action = RecurringAction.Stop },
                onDismiss = close,
            )

            RecurringAction.Change -> ChangeAmountDialog(
                recurring = open,
                thisMonth = thisMonth,
                onDismiss = close,
                onSave = { from, amount ->
                    onChange(open, from, amount)
                    close()
                },
            )

            RecurringAction.Stop -> StopRecurringDialog(
                recurring = open,
                bills = bills,
                thisMonth = thisMonth,
                onDismiss = close,
                onStop = { from ->
                    onStop(open, from)
                    close()
                },
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp)
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.recurring_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { adding = true }) {
                    Icon(painterResource(R.drawable.ic_add), null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.recurring_add))
                }
            }
            if (recurring.isEmpty()) {
                Text(
                    stringResource(R.string.recurring_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            } else {
                HorizontalDivider()
                recurring.forEach { rule ->
                    RecurringRow(rule, thisMonth, Modifier.clickable { openId = rule.id })
                }
            }
        }
    }
}

/** "¥8,000 / 月", its months, its state when it has not started or has ended, and its note's first line. */
@Composable
private fun RecurringRow(
    recurring: RecurringBill,
    thisMonth: YearMonth,
    modifier: Modifier = Modifier
) {
    val supporting = listOfNotNull(
        spanText(recurring),
        stateText(recurring, thisMonth),
        recurring.note.lineSequence().firstOrNull()?.ifBlank { null },
    ).joinToString(" · ")
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    R.string.recurring_amount,
                    "%,d".format(recurring.amount)
                )
            )
        },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    )
}

/** "2026年1月起，不结束" or "2026年1月–2026年12月". */
@Composable
private fun spanText(recurring: RecurringBill): String {
    val end = recurring.endMonth
    return if (end == null) stringResource(
        R.string.recurring_since,
        monthText(recurring.startMonth)
    )
    else stringResource(R.string.bill_range, monthText(recurring.startMonth), monthText(end))
}

/** "未开始" before its first month, "已结束" after its last; null while it runs. */
@Composable
private fun stateText(recurring: RecurringBill, thisMonth: YearMonth): String? {
    val end = recurring.endMonth
    return when {
        recurring.startMonth > thisMonth -> stringResource(R.string.recurring_upcoming)
        end != null && end < thisMonth -> stringResource(R.string.recurring_ended)
        else -> null
    }
}

/** A recurring bill: what it is, and the two things that can be done with it. */
@Composable
private fun RecurringDialog(
    recurring: RecurringBill,
    billed: Int,
    thisMonth: YearMonth,
    onChange: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_amount, "%,d".format(recurring.amount))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(
                        spanText(recurring),
                        stateText(recurring, thisMonth)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.recurring_billed, billed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recurring.note.isNotBlank()) {
                    Text(recurring.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChange) { Text(stringResource(R.string.recurring_change)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onStop) {
                    Text(
                        stringResource(R.string.bill_stop),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) }
            }
        },
    )
}

/** Adds a recurring bill: amount, note, first month and an optional last month. */
@Composable
private fun AddRecurringDialog(
    taken: TakenMonths,
    thisMonth: YearMonth,
    onDismiss: () -> Unit,
    onSave: (amount: Long, note: String, start: YearMonth, end: YearMonth?) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable(stateSaver = MonthSaver) { mutableStateOf(thisMonth) }
    var end by rememberSaveable(stateSaver = MonthSaver) { mutableStateOf<YearMonth?>(null) }
    val amount = amountText.toLongOrNull() ?: 0L
    val first = start ?: thisMonth
    val last = end
    val valid = last == null || last >= first
    val reached = if (valid) recurringMonths(first, last, thisMonth).size else 0
    val conflicts = if (valid) taken.conflicts(MonthSpan(first, last)) else emptyList()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_new)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AmountField(amountText, { amountText = it }, Modifier.focusRequester(focus))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.bill_note)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledRow(stringResource(R.string.bill_start)) {
                    MonthButton(first, { start = it })
                }
                LabeledRow(stringResource(R.string.bill_end)) {
                    if (last == null) {
                        TextButton(onClick = { end = maxOf(first, thisMonth) }) {
                            Text(stringResource(R.string.bill_no_end))
                        }
                    } else {
                        MonthButton(last, { end = it })
                        IconButton(onClick = { end = null }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                stringResource(R.string.bill_clear_end)
                            )
                        }
                    }
                }
                val each = "%,d".format(amount)
                Text(
                    when {
                        !valid -> stringResource(R.string.bill_summary_recurring_invalid)
                        last == null -> stringResource(
                            R.string.bill_summary_recurring,
                            monthText(first),
                            each,
                            reached
                        )

                        else -> stringResource(
                            R.string.bill_summary_recurring_end,
                            monthText(first),
                            monthText(last),
                            each,
                            reached,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (conflicts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.bill_conflict, monthsText(conflicts)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amount, note, first, last) },
                enabled = amount > 0 && valid && conflicts.isEmpty(),
            ) { Text(stringResource(R.string.cost_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}

/**
 * Changes a recurring bill's amount from a month of its span on (by default this month, or the nearest month it has).
 * Its bills from that month are written anew, a change made to one of them alone included.
 */
@Composable
private fun ChangeAmountDialog(
    recurring: RecurringBill,
    thisMonth: YearMonth,
    onDismiss: () -> Unit,
    onSave: (from: YearMonth, amount: Long) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(recurring.amount.toString()) }
    var from by rememberSaveable(stateSaver = MonthSaver) {
        mutableStateOf(
            recurring.span.nearest(
                thisMonth
            )
        )
    }
    val amount = amountText.toLongOrNull() ?: 0L
    val month = from ?: recurring.startMonth
    val inSpan = month in recurring.span
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_change)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledRow(stringResource(R.string.recurring_from)) {
                    MonthButton(
                        month,
                        { from = it })
                }
                AmountField(amountText, { amountText = it })
                Text(
                    if (inSpan) stringResource(R.string.recurring_change_hint, monthText(month))
                    else stringResource(R.string.recurring_out_of_span),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (inSpan) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(month, amount) },
                enabled = inSpan && amount > 0 && amount != recurring.amount,
            ) { Text(stringResource(R.string.cost_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}

/**
 * Stops a recurring bill from a month of its span on (by default next month, or the nearest month it has): its bills
 * from then are deleted and no more are added. Stopped from its first month, nothing is left of it.
 */
@Composable
private fun StopRecurringDialog(
    recurring: RecurringBill,
    bills: List<BillEntity>,
    thisMonth: YearMonth,
    onDismiss: () -> Unit,
    onStop: (from: YearMonth) -> Unit,
) {
    var from by rememberSaveable(stateSaver = MonthSaver) {
        mutableStateOf(recurring.span.nearest(thisMonth.plusMonths(1)))
    }
    val month = from ?: recurring.startMonth
    val inSpan = month in recurring.span
    val count = bills.count { it.recurringId == recurring.id && it.month >= month }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_stop_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledRow(stringResource(R.string.recurring_from)) {
                    MonthButton(
                        month,
                        { from = it })
                }
                Text(
                    if (inSpan) stringResource(R.string.bill_stop_text, monthText(month), count)
                    else stringResource(R.string.recurring_out_of_span),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inSpan) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStop(month) }, enabled = inSpan) {
                Text(stringResource(R.string.bill_stop), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}
