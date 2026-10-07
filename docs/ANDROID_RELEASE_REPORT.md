# UBAD Academy Hub — Android release report

Branch `arena/3fee9f22-ubad-academy-hub-2` · head `1d1167b` · 2026-10-07

**Status: NOT production-ready, and not claimed to be.** The build is green, the
release APK and AAB are produced, 49 unit tests pass and the emulator suite
passes every check it is able to reach — but three things are **NOT VERIFIED**
because they need a real Google account, live backend credentials, or a calm
emulator, and one §22 sub-check needs one clean confirming run. Those are listed
in §H and §M rather than glossed over.

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
- `app/src/test/resources/robolectric.properties`
- `app/src/main/res/values/strings_cloud.xml`, `values-en/strings_cloud.xml` (Android-only strings)
- `android/RELEASE.md`, this report

**Modified**
- `app/build.gradle.kts` — compileSdk/targetSdk 36, `versionCode 2`, `versionName 1.29.0`, cloud `buildConfigField`s, Firebase/Credential Manager dependencies
- `gradle/libs.versions.toml` — Firebase BoM, Credential Manager, googleid
- `ui/screens/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `ui/components/UiMessage.kt`, `di/CloudModule.kt`
- `tools/smoke/smoke.py` — the §22 update-in-place test
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

**Emulator result of the §22 test (run `37565031880`):** reinstall over populated
app **PASS**, launch after update **PASS**, Courses **PASS**, Notes **PASS**, unit
contents + progress **PASS**, Study deck **PASS**, quizzes **PASS**, settings/user
name **PASS**. The post-update PDF render check failed on its first attempt — see
§M item 1.

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

**Real results from CI (GitHub Actions), not "it compiles":**

| Check | Result |
| --- | --- |
| Security scan (sources) | **PASS** (run `37567403438`, head `1d1167b`) |
| Debug APK | **PASS** — `app-debug.apk`, 28,317 KB |
| Unit tests | **PASS** — 49 tests, 0 failures (was 22 before this session's work) |
| Release APK + AAB | **PASS** — release APK 6,021 KB (R8, 11,511 mapping entries), AAB 11,233 KB |
| Security scan (APK + merged manifest) | **PASS** — dangerous-permission and secret rules all green |
| Artifacts uploaded | **PASS** (`ubad-academy-apks`) |
| Emulator smoke suite | **PASS except two items in run `37565031880`**, both diagnosed as defects in the new test itself and both corrected in `1d1167b` |

Emulator coverage on the signed, R8-minified release APK: onboarding (Arabic
default, then English), Home, every hub section, Courses → unit → contents,
native PDF viewer, image viewer, Media3 audio, Study tabs, flashcards, quizzes,
search, deep links (valid and invalid), all 6 themes, RTL layout, rotation,
system night mode, offline Blog, focus-timer alarm broadcast, backup export
validated as web-compatible v2 JSON, import via the system picker, erase-all,
re-import, language switch, and relaunch after `am kill`.

Note on the last run (`37567403438`): the smoke job aborted because the
**emulator's own launcher ANR'd during boot** ("Pixel Launcher isn't responding"),
which covered the screen before the app could be driven. The app itself installed
successfully and cold-started in 5.9 s; this is CI infrastructure flakiness, not
an app defect — but it does mean the §22 confirming run has not yet happened on a
clean emulator.

## M. Remaining limitations

1. **§22 post-update PDF render is unconfirmed.** Every other §22 data check
   passed; the PDF render check failed once. The asset lives in
   `filesDir/course_assets/<id>`, which an app update does not touch, and the
   identical check passed twice earlier in the same run — so the likely cause is a
   slow first raster on a cold start after reinstall. The check now retries once
   with a 25 s window, but **the confirming run has not completed** (the next
   attempt was the emulator launcher ANR). Until it does, this stays open and the
   §22 blocker is not signed off.
2. **Google sign-in / Firestore / B2 are NOT VERIFIED** (§E, §F, §K).
3. **Unattributed PEM string in the debug dex.** A PEM private-key header
   literal (the `BEGIN … PRIVATE KEY` marker this report deliberately does not
   spell out, since the repo's own scanner matches it) appears in
   `classes19.dex` of the *debug* APK only; R8 removes it from
   the release APK, whose scan is clean. The rule still fails hard for release
   artifacts. It comes from dead code in one of the newly added dependencies and
   could not be traced to a specific library because artifact downloads are
   blocked from this environment. It must be chased down if it ever appears in a
   release APK.
4. **`READ_GSERVICES` is accepted, not removed.** It is signature-level, not
   user-visible, and merged in by Google's reCAPTCHA (transitive via Firebase
   Auth). Removing a permission an auth library expects is an unverifiable risk
   when sign-in cannot be exercised in CI.
5. **No signing key is configured in CI**, so the release APK is
   `app-release-unsigned.apk`; the smoke job signs it with a throwaway key.
   A real release needs `UBAD_KEYSTORE_*`/`UBAD_KEY_*` or `keystore.properties`.
6. **The local build could not be run in this environment** (no JDK/Android SDK,
   and Google Maven/Gradle hosts are unreachable). Every build and test result
   above comes from CI.
7. Web-only docs (`docs/ANDROID_MIGRATION_PLAN.md`,
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
