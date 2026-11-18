package com.github.aw00987.tokyosandwich.core.data.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.github.aw00987.tokyosandwich.core.YearMonthSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.YearMonth

/** The utility a built-in cost item stands for. */
enum class ProviderKind { Water, Electricity, Gas }

/**
 * What a cost item is, fixed when it is created. A [Cost] (费用) is a "file": it has bills and no sub-items. A [Set]
 * (费用集) is a "folder": it has sub-items (costs and sets) and no bills of its own.
 */
enum class ItemKind { Cost, Set }

/**
 * The items the app makes itself, whose name follows the app's language until the user renames them: the root and the
 * three utilities. The names are string resources (`cost/ui/ItemTitles.kt`).
 */
enum class BuiltInTitle { Root, Electricity, Gas, Water }

/**
 * One cost of the user's life, or a set of them. Items form a tree of [ItemKind.Set]s (folders) holding
 * [ItemKind.Cost]s (files) and further sets. Exactly one item, the root ("总花费", a set), has no parent. Saved in
 * [CostTree], in the settings, not in the database.
 */
@Serializable
data class CostItem(
    val id: Long,
    val parentId: Long?,
    /** The user's name for the item; empty, and not shown, while [builtIn] is set. */
    val title: String,
    val note: String = "",
    /** Never changes after the item is created. */
    val kind: ItemKind,
    /**
     * The key of the icon the user picked for the item (see `cost/ui/ItemIcons.kt`); null, or a key the app no longer
     * knows, means the default icon: a folder for a set, a bill for a cost, the utility's own icon for a provider item.
     */
    val icon: String? = null,
    /**
     * Set on the three built-in utility items (costs), one each; their bills come from the provider's sync, not from
     * the user.
     */
    val provider: ProviderKind? = null,
    /** Epoch milliseconds. (The order of an item among its siblings is its place in [CostTree.items].) */
    val createdAt: Long,
    /** Set while the item has the app's own name, in the app's language; renaming the item clears it. */
    val builtIn: BuiltInTitle? = null,
)

/**
 * The cost tree and the recurring bills, as saved in the settings (`settings.json`). Ids of items and recurring bills
 * come from one counter, [nextId]. The bills are in the database and point at the tree by id; there is no foreign key,
 * so the app checks them against the tree when it starts (`orphanBills`).
 */
@Serializable
data class CostTree(
    val nextId: Long,
    /**
     * Every item. The items of one parent are shown in the order of this list, which the user changes by dragging
     * (`placed`); so the order is saved, and exported, with the tree. No two items of one parent have names that
     * clash (`nameClash`).
     */
    val items: List<CostItem> = emptyList(),
    val recurring: List<RecurringBill> = emptyList(),
)

/** Where a bill came from. [Provider] rows are the utilities' synced monthly records. */
enum class BillOrigin { Manual, Recurring, Provider }

/**
 * The one bill table: a row is one bill of one cost item for one month, in whole yen; an item has at most one bill per
 * month. Every kind of bill ends up as such rows: a bill typed in for a month, the months of a history entry, the
 * months a recurring bill has reached, and the monthly records of a provider's sync (on the provider's item; every
 * sync writes the months it returned anew and keeps the others). What only some origins have is in [ext] (column `ext1`, JSON).
 */
@Entity(
    tableName = "bill",
    // One bill per item and month. It also makes catching up recurring bills idempotent. [itemId] points at a
    // [CostItem] of the tree in the settings, so there is no foreign key.
    indices = [Index("item_id", "month", unique = true)],
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val month: YearMonth,
    val amount: Long,
    val note: String = "",
    val origin: BillOrigin,
    /** The fields of some origins only; null when the bill has none. */
    @ColumnInfo(name = "ext1") val ext: BillExt? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    val usage: Double? get() = ext?.usage
    val days: Int? get() = ext?.days
    val recurringId: Long? get() = ext?.recurringId
}

/** What only some bills have, saved as JSON in the `ext1` column. Absent fields are null. */
@Serializable
data class BillExt(
    /** What was used, in the provider's unit (m³ or kWh); [BillOrigin.Provider] bills. */
    val usage: Double? = null,
    /** Days the bill covers, for the daily average; [BillOrigin.Provider] bills, null when unknown. */
    val days: Int? = null,
    /** The [RecurringBill] the bill belongs to; [BillOrigin.Recurring] bills. */
    val recurringId: Long? = null,
) {
    /** Null when there is nothing to save. */
    fun orNull(): BillExt? = takeUnless { it == BillExt() }
}

/**
 * A bill of the same amount every month from [startMonth] through [endMonth] (null = no end), saved in [CostTree]. Its
 * months up to the current one exist as [BillEntity] rows; the app adds the missing ones whenever it starts.
 */
@Serializable
data class RecurringBill(
    val id: Long,
    val itemId: Long,
    val amount: Long,
    val note: String = "",
    @Serializable(with = YearMonthSerializer::class) val startMonth: YearMonth,
    @Serializable(with = YearMonthSerializer::class) val endMonth: YearMonth? = null,
)

/** The JSON of `ext1`: null fields are left out, unknown ones ignored. */
private val extJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** `YearMonth` as ISO text (`2026-10`), which also sorts by time; [BillExt] as JSON. */
class CostConverters {
    @TypeConverter
    fun extToText(ext: BillExt?): String? =
        ext?.orNull()?.let { extJson.encodeToString(BillExt.serializer(), it) }

    @TypeConverter
    fun textToExt(text: String?): BillExt? =
        text?.let { extJson.decodeFromString(BillExt.serializer(), it) }

    @TypeConverter
    fun monthToText(month: YearMonth?): String? = month?.toString()

    @TypeConverter
    fun textToMonth(text: String?): YearMonth? = text?.let(YearMonth::parse)
}
