/**
 * VIP / Wallet / Contracts pages of the Casino Menu (UI.md §2) and the Cashier Contracts tab.
 */
import type { Player } from '@minecraft/server';
import { ActionFormData, MessageFormData } from '@minecraft/server-ui';
import { type ModuleContext, type Raw, chips, chipsAcc, color, duration, formatNumber, formatSigned, join, lines, lit, showForm, t, worldTick } from '../core';
import type { ContractsService } from './contracts';
import { MAX_TIER, type Perk, perksAt, progress, thresholdOf } from './logic';
import type { VipService } from './vip';

export class VipUi {
  constructor(
    private readonly ctx: ModuleContext,
    private readonly vip: VipService,
    private readonly contracts: ContractsService,
  ) {}

  private streakLine(p: Player): Raw {
    const s = this.ctx.streak.of(p);
    return s === 0
      ? t('gui.burmaldaholic.menu.wallet.streak_none')
      : t('gui.burmaldaholic.menu.wallet.streak', t(s > 0 ? 'hud.burmaldaholic.streak.lucky' : 'hud.burmaldaholic.streak.unlucky', Math.abs(s)));
  }

  /** "To Gold: 12 400 / 25 000 wagered" or "Top tier reached". */
  progressLine(p: Player): Raw {
    const w = this.vip.wagered(p);
    const pr = progress(w, this.vip.thresholds(), this.vip.tier(p));
    if (pr.next === undefined || pr.target === undefined) return color('§6', t('gui.burmaldaholic.vip.max_tier'));
    return t('gui.burmaldaholic.vip.progress', this.ctx.limits.tierName(pr.next), lit(formatNumber(Math.floor(w))), lit(formatNumber(pr.target)));
  }

  /** Text progress bar (20 cells). */
  private bar(p: Player): Raw | undefined {
    const pr = progress(this.vip.wagered(p), this.vip.thresholds(), this.vip.tier(p));
    if (pr.next === undefined) return undefined;
    const filled = Math.round(pr.fraction * 20);
    return lit(`§a${'|'.repeat(filled)}§8${'|'.repeat(20 - filled)}§r`);
  }

  async wallet(p: Player): Promise<void> {
    const { ctx, vip } = this;
    const tier = vip.tier(p);
    const today = vip.ledger(p);
    const net = today.returned - today.staked;
    const body = lines(
      t('gui.burmaldaholic.common.balance', chips(ctx.economy.balance(p))),
      t('gui.burmaldaholic.menu.wallet.lifetime', chips(Math.floor(vip.wagered(p)))),
      t('gui.burmaldaholic.menu.wallet.today', color(net > 0 ? '§a' : net < 0 ? '§c' : '§7', lit(formatSigned(net)))),
      this.streakLine(p),
      lit(' '),
      t('gui.burmaldaholic.vip.current', ctx.limits.tierName(tier)),
      t('gui.burmaldaholic.vip.max_bet', chips(vip.maxBetOf(tier))),
      this.progressLine(p),
      this.bar(p),
    );
    const form = new ActionFormData().title(t('gui.burmaldaholic.menu.wallet')).body(body);
    const buttons: (() => Promise<void> | void)[] = [];
    form.button(t('gui.burmaldaholic.vip.title'), 'textures/items/diamond');
    buttons.push(() => this.status(p));
    if (this.contracts.enabled()) {
      form.button(t('gui.burmaldaholic.menu.contracts'), 'textures/items/paper');
      buttons.push(() => this.contractsPage(p));
    }
    form.button(t('gui.burmaldaholic.common.back'));
    buttons.push(() => ctx.menu.open(p));
    const res = await showForm(p, form);
    if (!res || res.canceled || res.selection === undefined) return;
    await buttons[res.selection]?.();
  }

  private perkText(perk: Perk): Raw {
    if ('chips' in perk) return t(perk.key, chips(perk.chips));
    if ('percent' in perk) return t(perk.key, perk.percent);
    if ('count' in perk) return t(perk.key, perk.count);
    return t(perk.key);
  }

  /** All tiers with thresholds, max bets and perks; the player's tier is highlighted. */
  async status(p: Player): Promise<void> {
    const { ctx, vip } = this;
    const tier = vip.tier(p);
    const params = vip.perkParams();
    const th = vip.thresholds();
    const parts: Raw[] = [t('gui.burmaldaholic.vip.current', ctx.limits.tierName(tier)), this.progressLine(p)];
    for (let i = 0; i <= MAX_TIER; i++) {
      parts.push(lit(' '));
      parts.push(
        join(
          lit(i === tier ? '§l> ' : i < tier ? '§a+ ' : '§8- '),
          t('gui.burmaldaholic.vip.tier_line', ctx.limits.tierName(i), chips(thresholdOf(i, th)), chips(vip.maxBetOf(i))),
        ),
      );
      for (const perk of perksAt(i, params)) parts.push(join(lit(i <= tier ? '   §7• §r' : '   §8• '), this.perkText(perk)));
    }
    const form = new ActionFormData().title(t('gui.burmaldaholic.vip.title')).body(lines(...parts));
    form.button(t('gui.burmaldaholic.common.back'));
    const res = await showForm(p, form);
    if (!res || res.canceled) return;
    await this.wallet(p);
  }

  async contractsPage(p: Player, error?: Raw): Promise<void> {
    const { contracts } = this;
    if (!contracts.enabled()) return;
    const s = contracts.state(p);
    const left = 24000 - (worldTick() % 24000);
    const cost = contracts.rerollCost();
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.contracts.title'))
      .body(lines(error ? color('§c', error) : undefined, t('gui.burmaldaholic.contracts.resets_in', duration(left)), cost > 0 ? t('gui.burmaldaholic.contracts.reroll', chips(cost)) : undefined));
    for (const c of s.list) {
      const task = t(`gui.burmaldaholic.contracts.task.${c.id}`, c.target);
      const status = c.done
        ? color('§2', t('gui.burmaldaholic.contracts.done'))
        : join(t('gui.burmaldaholic.contracts.progress', Math.floor(c.progress), c.target), lit(' · '), t('gui.burmaldaholic.contracts.reward', chips(c.reward)));
      form.button(lines(task, status), c.done ? 'textures/items/emerald' : 'textures/items/paper');
    }
    form.button(t('gui.burmaldaholic.common.close'));
    const res = await showForm(p, form);
    if (!res || res.canceled || res.selection === undefined) return;
    const c = s.list[res.selection];
    if (!c) return;
    if (c.done) return this.contractsPage(p);
    if (c.rerolled) return this.contractsPage(p, t('gui.burmaldaholic.contracts.rerolled_already'));
    const confirm = new MessageFormData()
      .title(t('gui.burmaldaholic.contracts.title'))
      .body(lines(t(`gui.burmaldaholic.contracts.task.${c.id}`, c.target), t('gui.burmaldaholic.contracts.reward', chips(c.reward))))
      .button1(t('gui.burmaldaholic.contracts.reroll', chips(cost)))
      .button2(t('gui.burmaldaholic.common.back'));
    const ok = await showForm(p, confirm);
    if (!ok || ok.canceled || ok.selection !== 0) return this.contractsPage(p);
    if (!this.ctx.isCasinoEnabled()) return;
    const r = contracts.doReroll(p, res.selection);
    if (!r.ok) {
      const msg =
        r.reason === 'funds'
          ? t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p)))
          : r.reason === 'rerolled'
            ? t('gui.burmaldaholic.contracts.rerolled_already')
            : undefined;
      return this.contractsPage(p, msg);
    }
    p.sendMessage(t('msg.burmaldaholic.contracts.rerolled', chipsAcc(r.cost)));
    return this.contractsPage(p);
  }
}
