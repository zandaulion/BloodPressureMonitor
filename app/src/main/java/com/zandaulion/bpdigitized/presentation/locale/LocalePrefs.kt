package com.zandaulion.bpdigitized.presentation.locale

import android.content.Context

/**
 * Thin SharedPreferences wrapper for the user-chosen locale code.
 * Uses SharedPreferences (not DataStore) so it can be read synchronously
 * in [android.app.Activity.attachBaseContext] before Hilt is available.
 *
 * [code] is an IETF language tag ("es", "fr", "pt", "de", "ja") or an empty
 * string to follow the device system locale.
 */
object LocalePrefs {
    private const val PREFS = "locale_prefs"
    private const val KEY   = "locale_code"

    fun getLocale(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""

    fun setLocale(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()
    }
}
