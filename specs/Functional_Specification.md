# Functional Specification: BP Digitizer App

**Objective:** Native Android application that scans blood pressure readings from non-smart digital monitors, allows user validation, calculates health risk, stores history, and visualises trends.
**Target Platform:** Android (API 26+)
**Language:** Kotlin

---

## 2.1 Core Data Models

### BPReading (Domain Model)
| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | Room auto-generated |
| `timestamp` | `Long` | Epoch ms |
| `systolic` | `Int` | |
| `diastolic` | `Int` | |
| `pulse` | `Int?` | Nullable — not all monitors show it |
| `category` | `BPCategory` | Computed on save |
| `notes` | `String?` | User free-text |

### BP Category Logic (AHA Guidelines)
| Category | Rule |
|---|---|
| **Normal** | Systolic < 120 AND Diastolic < 80 |
| **Elevated** | Systolic 120–129 AND Diastolic < 80 |
| **Stage 1** | Systolic 130–139 OR Diastolic 80–89 |
| **Stage 2** | Systolic ≥ 140 OR Diastolic ≥ 90 |
| **Crisis** | Systolic > 180 AND/OR Diastolic > 120 |

### ReminderSettings (Domain Model)
| Field | Type | Default |
|---|---|---|
| `isEnabled` | `Boolean` | `false` |
| `morningMinutes` | `Int` | 450 (07:30) |
| `eveningMinutes` | `Int` | 1200 (20:00) |

---

## 2.2 Screen Specifications

### Screen 1: Dashboard (Home)

**Top App Bar actions (left → right):**
- Bell icon → Reminders screen
- Person icon → Health Profile screen
- Download icon → Import JSON file picker
- Share icon → Export readings (disabled when list is empty)
- Heart monitor icon → Sync to Health Connect (disabled when list is empty)

**Floating Action Buttons:**
- *No API key saved:* single large pencil FAB → manual entry
- *API key saved:* small pencil FAB (left) + large camera FAB (right) → camera scan

**Content:**
- Range chips: `7d / 30d / 90d / All` (default `30d`). Scopes the chart only, not the history list.
- Vico line chart: Systolic and Diastolic as two lines, x-axis labelled with dates (M/d format).
- `HealthRiskCard` (shown when readings exist): personalised risk assessment based on the latest reading + user profile.
- `LazyColumn` of all readings, newest first. Each row: date/time, `SYS/DIA · pulse bpm`, category badge, notes (italic). Swipe either direction to delete (5-second undo snackbar).

### Screen 2: Capture (Camera)

- Full-screen CameraX `PreviewView` with a rectangular overlay guide.
- "Capture" button triggers a still photo, preprocessing, and Gemini OCR.
- Loading indicator shown while Gemini processes the image.
- Navigates to Validation on completion (OCR values pre-fill the sliders).
- Back button returns to Dashboard.

### Screen 3: Validation / Manual Entry

**Arrived from camera:** Thumbnail of captured image at top (tap for full-screen viewer with pinch-to-zoom). "Retake" button available.
**Arrived from pencil FAB:** No thumbnail. Sliders pre-populated from last saved reading (first launch defaults: 120 / 80 / 60).

**UI:**
- Three `BpValueSlider` cards: Systolic (60–250), Diastolic (40–160), Pulse (30–200).
  - Each card: label, large value display, continuous `Slider`, `+` / `−` icon buttons for fine adjustment.
- Notes `OutlinedTextField` (optional, single line).
- "Save Reading" button (disabled until Systolic > Diastolic).

### Screen 4: Health Profile

Accessible via person icon in Dashboard top bar. Scroll-able form covering:

- **Basic Information:** Age (years), Biological sex (chips: Male / Female / Prefer not to say)
- **Body Measurements:** Weight (kg, lbs hint), Height (cm, ft″ hint), live BMI preview with category label
- **Health Factors:** Current smoker toggle, Diabetes/pre-diabetes toggle
- **Physical Activity:** Exercise level chips (Sedentary / Lightly active / Moderately active / Very active)
- **AI Scan:**
  - *No key:* description text with tappable link to `aistudio.google.com/apikey`, password-style input field, "Save API Key" button
  - *Key saved:* check icon + "Gemini API key saved / Camera scan is enabled", "Clear" button

Top bar "Save" button persists profile; navigates back on success.

All data stays on-device (DataStore Preferences).

### Screen 5: Reminders

Accessible via bell icon in Dashboard top bar.

- **Permission banners** (shown only when relevant):
  - `POST_NOTIFICATIONS` not granted → "Grant Permission" button
  - `SCHEDULE_EXACT_ALARM` not available → "Open Settings" button
- **Enable Reminders** master switch (Card). Toggling on checks `canScheduleExactAlarms()` first; redirects to system settings if missing.
- **Morning Check** time card (tappable when enabled) — Material 3 `TimePicker` dialog on tap
- **Evening Check** time card (tappable when enabled) — Material 3 `TimePicker` dialog on tap
- Times displayed as `HH:MM` (24-hour). Stored as minutes-since-midnight.

Notifications include "Log Now" (opens app) and "Snooze (15m)" actions. Alarms self-reschedule daily. Device reboot restores active alarms.

---

## 2.3 System Features

### 2.3.1 AI-Assisted OCR (Gemini 2.5 Flash)

1. Captured photo is EXIF-oriented and downscaled to max 1024 px.
2. Encoded as base64 JPEG and sent to the Gemini 2.5 Flash REST API.
3. Prompt instructs the model to return only `{"systolic": <int>, "diastolic": <int>, "pulse": <int>}`, omitting any value it cannot read with confidence.
4. Response is parsed; nulls for missing keys are passed to the Validation screen as blank sliders (user fills manually).

**API key management:** User pastes their key in the Health Profile screen. Stored in `EncryptedSharedPreferences` (Android Keystore). Camera FAB is hidden until a key is present.

### 2.3.2 JSON Export

- Triggered by the share icon in the Dashboard top bar.
- Fetches all readings from Room (unaffected by range chip filter).
- Serialises to a JSON array via `kotlinx.serialization`. Each entry includes a human-readable `dateTime` field alongside `timestamp` for portability.
- Writes to a temp file in cache dir, shares via `Intent.ACTION_SEND` + `FileProvider`.

### 2.3.3 JSON Import

- Triggered by the download icon in the Dashboard top bar.
- Opens an `OpenDocument` file picker (accepts `application/json` + `*/*`).
- Reads and parses the file with `isLenient = true` and `ignoreUnknownKeys = true` for forward/backward compatibility.
- Deduplicates by exact timestamp — only readings not already in the database are inserted.
- Shows a snackbar: "Imported N reading(s)" or "No new readings to import".

### 2.3.4 Health Connect Sync

- Triggered by the heart monitor icon in the Dashboard top bar.
- Checks `HealthConnectClient.getSdkStatus()` first; prompts to install if unavailable.
- Requests `WRITE_BLOOD_PRESSURE` + `WRITE_HEART_RATE` if not already granted.
- Upserts all local readings using stable `clientRecordId` values — safe to run multiple times without creating duplicates.

### 2.3.5 Health Risk Assessment

Shown as a `HealthRiskCard` at the top of the reading list when at least one reading exists.

Computed by `CalculateHealthRiskUseCase` from the most recent reading's `BPCategory` combined with the user's profile (age, sex, BMI, smoking, diabetes, activity level). Surfaces a risk level and contextual guidance. Purely informational; does not replace medical advice.
