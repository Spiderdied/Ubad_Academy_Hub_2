# UBAD ACADEMY HUB

A local-first, **spatial academic operating environment** — dark, glassmorphic,
3D, and fully offline. No account, no server, no frameworks.

## Features
- **Hierarchical spatial navigation** — Hub → Category → Subcategory → Content,
  each level a real layer that moves through depth (never a carousel).
- **Dashboard** — tasks, upcoming events, GPA, recent notes, quick actions.
- **Courses** — courses → units → lessons with progress tracking.
- **Notes** — full editor with **image** and **audio attachments** (IndexedDB).
- **Calendar** — month view, events, contextual swipe (month change only).
- **Grades / GPA** — standard 4.0 scale, mathematically correct, target planner.
- **Analytics** — canvas-drawn charts (no libraries).
- **Study Tools** — flashcards (flip + shuffle + swipe) and quizzes with scoring.
- **Settings** — real **English/Arabic** localization with full RTL, username
  (greeting only), dark/light theme, sound toggle, backup/restore.
- **Search** — compact icon → glass overlay across notes, courses, events, decks.
- **PWA** — installable, offline shell, service worker, app icons.
- **Privacy** — all data stays on your device. Backup is a plain JSON file.

## Structure

/
├── index.html          # shell + inline SVG icon sprite / logo
├── style.css           # design system (glass, depth, RTL, reduced-motion)
├── app.js              # state, storage, navigation, i18n, audio, sections
├── manifest.json
├── sw.js
└── assets/
    ├── icons/          # icon.svg · icon-maskable.svg (replaceable)
    └── audio/          # optional: click.mp3 · 3d-move.mp3 · back.mp3 · transition.mp3


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
The app **works perfectly without audio files** — it falls back to tiny
synthesized tones. To use your own sounds later, simply drop these files in:
```
assets/audio/click.mp3        # button / card activation
assets/audio/3d-move.mp3      # entering a layer
assets/audio/back.mp3         # returning
assets/audio/transition.mp3   # deep layer transitions
```
Missing files fail silently and never block navigation. Sounds can be
disabled in Settings.

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
- **IndexedDB** → notes + binary attachments (images/audio), app data.
- **localStorage** → lightweight prefs (language, theme, username, sound).
- **Export backup** produces `ubad-backup-YYYY-MM-DD.json` (attachments embedded
  as base64 data URLs). Import on any device restores everything — with a
  clear confirmation before overwriting. Imported files are validated and
  strictly normalized; nothing is ever executed.

## Updating
Bump `VERSION` inside `sw.js` (e.g. `v1.0.1`) when you change app files —
clients pick up the new shell on next visit.

## Privacy
Everything runs and stays on-device. No analytics, no tracking, no network
calls beyond serving the static files themselves.
````

