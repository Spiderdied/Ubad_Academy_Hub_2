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

# Library code that references WebView but that this app never activates:
#  androidx.media3.ui.WebViewSubtitleOutput — PlayerView's optional WebVTT renderer. SubtitleView only
#  creates it after setViewType(VIEW_TYPE_WEB), which the app never calls (default: CanvasSubtitleOutput).
#  androidx.core.text.util.LinkifyCompat — calls the static text utility WebView.findAddress(); no WebView is created.
KNOWN_LIBRARY_WEBVIEW = ('androidx.media3.ui.WebViewSubtitleOutput', 'androidx.media3.ui.SubtitleView',
                         'androidx.core.text.util.LinkifyCompat')
# The html2app bridge / CDN JS must not appear anywhere in a built APK.
JS_BRIDGE_BIN = re.compile(r'(?i)html2app|esm\.unpkg\.com|cdn\.jsdelivr|pdfjs-dist|pdf\.worker')

problems, notes = [], []


def _uleb(b, o):
    r = sh = 0
    while True:
        x = b[o]; o += 1; r |= (x & 0x7f) << sh; sh += 7
        if x < 0x80:
            return r, o


# Dalvik instruction sizes in 16-bit code units, by opcode (from the bytecode format table).
_SIZES = [1] * 256
for _op, _n in {0x02: 2, 0x03: 3, 0x05: 2, 0x06: 3, 0x08: 2, 0x09: 3, 0x13: 2, 0x14: 3, 0x15: 2, 0x16: 2,
                0x17: 3, 0x18: 5, 0x19: 2, 0x1a: 2, 0x1b: 3, 0x1c: 2, 0x1f: 2, 0x20: 2, 0x22: 2, 0x23: 2,
                0x24: 3, 0x25: 3, 0x26: 3, 0x29: 2, 0x2a: 3, 0x2b: 3, 0x2c: 3, 0xfa: 4, 0xfb: 4, 0xfc: 3,
                0xfd: 3, 0xfe: 2, 0xff: 2}.items():
    _SIZES[_op] = _n
for _r, _n in ((range(0x2d, 0x32), 2), (range(0x32, 0x38), 2), (range(0x38, 0x3e), 2), (range(0x44, 0x52), 2),
               (range(0x52, 0x60), 2), (range(0x60, 0x6e), 2), (range(0x6e, 0x73), 3), (range(0x74, 0x79), 3),
               (range(0x90, 0xb0), 2), (range(0xd0, 0xe3), 2)):
    for _op in _r:
        _SIZES[_op] = _n
_INVOKE = set(range(0x6e, 0x73)) | set(range(0x74, 0x79))
_TYPE_REF = {0x1c, 0x1f, 0x20, 0x22, 0x23}  # const-class, check-cast, instance-of, new-instance, new-array


def dex_webview_callers(d):
    """Names of classes whose bytecode touches android.webkit.WebView (invokes its methods or uses the type)."""
    import struct
    u32 = lambda o: struct.unpack_from('<I', d, o)[0]
    so, tc, to = u32(0x3C), u32(0x40), u32(0x44)
    mc, mo, cc, co = u32(0x58), u32(0x5C), u32(0x60), u32(0x64)

    def string(i):
        _, o = _uleb(d, u32(so + 4 * i))
        return d[o:d.index(b'\0', o)].decode('utf-8', 'replace')

    types = [u32(to + 4 * i) for i in range(tc)]
    wv = next((i for i, t in enumerate(types) if string(t) == 'Landroid/webkit/WebView;'), None)
    if wv is None:
        return set(), set()
    wv_methods = {i: string(u32(mo + 8 * i + 4)) for i in range(mc) if struct.unpack_from('<H', d, mo + 8 * i)[0] == wv}
    callers, used = set(), set()
    for ci in range(cc):
        base = co + 32 * ci
        cname = string(types[u32(base)]).strip('L;').replace('/', '.')
        data = u32(base + 24)
        if not data:
            continue
        o = data
        sf, o = _uleb(d, o); inf, o = _uleb(d, o); dm, o = _uleb(d, o); vm, o = _uleb(d, o)
        for _ in range(sf + inf):
            _, o = _uleb(d, o); _, o = _uleb(d, o)
        for _ in range(dm + vm):
            _, o = _uleb(d, o); _, o = _uleb(d, o); code, o = _uleb(d, o)
            if not code:
                continue
            n = u32(code + 12); ins = code + 16; pc = 0
            while pc < n:
                unit = struct.unpack_from('<H', d, ins + 2 * pc)[0]
                op = unit & 0xff
                if unit == 0x0100:    # packed-switch payload
                    pc += 4 + struct.unpack_from('<H', d, ins + 2 * pc + 2)[0] * 2; continue
                if unit == 0x0200:    # sparse-switch payload
                    pc += 2 + struct.unpack_from('<H', d, ins + 2 * pc + 2)[0] * 4; continue
                if unit == 0x0300:    # fill-array-data payload
                    w = struct.unpack_from('<H', d, ins + 2 * pc + 2)[0]; cnt = u32(ins + 2 * pc + 4)
                    pc += 4 + (cnt * w + 1) // 2; continue
                if op in _INVOKE or op in _TYPE_REF:
                    ref = struct.unpack_from('<H', d, ins + 2 * pc + 2)[0]
                    if (op in _INVOKE and ref in wv_methods) or (op in _TYPE_REF and ref == wv):
                        callers.add(cname)
                        if op in _INVOKE:
                            used.add(wv_methods[ref])
                pc += _SIZES[op]
    return callers, used


def r8_mapping(apk_path):
    """obfuscated → original class names from R8's mapping.txt (release builds)."""
    m = {}
    if 'release' not in os.path.basename(apk_path):
        return m
    for f in glob.glob(os.path.join(APP, 'build', 'outputs', 'mapping', 'release', 'mapping.txt')):
        for line in open(f, encoding='utf-8', errors='replace'):
            if not line.startswith((' ', '#')) and line.rstrip().endswith(':') and ' -> ' in line:
                orig, obf = line.rstrip()[:-1].split(' -> ')
                m[obf] = orig
    return m


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
    # Who added each permission (AGP manifest-merger blame report).
    for rep in sorted(glob.glob(os.path.join(APP, 'build', 'outputs', 'logs', 'manifest-merger-*-report.txt'))):
        lines = open(rep, encoding='utf-8', errors='replace').read().splitlines()
        blame = []
        for i, l in enumerate(lines):
            if l.startswith('uses-permission#'):
                src = next((x for x in lines[i + 1:i + 4] if x.startswith(('ADDED from', 'MERGED from'))), '')
                lib = re.search(r'\[([^\]]+)\]', src)
                blame.append(f"{l.split('#', 1)[1]} <- {lib.group(1) if lib else 'app manifest'}")
        notes.append(f'{os.path.basename(rep)}: ' + ('; '.join(blame) or 'no permissions'))
    if not manifests:
        problems.append('Merged manifest not found — permission scan could not run')
    for a in glob.glob(os.path.join(APP, 'build', 'outputs', '**', '*.apk'), recursive=True):
        callers, used = set(), set()
        with zipfile.ZipFile(a) as z:
            for n in z.namelist():
                if n.endswith(('.png', '.webp', '.so', '.jpg', '.mp3', '.wav', '.ttf')):
                    continue
                data = z.read(n)
                if n.endswith('.dex'):
                    c, u = dex_webview_callers(data)  # a parser error fails the scan loudly (no silent pass)
                    callers |= c; used |= u
                text = data.decode('latin-1')
                for name, pat in SECRET_PATTERNS.items():
                    if name == 'Hardcoded credential':
                        continue  # too noisy on binary dex constant pools
                    if re.search(pat, text):
                        problems.append(f'{name} inside {os.path.basename(a)}!{n}')
                if JS_BRIDGE_BIN.search(text):
                    problems.append(f'Web runtime/bridge reference inside {os.path.basename(a)}!{n}')
        mapping = r8_mapping(a)
        named = sorted(mapping.get(c, c) for c in callers)
        known = [c for c in named if c.startswith(KNOWN_LIBRARY_WEBVIEW)]
        unexpected = [c for c in named if c not in known]
        notes.append(f'{os.path.basename(a)}: {os.path.getsize(a) // 1024} KB; R8 mapping entries: {len(mapping)}')
        notes.append(f'  WebView callers: {", ".join(named) or "none"}; methods used: {", ".join(sorted(used)) or "none"}')
        if unexpected:
            problems.append(f'{os.path.basename(a)}: WebView used by unexpected/own code: {", ".join(unexpected)}')
        elif known:
            notes.append('  all WebView references attributed to inactive library code (' + ', '.join(known) + ')')


if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'source'
    source() if mode == 'source' else apk()
    annotate('notice', f'Security {mode}', notes)
    if problems:
        annotate('error', f'Security {mode}', problems)
        print('\n'.join(problems), file=sys.stderr)
        sys.exit(1)
    print(f'security scan ({mode}): OK')
