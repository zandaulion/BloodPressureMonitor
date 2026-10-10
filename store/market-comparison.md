# BP Digitizer vs. the Android BP-App Market

**App version:** 1.0.1 (versionCode 11) · **Date:** 2026-06-06
**Source:** [market-analysis.md](market-analysis.md) (landscape report) compared against the app's *verified current code*, not the older project notes.

This document maps our app against the market report's benchmarks and sorts the findings into three buckets:

1. **In line with the market** — table stakes we already meet.
2. **Differentiators** — where we lead or do something the field doesn't.
3. **Lagging behind** — gaps versus the leading apps (My Heart, Cardilog, SmartBP).

---

## Snapshot: what the app actually does today (verified)

| Capability | Status |
|---|---|
| Storage | Local-first (Room SQLite), **no account required** |
| Data entry — manual | Slider cards (Sys/Dia/Pulse) — **free** |
| Data entry — camera | On-device OCR of a normal monitor's display (bundled Hearth ONNX models, offline) — **free** |
| Data entry — Bluetooth cuff | **Not supported** (no BLE pairing) |
| Health Connect | Writes `BloodPressureRecord` + heart rate on request (no reads) — **free** |
| BP categorization | AHA/ACC 5 zones (Normal → Crisis), color-coded — **fixed to AHA** |
| Personalized risk | Profile-aware cardiovascular risk card (age, sex, BMI, smoking, diabetes, activity) |
| Charts | Interactive Vico trend chart (7/30/90/All, pinch-zoom), **"Avg" burst-smoothing** |
| MAP / Pulse Pressure | **Absent** |
| Contextual tags | Free-text `notes` only — **no structured tags** |
| PDF report | Multi-page, doctor-ready (line chart, note callouts, stats bar, table) — **free** |
| CSV / JSON export | Both present — **free** |
| LLM-ready export | **Absent** |
| Reminders | Morning/evening exact alarms |
| Pre-measurement breathing | **Absent** |
| Monetization | Freemium; **only the camera scan is gated**. No ads. |
| Localization | **12 languages** |
| Regulatory posture | Informational-only, disclaimers present; **camera reads an external monitor — no PPG/fingertip BP** |

---

## 1. In line with the market

These are the report's "table stakes" / recommended-blueprint items we already satisfy:

- **Local-first, no-account architecture.** Matches the privacy-led cohort the report recommends (My Heart, BP Diary, Cardilog) and sidesteps the cloud-breach/GDPR burden of Omron/Qardio. This is exactly the architecture the "Strategic Blueprint" prescribes.
- **Health Connect integration.** The report's single biggest infrastructure recommendation — be the analytical engine sitting on top of Health Connect so users can bring any cuff. We have read/write of `BloodPressureRecord`. ✔
- **AHA/ACC clinical categorization with color-coding.** Done the regulatory-safe way (attributing thresholds to a recognized body keeps us under CDS enforcement discretion).
- **Interactive trend charts** across 7/30/90/All with zoom — standard expected analytics.
- **Doctor-ready multi-page PDF** with per-range line charts, a stats bar (avg/min/max), numbered note callouts, and a readings table. This is the core "clinical reporting" deliverable.
- **CSV export** for archival/interoperability — the baseline export the report expects every app to have.
- **Manual entry + morning/evening reminders** — basic logging hygiene.
- **Informational-only disclaimers** in the PDF footer and health notes — the prominent medical disclaimer the report calls a "fundamental operational requirement."
- **Freemium with the core kept free** — core logging, charts, and reporting carry no paywall, matching the report's warning against gating essentials.

> Net: we clear essentially every table-stakes bar in the report. The basic-logging layer is, as the report notes, commoditized — so the story is in the next two sections.

---

## 2. Differentiators (where we lead)

- **📷 Camera OCR of a non-smart monitor's display.** This is the standout. None of the surveyed apps (My Heart, Cardilog, SmartBP, Omron, Qardio, Samsung Health) read the numbers off an ordinary cuff's LCD. It bridges the gap between cheap "dumb" monitors and smart-cuff convenience **without** requiring the user to buy Bluetooth hardware. It's our genuine moat.
- **🛡️ Regulatory-safe use of the camera.** Critically, we use the camera to *read an external, certified device's result* — **not** PPG/fingertip estimation, which the FDA has explicitly warned against and which the report flags as a liability trap. We get a "camera magic" hook while staying firmly in the safe, enforcement-discretion lane.
- **🚫 Ad-free.** The report names the "ad-fatigue crisis" as the #1 driver of churn and one-star reviews — and it's the single biggest complaint against the market leader, My Heart ("an ad for every breath I took"). We run zero ads. This is a direct, marketable wedge against the most-downloaded competitor.
- **❤️ Personalized cardiovascular-risk card.** We combine the BP zone with the user's profile (age, sex, BMI, smoking, diabetes, activity) into a LOW→VERY_HIGH risk read. Most basic trackers and even the big hardware hubs omit any personalized risk; the report positions this kind of insight as an "advanced clinical app" trait. Ours is transparent and rule-based (no black box).
- **📉 "Avg" burst-smoothing.** Averaging back-to-back sittings (with labels preserved in the PDF) is a thoughtful, BP-specific data-quality feature that competitors don't surface.
- **🆓 Free doctor-ready PDF.** SmartBP gates PDF generation behind a subscription; we give the full multi-page report away. This aligns with the report's recommended "value-additive" model and with how Cardilog disrupted paywalled rivals.
- **🌍 12-language localization.** Broader reach than most indie competitors, which widens the addressable market well beyond the US/AHA world.

---

## 3. Lagging behind the market

Ordered roughly by value-to-effort (cheap, high-value gaps first):

1. **No MAP or Pulse Pressure.** The report repeatedly cites Mean Arterial Pressure and Pulse Pressure as the exact line that separates "advanced clinical apps" (My Heart, Cardilog) from basic loggers — and notes even Omron/Qardio/Samsung omit them. Both are pure arithmetic on values we already store. **Highest value-to-effort gap.**
2. **No selectable clinical guidelines.** We're hard-coded to AHA/ACC. Leaders let users toggle AHA ↔ ESC/ESH ↔ JNC7 to match their physician's region. Given we already ship 12 languages, fixed-US thresholds undercut our international reach.
3. **No structured contextual tags.** We only have a free-text `notes` field. The report calls quick tags ("post-medication," "caffeine," "salty meal," "high stress," "poor sleep") *non-negotiable* — they're what turn a trend line into a behavioral/trigger-finding tool. We can't filter or correlate on free text.
4. **No LLM-ready export.** The report calls a context-rich, AI-pasteable prompt export "the absolute frontier," pioneered by My Heart. It's privacy-friendly (the user pastes into their own ChatGPT/Claude/Gemini) and cheap to build on top of our existing export. We have CSV/JSON but nothing AI-shaped.
5. **No pre-measurement guided breathing.** My Heart and BreathNow integrate a ~1-minute breathing protocol to counter white-coat syndrome and improve reading fidelity. We have nothing in the pre-measurement workflow.
6. **PDF lacks the "lab-report" visuals.** Our PDF is solid (line chart + table + stats) but missing the elements the report attributes to leading reports: a category **pie / % time-in-stage**, a **Sys-vs-Dia scatter**, and **regression trend lines**.
7. **No AI-driven pattern mining.** Cardilog's "Intelligence" auto-surfaces correlations (e.g., sodium → evening spikes). Our risk card is static per-reading, not a pattern engine across history. (Largest effort; depends on tags from #3 to be meaningful.)

---

## Bottom line & suggested sequence

Our **moat is acquisition-side**: camera entry + ad-free + privacy + a free doctor PDF + 12 languages. That's a strong, differentiated pitch against an ad-saturated field led by My Heart.

Our **gap is clinical depth**: MAP/PP, guideline choice, structured tags, and an AI/LLM angle — the very things the report says define the premium tier.

A pragmatic order that turns the gaps into strengths without bloating the app:

- **Quick wins (days):** MAP + Pulse Pressure (#1), guideline toggle (#2). Both are low-risk math/threshold work with high clinical-credibility payoff.
- **Next (high differentiation):** structured contextual tags (#3) → unlock tag filtering and, later, correlation insights.
- **On-trend, cheap:** LLM-ready export (#4) — leans directly into our existing privacy-first story.
- **Bigger bets:** richer PDF visuals (#6), guided breathing (#5), pattern-mining insights (#7).

> Strategic note: the report frames basic logging as commoditized — the moat is UX, clinical depth, AI, and frictionless monetization. We already own a unique UX hook (camera) and friction-free monetization (no ads, core free). Closing the clinical-depth gap (items #1–#4) would let us match the leaders on substance while keeping the acquisition advantages they can't easily copy.
