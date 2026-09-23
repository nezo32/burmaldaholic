// Fails on hardcoded player-facing strings and on unknown static lang keys.
// Player-facing text must be rawtext built with t()/plural()/join() from core.
// Escape hatch (use sparingly, reviewers will ask): put `// i18n-ignore` on the line.
import fs from 'node:fs';
import path from 'node:path';
import { buildLang } from './lang.mjs';
import { LANGS, ROOT, fail, rel, walk } from './lib.mjs';

const UI_CALLS = [
  'sendMessage', 'setActionBar', 'setTitle', 'updateSubtitle', 'title', 'body', 'button', 'button1', 'button2',
  'label', 'header', 'textField', 'toggle', 'slider', 'dropdown', 'submitButton', 'setLore', 'closeButton',
];
const Q = `['"\`]`;
const RULES = [
  { re: new RegExp(`\\.(${UI_CALLS.join('|')})\\(\\s*${Q}`), msg: 'string literal passed to UI/chat call; use t()/plural()' },
  { re: new RegExp(`\\.(textField|slider|dropdown|toggle)\\(\\s*[^,]+,\\s*${Q}[^'"\`]*[A-Za-zА-Яа-яЁё]`), msg: 'literal text in form control; use t()' },
  { re: new RegExp(`new\\s+(CustomForm|MessageBox)\\([^,]*,\\s*${Q}`), msg: 'literal form title; use t()' },
  { re: new RegExp(`\\.nameTag\\s*=\\s*${Q}`), msg: 'literal nameTag; use a translated entity name instead' },
  { re: new RegExp(`\\btext\\s*:\\s*${Q}`), msg: 'literal rawtext {text:...}; use t()/lit()', letters: true },
  { re: new RegExp(`\\blit\\(\\s*${Q}`), msg: 'lit() with letters; words must be translated', letters: true },
];
const hasLetters = (line, idx) => /[A-Za-zА-Яа-яЁё]/.test(line.slice(idx).replace(/§./g, '').replace(/^[^'"`]*['"`]/, '').split(/['"`]/)[0] ?? '');

const { merged } = buildLang();
const keys = merged[LANGS[0]];
const errors = [];

for (const file of walk(path.join(ROOT, 'src')).filter((f) => f.endsWith('.ts') && !f.endsWith('.test.ts'))) {
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  lines.forEach((line, i) => {
    if (line.includes('i18n-ignore')) return;
    const code = line.replace(/\/\/.*$/, '');
    if (/^\s*(\*|\/\*)/.test(code)) return;
    for (const r of RULES) {
      const m = r.re.exec(code);
      if (m && (!r.letters || hasLetters(code, m.index))) errors.push(`${rel(file)}:${i + 1}: ${r.msg}\n      ${line.trim()}`);
    }
    for (const m of code.matchAll(/\b(t|plural)\(\s*'([^'$]+)'/g)) {
      const key = m[1] === 'plural' ? `${m[2]}.p1` : m[2];
      if (!keys.has(key)) errors.push(`${rel(file)}:${i + 1}: unknown lang key '${m[2]}'${m[1] === 'plural' ? ' (plural base)' : ''}`);
    }
  });
}

fail(errors, 'Hardcoded string check failed');
console.log('strings OK');
