package com.irfan.prosantinibash.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/** Shared resource context also gives workers and PDF exports the saved app language. */
object AppText {
    @Volatile private var context: Context? = null

    fun configure(application: Context, tag: String) {
        DisplayLocale.locale = if (tag == "bn") Locale.forLanguageTag("bn-BD") else Locale.ENGLISH
        val configuration = Configuration(application.resources.configuration)
        configuration.setLocale(DisplayLocale.locale)
        context = application.createConfigurationContext(configuration)
    }

    fun get(@StringRes id: Int, vararg arguments: Any?): String {
        val resources = checkNotNull(context) { "Language resources have not been initialized" }.resources
        val values = arguments.map { if (it is Number) DisplayLocale.digits(it.toString()) else it }.toTypedArray()
        return resources.getString(id, *values)
    }
}
