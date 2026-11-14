package com.github.aw00987.tokyosandwich.core.data

import com.github.aw00987.tokyosandwich.core.data.ledger.LedgerStore
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/** What a provider has saved: its data (null when none) and when it was synced. */
data class SavedUsage(val data: UsageData? = null, val syncedAt: Long? = null)

/**
 * A provider's saved data and settings. The data is in two places: its monthly records as bills of its item in the
 * [ledger], the rest ([SyncedData]) in its [ProviderSync]. The contract choice is in its [ProviderSettings]. The data
 * is never edited: a successful sync writes the months it computed anew (the saved months it did not return stay) and
 * replaces the rest, and a logout, an account change or a contract switch deletes it.
 */
class UsageStore(
    private val settings: SettingsStore,
    private val sync: SyncStore,
    private val ledger: LedgerStore,
    private val provider: ProviderKind,
) {
    /** Without [ProviderSync.synced] the provider has no data, whatever bills are still in the ledger. */
    val data: Flow<SavedUsage> =
        combine(sync.provider(provider), ledger.providerRecords(provider)) { saved, records ->
            saved.synced?.let { SavedUsage(it.withRecords(records), it.at) } ?: SavedUsage()
        }

    /**
     * The records first, merged into the saved ones by month in one transaction, then the synced data. If the second write fails, the new records show with
     * the old contracts and sync time until the next sync replaces both.
     */
    suspend fun save(data: UsageData, syncedAt: Long) {
        ledger.mergeProviderRecords(provider, data.records)
        sync.update(provider) { it.copy(synced = data.synced(syncedAt)) }
    }

    /** Deletes the data; the contract choice stays. */
    suspend fun clear() {
        sync.update(provider) { it.copy(synced = null) }
        ledger.clearProvider(provider)
    }

    /** Deletes the data and the contract choice, which belonged to the account (on logout or account change). */
    suspend fun forgetAccount() {
        settings.update(provider) { it.copy(selectedContract = null) }
        sync.update(provider) { it.copy(synced = null) }
        ledger.clearProvider(provider)
    }

    /** The id of the contract chosen in settings, or null for the default. */
    suspend fun selectedContract(): String? = settings.current(provider).selectedContract

    suspend fun selectContract(id: String?) {
        settings.update(provider) { it.copy(selectedContract = id) }
    }

    /** The provider's per-install client ID, made the first time it is needed. */
    suspend fun clientId(): String {
        sync.current(provider).clientId?.let { return it }
        return sync.update(provider) {
            it.copy(
                clientId = it.clientId ?: UUID.randomUUID().toString()
            )
        }
            .clientId!!
    }
}
