package com.zandaulion.bpdigitized.domain.model

data class HealthAssessment(
    val bpCategory: BPCategory,
    val cardiovascularRisk: CardiovascularRisk,
    val bmi: Float?,
    val bmiCategory: BmiCategory?,
    /** Personalised, actionable notes derived from the combined BP + profile data. */
    val contextNotes: List<HealthNote>,
    /** False when the profile has no age set; prompts the user to complete it. */
    val profileComplete: Boolean,
)

enum class CardiovascularRisk {
    LOW,
    MODERATE,
    HIGH,
    VERY_HIGH,
}
