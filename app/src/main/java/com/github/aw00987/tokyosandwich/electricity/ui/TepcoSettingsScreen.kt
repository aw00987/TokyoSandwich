package com.github.aw00987.tokyosandwich.electricity.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ContractInfo
import com.github.aw00987.tokyosandwich.core.ui.AccountButtons
import com.github.aw00987.tokyosandwich.core.ui.AccountValue
import com.github.aw00987.tokyosandwich.core.ui.ContractChoice
import com.github.aw00987.tokyosandwich.core.ui.ProviderSettingsPage
import com.github.aw00987.tokyosandwich.ui.theme.TokyoSandwichTheme

/**
 * TEPCO account and contract, laid out like the water and gas pages. There is no login form: logging in opens
 * TEPCO's own page ([onLogin]). [email] is the account's email from the last login ("" when unknown); [loggedIn]
 * means there is something to log out of (data, a login or a contract choice).
 */
@Composable
fun TepcoSettingsScreen(
    email: String,
    loggedIn: Boolean,
    contracts: ContractChoice?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSelectContract: (id: String) -> Unit,
) {
    ProviderSettingsPage(
        title = stringResource(R.string.provider_tepco),
        accountTitle = stringResource(R.string.tepco_account_name),
        contracts = contracts,
        onBack = onBack,
        onSelectContract = onSelectContract,
    ) {
        if (email.isNotEmpty()) AccountValue(email)
        AccountButtons(
            loginEnabled = true,
            logoutEnabled = loggedIn,
            onLogin = onLogin,
            onLogout = onLogout,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TepcoSettingsPreview() {
    TokyoSandwichTheme(dynamicColor = false) {
        val contracts = listOf(
            ContractInfo("0000000000000001", "従量電灯B · 主合同", "東京都新宿区西新宿2丁目8-1"),
            ContractInfo("0000000000000002", "スタンダードS", "東京都世田谷区北沢1丁目2-3"),
        )
        TepcoSettingsScreen(
            email = "user@example.com",
            loggedIn = true,
            contracts = ContractChoice(contracts, contracts[0].id),
            onBack = {},
            onLogin = {},
            onLogout = {},
            onSelectContract = {},
        )
    }
}
