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

## Stack (each library is used only where it's needed)

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

## Project layout

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

## Building

JDK 17 and the Android SDK (platform 35) are required.

```bash
cd android
./gradlew assembleDebug testDebugUnitTest   # debug APK + unit tests
./gradlew assembleRelease bundleRelease     # release APK + AAB (R8 + resource shrinking)
```

Outputs are written to `app/build/outputs/apk/{debug,release}/` and `app/build/outputs/bundle/release/`.

**Release signing** is never committed (`*.jks`, `*.keystore` and `keystore.properties` are
git-ignored). Provide it with either of these:

* `android/keystore.properties`:
  ```properties
  storeFile=/absolute/path/to/release.jks
  storePassword=…
  keyAlias=…
  keyPassword=…
  ```
* environment variables: `UBAD_KEYSTORE_FILE`, `UBAD_KEYSTORE_PASSWORD`, `UBAD_KEY_ALIAS`, `UBAD_KEY_PASSWORD`.

If neither is present, the release build is produced unsigned.

**CI** (`.github/workflows/android.yml`) runs on every push that touches `android/`:

1. A source security scan.
2. `assembleDebug` and the unit tests.
3. An APK and merged-manifest security scan.
4. APK upload (artifact `ubad-academy-apks`).

Release APK and AAB are built by a manual dispatch (`release: true`) or by a commit message
containing `[release]`.

## Data and backup compatibility

* **Web → Android:** Settings → Backup → Import accepts a file exported by the web app. You pick
  sections just like on the web. Every record goes through the same normalization rules as the web
  (`WebNormalizer` is a line-by-line port of `normCourse`, `normIslam`, `normSchedule`, …). Legacy
  backups without a `sections` object and legacy `lessons` are handled.
* **Android → Web:** Export writes the exact web shape: `{app:"ubad-academy-hub", version:2,
  exportedAt, sections, data:{…, courseAssets:[{id,type,data:dataURL}], notes[].images/audio
  as data URLs, backgrounds}}`. The file is named `ubad-backup-YYYY-MM-DD.json`.
* Large files are streamed (base64 in chunks), so backups with big PDFs and videos don't need to fit
  in memory.
* Tests: `BackupCodecTest` covers web → Android semantics, lossless round-trips, partial restores and
  rejected files. `BackupWebCompatTest` checks every section of an Android export, including data
  created with native screens, against what the web restore code reads. `DataUrlTest` and
  `StreamJsonReaderTest` cover the streaming codec.

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
