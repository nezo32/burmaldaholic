// Shared helpers for build/check scripts.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const readJson = (p) => JSON.parse(fs.readFileSync(path.resolve(ROOT, p), 'utf8'));
export const MODULES = readJson('modules.json').modules;
export const PACK = readJson('pack.json');
export const LANGS = PACK.languages.map(([code]) => code);

export function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

export const rel = (p) => path.relative(ROOT, p).split(path.sep).join('/');

export function fail(errors, title) {
  if (errors.length === 0) return;
  console.error(`\n${title}: ${errors.length} problem(s)`);
  for (const e of errors) console.error('  - ' + e);
  process.exit(1);
}
