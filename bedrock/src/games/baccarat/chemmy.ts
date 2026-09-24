/**
 * Chemin de fer runtime (GAME_DESIGN §20.9, UI.md §14) for `baccarat_table_player_banked`.
 *
 *   BANK_OFFER (candidate: the winning banker → keep / pass, else the next seat clockwise →
 *   take an amount / pass; timeout = Pass) → BETTING (punters bet on Player up to the coverage
 *   or call Banco) → NO_MORE_BETS → SHUFFLE → DEAL (coup persisted with stakes and bank) →
 *   REVEAL → RESULT (settle, rake) → BANK_OFFER. All seats pass → a house coup (§20.5 on the
 *   same shoe, run by BaccaratGame) or wait.
 *
 * Money: the bank and every punter stake are escrowed in the economy's bank sink with
 * economy.transact and saved (./store.ts) on every change; results are paid back from it.
 * The house never pays here; it only takes the rake (owner's bankroll at an owned casino).
 * Every bank / punt choice goes through a BaccaratDecider (human forms today, bots later).
 */
import { type Player, system } from '@minecraft/server';
import { ActionFormData, type ActionFormResponse } from '@minecraft/server-ui';
import {
  HudPriority,
  type LeaveReason,
  NEWLINE,
  type Raw,
  type TableSession,
  chips,
  color,
  duration,
  join,
  lines,
  lit,
  onlinePlayer,
  parseCard,
  promptAmount,
  t,
} from '../../core';
import { MULTIPLAYER_SERVICE, type MultiplayerApi } from '../../multiplayer/api';
import type { BaccaratCoupEntry, BaccaratCoupEvent } from './api';
import type { BaccaratGame, TableRt } from './game';
import { GAME, HUD_CHANNEL } from './shared';
import {
  type BaccaratDecider,
  type BankDecision,
  type BankOfferView,
  type ChemmyBank,
  type Coup,
  type PunterStake,
  type SeatOccupant,
  type Winner,
  BANK_HOLDER_WINS,
  acceptBet,
  applyBanco,
  bancoCheck,
  clockwise,
  coupFromCards,
  coverage,
  dealOrder,
  nextCandidate,
  normalizeBankDecision,
  openCoverage,
  settleChemmy,
  winningNatural,
} from './logic';
import { type StoredChemmy, tableStore } from './store';
import { K, betResultLine, chemmyErrorText, resultRaw, sideName } from './text';

type Phase = 'offer' | 'coup' | 'house' | 'waiting';
type ChemAction = 'bet' | 'banco' | 'clear' | 'ready' | 'offer' | 'rules' | 'refresh' | 'leave';

/** Forms-based decider of a seated human (bots will implement BaccaratDecider elsewhere). */
class HumanDecider implements BaccaratDecider {
  constructor(
    private readonly ctl: ChemmyController,
    private readonly s: TableSession,
  ) {}

  async offerBank(v: BankOfferView): Promise<BankDecision | undefined> {
    const g = this.ctl.game;
    const now = system.currentTick;
    const body: (Raw | undefined)[] = [color('§e', t(`${K}.chemmy.offer`))];
    if (v.keep) body.push(t(`${K}.chemmy.bank`, chips(v.bank)));
    body.push(t(`${K}.chemmy.bank_amount`, chips(v.minBank)), t('gui.burmaldaholic.common.balance', chips(v.balance)));
    body.push(t('gui.burmaldaholic.common.timer', duration(Math.max(0, this.ctl.offerUntil - now))));
    const form = new ActionFormData().title(t(`${K}.title_chemmy`)).body(lines(...body));
    if (v.keep) form.button(t(`${K}.chemmy.keep`, chips(v.bank))).button(t(`${K}.chemmy.pass_bank`));
    else form.button(t(`${K}.chemmy.take`, chips(v.bank))).button(t(`${K}.chemmy.take_other`)).button(t(`${K}.chemmy.pass`));
    const res = await g.show<ActionFormResponse>(this.s, form);
    if (!res || res.selection === undefined) return undefined;
    if (v.keep) return res.selection === 0 ? { kind: 'keep' } : { kind: 'pass' };
    if (res.selection === 0) return { kind: 'take', amount: v.bank };
    if (res.selection === 2) return { kind: 'pass' };
    const amount = await promptAmount(this.s.player, {
      title: t(`${K}.chemmy.bank_amount`, chips(v.minBank)),
      min: v.minBank,
      max: Math.max(v.minBank, v.balance),
      default: v.bank,
      submit: t(`${K}.chemmy.take`, chips(v.bank)),
      info: [t('gui.burmaldaholic.common.balance', chips(v.balance))],
    });
    return amount === undefined ? undefined : { kind: 'take', amount };
  }
}

export class ChemmyController {
  phase: Phase = 'waiting';
  bank: ChemmyBank | undefined;
  lastBankerId: string | undefined;
  candidate: string | undefined;
  keepOffer = false;
  passed = new Set<string>();
  offerUntil = 0;
  private offerSeq = 0;
  stakes: PunterStake[] = [];
  banco: string | undefined;
  cover = 0;
  private idleUntil = 0;
  private waitingSince = 0;
  private lastSeatCount = 0;
  private coupCards: string[] | undefined;
  private lastWinner: Winner | undefined;
  private readonly lastBankOf = new Map<string, number>();
  private readonly results = new Map<string, Raw[]>();

  constructor(
    readonly game: BaccaratGame,
    readonly rt: TableRt,
  ) {
    // A stored escrow was recovered at load (recoverAll): the table always starts clean.
  }

  private get ctx() {
    return this.game.ctx;
  }
  private cfg() {
    const c = this.ctx.config;
    return {
      enabled: c.bool('baccarat.chemmy.enabled'),
      minBank: c.int('baccarat.chemmy.minBank'),
      rake: c.num('baccarat.chemmy.rakePercent'),
      offerTicks: c.int('baccarat.chemmy.bankOfferTicks'),
      idleTicks: c.int('baccarat.chemmy.idleTicks'),
      houseCoup: c.bool('baccarat.chemmy.houseCoupWhenNoBanker'),
    };
  }

  // ---- state queries (used by BaccaratGame) ---------------------------------------------

  isHouseCoup(): boolean {
    return this.phase === 'house';
  }
  isChemmyCoup(): boolean {
    return this.phase === 'coup';
  }
  bettorIds(): string[] {
    return this.stakes.map((s) => s.id);
  }
  holdsChips(): boolean {
    return !!this.bank || this.stakes.length > 0;
  }
  private canPunt(): boolean {
    return this.phase === 'coup' && this.rt.clock.canBet() && !!this.bank;
  }
  private name(id: string): string {
    return this.rt.names.get(id) ?? onlinePlayer(id)?.name ?? '?';
  }
  private occupant(id: string): SeatOccupant | undefined {
    return this.game.occupants(this.rt).find((o) => o.id === id);
  }
  /** The decision source of a seat (bots plug in here). */
  private deciderFor(o: SeatOccupant): BaccaratDecider | undefined {
    const s = this.game.session(this.rt, o.id);
    return o.kind === 'human' && s ? new HumanDecider(this, s) : undefined;
  }

  toStored(): StoredChemmy | undefined {
    if (!this.bank && !this.stakes.length) return undefined;
    return {
      bank: this.bank && { o: this.bank.ownerId, n: this.bank.ownerName, a: this.bank.amount, w: this.bank.wins },
      stakes: this.stakes.map((s) => ({ i: s.id, n: s.name, a: s.amount })),
      banco: this.banco,
      coup: this.coupCards,
    };
  }

  // ---- money ----------------------------------------------------------------------------

  private takeChips(p: Player, amount: number, reason: string): boolean {
    return this.ctx.economy.transact(
      [
        { account: p, delta: -amount },
        { account: 'bank', delta: amount },
      ],
      reason,
    );
  }

  /** Pay escrowed chips back (offline: queued with the message for their next join). */
  private giveChips(id: string, amount: number, reason: string, message?: Raw): void {
    if (amount <= 0) {
      if (message) this.ctx.wagers.tell(id, message);
      return;
    }
    const p = onlinePlayer(id);
    if (p) {
      this.ctx.economy.transact(
        [
          { account: p, delta: amount },
          { account: 'bank', delta: -amount },
        ],
        reason,
      );
      if (message) p.sendMessage(message);
    } else this.ctx.economy.creditById(id, amount, reason, message);
  }

  private returnBank(): void {
    const b = this.bank;
    this.bank = undefined;
    if (b) this.giveChips(b.ownerId, b.amount, 'baccarat.chemmy.bank_return', t('msg.burmaldaholic.baccarat.bank_returned', chips(b.amount)));
    this.game.persist(this.rt);
  }

  private refundStakes(message: (amount: number) => Raw): void {
    const list = this.stakes;
    this.stakes = [];
    this.banco = undefined;
    for (const s of list) this.giveChips(s.id, s.amount, 'baccarat.chemmy.refund', message(s.amount));
    this.game.persist(this.rt);
  }

  // ---- the loop -------------------------------------------------------------------------

  tick(now: number): void {
    const cfg = this.cfg();
    const seated = this.game.occupants(this.rt);
    if (!cfg.enabled) {
      // Chemin de fer switched off: undrawn stakes back, bank back, house coups only.
      if (this.phase !== 'house' && this.rt.clock.canBet()) {
        this.abort('disabled');
        this.phase = 'house';
        this.idleUntil = now; // re-enabled later: offers restart once the table is idle
      }
      return;
    }
    switch (this.phase) {
      case 'waiting':
        if (!seated.length) {
          this.lastSeatCount = 0;
          return;
        }
        if (seated.length !== this.lastSeatCount || now - this.waitingSince >= cfg.offerTicks * 3) this.startOffers();
        this.lastSeatCount = seated.length;
        return;
      case 'offer':
        if (!this.candidate || !seated.some((o) => o.id === this.candidate)) return this.decide(this.offerSeq, { kind: 'pass' });
        if (now >= this.offerUntil) {
          const s = this.game.session(this.rt, this.candidate);
          if (s) this.game.closeFor(s);
          this.decide(this.offerSeq, { kind: 'pass' });
        }
        return;
      case 'coup':
        if (!this.rt.clock.canBet()) return;
        if (!this.bank || !seated.some((o) => o.id === this.bank!.ownerId)) return this.bankerGone();
        if (!this.stakes.length && now >= this.idleUntil) {
          // No punter bet for idleTicks → the bank passes (§20.9).
          this.game.tell(this.rt, t('msg.burmaldaholic.baccarat.chemmy.passed_bank', lit(this.bank.ownerName)));
          this.lastBankerId = this.bank.ownerId;
          this.returnBank();
          this.passed = new Set([this.lastBankerId]);
          this.nextOffer(this.lastBankerId);
        }
        return;
      case 'house':
        if (this.rt.clock.canBet() && !this.rt.slips.size && now >= this.idleUntil && seated.length) this.startOffers();
        return;
    }
  }

  /** BANK_OFFER from the next seat after the last banker (first round: seat 1). */
  startOffers(keepId?: string): void {
    this.phase = 'offer';
    this.stakes = [];
    this.banco = undefined;
    this.rt.clock.reset();
    this.passed = new Set();
    if (keepId && this.bank) return this.offerTo(keepId, true);
    this.nextOffer(this.lastBankerId);
  }

  private nextOffer(after: string | undefined): void {
    const next = nextCandidate(clockwise(this.game.occupants(this.rt)), after, this.passed);
    if (next === undefined) return this.allPassed();
    this.offerTo(next, false);
  }

  private offerView(id: string, keep: boolean): BankOfferView {
    const p = onlinePlayer(id);
    const minBank = this.cfg().minBank;
    const balance = p ? this.ctx.economy.balance(p) : 0;
    const suggested = Math.max(minBank, Math.min(balance, this.lastBankOf.get(id) ?? minBank));
    return { keep, bank: keep && this.bank ? this.bank.amount : suggested, minBank, balance };
  }

  private offerTo(id: string, keep: boolean): void {
    this.phase = 'offer';
    this.candidate = id;
    this.keepOffer = keep;
    this.offerUntil = system.currentTick + this.cfg().offerTicks;
    const seq = ++this.offerSeq;
    const o = this.occupant(id);
    const p = onlinePlayer(id);
    // Nobody who owes the Loan Shark banks (§20.9, §5.8); vetoes (owner, closed, freeze) too.
    if (!keep && p) {
      const refuse = this.game.owes(p) ? t(`${K}.error.pvp_owing`) : this.ctx.wagers.check(p, GAME, this.rt.key);
      const min = this.cfg().minBank;
      if (refuse || this.ctx.economy.balance(p) < min) {
        if (refuse) p.sendMessage(refuse);
        return this.decide(seq, { kind: 'pass' });
      }
    }
    const waiting = t(`${K}.chemmy.waiting_offer`, lit(o?.name ?? this.name(id)));
    for (const s of this.game.sessions(this.rt)) {
      if (s.playerId === id) continue;
      this.ctx.hud.actionbar(s.player, HUD_CHANNEL, waiting, HudPriority.game, 60);
      this.game.reshowMain(s);
    }
    // The candidate may still look at the result form: close it so the offer can show.
    const cs = this.game.session(this.rt, id);
    if (cs) this.game.closeFor(cs);
    this.ask(seq);
  }

  /** Ask the candidate's decider (again, e.g. after they re-opened the table). */
  private ask(seq: number): void {
    const id = this.candidate;
    const o = id ? this.occupant(id) : undefined;
    const d = o ? this.deciderFor(o) : undefined;
    if (!id || !d) return;
    const view = this.offerView(id, this.keepOffer);
    Promise.resolve(d.offerBank(view))
      .then((r) => {
        if (r) this.decide(seq, r);
      })
      .catch((e: unknown) => this.ctx.log.error('baccarat bank offer failed', e));
  }

  private decide(seq: number, raw: BankDecision): void {
    if (seq !== this.offerSeq || this.phase !== 'offer' || !this.candidate) return;
    this.offerSeq++;
    const id = this.candidate;
    const d = normalizeBankDecision(raw, this.offerView(id, this.keepOffer));
    const p = onlinePlayer(id);
    const name = this.name(id);
    if (d.kind === 'keep' && this.bank && this.bank.ownerId === id) {
      this.game.tell(this.rt, t('msg.burmaldaholic.baccarat.chemmy.took_bank', lit(name), chips(this.bank.amount)));
      return this.startBetting();
    }
    if (d.kind === 'take' && p && !this.game.owes(p) && !this.ctx.wagers.check(p, GAME, this.rt.key) && this.takeChips(p, d.amount, 'baccarat.chemmy.bank')) {
      this.bank = { ownerId: id, ownerName: name, amount: d.amount, wins: 0 };
      this.lastBankOf.set(id, d.amount);
      this.game.persist(this.rt);
      this.game.tell(this.rt, t('msg.burmaldaholic.baccarat.chemmy.took_bank', lit(name), chips(d.amount)));
      return this.startBetting();
    }
    // Pass (also: timeout, invalid amount, not affordable).
    this.game.tell(this.rt, t('msg.burmaldaholic.baccarat.chemmy.passed_bank', lit(name)));
    if (this.keepOffer && this.bank?.ownerId === id) {
      this.lastBankerId = id;
      this.returnBank();
      this.passed = new Set([id]);
      this.keepOffer = false;
      return this.nextOffer(id);
    }
    this.passed.add(id);
    this.nextOffer(id);
  }

  private allPassed(): void {
    this.candidate = undefined;
    const cfg = this.cfg();
    this.rt.clock.reset();
    if (cfg.houseCoup && this.game.occupants(this.rt).length) {
      this.phase = 'house';
      this.idleUntil = system.currentTick + cfg.idleTicks;
      this.game.tell(this.rt, color('§7', t(`${K}.chemmy.house_coup`)));
    } else {
      this.phase = 'waiting';
      this.waitingSince = system.currentTick;
      this.lastSeatCount = this.game.occupants(this.rt).length;
    }
    for (const s of this.game.sessions(this.rt)) this.game.reshowMain(s);
  }

  private bankerMax(): number {
    const b = this.bank;
    if (!b) return 0;
    const p = onlinePlayer(b.ownerId);
    return p ? this.game.pvpLimits(p, this.rt).max : b.amount;
  }

  private startBetting(): void {
    this.phase = 'coup';
    this.candidate = undefined;
    this.stakes = [];
    this.banco = undefined;
    this.rt.clock.reset();
    this.cover = coverage(this.bank?.amount ?? 0, this.bankerMax());
    this.idleUntil = system.currentTick + this.cfg().idleTicks;
    this.game.persist(this.rt);
    for (const s of this.game.sessions(this.rt)) this.game.reshowMain(s);
  }

  /** The banker left / disconnected before the deal: stakes back, bank back, next seat (§20.9). */
  private bankerGone(): void {
    this.refundStakes(() => t('msg.burmaldaholic.baccarat.chemmy.banker_left'));
    if (this.bank) this.lastBankerId = this.bank.ownerId;
    this.returnBank();
    this.startOffers();
  }

  // ---- punters --------------------------------------------------------------------------

  private punterLimits(p: Player): { min: number; max: number } {
    return this.game.pvpLimits(p, this.rt);
  }

  private punterCheck(p: Player): Raw | undefined {
    if (!this.ctx.isCasinoEnabled()) return t('gui.burmaldaholic.error.casino_off');
    if (!this.canPunt() || this.banco) return t(`${K}.no_more_bets`);
    if (this.bank?.ownerId === p.id) return t('gui.burmaldaholic.error.invalid_bet_position');
    if (this.game.owes(p)) return t(`${K}.error.pvp_owing`);
    return this.ctx.wagers.check(p, GAME, this.rt.key);
  }

  /** A punter bet on Player; returns [error, info]. */
  punterBet(p: Player, amount: number): [Raw | undefined, Raw | undefined] {
    const refuse = this.punterCheck(p);
    if (refuse) return [refuse, undefined];
    const l = this.punterLimits(p);
    const r = acceptBet(this.stakes, this.cover, p.id, amount, l.min, l.max, !!this.banco);
    if (!r.ok) return [chemmyErrorText(r.error), undefined];
    const balance = this.ctx.economy.balance(p);
    if (r.amount > balance) return [t('gui.burmaldaholic.error.insufficient_funds', chips(balance)), undefined];
    if (!this.takeChips(p, r.amount, 'baccarat.chemmy.stake')) return [t('gui.burmaldaholic.error.insufficient_funds', chips(balance)), undefined];
    const own = this.stakes.find((s) => s.id === p.id);
    if (own) own.amount += r.amount;
    else this.stakes.push({ id: p.id, name: p.name, amount: r.amount });
    this.rt.names.set(p.id, p.name);
    this.rt.clock.onBet(system.currentTick);
    this.rt.clock.unready(p.id);
    this.game.persist(this.rt);
    return [undefined, r.snapped ? t(`${K}.chemmy.snapped`, chips(r.amount)) : undefined];
  }

  /** Banco: match the whole coverage alone; others refunded; betting closes at once. */
  callBanco(p: Player): Raw | undefined {
    const refuse = this.punterCheck(p);
    if (refuse) return refuse;
    const l = this.punterLimits(p);
    const own = this.stakes.find((s) => s.id === p.id)?.amount ?? 0;
    const err = bancoCheck(this.cover, this.ctx.economy.balance(p) + own, l.max, !!this.banco);
    if (err) return chemmyErrorText(err);
    const b = applyBanco(this.stakes, p.id, p.name, this.cover);
    if (b.extra > 0 && !this.takeChips(p, b.extra, 'baccarat.chemmy.banco')) return t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p)));
    this.stakes = b.stakes;
    this.banco = p.id;
    for (const s of b.refunds) this.giveChips(s.id, s.amount, 'baccarat.chemmy.refund'); // told by the Banco broadcast
    this.game.tell(this.rt, color('§6', t('msg.burmaldaholic.baccarat.chemmy.banco_called', lit(p.name))));
    this.rt.clock.onBet(system.currentTick);
    this.rt.clock.closeNow(system.currentTick);
    this.game.persist(this.rt);
    return undefined;
  }

  private clearStake(id: string, message?: Raw): void {
    const s = this.stakes.find((x) => x.id === id);
    if (!s || this.banco === id) return;
    this.stakes = this.stakes.filter((x) => x.id !== id);
    this.rt.clock.unready(id);
    this.giveChips(id, s.amount, 'baccarat.chemmy.refund', message);
    this.game.persist(this.rt);
  }

  // ---- coup -----------------------------------------------------------------------------

  onDealt(c: Coup): void {
    this.coupCards = dealOrder(c).map((x) => `${x.rank}${x.suit}`);
  }

  /** RESULT: pay punters from the escrow, move the bank, take the rake (§20.9). */
  settle(c: Coup): BaccaratCoupEvent {
    const ev = this.game.emptyEvent(this.rt, c, 'chemmy');
    this.results.clear();
    const bank = this.bank;
    const stakes = this.stakes;
    this.stakes = [];
    this.coupCards = undefined;
    const banco = this.banco;
    this.banco = undefined;
    this.lastWinner = c.winner;
    if (!bank) {
      for (const s of stakes) this.giveChips(s.id, s.amount, 'baccarat.chemmy.refund', t('msg.burmaldaholic.baccarat.bets_refunded', chips(s.amount)));
      return ev;
    }
    const cfg = this.cfg();
    const r = settleChemmy(c.winner, stakes, cfg.rake);
    const nat = winningNatural(c);
    const coupNo = this.rt.plate.coupNo;
    const entries: BaccaratCoupEntry[] = [];
    for (const s of stakes) {
      const ret = r.punterReturns.get(s.id) ?? 0;
      this.giveChips(s.id, ret, 'baccarat.chemmy.payout');
      const p = onlinePlayer(s.id);
      const line = t('msg.burmaldaholic.baccarat.coup', coupNo, join(resultRaw(c), lit(' · '), this.game.netRaw(ret - s.amount)));
      if (p) {
        this.ctx.wagers.recordPvp(p, GAME, s.amount, ret - s.amount);
        p.sendMessage(line);
      } else this.ctx.wagers.tell(s.id, t('msg.burmaldaholic.core.auto_completed', line));
      this.results.set(s.id, [betResultLine(sideName('player'), s.amount, ret), this.game.netRaw(ret - s.amount)]);
      if (c.winner === 'player' && nat === 9) {
        p?.sendMessage(color('§6', t('msg.burmaldaholic.baccarat.natural_nine')));
        this.game.unlock(s.id, 'baccarat_natural');
      }
      if (banco === s.id && c.winner === 'player') this.game.unlock(s.id, 'banco');
      entries.push({ playerId: s.id, player: p, bets: { player: s.amount }, staked: s.amount, totalReturn: ret });
    }
    bank.amount += r.bankDelta;
    if (r.rake > 0) this.payRake(r.rake);
    const banker = onlinePlayer(bank.ownerId);
    if (banker && r.matched > 0) this.ctx.wagers.recordPvp(banker, GAME, r.matched, r.bankerNet);
    if (r.matched > 0) {
      if (c.winner === 'banker') this.game.tell(this.rt, t('msg.burmaldaholic.baccarat.chemmy.bank_wins', chips(r.matched), chips(r.rake)));
      else if (c.winner === 'player') this.game.tell(this.rt, t('msg.burmaldaholic.baccarat.chemmy.bank_pays', chips(r.matched)));
    }
    if (c.winner === 'banker' && r.matched > 0) {
      bank.wins++;
      if (bank.wins >= BANK_HOLDER_WINS) this.game.unlock(bank.ownerId, 'bank_holder');
      if (nat === 9) {
        banker?.sendMessage(color('§6', t('msg.burmaldaholic.baccarat.natural_nine')));
        this.game.unlock(bank.ownerId, 'baccarat_natural');
      }
    } else if (c.winner === 'player') bank.wins = 0;
    this.results.set(bank.ownerId, [t(`${K}.chemmy.bank`, chips(bank.amount)), this.game.netRaw(r.bankerNet)]);
    return { ...ev, entries, banker: { id: bank.ownerId, net: r.bankerNet } };
  }

  /** Rake: to the owner's bankroll at an owned casino, otherwise it leaves the economy (§3.5). */
  private payRake(rake: number): void {
    const mp = this.ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE);
    const house = mp?.houseFor(this.rt.key);
    if (house?.kind !== 'bankroll') return;
    const ok = this.ctx.economy.transact(
      [
        { account: { bankroll: house.id }, delta: rake },
        { account: 'bank', delta: -rake },
      ],
      'baccarat.chemmy.rake',
    );
    if (ok) mp?.recordRake(this.rt.key, rake);
  }

  /** After RESULT: rotation (§20.9) or back to offers after a house coup. */
  afterCoup(): void {
    const cfg = this.cfg();
    if (!cfg.enabled) {
      this.returnBank();
      this.phase = 'house';
      return this.game.reopenBetting(this.rt);
    }
    if (this.phase === 'house') {
      this.startOffers();
      return;
    }
    const b = this.bank;
    if (!b) return this.startOffers();
    const seated = this.game.occupants(this.rt).some((o) => o.id === b.ownerId);
    if (this.lastWinner !== 'player' && seated && b.amount >= cfg.minBank) return this.startOffers(b.ownerId);
    this.lastBankerId = b.ownerId;
    this.returnBank();
    this.startOffers();
  }

  /** Table break / casino off / chemin de fer disabled: undrawn stakes back, bank back. */
  abort(reason: 'broken' | 'casino_off' | 'disabled'): void {
    if (this.stakes.length) this.refundStakes((a) => t('msg.burmaldaholic.baccarat.bets_refunded', chips(a)));
    this.returnBank();
    this.coupCards = undefined;
    this.candidate = undefined;
    this.offerSeq++;
    if (reason !== 'disabled') this.phase = 'waiting';
  }

  onJoin(s: TableSession): void {
    this.rt.names.set(s.playerId, s.player.name);
  }

  onLeave(id: string, reason: LeaveReason): void {
    if (this.phase === 'offer' && this.candidate === id) this.decide(this.offerSeq, { kind: 'pass' });
    const b = this.bank;
    if (b && b.ownerId === id && (this.phase === 'offer' || (this.phase === 'coup' && this.rt.clock.canBet()))) return this.bankerGone();
    if (this.canPunt() && this.stakes.some((s) => s.id === id) && (reason === 'leave' || reason === 'distance')) this.clearStake(id, t('msg.burmaldaholic.baccarat.left_refunded'));
  }

  resultLines(id: string): Raw[] | undefined {
    return this.results.get(id);
  }

  // ---- forms ----------------------------------------------------------------------------

  async showMain(s: TableSession, error?: Raw, info?: Raw): Promise<void> {
    const g = this.game;
    const p = s.player;
    if (this.phase === 'offer' && this.candidate === p.id) return this.ask(this.offerSeq);
    const now = system.currentTick;
    const body: (Raw | undefined)[] = [];
    if (error) body.push(color('§c', error));
    if (info) body.push(color('§6', info));
    body.push(...g.statusLines(this.rt, now));
    if (this.phase === 'offer' && this.candidate) body.push(color('§e', t(`${K}.chemmy.waiting_offer`, lit(this.name(this.candidate)))));
    if (this.phase === 'waiting') body.push(color('§7', t('gui.burmaldaholic.common.waiting_players')));
    const b = this.bank;
    const mine = b?.ownerId === p.id;
    if (b) {
      body.push(t(`${K}.chemmy.banker_is`, lit(b.ownerName)), t(`${K}.chemmy.bank`, chips(b.amount)));
      if (this.phase === 'coup') body.push(t(`${K}.chemmy.coverage`, chips(this.cover), chips(openCoverage(this.stakes, this.cover))));
    }
    const own = this.stakes.find((x) => x.id === p.id);
    if (mine) {
      body.push(color('§e', t(`${K}.chemmy.you_bank`)));
      for (const x of this.stakes) body.push(join(lit(' • '), t(`${K}.bet_line`, lit(x.name), chips(x.amount))));
    } else if (this.phase === 'coup') {
      body.push(NEWLINE, color('§l', t(`${K}.your_bets`)));
      body.push(own ? join(lit(' • '), t(`${K}.bet_line`, sideName('player'), chips(own.amount))) : color('§7', t(`${K}.no_bets`)));
      if (!this.rt.clock.canBet() && !own) body.push(color('§7', t(`${K}.waiting_next`)));
    }
    body.push(g.seatedLine(this.rt));
    const l = this.punterLimits(p);
    body.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))), t('gui.burmaldaholic.common.limits', chips(l.min), chips(l.max)));
    body.push(NEWLINE, ...g.historyLines(this.rt));

    const form = new ActionFormData().title(g.title(this.rt)).body(lines(...body));
    const actions: ChemAction[] = [];
    const add = (a: ChemAction, label: Raw) => {
      form.button(label);
      actions.push(a);
    };
    if (this.canPunt() && !mine && !this.banco) {
      add('bet', t(`${K}.chemmy.bet_player`));
      const bancoOk = !bancoCheck(this.cover, this.ctx.economy.balance(p) + (own?.amount ?? 0), l.max, false);
      if (bancoOk) add('banco', t(`${K}.chemmy.banco`, chips(this.cover)));
      if (own) add('clear', t(`${K}.clear_bets`));
      if (own && !this.rt.clock.isReady(p.id)) add('ready', t('gui.burmaldaholic.common.ready'));
    } else add('refresh', t('gui.burmaldaholic.common.ok'));
    add('rules', t('gui.burmaldaholic.common.rules'));
    add('leave', t('gui.burmaldaholic.common.leave'));

    const res = await g.show<ActionFormResponse>(s, form);
    if (!res || res.selection === undefined) return;
    switch (actions[res.selection]) {
      case 'bet':
        return this.showBet(s);
      case 'banco':
        return g.showMain(s, this.callBanco(p));
      case 'clear':
        if (this.canPunt()) this.clearStake(p.id);
        return g.showMain(s);
      case 'ready':
        this.rt.clock.setReady(p.id);
        return g.showMain(s);
      case 'rules':
        return g.showRules(s);
      case 'refresh':
        return g.showMain(s);
      case 'leave':
        return s.leave();
      default:
        return;
    }
  }

  private async showBet(s: TableSession): Promise<void> {
    const p = s.player;
    if (!this.canPunt()) return this.game.showMain(s, t(`${K}.no_more_bets`));
    const l = this.punterLimits(p);
    const own = this.stakes.find((x) => x.id === p.id)?.amount ?? 0;
    const open = openCoverage(this.stakes, this.cover);
    const max = Math.min(open, l.max - own, this.ctx.economy.balance(p));
    const min = Math.max(1, l.min - own);
    if (max < min) return this.game.showMain(s, open <= 0 ? t(`${K}.chemmy.error.coverage`, chips(open)) : t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p))));
    const amount = await promptAmount(p, {
      title: t(`${K}.bet_form_title`, sideName('player')),
      info: [t(`${K}.chemmy.coverage`, chips(this.cover), chips(open)), t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p)))],
      min,
      max,
      default: min,
      submit: t('gui.burmaldaholic.common.place_bet'),
    });
    if (amount === undefined || !s.isActive()) return;
    const [err, info] = this.punterBet(p, amount);
    return this.game.showMain(s, err, info);
  }

  // ---- restart recovery -----------------------------------------------------------------

  /**
   * World load: every saved chemin de fer escrow is closed out (§20.9): a drawn coup is
   * settled (punters paid, rake taken), undrawn stakes are refunded, and the bank always
   * goes back to its owner. Deferred one tick so the multiplayer service (rake) is up.
   */
  static recoverAll(game: BaccaratGame): void {
    system.run(() => {
      for (const [key, st] of Object.entries(tableStore.all())) {
        const c = st.chem;
        if (!c || (!c.bank && !c.stakes?.length)) continue;
        try {
          ChemmyController.recover(game, key, c);
        } catch (e) {
          game.ctx.log.error(`baccarat escrow recovery failed for ${key}`, e);
        }
        tableStore.update(key, (x) => ({ ...x, chem: undefined }));
      }
    });
  }

  private static recover(game: BaccaratGame, key: string, c: StoredChemmy): void {
    const ctx = game.ctx;
    const credit = (id: string, amount: number, reason: string, msg: Raw) => ctx.economy.creditById(id, Math.max(0, Math.floor(amount)), reason, msg);
    const stakes = (c.stakes ?? []).filter((s) => typeof s.i === 'string' && s.a > 0);
    let bankAmount = c.bank ? Math.max(0, Math.floor(c.bank.a)) : 0;
    let coup: Coup | undefined;
    try {
      coup = c.coup && c.bank ? coupFromCards(c.coup.map(parseCard)) : undefined;
    } catch {
      coup = undefined;
    }
    const label = t('gui.burmaldaholic.common.game.chemmy');
    if (coup && c.bank) {
      const r = settleChemmy(coup.winner, stakes.map((s) => ({ id: s.i, name: s.n, amount: s.a })), ctx.config.num('baccarat.chemmy.rakePercent'));
      for (const s of stakes) {
        const ret = r.punterReturns.get(s.i) ?? 0;
        credit(s.i, ret, 'baccarat.chemmy.payout', t('msg.burmaldaholic.core.round_played_out', label, join(resultRaw(coup), lit(' · '), game.netRaw(ret - s.a))));
      }
      bankAmount += r.bankDelta;
      if (r.rake > 0) {
        const mp = ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE);
        const house = mp?.houseFor(key);
        if (house?.kind === 'bankroll' && ctx.economy.transact([{ account: { bankroll: house.id }, delta: r.rake }, { account: 'bank', delta: -r.rake }], 'baccarat.chemmy.rake')) mp?.recordRake(key, r.rake);
      }
    } else for (const s of stakes) credit(s.i, s.a, 'baccarat.chemmy.refund', t('msg.burmaldaholic.baccarat.bets_refunded', chips(s.a)));
    if (c.bank) credit(c.bank.o, bankAmount, 'baccarat.chemmy.bank_return', t('msg.burmaldaholic.baccarat.bank_returned', chips(bankAmount)));
    ctx.log.info(`baccarat: closed the chemin de fer escrow of ${key} (${coup ? 'settled' : 'refunded'})`);
  }
}
