# Implementation Plan — Structured Contextual Tags

**Goal:** Let users attach quick, structured tags ("post-medication," "caffeine," "salty meal," "high stress," "poor sleep," …) to each reading, so trends become trigger-finding tools instead of just lines. The report calls this "non-negotiable" for a modern BP app.

**Market gap addressed:** "No structured contextual tags — only free-text notes" (market-comparison §3.3). Also unlocks correlation insights (see the On-Device Insights plan).

---

## ⚠️ Headline risk: the database migration

The DB is built with **`fallbackToDestructiveMigration()`** ([AppModule.kt:38](../app/src/main/java/com/zandaulion/bpdigitized/di/AppModule.kt)) and is at **version 2** ([BPDatabase.kt:14](../app/src/main/java/com/zandaulion/bpdigitized/data/local/BPDatabase.kt)). That means **bumping the schema version without a real migration WIPES every user's history on update.** That was tolerable pre-launch; now that 1.0.1 is shipping, it is not.

**This plan must add a real `Migration(2 → 3)`** and register it. Keep `fallbackToDestructiveMigration()` only as a last-resort safety net — the explicit migration takes precedence and preserves data.

---

## Scope

**In scope**
- A curated `BpTag` enum (multi-select), stored per reading.
- Real Room migration v2→v3 adding a `tags` column (no data loss).
- Tag picker on the validation screen; tags shown on rows; tags in CSV/PDF.

**Out of scope (later)**
- Free-text custom tags (start with a fixed, localizable set).
- Tag-based filtering UI and correlation insights (the latter is the Insights plan).

---

## Step-by-step

### Step 1 — Domain model
```kotlin
// domain/model/BpTag.kt
import kotlinx.serialization.Serializable

@Serializable
enum class BpTag {
    BEFORE_MEDICATION, AFTER_MEDICATION, CAFFEINE, ALCOHOL, SALTY_MEAL,
    EXERCISE, STRESS, POOR_SLEEP, RESTING, ON_WAKING,
}
```
Add to `BPReading` with a back-compat default ([BPReading.kt](../app/src/main/java/com/zandaulion/bpdigitized/domain/model/BPReading.kt)):
```kotlin
val tags: List<BpTag> = emptyList(),
```
The default keeps existing JSON import files valid (kotlinx.serialization).

### Step 2 — Room entity + TypeConverter + migration
**Entity** ([BPReadingEntity.kt](../app/src/main/java/com/zandaulion/bpdigitized/data/local/BPReadingEntity.kt)): add a column, stored as a comma-joined string of enum names:
```kotlin
val tags: String = "",   // e.g. "CAFFEINE,STRESS"
```
Update `toDomain()/toEntity()` to split/join:
```kotlin
fun BPReadingEntity.toDomain() = BPReading(
    id, timestamp, systolic, diastolic, pulse, category, notes,
    tags = tags.split(',').filter { it.isNotBlank() }.map(BpTag::valueOf),
)
fun BPReading.toEntity() = BPReadingEntity(
    id, timestamp, systolic, diastolic, pulse, category, notes,
    tags = tags.joinToString(",") { it.name },
)
```
> Storing a delimited string in one column avoids a join table and needs no extra DAO. (A `List<BpTag>` Room `TypeConverter` is an alternative, but the column-of-string is simpler and migration-friendly.)

**Migration + version bump** ([BPDatabase.kt](../app/src/main/java/com/zandaulion/bpdigitized/data/local/BPDatabase.kt)):
```kotlin
@Database(entities = [BPReadingEntity::class], version = 3, exportSchema = false)
...
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bp_readings ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}
```
**Register it** in `provideDatabase` ([AppModule.kt:36](../app/src/main/java/com/zandaulion/bpdigitized/di/AppModule.kt)):
```kotlin
Room.databaseBuilder(context, BPDatabase::class.java, "bp.db")
    .addMigrations(MIGRATION_2_3)
    .fallbackToDestructiveMigration()   // keep as last resort only
    .build()
```

### Step 3 — Save path
`ValidationState` ([ValidationViewModel.kt:18](../app/src/main/java/com/zandaulion/bpdigitized/presentation/validation/ValidationViewModel.kt)) gains `selectedTags: Set<BpTag> = emptySet()`; add `toggleTag(tag)`; include `tags = current.selectedTags.toList()` in the `BPReading(...)` built in `save()`. When editing/pre-populating from the last reading, leave tags empty (tags are per-measurement context).

### Step 4 — Validation UI (tag picker)
In [ValidationScreen.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/validation/ValidationScreen.kt), below the sliders / near the Notes field, add a `FlowRow` of `FilterChip`s:
```kotlin
FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    BpTag.entries.forEach { tag ->
        FilterChip(
            selected = tag in state.selectedTags,
            onClick = { viewModel.toggleTag(tag) },
            label = { Text(stringResource(tag.labelRes())) },
        )
    }
}
```
(Uses `androidx.compose.foundation.layout.FlowRow`.)

### Step 5 — Show tags on reading rows
In `ReadingRow` ([DashboardScreen.kt:632](../app/src/main/java/com/zandaulion/bpdigitized/presentation/dashboard/DashboardScreen.kt)), when `reading.tags` is non-empty, render a compact chip row (or a "· "-joined caption) under the value line, styled like the existing notes line.

### Step 6 — Exports
- **CSV** ([ExportCsvUseCase.kt](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/ExportCsvUseCase.kt)): add a `tags` column (semicolon-joined enum names, quoted).
- **PDF** ([ExportPdfUseCase.kt](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/ExportPdfUseCase.kt)): append tag labels into the table's Notes cell (e.g., "[caffeine, stress] " + notes), or add a slim column. Keep it compact — the table is width-constrained.
- **JSON**: works automatically via the serializable default; verify round-trip.

### Step 7 — Strings + localization
- One label per `BpTag` (+ a section header like "What might have affected this reading?").
- Add `BpTag.labelRes()` to [StringResources.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/extensions/StringResources.kt).
- Replicate across all 12 locales.

### Step 8 — Build & verify (migration is the key test)
```
./gradlew :app:assembleDebug
```
- [ ] **Upgrade test:** install the *current* build, add readings, then install this build over it → all readings survive and show no tags. (This proves the migration; destructive fallback would wipe them.)
- [ ] Add a reading with 2–3 tags → chips persist and show on the row.
- [ ] CSV has a populated `tags` column; PDF shows tags; JSON export→import round-trips tags.
- [ ] Tag labels localized when the app language changes.

---

## Effort & risk
- **Effort:** ~1 day. The tag set + UI is quick; the migration and export wiring need care.
- **Risk:** medium — **entirely concentrated in the migration.** Get Step 2 right (and run the upgrade test) and the rest is low-risk. Everything else is additive with safe defaults.

## Commit slicing
1. `feat: add BpTag model + Room migration v2→v3 for tags column`
2. `feat: tag picker on validation + save tags`
3. `feat: show tags on reading rows`
4. `feat: include tags in CSV/PDF exports`
5. `feat: localize tag labels`

## Enables next
Tag data is the input for **tag-correlation insights** in the On-Device Insights plan (e.g., "readings tagged 'salty meal' average +9 mmHg").
