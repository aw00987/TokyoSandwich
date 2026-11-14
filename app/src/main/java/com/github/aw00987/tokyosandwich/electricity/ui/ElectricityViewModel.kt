package com.github.aw00987.tokyosandwich.electricity.ui

import androidx.lifecycle.viewModelScope
import com.github.aw00987.tokyosandwich.core.ui.ProviderViewModel
import com.github.aw00987.tokyosandwich.electricity.data.ElectricityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The TEPCO data. There is no saved password: syncing needs the saved token of a login on TEPCO's page, and a sync
 * without a valid one fails with "login required", which is shown like any other sync error.
 */
class ElectricityViewModel(private val repository: ElectricityRepository) :
    ProviderViewModel(repository) {
    /** The account's email for the settings; empty string means unknown, null means not loaded yet. */
    val email: StateFlow<String?> = repository.email
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Redeems the code from TEPCO's login page and downloads the data in the background. */
    fun login(code: String, codeVerifier: String) {
        repository.login(code, codeVerifier)
    }

    fun loginFailed(code: String) = repository.loginFailed(code)
}
