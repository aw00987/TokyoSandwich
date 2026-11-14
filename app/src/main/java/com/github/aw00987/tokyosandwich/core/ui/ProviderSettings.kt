package com.github.aw00987.tokyosandwich.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R

/**
 * The layout every provider's settings page shares: the provider's name and a back button on top, then the account
 * section (headed by [accountTitle], the provider's own name for its account, logged in or not; [account] is its
 * content), and the shared contract section once the last sync has listed the contracts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsPage(
    title: String,
    accountTitle: String,
    contracts: ContractChoice?,
    onBack: () -> Unit,
    onSelectContract: (id: String) -> Unit,
    account: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.back)
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(accountTitle)
            account()
            if (contracts != null && contracts.contracts.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                ContractPicker(contracts, onSelectContract)
            }
        }
    }
}

/** A section heading of a settings page (the account's name, "合同"). */
@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/** The saved account (login ID or email), read-only, under the account's [SectionTitle]. */
@Composable
fun AccountValue(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * The two account buttons side by side, the same on every provider's page: "log in and update" and "log out and
 * delete the data".
 */
@Composable
fun AccountButtons(
    loginEnabled: Boolean,
    logoutEnabled: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onLogin, enabled = loginEnabled, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.account_login_update), textAlign = TextAlign.Center)
        }
        OutlinedButton(
            onClick = onLogout,
            enabled = logoutEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.account_logout_delete), textAlign = TextAlign.Center)
        }
    }
}

/**
 * The account part of a provider that saves a login ID and password. Logged out ([savedLoginId] empty) it shows the
 * form, logged in the saved ID; [onLogin] gets the typed credentials, or nulls when logged in (sync with the saved
 * ones). Shows nothing while [savedLoginId] is loading (null).
 */
@Composable
fun CredentialsAccount(
    savedLoginId: String?,
    loginIdLabel: String,
    loginIdContentType: ContentType,
    onLogin: (loginId: String?, password: String?) -> Unit,
    onLogout: () -> Unit,
) {
    if (savedLoginId == null) return
    val loggedIn = savedLoginId.isNotEmpty()
    var loginId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val canLogin = loggedIn || (loginId.isNotBlank() && password.isNotEmpty())
    val login = {
        focusManager.clearFocus()
        if (loggedIn) onLogin(null, null) else onLogin(loginId.trim(), password)
        password = ""
    }

    if (loggedIn) {
        AccountValue(savedLoginId)
    } else {
        OutlinedTextField(
            value = loginId,
            onValueChange = { loginId = it },
            label = { Text(loginIdLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = loginIdContentType },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(stringResource(if (showPassword) R.string.hide else R.string.show))
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (canLogin) login() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
        )
    }
    AccountButtons(
        loginEnabled = canLogin,
        logoutEnabled = loggedIn,
        onLogin = login,
        onLogout = {
            onLogout()
            loginId = ""
            password = ""
        },
    )
}
