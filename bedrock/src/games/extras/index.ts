/**
 * Extra Games module (GAME_DESIGN §11): Coin Flip (Lucky Coin), Wheel of Fortune (block),
 * Scratch Cards (items + Cashier shop), Plinko (block), Dice Duel (dice item: vs house / PvP).
 *
 * Every game runs as a core table session (busy guard, disconnect / distance / casino-off
 * handling): the blocks through the shared `burmaldaholic:table` component, the items through
 * a per-player "virtual" table. Rounds are placed and settled in the same tick (the animation
 * that follows is cosmetic), so a disconnect never leaves a round open; scratch cards keep
 * their progress on the player. Pure rules and RTP tests live in ./logic.
 */
import { type Player, system, world } from '@minecraft/server';
import { type CasinoModule, detach, isCasinoEnabled, t } from '../../core';
import { EXTRAS_SERVICE, type ExtrasApi } from './api';
import { COIN_GAME, coinFlow } from './coin-flip';
import { DICE_GAME, challengeForm, challengesPage, diceFlow, hasIncoming, openDice, startChallenges } from './dice-game';
import { PLINKO_GAME, plinkoFlow, plinkoLeave } from './plinko-game';
import { SCRATCH_GAME, SCRATCH_ITEMS, SCRATCH_USED, openShop, scratchFlow, useScratchCard } from './scratch-game';
import { ctx, gameEnabled, openVirtual, setContext } from './shared';
import { WHEEL_GAME, wheelFlow, wheelLeave } from './wheel-game';
import { registerExtrasPvp } from './pvp';

export const LUCKY_COIN_ID = 'burmaldaholic:lucky_coin';
export const DICE_ID = 'burmaldaholic:dice';
const COIN_COMPONENT = 'burmaldaholic:extras_lucky_coin';
const DICE_COMPONENT = 'burmaldaholic:extras_dice';
const SCRATCH_COMPONENT = 'burmaldaholic:extras_scratch';

/** Run an item action next tick, casino-guarded. */
function itemAction(player: Player, fn: (p: Player) => void): void {
  system.run(() => {
    if (!player.isValid) return;
    if (!isCasinoEnabled()) return player.sendMessage(t('gui.burmaldaholic.error.casino_off'));
    try {
      fn(player);
    } catch (e) {
      ctx().log.error('extras item action failed', e);
    }
  });
}

function openCoin(p: Player): void {
  if (!gameEnabled('extras.coinFlip.enabled')) return p.sendMessage(t('gui.burmaldaholic.error.disabled'));
  openVirtual(p, COIN_GAME);
}

/** Dice used: aiming at a player challenges them, otherwise duel the house. */
const lastDiceUse = new Map<string, number>();
function useDice(p: Player, target?: Player): void {
  if (lastDiceUse.get(p.id) === system.currentTick) return; // onUse + interact in the same tick
  lastDiceUse.set(p.id, system.currentTick);
  const aimed =
    target ??
    (p.getEntitiesFromViewDirection({ maxDistance: 6, type: 'minecraft:player' }).find((h) => h.entity.id !== p.id)?.entity as Player | undefined);
  if (aimed && aimed.typeId === 'minecraft:player') {
    if (!gameEnabled('extras.diceDuel.enabled')) return p.sendMessage(t('gui.burmaldaholic.error.disabled'));
    if (ctx().tables.sessionOf(p)) return p.sendMessage(t('gui.burmaldaholic.error.busy'));
    detach(challengeForm(p, aimed), (e) => ctx().log.error('dice duel challenge form', e));
    return;
  }
  openDice(p, 'house');
}

export const extrasModule: CasinoModule = {
  id: 'extras',
  config: [{ type: 'bool', name: 'enabled', default: true }],

  onStartup(sctx) {
    const t1 = sctx.tables;
    t1.register({ id: COIN_GAME, onOpen: (s, rejoined) => coinFlow(s, rejoined) });
    t1.register({ id: DICE_GAME, onOpen: (s, rejoined) => diceFlow(s, rejoined) });
    t1.register({ id: SCRATCH_GAME, onOpen: (s, rejoined) => scratchFlow(s, rejoined) });
    t1.register({ id: WHEEL_GAME, onOpen: (s, rejoined) => wheelFlow(s, rejoined), onLeave: (s) => wheelLeave(s) });
    t1.register({ id: PLINKO_GAME, onOpen: (s, rejoined) => plinkoFlow(s, rejoined), onLeave: (s) => plinkoLeave(s) });

    const items = sctx.event.itemComponentRegistry;
    items.registerCustomComponent(COIN_COMPONENT, { onUse: (e) => itemAction(e.source, openCoin) });
    items.registerCustomComponent(DICE_COMPONENT, { onUse: (e) => itemAction(e.source, (p) => useDice(p)) });
    items.registerCustomComponent(SCRATCH_COMPONENT, {
      onUse: (e, params) => {
        const kind = (params.params as { kind?: string } | undefined)?.kind === 'gold' ? 'gold' : 'basic';
        itemAction(e.source, (p) => useScratchCard(p, kind));
      },
    });
  },

  onWorldLoad(mctx) {
    registerExtrasPvp(mctx); // PvP modes (docs/architecture/pvp-bots.md)
    setContext(mctx);
    startChallenges();

    // Dice used on a player (interaction path; the onUse raycast covers the rest).
    world.afterEvents.playerInteractWithEntity.subscribe(
      mctx.guard((e) => {
        if (e.target.typeId !== 'minecraft:player' || (e.beforeItemStack ?? e.itemStack)?.typeId !== DICE_ID) return;
        useDice(e.player, e.target as Player);
      }),
    );

    mctx.menu.add({
      id: 'extras.challenges',
      order: 50,
      label: t('gui.burmaldaholic.menu.challenges'),
      icon: 'textures/items/extras/dice',
      visible: () => gameEnabled('extras.diceDuel.enabled') && mctx.config.bool('extras.diceDuel.pvpEnabled'),
      open: (p) => challengesPage(p),
    });
    mctx.cashier.add({
      id: 'extras.shop',
      order: 20,
      label: t('gui.burmaldaholic.cashier.shop'),
      icon: 'textures/items/extras/scratch_card',
      visible: () => gameEnabled('extras.scratch.enabled'),
      open: (p) => openShop(p),
    });
    mctx.hud.addSegment({
      id: 'extras.challenge',
      order: 60,
      render: (p) => (hasIncoming(p) ? t('gui.burmaldaholic.extras.dice.pending') : undefined),
    });

    const api: ExtrasApi = {
      openDiceDuel: (p) => openDice(p, 'house'),
      openDiceMenu: (p) => openDice(p, 'menu'),
      openScratchShop: (p) => detach(openShop(p), (e) => mctx.log.error('scratch shop form', e)),
      openCoinFlip: (p) => openCoin(p),
      items: {
        luckyCoin: LUCKY_COIN_ID,
        dice: DICE_ID,
        scratchCard: SCRATCH_ITEMS.basic,
        scratchCardGold: SCRATCH_ITEMS.gold,
        scratchCardUsed: SCRATCH_USED,
      },
    };
    mctx.services.provide(EXTRAS_SERVICE, api);
  },
};
