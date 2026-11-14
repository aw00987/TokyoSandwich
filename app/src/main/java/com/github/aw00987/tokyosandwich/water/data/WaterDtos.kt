package com.github.aw00987.tokyosandwich.water.data

import com.github.aw00987.tokyosandwich.core.compact
import com.github.aw00987.tokyosandwich.core.toAmount
import com.github.aw00987.tokyosandwich.core.toYearMonth
import kotlinx.serialization.Serializable

/** A water contract (usage location) of the account; [key] is the API's `wKey`. */
data class Contract(
    val key: String,
    val number: String,
    val address: String,
    val status: ContractStatus,
)

enum class ContractStatus { Active, StartRequested, StopRequested, Stopped, Unknown }

/** The saved contract if the account still has it, otherwise the first one (the default). */
fun chooseContract(contracts: List<Contract>, savedKey: String?): Contract? =
    contracts.firstOrNull { it.key == savedKey } ?: contracts.firstOrNull()

// --- API payloads (only the fields this app uses) ---

@Serializable
internal data class ContractDto(
    val wKey: String,
    val wNumber: String = "",
    val wStatus: String = "",
    val address1: String = "",
    val address2: String = "",
    val address3: String = "",
    val address4: String = "",
    val address5: String = "",
    val address6: String = "",
    val address7: String = "",
    val address8: String = "",
) {
    fun toDomain() = Contract(
        key = wKey,
        number = wNumber.trim(),
        address = formatAddress(),
        status = when (wStatus.trim()) {
            "04", "06" -> ContractStatus.Active
            "03" -> ContractStatus.StartRequested
            "07", "10" -> ContractStatus.StopRequested
            "08", "09", "11", "12", "13" -> ContractStatus.Stopped
            else -> ContractStatus.Unknown
        },
    )

    /**
     * Roughly the official web app's layout: ward/city, town, chōme, banchi-gō, then building and room.
     * (`address1`–`address8` are ward, town, chōme, banchi, gō, building name, building number, room.)
     */
    private fun formatAddress(): String {
        val chome = address3.compact()
            .let { if (it.isNotEmpty() && it.all(Char::isDigit)) "${it}丁目" else it }
        val block = listOf(address4, address5).map { it.compact() }.filter { it.isNotEmpty() }
            .joinToString("-")
        val building =
            listOf(address6, address7, address8).map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(" ")
        return listOf(address1.compact() + address2.compact() + chome + block, building)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }
}

/**
 * Item of `/user/useddata/{wKey}`; months are `yyyyMM` billing months, amounts are numeric strings.
 * `jys*` is 水道 (water supply), `ges*` is 下水道 (sewer); [remark2] is the period's `jissekiId`.
 */
@Serializable
internal data class UsedDataDto(
    val seiDateStart: String = "",
    val seiDateEnd: String = "",
    val sryo: String = "",
    val jysChAm: String = "",
    val gesChAm: String = "",
    val toChAm: String = "",
    val remark2: String = "",
) {
    /**
     * Fallback when the period's detail is unavailable: reading dates are estimated as the month before each billing
     * month (billing 6–7月 ≈ use 5/1–6/30; actual readings are a few days later), and the split is unknown.
     */
    fun toEstimatedPeriod(): WaterPeriod? {
        val from = seiDateStart.toYearMonth() ?: return null
        val to = seiDateEnd.toYearMonth() ?: from
        val usage = sryo.trim().toDoubleOrNull() ?: return null
        val total = toChAm.toAmount() ?: listOfNotNull(
            jysChAm.toAmount(),
            gesChAm.toAmount()
        ).takeIf { it.isNotEmpty() }?.sum()
        return WaterPeriod(
            start = from.minusMonths(1).atDay(1),
            endInclusive = to.minusMonths(1).atEndOfMonth(),
            usageM3 = usage,
            cost = CostBreakdown(unclassified = (total ?: 0).toDouble()),
            billedFrom = from,
            billedTo = to,
            approximate = true,
        )
    }
}

/**
 * `/user/meterdata/{wKey}?jisseki_id=…`; dates come pre-formatted without a year, like `" 7月 3日"`.
 * Charges: `*ChAm` = total incl. tax, `*KiAm` = basic (fixed) and `*JuAm` = volume charge before tax, for
 * `jys` (水道) and `ges` (下水道).
 */
@Serializable
internal data class MeterDataDto(
    val seiDateSt1: String = "",
    val seiDateEnd1: String = "",
    val useStDate: String = "",
    val useEndDate: String = "",
    val useDays: String = "",
    val sryoUsed: String = "",
    val jysChAm: String = "",
    val jysKiAm: String = "",
    val jysJuAm: String = "",
    val gesChAm: String = "",
    val gesKiAm: String = "",
    val gesJuAm: String = "",
    val toChAm: String = "",
    val nextKenScDate: String = "",
) {
    fun toPeriod(): WaterPeriod? {
        val from = seiDateSt1.toYearMonth() ?: return null
        val to = seiDateEnd1.toYearMonth() ?: from
        val (start, end) = readingDates(useStDate, useEndDate, to) ?: return null
        if (end < start) return null
        val usage = sryoUsed.trim().toDoubleOrNull() ?: return null
        val (waterFixed, waterVolume, waterOther) = splitCharge(
            jysChAm.toAmount(),
            jysKiAm.toAmount(),
            jysJuAm.toAmount()
        )
        val (sewerFixed, sewerVolume, sewerOther) = splitCharge(
            gesChAm.toAmount(),
            gesKiAm.toAmount(),
            gesJuAm.toAmount()
        )
        var cost =
            CostBreakdown(waterFixed, waterVolume, sewerFixed, sewerVolume, waterOther + sewerOther)
        // Neither service reported a charge: keep the bill total, if any, as unclassified.
        if (jysChAm.toAmount() == null && gesChAm.toAmount() == null) {
            cost = CostBreakdown(unclassified = (toChAm.toAmount() ?: 0).toDouble())
        }
        return WaterPeriod(start, end, usage, cost, from, to)
    }
}
