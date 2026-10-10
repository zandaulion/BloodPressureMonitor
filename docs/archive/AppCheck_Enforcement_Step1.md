> **Historical documentation:** applies to the former Firebase/Gemini builds.
> Version 1.6.0 removed these services and their configuration requirements.
> See [native offline OCR](../Hearth_OCR.md) for the current implementation.

# Step 1 — Flip App Check to Enforce for AI Logic / Vertex AI

**Goal:** make Firebase App Check actually *block* unverified calls to the Gemini/Vertex AI
endpoint, so a leaked `google-services.json` can't be replayed against your billing.

Companion to [`AppCheck_PlayIntegrity_Setup.md`](AppCheck_PlayIntegrity_Setup.md) (which covers
which SHA-256 to use and where to find it). This file is the ordered runbook.

> **Guiding principle: Enforce is the LAST action.** There are three prerequisites. Skip them and
> you lock out either your real users (no registered SHA) or yourself (debug builds can't pass Play
> Integrity). Do 1a → 1b → 1c → 1d in order.

> **✅ STATUS (2026-06-10): ENFORCED.** SHA-256 registered (1a), verified traffic confirmed under
> Monitor (1c), and the AI Logic / Vertex AI API flipped to **Enforce** (1d). Confirmed by a
> contrast test: a Play-Store-delivered build scans fine; a local debug build gets a 403 and the OCR
> falls back to the last saved reading. The **1b code is now committed** (`DebugAppCheckProviderFactory`
> under `BuildConfig.DEBUG`) — the only task left is registering the debug token in Firebase (see 1b).
>
> **Gotcha learned:** the cumulative "Unverified: invalid" bucket spiked to ~89% over 7 days but was
> *harmless* — it was pre-registration tokens plus the dev's own debug builds, not abuse. Judge
> readiness from a **short window** (last 60 min) and watch that **unknown-origin = 0**, not the
> cumulative invalid %.

Most of this is Firebase/Play Console work (only you have console access). The one code piece is 1b.

---

## 1a. Register the production app + Play app-signing SHA-256 (console)

This is what makes Play Integrity verification actually succeed for genuine installs.

- [ ] Grab the **Play *app-signing* key SHA-256** — **not** the upload key, **not** the debug key.
      Play Console → the `…/keymanagement` link → **App signing key certificate → SHA-256** → copy
      with the ⧉ icon.
      ⚠️ The field scrolls horizontally; hand-typing drops the last byte and registration *silently*
      fails. Use the copy button.
- [ ] Firebase Console → **App Check → Apps →** the Android app → register with **Play Integrity**.
- [ ] Paste the SHA-256 → tick the **Play Integrity ToS** → leave **TTL = 1 hour** → **Save**.

At this point genuine Play-signed installs *can* be verified, but nothing is blocked yet (still Monitor).

---

## 1b. Wire the debug App Check provider (code — diff below)

Once you Enforce, local **debug** builds can't pass Play Integrity (they're signed with the debug key,
not Google's). The debug provider gives them a registerable token instead. **Do this before enforcing
or your dev loop breaks.**

After the code change is in:

- [ ] Run a debug build on a device/emulator.
- [ ] In Logcat, filter for tag `DebugAppCheckProvider` — look for
      *"Enter this debug secret into the allow list… : `<UUID>`"*.
- [ ] Firebase → App Check → the app → ⋮ → **Manage debug tokens** → add that UUID.

That token now counts as "verified," so debug builds keep working under enforcement.

### The code change (three edits)

The dependency needs no version — it's covered by the Firebase BoM, same as the other two Firebase libs.

**`gradle/libs.versions.toml`** — add beside the other firebase entries (~line 71):

```toml
firebase-appcheck-debug = { group = "com.google.firebase", name = "firebase-appcheck-debug" }
```

**`app/build.gradle.kts`** — add by the App Check dep (~line 134):

```kotlin
implementation(libs.firebase.appcheck.debug)
```

**`app/src/main/java/com/zandaulion/bpdigitized/BpDigitizerApp.kt`** — add the import and make the
provider conditional:

```kotlin
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
```

```kotlin
firebaseAppCheck.installAppCheckProviderFactory(
    if (BuildConfig.DEBUG) {
        DebugAppCheckProviderFactory.getInstance()
    } else {
        PlayIntegrityAppCheckProviderFactory.getInstance()
    }
)
```

(`BuildConfig` is in the same package and `buildConfig = true` is already on, so no extra import.)

**Trade-off:** because the `DebugAppCheckProviderFactory` reference must compile in *both* variants,
it's a plain `implementation`, so the debug-provider class ships in the release APK too — but it's
dormant (the `BuildConfig.DEBUG` branch is never taken in release). The fully-clean alternative is to
split the init into `src/debug/` + `src/release/` source sets with `debugImplementation`, at the cost
of two extra files. For a single-maintainer app the dormant-class approach is the pragmatic pick.

---

## 1c. Confirm verified traffic while still on Monitor

Don't enforce blind. Verify **both** paths produce *verified* requests first:

- [ ] **Production/testing-track build** (Google-signed): install from an internal testing track,
      trigger a camera scan, then watch Firebase → App Check → **APIs → AI Logic / Vertex AI** metrics.
      "Verified requests" should climb; "Unverified" should stay flat for your own traffic.
- [ ] **Debug build**: same check, verified via the debug token from 1b.

Give it some real traffic so you're confident genuine scans register as verified.

---

## 1d. Flip AI Logic / Vertex AI to Enforce

- [ ] Firebase → App Check → **APIs** → the **AI Logic / Vertex AI** backend → switch
      **Monitor → Enforce** → confirm.
- [ ] Immediately run one more camera scan from a real install to confirm it still works.

From here, requests without a valid App Check token (anyone replaying the extracted
`google-services.json`) are rejected by Google before they cost anything.

> **Safety net — do before 1d:** set a GCP billing **budget alert** on the project (Step 2 of the
> broader hardening). Even a misconfigured enforce won't surprise you on the bill.

---

## Rollback

If real scans start failing after 1d, flip the AI Logic API back to **Monitor** in the same Firebase
App Check → APIs screen. It takes effect within minutes and unblocks everyone while you debug (usual
culprits: wrong SHA-256 registered in 1a, or a debug token not added in 1b).
