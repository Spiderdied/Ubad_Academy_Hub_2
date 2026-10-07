# UBAD Academy Hub — Android release report

Branch `arena/3fee9f22-ubad-academy-hub-2` · verified commit `332c99a` (run `37678791096`) · 2026-10-07

All results below were produced at that commit. Any commit after it is documentation only.

**Status: NOT production-ready, and not claimed to be — but every check that can
be run in this environment now passes, including the §22 release blocker.** Run
`37678791096` is green end to end (both jobs, every step): 51 unit tests pass, the
release APK and AAB are produced, and the emulator suite passes **73/73** on the
signed, R8-minified release APK installed over an already-populated app.

What remains **NOT VERIFIED** is precisely what cannot be exercised here: real
Google sign-in, live Firestore and live B2, because no Google account, Firebase
Console client id or live backend credentials exist in this environment — and a
production release signature, because no upload keystore does. Those are listed in
§M and §P rather than glossed over.

---

## A. Architecture

Native Kotlin + Jetpack Compose, **not** a WebView wrapper. The web app is
untouched and still deployed; the Android app is a second client of the same
contract.

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose (+ Material 3), MVVM, RTL-first (Arabic is the default locale) |
| DI | Hilt |
| Local DB | Room `ubad.db` — no `fallbackToDestructiveMigration` anywhere |
| Preferences | DataStore (`ubad_prefs` for settings, `ubad_cloud` for sync bookkeeping) |
| Media | Media3/ExoPlayer, Coil 3, `PdfRenderer` for PDFs |
| Network | OkHttp |
| Cloud | Firebase Auth (Google via Credential Manager) + Firestore + Backblaze B2 through the existing Cloudflare Worker |
| Build | AGP 8.7.3, Kotlin 2.1.0, compileSdk/targetSdk 36, minSdk 26, R8 + resource shrinking |

Why native rather than a WebView/Capacitor/TWA shell: the app must work fully
offline and restore a multi-section dataset (courses, notes with attachments,
planner, study tools) with the same semantics as the web, and it must drive
native PDF rendering, media playback, file pickers and the Android back stack.
A WebView shell would have inherited the web's offline and storage limits and
delivered a worse app. The web remains the source of truth for the data model:
both clients read and write the same Firestore document shape, and the Android
backup format is byte-compatible with the web's v2 backup (see §H).

## B. Files added / modified (this session)

**Added — cloud foundation**
- `core/cloud/CloudConfig.kt` — public client config + `isFirebaseConfigured` / `isGoogleSignInConfigured` / `isWorkerConfigured`, the 900 KB payload guard, 100 MB file cap, sync version
- `core/cloud/CloudPaths.kt` — web-identical object key builders, `safeFileName`, `workerPath`, `isWithinUserScope`
- `core/cloud/TaskAwait.kt` — `Task.awaitOrThrow()` (deliberately no `kotlinx-coroutines-play-services`)

**Added — cloud data layer**
- `data/cloud/CloudModels.kt`, `FirebaseBootstrap.kt`, `GoogleSignInClient.kt`, `CloudAuthRepository.kt`, `FirestoreCloudClient.kt`, `FirestoreValues.kt`, `CloudFileApi.kt`
- `data/cloud/CloudPayloadCodec.kt` — builds/applies the web's `cloudPayload()` shape
- `data/cloud/CloudSyncEngine.kt` — push/pull/merge, file manifest, last-write-wins
- `data/local/prefs/CloudPrefsStore.kt` — sync bookkeeping + remembered first-sync choice
- `ui/screens/settings/CloudSettingsSection.kt` — Google account / cloud sync UI

**Added — tests / tooling / docs**
- `app/src/test/java/.../data/cloud/CloudPathsTest.kt`, `FirestoreValuesTest.kt`
- `app/src/test/java/.../data/cloud/CloudPullSafetyTest.kt` — proves a cloud pull
  does not drop local binaries (note attachments survive `clearNotes()`'s FK
  cascade; course assets survive `replaceCourses`, unlike a backup restore)
- `app/src/test/resources/robolectric.properties`
- `app/src/main/res/values/strings_cloud.xml`, `values-en/strings_cloud.xml` (Android-only strings)
- `android/RELEASE.md`, this report

**Modified**
- `app/build.gradle.kts` — compileSdk/targetSdk 36, `versionCode 2`, `versionName 1.29.0`, cloud `buildConfigField`s, Firebase/Credential Manager dependencies
- `gradle/libs.versions.toml` — Firebase BoM, Credential Manager, googleid
- `ui/screens/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `ui/components/UiMessage.kt`, `di/CloudModule.kt`
- `tools/smoke/smoke.py` — the §22 update-in-place test, plus verification-pass
  hardening: waits for a real cold start instead of a fixed sleep, taps only
  on-screen nodes, clears system ANR dialogs so emulator stalls are not reported as
  app failures, scrolls horizontal tab rows that previously left checks silently
  skipped, and hashes `filesDir` before/after the update
- `tools/android-security-scan.py` — see §E
- `.github/workflows/android.yml` — upload the Room schema

Deliberately **not** changed: `app.js`, `style.css`, `index.html`, `sw.js`,
`firestore.rules`, `storage.rules`, `storage-backend/worker.js`. The web app and
the security rules are untouched.

## C. Package name

`com.ubad.academy` (debug builds: `com.ubad.academy.debug`). App name
**UBAD Academy Hub**. Deep links: `ubadacademy://course/{id}`, `unit/{c}/{u}`,
`blog/{id}`, `study?tab=…`.

## D. min / target SDK

- minSdk **26** (Android 8.0)
- compileSdk / targetSdk **36**
- versionCode **2**, versionName **1.29.0** (tracks the web release)
- Confirmed from the installed package in the emulator run: `versionName=1.29.0 minSdk=26 targetSdk=36 debuggable=False`

## E. Firebase status

**Config: done. Runtime: NOT VERIFIED.**

- Firebase is initialised manually from `FirebaseOptions` — there is no
  `google-services.json` (the repo's own scanner rejects a tracked copy).
- Deps pinned to BoM **33.7.0** (Auth 23.1.0, Firestore 25.x). BoM 34.19.0 was
  tried first and failed to compile: its `firebase-auth` ships Kotlin metadata
  2.3.0, which this project's Kotlin 2.1.0 compiler cannot read. Raising the BoM
  requires raising Kotlin, KSP, Hilt and the Compose compiler together.
- Cloud config is public-by-design and matches what the web already serves
  (`firebase-auth.js` / `index.html`). Nothing secret is in the app.
- `firestore.rules` and `storage.rules` were **not** weakened or modified.
- Firestore reads/writes go through the existing `users/{uid}` document with the
  same `cloudState` shape the web writes (`appData`, `files[]`, `syncVersion`,
  `syncId`, `clientUpdatedAt`), and the JSON⇄Firestore bridge is unit-tested.

**NOT VERIFIED:** no signed-in session was ever established in CI, so no live
Firestore read or write has been observed. This needs a real Google account and a
registered signing SHA-1 (§K).

## F. Backblaze B2 status

**Client: done. Runtime: NOT VERIFIED.**

- B2 is reached only through the project's existing Cloudflare Worker. The app
  ships **no** B2 key, secret, or Worker credential; the Worker keeps deriving the
  UID server-side from the Firebase ID token.
- Upload / download / delete are implemented with the Worker's contract
  (`POST /upload` + `X-UBAD-Path`, `GET /download?path=`, `DELETE /delete?path=`,
  `Authorization: Bearer <firebase-id-token>`), 100 MB cap, and a local
  scope check on top of the Worker's server-side check.
- Object keys are **relative and web-identical** (`notes/<id>/images/<index>-<name>`,
  `notes/<id>/audio/…`, `courseAssets/<id>`, `backgrounds/<theme>`); the
  `users/<uid>/` prefix is applied only at the Worker call. This was a real bug
  fixed this session — see §H.
- Downloads write to a `.part` file and rename, so an interrupted transfer can
  never replace a good local file with a truncated one.

**NOT VERIFIED:** no file has actually been uploaded to or downloaded from B2 from
Android, because that requires a signed-in Firebase session.

## G. Offline status

**Working, and exercised.** The app is offline-first by construction: Room holds
structural data, `filesDir` holds PDFs/images/audio/backgrounds, and the UI reads
local state only.

Verified in the emulator run with Wi-Fi and mobile data disabled: the home hub,
Courses, Study, Islam and the offline Blog state all rendered, and after
`am kill` + relaunch the app restored its state with Arabic preserved and the
imported data intact.

Failure handling: a failed upload aborts the push **before** Firestore is written
and never deletes local data; a failed download leaves the existing local file
untouched and retries on the next sync; runtime failure text is surfaced to the
user verbatim rather than replaced by a generic message.

## H. Data-migration status (§22 — release blocker)

**No destructive migration exists, and this is enforced.**

- No `fallbackToDestructiveMigration` anywhere. A Room schema change therefore
  *throws* rather than silently wiping data. The version bump and its `Migration`
  must be written together (`android/RELEASE.md` says so).
- No `localStorage.clear()`, no IndexedDB deletion, no automatic overwrite of
  local data with empty cloud data. The web's "erase all data" remains an explicit
  user action.
- Cloud pull **preserves** local binaries: note attachment rows are captured
  before `clearNotes()` (the FK cascades) and re-written, and course replacement
  does not reuse `BackupCodec.restoreCourses`, which clears the asset table and
  directory — correct for a backup, destructive for a cloud pull.
- Cross-client key compatibility was fixed twice this session (details in §B):
  stored keys are relative again, and `safeFileName` collapses runs of invalid
  characters exactly like the web, so the two clients address the same objects
  instead of creating orphans.
- CI now uploads `android/app/build/schemas/**` so the v1 baseline can be
  committed, which is what makes a future v1→v2 migration writable and testable.
- **New §22 test** in the smoke suite: reinstall the APK over an already-populated
  app (the same on-device path as a store update) and re-check the data.

**Emulator result — §22 now PASSES end to end (run `37678791096`, head `332c99a`).**
The suite first populates the app by importing a web-compatible backup: course
`smk-c1` with unit `smk-u1`, a PDF/image/audio/text asset set, a note carrying an
image attachment, a flashcard deck, a quiz, schedule data, a user name and the
Arabic UI. It then runs `adb install -r` over that app — the same path a store
update takes — and re-checks everything:

| §22 check | Result |
| --- | --- |
| reinstall over populated app | **PASS** |
| app launches after update | **PASS** |
| Courses intact | **PASS** |
| Notes intact | **PASS** |
| note body intact | **PASS** |
| note attachment row intact | **PASS** |
| unit contents intact (4 items, progress) | **PASS** |
| PDF asset still renders (cold start) | **PASS** |
| Study deck intact | **PASS** |
| quiz intact | **PASS** |
| settings and user name intact | **PASS** |
| language setting intact (Arabic UI) | **PASS** |
| **every file in `filesDir` byte-identical (md5, before vs after the update)** | **PASS** |

The last check matters most and is not a UI check: the harness hashes every file
under `/data/data/com.ubad.academy/files` before and after `install -r` and
requires the two sets to be identical, so "an update does not lose or alter user
data" is *measured* rather than inferred from screens. Two of the other checks
prove the files are still usable, not merely present: the note attachment renders
`n.png` from `filesDir/note_files/`, and the PDF opens and rasterises on a cold
start from `filesDir/course_assets/` (the unit screen renders the PDF buttons only
when `fileFor(assetId).exists()`, so their presence is the app's own assertion
that the asset file survived).

**Why this took five runs to confirm, and why the app was right all along —** the
assertion was `Page 1 of 2|صفحة 1 من 2`, but Android formats `%d` with the
*locale's* numerals, so the Arabic viewer renders **`صفحة ١ من ٢`** with
Arabic-Indic digits. §22 is the only PDF check that runs after the language switch
to Arabic; the two that run in English passed, which hid the assumption. Once the
harness logged the failing stage instead of just "viewer did not open", the answer
was immediate — the viewer was open all along:

```
INFO  pdf nav 1: tapped "Open PDF" but the page indicator never appeared:
      صفحة ١ من ٢ | صفحة ٢ من ٢ | رجوع | Smoke PDF | ١ / ٢ | ملء الشاشة |
      الصفحة السابقة | الصفحة التالية
```

The pattern now accepts either numeral script, so the check passes on its merits.
No app code was changed to make this pass.

## I. File handling

System file pickers only (`OpenDocument` / `CreateDocument` /
`PickVisualMedia`) — no broad storage permission is requested. Import, export,
background images, note attachments, course assets and PDFs all go through
`FileStore` under `filesDir`; temp/share files live in `cacheDir`. A `FileProvider`
(`${applicationId}.files`) serves files to other apps.

Permissions in the merged manifest, in full:
`INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` (both for the local focus
timer), `ACCESS_NETWORK_STATE` (Firebase Auth), `READ_GSERVICES` (Google's
reCAPTCHA, transitive via Firebase Auth), and the two
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` entries AndroidX adds. No
contacts / location / camera / microphone / SMS / phone / external storage.

**No push notifications of any kind:** no FCM dependency, no FCM token
registration, no notification Worker, no notification UI. The only notifications
are the local focus-timer alarm the app already had.

## J. PDF viewer

Unchanged and working: the native `PdfRenderer` viewer was **not** replaced, and
the web's PDF.js viewer (web-only, under `assets/pdfjs/`) is untouched. Verified
rendering in the emulator run (`PDF viewer (PdfRenderer)` **PASS**, and
`after re-import: PDF asset opens and renders` **PASS**).

## K. Google login

**Implemented following the official flow. NOT VERIFIED at runtime.**

- Credential Manager with the documented two-stage fallback
  (`setFilterByAuthorizedAccounts(true)` → retry `false` on
  `NoCredentialException`), then `GoogleIdTokenCredential.createFrom(...).idToken`
  → `GoogleAuthProvider.getCredential(idToken, null)` → `signInWithCredential`.
- The Firebase **UID remains the identity**; no fake or bypassed auth. There is no
  alternate "trust me" path.
- Two things are required before it can work, and neither can be done from CI:
  1. `UBAD_GOOGLE_WEB_CLIENT_ID` (the OAuth **web** client id) at build time — it
     has no default and cannot be derived. Without it the app builds and runs
     normally, and Settings says the web client id is needed instead of showing a
     button that fails on tap.
  2. The **release keystore's SHA-1 registered** in the Firebase console.
- A WebView-based fallback for Google sign-in is **impossible**: the official
  documentation confirms `GoogleAuthProvider` is not a `FederatedAuthProvider`,
  and `startActivityForSignInWithProvider` is deprecated and OAuth-provider-only.

## L. Testing results

**Real results from CI (GitHub Actions), not "it compiles". Final run
`37678791096` at head `332c99a`: `conclusion: success` — both jobs, every step.**

| Check | Result |
| --- | --- |
| Security scan (sources) | **PASS** — no secrets, PEM rule enforced, unknown keys rejected, WebView allowlists strict, no FCM; the PEM rule had already hard-failed a real run, so it is not vacuous |
| Assemble debug + unit tests | **PASS** — **51 tests, 0 failures** |
| Release APK + AAB (R8 + resource shrinking) | **PASS** — release APK 6,021 KB, AAB 11,233 KB, debug APK 28,317 KB |
| Security scan (APK + merged manifest) | **PASS** — dangerous-permission and secret rules all green |
| Emulator smoke suite (API 34, signed R8-minified release APK) | **PASS — 73 passed, 0 failed** |
| Artifacts uploaded | **PASS** (`ubad-academy-apks`, `ubad-smoke-evidence`) |

Emulator coverage on the signed, R8-minified release APK, installed over an
already-populated app: onboarding (Arabic default, then English), Home and every
hub section, Courses → course → unit → contents including text/image/PDF/audio,
native PDF viewer, image viewer with zoom pager, Media3 audio player, Study tabs,
flashcards deck and quiz, the Arabic+English text content screen, all 6 themes,
RTL layout, rotation, system night mode, deep links (valid and invalid),
offline Blog with Wi-Fi and data disabled, focus-timer alarm broadcast, backup
export validated as web-compatible v2 JSON, import through the system picker,
erase-all, re-import, language switching, relaunch after `am kill`, and the full
§22 update-in-place sequence (§H).

Two harness defects were found and fixed during this pass, both of which had been
hiding coverage rather than merely flaking:

- A regex with an inline flag after an alternation was invalid Python and aborted
  the suite whenever it was reached. It sat behind a tab tap that always failed, so
  **the "Text content (Arabic + English)" check had never actually run** in any
  previous green run. It runs now, and passes.
- `tap()` tapped a node's geometric centre without checking the node was on
  screen, so a button that was scrolled out of view produced a tap outside the
  screen and a false "the app did not respond" verdict. It now taps only visible
  nodes and scrolls until it finds one.

## M. Remaining limitations

1. **Real Google sign-in, live Firestore and live B2 are NOT VERIFIED.** Nothing
   in this environment can exercise them: there is no `UBAD_GOOGLE_WEB_CLIENT_ID`,
   no SHA-1 registered in Firebase Console, no `google-services.json` and no live
   Worker credentials (§E, §F, §K). The *code paths* were verified by inspection
   and unit test; the *runtime* is not claimed. This is the single largest gap and
   it is external, not a defect in the project.
2. **No production signing key.** CI builds `app-release-unsigned.apk` and the
   smoke job signs it with a throwaway key purely so the release build can be
   installed and driven. A store release needs `UBAD_KEYSTORE_*` / `UBAD_KEY_*`
   or a git-ignored `android/keystore.properties` (§N). No key or password is in
   the repository.
3. **Verification ran on an emulator, not physical hardware.** All device-level
   results come from an API 34 x86_64 AVD. Real devices differ in WebView version,
   GPU, memory and vendor power management; a physical-device pass is still owed
   before release.
4. **`READ_GSERVICES` is accepted, not removed.** It is signature-level, not
   user-visible, and merged in by Google's reCAPTCHA (transitive via Firebase
   Auth). Removing a permission an auth library expects is an unverifiable risk
   while sign-in cannot be exercised in CI.
5. **Unattributed PEM string in the debug dex.** A private-key header literal (the
   `BEGIN … PRIVATE KEY` marker this report deliberately does not spell out, since
   the repo's own scanner matches it) appears in `classes19.dex` of the *debug* APK
   only; R8 removes it from the release APK, whose scan is clean. The rule still
   fails hard for release artifacts. It comes from dead code in one of the newly
   added dependencies and could not be traced to a specific library because
   artifact downloads are blocked from this environment. It must be chased down if
   it ever appears in a release APK.
6. **The local build could not be run in this environment** (no JDK/Android SDK,
   and Google Maven/Gradle hosts are unreachable). Every build and test result
   above comes from CI.
7. **Android-build-time BoM pin.** Firebase BoM is pinned to 33.7.0 because
   34.19.0 emits Kotlin 2.3 metadata this toolchain cannot consume. Raising it
   requires raising Kotlin/KSP/Hilt/Compose together (§E).
8. Web-only docs (`docs/ANDROID_MIGRATION_PLAN.md`,
   `docs/ANDROID_FEATURE_COMPARISON.md`) are stale and should not be treated as
   current.

## N. Exact build commands

All from the `android/` directory:

```bash
cd android

# Debug APK
./gradlew :app:assembleDebug

# Unit tests
./gradlew :app:testDebugUnitTest

# Release APK (R8 + resource shrinking)
./gradlew :app:assembleRelease

# Release AAB for Play
./gradlew :app:bundleRelease

# Everything at once
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease
```

Signing (never committed) — env vars or a git-ignored
`android/keystore.properties`:

```bash
export UBAD_KEYSTORE_FILE=/path/to/ubad-release.jks
export UBAD_KEYSTORE_PASSWORD=…
export UBAD_KEY_ALIAS=ubad
export UBAD_KEY_PASSWORD=…
```

Google sign-in needs the web client id at build time:

```bash
./gradlew :app:assembleRelease \
  -Pubad.googleWebClientId=123456789012-abcdef.apps.googleusercontent.com
```

## O. Exact APK / AAB output paths

| Artifact | Path | Size in CI |
| --- | --- | --- |
| Debug APK | `android/app/build/outputs/apk/debug/app-debug.apk` | 28,317 KB |
| Release APK | `android/app/build/outputs/apk/release/app-release-unsigned.apk` | 6,021 KB |
| Release AAB | `android/app/build/outputs/bundle/release/app-release.aab` | 11,233 KB |
| R8 mapping (keep for crash symbolication) | `android/app/build/outputs/mapping/release/mapping.txt` | 11,511 entries |
| Unit test report | `android/app/build/reports/tests/testDebugUnitTest/index.html` | — |

Once a signing config is supplied the release APK is produced as
`android/app/build/outputs/apk/release/app-release.apk` (the `-unsigned` suffix
disappears).

## P. Verification status (final pass)

Every row states what was **observed** and what, if anything, is still blocked.
Nothing is marked verified because a build succeeded, and nothing that could not
be exercised is marked anything other than NOT VERIFIED.

### VERIFIED

| Area | Status | Evidence | Remaining blocker |
| --- | --- | --- | --- |
| Debug APK, release APK, release AAB | VERIFIED | run `37678791096`, all steps success; debug 28,317 KB, release (R8) 6,021 KB, AAB 11,233 KB | none |
| Unit tests | VERIFIED | `tests=51 failures=0`, including 2 new cloud-pull safety tests | none |
| §22 update-in-place data preservation | VERIFIED | 13/13 checks pass; `filesDir` **md5-identical** before/after `install -r`; notes, attachments, Courses, unit contents, deck, quiz, settings, Arabic UI and PDF render all intact | none |
| No destructive migration | VERIFIED | no `fallbackToDestructiveMigration`, no `localStorage.clear()`, no IndexedDB deletion anywhere; Room v1 with schema exported and uploaded by CI | none |
| Cloud pull preserves local binaries | VERIFIED | `CloudPullSafetyTest`: attachment rows survive the `clearNotes()` FK cascade; course assets survive `replaceCourses` (unlike a backup restore, by design) | none |
| Firestore / Storage rules unchanged | VERIFIED | `git diff origin/main -- firestore.rules storage.rules` is empty | none |
| UID isolation | VERIFIED | every cloud call keys off the signed-in uid; Worker `requireUser` resolves the uid server-side and `userPath()` returns 403 unless the path starts `users/<uid>/` | none |
| B2 / Worker secrets not exposed | VERIFIED | app sources contain no secret literals (comments only); client sends only the Firebase ID token; scanner rejects quoted B2/Worker secrets | none |
| Security scanner enforcement | VERIFIED | 9/9 staged source cases and the APK-mode cases behave as specified; the PEM rule has already hard-failed a real run | none |
| No push notifications / FCM | VERIFIED | repo-wide scan finds no messaging/FCM code, tokens or config; manifest has no FCM service; only the local Focus/Break timer, untouched vs base `5615424` | none |
| Minimal permissions | VERIFIED | manifest declares only `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`; CAMERA and RECORD_AUDIO are rejected by the scanner | none |
| Back button / navigation / deep links | VERIFIED | smoke: hierarchy unwinds home, invalid deep links (`course/does-not-exist`, `blog/unknown-post`) handled, state restored after `am kill` | none |
| Offline open and render | VERIFIED | smoke: with Wi-Fi and mobile data disabled the app opens and renders the offline Blog state; reconnect path is delta-only by inspection | none |
| Restart, process death, rotation, themes | VERIFIED | smoke: relaunch after `am kill` restores state and Arabic; rotation and all 6 themes pass | none |
| PDF viewer | VERIFIED | 3 separate checks pass, including rasterising on a cold start after an update | none |
| File handling: export → erase → re-import | VERIFIED | export validated as web-compatible v2 JSON; re-import through the system picker; PDF opens afterwards | none |
| Test suite honesty | VERIFIED | two harness bugs that had silently skipped the Arabic/English text-content check were found and fixed; that check now runs and passes | none |

### VERIFIED WITH LIMITATION

| Area | Status | Evidence | Remaining blocker |
| --- | --- | --- | --- |
| Google sign-in code path | VERIFIED WITH LIMITATION | verified line-by-line: `FirebaseBootstrap` (lazy, survives failure, no `google-services.json`), Credential Manager two-stage flow, `setServerClientId(webClientId)`, strict `GoogleIdTokenCredential`, `GoogleAuthProvider.getCredential(idToken, null)` → `signInWithCredential` | cannot be exercised: no `UBAD_GOOGLE_WEB_CLIENT_ID`, no SHA-1 registered in Firebase Console, no real Google account |
| Firestore model and client | VERIFIED WITH LIMITATION | payload shape/paths/rules checked by inspection and unit test; rules byte-identical to `origin/main` | no live read/write without sign-in |
| Backblaze B2 transport | VERIFIED WITH LIMITATION | `Authorization: Bearer <idToken>` on all three endpoints, `{ok:false,error,code}` parsed into user-readable messages, relative stored keys with idempotent `workerPath()`, client-side `isWithinUserScope` as defence in depth behind the Worker's own authority | no live Worker or credentials, so no real upload/download/delete |
| Feature parity with the web app | VERIFIED WITH LIMITATION | every reachable surface driven on the emulator (§L) | emulator only (API 34 x86_64), not physical hardware |
| Release build signature | VERIFIED WITH LIMITATION | CI signs with a throwaway key to make the release APK installable for smoke testing; R8 mapping has 11,511 entries | no real upload keystore in CI, by design |

### NOT VERIFIED

| Area | Status | Evidence | Remaining blocker |
| --- | --- | --- | --- |
| Real Google sign-in | NOT VERIFIED | none possible in this environment | **external**: needs `UBAD_GOOGLE_WEB_CLIENT_ID`, an SHA-1 registered in Firebase Console, and a real Google account |
| Live Firestore sync | NOT VERIFIED | none possible in this environment | **external**: depends on sign-in |
| Live B2 upload / download / delete | NOT VERIFIED | none possible in this environment | **external**: depends on sign-in plus a live Worker deployment |
| End-to-end cloud sync and reconnection after real network loss | NOT VERIFIED | offline *rendering* is verified; sync is not | **external**: depends on sign-in and live backends |
| Production release signature | NOT VERIFIED | builds are unsigned (CI signs with a throwaway key only for the smoke test) | **external**: needs the real upload keystore via `UBAD_KEYSTORE_*` / `UBAD_KEY_*` |
| Physical-device behaviour (WebView version, GPU, vendor power management) | NOT VERIFIED | all device results are from an emulator | **external**: needs real hardware |

### What this pass did not change

No production code, no rules, no database schema, no manifest and no build
configuration were modified during this verification pass. The diff for the pass
is limited to the smoke harness, one new unit test, the CI emulator memory
setting, and this report — the app under test is the same app that earlier phases
produced.
