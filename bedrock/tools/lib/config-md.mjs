// Pure helpers for tools/gen-config.mjs: parse docs/design/CONFIG.md into typed config
// definitions (key, type, default, range, section, owner module, label keys).

/** First key segment -> admin-form section (label `config.burmaldaholic.section.<section>`). */
export const SECTION_OF = {
  core: 'core', economy: 'economy', contracts: 'contracts', wager: 'wager', vip: 'vip',
  blackjack: 'blackjack', poker: 'poker', slots: 'slots', roulette: 'roulette', craps: 'craps',
  extras: 'extras', loan: 'loan', chaos: 'chaos', streak: 'streak', lastChance: 'lastchance',
  worldgen: 'worldgen', ownership: 'ownership', multiplayer: 'ownership', debug: 'debug',
  pvp: 'pvp', bots: 'bots', baccarat: 'baccarat', uth: 'uth',
};

/** First key segment -> Bedrock module that owns (reads) the key. */
export const OWNER_OF = {
  core: 'core', economy: 'core', contracts: 'core', wager: 'core', streak: 'core', debug: 'core',
  lastChance: 'lastchance', ownership: 'multiplayer', multiplayer: 'multiplayer',
};

/** Keys documented in CONFIG.md but stored elsewhere (casino mode has its own property). */
export const NOT_IN_STORE = new Set(['core.casinoMode']);

// Family members and table defaults that CONFIG.md references by section (GAME_DESIGN.md).
const CONTRACTS = { mine_iron: 10, mine_coal: 8, mine_diamond: 5, kill_zombie: 10, kill_skeleton: 8, kill_creeper: 6, kill_any: 8, trade: 8, fish: 6, harvest: 6, wager: 8, win_blackjack: 5, spin_slots: 5, roulette_red: 4, play_poker: 3, explore_nether: 3, smelt: 3 };
const APPRAISAL = { iron_ingot: 2, gold_ingot: 4, emerald: 8, emerald_block: 72, lapis_block: 15, golden_apple: 30, totem_of_undying: 150, nether_star: 400, trident: 250, diamond: 20, diamond_block: 180, netherite_scrap: 40, netherite_ingot: 150, ancient_debris: 45, enchanted_golden_apple: 300, heart_of_the_sea: 200, elytra: 500, echo_shard: 25 };
const CHAOS = { chip_shower: 18, lucky_buff: 20, diamond_rain: 4, xp_fountain: 12, curse: 16, mob_wave: 12, random_teleport: 8, weather_change: 8, golden_hour: 2 };
const SLOTS = {
  copper: {
    weights: { berries: 24, apple: 20, golden_carrot: 16, emerald: 12, diamond: 8, seven: 5, creeper: 15 },
    pays: { berries: 10, apple: 10, golden_carrot: 20, emerald: 30, diamond: 60, seven: 150, creeper: 0 },
  },
  gold: {
    weights: { berries: 22, apple: 19, golden_carrot: 16, emerald: 12, diamond: 8, seven: 5, wild: 3, creeper: 8, pearl: 5, star: 2 },
    pays: { berries: 8, apple: 7, golden_carrot: 11, emerald: 25, diamond: 50, seven: 100, wild: 200, creeper: 0, pearl: 10, star: 0 },
  },
  netherite: {
    weights: { berries: 20, apple: 19, golden_carrot: 16, emerald: 12, diamond: 9, seven: 6, wild: 3, tnt: 6, pearl: 5, clock: 2, star: 2 },
    pays: { berries: 8, apple: 9, golden_carrot: 14, emerald: 25, diamond: 50, seven: 100, wild: 250, tnt: 0, pearl: 10, clock: 50, star: 0 },
  },
};
const WHEEL_SEGMENTS = 'X B M B D B M B H B D B M B T B M B D B H B M B D B E B M B D B H B M B T B M B D B H B M B T B C M H D M B'.split(' ');
const PLINKO = {
  low: [10, 3, 1.6, 1.4, 1, 1, 0.5, 1, 1, 1.4, 1.6, 3, 10],
  medium: [33, 11, 4, 2, 1, 0.6, 0.3, 0.6, 1, 2, 4, 11, 33],
  high: [170, 24, 8.1, 2, 0.6, 0.2, 0.2, 0.2, 0.6, 2, 8.1, 24, 170],
};
/** `bots.table.<game>.*` matrix (CONFIG.md ## bots, BOTS.md §9.2). */
const BOT_TABLES = {
  poker: { policy: 'MIXED', count: 5, difficulty: 'MIXED', worldgenPolicy: 'MIXED', worldgenCount: 3 },
  chemmy: { policy: 'MIXED', count: 2, difficulty: 'MIXED', worldgenPolicy: 'MIXED', worldgenCount: 2 },
  blackjack: { policy: 'HUMANS_ONLY', count: 0, difficulty: 'NORMAL', worldgenPolicy: 'MIXED', worldgenCount: 2 },
  roulette: { policy: 'HUMANS_ONLY', count: 0, difficulty: 'MIXED', worldgenPolicy: 'MIXED', worldgenCount: 3 },
  craps: { policy: 'HUMANS_ONLY', count: 0, difficulty: 'MIXED', worldgenPolicy: 'MIXED', worldgenCount: 2 },
  baccarat: { policy: 'HUMANS_ONLY', count: 0, difficulty: 'MIXED', worldgenPolicy: 'MIXED', worldgenCount: 2 },
  uth: { policy: 'HUMANS_ONLY', count: 0, difficulty: 'NORMAL', worldgenPolicy: 'MIXED', worldgenCount: 2 },
};
/** Defaults CONFIG.md gives as a section reference instead of a literal. */
export const TABLE_DEFAULTS = {
  'pvp.scratch.weights': { coal: 30, iron: 25, gold: 18, emerald: 12, diamond: 6, star: 1, creeper: 5, foot: 3 },
  'pvp.scratch.values': { coal: 1, iron: 2, gold: 3, emerald: 5, diamond: 10, star: 25 },
  'extras.wheel.segments': WHEEL_SEGMENTS,
  'extras.wheel.multipliers': { B: 0, C: 0, H: 0.5, M: 1, D: 2, T: 3, E: 5, X: 10 },
  'extras.scratch.basic.prizes': [[10, 0.22], [20, 0.1], [50, 0.03], [100, 0.01], [500, 0.002], [2500, 0.0001]],
  'extras.scratch.gold.prizes': [[100, 0.2], [200, 0.1], [500, 0.05], [1000, 0.01], [5000, 0.001], [25000, 0.0002]],
  'extras.plinko.low': PLINKO.low,
  'extras.plinko.medium': PLINKO.medium,
  'extras.plinko.high': PLINKO.high,
};

/** `<placeholder>` families -> [{member, default, labelArg}] */
export function familyMembers(key) {
  const fam = (obj, value, arg) => Object.entries(obj).map(([m, v]) => ({ member: m, value: value(v, m), arg: arg(m) }));
  switch (key) {
    case 'contracts.weight.<id>': return fam(CONTRACTS, (v) => v, (m) => ({ id: m }));
    case 'wager.appraisal.<item_id>': return fam(APPRAISAL, (v) => v, (m) => ({ id: m }));
    case 'chaos.weight.<event>': return fam(CHAOS, (v) => v, (m) => ({ key: `gui.burmaldaholic.chaos.event.${m}` }));
    case 'chaos.event.<event>.enabled': return fam(CHAOS, () => true, (m) => ({ key: `gui.burmaldaholic.chaos.event.${m}` }));
    case 'slots.<tier>.weights': return fam(SLOTS, (v) => v.weights, (m) => ({ key: `block.burmaldaholic.slot_machine_${m}` }));
    case 'slots.<tier>.pays': return fam(SLOTS, (v) => v.pays, (m) => ({ key: `block.burmaldaholic.slot_machine_${m}` }));
    case 'slots.<tier>.berryPartial': return fam(SLOTS, () => [2, 3], (m) => ({ key: `block.burmaldaholic.slot_machine_${m}` }));
    case 'bots.table.<game>.policy':
    case 'bots.table.<game>.count':
    case 'bots.table.<game>.difficulty':
    case 'bots.table.<game>.worldgenPolicy':
    case 'bots.table.<game>.worldgenCount': {
      const field = key.split('.').pop();
      return fam(BOT_TABLES, (v) => v[field], (m) => ({ key: `gui.burmaldaholic.common.game.${m}` }));
    }
    default: throw new Error(`unknown config family ${key}`);
  }
}

/** '1 000' / '10⁶' / '0.25' / '9 007 199 254 740 991' -> number */
export function parseNum(s) {
  const t = s.replace(/\s/g, '').replace('−', '-');
  const sup = /^(\d+)([⁰¹²³⁴⁵⁶⁷⁸⁹]+)$/.exec(t);
  if (sup) {
    const exp = Number([...sup[2]].map((c) => '⁰¹²³⁴⁵⁶⁷⁸⁹'.indexOf(c)).join(''));
    return Number(sup[1]) ** exp;
  }
  if (!/^-?\d+(\.\d+)?$/.test(t)) return NaN;
  return Number(t);
}

/** Label key of an enum option (shared vocabularies where STRINGS.md defines no `<key>.<option>` row). */
export function optionLabel(key, exact, option) {
  const o = option.toLowerCase();
  if (key === 'core.hud.position') return `gui.burmaldaholic.menu.settings.corner.${o}`;
  if (/^bots\.table\.<game>\.(policy|worldgenPolicy)$/.test(key)) return `gui.burmaldaholic.bots.policy.${o}`;
  if (key === 'bots.table.<game>.difficulty') return `gui.burmaldaholic.bots.level.${o}`;
  if (key === 'pvp.tournament.autoMode') return `gui.burmaldaholic.pvp.game.${o}`;
  if (key === 'pvp.tournament.autoFormat') return `gui.burmaldaholic.pvp.tournament.format.${o}`;
  return `${exact}.${o}`;
}

/** Range cell -> {min,max,each} | {} | undefined (blank = inherit). */
export function parseRange(cell) {
  const c = cell.trim();
  if (c === '') return undefined;
  if (c === '—' || c === 'any') return {};
  const m = /^(each\s+|totals\s+)?([\d\s.⁰¹²³⁴⁵⁶⁷⁸⁹]+)–([\d\s.⁰¹²³⁴⁵⁶⁷⁸⁹]+)$/.exec(c);
  if (!m) return { note: c };
  return { min: parseNum(m[2]), max: parseNum(m[3]), each: !!m[1] };
}

export function parseType(cell) {
  const c = cell.trim();
  const en = /^enum\((.+)\)$/.exec(c);
  if (en) return { type: 'enum', options: en[1].split(',').map((o) => o.trim()) };
  if (/^(list|map)</.test(c)) return { type: 'json', shape: c.startsWith('map') ? 'map' : 'list', spec: c };
  if (['bool', 'int', 'long', 'double'].includes(c)) return { type: c };
  throw new Error(`unknown config type '${c}'`);
}

export function parseDefault(cell, type, key) {
  // Edition-specific default: `24 (Java) / 12 (Bedrock)` -> the Bedrock value.
  const edition = /^.+?\(Java\)\s*\/\s*(.+?)\s*\(Bedrock\)\s*$/.exec(cell.trim());
  const c = (edition ? edition[1] : cell.trim()).replace(/\s*\(.*\)\s*$/, '');
  if (type.type === 'bool') return c === 'true';
  if (type.type === 'enum') return c;
  if (type.type === 'json') {
    if (key in TABLE_DEFAULTS) return TABLE_DEFAULTS[key];
    // `map<k,v>` written as `royal 500, straightFlush 50, ...` (CONFIG.md §uth paytables)
    const pairs = type.shape === 'map' ? /^[A-Za-z_]\w*\s+-?[\d.]+(\s*,\s*[A-Za-z_]\w*\s+-?[\d.]+)*$/.exec(c) : null;
    if (pairs) {
      return Object.fromEntries(c.split(',').map((p) => {
        const [k, v] = p.trim().split(/\s+/);
        return [k, parseNum(v)];
      }));
    }
    return JSON.parse(c);
  }
  const n = parseNum(c);
  if (Number.isNaN(n)) throw new Error(`bad default '${cell}' for ${key}`);
  return n;
}

/**
 * Parse CONFIG.md. `labels` = Set of lang keys from STRINGS.md (to resolve label/tooltip/options).
 * Returns { defs, errors, warnings }.
 */
export function parseConfigMd(md, labels, modules) {
  const defs = [];
  const errors = [];
  const warnings = [];
  const skipped = new Set();
  let prevRange;
  for (const [i, raw] of md.split(/\r?\n/).entries()) {
    if (!raw.startsWith('| `')) continue;
    const cells = raw.split('|').slice(1, -1).map((c) => c.trim());
    const key = /^`([^`]+)`$/.exec(cells[0])?.[1];
    if (!key || cells.length < 5) continue;
    const first = key.split('.')[0];
    if (!SECTION_OF[first] && modules && !modules.includes(OWNER_OF[first] ?? first)) {
      // section of a module developed in another branch (not in modules.json yet): skip until merged
      skipped.add(OWNER_OF[first] ?? first);
      continue;
    }
    try {
      const type = parseType(cells[1]);
      // A blank range repeats the previous row's range for siblings of the same type
      // (vip.threshold.*, poker.stakes.*.bb ...); otherwise it means "no range".
      const parent = key.split('.').slice(0, -1).join('.');
      const group = /^([a-z]+\.[A-Za-z]+)/.exec(key)?.[1];
      let range = parseRange(cells[3]);
      if (range === undefined) range = prevRange && prevRange.type === type.type && prevRange.group === group ? prevRange.range : {};
      prevRange = { range, type: type.type, group, parent };
      if (NOT_IN_STORE.has(key)) continue;
      const base = { type: type.type, ...(type.options ? { options: type.options } : {}), ...(type.shape ? { shape: type.shape } : {}) };
      if (range.min !== undefined) Object.assign(base, range.each || type.type === 'json' ? { each: [range.min, range.max] } : { min: range.min, max: range.max });
      const members = key.includes('<') ? familyMembers(key) : [{ member: undefined, value: parseDefault(cells[2], type, key) }];
      for (const m of members) {
        const full = m.member === undefined ? key : key.replace(/<[^>]+>/, m.member);
        const first = full.split('.')[0];
        const def = { key: full, ...base, default: m.value, section: SECTION_OF[first], owner: OWNER_OF[first] ?? first };
        if (!def.section) throw new Error(`no section for ${full}`);
        // Labels (STRINGS.md §config): exact key, else the family template with the member as %1.
        const exact = `config.burmaldaholic.${full}`;
        const template = `config.burmaldaholic.${key.replace(/\.?<[^>]+>/, '')}`;
        if (labels.has(exact)) def.label = exact;
        else if (m.member !== undefined && labels.has(template)) Object.assign(def, { label: template, labelArg: m.arg });
        else if (labels.has(exact.replace(/\.[a-z]+$/, '')) && labels.has(`gui.burmaldaholic.common.game.${full.split('.').pop()}`)) {
          // per-game key under a template label (bots.atmosphere.maxPerTable.<game>)
          Object.assign(def, { label: exact.replace(/\.[a-z]+$/, ''), labelArg: { key: `gui.burmaldaholic.common.game.${full.split('.').pop()}` } });
        }
        else {
          def.label = exact;
          warnings.push(`no label for ${full} in STRINGS.md`);
        }
        if (labels.has(`${exact}.tooltip`)) def.tooltip = `${exact}.tooltip`;
        if (type.options) {
          def.optionLabels = type.options.map((o) => {
            const k = optionLabel(key, exact, o);
            if (!labels.has(k)) warnings.push(`no option label ${k}`);
            return k;
          });
        }
        defs.push(def);
      }
    } catch (e) {
      errors.push(`CONFIG.md:${i + 1}: ${e.message}`);
    }
  }
  if (skipped.size) warnings.push(`skipped the keys of modules not registered yet: ${[...skipped].join(', ')}`);
  return { defs, errors, warnings };
}
