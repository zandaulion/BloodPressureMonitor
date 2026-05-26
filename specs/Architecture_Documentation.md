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
4.  Data is pushed to **ValidationViewModel** as `ValidationState(systolic, diastolic, pulse, notes, imageUri)`.
5.  User edits/confirms via **Jetpack Compose UI** (also editing `notes` if desired).
6.  **Repository** maps domain model to Entity and inserts it into **Room**.

### Manual-Entry Data Flow
The Dashboard exposes a secondary entry point that navigates directly to the Validation screen with blank state and `imageUri = null`. Steps 1–3 above are skipped; the user fills all fields manually and the same Repository → Room write path is used.

## 1.5 Reactive Deletion Flow
1.  User swipes a row in the dashboard `LazyColumn`.
2.  **DashboardViewModel** removes the reading from local UI state immediately and emits a Snackbar event with an "Undo" action.
3.  After a 5-second window with no undo, the ViewModel calls `repository.delete(id)`.
4.  Room emission updates the `Flow<List<BPReading>>` and the UI converges.
5.  If "Undo" is tapped within the window, the pending delete is cancelled and the UI state is restored without any DB write.
