package com.github.aw00987.tokyosandwich.gas.ui

import androidx.lifecycle.viewModelScope
import com.github.aw00987.tokyosandwich.core.ui.ProviderViewModel
import com.github.aw00987.tokyosandwich.gas.data.GasRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Tokyo Gas data and its account: a saved login ID and password, which the repository logs in with by itself. */
class GasViewModel(private val repository: GasRepository) : ProviderViewModel(repository) {
    /** Saved login ID for the settings page; empty string means none saved, null means not loaded yet. */
    val loginId: StateFlow<String?> = repository.loginId
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves the account and syncs its data in the background; a null [password] keeps the saved password. */
    fun login(loginId: String, password: String?) {
        forgetContracts()
        repository.login(loginId, password)
    }
}
