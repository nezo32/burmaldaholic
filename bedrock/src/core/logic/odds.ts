/**
 * Odds service. PURE.
 *
 * Games ask for an adjusted win probability instead of hardcoding one, so cross-cutting
 * features (chaos "Golden Hour", streak "hot hand", VIP perks, Last Chance) can nudge odds
 * without editing game code. Modifiers are registered by those modules at startup.
 */
export interface OddsQuery {
  playerId: string;
  /** Module id of the game asking, e.g. 'slots'. */
  game: string;
  /** Base probability from the game's own rules (0..1). */
  base: number;
  /** Current streak for this player: +N wins in a row, -N losses in a row. */
  streak: number;
}

/** Returns a new probability (it will be clamped to [minP, maxP]). */
export type OddsModifier = (q: OddsQuery, current: number) => number;

export class OddsService {
  private readonly modifiers: { id: string; order: number; fn: OddsModifier }[] = [];
  private readonly streaks = new Map<string, number>();

  constructor(
    private readonly minP = 0,
    private readonly maxP = 0.95,
  ) {}

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
    return this.streaks.get(playerId) ?? 0;
  }

  /** Adjusted probability for a single binary outcome. */
  probability(playerId: string, game: string, base: number): number {
    const q: OddsQuery = { playerId, game, base, streak: this.streakOf(playerId) };
    let p = base;
    for (const m of this.modifiers) p = m.fn(q, p);
    return Math.min(this.maxP, Math.max(this.minP, p));
  }

  /** Games must report every resolved round so streak-based modifiers work. Push = 0. */
  recordResult(playerId: string, outcome: 'win' | 'loss' | 'push'): number {
    const s = this.streakOf(playerId);
    let next = s;
    if (outcome === 'win') next = s > 0 ? s + 1 : 1;
    else if (outcome === 'loss') next = s < 0 ? s - 1 : -1;
    this.streaks.set(playerId, next);
    return next;
  }

  resetStreak(playerId: string): void {
    this.streaks.delete(playerId);
  }
}
