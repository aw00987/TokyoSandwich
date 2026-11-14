package com.github.aw00987.tokyosandwich.gas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ContractInfo
import com.github.aw00987.tokyosandwich.core.ui.ContractChoice
import com.github.aw00987.tokyosandwich.core.ui.CredentialsAccount
import com.github.aw00987.tokyosandwich.core.ui.ProviderSettingsPage
import com.github.aw00987.tokyosandwich.ui.theme.TokyoSandwichTheme

/**
 * Tokyo Gas account and contract, the same page as the water one. [savedLoginId] is the stored login ID ("" when
 * logged out, null while loading). [onLogin] gets the typed credentials, or nulls to sync with the saved ones.
 */
@Composable
fun TokyoGasSettingsScreen(
    savedLoginId: String?,
    contracts: ContractChoice?,
    onBack: () -> Unit,
    onLogin: (loginId: String?, password: String?) -> Unit,
    onLogout: () -> Unit,
    onSelectContract: (id: String) -> Unit,
) {
    ProviderSettingsPage(
        title = stringResource(R.string.provider_tokyo_gas),
        accountTitle = stringResource(R.string.gas_account_name),
        contracts = contracts.takeUnless { savedLoginId.isNullOrEmpty() },
        onBack = onBack,
        onSelectContract = onSelectContract,
    ) {
        CredentialsAccount(
            savedLoginId = savedLoginId,
            loginIdLabel = stringResource(R.string.gas_login_id),
            loginIdContentType = ContentType.Username,
            onLogin = onLogin,
            onLogout = onLogout,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TokyoGasSettingsLoggedInPreview() {
    TokyoSandwichTheme(dynamicColor = false) {
        val contracts = listOf(
            ContractInfo("1", "お客さま番号 0000-000-0001"),
            ContractInfo("2", "お客さま番号 0000-000-0002 · 已解约"),
        )
        TokyoGasSettingsScreen(
            savedLoginId = "user@example.com",
            contracts = ContractChoice(contracts, "1"),
            onBack = {},
            onLogin = { _, _ -> },
            onLogout = {},
            onSelectContract = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TokyoGasSettingsLoggedOutPreview() {
    TokyoSandwichTheme(dynamicColor = false) {
        TokyoGasSettingsScreen(
            savedLoginId = "",
            contracts = null,
            onBack = {},
            onLogin = { _, _ -> },
            onLogout = {},
            onSelectContract = {},
        )
    }
}
