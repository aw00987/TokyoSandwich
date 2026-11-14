package com.github.aw00987.tokyosandwich.home.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthRange
import com.github.aw00987.tokyosandwich.cost.ui.MonthPickerDialog
import com.github.aw00987.tokyosandwich.cost.ui.monthText
import java.time.YearMonth

/**
 * The month the home tab shows, in its top bar: a step to the month before and after, and the month itself, which
 * opens the month picker. Only the months of [range] can be reached.
 */
@Composable
fun MonthSelector(
    month: YearMonth,
    range: MonthRange,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(month.minusMonths(1)) }, enabled = month > range.start) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                stringResource(R.string.home_previous_month),
                modifier = Modifier.rotate(180f),
            )
        }
        TextButton(onClick = { picking = true }) {
            Text(monthText(month), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        IconButton(onClick = { onSelect(month.plusMonths(1)) }, enabled = month < range.end) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                stringResource(R.string.home_next_month),
            )
        }
    }
    if (picking) {
        MonthPickerDialog(
            selected = month,
            max = range.end,
            min = range.start,
            onPick = {
                onSelect(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
