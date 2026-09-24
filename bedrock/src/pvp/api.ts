/**
 * Public API of the pvp module (types only + service name). The PvP ENGINE is core (`ctx.pvp`); this
 * service only exposes UI entry points other modules may open (e.g. a machine form's "Open lobbies").
 */
export const PVP_UI_SERVICE = 'pvp.ui';

export interface PvpUiApi {
  /** Open the PvP hub for a player (Casino Menu → Challenges). */
  openHub(playerId: string): void;
}
