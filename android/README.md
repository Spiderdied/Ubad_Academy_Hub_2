# Ubad Academy Hub — native Android app

A native Kotlin / Jetpack Compose rebuild of the Ubad Academy Hub web app (the PWA in the
repository root). It keeps the same **local-first, offline-first** model: no account, no server
database, and all data stays on the device. Existing web users move their data over with the
same **backup JSON v2** file, and the transfer works in both directions.

There is no WebView, iframe, TWA, PDF.js, JavaScript bridge, `localStorage`, IndexedDB or service
worker. Every screen is native Compose.

| | |
|---|---|
| Package | `com.ubad.academy` (debug builds: `com.ubad.academy.debug`) |
| SDK | minSdk 26, target/compile 35 |
| Version | 1.15.4 (matches the web app) |
| Languages | Arabic (default, RTL) and English, switchable in the app (per-app language on Android 13+) |
| Themes | Dark, OLED, Light, Paper, Sage, Rose, each with an optional custom background image |

## Architecture

MVVM with unidirectional data flow: Compose screens → `ViewModel` (`StateFlow`) → repositories →
Room / DataStore / private files, plus the Blog Worker. Hilt provides every dependency. Navigation
Compose uses `@Serializable` type-safe routes, and system Back behaves naturally.

### Stack (each library is used only where it's needed)

| Concern | Library |
|---|---|
| UI | Jetpack Compose, Material 3, adaptive navigation suite (bottom bar on phones, rail on tablets and landscape) |
| Architecture | MVVM (`ViewModel` + `StateFlow`), Hilt DI, Navigation Compose with type-safe routes |
| Structured data | Room (courses, units, contents, notes, attachments, events, tasks, schedule, decks, quizzes, links, Islam state, blog cache) |
| Preferences | DataStore (language, theme, sound, name, onboarding, focus lengths and timer, last PDF page, blog paging) |
| Files | App-private storage (`filesDir`). The system pickers (SAF / Photo Picker) handle input, so no storage permission is needed |
| PDF | Platform `PdfRenderer`: continuous scroll, pinch and double-tap zoom, page jump, fullscreen, remembers the last page, renders off the main thread with a bitmap cache |
| Audio/Video | Media3 ExoPlayer (local files) |
| Images | Coil 3 |
| Network | OkHttp + kotlinx.serialization, used **only** for the Blog Worker |
| Blog HTML | Jsoup allow-list sanitizer rendered as native Compose text and images |
| External links | Custom Tabs for Forms, Summaries, blog links and pages. YouTube opens in the YouTube app or the browser |
| Startup | SplashScreen API, adaptive and monochrome icon, edge-to-edge |

### Project layout

```
android/app/src/main/java/com/ubad/academy/
├── core/            pure helpers: web-compatible uid/dates/URL checks, Hijri (Umm al-Qura), PDF session, blog HTML, sounds
├── data/
│   ├── backup/      BackupCodec (streaming import/export of backup v2), WebNormalizer (Kotlin port of app.js norm*)
│   ├── local/       Room database + DAOs, FileStore (private files), SettingsStore (DataStore)
│   ├── remote/      BlogApi (Cloudflare Worker)
│   └── repository/  one repository per feature area
├── domain/model/    data classes that mirror the web data model 1:1
├── focus/           FocusTimer — absolute end time + AlarmManager
├── notifications/   channels, alarm receiver, notifier
└── ui/              theme, components, navigation, screens/<feature>
```

Strings live in `res/values*/strings_web.xml`, which is **generated** from the web `I18N`
dictionary by `tools/i18n-to-android.js` (`node tools/i18n-to-android.js`). Native-only
strings are in `strings_native.xml`. Colours, typography and shapes are defined once in
`ui/theme`.

## Requirements

| Tool | Version |
|---|---|
| JDK | **17** (Temurin or the JetBrains Runtime bundled with Android Studio). Gradle and AGP 8.7 don't support JDK 8/11 |
| Android SDK | Platform **35** (compileSdk/targetSdk), Build-Tools 35.x, Platform-Tools |
| Android Studio | Ladybug (2024.2) or newer, with AGP 8.7.3 support |
| Gradle | Provided by the wrapper (8.11.1). Don't install it separately |
| Device/emulator | Android 8.0 (API 26) or newer |

## Android Studio setup

1. **File → Open…** and choose the `android/` folder (not the repository root).
2. **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**: choose a JDK 17
   (for example the bundled "jbr-17").
3. Let Android Studio install SDK Platform 35 if it asks. The SDK path goes into
   `android/local.properties`, which is git-ignored.
4. Pick the `app` run configuration and the `debug` build variant, then **Run**. The debug build
   installs as `com.ubad.academy.debug`, so it can sit next to a release install.

## Build commands

Run everything from `android/`:

```bash
./gradlew assembleDebug                 # debug APK   → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # unit tests (Robolectric), reports in app/build/reports/tests/
./gradlew assembleRelease               # release APK → app/build/outputs/apk/release/app-release.apk
                                        #   (app-release-unsigned.apk when no signing config is present)
./gradlew bundleRelease                 # release AAB → app/build/outputs/bundle/release/app-release.aab
./gradlew assembleDebug testDebugUnitTest assembleRelease bundleRelease   # everything CI builds
```

Release builds use **R8** (`isMinifyEnabled = true`) and resource shrinking. The keep rules in
`app/proguard-rules.pro` are the minimum needed: generated serializers for `@Serializable` models
and type-safe navigation routes, plus `-dontwarn` entries for optional OkHttp/Jsoup dependencies.
Room, Hilt, Media3, Coil, DataStore and AndroidX ship their own consumer rules. The R8 mapping for
de-obfuscating crash reports is written to `app/build/outputs/mapping/release/mapping.txt`. Keep
it together with every release you distribute.

## Signing

Signing material is **never** committed. `*.jks`, `*.keystore`, `keystore.properties` and
`local.properties` are git-ignored, and the CI security scan fails if any of them is tracked.

1. Create an upload/release key once and store it outside the repository:
   ```bash
   keytool -genkeypair -v -keystore ~/keys/ubad-release.jks -alias ubad -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Give Gradle the key in one of two ways:
   * `android/keystore.properties` (git-ignored):
     ```properties
     storeFile=/home/you/keys/ubad-release.jks
     storePassword=…
     keyAlias=ubad
     keyPassword=…
     ```
   * or environment variables, for CI secrets: `UBAD_KEYSTORE_FILE`, `UBAD_KEYSTORE_PASSWORD`,
     `UBAD_KEY_ALIAS`, `UBAD_KEY_PASSWORD`.
3. Run `./gradlew assembleRelease bundleRelease`. With a signing config present, the outputs are
   signed `app-release.apk` / `app-release.aab`. Check them with
   `apksigner verify --print-certs app-release.apk`.

If no signing config is present, Gradle still builds the release artifacts, but unsigned.
**Back up the keystore and its passwords.** Losing them means you can never ship an update to
existing installs. For Google Play, enrol in Play App Signing and use this key as the *upload*
key.

## Continuous integration

`.github/workflows/android.yml` runs on every push that touches `android/`, the scanner or the
smoke tools:

1. **Security scan (sources)**, which checks for:
   * secrets across the whole repository
   * WebView and JS-bridge usage in the app code
   * CDN/JS runtime dependencies
   * permissions declared in the app manifest
2. **Debug APK + unit tests** (`assembleDebug testDebugUnitTest`). This includes the backup
   compatibility suites.
3. **Release APK + AAB** (R8), on a manual dispatch with `release: true` or when the commit
   message contains `[release]`.
4. **Security scan (APKs + merged manifest):**
   * secret patterns inside every APK
   * the html2app bridge and CDN JS
   * dangerous or unexpected merged permissions, with the library that added each one
   * bytecode-level attribution of every `android.webkit.WebView` reference, de-obfuscated with
     the R8 mapping
5. **Emulator smoke test (release runs):** the R8 release APK is signed with a throwaway key
   generated inside the job (never stored) and driven on an API 34 emulator by
   `tools/smoke/smoke.py`. It covers:
   * onboarding and the language switch
   * every section and Study tab, all 6 themes, deep links
   * the alarm receiver
   * web-backup import and export through the system file picker
   * the PDF, image and audio viewers
   * rotation, night mode, offline Blog
   * a restart after the process is killed

   Screenshots, the report and logcat are uploaded as `ubad-smoke-evidence`.
6. Artifacts: `ubad-academy-apks` (APKs, AAB, R8 mapping).

## Backup migration (web ⇄ Android)

**Moving from the web app to Android:**
1. In the web app, open **Settings → Backup → Export backup**, keep all sections selected, and save
   `ubad-backup-YYYY-MM-DD.json`.
2. Copy the file to the phone (Downloads, Drive, USB, …).
3. In the Android app, open **Settings → Import backup**, pick the file, choose sections, and tap
   **Restore selected**.

**Moving from Android to the web:** open **Settings → Export backup → Create backup** and save the
file where you want it. Then use **Import** in the web app's Settings.

How compatibility is guaranteed:

* **Web → Android:**
  * Every record goes through the same normalization rules as the web. `WebNormalizer` is a
    line-by-line port of `normCourse`, `normIslam`, `normSchedule`, and the rest.
  * Legacy backups (no `sections`, `lessons`, `tasks`, `focus`, `settings`) are handled.
  * Duplicate ids get new ids, like on the web.
  * Only sections you select are replaced. Everything else is untouched.
* **Android → Web:**
  * Export writes the exact web shape: `{app:"ubad-academy-hub", version:2, exportedAt, sections,
    data:{user, courses, courseAssets:[{id,name,type,data:dataURL}], notes (images/audio as data
    URLs), events, tasks, decks, quizzes, schedule, focus, islam, summaries, forms, backgrounds,
    settings}}`.
  * Android also writes `tasks`, `focus` (daily count and lengths) and `settings` (language,
    sound, theme), so Android → Android restores are lossless. **The web app's own backup format
    has no tasks, focus or settings, and its import ignores these keys.** Dashboard tasks, focus
    lengths, language, theme and sound therefore don't carry over into the web app. That is existing
    web behavior (web → web backups lose them too), and the web code was left unchanged.
* **Large files** are streamed (base64 in chunks), so backups with big PDFs and videos never need
  to fit in memory.
* **Tests:**
  * `BackupCodecTest` covers web → Android semantics, lossless round-trips, partial restores and
    rejected files.
  * `BackupWebCompatTest` checks every section of an Android export against what the web restore
    code reads (see its per-field contract). This includes data created with the native screens.
  * `DataUrlTest` and `StreamJsonReaderTest` cover the streaming codec.
  * The emulator smoke test repeats import and export on the real R8 build.

## Permissions

| Permission | Why | When |
|---|---|---|
| `INTERNET` | Blog (Worker) and blog images | normal permission |
| `POST_NOTIFICATIONS` | Focus/break end alarm | requested only when the user starts a timer for the first time |
| `SCHEDULE_EXACT_ALARM` | The timer ends on time while the app is closed | explained and offered only when a timer starts; if declined, the app falls back to an inexact alarm |

The app requests nothing else: no storage, location, camera, microphone or contacts. It runs no
background service. The timer stores an absolute end time and one `AlarmManager` alarm, so it
survives minimising, screen lock and the app being killed.

## Deep links

`ubadacademy://course/{id}`, `ubadacademy://unit/{courseId}/{unitId}`, `ubadacademy://blog/{postId}`,
`ubadacademy://study?tab=focus` (this one is also used by the timer notification).

## Security

* No secrets in the app. The Blogger API key stays inside the Cloudflare Worker, and the app only
  calls the Worker's public endpoint.
* `tools/android-security-scan.py` runs in CI and fails the build if it finds:
  * API keys, tokens, private keys, service-account files or tracked keystores
  * WebView or JS-bridge usage
  * CDN/JS runtime dependencies
  * unexpected or dangerous permissions (checked in the source and merged manifests)
* The APK scan reports every reference to `android.webkit.WebView`. The only ones come from
  Media3 UI's `WebViewSubtitleOutput`, the optional WebVTT subtitle renderer inside `PlayerView`.
  The app uses the default canvas renderer and never loads content into a WebView. The scan fails
  on any WebView content loading it can't attribute to that library path.
* Blog HTML goes through a Jsoup allow-list. Scripts, styles, forms and event handlers are dropped,
  and only `http(s)` links and images are kept. An embedded iframe (such as a YouTube video) turns
  into a native "open" block that launches outside the app. It is never embedded.
* Every URL the user saves goes through the web's `safeHttpUrl` rules. `javascript:`, `data:` and
  similar schemes are rejected.
* Cleartext traffic is disabled. The `FileProvider` is not exported. It grants temporary per-URI
  read access to one course or note file when the user chooses "open with" or "share", and nothing
  broader.
* Analytics: the web GA4 tag was **not** carried over. A native GA4 integration without Firebase
  would need the Measurement Protocol `api_secret`, which can't be kept secret inside an APK.
  Firebase Analytics was ruled out, so the app sends no analytics.

See `docs/ANDROID_FEATURE_COMPARISON.md` for the screen-by-screen comparison with the web app, and
`docs/ANDROID_MIGRATION_PLAN.md` for the original analysis.

## Known limitations

* **Google Play target API.** The app targets API **35**, as specified. Since **Aug 31, 2026**,
  Google Play only accepts new apps and updates that target **API 36**. An extension to Nov 1, 2026
  can be requested in Play Console. Sideloaded APKs and other stores are unaffected. Publishing on
  Play needs `compileSdk`/`targetSdk` raised to 36, followed by a re-test.
* **Timer after a reboot.** Android clears alarms when the device reboots, and the app deliberately
  doesn't request `RECEIVE_BOOT_COMPLETED`. If the phone restarts during a running focus or break,
  the end notification won't appear. The saved end time is reconciled the next time the app opens.
* **Exact alarms declined.** The timer still works, but Android may deliver the end notification a
  few minutes late (inexact alarm, Doze).
* **PDF.** `PdfRenderer` draws pages as images, so there is no text selection or in-document search,
  and password-protected PDFs can't be opened (an error state is shown).
* **Tasks/focus/preferences into the web app.** The web import ignores `tasks`, `focus` and
  `settings` (its own exports never contain them), so these don't reach the web app. Every other
  section round-trips.
* **Network features** need connectivity: Blog (cached after the first load), YouTube, Forms,
  Summaries and external links.
* **Media size.** Uploads are limited to 50 MB per file, like the web. Backups embed media as base64
  (about +33 %), again like the web.
* **Intentional differences from the web** are listed in `docs/ANDROID_FEATURE_COMPARISON.md`: wipe
  resets the language to Arabic, no analytics, YouTube/Forms/Summaries open externally.
* **Testing scope.** Runtime testing runs on an API 34 emulator in CI. Test manually on a range of
  physical devices before a store release (see the checklist).

## Release checklist

- [ ] `versionCode` bumped and `versionName` set in `app/build.gradle.kts`
- [ ] (Play only) `compileSdk`/`targetSdk` meet the current Play requirement (see Known limitations)
- [ ] Signing configured via `keystore.properties` or env vars; nothing signing-related tracked by git
- [ ] CI release run green: build, unit tests, source and APK security scans, emulator smoke test
- [ ] `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` shows the release certificate
- [ ] R8 `mapping.txt` archived with the release
- [ ] Manual pass on at least one physical phone and one tablet:
  - [ ] first launch and onboarding
  - [ ] Arabic RTL and English
  - [ ] all 6 themes
  - [ ] import a real web backup, then open its PDF, images and audio/video
  - [ ] focus timer with the app closed, with and without the exact-alarm permission
  - [ ] Blog online and offline
  - [ ] export, then re-import in the web app
  - [ ] rotation and Back gesture
- [ ] Store listing, privacy notes: no account, no analytics, data stays on device, network only for the Blog

