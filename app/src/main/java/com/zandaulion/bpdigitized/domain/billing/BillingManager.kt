package com.zandaulion.bpdigitized.domain.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a restore attempt. Retained for the Profile "Restore Purchases"
 * action, though the directly-distributed build has nothing to restore.
 */
enum class RestoreOutcome { RESTORED, NOTHING_TO_RESTORE, ERROR }

/**
 * Billing is disabled for direct (sideloaded) distribution: there is no Play
 * Billing outside the Store, so the former "Premium" gate is permanently
 * unlocked — every install gets the full app, including camera scanning.
 * RevenueCat is intentionally kept out of the runtime path.
 */
@Singleton
class BillingManager @Inject constructor() {
    private val _isProUser = MutableStateFlow(true)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    /** No-op: nothing to configure when billing is disabled. */
    fun initialize(apiKey: String) = Unit

    /** Everything is already unlocked, so a restore is trivially a success. */
    fun restorePurchases(onResult: (RestoreOutcome) -> Unit) {
        onResult(RestoreOutcome.RESTORED)
    }
}
