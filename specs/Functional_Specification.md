# Functional Specification: BP Digitizer App

**Objective:** Build a 100% offline, native Android application that uses on-device OCR to extract blood pressure readings from non-smart digital monitors, allows user validation, calculates health zones, and visualizes trends.
**Target Platform:** Android (Native)
**Language:** Kotlin

## 2.1 Core Data Models

**BPReading (Domain Model)**
* `id`: String (UUID) or Long (Auto-generate)
* `timestamp`: Long (Epoch time)
* `systolic`: Int
* `diastolic`: Int
* `pulse`: Int (Nullable, in case monitor doesn't show it)
* `category`: Enum (NORMAL, ELEVATED, STAGE_1, STAGE_2, HYPERTENSIVE_CRISIS)
* `notes`: String (Nullable, free-text user context such as "after exercise", "morning before coffee")

**BP Category Logic (AHA Guidelines)**
* **Normal:** Systolic < 120 AND Diastolic < 80
* **Elevated:** Systolic 120-129 AND Diastolic < 80
* **Stage 1:** Systolic 130-139 OR Diastolic 80-89
* **Stage 2:** Systolic >= 140 OR Diastolic >= 90
* **Crisis:** Systolic > 180 AND/OR Diastolic > 120

## 2.2 Screen Specifications

### Screen 1: Dashboard (Home)
* **UI Elements:**
    * FAB (Floating Action Button) to "Add New Reading via Camera" (Navigates to Capture Screen).
    * Secondary icon-button next to the FAB for "Add Manually" (Navigates directly to Validation Screen with blank state and no thumbnail).
    * Filter chips above the chart: `7d / 30d / 90d / All`, default `30d`. Chips scope only the chart, not the history list.
    * Vico Chart displaying a time-series line graph. Systolic and Diastolic plotted as two lines on the same Y-axis. Data filtered by the active range chip.
    * LazyColumn displaying the full history of readings, sorted by timestamp descending. Each row shows the numbers, category badge, and the `notes` text (if present).
    * Swipe-to-delete on each history row: deleting shows an "Undo" Snackbar for 5 seconds; if not undone within that window, the row is permanently removed from Room.
    * "Export Data" button in the top App Bar.
* **Behavior:** Reads a `Flow<List<BPReading>>` from Room. Updates automatically when new data is added or deleted.

### Screen 2: Capture (Camera)
* **UI Elements:**
    * Full-screen camera preview (`PreviewView`).
    * Overlay visual guide: A rectangular wireframe prompting the user to align the screen.
    * "Capture" button.
* **Behavior:** * Requests `CAMERA` permission on mount.
    * On capture, takes a high-res photo, saves a temporary cache file, and passes the URI/Bitmap to the ML Kit Analyzer.
    * Transitions to Validation Screen upon OCR completion.

### Screen 3: Human Validation
* **UI Elements:**
    * Top (camera path only): the full captured image scaled to ~120dp tall for user reference. Tapping it opens a fullscreen viewer with pinch-to-zoom. Hidden entirely on the manual-entry path.
    * Three `OutlinedTextField` components for Systolic, Diastolic, and Pulse. Pre-populated with OCR results when arriving from Capture; blank when arriving from manual-entry.
    * `OutlinedTextField` for `notes` (optional, single line, free text).
    * Keyboard Type for numeric fields: `KeyboardType.Number`.
    * Bottom: primary "Save Reading" button. Secondary "Retake" button shown only on the camera path — pops back to Capture, discarding current edits.
* **Validation Rules (Must pass before Save is enabled):**
    * Systolic must not be empty.
    * Diastolic must not be empty.
    * Systolic MUST be strictly greater than Diastolic.
* **Error Handling:** If ML Kit misses a number, the respective text field remains blank, forcing the user to type it. If OCR returns nothing usable, the user can still proceed (all fields blank) or tap "Retake" to try the camera again.
* **Behavior on Save:** Compute `category` via `CalculateBPZoneUseCase`, build a `BPReading` with current timestamp, insert via repository, then pop back to Dashboard.

## 2.3 System Features

### 2.3.1 OCR Parsing Algorithm Specification
* **Input:** Google ML Kit `Text` object.
* **Logic:**
    1. Filter out all non-numeric characters.
    2. Group resulting numbers by their Y-axis coordinates (bounding box `top`).
    3. Sort the groups from top to bottom (smallest Y to largest Y).
    4. Assign Top -> Systolic, Middle -> Diastolic, Bottom -> Pulse.
    5. Fallback: If Y-axis sorting is unreliable due to angle, default to magnitude sorting (Highest number = Systolic, lowest = Pulse), provided Systolic > Diastolic is maintained.

### 2.3.2 JSON Export Specification
* **Trigger:** User taps "Export" in the App Bar.
* **Process:**
    1. Fetch all records from Room (unfiltered — chart range chips do not affect export).
    2. Use `kotlinx.serialization` to convert `List<BPReading>` to a JSON string. All fields including `notes` are emitted; null `pulse` and null `notes` are written as JSON `null`.
    3. Write string to a local temporary file (e.g., `bp_export_TIMESTAMP.json`) in the cache directory.
    4. Fire an `Intent.ACTION_SEND` with MIME type `application/json` utilizing `FileProvider` so the user can share it via Android's native share sheet.
