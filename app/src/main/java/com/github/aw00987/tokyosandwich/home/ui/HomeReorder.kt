package com.github.aw00987.tokyosandwich.home.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent

/** How a drag of a home card ended. */
sealed interface HomeDrop {
    /** Let go between other cards: the card takes position [to] (counted without it). */
    data class Move(val key: String, val to: Int, val from: Int) : HomeDrop

    /** Let go on the bar at the top: the card leaves the home tab. */
    data class Remove(val key: String) : HomeDrop
}

/**
 * The drag that reorders the home tab's cards, which are stacked one per row: long-press a card's handle and carry it
 * up or down; the cards it passes make room, and letting go puts it there. Carried onto the bar that covers the top
 * bar meanwhile, it is removed. The order itself changes only when the card is let go, so the gesture is never lost to
 * a recomposition; until then the rows are only drawn displaced.
 */
@Stable
class HomeReorderState {
    /** The row being dragged, or null. */
    var draggedKey by mutableStateOf<String?>(null)
        private set

    /** How far the dragged row is from its place, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** The position the dragged row would take if let go now. */
    private var target by mutableIntStateOf(-1)

    /** Whether letting go now removes the card. */
    var overRemove by mutableStateOf(false)
        private set

    /** Where the finger is, in the root's coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** The rows' keys, top to bottom. */
    var keys: List<String> = emptyList()

    /** The gap between two rows, in pixels. */
    var spacing = 0f

    /** How far the list is scrolled, in pixels. */
    var scroll: () -> Int = { 0 }

    var removeBar: LayoutCoordinates? = null
    internal val rows = mutableMapOf<String, LayoutCoordinates>()
    internal val handles = mutableMapOf<String, LayoutCoordinates>()

    // The rows' places when the drag began; they do not change during it.
    private var tops = emptyList<Float>()
    private var heights = emptyList<Float>()
    private var from = -1
    private var startPointerY = 0f
    private var startScroll = 0

    val dragging: Boolean get() = draggedKey != null

    internal fun start(key: String, positionInHandle: Offset) {
        val handle = handles[key]?.takeIf { it.isAttached } ?: return
        val index = keys.indexOf(key)
        val placed = keys.map { rows[it]?.takeIf { row -> row.isAttached } }
        if (index < 0 || placed.any { it == null }) return
        tops = placed.map { it!!.positionInParent().y }
        heights = placed.map { it!!.size.height.toFloat() }
        from = index
        pointer = handle.localToRoot(positionInHandle)
        startPointerY = pointer.y
        startScroll = scroll()
        offset = 0f
        target = index
        overRemove = false
        draggedKey = key
    }

    internal fun moveTo(key: String, positionInHandle: Offset) {
        if (key != draggedKey) return
        val handle = handles[key]?.takeIf { it.isAttached } ?: return
        pointer = handle.localToRoot(positionInHandle)
        update()
    }

    /** Works out the row's displacement and target again, e.g. after the list scrolled under the finger. */
    fun update() {
        if (!dragging) return
        // The list scrolling moves the row away from the finger; the offset brings it back.
        offset = pointer.y - startPointerY + (scroll() - startScroll)
        val top = tops[from] + offset
        val bottom = top + heights[from]
        // It takes the place of every row whose middle its leading edge has passed.
        val passedAbove = (0 until from).count { top < tops[it] + heights[it] / 2 }
        val passedBelow = (from + 1 until tops.size).count { bottom > tops[it] + heights[it] / 2 }
        target = from - passedAbove + passedBelow
        overRemove = removeBar?.takeIf { it.isAttached }?.boundsInRoot()?.contains(pointer) == true
    }

    /** How far the row [key] is drawn from its place to make room for the dragged one. */
    fun shiftOf(key: String): Float {
        if (!dragging || overRemove) return 0f
        val index = keys.indexOf(key)
        val room = heights[from] + spacing
        return when {
            index < 0 || index == from -> 0f
            index in (from + 1)..target -> -room
            index in target until from -> room
            else -> 0f
        }
    }

    /**
     * How far the dragged row, let go now, is from the place it moves to: what is left to animate once the order has
     * changed.
     */
    private fun leftover(): Float {
        val moved = when {
            target > from -> (from + 1..target).sumOf { (heights[it] + spacing).toDouble() }
            target < from -> -(target until from).sumOf { (heights[it] + spacing).toDouble() }
            else -> 0.0
        }
        return offset - moved.toFloat()
    }

    /** Ends the drag; returns what it did (null: nothing) and how far the row still is from its new place. */
    internal fun end(): Pair<HomeDrop?, Float> {
        val key = draggedKey ?: return null to 0f
        val result = when {
            overRemove -> HomeDrop.Remove(key) to 0f
            else -> HomeDrop.Move(key, target, from) to leftover()
        }
        cancel()
        return result
    }

    internal fun cancel() {
        draggedKey = null
        overRemove = false
        offset = 0f
    }
}

/**
 * Makes this the handle that drags the row [key] after a long press. [onDragStart] is for the haptic tick; [onDrop]
 * gets what the drag did and how far the row is from its new place (to settle it there).
 */
fun Modifier.homeDragHandle(
    state: HomeReorderState,
    key: String,
    onDragStart: () -> Unit,
    onDrop: (drop: HomeDrop, leftover: Float) -> Unit,
): Modifier = composed {
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    DisposableEffect(state, key) {
        onDispose { state.handles.remove(key) }
    }
    onGloballyPositioned { state.handles[key] = it }
        .pointerInput(state, key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    state.start(key, offset)
                    if (state.dragging) currentOnDragStart()
                },
                onDrag = { change, _ ->
                    change.consume()
                    state.moveTo(key, change.position)
                },
                onDragEnd = {
                    val (drop, leftover) = state.end()
                    if (drop != null) currentOnDrop(drop, leftover)
                },
                onDragCancel = { state.cancel() },
            )
        }
}
