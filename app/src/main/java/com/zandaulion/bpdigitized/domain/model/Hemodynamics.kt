package com.zandaulion.bpdigitized.domain.model

import kotlin.math.roundToInt

/** Pulse Pressure = SBP − DBP (mmHg). */
fun pulsePressure(systolic: Int, diastolic: Int): Int = systolic - diastolic

/**
 * Mean Arterial Pressure ≈ (SBP + 2·DBP) / 3 (mmHg).
 * Standard resting approximation, rounded to the nearest integer.
 */
fun meanArterialPressure(systolic: Int, diastolic: Int): Int =
    ((systolic + 2.0 * diastolic) / 3.0).roundToInt()

val BPReading.pulsePressure: Int get() = pulsePressure(systolic, diastolic)
val BPReading.meanArterialPressure: Int get() = meanArterialPressure(systolic, diastolic)
