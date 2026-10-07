# UBAD ACADEMY HUB

A local-first, **spatial academic operating environment** — dark, glassmorphic,
3D, and fully offline. Local-first by default, with **optional** Google account
cloud sync. No server of its own, no frameworks, no build step.

> **Native Android app:** a Kotlin / Jetpack Compose build lives in
> [`android/`](android/README.md) (same data via backup v2 **and** the same
> Firestore + Backblaze B2 cloud state, no WebView).
> See [`docs/ANDROID_FEATURE_COMPARISON.md`](docs/ANDROID_FEATURE_COMPARISON.md).

## Features
- **Hierarchical spatial navigation** — Hub → Category → Subcategory → Content,
  each level a real layer that moves through depth (never a carousel).
- **Dashboard** — tasks, upcoming events, GPA, recent notes, quick actions.
- **Courses** — courses → units → lessons with progress tracking.
- **Notes** — full editor with **image** and **audio attachments** (IndexedDB).
- **Calendar** — month view, events, contextual swipe (month change only).
- **Grades / GPA** — standard 4.0 scale, mathematically correct, target planner.
- **Analytics** — canvas-drawn charts (no libraries).
- **Study Tools** — flashcards (flip + shuffle + swipe), quizzes with scoring,
  a customizable Focus/Break timer, saved **Google Forms** tests and saved
  **Summaries** links (opened in a shared internal viewer, with a graceful
  external-browser fallback when a site refuses to be embedded).
- **Settings** — real **English/Arabic** localization with full RTL, username
  (greeting only), dark/light theme, sound toggle, backup/restore,
  **Google account + cloud sync**.
- **Search** — compact icon → glass overlay across notes, courses, events, decks.
- **PWA** — installable, offline shell, service worker, app icons.
- **Privacy** — data stays on your device unless you sign in and enable sync.
  Backup is a plain JSON file.

## Structure

/
├── index.html          # shell + inline SVG icon sprite / logo
├── style.css           # design system (glass, depth, RTL, reduced-motion)
├── app.js              # state, storage, navigation, i18n, audio, sections, cloud sync
├── firebase-auth.js    # Firebase Auth + Firestore + B2 Worker bridge (classic script)
├── firebase.json       # Firestore/Storage rules deploy config
├── firestore.rules     # per-user document rules
├── storage.rules       # per-user object rules
├── storage-backend/    # Backblaze B2 Cloudflare Worker (deploy separately)
├── manifest.json
├── sw.js
├── android/            # native Kotlin / Jetpack Compose app
└── assets/
    ├── icons/          # icon.svg · icon-maskable.svg (replaceable)
    ├── sounds/         # Click_1.mp3 · Alarm.mp3 (bundled, always available offline)
    └── audio/          # optional: 3d-move.mp3 · back.mp3 · transition.mp3


## Run
Open `index.html` directly, or serve locally (recommended):
```bash
python -m http.server 8080
```

## Deploy (GitHub Pages)
1. Push all files to a repository.
2. Settings → Pages → deploy from branch (`main` / root).
3. Done — the service worker registers automatically over HTTPS.

## Audio
UBAD ships with two bundled sound files that always work offline:
```
assets/sounds/Click_1.mp3   # default interaction/click sound (buttons, nav, toggles…)
assets/sounds/Alarm.mp3     # plays when a Focus session or Break ends
```
Everything else **still works perfectly without extra audio files** — it falls
back to tiny synthesized tones. To add your own sounds for layer transitions
later, drop these optional files in:
```
assets/audio/3d-move.mp3      # entering a layer
assets/audio/back.mp3         # returning
assets/audio/transition.mp3   # deep layer transitions
```
Missing files fail silently and never block navigation. Sounds can be
disabled in Settings — when Sound is off, neither Click_1.mp3 nor
Alarm.mp3 play.

## Replacing the logo
The logo lives in two places, both isolated for easy replacement:
1. The `#i-logo` symbol inside `index.html` (the in-app logo).
2. `assets/icons/icon.svg` / `icon-maskable.svg` (the PWA icon).
Replace those — nothing else in the app changes.

## Icons (optional PNG upgrade)
SVG icons satisfy modern installability. For maximum compatibility you can
export PNGs and add them to `manifest.json`:
```json
{ "src": "assets/icons/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any" },
{ "src": "assets/icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any" }
```

## Data & Backup
- **IndexedDB** (`ubad-academy-hub`, v4) → notes + binary attachments
  (images/audio), course assets, per-theme backgrounds, app data.
- **localStorage** → lightweight prefs (language, theme, username, sound).
- **Export backup** produces `ubad-backup-YYYY-MM-DD.json` (attachments embedded
  as base64 data URLs). Import on any device restores everything — with a
  clear confirmation before overwriting. Imported files are validated and
  strictly normalized; nothing is ever executed.

## Updating
Bump `VERSION` inside `sw.js` (e.g. `v1.0.1`) when you change app files —
clients pick up the new shell on next visit.

## Privacy
Everything runs and stays on-device by default. Cloud sync is opt-in: it only
becomes active after you sign in with Google. Backup is never uploaded
automatically.

## Copyright

**© 2026 UBAD Academy Hub — All Rights Reserved.**

This project and its source code are proprietary. Unauthorized copying, reproduction, modification, redistribution, or use of the source code is prohibited without explicit permission from the copyright holder.

## Usage analytics (optional)
UBAD includes an offline-aware Google Analytics 4 integration for general usage
statistics such as section/feature usage. It never sends names, emails, phone
numbers, notes, course titles, files, or other user content.

To enable it, open `analytics-config.js` and set:
```js
window.UBAD_ANALYTICS_ID = 'G-XXXXXXXXXX';
```
Leave it empty to keep analytics disabled. Events used by the app are queued
locally while offline and flushed when an internet connection returns.

## Google account sync (optional)

Signing in with Google is **optional**. After sign-in the app synchronizes
structured UBAD data through the authenticated user document at
`users/{UID}` in Cloud Firestore: profile and settings, courses, notes text and
metadata, events, tasks, decks, quizzes, weekly schedule, Google Forms,
summaries, focus settings, and the Islamic section state.

The app stays local-first. IndexedDB remains the local source of truth for
binary media (note images/audio, course assets, PDFs and other course files,
custom background images). Their metadata and ids are synchronized, and the
binaries themselves are mirrored to Backblaze B2 (below).

Sync is automatic after sign-in, retries when the device comes back online,
listens for newer cloud changes, and offers manual **Sync now**, **Upload to
cloud** and **Download from cloud** actions in Settings.

**First sign-in with both local and cloud data asks** whether to Upload,
Restore or Merge. Nothing is overwritten silently.

## Backblaze B2 file storage

Binary file sync uses **Backblaze B2**. Firebase Auth and Firestore remain in
use for Google authentication and structured cloud state.

All B2 traffic goes through the Cloudflare Worker in
[`storage-backend/worker.js`](storage-backend/worker.js)
(`ubad-academy-sync`). The Worker:

- validates the caller's Firebase ID token against Firebase Auth REST,
- derives the UID **server-side** from that token — it never trusts a UID sent
  by the client,
- only permits paths inside `users/<firebase-uid>/...`, rejecting `..` and
  null bytes,
- keeps the B2 application key secret.

Endpoints: `GET /health`, `POST /upload`, `GET /download?path=…`,
`DELETE /delete?path=…`.

Cloud keys mirror the local layout:

```
users/<UID>/notes/<noteId>/images/<n>-<name>
users/<UID>/notes/<noteId>/audio/<n>-<name>
users/<UID>/courseAssets/<assetId>
users/<UID>/backgrounds/<theme>
```

Deployment and the required Worker secrets (`B2_KEY_ID`,
`B2_APPLICATION_KEY`, `FIREBASE_API_KEY`) are documented in
[`storage-backend/README.md`](storage-backend/README.md). **None of these
secret values belong in the PWA, the Android app, or this repository.**
