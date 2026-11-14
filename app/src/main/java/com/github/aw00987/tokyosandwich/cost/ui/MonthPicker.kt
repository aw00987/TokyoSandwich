package com.github.aw00987.tokyosandwich.cost.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.compressMonths
import java.time.YearMonth

@Composable
fun monthText(month: YearMonth): String =
    stringResource(R.string.year_month, month.year, month.monthValue)

/** [months] as ranges: "2025年4月–2026年3月、2026年5月". */
fun monthsText(resources: Resources, months: List<YearMonth>): String =
    compressMonths(months).joinToString(resources.getString(R.string.list_separator)) { range ->
        val start =
            resources.getString(R.string.year_month, range.start.year, range.start.monthValue)
        if (range.start == range.end) start
        else resources.getString(
            R.string.bill_range,
            start,
            resources.getString(R.string.year_month, range.end.year, range.end.monthValue),
        )
    }

/** A month as text: "2026年4月". */
fun monthText(resources: Resources, month: YearMonth): String =
    resources.getString(R.string.year_month, month.year, month.monthValue)

@Composable
fun monthsText(months: List<YearMonth>): String = monthsText(LocalResources.current, months)

/** A month as a button; tapping it opens the [MonthPickerDialog]. Months after [max] can't be picked. */
@Composable
fun MonthButton(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    max: YearMonth? = null,
) {
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }, modifier = modifier) {
        Text(
            monthText(month),
            maxLines = 1
        )
    }
    if (picking) {
        MonthPickerDialog(
            selected = month,
            max = max,
            onPick = {
                onMonthChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Picks a year and month: the year with a step either way, then one of the twelve months. Picking a month closes it.
 * Months before [min] or after [max] can't be picked.
 */
@Composable
fun MonthPickerDialog(
    selected: YearMonth,
    max: YearMonth?,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    min: YearMonth? = null,
) {
    var year by remember { mutableIntStateOf(selected.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }, enabled = min == null || year > min.year) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        stringResource(R.string.month_picker_previous_year),
                        modifier = Modifier.rotate(180f),
                    )
                }
                Text(
                    stringResource(R.string.month_picker_year, year),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { year++ }, enabled = max == null || year < max.year) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        stringResource(R.string.month_picker_next_year),
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..12).chunked(4).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { month ->
                            val value = YearMonth.of(year, month)
                            val label = stringResource(R.string.month_picker_month, month)
                            val cell = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp)
                            if (value == selected) {
                                FilledTonalButton(
                                    onClick = { onPick(value) },
                                    modifier = cell,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        0.dp
                                    ),
                                ) { Text(label, maxLines = 1) }
                            } else {
                                TextButton(
                                    onClick = { onPick(value) },
                                    enabled = (max == null || value <= max) && (min == null || value >= min),
                                    modifier = cell,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        0.dp
                                    ),
                                ) { Text(label, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        },
    )
}
