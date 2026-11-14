package com.github.aw00987.tokyosandwich.home.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.formatAmount
import com.github.aw00987.tokyosandwich.core.formatYenPrecise
import com.github.aw00987.tokyosandwich.core.ui.ChartPalette
import com.github.aw00987.tokyosandwich.core.ui.ChartSpec
import com.github.aw00987.tokyosandwich.core.ui.DetailRow
import com.github.aw00987.tokyosandwich.core.ui.DrawerToggle
import com.github.aw00987.tokyosandwich.core.ui.MonthlyChartCard
import com.github.aw00987.tokyosandwich.core.ui.OverviewFigures
import com.github.aw00987.tokyosandwich.core.ui.OverviewHeader
import com.github.aw00987.tokyosandwich.core.ui.OverviewWithDrawer
import com.github.aw00987.tokyosandwich.core.ui.ProviderCard
import com.github.aw00987.tokyosandwich.core.ui.yearPages
import com.github.aw00987.tokyosandwich.cost.ui.itemIcon
import com.github.aw00987.tokyosandwich.cost.ui.itemTitle
import kotlinx.coroutines.flow.Flow

/**
 * A cost on the home tab, built from the providers' card parts: its name and what it cost in the month picked at the
 * top ([amount]; null, shown as "—", when it has no bill then), and a drawer with all its [bills] by month.
 * [modifier] goes on the card, not on the drawer.
 */
@Composable
fun CostHomeCard(
    item: CostItem,
    amount: Long?,
    bills: Flow<List<BillEntity>>,
    modifier: Modifier = Modifier,
) {
    val billList by bills.collectAsStateWithLifecycle(emptyList())
    val colors = MaterialTheme.colorScheme
    val palette = ChartPalette(
        colors.surfaceContainerHigh, colors.onSurface, colors.primary, colors.onPrimary,
        colors.tertiary,
    )
    val card: @Composable (Boolean, (() -> Unit)?, Modifier) -> Unit =
        { expanded, onToggle, cardModifier ->
            ProviderCard(
                palette.container,
                cardModifier,
                palette.content,
                itemIcon(item),
                trailing = { DrawerToggle(expanded, onToggle ?: {}, enabled = onToggle != null) },
            ) {
                OverviewHeader(itemTitle(item))
                OverviewFigures(
                    amount?.let { formatAmount(it.toDouble()) } ?: "—",
                    prefix = if (amount != null) "¥" else "",
                    stats = emptyList(),
                )
            }
        }
    if (billList.isEmpty()) {
        card(false, null, modifier)
        return
    }
    OverviewWithDrawer(
        card = { expanded, onToggle, overDrawer ->
            card(
                expanded,
                onToggle,
                overDrawer.then(modifier)
            )
        },
        drawer = { drawerModifier, shape, padding ->
            val pages = remember(billList) { yearPages(billList.associateBy { it.month }) }
            val spec = ChartSpec<BillEntity>(
                cost = { it.amount.toDouble() },
                infoText = stringResource(R.string.home_cost_chart_info),
                detail = { bill ->
                    DetailRow(
                        stringResource(R.string.pie_amount),
                        formatYenPrecise(bill.amount.toDouble()),
                        bold = true,
                    )
                    if (bill.note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            bill.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            MonthlyChartCard(pages, spec, drawerModifier, palette, shape, padding)
        },
    )
}
