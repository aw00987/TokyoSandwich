package com.github.aw00987.tokyosandwich.core.data.ledger

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

/** How many complete months are computed when the app starts. */
const val WARM_MONTHS = 3

/** The [count] complete months before [current], newest first. */
fun lastCompleteMonths(current: YearMonth, count: Int = WARM_MONTHS): List<YearMonth> =
    (1..count).map { current.minusMonths(it.toLong()) }

/**
 * The month totals of the cost tree ([MonthTotals]), kept in memory only: a cache, not the user's data. The last
 * [WARM_MONTHS] complete months are computed when the app starts ([warmUp]); any other month when it is first asked
 * for ([get]), and then kept too.
 *
 * **Nothing empties it by itself yet.** A bill added, an item moved or a sync does not change a month already
 * computed, so the home tab shows the old figures until the user taps its refresh button ([invalidate], then the
 * months are computed again) or the app's process restarts.
 */
class MonthTotalsCache(
    private val ledger: LedgerStore,
    private val currentMonth: () -> YearMonth = YearMonth::now,
) {
    private val lock = Mutex()
    private val months = HashMap<YearMonth, MonthTotals>()

    suspend fun warmUp() {
        lastCompleteMonths(currentMonth()).forEach { get(it) }
    }

    /** The totals of [month]: the kept ones, or computed now from the bills and the tree and kept. */
    suspend fun get(month: YearMonth): MonthTotals = lock.withLock {
        months.getOrPut(month) { ledger.monthTotals(month) }
    }

    /** Forgets every month, so each is computed again when next asked for: the home tab's refresh button. */
    suspend fun invalidate() = lock.withLock { months.clear() }
}
