> **Historical documentation:** applies to the former Firebase/Gemini builds.
> Version 1.6.0 removed these services and their configuration requirements.
> See [native offline OCR](../Hearth_OCR.md) for the current implementation.

# Securing AI Usage with Firebase App Check (Play Integrity)

**Goal:** Restrict the app's AI calls (Firebase Vertex AI / "AI Logic" — used for the camera OCR via Gemini) so that **only the genuine, Play-distributed app** can call them. This protects the AI quota/billing from abuse by anyone who extracts the API config.

**Mechanism:** Firebase **App Check** with the **Play Integrity** provider. Every request from the app carries a short-lived App Check token that Firebase verifies via Google Play Integrity before allowing the AI call.

---

## Status

| Piece | State |
|---|---|
| App Check SDK + Play Integrity provider dependency (`firebase-appcheck-playintegrity`) | ✅ present in `app/build.gradle.kts` |
| App-side initialization | ✅ already done in [`BpDigitizerApp.kt`](../app/src/main/java/com/zandaulion/bpdigitized/BpDigitizerApp.kt) (`installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())`) |
| Register app in Firebase App Check with SHA-256 | ⏳ in progress (this guide) |
| Flip AI Logic API from **Monitor** → **Enforce** | ⏳ pending (do after verifying real traffic) |
| Debug provider for local debug builds | ⏳ pending (needed before enforcing, or debug builds break) |

**No app code change is required** to register/enforce — the wiring is already in place.

---

## Key facts

- **Firebase project:** `bpdigitizer`
- **Package name:** `com.zandaulion.bpdigitized`
- **App (Firebase App Check):** "BP Tracker & AI Scanner"
- **Direct link to the Play App Signing page** (bookmark this — saves all the menu hunting):
  ```
  https://play.google.com/console/u/0/developers/7961665871868306457/app/4975117753688912635/keymanagement
  ```

---

## Which SHA-256 to register (the important decision)

Use the **Google Play app signing key** SHA-256 — **not** the debug key, **not** the upload key.

**Why:** every app installed from Google Play (production *and* all testing tracks) is re-signed by **Google's** app signing key. Play Integrity evaluates that installed, Google-signed app, so Firebase must hold *that* certificate's fingerprint. Google keeps that key on its servers; it is not in any local keystore.

| Certificate | SHA-256 use here |
|---|---|
| **App signing key certificate** (Play Console → App signing) | ✅ **This is the one to register** |
| Upload key certificate | ❌ stripped & re-signed by Google before users get the app |
| Local debug keystore | ❌ debug builds use a debug *token*, not a cert, for App Check |

### Where to find it in Play Console
The certificate page is **App signing** (under "Protected with Play" in the newest console; the left nav doesn't always list it). Most reliable route — open the direct link above, or edit the URL:
1. In any Play Console URL for this app: `…/app/4975117753688912635/<anything>`
2. Replace the tail with **`keymanagement`** and press Enter.
3. Page shows **App signing key certificate** with MD5 / SHA-1 / **SHA-256**.

⚠️ **The SHA-256 field is wider than the box and scrolls horizontally — use the copy icon (⧉) at the end of the row.** Hand-typing drops the last byte (32 bytes total) and registration silently fails.

---

## Fingerprints — fetch live, not stored here

The certificate fingerprints are **intentionally not recorded in this file.** Pull them on demand:

- **Play App Signing key certificate** (the one to register in Firebase): open the `keymanagement` link above → **App signing key certificate → SHA-256** → copy with the ⧉ button (the field scrolls; the copy button gets the full 32 bytes).
- **Local debug keystore** (reference only — *not* for the App Check box): regenerate any time with
  ```
  keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
  ```

---

## Steps in the Firebase console

1. Firebase Console → **App Check** → **Apps** → the Android app → register with **Play Integrity**.
2. Paste the **App signing key SHA-256** (copied via ⧉) into **"Enter SHA-256"**.
3. Tick the **Play Integrity API Terms of Service** checkbox.
4. Leave **Token time to live = 1 hour** (default).
5. **Save.**

> You do **not** need to touch the Play Console's "Play Integrity API → Get started / 7 services" (that's for calling Play Integrity directly from your own backend). Firebase App Check drives Play Integrity for you.

---

## After registering — enforcement (do carefully)

1. In Firebase → **App Check → APIs**, find the AI backend (**AI Logic / Vertex AI**). Keep it on **Monitor** first.
2. Use the live app, trigger an AI/camera scan, and confirm requests appear as **verified** in the App Check metrics.
3. Only then switch that API to **Enforce**. Enforcing before a verified request is seen can lock out real users.

### ⚠️ Debug builds break under enforcement
Local debug builds can't pass Play Integrity. Before flipping to Enforce, set up the App Check **debug provider**:
- Add a `debug`-only `DebugAppCheckProviderFactory` path (instead of Play Integrity) in the app's App Check init.
- Run the app once; grab the printed debug token from Logcat.
- Register it in Firebase → App Check → (app) → ⋮ → **Manage debug tokens**.

*(This code change is not yet done — pick it up when ready to enforce.)*

---

## Next actions (pending)
- [ ] Paste App signing SHA-256 into Firebase, Save.
- [ ] Wire the debug-provider path for local testing (code change).
- [ ] Verify real traffic as **verified** under Monitor.
- [ ] Flip **AI Logic / Vertex AI** to **Enforce**.
