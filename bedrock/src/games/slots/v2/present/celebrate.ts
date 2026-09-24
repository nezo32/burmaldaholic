/**
 * Slot big wins and jackpots on Bedrock (animation/slots.md §2.5, §4.11–§4.13, §6.2 "Jackpots in the form";
 * SLOTS.md §10.1; task BS5; lane B-L9).
 *
 * - Tier words are the SLOTS keys (D4): NICE / BIG / MEGA / EPIC WIN!, MAX WIN!, "%1 JACKPOT!".
 * - The word starts at Nice and upgrades as the rolling amount passes 15× / 40× / 100× the bet; the final word
 *   always equals the server tier (never re-derived here: the tier comes with the round).
 * - Big+ also get a title + `updateSubtitle` roll-up (the core `fx.celebrate` when lane B-L1's FxService is
 *   installed, else the fallback below), Mega+ a gold `camera.fade`, Epic a `camerashake` — both skipped by
 *   reduce motion / flashes off (camera helpers honour the settings).
 * - Major / Grand jackpots: a cinematic — the form closes, gold fade, the camera eases to the cabinet front,
 *   title + subtitle roll-up, particles, (Grand) shake, camera clear, the form re-opens (§6.2 table).
 *   Reduce motion: titles only. Mini / Minor stay in the form (header + meter flash).
 *
 * No @minecraft runtime import: the runtime primitives are injected (`CelebrateRuntime`), so the schedule is
 * unit-tested with a fake clock.
 */
import type { Player } from '@minecraft/server';
import { rollUpDurationMs, rollUpValue } from '../../../../core/logic/anim/rollup';
import { ceilTicks } from '../../../../core/logic/anim/timeline';
import { type WinTier, type WinTierTable, tierOrdinal, upgradePoints } from '../../../../core/logic/anim/win-tier';
import { type Raw, chips, join, lit, t } from '../../../../core/logic/rawtext';
import { SLOT_TIER_TABLE, SLOT_TIER_WORDS } from '../logic/tiers';

// ---------------------------------------------------------------------------------------------------------
// Pure model
// ---------------------------------------------------------------------------------------------------------

export const TIER_WORD_KEYS = SLOT_TIER_WORDS;

const PNS = 'burmaldaholic:';
/**
 * Particle ids (same ids as Java, slots.md §9.3). Definitions: core lane B-L1 (`coin_burst`, `confetti`,
 * `sparkle`, `jackpot_burst`) and slots lane B-L10 (`ember_burst`, `void_motes`); spawning an undefined one is
 * a no-op (caught), so the form works before the assets land.
 */
export const SLOT_PARTICLE = {
  coin: PNS + 'coin_burst',
  confetti: PNS + 'confetti',
  sparkle: PNS + 'sparkle',
  jackpot: PNS + 'jackpot_burst',
  ember: PNS + 'ember_burst',
  voidMotes: PNS + 'void_motes',
} as const;

/** Colour of a tier word (the header alternates it with `§e§l`, a colour change, not a flash). */
export function tierColor(tier: WinTier): string {
  switch (tier) {
    case 'EPIC':
      return '§d§l';
    case 'MEGA':
      return '§c§l';
    case 'BIG':
      return '§6§l';
    default:
      return '§a§l';
  }
}

export interface SlotCelebration {
  /** server tier of the whole spin (excluding progressive awards) */
  readonly tier: WinTier;
  readonly total: number;
  readonly bet: number;
  /** the word shown at the first frame */
  readonly startTier: WinTier;
  /** `[tier, amount]`: the word upgrades when the rolling amount reaches `amount` */
  readonly upgrades: ReadonlyArray<readonly [WinTier, number]>;
  readonly rollupMs: number;
  readonly maxWin: boolean;
  /** Big+ (title / overlay); Nice is the in-form banner only */
  readonly overlay: boolean;
}

/** PURE: the celebration of a settled spin. `tier` is the server's (SLOTS.md §10.1 table), never recomputed. */
export function slotCelebration(total: number, bet: number, tier: WinTier, maxWin: boolean, table: WinTierTable = SLOT_TIER_TABLE): SlotCelebration {
  const nice = tierOrdinal(tier) >= tierOrdinal('NICE') && tier !== 'JACKPOT';
  const startTier: WinTier = nice ? 'NICE' : tier;
  const upgrades = nice ? upgradePoints(table, bet, 'NICE', tier) : [];
  return {
    tier,
    total,
    bet,
    startTier,
    upgrades,
    rollupMs: total <= 0 ? 0 : rollUpDurationMs(total, bet, 600, 8000),
    maxWin,
    overlay: tierOrdinal(tier) >= tierOrdinal('BIG') && tier !== 'JACKPOT',
  };
}

/** Word for a rolled amount: start tier, then each upgrade reached; the exact total shows the server tier. */
export function tierWordAt(c: SlotCelebration, shown: number): WinTier {
  if (shown >= c.total) return c.tier;
  let w = c.startTier;
  for (const [tier, amount] of c.upgrades) if (shown >= amount && tierOrdinal(tier) <= tierOrdinal(c.tier)) w = tier;
  return w;
}

/** Title timings (fade in / stay / out, ticks) per tier, global.md §2.6 Bedrock table. */
export const TITLE_TIMES: Readonly<Partial<Record<WinTier, readonly [number, number, number]>>> = {
  BIG: [3, 36, 8],
  MEGA: [3, 44, 10],
  EPIC: [3, 52, 10],
  JACKPOT: [0, 60, 12],
};

/** Particles per tier at the cabinet (≤ 60 per burst, jackpots ≤ 150 in total). */
export const TIER_PARTICLES: Readonly<Partial<Record<WinTier, ReadonlyArray<readonly [string, number]>>>> = {
  NICE: [[SLOT_PARTICLE.sparkle, 12]],
  BIG: [[SLOT_PARTICLE.coin, 40]],
  MEGA: [
    [SLOT_PARTICLE.coin, 40],
    [SLOT_PARTICLE.confetti, 60],
  ],
  EPIC: [
    [SLOT_PARTICLE.coin, 60],
    [SLOT_PARTICLE.confetti, 60],
    ['minecraft:totem_particle', 30],
  ],
};

export const JACKPOT_TIER_KEYS = ['mini', 'minor', 'major', 'grand'] as const;

/** Word of a jackpot tier 1…4 (`slots.jackpot.won` with the tier name). */
export const jackpotWord = (tier: number): Raw => t('gui.burmaldaholic.slots.jackpot.won', t(`gui.burmaldaholic.slots.jackpot.tier.${JACKPOT_TIER_KEYS[Math.min(3, Math.max(1, tier) - 1)]}`));

/** Major / Grand cinematic schedule in ticks (animation/slots.md §6.2 table). */
export interface CinematicPlan {
  readonly fade: number;
  readonly camera: number;
  readonly title: number;
  readonly burst: number;
  readonly rollTicks: number;
  readonly clear: number;
  readonly reopen: number;
  readonly shake: boolean;
}

export function cinematicPlan(tier: number): CinematicPlan | undefined {
  if (tier === 3) return { fade: 0, camera: 2, title: 4, burst: 6, rollTicks: 40, clear: 50, reopen: 60, shake: false };
  if (tier === 4) return { fade: 0, camera: 2, title: 4, burst: 6, rollTicks: 60, clear: 70, reopen: 80, shake: true };
  return undefined;
}

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** Camera for the push-in: 2.2 blocks in front of the reel face, 1.4 up, looking at the face. */
export function cinematicCamera(face: Vec3, facing: { x: number; z: number }): { location: Vec3; facingLocation: Vec3 } {
  return {
    location: { x: face.x + facing.x * 2.2, y: face.y + 1.4, z: face.z + facing.z * 2.2 },
    facingLocation: { x: face.x, y: face.y + 0.6, z: face.z },
  };
}

// ---------------------------------------------------------------------------------------------------------
// Runtime (injected primitives)
// ---------------------------------------------------------------------------------------------------------

/** The core FxService `celebrate` (lane B-L1, global.md §2.6 ⚠ CHANGED request shape). */
export interface SlotCelebrationRequest {
  readonly tier: WinTier;
  readonly net: number;
  readonly stake: number;
  readonly table: WinTierTable;
  readonly words: typeof SLOT_TIER_WORDS;
  readonly jackpotSubTier?: number;
  readonly maxWin: boolean;
  readonly game: string;
  readonly seed: number;
}
export interface SlotFx {
  celebrate(player: Player, request: SlotCelebrationRequest): void;
}

export interface CelebrateRuntime {
  /** `system.runTimeout` */
  after(ticks: number, fn: () => void): void;
  setTitle(p: Player, title: Raw, subtitle: Raw, times: readonly [number, number, number]): void;
  updateSubtitle(p: Player, subtitle: Raw): void;
  /** returns false when skipped (reduce motion / flashes off) */
  fadeGold(p: Player, inS: number, holdS: number, outS: number): boolean;
  shake(p: Player, intensity: number, seconds: number): boolean;
  particle(id: string, count: number): void;
  closeForms(p: Player): void;
  setCamera?(p: Player, location: Vec3, facingLocation: Vec3, easeSeconds: number): boolean;
  clearCamera?(p: Player): void;
  isValid(p: Player): boolean;
}

export interface CelebrateOptions {
  readonly reduceMotion: boolean;
  readonly celebrations: 'all' | 'mine' | 'off';
  readonly fx?: SlotFx;
  readonly seed?: number;
  /** particle count after settings (reduce motion × 0.3) */
  readonly scale?: (n: number) => number;
}

const amountRaw = (n: number): Raw => join(lit('§a+'), chips(n), lit('§r'));

/**
 * Title roll-up with the upgrading tier word (Big+). `runTimeout` chain every 2 t; the last frame prints the
 * exact total. Returns a cancel function that jumps to the final value.
 */
export function titleWinRollUp(p: Player, c: SlotCelebration, rt: CelebrateRuntime, o: CelebrateOptions): () => void {
  const times = TITLE_TIMES[c.tier] ?? TITLE_TIMES.BIG!;
  const ticks = o.reduceMotion ? 2 : Math.max(2, ceilTicks(c.rollupMs));
  const steps = o.reduceMotion ? 2 : Math.max(1, Math.floor(ticks / 2));
  let word = c.startTier;
  let done = false;
  const titleFor = (w: WinTier): Raw => join(lit(tierColor(w)), t(c.maxWin && w === c.tier ? SLOT_TIER_WORDS.maxWin! : SLOT_TIER_WORDS[w]), lit('§r'));
  rt.setTitle(p, titleFor(word), amountRaw(0), [times[0], times[1] + ticks, times[2]]);
  const step = (i: number): void => {
    if (done || !rt.isValid(p)) return;
    const shown = i >= steps ? c.total : rollUpValue(c.total, i / steps);
    const w = tierWordAt(c, shown);
    if (w !== word) {
      word = w;
      rt.setTitle(p, titleFor(w), amountRaw(shown), [0, times[1] + ticks - 2 * i, times[2]]);
    } else rt.updateSubtitle(p, amountRaw(shown));
    if (i >= steps) {
      done = true;
      return;
    }
    rt.after(o.reduceMotion ? Math.max(1, ticks) : 2, () => step(i + 1));
  };
  rt.after(o.reduceMotion ? 1 : 2, () => step(1));
  return () => {
    if (done) return;
    done = true;
    if (word !== c.tier) rt.setTitle(p, titleFor(c.tier), amountRaw(c.total), [0, times[1], times[2]]);
    else rt.updateSubtitle(p, amountRaw(c.total));
  };
}

/** Spin celebration after the reels (Nice+). Returns a cancel/skip function. */
export function celebrateSpin(p: Player, c: SlotCelebration, rt: CelebrateRuntime, o: CelebrateOptions): () => void {
  if (tierOrdinal(c.tier) < tierOrdinal('NICE') || c.tier === 'JACKPOT') return () => {};
  const scale = o.scale ?? ((n: number) => n);
  for (const [id, n] of TIER_PARTICLES[c.overlay ? c.tier : 'NICE'] ?? []) rt.particle(id, scale(n));
  if (!c.overlay || o.celebrations === 'off') return () => {};
  if (o.fx) {
    o.fx.celebrate(p, { tier: c.tier, net: c.total, stake: c.bet, table: SLOT_TIER_TABLE, words: SLOT_TIER_WORDS, maxWin: c.maxWin, game: 'slots', seed: o.seed ?? 0 });
    return () => {};
  }
  if (tierOrdinal(c.tier) >= tierOrdinal('MEGA')) {
    // gold 30 % flash at the Mega upgrade (flashes on); the camera helper skips it under reduce motion
    const at = c.upgrades.find(([tier]) => tier === 'MEGA');
    const ms = at ? Math.floor(c.rollupMs * cubicInverse(at[1] / Math.max(1, c.total))) : 0;
    rt.after(Math.max(1, ceilTicks(ms)), () => rt.fadeGold(p, 0.1, 0.1, 0.3));
  }
  if (c.tier === 'EPIC') {
    const at = c.upgrades.find(([tier]) => tier === 'EPIC');
    const ms = at ? Math.floor(c.rollupMs * cubicInverse(at[1] / Math.max(1, c.total))) : 0;
    rt.after(Math.max(1, ceilTicks(ms)), () => rt.shake(p, 0.25, 0.6));
  }
  return titleWinRollUp(p, c, rt, o);
}

/** Progress at which outCubic reaches `v` (0…1): 1 − (1 − v)^(1/3). */
export const cubicInverse = (v: number): number => (v <= 0 ? 0 : v >= 1 ? 1 : 1 - Math.cbrt(1 - v));

export interface JackpotShow {
  readonly tier: number;
  readonly chips: number;
  /** cabinet face + outward direction for the camera push-in */
  readonly face?: Vec3;
  readonly facing?: { x: number; z: number };
}

/**
 * Major / Grand cinematic. Calls `reopen` when the form should come back (also after reduce-motion titles).
 * Returns a skip function (clears the camera and reopens at once). Mini / Minor: returns undefined (in form).
 */
export function jackpotCinematic(p: Player, jp: JackpotShow, rt: CelebrateRuntime, o: CelebrateOptions, reopen: () => void): (() => void) | undefined {
  const plan = cinematicPlan(jp.tier);
  if (!plan) return undefined;
  let finished = false;
  let cameraSet = false;
  const finish = (): void => {
    if (finished) return;
    finished = true;
    if (cameraSet) rt.clearCamera?.(p);
    reopen();
  };
  rt.closeForms(p);
  const scale = o.scale ?? ((n: number) => n);
  const title = join(lit('§e§l'), jackpotWord(jp.tier), lit('§r'));
  const roll = (ticks: number): void => {
    const steps = o.reduceMotion ? 2 : Math.max(1, Math.floor(ticks / 2));
    rt.setTitle(p, title, amountRaw(0), [o.reduceMotion ? 0 : 5, ticks + 20, 10]);
    const step = (i: number): void => {
      if (finished || !rt.isValid(p)) return;
      rt.updateSubtitle(p, amountRaw(i >= steps ? jp.chips : rollUpValue(jp.chips, i / steps)));
      if (i < steps) rt.after(o.reduceMotion ? ticks : 2, () => step(i + 1));
    };
    rt.after(2, () => step(1));
  };
  if (o.reduceMotion) {
    roll(plan.rollTicks);
    rt.after(plan.reopen, finish);
    return finish;
  }
  rt.after(plan.fade, () => rt.fadeGold(p, 0.1, 0.1, 0.4));
  if (jp.face && jp.facing && rt.setCamera) {
    const cam = cinematicCamera(jp.face, jp.facing);
    rt.after(plan.camera, () => {
      if (!finished && rt.isValid(p)) cameraSet = rt.setCamera!(p, cam.location, cam.facingLocation, 1.0);
    });
  }
  rt.after(plan.title, () => {
    if (!finished) roll(plan.rollTicks);
  });
  rt.after(plan.burst, () => {
    if (finished) return;
    rt.particle(SLOT_PARTICLE.jackpot, scale(jp.tier === 4 ? 30 : 20));
    rt.particle(SLOT_PARTICLE.coin, scale(60));
    if (plan.shake) rt.shake(p, 0.2, 0.6);
  });
  rt.after(plan.clear, () => {
    if (cameraSet) rt.clearCamera?.(p);
    cameraSet = false;
  });
  rt.after(plan.reopen, finish);
  return finish;
}

/** Mini / Minor in-form jackpot particles (world, ≤ 60). */
export function jackpotInForm(jp: JackpotShow, rt: CelebrateRuntime, o: CelebrateOptions): void {
  const scale = o.scale ?? ((n: number) => n);
  rt.particle(SLOT_PARTICLE.coin, scale(jp.tier === 2 ? 30 : 20));
  rt.particle(SLOT_PARTICLE.sparkle, scale(12));
}
