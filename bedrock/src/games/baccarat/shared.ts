/** Constants shared by the baccarat runtime files. */
import type { GameId } from '../../core';

/** Wager game id (core GAME_IDS; the exact edge of each bet is passed per bet here). */
export const GAME: GameId = 'baccarat';
export const HUD_CHANNEL = 'baccarat.table';
export const HIGH_ROLLER = 'high_roller';
export const PLAYER_BANKED = 'player_banked';
