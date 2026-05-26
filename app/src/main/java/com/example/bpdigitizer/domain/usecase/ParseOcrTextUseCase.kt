package com.example.bpdigitizer.domain.usecase

import android.util.Log
import javax.inject.Inject

private const val TAG = "BpOcr.Parse"

data class ParsedReading(
    val systolic: Int?,
    val diastolic: Int?,
    val pulse: Int?,
)

/**
 * Parses a plain-text string produced by Tesseract (digit-whitelist mode) into
 * a [ParsedReading].
 *
 * Tesseract with PSM_SINGLE_BLOCK outputs the recognised digits in reading
 * order (top → bottom on screen), so the first number is SYS, the second DIA,
 * and the third PUL — exactly how a blood-pressure monitor lays out its display.
 *
 * If positional ordering yields an impossible SYS < DIA result (e.g. due to a
 * missed digit), we fall back to magnitude-descending order.
 */
class ParseOcrTextUseCase @Inject constructor() {

    operator fun invoke(ocrText: String): ParsedReading {
        Log.d(TAG, "─── invoke  text=\"${ocrText.take(80).replace('\n', '↵')}\"")

        val numbers = NUMBER_REGEX.findAll(ocrText)
            .map { it.value.toInt() }
            .filter { it in MIN_PLAUSIBLE..MAX_PLAUSIBLE }
            .toList()

        Log.d(TAG, "  numbers in range: $numbers")

        if (numbers.isEmpty()) {
            Log.d(TAG, "  → no usable numbers")
            return ParsedReading(null, null, null)
        }

        // ── Positional path (top-to-bottom = SYS, DIA, PUL) ──────────────────
        val sys   = numbers.getOrNull(0)
        val dia   = numbers.getOrNull(1)
        val pulse = numbers.getOrNull(2)

        if (sys != null && dia != null && sys >= dia) {
            Log.d(TAG, "  ✓ positional: sys=$sys  dia=$dia  pulse=$pulse")
            return ParsedReading(sys, dia, pulse)
        }

        // ── Magnitude fallback ────────────────────────────────────────────────
        val desc = numbers.sortedDescending()
        Log.d(TAG, "  ⚠ magnitude fallback: $desc")
        return ParsedReading(
            systolic  = desc.getOrNull(0),
            diastolic = desc.getOrNull(1),
            pulse     = desc.getOrNull(2),
        )
    }

    private companion object {
        val NUMBER_REGEX = Regex("\\d{2,3}")
        const val MIN_PLAUSIBLE = 30
        const val MAX_PLAUSIBLE = 250
    }
}
