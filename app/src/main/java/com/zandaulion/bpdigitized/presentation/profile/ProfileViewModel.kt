package com.zandaulion.bpdigitized.presentation.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zandaulion.bpdigitized.domain.model.ActivityLevel
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.domain.model.BmiCategory
import com.zandaulion.bpdigitized.domain.model.UserProfile
import com.zandaulion.bpdigitized.domain.billing.BillingManager
import com.zandaulion.bpdigitized.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

/** Outcome of a "Restore Purchases" attempt — resolved to a string in the UI layer. */
enum class RestoreState { Success, Error }

/** Measurement unit system for the body-measurement fields. Storage stays metric. */
enum class UnitSystem { METRIC, IMPERIAL }

// Conversion constants.
private const val LB_PER_KG = 2.20462f
private const val CM_PER_IN = 2.54f
private const val IN_PER_FT = 12

/** Always formats with a '.' separator so the value round-trips through toFloatOrNull. */
private fun fmtUs(value: Float): String = "%.1f".format(java.util.Locale.US, value)

data class ProfileFormState(
    // ── Billing ───────────────────────────────────────────────────────────────
    val isRestoring: Boolean = false,
    val restoreResult: RestoreState? = null,
    // ── Health profile ────────────────────────────────────────────────────────
    val birthYearText: String = "",
    val sex: BiologicalSex = BiologicalSex.PREFER_NOT_TO_SAY,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val weightText: String = "",      // kg in metric mode, lb in imperial mode
    val heightText: String = "",      // cm — metric mode only
    val heightFtText: String = "",    // ft — imperial mode only
    val heightInText: String = "",    // in — imperial mode only
    val isSmoker: Boolean = false,
    val hasDiabetes: Boolean = false,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE,
    val isLoaded: Boolean = false,
    val backingWeightKg: Float? = null,
    val backingHeightCm: Float? = null,
) {
    val birthYearError: Boolean
        get() {
            if (birthYearText.isEmpty()) return false
            val year = birthYearText.toIntOrNull() ?: return true
            val currentYear = java.time.Year.now().value
            return year !in (currentYear - 120)..(currentYear - 1)
        }

    /** Computed age shown as supporting text; null until a valid birth year is entered. */
    val computedAge: Int?
        get() {
            val year = birthYearText.toIntOrNull() ?: return null
            val age = java.time.Year.now().value - year
            return if (age in 1..120) age else null
        }

    /** Canonical weight in kilograms derived from the active-unit text. Null if blank/invalid/≤0. */
    val weightKg: Float?
        get() {
            if (backingWeightKg != null) return backingWeightKg
            val v = weightText.toFloatOrNull()?.takeIf { it > 0f } ?: return null
            return if (unitSystem == UnitSystem.METRIC) v else v / LB_PER_KG
        }

    /** Canonical height in centimetres derived from the active-unit text. Null if blank/invalid/≤0. */
    val heightCm: Float?
        get() {
            if (backingHeightCm != null) return backingHeightCm
            return if (unitSystem == UnitSystem.METRIC) {
                heightText.toFloatOrNull()?.takeIf { it > 0f }
            } else {
                val ft = heightFtText.toFloatOrNull() ?: 0f
                val inch = heightInText.toFloatOrNull() ?: 0f
                ((ft * IN_PER_FT + inch) * CM_PER_IN).takeIf { it > 0f }
            }
        }

    val weightError: Boolean
        get() {
            if (weightText.isBlank()) return false
            val kg = weightKg ?: return true            // non-empty but unparseable/≤0
            return kg > 500f
        }

    val heightError: Boolean
        get() {
            val hasInput = if (unitSystem == UnitSystem.METRIC) {
                heightText.isNotBlank()
            } else {
                heightFtText.isNotBlank() || heightInText.isNotBlank()
            }
            if (!hasInput) return false
            val cm = heightCm ?: return true
            return cm > 280f
        }

    /** Live BMI from the canonical values, null until both weight + height are valid. */
    val bmi: Float?
        get() {
            val w = weightKg ?: return null
            val h = heightCm ?: return null
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

    fun toUserProfile() = UserProfile(
        birthYear = birthYearText.toIntOrNull(),
        biologicalSex = sex,
        weightKg = weightKg,
        heightCm = heightCm,
        isSmoker = isSmoker,
        hasDiabetes = hasDiabetes,
        activityLevel = activityLevel,
    )
}

sealed interface ProfileEvent {
    data object Saved : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UserProfileRepository,
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val p = repository.observe().first()
            val unit = UnitPrefs.get(context)
            _state.update {
                ProfileFormState(
                    birthYearText = p.birthYear?.toString()
                        ?: (java.time.Year.now().value - 40).toString(),
                    sex = p.biologicalSex,
                    isSmoker = p.isSmoker,
                    hasDiabetes = p.hasDiabetes,
                    activityLevel = p.activityLevel,
                    unitSystem = unit,
                    isLoaded = true,
                    backingWeightKg = p.weightKg,
                    backingHeightCm = p.heightCm,
                ).withMeasurements(p.weightKg, p.heightCm, unit)
            }
        }
    }

    fun restorePurchases() {
        _state.update { it.copy(isRestoring = true, restoreResult = null) }
        billingManager.restorePurchases { success, _ ->
            _state.update {
                it.copy(
                    isRestoring = false,
                    restoreResult = if (success) RestoreState.Success else RestoreState.Error,
                )
            }
        }
    }

    fun dismissRestoreResult() = _state.update { it.copy(restoreResult = null) }

    fun updateBirthYear(v: String) = _state.update { it.copy(birthYearText = v.filter(Char::isDigit).take(4)) }
    fun updateSex(v: BiologicalSex) = _state.update { it.copy(sex = v) }
    fun updateWeight(v: String)   = _state.update { it.copy(weightText = sanitiseDecimal(v, maxLen = 6), backingWeightKg = null) }
    fun updateHeight(v: String)   = _state.update { it.copy(heightText = sanitiseDecimal(v, maxLen = 6), backingHeightCm = null) }
    fun updateHeightFt(v: String) = _state.update { it.copy(heightFtText = v.filter(Char::isDigit).take(1), backingHeightCm = null) }
    fun updateHeightIn(v: String) = _state.update { it.copy(heightInText = v.filter(Char::isDigit).take(2), backingHeightCm = null) }
    fun updateSmoker(v: Boolean)  = _state.update { it.copy(isSmoker = v) }
    fun updateDiabetes(v: Boolean) = _state.update { it.copy(hasDiabetes = v) }
    fun updateActivity(v: ActivityLevel) = _state.update { it.copy(activityLevel = v) }

    /** Switches unit system, converting the currently-entered values into the new unit. */
    fun updateUnitSystem(target: UnitSystem) {
        _state.update { s ->
            if (s.unitSystem == target) return@update s
            // Read canonical values from the *current* unit, then re-fill in the target unit.
            val kg = s.weightKg
            val cm = s.heightCm
            s.copy(
                unitSystem = target,
                backingWeightKg = kg,
                backingHeightCm = cm,
            ).withMeasurements(kg, cm, target)
        }
        UnitPrefs.set(context, target)
    }

    fun save() {
        val s = _state.value
        if (s.birthYearError || s.weightError || s.heightError) return
        viewModelScope.launch {
            repository.save(s.toUserProfile())
            _events.send(ProfileEvent.Saved)
        }
    }

    private fun sanitiseDecimal(raw: String, maxLen: Int): String {
        // Locale decimal keyboards (e.g. Romanian) emit ',' — normalise it to '.' so the
        // value is always stored and parsed with a period separator.
        val filtered = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
        val dotIndex = filtered.indexOf('.')
        return if (dotIndex == -1) filtered.take(maxLen)
        else filtered.take(maxLen).let { s ->
            // At most one decimal point; keep at most 1 digit after the dot for readability
            val intPart = s.substring(0, dotIndex)
            val decPart = s.substring(dotIndex + 1).take(1)
            "$intPart.$decPart"
        }
    }
}

/** Fills the editable measurement text fields from canonical kg/cm in the given unit. */
private fun ProfileFormState.withMeasurements(
    kg: Float?,
    cm: Float?,
    unit: UnitSystem,
): ProfileFormState = when (unit) {
    UnitSystem.METRIC -> copy(
        weightText   = kg?.let { fmtUs(it) }.orEmpty(),
        heightText   = cm?.let { fmtUs(it) }.orEmpty(),
        heightFtText = "",
        heightInText = "",
    )
    UnitSystem.IMPERIAL -> {
        val (ft, inch) = cm.toFeetInches()
        copy(
            weightText   = kg?.let { fmtUs(it * LB_PER_KG) }.orEmpty(),
            heightText   = "",
            heightFtText = ft,
            heightInText = inch,
        )
    }
}

/** Converts centimetres to whole feet + inches as text. Carries 12″ up to the next foot. */
private fun Float?.toFeetInches(): Pair<String, String> {
    if (this == null) return "" to ""
    val totalIn = this / CM_PER_IN
    var ft = (totalIn / IN_PER_FT).toInt()
    var inch = (totalIn % IN_PER_FT).roundToInt()
    if (inch == IN_PER_FT) { ft += 1; inch = 0 }
    return ft.toString() to inch.toString()
}
