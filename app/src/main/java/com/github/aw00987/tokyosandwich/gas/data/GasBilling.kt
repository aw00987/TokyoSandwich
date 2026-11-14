package com.github.aw00987.tokyosandwich.gas.data

import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.Texts
import com.github.aw00987.tokyosandwich.core.compact
import com.github.aw00987.tokyosandwich.core.data.ContractInfo
import com.github.aw00987.tokyosandwich.core.data.MonthlyRecord
import com.github.aw00987.tokyosandwich.core.formatYenPrecise
import com.github.aw00987.tokyosandwich.core.isoToTokyoDate
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** A gas contract of the myTOKYOGAS account. [index] (`indexNumber`) identifies it in the API. */
data class GasContract(
    val index: Int,
    /** お客さま番号 as the site shows it; the only way to tell contracts apart (there is no address). */
    val customerNumber: String = "",
    /** The contract has an end date (cancelled). */
    val ended: Boolean = false,
)

/**
 * One billing period. Tokyo Gas reads the meter monthly, so it runs from the day after one reading through the next
 * reading ([end], inclusive). Amounts include tax; [fixedYen]/[volumeYen] are the bill's basic and volume charges
 * (also with tax, with fractions of a yen), null when the bill has no such lines.
 */
data class GasPeriod(
    val start: LocalDate,
    val end: LocalDate,
    val usageM3: Double,
    val costYen: Double,
    val fixedYen: Double? = null,
    val volumeYen: Double? = null,
    /** The meter was not read: an estimated reading (推定検針). */
    val estimated: Boolean = false,
) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)

    /** The month the period is billed in (that of its meter reading), which is the month of its record. */
    val month: YearMonth get() = YearMonth.from(end)

    /** The part of [costYen] that is neither basic nor volume charge (rounding, or lines not seen so far). */
    val unclassifiedYen: Double?
        get() = if (fixedYen == null && volumeYen == null) null else
            costYen - (fixedYen ?: 0.0) - (volumeYen ?: 0.0)
}

/**
 * One record per billing month, oldest first, from the periods of one sync. If two periods end in the same month, the
 * later one wins.
 */
internal fun gasRecords(periods: List<GasPeriod>, texts: Texts): List<MonthlyRecord> =
    periods.sortedBy { it.end }.associateBy { it.month }.values.sortedBy { it.month }
        .map { period ->
            MonthlyRecord(
                month = period.month,
                usage = period.usageM3,
                cost = period.costYen.roundToLong(),
                days = period.days,
                note = gasNote(period, texts),
            )
        }

/** The period's dates and length, then the bill's basic and volume charges. */
internal fun gasNote(period: GasPeriod, texts: Texts): String = buildList {
    add(
        listOfNotNull(
            texts.get(
                R.string.period_range,
                period.start.monthValue,
                period.start.dayOfMonth,
                period.end.monthValue,
                period.end.dayOfMonth,
            ),
            texts.get(R.string.gas_days, period.days),
            texts.get(R.string.gas_estimated).takeIf { period.estimated },
        ).joinToString(" · ")
    )
    fun item(label: Int, amount: Double) =
        texts.get(R.string.note_item, texts.get(label), formatYenPrecise(amount))
    period.fixedYen?.let { add(item(R.string.gas_basic_charge, it)) }
    period.volumeYen?.let { add(item(R.string.gas_volume_charge, it)) }
    period.unclassifiedYen?.takeIf { abs(it) >= 0.5 }
        ?.let { add(item(R.string.gas_other_charge, it)) }
}.joinToString("\n")

/** The contract for the settings picker: its customer number, and whether it has ended. */
internal fun GasContract.toInfo(texts: Texts): ContractInfo = ContractInfo(
    id = index.toString(),
    title = listOfNotNull(
        customerNumber.takeIf { it.isNotBlank() }
            ?.let { texts.get(R.string.gas_customer_number, it) }
            ?: texts.get(R.string.contract_unnamed),
        texts.get(R.string.gas_contract_ended).takeIf { ended },
    ).joinToString(" · "),
)

/** The saved contract if the account still has it, otherwise the first active one, otherwise the first. */
internal fun chooseContract(contracts: List<GasContract>, savedIndex: Int?): GasContract? =
    contracts.firstOrNull { it.index == savedIndex }
        ?: contracts.firstOrNull { !it.ended }
        ?: contracts.firstOrNull()

// --- API payloads (only the fields this app uses); see poc/api/东京燃气api.md ---

/** G1: the account's contracts (gas, and electricity sold by Tokyo Gas). */
internal const val CUSTOMER_NUMBERS_QUERY =
    "query CustomerNumbers { customerNumbers { indexNumber contractType endDate displayCustomerNumber } }"

/** G2: every billing period of a contract with its dates, usage and charges. */
internal const val GAS_CHARGE_QUERY =
    """query GasElectricityCharge(${'$'}contractIndexNumber: Int!) {
  gasElectricityCharge(contractIndexNumber: ${'$'}contractIndexNumber) {
    bills {
      chargeDate totalCharge
      gasCharge {
        subtotalCharge from to
        details {
          usage usageDays charge from to meterReadingDate isEstimated
          breakdownList { charge displayName }
        }
      }
    }
  }
}"""

private const val GAS_CONTRACT = "GAS"
private const val BASIC_CHARGE = "ガス基本料金"
private const val VOLUME_CHARGE = "ガス従量料金"

@Serializable
internal data class CustomerNumbersDto(val customerNumbers: List<Item> = emptyList()) {
    @Serializable
    internal data class Item(
        val indexNumber: Int? = null,
        val contractType: String = "",
        val endDate: String? = null,
        val displayCustomerNumber: String = "",
    )

    val gasContracts: List<GasContract>
        get() = customerNumbers.mapNotNull { item ->
            val index = item.indexNumber ?: return@mapNotNull null
            if (item.contractType.compact() != GAS_CONTRACT) return@mapNotNull null
            GasContract(
                index,
                item.displayCustomerNumber.trim(),
                ended = !item.endDate.isNullOrBlank()
            )
        }
}

@Serializable
internal data class GasChargeDto(val gasElectricityCharge: Charge? = null) {
    @Serializable
    internal data class Charge(val bills: List<Bill> = emptyList())

    @Serializable
    internal data class Bill(val totalCharge: Double? = null, val gasCharge: GasCharge? = null)

    @Serializable
    internal data class GasCharge(
        val subtotalCharge: Double? = null,
        val details: List<Detail> = emptyList()
    )

    @Serializable
    internal data class Detail(
        val usage: Double? = null,
        val charge: Double? = null,
        val from: String = "",
        val to: String = "",
        val isEstimated: Boolean = false,
        val breakdownList: List<Breakdown> = emptyList(),
    )

    @Serializable
    internal data class Breakdown(val charge: Double? = null, val displayName: String = "")

    private val bills get() = gasElectricityCharge?.bills.orEmpty()

    /**
     * The billing periods, oldest first. A bill's details (one so far; several would be a contract change) are added
     * up. Bills without gas details (electricity only) or without dates are skipped.
     */
    fun toPeriods(): List<GasPeriod> = bills.mapNotNull { bill ->
        val gas = bill.gasCharge ?: return@mapNotNull null
        val details = gas.details.ifEmpty { return@mapNotNull null }
        val start =
            details.mapNotNull { it.from.isoToTokyoDate() }.minOrNull() ?: return@mapNotNull null
        val end =
            details.mapNotNull { it.to.isoToTokyoDate() }.maxOrNull() ?: return@mapNotNull null
        if (end < start) return@mapNotNull null
        val usage = details.sumOf { it.usage ?: return@mapNotNull null }
        val charges = details.mapNotNull { it.charge }
        val cost = charges.takeIf { it.size == details.size }?.sum()
            ?: gas.subtotalCharge ?: bill.totalCharge ?: return@mapNotNull null
        val lines = details.flatMap { it.breakdownList }
        fun line(name: String) = lines.filter { it.displayName.compact() == name }
            .takeIf { it.isNotEmpty() }?.sumOf { it.charge ?: 0.0 }
        GasPeriod(
            start = start,
            end = end,
            usageM3 = usage,
            costYen = cost,
            fixedYen = line(BASIC_CHARGE),
            volumeYen = line(VOLUME_CHARGE),
            estimated = details.any { it.isEstimated },
        )
    }.sortedBy { it.start }
}
