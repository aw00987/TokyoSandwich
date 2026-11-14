package com.github.aw00987.tokyosandwich.core.data

import android.content.Context
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * What the providers keep on this device besides their bills, in a small JSON file of its own
 * (`provider_sync.json`, not encrypted): what the last sync saved, and per-install IDs. None of it is user data: it is
 * never exported, and a sync (or a new install) makes it again.
 */
@Serializable
data class SyncState(
    val water: ProviderSync = ProviderSync(),
    val electricity: ProviderSync = ProviderSync(),
    val gas: ProviderSync = ProviderSync(),
) {
    operator fun get(kind: ProviderKind): ProviderSync = when (kind) {
        ProviderKind.Water -> water
        ProviderKind.Electricity -> electricity
        ProviderKind.Gas -> gas
    }

    fun with(kind: ProviderKind, sync: ProviderSync): SyncState = when (kind) {
        ProviderKind.Water -> copy(water = sync)
        ProviderKind.Electricity -> copy(electricity = sync)
        ProviderKind.Gas -> copy(gas = sync)
    }
}

/** One provider's part of [SyncState]. */
@Serializable
data class ProviderSync(
    /** A random ID per install that some services want with every request (TEPCO); kept across logouts. */
    val clientId: String? = null,
    /** What the last sync saved besides the bills; null when the provider has no data. */
    val synced: SyncedData? = null,
)

/**
 * The part of a [UsageData] that is not bills, and when it was fetched. Its records are bills of the provider's item
 * in the ledger.
 */
@Serializable
data class SyncedData(
    /** When the data was fetched, in epoch milliseconds. */
    val at: Long,
    val contract: ContractInfo,
    val contracts: List<ContractInfo> = emptyList(),
    val soFar: PeriodSoFar? = null,
) {
    fun withRecords(records: List<MonthlyRecord>): UsageData =
        UsageData(contract, contracts, records, soFar)
}

fun UsageData.synced(at: Long): SyncedData = SyncedData(at, contract, contracts, soFar)

/** Reads and writes [SyncState] (`provider_sync.json` in the app's private files, excluded from backups). */
class SyncStore(context: Context) {
    private val store =
        jsonDataStore(context, "provider_sync.json", SyncState.serializer(), SyncState())

    fun provider(kind: ProviderKind): Flow<ProviderSync> =
        store.data.map { it[kind] }.distinctUntilChanged()

    suspend fun current(kind: ProviderKind): ProviderSync = store.data.first()[kind]

    /** Changes the part of [kind] in one write; returns the new one. */
    suspend fun update(
        kind: ProviderKind,
        transform: (ProviderSync) -> ProviderSync
    ): ProviderSync =
        store.updateData { it.with(kind, transform(it[kind])) }[kind]

    /** Deletes every provider's synced data (the client IDs stay). */
    suspend fun clearSynced() {
        store.updateData { state ->
            ProviderKind.entries.fold(state) { s, k ->
                s.with(
                    k,
                    s[k].copy(synced = null)
                )
            }
        }
    }
}
