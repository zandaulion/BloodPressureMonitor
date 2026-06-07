# Feature Plans — Closing the Market Gaps

These plans address the "lagging behind" gaps from the [market comparison](../store/market-comparison.md) §3. The **LLM-ready export** gap (§3.4) is intentionally **not** planned — historical health data stays off the cloud, by your direction. The insights work below is therefore designed to run **entirely on-device**.

## The plans

| # | Plan | Gap | Effort | Risk | DB change? |
|---|------|-----|--------|------|-----------|
| 1 | [MAP & Pulse Pressure](MAP_PulsePressure_Plan.md) | §3.1 | ~0.5 day | low | none |
| 2 | [Guideline toggle (AHA/ESC/JNC7)](Guideline_Toggle_Plan.md) | §3.2 | ~1.5–2 days | medium | none |
| 3 | [Contextual tags](Contextual_Tags_Plan.md) | §3.3 | ~1 day | medium | **yes — real migration v2→v3** |
| 4 | [Guided breathing](Guided_Breathing_Plan.md) | §3.5 | ~0.5–1 day | low | none |
| 5 | [Richer PDF visuals](PDF_Visuals_Plan.md) | §3.6 | ~1 day | low–med | none |
| 6 | [On-device insights](Insights_Engine_Plan.md) | §3.7 | ~1–1.5 days | low | none |

> §3.4 (LLM-ready export) — **excluded by design** (privacy).

## Suggested order

1. **MAP & Pulse Pressure** — biggest credibility-per-effort; pure math, no risk. Quick win.
2. **Contextual tags** — do the **migration** early while the dataset is small, and because it unlocks the best insights. (Critical: ships a real `Migration(2→3)` so updates don't wipe data.)
3. **On-device insights** — ship the timestamp/value version; it gains tag-correlation insights for free once #2 is in.
4. **Guideline toggle** — high value for the non-US, multi-language audience; the largest wiring job.
5. **Richer PDF visuals** — self-contained; composes with #1 (MAP/PP) and #4 (per-guideline distribution).
6. **Guided breathing** — nice differentiator, lowest dependency; do it whenever.

## Shared building blocks (build once, reuse)

- **`linregress` least-squares helper** — used by both the PDF trend line (#5) and the 30-day trend insight (#6). Factor into one `domain/usecase/Statistics.kt`.
- **Settings screen** — introduced by the Guideline toggle (#2/#4 in the plans), reused by Guided breathing's toggle. Becomes the natural home for the existing Avg toggle and units too.
- **Classification source of truth** — once the Guideline toggle (#4) lands, the PDF distribution (#5) and insights' "in range" (#6) should read the selected guideline's class, not the stored AHA `category`.

## Cross-cutting reminders

- **Localization:** every plan except PDF visuals adds in-app strings → replicate across all 12 locales (`values-{de,es,fr,ar,hi,ja,ko,pt,ro,uk,zh}`). The PDF/CSV stay English by design.
- **Workflow:** branch per task, test on device, then ask before commit/merge (per the saved git workflow). Each plan lists a commit slicing.
- **Versioning:** bump `versionCode`/`versionName` and add a `store/release-notes-<v>.txt` entry when a feature is ready to ship.
