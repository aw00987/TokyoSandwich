package com.github.aw00987.tokyosandwich.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.MonthlyRecord
import com.github.aw00987.tokyosandwich.core.data.UsageData
import com.github.aw00987.tokyosandwich.core.formatAmount
import com.github.aw00987.tokyosandwich.core.formatYenPrecise
import java.time.YearMonth

/**
 * Everything that makes one provider's home section differ from another's: its name, icon and colours, its unit, and
 * the texts of its empty states. The layout and behaviour are the same for all ([ProviderHomeSection]).
 */
class ProviderStyle(
    val name: String,
    @DrawableRes val icon: Int,
    val palette: ChartPalette,
    /** `m³` or `kWh`. */
    val unit: String,
    /** A usage with its unit, for the chart and the daily average. */
    val formatUsage: (Double) -> String,
    /** The chart's "关于数据" text: what a month means for this provider and where the figures come from. */
    val chartInfo: String,
    val noDataTitle: String,
    val noDataDetail: String,
)

/**
 * A provider's card on the home tab: its figures of [month] (the month picked at the top of the tab) with the chart
 * drawer, or a card explaining why there is no data. Refreshing is done on the provider's row in the settings;
 * [onRetry] syncs again after a first sync failed. Without an account, or without a bill for [month], the card looks
 * like any other with "—" for every figure. [modifier] goes on the card itself, not on its drawer (the home tab
 * makes the card the handle that drags the row).
 */
@Composable
fun ProviderHomeSection(
    state: ProviderUiState,
    style: ProviderStyle,
    month: YearMonth,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ProviderUiState.Loading ->
            MessageCard(style, style.name, detail = null, modifier, loading = true)

        ProviderUiState.NotConnected ->
            MonthCard(style, record = null, expanded = false, onToggle = null, modifier)

        is ProviderUiState.NoData -> if (state.syncing) {
            MessageCard(style, style.name, detail = null, modifier, loading = true)
        } else {
            MessageCard(style, style.noDataTitle, style.noDataDetail, modifier) {
                Button(onClick = onRetry) { Text(stringResource(R.string.sync_again)) }
            }
        }

        is ProviderUiState.Content -> UsageSection(state.data, style, month, modifier)
    }
}

@Composable
private fun MessageCard(
    style: ProviderStyle,
    title: String,
    detail: String?,
    modifier: Modifier,
    loading: Boolean = false,
    actions: @Composable () -> Unit = {},
) = ProviderMessageCard(
    style.palette.container, title, detail, modifier, loading, style.palette.content, actions,
)

@Composable
private fun UsageSection(
    data: UsageData,
    style: ProviderStyle,
    month: YearMonth,
    modifier: Modifier,
) {
    val record = remember(data, month) { data.records.firstOrNull { it.month == month } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (data.contracts.size > 1) {
            Text(
                listOf(data.contract.title, data.contract.subtitle).filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (data.records.isEmpty()) {
            // Nothing billed yet: no chart either.
            MonthCard(style, record, expanded = false, onToggle = null, modifier)
        } else {
            OverviewWithDrawer(
                card = { expanded, onToggle, overDrawer ->
                    MonthCard(style, record, expanded, onToggle, overDrawer.then(modifier))
                },
                drawer = { modifier, shape, padding ->
                    RecordChartCard(data.records, style, modifier, shape, padding)
                },
            )
        }
    }
}

/**
 * The provider's name and its bill of the month picked on the home tab ([record]: cost, usage and daily average), or
 * "—" for every figure when it has none. Only the triangle at its right opens the chart ([onToggle]; null draws it
 * faded, for a card without a chart): the rest of the card does nothing when tapped.
 */
@Composable
private fun MonthCard(
    style: ProviderStyle,
    record: MonthlyRecord?,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ProviderCard(
        style.palette.container,
        modifier,
        style.palette.content,
        style.icon,
        trailing = { DrawerToggle(expanded, onToggle ?: {}, enabled = onToggle != null) },
    ) {
        OverviewHeader(style.name)
        OverviewFigures(
            record?.let { formatAmount(it.cost.toDouble()) } ?: "—",
            prefix = if (record != null) "¥" else "",
            stats = listOf(
                stringResource(R.string.usage) to (record?.usage?.let(style.formatUsage) ?: "—"),
                stringResource(R.string.daily_average) to
                        (record?.dailyAverage?.let(style.formatUsage) ?: "—"),
            ),
        )
    }
}

/** Monthly chart of the saved records, one page per calendar year. */
@Composable
private fun RecordChartCard(
    records: List<MonthlyRecord>,
    style: ProviderStyle,
    modifier: Modifier,
    shape: Shape,
    contentPadding: PaddingValues,
) {
    val pages = remember(records) { yearPages(records.associateBy { it.month }) }
    val spec = ChartSpec<MonthlyRecord>(
        usageUnit = style.unit,
        usage = { it.usage },
        cost = { it.cost.toDouble() },
        formatUsage = style.formatUsage,
        infoText = style.chartInfo,
        detail = { RecordDetail(it, style) },
    )
    MonthlyChartCard(pages, spec, modifier, style.palette, shape, contentPadding)
}

/** One month in the chart's detail dialog: its figures, then the provider's note line by line. */
@Composable
private fun ColumnScope.RecordDetail(record: MonthlyRecord, style: ProviderStyle) {
    DetailRow(stringResource(R.string.bubble_usage), style.formatUsage(record.usage))
    record.dailyAverage?.let {
        DetailRow(stringResource(R.string.daily_average), style.formatUsage(it))
    }
    DetailRow(
        stringResource(R.string.bubble_total),
        formatYenPrecise(record.cost.toDouble()),
        bold = true
    )
    if (record.usage > 0) {
        DetailRow(
            stringResource(R.string.unit_price),
            stringResource(
                R.string.unit_price_value,
                "%.1f".format(record.cost / record.usage),
                style.unit
            ),
        )
    }
    if (record.note.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        record.note.lines().forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
