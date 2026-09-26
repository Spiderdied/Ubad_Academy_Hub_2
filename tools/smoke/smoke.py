#!/usr/bin/env python3
"""Runtime smoke test of the R8-minified release APK on an emulator (run by CI).

  python3 tools/smoke/smoke.py <apk> <out-dir>

Drives the real UI through `adb` + `uiautomator dump`: onboarding, language switch, every hub
section, Study tabs, all 6 themes, deep links, rotation, night mode, offline Blog, the focus
alarm receiver, backup import (web-shaped v2 file) through the system picker, the imported PDF
/ image / audio in the native viewers, and backup export validated as JSON v2.

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
    line = f'{status:5} {name}' + (f' — {detail}' if detail else '')
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


def find(pattern, ns=None):
    rx = re.compile(pattern)
    for n in (nodes() if ns is None else ns):
        for t in (n.get('text'), n.get('content-desc')):
            if t and rx.fullmatch(t.strip()):
                return n
    return None


def swipe_up():
    size = re.findall(r'(\d+)x(\d+)', sh('wm size'))[-1]
    w, h = int(size[0]), int(size[1])
    sh(f'input swipe {w // 2} {int(h * .75)} {w // 2} {int(h * .3)} 300')
    time.sleep(1)


def tap(pattern, scrolls=4, wait=2.0):
    for i in range(scrolls + 1):
        n = find(pattern)
        if n is not None:
            x, y = center(n)
            sh(f'input tap {x} {y}')
            time.sleep(wait)
            return True
        if i < scrolls:
            swipe_up()
    return False


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
    with open(os.path.join(OUT, f'{len(report):02d}-{name}.png'), 'wb') as f:
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


def launch():
    sh(f'am start -W -n {PKG}/.MainActivity')
    time.sleep(3)


def deeplink(uri):
    sh(f"am start -W -a android.intent.action.VIEW -d '{uri}' {PKG}")
    time.sleep(3)


def home_hub():
    """Back to the Hub (either language): bottom-bar Home if visible, else Back; relaunch if we left the app."""
    for _ in range(8):
        ns = nodes()
        if find(r'Settings|الإعدادات', ns) is not None and find(r'Blog|المدونة', ns) is not None:
            return True
        home = find(r'Home|الرئيسية', ns)
        if home is not None:
            x, y = center(home); sh(f'input tap {x} {y}'); time.sleep(1.5)
        else:
            back()
        if not alive() or PKG not in sh('dumpsys window | grep mCurrentFocus'):
            launch()
    return False


# ─────────────────────────────── run ───────────────────────────────
def main():
    adb('wait-for-device')
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
    # 2. Switch to English inside onboarding, then finish
    tap(r'English', scrolls=0)
    step('onboarding: English', r'Get started', 10)
    tap(r'Get started', scrolls=2, wait=3)
    if not step('onboarding done → Home', r'Settings', 15):
        return

    # 3. Backup import (web → Android) through the system picker
    subprocess.run(['python3', os.path.join(os.path.dirname(__file__), 'make_web_backup.py'), '/tmp/web-backup.json'], check=True)
    adb('push', '/tmp/web-backup.json', '/sdcard/Download/web-backup.json')
    sh('content call --uri content://media/external/file --method scan_file --arg /sdcard/Download/web-backup.json >/dev/null 2>&1')
    sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/web-backup.json >/dev/null 2>&1")
    time.sleep(2)
    tap(r'Settings', scrolls=2)
    step('Settings', r'Settings|Language.*', 10)
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
                if tap(r'PDF · \d+', scrolls=0) and tap(r'Open PDF', scrolls=1, wait=4):
                    step('PDF viewer (PdfRenderer)', r'.*1.*', 10)
                    sh('input keyevent KEYCODE_VOLUME_DOWN'); back()
                if tap(r'Image · \d+', scrolls=0):
                    step('Image tab', r'.*Smoke Image.*', 5)
                if tap(r'Audio · \d+', scrolls=0) and tap(r'Play', scrolls=1, wait=4):
                    step('Media3 player (audio)', None)
                    back()
                if tap(r'Text · \d+', scrolls=0):
                    step('Text content (Arabic + English)', r'.*سطر عربي.*|.*English line.*', 5)
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
    home_hub()
    tap(r'Settings', scrolls=2)
    for th in (r'OLED Black', r'Aurora', r'Paper', r'Sage', r'Rose', r'Midnight'):
        if tap(th, scrolls=6, wait=1.5):
            step(f'theme {th}', None)
        else:
            log('FAIL', f'theme {th} not found')

    # 9. Backup export (Android → Web shape) through the system picker
    sh('rm -f /sdcard/Download/ubad-backup-*.json')
    exported = False
    if tap(r'Export backup', scrolls=8, wait=3) and tap(r'Create backup', scrolls=0, wait=4):
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

    # 10. Arabic + RTL
    home_hub(); tap(r'Settings', scrolls=2)
    if tap(r'العربية', scrolls=4, wait=4):
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
    step('relaunch after process kill (data persisted, Arabic kept)', r'.*الإعدادات.*|.*المقررات.*', 15)
    home_hub(); tap(r'المقررات', scrolls=1)
    step('imported data persisted after restart', r'.*Smoke Physics.*', 10)


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
