/** Constants shared by the baccarat runtime files. */
import type { GameId } from '../../core';

/**
 * Wager game id. 'baccarat' is not in core's GAME_IDS yet (needed core change, see the module
 * report); core only uses it as a label key (`gui.burmaldaholic.common.game.baccarat`, in this
 * module's lang fragment) and for the house edge (passed per bet here).
 */
export const GAME = 'baccarat' as GameId;
export const HUD_CHANNEL = 'baccarat.table';
export const HIGH_ROLLER = 'high_roller';
export const PLAYER_BANKED = 'player_banked';
