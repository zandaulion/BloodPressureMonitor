package com.zandaulion.bpdigitized.presentation.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ── Static help content ───────────────────────────────────────────────────────

private data class HelpSection(val title: String, val body: String)

private val HELP_SECTIONS = listOf(
    HelpSection(
        title = "Scanning with Camera",
        body  = """
Camera scanning is a Premium feature, available with an active subscription.

Tap the camera button (bottom-right) to open the live preview. Frame your blood pressure monitor's display inside the on-screen guide, then tap Capture.

The photo is sent to Gemini 2.5 Flash, which reads the Systolic, Diastolic, and Pulse values. You can review and correct them on the next screen before saving.

If you don't have an active subscription, tapping the camera button will open the upgrade screen where you can subscribe or restore a previous purchase.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Manual Entry",
        body  = """
Tap the pencil button to enter a reading without taking a photo. The sliders are pre-filled with your last saved reading (defaults: 120 / 80 / 60 on first launch).

Adjust Systolic, Diastolic, and Pulse using the slider or the ± buttons. Add an optional note, then tap Save Reading.

The Save button is disabled until Systolic is greater than Diastolic.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Trends Chart",
        body  = """
The chart on the Dashboard plots Systolic (red) and Diastolic (blue) readings over time. Use the chips above the chart to filter the view:

  • 7d — last 7 days
  • 30d — last 30 days (default)
  • 90d — last 90 days
  • All — complete history

The Y-axis is fixed at 40–180 mmHg. Dashed reference lines mark the AHA normal upper limits: SYS 120 and DIA 80.

Rotate the phone to landscape for a full-screen chart view.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Reading History",
        body  = """
All readings are listed below the chart, newest first. Each row shows:

  • Date and time
  • SYS / DIA · pulse (bpm)
  • A colour-coded BP category badge (Normal / Elevated / Stage 1 / Stage 2 / Crisis)
  • Any note you added (shown in italics)

Swipe a row left or right to delete it. A snackbar appears at the bottom with an Undo button — you have 5 seconds to reverse the deletion before it is permanent.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Health Profile",
        body  = """
Tap the Person icon to open your Health Profile. Filling it in allows the app to personalise the Health Risk card.

Fields available:

  • Birth year — used to compute your age
  • Biological sex
  • Weight (kg) and Height (cm) — live BMI preview shown
  • Current smoker toggle
  • Diabetes / pre-diabetes toggle
  • Physical activity level

All data stays on-device (DataStore Preferences). The profile is optional; the app works fully without it.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Health Risk Assessment",
        body  = """
When at least one reading exists, a Health Risk card appears at the top of the reading list. It combines your latest BP category with your health profile to surface contextual guidance.

Guidance is drawn from AHA (American Heart Association) guidelines and is prefixed with a disclaimer. It is informational only and does not replace advice from a qualified healthcare professional.

Tap "Edit profile" on the card to complete or update your health profile.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Reminders",
        body  = """
Tap the Bell icon to configure daily measurement reminders.

  1. Grant the notification permission if prompted.
  2. Enable the master switch.
  3. Set a Morning time (ideally 30 minutes after waking, before coffee or medication).
  4. Set an Evening time (when you are relaxed and at rest).

Notifications include a Log Now shortcut that opens the app, and a Snooze (15m) action. Alarms are re-scheduled automatically each day and survive device reboots.

If the exact-alarm permission is not available on your device, the app will direct you to the system settings to enable it.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Exporting Data",
        body  = """
Tap the Share icon in the top bar to export your data. A dropdown offers three formats:

  • JSON — your complete reading history. Can be imported back into the app.
  • CSV — your complete history in spreadsheet format, compatible with Excel or Google Sheets.
  • PDF — a multi-page document containing:
      – Landscape trend charts for the last 7 days, 30 days, and all time. Each chart includes reference lines, numbered note callouts, and a stats bar (avg / min / max).
      – A portrait table of readings from the last 30 days with colour-coded categories.

The export is shared via Android's standard share sheet.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Importing Data",
        body  = """
Tap the Download icon (top bar) to import readings from a JSON file previously exported from this app.

The app opens a file picker. Select your JSON export file. Only readings whose timestamps do not already exist in the database are inserted — re-importing the same file is safe.

A snackbar confirms how many new readings were added, or tells you there was nothing new to import.
        """.trimIndent(),
    ),
    HelpSection(
        title = "Health Connect",
        body  = """
Health Connect is Google's on-device health data platform. This app can write your readings as Blood Pressure and Heart Rate records.

Bulk sync: tap the heart-monitor icon in the top bar. You will be asked to grant write permissions the first time. All existing readings are uploaded (safe to repeat — existing records are updated, not duplicated).

Auto-push: every time you save a new reading, it is automatically pushed to Health Connect in the background — provided you have already granted permissions. No extra action is needed.

If Health Connect is not installed on your device, the app will offer a link to the Play Store to install it.
        """.trimIndent(),
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            items(HELP_SECTIONS) { section ->
                HelpCard(section)
            }
        }
    }
}

@Composable
private fun HelpCard(section: HelpSection) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = section.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
