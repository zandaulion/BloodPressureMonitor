package com.zandaulion.bpdigitized.presentation.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    /**
     * Returns a context whose resources are resolved in [code].
     * If [code] is blank, the original [context] is returned unchanged
     * (system locale applies).
     */
    fun applyLocale(context: Context, code: String): Context {
        if (code.isBlank()) return context
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
