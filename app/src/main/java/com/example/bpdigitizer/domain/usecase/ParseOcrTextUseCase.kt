package com.example.bpdigitizer.domain.usecase

import com.google.mlkit.vision.text.Text
import javax.inject.Inject

data class ParsedReading(
    val systolic: Int?,
    val diastolic: Int?,
    val pulse: Int?,
)

class ParseOcrTextUseCase @Inject constructor() {

    operator fun invoke(text: Text): ParsedReading {
        val tokens = extractNumericTokens(text)
        if (tokens.isEmpty()) return ParsedReading(null, null, null)

        val rowValues = groupByRows(tokens).map { row -> row.maxOf { it.value } }
        val sys = rowValues.getOrNull(0)
        val dia = rowValues.getOrNull(1)
        val pulse = rowValues.getOrNull(2)

        // Primary path: Y-axis ordering yielded a valid Sys > Dia.
        if (sys != null && dia != null && sys > dia) {
            return ParsedReading(sys, dia, pulse)
        }

        // Fallback: magnitude sort. Largest = Sys, next = Dia, smallest = Pulse.
        val distinctSortedDesc = tokens.map { it.value }.distinct().sortedDescending()
        return ParsedReading(
            systolic = distinctSortedDesc.getOrNull(0),
            diastolic = distinctSortedDesc.getOrNull(1),
            pulse = distinctSortedDesc.getOrNull(2),
        )
    }

    private fun extractNumericTokens(text: Text): List<NumberToken> {
        val raw = mutableListOf<NumberToken>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val normalized = normalizeSevenSegment(element.text)
                    NUMBER_REGEX.findAll(normalized).forEach { match ->
                        val value = match.value.toInt()
                        if (value in MIN_PLAUSIBLE..MAX_PLAUSIBLE) {
                            raw += NumberToken(value = value, top = box.top, height = box.height())
                        }
                    }
                }
            }
        }
        if (raw.isEmpty()) return raw
        // Filter to only "large" digits — drops date/time headers and unit labels
        // that share the screen with the main BP/pulse readout.
        val maxHeight = raw.maxOf { it.height }
        val minHeight = (maxHeight * MIN_HEIGHT_RATIO).toInt()
        return raw.filter { it.height >= minHeight }
    }

    private fun groupByRows(tokens: List<NumberToken>): List<List<NumberToken>> {
        val sortedByTop = tokens.sortedBy { it.top }
        val avgHeight = tokens.sumOf { it.height }.toDouble() / tokens.size
        val threshold = (avgHeight * ROW_THRESHOLD_RATIO).toInt().coerceAtLeast(MIN_THRESHOLD_PX)

        val groups = mutableListOf<MutableList<NumberToken>>()
        for (token in sortedByTop) {
            val current = groups.lastOrNull()
            if (current != null && (token.top - current.first().top) < threshold) {
                current += token
            } else {
                groups += mutableListOf(token)
            }
        }
        return groups
    }

    /**
     * Maps characters ML Kit commonly mistakes for digits when looking at a
     * 7-segment LCD into their intended digit. The "1" digit on a 7-segment is
     * just the two right-hand vertical bars, which is shape-identical to a
     * capital "I", so ML Kit often returns "I20" for "120" etc.
     */
    private fun normalizeSevenSegment(s: String): String = s.map { c ->
        when (c) {
            'I', 'l', '|' -> '1'
            'O' -> '0'
            'B' -> '8'
            else -> c
        }
    }.joinToString("")

    private data class NumberToken(val value: Int, val top: Int, val height: Int)

    private companion object {
        val NUMBER_REGEX = Regex("\\d{2,3}")
        const val MIN_PLAUSIBLE = 30
        const val MAX_PLAUSIBLE = 250
        const val ROW_THRESHOLD_RATIO = 0.6
        const val MIN_THRESHOLD_PX = 8
        const val MIN_HEIGHT_RATIO = 0.5
    }
}
