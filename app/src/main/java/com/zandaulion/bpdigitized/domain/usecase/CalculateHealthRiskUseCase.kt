package com.zandaulion.bpdigitized.domain.usecase

import com.zandaulion.bpdigitized.domain.model.ActivityLevel
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BmiCategory
import com.zandaulion.bpdigitized.domain.model.CardiovascularRisk
import com.zandaulion.bpdigitized.domain.model.HealthAssessment
import com.zandaulion.bpdigitized.domain.model.HealthNote
import com.zandaulion.bpdigitized.domain.model.UserProfile
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

    private fun buildNotes(category: BPCategory, p: UserProfile): List<HealthNote> = buildList {
        add(HealthNote.DISCLAIMER)

        when (category) {
            BPCategory.HYPERTENSIVE_CRISIS -> add(HealthNote.BP_CRISIS)
            BPCategory.STAGE_2             -> add(HealthNote.BP_STAGE2)
            BPCategory.STAGE_1             -> add(HealthNote.BP_STAGE1)
            BPCategory.ELEVATED            -> add(HealthNote.BP_ELEVATED)
            BPCategory.NORMAL              -> Unit
        }
        if (p.isSmoker)    add(HealthNote.SMOKER)
        if (p.hasDiabetes) add(HealthNote.DIABETES)
        when (p.bmiCategory) {
            BmiCategory.OBESE, BmiCategory.OVERWEIGHT -> add(HealthNote.BMI_ELEVATED)
            BmiCategory.UNDERWEIGHT                   -> add(HealthNote.BMI_UNDERWEIGHT)
            else                                      -> Unit
        }
        if (p.activityLevel == ActivityLevel.SEDENTARY) add(HealthNote.SEDENTARY)
        p.age?.let { age -> if (age >= 65) add(HealthNote.OLDER_ADULT) }
    }
}
