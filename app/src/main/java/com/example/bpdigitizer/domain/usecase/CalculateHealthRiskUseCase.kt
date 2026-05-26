package com.example.bpdigitizer.domain.usecase

import com.example.bpdigitizer.domain.model.ActivityLevel
import com.example.bpdigitizer.domain.model.BiologicalSex
import com.example.bpdigitizer.domain.model.BPCategory
import com.example.bpdigitizer.domain.model.BmiCategory
import com.example.bpdigitizer.domain.model.CardiovascularRisk
import com.example.bpdigitizer.domain.model.HealthAssessment
import com.example.bpdigitizer.domain.model.UserProfile
import javax.inject.Inject

/**
 * Combines a BP category with the user's health profile to produce a
 * personalised [HealthAssessment].
 *
 * Risk-factor scoring is a simplified adaptation of the Framingham /
 * ACC-AHA 2019 pooled-cohort framework:
 *   – BP severity, age, sex, BMI, smoking, diabetes, and activity level
 *     all contribute points.
 *   – The final score is mapped to LOW / MODERATE / HIGH / VERY_HIGH.
 *
 * This is for informational purposes only and does not replace medical advice.
 */
class CalculateHealthRiskUseCase @Inject constructor() {

    operator fun invoke(category: BPCategory, profile: UserProfile): HealthAssessment {
        val pts = riskPoints(category, profile)
        val risk = mapRisk(category, pts)
        return HealthAssessment(
            bpCategory = category,
            cardiovascularRisk = risk,
            bmi = profile.bmi,
            bmiCategory = profile.bmiCategory,
            contextNotes = buildNotes(category, profile),
            profileComplete = profile.isComplete,
        )
    }

    // ── scoring ──────────────────────────────────────────────────────────────

    private fun riskPoints(category: BPCategory, p: UserProfile): Int {
        var pts = bpPoints(category)
        p.age?.let { age ->
            pts += when {
                age >= 75 -> 3
                age >= 65 -> 2
                age >= 55 -> 1
                else      -> 0
            }
        }
        if (p.biologicalSex == BiologicalSex.MALE) pts += 1
        pts += when (p.bmiCategory) {
            BmiCategory.OBESE      -> 2
            BmiCategory.OVERWEIGHT -> 1
            else                   -> 0
        }
        if (p.isSmoker)   pts += 2
        if (p.hasDiabetes) pts += 2
        if (p.activityLevel == ActivityLevel.SEDENTARY) pts += 1
        return pts
    }

    private fun bpPoints(c: BPCategory) = when (c) {
        BPCategory.NORMAL             -> 0
        BPCategory.ELEVATED           -> 1
        BPCategory.STAGE_1            -> 2
        BPCategory.STAGE_2            -> 3
        BPCategory.HYPERTENSIVE_CRISIS -> 5
    }

    private fun mapRisk(category: BPCategory, pts: Int): CardiovascularRisk = when {
        category == BPCategory.HYPERTENSIVE_CRISIS         -> CardiovascularRisk.VERY_HIGH
        category == BPCategory.STAGE_2   && pts >= 6       -> CardiovascularRisk.VERY_HIGH
        category == BPCategory.STAGE_2                     -> CardiovascularRisk.HIGH
        category == BPCategory.STAGE_1   && pts >= 7       -> CardiovascularRisk.HIGH
        category == BPCategory.STAGE_1                     -> CardiovascularRisk.MODERATE
        category == BPCategory.ELEVATED  && pts >= 5       -> CardiovascularRisk.MODERATE
        pts >= 8                                            -> CardiovascularRisk.MODERATE
        else                                                -> CardiovascularRisk.LOW
    }

    // ── contextual notes ─────────────────────────────────────────────────────

    private fun buildNotes(category: BPCategory, p: UserProfile): List<String> = buildList {
        // BP-category guidance comes first so it's always visible
        when (category) {
            BPCategory.HYPERTENSIVE_CRISIS ->
                add("Seek medical attention promptly if this reading is confirmed on a retake.")
            BPCategory.STAGE_2 ->
                add("Stage 2 hypertension often requires medication alongside lifestyle changes.")
            BPCategory.STAGE_1 ->
                add("Lifestyle changes (DASH diet, exercise, reduced sodium) are very effective at this stage.")
            BPCategory.ELEVATED ->
                add("Reducing sodium and regular aerobic exercise can bring elevated BP back to normal.")
            BPCategory.NORMAL -> Unit
        }
        // Profile-driven notes ordered by impact
        if (p.isSmoker) {
            add("Quitting smoking is the single highest-impact change for cardiovascular risk.")
        }
        if (p.hasDiabetes) {
            add("Diabetes and high BP together significantly raise cardiovascular risk — close monitoring is important.")
        }
        when (p.bmiCategory) {
            BmiCategory.OBESE ->
                add("Reducing BMI toward normal range can lower systolic BP by 5–20 mmHg.")
            BmiCategory.OVERWEIGHT ->
                add("Even 5–10 % weight loss can meaningfully lower BP.")
            BmiCategory.UNDERWEIGHT ->
                add("Low body weight may indicate other health concerns worth discussing with a doctor.")
            else -> Unit
        }
        if (p.activityLevel == ActivityLevel.SEDENTARY) {
            add("Adding 30 min of moderate aerobic activity most days can lower systolic BP by 4–9 mmHg.")
        }
        p.age?.let { age ->
            if (age >= 65) add("For adults over 65 a systolic target below 130 mmHg is often recommended.")
        }
    }
}
