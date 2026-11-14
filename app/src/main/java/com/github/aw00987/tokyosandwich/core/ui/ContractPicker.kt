package com.github.aw00987.tokyosandwich.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ContractInfo

/** The contract section of a provider's settings page: a heading and one radio row per contract. */
@Composable
fun ContractPicker(choice: ContractChoice, onSelect: (id: String) -> Unit) {
    SectionTitle(stringResource(R.string.contract))
    Column(Modifier.selectableGroup()) {
        choice.contracts.forEach { contract ->
            ContractRow(
                contract,
                selected = contract.id == choice.selectedId
            ) { onSelect(contract.id) }
        }
    }
}

@Composable
private fun ContractRow(contract: ContractInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(contract.title, style = MaterialTheme.typography.bodyLarge)
            if (contract.subtitle.isNotEmpty()) {
                Text(
                    contract.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
