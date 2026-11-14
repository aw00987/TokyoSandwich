package com.github.aw00987.tokyosandwich.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.SyncError

/** What a failed sync tells the user. */
data class ErrorText(val title: String, val message: String)

/**
 * A failed sync, whatever the provider and the reason: it only tells the user what happened. What to do about it (log
 * in again, check the account in the settings, try later) is in the text; the dialog takes the user nowhere.
 */
@Composable
fun SyncErrorDialog(text: ErrorText, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.title) },
        text = { Text(text.message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chart_info_ok)) }
        },
    )
}

/** The message for errors a provider has no words of its own for. */
@Composable
fun defaultErrorMessage(error: SyncError): String = when (error) {
    SyncError.NoCredentials -> stringResource(R.string.error_no_credentials)
    SyncError.LoginRequired -> stringResource(R.string.error_login_required)
    is SyncError.LoginFailed -> listOfNotNull(
        stringResource(R.string.login_error_other, error.code),
        error.detail,
    ).joinToString("\n")

    SyncError.NoContract -> stringResource(R.string.error_no_contract)
    SyncError.Busy -> stringResource(R.string.error_busy)
    is SyncError.Failed -> listOfNotNull(
        stringResource(R.string.load_failed_detail),
        error.message,
    ).joinToString("\n")
}
