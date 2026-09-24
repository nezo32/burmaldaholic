/**
 * Bedrock celebration plan (global.md §2.6 Bedrock column, ⚠ CHANGED by lead decision L2: the caller supplies its
 * tier words, threshold table and stems; docs/architecture/animation.md §4). PURE.
 *
 * `buildCelebration(request, profile)` turns a server-decided result into a LOCAL `Timeline` (group 0, skippable)
 * that the runtime (`core/presentation/fx.ts`) plays through the shared scheduler. `celebrationFrame` samples it;
 * the terminal frame is always the exact server amount and the server tier word (fidelity: global §6.2, §6.9).
 *
 * Bedrock storyboard per tier (title fade in / stay / out in ticks, roll-up length, particles, camera, form delay):
 *
 * | tier    | presentation                    | roll-up | particles                         | camera                    | form |
 * |---------|---------------------------------|---------|-----------------------------------|---------------------------|------|
 * | LOSS    | action bar, red word            | —       | —                                 | —                         | 12 t |
 * | PUSH    | action bar, gray word           | —       | —                                 | —                         | 12 t |
 * | RETURN  | action bar "Returned N", muted  | —       | —                                 | —                         | 12 t |
 * | WIN     | action bar `+N`                 | —       | `chip_pop`                        | —                         | 20 t |
 * | NICE    | action bar word + `+N`          | —       | `sparkle` 12                      | —                         | 30 t |
 * | BIG     | title 3/36/8 + subtitle roll-up | 20 t    | `chip_fountain` 20                | —                         | 40 t |
 * | MEGA    | title 3/44/10                   | 24 t    | `chip_fountain` 30 + `sparkle` 16 | gold fade 0.1/0.1/0.3 s   | 50 t |
 * | EPIC    | title 3/52/10                   | 30 t    | `chip_fountain` 40 + `gold_burst` | fade + shake 0.15 × 0.4 s | 60 t |
 * | JACKPOT | title 0/60/12, `×M` suffix      | 36 t    | `gold_burst` + fountain 60 + sparks | fade 0.1/0.2/0.5 + shake 0.2 × 0.6 | 60 t |
 *
 * JACKPOT with a sub-tier (slots.md §4.11) uses the Mini/Minor/Major/Grand roll-up and total lengths.
 * Overlay tiers start at the lowest overlay word of the caller's table and upgrade when the rolling amount passes
 * the table's thresholds (`upgradePoints`); the word always ends on the server tier.
 *
 * Settings (global.md §2.8): speed scales every local duration; reduce motion = roll-up ≤ 300 ms in 2 steps, no
 * camera, particle counts × 0.3, no count ticks; flashes off = no fade, no firework sparks; celebrations `off` = the
 * WIN presentation (action bar, no particles/camera) with the real tier word and amount (information is never
 * behind a toggle).
 */
import { ceilTicks, LOCAL, scaleMs, Timeline, type TimingProfile } from './timeline';
import { rollUpValue } from './rollup';
import { CORE_TIER_WORDS, DEFAULT_TIERS, type TierWords, type WinTier, type WinTierTable, isOverlayTier, tierOrdinal, upgradePoints, WIN_TIERS } from './win-tier';

/** Fanfare stem per tier (catalog sound ids without the namespace; undefined = silent). Twin of Java `TierStems`. */
export type TierStems = Readonly<Partial<Record<WinTier, string>>> & { readonly tick?: string };

export const CORE_TIER_STEMS: TierStems = {
  WIN: 'win_small',
  NICE: 'win_nice',
  BIG: 'win_big',
  MEGA: 'win_mega',
  EPIC: 'win_mega',
  JACKPOT: 'jackpot',
  RETURN: 'push',
  PUSH: 'push',
  tick: 'chip_count',
};

/** Input of `fx.celebrate` (twin of Java `CelebrationRequest`). */
export interface CelebrationRequest {
  /** Final tier, computed by the server (`winTierOf`); never re-derived here. */
  readonly tier: WinTier;
  /** Total return the roll-up counts to (exact on the last frame). */
  readonly ret: number;
  /** Stake the multiples refer to. */
  readonly stake: number;
  /** Game id (timeline name; `slots.nether`, `roulette` …). */
  readonly game: string;
  /** Cosmetic seed (public values only, `seedMix`). */
  readonly seed: number;
  /** Threshold table for the upgrade beats (default `DEFAULT_TIERS`). */
  readonly table?: WinTierTable;
  /** Lang keys per tier (default `CORE_TIER_WORDS`; slots pass `SLOT_TIER_WORDS`). */
  readonly words?: TierWords;
  /** Sound stems per tier (default `CORE_TIER_STEMS`; slots pass their `slots.*` ids). */
  readonly stems?: TierStems;
  /** Jackpot sub-tier: 0 none, 1 Mini … 4 Grand. */
  readonly subTier?: number;
  /** Lang key of the sub-tier word, passed as %1 to the JACKPOT word (`%1 JACKPOT!`). */
  readonly subTierWord?: string;
  /** Show the MAX WIN plate word first (`words.maxWin`). */
  readonly maxWin?: boolean;
}

/** The viewer's profile for a celebration (FX settings, global.md §2.8). */
export interface CelebrationProfile extends TimingProfile {
  readonly celebrations: 'all' | 'mine' | 'off';
}

/** Particle ids used by the kit (core-owned, `packs/core/RP/particles`). Index = beat arg. */
export const CEL_PARTICLES = ['burmaldaholic:chip_pop', 'burmaldaholic:chip_fountain', 'burmaldaholic:sparkle', 'burmaldaholic:gold_burst', 'minecraft:totem_particle'] as const;
export const PARTICLE_CHIP_POP = 0;
export const PARTICLE_FOUNTAIN = 1;
export const PARTICLE_SPARKLE = 2;
export const PARTICLE_GOLD_BURST = 3;
/** Vanilla colourful burst standing in for firework sparks (dropped when flashes are off). */
export const PARTICLE_SPARKS = 4;

/** Beat kinds of a celebration timeline. */
export const CEL_BEAT = {
  /** args [finalTier, startWord, mode (0 action bar, 1 title), fadeIn t, stay t, fadeOut t] */
  START: 'fx.cel.start',
  /** dur = roll-up length; the amount is sampled with `rollUpValue` */
  ROLL: 'fx.cel.roll',
  /** word upgrade; args [tier] */
  WORD: 'fx.cel.word',
  /** fanfare stem; args [tier] */
  STEM: 'fx.cel.stem',
  /** count tick; args [n] (pitch = tickPitch(n)) */
  TICK: 'fx.cel.tick',
  /** particles; args [particle index, count] */
  BURST: 'fx.cel.burst',
  /** camera fade; args [r, g, b (0–255), in ms, hold ms, out ms] */
  FADE: 'fx.cel.fade',
  /** camera shake; args [intensity × 100, ms] */
  SHAKE: 'fx.cel.shake',
  /** the result form may open (terminal) */
  END: 'fx.cel.end',
} as const;

export const MODE_ACTIONBAR = 0;
export const MODE_TITLE = 1;

interface TierStyle {
  readonly fade: readonly [number, number, number];
  readonly rollTicks: number;
  readonly formTicks: number;
  readonly particles: ReadonlyArray<readonly [number, number]>;
  readonly fadeMs?: readonly [number, number, number];
  readonly shake?: readonly [number, number];
}

const NONE: TierStyle = { fade: [0, 0, 0], rollTicks: 0, formTicks: 12, particles: [] };
/** Bedrock storyboard (global.md §2.6); indexed by tier. */
const STYLE: Readonly<Record<WinTier, TierStyle>> = {
  LOSS: NONE,
  PUSH: NONE,
  RETURN: NONE,
  WIN: { fade: [0, 0, 0], rollTicks: 0, formTicks: 20, particles: [[PARTICLE_CHIP_POP, 8]] },
  NICE: { fade: [0, 0, 0], rollTicks: 0, formTicks: 30, particles: [[PARTICLE_SPARKLE, 12]] },
  BIG: { fade: [3, 36, 8], rollTicks: 20, formTicks: 40, particles: [[PARTICLE_FOUNTAIN, 20]] },
  MEGA: { fade: [3, 44, 10], rollTicks: 24, formTicks: 50, particles: [[PARTICLE_FOUNTAIN, 30], [PARTICLE_SPARKLE, 16]], fadeMs: [100, 100, 300] },
  EPIC: { fade: [3, 52, 10], rollTicks: 30, formTicks: 60, particles: [[PARTICLE_FOUNTAIN, 40], [PARTICLE_GOLD_BURST, 30]], fadeMs: [100, 100, 300], shake: [15, 400] },
  JACKPOT: {
    fade: [0, 60, 12],
    rollTicks: 36,
    formTicks: 60,
    particles: [[PARTICLE_GOLD_BURST, 40], [PARTICLE_FOUNTAIN, 60], [PARTICLE_SPARKS, 12]],
    fadeMs: [100, 200, 500],
    shake: [20, 600],
  },
};

/** Jackpot sub-tiers Mini … Grand (slots.md §4.11): roll-up ms, total ms. */
const JACKPOT_SUB: ReadonlyArray<readonly [number, number]> = [
  [1000, 2000],
  [1400, 2500],
  [2000, 3000],
  [3000, 4000],
];

/** Gold `#FFD640` (global.md §2.1) for camera fades. */
export const FADE_GOLD: readonly [number, number, number] = [0xff, 0xd6, 0x40];

/** Most particles any one burst may request (global.md §2.9). */
export const MAX_BURST = 60;
/** Reduce motion: roll-ups ≤ 300 ms, particles × 0.3. */
export const REDUCED_ROLL_MS = 300;

const clampCount = (n: number, reduce: boolean): number => Math.max(1, Math.min(MAX_BURST, reduce ? Math.round(n * 0.3) : n));

/** Lowest overlay word the table knows (NICE for slots, BIG for the default table). */
export function startWord(tier: WinTier, table: WinTierTable): WinTier {
  if (!isOverlayTier(tier)) return tier;
  const first: WinTier = table.multiples[0] > 0 ? 'NICE' : 'BIG';
  return tierOrdinal(first) < tierOrdinal(tier) ? first : tier;
}

/** Smallest ms in [0, rollMs] at which the shown amount reaches `amount` (the roll-up is monotonic). */
export function reachMs(ret: number, rollMs: number, amount: number): number {
  if (rollMs <= 0 || amount <= 0) return 0;
  if (amount > ret) return rollMs;
  let lo = 0;
  let hi = rollMs;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (rollUpValue(ret, mid / rollMs) >= amount) hi = mid;
    else lo = mid + 1;
  }
  return lo;
}

const ticksMs = (p: TimingProfile, ticks: number): number => scaleMs(p, ticks * 50);
const scaledTicks = (p: TimingProfile, ticks: number): number => (ticks <= 0 ? 0 : Math.max(1, ceilTicks(scaleMs(p, ticks * 50))));

/**
 * Builds the celebration timeline. Every beat is LOCAL (group 0): the viewer's speed applies and sneaking skips to
 * the end. The server-side reveal gate is the caller's business (games call this AFTER their gate).
 */
export function buildCelebration(req: CelebrationRequest, profile: CelebrationProfile): Timeline {
  const table = req.table ?? DEFAULT_TIERS;
  const off = profile.celebrations === 'off';
  const reduce = profile.reduceMotion;
  const flashes = profile.flashes && !reduce;
  const tier = req.tier;
  const style = STYLE[tier];
  const title = !off && isOverlayTier(tier);
  const b = Timeline.builder(`fx.celebrate.${req.game}`, req.seed).clock(LOCAL).group(0);

  // lengths (ms, speed-scaled)
  let rollMs = title ? ticksMs(profile, style.rollTicks) : 0;
  let totalMs = ticksMs(profile, off && isOverlayTier(tier) ? STYLE.WIN.formTicks : style.formTicks);
  const sub = req.subTier ?? 0;
  if (tier === 'JACKPOT' && sub >= 1 && sub <= 4 && !off) {
    const [r, tot] = JACKPOT_SUB[sub - 1]!;
    rollMs = scaleMs(profile, r);
    totalMs = scaleMs(profile, tot);
  }
  if (reduce) rollMs = Math.min(rollMs, REDUCED_ROLL_MS);
  totalMs = Math.max(totalMs, rollMs);

  const first = title ? startWord(tier, table) : tier;
  const fade = title ? style.fade : ([0, 0, 0] as const);
  const stay = title ? Math.max(scaledTicks(profile, fade[1]), ceilTicks(totalMs) - scaledTicks(profile, fade[0])) : 0;
  b.add(0, 0, CEL_BEAT.START, -1, tierOrdinal(tier), tierOrdinal(first), title ? MODE_TITLE : MODE_ACTIONBAR, scaledTicks(profile, fade[0]), stay, scaledTicks(profile, fade[2]));
  if (rollMs > 0) b.add(0, rollMs, CEL_BEAT.ROLL, -1);

  // words and the moments each tier is reached (upgrade beats)
  const reached: Array<[WinTier, number]> = [[first, 0]];
  if (title) for (const [t, amount] of upgradePoints(table, req.stake, first, tier)) reached.push([t, reachMs(req.ret, rollMs, amount)]);
  // the final word is the server tier even if its threshold lies beyond the return (e.g. a net-floor rule)
  if (reached[reached.length - 1]![0] !== tier) reached.push([tier, rollMs]);
  const stems = req.stems ?? CORE_TIER_STEMS;
  reached.forEach(([t, at], i) => {
    if (i > 0) b.add(at, 0, CEL_BEAT.WORD, -1, tierOrdinal(t));
    const stemTier: WinTier = off && isOverlayTier(t) ? 'WIN' : t;
    if (stems[stemTier] && (i === 0 || !off)) b.add(at, 0, CEL_BEAT.STEM, -1, tierOrdinal(stemTier));
    if (off) return;
    // particles of the tier the word just reached (the final tier's set lands on its upgrade)
    const set = i === 0 && title ? STYLE[t].particles.slice(0, 1) : STYLE[t].particles;
    for (const [pi, count] of set) {
      if (pi === PARTICLE_SPARKS && !flashes) continue;
      b.add(at, 0, CEL_BEAT.BURST, -1, pi, clampCount(count, reduce));
    }
  });

  // count ticks (BIG+ only, 5/s, not with reduce motion or celebrations off)
  if (title && !reduce && stems.tick && rollMs > 0) {
    let n = 0;
    for (let at = 200; at < rollMs; at += 200) b.add(at, 0, CEL_BEAT.TICK, -1, n++);
  }

  // camera at the moment the final tier is reached
  const finalAt = reached[reached.length - 1]![1];
  if (title && flashes && style.fadeMs) {
    const [i, h, o] = style.fadeMs;
    b.add(finalAt, 0, CEL_BEAT.FADE, -1, FADE_GOLD[0], FADE_GOLD[1], FADE_GOLD[2], i, h, o);
  }
  if (title && !reduce && style.shake) b.add(finalAt, 0, CEL_BEAT.SHAKE, -1, style.shake[0], style.shake[1]);

  b.add(totalMs, 0, CEL_BEAT.END, -1);
  return b.build();
}

/** What a celebration shows at time `t` (pure frame sampler). */
export interface CelebrationFrame {
  /** Amount shown (exact `ret` at the end). */
  readonly amount: number;
  /** Tier word shown. */
  readonly word: WinTier;
  readonly done: boolean;
}

export function celebrationFrame(req: CelebrationRequest, tl: Timeline, t: number): CelebrationFrame {
  const end = tl.endMs();
  if (t >= end) return celebrationTerminal(req);
  let word: WinTier = WIN_TIERS[0]!;
  let amount = req.ret;
  for (const b of tl.beats) {
    if (b.kind === CEL_BEAT.START) word = WIN_TIERS[b.args[1]!]!;
    else if (b.kind === CEL_BEAT.WORD && b.at <= t) word = WIN_TIERS[b.args[0]!]!;
    else if (b.kind === CEL_BEAT.ROLL) amount = t >= b.at + b.dur ? req.ret : rollUpValue(req.ret, Math.max(0, t - b.at) / b.dur);
  }
  return { amount, word, done: false };
}

export const celebrationTerminal = (req: CelebrationRequest): CelebrationFrame => ({ amount: req.ret, word: req.tier, done: true });

/** Lang key of a tier word from the caller's words (defaults to core). */
export const tierWordKey = (words: TierWords | undefined, tier: WinTier): string => (words ?? CORE_TIER_WORDS)[tier];
