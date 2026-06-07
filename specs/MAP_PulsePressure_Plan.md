# Implementation Plan — Mean Arterial Pressure (MAP) + Pulse Pressure (PP)

**Goal:** Add two derived hemodynamic metrics — Mean Arterial Pressure and Pulse Pressure — and surface them in the app UI, the PDF report, and the CSV export. This is the report's highest value-to-effort gap (the line it draws between "basic loggers" and "advanced clinical apps").

**Status target:** ships as part of the next version (bump after this lands).

---

## Scope

**In scope**
- Compute MAP and PP from the systolic/diastolic we already store.
- Show them on: the dashboard reading row, the validation screen (live), the PDF stats bar, and the CSV export.
- A short Help-screen explainer, localized to all 12 languages.

**Out of scope (deliberately)**
- Selectable clinical guidelines (AHA/ESC/JNC7) — separate, larger feature.
- Storing MAP/PP in the database — they are pure derivations; computing on the fly avoids a Room migration and any risk of stored values drifting from the raw reading.
- Charting MAP/PP as extra lines — keep the trend chart to Sys/Dia.

---

## Design decisions

1. **Formulas**
   - **Pulse Pressure** `PP = SBP − DBP` (mmHg).
   - **Mean Arterial Pressure** `MAP ≈ DBP + (SBP − DBP)/3 = (SBP + 2·DBP)/3`, rounded to the nearest integer (standard resting-rate approximation).
2. **Single source of truth.** One small pure-function file in the domain layer; every surface (live sliders working on raw `Int`s, and saved `BPReading`s) calls the same functions. No duplication.
3. **No DB / Room change.** Nothing to migrate. The change is additive and low-risk.
4. **PDF and CSV are already English-only** (hard-coded strings). So MAP/PP labels there are plain literals and need **no translation**. Only the in-app Compose strings get localized.

---

## Step-by-step

### Step 1 — Domain helper (single source of truth)

Create `app/src/main/java/com/zandaulion/bpdigitized/domain/model/Hemodynamics.kt`:

```kotlin
package com.zandaulion.bpdigitized.domain.model

import kotlin.math.roundToInt

/** Pulse Pressure = SBP − DBP (mmHg). */
fun pulsePressure(systolic: Int, diastolic: Int): Int = systolic - diastolic

/**
 * Mean Arterial Pressure ≈ DBP + (SBP − DBP)/3 = (SBP + 2·DBP)/3 (mmHg).
 * Standard resting approximation, rounded to the nearest integer.
 */
fun meanArterialPressure(systolic: Int, diastolic: Int): Int =
    ((systolic + 2.0 * diastolic) / 3.0).roundToInt()

/** Convenience accessors for a saved reading. */
val BPReading.pulsePressure: Int get() = pulsePressure(systolic, diastolic)
val BPReading.meanArterialPressure: Int get() = meanArterialPressure(systolic, diastolic)
```

> Note: these are body-level helpers, not constructor properties, so `@Serializable BPReading` JSON export is unaffected.

### Step 2 — Dashboard reading row

File: `presentation/dashboard/DashboardScreen.kt`, composable `ReadingRow` (~line 632).

After the `"${reading.systolic}/${reading.diastolic}$pulseSuffix"` `Text` (line ~657), add a caption line:

```kotlin
Text(
    text = stringResource(
        R.string.reading_hemodynamics_format,
        reading.meanArterialPressure,
        reading.pulsePressure,
    ),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

Add imports for `meanArterialPressure` / `pulsePressure`. (Saved readings always satisfy SBP > DBP because validation enforces it, so PP is always positive.)

### Step 3 — Validation screen (live readout)

File: `presentation/validation/ValidationScreen.kt`. The screen already has `state.systolic` / `state.diastolic`.

After the validation-error block (~line 217) and before the Notes field, add a live readout shown only when the entry is valid (so PP can't render negative):

```kotlin
if (state.isValid) {
    HemodynamicsReadout(
        map = meanArterialPressure(state.systolic, state.diastolic),
        pulsePressure = pulsePressure(state.systolic, state.diastolic),
    )
}
```

Add a small private composable (near `BpValueSlider`):

```kotlin
@Composable
private fun HemodynamicsReadout(map: Int, pulsePressure: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Metric(stringResource(R.string.hemo_map_label), map)
            Metric(stringResource(R.string.hemo_pp_label), pulsePressure)
        }
    }
}

@Composable
private fun Metric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.hemo_value_mmhg, value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
```

This also gives the user gentle education as they drag the sliders.

### Step 4 — PDF stats bar

File: `domain/usecase/ExportPdfUseCase.kt`, function `drawStatsBar` (~line 227). English literals are fine here.

After the existing `Max` line (~line 247), append:

```kotlin
val map = readings.map { meanArterialPressure(it.systolic, it.diastolic) }
val pp  = readings.map { pulsePressure(it.systolic, it.diastolic) }
vline()
value("MAP  ${map.average().roundToInt()}")
value("PP  ${pp.average().roundToInt()}")
```

Add the import for the two functions. **Visual check:** the stats bar sits on a wide landscape page (`CHART_R = 1524`); confirm the new values don't run off the right edge. If tight, drop `Min`/`Max` or shorten labels.

### Step 5 — CSV export

File: `domain/usecase/ExportCsvUseCase.kt`.

- Header → add two columns after `pulse`:
  ```
  id,dateTime,timestamp,systolic,diastolic,pulse,map,pulsePressure,category,notes
  ```
- Row → insert after the `pulse` field:
  ```kotlin
  "${meanArterialPressure(r.systolic, r.diastolic)}," +
  "${pulsePressure(r.systolic, r.diastolic)}," +
  ```
  (Add the import.)

### Step 6 — Help screen explainer

File: `presentation/help/HelpScreen.kt` + help strings.

Add a short section (title + body) explaining what MAP and PP are and that they're informational, e.g.:

> **Mean Arterial Pressure (MAP)** is the average pressure in your arteries across a heartbeat (≈ diastolic + a third of the gap to systolic). **Pulse Pressure (PP)** is simply systolic minus diastolic; a persistently high PP can indicate arterial stiffness. Both are shown for information only — discuss them with your doctor.

Render it consistently with the existing Help sections (see how `help_trends_body` / `help_export_body` are laid out).

### Step 7 — Strings + localization

Add to `app/src/main/res/values/strings.xml`:

```xml
<string name="reading_hemodynamics_format">MAP %1$d · PP %2$d mmHg</string>
<string name="hemo_map_label">MAP</string>
<string name="hemo_pp_label">Pulse pressure</string>
<string name="hemo_value_mmhg">%1$d mmHg</string>
<string name="help_hemo_title">Mean arterial &amp; pulse pressure</string>
<string name="help_hemo_body">…(the explainer text above)…</string>
```

Then replicate translated values into the 11 other locales:
`values-de, values-es, values-fr, values-ar, values-hi, values-ja, values-ko, values-pt, values-ro, values-uk, values-zh`.

Notes:
- "MAP"/"mmHg" are typically left as-is across locales; translate `hemo_pp_label` and the help title/body.
- Watch apostrophe escaping (`\'`) in `fr/es/...` and the RTL `ar` text.

### Step 8 — Build & verify

```
./gradlew :app:assembleDebug
```

Manual checks on device:
- [ ] Dashboard rows show `MAP … · PP … mmHg` under the reading.
- [ ] Validation: the MAP/PP card updates live as sliders move; hidden while SBP ≤ DBP.
- [ ] Export PDF → stats bar shows MAP + PP averages, nothing clipped.
- [ ] Export CSV → new `map,pulsePressure` columns populated; opens cleanly in a spreadsheet.
- [ ] Help screen shows the new section, translated when the app language is switched.
- [ ] Switch app language → in-app labels localized; PDF/CSV remain English (expected).

### Step 9 — Version bump + release notes (when approved)

- `app/build.gradle.kts`: bump `versionCode` and `versionName`.
- Add a new `store/release-notes-<version>.txt` line item (≤500 chars/locale): "New: Mean Arterial Pressure (MAP) and Pulse Pressure shown for each reading, in the PDF report, and in CSV export."

---

## Effort & risk

- **Effort:** roughly half a day. The logic is trivial; the bulk is the 12-locale string work in Step 7 (mechanical).
- **Risk:** low. Additive only — no DB migration, no changes to how readings are stored or categorized. Worst case is a PDF layout tweak in Step 4.

## Suggested commit slicing (per the branch-per-task workflow)

1. `feat: add MAP and pulse pressure domain helpers`
2. `feat: show MAP/PP on reading rows and validation screen`
3. `feat: include MAP/PP in PDF and CSV exports`
4. `feat: document MAP/PP in Help + localize`

(Or one branch `feat/hemodynamics` with these as commits — your call. Hold the merge until you've tested on device.)

## Natural next step after this

The **guideline toggle** (AHA ↔ ESC/ESH ↔ JNC7) — it pairs well, since both are "clinical depth" credibility features and your 12-language reach makes non-US thresholds matter.
