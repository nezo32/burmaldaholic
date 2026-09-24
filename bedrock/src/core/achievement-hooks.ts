/**
 * Achievement wiring (GAME_DESIGN §19): core-observable conditions (wagers, streak, balance)
 * plus the public hooks of feature modules (their api.ts). Game-internal conditions without a
 * hook (blackjack natural, plinko edge, loan, chaos teleport, owned casinos...) call
 * `ctx.achievements.unlock` in their module.
 */
import { system, world } from '@minecraft/server';
import type { CrapsApi } from '../games/craps/api';
import type { RouletteApi } from '../games/roulette/api';
import type { LastchanceApi } from '../lastchance/api';
import type { WorldgenApi } from '../worldgen/api';
import type { Achievements } from './achievements';
import type { Economy } from './economy';
import type { GoldenHour } from './golden-hour';
import { streakAchievements, wagerAchievements } from './logic/achievements';
import { createLogger } from './log';
import type { Services } from './services';
import type { StreakService } from './streak';
import type { WagerService } from './wagers';

const log = createLogger('core.achievements');

/** Service names (the api.ts constants; literal here so core does not bundle module code). */
const SERVICES = { craps: 'craps', roulette: 'roulette', slots: 'slots', lastchance: 'lastchance', worldgen: 'worldgen' } as const;

export interface HookDeps {
  achievements: Achievements;
  wagers: WagerService;
  streak: StreakService;
  goldenHour: GoldenHour;
  economy: Economy;
  services: Services;
}

export function wireAchievements(d: HookDeps): void {
  const a = d.achievements;
  const safe =
    <A extends unknown[]>(what: string, fn: (...args: A) => void) =>
    (...args: A): void => {
      try {
        fn(...args);
      } catch (e) {
        log.error(`${what} hook failed`, e);
      }
    };

  d.wagers.onSettled(
    safe('wager', (e) => {
      if (!e.player?.isValid) return;
      for (const id of wagerAchievements({ staked: e.staked, net: e.net, stakeKind: e.stakeKind, goldenHour: !e.deferred && d.goldenHour.isActive() })) a.unlock(e.player, id);
      if (e.houseBanked && !e.deferred) {
        const casino = d.services.get<WorldgenApi>(SERVICES.worldgen)?.casinoAt(e.player.dimension.id, e.player.location);
        if (casino?.kind === 'high_roller') a.unlock(e.player, 'high_roller');
      }
    }),
  );
  d.streak.onChange(safe('streak', (p, _prev, next) => streakAchievements(next).forEach((id) => a.unlock(p, id))));
  d.economy.onChange(safe('balance', (p, balance) => balance > 0 && a.unlock(p, 'root')));
  world.afterEvents.playerSpawn.subscribe(
    safe('spawn', (e) => {
      if (e.initialSpawn && d.economy.balance(e.player) > 0) system.runTimeout(() => e.player.isValid && a.unlock(e.player, 'root'), 60);
    }),
  );

  // Module hooks: services are provided during onWorldLoad of every module -> subscribe next tick.
  system.run(() => {
    d.services.get<LastchanceApi>(SERVICES.lastchance)?.onSaved(
      safe('lastchance', (e) => {
        a.unlock(e.player, 'not_today');
        if (e.mode === 'high_stakes' && world.isHardcore) a.unlock(e.player, 'scarred');
      }),
    );
    d.services.get<CrapsApi>(SERVICES.craps)?.onPointMade(safe('craps', (shooter, inRow) => inRow >= 3 && a.unlock(shooter, 'hot_shooter')));
    // slots v2 unlocks its own advancements (SLOTS.md §14, `v2/logic/round.ts` achievementsFor)
    d.services.get<RouletteApi>(SERVICES.roulette)?.onSpin(
      safe('roulette', (e) => {
        if (e.result !== 0) return;
        for (const en of e.entries) {
          if (en.bets.some((b) => b.type === 'straight' && b.numbers.length === 1 && b.numbers[0] === 0 && b.totalReturn > 0)) a.unlock(en.player ?? en.playerId, 'zero_hero');
        }
      }),
    );
    d.services.get<WorldgenApi>(SERVICES.worldgen)?.onEnter(safe('worldgen', (p, c) => c.kind === 'piglin_parlor' && a.unlock(p, 'piglin_parlor')));
  });
}
