# Architecture Documentation: BP Digitizer App

**Objective:** Native Android application that scans blood pressure readings from non-smart digital monitors via an AI vision API, allows user validation, calculates health risk, stores history, and visualises trends.
**Target Platform:** Android (API 26+)
**Language:** Kotlin

---

## 1.1 Architectural Pattern

The application follows **MVVM + Clean Architecture** with Single Source of Truth (SSOT) and Unidirectional Data Flow (UDF).

* **UI Layer:** Jetpack Compose (Material 3) + `StateFlow` / one-shot `Channel` events
* **Domain Layer:** Use cases, repository interfaces, domain models
* **Data Layer:** Room (readings), DataStore Preferences (user profile + reminders), EncryptedSharedPreferences (API key), AlarmManager (reminders)
* **DI:** Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `@AndroidEntryPoint`)

---

## 1.2 Tech Stack & Dependencies

| Category | Library |
|---|---|
| UI | Jetpack Compose BOM 2026.05+, Material 3 |
| Camera | CameraX (`camera2`, `lifecycle`, `view`) |
| AI OCR | Gemini 2.5 Flash REST API (vision) |
| Local DB | Room 2.8+ |
| Charts | Vico 2.0 (`compose`, `compose-m3`, `core`) |
| Serialisation | `kotlinx.serialization-json` |
| Concurrency | Kotlin Coroutines & Flow |
| DI | Hilt 2.59+ |
| Navigation | Navigation Compose 2.8+ |
| Preferences | AndroidX DataStore Preferences 1.1+ |
| Secure Storage | AndroidX Security Crypto 1.1 (`EncryptedSharedPreferences`) |
| Health | AndroidX Health Connect 1.1-rc+ |
| Permissions | Accompanist Permissions 0.36+ |
| Image | Coil Compose 2.7, ExifInterface 1.3 |
| Reminders | Android `AlarmManager` (exact alarms) + `NotificationCompat` |
| PDF | `android.graphics.pdf.PdfDocument` (built-in, no extra dependency) |

---

## 1.3 Folder Structure

```text
com.example.bpdigitizer
├── data/
│   ├── local/              # Room Database, DAOs, Entities
│   ├── ocr/                # GeminiOcrRecognizer, TesseractRecognizer
│   ├── reminder/           # ReminderType, ReminderScheduler, NotificationHelper
│   │                       # ReminderReceiver, BootReceiver, SnoozeReceiver
│   └── repository/         # BPRepositoryImpl, UserProfileRepositoryImpl,
│                           # ReminderRepositoryImpl, ApiKeyRepositoryImpl
├── di/
│   └── AppModule.kt        # Hilt modules (DataModule, RepositoryModule)
├── domain/
│   ├── model/              # BPReading, BPCategory, UserProfile, HealthAssessment,
│   │                       # ReminderSettings
│   ├── repository/         # BPRepository, UserProfileRepository,
│   │                       # ReminderRepository, ApiKeyRepository
│   └── usecase/            # ParseOcrTextUseCase, CalculateBPZoneUseCase,
│                           # ExportReadingsUseCase (JSON), ExportCsvUseCase,
│                           # ExportPdfUseCase, ImportReadingsUseCase,
│                           # SyncToHealthConnectUseCase,
│                           # PushReadingToHealthConnectUseCase,
│                           # CalculateHealthRiskUseCase
└── presentation/
    ├── capture/            # CaptureScreen, CaptureViewModel, ImagePreprocessor
    ├── components/         # CategoryBadge, HealthRiskCard
    ├── dashboard/          # DashboardScreen, DashboardViewModel, BPTrendChart
    ├── profile/            # ProfileScreen, ProfileViewModel
    ├── reminders/          # RemindersScreen, RemindersViewModel
    ├── theme/              # Color, Theme
    ├── validation/         # ValidationScreen, ValidationViewModel
    └── AppNavHost.kt       # Navigation graph + Routes
```

---

## 1.4 Data Flow: Camera Scan → Database

1. **CameraX** takes a still photo and saves it to internal storage.
2. **`ImagePreprocessor.preprocessLight()`** EXIF-orients and downscales to max 1024 px.
3. **`GeminiOcrRecognizer.recognize()`** base64-encodes the JPEG and POSTs it to the Gemini 2.5 Flash REST endpoint with a structured prompt asking for `{"systolic", "diastolic", "pulse"}`.
4. The JSON response is parsed into a **`ParsedReading`** (nulls for any value Gemini couldn't read with confidence).
5. **`CaptureViewModel`** emits a `ParseComplete` event → Navigation pushes to Validation.
6. **`ValidationScreen`** pre-populates sliders from OCR values (or from the last saved reading on the manual path). User corrects if needed.
7. **`ValidationViewModel.save()`** computes `BPCategory`, builds a `BPReading`, inserts via **`BPRepository`** → Room.
8. **`PushReadingToHealthConnectUseCase`** is launched in a child coroutine immediately after the DB insert — see §1.7.

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

## 1.6 Trend Chart (BPTrendChart)

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

### Per-Reading Auto-Push (`PushReadingToHealthConnectUseCase`)
Called from `ValidationViewModel.save()` immediately after the new reading is persisted to Room.

- Launched as a **child coroutine** of the save job — navigation back to Dashboard is not delayed.
- **Best-effort, silent:** checks `getSdkStatus()` and `getGrantedPermissions()` before attempting to write; swallows all exceptions.
- No permission prompting — if permissions have not yet been granted the push is silently skipped. Prompting is handled exclusively by the manual sync button.
- Uses the same stable `clientRecordId` scheme as the bulk sync, so a subsequent bulk sync will upsert rather than duplicate.

### Manifest requirements
`<queries>` block for package visibility (API 30+); `VIEW_PERMISSION_USAGE + HEALTH_PERMISSIONS` intent filter (API 34+).

---

## 1.9 API Key Secure Storage

The Gemini API key is **never baked into the APK binary**. It is:

1. Entered once by the user in the Profile screen.
2. Stored in `EncryptedSharedPreferences` (AES-256-GCM, backed by Android Keystore).
3. Read at runtime by `GeminiOcrRecognizer` via `ApiKeyRepository`.

A `BuildConfig.GEMINI_API_KEY` populated from `local.properties` acts as a silent dev-time fallback only.

`ApiKeyRepository.observeHasKey(): Flow<Boolean>` is observed by `DashboardViewModel` to show/hide the camera FAB in real time.

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
