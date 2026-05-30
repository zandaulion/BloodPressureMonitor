package com.zandaulion.bpdigitized.presentation.profile

import android.content.Context

/**
 * Persists the user's preferred measurement unit system (metric vs imperial).
 * This is a display preference only — the canonical profile values are always
 * stored in metric (kg / cm). Uses SharedPreferences for a simple synchronous read.
 */
object UnitPrefs {
    private const val PREFS = "unit_prefs"
    private const val KEY   = "unit_system"

    fun get(context: Context): UnitSystem =
        runCatching {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, UnitSystem.METRIC.name) ?: UnitSystem.METRIC.name
            UnitSystem.valueOf(name)
        }.getOrDefault(UnitSystem.METRIC)

    fun set(context: Context, system: UnitSystem) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, system.name).apply()
    }
}
