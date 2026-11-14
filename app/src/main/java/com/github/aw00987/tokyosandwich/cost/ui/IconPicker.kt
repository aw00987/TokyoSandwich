package com.github.aw00987.tokyosandwich.cost.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R

/**
 * The item's icon as a button, put at the start of its name field. Tapping it opens the icon picker. [icon] is the
 * picked icon's key, null for the default one ([defaultIcon]).
 */
@Composable
fun IconChoiceButton(icon: String?, @DrawableRes defaultIcon: Int, onPick: (String?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FilledTonalIconButton(onClick = { picking = true }) {
        Icon(
            painterResource(ItemIcon.of(icon)?.drawable ?: defaultIcon),
            stringResource(R.string.cost_pick_icon),
        )
    }
    if (picking) {
        IconPickerDialog(
            selected = ItemIcon.of(icon),
            defaultIcon = defaultIcon,
            onDismiss = { picking = false },
            onPick = {
                onPick(it?.key)
                picking = false
            },
        )
    }
}

/** Every icon to pick from, the item's default first. Picking one closes the dialog. */
@Composable
private fun IconPickerDialog(
    selected: ItemIcon?,
    @DrawableRes defaultIcon: Int,
    onDismiss: () -> Unit,
    onPick: (ItemIcon?) -> Unit,
) {
    val choices: List<ItemIcon?> = listOf(null) + ItemIcon.entries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cost_pick_icon)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(52.dp),
                modifier = Modifier.heightIn(max = 400.dp),
            ) {
                items(choices, key = { it?.key ?: "" }) { choice ->
                    val isSelected = choice == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable(role = Role.RadioButton) { onPick(choice) },
                    ) {
                        Icon(
                            painterResource(choice?.drawable ?: defaultIcon),
                            stringResource(choice?.label ?: R.string.cost_icon_default),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_cancel)) } },
    )
}
