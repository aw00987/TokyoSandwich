package com.github.aw00987.tokyosandwich.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aw00987.tokyosandwich.core.data.ContractInfo
import com.github.aw00987.tokyosandwich.core.data.ProviderRepository
import com.github.aw00987.tokyosandwich.core.data.SyncError
import com.github.aw00987.tokyosandwich.core.data.UsageData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a provider's part of the home tab shows. */
sealed interface ProviderUiState {
    data object Loading : ProviderUiState

    /** The provider is not set up: no account is saved, or the user never logged in. */
    data object NotConnected : ProviderUiState

    /** Set up, but no data is saved: the first sync is running ([syncing]), or it failed. */
    data class NoData(val syncing: Boolean) : ProviderUiState

    data class Content(val data: UsageData, val syncedAt: Long?, val refreshing: Boolean) :
        ProviderUiState
}

/** True while a sync of the provider is running. */
val ProviderUiState.syncing: Boolean
    get() = (this as? ProviderUiState.NoData)?.syncing == true ||
            (this as? ProviderUiState.Content)?.refreshing == true

/** When the saved data was fetched (epoch milliseconds), or null when there is none. */
val ProviderUiState.syncedAt: Long?
    get() = (this as? ProviderUiState.Content)?.syncedAt

/** Whether a refresh can be started: the provider is set up (or has data) and no sync is running. */
val ProviderUiState.canRefresh: Boolean
    get() = this != ProviderUiState.Loading && this != ProviderUiState.NotConnected && !syncing

/** The account's contracts and the one currently shown, as of the last successful sync. */
data class ContractChoice(val contracts: List<ContractInfo>, val selectedId: String?)

/**
 * Shows a provider's data saved on the device; opening the app does not use the network. Refresh, contract switches
 * and logout are delegated to the [ProviderRepository], which runs them in the background; their failures surface as
 * [syncError]. A provider's own view model adds its way of logging in.
 */
open class ProviderViewModel(private val repository: ProviderRepository) : ViewModel() {
    val state: StateFlow<ProviderUiState> = combine(
        repository.connected,
        repository.usage,
        repository.syncedAt,
        repository.status,
    ) { connected, usage, syncedAt, status ->
        when {
            usage != null -> ProviderUiState.Content(usage, syncedAt, status.running)
            status.running -> ProviderUiState.NoData(syncing = true)
            !connected -> ProviderUiState.NotConnected
            else -> ProviderUiState.NoData(syncing = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderUiState.Loading)

    /** The last sync's failure, to show as a dialog until dismissed. */
    val syncError: StateFlow<SyncError?> = repository.status
        .map { it.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Kept separately from the saved data so the picker stays visible while a contract switch re-syncs.
    private val _contracts = MutableStateFlow<ContractChoice?>(null)

    /** Contracts for the settings picker; null until data has been synced. */
    val contracts: StateFlow<ContractChoice?> = _contracts.asStateFlow()

    init {
        viewModelScope.launch {
            repository.usage.filterNotNull()
                .collect { _contracts.value = ContractChoice(it.contracts, it.contract.id) }
        }
    }

    /** Downloads the data again; the saved data is replaced only when the new download is complete. */
    fun refresh() {
        repository.refresh()
    }

    /** Switches the contract shown on the home tab and downloads its data. */
    fun selectContract(id: String) {
        if (_contracts.value?.selectedId == id) return
        _contracts.update { it?.copy(selectedId = id) }
        repository.selectContract(id)
    }

    /** Deletes the account and its saved data in the background. */
    fun logout() {
        forgetContracts()
        repository.logout()
    }

    fun dismissError() = repository.dismissError()

    /** The contracts belong to the account that is being replaced or removed. */
    protected fun forgetContracts() {
        _contracts.value = null
    }
}
