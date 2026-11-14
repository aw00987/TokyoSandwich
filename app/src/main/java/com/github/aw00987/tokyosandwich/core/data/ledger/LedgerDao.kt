package com.github.aw00987.tokyosandwich.core.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * The bill table. It knows nothing of the cost tree (which is in the settings): the rules are checked by
 * [LedgerStore], which calls these.
 */
@Dao
abstract class LedgerDao {
    /** Every bill: for an export, and for checking the bills against the tree. */
    @Query("SELECT * FROM bill ORDER BY item_id, month")
    abstract suspend fun allBills(): List<BillEntity>

    /** The bills of the item, newest month first (within a month, the last added first). */
    @Query("SELECT * FROM bill WHERE item_id = :itemId ORDER BY month DESC, id DESC")
    abstract fun bills(itemId: Long): Flow<List<BillEntity>>

    @Query("SELECT * FROM bill WHERE item_id = :itemId")
    abstract suspend fun billRows(itemId: Long): List<BillEntity>

    @Query("SELECT * FROM bill WHERE id = :id")
    abstract suspend fun bill(id: Long): BillEntity?

    @Query("SELECT COUNT(*) FROM bill WHERE item_id IN (:itemIds)")
    abstract suspend fun billCount(itemIds: Set<Long>): Int

    /** What each item's bill of [month] amounts to (an item has at most one bill a month). */
    @Query("SELECT item_id AS itemId, amount FROM bill WHERE month = :month")
    abstract suspend fun monthAmounts(month: YearMonth): List<ItemAmount>

    /** The earliest month that has a bill, null when there is none. */
    @Query("SELECT MIN(month) FROM bill")
    abstract fun firstMonth(): Flow<YearMonth?>

    /** Months that have a bill already are skipped (the unique index on `(item_id, month)`): their id is -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBills(bills: List<BillEntity>): List<Long>

    @Update
    abstract suspend fun updateBillRow(bill: BillEntity)

    @Query("DELETE FROM bill WHERE id = :id")
    abstract suspend fun deleteBillRow(id: Long)

    @Query("DELETE FROM bill WHERE id IN (:ids)")
    abstract suspend fun deleteBillRows(ids: Collection<Long>)

    @Query("DELETE FROM bill WHERE item_id IN (:itemIds)")
    abstract suspend fun deleteBillsOf(itemIds: Collection<Long>)

    @Query("DELETE FROM bill WHERE json_extract(ext1, '$.recurringId') = :recurringId AND month >= :from")
    abstract suspend fun deleteRecurringBillsFrom(recurringId: Long, from: YearMonth): Int

    /** The provider's synced bills on its item [itemId], oldest first. */
    @Query("SELECT * FROM bill WHERE item_id = :itemId AND origin = 'Provider' ORDER BY month, id")
    abstract fun providerBills(itemId: Long): Flow<List<BillEntity>>

    @Query("DELETE FROM bill WHERE origin = 'Provider' AND item_id = :itemId")
    abstract suspend fun deleteProviderBills(itemId: Long)

    @Query("DELETE FROM bill")
    protected abstract suspend fun deleteAllBills()

    @Update
    protected abstract suspend fun updateBillRows(bills: List<BillEntity>)

    /**
     * Merges a sync's [bills] into the synced bills of the provider's item [itemId], in one transaction: see
     * [mergeProviderBills]. Months the sync did not return keep their bills.
     */
    @Transaction
    open suspend fun mergeProviderBills(itemId: Long, bills: List<BillEntity>) {
        val saved = billRows(itemId).filter { it.origin == BillOrigin.Provider }
        val merge = mergeProviderBills(
            saved,
            bills.map { it.copy(id = 0, itemId = itemId, origin = BillOrigin.Provider) })
        updateBillRows(merge.updated)
        insertBills(merge.added)
    }

    /** For an import: every bill is replaced by [bills] (the user's), and the providers' are deleted. */
    @Transaction
    open suspend fun replaceAllBills(bills: List<BillEntity>) {
        deleteAllBills()
        insertBills(bills.map { it.copy(id = 0) })
    }
}

/** What a sync changes in a provider's bills: rows to update in place and rows to insert. */
internal data class ProviderBillMerge(val updated: List<BillEntity>, val added: List<BillEntity>)

/**
 * Merges a sync's bills ([synced], one per month) into the provider's [saved] bills, by month: a month that has a
 * bill gets the new amount, note and ext (a recomputed or corrected bill), keeping its id and `createdAt`; a new month
 * is added. A month that is unchanged is left alone, and a saved month the sync did not return is kept, so the history
 * grows beyond what the service still returns.
 */
internal fun mergeProviderBills(
    saved: List<BillEntity>,
    synced: List<BillEntity>
): ProviderBillMerge {
    val byMonth = saved.associateBy { it.month }
    val updated = mutableListOf<BillEntity>()
    val added = mutableListOf<BillEntity>()
    for (bill in synced) {
        val old = byMonth[bill.month]
        if (old == null) {
            added += bill
        } else {
            val new = bill.copy(id = old.id, itemId = old.itemId, createdAt = old.createdAt)
            if (new != old) updated += new
        }
    }
    return ProviderBillMerge(updated, added)
}
