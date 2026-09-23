/**
 * Persistent Lucky/Unlucky streak (GAME_DESIGN §14). Player dynamic property
 * `burmaldaholic:core.streak` = JSON {s, t}. Updated by the wager service on every settled
 * wager (stake ≥ 1); decays with world time. Implements the odds service's StreakSource.
 */
import { type Player, world } from '@minecraft/server';
import type { ConfigService } from './config';
import type { StreakSource } from './logic/odds';
import { type RoundOutcome, type StreakConfig, type StreakState, decay, record, streakMessageKey } from './logic/streak';
import { t } from './logic/rawtext';
import { readJson, worldTick, writeJson } from './store';

const PROP = 'burmaldaholic:core.streak';

export type StreakListener = (player: Player, prev: number, next: number) => void;

export class StreakService implements StreakSource {
  private readonly listeners: StreakListener[] = [];

  constructor(private readonly cfg: ConfigService) {}

  config(): StreakConfig {
    const c = this.cfg;
    return {
      enabled: c.bool('streak.enabled'),
      max: c.int('streak.max'),
      luckyPerStep: c.num('streak.luckyPerStep'),
      pityPerStep: c.num('streak.pityPerStep'),
      minHouseEdge: c.num('streak.minHouseEdge'),
      decayTicks: c.int('streak.decayTicks'),
    };
  }

  onChange(l: StreakListener): void {
    this.listeners.push(l);
  }

  private state(p: Player): StreakState {
    const st = readJson<Partial<StreakState>>(p, PROP, {});
    return { s: typeof st.s === 'number' ? st.s : 0, t: typeof st.t === 'number' ? st.t : worldTick() };
  }

  /** Current streak of a player (decay applied). */
  of(p: Player): number {
    return decay(this.state(p), worldTick(), this.config()).s;
  }

  get(playerId: string): number {
    const p = world.getEntity(playerId);
    return p && p.typeId === 'minecraft:player' ? this.of(p as Player) : 0;
  }

  record(playerId: string, outcome: RoundOutcome): number {
    const p = world.getEntity(playerId);
    return p && p.typeId === 'minecraft:player' ? this.recordFor(p as Player, outcome) : 0;
  }

  recordFor(p: Player, outcome: RoundOutcome): number {
    const now = worldTick();
    const prev = this.of(p);
    const next = record(this.state(p), outcome, now, this.config());
    writeJson(p, PROP, next);
    if (next.s !== prev) {
      const key = streakMessageKey(prev, next.s);
      if (key) p.sendMessage(t(key));
      for (const l of this.listeners) l(p, prev, next.s);
    }
    return next.s;
  }

  reset(p: Player): void {
    writeJson(p, PROP, undefined);
  }
}
