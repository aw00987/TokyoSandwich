package com.github.aw00987.tokyosandwich.electricity.data

import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.Texts
import com.github.aw00987.tokyosandwich.core.compact
import com.github.aw00987.tokyosandwich.core.data.ContractInfo
import com.github.aw00987.tokyosandwich.core.data.MonthlyRecord
import com.github.aw00987.tokyosandwich.core.data.PeriodSoFar
import com.github.aw00987.tokyosandwich.core.toAmount
import com.github.aw00987.tokyosandwich.core.toDecimal
import com.github.aw00987.tokyosandwich.core.toLocalDate
import com.github.aw00987.tokyosandwich.core.toYearMonth
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** An electricity contract of the TEPCO account. [number] (`contractNum`) identifies it. */
data class TepcoContract(
    val number: String,
    val planName: String,
    val address: String,
    val main: Boolean = false,
)

/**
 * One billing month (B3 `usedMonth`). TEPCO reads the meter monthly, so it covers one meter reading period, roughly
 * mid-previous month to mid-month, not the calendar month. [confirmed] is false while the bill is still provisional
 * (`billingClass` 00). [start]/[end] are the period's dates when known (B3 does not return them; see [fillPeriodDates]).
 */
data class BilledMonth(
    val month: YearMonth,
    val usageKwh: Double,
    val costYen: Int,
    val confirmed: Boolean = true,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
) {
    /** Days of the period, when its dates are known. */
    val days: Int?
        get() = if (start != null && end != null && end >= start) {
            (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        } else null
}

/**
 * The meter reading period in progress ([billingMonth] is the month it will be billed in): actual usage and cost from
 * [start] through [usedThrough] (yesterday), from B2 `usedInfo`. The service's forecast is not used.
 */
data class CurrentPeriod(
    val billingMonth: YearMonth,
    val start: LocalDate,
    val end: LocalDate,
    val usedThrough: LocalDate? = null,
    val usageKwh: Double? = null,
    val costYen: Int? = null,
) {
    fun toSoFar() = PeriodSoFar(start, usedThrough, usageKwh, costYen?.toDouble())
}

/**
 * The period dates of one sync's billed months, as far as they can be told: [known] (from B2) for its month, and what
 * follows from the neighbours. Consecutive billing months are back to back, and the period before [current] ends the
 * day before it starts. Uses nothing but this sync's answers.
 */
internal fun fillPeriodDates(
    months: List<BilledMonth>,
    current: CurrentPeriod?,
    known: Map<YearMonth, Pair<LocalDate, LocalDate>> = emptyMap(),
): List<BilledMonth> {
    val dated = months.map { m ->
        known[m.month]?.let { (start, end) -> m.copy(start = start, end = end) } ?: m
    }
    val byMonth = dated.associateBy { it.month }
    return dated.map { m ->
        val nextStart = byMonth[m.month.plusMonths(1)]?.start
            ?: current?.takeIf { it.billingMonth == m.month.plusMonths(1) }?.start
        val previousEnd = byMonth[m.month.minusMonths(1)]?.end
        m.copy(start = m.start ?: previousEnd?.plusDays(1), end = m.end ?: nextStart?.minusDays(1))
    }
}

/** One record per billing month, oldest first, from the months of one sync. */
internal fun tepcoRecords(months: List<BilledMonth>, texts: Texts): List<MonthlyRecord> =
    months.associateBy { it.month }.values.sortedBy { it.month }.map { month ->
        MonthlyRecord(
            month = month.month,
            usage = month.usageKwh,
            cost = month.costYen.toLong(),
            days = month.days,
            note = tepcoNote(month, texts),
        )
    }

/** Whether the bill is final, and the period's dates when known. */
internal fun tepcoNote(month: BilledMonth, texts: Texts): String = listOfNotNull(
    texts.get(if (month.confirmed) R.string.elec_bill_confirmed else R.string.elec_bill_provisional),
    month.days?.let { days ->
        listOf(
            texts.get(
                R.string.period_range,
                month.start!!.monthValue,
                month.start.dayOfMonth,
                month.end!!.monthValue,
                month.end.dayOfMonth,
            ),
            texts.get(R.string.gas_days, days),
        ).joinToString(" · ")
    },
).joinToString("\n")

/** The contract for the settings picker: its plan, whether it is the main one, and the address below. */
internal fun TepcoContract.toInfo(texts: Texts): ContractInfo = ContractInfo(
    id = number,
    title = listOfNotNull(
        planName.ifEmpty { texts.get(R.string.contract_unnamed) },
        texts.get(R.string.tepco_contract_main).takeIf { main },
    ).joinToString(" · "),
    subtitle = address,
)

/** The saved contract if the account still has it, otherwise the main contract, otherwise the first. */
internal fun chooseContract(contracts: List<ContractDto>, savedNumber: String?): ContractDto? =
    contracts.firstOrNull { it.contractNum == savedNumber }
        ?: contracts.firstOrNull { it.mainContractFlag == "1" }
        ?: contracts.firstOrNull()

// --- API payloads (only the fields this app uses); see poc/api/东京电力api.md ---

/** `contractClass` values of electricity contracts: 01 regulated (経過措置) plans, 02 deregulated plans. */
private val ELECTRIC_CLASSES = setOf("01", "02")

private const val KWH = "kWh"

/** `billingStatus` 01: the item has data. */
private const val HAS_DATA = "01"

/** C1 `/contract/list`. */
@Serializable
internal data class ContractListDto(
    val readOffset: String = "0",
    val contracts: List<ContractDto> = emptyList(),
) {
    val electricContracts: List<ContractDto>
        get() = contracts.filter { it.contractClass in ELECTRIC_CLASSES && it.contractNum.isNotBlank() }
}

@Serializable
internal data class ContractDto(
    val contractClass: String = "",
    val contractNum: String = "",
    val address: String = "",
    val planName: String = "",
    val mainContractFlag: String = "",
) {
    fun toDomain() = TepcoContract(
        number = contractNum.compact(),
        planName = planName.trim(),
        address = address.trim(),
        main = mainContractFlag == "1",
    )
}

/** B1 `/billing/meter`: the reading period in progress. */
@Serializable
internal data class BillingMeterDto(val meterInfo: MeterInfoDto = MeterInfoDto())

@Serializable
internal data class MeterInfoDto(
    val month: String = "",
    val startDate: String = "",
    val endDate: String = "",
)

/** B2 `/billing/month`: a billing month's period and its actual usage so far (the forecast is ignored). */
@Serializable
internal data class BillingMonthDto(val billInfo: BillInfoDto = BillInfoDto()) {
    @Serializable
    internal data class BillInfoDto(
        val billingStatus: String = "",
        val meterInfo: MeterInfoDto = MeterInfoDto(),
        val usedInfo: UsedInfoDto? = null,
    )

    @Serializable
    internal data class UsedInfoDto(
        val endDate: String = "",
        val charge: String = "",
        val power: String = "",
        val unit: String = "",
    )

    /** Combines B1's period with B2's actual figures so far. */
    fun toPeriod(meter: MeterInfoDto): CurrentPeriod? {
        val month = meter.month.toYearMonth() ?: return null
        val start = meter.startDate.toLocalDate() ?: return null
        val end = meter.endDate.toLocalDate() ?: return null
        val used =
            billInfo.usedInfo?.takeIf { billInfo.billingStatus == HAS_DATA && it.unit.isKwh() }
        return CurrentPeriod(
            billingMonth = month,
            start = start,
            end = end,
            usedThrough = used?.endDate?.toLocalDate(),
            usageKwh = used?.power?.toDecimal(),
            costYen = used?.charge?.toAmount(),
        )
    }

    /** The period's dates (B2 for a past billing month), or null. */
    fun periodDates(): Pair<LocalDate, LocalDate>? {
        val start = billInfo.meterInfo.startDate.toLocalDate() ?: return null
        val end = billInfo.meterInfo.endDate.toLocalDate() ?: return null
        return (start to end).takeIf { end >= start }
    }
}

/** B3 `/billing/month-history`: the billed months, oldest first. */
@Serializable
internal data class MonthHistoryDto(val billInfos: List<Item> = emptyList()) {
    @Serializable
    internal data class Item(
        val usedMonth: String = "",
        val billingStatus: String = "",
        val detailInfos: List<Detail> = emptyList(),
    )

    @Serializable
    internal data class Detail(
        val billingClass: String = "",
        val usedCharge: String = "",
        val usedPowerInfo: Usage? = null,
        val historyInfos: List<History> = emptyList(),
    )

    @Serializable
    internal data class History(val usedPowerInfo: Usage? = null)

    @Serializable
    internal data class Usage(val power: String = "", val unit: String = "")

    /**
     * A month's bill is the sum of its details (a contract change mid-month gives several); its usage is the sum of
     * `historyInfos` when present, else of the details. Months without data, or with another unit, are skipped.
     */
    fun toMonths(): List<BilledMonth> = billInfos.mapNotNull { item ->
        val month = item.usedMonth.toYearMonth() ?: return@mapNotNull null
        if (item.billingStatus != HAS_DATA || item.detailInfos.isEmpty()) return@mapNotNull null
        val usages = item.detailInfos.flatMap { detail ->
            detail.historyInfos.mapNotNull { it.usedPowerInfo }
                .ifEmpty { listOfNotNull(detail.usedPowerInfo) }
        }
        if (usages.isEmpty() || usages.any { !it.unit.isKwh() }) return@mapNotNull null
        val usage = usages.map { it.power.toDecimal() ?: return@mapNotNull null }.sum()
        val cost = item.detailInfos.sumOf { it.usedCharge.toAmount() ?: return@mapNotNull null }
        BilledMonth(
            month,
            usage,
            cost,
            confirmed = item.detailInfos.all { it.billingClass == "01" })
    }.sortedBy { it.month }
}

private fun String.isKwh() = compact().equals(KWH, ignoreCase = true)
