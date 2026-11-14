package com.github.aw00987.tokyosandwich.core.data

import com.github.aw00987.tokyosandwich.core.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// The one model every provider saves. A provider's own processing (parsing, estimating, rounding) ends here: what the
// device keeps, and what the screens read, is only this.

/**
 * One month of one utility: how much was used and what it cost (whole yen, tax included). What "month" means is the
 * provider's choice (a calendar month for water, the billing month for electricity and gas); [note] says so where it
 * matters. It is saved as a bill of the provider's cost item (see `ledger/LedgerStore`).
 */
data class MonthlyRecord(
    val month: YearMonth,
    /** In the provider's unit (m³ or kWh). */
    val usage: Double,
    /** Rounded to whole yen by the provider's processing, like every bill. */
    val cost: Long,
    /** Days the figures cover, for the daily average; null when the provider does not know. */
    val days: Int? = null,
    /**
     * Whatever else the provider wants to tell about the month, as plain text with one item per line: how the figures
     * were obtained, the period's dates, the cost breakdown.
     */
    val note: String = "",
) {
    val dailyAverage: Double? get() = days?.takeIf { it > 0 }?.let { usage / it }
}

/** A contract of the account as the settings picker shows it. [id] is what the provider's API calls it. */
@Serializable
data class ContractInfo(
    val id: String,
    val title: String,
    val subtitle: String = "",
)

/**
 * The period that is not billed yet: actual figures from [start] through [through]. Only providers that report them
 * (electricity) fill this in.
 */
@Serializable
data class PeriodSoFar(
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val through: LocalDate? = null,
    val usage: Double? = null,
    val cost: Double? = null,
) {
    /** Days from [start] through [through], or 0 when nothing is used yet. */
    val days: Int
        get() = through?.takeIf { it >= start }
            ?.let { (ChronoUnit.DAYS.between(start, it) + 1).toInt() } ?: 0

    val dailyAverage: Double? get() = usage?.takeIf { days > 0 }?.let { it / days }
}

/**
 * Everything one sync produces for [contract]. A sync always builds it from the service's answer alone. Saved in two
 * parts: [records] merged by month into the bills in the ledger (the saved months the service no longer returns stay),
 * the rest as [SyncedData] in the settings. Read back, [records] are all the saved months.
 */
data class UsageData(
    /** The contract the records belong to. */
    val contract: ContractInfo,
    /** Every contract of the account, for the settings picker. */
    val contracts: List<ContractInfo> = emptyList(),
    /** Oldest first, at most one per month. */
    val records: List<MonthlyRecord> = emptyList(),
    val soFar: PeriodSoFar? = null,
)
