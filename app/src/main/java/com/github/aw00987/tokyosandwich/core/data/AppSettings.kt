package com.github.aw00987.tokyosandwich.core.data

import android.content.Context
import com.github.aw00987.tokyosandwich.core.data.ledger.CostTree
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * The user's data apart from the bills, in one JSON file (`settings.json`, not encrypted): the cost tree with its
 * recurring bills, and what the user chose in settings. This is what an export saves, with the bills added
 * (`Backup.kt`). What the syncs produce is in [SyncState] instead, and accounts, passwords and sessions stay in each
 * provider's encrypted store. App-wide settings go next to [providers].
 */
@Serializable
data class AppSettings(
    /** Null until the app has made the default tree (`LedgerStore.startUp`). */
    val costs: CostTree? = null,
    val providers: ProvidersSettings = ProvidersSettings(),
    /** The home tab's cards; the three utilities until the user changes them. */
    val home: HomeLayout = HomeLayout(),
)

/** One [ProviderSettings] per provider. */
@Serializable
data class ProvidersSettings(
    val water: ProviderSettings = ProviderSettings(),
    val electricity: ProviderSettings = ProviderSettings(),
    val gas: ProviderSettings = ProviderSettings(),
) {
    operator fun get(kind: ProviderKind): ProviderSettings = when (kind) {
        ProviderKind.Water -> water
        ProviderKind.Electricity -> electricity
        ProviderKind.Gas -> gas
    }

    fun with(kind: ProviderKind, settings: ProviderSettings): ProvidersSettings = when (kind) {
        ProviderKind.Water -> copy(water = settings)
        ProviderKind.Electricity -> copy(electricity = settings)
        ProviderKind.Gas -> copy(gas = settings)
    }
}

/** What the user chose for a provider. */
@Serializable
data class ProviderSettings(
    /** The id of the contract chosen in settings ([ContractInfo.id]), or null for the provider's default. */
    val selectedContract: String? = null,
)

/** Reads and writes [AppSettings] (`settings.json` in the app's private files, excluded from backups). */
class SettingsStore(context: Context) {
    private val store =
        jsonDataStore(context, "settings.json", AppSettings.serializer(), AppSettings())

    val data: Flow<AppSettings> = store.data

    suspend fun current(): AppSettings = store.data.first()

    /** Changes the settings in one write; returns the new ones. */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings =
        store.updateData(transform)

    fun provider(kind: ProviderKind): Flow<ProviderSettings> =
        store.data.map { it.providers[kind] }.distinctUntilChanged()

    suspend fun current(kind: ProviderKind): ProviderSettings = current().providers[kind]

    /** Changes the settings of [kind] in one write. */
    suspend fun update(kind: ProviderKind, transform: (ProviderSettings) -> ProviderSettings) {
        update { it.copy(providers = it.providers.with(kind, transform(it.providers[kind]))) }
    }
}
