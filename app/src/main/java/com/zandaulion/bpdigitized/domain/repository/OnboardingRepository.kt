package com.zandaulion.bpdigitized.domain.repository

import kotlinx.coroutines.flow.Flow

/** Tracks whether the user has completed the first-launch intro flow. */
interface OnboardingRepository {
    /** Emits true once the intro screens have been seen (or skipped). */
    fun hasSeenOnboarding(): Flow<Boolean>

    /** Marks the intro flow as completed so it is not shown again. */
    suspend fun setSeen()
}
