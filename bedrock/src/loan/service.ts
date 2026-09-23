/**
 * Loan bookkeeping on top of core: per-player loan records (world-bound, survive death, relog,
 * dimension and difficulty changes), taking and repaying, deadline warnings, default, late fees,
 * garnishment (economy.onChange), Asset Freeze, the HUD segment and core's debt provider.
 */
import { type Player, world } from '@minecraft/server';
import {
  type ModuleContext,
  type Raw,
  HudPriority,
  chips,
  color,
  formatDhm,
  lit,
  readJson,
  t,
  unit,
  worldJson,
  worldTick,
  writeJson,
} from '../core';
import {
  type AdvanceConfig,
  type Band,
  type LoanRecord,
  type Product,
  type RateConfig,
  MCD,
  advance,
  applyPayment,
  difficultyBand,
  emptyRecord,
  freezeSteps,
  garnishAmount,
  isGarnishable,
  loanRate,
  nextBoundary,
  normalizeRecord,
  parseDifficulty,
  parseProducts,
  seizeAmount,
  shiftTimers,
  scheduleFirstWave,
  takeError,
  takeLoan,
} from './logic';

/** Per-player loan record (JSON) on the player entity; mirrored in a world index for dormancy shifts. */
const RECORD_PROP = 'burmaldaholic:loan.record';
const INDEX_PROP = 'burmaldaholic:loan.debtors';
const LAST_TICK_PROP = 'burmaldaholic:loan.last_tick';

export type DebtListener = (player: Player, rec: LoanRecord, prevOwed: number) => void;

export class LoanService {
  private readonly cache = new Map<string, LoanRecord>();
  private readonly debtListeners: DebtListener[] = [];
  private garnishing = false;

  constructor(readonly ctx: ModuleContext) {}

  // ---- state ----------------------------------------------------------------------------

  /** Loans are live: casino mode on and `loan.enabled`. Otherwise everything is dormant. */
  active(): boolean {
    return this.ctx.isCasinoEnabled() && this.ctx.config.bool('loan.enabled');
  }

  record(player: Player): LoanRecord {
    let r = this.cache.get(player.id);
    if (!r) {
      r = normalizeRecord(readJson<unknown>(player, RECORD_PROP, undefined));
      this.cache.set(player.id, r);
    }
    return r;
  }

  /** Persist; fires debt listeners when owed changes (squads switch to PAID at 0). */
  save(player: Player, rec: LoanRecord): void {
    const prev = this.record(player);
    this.cache.set(player.id, rec);
    writeJson(player, RECORD_PROP, rec);
    const idx = new Set(worldJson.read<string[]>(INDEX_PROP, []));
    const had = idx.has(player.id);
    if (rec.status !== 'none' && !had) idx.add(player.id);
    else if (rec.status === 'none' && had && rec.cooldownUntil <= worldTick()) idx.delete(player.id);
    if (idx.has(player.id) !== had) worldJson.write(INDEX_PROP, [...idx]);
    if (prev.owed !== rec.owed || prev.status !== rec.status) for (const l of this.debtListeners) l(player, rec, prev.owed);
  }

  onDebtChange(l: DebtListener): void {
    this.debtListeners.push(l);
  }

  forget(playerId: string): void {
    this.cache.delete(playerId);
  }

  owed(player: Player): number {
    return this.active() ? this.record(player).owed : 0;
  }

  inDefault(player: Player): boolean {
    return this.active() && this.record(player).status === 'default';
  }

  // ---- config ---------------------------------------------------------------------------

  band(): Band {
    return difficultyBand(parseDifficulty(String(world.getDifficulty())), world.isHardcore);
  }

  isPeaceful(): boolean {
    return !world.isHardcore && parseDifficulty(String(world.getDifficulty())) === 'peaceful';
  }

  /** Collectors hunt defaulters; otherwise (Peaceful or collectors off) Asset Freeze applies. */
  collectorsMode(): boolean {
    return this.ctx.config.bool('loan.collectors.enabled') && !this.isPeaceful();
  }

  frozen(player: Player): boolean {
    return this.inDefault(player) && !this.collectorsMode();
  }

  products(): Product[] {
    return parseProducts(this.ctx.config.json('loan.products'));
  }

  rateConfig(): RateConfig {
    const c = this.ctx.config;
    return {
      base: c.num(`loan.rate.${this.band()}`),
      goodStandingDiscount: c.num('loan.goodStandingDiscount'),
      goodStandingMaxSteps: c.int('loan.goodStandingMaxSteps'),
      minRate: c.num('loan.minRate'),
      platinumDiscount: c.num('loan.platinumDiscount'),
    };
  }

  rateFor(player: Player): number {
    return loanRate(this.rateConfig(), this.record(player).goodStanding, this.ctx.limits.tier(player));
  }

  private advanceConfig(): AdvanceConfig {
    const c = this.ctx.config;
    const warn = c.json<number[]>('loan.warningTicks');
    return {
      lateFee: c.num(`loan.lateFee.${this.band()}`),
      capMultiplier: c.num('loan.lateFeeCapMultiplier'),
      warningTicks: Array.isArray(warn) ? warn.filter((x) => typeof x === 'number') : [24000, 2400],
    };
  }

  // ---- taking / paying ------------------------------------------------------------------

  /** Take a product (all anti-abuse checks). Returns an error text or undefined on success. */
  take(player: Player, product: Product): Raw | undefined {
    if (!this.active()) return t('gui.burmaldaholic.error.disabled');
    const now = worldTick();
    const rec = this.record(player);
    const err = takeError(rec, product, this.ctx.limits.tier(player), now);
    if (err === 'in_default') return t('gui.burmaldaholic.error.in_default');
    if (err === 'one_at_a_time') return t('msg.burmaldaholic.loan.one_at_a_time');
    if (err === 'cooldown') return t('msg.burmaldaholic.loan.cooldown', dayCount(rec.cooldownUntil - now));
    if (err === 'vip') return t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(product.minTier));
    const next = takeLoan(rec, product, this.rateFor(player), now);
    this.save(player, next);
    this.ctx.economy.credit(player, product.principal, 'loan.take');
    this.ctx.achievements.unlock(player, 'loan_taken');
    player.sendMessage(color('§6', t('msg.burmaldaholic.loan.taken', chips(product.principal), chips(next.due), Math.floor(next.deadlineTick / MCD))));
    this.ctx.log.info(`${player.name} took loan ${product.principal} (due ${next.due})`);
    return undefined;
  }

  /** Pay up to `amount` from the balance. Returns chips paid (0 if nothing to pay / no funds). */
  pay(player: Player, amount: number, reason = 'loan.pay', announce = true): number {
    if (!this.active()) return 0;
    const rec = this.record(player);
    const want = Math.min(Math.floor(amount), rec.owed, this.ctx.economy.balance(player));
    if (!(want > 0)) return 0;
    if (!this.ctx.economy.debit(player, want, reason)) return 0;
    return this.applyToDebt(player, want, announce);
  }

  /**
   * Reduce the debt by chips already taken from the player (payment, garnishment, seizure,
   * repossessed item). `announce` sends the paid / repaid messages.
   */
  applyToDebt(player: Player, amount: number, announce: boolean): number {
    const rec = this.record(player);
    const r = applyPayment(rec, amount, worldTick(), this.ctx.config.int('loan.defaultCooldownDays'));
    if (r.paid <= 0) return 0;
    this.save(player, r.rec);
    if (r.closed) {
      player.sendMessage(color('§a', t(r.onTime ? 'msg.burmaldaholic.loan.repaid_on_time' : 'msg.burmaldaholic.loan.repaid')));
      if (r.onTime) this.ctx.achievements.unlock(player, 'clean_slate');
      this.ctx.hud.clear(player, 'loan.status');
    } else if (announce) {
      player.sendMessage(t('msg.burmaldaholic.loan.paid_partial', chips(r.paid), chips(r.rec.owed)));
    }
    return r.paid;
  }

  /** Operator: replace the record (set/clear/force default). */
  adminReplace(player: Player, rec: LoanRecord): void {
    this.save(player, rec);
  }

  // ---- time -----------------------------------------------------------------------------

  /** Every second per online player: warnings, default, late fees, Asset Freeze. */
  tick(player: Player): void {
    const now = worldTick();
    const rec = this.record(player);
    if (rec.status === 'none') return;
    const { rec: adv, events } = advance(rec, now, this.advanceConfig());
    let next = adv;
    const owedBefore = rec.owed;
    for (const w of events.warnings) {
      player.sendMessage(color('§e', t(w >= MCD ? 'msg.burmaldaholic.loan.warning_day' : 'msg.burmaldaholic.loan.warning_final', chips(next.owed))));
      player.playSound('note.pling');
    }
    if (events.defaulted) {
      this.ctx.hud.title(player, color('§c', t('msg.burmaldaholic.loan.defaulted_title')), undefined, 5, 60, 10);
      player.sendMessage(color('§c', t('msg.burmaldaholic.loan.defaulted', chips(next.owed))));
      this.ctx.achievements.unlock(player, 'knock_knock');
      player.playSound('mob.evocation_illager.prepare_attack');
      if (this.collectorsMode()) next = scheduleFirstWave(next, now, this.ctx.config.int('loan.firstWaveDelayTicks'));
    }
    let owedRunning = owedBefore;
    for (const fee of events.lateFees) {
      owedRunning += fee;
      player.sendMessage(color('§c', t('msg.burmaldaholic.loan.late_fee', chips(fee), chips(Math.min(owedRunning, next.owed)))));
    }
    if (next.status === 'default') {
      if (this.collectorsMode()) {
        // Switched from Asset Freeze to collectors mid-default: next wave at the next boundary.
        if (next.nextWaveTick <= 0) next = { ...next, nextWaveTick: nextBoundary(next.deadlineTick, now) };
        if (next.freezeMark >= 0) next = { ...next, freezeMark: -1 };
      } else {
        const f = freezeSteps(next, now, events.defaulted);
        next = f.rec;
        if (next.nextWaveTick > 0) next = { ...next, nextWaveTick: 0, queued: false };
        if (f.seizures > 0) {
          this.save(player, next);
          for (let i = 0; i < f.seizures; i++) this.freezeSeize(player);
          return;
        }
      }
    }
    if (next !== rec) this.save(player, next);
  }

  private freezeSeize(player: Player): void {
    const rec = this.record(player);
    const seize = seizeAmount(this.ctx.economy.balance(player), this.ctx.config.int('loan.peacefulSeizePercent'), rec.owed);
    if (seize > 0 && this.ctx.economy.debit(player, seize, 'loan.freeze')) this.applyToDebt(player, seize, false);
    player.sendMessage(color('§c', t('msg.burmaldaholic.loan.asset_freeze', chips(seize))));
  }

  /**
   * While dormant (casino mode or loans off) nothing may happen, so when loans come back all
   * pending timers move forward by the dormant time. Called every second while active.
   */
  shiftDormancy(): void {
    const now = worldTick();
    const last = worldJson.read<number>(LAST_TICK_PROP, now);
    worldJson.write(LAST_TICK_PROP, now);
    const gap = now - last;
    if (gap <= 200) return; // normal cadence (and lag spikes) are not dormancy
    const ids = new Set(worldJson.read<string[]>(INDEX_PROP, []));
    for (const p of world.getAllPlayers()) {
      if (!ids.has(p.id)) continue;
      this.save(p, shiftTimers(this.record(p), gap));
      ids.delete(p.id);
    }
    // Offline debtors: shifted on their next join.
    const pending = worldJson.read<Record<string, number>>(`${LAST_TICK_PROP}.pending`, {});
    for (const id of ids) pending[id] = (pending[id] ?? 0) + gap;
    worldJson.write(`${LAST_TICK_PROP}.pending`, pending);
  }

  /** Apply a dormancy shift recorded while this player was offline. */
  onJoin(player: Player): void {
    const pending = worldJson.read<Record<string, number>>(`${LAST_TICK_PROP}.pending`, {});
    const gap = pending[player.id];
    if (!gap) return;
    delete pending[player.id];
    worldJson.write(`${LAST_TICK_PROP}.pending`, pending);
    this.save(player, shiftTimers(this.record(player), gap));
  }

  /** Mark "now" as active without shifting (first activation). */
  touch(): void {
    worldJson.write(LAST_TICK_PROP, worldTick());
  }

  // ---- garnishment ----------------------------------------------------------------------

  /** economy.onChange: in default, a share of every income credit goes to the debt first. */
  onBalanceChange(player: Player, delta: number, reason: string): void {
    if (this.garnishing || delta <= 0 || !isGarnishable(reason) || !this.inDefault(player)) return;
    const pct = this.frozen(player) ? 100 : this.ctx.config.int('loan.garnishPercent');
    const take = garnishAmount(delta, pct, this.record(player).owed);
    if (take <= 0) return;
    this.garnishing = true;
    try {
      if (!this.ctx.economy.debit(player, take, 'loan.garnish')) return;
      this.applyToDebt(player, take, false);
      this.ctx.hud.actionbar(player, 'loan.garnish', color('§c', t('msg.burmaldaholic.loan.garnished', chips(take))), HudPriority.alert, 60);
    } finally {
      this.garnishing = false;
    }
  }

  // ---- HUD ------------------------------------------------------------------------------

  /** Status-line part: "Loan: 1d 04:12 · owed 600" / blinking red "OVERDUE · owed 780". */
  hudSegment(player: Player): Raw | undefined {
    if (!this.active()) return undefined;
    const rec = this.record(player);
    if (rec.status === 'none') return undefined;
    if (rec.status === 'default') {
      const blink = Math.floor(worldTick() / 20) % 2 === 0 ? '§c' : '§4';
      return color(blink, t('hud.burmaldaholic.loan_default', rec.owed));
    }
    const [d, hm] = formatDhm(rec.deadlineTick - worldTick());
    return color('§e', t('hud.burmaldaholic.loan', t('hud.burmaldaholic.time.dhm', d, lit(hm)), rec.owed));
  }

  /** A fresh record (used by admin clear on players who never had one). */
  static empty(): LoanRecord {
    return emptyRecord();
  }
}

/** Whole MCD count for "no loans for 3 days" (at least 1 while any time is left). */
export const dayCount = (ticks: number): Raw => unit('day', Math.max(1, Math.ceil(ticks / MCD)));
