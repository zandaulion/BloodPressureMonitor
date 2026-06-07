# Implementation Plan — Richer "Lab-Report" PDF Visuals

**Goal:** Bring the exported PDF up to the "leading report" bar the market report describes: a **category distribution** (% time in each stage), a **systolic-vs-diastolic scatter**, and a **regression trend line** over the existing time series.

**Market gap addressed:** "PDF lacks the lab-report visuals" (market-comparison §3.6).

---

## Context (what we have today)

`ExportPdfUseCase` ([ExportPdfUseCase.kt](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/ExportPdfUseCase.kt)) already produces:
- One **landscape chart page per range** (7d / 30d / all) — line chart, numbered note callouts + legend, a stats bar (avg/min/max), footer disclaimer.
- One or more **portrait table pages** (last 30 days).

It's pure `android.graphics` Canvas drawing at A4@2×. All three additions are self-contained Canvas work in this one file. **No strings** (the PDF is English-only by design), **no data layer**.

---

## Scope

**In scope (3 visuals)**
1. Category distribution — a 100% horizontal stacked bar with a % legend.
2. Sys-vs-Dia scatter — points with reference lines.
3. Regression trend line — least-squares fit overlaid on the existing line chart, with the slope annotated.

**Out of scope**
- Reworking pagination beyond adding one summary page.
- Localizing PDF text (stays English, consistent with the current export).

---

## Step-by-step

### Step 1 — Shared math helpers
Add to the file's math section:
```kotlin
// Least-squares slope/intercept of y over x.
private fun linregress(xs: List<Double>, ys: List<Double>): Pair<Double, Double> {
    val n = xs.size
    val mx = xs.average(); val my = ys.average()
    val sxx = xs.sumOf { (it - mx) * (it - mx) }
    if (sxx == 0.0) return 0.0 to my
    val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
    val slope = sxy / sxx
    return slope to (my - slope * mx)
}
```
Use **days since first reading** as x so the slope reads as "mmHg/day".

### Step 2 — Regression overlay on the line chart
In `drawChart` ([ExportPdfUseCase.kt:146](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/ExportPdfUseCase.kt)), after the series paths:
- Compute `linregress` for systolic (and optionally diastolic) vs day-offset.
- Draw a dashed line from the fit at `firstTs` to `lastTs` using the existing `xC`/`yC` mappers and a dashed `Paint` (reuse the `pRefLine` style).
- Annotate near the right edge: `"Trend: %+.1f mmHg/day".format(slope)` (or per week if clearer).

### Step 3 — Category distribution bar
New `drawCategoryDistribution(c, readings, top)`:
- Count readings per `BPCategory` (or per the selected guideline's class, if the Guideline plan has landed — read its classification the same way).
- Draw a full-width bar split proportionally, each segment filled with the existing `categoryArgb(cat)` color.
- Below it, a legend: swatch + `"<label> NN%"` per non-zero category (reuse `categoryLabel`).
- Place it on a new **summary page** (Step 5) or in the empty band above the stats bar on the all-time page.

### Step 4 — Sys-vs-Dia scatter
New `drawScatter(c, readings, left, top, right, bottom)`:
- X = diastolic (e.g., 40–120), Y = systolic (e.g., 80–200); reuse the `yC`-style normalization for both axes.
- Plot each reading as a small dot colored by its category.
- Draw the 120/80 reference lines (vertical at dia=80, horizontal at sys=120) to frame the "normal" quadrant.
- Axis labels + title "Systolic vs Diastolic".

### Step 5 — Assemble a summary page
Add `drawSummaryPage(doc, pageNum, allReadings)` that lays out, on one portrait A4 page: the category distribution bar (top), the scatter (middle), and a small stats block (overall avg/min/max, MAP/PP if that plan landed). Insert it in `invoke()` ([line ~46](../app/src/main/java/com/zandaulion/bpdigitized/domain/usecase/ExportPdfUseCase.kt)) **after** the chart pages and **before** the table pages, guarded by `all.size >= 2`.

### Step 6 — Build & verify (visual)
```
./gradlew :app:assembleDebug
```
Export with a realistic dataset (mixed categories, ≥30 readings spanning weeks) and open the PDF:
- [ ] Trend line sits sensibly through the cloud; slope annotation matches the visual direction.
- [ ] Distribution segments sum to 100%; colors match the category badges; legend % are correct.
- [ ] Scatter points land in the right quadrants; reference lines at 120/80; nothing clipped.
- [ ] Summary page paginates cleanly between charts and table; footer/disclaimer present.
- [ ] Degenerate cases: all-identical readings (slope 0, single-color bar), exactly 2 readings — no crashes/NaNs.

---

## Effort & risk
- **Effort:** ~1 day, all in one file.
- **Risk:** low–medium, purely cosmetic/layout. Watch for divide-by-zero (handled in `linregress`) and right-edge clipping on the landscape page.

## Commit slicing
1. `feat: add regression helper + trend line overlay in PDF`
2. `feat: add category distribution bar to PDF`
3. `feat: add sys-vs-dia scatter + summary page`

## Synergy
If the **Guideline** and **MAP/PP** plans have landed, classify the distribution per the selected guideline and add MAP/PP to the summary stats block — these compose naturally.
