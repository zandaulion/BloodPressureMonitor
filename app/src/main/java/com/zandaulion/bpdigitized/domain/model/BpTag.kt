package com.zandaulion.bpdigitized.domain.model

import kotlinx.serialization.Serializable

/**
 * Structured, multi-select context attached to a reading (what might have
 * influenced it). A fixed, localizable set — stored per reading as a
 * comma-joined string of these enum names (see [BPReadingEntity]).
 *
 * Order here is the display order in the picker. Append new values at the end;
 * never reorder/rename existing ones, as stored readings persist the name.
 */
@Serializable
enum class BpTag {
    BEFORE_MEDICATION,
    AFTER_MEDICATION,
    CAFFEINE,
    ALCOHOL,
    SALTY_MEAL,
    EXERCISE,
    STRESS,
    POOR_SLEEP,
    RESTING,
    ON_WAKING,
}
