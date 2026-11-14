package com.github.aw00987.tokyosandwich.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One provider's row on the settings tab: its [icon] and [name], its account as the status line ([status]: the login
 * ID or email, or "not logged in"; null shows none), and on the right when its data was last fetched and the refresh
 * button, which spins while the provider syncs. Refreshing is possible only once the provider is set up
 * ([canRefresh]). Tapping the time explains how often refreshing is worthwhile; the rest of the row opens the
 * provider's page ([onOpen]).
 */
@Composable
fun ProviderSettingsItem(
    @DrawableRes icon: Int,
    name: String,
    status: String?,
    state: ProviderUiState,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = status?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = { Icon(painterResource(icon), null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.syncedAt?.let { SyncedAt(it) }
                IconButton(onClick = onRefresh, enabled = state.canRefresh) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        stringResource(R.string.refresh),
                        modifier = Modifier.spinning(state.syncing),
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/** The fetch time of the saved data; tapping it explains how often refreshing is worthwhile. */
@Composable
private fun SyncedAt(epochMillis: Long) {
    var showAdvice by rememberSaveable { mutableStateOf(false) }
    val text = remember(epochMillis) {
        SYNCED_AT_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.refresh_advice_title)) {
                showAdvice = true
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
    if (showAdvice) {
        AlertDialog(
            onDismissRequest = { showAdvice = false },
            title = { Text(stringResource(R.string.refresh_advice_title)) },
            text = { Text(stringResource(R.string.refresh_advice_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    showAdvice = false
                }) { Text(stringResource(R.string.chart_info_ok)) }
            },
        )
    }
}

private val SYNCED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/**
 * Spins the element while [active]. When it stops, the current turn finishes instead of jumping back, so the icon
 * always comes to rest upright.
 */
@Composable
private fun Modifier.spinning(active: Boolean): Modifier {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            angle.animateTo(
                360f,
                infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
            )
        } else if (angle.value != 0f) {
            angle.animateTo(
                360f,
                tween(((360f - angle.value) / 360f * 1_000).toInt(), easing = LinearEasing)
            )
            angle.snapTo(0f)
        }
    }
    return rotate(angle.value)
}
