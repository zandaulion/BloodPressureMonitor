# BP Digitizer

A privacy-respecting, local-first Android app for logging, visualising, and exporting blood pressure readings.
Point your camera at any non-smart digital monitor and let Gemini 2.5 Flash read the numbers for you — or enter them manually.

---

## Features

| Feature | Details |
|---|---|
| **AI-Assisted OCR** | Gemini 2.5 Flash reads SYS / DIA / Pulse from a photo of any digital BP monitor |
| **Manual Entry** | Sliders pre-populated from the last saved reading |
| **History & Trends** | Vico line chart (40–180 mmHg, Y-axis fixed); 7 d / 30 d / 90 d / All filters; pinch-to-zoom with live scroll; dotted reference lines at 120 / 80 mmHg; X-axis labels switch to `M/d HH:mm` when the visible window is under 24 h |
| **Trend / Scatter views** | In portrait, a toggle switches the dashboard chart between the trend line and a Sys-vs-Dia scatter (diastolic × systolic, dots coloured by zone, 120 / 80 reference lines, tap a dot for its value + date); the range filters drive both, and the chosen view is remembered across restarts |
| **Landscape mode** | Rotate for a full-screen split — the trend chart (left ⅔) beside the Sys-vs-Dia scatter (right ⅓), both driven by the same range filters at once |
| **Health Risk Card** | Personalised risk assessment (AHA-based) from the latest reading + user profile |
| **Reminders** | Two configurable daily alarms (morning + evening) via `AlarmManager`; 15-min snooze action |
| **Export** | JSON · CSV · multi-page PDF (landscape charts + portrait table of last 30 days) |
| **Import** | JSON import with timestamp-based deduplication |
| **Health Connect** | Bulk sync reading history to Google Health Connect |
| **Health Profile** | Birth year, biological sex, weight / height (with BMI preview), smoker, diabetes, activity level |
| **AI-Powered OCR** | Camera scan powered by Vertex AI (Gemini 2.5 Flash); no user API key required |
| **Localization** | Fully translated into 12 languages: English, Arabic, Chinese, French, German, Hindi, Japanese, Korean, Portuguese, Romanian, Spanish, and Ukrainian |

---

## Getting Started

### Prerequisites
- Android Studio Iguana or later
- Android device or emulator running API 26+

### Build
```bash
git clone https://github.com/zandaulion/BloodPressureMonitor.git
cd BloodPressureMonitor
./gradlew assembleDebug
```

---

## Architecture

**MVVM + Clean Architecture** with Hilt DI, Jetpack Compose (Material 3), Room, and DataStore.

```
presentation/  →  domain/usecases  →  domain/repositories  →  data/
     ↑                                        ↑
  ViewModels                          Room · DataStore · EncryptedSharedPrefs
```

See [`specs/Architecture_Documentation.md`](specs/Architecture_Documentation.md) for the full technical breakdown.

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose BOM, Material 3 |
| Charts | Vico 2.0 |
| Camera | CameraX |
| OCR | Gemini 2.5 Flash REST API |
| Database | Room |
| Preferences | AndroidX DataStore |
| Health | AndroidX Health Connect |
| DI | Hilt |
| Navigation | Navigation Compose |
| PDF | `android.graphics.pdf.PdfDocument` (built-in, no extra dependency) |

---

