package com.github.aw00987.tokyosandwich.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.HomeCard
import com.github.aw00987.tokyosandwich.core.data.HomeCardKind
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthRange
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.YearMonth

private val CARD_SPACING = 12.dp

/**
 * The home tab: the month picker in the top bar, and under it the cards the user put there, one per row, all showing
 * that month. A provider's card is drawn by [providerCard]; a cost's and a set's are drawn here.
 *
 * Long-pressing a card's handle (the card itself; the header of a ring chart) lifts its row: carried up or down it
 * changes place ([onMove]), carried onto the bar that covers the top bar meanwhile it is removed ([onRemove], which a
 * snackbar can undo with [onRestore]). The rows are in a plain scrolling `Column`, not a `LazyColumn`, so the dragged
 * row's gesture survives scrolling; keep it that way.
 *
 * The refresh button in the top bar ([onRefresh]) computes every card again from the saved bills and the tree; while
 * it runs the tab is covered by a spinner, and afterwards the cards are in their default state.
 */
@Composable
fun HomeScreen(
    state: HomeUiState?,
    onSelectMonth: (YearMonth) -> Unit,
    onRefresh: () -> Unit,
    onMove: (key: String, to: Int) -> Unit,
    onRemove: (HomeCard) -> Unit,
    onRestore: (HomeCard, index: Int) -> Unit,
    bills: (itemId: Long) -> Flow<List<BillEntity>>,
    providerCard: @Composable (ProviderKind, YearMonth, Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        HomeBoard(state, onSelectMonth, onRefresh, onMove, onRemove, onRestore, bills, providerCard)
        // While the cards are computed again, the tab is covered and takes no input.
        if (state?.refreshing == true) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun HomeBoard(
    state: HomeUiState?,
    onSelectMonth: (YearMonth) -> Unit,
    onRefresh: () -> Unit,
    onMove: (key: String, to: Int) -> Unit,
    onRemove: (HomeCard) -> Unit,
    onRestore: (HomeCard, index: Int) -> Unit,
    bills: (itemId: Long) -> Flow<List<BillEntity>>,
    providerCard: @Composable (ProviderKind, YearMonth, Modifier) -> Unit,
) {
    val reorder = remember { HomeReorderState() }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            Box {
                HomeTopBar(state?.month, state?.range, onSelectMonth, onRefresh)
                if (reorder.dragging) {
                    RemoveBar(
                        hovered = reorder.overRemove,
                        modifier = Modifier
                            .matchParentSize()
                            .onGloballyPositioned { reorder.removeBar = it },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val shown = state ?: return@Scaffold
        val scope = rememberCoroutineScope()
        val resources = LocalResources.current
        val haptics = LocalHapticFeedback.current
        val density = LocalDensity.current
        val scroll = rememberScrollState()
        var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }

        // The order on screen: the saved one, or what a drop just made of it until the settings have caught up.
        var cards by remember(shown.cards) { mutableStateOf(shown.cards) }
        val itemsById = remember(shown.items) { shown.items.associateBy { it.id } }
        // A card whose item is not in the tree (yet) has nothing to show.
        val rows = cards.filter { it.kind == HomeCardKind.Provider || it.itemId in itemsById }
        reorder.keys = rows.map { it.key }
        reorder.spacing = with(density) { CARD_SPACING.toPx() }
        reorder.scroll = { scroll.value }

        // The row that was just let go, and how far it still is from its new place.
        var settling by remember { mutableStateOf<String?>(null) }
        val settle = remember { Animatable(0f) }

        // While a row is held near the top or bottom edge, the list scrolls under it.
        LaunchedEffect(reorder.dragging) {
            if (!reorder.dragging) return@LaunchedEffect
            val edge = with(density) { 64.dp.toPx() }
            val step = with(density) { 10.dp.toPx() }
            while (true) {
                withFrameNanos { }
                val bounds = viewport?.takeIf { it.isAttached }?.boundsInRoot() ?: continue
                val y = reorder.pointer.y
                val delta = when {
                    y in bounds.top..(bounds.top + edge) -> -step
                    y in (bounds.bottom - edge)..bounds.bottom -> step
                    else -> 0f
                }
                if (delta != 0f && scroll.scrollBy(delta) != 0f) reorder.update()
            }
        }

        val onDrop: (HomeDrop, Float) -> Unit = { drop, leftover ->
            when (drop) {
                is HomeDrop.Move -> {
                    val card = rows.firstOrNull { it.key == drop.key }
                    if (card != null) {
                        if (drop.to != drop.from) {
                            // The position among the rows on screen, as a position among all the saved cards.
                            val others = rows.filter { it.key != drop.key }
                            val before = others.getOrNull(drop.to)
                            val rest = cards.filter { it.key != drop.key }
                            val to = before?.let { rest.indexOf(it) } ?: rest.size
                            cards = rest.toMutableList().apply { add(to, card) }
                            onMove(drop.key, to)
                        }
                        settling = drop.key
                        scope.launch {
                            settle.snapTo(leftover)
                            settle.animateTo(0f)
                            settling = null
                        }
                    }
                }

                is HomeDrop.Remove -> cards.firstOrNull { it.key == drop.key }.let { card ->
                    if (card == null) return@let
                    val index = cards.indexOf(card)
                    cards = cards - card
                    onRemove(card)
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = resources.getString(R.string.home_removed),
                            actionLabel = resources.getString(R.string.cost_undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) onRestore(card, index)
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .onGloballyPositioned { viewport = it },
        ) {
            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
            ) {
                // A refresh builds every card anew: ring charts back on their own set, drawers closed.
                rows.forEach { card ->
                    key(shown.generation, card.key) {
                        val dragged = reorder.draggedKey == card.key
                        // The other rows slide out of the dragged one's way; once it is let go they are in place.
                        val shift by animateFloatAsState(
                            if (reorder.dragging) reorder.shiftOf(card.key) else 0f,
                            label = "homeRowShift",
                        )
                        val handle = Modifier.homeDragHandle(
                            reorder,
                            card.key,
                            onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrop = onDrop,
                        )
                        // The outer box keeps the row's place in the list; the inner one is what moves.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .zIndex(if (dragged || settling == card.key) 1f else 0f)
                                .onGloballyPositioned { reorder.rows[card.key] = it },
                        ) {
                            Box(
                                Modifier.graphicsLayer {
                                    translationY = when {
                                        dragged -> reorder.offset
                                        settling == card.key -> settle.value
                                        reorder.dragging -> shift
                                        else -> 0f
                                    }
                                    if (dragged) {
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                        alpha = if (reorder.overRemove) 0.6f else 0.95f
                                    }
                                },
                            ) {
                                when (card.kind) {
                                    HomeCardKind.Provider ->
                                        card.provider?.let { providerCard(it, shown.month, handle) }

                                    HomeCardKind.Cost -> itemsById[card.itemId]?.let { item ->
                                        CostHomeCard(
                                            item = item,
                                            amount = shown.totals?.get(item.id),
                                            bills = remember(item.id) { bills(item.id) },
                                            modifier = handle,
                                        )
                                    }

                                    HomeCardKind.Pie -> itemsById[card.itemId]?.let { item ->
                                        SetPieCard(
                                            root = item,
                                            items = shown.items,
                                            totals = shown.totals,
                                            headerModifier = handle,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The logo, then the button that computes the cards again from the saved bills and the month every card shows.
 * Syncing a provider is on its row in the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    month: YearMonth?,
    range: MonthRange?,
    onSelectMonth: (YearMonth) -> Unit,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            Image(
                painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 16.dp, end = 8.dp)
                    .size(28.dp),
            )
        },
        // The month picker needs the room the app's name would take; the logo stands for it.
        title = {},
        actions = {
            if (month != null && range != null) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        stringResource(R.string.home_refresh)
                    )
                }
                MonthSelector(month, range, onSelectMonth)
            }
        },
    )
}

/** Covers the top bar while a card is dragged: letting the card go on it takes the card off the home tab. */
@Composable
private fun RemoveBar(hovered: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = if (hovered) colors.errorContainer else colors.surfaceContainerHighest,
        contentColor = if (hovered) colors.onErrorContainer else colors.onSurfaceVariant,
    ) {
        Box(Modifier.statusBarsPadding(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(20.dp))
                Text(
                    stringResource(R.string.home_remove_bar),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
