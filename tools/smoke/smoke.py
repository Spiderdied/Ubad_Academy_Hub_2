#!/usr/bin/env python3
"""Runtime smoke test of the R8-minified release APK on an emulator (run by CI).

  python3 tools/smoke/smoke.py <apk> <out-dir>

Drives the real UI through `adb` + `uiautomator dump`: onboarding, language switch, every hub
section, Study tabs, all 6 themes, deep links, rotation, night mode, offline Blog, the focus
alarm receiver, backup import (web-shaped v2 file) through the system picker, the imported PDF
/ image / audio in the native viewers, backup export validated as JSON v2, and an
install-over-existing-data update (§22 data-migration release blocker).
The harness waits for boot and clears system ANR dialogs, so an unstable emulator is
reported as such instead of as an app failure.

Hard failures (exit 1): the process crashes or dies, the app does not launch, onboarding cannot be
completed, or a checked screen never appears. Everything is written to <out-dir>/report.txt plus
a screenshot per step.
"""
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = 'com.ubad.academy'
APK, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
report, failures = [], []


def adb(*args, check=False, timeout=90):
    r = subprocess.run(['adb', *args], capture_output=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.decode(errors='replace')}")
    return r.stdout.decode('utf-8', errors='replace')


def sh(cmd, timeout=90):
    return adb('shell', cmd, timeout=timeout)


def log(status, name, detail=''):
    detail = detail.replace('\n', ' ')
    line = f'{status:5} {name}' + (f' — {detail[:600 if status == "FAIL" else 160]}' if detail else '')
    print(line, flush=True)
    report.append(line)
    if status == 'FAIL':
        failures.append(name)


def nodes():
    for _ in range(3):
        sh('uiautomator dump /sdcard/ui.xml >/dev/null 2>&1')
        raw = sh('cat /sdcard/ui.xml')
        try:
            return list(ET.fromstring(raw[raw.index('<?xml'):] if '<?xml' in raw else raw).iter('node'))
        except Exception:
            time.sleep(1)
    return []


def texts(ns=None):
    ns = nodes() if ns is None else ns
    return [t for n in ns for t in (n.get('text'), n.get('content-desc')) if t]


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2


_SCREEN = None


def screen_size():
    """(width, height) of the emulator screen, read once and cached."""
    global _SCREEN
    if _SCREEN is None:
        m = re.findall(r'(\d+)x(\d+)', sh('wm size'))
        _SCREEN = (int(m[-1][0]), int(m[-1][1])) if m else (1080, 1920)
    return _SCREEN


def visible_center(n, margin=6):
    """Centre of the part of node `n` that is actually on screen, or None.

    uiautomator dumps include nodes that are scrolled out of view. Tapping such a
    node's geometric centre puts the tap outside the screen (or on whatever else
    is there) and silently does nothing, while the caller believes it tapped.

    That is exactly what made the §22 post-update PDF check fail three times in a
    row while the identical interaction passed elsewhere in the suite: the action
    list containing "Open PDF" sat below the fold in that particular navigation, so
    "Open PDF" was found in the dump but tapping it did nothing. Callers must keep
    scrolling while this returns None.
    """
    b = re.findall(r'\d+', n.get('bounds') or '')
    if len(b) < 4:
        return None
    x1, y1, x2, y2 = (int(v) for v in b[:4])
    w, h = screen_size()
    cx1, cy1 = max(x1, 0), max(y1, 0)
    cx2, cy2 = min(x2, w), min(y2, h)
    if cx2 - cx1 < margin or cy2 - cy1 < margin:
        return None
    return (cx1 + cx2) // 2, (cy1 + cy2) // 2


def find(pattern, ns=None, last=False):
    rx = re.compile(pattern)
    hits = [n for n in (nodes() if ns is None else ns)
            if any(t and rx.fullmatch(t.strip()) for t in (n.get('text'), n.get('content-desc')))]
    return (hits[-1] if last else hits[0]) if hits else None


def swipe_up():
    size = re.findall(r'(\d+)x(\d+)', sh('wm size'))[-1]
    w, h = int(size[0]), int(size[1])
    sh(f'input swipe {w // 2} {int(h * .75)} {w // 2} {int(h * .3)} 300')
    time.sleep(1)


def swipe_down():
    size = re.findall(r'(\d+)x(\d+)', sh('wm size'))[-1]
    w, h = int(size[0]), int(size[1])
    sh(f'input swipe {w // 2} {int(h * .3)} {w // 2} {int(h * .75)} 300')
    time.sleep(0.6)


def tap(pattern, scrolls=4, wait=2.0, last=False):
    """Taps the first ON-SCREEN node matching; scrolls down, then back up, to find one.

    Only nodes with a visible area are tapped (see visible_center): a match that is
    scrolled out of view is skipped and the scroll continues, instead of firing a
    tap at coordinates outside the screen and silently doing nothing.
    """
    rx = re.compile(pattern)
    moves = [None] + [swipe_up] * scrolls + [swipe_down] * (scrolls * 2)
    for move in moves:
        if move:
            move()
        hits = [n for n in nodes()
                if any(t and rx.fullmatch(t.strip()) for t in (n.get('text'), n.get('content-desc')))]
        for n in (list(reversed(hits)) if last else hits):
            pos = visible_center(n)
            if pos is None:
                continue
            sh(f'input tap {pos[0]} {pos[1]}')
            time.sleep(wait)
            return True
    return False


def tap_tab(pattern, anchor=r'Text · \d+|نص · \d+'):
    """Taps a tab in a horizontally scrolling tab row, swiping the row (both directions) until it shows."""
    if tap(pattern, scrolls=0):
        return True
    # Any visible tab gives the row's y coordinate. The anchor (the "Text" tab) is
    # only a hint: a unit legitimately may have no text item, and then the old code
    # gave up before it ever tried to scroll the tab row.
    a = find(anchor) or find(r'.* · \d+')
    if a is None:
        return False
    _, y = center(a)
    w = int(re.findall(r'(\d+)x(\d+)', sh('wm size'))[-1][0])
    for x1, x2 in ((int(w * .85), int(w * .15)),) * 3 + ((int(w * .15), int(w * .85)),) * 6:
        sh(f'input swipe {x1} {y} {x2} {y} 300'); time.sleep(0.8)
        if tap(pattern, scrolls=0):
            return True
    return False


# A system "… isn't responding" dialog (typically the launcher, while the
# emulator is still settling) covers the screen and makes every later UI check
# fail. Matched as a FULL match because `find()` uses fullmatch on each text
# node. Both the typographic and the plain apostrophe are covered.
ANR_DIALOG = r".*(?:isn't|isn\u2019t|not responding|لا يستجيب).*"
# "Wait" keeps the process alive. If it is *our* app that is genuinely hung, the
# dialog returns and the check still fails on the retry — so dismissing never
# hides a real defect, it only clears emulator obstruction.
ANR_DISMISS = r"Wait|انتظار|إنتظار"


# The PDF viewer's "Page 1 of 2" indicator. Android formats %d with the *locale's*
# numerals, so the same string resource renders "صفحة ١ من ٢" in Arabic: the failure
# this replaced was an assertion that only accepted Western digits, which made a
# working viewer look broken. Match either script.
PDF_PAGE_INDICATOR = r'Page\s+\d+\s+of\s+\d+|صفحة\s+[0-9\u0660-\u0669\u06F0-\u06F9]+\s+من\s+[0-9\u0660-\u0669\u06F0-\u06F9]+'


def dismiss_anr(attempts=3):
    """Clears a system ANR dialog. Returns True when one was dismissed.

    Keeps emulator instability from being reported as an app failure: the caller
    retries the check afterwards, so a real failure survives both attempts.
    """
    dismissed = False
    for _ in range(attempts):
        if find(ANR_DIALOG) is None:
            return dismissed
        dismissed = True
        if not tap(ANR_DISMISS, scrolls=0, wait=1.5):
            sh('input keyevent KEYCODE_BACK')   # no Wait button — dismiss the dialog
            time.sleep(1)
        time.sleep(1)
    return dismissed


def wait_for_boot(max_secs=240):
    """Waits for the emulator to finish booting and settle before driving the UI.

    An ANR during boot was observed covering the screen before the app could be
    tested; waiting here makes an unstable CI emulator distinguishable from a
    broken app.
    """
    adb('wait-for-device', timeout=max_secs)
    end = time.time() + max_secs
    while time.time() < end:
        if sh('getprop sys.boot_completed').strip() == '1':
            break
        time.sleep(2)
    time.sleep(5)          # let SystemUI and the launcher settle
    dismiss_anr()
    return sh('getprop sys.boot_completed').strip() == '1'


def wait_for(pattern, secs=15):
    end = time.time() + secs
    while time.time() < end:
        if find(pattern) is not None:
            return True
        time.sleep(1)
    return False


def alive():
    return bool(sh(f'pidof {PKG}').strip())


def crashed():
    out = adb('logcat', '-d', '-b', 'crash')
    return out if PKG in out else ''


def shot(name):
    safe = re.sub(r'[^A-Za-z0-9._-]+', '_', name)[:60]
    with open(os.path.join(OUT, f'{len(report):02d}-{safe}.png'), 'wb') as f:
        f.write(subprocess.run(['adb', 'exec-out', 'screencap', '-p'], capture_output=True, timeout=60).stdout)


def health(name):
    c = crashed()
    if c:
        log('FAIL', f'{name}: crash', c[-1500:].replace('\n', ' | '))
        open(os.path.join(OUT, 'crash.txt'), 'a').write(c)
        adb('logcat', '-c', '-b', 'crash')
        return False
    if not alive():
        log('FAIL', f'{name}: process not running')
        return False
    return True


def step(name, expect=None, secs=15):
    """Checks health, optionally waits for a text regex, records the visible texts and a screenshot."""
    ok = wait_for(expect, secs) if expect else True
    if not ok and expect and dismiss_anr():
        # One retry after clearing an emulator obstruction. A real app failure
        # fails both attempts; a transient emulator stall does not.
        log('INFO', f'{name}: system ANR dialog dismissed, retrying')
        ok = wait_for(expect, secs)
    healthy = health(name)
    vis = ' | '.join(dict.fromkeys(texts()))[:400]
    if healthy:
        log('PASS' if ok else 'FAIL', name, (f'missing /{expect}/; ' if not ok else '') + vis)
    shot(name)
    return ok and healthy


def back(times=1):
    for _ in range(times):
        sh('input keyevent KEYCODE_BACK')
        time.sleep(1.2)


def launch(timeout=40):
    """Starts the app and waits until it is genuinely interactive.

    A fixed sleep here was the cause of a real false failure: cold starts on a
    fresh AVD measured 11.7 s, so a 3 s wait let the next interaction (tapping the
    onboarding language chip) fire while the screen was still settling. Waiting for
    the app's activity to be the resumed one *and* for it to have drawn content is
    both faster in the common case and immune to slow boots.
    """
    dismiss_anr()
    sh(f'am start -W -n {PKG}/.MainActivity')
    end = time.time() + timeout
    while time.time() < end:
        if PKG in sh('dumpsys activity activities | grep -m1 ResumedActivity') and texts():
            time.sleep(1.5)   # let the first frame settle before interacting
            break
        time.sleep(1)
    # A launcher ANR can appear over the app right after a cold start; clear it so
    # the next check inspects the app rather than the dialog.
    dismiss_anr()


def deeplink(uri):
    sh(f"am start -W -a android.intent.action.VIEW -d '{uri}' {PKG}")
    time.sleep(3)


def home_hub():
    """Back to the Hub (either language): bottom-bar Home if visible, else Back; relaunch if we left the app."""
    for _ in range(8):
        ns = nodes()
        if find(r'ACADEMY HUB', ns) is None and find(r'Dashboard|لوحة التحكم', ns) is not None:
            for _ in range(3):
                swipe_down()                         # maybe a scrolled Hub grid: go to its top
            ns = nodes()
        if find(r'ACADEMY HUB', ns) is not None:     # brand header exists only on the Hub
            return True
        home = find(r'Home|الرئيسية', ns)
        if home is not None:
            x, y = center(home); sh(f'input tap {x} {y}'); time.sleep(1.5)
        else:
            back()
        if not alive() or PKG not in sh('dumpsys window | grep mCurrentFocus'):
            launch()
    return False


def open_screen(label, expect, tries=3):
    """Hub → tile `label`, waiting for `expect`; retries (a tap can land while the Hub grid is still settling)."""
    for _ in range(tries):
        home_hub()
        time.sleep(1)
        if tap(label, scrolls=3, wait=2.5) and wait_for(expect, 8):
            return True
    return False


def files_dir_state():
    """Every file under the app's filesDir, as "md5  path" lines (root shell).

    Byte-level evidence for §22. `install -r` replaces the APK and force-stops the
    app, but must not alter a single byte the user already stored. Comparing the
    whole directory before and after the update is independent of any UI
    automation, so data preservation can be judged even if a screen check is flaky.
    """
    base = f'/data/data/{PKG}/files'
    listing = sh(f'find {base} -type f 2>/dev/null')
    rows = []
    for path in sorted(p for p in listing.split() if p):
        digest = sh(f'md5sum {path} 2>/dev/null').split()
        if digest:
            rows.append(f'{digest[0]}  {path}')
    return '\n'.join(rows)


def open_course_pdf(tab_pattern=r'PDF · \d+', attempts=3):
    """Navigates Home → Courses → Smoke Physics → Smoke Unit → PDF tab → Open PDF.

    Returns True once the viewer's page indicator is on screen. Each attempt
    re-navigates from the Hub and waits for the action list to be present before
    tapping, so a half-completed navigation or a tap that lands while the screen
    is still settling cannot poison the following attempt.

    Every failed stage is logged with the screen it saw. This check is the §22
    release gate, so when it fails it must say where and why rather than just
    "the viewer did not open".
    """
    for i in range(attempts):
        n = i + 1
        home_hub()
        if not tap(r'Courses|المقررات', scrolls=2):
            log('INFO', f'pdf nav {n}: hub card Courses not found')
            continue
        if not tap(r'.*Smoke Physics.*', scrolls=1):
            log('INFO', f'pdf nav {n}: course not found')
            continue
        if not tap(r'.*Smoke Unit.*', scrolls=1):
            log('INFO', f'pdf nav {n}: unit not found')
            continue
        if not tap_tab(tab_pattern, anchor=r'(Text|نص) · \d+'):
            log('INFO', f'pdf nav {n}: PDF tab not selectable; tabs on screen: '
                        + ' | '.join(t for t in texts() if ' · ' in t)[:160])
            continue
        if not wait_for(r'Open PDF|فتح PDF', 10):
            log('INFO', f'pdf nav {n}: "Open PDF" not in the view hierarchy after tab select: '
                        + ' | '.join(texts())[:160])
            continue
        # Present in the hierarchy is not the same as reachable: the PDF buttons sit
        # in a FlowRow at the bottom of an expanded card, so they are often below the
        # fold. tap() scrolls until the button is genuinely on screen (a match
        # scrolled out of view is skipped instead of being tapped at coordinates
        # outside the screen), so give it room to scroll - this is the setting the
        # two passing PDF checks in this suite use.
        if not tap(r'Open PDF|فتح PDF', scrolls=6, wait=4):
            log('INFO', f'pdf nav {n}: "Open PDF" exists but no scroll position exposed it on screen: '
                        + ' | '.join(texts())[:160])
            continue
        if wait_for(PDF_PAGE_INDICATOR, 30):
            return True
        log('INFO', f'pdf nav {n}: tapped "Open PDF" but the page indicator never appeared: '
                    + ' | '.join(texts())[:160])
        back()
    return False


# ─────────────────────────────── run ───────────────────────────────
def main():
    booted = wait_for_boot()
    log('PASS' if booted else 'FAIL', 'emulator boot completed')
    adb('root'); time.sleep(3); adb('wait-for-device')
    sh('settings put global package_verifier_enable 0')
    out = adb('install', '-r', '-g', APK, timeout=300)
    log('PASS' if 'Success' in out else 'FAIL', 'install release APK', out.strip()[-200:])
    adb('logcat', '-c', '-b', 'all')
    info = sh(f'dumpsys package {PKG}')
    ver = re.search(r'versionName=(\S+)', info); tsdk = re.search(r'targetSdk=(\d+)', info); msdk = re.search(r'minSdk=(\d+)', info)
    log('INFO', 'package', f'versionName={ver and ver.group(1)} minSdk={msdk and msdk.group(1)} targetSdk={tsdk and tsdk.group(1)} '
        f'debuggable={"DEBUGGABLE" in info.split("pkgFlags=")[1][:200] if "pkgFlags=" in info else "?"}')

    # 1. Launch + splash + onboarding (Arabic default)
    t0 = time.time(); launch()
    log('INFO', 'cold start', f'{time.time() - t0:.1f}s')
    if not step('launch → onboarding (Arabic default)', r'ابدأ|Get started', 25):
        return
    # 2. Switch to English inside onboarding, then finish.
    # The chip can miss if the screen is still settling; retry the tap once. The
    # assertion below stays hard - it still fails if English never appears.
    if not (tap(r'English', scrolls=0, wait=3) and wait_for(r'Get started', 8)):
        tap(r'English', scrolls=0, wait=3)
    step('onboarding: English', r'Get started', 10)
    tap(r'Get started', scrolls=2, wait=3)
    if not step('onboarding done → Home', r'ACADEMY HUB', 15):
        return

    # 3. Backup import (web → Android) through the system picker
    subprocess.run(['python3', os.path.join(os.path.dirname(__file__), 'make_web_backup.py'), '/tmp/web-backup.json'], check=True)
    adb('push', '/tmp/web-backup.json', '/sdcard/Download/web-backup.json')
    sh('content call --uri content://media/external/file --method scan_file --arg /sdcard/Download/web-backup.json >/dev/null 2>&1')
    sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/web-backup.json >/dev/null 2>&1")
    time.sleep(2)
    ok = open_screen(r'Settings', r'Language|Import backup|Export backup')
    log('PASS' if ok else 'FAIL', 'Settings', '' if ok else ' | '.join(texts())[:200])
    imported = False
    if tap(r'Import backup', scrolls=8, wait=4):
        picked = tap(r'web-backup\.json', scrolls=0, wait=4)
        if not picked:  # picker opened on Recent: open the roots drawer → Downloads
            tap(r'Show roots', scrolls=0, wait=2) and tap(r'Downloads?', scrolls=0, wait=3)
            picked = tap(r'web-backup\.json', scrolls=2, wait=4)
        if picked and wait_for(r'Restore selected', 20):
            tap(r'Restore selected', scrolls=0, wait=5)
            imported = True
        log('PASS' if imported else 'FAIL', 'backup import via system picker', '' if imported else ' | '.join(texts())[:300])
    else:
        log('FAIL', 'backup import button not found')
    health('after import')

    # 4. Imported data visible in every section (web → Android semantics at runtime)
    home_hub()
    if tap(r'Courses', scrolls=1):
        step('Courses shows imported course', r'.*Smoke Physics.*', 10)
        if tap(r'.*Smoke Physics.*', scrolls=1):
            step('Course detail', r'.*Smoke Unit.*', 10)
            if tap(r'.*Smoke Unit.*', scrolls=1):
                step('Unit', r'.*Smoke.*', 10)
                if tap_tab(r'PDF · \d+'):
                    if tap(r'Open PDF', scrolls=2, wait=4):
                        step('PDF viewer (PdfRenderer)', PDF_PAGE_INDICATOR, 15)
                        back()
                    else:
                        log('FAIL', 'Open PDF button not found', ' | '.join(texts())[:200])
                else:
                    log('FAIL', 'PDF tab not found')
                if tap_tab(r'Image · \d+'):
                    step('Image tab', r'.*Smoke Image.*', 5)
                    if tap(r'Smoke Image 1', scrolls=1, wait=3):
                        step('Image viewer (zoom pager)', None)
                        back()
                else:
                    log('FAIL', 'Image tab not found')
                if tap_tab(r'Audio · \d+') and tap(r'Play', scrolls=1, wait=4):
                    step('Media3 player (audio)', None)
                    back()
                else:
                    log('FAIL', 'Audio tab / Play not found')
                if tap_tab(r'Text · \d+'):
                    step('Text content (Arabic + English)', r'(?s).*(?:سطر عربي|English line).*', 5)
    home_hub()
    for label, expect in ((r'Notes', r'.*Smoke Note.*'), (r'Calendar', r'.*'), (r'Dashboard', r'.*'),
                          (r'I am Muslim', r'.*'), (r'Blog', r'.*')):
        if tap(label, scrolls=2):
            step(f'{label} screen', expect, 12)
        else:
            log('FAIL', f'hub card {label} not found')
        home_hub()

    # 5. Study tabs
    if tap(r'Study Tools', scrolls=2):
        step('Study', r'Flashcards', 10)
        for tab, expect in ((r'Flashcards', r'.*Smoke Deck.*'), (r'Quizzes', r'.*Smoke Quiz.*'), (r'Focus', r'.*'),
                            (r'Google Forms', r'.*Smoke Form.*'), (r'Summaries', r'.*Smoke Summary.*'), (r'Study Schedule', r'.*')):
            ok = tap(tab, scrolls=0)
            if not ok:  # tabs scroll horizontally
                sh('input swipe 900 400 200 400 300'); time.sleep(1); ok = tap(tab, scrolls=0)
            if ok:
                step(f'Study → {tab}', expect, 8)
            else:
                log('FAIL', f'Study tab {tab} not found')
        tap(r'Flashcards', scrolls=0)
        if tap(r'.*Smoke Deck.*', scrolls=0, wait=3):
            step('Deck (flashcards)', r'.*F1.*|.*F2.*', 8)
            back()
        tap(r'Quizzes', scrolls=0)
        if tap(r'.*Smoke Quiz.*', scrolls=0, wait=3):
            step('Quiz screen', r'.*', 5)
            back()
    home_hub()

    # 6. Search (typed route) and deep links
    if tap(r'Search', scrolls=0):
        sh('input text Smoke'); time.sleep(2)
        step('Search "Smoke"', r'.*Smoke.*', 8)
        back(2)
    for uri in ('ubadacademy://course/smk-c1', 'ubadacademy://unit/smk-c1/smk-u1', 'ubadacademy://study?tab=focus',
                'ubadacademy://blog/unknown-post', 'ubadacademy://course/does-not-exist'):
        deeplink(uri)
        step(f'deep link {uri}', None)

    # 7. Focus alarm receiver + AlarmManager path (R8: Hilt entry point in a BroadcastReceiver)
    sh(f'am broadcast -a com.ubad.academy.action.FOCUS_PHASE_END -n {PKG}/.notifications.FocusAlarmReceiver')
    time.sleep(2)
    health('focus alarm receiver broadcast')
    log('PASS' if alive() else 'FAIL', 'focus alarm receiver handled broadcast')

    # 8. Themes (all 6) + language back to Arabic
    open_screen(r'Settings', r'Language|Import backup|Export backup')
    for th in (r'OLED Black', r'Aurora', r'Paper', r'Sage', r'Rose', r'Midnight'):
        if tap(th, scrolls=6, wait=1.5):
            step(f'theme {th}', None)
        else:
            log('FAIL', f'theme {th} not found')

    # 9. Backup export (Android → Web shape) through the system picker
    sh('rm -f /sdcard/Download/ubad-backup-*.json')
    exported = False
    if tap(r'Export backup', scrolls=8, wait=3) and tap(r'Create backup', scrolls=0, wait=4, last=True):
        if tap(r'(?i)save', scrolls=0, wait=5):
            time.sleep(3)
            path = sh('ls /sdcard/Download/ | grep ubad-backup').strip().splitlines()
            if path:
                adb('pull', f'/sdcard/Download/{path[0]}', os.path.join(OUT, 'android-export.json'))
                try:
                    d = json.load(open(os.path.join(OUT, 'android-export.json'), encoding='utf-8'))
                    data = d['data']
                    checks = {
                        'envelope': d.get('app') == 'ubad-academy-hub' and d.get('version') == 2,
                        'course': any(c['name'] == 'Smoke Physics' for c in data.get('courses', [])),
                        'pdf asset': any(a['id'] == 'smk-a1' and a['data'].startswith('data:application/pdf;base64,') for a in data.get('courseAssets', [])),
                        'note+image': any(n['title'] == 'Smoke Note' and n['images'] and n['images'][0]['data'].startswith('data:image/png') for n in data.get('notes', [])),
                        'events': any(e['title'] == 'Smoke Exam' for e in data.get('events', [])),
                        'decks': any(k['title'] == 'Smoke Deck' and len(k['cards']) == 2 for k in data.get('decks', [])),
                        'quizzes': any(q['title'] == 'Smoke Quiz' for q in data.get('quizzes', [])),
                        'schedule': any(s['title'] == 'Smoke Session' for s in data.get('schedule', [])),
                        'forms': any(f['title'] == 'Smoke Form' for f in data.get('forms', [])),
                        'summaries': any(f['title'] == 'Smoke Summary' for f in data.get('summaries', [])),
                        'islam': (data.get('islam') or {}).get('tasbih', {}).get('total') == 5,
                        'user': data.get('user', {}).get('name') == 'Smoke Tester',
                    }
                    exported = all(checks.values())
                    log('PASS' if exported else 'FAIL', 'backup export JSON v2 content', ', '.join(f'{k}={v}' for k, v in checks.items()))
                except Exception as e:
                    log('FAIL', 'backup export JSON parse', repr(e))
    if not exported and not any('backup export JSON' in r for r in report):
        log('FAIL', 'backup export via system picker', ' | '.join(texts())[:300])
    health('after export')

    # 9b. Round trip: erase everything, re-import the file Android just exported, check data is intact.
    #     Erase resets the language to Arabic (documented deviation), so labels match both languages.
    if exported:
        export_name = path[0]
        SETTINGS, COURSES, NOTES = r'Settings|الإعدادات', r'Courses|المقررات', r'Notes|الملاحظات'
        open_screen(SETTINGS, r'.*(Language|Import backup|Export backup|اللغة|استيراد نسخة احتياطية).*')
        wiped = tap(r'Erase all data', scrolls=10, wait=2) and tap(r'Erase all data', scrolls=0, wait=5, last=True)
        if wiped:
            log('PASS' if wait_for(r'.*(الإعدادات|اللغة|المظهر).*', 10) else 'FAIL', 'erase resets language to Arabic')
        home_hub(); tap(COURSES, scrolls=1)
        empty = wiped and find(r'.*Smoke Physics.*') is None
        log('PASS' if empty else 'FAIL', 'erase all data before re-import', ' | '.join(texts())[:200])
        open_screen(SETTINGS, r'.*(Language|Import backup|Export backup|اللغة|استيراد نسخة احتياطية).*')
        reimported, stage = False, 'import button'
        if tap(r'Import backup|استيراد نسخة احتياطية', scrolls=10, wait=4) or \
                tap(r'.*(Import|استيراد).*', scrolls=10, wait=4):
            stage = 'file in picker'
            picked = tap(re.escape(export_name), scrolls=0, wait=4)
            if not picked:
                tap(r'Show roots', scrolls=0, wait=2) and tap(r'Downloads?', scrolls=0, wait=3)
                picked = tap(r'.*' + re.escape(export_name.rsplit('.', 1)[0]) + r'.*', scrolls=2, wait=4)
            if picked:
                stage = 'restore dialog'
                if wait_for(r'.*(Restore selected|استرجاع المحدد).*', 20):
                    tap(r'.*(Restore selected|استرجاع المحدد).*', scrolls=0, wait=5, last=True)
                    reimported = True
        log('PASS' if reimported else 'FAIL', f're-import Android export ({export_name}) via system picker',
            '' if reimported else f'stuck at {stage}: ' + ' | '.join(texts())[:260])
        health('after re-import')
        if reimported:
            for label, expect in ((COURSES, r'.*Smoke Physics.*'), (NOTES, r'.*Smoke Note.*')):
                home_hub(); tap(label, scrolls=2)
                step(f'after re-import: {label.split("|")[0]} intact', expect, 10)
            home_hub(); tap(COURSES, scrolls=1)
            if tap(r'.*Smoke Physics.*', scrolls=1) and tap(r'.*Smoke Unit.*', scrolls=1):
                step('after re-import: unit contents + progress intact', r'.*(4 items|4 عناصر).*', 8)
                if tap_tab(r'PDF · \d+', anchor=r'(Text|نص) · \d+') and tap(r'Open PDF|فتح PDF', scrolls=2, wait=4):
                    step('after re-import: PDF asset opens and renders', PDF_PAGE_INDICATOR, 15)
                    back()
                else:
                    log('FAIL', 'after re-import: PDF not reachable', ' | '.join(texts())[:200])
            home_hub(); tap(r'Study Tools|أدوات الدراسة', scrolls=2)
            step('after re-import: Study deck intact', r'.*Smoke Deck.*', 8)
            tap(r'Quizzes|الاختبارات', scrolls=0)
            step('after re-import: quiz intact', r'.*Smoke Quiz.*', 8)
            home_hub(); tap(r'Dashboard|لوحة التحكم', scrolls=1)
            step('after re-import: user name intact', r'.*Smoke Tester.*', 8)

    # 10. Arabic + RTL
    home_hub(); tap(r'Settings|الإعدادات', scrolls=2)
    if find(r'.*الإعدادات.*') is not None or tap(r'العربية', scrolls=4, wait=4):
        ok = step('language → Arabic', r'الإعدادات|.*الإعدادات.*', 10)
        back_btn = find(r'رجوع|Back|Navigate up')
        if back_btn is not None:
            x, _ = center(back_btn)
            w = int(re.findall(r'(\d+)x(\d+)', sh('wm size'))[-1][0])
            log('PASS' if x > w / 2 else 'FAIL', 'RTL layout (back button on the right)', f'x={x} width={w}')
    # 11. Rotation, night mode, offline Blog, process death + restore
    sh('settings put system accelerometer_rotation 0'); sh('settings put system user_rotation 1'); time.sleep(3)
    step('rotation → landscape', None)
    home_hub(); step('landscape Home', None)
    sh('settings put system user_rotation 0'); time.sleep(2)
    sh('cmd uimode night yes'); time.sleep(2); step('system night mode on', None)
    sh('cmd uimode night no'); time.sleep(2); step('system night mode off', None)
    sh('svc wifi disable'); sh('svc data disable'); time.sleep(2)
    home_hub()
    if tap(r'المدونة', scrolls=2, wait=4):
        step('Blog offline (cached/offline state)', None)
    sh('svc wifi enable'); sh('svc data enable')
    sh('input keyevent KEYCODE_HOME'); time.sleep(1)
    sh(f'am kill {PKG}'); time.sleep(1)
    launch()
    # Android restores the saved back stack after a process kill, so any Arabic screen is correct here.
    step('relaunch after process kill (state restored, Arabic kept)', r'.*(الإعدادات|المقررات|المدونة|الرئيسية|رجوع).*', 15)
    home_hub(); tap(r'المقررات', scrolls=1)
    step('imported data persisted after restart', r'.*Smoke Physics.*', 10)

    # ── §22 data-migration release blocker: an app UPDATE must not lose data ──
    # `adb install -r` over an installed, already-populated app exercises the same
    # on-device path as a store update: the APK is replaced while /data survives,
    # so Room must open the existing database (a missing migration would throw
    # here), and DataStore plus the file store must come back untouched. Nothing
    # in this step clears data — if anything is missing afterwards, the update
    # path is destructive and the build must not ship.
    files_before = files_dir_state()
    log('INFO', 'filesDir before update', f'{len(files_before.splitlines())} file(s) hashed')
    upd = adb('install', '-r', '-g', APK, timeout=300)
    ok = 'Success' in upd
    log('PASS' if ok else 'FAIL', 'update (§22): reinstall over populated app', upd.strip()[-200:])
    if ok:
        # Independent of the UI: every user file must be byte-identical afterwards.
        files_after = files_dir_state()
        same = bool(files_before) and files_before == files_after
        log('PASS' if same else 'FAIL', 'update (§22): every file in filesDir unchanged (md5)',
            f'{len(files_after.splitlines())} file(s) after, {len(files_before.splitlines())} before'
            + ('' if same else ' — CONTENT CHANGED OR MISSING'))
        # No health() here: `install -r` force-stops the app, so "process not
        # running" is the expected state at this instant, not a failure. The
        # launch below is what proves the updated app actually starts.
        launch()
        step('update (§22): app launches after update', r'.*(الرئيسية|المقررات|رجوع|Home).*', 25)
        # The language is a persisted setting: were DataStore reset by the update,
        # the UI would be back to English instead of Arabic.
        step('update (§22): language setting intact (Arabic UI)', r'.*(المقررات|الرئيسية|لوحة التحكم).*', 10)
        for label, expect in ((COURSES, r'.*Smoke Physics.*'), (NOTES, r'.*Smoke Note.*')):
            home_hub(); tap(label, scrolls=2)
            step(f'update (§22): {label.split("|")[0]} intact', expect, 10)
        home_hub(); tap(COURSES, scrolls=1)
        if tap(r'.*Smoke Physics.*', scrolls=1) and tap(r'.*Smoke Unit.*', scrolls=1):
            step('update (§22): unit contents intact', r'.*(4 items|4 عناصر).*', 8)
            # The asset FILE lives in filesDir/course_assets/<id>, which an update
            # does not touch, so a slow first raster is not data loss. The
            # navigator retries from the Hub each time.
            rendered = open_course_pdf()
            if rendered:
                log('PASS', 'update (§22): PDF asset still renders', 'viewer opened on a cold start')
                back()
            else:
                # health() runs first so a genuine crash is reported as a crash
                # rather than as "the viewer did not open" — the two have very
                # different meanings for §22.
                health('update (§22): PDF asset still renders')
                log('FAIL', 'update (§22): PDF asset still renders',
                    'viewer did not open after 3 navigations (asset row and unit contents survived): '
                    + ' | '.join(texts())[:200])
        else:
            log('FAIL', 'update (§22): course not openable after update', ' | '.join(texts())[:200])
        # Note content lives in Room and its attachment bytes in
        # filesDir/note_files/<fileId>: the body text proves the record, and the
        # attachment image composes with contentDescription == its file name
        # ("n.png" in the fixture), which proves the attachment row survived too.
        home_hub(); tap(NOTES, scrolls=2)
        if tap(r'.*Smoke Note.*', scrolls=2, wait=3):
            step('update (§22): note body intact', r'.*ملاحظة.*', 8)
            step('update (§22): note attachment row intact', r'n\.png', 8)
            back()
        else:
            log('FAIL', 'update (§22): note not openable after update', ' | '.join(texts())[:200])
        home_hub(); tap(r'Study Tools|أدوات الدراسة', scrolls=2)
        step('update (§22): Study deck intact', r'.*Smoke Deck.*', 8)
        tap(r'Quizzes|الاختبارات', scrolls=0)
        step('update (§22): quiz intact', r'.*Smoke Quiz.*', 8)
        home_hub(); tap(r'Dashboard|لوحة التحكم', scrolls=1)
        step('update (§22): settings and user name intact', r'.*Smoke Tester.*', 8)


try:
    main()
except Exception as e:  # a broken harness must not look like a pass
    log('FAIL', 'harness exception', repr(e))
finally:
    health('final')
    full = adb('logcat', '-d', '-b', 'main', '-b', 'system', '-b', 'crash', timeout=120)
    open(os.path.join(OUT, 'logcat.txt'), 'w').write(full)
    open(os.path.join(OUT, 'report.txt'), 'w').write('\n'.join(report) + '\n')
    passed = sum(r.startswith('PASS') for r in report)
    print(f'SMOKE: {passed} passed, {len(failures)} failed')
    sys.exit(1 if failures else 0)
