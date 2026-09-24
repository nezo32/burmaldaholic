/**
 * Seats & Bots service (BOTS.md), reached by modules as `ctx.bots`. World-wide limits
 * (`bots.maxActive`, `bots.maxActiveTables`), bot rng creation, think times, the heavy-job queue, the
 * daily heat ledger and per-table state factories. SKELETON (task B-B1): `table()` returns a
 * HUMANS_ONLY-behaving TableBots so games can integrate before the logic lands.
 */
import type { ConfigService } from '../config';
import type { BotWork } from '../logic/bots/policy';
import { botRng } from '../logic/bots/rng';
import { type ThinkConfig, thinkTicks } from '../logic/bots/think';
import { type BotProfile, type BotSettings, HUMANS_ONLY_SETTINGS, type OwnerControls, effectiveSpeed, unownedControls } from '../logic/bots/types';
import type { Rng } from '../logic/rng';
import { BotJobs } from './jobs';
import type { BotTableHooks, SafePointResult, TableBots } from './table-bots';

export interface BotsService {
  enabled(): boolean;
  /** bots that may still sit world-wide */
  activeBudgetLeft(): number;
  /** a fresh bot stream for a table / match (never Math.random) */
  newRng(tableKey: string): Rng;
  think(rng: Rng, bot: BotProfile, settings: BotSettings, big: boolean, humanTimer: number, game: string): number;
  /** run heavy work within the world budget; onDone(result | undefined) unless stale */
  submitWork(work: BotWork, deadlineTicks: number, stillWanted: () => boolean, onDone: (result: unknown) => void): void;
  /** per-table state (created or loaded from `burmaldaholic:bots:<key>`) */
  table(key: string, hooks: BotTableHooks, defaults: BotSettings, limits?: OwnerControls): TableBots;
  /** net won from house-funded bots today (BOTS.md §5.4) */
  netToday(playerId: string): number;
  /** add an attributed net (economy.ts pokerPotNet / pvpNet); drives heat stages */
  recordNet(playerId: string, net: number): void;
  /** house bots refuse this player until the next MCD */
  sulking(playerId: string): boolean;
}

export class Bots implements BotsService {
  private sessions = 0;
  private active = 0;
  private readonly jobs: BotJobs;

  constructor(private readonly config: ConfigService) {
    this.jobs = new BotJobs(() => this.config.int('bots.maxConcurrentJobs'));
  }

  enabled(): boolean {
    return this.config.bool('bots.enabled');
  }
  activeBudgetLeft(): number {
    return Math.max(0, this.config.int('bots.maxActive') - this.active);
  }
  newRng(tableKey: string): Rng {
    return botRng(tableKey, ++this.sessions, Date.now(), this.config.num('debug.fixedSeed'));
  }
  think(rng: Rng, bot: BotProfile, settings: BotSettings, big: boolean, humanTimer: number, game: string): number {
    const poker = game === 'poker';
    const c: ThinkConfig = {
      minTicks: this.config.int(poker ? 'poker.botThinkMinTicks' : 'bots.think.minTicks'),
      maxTicks: this.config.int(poker ? 'poker.botThinkMaxTicks' : 'bots.think.maxTicks'),
      fastFactor: this.config.num('bots.think.fastFactor'),
      tankTicks: this.config.int('bots.think.tankTicks'),
    };
    return thinkTicks(rng, c, bot, effectiveSpeed(settings), big, humanTimer);
  }
  submitWork(work: BotWork, deadlineTicks: number, stillWanted: () => boolean, onDone: (result: unknown) => void): void {
    this.jobs.submit(work, deadlineTicks, stillWanted, onDone);
  }
  table(key: string, hooks: BotTableHooks, defaults: BotSettings, limits?: OwnerControls): TableBots {
    // TODO(B-B1): load/save `burmaldaholic:bots:<key>`, host, claimants, private access, safe point fill.
    const rng = this.newRng(key);
    const lim = limits ?? unownedControls(hooks.seats());
    void lim;
    void defaults;
    const empty: SafePointResult = { joined: [], left: [], seatedClaimants: [], settingsApplied: false };
    return {
      key,
      rng,
      settings: () => HUMANS_ONLY_SETTINGS,
      pending: () => undefined,
      host: () => hooks.seatedHumans()[0],
      bots: () => [],
      admit: () => ({ ok: true, sitNow: true }),
      requestChange: () => undefined,
      setLimits: () => undefined,
      safePoint: () => empty,
      think: (bot, big, humanTimer) => this.think(rng, bot, HUMANS_ONLY_SETTINGS, big, humanTimer, hooks.game),
      endSession: () => undefined,
    };
  }
  netToday(): number {
    return 0; // TODO(B-B1) world sharded `burmaldaholic:bots_ledger` {playerId: [day, net]}
  }
  recordNet(): void {
    // TODO(B-B1)
  }
  sulking(): boolean {
    return false;
  }
  /** @internal counters for the world budget */
  countJoined(n: number): void {
    this.active = Math.max(0, this.active + n);
  }
}
