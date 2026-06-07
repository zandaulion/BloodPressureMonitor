# Implementation Plan — Selectable Clinical Guidelines (ACC/AHA · ESH/ESC · JNC7)

**Goal:** Let the user pick which clinical guideline classifies their readings, and re-color/re-label the *entire* history accordingly. This matches what My Heart/Cardilog do and is important for our 12-language (largely non-US) reach, where ESH/ESC is the regional standard.

**Market gap addressed:** "No selectable clinical guidelines" (market-comparison §3.2).

---

## The core problem

Today classification is **AHA-only and frozen at save time**:
- `CalculateBPZoneUseCase` ([CalculateBPZoneUseCase.kt](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/CalculateBPZoneUseCase.kt)) hard-codes AHA thresholds.
- The result is stored in `BPReadingEntity.category` ([BPReadingEntity.kt:15](../app/src/main/java/com/zandaulion/bpdigitized/data/local/BPReadingEntity.kt)) at insert ([ValidationViewModel.kt:90](../app/src/main/java/com/zandaulion/bpdigitized/presentation/validation/ValidationViewModel.kt)).
- `BPCategory` ([BPCategory.kt](../app/src/main/java/com/zandaulion/bpdigitized/domain/model/BPCategory.kt)) is a 5-value **AHA-shaped** enum consumed in ~6 places (badge, risk, PDF, string mappings).

Different guidelines have **different category sets and thresholds**, so a pure label swap is medically wrong at the boundaries. We need a proper per-guideline classifier, derived **on the fly** from the stored `systolic`/`diastolic` (which never change), with the selected guideline persisted as a preference.

---

## Scope

**In scope**
- A `Guideline` preference (persisted), default `ACC_AHA` (preserves current behavior).
- A per-guideline classifier producing a guideline-accurate label + a color bucket.
- Re-derive classification for display everywhere (rows, badge, risk card, PDF).
- A Settings screen to choose the guideline (new — see Step 6; reused by the breathing plan).

**Out of scope**
- Changing what's stored in the DB. `category` stays as the AHA value (a cache / Health-Connect-neutral default); display derives live.
- Per-reading guideline overrides. The choice is global.

---

## Threshold tables (office BP, mmHg)

> A reading takes the **highest** category its systolic OR diastolic qualifies for.

**ACC/AHA 2017** (current)
| Category | Systolic | | Diastolic |
|---|---|---|---|
| Normal | <120 | and | <80 |
| Elevated | 120–129 | and | <80 |
| Stage 1 | 130–139 | or | 80–89 |
| Stage 2 | ≥140 | or | ≥90 |
| Crisis | >180 | or | >120 |

**ESH/ESC 2023**
| Category | Systolic | | Diastolic |
|---|---|---|---|
| Optimal | <120 | and | <80 |
| Normal | 120–129 | and/or | 80–84 |
| High–normal | 130–139 | and/or | 85–89 |
| Grade 1 | 140–159 | and/or | 90–99 |
| Grade 2 | 160–179 | and/or | 100–109 |
| Grade 3 | ≥180 | and/or | ≥110 |

**JNC7 2003**
| Category | Systolic | | Diastolic |
|---|---|---|---|
| Normal | <120 | and | <80 |
| Prehypertension | 120–139 | or | 80–89 |
| Stage 1 | 140–159 | or | 90–99 |
| Stage 2 | ≥160 | or | ≥100 |

---

## Design

Introduce a normalized **severity** for coloring (reuse the existing 5-color scale) and a **guideline-specific label** for text:

```kotlin
// domain/model/Guideline.kt
enum class Guideline { ACC_AHA, ESH_ESC, JNC7 }

// A color bucket shared by all guidelines (drives the existing CategoryNormal…Crisis colors).
enum class BpSeverity { OPTIMAL, NORMAL, ELEVATED, MILD, MODERATE, SEVERE }

// One classified result: the color bucket + a stable label key.
data class BpClassification(
    val guideline: Guideline,
    val severity: BpSeverity,
    val labelKey: BpLabelKey,   // enum → string resource (per guideline)
)
```

`BpLabelKey` enumerates every distinct label across the three guidelines (Optimal, Normal, HighNormal, Elevated, Prehypertension, Stage1, Stage2, Grade1, Grade2, Grade3, Crisis). Severity → color reuses `CategoryNormal/Elevated/Stage2/Crisis` etc. from [theme/Color.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/theme/Color.kt).

---

## Step-by-step

### Step 1 — Domain model + classifier
- Add `Guideline`, `BpSeverity`, `BpLabelKey`, `BpClassification` (above).
- Add `ClassifyBpUseCase`:
  ```kotlin
  class ClassifyBpUseCase @Inject constructor() {
      operator fun invoke(guideline: Guideline, systolic: Int, diastolic: Int): BpClassification =
          when (guideline) {
              Guideline.ACC_AHA -> aha(systolic, diastolic)
              Guideline.ESH_ESC -> esc(systolic, diastolic)
              Guideline.JNC7    -> jnc7(systolic, diastolic)
          }
      // each: walk the table top-down, return the first matching class
  }
  ```
- Keep `CalculateBPZoneUseCase` as the AHA `BPCategory` source for **storage** (unchanged save path), so the DB and Health Connect stay stable.

### Step 2 — Persist the preference
Follow the [ChartSettingsRepositoryImpl](../app/src/main/java/com/zandaulion/bpdigitized/data/repository/ChartSettingsRepositoryImpl.kt) pattern on the shared `DataStore<Preferences>`:
```kotlin
override fun observeGuideline(): Flow<Guideline> = dataStore.data.map {
    it[KEY_GUIDELINE]?.let(Guideline::valueOf) ?: Guideline.ACC_AHA
}
override suspend fun setGuideline(g: Guideline) =
    dataStore.edit { it[KEY_GUIDELINE] = g.name }
private val KEY_GUIDELINE = stringPreferencesKey("clinical_guideline")
```
Put it in a new `AppSettingsRepository` (or extend `ChartSettingsRepository`). Bind in [AppModule.kt](../app/src/main/java/com/zandaulion/bpdigitized/di/AppModule.kt) `RepositoryModule`.

### Step 3 — Derive classification in the Dashboard VM
`DashboardViewModel` already combines flows (readings, range, profile, chartSettings…). Add the `guideline` flow and expose, per displayed reading, a `BpClassification` (computed via `ClassifyBpUseCase` from stored sys/dia). Pass classification — not `reading.category` — to the UI.

### Step 4 — Make display sites guideline-aware
Replace direct `reading.category` reads with the derived classification:
- **Reading row** badge ([DashboardScreen.kt:662](../app/src/main/java/com/zandaulion/bpdigitized/presentation/dashboard/DashboardScreen.kt)) → `CategoryBadge` takes `BpClassification`.
- **CategoryBadge** ([CategoryBadge.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/components/CategoryBadge.kt)) → color from `severity`, label from `labelKey`.
- **Health Risk card**: `CalculateHealthRiskUseCase` ([CalculateHealthRiskUseCase.kt](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/CalculateHealthRiskUseCase.kt)) currently keys off `BPCategory`. Map `BpSeverity` → the existing `bpPoints`/notes so risk reflects the selected guideline. (Keep the same point ladder; just feed it severity.)
- **String mapping**: add `BpLabelKey.labelRes()` and `severity`→color helpers in [StringResources.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/extensions/StringResources.kt). The old `badgeLabelRes()/displayLabelRes()` can delegate to AHA for back-compat.

### Step 5 — PDF export
`ExportPdfUseCase` ([ExportPdfUseCase.kt:368](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/ExportPdfUseCase.kt)) has `categoryLabel`/`categoryArgb` on `BPCategory`. Inject the guideline (read once like `smooth`), classify each reading via `ClassifyBpUseCase`, and label/color from the classification. Add a header note: "Classified per <guideline name>" so the doctor knows the standard used.

### Step 6 — Settings screen (new; shared)
There's no Settings screen today (top bar = bell/person/help). Add one:
- `presentation/settings/SettingsScreen.kt` + `Routes.SETTINGS` in [AppNavHost.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/AppNavHost.kt) + a gear icon in the Dashboard top bar.
- First control: a guideline selector (segmented buttons / radio list) with a one-line description of each.
- This screen becomes the home for the breathing toggle (Guided Breathing plan) and is a good future home for the Avg toggle and units.

### Step 7 — Strings + localization
- Guideline display names + short descriptions.
- Every `BpLabelKey` label (Optimal/Normal/High-normal/Elevated/Prehypertension/Stage 1–2/Grade 1–3/Crisis).
- Replicate to all 12 locales. Many of these are clinical terms with established local translations — worth care for `de/es/fr/pt` especially.

### Step 8 — Build & verify
```
./gradlew :app:assembleDebug
```
- [ ] Default is ACC/AHA; existing readings look identical to before.
- [ ] Switch to ESH/ESC → a 135/85 reading relabels Normal→**High–normal**; colors update across rows, risk card, and a fresh PDF.
- [ ] Switch to JNC7 → 135/85 becomes **Prehypertension**.
- [ ] Stored DB category is untouched (verify a re-export/Health-Connect sync still works).
- [ ] All three guideline names + labels localized.

---

## Effort & risk
- **Effort:** ~1.5–2 days (the classifier + three threshold tables are quick; the wiring through every display site and the localized clinical labels are the bulk).
- **Risk:** medium. The main risk is missing a `reading.category` consumer — grep `BPCategory` (14 files) and route each display path through the classification. Storage/Health-Connect are deliberately untouched to contain blast radius.

## Lighter MVP fallback (if you want it faster)
**Label-remap only:** keep the AHA engine, and per guideline remap the 5 AHA zones to the nearest guideline label. ~½ day, but imprecise at boundaries (e.g. AHA "Stage 1" 130–139 is ESC "High–normal", not "Grade 1"). Not recommended as the final form, but acceptable as a stopgap.

## Commit slicing
1. `feat: add Guideline model + per-guideline BP classifier`
2. `feat: persist guideline preference`
3. `feat: derive BP classification for display (rows, badge, risk)`
4. `feat: guideline-aware PDF labels`
5. `feat: settings screen with guideline selector`
6. `feat: localize guideline names + clinical labels`
