package com.github.aw00987.tokyosandwich.cost.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.BuiltInNames
import com.github.aw00987.tokyosandwich.core.data.ledger.BuiltInTitle
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import java.util.Locale

@StringRes
private fun BuiltInTitle.nameRes(): Int = when (this) {
    BuiltInTitle.Root -> R.string.cost_root
    BuiltInTitle.Electricity -> R.string.provider_tepco
    BuiltInTitle.Gas -> R.string.provider_tokyo_gas
    BuiltInTitle.Water -> R.string.provider_tokyo_water
}

/** The item's name as shown: the user's, or, for an item that still has the app's own name, that name in the app's language. */
fun itemTitle(resources: Resources, item: CostItem): String =
    item.builtIn?.let { resources.getString(it.nameRes()) } ?: item.title

@Composable
fun itemTitle(item: CostItem): String = itemTitle(LocalResources.current, item)

/** The languages the app is in (`res/values*`; keep in step with them). */
private val APP_LANGUAGES = listOf("en", "zh", "ja")

/** The built-in items' names in every language of the app, read once from the string resources. */
fun builtInNames(context: Context): BuiltInNames {
    val resources = APP_LANGUAGES.map { language ->
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        context.createConfigurationContext(config).resources
    }
    val names = BuiltInTitle.entries.associateWith { title ->
        resources.mapTo(HashSet()) { it.getString(title.nameRes()) }
    }
    return BuiltInNames { names.getValue(it) }
}
