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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.zandaulion.bpdigitized.domain.model.ActivityLevel
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.domain.model.BmiCategory

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
                title = { Text("Health Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.birthYearError && !state.weightError && !state.heightError,
                    ) {
                        Text("Save")
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
                text = "This information helps tailor your blood pressure risk assessment. " +
                    "All data stays on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // ── Basic information ─────────────────────────────────────────────
            SectionHeader("Basic Information")

            OutlinedTextField(
                value = state.birthYearText,
                onValueChange = viewModel::updateBirthYear,
                label = { Text("Birth year") },
                placeholder = { Text("e.g. 1985") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.birthYearError,
                supportingText = when {
                    state.birthYearError ->
                        { { Text("Enter a valid birth year") } }
                    state.computedAge != null ->
                        { { Text("Age: ${state.computedAge} years") } }
                    else -> null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Biological sex",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiologicalSex.entries.forEach { sex ->
                    FilterChip(
                        selected = state.sex == sex,
                        onClick = { viewModel.updateSex(sex) },
                        label = { Text(sex.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Body measurements ─────────────────────────────────────────────
            SectionHeader("Body Measurements")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.weightText,
                    onValueChange = viewModel::updateWeight,
                    label = { Text("Weight") },
                    suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.weightError,
                    supportingText = when {
                        state.weightError -> { { Text("Enter a value > 0") } }
                        state.weightLbs != null -> { { Text(state.weightLbs!!) } }
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.heightText,
                    onValueChange = viewModel::updateHeight,
                    label = { Text("Height") },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.heightError,
                    supportingText = when {
                        state.heightError -> { { Text("Enter a value > 0") } }
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
                        Text("BMI", style = MaterialTheme.typography.bodyMedium)
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
                                    text = cat.label,
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
            SectionHeader("Health Factors")

            ToggleRow(
                label = "Current smoker",
                description = "Doubles cardiovascular risk",
                checked = state.isSmoker,
                onChecked = viewModel::updateSmoker,
            )
            HorizontalDivider()
            ToggleRow(
                label = "Diabetes / pre-diabetes",
                description = "Significantly raises risk when combined with high BP",
                checked = state.hasDiabetes,
                onChecked = viewModel::updateDiabetes,
            )

            Spacer(Modifier.height(8.dp))

            // ── Physical activity ─────────────────────────────────────────────
            SectionHeader("Physical Activity")

            Text(
                text = "Typical exercise level",
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
                                label = { Text(level.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            // Description of the selected level
            Text(
                text = state.activityLevel.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(8.dp))

            // ── Subscriptions ───────────────────────────────────────────────────────
            SectionHeader("Subscriptions")

            Text(
                text = "Manage your Pro subscription to unlock the AI Camera Scanner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::restorePurchases,
                enabled = !state.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isRestoring) "Restoring..." else "Restore Purchases")
            }
            
            state.restoreMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "ⓘ  This assessment is informational and does not replace medical advice.",
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
