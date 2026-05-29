package com.zandaulion.bpdigitized.presentation.profile

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.ActivityLevel
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.domain.model.BmiCategory
import com.zandaulion.bpdigitized.presentation.extensions.descriptionRes
import com.zandaulion.bpdigitized.presentation.extensions.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.events, lifecycleOwner) {
        viewModel.events.flowWithLifecycle(lifecycleOwner.lifecycle).collect { event ->
            when (event) {
                ProfileEvent.Saved -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.birthYearError && !state.weightError && !state.heightError,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // ── Basic information ─────────────────────────────────────────────
            SectionHeader(stringResource(R.string.profile_section_basic))

            OutlinedTextField(
                value = state.birthYearText,
                onValueChange = viewModel::updateBirthYear,
                label = { Text(stringResource(R.string.profile_birth_year_label)) },
                placeholder = { Text(stringResource(R.string.profile_birth_year_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.birthYearError,
                supportingText = when {
                    state.birthYearError ->
                        { { Text(stringResource(R.string.profile_birth_year_error)) } }
                    state.computedAge != null ->
                        { { Text(stringResource(R.string.profile_age, state.computedAge!!)) } }
                    else -> null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.profile_sex_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiologicalSex.entries.forEach { sex ->
                    FilterChip(
                        selected = state.sex == sex,
                        onClick = { viewModel.updateSex(sex) },
                        label = { Text(stringResource(sex.labelRes())) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Body measurements ─────────────────────────────────────────────
            SectionHeader(stringResource(R.string.profile_section_body))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.weightText,
                    onValueChange = viewModel::updateWeight,
                    label = { Text(stringResource(R.string.profile_weight_label)) },
                    suffix = { Text(stringResource(R.string.profile_weight_suffix)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.weightError,
                    supportingText = when {
                        state.weightError -> { { Text(stringResource(R.string.profile_weight_error)) } }
                        state.weightLbs != null -> { { Text(state.weightLbs!!) } }
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.heightText,
                    onValueChange = viewModel::updateHeight,
                    label = { Text(stringResource(R.string.profile_height_label)) },
                    suffix = { Text(stringResource(R.string.profile_height_suffix)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.heightError,
                    supportingText = when {
                        state.heightError -> { { Text(stringResource(R.string.profile_height_error)) } }
                        state.heightImperial != null -> { { Text(state.heightImperial!!) } }
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            // Live BMI preview
            val bmi = state.bmi
            if (bmi != null) {
                val cat = state.bmiCategory
                val catColor = when (cat) {
                    BmiCategory.NORMAL -> MaterialTheme.colorScheme.primary
                    BmiCategory.UNDERWEIGHT, BmiCategory.OVERWEIGHT ->
                        MaterialTheme.colorScheme.tertiary
                    BmiCategory.OBESE -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurface
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.profile_bmi), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "%.1f".format(bmi),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (cat != null) {
                                Text(
                                    text = stringResource(cat.labelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = catColor,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Health factors ────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.profile_section_health))

            ToggleRow(
                label = stringResource(R.string.profile_smoker_label),
                description = stringResource(R.string.profile_smoker_desc),
                checked = state.isSmoker,
                onChecked = viewModel::updateSmoker,
            )
            HorizontalDivider()
            ToggleRow(
                label = stringResource(R.string.profile_diabetes_label),
                description = stringResource(R.string.profile_diabetes_desc),
                checked = state.hasDiabetes,
                onChecked = viewModel::updateDiabetes,
            )

            Spacer(Modifier.height(8.dp))

            // ── Physical activity ─────────────────────────────────────────────
            SectionHeader(stringResource(R.string.profile_section_activity))

            Text(
                text = stringResource(R.string.profile_activity_level_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            // 2×2 grid of chips
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActivityLevel.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { level ->
                            FilterChip(
                                selected = state.activityLevel == level,
                                onClick = { viewModel.updateActivity(level) },
                                label = { Text(stringResource(level.labelRes())) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            // Description of the selected level
            Text(
                text = stringResource(state.activityLevel.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(8.dp))

            // ── Subscriptions ─────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.profile_section_subscriptions))

            Text(
                text = stringResource(R.string.profile_subscriptions_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::restorePurchases,
                enabled = !state.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isRestoring) R.string.profile_restoring
                        else R.string.profile_restore_purchases,
                    ),
                )
            }

            state.restoreResult?.let { result ->
                Text(
                    text = stringResource(
                        when (result) {
                            RestoreState.Success -> R.string.profile_restore_success
                            RestoreState.Error   -> R.string.profile_restore_error
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.profile_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    HorizontalDivider()
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
