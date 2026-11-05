# TaskFlow release notes

## Version 2.0.0 — Final assessment update

### Added

- Attach a JPEG, PNG or WebP image to a task, up to 5 MB.
- List, preview, and delete task images from the task details screen.
- Authenticated REST endpoints store and retrieve image bytes from PostgreSQL; task and account ownership are checked for each operation.
- PostgreSQL schema stores the file name, media type, byte count, timestamp and binary content. Deleting a task removes its attachments.
- Added ObjectBox NoSQL storage for a per-account workspace snapshot and pending task changes.
- Added offline task create, edit and delete queuing with API sync when network connectivity returns. Client task IDs make retries idempotent.

### Updated

- Increased Android `versionCode` to 2 and set `versionName` to 2.0.0.
- Prepared optional release signing from local Gradle properties or environment variables; no signing key is included.
- Updated compile/target SDK to API 36 and the Android Gradle Plugin to 8.10.1 for current Play submission targeting.
- Clarified in the app and README that in-app reminders are not push notifications and documented remaining submission work.

### Verification

- Android `testDebugUnitTest`, `assembleDebug`, and `lintDebug` completed successfully on 5 November 2026. The server API suite passed all 15 tests, including attachment validation/ownership and offline-create retry idempotency. Automated verification does not replace a physical-phone demonstration.
- A dedicated cloud object-storage service, Google sign-in, push notifications, isiZulu/Afrikaans translations, signed release APK, final phone video, Play Console evidence, and current hosted service verification remain outstanding.
