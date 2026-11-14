package com.github.aw00987.tokyosandwich.water.ui

import androidx.lifecycle.viewModelScope
import com.github.aw00987.tokyosandwich.core.ui.ProviderViewModel
import com.github.aw00987.tokyosandwich.water.data.WaterRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Tokyo Waterworks data and its account: a saved login ID and password. */
class WaterViewModel(private val repository: WaterRepository) : ProviderViewModel(repository) {
    /** Saved login ID for the Settings tab; empty string means none saved, null means not loaded yet. */
    val loginId: StateFlow<String?> = repository.loginId
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves the account and syncs its data in the background; a null [password] keeps the saved password. */
    fun login(loginId: String, password: String?) {
        forgetContracts()
        repository.login(loginId, password)
    }
}
