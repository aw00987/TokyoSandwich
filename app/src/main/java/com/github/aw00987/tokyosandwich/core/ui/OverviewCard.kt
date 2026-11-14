package com.github.aw00987.tokyosandwich.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.aw00987.tokyosandwich.R

/** How far the chart drawer tucks under the overview card, so it looks pulled out from beneath it. */
private val DRAWER_TUCK = 20.dp

/** Width of the stats column on an overview card. */
private val STATS_WIDTH = 112.dp

/**
 * An overview card that pulls a chart out from underneath like a drawer, or pushes it back in.
 * [card] gets whether the drawer is open, the toggle and the modifier it must apply; [drawer] gets the modifier, shape
 * and padding that make the chart sit under the card.
 */
@Composable
fun OverviewWithDrawer(
    card: @Composable (expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) -> Unit,
    drawer: @Composable (modifier: Modifier, shape: Shape, contentPadding: PaddingValues) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        card(expanded, { expanded = !expanded }, Modifier.zIndex(1f))
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            modifier = Modifier.layout { measurable, constraints ->
                // Shift the drawer up under the overview card and give back that space below it.
                val placeable = measurable.measure(constraints)
                val tuck = DRAWER_TUCK.roundToPx()
                layout(
                    placeable.width,
                    (placeable.height - tuck).coerceAtLeast(0)
                ) { placeable.place(0, -tuck) }
            },
        ) {
            val corner = 16.dp
            drawer(
                Modifier.padding(horizontal = 8.dp),
                RoundedCornerShape(bottomStart = corner, bottomEnd = corner),
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp + DRAWER_TUCK,
                    bottom = 12.dp
                ),
            )
        }
    }
}

/**
 * A provider's card on the home tab, in [container] colour. With an [icon], it is drawn large at the card's left,
 * centred against the [content]; [trailing] sits at the card's right edge, also centred (the drawer's toggle).
 */
@Composable
fun ProviderCard(
    container: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = contentColorFor(container),
    @DrawableRes icon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = contentColor),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (icon != null) 14.dp else 20.dp,
                    end = if (trailing != null) 6.dp else 20.dp,
                    top = 14.dp,
                    bottom = 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    painterResource(it),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(36.dp),
                )
            }
            Column(Modifier.weight(1f), content = content)
            trailing?.invoke()
        }
    }
}

/** A provider card that has no data to show: a [title], an explanation and the [actions] that fix it. */
@Composable
fun ProviderMessageCard(
    container: Color,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    contentColor: Color = contentColorFor(container),
    actions: @Composable () -> Unit = {},
) {
    ProviderCard(container, modifier, contentColor) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
        }
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(
            Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { actions() }
    }
}

/**
 * The only thing on an overview card that opens or closes its chart drawer: a thin chevron "⌄" that turns into "⌃" when the
 * drawer is open, in a touch target much larger than the triangle (the project has no icon library). Not [enabled], it
 * is drawn faded and does nothing (a card without data).
 */
@Composable
fun DrawerToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "drawerToggle")
    val color = LocalContentColor.current
    val label = stringResource(if (expanded) R.string.chart_hide else R.string.chart_show)
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation }) {
            val top = size.height * 0.34f
            val bottom = size.height * 0.68f
            val chevron = Path().apply {
                moveTo(size.width * 0.12f, top)
                lineTo(size.width / 2, bottom)
                lineTo(size.width * 0.88f, top)
            }
            drawPath(
                chevron,
                color.copy(alpha = color.alpha * if (enabled) 0.7f else 0.3f),
                style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * The first line of an overview card: [title] on the left and an optional [trailing] note on the right. Always one
 * line, so every provider's card is equally tall.
 */
@Composable
fun OverviewHeader(title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
    }
}

/**
 * The figures of an overview card: the period's cost ([value], after a smaller [prefix] such as "¥") large on the
 * left, and the [stats] as label/value rows stacked on the right (the card's right edge is the drawer toggle's). Each
 * text stays on one line and shrinks to fit, so long numbers never make a card taller.
 */
@Composable
fun OverviewFigures(value: String, prefix: String = "", stats: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f)) {
            if (prefix.isNotEmpty()) {
                Text(
                    prefix,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(end = 2.dp),
                )
            }
            val big = MaterialTheme.typography.headlineLarge
            Text(
                value,
                style = big,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = big.fontSize),
                modifier = Modifier
                    .alignByBaseline()
                    .weight(1f, fill = false),
            )
        }
        // A fixed width, so the stats line up from one provider's card to the next.
        Column(
            Modifier
                .padding(start = 12.dp)
                .width(STATS_WIDTH),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            stats.forEach { (label, statValue) -> Stat(label, statValue) }
        }
    }
}

/** One row of the stats: a muted [label] on the left and its [value] aligned to the right. */
@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .alignByBaseline()
                .padding(end = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        val style = MaterialTheme.typography.bodyLarge
        Text(
            value,
            style = style,
            textAlign = TextAlign.End,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize),
            modifier = Modifier.alignByBaseline(),
        )
    }
}
