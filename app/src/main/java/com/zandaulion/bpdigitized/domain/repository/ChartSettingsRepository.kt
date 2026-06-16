package com.zandaulion.bpdigitized.domain.repository

import kotlinx.coroutines.flow.Flow

/** User preferences that affect how the trend chart (and PDF chart) is rendered. */
interface ChartSettingsRepository {
    /**
     * Emits true when consecutive readings taken in one sitting should be averaged into
     * a single smoothed point on the chart. Defaults to true.
     */
    fun observeSmoothBursts(): Flow<Boolean>

    suspend fun setSmoothBursts(enabled: Boolean)

    /**
     * Emits the persisted dashboard chart-mode token (a `ChartMode` enum name, e.g.
     * "TREND" or "SCATTER"). Blank when the user has never chosen one; the caller maps
     * blank/unrecognised tokens to the default mode.
     */
    fun observeChartMode(): Flow<String>

    suspend fun setChartMode(mode: String)
}
