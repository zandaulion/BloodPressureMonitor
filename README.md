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
| **Landscape mode** | Full-screen trend chart when the device is rotated |
| **Health Risk Card** | Personalised risk assessment (AHA-based) from the latest reading + user profile |
| **Reminders** | Two configurable daily alarms (morning + evening) via `AlarmManager`; 15-min snooze action |
| **Export** | JSON · CSV · multi-page PDF (landscape charts + portrait table of last 30 days) |
| **Import** | JSON import with timestamp-based deduplication |
| **Health Connect** | Bulk sync + automatic per-reading push after each save (when permissions are granted) |
| **Health Profile** | Birth year, biological sex, weight / height (with BMI preview), smoker, diabetes, activity level |
| **Secure API Key** | Stored in `EncryptedSharedPreferences` (Android Keystore); never baked into the APK |

---

## Getting Started

### Prerequisites
- Android Studio Iguana or later
- Android device or emulator running API 26+
- A free [Google AI Studio API key](https://aistudio.google.com/apikey) *(required for camera scan; manual entry works without one)*

### Build
```bash
git clone https://github.com/zandaulion/BloodPressureMonitor.git
cd BloodPressureMonitor
./gradlew assembleDebug
```

No `local.properties` entry is required to build. The API key is entered by the user at runtime via the Health Profile screen.

### Optional dev-time key
Add the following to `local.properties` to pre-fill the key during development:
```
GEMINI_API_KEY=your_key_here
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
| Secure Storage | AndroidX Security Crypto (`EncryptedSharedPreferences`) |
| Health | AndroidX Health Connect |
| DI | Hilt |
| Navigation | Navigation Compose |
| PDF | `android.graphics.pdf.PdfDocument` (built-in, no extra dependency) |

---

