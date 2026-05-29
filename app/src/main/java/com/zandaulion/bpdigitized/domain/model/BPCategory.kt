package com.zandaulion.bpdigitized.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BPCategory {
    NORMAL,
    ELEVATED,
    STAGE_1,
    STAGE_2,
    HYPERTENSIVE_CRISIS,
}
