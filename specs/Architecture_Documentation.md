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
│                           # ExportReadingsUseCase, ImportReadingsUseCase,
│                           # SyncToHealthConnectUseCase, CalculateHealthRiskUseCase
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

### Manual-Entry Data Flow
Dashboard "pencil" FAB navigates to Validation with no OCR values. Sliders are pre-populated from the last saved reading (default 120/80/60 on first launch). Steps 1–4 are skipped entirely.

---

## 1.5 Reactive Deletion Flow (5-second Undo)

1. User swipes a row → `DashboardViewModel.requestDelete()`.
2. Row is immediately hidden in UI (removed from `pendingDeletions` filter).
3. `ShowUndoSnackbar` event fires; 5-second coroutine delay starts.
4. If "Undo" is tapped → job cancelled, row restored. No DB write.
5. If delay elapses → `repository.delete(id)` called; Room emission confirms removal.

---

## 1.6 Reminder System

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

## 1.7 Health Connect Sync

`SyncToHealthConnectUseCase` upserts all local readings as `BloodPressureRecord` + `HeartRateRecord` entries using stable `Metadata.manualEntry(clientRecordId)` — repeat syncs are idempotent.

Permission flow: `DashboardViewModel` checks `getSdkStatus()` → requests `WRITE_BLOOD_PRESSURE` + `WRITE_HEART_RATE` via `PermissionController.createRequestPermissionResultContract()`.

Manifest requirements: `<queries>` for package visibility (API 30+), `VIEW_PERMISSION_USAGE + HEALTH_PERMISSIONS` intent filter (API 34+).

---

## 1.8 API Key Secure Storage

The Gemini API key is **never baked into the APK binary**. It is:

1. Entered once by the user in the Profile screen.
2. Stored in `EncryptedSharedPreferences` (AES-256-GCM, backed by Android Keystore).
3. Read at runtime by `GeminiOcrRecognizer` via `ApiKeyRepository`.

A `BuildConfig.GEMINI_API_KEY` populated from `local.properties` acts as a silent dev-time fallback only.

`ApiKeyRepository.observeHasKey(): Flow<Boolean>` is observed by `DashboardViewModel` to show/hide the camera FAB in real time.

---

## 1.9 DataStore Layout

A single `DataStore<Preferences>` instance (`datastore/user_profile.preferences_pb`) holds two logical namespaces:

| Key prefix | Owner | Fields |
|---|---|---|
| *(none)* | `UserProfileRepositoryImpl` | `age`, `sex`, `weight_kg`, `height_cm`, `is_smoker`, `has_diabetes`, `activity_level` |
| `reminder_*` | `ReminderRepositoryImpl` | `reminder_is_enabled`, `reminder_morning_minutes`, `reminder_evening_minutes` |
