#!/usr/bin/env python3
"""Security audit for the native Android app (run by CI, also runnable locally).

  python3 tools/android-security-scan.py source   # repo sources (before the build)
  python3 tools/android-security-scan.py apk      # built APKs + merged manifest

Fails (exit 1) on: committed secrets/keystores, WebView/JS-bridge usage in the app,
runtime JS/CDN dependencies, dangerous or unexpected permissions. Emits GitHub
annotations so results show up on the workflow run.
"""
import glob
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
APP = os.path.join(ROOT, 'android', 'app')
SKIP_DIRS = {'.git', 'build', '.gradle', 'node_modules', '.idea', '.cxx'}

SECRET_PATTERNS = {
    'Google API key': r'AIza[0-9A-Za-z_\-]{35}',
    'GitHub token': r'\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})',
    'Private key': r'-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----',
    'AWS access key': r'\bAKIA[0-9A-Z]{16}\b',
    'Service account': r'"type"\s*:\s*"service_account"',
    'Slack token': r'\bxox[baprs]-[A-Za-z0-9-]{10,}',
    'Stripe live key': r'\b[sr]k_live_[A-Za-z0-9]{16,}',
    'Hardcoded credential': r'(?i)\b(?:api[_-]?key|client[_-]?secret|password|passwd|auth[_-]?token|access[_-]?token|bearer)\b\s*[:=]\s*["\'][^"\'$\s{]{8,}["\']',
}
SECRET_FILES = re.compile(r'(?i)(\.jks|\.keystore|\.p12|\.pem|keystore\.properties|google-services\.json|service[-_]account.*\.json|local\.properties)$')
TEXT_EXT = ('.kt', '.kts', '.java', '.xml', '.json', '.properties', '.gradle', '.toml', '.yml', '.yaml', '.js', '.html', '.md', '.pro', '.txt', '.css')

# App code must never embed a browser engine or a JS bridge.
WEBVIEW = re.compile(r'import\s+android\.webkit|\bWebView\s*\(|<WebView\b|addJavascriptInterface|evaluateJavascript|WebChromeClient|WebViewClient')
JS_DEPS = re.compile(r'(?i)unpkg\.com|jsdelivr|html2app|pdf\.js|pdfjs|cdnjs|localStorage|indexedDB|serviceWorker')

ALLOWED_PERMISSIONS = {
    'android.permission.INTERNET',              # Blog worker + remote blog images
    'android.permission.POST_NOTIFICATIONS',    # focus timer end (asked lazily)
    'android.permission.SCHEDULE_EXACT_ALARM',  # timer ends on time (optional)
}
DANGEROUS = re.compile(r'(?i)EXTERNAL_STORAGE|READ_MEDIA_|LOCATION|CAMERA|RECORD_AUDIO|CONTACTS|READ_PHONE|CALL_PHONE|SMS|QUERY_ALL_PACKAGES|SYSTEM_ALERT_WINDOW|REQUEST_INSTALL_PACKAGES|BODY_SENSORS|GET_ACCOUNTS|USE_EXACT_ALARM')
# Library-merged permissions that are normal-level and scoped to the app.
LIBRARY_OK = re.compile(r'DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$|^android\.permission\.(ACCESS_NETWORK_STATE|WAKE_LOCK)$')

problems, notes = [], []


def _uleb(b, o):
    r = sh = 0
    while True:
        x = b[o]; o += 1; r |= (x & 0x7f) << sh; sh += 7
        if x < 0x80:
            return r, o


def dex_webview_refs(d):
    """Methods invoked on android.webkit.WebView + classes named *WebView* (to attribute library references)."""
    import struct
    so, tc, to = struct.unpack_from('<I', d, 0x3C)[0], *struct.unpack_from('<II', d, 0x40)
    mc, mo = struct.unpack_from('<II', d, 0x58)
    cc, co = struct.unpack_from('<II', d, 0x60)

    def string(i):
        off = struct.unpack_from('<I', d, so + 4 * i)[0]
        _, o = _uleb(d, off)
        return d[o:d.index(b'\0', o)].decode('utf-8', 'replace')

    types = [struct.unpack_from('<I', d, to + 4 * i)[0] for i in range(tc)]
    wv = [i for i, t in enumerate(types) if string(t) == 'Landroid/webkit/WebView;']
    methods = set()
    if wv:
        for i in range(mc):
            c, _, nm = struct.unpack_from('<HHI', d, mo + 8 * i)
            if c == wv[0]:
                methods.add(string(nm))
    named = set()
    for i in range(cc):
        name = string(types[struct.unpack_from('<I', d, co + 32 * i)[0]])
        if 'webview' in name.lower() or 'webkit' in name.lower():
            named.add(name.strip('L;').replace('/', '.'))
    return methods, named


def rel(p):
    return os.path.relpath(p, ROOT)


def annotate(kind, title, lines):
    for i in range(0, min(len(lines), 200), 25):
        msg = '%0A'.join(l.replace('%', '%25') for l in lines[i:i + 25])
        print(f'::{kind} title={title} {i // 25}::{msg}')


def walk(base):
    for d, dirs, files in os.walk(base):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        for f in files:
            yield os.path.join(d, f)


def tracked_files():
    try:
        out = subprocess.run(['git', 'ls-files'], cwd=ROOT, capture_output=True, text=True, check=True).stdout
        return [os.path.join(ROOT, l) for l in out.splitlines() if l]
    except Exception:
        return list(walk(ROOT))


def scan_text(path, text, secrets_only=False):
    for name, pat in SECRET_PATTERNS.items():
        for m in re.finditer(pat, text):
            line = text.count('\n', 0, m.start()) + 1
            problems.append(f'{name}: {rel(path)}:{line}')
    if secrets_only:
        return
    for i, l in enumerate(text.splitlines(), 1):
        s = l.strip()
        if s.startswith(('*', '//', '/*', '<!--')):
            continue  # documentation that says "no WebView" is fine
        if WEBVIEW.search(l):
            problems.append(f'WebView usage: {rel(path)}:{i}: {s[:120]}')
        if JS_DEPS.search(l):
            problems.append(f'Web runtime dependency: {rel(path)}:{i}: {s[:120]}')


def source():
    files = tracked_files()
    for p in files:
        if SECRET_FILES.search(p):
            problems.append(f'Secret-bearing file is tracked: {rel(p)}')
    # 1) whole repository: secrets
    for p in files:
        if not p.endswith(TEXT_EXT) or not os.path.isfile(p) or os.path.getsize(p) > 5_000_000:
            continue
        if '/android/app/src/test/' in p:
            continue  # test fixtures
        scan_text(p, open(p, encoding='utf-8', errors='replace').read(), secrets_only=True)
    # 2) app sources: WebView / JS runtime deps
    for p in walk(os.path.join(APP, 'src', 'main')):
        if p.endswith(('.kt', '.java', '.xml')):
            scan_text(p, open(p, encoding='utf-8', errors='replace').read())
    # 3) declared permissions
    manifest = open(os.path.join(APP, 'src', 'main', 'AndroidManifest.xml'), encoding='utf-8').read()
    declared = set(re.findall(r'uses-permission[^>]*android:name="([^"]+)"', manifest))
    for perm in sorted(declared - ALLOWED_PERMISSIONS):
        problems.append(f'Unexpected permission declared: {perm}')
    if re.search(r'android:usesCleartextTraffic="true"', manifest):
        problems.append('Cleartext traffic enabled in manifest')
    # 4) review list of every URL literal shipped in the app
    urls = set()
    for p in walk(os.path.join(APP, 'src', 'main')):
        if p.endswith(('.kt', '.xml')):
            urls.update(re.findall(r'https?://[^\s"\'<>)]+', open(p, encoding='utf-8', errors='replace').read()))
    urls = sorted(u for u in urls if 'schemas.android.com' not in u)
    notes.append('Permissions: ' + ', '.join(sorted(declared)))
    notes.append('URL literals in app: ' + (' | '.join(urls) or 'none'))


def apk():
    manifests = glob.glob(os.path.join(APP, 'build', 'intermediates', 'merged_manifest*', '**', 'AndroidManifest.xml'), recursive=True)
    merged = set()
    for m in manifests:
        merged.update(re.findall(r'uses-permission(?:-sdk-23)?[^>]*android:name="([^"]+)"', open(m, encoding='utf-8').read()))
    for perm in sorted(merged):
        if DANGEROUS.search(perm):
            problems.append(f'Dangerous permission in merged manifest: {perm}')
        elif perm not in ALLOWED_PERMISSIONS and not LIBRARY_OK.search(perm):
            problems.append(f'Unexpected merged permission: {perm}')
    notes.append('Merged permissions: ' + (', '.join(sorted(merged)) or 'manifest not found'))
    for a in glob.glob(os.path.join(APP, 'build', 'outputs', '**', '*.apk'), recursive=True):
        webview_refs, wv_methods, wv_classes = 0, set(), set()
        with zipfile.ZipFile(a) as z:
            for n in z.namelist():
                if n in ('META-INF/CERT.RSA',) or n.endswith(('.png', '.webp', '.so')):
                    continue
                data = z.read(n)
                if n.endswith('.dex'):
                    webview_refs += data.count(b'Landroid/webkit/WebView;')
                    try:
                        m, c = dex_webview_refs(data); wv_methods |= m; wv_classes |= c
                    except Exception as e:  # never fail the scan on a parser edge case
                        notes.append(f'dex parse skipped for {n}: {e}')
                text = data.decode('latin-1')
                for name, pat in SECRET_PATTERNS.items():
                    if name == 'Hardcoded credential':
                        continue  # too noisy on binary dex constant pools
                    if re.search(pat, text):
                        problems.append(f'{name} inside {os.path.basename(a)}!{n}')
        # Only framework-level references (e.g. androidx compat shims) may remain; the app never creates one.
        notes.append(f'{os.path.basename(a)}: {os.path.getsize(a) // 1024} KB, dex references to android.webkit.WebView type: {webview_refs}')
        notes.append('WebView methods referenced: ' + (', '.join(sorted(wv_methods)) or 'none'))
        notes.append('Classes named *WebView*/*webkit*: ' + (', '.join(sorted(wv_classes)) or 'none'))
        own = [c for c in wv_classes if c.startswith('com.ubad.')]
        if own or wv_methods & {'loadUrl', 'loadData', 'loadDataWithBaseURL', 'addJavascriptInterface', 'evaluateJavascript'}:
            problems.append(f'App code loads content in a WebView: methods={sorted(wv_methods)} classes={own}')


if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'source'
    source() if mode == 'source' else apk()
    annotate('notice', f'Security {mode}', notes)
    if problems:
        annotate('error', f'Security {mode}', problems)
        print('\n'.join(problems), file=sys.stderr)
        sys.exit(1)
    print(f'security scan ({mode}): OK')
