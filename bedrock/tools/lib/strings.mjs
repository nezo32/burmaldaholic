// Pure helpers for tools/gen-lang.mjs: parse docs/design/STRINGS.md, route every key to the
// Bedrock module that owns it, convert placeholders to the Bedrock .lang form, add the
// Bedrock-only aliases (LOCALIZATION.md §1.3) and render/merge lang fragments.
// No I/O here so it can be unit-tested (tools/lib/strings.test.mjs).

/** STRINGS.md `## section` -> Bedrock module id (sections that are not modules go to core). */
export const SECTION_OWNERS = {
  core: 'core',
  streak: 'core',
  vip: 'vip',
  blackjack: 'blackjack',
  poker: 'poker',
  slots: 'slots',
  roulette: 'roulette',
  craps: 'craps',
  extras: 'extras',
  loan: 'loan',
  chaos: 'chaos',
  lastchance: 'lastchance',
  worldgen: 'worldgen',
  multiplayer: 'multiplayer',
  pvp: 'pvp',
  bots: 'bots',
  // modules developed in parallel branches: their keys are skipped (warning) until the module is registered
  baccarat: 'baccarat',
  uth: 'uth',
  advancements: 'core',
  config: 'core',
  sounds: 'core',
};

/** `### subsection` overrides inside a section (shared strings that core helpers render). */
export const SUBSECTION_OWNERS = {
  'blackjack/Cards (shared with poker)': 'core',
};

/** Key segment after `burmaldaholic.` that names a module under another spelling (CONFIG.md keys). */
export const SEGMENT_ALIASES = { lastChance: 'lastchance', ownership: 'multiplayer' };

export const MARKER = '## ---- manual: lines below this marker are kept by tools/gen-lang.mjs ----';

/**
 * Parse STRINGS.md. Rows: `| \`key\` | English | Russian |` (other lines are commentary).
 * Returns { entries: [{key, en, ru, section, subsection, line}], errors }.
 */
export function parseStrings(md) {
  const entries = [];
  const errors = [];
  const seen = new Map();
  let section = '';
  let subsection = '';
  md.split(/\r?\n/).forEach((raw, i) => {
    const line = i + 1;
    const h2 = /^##\s+(.+?)\s*$/.exec(raw);
    if (h2 && !raw.startsWith('###')) {
      section = h2[1].split(/[\s(]/)[0].toLowerCase();
      subsection = '';
      return;
    }
    const h3 = /^###\s+(.+?)\s*$/.exec(raw);
    if (h3) {
      subsection = h3[1];
      return;
    }
    if (!raw.startsWith('| `')) return;
    const cells = raw.split('|').slice(1, -1).map((c) => c.trim());
    if (cells.length !== 3) return errors.push(`STRINGS.md:${line}: expected 3 cells, got ${cells.length}`);
    const key = /^`([^`]+)`$/.exec(cells[0])?.[1];
    if (!key) return errors.push(`STRINGS.md:${line}: bad key cell ${cells[0]}`);
    if (seen.has(key)) errors.push(`STRINGS.md:${line}: duplicate key ${key} (first at line ${seen.get(key)})`);
    seen.set(key, line);
    if (!cells[1] || !cells[2]) errors.push(`STRINGS.md:${line}: empty value for ${key}`);
    entries.push({ key, en: cells[1], ru: cells[2], section, subsection, line });
  });
  return { entries, errors };
}

/**
 * LOCALIZATION.md §2 exact rule: `%N$s` -> `%N`, keep `%s`, keep `%%`.
 * Also strips trailing whitespace (Bedrock .lang forbids it).
 */
export function toBedrock(value) {
  return value.replace(/%(%|(\d+)\$s)/g, (m, a, n) => (a === '%' ? '%%' : `%${n}`)).trimEnd();
}

/** Module that owns `key` (must agree with scripts/lang.mjs ownership check). */
export function ownerOf(entry, modules) {
  const known = new Set(modules);
  const seg = segmentAfterNs(entry.key);
  if (seg) {
    const id = SEGMENT_ALIASES[seg] ?? seg;
    if (known.has(id)) return id;
  }
  const sub = SUBSECTION_OWNERS[`${entry.section}/${entry.subsection}`];
  if (sub) return sub;
  const owner = SECTION_OWNERS[entry.section];
  if (!owner) throw new Error(`STRINGS.md:${entry.line}: section '${entry.section}' has no owner module (add it to SECTION_OWNERS)`);
  return owner;
}

/** `gui.burmaldaholic.vip.title` -> 'vip' (only when followed by another segment). */
export function segmentAfterNs(key) {
  return /(?:^|\.)burmaldaholic\.([A-Za-z_]+)\./.exec(key)?.[1];
}

/** Bedrock-only aliases generated from canonical keys (LOCALIZATION.md §1.3). */
export function aliasesFor(entry) {
  const out = [];
  const ent = /^entity\.burmaldaholic\.([a-z0-9_]+)$/.exec(entry.key);
  if (ent) out.push({ ...entry, key: `entity.burmaldaholic:${ent[1]}.name` });
  const egg = /^item\.burmaldaholic\.([a-z0-9_]+)_spawn_egg$/.exec(entry.key);
  if (egg) out.push({ ...entry, key: `item.spawn_egg.entity.burmaldaholic:${egg[1]}.name` });
  if (entry.key === 'modmenu.nameTranslation.burmaldaholic') out.push({ ...entry, key: 'pack.name' });
  if (entry.key === 'modmenu.summaryTranslation.burmaldaholic') out.push({ ...entry, key: 'pack.description' });
  return out;
}

/** Group entries (plus aliases) by owning module: Map<module, entry[]>. */
export function routeEntries(entries, modules, skipped = []) {
  const byModule = new Map(modules.map((m) => [m, []]));
  for (const e of entries) {
    const owner = ownerOf(e, modules);
    // A section whose module is not in modules.json yet (merged later from another branch): skip it.
    if (!byModule.has(owner)) {
      skipped.push(e);
      continue;
    }
    byModule.get(owner).push(e, ...aliasesFor(e));
  }
  return byModule;
}

/** Split an existing fragment into [generatedPart, manualPart] at MARKER. */
export function splitManual(text) {
  const i = text.indexOf(MARKER);
  if (i < 0) return { manual: '' };
  return { manual: text.slice(i + MARKER.length).replace(/^\r?\n/, '') };
}

/** Render one lang fragment. `lang` is 'en' | 'ru'. */
export function renderFragment(module, lang, list, manual = '') {
  const head = [
    `## ${module} strings (${lang === 'en' ? 'en_US' : 'ru_RU'}). GENERATED from docs/design/STRINGS.md by tools/gen-lang.mjs.`,
    '## Do not edit above the manual marker: re-run `npm run gen:lang` instead. Placeholders: %1, %2 ... (Bedrock form).',
  ];
  const body = [];
  let lastSection = '';
  for (const e of list) {
    const sec = e.subsection ? `${e.section} / ${e.subsection}` : e.section;
    if (sec !== lastSection) {
      body.push(`## [${sec}]`);
      lastSection = sec;
    }
    body.push(`${e.key}=${toBedrock(lang === 'en' ? e.en : e.ru)}`);
  }
  return [...head, ...body, MARKER, manual.trimEnd()].join('\n').trimEnd() + '\n';
}
