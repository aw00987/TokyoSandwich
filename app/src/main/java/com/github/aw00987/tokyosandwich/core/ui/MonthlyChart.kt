package com.github.aw00987.tokyosandwich.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.formatAmount
import com.github.aw00987.tokyosandwich.core.formatDecimal
import com.github.aw00987.tokyosandwich.core.formatYenPrecise
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

enum class ChartMetric { Usage, Cost }

/** One swipeable chart page: a calendar year, with a slot per month (January first) that is null without data. */
@Immutable
data class YearChartPage<T>(val year: Int, val points: List<T?>) {
    fun month(slot: Int): YearMonth = YearMonth.of(year, slot + 1)
}

/** Groups [points] by calendar year into 12-slot pages, newest year first. */
fun <T> yearPages(points: Map<YearMonth, T>): List<YearChartPage<T>> {
    if (points.isEmpty()) return emptyList()
    val years = points.keys.map { it.year }
    return (years.max() downTo years.min()).map { year ->
        YearChartPage(year, (1..12).map { points[YearMonth.of(year, it)] })
    }
}

/**
 * What a provider's data looks like on the chart. [fixedCost], when set, is drawn as a lighter area under the cost line
 * (e.g. water's basic charges). [detail] fills the dialog that opens when a point is tapped. Without [usage] the chart
 * shows the cost only and has no usage/cost switch (a cost item's bills).
 */
class ChartSpec<T>(
    val cost: (T) -> Double,
    val infoText: String,
    val usageUnit: String = "",
    val usage: ((T) -> Double)? = null,
    val formatUsage: (Double) -> String = { "" },
    val fixedCost: ((T) -> Double)? = null,
    val fixedCostLabel: String? = null,
    val detail: @Composable ColumnScope.(T) -> Unit,
)

/** The card's colours: [container] behind everything, [line] for the main series, [accent] for the fixed-cost area. */
@Immutable
data class ChartPalette(
    val container: Color,
    val content: Color,
    val line: Color,
    val onLine: Color,
    val accent: Color,
) {
    companion object {
        @Composable
        fun primary() = MaterialTheme.colorScheme.let {
            ChartPalette(
                it.primaryContainer,
                it.onPrimaryContainer,
                it.primary,
                it.onPrimary,
                it.tertiary
            )
        }
    }
}

private val CANVAS_CHART_HEIGHT = 220.dp
private const val GRID_LINES = 4

/** What the chart shows; swapped as a whole while its data layer is invisible. */
private data class ChartContent<T>(
    val index: Int,
    val page: YearChartPage<T>,
    val metric: ChartMetric,
)

private const val FADE_OUT_MS = 120
private const val FADE_IN_MS = 220
private val SLIDE_DISTANCE = 12.dp
private val SWIPE_THRESHOLD = 48.dp

/**
 * Monthly line chart, one calendar year per page ([pages], newest first), drawn on a Compose Canvas. Swiping right
 * shows older years, swiping left newer.
 * The frame (card, grid lines) stays put; axis labels, series and the title fade through to the new page.
 */
@Composable
fun <T> MonthlyChartCard(
    pages: List<YearChartPage<T>>,
    spec: ChartSpec<T>,
    modifier: Modifier = Modifier,
    palette: ChartPalette = ChartPalette.primary(),
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    var chosenMetric by rememberSaveable { mutableStateOf(ChartMetric.Cost) }
    val metric = if (spec.usage == null) ChartMetric.Cost else chosenMetric

    // Page 0 is the newest; higher indices go back in time.
    var targetIndex by rememberSaveable { mutableIntStateOf(0) }
    val index = targetIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val target = pages.getOrNull(index)?.let { ChartContent(index, it, metric) }

    var shown by remember { mutableStateOf(target) }
    val contentAlpha = remember { Animatable(0f) }
    val slide = remember { Animatable(0f) }
    val slidePx = with(LocalDensity.current) { SLIDE_DISTANCE.toPx() }
    val fadeIn = tween<Float>(FADE_IN_MS, easing = LinearOutSlowInEasing)
    LaunchedEffect(target) {
        val old = shown
        if (old != target) {
            contentAlpha.animateTo(0f, tween(FADE_OUT_MS, easing = FastOutLinearInEasing))
            shown = target
            // Older pages sit to the left, so they slide in from the left; newer ones from the right.
            val samePageSet = old != null && target != null && old.metric == target.metric
            slide.snapTo(if (samePageSet) (old.index - target.index).sign * slidePx else 0f)
        }
        coroutineScope {
            launch { slide.animateTo(0f, fadeIn) }
            contentAlpha.animateTo(1f, fadeIn)
        }
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = chartCardColor(palette),
            contentColor = palette.content,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if ((shown?.metric ?: metric) == ChartMetric.Cost) {
                        stringResource(R.string.chart_unit_yen)
                    } else {
                        spec.usageUnit
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                Text(
                    shown?.let { "${it.page.year}年" }.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer { alpha = contentAlpha.value },
                )
                if (spec.usage != null) {
                    CompactToggle(
                        ChartMetric.entries,
                        metric,
                        { stringResource(if (it == ChartMetric.Usage) R.string.chart_usage else R.string.chart_cost) },
                        { chosenMetric = it },
                        palette,
                    )
                }
            }
            val content = shown
            if (content == null) {
                Text(
                    stringResource(R.string.chart_no_data),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                CanvasChart(
                    content,
                    spec,
                    palette,
                    contentAlpha = { contentAlpha.value },
                    slideX = { slide.value },
                    onSwipe = { delta ->
                        targetIndex = (index + delta).coerceIn(0, pages.lastIndex)
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChartInfoButton(spec.infoText)
                    Spacer(Modifier.weight(1f))
                    if (content.metric == ChartMetric.Cost && spec.fixedCost != null) {
                        CostLegend(palette, spec.fixedCostLabel.orEmpty())
                    }
                }
            }
        }
    }
}

/** "关于数据" text button under the chart's bottom-left corner that explains where the chart's values come from. */
@Composable
private fun ChartInfoButton(infoText: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { open = true },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp),
    ) {
        Text(
            stringResource(R.string.chart_info_title),
            style = MaterialTheme.typography.labelMedium
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                }) { Text(stringResource(R.string.chart_info_ok)) }
            },
            title = { Text(stringResource(R.string.chart_info_title)) },
            text = { Text(infoText) },
        )
    }
}

@Composable
private fun CostLegend(palette: ChartPalette, fixedLabel: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(palette.line, stringResource(R.string.bubble_total), palette)
        LegendItem(palette.accent.copy(alpha = 0.55f), fixedLabel, palette)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, palette: ChartPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(10.dp), shape = CircleShape, color = color) {}
        Spacer(Modifier.padding(start = 6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    }
}

/**
 * The chart card's background: the overview card's container colour, softened a little towards the surface so the
 * lines and labels stay readable on it.
 */
@Composable
private fun chartCardColor(palette: ChartPalette): Color =
    lerp(palette.container, MaterialTheme.colorScheme.surfaceContainerLow, 0.35f)

/** Secondary text (units, axis labels, legend) on the chart card. */
private val ChartPalette.muted: Color get() = content.copy(alpha = 0.72f)

/** Chart geometry shared by drawing and hit testing. */
private class ChartGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val slotCount: Int,
    val maxY: Double,
) {
    fun x(slot: Int): Float =
        if (slotCount <= 1) (left + right) / 2 else left + (right - left) * slot / (slotCount - 1)

    fun y(value: Double): Float = bottom - ((value / maxY).toFloat() * (bottom - top))
}

/**
 * Three stacked layers: static grid lines, fading axis labels, and the fading (slightly sliding) series.
 * [contentAlpha] and [slideX] are read only while drawing, so the animation redraws without recomposing.
 */
@Composable
private fun <T> CanvasChart(
    content: ChartContent<T>,
    spec: ChartSpec<T>,
    palette: ChartPalette,
    contentAlpha: () -> Float,
    slideX: () -> Float,
    onSwipe: (Int) -> Unit,
) {
    val (_, page, metric) = content
    // Slot under the finger during a long press, and slot whose detail dialog is open.
    var preview by remember(page, metric) { mutableStateOf<Int?>(null) }
    var detail by remember(page, metric) { mutableStateOf<Int?>(null) }
    val selected = preview ?: detail
    val haptics = LocalHapticFeedback.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val values: List<Double?> = remember(page, metric) {
        page.points.map { point ->
            point?.let { if (metric == ChartMetric.Cost) spec.cost(it) else spec.usage?.invoke(it) }
        }
    }
    val fixedValues: List<Double?> = remember(page, metric) {
        val fixed = spec.fixedCost
        if (metric == ChartMetric.Cost && fixed != null) page.points.map { it?.let(fixed) } else emptyList()
    }
    val maxY = remember(values) { niceCeil((values.filterNotNull().maxOrNull() ?: 0.0) * 1.15) }

    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val cardColor = chartCardColor(palette)
    val gridColor = palette.content.copy(alpha = 0.18f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = palette.muted)
    val density = LocalDensity.current
    val xLabelHeight = remember(labelStyle) { textMeasurer.measure("0", labelStyle).size.height }
    // The plot's left edge follows the widest y label; ease it so grid lines do not jump between pages.
    val targetLeft = remember(maxY, metric, labelStyle) {
        val yLabelWidth = (0..GRID_LINES).maxOf {
            textMeasurer.measure(axisValue(maxY * it / GRID_LINES, metric), labelStyle).size.width
        }
        yLabelWidth + with(density) { 8.dp.toPx() }
    }
    val left = remember { Animatable(targetLeft) }
    LaunchedEffect(targetLeft) {
        left.animateTo(
            targetLeft,
            tween(FADE_IN_MS, easing = LinearOutSlowInEasing)
        )
    }

    val geometry: () -> ChartGeometry? = {
        if (canvasSize == IntSize.Zero) {
            null
        } else with(density) {
            ChartGeometry(
                left = left.value,
                top = 8.dp.toPx(),
                right = canvasSize.width - 6.dp.toPx(),
                bottom = canvasSize.height - xLabelHeight - 8.dp.toPx(),
                slotCount = page.points.size,
                maxY = maxY,
            )
        }
    }
    val currentOnSwipe by rememberUpdatedState(onSwipe)

    Box(
        Modifier
            .fillMaxWidth()
            .height(CANVAS_CHART_HEIGHT)
            .onSizeChanged { canvasSize = it }
            .pointerInput(page, metric, maxY) {
                fun nearestSlot(x: Float, g: ChartGeometry): Int? =
                    values.indices.filter { values[it] != null }.minByOrNull { abs(g.x(it) - x) }

                // A tap must land in a box around the point: the slot's column (at least 48dp wide) by 64dp tall.
                fun hitSlot(p: Offset, g: ChartGeometry): Int? {
                    val i = nearestSlot(p.x, g) ?: return null
                    val spacing =
                        if (g.slotCount <= 1) g.right - g.left else (g.right - g.left) / (g.slotCount - 1)
                    val halfWidth = maxOf(spacing / 2f, TAP_TARGET_HALF.toPx())
                    val halfHeight = TAP_TARGET_HALF_HEIGHT.toPx()
                    val ys = listOfNotNull(values[i], fixedValues.getOrNull(i)).map { g.y(it) }
                    val hit =
                        abs(g.x(i) - p.x) <= halfWidth && ys.any { abs(it - p.y) <= halfHeight }
                    return if (hit) i else null
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        awaitPressOutcome(down, viewConfiguration.touchSlop)
                    }
                    val g = geometry() ?: return@awaitEachGesture
                    when (outcome) {
                        null -> { // long press: follow the finger until it lifts
                            preview = nearestSlot(down.position.x, g)
                            if (preview != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            trackPointer(down.id) { position ->
                                val next = nearestSlot(position.x, g)
                                if (next != preview && next != null) {
                                    preview = next
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            preview = null
                        }

                        PressOutcome.Tap -> hitSlot(down.position, g)?.let { detail = it }
                        PressOutcome.HorizontalDrag -> {
                            var total = 0f
                            trackPointer(down.id) { total = it.x - down.position.x }
                            val threshold = SWIPE_THRESHOLD.toPx()
                            // Dragging right reveals older data (index + 1), left reveals newer.
                            when {
                                total > threshold -> currentOnSwipe(1)
                                total < -threshold -> currentOnSwipe(-1)
                            }
                        }

                        PressOutcome.Cancelled -> Unit
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGridLines(geometry() ?: return@Canvas, gridColor)
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha() }) {
            val g = geometry() ?: return@Canvas
            drawYLabels(g, textMeasurer, labelStyle, metric)
            drawXLabels(g, page, textMeasurer, labelStyle)
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationX = slideX()
                },
        ) {
            val g = geometry() ?: return@Canvas
            if (fixedValues.isNotEmpty()) {
                drawSeries(
                    g,
                    fixedValues,
                    palette.accent.copy(alpha = 0.55f),
                    1.5.dp.toPx(),
                    areaAlpha = 0.22f,
                    areaColor = palette.accent
                )
            }
            drawSeries(
                g,
                values,
                palette.line,
                2.5.dp.toPx(),
                areaAlpha = if (fixedValues.isEmpty()) 0.22f else 0f,
                areaColor = palette.line
            )
            values.forEachIndexed { i, v ->
                if (v != null) {
                    drawCircle(cardColor, 4.dp.toPx(), Offset(g.x(i), g.y(v)))
                    drawCircle(palette.line, 2.5.dp.toPx(), Offset(g.x(i), g.y(v)))
                }
            }

            selected?.let { i ->
                val v = values.getOrNull(i) ?: return@let
                val x = g.x(i)
                drawLine(
                    palette.content.copy(alpha = 0.45f),
                    Offset(x, g.top),
                    Offset(x, g.bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
                fixedValues.getOrNull(i)?.let { f ->
                    drawCircle(palette.accent, 4.dp.toPx(), Offset(x, g.y(f)))
                }
                drawCircle(palette.line.copy(alpha = 0.2f), 10.dp.toPx(), Offset(x, g.y(v)))
                drawCircle(colors.surface, 6.dp.toPx(), Offset(x, g.y(v)))
                drawCircle(palette.line, 4.5.dp.toPx(), Offset(x, g.y(v)))
            }
        }
        val i = preview
        val g = if (i != null) geometry() else null
        val v = i?.let { values.getOrNull(it) }
        if (g != null && i != null && v != null) {
            PreviewBubble(
                monthLabel(page.month(i)),
                if (metric == ChartMetric.Cost) formatYenPrecise(v) else spec.formatUsage(v),
                Modifier.belowPoint(g.x(i), g.y(v), 12.dp),
            )
        }
    }

    detail?.let { slot -> page.points.getOrNull(slot)?.let { slot to it } }?.let { (slot, point) ->
        PointDetailDialog(monthLabel(page.month(slot)), onDismiss = { detail = null }) {
            spec.detail(this, point)
        }
    }
}

private val TAP_TARGET_HALF = 24.dp
private val TAP_TARGET_HALF_HEIGHT = 32.dp

private enum class PressOutcome { Tap, HorizontalDrag, Cancelled }

/**
 * Waits for the press started by [down] to end in a tap or to move past [touchSlop]. Vertical moves are left
 * unconsumed so an enclosing vertical scroll can take them; horizontal ones are claimed for swiping pages.
 */
private suspend fun AwaitPointerEventScope.awaitPressOutcome(
    down: PointerInputChange,
    touchSlop: Float
): PressOutcome {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            ?: return PressOutcome.Cancelled
        if (change.isConsumed) return PressOutcome.Cancelled
        if (change.changedToUp()) return PressOutcome.Tap
        val delta = change.position - down.position
        if (delta.getDistance() > touchSlop) {
            if (abs(delta.x) < abs(delta.y)) return PressOutcome.Cancelled
            change.consume()
            return PressOutcome.HorizontalDrag
        }
    }
}

/** Consumes every move of pointer [id], reporting its position, until it lifts. */
private suspend fun AwaitPointerEventScope.trackPointer(id: PointerId, onMove: (Offset) -> Unit) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        change.consume()
        if (change.changedToUp() || !change.pressed) return
        onMove(change.position)
    }
}

private fun axisValue(value: Double, metric: ChartMetric): String =
    if (metric == ChartMetric.Cost) "¥" + formatAmount(value) else formatDecimal(value)

private fun DrawScope.drawGridLines(g: ChartGeometry, lineColor: Color) {
    for (i in 0..GRID_LINES) {
        val y = g.y(g.maxY * i / GRID_LINES)
        drawLine(
            lineColor.copy(alpha = if (i == 0) 1f else 0.5f),
            Offset(g.left, y),
            Offset(g.right, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun DrawScope.drawYLabels(
    g: ChartGeometry,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    metric: ChartMetric,
) {
    for (i in 0..GRID_LINES) {
        val value = g.maxY * i / GRID_LINES
        val y = g.y(value)
        val layout = textMeasurer.measure(axisValue(value, metric), style)
        drawText(
            layout,
            topLeft = Offset(g.left - 8.dp.toPx() - layout.size.width, y - layout.size.height / 2f)
        )
    }
}

private fun DrawScope.drawXLabels(
    g: ChartGeometry,
    page: YearChartPage<*>,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    page.points.indices.forEach { i ->
        if (i % 2 != 0) return@forEach // every other month keeps the labels from crowding
        val layout = textMeasurer.measure("${page.month(i).monthValue}月", style)
        val left = (g.x(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(left, g.bottom + 6.dp.toPx()))
    }
}

/** Draws each run of consecutive non-null values as its own line (gaps stay open), with an optional gradient area. */
private fun DrawScope.drawSeries(
    g: ChartGeometry,
    values: List<Double?>,
    color: Color,
    width: Float,
    areaAlpha: Float,
    areaColor: Color,
) {
    val runs = mutableListOf<List<Int>>()
    var run = mutableListOf<Int>()
    values.forEachIndexed { i, v ->
        if (v != null) run += i else if (run.isNotEmpty()) {
            runs += run; run = mutableListOf()
        }
    }
    if (run.isNotEmpty()) runs += run

    for (indices in runs) {
        val line = Path()
        indices.forEachIndexed { n, i ->
            val p = Offset(g.x(i), g.y(values[i]!!))
            if (n == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
        }
        if (areaAlpha > 0f) {
            val area = Path().apply {
                addPath(line)
                lineTo(g.x(indices.last()), g.bottom)
                lineTo(g.x(indices.first()), g.bottom)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(areaColor.copy(alpha = areaAlpha), areaColor.copy(alpha = 0.02f)),
                    startY = g.top,
                    endY = g.bottom,
                ),
            )
        }
        if (indices.size == 1) {
            drawCircle(color, width * 1.4f, Offset(g.x(indices[0]), g.y(values[indices[0]]!!)))
        } else {
            drawPath(
                line,
                color,
                style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/** Small pill-shaped single-choice toggle that fits in the chart header. */
@Composable
private fun <T> CompactToggle(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    palette: ChartPalette,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.surface.copy(alpha = 0.55f))
            .selectableGroup()
            .padding(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) palette.line else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(option) })
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) palette.onLine else palette.content,
                )
            }
        }
    }
}

/**
 * Centres the child horizontally on the point (x, y) in px, [gap] below it, keeping it inside the chart.
 * If there is no room below, it goes above the point instead.
 */
private fun Modifier.belowPoint(x: Float, y: Float, gap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val gapPx = gap.roundToPx()
    layout(width, height) {
        val left = (x - placeable.width / 2f).roundToInt()
            .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
        val below = y.roundToInt() + gapPx
        val top =
            if (below + placeable.height <= height) below else (y.roundToInt() - gapPx - placeable.height).coerceAtLeast(
                0
            )
        placeable.place(left, top)
    }
}

private fun monthLabel(month: YearMonth) = "${month.year}年${month.monthValue}月"

/** Small two-line bubble shown while long-pressing: the month and the value of the current metric. */
@Composable
private fun PreviewBubble(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Details of one month: the month top-left, a close button top-right, the provider's [content] below. */
@Composable
private fun PointDetailDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        CloseGlyph(stringResource(R.string.detail_close))
                    }
                }
                Column(
                    Modifier.padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

/** An "×" drawn in the current content colour (the project has no icon library). */
@Composable
private fun CloseGlyph(contentDescription: String) {
    val color = LocalContentColor.current
    Canvas(
        Modifier
            .size(14.dp)
            .semantics { this.contentDescription = contentDescription }) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

/** A label/value row of a detail dialog. */
@Composable
fun DetailRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.padding(start = 12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.SemiBold else null
        )
    }
}

/** An indented note under a [DetailRow]. */
@Composable
fun DetailNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 8.dp)
    )
}

/** The muted first line of a detail dialog (how the values were obtained). */
@Composable
fun DetailCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
}

/** Rounds [value] up to 1, 2, 2.5 or 5 × 10ⁿ so the axis ends on a round number. */
internal fun niceCeil(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= value - 1e-9 }
    return step * magnitude
}
