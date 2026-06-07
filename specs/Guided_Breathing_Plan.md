# Implementation Plan — Pre-Measurement Guided Breathing

**Goal:** Offer an optional ~1-minute guided breathing animation before a measurement, to counter "white-coat" elevation and produce a calmer, higher-fidelity baseline. The report highlights this (My Heart, BreathNow) as a differentiating, intervention-style feature.

**Market gap addressed:** "No pre-measurement guided breathing" (market-comparison §3.5).

---

## Scope

**In scope**
- A `BreathingScreen` with a smooth expand/hold/contract animation, a cycle counter, and Skip / "I'm ready" actions.
- An **opt-in** setting (default OFF). When on, breathing is shown before manual entry and/or camera capture.
- Localized prompts.

**Out of scope**
- Audio/haptics, meditation library, custom patterns (could come later). MVP is a single visual pattern.
- Any DB change (none needed).

---

## Design decisions

1. **Opt-in, not forced.** My Heart *requires* it; that risks annoying users who just want to log. Default OFF, with a clear toggle. (Easy to flip the default later if you prefer.)
2. **Pattern:** box-ish 4-4-6 (inhale 4s, hold 4s, exhale 6s) × 4 cycles ≈ 56s. Skippable at any time.
3. **Placement:** a screen shown *before* `VALIDATION` (manual) and before `CAPTURE` (camera) when enabled, with a Skip that proceeds immediately.
4. **No persistence of breathing sessions** — it's a transient pre-step.

---

## Step-by-step

### Step 1 — Setting (persisted)
Reuse the settings repository introduced by the Guideline plan (or the [ChartSettingsRepository](../app/src/main/java/com/zandaulion/bpdigitized/data/repository/ChartSettingsRepositoryImpl.kt) pattern on the shared `DataStore<Preferences>`):
```kotlin
override fun observeBreathingEnabled(): Flow<Boolean> =
    dataStore.data.map { it[KEY_BREATHING] ?: false }
override suspend fun setBreathingEnabled(enabled: Boolean) =
    dataStore.edit { it[KEY_BREATHING] = enabled }
private val KEY_BREATHING = booleanPreferencesKey("breathing_before_reading")
```

### Step 2 — The breathing screen
New `presentation/breathing/BreathingScreen.kt`. A circle that scales with the phase, a phase label, a cycle countdown, Skip + Ready buttons.

Animation sketch (drive a 0→1 progress per phase with `Animatable`, map to scale):
```kotlin
@Composable
fun BreathingScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(MIN_SCALE) }
    var phase by remember { mutableStateOf(Phase.INHALE) }
    var cyclesLeft by remember { mutableStateOf(TOTAL_CYCLES) }

    LaunchedEffect(Unit) {
        while (cyclesLeft > 0) {
            phase = Phase.INHALE; scale.animateTo(MAX_SCALE, tween(INHALE_MS, easing = LinearEasing))
            phase = Phase.HOLD;   delay(HOLD_MS.toLong())
            phase = Phase.EXHALE; scale.animateTo(MIN_SCALE, tween(EXHALE_MS, easing = LinearEasing))
            cyclesLeft--
        }
        onDone()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(220.dp).scale(scale.value).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Text(stringResource(phase.labelRes()), style = MaterialTheme.typography.headlineSmall)
        // Skip / Ready button pinned at the bottom → onDone()
    }
}
```
Constants: `INHALE_MS=4000, HOLD_MS=4000, EXHALE_MS=6000, TOTAL_CYCLES=4, MIN_SCALE=0.6f, MAX_SCALE=1f`.

Accessibility: if the OS has reduce-motion enabled, fall back to a static circle with phase text only.

### Step 3 — Navigation wiring
In [AppNavHost.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/AppNavHost.kt):
- Add `Routes.BREATHING = "breathing?next={next}"` carrying where to go after (validation vs capture).
- Add a `composable(Routes.BREATHING)` that renders `BreathingScreen(onDone = { navigate(next, popUpTo(BREATHING){inclusive=true}) })`.
- In the Dashboard FAB handlers (`onAddManually` / `onAddViaCamera`), branch on the setting: if breathing enabled → navigate to `BREATHING` with the appropriate `next`; else go straight to validation/capture as today.
  - The cleanest place to read the flag is `DashboardViewModel` (expose `breathingEnabled` in UI state); the screen then chooses the destination.

### Step 4 — Settings toggle
Add a switch "Calm breathing before each reading" to the Settings screen (created in the Guideline plan; if that isn't built yet, add the toggle to [ProfileScreen.kt](../app/src/main/java/com/zandaulion/bpdigitized/presentation/profile/ProfileScreen.kt) under a "Measurement" section as an interim home).

### Step 5 — Strings + localization
- Phase prompts: "Breathe in", "Hold", "Breathe out".
- "Skip", "I'm ready", screen title, setting label + description.
- Add `Phase.labelRes()` mapping. Replicate across 12 locales.

### Step 6 — Build & verify
```
./gradlew :app:assembleDebug
```
- [ ] Setting OFF (default): Add-manual and Add-camera behave exactly as today.
- [ ] Setting ON: both entry points first show the breathing screen; it animates 4 cycles then auto-advances.
- [ ] Skip / "I'm ready" jumps straight to validation/capture.
- [ ] Back from breathing returns to the dashboard (doesn't strand the user).
- [ ] Prompts localized; reduce-motion shows the static fallback.

---

## Effort & risk
- **Effort:** ~0.5–1 day. The animation and nav branch are the work; no data layer.
- **Risk:** low. Self-contained new screen; the only integration points are two FAB handlers and one nav route.

## Commit slicing
1. `feat: add breathing preference`
2. `feat: add guided breathing screen + animation`
3. `feat: route through breathing before manual/camera entry when enabled`
4. `feat: settings toggle + localize breathing prompts`
