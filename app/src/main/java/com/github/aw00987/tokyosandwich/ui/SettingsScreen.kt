package com.github.aw00987.tokyosandwich.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.ui.ProviderSettingsItem
import com.github.aw00987.tokyosandwich.core.ui.ProviderUiState
import com.github.aw00987.tokyosandwich.ui.theme.TokyoSandwichTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Sub-pages of the Settings tab. Further providers get their own entries later. */
enum class SettingsPage { Root, TokyoWater, Tepco, TepcoLogin, TokyoGas }

/**
 * First level of the Settings tab: one row per utility provider ([providers], each a
 * [com.github.aw00987.tokyosandwich.core.ui.ProviderSettingsItem]: its account, when its data was fetched and its
 * refresh button), then "语言" (the app's language, see
 * [AppLanguage]), then "外观" (system, light or dark, see [AppTheme]), then "数据": export the user's data to a file, or import one (after a confirmation, since it replaces
 * everything).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    providers: @Composable () -> Unit,
    backupRunning: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onExport: (Uri) -> Unit = {},
    onImport: (Uri) -> Unit = {},
) {
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }
    var importing by rememberSaveable { mutableStateOf<Uri?>(null) }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importing = it }
    importing?.let { uri ->
        AlertDialog(
            onDismissRequest = { importing = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    importing = null
                    onImport(uri)
                }) { Text(stringResource(R.string.backup_import_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    importing = null
                }) { Text(stringResource(R.string.cost_cancel)) }
            },
        )
    }
    val fileName = stringResource(
        R.string.backup_file_name,
        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Icon(
                        painterResource(R.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp)
                            .size(28.dp),
                    )
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            providers()
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_language))
            LanguageItem()
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_theme))
            ThemeItem()
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_data))
            ActionItem(
                R.drawable.ic_file_download,
                stringResource(R.string.backup_export),
                !backupRunning
            ) {
                exportFile.launch(fileName)
            }
            ActionItem(
                R.drawable.ic_file_upload,
                stringResource(R.string.backup_import),
                !backupRunning
            ) {
                importFile.launch(
                    arrayOf(
                        "application/json",
                        "text/plain",
                        "application/octet-stream"
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun languageLabel(language: AppLanguage): String =
    language.label ?: stringResource(R.string.language_system)

@Composable
private fun themeLabel(theme: AppTheme): String = stringResource(
    when (theme) {
        AppTheme.System -> R.string.language_system
        AppTheme.Light -> R.string.theme_light
        AppTheme.Dark -> R.string.theme_dark
    }
)

/** The app's language, and a dialog to choose another. */
@Composable
private fun LanguageItem() {
    val context = LocalContext.current
    ChoiceItem(
        R.drawable.ic_language,
        stringResource(R.string.settings_language),
        AppLanguage.entries,
        remember { currentAppLanguage(context) },
        { languageLabel(it) },
    ) { setAppLanguage(context, it) }
}

/** The app's theme (system, light or dark), and a dialog to choose another. */
@Composable
private fun ThemeItem() {
    val context = LocalContext.current
    ChoiceItem(
        R.drawable.ic_dark_mode,
        stringResource(R.string.settings_theme),
        AppTheme.entries,
        remember { currentAppTheme(context) },
        { themeLabel(it) },
    ) { setAppTheme(context, it) }
}

/** A row showing the [current] option, and a dialog of radio buttons to choose another; [onChoose] gets a new one. */
@Composable
private fun <T> ChoiceItem(
    @DrawableRes icon: Int,
    title: String,
    options: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label(current)) },
        leadingContent = { Icon(painterResource(icon), null) },
        modifier = Modifier.clickable { choosing = true },
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == current,
                                    role = Role.RadioButton,
                                    onClick = {
                                        choosing = false
                                        if (option != current) onChoose(option)
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == current, onClick = null)
                            Text(label(option), Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    choosing = false
                }) { Text(stringResource(R.string.cost_cancel)) }
            },
        )
    }
}

@Composable
private fun ActionItem(
    @DrawableRes icon: Int,
    name: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name) },
        leadingContent = { Icon(painterResource(icon), null) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TokyoSandwichTheme(dynamicColor = false) {
        SettingsScreen(
            providers = {
                ProviderSettingsItem(
                    icon = R.drawable.ic_bolt,
                    name = stringResource(R.string.provider_tepco),
                    status = "user@example.com",
                    state = ProviderUiState.NotConnected,
                    onOpen = {},
                    onRefresh = {},
                )
            },
        )
    }
}
