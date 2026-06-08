package com.zandaulion.bpdigitized.domain.model

/**
 * A single automatically-derived pattern insight, computed entirely on-device
 * from the user's reading history. Nothing is sent to a server; this is pure
 * deterministic Kotlin over List<BPReading>.
 */
enum class InsightKind {
    MORNING_HIGHER,  // morning readings average higher than evenings
    EVENING_HIGHER,  // evening readings average higher than mornings
    TREND_DOWN,      // systolic trending downward over 30 days
    TREND_UP,        // systolic trending upward over 30 days
    TIME_IN_RANGE,   // % of recent readings in the healthy range
    STREAK,          // N consecutive readings in the healthy range
    TAG_HIGHER,      // a specific tag correlates with higher systolic
    TAG_LOWER,       // a specific tag correlates with lower systolic
    WEEKEND_HIGHER,  // weekend readings average higher than weekdays
    WEEKDAY_HIGHER,  // weekday readings average higher than weekends
}

enum class InsightTone { POSITIVE, NEUTRAL, WATCH }

/**
 * @param kind     identifies the insight type and maps to its localized string template
 * @param tone     drives the indicator-dot colour in the UI (green / amber / grey)
 * @param args     pre-formatted string args passed positionally into the template
 * @param priority higher value = shown first; surfaces the most relevant insight at the top
 */
data class Insight(
    val kind: InsightKind,
    val tone: InsightTone,
    val args: List<String>,
    val priority: Int,
)
