/**
 * VIP module (GAME_DESIGN §12, plus the daily contracts of §3.4.4, which core does not ship on
 * Bedrock): lifetime-wagered tiers feeding core limits, promotions, cashback, cosmetics,
 * HUD segment, Wallet / VIP / Contracts pages and the vip_* milestones.
 */
import { system } from '@minecraft/server';
import { type CasinoModule, t } from '../core';
import { SLOTS_SERVICE, type SlotsApi } from '../games/slots/api';
import { VIP_SERVICE, type VipApi } from './api';
import { ContractsService } from './contracts';
import { isContractId } from './logic';
import { VipUi } from './ui';
import { VipService } from './vip';

export const vipModule: CasinoModule = {
  id: 'vip',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  onStartup(ctx) {
    ctx.registerCommand({
      name: 'vip',
      description: 'Show your VIP status and perks',
      run: (player) => {
        if (player) void ui?.status(player);
      },
    });
    ctx.registerCommand({
      name: 'contracts',
      description: 'Show your daily contracts',
      run: (player) => {
        if (player) void ui?.contractsPage(player);
      },
    });
  },
  onWorldLoad(ctx) {
    const vip = new VipService(ctx);
    const contracts = new ContractsService(ctx, vip);
    vip.contractHook = (p, id, n) => contracts.progress(p, id, n);
    vip.onPromoted((e) => contracts.topUp(e.player));
    vip.start();
    contracts.start();
    ui = new VipUi(ctx, vip, contracts);
    const pages = ui;

    const api: VipApi = vip.api({
      reportContract: (p, id, amount = 1) => {
        if (!isContractId(id)) return;
        contracts.registerSource(id);
        contracts.progress(p, id, amount);
      },
      registerContractSource: (id) => contracts.registerSource(id),
    });
    ctx.services.provide(VIP_SERVICE, api);

    // SLOTS.md §8.7 `slots_feature`: a spin that triggered free spins or a bonus game (a bought feature does not
    // count; PvP spins never reach the slots spin stream). Services are provided during onWorldLoad → next tick.
    system.run(
      ctx.guard(() => {
        const slots = ctx.services.get<SlotsApi>(SLOTS_SERVICE);
        if (!slots) return;
        contracts.registerSource('slots_feature');
        slots.onSpin((e) => {
          if (e.featureTriggered && !e.bought && e.player.isValid) contracts.progress(e.player, 'slots_feature', 1);
        });
      }),
    );

    ctx.menu.add({ id: 'vip.wallet', order: 10, label: t('gui.burmaldaholic.menu.wallet'), icon: 'textures/items/gold_nugget', open: (p) => pages.wallet(p) });
    ctx.menu.add({
      id: 'vip.contracts',
      order: 20,
      label: t('gui.burmaldaholic.menu.contracts'),
      icon: 'textures/items/paper',
      visible: () => contracts.enabled(),
      open: (p) => pages.contractsPage(p),
    });
    ctx.cashier.add({
      id: 'vip.contracts',
      order: 10,
      label: t('gui.burmaldaholic.menu.contracts'),
      icon: 'textures/items/paper',
      visible: () => contracts.enabled(),
      open: (p) => pages.contractsPage(p),
    });
  },
};

let ui: VipUi | undefined;
