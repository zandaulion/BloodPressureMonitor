# BP Digitizer

A privacy-respecting, local-first Android app for logging, visualising, and exporting blood pressure readings.
Point your camera at an upright digital monitor with three SYS / DIA / pulse rows and let the bundled Hearth models read the numbers on your device — or enter them manually. Always review the detected values before saving.

The app requires no account, subscription, cloud service, or first-run model
download. Internet permission is explicitly removed, including permissions
from library manifests. Firebase and RevenueCat are not bundled. Automatic
cloud backup and device transfer exclude app data; readings and profile data
stay in local app storage. Export/share and Health Connect are optional,
user-triggered transfers: a chosen receiving app controls its own subsequent
handling of the copied data. Keep local exports if you need a backup.

The APK bundles native ONNX Runtime and both OCR models. Scanning needs no
WebView, browser, Google Play Services, or separately installed model runtime.

---

## Features

| Feature | Details |
|---|---|
| **On-device OCR** | Hearth reads SYS / DIA / pulse locally, with agreement-based crop and lighting fallbacks |
| **Manual Entry** | Sliders pre-populated from the last saved reading |
| **History & Trends** | Vico line chart (40–180 mmHg for sub-day data; adaptive bounds for longer spans); 7 d / 30 d / 90 d / All filters; pinch-to-zoom with live scroll; dotted reference lines at 120 / 80 mmHg; X-axis labels switch to `M/d HH:mm` when the visible window is under 24 h |
| **Trend / Scatter views** | In portrait, a toggle switches the dashboard chart between the trend line and a rotatable 3D scatter (diastolic × systolic × pulse, dots coloured by recency, 120 / 80 reference lines, tap a dot for details); the range filters drive both, and the chosen view is remembered across restarts |
| **Landscape mode** | Rotate for a full-screen split — the trend chart (left ⅔) beside the 3D scatter (right ⅓), both driven by the same range filters at once |
| **Health Risk Card** | Personalised risk assessment (AHA-based) from the latest reading + user profile |
| **Reminders** | Two configurable daily alarms (morning + evening) via `AlarmManager`; 15-min snooze action |
| **Export** | JSON backup of the health profile and full reading history, including profile-only backups · CSV · multi-page PDF reports |
| **Import** | PWA and Android JSON backups; choose profile and readings or readings only; merge by timestamp |
| **Health Connect** | Bulk sync reading history to Google Health Connect |
| **Health Profile** | Birth year, biological sex, weight / height (with BMI preview), smoker, diabetes, activity level |
| **Offline camera scanning** | Both ONNX models and the runtime ship in the APK; no photo upload, API key, or model download |
| **Localization** | Fully translated into 12 languages: English, Arabic, Chinese, French, German, Hindi, Japanese, Korean, Portuguese, Romanian, Spanish, and Ukrainian |

---

## Getting Started

### Prerequisites
- JDK 21, Android SDK 36, and the included Gradle wrapper (9.5.0)
- Android device or emulator running API 26+; release APKs require ARM64

Release APKs from version 1.6.2 target **ARM64 (`arm64-v8a`)** phones, including
the Galaxy A52 and Fold4. They omit native libraries for 32-bit ARM and x86
devices to reduce download size. Debug builds retain emulator architecture
support. Code shrinking remains disabled; both OCR models are still bundled.
The signed 1.6.2 APK is 53.7 MB (51.2 MiB), down 60.2% from 1.6.1.
It cannot be installed on 32-bit ARM or x86 devices.

### Build
```bash
git clone https://github.com/zandaulion/BloodPressureMonitor.git
cd BloodPressureMonitor
./gradlew :app:assembleDebug
```

### Signing (not included)
The repository does not ship any project credentials. To build and run it yourself:

Camera scanning needs no Firebase project, `google-services.json`, account,
WebView, or model download. Native CPU inference and the models are included
in the APK. Build-time dependencies are fetched by Gradle; the installed app
does not fetch anything.

- **Release signing:** copy `keystore.properties.template` to `keystore.properties`
  and point it at your own keystore. Without it, release builds are left unsigned.

---

## Architecture

**MVVM + Clean Architecture** with Hilt DI, Jetpack Compose (Material 3), Room, and DataStore.

```
presentation/  →  domain/usecases  →  domain/repositories  →  data/
     ↑                                        ↑
  ViewModels                          Room · DataStore
```

See [`specs/Architecture_Documentation.md`](specs/Architecture_Documentation.md) for the full technical breakdown.

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose BOM, Material 3 |
| Charts | Vico 2.0 |
| Camera | CameraX |
| OCR | Hearth ONNX models · bundled native ONNX Runtime |
| Database | Room |
| Preferences | AndroidX DataStore |
| Health | AndroidX Health Connect |
| DI | Hilt |
| Navigation | Navigation Compose |
| PDF | `android.graphics.pdf.PdfDocument` (built-in, no extra dependency) |

---


## License

Copyright (C) 2026 Zandaulion

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
[GNU General Public License](LICENSE) for more details.

SPDX-License-Identifier: `GPL-3.0-or-later`

**Not a medical device.** This app is for personal record-keeping only. It does not diagnose,
treat or prevent any condition; always consult a qualified healthcare professional about your
blood pressure.

## Local OCR integration

The native camera and confirmation screen use the pipeline from
[bp-digitizer-web](https://github.com/zandaulion/bp-digitizer-web), based on
[Hearth](https://github.com/zandaulion/hearth-bp-ocr). See
[docs/Hearth_OCR.md](docs/Hearth_OCR.md) for provenance, runtime boundaries and checks.

See [JSON backup compatibility](docs/JSON_Backups.md) for profile restoration,
PWA imports and merge behavior, and [release notes](docs/Release_Notes.md) for
versions 1.6.0–1.6.2.
