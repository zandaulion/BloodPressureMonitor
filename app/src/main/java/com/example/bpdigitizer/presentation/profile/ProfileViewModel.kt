package com.example.bpdigitizer.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bpdigitizer.domain.model.ActivityLevel
import com.example.bpdigitizer.domain.model.BiologicalSex
import com.example.bpdigitizer.domain.model.BmiCategory
import com.example.bpdigitizer.domain.model.UserProfile
import com.example.bpdigitizer.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileFormState(
    val ageText: String = "",
    val sex: BiologicalSex = BiologicalSex.PREFER_NOT_TO_SAY,
    val weightText: String = "",   // kg
    val heightText: String = "",   // cm
    val isSmoker: Boolean = false,
    val hasDiabetes: Boolean = false,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE,
    val isLoaded: Boolean = false,
) {
    val ageError: Boolean
        get() = ageText.isNotEmpty() && ageText.toIntOrNull()?.let { it !in 1..120 } != false

    val weightError: Boolean
        get() = weightText.isNotEmpty() &&
            weightText.toFloatOrNull()?.let { it <= 0f || it > 500f } != false

    val heightError: Boolean
        get() = heightText.isNotEmpty() &&
            heightText.toFloatOrNull()?.let { it <= 0f || it > 280f } != false

    /** Live BMI from the form fields, null until both weight + height are valid numbers. */
    val bmi: Float?
        get() {
            val w = weightText.toFloatOrNull()?.takeIf { it > 0f } ?: return null
            val h = heightText.toFloatOrNull()?.takeIf { it > 0f } ?: return null
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

    /** Imperial helper shown as supporting text under the weight field. */
    val weightLbs: String?
        get() = weightText.toFloatOrNull()?.let { "≈ ${"%.1f".format(it * 2.20462f)} lbs" }

    /** Imperial helper shown as supporting text under the height field. */
    val heightImperial: String?
        get() = heightText.toFloatOrNull()?.let { cm ->
            val totalIn = cm / 2.54f
            val ft = (totalIn / 12).toInt()
            val inch = (totalIn % 12).toInt()
            "≈ ${ft}′${inch}″"
        }

    fun toUserProfile() = UserProfile(
        age = ageText.toIntOrNull(),
        biologicalSex = sex,
        weightKg = weightText.toFloatOrNull()?.takeIf { it > 0f },
        heightCm = heightText.toFloatOrNull()?.takeIf { it > 0f },
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
    private val repository: UserProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val p = repository.observe().first()
            _state.update {
                ProfileFormState(
                    ageText = p.age?.toString().orEmpty(),
                    sex = p.biologicalSex,
                    weightText = p.weightKg?.let { "%.1f".format(it) }.orEmpty(),
                    heightText = p.heightCm?.let { "%.1f".format(it) }.orEmpty(),
                    isSmoker = p.isSmoker,
                    hasDiabetes = p.hasDiabetes,
                    activityLevel = p.activityLevel,
                    isLoaded = true,
                )
            }
        }
    }

    fun updateAge(v: String)      = _state.update { it.copy(ageText = v.filter(Char::isDigit).take(3)) }
    fun updateSex(v: BiologicalSex) = _state.update { it.copy(sex = v) }
    fun updateWeight(v: String)   = _state.update { it.copy(weightText = sanitiseDecimal(v, maxLen = 6)) }
    fun updateHeight(v: String)   = _state.update { it.copy(heightText = sanitiseDecimal(v, maxLen = 6)) }
    fun updateSmoker(v: Boolean)  = _state.update { it.copy(isSmoker = v) }
    fun updateDiabetes(v: Boolean) = _state.update { it.copy(hasDiabetes = v) }
    fun updateActivity(v: ActivityLevel) = _state.update { it.copy(activityLevel = v) }

    fun save() {
        val s = _state.value
        if (s.ageError || s.weightError || s.heightError) return
        viewModelScope.launch {
            repository.save(s.toUserProfile())
            _events.send(ProfileEvent.Saved)
        }
    }

    private fun sanitiseDecimal(raw: String, maxLen: Int): String {
        val filtered = raw.filter { it.isDigit() || it == '.' }
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
