package com.github.aw00987.tokyosandwich.core

import android.content.Context
import androidx.annotation.StringRes

/**
 * The app's string resources, for the data layer: a provider writes each record's note (a plain string) while it
 * processes a sync, so it needs the texts without depending on a screen.
 */
interface Texts {
    fun get(@StringRes id: Int, vararg args: Any): String
}

class ResourceTexts(context: Context) : Texts {
    private val resources = context.applicationContext.resources

    override fun get(id: Int, vararg args: Any): String = resources.getString(id, *args)
}
