# Architecture Documentation: BP Digitizer App

**Objective:** Native Android application that scans blood pressure readings from non-smart digital monitors using bundled on-device Hearth models, allows user validation, calculates health risk, stores history, and visualises trends.
**Target Platform:** Android (API 26+); release APKs target ARM64 from 1.6.2
**Language:** Kotlin

---

## 1.1 Architectural Pattern

The application follows **MVVM + Clean Architecture** with Single Source of Truth (SSOT) and Unidirectional Data Flow (UDF).

* **UI Layer:** Jetpack Compose (Material 3) + `StateFlow` / one-shot `Channel` events
* **Domain Layer:** Use cases, repository interfaces, domain models
* **Data Layer:** Room (readings), DataStore Preferences (user profile + reminders), AlarmManager (reminders)
* **DI:** Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `@AndroidEntryPoint`)

---

## 1.2 Tech Stack & Dependencies

| Category | Library |
|---|---|
| UI | Jetpack Compose BOM 2026.05+, Material 3 |
| Camera | CameraX (`camera2`, `lifecycle`, `view`) |
| OCR models | Bundled Hearth detector and DigitNet ONNX exports |
| Local DB | Room 2.8+ |
| Charts | Vico 2.0 (`compose`, `compose-m3`, `core`) |
| Serialisation | `kotlinx.serialization-json` |
| Concurrency | Kotlin Coroutines & Flow |
| DI | Hilt 2.59+ |
| Navigation | Navigation Compose 2.8+ |
| Preferences | AndroidX DataStore Preferences 1.1+ |
| OCR runtime | Bundled native ONNX Runtime Android 1.24.3 (CPU) |
| Health | AndroidX Health Connect 1.1-rc+ |
| Permissions | Accompanist Permissions 0.36+ |
| Image | Coil Compose 2.7, ExifInterface 1.3 |
| Reminders | Android `AlarmManager` (exact alarms) + `NotificationCompat` |
| PDF | `android.graphics.pdf.PdfDocument` (built-in, no extra dependency) |

---

## 1.3 Folder Structure

```text
com.zandaulion.bpdigitized
├── data/
│   ├── local/              # Room Database, DAOs, Entities
│   ├── ocr/                # HearthOcrRecognizer, native Hearth processing core
│   ├── reminder/           # ReminderType, ReminderScheduler, NotificationHelper
│   │                       # ReminderReceiver, BootReceiver, SnoozeReceiver
│   └── repository/         # BPRepositoryImpl, UserProfileRepositoryImpl,
│                           # ReminderRepositoryImpl
├── di/
│   └── AppModule.kt        # Hilt modules (DataModule, RepositoryModule)
├── domain/
│   ├── backup/             # BackupCodec: PWA-compatible JSON and profile patches
│   ├── model/              # BPReading, BPCategory, UserProfile, HealthAssessment,
│   │                       # ReminderSettings
│   ├── repository/         # BPRepository, UserProfileRepository,
│   │                       # ReminderRepository
│   └── usecase/            # ParsedReading, CalculateBPZoneUseCase,
│                           # ExportReadingsUseCase (JSON), ExportCsvUseCase,
│                           # ExportPdfUseCase, ImportReadingsUseCase, RestoreBackupUseCase,
│                           # SyncToHealthConnectUseCase,
│                           # PushReadingToHealthConnectUseCase,
│                           # CalculateHealthRiskUseCase
└── presentation/
    ├── capture/            # CaptureScreen, CaptureViewModel, ImagePreprocessor
    ├── components/         # CategoryBadge, HealthRiskCard
    ├── dashboard/          # DashboardScreen, DashboardViewModel,
    │                       # BPTrendChart, BPScatter3DChart
    ├── profile/            # ProfileScreen, ProfileViewModel
    ├── reminders/          # RemindersScreen, RemindersViewModel
    ├── theme/              # Color, Theme
    ├── validation/         # ValidationScreen, ValidationViewModel
    └── AppNavHost.kt       # Navigation graph + Routes
```

---

## 1.4 Data Flow: Camera Scan → Database

1. **CameraX** takes a still photo and saves it to internal storage.
2. **`ImagePreprocessor.preprocessForHearth()`** applies EXIF orientation, retains RGB, and limits the longest edge to 1920 px.
3. **`HearthOcrRecognizer.recognize()`** runs the bundled detector and DigitNet on a background executor using native ONNX Runtime. No image upload, WebView or runtime download occurs.
4. Kotlin assembly and agreement-based portrait, lighting and perspective fallbacks produce a complete plausible triplet or a refusal.
5. **`CaptureViewModel`** emits a `ParseComplete` event only for a usable triplet; otherwise it stays on the camera and offers a retake/manual entry.
6. **`ValidationScreen`** pre-populates sliders from OCR values (or from the last saved reading on the manual path). User corrects if needed.
7. **`ValidationViewModel.save()`** computes `BPCategory`, builds a `BPReading`, inserts via **`BPRepository`** → Room.
8. Saving writes only to the local database. Health Connect export is explicitly initiated by the user from the dashboard.

### Manual-Entry Data Flow
Dashboard "pencil" FAB navigates to Validation with no OCR values. Sliders are pre-populated from the last saved reading (default 120/80/60 on first launch). Steps 1–4 are skipped entirely.

---

## 1.5 Reactive Deletion Flow (Instant Delete + Undo)

1. User swipes a `SwipeableReadingRow` → `DashboardViewModel.requestDelete()` is called.
2. `repository.delete(id)` removes the reading from Room immediately (no delay, no in-memory filter).
3. Room's reactive `Flow` emits the updated list → `LazyColumn` removes the row.
4. A `ShowUndoSnackbar` one-shot event fires; the Material 3 Snackbar shows with an **Undo** action.
5. If **Undo** is tapped → `DashboardViewModel.undoDelete(reading)` calls `repository.insert(reading)` with the original ID; Room emits again and the row reappears.
6. If the snackbar is dismissed or times out → nothing further happens (the record is already gone from Room).

**Compose state note:** `SwipeableReadingRow` creates its `SwipeToDismissBoxState` with plain `remember` (not `rememberSaveable`). This is intentional — `rememberSaveable` would persist the dismissed offset across composition entries (keyed by `reading.id` in the `LazyColumn`). When an undo-restored item re-enters composition it would otherwise: (a) appear visually dismissed (only showing the trash-icon background), and (b) have `SwipeToDismissBox`'s internal `LaunchedEffect` fire `onDismiss` again, re-deleting the just-restored reading. The `onDismiss` lambda is wrapped in `rememberUpdatedState` to stay stable across recompositions and avoid spurious effect re-triggers.

---

## 1.6 Dashboard Charts (Trend & Scatter)

The dashboard chart area has two views, **Trend** (`BPTrendChart`) and **Scatter** (`BPScatter3DChart`). Both consume the same `ui.chartReadings` (already range-filtered and `Avg`-smoothed by the ViewModel), so the range chips and smoothing setting drive both.

In **portrait**, a two-segment `SegmentedButton` toggle below the range chips picks which view fills the chart area. The chosen mode is exposed as a separate `chartMode: StateFlow<ChartMode>` on `DashboardViewModel` (kept out of the 5-arg `ui` combine for arity) and **persisted in DataStore** via `ChartSettingsRepository.observeChartMode()/setChartMode()` (key `chart_mode`, storing the `ChartMode` enum name; blank/unknown → `TREND`).

In **landscape**, the screen goes full-screen and shows both views side by side in a weighted `Row` — Trend at `weight(2f)`, Scatter at `weight(1f)` — so trend gets two-thirds of the width and scatter the remaining third. The toggle is hidden and `chart_mode` is ignored; the range/`Avg` chips still drive both.

### Trend chart (BPTrendChart)

**Component:** `presentation/dashboard/BPTrendChart.kt`  
**Library:** Vico 2.0 (`compose`, `compose-m3`, `core`)

### Data mapping
Readings are passed in as a time-sorted `List<BPReading>`. They are mapped to integer x-indices (0, 1, 2 …) and two y-series (systolic, diastolic) via `CartesianChartModelProducer`. A `key(readings.size)` wrapper resets all Vico internal state whenever the list length changes, preventing a stale-bounds crash when narrowing the range filter.

### Zoom & scroll
`VicoScrollState` and `VicoZoomState` are created outside the `key` block and passed explicitly to `CartesianChartHost`, enabling pinch-to-zoom and pan gestures. Both states are backed by `MutableFloatState` and are therefore observable from Compose.

### Adaptive X-axis labels
The X-axis label format switches between `M/d` (multi-day view) and `M/d HH:mm` (sub-day view) based on the **currently visible time span** in the viewport — not the full data range and not the zoom factor.

The visible span is computed reactively inside `derivedStateOf`:

```
visibleFraction = chartWidth / (chartWidth + scrollState.maxValue)
visibleCount    = n × visibleFraction
startIdx        = (n − visibleCount) × (scroll / maxScroll)
visibleSpanMs   = readings[endIdx].timestamp − readings[startIdx].timestamp
```

`chartWidth` is captured via `Modifier.onSizeChanged` on `CartesianChartHost` and stored in a `MutableFloatState`. Because `chartWidthState.floatValue`, `scrollState.value`, and `scrollState.maxValue` are all `MutableFloatState` reads inside `derivedStateOf`, the label format updates automatically on every pinch-zoom or scroll gesture.

**Why not use `zoomState.value` directly?** Vico's initial zoom is `Zoom.Content` — the factor needed to fit all data — not `1.0`. A formula based on `n / zoom` produces the wrong count from the first frame. The geometry-based formula (`chartWidth / totalContentWidth`) is correct regardless of the starting zoom value.

### Scatter plot (BPScatter3DChart)

**Component:** `presentation/dashboard/BPScatter3DChart.kt`
**Rendering:** Compose `Canvas` (no Vico) — mirrors the PDF overview scatter so the two surfaces match visually.

Each reading is one dot: **diastolic on X, systolic on Y and pulse on Z**.
The initial view is head-on; drag to rotate and reveal pulse depth. Dots are
coloured by recency, with an oldest-to-newest legend. Dashed reference lines
mark SYS 120 and DIA 80. Tap a dot for its reading details and timestamp.

## 1.7 Reminder System

**Mechanism:** `AlarmManager.setExactAndAllowWhileIdle()` (bypasses Doze mode).

**Components:**

| Class | Role |
|---|---|
| `ReminderScheduler` | Wraps `AlarmManager`; schedules/cancels `MORNING`, `EVENING`, `SNOOZE` alarms |
| `ReminderReceiver` | `@AndroidEntryPoint` broadcast; posts notification, reschedules next-day alarm |
| `BootReceiver` | `@AndroidEntryPoint` broadcast; re-schedules on `BOOT_COMPLETED` |
| `SnoozeReceiver` | `@AndroidEntryPoint` broadcast; schedules 15-min snooze (skips if overlaps next regular alarm) |
| `NotificationHelper` | Creates high-importance channel; defines notification IDs |
| `ReminderRepository` | DataStore-backed; persists `isEnabled`, `morningMinutes`, `eveningMinutes` |

**Times** are stored as minutes-since-midnight (`Int`). Defaults: 07:30 morning, 20:00 evening.

**Permissions required:** `POST_NOTIFICATIONS` (runtime, API 33+), `SCHEDULE_EXACT_ALARM` (runtime prompt on API 34+), `RECEIVE_BOOT_COMPLETED`.

---

## 1.8 Health Connect Integration

### Bulk Sync (`SyncToHealthConnectUseCase`)
Upserts all local readings as `BloodPressureRecord` + `HeartRateRecord` entries using stable `Metadata.manualEntry(clientRecordId)` — repeat syncs are idempotent.

Permission flow: `DashboardViewModel` checks `getSdkStatus()` → requests `WRITE_BLOOD_PRESSURE` + `WRITE_HEART_RATE` via `PermissionController.createRequestPermissionResultContract()`.

### Explicit export only
Saving a reading writes to Room only. Health Connect receives readings when the
user chooses the dashboard sync action and grants its permissions. The core app
works without Health Connect installed or authorized.

### Manifest requirements
`<queries>` block for package visibility (API 30+); `VIEW_PERMISSION_USAGE + HEALTH_PERMISSIONS` intent filter (API 34+).

---

## 1.9 AI / OCR Backend

OCR runs entirely on-device with both model files and native ONNX Runtime packaged in the APK. Internet permissions are explicitly removed from the merged manifest; no account, subscription, key, browser, Google Play Services or model download is required. Automatic cloud backup and device transfer exclude app data. See [native OCR architecture and validation](../docs/Hearth_OCR.md).

---

## 1.10 DataStore Layout

A single `DataStore<Preferences>` instance (`datastore/user_profile.preferences_pb`) holds two logical namespaces:

| Key | Owner | Type | Notes |
|---|---|---|---|
| `profile_birth_year` | `UserProfileRepositoryImpl` | `Int` | Absent when not set; age computed as `currentYear − birthYear` |
| `profile_sex` | `UserProfileRepositoryImpl` | `String` | |
| `profile_weight_kg` | `UserProfileRepositoryImpl` | `Float` | |
| `profile_height_cm` | `UserProfileRepositoryImpl` | `Float` | |
| `profile_is_smoker` | `UserProfileRepositoryImpl` | `Boolean` | |
| `profile_has_diabetes` | `UserProfileRepositoryImpl` | `Boolean` | |
| `profile_activity_level` | `UserProfileRepositoryImpl` | `String` | |
| `reminder_is_enabled` | `ReminderRepositoryImpl` | `Boolean` | |
| `reminder_morning_minutes` | `ReminderRepositoryImpl` | `Int` | Minutes since midnight |
| `reminder_evening_minutes` | `ReminderRepositoryImpl` | `Int` | Minutes since midnight |

---

## 1.11 PDF Export Engine

PDF generation is handled entirely by **`ExportPdfUseCase`** using `android.graphics.pdf.PdfDocument` (built-in Android API, no extra dependency). All drawing is done on `android.graphics.Canvas` because Vico is Compose-only and cannot render to an off-screen PDF canvas.

**Execution context:** `withContext(Dispatchers.Default)` — never blocks the main thread.

### Page types

| Type | Dimensions | Count |
|---|---|---|
| Landscape chart | 1684 × 1190 px (A4 @ 2×) | 0–3 (one per active time range) |
| Portrait table | 1190 × 1684 px (A4 @ 2×) | ≥ 1 (if any readings in last 30 days) |

### Chart page layout (bottom-up zones)
```
┌─ Header (title, date range, export date) ──────────────────────────┐
│  Divider                                                            │
│                                                                     │
│  Chart area                                                         │
│   - Grid lines every 20 mmHg (Y: 40–180)                           │
│   - Y-axis labels on left (right-aligned) and right (left-aligned) │
│   - Dashed reference lines at SYS 120 and DIA 80                   │
│   - Rotated X-axis date labels (max 10)                            │
│   - Systolic (red) + Diastolic (blue) line + dot series            │
│   - Numbered callout badges on noted readings (max 12)             │
│                                                                     │
│  X-label space (55 px)                                              │
│  Note legend (3 columns, ceil(notes/3) rows × 36 px/row)           │
│  Stats bar (avg / min / max SYS and DIA with colour swatches)      │
├─ Footer (disclaimer) ───────────────────────────────────────────────┤
```

### Chart page skip logic
A range page is omitted when:
- Fewer than 2 readings fall in the range, **or**
- The reading set is identical to the previous page (e.g. "30 d" = "All" when all readings are recent).

### Table pages
- Readings from the **last 30 days**, newest-first.
- ~30 rows per portrait page (computed dynamically from page height − margins).
- Alternating row background; category labels are colour-coded to match the app theme.
- Notes truncated at 52 characters.
- "Last 30 Days" indicated in the page title.


## 1.12 JSON Backup and Restoration

`ExportReadingsUseCase` reads the health profile from DataStore and the full
reading history from Room, then uses `BackupCodec` to create the PWA-compatible
version-1 JSON envelope. Profile-only exports are supported.

`ImportReadingsUseCase` decodes and validates the entire file before any writes.
`DashboardViewModel` holds the pending backup in memory and asks whether to
restore its profile as well as readings. Cancel makes no changes.
`RestoreBackupUseCase` merges readings by exact timestamp, then applies the
selected profile patch: omitted fields preserve local values, explicit null
clears optional values. Storage failures propagate to the UI. Room and DataStore
are separate stores, so a failed restore may require a retry; timestamp
deduplication makes retrying a partial reading merge safe.

See [JSON Backups](../docs/JSON_Backups.md) for the schema and compatibility rules.

## 1.13 Release Packaging

Release builds filter native libraries to `arm64-v8a`; debug builds keep their
default ABI support for emulator testing. Both ONNX models and native CPU
inference remain bundled. Code shrinking is disabled. Version 1.6.2 is 53.7 MB
versus 135.0 MB for 1.6.1; 32-bit ARM and x86 devices need a separately built APK.
See [release notes](../docs/Release_Notes.md) for validation and compatibility.
