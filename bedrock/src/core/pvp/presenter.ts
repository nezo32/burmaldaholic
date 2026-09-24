/**
 * Edition UI hook of the PvP engine (PVP.md §3.11): the engine decides WHAT and WHEN, the presenter
 * HOW. Bedrock: titles / subtitles (with `hud.holdTitle`, PVP.md §14), the action-bar ticker through
 * `ctx.hud.actionbar(p, 'pvp.<id>', …)`, chat for the round log, forms only for set-up and results.
 * Modes may add `describe(step)` lines via `PvpModeUi` (registered with the mode). Default: nothing.
 */
import type { Player } from '@minecraft/server';
import type { MatchRecord } from '../logic/pvp/match';
import type { Step } from '../logic/pvp/mode';
import type { Raw } from '../logic/rawtext';

export interface PvpPresenter {
  lobbyChanged?(match: MatchRecord): void;
  revealStep?(match: MatchRecord, step: Step, viewers: readonly Player[]): void;
  finalReveal?(match: MatchRecord, place: number, viewers: readonly Player[]): void;
  result?(match: MatchRecord, participants: readonly Player[]): void;
  sound?(match: MatchRecord, soundId: string, who: number): void;
  particles?(match: MatchRecord, particleId: string, who: number): void;
}

/** Per-mode presentation (runtime, NOT logic): action-bar line of a revealed step for one viewer. */
export interface PvpModeUi {
  describe(match: MatchRecord, step: Step, viewerSeat: number): Raw | undefined;
}
