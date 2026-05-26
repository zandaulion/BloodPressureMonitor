package com.example.bpdigitizer.domain.usecase

import com.example.bpdigitizer.domain.model.BPCategory
import javax.inject.Inject

class CalculateBPZoneUseCase @Inject constructor() {
    operator fun invoke(systolic: Int, diastolic: Int): BPCategory {
        // AHA guidelines. Crisis takes precedence.
        if (systolic > 180 || diastolic > 120) return BPCategory.HYPERTENSIVE_CRISIS
        if (systolic >= 140 || diastolic >= 90) return BPCategory.STAGE_2
        if (systolic in 130..139 || diastolic in 80..89) return BPCategory.STAGE_1
        if (systolic in 120..129 && diastolic < 80) return BPCategory.ELEVATED
        return BPCategory.NORMAL
    }
}
