package com.example.bpdigitizer.domain.model

data class HealthAssessment(
    val bpCategory: BPCategory,
    val cardiovascularRisk: CardiovascularRisk,
    val bmi: Float?,
    val bmiCategory: BmiCategory?,
    /** Personalised, actionable notes derived from the combined BP + profile data. */
    val contextNotes: List<String>,
    /** False when the profile has no age set; prompts the user to complete it. */
    val profileComplete: Boolean,
)

enum class CardiovascularRisk(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
    VERY_HIGH("Very High"),
}
