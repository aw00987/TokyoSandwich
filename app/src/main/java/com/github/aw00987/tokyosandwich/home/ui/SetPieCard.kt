package com.github.aw00987.tokyosandwich.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemShare
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthTotals
import com.github.aw00987.tokyosandwich.core.data.ledger.breakdown
import com.github.aw00987.tokyosandwich.core.data.ledger.childrenOf
import com.github.aw00987.tokyosandwich.core.data.ledger.pathTo
import com.github.aw00987.tokyosandwich.core.formatAmount
import com.github.aw00987.tokyosandwich.core.ui.DetailRow
import com.github.aw00987.tokyosandwich.core.ui.DonutChart
import com.github.aw00987.tokyosandwich.core.ui.DonutColors
import com.github.aw00987.tokyosandwich.core.ui.DonutSlice
import com.github.aw00987.tokyosandwich.cost.ui.itemIcon
import com.github.aw00987.tokyosandwich.cost.ui.itemTitle

/** What a long press on the ring or its legend shows. */
private sealed interface PieDetail {
    data class Item(val share: ItemShare) : PieDetail
    data class Other(val members: List<ItemShare>) : PieDetail
}

/** [amount] as a share of [total]: "12.3%", "<0.1%" for a share too small for one decimal. */
internal fun shareText(amount: Long, total: Long): String {
    if (total <= 0L) return "—"
    val percent = amount * 100.0 / total
    return if (amount > 0 && percent < 0.1) "<0.1%" else "%.1f%%".format(percent)
}

private fun yen(amount: Long): String = "¥" + formatAmount(amount.toDouble())

/**
 * A set on the home tab: what it cost in the month picked at the top, as a ring of its sub-items ([totals]; null
 * while they are computed). Tapping a sub-set, on the ring or in the legend under it, shows that set's ring in the
 * same card, and the middle of the ring leads back up, never above [root]. Tapping a cost does nothing. A long press
 * shows a slice's figures. Past the eight largest, the sub-items share one "other" slice, which opens in the legend
 * only. The header ([headerModifier]) is the handle that drags the card.
 */
@Composable
fun SetPieCard(
    root: CostItem,
    items: List<CostItem>,
    totals: MonthTotals?,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
) {
    // The set whose ring is shown; back on the card's own set when it is gone or was moved out from under it.
    var currentId by rememberSaveable(root.id) { mutableLongStateOf(root.id) }
    val path = remember(items, currentId, root.id) {
        items.pathTo(currentId).dropWhile { it.id != root.id }
            .takeIf { way -> way.isNotEmpty() && way.last().kind == ItemKind.Set }
            ?: listOf(root)
    }
    val current = path.last()
    val children = remember(items, current.id) { items.childrenOf(current.id) }
    val split = remember(children, totals) { breakdown(children, totals?.totals.orEmpty()) }
    val colors = DonutColors.current()
    // A colour belongs to the item, not to its rank: the slices take the colours in the items' own order.
    val slots = remember(split, children) {
        val shown = split.top.mapTo(HashSet()) { it.item.id }
        children.filter { it.id in shown }.mapIndexed { index, item -> item.id to index }.toMap()
    }

    fun colorOf(share: ItemShare): Color = colors.slot(slots.getValue(share.item.id))

    var otherOpen by rememberSaveable(current.id) { mutableStateOf(false) }
    var detail by remember { mutableStateOf<PieDetail?>(null) }
    val open = { item: CostItem -> if (item.kind == ItemKind.Set) currentId = item.id }

    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            headerModifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(itemIcon(root)), null, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    itemTitle(root),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (path.size > 1) {
                    Text(
                        path.drop(1).map { itemTitle(it) }.joinToString(" › "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
        }

        val slices = split.top.map {
            DonutSlice(it.amount.toDouble(), colorOf(it), itemIcon(it.item))
        } + listOfNotNull(
            DonutSlice(
                split.otherAmount.toDouble(),
                colors.other
            ).takeIf { split.other.isNotEmpty() },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = slices,
                emptyColor = MaterialTheme.colorScheme.outlineVariant,
                onSliceClick = { index -> split.top.getOrNull(index)?.let { open(it.item) } },
                onSliceLongClick = { index ->
                    detail = split.top.getOrNull(index)?.let { PieDetail.Item(it) }
                        ?: PieDetail.Other(split.other)
                },
                onCentreClick = if (path.size > 1) {
                    { currentId = path[path.size - 2].id }
                } else null,
                onCentreClickLabel = stringResource(R.string.pie_back),
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (path.size > 1) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_upward),
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        itemTitle(current),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val big = MaterialTheme.typography.titleLarge
                    Text(
                        if (split.total > 0) yen(split.total) else "—",
                        style = big,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 12.sp,
                            maxFontSize = big.fontSize
                        ),
                    )
                }
            }
        }

        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp)) {
            split.top.forEach { share ->
                LegendRow(
                    dot = colorOf(share),
                    item = share.item,
                    amount = yen(share.amount),
                    share = shareText(share.amount, split.total),
                    onClick = { open(share.item) },
                    onLongClick = { detail = PieDetail.Item(share) },
                )
            }
            if (split.other.isNotEmpty()) {
                OtherRow(
                    dot = colors.other,
                    count = split.other.size,
                    amount = yen(split.otherAmount),
                    share = shareText(split.otherAmount, split.total),
                    open = otherOpen,
                    onClick = { otherOpen = !otherOpen },
                    onLongClick = { detail = PieDetail.Other(split.other) },
                )
                if (otherOpen) {
                    split.other.forEach { share ->
                        LegendRow(
                            dot = null,
                            item = share.item,
                            amount = yen(share.amount),
                            share = shareText(share.amount, split.total),
                            onClick = { open(share.item) },
                            onLongClick = { detail = PieDetail.Item(share) },
                            indent = true,
                        )
                    }
                }
            }
            // What cost nothing this month: no slice, but still there to open.
            split.empty.forEach { item ->
                LegendRow(
                    dot = null,
                    item = item,
                    amount = "—",
                    share = "",
                    onClick = { open(item) },
                    onLongClick = { detail = PieDetail.Item(ItemShare(item, 0)) },
                    muted = true,
                )
            }
        }
    }

    detail?.let { shown ->
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = {
                TextButton(onClick = {
                    detail = null
                }) { Text(stringResource(R.string.chart_info_ok)) }
            },
            title = {
                Text(
                    when (shown) {
                        is PieDetail.Item -> itemTitle(shown.share.item)
                        is PieDetail.Other -> stringResource(R.string.pie_other, shown.members.size)
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val amount = when (shown) {
                        is PieDetail.Item -> shown.share.amount
                        is PieDetail.Other -> shown.members.sumOf { it.amount }
                    }
                    DetailRow(stringResource(R.string.pie_amount), yen(amount), bold = true)
                    DetailRow(
                        stringResource(R.string.pie_share, itemTitle(current)),
                        shareText(amount, split.total),
                    )
                    when (shown) {
                        is PieDetail.Item -> if (shown.share.item.kind == ItemKind.Set) {
                            DetailRow(
                                stringResource(R.string.pie_sub_items),
                                items.childrenOf(shown.share.item.id).size.toString(),
                            )
                        }

                        is PieDetail.Other -> {
                            Spacer(Modifier.size(4.dp))
                            shown.members.forEach { DetailRow(itemTitle(it.item), yen(it.amount)) }
                        }
                    }
                }
            },
        )
    }
}

/** One sub-item under the ring: its colour ([dot]; none for an item without a slice of its own), icon, name and figures. */
@Composable
private fun LegendRow(
    dot: Color?,
    item: CostItem,
    amount: String,
    share: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    indent: Boolean = false,
    muted: Boolean = false,
) {
    LegendLine(
        dot,
        amount,
        share,
        onClick,
        onLongClick,
        indent,
        muted,
        drills = item.kind == ItemKind.Set
    ) {
        Icon(painterResource(itemIcon(item)), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            itemTitle(item),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** The row of the sub-items that share the "other" slice; tapping it lists them underneath. */
@Composable
private fun OtherRow(
    dot: Color,
    count: Int,
    amount: String,
    share: String,
    open: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    LegendLine(dot, amount, share, onClick, onLongClick, drills = false) {
        Text(
            stringResource(R.string.pie_other, count),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            stringResource(if (open) R.string.pie_other_hide else R.string.pie_other_show),
            Modifier
                .padding(start = 2.dp)
                .size(18.dp)
                .rotate(if (open) -90f else 90f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendLine(
    dot: Color?,
    amount: String,
    share: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    indent: Boolean = false,
    muted: Boolean = false,
    drills: Boolean,
    name: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(start = if (indent) 22.dp else 8.dp, end = 4.dp, top = 9.dp, bottom = 9.dp)
            .alpha(if (muted) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot ?: Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = name)
        if (share.isNotEmpty()) {
            Text(
                share,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(start = 10.dp),
        )
        // A sub-set opens; the mark keeps its width on every row so the figures line up.
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            null,
            Modifier
                .size(18.dp)
                .alpha(if (drills) 1f else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
