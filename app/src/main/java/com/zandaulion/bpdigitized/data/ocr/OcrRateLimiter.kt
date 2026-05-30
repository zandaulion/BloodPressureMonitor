package com.zandaulion.bpdigitized.data.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrRateLimiter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val PREFS = "ocr_rate_limiter_prefs"
    private val KEY_DATE = "last_scan_date"
    private val KEY_COUNT = "scan_count_today"
    
    // Set to a high but safe limit to prevent abuse while allowing normal usage.
    private val DAILY_LIMIT = 200

    @Synchronized
    fun tryConsume(): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastDate = prefs.getString(KEY_DATE, "")
        
        var count = if (lastDate == today) {
            prefs.getInt(KEY_COUNT, 0)
        } else {
            0
        }

        if (count >= DAILY_LIMIT) {
            return false
        }

        count++
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_COUNT, count)
            .apply()
            
        return true
    }
}
