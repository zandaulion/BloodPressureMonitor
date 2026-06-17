package com.zandaulion.bpdigitized.domain.billing

import android.app.Application
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.restorePurchasesWith
import com.revenuecat.purchases.getCustomerInfoWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a restore attempt. A restore that completes without finding an
 * eligible purchase is [NOTHING_TO_RESTORE] — a normal result, NOT [ERROR] — so
 * the UI can tell the user "no previous purchase found" instead of "failed".
 */
enum class RestoreOutcome { RESTORED, NOTHING_TO_RESTORE, ERROR }

@Singleton
class BillingManager @Inject constructor(
    private val application: Application
) {
    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    fun initialize(apiKey: String) {
        Purchases.debugLogsEnabled = true
        Purchases.configure(PurchasesConfiguration.Builder(application, apiKey).build())
        
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
            updateProStatus(customerInfo)
        }
        
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error -> Log.e("BillingManager", "Failed to get customer info: ${error.message}") },
            onSuccess = { customerInfo -> updateProStatus(customerInfo) }
        )
    }

    private fun updateProStatus(customerInfo: CustomerInfo) {
        // Assume the entitlement is named "pro" in RevenueCat dashboard
        val isPro = customerInfo.entitlements["pro"]?.isActive == true
        _isProUser.value = isPro
    }

    fun restorePurchases(onResult: (RestoreOutcome) -> Unit) {
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { error ->
                Log.e("BillingManager", "Restore error: ${error.message}")
                onResult(RestoreOutcome.ERROR)
            },
            onSuccess = { customerInfo ->
                updateProStatus(customerInfo)
                // Restore succeeded; whether it actually granted Pro decides the message.
                onResult(
                    if (_isProUser.value) RestoreOutcome.RESTORED
                    else RestoreOutcome.NOTHING_TO_RESTORE
                )
            }
        )
    }
}
