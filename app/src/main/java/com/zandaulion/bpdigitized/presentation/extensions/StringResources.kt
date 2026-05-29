package com.zandaulion.bpdigitized.presentation.extensions

import androidx.annotation.StringRes
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.ActivityLevel
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.domain.model.BmiCategory
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.CardiovascularRisk
import com.zandaulion.bpdigitized.presentation.dashboard.ChartRange

// ── Domain enum → string resource ID mappings ─────────────────────────────────
// Keep the domain layer free of Android imports. All UI-facing string lookups
// live here so a single file needs updating when new locales are added.

@StringRes fun BiologicalSex.labelRes(): Int = when (this) {
    BiologicalSex.MALE              -> R.string.sex_male
    BiologicalSex.FEMALE            -> R.string.sex_female
    BiologicalSex.PREFER_NOT_TO_SAY -> R.string.sex_prefer_not_to_say
}

@StringRes fun ActivityLevel.labelRes(): Int = when (this) {
    ActivityLevel.SEDENTARY         -> R.string.activity_sedentary
    ActivityLevel.LIGHTLY_ACTIVE    -> R.string.activity_lightly_active
    ActivityLevel.MODERATELY_ACTIVE -> R.string.activity_moderately_active
    ActivityLevel.VERY_ACTIVE       -> R.string.activity_very_active
}

@StringRes fun ActivityLevel.descriptionRes(): Int = when (this) {
    ActivityLevel.SEDENTARY         -> R.string.activity_desc_sedentary
    ActivityLevel.LIGHTLY_ACTIVE    -> R.string.activity_desc_lightly_active
    ActivityLevel.MODERATELY_ACTIVE -> R.string.activity_desc_moderately_active
    ActivityLevel.VERY_ACTIVE       -> R.string.activity_desc_very_active
}

@StringRes fun BmiCategory.labelRes(): Int = when (this) {
    BmiCategory.UNDERWEIGHT -> R.string.bmi_underweight
    BmiCategory.NORMAL      -> R.string.bmi_normal
    BmiCategory.OVERWEIGHT  -> R.string.bmi_overweight
    BmiCategory.OBESE       -> R.string.bmi_obese
}

@StringRes fun CardiovascularRisk.labelRes(): Int = when (this) {
    CardiovascularRisk.LOW       -> R.string.cv_risk_low
    CardiovascularRisk.MODERATE  -> R.string.cv_risk_moderate
    CardiovascularRisk.HIGH      -> R.string.cv_risk_high
    CardiovascularRisk.VERY_HIGH -> R.string.cv_risk_very_high
}

/** Short badge label shown inside the coloured pill (e.g. "Normal", "Stage 1"). */
@StringRes fun BPCategory.badgeLabelRes(): Int = when (this) {
    BPCategory.NORMAL              -> R.string.bp_category_normal
    BPCategory.ELEVATED            -> R.string.bp_category_elevated
    BPCategory.STAGE_1             -> R.string.bp_category_stage1
    BPCategory.STAGE_2             -> R.string.bp_category_stage2
    BPCategory.HYPERTENSIVE_CRISIS -> R.string.bp_category_crisis
}

/** Full display label used in the Health Risk card (e.g. "Stage 1 Hypertension"). */
@StringRes fun BPCategory.displayLabelRes(): Int = when (this) {
    BPCategory.NORMAL              -> R.string.bp_display_normal
    BPCategory.ELEVATED            -> R.string.bp_display_elevated
    BPCategory.STAGE_1             -> R.string.bp_display_stage1
    BPCategory.STAGE_2             -> R.string.bp_display_stage2
    BPCategory.HYPERTENSIVE_CRISIS -> R.string.bp_display_crisis
}

@StringRes fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.SEVEN_DAYS   -> R.string.chart_range_7d
    ChartRange.THIRTY_DAYS  -> R.string.chart_range_30d
    ChartRange.NINETY_DAYS  -> R.string.chart_range_90d
    ChartRange.ALL          -> R.string.chart_range_all
}
