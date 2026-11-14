package com.github.aw00987.tokyosandwich.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.BackupProblem
import com.github.aw00987.tokyosandwich.core.data.SyncError
import com.github.aw00987.tokyosandwich.core.data.homeCardOf
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import com.github.aw00987.tokyosandwich.core.data.needsLogin
import com.github.aw00987.tokyosandwich.core.ui.ErrorText
import com.github.aw00987.tokyosandwich.core.ui.ProviderHomeSection
import com.github.aw00987.tokyosandwich.core.ui.ProviderSettingsItem
import com.github.aw00987.tokyosandwich.core.ui.ProviderStyle
import com.github.aw00987.tokyosandwich.core.ui.ProviderUiState
import com.github.aw00987.tokyosandwich.core.ui.ProviderViewModel
import com.github.aw00987.tokyosandwich.core.ui.SyncErrorDialog
import com.github.aw00987.tokyosandwich.cost.ui.CostScreen
import com.github.aw00987.tokyosandwich.cost.ui.CostViewModel
import com.github.aw00987.tokyosandwich.electricity.ui.ElectricityViewModel
import com.github.aw00987.tokyosandwich.electricity.ui.TepcoLoginScreen
import com.github.aw00987.tokyosandwich.electricity.ui.TepcoSettingsScreen
import com.github.aw00987.tokyosandwich.electricity.ui.clearTepcoWebSession
import com.github.aw00987.tokyosandwich.electricity.ui.electricityErrorText
import com.github.aw00987.tokyosandwich.electricity.ui.electricityStyle
import com.github.aw00987.tokyosandwich.gas.ui.GasViewModel
import com.github.aw00987.tokyosandwich.gas.ui.TokyoGasSettingsScreen
import com.github.aw00987.tokyosandwich.gas.ui.gasErrorText
import com.github.aw00987.tokyosandwich.gas.ui.gasStyle
import com.github.aw00987.tokyosandwich.home.ui.HomeScreen
import com.github.aw00987.tokyosandwich.home.ui.HomeViewModel
import com.github.aw00987.tokyosandwich.water.ui.TokyoWaterSettingsScreen
import com.github.aw00987.tokyosandwich.water.ui.WaterViewModel
import com.github.aw00987.tokyosandwich.water.ui.waterErrorText
import com.github.aw00987.tokyosandwich.water.ui.waterStyle

private enum class Tab(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int
) {
    Cost(R.string.tab_cost, R.drawable.ic_cost, R.drawable.ic_cost_filled),
    Home(R.string.tab_home, R.drawable.ic_home, R.drawable.ic_home_filled),
    Settings(R.string.tab_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
}

/**
 * Root composable: bottom navigation between the cost tab (the cost tree), the home tab (the cards the user put
 * there, `home/ui/HomeScreen`) and the settings tab.
 */
@Composable
fun AppRoot(
    water: WaterViewModel,
    electricity: ElectricityViewModel,
    gas: GasViewModel,
    cost: CostViewModel,
    home: HomeViewModel,
    backup: BackupViewModel,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Root) }
    // System back leaves a settings sub-page before it leaves the app (the login page handles back itself).
    BackHandler(
        enabled = tab == Tab.Settings && settingsPage in setOf(
            SettingsPage.TokyoWater,
            SettingsPage.Tepco,
            SettingsPage.TokyoGas,
        )
    ) {
        settingsPage = SettingsPage.Root
    }
    val waterState by water.state.collectAsStateWithLifecycle()
    val waterLoginId by water.loginId.collectAsStateWithLifecycle()
    val waterContracts by water.contracts.collectAsStateWithLifecycle()
    val waterError by water.syncError.collectAsStateWithLifecycle()
    val elecState by electricity.state.collectAsStateWithLifecycle()
    val elecContracts by electricity.contracts.collectAsStateWithLifecycle()
    val elecError by electricity.syncError.collectAsStateWithLifecycle()
    val tepcoEmail by electricity.email.collectAsStateWithLifecycle()
    val gasState by gas.state.collectAsStateWithLifecycle()
    val gasContracts by gas.contracts.collectAsStateWithLifecycle()
    val gasError by gas.syncError.collectAsStateWithLifecycle()
    val gasLoginId by gas.loginId.collectAsStateWithLifecycle()
    val homeState by home.state.collectAsStateWithLifecycle()
    val backupRunning by backup.running.collectAsStateWithLifecycle()
    // Kept here, not on the settings page, so a result that comes after the page was left is still shown there.
    val settingsSnackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(backup) {
        backup.events.collect { event ->
            settingsSnackbar.showSnackbar(resources.getString(backupMessage(event)))
        }
    }

    val openSettings = { page: SettingsPage ->
        settingsPage = page
        tab = Tab.Settings
    }
    val tepcoStatus = when {
        tepcoEmail == null -> null
        tepcoEmail.isNullOrEmpty() && elecState is ProviderUiState.Content -> null
        else -> accountStatus(tepcoEmail)
    }
    // The same for every provider: its home card, its settings row (with the refresh button) and its sync errors.
    // `loginPage` is where the user logs in again.
    val waterEntry = ProviderEntry(
        "water", water, waterState, waterError, waterStyle(), accountStatus(waterLoginId),
        SettingsPage.TokyoWater,
    ) { waterErrorText(it) }
    val elecEntry = ProviderEntry(
        "electricity", electricity, elecState, elecError, electricityStyle(), tepcoStatus,
        SettingsPage.Tepco,
    ) { electricityErrorText(it) }
    val gasEntry = ProviderEntry(
        "gas", gas, gasState, gasError, gasStyle(), accountStatus(gasLoginId),
        SettingsPage.TokyoGas,
    ) { gasErrorText(it) }

    // One dialog at a time; the next provider's error shows after this one is dismissed. A failure that needs a new
    // login first opens that provider's settings page, so the dialog is shown where the user logs in.
    val failed = listOf(waterEntry, elecEntry, gasEntry).firstOrNull { it.error != null }
    LaunchedEffect(failed?.key, failed?.error) {
        if (failed != null && failed.error?.needsLogin == true) openSettings(failed.loginPage)
    }
    failed?.let { entry ->
        entry.error?.let { SyncErrorDialog(entry.errorText(it), entry.viewModel::dismissError) }
    }

    Scaffold(
        // Each tab draws its own top app bar, so only the bottom bar's space is reserved here.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    val selected = item == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                painterResource(if (selected) item.selectedIcon else item.icon),
                                null
                            )
                        },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            when (tab) {
                Tab.Cost -> CostScreen(
                    cost,
                    onHome = { item -> homeState?.cards?.any { it.key == homeCardOf(item).key } == true },
                    onToggleHome = home::toggle,
                )

                Tab.Home -> HomeScreen(
                    state = homeState,
                    onSelectMonth = home::selectMonth,
                    onRefresh = home::refresh,
                    onMove = home::move,
                    onRemove = home::remove,
                    onRestore = home::restore,
                    bills = home::bills,
                ) { kind, month, cardModifier ->
                    val entry = when (kind) {
                        ProviderKind.Water -> waterEntry
                        ProviderKind.Electricity -> elecEntry
                        ProviderKind.Gas -> gasEntry
                    }
                    ProviderHomeSection(
                        entry.state, entry.style, month, entry.viewModel::refresh, cardModifier,
                    )
                }

                Tab.Settings -> when (settingsPage) {
                    SettingsPage.Root -> SettingsScreen(
                        providers = {
                            listOf(elecEntry, gasEntry, waterEntry).forEach { entry ->
                                ProviderSettingsItem(
                                    icon = entry.style.icon,
                                    name = entry.style.name,
                                    status = entry.status,
                                    state = entry.state,
                                    onOpen = { settingsPage = entry.loginPage },
                                    onRefresh = entry.viewModel::refresh,
                                )
                            }
                        },
                        backupRunning = backupRunning,
                        snackbar = settingsSnackbar,
                        onExport = backup::export,
                        onImport = backup::import,
                    )

                    SettingsPage.TokyoWater -> TokyoWaterSettingsScreen(
                        savedLoginId = waterLoginId,
                        contracts = waterContracts,
                        onBack = { settingsPage = SettingsPage.Root },
                        onLogin = { id, password ->
                            if (id == null) water.refresh() else water.login(id, password)
                            tab = Tab.Home
                        },
                        onLogout = water::logout,
                        onSelectContract = water::selectContract,
                    )

                    SettingsPage.Tepco -> TepcoSettingsScreen(
                        email = tepcoEmail.orEmpty(),
                        loggedIn = elecState is ProviderUiState.Content ||
                                !tepcoEmail.isNullOrEmpty() || elecContracts != null,
                        contracts = elecContracts,
                        onBack = { settingsPage = SettingsPage.Root },
                        onLogin = { settingsPage = SettingsPage.TepcoLogin },
                        onLogout = {
                            electricity.logout()
                            clearTepcoWebSession()
                        },
                        onSelectContract = electricity::selectContract,
                    )

                    SettingsPage.TepcoLogin -> TepcoLoginScreen(
                        onCode = { code, verifier ->
                            // The app keeps the token, not TEPCO's web session.
                            clearTepcoWebSession()
                            electricity.login(code, verifier)
                            settingsPage = SettingsPage.Root
                            tab = Tab.Home
                        },
                        onError = { code ->
                            clearTepcoWebSession()
                            electricity.loginFailed(code)
                            settingsPage = SettingsPage.Tepco
                        },
                        onBack = { settingsPage = SettingsPage.Tepco },
                    )

                    SettingsPage.TokyoGas -> TokyoGasSettingsScreen(
                        savedLoginId = gasLoginId,
                        contracts = gasContracts,
                        onBack = { settingsPage = SettingsPage.Root },
                        onLogin = { id, password ->
                            if (id == null) gas.refresh() else gas.login(id, password)
                            tab = Tab.Home
                        },
                        onLogout = gas::logout,
                        onSelectContract = gas::selectContract,
                    )
                }
            }
        }
    }
}

/** What the home tab, the settings tab and the error dialog need of one provider; the same for every provider. */
private class ProviderEntry(
    val key: String,
    val viewModel: ProviderViewModel,
    val state: ProviderUiState,
    val error: SyncError?,
    val style: ProviderStyle,
    /** The account in the settings row, or "not logged in"; none while loading. */
    val status: String?,
    /** The provider's settings page, where the user logs in. */
    val loginPage: SettingsPage,
    val errorText: @Composable (SyncError) -> ErrorText,
)

/** A provider's status line in the settings: its account, or "not logged in"; none while loading (null). */
@Composable
private fun accountStatus(account: String?): String? = when {
    account == null -> null
    account.isEmpty() -> stringResource(R.string.not_logged_in)
    else -> account
}

/** The snackbar text of an export's or import's result. */
private fun backupMessage(event: BackupEvent): Int = when (event) {
    BackupEvent.Exported -> R.string.backup_exported
    BackupEvent.Imported -> R.string.backup_imported
    is BackupEvent.Failed -> when (event.problem) {
        null -> R.string.backup_failed_io
        BackupProblem.NotABackup -> R.string.backup_failed_not_backup
        BackupProblem.OtherVersion -> R.string.backup_failed_version
        BackupProblem.BrokenTree -> R.string.backup_failed_tree
        BackupProblem.BrokenBills -> R.string.backup_failed_bills
        BackupProblem.DuplicateMonth -> R.string.backup_failed_duplicate
    }
}
