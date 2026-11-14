package com.github.aw00987.tokyosandwich.core.data.ledger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Every bill, the user's and the utilities' synced monthly records alike, in the SQLite file `cost.db` (app-private,
 * excluded from backups, not encrypted: it holds months, amounts, usage and notes, never accounts, contract numbers or
 * addresses). The cost tree the bills belong to is in the settings ([CostTree]). Use it through [LedgerStore]. It is
 * user-written data that no sync brings back: a schema change after release needs a migration.
 */
@Database(entities = [BillEntity::class], version = 1)
@TypeConverters(CostConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        fun create(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "cost.db")
                .build()
    }
}
