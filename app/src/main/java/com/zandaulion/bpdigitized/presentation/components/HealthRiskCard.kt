package com.zandaulion.bpdigitized.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.CardiovascularRisk
import com.zandaulion.bpdigitized.domain.model.HealthAssessment
import com.zandaulion.bpdigitized.presentation.theme.CategoryCrisis
import com.zandaulion.bpdigitized.presentation.theme.CategoryElevated
import com.zandaulion.bpdigitized.presentation.theme.CategoryNormal
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage2

private fun CardiovascularRisk.badgeColor(): Color = when (this) {
    CardiovascularRisk.LOW       -> CategoryNormal
    CardiovascularRisk.MODERATE  -> CategoryElevated
    CardiovascularRisk.HIGH      -> CategoryStage2
    CardiovascularRisk.VERY_HIGH -> CategoryCrisis
}

private fun BPCategory.displayLabel() = when (this) {
    BPCategory.NORMAL              -> "Normal BP"
    BPCategory.ELEVATED            -> "Elevated BP"
    BPCategory.STAGE_1             -> "Stage 1 Hypertension"
    BPCategory.STAGE_2             -> "Stage 2 Hypertension"
    BPCategory.HYPERTENSIVE_CRISIS -> "Hypertensive Crisis"
}

/** Max notes shown collapsed; tap "Show more" to reveal the rest. */
private const val COLLAPSED_NOTES = 2

@Composable
fun HealthRiskCard(
    assessment: HealthAssessment,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

            // ── header row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Risk Assessment", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onEditProfile) {
                    Text(
                        if (assessment.profileComplete) "Edit Profile" else "Set Up Profile",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            // ── risk badge + BP category ──────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RiskBadge(
                    text = "${assessment.cardiovascularRisk.label} Risk",
                    color = assessment.cardiovascularRisk.badgeColor(),
                )
                Text(
                    text = assessment.bpCategory.displayLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── BMI row ───────────────────────────────────────────────────────
            if (assessment.bmi != null && assessment.bmiCategory != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "BMI ${"%.1f".format(assessment.bmi)} · ${assessment.bmiCategory.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── profile incomplete prompt ─────────────────────────────────────
            if (!assessment.profileComplete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Add your age, weight, height, and lifestyle details for a personalised assessment.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── context notes ─────────────────────────────────────────────────
            if (assessment.contextNotes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val visibleNotes = if (expanded) assessment.contextNotes
                else assessment.contextNotes.take(COLLAPSED_NOTES)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    visibleNotes.forEach { note ->
                        Text(
                            text = "• $note",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (assessment.contextNotes.size > COLLAPSED_NOTES) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            if (expanded) "Show less"
                            else "Show ${assessment.contextNotes.size - COLLAPSED_NOTES} more…",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** Coloured pill badge used exclusively inside this card. */
@Composable
private fun RiskBadge(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
