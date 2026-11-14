package com.github.aw00987.tokyosandwich.water.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.SyncError
import com.github.aw00987.tokyosandwich.core.formatDecimal
import com.github.aw00987.tokyosandwich.core.ui.ChartPalette
import com.github.aw00987.tokyosandwich.core.ui.ErrorText
import com.github.aw00987.tokyosandwich.core.ui.ProviderStyle
import com.github.aw00987.tokyosandwich.core.ui.defaultErrorMessage
import com.github.aw00987.tokyosandwich.water.data.WaterApi
import kotlin.math.roundToInt

/** How Tokyo Waterworks looks on the home tab: the theme's primary colours, m³ (litres below 1 m³). */
@Composable
fun waterStyle() = ProviderStyle(
    name = stringResource(R.string.provider_tokyo_water),
    icon = R.drawable.ic_water_drop_filled,
    palette = ChartPalette.primary(),
    unit = stringResource(R.string.chart_unit_m3),
    formatUsage = ::formatWater,
    chartInfo = stringResource(R.string.chart_estimated_note),
    noDataTitle = stringResource(R.string.no_data),
    noDataDetail = stringResource(R.string.no_data_detail),
)

/** Litres below 1 m³, m³ with one decimal otherwise. */
internal fun formatWater(m3: Double): String =
    if (m3 < 1.0) "${(m3 * 1000).roundToInt()} L" else "${formatDecimal(m3)} m³"

@Composable
fun waterErrorText(error: SyncError) = ErrorText(
    title = stringResource(
        when (error) {
            is SyncError.LoginFailed -> R.string.login_failed
            SyncError.NoCredentials -> R.string.no_account
            else -> R.string.load_failed
        }
    ),
    message = when (error) {
        is SyncError.LoginFailed -> when (error.code) {
            WaterApi.LOGIN_INVALID -> stringResource(R.string.login_error_invalid)
            WaterApi.LOGIN_LOCKED -> stringResource(R.string.login_error_locked)
            WaterApi.LOGIN_REVOKED -> stringResource(R.string.login_error_revoked)
            else -> stringResource(R.string.login_error_other, error.code)
        }

        SyncError.NoCredentials -> stringResource(R.string.no_account_detail)
        else -> defaultErrorMessage(error)
    },
)
