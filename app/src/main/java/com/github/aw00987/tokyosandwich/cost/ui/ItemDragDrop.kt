package com.github.aw00987.tokyosandwich.cost.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Where a dragged item can be dropped. */
sealed interface DropTarget {
    /** Onto the row of another item: move into it. */
    data class Item(val id: Long) : DropTarget

    /** Onto the bar above the list: move to the level above. */
    data object Up : DropTarget

    /** Between two rows: put the item just before the row [beforeId] (null: after the last row), in this level. */
    data class Before(val beforeId: Long?) : DropTarget
}

/**
 * The drag of one row of the cost list: long-press a row, carry it onto a set's row (its middle), between two rows
 * (the upper or lower part of a row) or onto the "move up" bar, and let go. Positions are in the coordinates of the drag area (the box that holds the bar, the list and the lifted item),
 * and the rows' bounds are read from the layout at the moment they are needed, so scrolling during a drag is fine.
 */
@Stable
class ItemDragState {
    /** The row being dragged, or null. */
    var draggedId by mutableStateOf<Long?>(null)
        private set

    /** Where the finger is. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** What a drop would do right now, or null (letting go cancels). */
    var target by mutableStateOf<DropTarget?>(null)
        private set

    /** The rows that take a drop into them: the sets. */
    var droppable: Set<Long> = emptySet()

    /** The rows' ids, top to bottom. */
    var order: List<Long> = emptyList()

    var area: LayoutCoordinates? = null
    var list: LayoutCoordinates? = null
    var upBar: LayoutCoordinates? = null
    internal val rows = mutableMapOf<Long, LayoutCoordinates>()

    val dragging: Boolean get() = draggedId != null

    /** The bounds of [coordinates] in the drag area, or null when either is not laid out. */
    fun boundsOf(coordinates: LayoutCoordinates?): Rect? {
        val area = area?.takeIf { it.isAttached } ?: return null
        val attached = coordinates?.takeIf { it.isAttached } ?: return null
        return area.localBoundingBoxOf(attached, clipBounds = false)
    }

    internal fun start(id: Long, positionInRow: Offset) {
        val row = rows[id]?.takeIf { it.isAttached } ?: return
        val area = area?.takeIf { it.isAttached } ?: return
        draggedId = id
        pointer = area.localPositionOf(row, positionInRow)
        target = null
    }

    internal fun moveTo(id: Long, positionInRow: Offset) {
        val row = rows[id]?.takeIf { it.isAttached } ?: return
        val area = area?.takeIf { it.isAttached } ?: return
        pointer = area.localPositionOf(row, positionInRow)
        retarget()
    }

    /** Finds the target under the finger again, e.g. after the list scrolled under it. */
    fun retarget() {
        val dragged = draggedId ?: return
        target = when {
            boundsOf(upBar)?.contains(pointer) == true -> DropTarget.Up
            // Rows scrolled out of the list's window are not under the finger.
            boundsOf(list)?.contains(pointer) != true -> null
            else -> rowTarget(dragged)
        }
    }

    /**
     * The target in the list: a set's row in its middle half, otherwise the gap above or below the row under the
     * finger. A gap next to the dragged row itself would change nothing, and is no target.
     */
    private fun rowTarget(dragged: Long): DropTarget? {
        val index = order.indexOfFirst { id -> boundsOf(rows[id])?.contains(pointer) == true }
        if (index < 0) return null
        val id = order[index]
        val bounds = boundsOf(rows[id]) ?: return null
        val y = (pointer.y - bounds.top) / bounds.height
        if (id != dragged && id in droppable && y in 0.25f..0.75f) return DropTarget.Item(id)
        if (id == dragged) return null
        val beforeIndex = if (y < 0.5f) index else index + 1
        val draggedIndex = order.indexOf(dragged)
        if (beforeIndex == draggedIndex || beforeIndex == draggedIndex + 1) return null
        return DropTarget.Before(order.getOrNull(beforeIndex))
    }

    internal fun end() {
        draggedId = null
        target = null
    }
}

/**
 * Makes the row of item [id] draggable after a long press. [onDragStart] is for the haptic tick; [onDrop] is called
 * with the item and the target it was let go on (nothing is called when it is let go anywhere else).
 */
fun Modifier.dragSource(
    state: ItemDragState,
    id: Long,
    onDragStart: () -> Unit,
    onDrop: (id: Long, target: DropTarget) -> Unit,
): Modifier = composed {
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    DisposableEffect(state, id) {
        onDispose { state.rows.remove(id) }
    }
    onGloballyPositioned { state.rows[id] = it }
        .pointerInput(state, id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    state.start(id, offset)
                    if (state.dragging) currentOnDragStart()
                },
                onDrag = { change, _ ->
                    change.consume()
                    state.moveTo(id, change.position)
                },
                onDragEnd = {
                    val target = state.target
                    state.end()
                    if (target != null) currentOnDrop(id, target)
                },
                onDragCancel = { state.end() },
            )
        }
}
