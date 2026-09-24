import { describe, expect, it } from 'vitest';
import {
  CEL_BEAT,
  type CelebrationProfile,
  type CelebrationRequest,
  MAX_BURST,
  MODE_ACTIONBAR,
  MODE_TITLE,
  PARTICLE_SPARKS,
  REDUCED_ROLL_MS,
  buildCelebration,
  celebrationFrame,
  celebrationTerminal,
  reachMs,
  startWord,
} from './celebration';
import { rollUpValue } from './rollup';
import { DEFAULT_TIERS, SLOT_TIERS, type WinTier, WIN_TIERS, type WinTierTable, tierOrdinal, winTierOf } from './win-tier';
import { LOCAL } from './timeline';

const FULL: CelebrationProfile = { speedPct: 100, reduceMotion: false, flashes: true, celebrations: 'all' };
const REDUCED: CelebrationProfile = { speedPct: 100, reduceMotion: true, flashes: false, celebrations: 'all' };
const req = (tier: WinTier, ret: number, stake: number, table: WinTierTable = DEFAULT_TIERS, extra: Partial<CelebrationRequest> = {}): CelebrationRequest => ({ tier, ret, stake, table, game: 'test', seed: 7, ...extra });
const kinds = (r: CelebrationRequest, p = FULL) => buildCelebration(r, p).beats.map((b) => b.kind);

/** Every (ret, stake) the tier functions produce, for both tables. */
function vectors(): CelebrationRequest[] {
  const out: CelebrationRequest[] = [];
  for (const table of [DEFAULT_TIERS, SLOT_TIERS]) {
    for (const stake of [5, 10, 50, 100, 1000]) {
      for (const mult of [0, 0.5, 1, 2, 5, 9, 10, 14, 15, 25, 39, 40, 50, 99, 100, 250]) {
        const ret = Math.round(stake * mult);
        out.push(req(winTierOf(ret, stake, table), ret, stake, table));
      }
    }
    out.push(req('JACKPOT', 50_000, 50, table, { subTier: 4, subTierWord: 'x' }));
    out.push(req('JACKPOT', 800, 50, table));
  }
  return out;
}

describe('celebration plan (global.md §2.6 Bedrock, lead decision L2)', () => {
  it('ends on the exact server amount and the server tier word for every vector and profile', () => {
    for (const profile of [FULL, REDUCED, { ...FULL, speedPct: 50 }, { ...FULL, speedPct: 150 }, { ...FULL, celebrations: 'off' as const }]) {
      for (const r of vectors()) {
        const tl = buildCelebration(r, profile);
        const end = celebrationFrame(r, tl, tl.endMs());
        expect(end).toEqual(celebrationTerminal(r));
        expect(end.amount).toBe(r.ret);
        expect(end.word).toBe(r.tier);
        // the last word beat (if any) is the server tier
        const words = tl.beats.filter((b) => b.kind === CEL_BEAT.WORD).map((b) => WIN_TIERS[b.args[0]!]);
        if (words.length) expect(words[words.length - 1]).toBe(r.tier);
      }
    }
  });

  it('is LOCAL, one skip group, and the roll-up never goes down or over the total', () => {
    for (const r of vectors()) {
      const tl = buildCelebration(r, FULL);
      expect(tl.beats.every((b) => b.clock === LOCAL && b.group === 0)).toBe(true);
      expect(tl.sharedEndMs()).toBe(0);
      let prev = -1;
      for (let t = 0; t <= tl.endMs(); t += 50) {
        const f = celebrationFrame(r, tl, t);
        expect(f.amount).toBeGreaterThanOrEqual(prev);
        expect(f.amount).toBeLessThanOrEqual(r.ret);
        prev = f.amount;
      }
    }
  });

  it('words only go up, and each upgrade happens when the rolling amount passes the caller table threshold', () => {
    const r = req('EPIC', 60 * 100, 100); // default: BIG → MEGA (25×) → EPIC (50×)
    const tl = buildCelebration(r, FULL);
    const start = tl.beats.find((b) => b.kind === CEL_BEAT.START)!;
    expect(WIN_TIERS[start.args[1]!]).toBe('BIG');
    expect(start.args[2]).toBe(MODE_TITLE);
    const ups = tl.beats.filter((b) => b.kind === CEL_BEAT.WORD);
    expect(ups.map((b) => WIN_TIERS[b.args[0]!])).toEqual(['MEGA', 'EPIC']);
    const roll = tl.beats.find((b) => b.kind === CEL_BEAT.ROLL)!;
    for (const [b, threshold] of [
      [ups[0]!, 2500],
      [ups[1]!, 5000],
    ] as const) {
      expect(rollUpValue(r.ret, b.at / roll.dur)).toBeGreaterThanOrEqual(threshold);
      expect(rollUpValue(r.ret, (b.at - 1) / roll.dur)).toBeLessThan(threshold);
    }
    // slots start at NICE and use 15 / 40 / 100 ×
    const s = buildCelebration(req('EPIC', 120 * 5, 5, SLOT_TIERS), FULL);
    expect(WIN_TIERS[s.beats.find((b) => b.kind === CEL_BEAT.START)!.args[1]!]).toBe('NICE');
    expect(s.beats.filter((b) => b.kind === CEL_BEAT.WORD).map((b) => WIN_TIERS[b.args[0]!])).toEqual(['BIG', 'MEGA', 'EPIC']);
  });

  it('startWord / reachMs', () => {
    expect(startWord('MEGA', DEFAULT_TIERS)).toBe('BIG');
    expect(startWord('BIG', DEFAULT_TIERS)).toBe('BIG');
    expect(startWord('MEGA', SLOT_TIERS)).toBe('NICE');
    expect(startWord('WIN', SLOT_TIERS)).toBe('WIN');
    expect(reachMs(1000, 1000, 0)).toBe(0);
    expect(reachMs(1000, 1000, 1000)).toBe(1000);
    expect(reachMs(1000, 1000, 5000)).toBe(1000);
  });

  it('non-overlay tiers use the action bar with no roll-up', () => {
    for (const tier of ['LOSS', 'PUSH', 'RETURN', 'WIN', 'NICE'] as const) {
      const tl = buildCelebration(req(tier, 10, 10), FULL);
      expect(tl.beats.find((b) => b.kind === CEL_BEAT.START)!.args[2]).toBe(MODE_ACTIONBAR);
      expect(tl.beats.some((b) => b.kind === CEL_BEAT.ROLL || b.kind === CEL_BEAT.FADE || b.kind === CEL_BEAT.SHAKE)).toBe(false);
    }
    expect(buildCelebration(req('WIN', 20, 10), FULL).endMs()).toBe(1000); // form after 20 t
    expect(buildCelebration(req('LOSS', 0, 10), FULL).beats.some((b) => b.kind === CEL_BEAT.STEM)).toBe(false); // silent
  });

  it('storyboard lengths: form delays 40 / 50 / 60 / 60 t; jackpot sub-tiers 2.0–4.0 s', () => {
    expect(buildCelebration(req('BIG', 1200, 100), FULL).endMs()).toBe(2000);
    expect(buildCelebration(req('MEGA', 3000, 100), FULL).endMs()).toBe(2500);
    expect(buildCelebration(req('EPIC', 6000, 100), FULL).endMs()).toBe(3000);
    expect(buildCelebration(req('JACKPOT', 6000, 100), FULL).endMs()).toBe(3000);
    expect([1, 2, 3, 4].map((sub) => buildCelebration(req('JACKPOT', 6000, 100, DEFAULT_TIERS, { subTier: sub }), FULL).endMs())).toEqual([2000, 2500, 3000, 4000]);
  });

  it('camera: MEGA fades, EPIC/JACKPOT fade + shake, at the moment the final tier is reached', () => {
    expect(kinds(req('BIG', 1200, 100))).not.toContain(CEL_BEAT.FADE);
    expect(kinds(req('MEGA', 3000, 100))).toContain(CEL_BEAT.FADE);
    expect(kinds(req('MEGA', 3000, 100))).not.toContain(CEL_BEAT.SHAKE);
    const tl = buildCelebration(req('EPIC', 6000, 100), FULL);
    const epicAt = tl.beats.filter((b) => b.kind === CEL_BEAT.WORD).pop()!.at;
    expect(tl.beats.find((b) => b.kind === CEL_BEAT.SHAKE)!.at).toBe(epicAt);
    expect(tl.beats.find((b) => b.kind === CEL_BEAT.FADE)!.at).toBe(epicAt);
  });

  it('reduce motion: no camera, no count ticks, roll-up ≤ 300 ms, particles × 0.3; flashes off: no fade, no sparks', () => {
    for (const r of vectors()) {
      const tl = buildCelebration(r, REDUCED);
      const k = tl.beats.map((b) => b.kind);
      expect(k).not.toContain(CEL_BEAT.FADE);
      expect(k).not.toContain(CEL_BEAT.SHAKE);
      expect(k).not.toContain(CEL_BEAT.TICK);
      const roll = tl.beats.find((b) => b.kind === CEL_BEAT.ROLL);
      if (roll) expect(roll.dur).toBeLessThanOrEqual(REDUCED_ROLL_MS);
      expect(tl.beats.some((b) => b.kind === CEL_BEAT.BURST && b.args[0] === PARTICLE_SPARKS)).toBe(false);
    }
    const full = buildCelebration(req('EPIC', 6000, 100), FULL).beats.filter((b) => b.kind === CEL_BEAT.BURST).map((b) => b.args[1]!);
    const red = buildCelebration(req('EPIC', 6000, 100), REDUCED).beats.filter((b) => b.kind === CEL_BEAT.BURST).map((b) => b.args[1]!);
    expect(red).toEqual(full.map((n) => Math.max(1, Math.round(n * 0.3))));
    const noFlash = buildCelebration(req('JACKPOT', 6000, 100), { ...FULL, flashes: false });
    expect(noFlash.beats.some((b) => b.kind === CEL_BEAT.FADE)).toBe(false);
    expect(noFlash.beats.some((b) => b.kind === CEL_BEAT.SHAKE)).toBe(true); // shake is motion, not a flash
  });

  it('budgets: ≤ 60 per burst, ≤ 24 particle calls, count ticks ≤ 5/s', () => {
    for (const r of vectors()) {
      const tl = buildCelebration(r, FULL);
      const bursts = tl.beats.filter((b) => b.kind === CEL_BEAT.BURST);
      expect(bursts.length).toBeLessThanOrEqual(24);
      for (const b of bursts) expect(b.args[1]).toBeLessThanOrEqual(MAX_BURST);
      const ticks = tl.beats.filter((b) => b.kind === CEL_BEAT.TICK);
      for (let i = 1; i < ticks.length; i++) expect(ticks[i]!.at - ticks[i - 1]!.at).toBeGreaterThanOrEqual(200);
    }
  });

  it('speed scales local lengths; celebrations off = the WIN presentation with the real word', () => {
    expect(buildCelebration(req('EPIC', 6000, 100), { ...FULL, speedPct: 150 }).endMs()).toBe(2000);
    expect(buildCelebration(req('EPIC', 6000, 100), { ...FULL, speedPct: 50 }).endMs()).toBe(6000);
    const off = buildCelebration(req('EPIC', 6000, 100), { ...FULL, celebrations: 'off' });
    expect(off.beats.find((b) => b.kind === CEL_BEAT.START)!.args[2]).toBe(MODE_ACTIONBAR);
    expect(off.beats.some((b) => [CEL_BEAT.BURST, CEL_BEAT.FADE, CEL_BEAT.SHAKE, CEL_BEAT.TICK].includes(b.kind as never))).toBe(false);
    expect(off.beats.filter((b) => b.kind === CEL_BEAT.STEM).map((b) => WIN_TIERS[b.args[0]!])).toEqual(['WIN']);
    expect(off.endMs()).toBe(1000);
  });

  it('is deterministic (canonical JSON) and tier ordinals are stable', () => {
    const r = req('MEGA', 3100, 100);
    expect(buildCelebration(r, FULL).toCanonicalJson()).toBe(buildCelebration(r, FULL).toCanonicalJson());
    expect(tierOrdinal('JACKPOT')).toBe(8);
  });
});
