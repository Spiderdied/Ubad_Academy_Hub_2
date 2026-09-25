# Web ↔ Android feature comparison

This compares the web/PWA app (`index.html`, `app.js`, `style.css`) with the native app in
`android/`, one feature at a time. "Same" means the behavior and stored data were migrated
unchanged. Every intentional difference is listed in the *Deviations* section with its reason.

## Screens

| Web layer / feature | Android | Status |
|---|---|---|
| **Onboarding** (language, name, theme) | `OnboardingGate` | Same |
| **Hub**: section grid, date chip, Ramadan countdown chip, search button, star particles | `HubScreen` (adaptive grid, staggered entrance, `HubStars` port of `FX.starsStart`, off when animations are disabled or on low-RAM devices) | Same |
| **Dashboard**: greeting by time of day, prayer/course/task/note tiles, quick-add task, today's tasks + schedule sessions (done, delete, overdue), next 5 events, 3 recent notes, quick actions | `DashboardScreen` | Same |
| **Courses** list (code, credits, instructor · semester, progress) | `CoursesScreen` | Same |
| **Course detail**: edit/delete, stats, units, add unit | `CourseDetailScreen` | Same |
| **Unit**: tabs by type with counts, done tick, edit/delete, rename/delete unit | `UnitScreen` | Same |
| Content editor: text ≤50k, several images, video file or YouTube URL, audio, PDF, 50 MB limits | `ContentEditorDialog` + system pickers | Same |
| **PDF viewer** (PDF.js): continuous scroll, prev/next, zoom, fit, fullscreen, pinch, lazy rendering | `PdfViewerScreen` on the platform `PdfRenderer`: continuous scroll, pinch and double-tap zoom, page jump, fullscreen, **remembers the last page**, background rendering + LRU cache, Arabic PDFs, large files | Same, plus last-page memory |
| Image lightbox (zoom/pan gallery) | `ImageViewerScreen` (pager + zoom) | Same |
| Local audio/video (`<audio>`/`<video>`) | `MediaPlayerScreen` (Media3 ExoPlayer) | Same |
| YouTube (`youtube.com/embed` iframe) | Thumbnail card that opens the YouTube app, or the browser if it isn't installed | Deviation 3 |
| **Notes**: search, pinned first, tag chips, attachment icons | `NotesScreen` | Same |
| **Note editor**: title, body, tags, pin, image/audio attachments, guard against discarding unsaved changes | `NoteEditorScreen` (the guard uses `BackHandler`) | Same |
| **Calendar**: month grid, prev/next, swipe changes month only, events by day, event modal | `CalendarScreen` (the swipe direction follows RTL like the web) | Same |
| **أنا مسلم**: 5 prayers + 5/5 celebration + history; 9 Sunnah; fasting + Mon/Thu + White Days + next 6 recommended fasts; Umm al-Qura Hijri; tasbih (5 built-in + ≤30 custom, targets 33/100/1000, lifetime total, vibration); Ramadan countdown | `IslamScreen` (4 tabs) | Same. No prayer times and no location, as on the web |
| **Blog** (`analytics` layer): paginated list, search, refresh, offline cache, "cached copy" notice | `BlogScreen` (same Worker, Room cache, loading/empty/error/offline states) | Same |
| **Blog post**: sanitized HTML, hero image, open original | `BlogPostScreen`: Jsoup allow-list rendered as native text and images, links in Custom Tabs | Same (native rendering, no WebView) |
| **Study → Flashcards**: decks, flip, prev/next, shuffle, swipe (RTL-aware), add/edit cards | `DeckScreen` | Same (card order, shuffle and swipe direction preserved) |
| **Test yourself**: shuffled, Known/Unknown, final score message | `DeckTestScreen` | Same |
| **Study → Focus**: focus/break lengths 1–180, daily done count, alarm, confetti, auto-switch to break | `FocusTab` + `FocusTimer` (saved absolute end time + `AlarmManager`; notifies while the app is closed) | Same, plus a notification when the app is closed |
| **Study → Google Forms** | Study tab; links open in Custom Tabs | Deviation 4 |
| **Study → Weekly schedule** (5-minute time steps, new entries first, done per date) | `ScheduleTab` | Same |
| **Quizzes** (edit/play, 2–4 options, scoring) — latent in the web UI | `QuizEditScreen` / `QuizPlayScreen`, reachable from a Study tab | Deviation 5 |
| **Summaries** (CRUD, pin, lastOpened) — latent in the web UI | Study tab; links open in Custom Tabs | Deviation 5 |
| Link viewer (iframe with a 6-second timeout and browser fallback) | Custom Tabs, falling back to the browser | Deviation 4 |
| **Settings**: name, language, 6 themes, background image per theme, sound, backup export/import by section, wipe, developer support (copy number/email), about/rights | `SettingsScreen` | Same, except Deviation 1 |
| **Search** overlay (Ctrl/Cmd + K or icon): notes, courses, events, decks, forms, summaries | `SearchScreen` (icon, and Ctrl/Cmd + K on hardware keyboards) | Same |

## Cross-cutting behavior

| Web | Android | Status |
|---|---|---|
| Toast after every save/delete, confirmation dialogs | Snackbars/toasts from string resources, `ConfirmDialog` | Same |
| Confetti (5/5 prayers, all Sunnah, task done, focus complete, tasbih target, quiz ≥80%) | `ConfettiOverlay`, off when animations are disabled | Same |
| Sounds: `Click_1.mp3`, `Alarm.mp3`, synthesized move/back/transition blips (their mp3s are missing on the web) | `SoundPool` with the same click and alarm files at the same volumes; the move/back/transition blips are rendered from the same oscillator parameters into WAVs | Same |
| Reduced motion (`prefers-reduced-motion`) | System "remove animations" setting | Same |
| Swipe from the start edge to go back | System back gesture and predictive back, with the Navigation Compose back stack | Deviation 6 |
| AR/EN with full RTL; strings from `I18N` | `strings_web.xml` generated from `I18N` + `strings_native.xml`; per-app language | Same |
| 6 themes: Dark, OLED, Light, Paper, Sage, Rose | Material 3 color schemes + `UbadColors` extension per theme | Same |
| Offline-first (service worker + IndexedDB) | Room + DataStore + private files; only the Blog touches the network | Same, natively |
| Responsive layout | Adaptive grid, bottom bar on phones, navigation rail on tablets and landscape, width-capped reading screens | Same |
| PWA install, icons, splash | Adaptive + monochrome launcher icon, SplashScreen API, edge-to-edge | Native equivalent |
| — | Deep links `ubadacademy://course/{id}`, `unit/{c}/{u}`, `blog/{id}`, `study?tab=focus` | Added (required) |

## Data

All of these keep their meaning, field by field, and use the same limits as the web
(`WebNormalizer` ports the `norm*` functions):

* courses, units, contents (with legacy `lessons`), course assets
* notes with image/audio attachments
* events, tasks, weekly schedule
* flashcard decks, quizzes, forms, summaries
* focus settings, the Islam state (prayers, Sunnah, fasts, tasbih, custom dhikr, history)
* user name, language, theme, sound, per-theme background images

Backup v2 import and export are compatible in both directions. The unit tests cover this:

* `BackupCodecTest` covers web → Android.
* `BackupWebCompatTest` covers Android → Web.

## Deviations (intentional)

1. **Wipe all data** resets the language to **Arabic**, the native default for new installs. The web
   resets it to English. Everything else matches: all data is cleared and onboarding is not shown
   again.
2. **Analytics removed.** The web sends anonymous GA4 events. A clean native GA4 integration
   without Firebase would use the Measurement Protocol, which needs an `api_secret` that can't be
   kept secret inside an APK. Firebase Analytics was ruled out, so the app sends no analytics.
3. **YouTube** videos open in the YouTube app or the browser instead of an embedded iframe. This
   follows the no-WebView, no-custom-player requirement.
4. **Forms, Summaries and external pages** open in Custom Tabs instead of the in-page iframe viewer.
   The 6-second "site refused to embed" fallback is no longer needed.
5. **Quizzes and Summaries** get their own Study tabs. On the web, the code and data existed but no
   tab linked to them. Their behavior and data format are unchanged.
6. **Navigation** uses the Android back stack and system back gesture instead of the web's history
   hack and custom edge swipe.
7. **Removed web-only plumbing:** service worker, `manifest.json`, the unpkg `html2app` bridge,
   `localStorage`/IndexedDB and PDF.js. Native equivalents replace each one.

## Not applicable

The old root README also lists **Grades/GPA** and **canvas analytics charts**. Neither exists in
the current `app.js`, which only contains unused `ana.*` i18n keys, so there was nothing to migrate.
