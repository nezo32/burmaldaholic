/**
 * Public API of the extras module for other modules (types only + service name).
 * Provided in onWorldLoad: ctx.services.provide(EXTRAS_SERVICE, impl).
 */
import type { Player } from '@minecraft/server';

export const EXTRAS_SERVICE = 'extras';

export interface ExtrasApi {
  /** Dice Duel vs the house (craps table side menu, §11.5). */
  openDiceDuel(player: Player): void;
  /** Dice Duel hub (vs house / challenge a player / pending challenges). */
  openDiceMenu(player: Player): void;
  /** Scratch-card shop (croupier NPCs of worldgen, §16). */
  openScratchShop(player: Player): void;
  /** Coin Flip form (same as using a Lucky Coin). */
  openCoinFlip(player: Player): void;
  /** Item ids defined by extras (loot tables, shops). */
  readonly items: {
    luckyCoin: string;
    dice: string;
    scratchCard: string;
    scratchCardGold: string;
    scratchCardUsed: string;
  };
}
