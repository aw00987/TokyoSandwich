package com.github.aw00987.tokyosandwich.water.data

import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.Texts
import com.github.aw00987.tokyosandwich.core.data.ContractInfo
import com.github.aw00987.tokyosandwich.core.data.MonthlyRecord
import com.github.aw00987.tokyosandwich.core.formatYenPrecise
import java.time.YearMonth
import kotlin.math.roundToLong

// The last step of the water processing: billing periods (about two months each) become the calendar-month records
// that are saved. The estimate (daily averages, see [UsageAggregator]) is always made from the complete history of one
// sync, never from records saved earlier.

/** One record per fully billed calendar month, oldest first; a partial month at either end is left out. */
internal fun waterRecords(periods: List<WaterPeriod>, texts: Texts): List<MonthlyRecord> =
    UsageAggregator.months(periods).map { point ->
        MonthlyRecord(
            month = YearMonth.from(point.start),
            usage = point.usageM3,
            cost = point.cost.total.roundToLong(),
            days = point.coveredDays,
            note = waterNote(point, texts),
        )
    }

/** How the month was estimated, then the fixed/volume split of its cost (water supply and sewer). */
internal fun waterNote(point: UsagePoint, texts: Texts): String = buildList {
    add(
        buildList {
            add(texts.get(R.string.bubble_estimated))
            if (point.partial) add(
                texts.get(
                    R.string.bubble_partial,
                    point.coveredDays,
                    point.totalDays
                )
            )
            if (point.approximate) add(texts.get(R.string.bubble_approximate))
        }.joinToString(" · ")
    )
    val cost = point.cost
    fun item(label: Int, amount: Double) =
        texts.get(R.string.note_item, texts.get(label), formatYenPrecise(amount))
    if (cost.fixed + cost.volume > 0.005 || cost.unclassified <= 0.005) {
        add(item(R.string.bubble_fixed, cost.fixed))
        add(
            texts.get(
                R.string.bubble_water_basic,
                formatYenPrecise(cost.waterFixed),
                formatYenPrecise(cost.sewerFixed),
            )
        )
        add(item(R.string.bubble_volume, cost.volume))
        add(
            texts.get(
                R.string.bubble_water_volume,
                formatYenPrecise(cost.waterVolume),
                formatYenPrecise(cost.sewerVolume),
            )
        )
    }
    if (cost.unclassified > 0.005) add(item(R.string.bubble_unclassified, cost.unclassified))
}.joinToString("\n")

/** The contract for the settings picker: its number, status and whether it is the default; the address below. */
internal fun Contract.toInfo(texts: Texts, isDefault: Boolean): ContractInfo = ContractInfo(
    id = key,
    title = buildList {
        add(
            texts.get(
                R.string.contract_number,
                number.ifEmpty { texts.get(R.string.contract_unnamed) })
        )
        when (status) {
            ContractStatus.Active -> R.string.contract_status_active
            ContractStatus.StartRequested -> R.string.contract_status_start_requested
            ContractStatus.StopRequested -> R.string.contract_status_stop_requested
            ContractStatus.Stopped -> R.string.contract_status_stopped
            ContractStatus.Unknown -> null
        }?.let { add(texts.get(it)) }
        if (isDefault) add(texts.get(R.string.contract_default))
    }.joinToString(" · "),
    subtitle = address,
)
