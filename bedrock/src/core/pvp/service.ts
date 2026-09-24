/**
 * The PvP engine (PVP.md §3; docs/architecture/pvp-bots.md). Reached by modules as `ctx.pvp`.
 * SKELETON: every entry point answers `gui.burmaldaholic.error.disabled` until task B-P1 lands, so
 * nothing changes in game. Money: ONE `economy.transact` per escrow / settle (bank as escrow holder;
 * rake to the bank or the anchor's bankroll, bot share of the rake always to the bank — BOTS.md §5.1;
 * bot stakes through their purse, core/bots/purses.ts).
 */
import type { Player } from '@minecraft/server';
import type { BotDifficulty, BotSettings } from '../logic/bots/types';
import type { MatchRecord } from '../logic/pvp/match';
import type { AnchorKind, AnyPvpMode, PvpModeId } from '../logic/pvp/mode';
import { EMPTY_H2H, type HeadToHead } from '../logic/pvp/rivalry';
import { type Raw, t } from '../logic/rawtext';
import type { PvpModeUi, PvpPresenter } from './presenter';

export type PvpResult<T> = { ok: true; value: T } | { ok: false; error: Raw };

export interface PvpAnchor {
  kind: AnchorKind;
  /** table key of the machine (`ctx.tables` key) or undefined for anchor-less lobbies / duels */
  tableKey?: string;
  dimension: string;
  location: { x: number; y: number; z: number };
}

export type PvpOpponent = { kind: 'player'; playerId: string } | { kind: 'bot'; difficulty: BotDifficulty };

export interface PvpStats {
  wins: number;
  losses: number;
  net: number;
  winStreak: number;
  nemesis?: string;
}

export interface PvpService {
  /** Mode-owning modules register their modes (+ optional per-mode UI) in onWorldLoad. */
  registerMode(mode: AnyPvpMode, ui?: PvpModeUi): void;
  mode(id: PvpModeId): AnyPvpMode | undefined;
  modes(): readonly AnyPvpMode[];
  /** pvp module installs the Bedrock presenter once. */
  setPresenter(p: PvpPresenter): void;

  // duels (§3.3.1)
  challenge(challenger: Player, mode: PvpModeId, params: unknown, stake: number, opponent: PvpOpponent): PvpResult<MatchRecord>;
  accept(target: Player, matchId: string): PvpResult<MatchRecord>;
  decline(target: Player, matchId: string): void;
  withdraw(challenger: Player, matchId: string): void;
  // lobbies (§3.3.2, §3.15)
  openLobby(host: Player, mode: PvpModeId, params: unknown, stake: number, anchor: PvpAnchor, seating: BotSettings, inviteOnly: boolean): PvpResult<MatchRecord>;
  join(player: Player, matchId: string, stake: number): PvpResult<MatchRecord>;
  topUp(player: Player, matchId: string, extra: number): PvpResult<number>;
  leave(player: Player): void;
  start(host: Player, matchId: string): PvpResult<MatchRecord>;
  fillWithBots(host: Player, matchId: string): void;
  // during a match
  press(player: Player): void;
  decide(player: Player, decision: string, option: number): void;
  taunt(player: Player, line: number): Raw | undefined;
  rematch(player: Player, matchId: string): void;
  // queries
  matchOf(playerId: string): MatchRecord | undefined;
  get(matchId: string): MatchRecord | undefined;
  lobbiesNear(player: Player, mode?: PvpModeId): MatchRecord[];
  invitesFor(playerId: string): MatchRecord[];
  record(a: Player, b: string): HeadToHead;
  stats(player: Player): PvpStats;
  // admin (§3.13)
  all(): MatchRecord[];
  cancel(matchId: string): void;
}

const disabled = <T>(): PvpResult<T> => ({ ok: false, error: t('gui.burmaldaholic.error.disabled') });

/** SKELETON engine (task B-P1). */
export class PvpEngine implements PvpService {
  private readonly registered = new Map<PvpModeId, { mode: AnyPvpMode; ui?: PvpModeUi }>();
  private presenter: PvpPresenter = {};

  /** Core, at world load: LOBBY → refund, DRAWN → settle from tape (offline-safe), timers. */
  boot(): void {
    // TODO(B-P1): pvpStore.ids() play-out (PVP.md §3.6), system.runInterval timers, casino-off hook.
  }

  registerMode(mode: AnyPvpMode, ui?: PvpModeUi): void {
    if (this.registered.has(mode.id)) throw new Error(`PvP mode '${mode.id}' registered twice`);
    this.registered.set(mode.id, { mode, ui });
  }
  mode(id: PvpModeId): AnyPvpMode | undefined {
    return this.registered.get(id)?.mode;
  }
  modes(): readonly AnyPvpMode[] {
    return [...this.registered.values()].map((r) => r.mode);
  }
  setPresenter(p: PvpPresenter): void {
    this.presenter = p;
  }
  presenterOf(): PvpPresenter {
    return this.presenter;
  }

  challenge(): PvpResult<MatchRecord> {
    return disabled();
  }
  accept(): PvpResult<MatchRecord> {
    return disabled();
  }
  decline(): void {}
  withdraw(): void {}
  openLobby(): PvpResult<MatchRecord> {
    return disabled();
  }
  join(): PvpResult<MatchRecord> {
    return disabled();
  }
  topUp(): PvpResult<number> {
    return disabled();
  }
  leave(): void {}
  start(): PvpResult<MatchRecord> {
    return disabled();
  }
  fillWithBots(): void {}
  press(): void {}
  decide(): void {}
  taunt(): Raw | undefined {
    return t('gui.burmaldaholic.error.disabled');
  }
  rematch(): void {}
  matchOf(): MatchRecord | undefined {
    return undefined;
  }
  get(): MatchRecord | undefined {
    return undefined;
  }
  lobbiesNear(): MatchRecord[] {
    return [];
  }
  invitesFor(): MatchRecord[] {
    return [];
  }
  record(): HeadToHead {
    return EMPTY_H2H;
  }
  stats(): PvpStats {
    return { wins: 0, losses: 0, net: 0, winStreak: 0 };
  }
  all(): MatchRecord[] {
    return [];
  }
  cancel(): void {}
}
