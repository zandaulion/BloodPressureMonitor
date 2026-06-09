package com.zandaulion.bpdigitized

/**
 * Compile-time feature switches.
 *
 * [CLINICAL] gates every medically-framed surface so the published app reads as a
 * neutral fitness blood-pressure logger rather than a hypertension-management tool:
 *
 *  - BP categories show neutral zone names (Optimal/Elevated/High/Very High/Extreme)
 *    instead of AHA staging (Stage 1/2 Hypertension, Hypertensive Crisis).
 *  - The cardiovascular Risk Assessment card and its guidance notes are hidden.
 *  - The diabetes and smoker health-factor inputs are hidden.
 *  - Medication-timing context tags are removed from the tag pickers.
 *  - The MAP / Pulse-Pressure readouts are hidden.
 *
 * The clinical code stays compiled (gated by a runtime `if`, not deleted), so flipping
 * CLINICAL_MODE to true in app/build.gradle.kts restores the full clinical build.
 */
object Features {
    val CLINICAL: Boolean = BuildConfig.CLINICAL_MODE
}
