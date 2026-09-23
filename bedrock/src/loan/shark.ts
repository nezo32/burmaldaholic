/**
 * Loan Shark / Piglin Moneylender NPC (GAME_DESIGN §5.1, UI.md §10): right-click opens the Loan
 * screen (greeting variant + status; one button per available product; Pay…; Leave), with a
 * confirmation before any loan. He is invulnerable to players while his screen is open, and
 * when killed (1 emerald) he respawns at his home 1 MCD later. Debt is not affected.
 */
import { type Dimension, type Entity, type Player, type Vector3, system, world } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import {
  type Raw,
  chips,
  color,
  formatDhm,
  join,
  lines,
  lit,
  mathRng,
  promptAmount,
  showForm,
  t,
  unit,
  variant,
  worldJson,
  worldTick,
  detach,
} from '../core';
import { LOAN_SHARK_ID, PIGLIN_MONEYLENDER_ID } from './api';
import { type Product, MCD, VARIANTS, availableProducts, dueAmount, ratePercent } from './logic';
import { type LoanService, dayCount } from './service';

const HOME_PROP = 'burmaldaholic:loan.home';
const RESPAWN_PROP = 'burmaldaholic:loan.respawns';
const SHARK_TYPES = new Set([LOAN_SHARK_ID, PIGLIN_MONEYLENDER_ID]);

interface Respawn {
  type: string;
  dim: string;
  x: number;
  y: number;
  z: number;
  at: number;
}

/** "<Loan Shark> line" chat format for NPC speech. */
export function speech(nameKey: string, line: Raw, nameColor = '§6'): Raw {
  return join(lit(nameColor), lit('<'), t(nameKey), lit('>§r '), line);
}

const say = (key: keyof typeof VARIANTS, ...args: (Raw | number)[]): Raw => variant(mathRng, key, VARIANTS[key], ...args);

export class LoanShark {
  /** Sharks whose screen is open (entity id -> count). */
  private readonly busy = new Map<string, number>();

  constructor(private readonly svc: LoanService) {}

  start(): void {
    const ctx = this.svc.ctx;
    world.afterEvents.playerInteractWithEntity.subscribe(
      ctx.guard((e) => {
        if (SHARK_TYPES.has(e.target.typeId)) detach(this.talk(e.player, e.target), (err) => this.svc.ctx.log.error('loan shark form', err));
      }),
    );
    // Invulnerable to players while a loan screen is open.
    world.beforeEvents.entityHurt.subscribe((e) => {
      if (!this.busy.has(e.hurtEntity.id)) return;
      const src = e.damageSource.damagingEntity;
      if (src?.typeId === 'minecraft:player' || src?.typeId === undefined) e.cancel = true;
    });
    world.afterEvents.entitySpawn.subscribe((e) => {
      if (e.entity.isValid && SHARK_TYPES.has(e.entity.typeId)) this.remember(e.entity);
    });
    world.afterEvents.entityLoad.subscribe((e) => {
      if (e.entity.isValid && SHARK_TYPES.has(e.entity.typeId)) this.remember(e.entity);
    });
    world.afterEvents.entityDie.subscribe(
      (e) => {
        try {
          this.onDeath(e.deadEntity);
        } catch (err) {
          ctx.log.error('shark death', err);
        }
      },
      { entityTypes: [LOAN_SHARK_ID, PIGLIN_MONEYLENDER_ID] },
    );
    system.runInterval(
      ctx.guard(() => this.respawnDue()),
      100,
    );
  }

  // ---- placement & respawn ---------------------------------------------------------------

  spawn(dimension: Dimension, location: Vector3, piglin = false): Entity | undefined {
    try {
      const e = dimension.spawnEntity(piglin ? PIGLIN_MONEYLENDER_ID : LOAN_SHARK_ID, location);
      this.remember(e);
      return e;
    } catch (err) {
      this.svc.ctx.log.warn('cannot spawn loan shark', err);
      return undefined;
    }
  }

  private remember(e: Entity): void {
    if (e.getDynamicProperty(HOME_PROP) === undefined) {
      const { x, y, z } = e.location;
      e.setDynamicProperty(HOME_PROP, JSON.stringify({ dim: e.dimension.id, x, y, z }));
    }
  }

  private onDeath(e: Entity): void {
    let home: { dim: string; x: number; y: number; z: number } | undefined;
    try {
      const raw = e.getDynamicProperty(HOME_PROP);
      home = typeof raw === 'string' ? JSON.parse(raw) : undefined;
    } catch {
      home = undefined;
    }
    if (!home) home = { dim: e.dimension.id, ...e.location };
    const list = worldJson.read<Respawn[]>(RESPAWN_PROP, []);
    list.push({ type: e.typeId, ...home, at: worldTick() + MCD });
    worldJson.write(RESPAWN_PROP, list.slice(-64));
    this.busy.delete(e.id);
  }

  private respawnDue(): void {
    const list = worldJson.read<Respawn[]>(RESPAWN_PROP, []);
    if (!list.length) return;
    const now = worldTick();
    const keep: Respawn[] = [];
    for (const r of list) {
      if (now < r.at || !this.tryRespawn(r)) keep.push(r);
    }
    if (keep.length !== list.length) worldJson.write(RESPAWN_PROP, keep);
  }

  /** True when done (spawned, or someone already stands there). False = chunk not loaded yet. */
  private tryRespawn(r: Respawn): boolean {
    let dim: Dimension;
    try {
      dim = world.getDimension(r.dim);
    } catch {
      return true;
    }
    const loc = { x: r.x, y: r.y, z: r.z };
    try {
      if (!dim.getBlock(loc)) return false;
    } catch {
      return false;
    }
    if (dim.getEntities({ location: loc, maxDistance: 8, type: r.type }).length) return true;
    const e = this.spawn(dim, loc, r.type === PIGLIN_MONEYLENDER_ID);
    return e !== undefined;
  }

  // ---- screens ---------------------------------------------------------------------------

  private async talk(player: Player, shark: Entity): Promise<void> {
    const ctx = this.svc.ctx;
    if (!ctx.config.bool('loan.enabled')) return player.sendMessage(t('gui.burmaldaholic.error.disabled'));
    this.busy.set(shark.id, (this.busy.get(shark.id) ?? 0) + 1);
    try {
      await this.loanScreen(player, shark.typeId === PIGLIN_MONEYLENDER_ID);
    } finally {
      const n = (this.busy.get(shark.id) ?? 1) - 1;
      if (n > 0) this.busy.set(shark.id, n);
      else this.busy.delete(shark.id);
    }
  }

  /** The shark's Loan screen (also used by `/burmaldaholic:loan` near nobody: status only). */
  async loanScreen(player: Player, piglin: boolean, error?: Raw): Promise<void> {
    const svc = this.svc;
    const ctx = svc.ctx;
    this.svc.tick(player);
    const rec = svc.record(player);
    const now = worldTick();
    const tier = ctx.limits.tier(player);
    const rate = svc.rateFor(player);
    const nameKey = piglin ? 'entity.burmaldaholic.piglin_moneylender' : 'entity.burmaldaholic.loan_shark';

    const greeting =
      rec.status === 'default'
        ? say('dialog.burmaldaholic.loan.overdue')
        : piglin
          ? say('dialog.burmaldaholic.loan.piglin_greeting')
          : say('dialog.burmaldaholic.loan.greeting');
    const body: (Raw | undefined)[] = [];
    if (error) body.push(color('§c', error));
    body.push(speech(nameKey, greeting), lit(''), this.statusLine(player));

    const offers: Product[] = [];
    if (rec.status === 'none' && now >= rec.cooldownUntil) {
      const { available, locked } = availableProducts(svc.products(), tier);
      offers.push(...available);
      body.push(t('gui.burmaldaholic.loan.interest', ratePercent(rate)));
      if (rec.goodStanding > 0) {
        const steps = Math.min(rec.goodStanding, ctx.config.int('loan.goodStandingMaxSteps'));
        body.push(t('gui.burmaldaholic.loan.good_standing', rec.goodStanding, ratePercent(steps * ctx.config.num('loan.goodStandingDiscount'))));
      }
      if (locked > 0) body.push(color('§7', t('gui.burmaldaholic.loan.locked_count', locked)), speech(nameKey, say('dialog.burmaldaholic.loan.refuse_vip')));
    }
    body.push(t('gui.burmaldaholic.common.balance', chips(ctx.economy.balance(player))));

    const form = new ActionFormData().title(t(piglin ? 'entity.burmaldaholic.piglin_moneylender' : 'gui.burmaldaholic.loan.title')).body(lines(...body));
    const actions: (() => Promise<void>)[] = [];
    for (const p of offers) {
      form.button(lines(t(`gui.burmaldaholic.loan.product.${p.id}`), t('gui.burmaldaholic.loan.offer', chips(p.principal), chips(dueAmount(p.principal, rate)), unit('day', p.days))));
      actions.push(() => this.confirm(player, p, piglin));
    }
    if (rec.status !== 'none') {
      const payAll = Math.min(rec.owed, ctx.economy.balance(player));
      if (payAll >= rec.owed) {
        form.button(t('gui.burmaldaholic.loan.pay_all', chips(rec.owed)));
        actions.push(async () => this.afterPay(player, piglin, svc.pay(player, rec.owed)));
      }
      form.button(t('gui.burmaldaholic.loan.pay'));
      actions.push(() => this.payScreen(player, () => this.loanScreen(player, piglin)));
    }
    form.button(t('gui.burmaldaholic.common.close'));
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    await actions[res.selection]?.();
  }

  private afterPay(player: Player, piglin: boolean, paid: number): void {
    if (paid > 0 && this.svc.record(player).status === 'none') {
      player.sendMessage(speech(piglin ? 'entity.burmaldaholic.piglin_moneylender' : 'entity.burmaldaholic.loan_shark', say('dialog.burmaldaholic.loan.repaid')));
    }
  }

  private async confirm(player: Player, p: Product, piglin: boolean): Promise<void> {
    const svc = this.svc;
    const due = dueAmount(p.principal, svc.rateFor(player));
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.loan.confirm_title'))
      .body(t('gui.burmaldaholic.loan.confirm', chips(p.principal), chips(due), unit('day', p.days)))
      .button(t('gui.burmaldaholic.loan.sign'))
      .button(t('gui.burmaldaholic.common.cancel'));
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection !== 0) return this.loanScreen(player, piglin);
    const err = svc.take(player, p);
    if (err) return this.loanScreen(player, piglin, err);
    player.sendMessage(speech(piglin ? 'entity.burmaldaholic.piglin_moneylender' : 'entity.burmaldaholic.loan_shark', say('dialog.burmaldaholic.loan.given')));
    player.playSound('random.orb');
  }

  /** Status line: none / active (owed, time left) / default / cooldown. */
  statusLine(player: Player): Raw {
    const rec = this.svc.record(player);
    const now = worldTick();
    if (rec.status === 'active') {
      const [d, hm] = formatDhm(rec.deadlineTick - now);
      return color('§e', t('gui.burmaldaholic.loan.status.active', chips(rec.owed), t('hud.burmaldaholic.time.dhm', d, lit(hm))));
    }
    if (rec.status === 'default') return color('§c', t('gui.burmaldaholic.loan.status.default', chips(rec.owed)));
    if (now < rec.cooldownUntil) return color('§7', t('gui.burmaldaholic.loan.status.cooldown', dayCount(rec.cooldownUntil - now)));
    return color('§a', t('gui.burmaldaholic.loan.status.none'));
  }

  /** Pay… (amount slider + exact field), 1 … min(balance, owed). */
  async payScreen(player: Player, back?: () => Promise<void>): Promise<void> {
    const svc = this.svc;
    const rec = svc.record(player);
    const balance = svc.ctx.economy.balance(player);
    const max = Math.min(balance, rec.owed);
    if (rec.status === 'none') return back?.();
    if (max < 1) {
      player.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(balance)));
      return;
    }
    const amount = await promptAmount(player, {
      title: t('gui.burmaldaholic.loan.pay_amount'),
      info: [this.statusLine(player), t('gui.burmaldaholic.common.balance', chips(balance))],
      min: 1,
      max,
      default: max,
      sliderLabel: t('gui.burmaldaholic.loan.pay_amount'),
      submit: t('gui.burmaldaholic.loan.pay'),
    });
    if (amount === undefined) return back?.();
    const paid = svc.pay(player, amount);
    if (paid <= 0) player.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(svc.ctx.economy.balance(player))));
  }

  /** Casino Card → Loan (UI.md §2): status, owed, countdown, Pay. No new loans here. */
  async statusScreen(player: Player): Promise<void> {
    const svc = this.svc;
    svc.tick(player);
    const rec = svc.record(player);
    const body: Raw[] = [this.statusLine(player), t('gui.burmaldaholic.common.balance', chips(svc.ctx.economy.balance(player)))];
    if (rec.goodStanding > 0) {
      const steps = Math.min(rec.goodStanding, svc.ctx.config.int('loan.goodStandingMaxSteps'));
      body.push(t('gui.burmaldaholic.loan.good_standing', rec.goodStanding, ratePercent(steps * svc.ctx.config.num('loan.goodStandingDiscount'))));
    }
    const form = new ActionFormData().title(t('gui.burmaldaholic.loan.title')).body(lines(...body));
    const actions: (() => Promise<void>)[] = [];
    if (rec.status !== 'none') {
      if (svc.ctx.economy.balance(player) >= rec.owed) {
        form.button(t('gui.burmaldaholic.loan.pay_all', chips(rec.owed)));
        actions.push(async () => void svc.pay(player, rec.owed));
      }
      form.button(t('gui.burmaldaholic.loan.pay'));
      actions.push(() => this.payScreen(player, () => this.statusScreen(player)));
    }
    form.button(t('gui.burmaldaholic.common.close'));
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    await actions[res.selection]?.();
  }
}
