package com.github.aw00987.tokyosandwich

import android.app.Application
import com.github.aw00987.tokyosandwich.core.ResourceTexts
import com.github.aw00987.tokyosandwich.core.data.BackupService
import com.github.aw00987.tokyosandwich.core.data.SettingsStore
import com.github.aw00987.tokyosandwich.core.data.SyncStore
import com.github.aw00987.tokyosandwich.core.data.UsageStore
import com.github.aw00987.tokyosandwich.core.data.ledger.LedgerDatabase
import com.github.aw00987.tokyosandwich.core.data.ledger.LedgerStore
import com.github.aw00987.tokyosandwich.core.data.ledger.MonthTotalsCache
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import com.github.aw00987.tokyosandwich.cost.ui.builtInNames
import com.github.aw00987.tokyosandwich.electricity.data.ElectricityRepository
import com.github.aw00987.tokyosandwich.electricity.data.TepcoApi
import com.github.aw00987.tokyosandwich.electricity.data.TepcoSettingsStore
import com.github.aw00987.tokyosandwich.gas.data.GasAccountStore
import com.github.aw00987.tokyosandwich.gas.data.GasRepository
import com.github.aw00987.tokyosandwich.gas.data.TokyoGasApi
import com.github.aw00987.tokyosandwich.gas.data.TokyoGasWebLogin
import com.github.aw00987.tokyosandwich.water.data.SessionStore
import com.github.aw00987.tokyosandwich.water.data.WaterApi
import com.github.aw00987.tokyosandwich.water.data.WaterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds app-wide singletons (manual dependency injection). */
class TokyoSandwichApp : Application() {
    /** Work that must outlive any screen, such as syncing and deleting the user's data. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val texts by lazy { ResourceTexts(this) }

    /** The user's data apart from the bills: the cost tree, its recurring bills and the settings. */
    val settings by lazy { SettingsStore(this) }

    /** What the syncs saved besides the bills, and per-install IDs. */
    private val sync by lazy { SyncStore(this) }

    /** The built-in cost items' names in every language: no item of their level may be named the same. */
    private val itemNames by lazy { builtInNames(this) }

    /** The cost tree and every bill: the user's, and the providers' synced monthly records. */
    val ledger by lazy {
        LedgerStore(
            LedgerDatabase.create(this).dao(),
            settings,
            itemNames,
        )
    }

    /** What the cost tree cost month by month, kept in memory for the home tab. */
    val monthTotals by lazy { MonthTotalsCache(ledger) }

    /** Export and import of the user's data. */
    val backup by lazy { BackupService(ledger, sync, itemNames) }

    val waterRepository by lazy {
        WaterRepository(
            WaterApi(),
            SessionStore(this),
            UsageStore(settings, sync, ledger, ProviderKind.Water),
            texts,
            appScope,
        )
    }

    val electricityRepository by lazy {
        ElectricityRepository(
            TepcoApi(),
            TepcoSettingsStore(this),
            UsageStore(settings, sync, ledger, ProviderKind.Electricity),
            texts,
            appScope,
        )
    }

    val gasRepository by lazy {
        GasRepository(
            TokyoGasApi(),
            GasAccountStore(this),
            UsageStore(settings, sync, ledger, ProviderKind.Gas),
            TokyoGasWebLogin(this),
            texts,
            appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Recurring bills have no background job: the months that came since the last start get their bills now.
        // The home tab's months are computed once the bills are in order.
        appScope.launch {
            ledger.startUp()
            monthTotals.warmUp()
        }
    }
}
