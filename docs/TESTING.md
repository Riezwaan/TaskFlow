# Test and build evidence

Checks performed on 21 September 2026. Local verification does not prove a hosted deployment or physical-phone demonstration.

| Check | Observed result |
|---|---|
| Express/PostgreSQL integration suite | 12 tests passed; 0 failed |
| Kotlin validation/filter tests | 4 passed; 0 failed |
| Retrofit HTTP contract tests | 2 passed; 0 failed |
| Android debug build | Successful; installable APK produced |
| Android device-test APK build | Successful; this is compilation, not device execution |
| Local emulator execution | Blocked: Android did not finish booting; its installer threw a StorageManagerService/PackageManagerInternal null-reference error before app installation |
| Android lint | Successful; 0 errors, 10 warnings |
| APK signature verification | Passed with Android apksigner |
| npm dependency audit | 0 vulnerabilities reported after the rate-limit dependency update |
| GitHub Actions | Workflow prepared; not run remotely because no repository/account was available |
| Hosted API/PostgreSQL | Not deployed; requires account setup |
| Physical Android phone/video | Not yet verified or recorded |

The 12 API tests cover database readiness, invalid input, password/token hashing, duplicate registration, login, unauthorised requests, board CRUD, task/checklist validation, cross-user access prevention, idempotent rewards, persisted settings, deletion and session expiry/revocation. Tests use an isolated in-memory PGlite PostgreSQL instance; production uses PostgreSQL through pg.

Kotlin tests cover credential limits, strict calendar dates, task constraints, combined filters, Retrofit bearer headers/JSON decoding and propagation of HTTP 401. The Compose device test checks invalid-email feedback without a network request.

Lint warnings concern newer dependency versions, Android data-extraction rules and optional SharedPreferences KTX style. Dependencies are pinned to a compatible tested build rather than automatically upgraded. Backup is disabled in the application manifest. The encrypted token cannot be decrypted without its device Keystore key.

## Evidence files

- `evidence/android-build.txt`: successful build, test and lint tasks.
- `evidence/TEST-za.ac.taskflow.ValidationTest.xml`: four Kotlin tests.
- `evidence/TEST-za.ac.taskflow.ApiTest.xml`: two Retrofit tests.
- `evidence/lint-results-debug.html`: complete lint report.
- `evidence/device-attempt.txt`: emulator installation failure, not an app/test pass.

The workspace used Gradle 8.11.1, Temurin JDK 17, Android SDK/Build Tools 35, a workspace-local debug key and in-process Kotlin compilation to accommodate the restricted Windows environment. The optional `TASKFLOW_DEBUG_KEYSTORE` property selects that development key; it is not included in Git and is not a release key. Other machines can use Android Studio's normal debug key.

## Manual acceptance checklist

- [ ] Register, demonstrate invalid input, sign out and sign in.
- [ ] Create, select, rename and delete a board with confirmation.
- [ ] Create/edit/delete a task; preserve description, date, priority and checklist.
- [ ] Drag between visible columns and use status buttons.
- [ ] Combine title, priority and date filters.
- [ ] Complete/reopen/recomplete without duplicate rewards.
- [ ] Save settings, restart/sign in and confirm persistence.
- [ ] Toggle reminders and inspect the selected board's due-date list.
- [ ] Disconnect networking and verify errors/retained editor; retry on reconnection.
- [ ] Verify a second account cannot see the first account's boards.
- [ ] Test portrait/landscape, scrolling and larger text on a physical phone.
- [ ] Confirm hosted HTTPS and database persistence; verify all GitHub Actions jobs.
- [ ] Capture actual screenshots and narrated phone video.

Keep unexecuted checks unchecked. Run this checklist against the hosted service before submission.
