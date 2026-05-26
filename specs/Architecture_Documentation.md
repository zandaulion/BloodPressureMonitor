# Architecture Documentation: BP Digitizer App

**Objective:** Build a 100% offline, native Android application that uses on-device OCR to extract blood pressure readings from non-smart digital monitors, allows user validation, calculates health zones, and visualizes trends.
**Target Platform:** Android (Native)
**Language:** Kotlin

## 1.1 Architectural Pattern
The application will follow **MVVM (Model-View-ViewModel)** recommended by Google, utilizing Single Source of Truth (SSOT) principles and Unidirectional Data Flow (UDF).

* **UI Layer:** Jetpack Compose (Declarative UI)
* **State Management:** `ViewModel` and `StateFlow`
* **Domain Layer:** Use Cases (OCR processing, string parsing, category calculation)
* **Data Layer:** Room Database (Local SQLite)

## 1.2 Tech Stack & Dependencies
* **UI:** Jetpack Compose (Material 3)
* **Camera:** CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`)
* **OCR/Machine Learning:** Google ML Kit Text Recognition (`com.google.mlkit:text-recognition`)
* **Local Database:** Room (`room-runtime`, `room-ktx`, `room-compiler`)
* **Charts:** Vico (`com.patrykandpatrick.vico:compose`, `com.patrykandpatrick.vico:core`)
* **JSON Serialization:** `kotlinx.serialization` (`kotlinx-serialization-json`)
* **Concurrency:** Kotlin Coroutines & Flow
* **Dependency Injection:** Hilt (Optional but recommended for scalability, `hilt-android`)

## 1.3 Recommended Folder Structure
```text
com.example.bpdigitizer
├── app/
├── data/
│   ├── local/              # Room Database, DAOs, Entities
│   └── repository/         # Repository implementations
├── domain/
│   ├── model/              # Domain models (e.g., BPReading)
│   ├── repository/         # Repository interfaces
│   └── usecase/            # ParseOcrTextUseCase, CalculateBPZoneUseCase
├── presentation/
│   ├── capture/            # Camera screen, ViewModel, Analyzer
│   ├── validation/         # Validation screen, ViewModel
│   ├── dashboard/          # Graph screen, ViewModel
│   └── components/         # Reusable Compose UI elements (Charts, Cards)
└── MainActivity.kt
```

## 1.4 Data Flow Lifecycle (OCR to Database)
1.  **CameraX** streams `ImageProxy` frames to a custom `ImageAnalysis.Analyzer`.
2.  **ML Kit** processes the frame and outputs a `Text` object.
3.  **ParseOcrTextUseCase** extracts bounding boxes, sorts text blocks vertically, and extracts 2-3 digit integers.
4.  Data is pushed to **ValidationViewModel** as `ValidationState(systolic, diastolic, pulse, imageUri)`.
5.  User edits/confirms via **Jetpack Compose UI**.
6.  **Repository** maps domain model to Entity and inserts it into **Room**.
