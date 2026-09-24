/**
 * Public API of the bots module (types only + service name): table games add the BOTS.md §8.3 entries
 * to their table forms through it.
 */
import type { Player } from '@minecraft/server';

export const BOTS_UI_SERVICE = 'bots.ui';

export interface BotsUiApi {
  /** "Table settings…" ModalForm (host / keeper / owner / op) or the read-only "Table info". */
  openSettings(player: Player, tableKey: string): Promise<void>;
  /** "Private table…" ActionForm. */
  openPrivate(player: Player, tableKey: string): Promise<void>;
}
