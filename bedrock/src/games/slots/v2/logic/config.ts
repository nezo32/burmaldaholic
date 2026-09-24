/**
 * Slots v2 config (SLOTS.md §12) → validated {@link MachineDef}. PURE. Lane S-B4.
 *
 * `readMachineConfig` reads every `slots.<m>.*` key through a getter (the module passes `ctx.config` once the
 * keys are in CONFIG.md; until the cut-over it passes a getter that returns undefined → defaults). Every key is
 * validated on its own: an invalid value is rejected with an error message and the default is kept (the
 * "reject, never auto-fix" rule of SLOTS.md §12). `buildMachineDef` turns a config into integer fifths.
 */
import { COIN_KEYS, DEFAULT_CONFIG, JACKPOT_TIERS, type MachineConfig, PICK_KEYS, SYMBOLS } from './machines';
import { type MachineDef, type MachineId, REELS, type SymbolRole } from './types';

export type ConfigGetter = (key: string) => unknown;

/** Bonus symbol reels (bit r−1): Overworld chests on 1, 3, 5; End crystals on 2, 3, 4. */
export const BONUS_REELS_MASK: Readonly<Record<MachineId, number>> = { overworld: 0b10101, nether: 0, end: 0b01110 };

const EPS = 1e-9;
const isInt = (v: unknown): v is number => typeof v === 'number' && Number.isInteger(v);
const isNum = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);
/** x × 5 as an integer when x is a multiple of 0.2, else undefined. */
export const toFifths = (x: number): number | undefined => {
  const f = Math.round(x * 5);
  return Math.abs(x * 5 - f) < EPS ? f : undefined;
};

type Check<T> = (v: unknown) => T | string;

const intIn =
  (min: number, max: number): Check<number> =>
  (v) =>
    isInt(v) && v >= min && v <= max ? v : `expected an integer ${min}–${max}`;
const numIn =
  (min: number, max: number): Check<number> =>
  (v) =>
    isNum(v) && v >= min && v <= max ? v : `expected a number ${min}–${max}`;
const bool: Check<boolean> = (v) => (typeof v === 'boolean' ? v : 'expected true/false');
const fifthsIn =
  (min: number, max: number): Check<number> =>
  (v) =>
    isNum(v) && v >= min && v <= max && toFifths(v) !== undefined ? v : `expected a multiple of 0.2 in ${min}–${max}`;
const listOf =
  <T>(each: Check<T>, minLen: number, maxLen: number): Check<T[]> =>
  (v) => {
    if (!Array.isArray(v) || v.length < minLen || v.length > maxLen) return `expected a list of ${minLen === maxLen ? minLen : `${minLen}–${maxLen}`} entries`;
    const out: T[] = [];
    for (const x of v) {
      const r = each(x);
      if (typeof r === 'string') return r;
      out.push(r);
    }
    return out;
  };
const mapOf =
  <T>(keys: readonly string[], each: Check<T>): Check<Record<string, T>> =>
  (v) => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) return 'expected a map';
    const out: Record<string, T> = {};
    for (const k of keys) {
      const raw = (v as Record<string, unknown>)[k];
      if (raw === undefined) return `missing key ${k}`;
      const r = each(raw);
      if (typeof r === 'string') return `${k}: ${r}`;
      out[k] = r;
    }
    for (const k of Object.keys(v)) if (!keys.includes(k)) return `unknown key ${k}`;
    return out;
  };

const betList: Check<number[]> = (v) => {
  const l = listOf(intIn(5, 1_000_000), 1, 8)(v);
  if (typeof l === 'string') return l;
  if (l.some((b) => b % 5 !== 0)) return 'every bet must be a multiple of 5';
  for (let i = 1; i < l.length; i++) if (l[i]! <= l[i - 1]!) return 'bets must be sorted ascending';
  return l;
};

const wheelToken =
  (allowUp: boolean): Check<string> =>
  (v) => {
    if (typeof v !== 'string') return 'expected a string token';
    if (/^[1-9]\d{0,4}$/.test(v)) return v;
    if (['MINI', 'MINOR', 'MAJOR', 'GRAND'].includes(v)) return v;
    if (v === 'UP' && allowUp) return v;
    return `bad wheel token ${v}`;
  };

/** Strip validation (SLOTS.md §12 `slots.<m>.strips`). Returns an error or undefined. */
export function stripError(id: MachineId, strips: readonly string[]): string | undefined {
  if (strips.length !== REELS) return 'exactly 5 strips expected';
  const codes = SYMBOLS[id].map((s) => s.code);
  const role = (c: string): SymbolRole | undefined => SYMBOLS[id][codes.indexOf(c)]?.role;
  const bonusMask = BONUS_REELS_MASK[id];
  for (let r = 0; r < REELS; r++) {
    const s = strips[r]!.trim().split(/\s+/);
    if (s.length < 3 || s.length > 200) return `reel ${r + 1}: 3–200 stops expected`;
    const scat: number[] = [];
    for (let i = 0; i < s.length; i++) {
      const ro = role(s[i]!);
      if (!ro) return `reel ${r + 1}: unknown code ${s[i]}`;
      if (ro === 'WILD' && (r === 0 || r === 4)) return `reel ${r + 1}: wild only on reels 2–4`;
      if (ro === 'BONUS' && !(bonusMask & (1 << r))) return `reel ${r + 1}: bonus symbol not allowed`;
      if (ro === 'SCATTER') scat.push(i);
    }
    for (let a = 0; a < scat.length; a++)
      for (let b = a + 1; b < scat.length; b++) {
        const d = Math.abs(scat[a]! - scat[b]!);
        if (Math.min(d, s.length - d) < 3) return `reel ${r + 1}: scatters closer than 3 stops`;
      }
  }
  return undefined;
}

const stripsCheck =
  (id: MachineId): Check<string[]> =>
  (v) => {
    const l = listOf<string>((x) => (typeof x === 'string' ? x : 'expected strings'), 5, 5)(v);
    if (typeof l === 'string') return l;
    return stripError(id, l) ?? l;
  };

/** Every configurable key of a machine with its check (relative to `slots.<m>.`). */
function machineChecks(id: MachineId): Array<[string, Check<unknown>, (c: MachineConfig, v: never) => void]> {
  const paySyms = SYMBOLS[id].filter((s) => s.role === 'PAY').map((s) => s.id);
  const tiers = JACKPOT_TIERS as readonly string[];
  const out: Array<[string, Check<unknown>, (c: MachineConfig, v: never) => void]> = [
    ['enabled', bool, (c, v: boolean) => (c.enabled = v)],
    ['bets', betList, (c, v: number[]) => (c.bets = v)],
    ['defaultBet', intIn(1, 1_000_000), (c, v: number) => (c.defaultBet = v)],
    ['minVipTier', intIn(0, 5), (c, v: number) => (c.minVipTier = v)],
    ['maxWinMultiple', intIn(50, 100_000), (c, v: number) => (c.maxWinMultiple = v)],
    ['strips', stripsCheck(id), (c, v: string[]) => (c.strips = v)],
    ['pays', mapOf(paySyms, listOf(fifthsIn(0, 10_000), 3, 3)), (c, v: Record<string, number[]>) => (c.pays = v)],
    ['scatterPays', listOf(intIn(0, 10_000), 3, 3), (c, v: number[]) => (c.scatterPays = v)],
    ['freeSpins', listOf(intIn(0, 100), 3, 3), (c, v: number[]) => (c.freeSpins = v)],
    ['freeSpins.retrigger', intIn(0, 100), (c, v: number) => (c.retrigger = v)],
    ['freeSpins.cap', intIn(1, 500), (c, v: number) => (c.fsCap = v)],
    ['jackpot.refBet', intIn(5, 1_000_000), (c, v: number) => (c.jackpot.refBet = v)],
    ['jackpot.seed', mapOf(tiers, intIn(0, 100_000)), (c, v: MachineConfig['jackpot']['seed']) => (c.jackpot.seed = v)],
    ['jackpot.contribution', mapOf(tiers, numIn(0, 0.05)), (c, v: MachineConfig['jackpot']['contribution']) => (c.jackpot.contribution = v)],
    ['jackpot.owned', mapOf(tiers, intIn(0, 100_000)), (c, v: MachineConfig['jackpot']['owned']) => (c.jackpot.owned = v)],
  ];
  if (id === 'overworld') {
    out.push(['freeSpins.multiplier', intIn(1, 10), (c, v: number) => (c.fsMultiplier = v)]);
    out.push(['pick.board', intIn(3, 30), (c, v: number) => (c.pick!.board = v)]);
    out.push(['pick.weights', mapOf(PICK_KEYS.map((k) => k[0]), intIn(0, 10_000_000)), (c, v: Record<string, number>) => (c.pick!.weights = v)]);
  }
  if (id === 'nether') {
    out.push(['tumble.ladder', listOf(intIn(1, 100), 4, 4), (c, v: number[]) => (c.ladder = v)]);
    out.push(['tumble.ladderFree', listOf(intIn(1, 100), 4, 4), (c, v: number[]) => (c.ladderFree = v)]);
    out.push(['hold.trigger', intIn(3, 15), (c, v: number) => (c.hold!.trigger = v)]);
    out.push(['hold.respins', intIn(1, 10), (c, v: number) => (c.hold!.respins = v)]);
    out.push(['hold.coinChance', numIn(0, 0.5), (c, v: number) => (c.hold!.coinChance = v)]);
    out.push(['hold.coinWeights', mapOf(COIN_KEYS.map((k) => k[0]), intIn(0, 10_000_000)), (c, v: Record<string, number>) => (c.hold!.coinWeights = v)]);
  }
  if (id === 'end') {
    out.push(['wheel.outer', listOf(wheelToken(true), 4, 32), (c, v: string[]) => (c.wheel!.outer = v)]);
    out.push(['wheel.middle', listOf(wheelToken(true), 4, 32), (c, v: string[]) => (c.wheel!.middle = v)]);
    out.push(['wheel.core', listOf(wheelToken(false), 4, 32), (c, v: string[]) => (c.wheel!.core = v)]);
  }
  if (id !== 'overworld') out.push(['buy.price', fifthsIn(1, 10_000), (c, v: number) => (c.buyPrice = v)]);
  return out;
}

/** Every `slots.<m>.*` key this module reads (for the cut-over's CONFIG.md rows and v1-key warnings). */
export const machineConfigKeys = (id: MachineId): string[] => machineChecks(id).map(([k]) => `slots.${id}.${k}`);

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T;

/**
 * Read one machine's config: defaults overridden by every valid key the getter returns (undefined = not set).
 * Invalid values are reported in `errors` (`slots.<m>.<key>: why`) and the default is kept.
 */
export function readMachineConfig(id: MachineId, get: ConfigGetter): { config: MachineConfig; errors: string[] } {
  const config = clone(DEFAULT_CONFIG[id]);
  const errors: string[] = [];
  for (const [key, check, apply] of machineChecks(id)) {
    const raw = get(`slots.${id}.${key}`);
    if (raw === undefined) continue;
    const v = check(raw);
    if (typeof v === 'string') errors.push(`slots.${id}.${key}: ${v}`);
    else apply(config, v as never);
  }
  // cross-key rules
  if (!config.bets.includes(config.defaultBet)) {
    const near = config.bets.reduce((a, b) => (Math.abs(b - config.defaultBet) < Math.abs(a - config.defaultBet) ? b : a), config.bets[0]!);
    config.defaultBet = near;
  }
  for (const t of JACKPOT_TIERS) if (config.jackpot.seed[t] < 0) config.jackpot.seed[t] = 0;
  const w = config.pick ? Object.values(config.pick.weights).reduce((a, b) => a + b, 0) : 1;
  if (w <= 0 && config.pick) {
    errors.push(`slots.${id}.pick.weights: all weights are 0`);
    config.pick.weights = { ...DEFAULT_CONFIG[id].pick!.weights };
  }
  if (config.hold && Object.values(config.hold.coinWeights).reduce((a, b) => a + b, 0) <= 0) {
    errors.push(`slots.${id}.hold.coinWeights: all weights are 0`);
    config.hold.coinWeights = { ...DEFAULT_CONFIG[id].hold!.coinWeights };
  }
  return { config, errors };
}

const wheelCode = (t: string): number => (t === 'UP' ? 0 : t === 'MINI' ? -1 : t === 'MINOR' ? -2 : t === 'MAJOR' ? -3 : t === 'GRAND' ? -4 : Number(t));

/** Integer engine definition from a (validated) config. */
export function buildMachineDef(id: MachineId, c: MachineConfig = DEFAULT_CONFIG[id]): MachineDef {
  const syms = SYMBOLS[id];
  const codes = syms.map((s) => s.code);
  const strips = c.strips.map((s) =>
    s
      .trim()
      .split(/\s+/)
      .map((code) => {
        const i = codes.indexOf(code);
        if (i < 0) throw new Error(`unknown code ${code}`);
        return i;
      }),
  );
  const paysFifths = syms.map((s) => (s.role === 'PAY' ? (c.pays[s.id] ?? [0, 0, 0]).map((x) => toFifths(x) ?? 0) : [0, 0, 0]));
  const tiers = [0, ...JACKPOT_TIERS.map((t) => c.jackpot.seed[t] * c.jackpot.refBet)];
  const def: MachineDef = {
    machine: id,
    codes,
    roles: syms.map((s) => s.role),
    strips,
    paysFifths,
    scatterFifths: [c.scatterPays[0]! * 5, c.scatterPays[1]! * 5, c.scatterPays[2]! * 5],
    bonusReelsMask: BONUS_REELS_MASK[id],
    freeSpins: [c.freeSpins[0]!, c.freeSpins[1]!, c.freeSpins[2]!],
    retrigger: c.retrigger,
    fsCap: c.fsCap,
    fsMultiplier: c.fsMultiplier ?? 1,
    ladder: c.ladder ?? [],
    ladderFree: c.ladderFree ?? [],
    capMultiple: c.maxWinMultiple,
    buyPriceFifths: c.buyPrice === undefined ? 0 : (toFifths(c.buyPrice) ?? 0),
    jackpot: {
      ref: c.jackpot.refBet,
      seeds: tiers,
      contribution: [0, ...JACKPOT_TIERS.map((t) => c.jackpot.contribution[t])],
      owned: [0, ...JACKPOT_TIERS.map((t) => c.jackpot.owned[t])],
    },
  };
  if (c.pick) def.hunt = { board: c.pick.board, values: PICK_KEYS.map((k) => k[1]), weights: PICK_KEYS.map((k) => c.pick!.weights[k[0]] ?? 0) };
  if (c.hold)
    def.hoard = {
      trigger: c.hold.trigger,
      respins: c.hold.respins,
      chanceMicro: Math.round(c.hold.coinChance * 1_000_000),
      values: COIN_KEYS.map((k) => k[1]),
      weights: COIN_KEYS.map((k) => c.hold!.coinWeights[k[0]] ?? 0),
    };
  if (c.wheel) def.wheel = { rings: [c.wheel.outer.map(wheelCode), c.wheel.middle.map(wheelCode), c.wheel.core.map(wheelCode)] };
  return def;
}

const cache = new Map<MachineId, MachineDef>();
/** Default definition (cached). */
export function defaultMachine(id: MachineId): MachineDef {
  let d = cache.get(id);
  if (!d) cache.set(id, (d = buildMachineDef(id)));
  return d;
}

/** Bet ladder offered to a player (SLOTS.md §6.1): `≤ min(VIP max, owner max)` and `≥ owner min`. */
export function offeredBets(bets: readonly number[], vipMax: number, ownerMin = 0, ownerMax = Number.MAX_SAFE_INTEGER): number[] {
  return bets.filter((b) => b <= Math.min(vipMax, ownerMax) && b >= ownerMin);
}

/** v1 keys that are ignored after the cut-over (SLOTS.md §11, §12 "Removed"): one warning lists them. */
export const REMOVED_V1_KEYS: readonly string[] = [
  'slots.copper.maxLineBet',
  'slots.gold.maxLineBet',
  'slots.netherite.minLineBet',
  'slots.netherite.maxLineBet',
  'slots.netherite.minVipTier',
  'slots.copper.weights',
  'slots.gold.weights',
  'slots.netherite.weights',
  'slots.copper.pays',
  'slots.gold.pays',
  'slots.netherite.pays',
  'slots.copper.berryPartial',
  'slots.gold.berryPartial',
  'slots.netherite.berryPartial',
  'slots.jackpot.contribution.gold',
  'slots.jackpot.contribution.netherite',
  'slots.jackpot.seed.gold',
  'slots.jackpot.seed.netherite',
  'slots.ownedStarPays',
  'slots.spinTicks',
  'pvp.slots.starPoints',
];
