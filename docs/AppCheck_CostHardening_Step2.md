# Step 2 — Cost backstop for the Vertex AI / Gemini scanning

**Goal:** bound and monitor the spend on the camera OCR calls. Step 1
([`AppCheck_Enforcement_Step1.md`](AppCheck_Enforcement_Step1.md)) used App Check to stop
*outsiders* from calling the endpoint. Step 2 protects against what App Check can't catch — a
runaway bug, a pricing change, or a genuine-but-abusive user scanning in a loop — and closes the
residual API-key exposure.

> **Why the client-side limiter isn't enough:** `OcrRateLimiter` (200/day, in `SharedPreferences`)
> is trivially bypassable (clear app data, re-signed APK) and never runs for direct API calls. Keep
> it as a UX nicety, but the *real* spend controls are server-side: budget alerts + quota caps.

All of this is Google Cloud / Firebase console work — no app code change.

**Recommended order:** 2a now (5 min, no risk) → 2b (the real cap) → 2c (optional cleanup).

- **Firebase / GCP project:** `bpdigitizer`
- **Package name:** `com.zandaulion.bpdigitized`
- **Alert email:** zandaulion@gmail.com

---

## 2a. Billing budget + alerts — do this first (zero downside)

Your smoke alarm. Set it before touching anything else.

- [ ] Cloud Console → **Billing → Budgets & alerts → Create budget**.
- [ ] Scope to project **`bpdigitizer`** (project-wide is simplest; you *can* narrow to just the
      Vertex AI service if you want it isolated).
- [ ] Set an amount + alert thresholds **50% / 90% / 100%**, email to zandaulion@gmail.com.

**Sizing:** one scan = a downscaled ~1024px image + a tiny prompt + a ~20-token JSON reply on Gemini
Flash → a *fraction of a cent*. Even the absolute client-capped ceiling (200/day × 30 testers =
6,000 scans/day, which nobody will hit) is only a few dollars/day. Set the budget where a number
would genuinely *surprise* you — low double-digit dollars/month is already generous. Confirm current
Gemini 2.5 Flash pricing before settling on the figure.

> ⚠️ **A budget ALERTS, it does not CAP.** It emails you; it does not stop spend. That's 2b's job.

---

## 2b. Vertex AI quota cap — the actual hard ceiling

> **✅ AS-BUILT (2026-06-10): set to 20,000,000 input tokens per DAY for `gemini-2.5-flash-ga`.**
>
> ⚠️ **The quota is PER DAY, not per minute** — even though its display name reads
> "Global generate content input tokens **per minute** per base model per day…". The system metric
> name is `GlobalGenerateContentInputTokensPerDayPerBaseModel`, and the **10-billion default** only
> makes sense as a daily figure (10B/min would be absurd). So size it for a *day's* scans. The
> per-minute guidance and the 100k/200k values below were written before we discovered this — ignore
> them; **20M/day** is the real setting.
>
> Sizing (per day): ~1,500 input tokens/scan → 20M/day ≈ ~13,000 scans/day ≈ **~6,500 DAU** of
> headroom, ~1,600× current usage, a 500× cut from the default. Worst-case ceiling if saturated every
> day ≈ **$90–180/mo** (input-dominated; output negligible). Rule of thumb: supportable DAU ≈
> cap ÷ 3,000. Retune from real `usageMetadata` token counts as usage grows.

The circuit breaker the budget can't be.

- [ ] Cloud Console → **APIs & Services → Vertex AI API → Quotas** (or **IAM & Admin → Quotas**).
- [ ] Filter for the Gemini **generate-content requests-per-minute** quota for your model/region.
- [ ] **Lower** it to a value comfortably above real peak. Realistic concurrency is tiny (30
      testers, sub-200/day each), so even a modest per-minute cap bounds worst-case daily cost hard.

> ⚠️ Set it **above** expected legit concurrency or you'll throttle testers. Some quotas can't be
> lowered below a floor, or the edit needs a short review. If the lever isn't available for your
> model/region, lean on 2a + App Check — already a solid posture for an app this size.

*(True "kill switch" alternative — a budget → Pub/Sub → Cloud Function that disables billing —
exists but is deliberately omitted: it kills ALL project services and re-enabling is slow. Too blunt
for a consumer app. A quota cap is the right-sized hard limit.)*

---

## 2c. Lock down the Firebase API key — optional defense-in-depth (lowest priority)

Now that App Check gates the AI calls, the key in `google-services.json` can't make Gemini calls on
its own. This step only hardens it against being used for *other* APIs. Worth doing, not urgent.

- [ ] Cloud Console → **APIs & Services → Credentials** → the Android key (`AIzaSy…reU`).
- [ ] **Application restriction → Android apps** → add package `com.zandaulion.bpdigitized` + the
      app-signing fingerprint.
- [ ] **API restriction** → restrict the key to only the APIs the app actually uses.
- [ ] Re-test the app afterward (scan, reminders, Health Connect) to confirm nothing broke.

> ⚠️ **This uses SHA-1, NOT SHA-256.** API-key Android restrictions take the **SHA-1** fingerprint;
> App Check/Play Integrity (Step 1) took the **SHA-256**. Grab the SHA-1 from the same Play
> `keymanagement` page.
>
> ⚠️ Google's guidance: this key isn't a secret, and restricting it carelessly can break Firebase
> features. Don't regenerate/delete it. If unsure, this is the one step you can safely skip.

---

## Done state

- [ ] Budget alert live and emailing you (2a)
- [ ] Per-minute quota capped above real peak (2b)
- [ ] API key restricted + app re-tested, or consciously skipped (2c)

Net effect: a runaway can at most cost a **bounded, alerted** amount before you notice — and outside
callers are already blocked by Step 1's App Check enforcement.
