package com.github.aw00987.tokyosandwich.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.ui.theme.PieOtherDark
import com.github.aw00987.tokyosandwich.ui.theme.PieOtherLight
import com.github.aw00987.tokyosandwich.ui.theme.PieSlicesDark
import com.github.aw00987.tokyosandwich.ui.theme.PieSlicesLight
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** One slice of a [DonutChart]: its [value] sets its share of the ring; [icon] is drawn on it when it is wide enough. */
@Immutable
data class DonutSlice(val value: Double, val color: Color, @DrawableRes val icon: Int? = null)

/** The colours of a ring chart's slices, in a fixed order, and of the slice that stands for everything else. */
@Immutable
data class DonutColors(val slices: List<Color>, val other: Color) {
    /** The colour of slot [index]; slots past the last colour are [other] (a hue is never made up or reused). */
    fun slot(index: Int): Color = slices.getOrElse(index) { other }

    companion object {
        @Composable
        fun current(): DonutColors =
            if (isSystemInDarkTheme()) DonutColors(PieSlicesDark, PieOtherDark)
            else DonutColors(PieSlicesLight, PieOtherLight)
    }
}

/** The ring's outer diameter, with the room a pressed slice grows into. */
private val DONUT_SIZE = 232.dp
private val RING_THICKNESS = 46.dp

/** How much thicker a slice is while it is pressed. */
private val PRESS_GROWTH = 8.dp

/** The gap between two slices, where the card shows through. */
private val SLICE_GAP = 2.dp
private val ICON_SIZE = 20.dp
private const val PRESS_MS = 120

/**
 * A thick ring whose slices are the shares of [slices] (clockwise from the top, none under [MIN_SWEEP]); with nothing
 * above zero it is one ring in [emptyColor]. A slice answers a tap ([onSliceClick]) and a long press
 * ([onSliceLongClick]), and looks pressed while it is held whether or not the tap does anything. The hole holds
 * [centre]; it is a button when [onCentreClick] is set.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    emptyColor: Color,
    onSliceClick: (Int) -> Unit,
    onSliceLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCentreClick: (() -> Unit)? = null,
    onCentreClickLabel: String? = null,
    centre: @Composable BoxScope.() -> Unit = {},
) {
    val sweeps = remember(slices) { donutSweeps(slices.map { it.value }) }
    val painters = slices.map { slice -> slice.icon?.let { painterResource(it) } }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onSliceClick)
    val longClick by rememberUpdatedState(onSliceLongClick)

    // The slice that is, or was last, held, and how far its pressed look has come.
    var pressed by remember(slices) { mutableIntStateOf(-1) }
    val press = remember(slices) { Animatable(0f) }

    Box(modifier.size(DONUT_SIZE), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(sweeps) {
                    val outer = size.width / 2f - PRESS_GROWTH.toPx() / 2
                    val inner = outer - RING_THICKNESS.toPx()
                    fun sliceAt(position: Offset): Int? {
                        val hit = donutHit(
                            position.x - size.width / 2f, position.y - size.height / 2f,
                            outer, inner, sweeps,
                        )
                        return (hit as? DonutHit.Slice)?.index
                    }
                    detectTapGestures(
                        onPress = { position ->
                            val index = sliceAt(position) ?: return@detectTapGestures
                            pressed = index
                            scope.launch { press.animateTo(1f, tween(PRESS_MS)) }
                            tryAwaitRelease()
                            scope.launch { press.animateTo(0f, tween(PRESS_MS)) }
                        },
                        onTap = { position -> sliceAt(position)?.let { click(it) } },
                        onLongPress = { position ->
                            sliceAt(position)?.let {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                longClick(it)
                            }
                        },
                    )
                },
        ) {
            val thickness = RING_THICKNESS.toPx()
            val outer = size.width / 2f - PRESS_GROWTH.toPx() / 2
            val middle = outer - thickness / 2
            val arcTopLeft = Offset(center.x - middle, center.y - middle)
            val arcSize = Size(middle * 2, middle * 2)
            if (sweeps.none { it > 0f }) {
                drawCircle(emptyColor, middle, style = Stroke(thickness))
                return@Canvas
            }
            // The gap as an angle at the middle of the ring; a lone slice is a closed ring.
            val gap = if (sweeps.count { it > 0f } > 1) {
                Math.toDegrees((SLICE_GAP.toPx() / middle).toDouble()).toFloat()
            } else 0f
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = sweeps[index]
                if (sweep <= 0f) return@forEachIndexed
                val held = if (index == pressed) press.value else 0f
                drawArc(
                    color = lerp(slice.color, Color.White, 0.25f * held),
                    startAngle = start + gap / 2,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(thickness + PRESS_GROWTH.toPx() * held),
                )
                val painter = painters[index]
                val icon = ICON_SIZE.toPx()
                // Only where the icon fits on the slice with some air around it.
                val arcLength = Math.toRadians((sweep - gap).toDouble()) * middle
                if (painter != null && arcLength >= icon * 1.5f) {
                    val angle = Math.toRadians((start + sweep / 2).toDouble())
                    val x = center.x + middle * cos(angle).toFloat() - icon / 2
                    val y = center.y + middle * sin(angle).toFloat() - icon / 2
                    val tint = if (slice.color.luminance() > 0.35f) Color.Black else Color.White
                    translate(x, y) {
                        with(painter) {
                            draw(
                                Size(icon, icon),
                                alpha = 0.9f,
                                colorFilter = ColorFilter.tint(tint)
                            )
                        }
                    }
                }
                start += sweep
            }
        }
        Box(
            Modifier
                .size(DONUT_SIZE - (RING_THICKNESS + PRESS_GROWTH) * 2 - 8.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = onCentreClick != null,
                    onClickLabel = onCentreClickLabel,
                    role = Role.Button,
                    onClick = { onCentreClick?.invoke() },
                ),
            contentAlignment = Alignment.Center,
            content = centre,
        )
    }
}
