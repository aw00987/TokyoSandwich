package com.github.aw00987.tokyosandwich.cost.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthRange
import com.github.aw00987.tokyosandwich.core.data.ledger.TakenMonths
import com.github.aw00987.tokyosandwich.core.data.ledger.expandMonths
import com.github.aw00987.tokyosandwich.core.formatAmount
import java.time.YearMonth

private val RangesSaver = Saver<List<MonthRange>, ArrayList<String>>(
    save = { ranges -> ranges.mapTo(ArrayList()) { "${it.start}/${it.end}" } },
    restore = { saved ->
        saved.map { text ->
            text.split("/").let { MonthRange(YearMonth.parse(it[0]), YearMonth.parse(it[1])) }
        }
    },
)

/**
 * The page that adds bills to the cost item named by [target] (its path): an amount, a note and the months, one bill
 * of that amount for each. One month by default (the last complete one); "添加更多月份" turns it into ranges to include
 * and ranges to leave out. Every month is in the past or this month: a bill that comes every month is not added here
 * but on the cost page ("每月固定"). The line above the save button says what saving will add. A cost has at most one
 * bill per month: when a month is [taken] already, the page says which and cannot save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillScreen(
    target: String,
    taken: TakenMonths,
    onBack: () -> Unit,
    onSave: (BillEntry) -> Unit,
) {
    val thisMonth = remember { YearMonth.now() }
    val lastMonth = thisMonth.minusMonths(1)
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    // One month, or ranges: the one month is the first include range.
    var ranges by rememberSaveable { mutableStateOf(false) }
    var include by rememberSaveable(stateSaver = RangesSaver) {
        mutableStateOf(listOf(MonthRange(lastMonth, lastMonth)))
    }
    var exclude by rememberSaveable(stateSaver = RangesSaver) { mutableStateOf(emptyList()) }
    var excluding by rememberSaveable { mutableStateOf(false) }

    val amount = amountText.toLongOrNull() ?: 0L
    // What saving adds.
    val months: List<YearMonth> =
        if (ranges) expandMonths(include, exclude).filter { it <= thisMonth }
        else listOfNotNull(include.firstOrNull()?.start)
    // Months that have a bill (or belong to a recurring bill) already.
    val conflicts = taken.conflicts(months)
    val entry = BillEntry(amount, note, months)
        .takeIf { amount > 0 && months.isNotEmpty() && conflicts.isEmpty() }

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cost_add_bill)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.back)
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.bill_target, target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AmountField(amountText, { amountText = it }, Modifier.focusRequester(amountFocus))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.bill_note)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!ranges) {
                LabeledRow(stringResource(R.string.bill_month)) {
                    MonthButton(
                        include.firstOrNull()?.start ?: lastMonth,
                        { include = listOf(MonthRange(it, it)) },
                        max = thisMonth,
                    )
                }
                TextButton(onClick = { ranges = true }) {
                    Icon(painterResource(R.drawable.ic_add), null)
                    Text(
                        stringResource(R.string.bill_more_months),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else {
                Text(
                    stringResource(R.string.bill_history_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MonthRangesEditor(
                    title = stringResource(R.string.bill_include),
                    ranges = include,
                    onChange = { include = it },
                    newRange = MonthRange(lastMonth, lastMonth),
                    max = thisMonth,
                )
                if (excluding || exclude.isNotEmpty()) {
                    MonthRangesEditor(
                        title = stringResource(R.string.bill_exclude),
                        ranges = exclude,
                        onChange = { exclude = it },
                        newRange = MonthRange(lastMonth, lastMonth),
                        max = thisMonth,
                    )
                } else {
                    TextButton(onClick = {
                        excluding = true
                        exclude = listOf(MonthRange(lastMonth, lastMonth))
                    }) {
                        Icon(painterResource(R.drawable.ic_add), null)
                        Text(
                            stringResource(R.string.bill_exclude_months),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider()
            Text(
                if (months.isEmpty()) stringResource(R.string.bill_summary_none)
                else stringResource(
                    R.string.bill_summary,
                    months.size,
                    formatAmount(amount.toDouble()),
                    "%,d".format(amount * months.size),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ranges && months.isNotEmpty()) {
                Text(
                    monthsText(months),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (conflicts.isNotEmpty()) {
                Text(
                    stringResource(R.string.bill_conflict, monthsText(conflicts)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { entry?.let(onSave) },
                enabled = entry != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.cost_save)) }
        }
    }
}

/** A whole-yen amount: digits only, no leading zeros. */
@Composable
fun AmountField(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = text,
        onValueChange = { onChange(it.filter(Char::isDigit).trimStart('0').take(10)) },
        label = { Text(stringResource(R.string.bill_amount)) },
        prefix = { Text("¥") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        content()
    }
}

/** A list of month ranges, each a start and an end month; ranges can be added and removed. */
@Composable
private fun MonthRangesEditor(
    title: String,
    ranges: List<MonthRange>,
    onChange: (List<MonthRange>) -> Unit,
    newRange: MonthRange,
    max: YearMonth,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        ranges.forEachIndexed { index, range ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MonthButton(
                    month = range.start,
                    // Picking a start after the end moves the end with it, so the range never comes out empty.
                    onMonthChange = { picked ->
                        onChange(
                            ranges.replaced(
                                index,
                                MonthRange(picked, maxOf(picked, range.end))
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    max = max,
                )
                Text("–")
                MonthButton(
                    month = range.end,
                    onMonthChange = { picked ->
                        onChange(
                            ranges.replaced(
                                index,
                                MonthRange(minOf(picked, range.start), picked)
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    max = max,
                )
                IconButton(onClick = { onChange(ranges.filterIndexed { i, _ -> i != index }) }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        stringResource(R.string.bill_remove_range)
                    )
                }
            }
        }
        TextButton(onClick = { onChange(ranges + newRange) }) {
            Icon(painterResource(R.drawable.ic_add), null)
            Text(stringResource(R.string.bill_add_range), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { i, old -> if (i == index) value else old }
