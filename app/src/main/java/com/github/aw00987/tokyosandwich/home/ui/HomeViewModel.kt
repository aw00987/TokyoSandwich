package com.github.aw00987.tokyosandwich.home.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aw00987.tokyosandwich.core.data.HomeCard
import com.github.aw00987.tokyosandwich.core.data.HomeLayout
import com.github.aw00987.tokyosandwich.core.data.SettingsStore
import com.github.aw00987.tokyosandwich.core.data.homeCardOf
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.LedgerStore
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthRange
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthTotals
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthTotalsCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** What the home tab shows. */
data class HomeUiState(
    /** The month every card shows. */
    val month: YearMonth,
    /** The months that can be picked: from the first one with a bill to the last complete one. */
    val range: MonthRange,
    /** The cards, top to bottom. */
    val cards: List<HomeCard>,
    /** The cost tree, for the cards that show an item. */
    val items: List<CostItem>,
    /** What every item cost in [month]; null while it is being computed. */
    val totals: MonthTotals?,
    /** Counts the refreshes: the cards are built anew, in their default state, whenever it changes. */
    val generation: Int = 0,
    /** A refresh is running: the tab shows a spinner and takes no input. */
    val refreshing: Boolean = false,
)

/** The months the home tab offers: [first] (the earliest bill) through [last], or [last] alone. */
fun homeMonths(first: YearMonth?, last: YearMonth): MonthRange =
    MonthRange(if (first != null && first < last) first else last, last)

/**
 * The home tab: the month picked at its top (the last complete month until the user picks another), its cards (the
 * layout saved in the settings) and the month's totals of the cost tree, from the [totals] cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val ledger: LedgerStore,
    private val settings: SettingsStore,
    private val totals: MonthTotalsCache,
    private val saved: SavedStateHandle,
    currentMonth: () -> YearMonth = YearMonth::now,
) : ViewModel() {
    private val lastMonth = currentMonth().minusMonths(1)

    private val range: Flow<MonthRange> = ledger.firstMonth.map { homeMonths(it, lastMonth) }

    // Kept as text, which a saved state always takes.
    private val picked: Flow<YearMonth?> =
        saved.getStateFlow<String?>(KEY_MONTH, null).map { it?.let(YearMonth::parse) }

    private val month: Flow<YearMonth> = combine(picked, range) { month, range ->
        (month ?: lastMonth).coerceIn(range.start, range.end)
    }.distinctUntilChanged()

    private val generation = MutableStateFlow(0)
    private val refreshing = MutableStateFlow(false)

    // Asked for again after every refresh, which empties the cache.
    private val monthTotals: Flow<MonthTotals?> =
        combine(month, generation) { month, _ -> month }
            .mapLatest<YearMonth, MonthTotals?> { totals.get(it) }
            .onStart { emit(null) }

    private val layout: Flow<HomeLayout> = settings.data.map { it.home }.distinctUntilChanged()

    /** Null until the settings are read. */
    val state: StateFlow<HomeUiState?> =
        combine(
            month,
            range,
            layout,
            ledger.items,
            monthTotals
        ) { month, range, layout, items, totals ->
            // The totals of the month shown before are not this month's.
            HomeUiState(month, range, layout.cards, items, totals?.takeIf { it.month == month })
        }.combine(generation) { state, generation -> state.copy(generation = generation) }
            .combine(refreshing) { state, refreshing -> state.copy(refreshing = refreshing) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Computes everything again from the bills and the tree as they are now: the cached months are forgotten, the
     * usual ones and the month shown are computed anew, and the cards start over in their default state (a ring
     * chart on its own set, every drawer closed). The picked month stays. Meanwhile [HomeUiState.refreshing] is set.
     */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val shown = state.value?.month
                // Long enough for the spinner to be seen as an answer to the tap.
                val pause = launch { delay(MIN_REFRESH_MS) }
                totals.invalidate()
                totals.warmUp()
                shown?.let { totals.get(it) }
                pause.join()
                generation.value++
            } finally {
                refreshing.value = false
            }
        }
    }

    fun selectMonth(month: YearMonth) {
        saved[KEY_MONTH] = month.toString()
    }

    /** The bills of a cost, newest first, for its card's chart. */
    fun bills(itemId: Long): Flow<List<BillEntity>> = ledger.bills(itemId)

    /** Puts the item's card at the bottom of the home tab, or takes it away when it is there. */
    fun toggle(item: CostItem) = change { layout ->
        val card = homeCardOf(item)
        if (card in layout) layout.minus(card) else layout.plus(card)
    }

    fun remove(card: HomeCard) = change { it.minus(card) }

    /** Puts a removed card back at [index] (undo). */
    fun restore(card: HomeCard, index: Int) = change { it.plus(card, index) }

    /** Moves the card [key] to position [to]. */
    fun move(key: String, to: Int) = change { it.moved(key, to) }

    private fun change(transform: (HomeLayout) -> HomeLayout) {
        viewModelScope.launch { settings.update { it.copy(home = transform(it.home)) } }
    }

    private companion object {
        const val KEY_MONTH = "home_month"
        const val MIN_REFRESH_MS = 400L
    }
}
