package com.github.aw00987.tokyosandwich.gas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.SyncError
import com.github.aw00987.tokyosandwich.core.formatDecimal
import com.github.aw00987.tokyosandwich.core.ui.ChartPalette
import com.github.aw00987.tokyosandwich.core.ui.ErrorText
import com.github.aw00987.tokyosandwich.core.ui.ProviderStyle
import com.github.aw00987.tokyosandwich.core.ui.defaultErrorMessage
import com.github.aw00987.tokyosandwich.gas.data.GasLoginFailure
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameContainerDark
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameContainerLight
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameLineDark
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameLineLight
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameOnContainerDark
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameOnContainerLight
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameOnLineDark
import com.github.aw00987.tokyosandwich.ui.theme.GasFlameOnLineLight

/** How Tokyo Gas looks on the home tab: orange (dark rust in dark mode), m³. */
@Composable
fun gasStyle() = ProviderStyle(
    name = stringResource(R.string.provider_tokyo_gas),
    icon = R.drawable.ic_flame,
    palette = if (isSystemInDarkTheme()) {
        ChartPalette(
            GasFlameContainerDark,
            GasFlameOnContainerDark,
            GasFlameLineDark,
            GasFlameOnLineDark,
            MaterialTheme.colorScheme.tertiary,
        )
    } else {
        ChartPalette(
            GasFlameContainerLight,
            GasFlameOnContainerLight,
            GasFlameLineLight,
            GasFlameOnLineLight,
            MaterialTheme.colorScheme.tertiary,
        )
    },
    unit = stringResource(R.string.chart_unit_m3),
    formatUsage = ::formatM3,
    chartInfo = stringResource(R.string.gas_chart_note),
    noDataTitle = stringResource(R.string.gas_no_data),
    noDataDetail = stringResource(R.string.gas_no_data_detail),
)

/** m³ with up to two decimals (daily averages are around 1 m³). */
internal fun formatM3(value: Double): String =
    if (value >= 10 || value % 1.0 == 0.0) "${formatDecimal(value)} m³" else "%.2f m³".format(value)

/** The login page's own error text is shown when there is one. */
@Composable
fun gasErrorText(error: SyncError) = ErrorText(
    title = stringResource(
        if (error is SyncError.LoginFailed) R.string.gas_login_failed else R.string.gas_error_load_failed
    ),
    message = when (error) {
        SyncError.NoCredentials -> stringResource(R.string.gas_no_account_detail)
        is SyncError.LoginFailed -> listOfNotNull(
            stringResource(
                when (error.code) {
                    GasLoginFailure.Rejected.name -> R.string.gas_login_rejected
                    GasLoginFailure.PasswordChangeRequired.name -> R.string.gas_login_password_change
                    GasLoginFailure.InteractionRequired.name -> R.string.gas_login_interaction
                    else -> R.string.gas_login_page_unknown
                }
            ),
            error.detail,
        ).joinToString("\n")

        SyncError.NoContract -> stringResource(R.string.gas_error_no_contract)
        SyncError.Busy -> stringResource(R.string.gas_error_busy)
        else -> defaultErrorMessage(error)
    },
)
