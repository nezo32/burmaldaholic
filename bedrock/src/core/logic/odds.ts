/**
 * Odds service. PURE.
 *
 * Two hooks for cross-cutting features, so they never edit game code:
 *  1. `probability(playerId, game, base)`: binary-outcome probability after modifiers
 *     registered with `addModifier` (chaos buffs, VIP perks, Last Chance...).
 *  2. Streak re-draw (GAME_DESIGN §14) for RNG games: `rerollChance(playerId, rtp)` and
 *     `draw(playerId, rtp, rng, drawFn, isLosing)`. The streak value comes from the core
 *     streak service (persisted per player); tests use the in-memory source.
 */
import type { Rng } from './rng';
import { DEFAULT_STREAK, type RoundOutcome, type StreakConfig, drawWithReroll, rerollChance } from './streak';

export interface OddsQuery {
  playerId: string;
  /** Game asking, e.g. 'slots', 'coin_flip'. */
  game: string;
  /** Base probability from the game's own rules (0..1). */
  base: number;
  /** Current streak for this player: +N wins in a row, -N losses in a row. */
  streak: number;
}

/** Returns a new probability (it will be clamped to [minP, maxP]). */
export type OddsModifier = (q: OddsQuery, current: number) => number;

/** Where streak values live (core: player dynamic property; tests: memory). */
export interface StreakSource {
  get(playerId: string): number;
  record(playerId: string, outcome: RoundOutcome): number;
  config(): StreakConfig;
}

export function memoryStreaks(cfg: StreakConfig = DEFAULT_STREAK): StreakSource & { reset(id: string): void } {
  const m = new Map<string, number>();
  return {
    get: (id) => m.get(id) ?? 0,
    record(id, o) {
      const s = m.get(id) ?? 0;
      const next = o === 'win' ? Math.min(cfg.max, Math.max(s, 0) + 1) : o === 'loss' ? Math.max(-cfg.max, Math.min(s, 0) - 1) : s;
      m.set(id, next);
      return next;
    },
    config: () => cfg,
    reset: (id) => void m.delete(id),
  };
}

export class OddsService {
  private readonly modifiers: { id: string; order: number; fn: OddsModifier }[] = [];

  constructor(
    private readonly minP = 0,
    private readonly maxP = 0.95,
    private streaks: StreakSource = memoryStreaks(),
  ) {}

  /** Core wires the persistent streak store here at world load. */
  setStreakSource(s: StreakSource): void {
    this.streaks = s;
  }

  /** Register a modifier. Lower order runs first. Re-registering an id replaces it. */
  addModifier(id: string, fn: OddsModifier, order = 100): void {
    this.removeModifier(id);
    this.modifiers.push({ id, order, fn });
    this.modifiers.sort((a, b) => a.order - b.order);
  }

  removeModifier(id: string): void {
    const i = this.modifiers.findIndex((m) => m.id === id);
    if (i >= 0) this.modifiers.splice(i, 1);
  }

  streakOf(playerId: string): number {
    return this.streaks.get(playerId);
  }

  /** Adjusted probability for a single binary outcome. */
  probability(playerId: string, game: string, base: number): number {
    const q: OddsQuery = { playerId, game, base, streak: this.streakOf(playerId) };
    let p = base;
    for (const m of this.modifiers) p = m.fn(q, p);
    return Math.min(this.maxP, Math.max(this.minP, p));
  }

  /**
   * Record a round result for the streak. Settling through `ctx.wagers.settle` does this
   * automatically - call it only for rounds that do not go through the wager service.
   */
  recordResult(playerId: string, outcome: RoundOutcome): number {
    return this.streaks.record(playerId, outcome);
  }

  /** Streak re-draw probability for a losing outcome in a game with the given RTP (§14). */
  rerollChance(playerId: string, rtp: number): number {
    return rerollChance(this.streakOf(playerId), rtp, this.streaks.config());
  }

  /**
   * Draw an RNG-game outcome with the streak re-draw applied:
   * `const { result } = ctx.odds.draw(p.id, GAME_RTP.coin_flip, rng, () => flip(rng), (r) => !r.win)`.
   */
  draw<T>(playerId: string, rtp: number, rng: Rng, drawFn: () => T, isLosing: (x: T) => boolean): { result: T; rerolled: boolean } {
    return drawWithReroll(rng, this.rerollChance(playerId, rtp), drawFn, isLosing);
  }
}
