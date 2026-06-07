# Implementation Plan — On-Device Insights Engine

**Goal:** Surface automatic, plain-language insights from a user's readings — time-of-day patterns, trends, and (once tags exist) trigger correlations — to match the "AI insights" tier the market leaders advertise (e.g., Cardilog Intelligence).

**Market gap addressed:** "No AI-driven pattern mining" (market-comparison §3.7).

> **Privacy by design — explicit constraint.** Per your direction, **nothing leaves the device.** This is a deterministic, on-device statistics engine in pure Kotlin — **no cloud, no model download, no network calls.** It reinforces the app's privacy positioning rather than compromising it. (This is deliberately *not* the LLM-export idea, which you've excluded.)

---

## Scope

**In scope**
- A `GenerateInsightsUseCase` computing a ranked list of insights locally.
- An `InsightsCard` on the dashboard showing the top few, with "show more".
- Insight types that work today (timestamps + values), plus tag-correlation insights gated on the Tags plan.

**Out of scope**
- Any ML model or cloud inference.
- Push-notifying insights (could be a later, opt-in extension).

---

## Insight catalog (all local, all deterministic)

| Insight | Needs | Example output |
|---|---|---|
| Morning vs evening | timestamps | "Your morning readings average **8/5 mmHg higher** than evenings." |
| 30-day trend | timestamps | "Systolic is trending **down ~4 mmHg** over the last 30 days." |
| Time-in-range | values | "**72%** of the last 30 readings were in the normal range." |
| Consistency streak | values | "**12** readings in a row in a healthy range — nice." |
| Tag correlation | **Tags plan** | "Readings tagged **'salty meal'** average **+9 mmHg** systolic." |
| Weekday vs weekend | timestamps | "Weekends run **~6 mmHg** higher than weekdays." |

Each insight only appears when it clears **minimum-sample** and **minimum-effect** thresholds, so we never show noise (e.g., need ≥5 readings per side and ≥3 mmHg difference).

---

## Step-by-step

### Step 1 — Domain model
```kotlin
// domain/model/Insight.kt
enum class InsightKind { MORNING_EVENING, TREND_30D, TIME_IN_RANGE, STREAK, TAG_CORRELATION, WEEKDAY_WEEKEND }

data class Insight(
    val kind: InsightKind,
    val severity: InsightTone,            // POSITIVE / NEUTRAL / WATCH → tints the row
    val args: List<String>,               // numbers formatted by the UI into a localized template
    val priority: Int,                    // for ranking
)
enum class InsightTone { POSITIVE, NEUTRAL, WATCH }
```

### Step 2 — The engine
`domain/usecase/GenerateInsightsUseCase.kt` — pure functions over `List<BPReading>` (+ optional `UserProfile`). Sketch:
```kotlin
class GenerateInsightsUseCase @Inject constructor() {
    operator fun invoke(readings: List<BPReading>): List<Insight> {
        val recent = readings.filter { it.timestamp >= cutoff(30) }
        return buildList {
            morningVsEvening(recent)?.let(::add)
            trend30d(recent)?.let(::add)
            timeInRange(recent)?.let(::add)
            streak(readings)?.let(::add)
            weekdayVsWeekend(recent)?.let(::add)
            // tagCorrelations(recent) once the Tags plan lands
        }.sortedByDescending { it.priority }.take(MAX_INSIGHTS)
    }
}
```
- **Morning/evening:** split by hour-of-day (e.g., <12:00 vs ≥12:00), compare mean systolic/diastolic; emit if |Δ| ≥ 3 mmHg and each side ≥ 5 readings.
- **Trend:** reuse the least-squares helper from the **PDF Visuals plan** (`linregress` on day-offset); emit if |slope×30| ≥ 4 mmHg.
- **Time-in-range / streak:** count against `BPCategory.NORMAL` (or the selected guideline's normal class, if the Guideline plan landed).
- All thresholds in a `companion object` so they're easy to tune.

### Step 3 — Wire into the dashboard
`DashboardViewModel` already combines the readings flow. Add `insights: List<Insight>` to its UI state (compute via the use case whenever readings change). No new repository.

### Step 4 — UI: InsightsCard
New `presentation/components/InsightsCard.kt`, styled like [HealthRiskCard.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/components/HealthRiskCard.kt) (surfaceVariant card, title, list, "show more"). Place it on the dashboard below the chart / risk card. Each row: a tone-tinted dot + the localized sentence. Hide the whole card when there are no qualifying insights.

### Step 5 — Strings + localization
- One **template** per `InsightKind` with positional args, e.g.:
  `<string name="insight_morning_evening">Your morning readings average %1$s/%2$s mmHg higher than evenings.</string>`
- Add `InsightKind.templateRes()` to [StringResources.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/extensions/StringResources.kt); the card formats `insight.args` into it.
- Replicate across all 12 locales. (Mind plural/grammar; keep templates simple and number-driven to translate cleanly.)

### Step 6 — Build & verify
```
./gradlew :app:assembleDebug
```
- [ ] With a rich dataset, the card shows 2–3 sensible insights; numbers match a hand check.
- [ ] With **few** readings, the card hides (thresholds respected) — no noise, no "NaN".
- [ ] Switching guideline (if present) re-bases time-in-range correctly.
- [ ] Airplane mode: insights still compute (proves on-device). 🔒
- [ ] Templates localized.

---

## Dependencies & phasing
- **Ships standalone today** with timestamp/value insights (morning/evening, trend, time-in-range, streak, weekday/weekend).
- **Gains tag-correlation insights** automatically once the **Contextual Tags** plan lands — add `tagCorrelations()` then.
- **Shares** the `linregress` helper with the **PDF Visuals** plan — factor it into one domain util if both are built (e.g., `domain/usecase/Statistics.kt`).

## Effort & risk
- **Effort:** ~1–1.5 days for the standalone version (engine + card + localized templates); +half a day for tag correlations later.
- **Risk:** low. Pure, testable functions; worst case an insight is uninteresting — tune thresholds. Great candidate for unit tests (deterministic in → expected out).

## Commit slicing
1. `feat: add Insight model + on-device insights engine (timestamp/value insights)`
2. `feat: insights card on dashboard`
3. `feat: localize insight templates`
4. (after Tags) `feat: add tag-correlation insights`
