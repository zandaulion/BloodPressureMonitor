# ML Kit BP-screen benchmark

This is a standalone development harness, not production application code. It
runs bundled Google ML Kit Text Recognition v2 against a frozen, public
BP-monitor regression suite and compares preprocessing variants. It does not use
Firebase or send images over the network.

The harness expects this app-scoped directory on the Android device:

```text
Android/data/com.zandaulion.mlkitbenchmark/files/benchmark/
  manifest.json
  images/...
  views/row-crop/...
  views/rectified/...
```

Create that bundle outside either Git repository from the Hearth checkout:

```sh
.venv/bin/python tools/export_android_benchmark.py \
  /tmp/hearth-mlkit-benchmark --derived-views
```

The app starts the benchmark when launched and writes `mlkit-report.json` beside
the manifest. The manifest and report mark `external-v2` as a consumed,
selection-disallowed regression suite. Do not commit benchmark images, detailed
readings, reports or device connection details.

The variants are original image, full-image grayscale, detector-selected row
crop, grayscale row crop, Otsu row crop, Otsu plus dilation, display
rectification, rectified grayscale and strict agreement across retries. Missing
derived views count as no reading. ML Kit time and source preprocessing time are
stored separately because preprocessing may be produced on different hardware.

Build only this module with:

```sh
./gradlew :mlkitbenchmark:assembleDebug
```

Aggregate results and interpretation are documented in Hearth's
`docs/OCR_ABLATION.md`.
