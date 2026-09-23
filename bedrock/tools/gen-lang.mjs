#!/usr/bin/env node
// Generate every module's lang fragment (lang/<module>/en_US.lang + ru_RU.lang) from
// docs/design/STRINGS.md, the master string list.
//
//   node tools/gen-lang.mjs            write all fragments
//   node tools/gen-lang.mjs --check    exit 1 if any fragment differs from the spec (no writes)
//   node tools/gen-lang.mjs slots vip  only these modules
//
// Re-runnable: everything below the "manual" marker line of an existing fragment is kept, so a
// feature dev may hand-add keys there (they still need an EN+RU pair; scripts/lang.mjs checks).
// Changing a spec string = edit STRINGS.md and re-run (preferred), or edit the generated line by
// hand and accept that the next run resets it (use --check to see drift).
/* global process, console */
/* eslint-disable no-console */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseStrings, renderFragment, routeEntries, splitManual } from './lib/strings.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SPEC = path.resolve(ROOT, '../docs/design/STRINGS.md');
const MODULES = JSON.parse(fs.readFileSync(path.join(ROOT, 'modules.json'), 'utf8')).modules.map((m) => m.id);
const LANGS = [
  ['en', 'en_US'],
  ['ru', 'ru_RU'],
];

const args = process.argv.slice(2);
const check = args.includes('--check');
const only = args.filter((a) => !a.startsWith('--'));
for (const m of only) if (!MODULES.includes(m)) fail([`unknown module '${m}'`]);

const { entries, errors } = parseStrings(fs.readFileSync(SPEC, 'utf8'));
if (errors.length) fail(errors);
const routed = routeEntries(entries, MODULES);

let changed = 0;
for (const mod of only.length ? only : MODULES) {
  for (const [lang, code] of LANGS) {
    const file = path.join(ROOT, 'lang', mod, `${code}.lang`);
    const old = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
    const next = renderFragment(mod, lang, routed.get(mod), splitManual(old).manual);
    if (next === old) continue;
    changed++;
    if (check) console.error(`out of date: lang/${mod}/${code}.lang`);
    else {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, next);
    }
  }
}
const counts = MODULES.map((m) => `${m}=${routed.get(m).length}`).join(' ');
if (check && changed) fail([`${changed} fragment(s) differ from STRINGS.md; run: node tools/gen-lang.mjs`]);
console.log(`${check ? 'checked' : 'generated'} ${entries.length} spec keys (${counts})${check ? '' : `, ${changed} file(s) written`}`);

function fail(errs) {
  for (const e of errs) console.error('  - ' + e);
  process.exit(1);
}
