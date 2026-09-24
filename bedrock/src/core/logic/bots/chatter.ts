/** Bot quip rate limits (BOTS.md §7.4). PURE; one limiter per table / match; time = world ticks. */
import type { Rng } from '../rng';

export const ALWAYS_FIRE = new Set(['yield', 'word_got_around', 'sulk', 'duel_accept', 'duel_decline']);

/** Variant counts of `dialog.burmaldaholic.bots.<event>.N` (BOTS.md §11.4). */
export const QUIP_VARIANTS: Readonly<Record<string, number>> = {
  join: 4, yield: 3, leave: 3, win_big: 4, bust: 4, bad_beat: 3, fold_to_shove: 3, hero_call: 3, human_wins: 4, all_in: 3,
  blackjack: 3, seven_out: 3, natural: 3, bank_take: 3, banco: 3, pvp_win: 3, pvp_loss: 3, duel_accept: 3, duel_decline: 2,
  word_got_around: 3, sulk: 3, idle: 4,
};

export interface ChatterConfig {
  chance: number;
  botCooldownTicks: number;
  tableCooldownTicks: number;
  maxPerMinute: number;
}

export class ChatterLimiter {
  private readonly lastByBot = new Map<string, number>();
  private lastTable = -Infinity;
  private readonly minute: number[] = [];

  /** @returns ticks to wait before saying the line (0 = now), or -1 = drop it. `hard` halves the chance. */
  admit(botKey: string, event: string, now: number, c: ChatterConfig, rng: Rng, hard = false): number {
    const always = ALWAYS_FIRE.has(event);
    if (!always) {
      if (rng.next() >= (hard ? c.chance / 2 : c.chance)) return -1;
      const last = this.lastByBot.get(botKey);
      if (last !== undefined && now - last < c.botCooldownTicks) return -1;
    }
    while (this.minute.length && now - this.minute[0]! >= 1200) this.minute.shift();
    if (this.minute.length >= c.maxPerMinute && !always) return -1;
    const wait = Math.max(0, this.lastTable + c.tableCooldownTicks - now);
    if (wait > 40 || (wait > 0 && !always)) return -1;
    this.lastByBot.set(botKey, now + wait);
    this.lastTable = now + wait;
    this.minute.push(now + wait);
    return wait;
  }
}
