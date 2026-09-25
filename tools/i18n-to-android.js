// Converts the web app's I18N dictionary (app.js) into Android string resources.
// Usage: node tools/i18n-to-android.js  (run from repo root)
const fs = require('fs');
const src = fs.readFileSync('app.js', 'utf8');
const start = src.indexOf('const I18N={');
const end = src.indexOf('}};', start) + 3;
const I18N = eval('(' + src.slice(start + 'const I18N='.length, end - 1) + ')');
const keyName = k => k.replace(/[^A-Za-z0-9]/g, '_');
function esc(s) {
  let out = String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"')
    .replace(/\n/g, '\\n');
  if (/^[@?]/.test(out)) out = '\\' + out;
  return out;
}
function convert(dict, placeholderOrderFrom) {
  const lines = [];
  for (const [k, v] of Object.entries(dict)) {
    let s = String(v);
    const vars = [];
    s.replace(/\{(\w+)\}/g, (_, n) => { if (!vars.includes(n)) vars.push(n); });
    // stable arg order: use the English ordering so both locales match
    const order = (placeholderOrderFrom && placeholderOrderFrom[k]) || vars;
    let body = esc(s.replace(/%/g, '%%'));
    order.forEach((n, i) => { body = body.split('{' + n + '}').join('%' + (i + 1) + '$s'); });
    const formatted = order.length ? '' : (s.includes('%') ? ' formatted="false"' : '');
    if (!order.length) body = esc(s);
    lines.push(`    <string name="${keyName(k)}"${formatted}>${body}</string>`);
  }
  return lines;
}
const enVars = {};
for (const [k, v] of Object.entries(I18N.en)) {
  const vars = []; String(v).replace(/\{(\w+)\}/g, (_, n) => { if (!vars.includes(n)) vars.push(n); });
  if (vars.length) enVars[k] = vars;
}
// Arabic is the primary language → default values/ ; English → values-en/
const ar = { ...I18N.en, ...I18N.ar };
const header = '<?xml version="1.0" encoding="utf-8"?>\n<!-- GENERATED from app.js I18N by tools/i18n-to-android.js. Do not edit by hand; edit strings_native.xml for Android-only strings. -->\n<resources>\n';
const res = 'android/app/src/main/res';
fs.mkdirSync(res + '/values', { recursive: true });
fs.mkdirSync(res + '/values-en', { recursive: true });
fs.writeFileSync(res + '/values/strings_web.xml', header + convert(ar, enVars).join('\n') + '\n</resources>\n');
fs.writeFileSync(res + '/values-en/strings_web.xml', header + convert(I18N.en, enVars).join('\n') + '\n</resources>\n');
console.log('en keys', Object.keys(I18N.en).length, 'ar keys', Object.keys(I18N.ar).length,
  'missing in ar', Object.keys(I18N.en).filter(k => !(k in I18N.ar)));
