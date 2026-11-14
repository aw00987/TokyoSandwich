package com.github.aw00987.tokyosandwich.water.ui

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
 * Tokyo Waterworks account and contract. [savedLoginId] is the stored login ID ("" when logged out, null while
 * loading). [onLogin] gets the typed credentials, or nulls to sync with the saved ones.
 */
@Composable
fun TokyoWaterSettingsScreen(
    savedLoginId: String?,
    contracts: ContractChoice?,
    onBack: () -> Unit,
    onLogin: (loginId: String?, password: String?) -> Unit,
    onLogout: () -> Unit,
    onSelectContract: (key: String) -> Unit,
) {
    ProviderSettingsPage(
        title = stringResource(R.string.provider_tokyo_water),
        accountTitle = stringResource(R.string.water_account_name),
        contracts = contracts.takeUnless { savedLoginId.isNullOrEmpty() },
        onBack = onBack,
        onSelectContract = onSelectContract,
    ) {
        CredentialsAccount(
            savedLoginId = savedLoginId,
            loginIdLabel = stringResource(R.string.login_id),
            loginIdContentType = ContentType.EmailAddress,
            onLogin = onLogin,
            onLogout = onLogout,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TokyoWaterSettingsLoggedInPreview() {
    TokyoSandwichTheme(dynamicColor = false) {
        val contracts = listOf(
            ContractInfo("a", "No. 1234567890 · 签约中 · 默认", "新宿区西新宿2丁目8-1"),
            ContractInfo("b", "No. 9876543210 · 已停止", "世田谷区北沢1丁目2-3 サンドイッチ荘 101"),
        )
        TokyoWaterSettingsScreen(
            savedLoginId = "user@example.com",
            contracts = ContractChoice(contracts, "a"),
            onBack = {},
            onLogin = { _, _ -> },
            onLogout = {},
            onSelectContract = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TokyoWaterSettingsLoggedOutPreview() {
    TokyoSandwichTheme(dynamicColor = false) {
        TokyoWaterSettingsScreen(
            savedLoginId = "",
            contracts = null,
            onBack = {},
            onLogin = { _, _ -> },
            onLogout = {},
            onSelectContract = {},
        )
    }
}
