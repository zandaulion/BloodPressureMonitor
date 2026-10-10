# JSON profile and reading backups

Android 1.6.1 and later export the same version-1 envelope as the PWA:

```json
{
  "app": "bp-digitizer",
  "version": 1,
  "exported": "2024-01-01T00:00:00Z",
  "profile": {
    "birthYear": null,
    "sex": "OTHER",
    "weightKg": null,
    "heightCm": null,
    "activity": "MODERATELY_ACTIVE",
    "smoker": false,
    "diabetes": false
  },
  "readings": []
}
```

This example contains defaults only. No user backup is included in source or tests.

JSON export contains the health profile even when there are no readings. CSV and
PDF remain reading reports. Language, display units, reminders and other app
settings are not part of this health-profile backup.

Import accepts the envelope above, PWA JSON exports, and older Android arrays.
Timestamps remain epoch milliseconds; IDs are reassigned locally. Android tag
arrays and PWA comma-separated `tag_*` strings are normalized to the same tags.
The PWA's `OTHER` sex value maps to Android's “prefer not to say” choice.

When a file contains profile fields, the app offers **Profile and readings**,
**Readings only**, or **Cancel** before any writes. Included profile fields replace
local values; omitted fields are preserved, and explicit null clears an optional
birth year, height or weight. Missing, null or empty profiles leave the local
profile unchanged. A profile can be restored even when all readings are duplicates.

Readings are merged by exact timestamp, including duplicates within the file.
The entire file is decoded and validated before restoration begins. Unsupported
versions, malformed fields, unknown tags and invalid profile enums are rejected
rather than silently discarded. Unrecognized reading categories are recalculated,
as with older Android imports. No failed storage write is reported as a successful
import. Room readings and the DataStore profile are separate stores: a storage
failure can leave a partial reading merge, which can safely be retried because
existing timestamps are skipped; the profile is saved after readings succeed.

Everything runs locally. Export uses Android's explicit share sheet; the app
still has no Internet permission. Tests use synthetic records, and Android file
and DataStore tests use isolated storage without accessing the user's profile
or reading database.

## Validation of 1.6.1

On 2026-10-10, the signed release (version code 28) passed 17 JVM tests, including
10 backup regression tests, and three Android instrumentation tests on the A52.
The storage test exercised file URIs, profile persistence in an isolated DataStore,
PWA import, JSON export/re-import, tags and duplicate handling. The existing OCR
and installed-app privacy checks also passed. Release lint reported zero errors,
68 warnings and two hints.

A synthetic profile-only file was selected through the Android file picker. The
three-choice confirmation dialog appeared; Cancel dismissed it and returned to
the dashboard. The test file was removed, and the user's saved profile/readings
were not used or overwritten by these tests.

Signed APK SHA-256:
`83f0f0c4987af691b0499677bd64865883c7913256ba34bec111f48b634e0471`.
