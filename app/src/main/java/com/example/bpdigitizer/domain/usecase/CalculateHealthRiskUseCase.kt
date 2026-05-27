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
        // Always include a disclaimer first
        add("Disclaimer: This information is based on general AHA guidelines and is not medical advice. Always consult your doctor.")

        // BP-category guidance comes first so it's always visible
        when (category) {
            BPCategory.HYPERTENSIVE_CRISIS ->
                add("According to AHA guidelines, readings above 180/120 indicate a hypertensive crisis. Please consult a healthcare professional immediately.")
            BPCategory.STAGE_2 ->
                add("According to AHA guidelines, Stage 2 hypertension often requires medical consultation.")
            BPCategory.STAGE_1 ->
                add("According to AHA guidelines, Stage 1 hypertension is often managed with lifestyle modifications under a doctor's supervision.")
            BPCategory.ELEVATED ->
                add("According to AHA guidelines, elevated blood pressure can often be managed with lifestyle modifications.")
            BPCategory.NORMAL -> Unit
        }
        // Profile-driven notes ordered by impact
        if (p.isSmoker) {
            add("Public health guidelines note that quitting smoking reduces cardiovascular risk.")
        }
        if (p.hasDiabetes) {
            add("The AHA notes that diabetes combined with high blood pressure increases cardiovascular risk.")
        }
        when (p.bmiCategory) {
            BmiCategory.OBESE, BmiCategory.OVERWEIGHT ->
                add("According to general guidelines, managing weight can help support healthy blood pressure.")
            BmiCategory.UNDERWEIGHT ->
                add("Low body weight can be associated with other health considerations. Please discuss with your doctor.")
            else -> Unit
        }
        if (p.activityLevel == ActivityLevel.SEDENTARY) {
            add("The AHA recommends regular aerobic activity to help support cardiovascular health.")
        }
        p.age?.let { age ->
            if (age >= 65) add("For older adults, blood pressure targets may vary. Please consult your doctor for personalized advice.")
        }
    }
}
