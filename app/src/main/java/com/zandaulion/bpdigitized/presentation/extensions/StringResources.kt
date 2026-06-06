package com.zandaulion.bpdigitized.presentation.extensions

import androidx.annotation.StringRes
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.ActivityLevel
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.domain.model.BmiCategory
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.model.CardiovascularRisk
import com.zandaulion.bpdigitized.domain.model.HealthNote
import com.zandaulion.bpdigitized.presentation.dashboard.ChartRange
import com.zandaulion.bpdigitized.presentation.dashboard.ExportType

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

@StringRes fun HealthNote.stringRes(): Int = when (this) {
    HealthNote.DISCLAIMER      -> R.string.risk_note_disclaimer
    HealthNote.BP_CRISIS       -> R.string.risk_note_bp_crisis
    HealthNote.BP_STAGE2       -> R.string.risk_note_bp_stage2
    HealthNote.BP_STAGE1       -> R.string.risk_note_bp_stage1
    HealthNote.BP_ELEVATED     -> R.string.risk_note_bp_elevated
    HealthNote.SMOKER          -> R.string.risk_note_smoker
    HealthNote.DIABETES        -> R.string.risk_note_diabetes
    HealthNote.BMI_ELEVATED    -> R.string.risk_note_bmi_elevated
    HealthNote.BMI_UNDERWEIGHT -> R.string.risk_note_bmi_underweight
    HealthNote.SEDENTARY       -> R.string.risk_note_sedentary
    HealthNote.OLDER_ADULT     -> R.string.risk_note_older_adult
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

@StringRes fun ExportType.labelRes(): Int = when (this) {
    ExportType.JSON -> R.string.export_type_json
    ExportType.CSV  -> R.string.export_type_csv
    ExportType.PDF  -> R.string.export_type_pdf
}

@StringRes fun BpTag.labelRes(): Int = when (this) {
    BpTag.BEFORE_MEDICATION -> R.string.tag_before_medication
    BpTag.AFTER_MEDICATION  -> R.string.tag_after_medication
    BpTag.CAFFEINE          -> R.string.tag_caffeine
    BpTag.ALCOHOL           -> R.string.tag_alcohol
    BpTag.SALTY_MEAL        -> R.string.tag_salty_meal
    BpTag.EXERCISE          -> R.string.tag_exercise
    BpTag.STRESS            -> R.string.tag_stress
    BpTag.POOR_SLEEP        -> R.string.tag_poor_sleep
    BpTag.RESTING           -> R.string.tag_resting
    BpTag.ON_WAKING         -> R.string.tag_on_waking
}
