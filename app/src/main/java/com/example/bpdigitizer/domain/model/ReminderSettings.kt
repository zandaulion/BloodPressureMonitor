package com.example.bpdigitizer.domain.model

data class ReminderSettings(
    val isEnabled: Boolean = false,
    /** Morning trigger: minutes since midnight (e.g. 7 * 60 + 30 = 07:30). */
    val morningMinutes: Int = 7 * 60 + 30,
    /** Evening trigger: minutes since midnight (e.g. 20 * 60 = 20:00). */
    val eveningMinutes: Int = 20 * 60,
)
