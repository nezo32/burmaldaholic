/**
 * Config definitions and validation. PURE.
 *
 * The full key list lives in `config-catalog.ts` (generated from docs/design/CONFIG.md by
 * tools/gen-config.mjs). Keys are the exact CONFIG.md dotted keys (`economy.ore.diamond`),
 * identical on Java and Bedrock. Values persist as ONE world dynamic property
 * `burmaldaholic:config` holding a flat JSON object of overrides only (missing = default).
 *
 * Validation (CONFIG.md "Storage and editing"): out-of-range numbers are CLAMPED (and the
 * editor is told); a wrong type falls back to the default.
 */

export type ConfigType = 'bool' | 'int' | 'long' | 'double' | 'enum' | 'json';
/** json values: lists / maps from CONFIG.md (`list<int>`, `map<symbol,int>` ...). */
export type JsonValue = boolean | number | string | null | JsonValue[] | { [k: string]: JsonValue };
export type ConfigValue = boolean | number | string | JsonValue;

export interface ConfigDef {
  /** Exact CONFIG.md key, e.g. `blackjack.decks`. */
  readonly key: string;
  readonly type: ConfigType;
  readonly default: ConfigValue;
  /** Inclusive numeric range (int/long/double). Missing = unbounded (safe integers for int/long). */
  readonly min?: number;
  readonly max?: number;
  /** json lists/maps: every number inside must be within [each[0], each[1]]. */
  readonly each?: readonly [number, number];
  readonly shape?: 'list' | 'map';
  readonly options?: readonly string[];
  /** Admin-form section: label `config.burmaldaholic.section.<section>`. */
  readonly section: string;
  /** Module that reads the key (core for economy/contracts/wager/streak/debug). */
  readonly owner: string;
  /** Lang key of the label; `labelArg` fills its %1 for family templates. */
  readonly label: string;
  readonly labelArg?: { readonly key?: string; /** literal member id (not a word to translate) */ readonly id?: string };
  readonly tooltip?: string;
  /** Lang keys for enum options (same order as `options`). */
  readonly optionLabels?: readonly string[];
}

/**
 * Extra config a module declares in `CasinoModule.config` (only for keys NOT in CONFIG.md;
 * catalog keys need no declaration). `name` is relative to the module (`enabled` ->
 * `<prefix>.enabled`) unless it contains a dot. Label key: `config.burmaldaholic.<full key>`.
 */
export type ModuleConfigDef =
  | { type: 'bool'; name: string; default: boolean }
  | { type: 'int'; name: string; default: number; min: number; max: number; step?: number }
  | { type: 'double'; name: string; default: number; min: number; max: number }
  | { type: 'enum'; name: string; default: string; options: readonly string[] };

/**
 * Lang key of enum option `i`: its `optionLabels` entry, else the conventional
 * `config.burmaldaholic.<key>.<option lowercased>` - never the raw option name (review m7).
 */
export function enumOptionLabel(d: Pick<ConfigDef, 'key' | 'options' | 'optionLabels'>, i: number): string {
  return d.optionLabels?.[i] ?? `config.burmaldaholic.${d.key}.${String(d.options?.[i] ?? '').toLowerCase()}`;
}

/** Config-key prefix of a module (CONFIG.md spelling). */
export const MODULE_CONFIG_PREFIX: Readonly<Record<string, string>> = { lastchance: 'lastChance' };
export const configPrefix = (module: string): string => MODULE_CONFIG_PREFIX[module] ?? module;

export const CONFIG_PROPERTY = 'burmaldaholic:config';

export function fromModuleDef(module: string, d: ModuleConfigDef): ConfigDef {
  const key = d.name.includes('.') ? d.name : `${configPrefix(module)}.${d.name}`;
  const common = { key, section: module === 'multiplayer' ? 'ownership' : module, owner: module, label: `config.burmaldaholic.${key}` };
  switch (d.type) {
    case 'bool':
      return { ...common, type: 'bool', default: d.default };
    case 'int':
      return { ...common, type: 'int', default: d.default, min: d.min, max: d.max };
    case 'double':
      return { ...common, type: 'double', default: d.default, min: d.min, max: d.max };
    case 'enum':
      return {
        ...common,
        type: 'enum',
        default: d.default,
        options: d.options,
        optionLabels: d.options.map((o) => `config.burmaldaholic.${key}.${o.toLowerCase()}`),
      };
  }
}

export interface Sanitized {
  value: ConfigValue;
  /** true when a number was clamped into range */
  clamped: boolean;
  /** true when the raw value had the wrong type/shape and the default was used */
  invalid: boolean;
}

function bounds(def: ConfigDef): [number, number] {
  const intLike = def.type === 'int' || def.type === 'long';
  const lo = def.min ?? (intLike ? (def.type === 'int' ? -2_147_483_648 : Number.MIN_SAFE_INTEGER) : -Number.MAX_VALUE);
  const hi = def.max ?? (intLike ? (def.type === 'int' ? 2_147_483_647 : Number.MAX_SAFE_INTEGER) : Number.MAX_VALUE);
  return [lo, hi];
}

function jsonOk(def: ConfigDef, v: unknown): boolean {
  if (def.shape === 'list' && !Array.isArray(v)) return false;
  if (def.shape === 'map' && (typeof v !== 'object' || v === null || Array.isArray(v))) return false;
  const nums: number[] = [];
  const walk = (x: unknown): boolean => {
    if (typeof x === 'number') return Number.isFinite(x) ? (nums.push(x), true) : false;
    if (typeof x === 'string' || typeof x === 'boolean') return true;
    if (Array.isArray(x)) return x.every(walk);
    if (x && typeof x === 'object') return Object.values(x).every(walk);
    return false;
  };
  if (!walk(v)) return false;
  // Lists of scalars / maps must keep the default's element type (e.g. segments are strings).
  const d = def.default;
  if (def.shape === 'list' && Array.isArray(d) && d.length > 0 && Array.isArray(v)) {
    const want = typeof d[0];
    if (v.some((e) => typeof e !== want)) return false;
  }
  if (def.each) return nums.every((n) => n >= def.each![0] && n <= def.each![1]);
  return true;
}

/** Coerce a stored / typed-in value to a valid value for `def`. */
export function sanitizeValue(def: ConfigDef, raw: unknown): Sanitized {
  const bad: Sanitized = { value: def.default, clamped: false, invalid: raw !== undefined };
  switch (def.type) {
    case 'bool':
      return typeof raw === 'boolean' ? { value: raw, clamped: false, invalid: false } : bad;
    case 'enum':
      return typeof raw === 'string' && def.options?.includes(raw) ? { value: raw, clamped: false, invalid: false } : bad;
    case 'json':
      return jsonOk(def, raw) ? { value: raw as JsonValue, clamped: false, invalid: false } : bad;
    default: {
      if (typeof raw !== 'number' || !Number.isFinite(raw)) return bad;
      const [lo, hi] = bounds(def);
      const n = def.type === 'double' ? raw : Math.round(raw);
      const v = Math.min(hi, Math.max(lo, n));
      return { value: v, clamped: v !== raw, invalid: false };
    }
  }
}

/** Back-compat helper: sanitized value only. */
export const sanitize = (def: ConfigDef, raw: unknown): ConfigValue => sanitizeValue(def, raw).value;

/**
 * Parse text typed by an operator (admin text field, `/scriptevent burmaldaholic:config set`).
 * Accepts `1 000` style grouping for numbers and JSON for lists/maps. Returns undefined when the
 * text cannot be read as the def's type (the caller shows `config.burmaldaholic.invalid`).
 */
export function parseInput(def: ConfigDef, text: string): ConfigValue | undefined {
  const s = text.trim();
  switch (def.type) {
    case 'bool':
      if (/^(true|on|yes|1)$/i.test(s)) return true;
      if (/^(false|off|no|0)$/i.test(s)) return false;
      return undefined;
    case 'enum': {
      const hit = def.options?.find((o) => o.toLowerCase() === s.toLowerCase());
      return hit;
    }
    case 'json':
      try {
        return JSON.parse(s) as JsonValue;
      } catch {
        return undefined;
      }
    default: {
      const n = Number(s.replace(/[\s\u00a0_]/g, '').replace(',', '.'));
      if (s === '' || !Number.isFinite(n)) return undefined;
      if (def.type !== 'double' && !Number.isInteger(n)) return undefined;
      return n;
    }
  }
}

/** Format a value for a text field / chat (no grouping so it round-trips through parseInput). */
export function formatValue(v: ConfigValue): string {
  return typeof v === 'object' ? JSON.stringify(v) : String(v);
}

/** Definition-level sanity (duplicate keys, default in range/options). Throws on error. */
export function validateDefs(defs: readonly ConfigDef[]): void {
  const seen = new Set<string>();
  for (const d of defs) {
    if (!/^[a-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*$/.test(d.key)) throw new Error(`config ${d.key}: bad key`);
    if (seen.has(d.key)) throw new Error(`config ${d.key}: duplicate`);
    seen.add(d.key);
    const s = sanitizeValue(d, d.default);
    if (s.invalid || s.clamped) throw new Error(`config ${d.key}: default out of range / wrong type`);
  }
}

/**
 * Cross-key rules from CONFIG.md, applied after single-key sanitizing. `get` returns the
 * effective value. Returns corrections to apply ({key: value}); empty when consistent.
 *  - economy.emeraldSellRate >= economy.emeraldBuyRate (and the Gold VIP buy rate)
 *  - vip.threshold.* ascending (silver < gold < platinum < diamond < netherite)
 *  - streak.minHouseEdge >= 0.005
 */
export function crossValidate(get: (key: string) => ConfigValue | undefined): Record<string, number> {
  const fix: Record<string, number> = {};
  const num = (k: string): number | undefined => {
    const v = k in fix ? fix[k] : get(k);
    return typeof v === 'number' ? v : undefined;
  };
  const buy = Math.max(num('economy.emeraldBuyRate') ?? 0, num('economy.emeraldBuyRateGoldVip') ?? 0);
  const sell = num('economy.emeraldSellRate');
  if (sell !== undefined && sell < buy) fix['economy.emeraldSellRate'] = buy;
  let prev = 0;
  for (const tier of ['silver', 'gold', 'platinum', 'diamond', 'netherite']) {
    const k = `vip.threshold.${tier}`;
    const v = num(k);
    if (v === undefined) continue;
    if (v <= prev) fix[k] = prev + 1;
    prev = num(k) ?? v;
  }
  const edge = num('streak.minHouseEdge');
  if (edge !== undefined && edge < 0.005) fix['streak.minHouseEdge'] = 0.005;
  return fix;
}

/**
 * Pure override store: the parsed content of `burmaldaholic:config`. Only non-default values
 * are kept so the JSON stays small (32 767-char dynamic property limit).
 */
export class ConfigOverrides {
  private readonly values = new Map<string, ConfigValue>();

  constructor(private readonly defs: ReadonlyMap<string, ConfigDef>) {}

  static parse(defs: ReadonlyMap<string, ConfigDef>, json: unknown): ConfigOverrides {
    const o = new ConfigOverrides(defs);
    if (typeof json !== 'string') return o;
    try {
      const obj = JSON.parse(json) as Record<string, unknown>;
      // Unknown keys are dropped (logged by the caller); stored values are re-sanitized.
      for (const [k, v] of Object.entries(obj)) if (defs.has(k)) o.values.set(k, v as ConfigValue);
    } catch {
      /* corrupt property -> defaults */
    }
    return o;
  }

  /** Effective value (override or default), always valid for the def. */
  get(key: string): ConfigValue {
    const def = this.defs.get(key);
    if (!def) throw new Error(`unknown config key ${key}`);
    const raw = this.values.get(key);
    if (raw === undefined) return def.default;
    const s = sanitizeValue(def, raw);
    const fix = crossValidate((k) => (k === key ? s.value : this.peek(k)));
    return key in fix ? (fix[key] as number) : s.value;
  }

  private peek(key: string): ConfigValue | undefined {
    const def = this.defs.get(key);
    if (!def) return undefined;
    const raw = this.values.get(key);
    return raw === undefined ? def.default : sanitizeValue(def, raw).value;
  }

  has(key: string): boolean {
    return this.values.has(key);
  }

  /** Set a value (sanitized/clamped). Returns what was stored and whether it was clamped. */
  set(key: string, raw: unknown): Sanitized {
    const def = this.defs.get(key);
    if (!def) throw new Error(`unknown config key ${key}`);
    const s = sanitizeValue(def, raw);
    if (s.invalid) return s;
    if (JSON.stringify(s.value) === JSON.stringify(def.default)) this.values.delete(key);
    else this.values.set(key, s.value);
    return s;
  }

  reset(key: string): void {
    this.values.delete(key);
  }

  serialize(): string {
    return JSON.stringify(Object.fromEntries(this.values));
  }
}
