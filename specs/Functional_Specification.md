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
- Share icon → Export dropdown menu (disabled when list is empty):
  - **Export as JSON** — full history
  - **Export as CSV** — full history
  - **Export as PDF** — multi-page document (see §2.3.5)
- Heart monitor icon → Sync to Health Connect (disabled when list is empty)

**Floating Action Buttons:**
- Small pencil FAB (left) → manual entry
- Large camera FAB (right) → camera scan via Vertex AI

**Content:**
- Range chips: `7d / 30d / 90d / All` (default `30d`). Scopes the chart only, not the history list.
- **Chart-view toggle** (portrait only): a two-segment switch — `Trend` / `Scatter` — below the range chips selects which view fills the chart area. The selected view is persisted in DataStore (`chart_mode`) so it survives app restarts; defaults to `Trend`. The range chips and the `Avg` smoothing setting drive both views.
- **Vico line chart** (Trend view): Systolic and Diastolic as two colour-coded lines.
  - Y-axis fixed at **40–180 mmHg** on both portrait sides.
  - Dashed reference lines at **SYS 120** and **DIA 80** (AHA normal upper limits).
  - X-axis labelled with dates (M/d format).
- **Scatter plot** (Scatter view): each reading is a dot — diastolic (X) vs systolic (Y) — coloured by its zone, matching the PDF overview scatter.
  - Axis bounds pad ±5 mmHg and snap outward to the nearest 10; dashed reference lines at **SYS 120** / **DIA 80** drawn when in range.
  - Tapping a dot reveals its `SYS/DIA · date` in a small tooltip; a legend below lists the zones present in the range.
- **Landscape orientation** — the dashboard goes full-screen and shows **both** views at once: the Trend chart fills the left **two-thirds** of the width and the Scatter plot the remaining **third**, both full-height. The portrait toggle and all other UI are hidden. The range chips and `Avg` setting drive both charts simultaneously (the saved `chart_mode` is ignored here).
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

After saving, the reading is automatically pushed to Health Connect in the background if permissions are already granted (see §2.3.7).

### Screen 4: Health Profile

Accessible via person icon in Dashboard top bar. Scroll-able form covering:

- **Basic Information:**
  - **Birth year** — 4-digit year (e.g. 1985); computed age shown as supporting text; defaults to `current year − 40`; validates range `(current year − 120)..(current year − 1)`
  - Biological sex (chips: Male / Female / Prefer not to say)
- **Body Measurements:** Weight (kg, lbs hint), Height (cm, ft″ hint), live BMI preview with category label
- **Health Factors:** Current smoker toggle, Diabetes/pre-diabetes toggle
- **Physical Activity:** Exercise level chips (Sedentary / Lightly active / Moderately active / Very active)
- **AI Scan:**
  - Camera scan is always available via Vertex AI; no user API key required

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

**AI backend:** Powered by Vertex AI (Gemini 2.5 Flash). No user API key required.

### 2.3.2 JSON Export

- One option in the **Export** dropdown (share icon, Dashboard top bar).
- Fetches all readings from Room (unaffected by range chip filter).
- Serialises to a JSON array via `kotlinx.serialization`. Each entry includes a human-readable `dateTime` field alongside `timestamp` for portability.
- Writes to a temp file in cache dir, shares via `Intent.ACTION_SEND` + `FileProvider` (`application/json`).

### 2.3.3 JSON Import

- Triggered by the download icon in the Dashboard top bar.
- Opens an `OpenDocument` file picker (accepts `application/json` + `*/*`).
- Reads and parses the file with `isLenient = true` and `ignoreUnknownKeys = true` for forward/backward compatibility.
- Deduplicates by exact timestamp — only readings not already in the database are inserted.
- Shows a snackbar: "Imported N reading(s)" or "No new readings to import".

### 2.3.4 Health Connect Sync (Bulk)

- Triggered by the heart monitor icon in the Dashboard top bar.
- Checks `HealthConnectClient.getSdkStatus()` first; prompts to install if unavailable.
- Requests `WRITE_BLOOD_PRESSURE` + `WRITE_HEART_RATE` if not already granted.
- Upserts all local readings using stable `clientRecordId` values — safe to run multiple times without creating duplicates.

### 2.3.5 PDF Export

- One option in the **Export** dropdown.
- Generated natively with `android.graphics.pdf.PdfDocument` (no extra dependency).
- Shared via `Intent.ACTION_SEND` + `FileProvider` (`application/pdf`).
- **Generation runs off the main thread** (`Dispatchers.Default`).

**Page structure:**

| Pages | Orientation | Contents |
|---|---|---|
| Up to 3 chart pages | Landscape A4 @ 2× (1684 × 1190 px) | One page per active time range: Last 7 Days · Last 30 Days · All Readings. A range page is omitted when fewer than 2 readings exist for it, or when its reading set is identical to the previous page. |
| 1+ table pages | Portrait A4 @ 2× (1190 × 1684 px) | All readings from the **last 30 days**, newest first. |

**Chart page contents:**
- Title + date range + export date in header.
- Y-axis: fixed 40–180 mmHg, grid lines every 20 units, labels on both the left and right sides.
- Dashed reference lines at SYS 120 and DIA 80.
- Rotated X-axis date labels (up to 10 evenly distributed).
- Systolic (red) and Diastolic (blue) line series with dots. Dot size scales with reading count.
- Numbered callout badges on dots where the reading has notes (max 12 per page).
- Stats bar: average / min / max SYS and DIA.
- Note legend (3 columns) listing badge number, date, and truncated note text.
- Disclaimer footer on every page.

**Table page contents:**
- Columns: Date/Time · SYS · DIA · Pulse · Category (colour-coded) · Notes (truncated at 52 chars).
- Alternating row shading; ~30 rows per page.
- Continuation header on subsequent pages.

### 2.3.6 CSV Export

- One option in the **Export** dropdown.
- Fetches all readings from Room (unaffected by range chip filter).
- RFC 4180-compliant CSV with header row: `id, dateTime, timestamp, systolic, diastolic, pulse, category, notes`.
- Fields containing commas, double-quotes, or newlines are wrapped in double-quotes with internal quotes doubled.
- Shared via `Intent.ACTION_SEND` + `FileProvider` (`text/csv`).

### 2.3.7 Health Connect Auto-Push (Per Reading)

- Triggered automatically inside `ValidationViewModel.save()` immediately after a reading is persisted to Room.
- Runs as a background coroutine — **does not delay navigation** back to Dashboard.
- Silent best-effort: no-ops without prompting if Health Connect is not installed, not available, or write permissions have not yet been granted.
- Uses the same stable `clientRecordId` scheme as the bulk sync — re-saving the same reading is idempotent.
- Permission prompting remains exclusively on the manual sync button (§2.3.4).

### 2.3.8 Health Risk Assessment

Shown as a `HealthRiskCard` at the top of the reading list when at least one reading exists.

Computed by `CalculateHealthRiskUseCase` from the most recent reading's `BPCategory` combined with the user's profile (birth year / age, sex, BMI, smoking, diabetes, activity level). Surfaces a risk level and contextual guidance attributed to AHA guidelines, prefixed with a disclaimer that it is informational only and not a substitute for medical advice.
