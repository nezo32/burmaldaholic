/**
 * Round lifecycle maths of the v2 service (SLOTS.md §1.2, §6, §8): the persisted record, settlement split
 * (wager return vs pool money), the streak re-draw rule, chaos mapping (§8.4), advancements (§14), autoplay stop
 * rules (§6.4), statistics (§8.8), buy-feature limits (§6.3) and the global `slots.*` keys (§12). PURE.
 * Lanes S-B5 / S-B7.
 */
import { evaluateSpin, featureTriggered, freeSpinsPlayed, huntOpens, runTumbles } from './engine';
import { TOP_SYMBOL } from './machines';
import { slotTier } from './tiers';
import { type MachineDef, type SpinTape, TAPE_VERSION, tapePoolChips, tapeTotalChips } from './types';
import type { ConfigGetter } from './config';

// ---- persisted record (SLOTS.md §8.1) ---------------------------------------------------------------

/** `{v:2, machine, bet, price?, tape, total, jackpotAwards[], startTick, opened?}` + bookkeeping. */
export interface RoundRecord {
  v: 2;
  machine: string;
  playerId: string;
  bet: number;
  /** buy price (chips) when bought */
  price?: number;
  /** `encodeTape` string */
  tape: string;
  /** wager return in chips (spin total excl. progressive awards) */
  total: number;
  /** progressive awards (pool money), paid next to the wager settlement */
  jackpotAwards: Array<{ tier: number; chips: number }>;
  startTick: number;
  /** reveal gate tick (startTick + shared timeline length) */
  gateTick: number;
  /** Treasure Hunt picks made so far (the i-th pick reveals entry i) */
  opened?: number;
  /** wager ticket id (the drawn ticket core settles after a restart) */
  ticket: string;
}

export const isRoundRecord = (v: unknown): v is RoundRecord => {
  const r = v as Partial<RoundRecord> | undefined;
  return !!r && r.v === TAPE_VERSION && typeof r.tape === 'string' && typeof r.playerId === 'string' && typeof r.total === 'number' && Array.isArray(r.jackpotAwards);
};

export interface Settlement {
  /** chips staked (bet or buy price) */
  stake: number;
  /** credited through `wagers.settle` (Golden Hour / streak / VIP see this) */
  wagerReturn: number;
  /** progressive pool money credited separately (excluded from Golden Hour, SLOTS.md §8.3) */
  poolChips: number;
}

export function settlement(def: MachineDef, tape: SpinTape): Settlement {
  return { stake: tape.bought ? (def.buyPriceFifths * tape.bet) / 5 : tape.bet, wagerReturn: tapeTotalChips(tape), poolChips: tapePoolChips(tape) };
}

/** Streak re-draw (SLOTS.md §8.2): the whole spin counts; only a total return below the bet is "losing". */
export const isLosingSpin = (tape: SpinTape): boolean => tapeTotalChips(tape) + tapePoolChips(tape) < tape.bet;

// ---- chaos (SLOTS.md §8.4) -------------------------------------------------------------------------

export type SlotChaos =
  | { event: 'jackpot'; tier: number }
  | { event: 'chip_shower'; tier: number }
  | { event: 'golden_hour' }
  | { event: 'mob_wave'; flavour: 'creeper_friends' | 'wither_skulls' }
  | { event: 'lucky_buff'; flavour: 'tumble_chain'; tumbles: number }
  | { event: 'random_teleport'; flavour: 'dragon_fling' }
  | { event: 'xp_fountain'; flavour: 'void_walker' };

/** Tumbles of the base spin (0 for non-tumbling machines or a bought feature). */
export function baseTumbles(def: MachineDef, tape: SpinTape): number {
  if (tape.bought || def.ladder.length === 0) return 0;
  return runTumbles(def, tape.stops, def.ladder).steps.length - 1;
}

/** 5 of the machine's top symbol in the base spin (any tumble step). */
export function topFive(def: MachineDef, tape: SpinTape): boolean {
  if (tape.bought) return false;
  const top = TOP_SYMBOL[def.machine];
  const e = evaluateSpin(def, tape.stops, false);
  const steps = e.chain ? e.chain.steps.map((s) => s.result) : [e.ways!];
  return steps.some((r) => r.wins.some((w) => w.symbol === top && w.k === 5));
}

/** Scatters on the final base window. */
export const baseScatters = (def: MachineDef, tape: SpinTape): number => (tape.bought ? 0 : evaluateSpin(def, tape.stops, false).scatters);

/** At most one event per spin, by priority (the big-win rule, priority 5, is chaos's own onSettled rule). */
export function chaosFor(def: MachineDef, tape: SpinTape): SlotChaos | undefined {
  const tiers = tape.jackpots.map((j) => j.tier);
  const top = Math.max(0, ...tiers);
  if (top >= 3) return { event: 'jackpot', tier: top };
  if (top >= 1) return { event: 'chip_shower', tier: top };
  if (!tape.bought && baseScatters(def, tape) >= 5 && tape.freeSpins) return { event: 'golden_hour' };
  if (def.machine === 'overworld' && tape.hunt && tape.hunt.entries[0] === 0) return { event: 'mob_wave', flavour: 'creeper_friends' };
  if (def.machine === 'nether' && !tape.bought) {
    if (topFive(def, tape)) return { event: 'mob_wave', flavour: 'wither_skulls' };
    const n = baseTumbles(def, tape);
    if (n >= 6) return { event: 'lucky_buff', flavour: 'tumble_chain', tumbles: n };
  }
  if (def.machine === 'end') {
    if (topFive(def, tape)) return { event: 'random_teleport', flavour: 'dragon_fling' };
    const last = tape.freeSpins?.spins.at(-1);
    if (last && last.stickyMaskAfter === 7) return { event: 'xp_fountain', flavour: 'void_walker' };
  }
  return undefined;
}

// ---- advancements (SLOTS.md §14) ---------------------------------------------------------------------

/** Advancement ids a settled (non-PvP) spin earns. */
export function achievementsFor(def: MachineDef, tape: SpinTape, bigWinTiers?: readonly [number, number, number, number]): string[] {
  const out: string[] = [];
  if (topFive(def, tape)) out.push('top_five');
  if (tape.jackpots.length) out.push('mini_jackpot');
  if (tape.jackpots.some((j) => j.tier >= 3)) out.push('jackpot');
  if (tape.freeSpins && !tape.bought) out.push('free_spins');
  if (tape.hunt && huntOpens(def, tape) >= 10 && tape.hunt.entries.slice(0, 10).every((e) => e !== 0)) out.push('treasure_hunter');
  if (baseTumbles(def, tape) >= 6) out.push('tumble_six');
  if (tape.hoard && tape.hoard.initialCells.length + tape.hoard.respinCells.flat().length >= 15) out.push('hoard_full');
  if (tape.freeSpins?.spins.some((s) => s.stickyMaskAfter === 7)) out.push('void_walker');
  if (tape.wheel && tape.wheel.segments.length >= 3) out.push('dragon_core');
  const tier = slotTier(tape.totalFifths, bigWinTiers);
  if (tier === 'EPIC') out.push('epic_win');
  if (tape.capHit) out.push('max_win');
  return out;
}

// ---- autoplay (SLOTS.md §6.4) ---------------------------------------------------------------------

export interface AutoplayState {
  left: number;
  startBalance: number;
  /** × bet */
  lossLimit: number;
  stopOnFeature: boolean;
  /** × bet, 0 = off */
  stopOnWin: number;
  bet: number;
}

export type AutoStop = 'jackpot' | 'max_win' | 'feature' | 'big_win' | 'loss' | 'funds' | 'done';

/** Checked after each settled spin; `balance` = balance after the settlement, `nextStake` = next spin's stake. */
export function autoplayStop(s: AutoplayState, tape: SpinTape, balance: number, nextStake: number): AutoStop | undefined {
  if (tape.jackpots.length) return 'jackpot';
  if (tape.capHit) return 'max_win';
  if (s.stopOnFeature && featureTriggered(tape)) return 'feature';
  const win = tapeTotalChips(tape);
  if (s.stopOnWin > 0 && win >= s.stopOnWin * s.bet) return 'big_win';
  if (s.startBalance - balance >= s.lossLimit * s.bet) return 'loss';
  if (balance < nextStake) return 'funds';
  if (s.left <= 0) return 'done';
  return undefined;
}

// ---- statistics (SLOTS.md §8.8) --------------------------------------------------------------------

export interface MachineStats {
  spins: number;
  wagered: number;
  returned: number;
  features: number;
  /** best spin, fifths of the bet */
  bestFifths: number;
  /** jackpots won per tier 1..4 (index 0 unused) */
  jackpots: number[];
}

export const emptyStats = (): MachineStats => ({ spins: 0, wagered: 0, returned: 0, features: 0, bestFifths: 0, jackpots: [0, 0, 0, 0, 0] });

export function addStats(s: MachineStats | undefined, def: MachineDef, tape: SpinTape): MachineStats {
  const out = s ? { ...s, jackpots: s.jackpots.slice() } : emptyStats();
  const st = settlement(def, tape);
  out.spins++;
  out.wagered += st.stake;
  out.returned += st.wagerReturn + st.poolChips;
  if (featureTriggered(tape)) out.features++;
  out.bestFifths = Math.max(out.bestFifths, tape.totalFifths);
  for (const j of tape.jackpots) out.jackpots[j.tier] = (out.jackpots[j.tier] ?? 0) + 1;
  return out;
}

// ---- buy feature limits (SLOTS.md §6.3) ------------------------------------------------------------

export function buyError(def: MachineDef, bet: number, enabled: boolean, ownerAllows: boolean, tierMax: number, tierMaxMultiple: number, ladder: readonly number[]): 'buy_disabled' | 'bet_unavailable' | 'buy_limit' | undefined {
  if (!enabled || !ownerAllows || def.buyPriceFifths <= 0) return 'buy_disabled';
  if (!ladder.includes(bet)) return 'bet_unavailable';
  if ((def.buyPriceFifths * bet) / 5 > tierMax * tierMaxMultiple) return 'buy_limit';
  return undefined;
}

// ---- global keys (SLOTS.md §12) ---------------------------------------------------------------------

export interface SlotsGlobalConfig {
  enabled: boolean;
  buyEnabled: boolean;
  buyTierMaxMultiple: number;
  autoplayEnabled: boolean;
  autoplayCounts: number[];
  autoplayLossLimits: number[];
  turboAllowed: boolean;
  anticipation: boolean;
  bigWinTiers: [number, number, number, number];
  announceMinTier: number;
  inWorldEnabled: boolean;
  inWorldRadius: number;
  ddui: boolean;
  validateRtp: boolean;
}

export const GLOBAL_DEFAULTS: SlotsGlobalConfig = {
  enabled: true,
  buyEnabled: true,
  buyTierMaxMultiple: 25,
  autoplayEnabled: true,
  autoplayCounts: [10, 25, 50, 100],
  autoplayLossLimits: [10, 25, 50, 100],
  turboAllowed: true,
  anticipation: true,
  bigWinTiers: [5, 15, 40, 100],
  announceMinTier: 3,
  inWorldEnabled: true,
  inWorldRadius: 24,
  ddui: true,
  validateRtp: true,
};

const TIER_NAMES = ['MINI', 'MINOR', 'MAJOR', 'GRAND'];

export function readGlobalConfig(get: ConfigGetter): SlotsGlobalConfig {
  const c: SlotsGlobalConfig = { ...GLOBAL_DEFAULTS, autoplayCounts: [...GLOBAL_DEFAULTS.autoplayCounts], autoplayLossLimits: [...GLOBAL_DEFAULTS.autoplayLossLimits], bigWinTiers: [...GLOBAL_DEFAULTS.bigWinTiers] };
  const b = (k: string, f: (v: boolean) => void): void => {
    const v = get(k);
    if (typeof v === 'boolean') f(v);
  };
  const i = (k: string, lo: number, hi: number, f: (v: number) => void): void => {
    const v = get(k);
    if (typeof v === 'number' && Number.isInteger(v) && v >= lo && v <= hi) f(v);
  };
  const l = (k: string, lo: number, hi: number, n: [number, number], f: (v: number[]) => void): void => {
    const v = get(k);
    if (Array.isArray(v) && v.length >= n[0] && v.length <= n[1] && v.every((x) => typeof x === 'number' && Number.isInteger(x) && x >= lo && x <= hi)) f(v as number[]);
  };
  b('slots.enabled', (v) => (c.enabled = v));
  b('slots.buyFeature.enabled', (v) => (c.buyEnabled = v));
  i('slots.buyFeature.tierMaxMultiple', 1, 1000, (v) => (c.buyTierMaxMultiple = v));
  b('slots.autoplay.enabled', (v) => (c.autoplayEnabled = v));
  l('slots.autoplay.counts', 1, 1000, [1, 8], (v) => (c.autoplayCounts = v));
  l('slots.autoplay.lossLimits', 1, 10_000, [1, 8], (v) => (c.autoplayLossLimits = v));
  b('slots.turboAllowed', (v) => (c.turboAllowed = v));
  b('slots.anticipation', (v) => (c.anticipation = v));
  l('slots.bigWinTiers', 1, 10_000, [4, 4], (v) => {
    if (v[0]! < v[1]! && v[1]! < v[2]! && v[2]! < v[3]!) c.bigWinTiers = [v[0]!, v[1]!, v[2]!, v[3]!];
  });
  const a = get('slots.jackpot.announceMinTier');
  if (typeof a === 'string' && TIER_NAMES.includes(a)) c.announceMinTier = TIER_NAMES.indexOf(a) + 1;
  b('slots.inWorld.enabled', (v) => (c.inWorldEnabled = v));
  i('slots.inWorld.radius', 0, 64, (v) => (c.inWorldRadius = v));
  b('slots.bedrock.ddui', (v) => (c.ddui = v));
  b('slots.validateRtp', (v) => (c.validateRtp = v));
  return c;
}

/** Free spins played (statistics / Showdown). */
export { freeSpinsPlayed };
