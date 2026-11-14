package com.github.aw00987.tokyosandwich.electricity.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.SyncError
import com.github.aw00987.tokyosandwich.core.ui.ChartPalette
import com.github.aw00987.tokyosandwich.core.ui.ErrorText
import com.github.aw00987.tokyosandwich.core.ui.ProviderStyle
import com.github.aw00987.tokyosandwich.core.ui.defaultErrorMessage
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowContainerDark
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowContainerLight
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowLineDark
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowLineLight
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowOnContainerDark
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowOnContainerLight
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowOnLineDark
import com.github.aw00987.tokyosandwich.ui.theme.ElecYellowOnLineLight

/** How TEPCO looks on the home tab: light yellow (dark amber in dark mode), kWh. */
@Composable
fun electricityStyle() = ProviderStyle(
    name = stringResource(R.string.provider_tepco),
    icon = R.drawable.ic_bolt,
    palette = if (isSystemInDarkTheme()) {
        ChartPalette(
            ElecYellowContainerDark,
            ElecYellowOnContainerDark,
            ElecYellowLineDark,
            ElecYellowOnLineDark,
            MaterialTheme.colorScheme.tertiary,
        )
    } else {
        ChartPalette(
            ElecYellowContainerLight,
            ElecYellowOnContainerLight,
            ElecYellowLineLight,
            ElecYellowOnLineLight,
            MaterialTheme.colorScheme.tertiary,
        )
    },
    unit = stringResource(R.string.chart_unit_kwh),
    formatUsage = ::formatKwh,
    chartInfo = stringResource(R.string.elec_chart_note),
    noDataTitle = stringResource(R.string.elec_no_data),
    noDataDetail = stringResource(R.string.elec_not_connected_detail),
)

internal fun formatKwh(value: Double): String = "%.1f kWh".format(value)

@Composable
fun electricityErrorText(error: SyncError) = ErrorText(
    title = stringResource(
        when (error) {
            SyncError.LoginRequired -> R.string.elec_error_login_required
            is SyncError.LoginFailed -> R.string.login_failed
            else -> R.string.elec_error_load_failed
        }
    ),
    message = when (error) {
        SyncError.LoginRequired -> stringResource(R.string.elec_error_login_required_detail)
        is SyncError.LoginFailed -> stringResource(R.string.elec_error_login_failed, error.code)
        SyncError.NoContract -> stringResource(R.string.elec_error_no_contract)
        SyncError.Busy -> stringResource(R.string.elec_error_busy)
        else -> defaultErrorMessage(error)
    },
)
