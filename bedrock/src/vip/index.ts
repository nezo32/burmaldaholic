/**
 * VIP module (GAME_DESIGN §12, plus the daily contracts of §3.4.4, which core does not ship on
 * Bedrock): lifetime-wagered tiers feeding core limits, promotions, cashback, cosmetics,
 * HUD segment, Wallet / VIP / Contracts pages and the vip_* milestones.
 */
import { type CasinoModule, t } from '../core';
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
