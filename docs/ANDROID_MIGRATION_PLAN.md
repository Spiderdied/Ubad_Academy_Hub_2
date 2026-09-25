# UBAD Academy Hub → Native Android: Phase 1 Analysis & Migration Plan

Source analysed: `main` @ `2d07f65` (SW `v1.15.4`): `index.html` (133 lines), `app.js` (3,940),
`style.css` (1,475), `sw.js`, `manifest.json`, `analytics-config.js`, the bundled `assets/`, and two
PDF diagnostic notes. I read every file, and I checked the live Blog Worker endpoint by hand.

---

## 0. The most important finding

**The existing app has no backend of its own.** It has no Firebase, no login, no user accounts,
no admin role, no GitHub API calls, and no server database. It is a **local-first, single-user
PWA**, and all user data lives on the device in IndexedDB and localStorage.
The README says so too: *"No account, no server, no frameworks… all data stays on your device."*

The app talks to only three things over the network:
1. **Blog feed**: `GET https://ubad-blog-api.abdalla-toaila34.workers.dev[?pageToken=…]`. This is a
   Cloudflare Worker that proxies the **Blogger API v3** for `ubad34.blogspot.com`.
2. **Google Analytics 4** (`gtag.js`, ID `G-P011GS30Y3`). It only records anonymous section and
   feature events.
3. **Embedded third-party pages** chosen by the user: YouTube embeds, Google Forms
   (`embedded=true`), and "Summary" links.

It also imports a module at runtime: `https://esm.unpkg.com/@yandeu/js-bridge` (the html2app WebView bridge).

So these sections of the brief do **not apply to the current product**: 7 (Firebase Auth), 8 (Firebase),
14 (FCM), 15 (Admin), 16 (GitHub), and the server-side parts of 9 (instructor-published courses,
attendance, payments). If I built them, I would be inventing functionality and a backend that don't
exist, which §33 forbids. See Decision D1 at the end.

---

## 1. Current architecture

| Aspect | Implementation |
|---|---|
| Shell | One `index.html`, an inline SVG icon sprite plus the logo, and `<main id="stage">` |
| Code | One IIFE in `app.js`. There is no framework and no build step |
| "Screens" | A `LAYERS` registry of `{title(), render(params)}`. Each screen returns a DOM `<section>` |
| Navigation | `Nav` is a custom stack (push/pop/replace/popTo) with 3D depth animation. `Hist` mirrors the stack into `history` so the hardware Back button works in a WebView |
| State | One global `state` object. `saveData()` writes all of it to IndexedDB `kv/appdata` as one JSON blob |
| Storage | IndexedDB `ubad-academy-hub` v2 has 3 stores: `kv` (appdata, `bg-<theme>` blobs), `notes` (with binary attachments), `courseAssets` (`{id, blob}`). localStorage keys: `ubad.prefs.v1`, `ubad.onboarding.v1`, `ubad.analytics.queue.v1`, `ubad_blog_cache_v1` |
| i18n | A hand-written `I18N.en` / `I18N.ar` dictionary (about 280 keys each). `applyLang()` sets `dir=rtl` |
| Theming | CSS variables with 6 themes: `dark` (default), `oled`, `light`, `paper`, `sage`, `rose` |
| Offline | A service worker: cache-first for static assets, network-first for navigation |
| Packaging | It was already packaged as an APK through **html2app (WebView)**. The `Hist` sentinel, `WV.exitApp()` and the js-bridge exist only because of that |

## 2. Screens (LAYERS) → native mapping

| Web layer | What it does | Native screen |
|---|---|---|
| `hub` | Animated 3D grid of 8 section cards, date chip, Ramadan countdown chip, search button, star particles | `HubScreen` (home, adaptive grid) |
| `dashboard` | Greeting by time of day; tiles for prayers x/5, course count, open tasks, note count; quick-add task; today's tasks merged with today's **schedule** sessions (done toggle, delete, overdue chip); next 5 events; 3 most recent notes; quick actions | `DashboardScreen` |
| `courses` | Course cards (code, credits, name, instructor · semester, progress bar) | `CoursesScreen` |
| `courseDetail` | Header, edit and delete, stats, list of units, add unit | `CourseDetailScreen` |
| `unit` | Tabs by content type (text/image/video/audio/pdf) with counts; for each item: done tick, edit, delete; rename and delete unit | `UnitScreen` |
| content modal | Add or edit content: text (max 50k chars), images (several, ≤50 MB each), video (local file ≤50 MB **or** a YouTube URL), audio, PDF (≤50 MB) | `ContentEditorSheet` + system file picker |
| PDF modal | PDF.js 3.11: continuous scroll, prev/next, zoom ±, fit, fullscreen, pinch, lazy rendering near the viewport | `PdfViewerScreen` (native `PdfRenderer`) |
| lightbox | Image gallery with zoom and pan | `ImageViewerScreen` (pager + zoom) |
| `notes` | Search, pinned first, tag chips, attachment icons | `NotesScreen` |
| `noteEditor` | Title, body, tags, pin, **image and audio file attachments** (picked from files; there is no mic recording), guard against discarding unsaved changes | `NoteEditorScreen` |
| `calendar` | Month grid, prev/next month, swipe to change month, events by day, event modal (title, desc, date, time) | `CalendarScreen` + `EventEditorSheet` |
| `islam` ("أنا مسلم") | 4 tabs. **Prayers**: tick 5 daily prayers, 5/5 celebration, per-day history. **Sunnah**: 9 rawatib/duha/qiyam/witr ticks. **Fasting**: toggle today, total count, Mon/Thu and White-Days hints, next 6 recommended fasts (Hijri via Umm al-Qura). **Tasbih**: 5 built-in adhkar plus up to 30 custom ones, targets 33/100/1000, count and lifetime total. Also Hijri date and Ramadan countdown | `IslamScreen` (tabs) |
| `analytics` ⚠ | **The name is misleading: this is the Blog** (`nav.analytics` = "Blog"/"المدونة"). Paginated list, search, refresh, offline cache, "showing cached copy" state | `BlogScreen` |
| `analyticsPost` | Sanitised Blogger HTML article, hero image, "open original" | `BlogPostScreen` |
| `study` | Tabs: **Flashcards** (decks), **Focus** timer, **Google Forms**, **Weekly Schedule** | `StudyScreen` (tabs) |
| `deck` | Flip card, prev/next, shuffle, swipe, add/edit cards | `DeckScreen` |
| `deckTest` | "Test yourself": shuffled, Known/Unknown, final score with message | `DeckTestScreen` |
| `quizEdit` / `quizPlay` | Multiple-choice quizzes (2–4 options) with scoring. **Latent**: the code and data model exist, but no tab links to them | `QuizEditScreen` / `QuizPlayScreen` (exposed as a Study tab; see D3) |
| `linkViewer` | Opens a Forms or Summary link in an iframe, with a 6-second timeout and an "open in browser" fallback | Custom Tabs (see §9) |
| Summaries | CRUD, pin, lastOpened. **Latent in the UI**: reachable from search and backup, but there is no Study tab for it | Study tab "التلخيصات" |
| `settings` | Username, language EN/AR, 6 themes, **custom background image per theme**, sound on/off, backup export/import **by section**, wipe all, developer support (Vodafone Cash number and PayPal email, each with a copy button), about/copyright | `SettingsScreen` |
| search overlay | Ctrl+K or icon. Searches notes, courses, events, decks, forms, summaries | `SearchScreen` (M3 `SearchBar`) |
| onboarding | First run: language, name, theme | `OnboardingScreen` |

Other behaviours: a toast after every save or delete, confirmation dialogs, confetti (5/5 prayers,
task done, focus complete), click/move/back sounds (`Click_1.mp3`, `Alarm.mp3`, with synthesised
fallbacks), vibration, reduced-motion support, and swipe from the start edge to go back.

## 3. Data model (the source of truth, which Room must preserve)

```
appdata = {
  user:{name}, settings:{lang:'en'|'ar', sound:bool, theme:dark|oled|light|paper|sage|rose},
  courses:[{id,name,code,instructor,credits(0-99),semester,createdAt,
            units:[{id,title,contents:[{id,type:text|image|video|audio|pdf,title,text,
                    assetId,assets:[{id,name,mime}],name,mime,source:''|youtube|local,url,done,createdAt}]}]}],
  events:[{id,title,desc,date:YYYY-MM-DD,time:HH:MM|'',createdAt}],
  tasks:[{id,title,done,due,createdAt}],
  schedule:[{id,title,days:[0-6],start,end,doneDates:{date:true},createdAt}],
  decks:[{id,title,createdAt,cards:[{id,front,back}]}],
  quizzes:[{id,title,createdAt,questions:[{q,options[≤4],correct}]}],
  forms / summaries:[{id,title,url,createdAt,lastOpened,pinned}],
  focus:{day,done,focusMins(1-180),breakMins(1-180)},
  islam:{day,prayers{5},rawatib{9},tasbih{mode,count,total,target,adhkar[]},fasts[dates],hist{date:0-5}}
}
notes store:  {id,title,body,tags[≤8],pin,createdAt,updatedAt,images[{name,blob}],audio[{name,blob}]}
courseAssets: {id, blob}          kv 'bg-<theme>': {blob}
```
Backup file: `{app:'ubad-academy-hub', version:2, exportedAt, sections:{…}, data:{…, courseAssets:[{id,type,data:dataURL}], notes[…base64], backgrounds{}}}`.
**The Android app must import and export this exact format.** That is how existing web users bring their data across.

## 4. Backend services

| Service | Used? | Android replacement |
|---|---|---|
| Firebase (any product) | **No** | Not added (see D1) |
| Blogger via Cloudflare Worker | Yes | Retrofit/Ktor-style client (OkHttp + kotlinx.serialization) to the **same Worker URL**, with Room cache |
| Google Analytics 4 (web gtag) | Yes, anonymous events with an offline queue | Needs a decision (D2): Firebase Analytics (needs a `google-services.json`), or leave it out |
| GitHub API | **No** (GitHub is used only as Pages hosting) | Nothing to migrate |
| unpkg js-bridge | Yes (html2app) | **Removed**. Native navigation makes it unnecessary |

## 5. Authentication
**None exists.** "Username" is only a greeting string. Following §7 ("do not invent authentication
methods"), the Android app will have no login. Profile = name, language, theme.

## 6. Database / storage / files
* Large binaries (course media, note attachments, backgrounds) → **app-private files** in
  `filesDir/assets/<id>`. Room stores only metadata and paths. Nothing goes on external storage, so no
  storage permission is needed.
* Structured data → **Room** (tables for courses, units, contents, notes, attachments, events, tasks,
  schedule, decks, cards, quizzes, links, islam_day, prayer_history, fasts, adhkar, blog_posts).
* Prefs (lang, theme, sound, name, onboarding flag, focus lengths, last PDF page) → **DataStore**.
* Uploads = the local file picker (SAF `OpenDocument` / `PickMultipleVisualMedia`). "Downloads" =
  the PDF download link → `CreateDocument` (SAF) to save a copy, or a share sheet. `DownloadManager`
  is not needed because the files are already local.

## 7. External APIs & embeds
Blogger Worker (JSON as described above). YouTube `youtube.com/embed` iframes. Google Forms. Arbitrary
https links. Blogger post images come from `blogger.googleusercontent.com`.

## 8. Security audit (§22)

| Item | Location | Verdict | Action |
|---|---|---|---|
| GA4 Measurement ID `G-P011GS30Y3` | `index.html`, `analytics-config.js` | **SAFE TO SHIP**. Public by design | If Firebase Analytics is chosen, use its own config |
| Blog Worker URL | `app.js:78` | **SAFE TO SHIP**. Public endpoint; the **Blogger API key stays inside the Worker** | Keep. Recommendation: rate-limit the Worker |
| Vodafone Cash number `01093557071` | `app.js:3476` | **SAFE TO SHIP**. Payment details you chose to publish | Move to `strings.xml` (not a secret) |
| PayPal email `abdalla.toaila34@gmail.com` | `app.js:3481` | **SAFE TO SHIP**. Same reason | Same |
| Runtime import from `esm.unpkg.com` | `app.js` boot | **MUST BE REMOVED**. Runs third-party JS from a CDN without integrity checks (supply-chain risk) | Replaced by native back handling |
| Iframes with `allow-same-origin allow-scripts` for user links | `linkViewer` | **MUST BE REMOVED** in native | Custom Tabs: the browser sandbox, no JS bridge |
| Blog HTML sanitiser based on a blacklist | `sanitizeBlogHtml` | Acceptable on web; **replace** | Native rendering (no script execution at all) |
| Firebase keys, service accounts, GitHub PATs, passwords, admin credentials, `.env` | anywhere | **None found** | — |

Outside the code: the author's name and email are exposed through the Worker subdomain and Blogger
author metadata. That is public anyway and needs no action.

## 9. Browser-only functionality that must be replaced

| Web mechanism | Why it can't move over as-is | Native replacement |
|---|---|---|
| PDF.js + worker + cmaps (8 MB of assets) | JS engine | `android.graphics.pdf.PdfRenderer` (platform, maintained, no dependency). Pages are rendered as bitmaps on `Dispatchers.IO`, with an LRU cache, `LazyColumn` for continuous scroll plus a pager option, pinch zoom, fullscreen through `WindowInsetsController`, and the last page saved in DataStore. Arabic displays correctly because it renders the embedded glyphs |
| IndexedDB / localStorage | Browser | Room + files + DataStore |
| Service worker / manifest | PWA | Not needed. The app is installed; the Blog is cached in Room; images via the Coil disk cache |
| `Hist` popstate sentinel, `WV.exitApp`, edge-swipe back | WebView hack | Navigation Compose back stack + predictive back |
| `setInterval` focus timer | Stops when the app is in the background | `endsAt` timestamp persisted in DataStore; ticks come from a `StateFlow` in the ViewModel; **AlarmManager exact alarm** → notification plus `Alarm.mp3` when a phase ends, even if the app is killed. No foreground service |
| `new Audio()` / WebAudio | Browser | `SoundPool` for the click sound, `MediaPlayer`/notification sound for the alarm |
| `navigator.vibrate` | Browser | `HapticFeedback` / `Vibrator` |
| YouTube iframe | iframe | **Decision D4**: open in the YouTube app or browser via Intent (no WebView), with a Coil thumbnail from `img.youtube.com`. The alternative is a small WebView used *only* for the YouTube player |
| Google Forms / Summaries iframe | iframe | Chrome **Custom Tabs** (an in-app browser tab: native, not a WebView). Forms still work, including sign-in |
| Blog `innerHTML` | DOM | Parse with **Jsoup** → strip `style/script` (the posts contain large inline CSS, as I confirmed on the live feed) → `AnnotatedString` via `AndroidHtml.fromHtml`/Compose `LinkAnnotation`. Images appear as Coil `AsyncImage` blocks. "Open original" goes to a Custom Tab |
| `<video>`/`<audio>` for local files | DOM | **Media3 ExoPlayer** (`PlayerView` inside Compose) |
| Canvas confetti / star particles | Canvas | Compose `Canvas` animations. Respect "remove animations" |
| `<a download>` backup export | DOM | SAF `CreateDocument("application/json")`, streamed with base64 so large backups don't cause an OOM |
| `<input type=file>` | DOM | Photo Picker / `OpenDocument` / `OpenMultipleDocuments` |
| `Intl` Hijri (`islamic-umalqura`) | JS `Intl` | `android.icu.util.IslamicCalendar` with `CalculationType.ISLAMIC_UMALQURA` (API 24+) |
| `navigator.onLine` | Browser | `ConnectivityManager.NetworkCallback` → `StateFlow<Boolean>` |
| Clipboard copy | Browser | `ClipboardManager` |

## 10. Proposed native architecture

* **Kotlin 2.x, Compose BOM (stable), Material 3, AGP 8.x, Gradle version catalog.**
  `minSdk 26` (Android 8.0: covers adaptive icons and about 97% of devices), `targetSdk/compileSdk 35`.
  `applicationId com.ubad.academy`, versionCode 1, versionName "1.15.4" (continues from the web).
* **Hilt** for DI, **Room** (KSP), **DataStore**, **Coil 3**, **Media3**, **Jsoup**, **OkHttp +
  kotlinx.serialization**, **core-splashscreen**, **androidx.browser** (Custom Tabs),
  **Material3 adaptive** (`NavigationSuiteScaffold`: bottom bar on phones, rail on tablets and in landscape).
* A single Gradle module, `:app` (the project is small, so extra modules would be abstraction for its own sake), organised by package:

```
com.ubad.academy/
├── UbadApplication.kt, MainActivity.kt
├── core/            (connectivity, dispatchers, sound, haptics, hijri, result/UiState, time utils)
├── data/
│   ├── local/       (UbadDatabase, entities, DAOs, converters, FileStore, PrefsDataStore)
│   ├── remote/      (BlogApi, DTOs, BlogHtmlParser)
│   ├── backup/      (WebBackupCodec: reads/writes v2 JSON, streaming)
│   └── repository/  (CourseRepo, NoteRepo, CalendarRepo, TaskRepo, StudyRepo, IslamRepo, BlogRepo, SettingsRepo, BackupRepo, SearchRepo)
├── domain/model/    (pure Kotlin models; no use-case layer unless logic is shared)
├── notifications/   (channels, FocusAlarmReceiver, optional reminders)
└── ui/
    ├── theme/       (UbadTheme: 6 schemes, typography, shapes, gradient brand brush)
    ├── navigation/  (typed @Serializable routes, NavHost, deep links)
    ├── components/  (CourseCard, UnitRow, ContentCard, DocumentCard, TaskRow, ProgressRing, EmptyState, ErrorState, Skeleton, ConfirmDialog…)
    └── screens/ hub, dashboard, courses, course, unit, pdf, media, notes, calendar, islam, blog, study, settings, search, onboarding
```
* **Theme**: brand gradient `#22D3EE → #3B82F6 → #8B5CF6`, dark base `#050816/#0A1024`. All 6 web
  themes become `ColorScheme`s (dark, oled = pure black, light, paper, sage, rose) plus an optional
  "follow system". Typography: a bundled Arabic-capable font (e.g. IBM Plex Sans Arabic or Noto Kufi,
  both OFL), all sizes in `sp`.
* **Localization**: `values/strings.xml` (English) + `values-ar/strings.xml` (Arabic), ported **from
  the existing I18N dictionaries**. Arabic is the default for new installs. The in-app language switch
  uses `AppCompatDelegate.setApplicationLocales` (per-app language, Android 13 system settings integration).
* **Deep links**: `ubadacademy://course/{id}`, `…/unit/{courseId}/{unitId}`, `…/blog/{postId}`,
  `…/study/focus`. No HTTPS App Links, because there is no owned domain to verify.
* **Permissions**: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (requested only when the
  user first starts a focus timer), and `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` for timer end.
  Nothing else: no storage, camera, mic, or location.
* **Builds**: debug APK, release APK (R8 + resource shrinking, signing from `keystore.properties`
  / env vars, **never committed**), and `bundleRelease` AAB.

## 11. Migration phases (each one ends with a green `./gradlew assembleDebug` plus tests)

1. ✅ Analysis (this document)
2. Architecture skeleton: Gradle, version catalog, Hilt, manifest, splash, adaptive icon from the existing PNG
3. Theme (6 schemes) + RTL + strings (full port of the i18n) + navigation shell (hub, adaptive nav) + onboarding
4. Data layer: Room schema, FileStore, DataStore, repositories + **web backup import/export** (brings existing users' data in early)
5. Hub + Dashboard (tasks, schedule-merge, events, recent notes, tiles)
6. Courses → course detail → unit → content editor (all 5 types, file picker, 50 MB limit)
7. Native PDF viewer + image gallery + ExoPlayer audio/video + YouTube handling
8. Notes (+ attachments) + Calendar + Search
9. Study tools: decks, flip, test-yourself, quizzes, focus timer with alarm, schedule, Forms/Summaries (Custom Tabs)
10. أنا مسلم: prayers, sunnah, fasting (Umm al-Qura), tasbih, Ramadan countdown
11. Blog (Worker client, Room cache, pagination, pull-to-refresh, native article rendering)
12. Settings: profile, language, theme, per-theme background, sound, backup, wipe, support (copy to clipboard)
13. Offline polish, notifications, accessibility pass (TalkBack, 48 dp targets, font scale 200%)
14. Security re-audit, unit tests (repos, backup codec, Hijri, timer, schedule logic, focus clamping), Compose UI tests (navigation, back stack, PDF open, offline state, RTL)
15. Release config (R8 rules, signing template, AAB) + README

Brief phases that map to nothing: 5 (auth), 13 (admin), and FCM in 15. These are skipped unless D1 says otherwise.

## 12. Files to create / modify
**Create** (new `android/` directory; the web app stays untouched so the PWA keeps working):
`android/settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`,
Gradle wrapper, `app/build.gradle.kts`, `app/proguard-rules.pro`, `AndroidManifest.xml`,
`res/values{,-ar,-night}/…`, `res/mipmap-anydpi-v26/ic_launcher.xml` + foreground/monochrome layers,
`res/raw/click.mp3, alarm.mp3` (copied from `assets/sounds`), `res/font/*`, `res/xml/{backup_rules,data_extraction_rules,locales_config}.xml`,
about 120 Kotlin files following the tree above, `app/src/test/**`, `app/src/androidTest/**`,
`android/README.md`, `.gitignore` entries (`build/`, `local.properties`, `*.jks`, `keystore.properties`).
**Modify**: root `README.md` (add a section on the Android app). Optionally fix the web `sw.js`/`manifest.json`,
which reference `icon.svg`/`icon-maskable.svg`: those files don't exist in the repo.

## 13. Environment note
This sandbox currently has **no JDK or Android SDK** (2 vCPU, 3 GB RAM). To meet "compile after every
phase" I will install JDK 17 + Android command-line tools + platform 35 into the workspace (about 1.5 GB,
kept out of Git) and build headless with `--no-daemon -Xmx2g`. Instrumented tests (emulator) can't run
here. They will be written and compile-checked; JVM unit tests and Robolectric-based Compose tests will actually run.

## 14. Decisions needed before Phase 2
* **D1: Backend scope.** (a) Keep it faithful: a local-first, single-user app with no login, no
  Firebase, no admin *(recommended; matches the real product and loses no data)*. (b) Add a new
  Firebase backend (Auth + Firestore + Storage + roles + FCM). That is new functionality and needs a
  Firebase project and `google-services.json` from you, plus server-side security rules.
* **D2: Analytics.** Firebase Analytics (needs `google-services.json`) / none.
* **D3: Latent features.** Should the unreachable **Quizzes** and **Summaries** get Study tabs? *(recommended: yes)*
* **D4: YouTube lessons.** Open externally (strictly no WebView) / a player limited to YouTube only.
