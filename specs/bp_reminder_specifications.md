# Feature Specification: Blood Pressure Reminders

## 1. Overview
This document outlines the UX and technical specifications for implementing daily blood pressure logging reminders in our Android application. The goal is to provide accurate, timely reminders that align with medical guidelines while preventing notification fatigue.

## 2. UX & Product Strategy
Medical guidelines dictate checking blood pressure twice a day. The app will prompt the user to configure two daily triggers during onboarding:

### 2.1 Morning Trigger: "Wake + 30 Minutes"
* **Goal:** Trigger after the user wakes and uses the restroom, but before eating, exercising, or taking medication.
* **Configuration:** User inputs their typical wake-up time. The system schedules the notification for 30 minutes after this time.
* **Notification Copy:** *"Time for your morning BP check. Make sure you've rested for 5 minutes and haven't had coffee yet!"*

### 2.2 Evening Trigger: "The Wind-Down"
* **Goal:** Trigger when the user is naturally relaxing away from daily stressors.
* **Configuration:** User selects a specific evening time (e.g., between 7:00 PM and 9:00 PM).
* **Notification Copy:** *"Ready for your evening BP check? Sit back, rest for 5 minutes, and take your reading."*

---

## 3. Technical Implementation

**Tech Stack:** Kotlin, Coroutines, AndroidX Core, DataStore Preferences.
**Core Mechanism:** `AlarmManager` (Exact Alarms) must be used over `WorkManager` to guarantee precise execution and bypass Android Doze mode constraints.

### 3.1 Permissions & Manifest
The `AndroidManifest.xml` must declare:
* `android.permission.POST_NOTIFICATIONS` (API 33+)
* `android.permission.SCHEDULE_EXACT_ALARM` (API 31+, requires runtime prompt on API 34+)
* `android.permission.USE_EXACT_ALARM`
* `android.permission.RECEIVE_BOOT_COMPLETED`

### 3.2 Data Persistence
Use **Preferences DataStore** to store trigger configurations:
* `morning_trigger_time` (Long): Offset or absolute time calculation for morning.
* `evening_trigger_time` (Long): Absolute evening time.
* `is_reminder_enabled` (Boolean): Master toggle for notifications.

### 3.3 The Scheduler (`ReminderScheduler`)
Create an `ExactAlarmScheduler` utilizing `AlarmManager`.
* Must check `alarmManager.canScheduleExactAlarms()` on API 31+. If `false`, prompt user via `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
* Use `setExactAndAllowWhileIdle()` to guarantee delivery even in deep sleep (Doze mode).
* Pass a `PendingIntent` (Broadcast) containing the `ReminderType` (MORNING, EVENING, SNOOZE).

### 3.4 Broadcast Receivers
1.  **`ReminderReceiver`**: 
    * Triggered by the `AlarmManager`.
    * Builds and posts the notification.
    * **Crucial:** Immediately schedules the *next* day's alarm for this trigger type upon execution.
2.  **`BootReceiver`**:
    * Listens for `Intent.ACTION_BOOT_COMPLETED`.
    * Reads current configuration from `DataStore` and reschedules all active alarms, as OS reboots clear `AlarmManager` registries.
3.  **`SnoozeReceiver`**:
    * Triggered directly from the notification action.
    * Calculates `System.currentTimeMillis() + 15 minutes` and schedules a new exact alarm for the `SNOOZE` type.

### 3.5 Notification Design
Construct the notification using `NotificationCompat.Builder`:
* **Channel:** High Importance (API 26+) so it appears heads-up if allowed.
* **Primary Intent:** Opens the `MainActivity` (or direct logging screen).
* **Actions:**
    1.  `Log Now` (Opens app).
    2.  `Snooze (15m)` (Fires `SnoozeReceiver`).
* **Auto-Cancel:** `true`.

## 4. Edge Cases to Handle
1.  **Timezone Changes:** Listen for `Intent.ACTION_TIMEZONE_CHANGED` or recalculate the next alarm interval carefully so the user isn't woken up at 3:00 AM while traveling.
2.  **Permission Revocation:** On app launch, verify `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM` permissions. If revoked by the system or user, gracefully prompt for re-authorization.
3.  **Snooze Overlap:** Prevent scheduling a 15-minute snooze if it overlaps with the next standard scheduled reading.
