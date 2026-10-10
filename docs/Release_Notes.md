# Release notes

## 1.6.3 — Build and packaging cleanup

- APKs and bundles no longer include AGP's dependency metadata block, an
  encrypted dependency list readable only by Google. F-Droid asks for this.
- The build no longer downloads a JDK: the foojay toolchain resolver and the
  Gradle daemon JDK criteria were removed. Gradle runs on `JAVA_HOME` or the
  `java` on `PATH`, which must be a full JDK 17 or newer.
- Removed an unused "Premium feature" accessibility string from all languages.
- Added F-Droid metadata (`fastlane/`, `fdroid/`) and model provenance notes.
  App behaviour, OCR models and the database schema are unchanged.

Validation: an unsigned release built on OpenJDK 17 without a keystore; release
vital lint and all 17 JVM tests passed. The release APK keeps ARM64-only native
libraries. `fdroid scanner` (source and APK) and `fdroid lint` reported no
problems, and F-Droid's own CI pipeline built `v1.6.3` from the submitted
recipe ([fdroiddata !51961](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51961)).

## 1.6.2 — Smaller ARM64 release

- Release APKs include only ARM64 native libraries. Android API 26+ is still
  required; 32-bit ARM and x86 devices cannot install this release APK. Debug
  builds retain emulator architecture support.
- The signed APK is 53,705,736 bytes (53.7 MB / 51.2 MiB), down 60.2% from
  135,021,115 bytes. Both Hearth models and the ARM64 runtime are unchanged.
- Enabled the Export menu with an empty history so a profile-only JSON backup
  can be shared from the dashboard.
- Offline operation and reading storage are unchanged. Code shrinking remains
  disabled.

Validation: release assembly, release vital lint and all 17 JVM tests passed;
signature and
16 KiB ZIP alignment checks passed, and installation as an update on a Galaxy
A52 succeeded. All three device tests passed: native OCR, permissions/backup
privacy checks, and isolated JSON/profile storage. The dashboard opened normally.

See [OCR validation](Hearth_OCR.md#arm64-packaging-in-162) for the APK checksum.

## 1.6.1 — Portable health profile backups

- JSON backups include the health profile and all readings. The format supports
  an empty reading list; 1.6.2 also enables the dashboard Export menu in that case.
- Import supports PWA version-1 exports and older Android reading arrays.
- Profile restoration offers profile and readings, readings only, or cancel.
  Readings merge by exact timestamp; duplicate imports do not duplicate readings.
- PWA tags and profile values are normalized for Android. Invalid backups fail
  validation before writes; storage errors are reported.

Validation: 17 JVM tests and three device tests passed; release lint reported
zero errors, 68 warnings and two hints. Fixtures are synthetic.
See [JSON backups](JSON_Backups.md) for schema, merge rules and restore limits.

## 1.6.0 — Self-contained offline OCR

- Replaced Gemini/Firebase, subscriptions and Tesseract with bundled Hearth
  ONNX models and native CPU inference. No WebView or model download is needed.
- Removed Internet permissions and disabled automatic cloud backup and device
  transfer for app data. Every detected reading still requires user confirmation.
- Health Connect transfers require the explicit dashboard action.
- Ported crop, lighting and experimental display-rectification fallbacks from
  the web app.

Validation: seven JVM tests (including 990 synthetic parity cases), two device
tests and release lint passed with zero lint errors. These checks establish
packaging and local execution; real-monitor photograph accuracy has not been
validated with a held-out corpus. See [Hearth OCR](Hearth_OCR.md) for details.
