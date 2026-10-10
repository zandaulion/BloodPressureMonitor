# Native on-device Hearth OCR

CameraX, OCR and confirmation run inside the Android app. The APK contains native
ONNX Runtime Android 1.24.3, the two Hearth model files and Kotlin image-processing
code. No WebView, JavaScript, browser, Google Play Services, model download,
account or network service is required for scanning. Gradle downloads build-time
dependencies when preparing a build, not when the installed app runs.

## Provenance and licenses

The Kotlin algorithms port `bp-digitizer-web` commit
`85792331a8ea6f51c584f4d25d1bd02348f0c735`, based on `hearth-bp-ocr`. The port
includes the web app's experimental display-rectification fallback. The exact
ONNX model checksums and sizes are in `app/src/main/assets/hearth/models/manifest.json`.
The reader verifies those checksums against both manifest and config at startup.

Hearth's AGPL-3.0-only license/notice and ONNX Runtime's license/third-party notices
ship with the models. Kotlin ports retain the upstream license identifiers.
The original JavaScript algorithms live only in `test/hearth/reference/` for
comparison tests and are not packaged in the APK. No dataset images, private
photos, readings or device identifiers are included in tests.

## Processing and lifecycle

* Decode with bounded resolution, handle all eight EXIF orientations, retain RGB
  and limit the longest edge to 1920 pixels.
* Letterbox RGB to 512×512 with gray 114 padding and planar floats in [0, 1].
* Decode the detector's `[1, 15, N]` output, preserving the unusual class order
  where class 2 is `row`. Suppress duplicate hypotheses across digit classes.
* Resize digit crops to 32×48, then grayscale and normalize with a minimum
  standard deviation of 12. Refine with DigitNet; reject the background class.
  Batches of 32 bound tensor allocations. All native tensors/results are closed.
* Assemble one unambiguous SYS/DIA/pulse stack. Preserve a plausible full-frame
  result, including low-confidence review results.
* Otherwise require agreement between two portrait crops, then between two
  lighting-normalized row crops. Perspective correction is attempted last:
  at least two plausible corrected views must agree, none may disagree, and
  recovered values always retain review status.
* Only complete, plausible triplets reach the native confirmation screen.
  Missing or ambiguous values are never replaced with defaults. Every scan
  requires review before saving; model scores are not correctness probabilities.
* Native validation ranges match Hearth (SYS 50–280, DIA 25–180, pulse 20–250,
  SYS greater than DIA). These are transcription checks, not medical interpretation.

One dedicated executor serializes inference, session initialization and cleanup.
Both CPU sessions are reused while the capture ViewModel is alive. Cancellation
is checked between native calls and throughout pixel processing. A 60-second
coroutine timeout is checked at those boundaries; an already-running native
call must return before its resources can be released. Cancel clears the UI
immediately. Disposal queues session cleanup after any in-flight work and shuts
down the executor; it never closes a native session during inference.

The Kotlin resizer uses explicit bilinear RGB interpolation. Browser Canvas
resampling and CPU/WASM numerical execution can differ near score thresholds.
Algorithm parity on synthetic inputs is not evidence of identical predictions
on every photograph; held-out real-photo validation remains necessary.

## Privacy boundary

The manifest removes Internet and network-state permissions, including any
permissions requested by transitive SDK manifests. Firebase, RevenueCat and
unused Tesseract dependencies and initialization have been removed. ONNX Runtime
telemetry is explicitly disabled. Models load directly from APK assets; there
are no runtime URLs or model-download paths.

Cloud backup and device transfer exclude every app storage domain, in addition
to `allowBackup=false`, following Android's
[backup exclusion rules](https://developer.android.com/identity/data/autobackup).
Native camera files retain the existing local capture/preview lifecycle.
Explicit share/export and Health Connect actions can copy data outside the app;
receiving apps control their own subsequent handling. Core operation does not
require either feature. Local export is the user's backup mechanism.

## Validation

* `node --test test/hearth/*.test.mjs`: upstream synthetic regression tests and
  model/asset integrity checks.
* `node test/hearth/generate-native-fixtures.mjs`: regenerate the deterministic
  synthetic corpus from the web reference; inspect corpus changes when updating
  either implementation.
* `./gradlew :app:testDebugUnitTest`: Kotlin parity cases, tensor preprocessing,
  fallback orchestration and actual CPU inference with generated blank/noisy
  images, including a synthetic display also refused by Hearth’s reference. Desktop ONNX Runtime is a test-only dependency, not an APK dependency.
* `./gradlew :app:assembleDebug :app:lintDebug`: Android build and static checks.
* `./gradlew :app:connectedDebugAndroidTest -PisolatedOcrTest=true`: installed
  permission/backup checks, actual native inference, repeated scans and coroutine
  cancellation/retry. Select one device explicitly. The `.ocrtest` package keeps
  the normal installation and its database separate.
* `./gradlew :app:assembleRelease :app:assembleReleaseAndroidTest -PocrTestBuildType=release`: build the signed release and an instrumentation APK
  targeting that exact release. Install both on the selected test device and run
  `com.zandaulion.bpdigitized.test/androidx.test.runner.AndroidJUnitRunner` with
  `adb shell am instrument -w`. These tests use generated pixels and do not write
  readings to the app database.

### Signed 1.6.0 validation (2026-10-09)

The release (version code 27) was built and signed on the Android build host and
installed on a Galaxy A52 running Android 14. Validation completed:

* Release and instrumentation APK assembly, seven JVM tests (including 990
  synthetic reference cases), and release lint: zero errors, 68 warnings and
  two hints. Remaining lint findings include dependency-update suggestions,
  typography and existing UI cleanup.
* Both instrumentation tests passed against the signed release: native model
  initialization/inference, repeated scans, cancellation/retry, no Internet or
  network-state permission, and automatic backup disabled.
* APK inspection verified both model hashes and the native ARM64 runtime, with
  no JavaScript, WASM or HTML assets.
* First-run navigation, camera permission and capture were exercised. A dark
  capture displayed the unreadable-photo message and stayed on the camera;
  no reading was saved. The app returned to its empty dashboard.

The final APK SHA-256 is
`a4817dcbbe3a97c54af6967f5a5a76432e5f78a0c64fdd5656144c2b58a481f6`.
These checks validate Android packaging and offline execution, not accuracy on
real monitor photographs. No real-monitor photo corpus was used in this device
test, and no claim of photograph-level parity with the web app is made.

### ARM64 packaging in 1.6.2

Release APKs now include only `arm64-v8a`; debug builds retain their default
architecture support. Code shrinking remains disabled. The signed release
(version code 29) is 53,705,736 bytes, down from 135,021,115 bytes in 1.6.1
(a 60.2% reduction). ARM64 native libraries and all Hearth assets were verified
byte-for-byte identical to the preceding release. Signature and 16 KiB ZIP
alignment checks passed.

The final 1.6.2 build also enables the dashboard Export menu with no readings,
allowing profile-only JSON backups. Release assembly, release vital lint and
all 17 JVM tests passed after that correction.

On 2026-10-10, installation as an update on the A52 succeeded, all three device
tests passed (native OCR, privacy permissions/backup, and isolated JSON profile
storage), and the dashboard opened normally. The enabled Export control opened
the JSON export menu; it was dismissed without sharing or changing user data.
This packaging change does not
extend the earlier real-photo accuracy validation.

APK SHA-256:
`9d0fe807aaf024d31c5fa1daf3e1db815d09149e71bf12d852d69086547d2d8c`.
