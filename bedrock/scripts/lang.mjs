// Merge lang fragments lang/<module>/<LANG>.lang and validate them.
// Fails on: key-set mismatch between languages, placeholder mismatch, non-positional
// placeholders, duplicate keys, keys outside the module's namespace, incomplete plural sets.
// Usage: node scripts/lang.mjs            (check only)
//        import { buildLang } from './lang.mjs'  (used by build.mjs)
import fs from 'node:fs';
import path from 'node:path';
import { LANGS, MODULES, ROOT, fail, rel } from './lib.mjs';

const PLURALS = ['p1', 'p21', 'p2', 'p5'];
const KNOWN = new Set(MODULES.map((m) => m.id));
const GLOBAL_KEYS = new Set(['pack.name', 'pack.description']);

export function parseLang(file) {
  const entries = new Map();
  const errors = [];
  const lines = fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/);
  lines.forEach((raw, i) => {
    const line = raw.replace(/\t##.*$/, '').trimEnd();
    if (!line.trim() || line.trimStart().startsWith('#')) return;
    const eq = line.indexOf('=');
    if (eq <= 0) return errors.push(`${rel(file)}:${i + 1}: expected key=value`);
    const key = line.slice(0, eq).trim();
    const value = line.slice(eq + 1);
    if (entries.has(key)) errors.push(`${rel(file)}:${i + 1}: duplicate key ${key}`);
    entries.set(key, { value, line: i + 1 });
  });
  return { entries, errors };
}

/** Returns sorted positional indices, or an error string. */
export function placeholders(value) {
  const idx = [];
  const re = /%(%|(\d+)\$[sd]|[sd])/g;
  let m;
  while ((m = re.exec(value))) {
    if (m[1] === '%') continue;
    if (!m[2]) return { error: `non-positional placeholder '${m[0]}' (use %1$s)` };
    idx.push(Number(m[2]));
  }
  return { idx: [...new Set(idx)].sort((a, b) => a - b) };
}

export function buildLang() {
  const errors = [];
  const merged = Object.fromEntries(LANGS.map((l) => [l, new Map()]));
  const owner = new Map();

  for (const mod of MODULES) {
    const dir = path.join(ROOT, 'lang', mod.id);
    const parsed = {};
    for (const lang of LANGS) {
      const file = path.join(dir, `${lang}.lang`);
      if (!fs.existsSync(file)) {
        errors.push(`missing ${rel(file)}`);
        continue;
      }
      const p = parseLang(file);
      errors.push(...p.errors);
      parsed[lang] = p.entries;
    }
    const [base, ...others] = LANGS;
    if (!parsed[base]) continue;

    for (const [key, { line }] of parsed[base]) {
      const where = `lang/${mod.id}/${base}.lang:${line}`;
      // Scheme (docs/design): <category>.burmaldaholic.<module>.<rest> e.g. msg.burmaldaholic.slots.win,
      // or engine keys containing the identifier, e.g. item.burmaldaholic:chip_100, entity.burmaldaholic:dealer.name.
      // A key naming ANOTHER module (x.burmaldaholic.<other>.*) is rejected.
      const owned = /^[a-z_]+\.burmaldaholic\.([a-z_]+)\./.exec(key);
      const ok = GLOBAL_KEYS.has(key)
        ? mod.id === 'core'
        : key.includes('burmaldaholic:') || (owned !== null && (owned[1] === mod.id || !KNOWN.has(owned[1])));
      if (!ok) errors.push(`${where}: key '${key}' must look like 'msg.burmaldaholic.${mod.id}.*' (or an engine key containing 'burmaldaholic:')`);
      if (owner.has(key)) errors.push(`${where}: key '${key}' already defined by module '${owner.get(key)}'`);
      owner.set(key, mod.id);
      // Plural completeness.
      const m = /^(.*)\.(p1|p21|p2|p5)$/.exec(key);
      if (m) for (const s of PLURALS) if (!parsed[base].has(`${m[1]}.${s}`)) errors.push(`${where}: plural '${m[1]}' is missing .${s}`);
    }

    for (const lang of others) {
      if (!parsed[lang]) continue;
      for (const key of parsed[base].keys()) if (!parsed[lang].has(key)) errors.push(`lang/${mod.id}/${lang}.lang: missing key '${key}'`);
      for (const key of parsed[lang].keys()) if (!parsed[base].has(key)) errors.push(`lang/${mod.id}/${lang}.lang: extra key '${key}' (not in ${base})`);
    }

    for (const lang of LANGS) {
      if (!parsed[lang]) continue;
      for (const [key, { value, line }] of parsed[lang]) {
        const ph = placeholders(value);
        if (ph.error) {
          errors.push(`lang/${mod.id}/${lang}.lang:${line}: ${ph.error}`);
          continue;
        }
        if (lang !== base && parsed[base].has(key)) {
          const b = placeholders(parsed[base].get(key).value);
          if (!b.error && b.idx.join() !== ph.idx.join()) {
            errors.push(`lang/${mod.id}/${lang}.lang:${line}: '${key}' placeholders [${ph.idx}] != ${base} [${b.idx}]`);
          }
        }
        if (!value.trim()) errors.push(`lang/${mod.id}/${lang}.lang:${line}: empty value for '${key}'`);
        merged[lang].set(key, value);
      }
    }
  }

  // Unknown lang dirs (typo / module not registered).
  const known = new Set(MODULES.map((m) => m.id));
  for (const d of fs.readdirSync(path.join(ROOT, 'lang'))) if (!known.has(d)) errors.push(`lang/${d}: not a module in modules.json`);

  return { errors, merged };
}

export function renderLang(map) {
  return [...map.entries()].map(([k, v]) => `${k}=${v}`).join('\n') + '\n';
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const { errors, merged } = buildLang();
  fail(errors, 'Lang check failed');
  console.log(`lang OK: ${LANGS.map((l) => `${l}=${merged[l].size}`).join(', ')} keys`);
}
