package com.example.bpdigitizer.domain.model

data class UserProfile(
    val birthYear: Int? = null,
    val biologicalSex: BiologicalSex = BiologicalSex.PREFER_NOT_TO_SAY,
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val isSmoker: Boolean = false,
    val hasDiabetes: Boolean = false,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE,
) {
    /** Age derived from [birthYear] and the current calendar year. Null if birth year is unset. */
    val age: Int?
        get() = birthYear?.let { java.time.Year.now().value - it }

    /** Null until both weight and height are provided. */
    val bmi: Float?
        get() {
            val w = weightKg ?: return null
            val h = heightCm ?: return null
            if (h <= 0f) return null
            val hm = h / 100f
            return w / (hm * hm)
        }

    val bmiCategory: BmiCategory?
        get() = bmi?.let {
            when {
                it < 18.5f -> BmiCategory.UNDERWEIGHT
                it < 25.0f -> BmiCategory.NORMAL
                it < 30.0f -> BmiCategory.OVERWEIGHT
                else       -> BmiCategory.OBESE
            }
        }

    /** True when at least birth year is filled — minimum for a meaningful risk tweak. */
    val isComplete: Boolean get() = birthYear != null
}

enum class BiologicalSex(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    PREFER_NOT_TO_SAY("N/A"),
}

enum class ActivityLevel(val label: String, val description: String) {
    SEDENTARY("Sedentary", "Little or no exercise"),
    LIGHTLY_ACTIVE("Light", "Light exercise 1–3 days/wk"),
    MODERATELY_ACTIVE("Moderate", "Moderate exercise 3–5 days/wk"),
    VERY_ACTIVE("Very active", "Hard exercise 6–7 days/wk"),
}

enum class BmiCategory(val label: String) {
    UNDERWEIGHT("Underweight"),
    NORMAL("Normal"),
    OVERWEIGHT("Overweight"),
    OBESE("Obese"),
}
