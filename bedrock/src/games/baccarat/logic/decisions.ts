/**
 * Seat occupants and the decision interface of a baccarat table. PURE (types + tiny helpers).
 *
 * A seat holds an abstract occupant — today always a human player, later possibly a virtual
 * bot participant with its own stake source (docs/design/BOTS.md, shared core bot framework).
 * Every in-round choice goes through a `BaccaratDecider`: the runtime asks, a human answers
 * through forms, a bot policy answers synchronously. Punto Banco itself has no in-coup
 * decisions (the tableau is fixed); what is decided is WHAT to bet (house coups), and at a
 * chemin de fer table whether to take / keep / pass the bank and how to punt (bet or Banco).
 */
import type { Slip } from './bets';

export type OccupantKind = 'human' | 'bot';

export interface SeatOccupant {
  /** player id for humans, a stable virtual id for bots */
  readonly id: string;
  readonly name: string;
  readonly kind: OccupantKind;
  /** 1-based seat number (clockwise order) */
  readonly seat: number;
}

/** What a bank candidate sees (§20.9 BANK_OFFER). */
export interface BankOfferView {
  /** true: a winning banker may keep `bank`; false: a fresh offer (take an amount or pass) */
  readonly keep: boolean;
  /** the current bank when keeping, else the suggested amount (last bank or min) */
  readonly bank: number;
  readonly minBank: number;
  /** the candidate's spendable chips */
  readonly balance: number;
}

export type BankDecision = { readonly kind: 'take'; readonly amount: number } | { readonly kind: 'keep' } | { readonly kind: 'pass' };

/** What a punter sees at a chemin de fer coup. */
export interface PunterView {
  readonly coverage: number;
  readonly open: number;
  readonly min: number;
  /** the punter's own max this coup */
  readonly max: number;
  readonly balance: number;
  readonly bancoAvailable: boolean;
}

export type PunterDecision = { readonly kind: 'bet'; readonly amount: number } | { readonly kind: 'banco' } | { readonly kind: 'none' };

/** What a bettor sees at a house (Punto Banco) coup. */
export interface CoupBetView {
  readonly min: number;
  readonly max: number;
  readonly step: number;
  readonly sideMax: number;
  readonly pairs: boolean;
  readonly balance: number;
  /** last coup's slip, if any (Rebet) */
  readonly last?: Slip;
}

/**
 * Decision source of one occupant. Human implementations show forms (async, may be cancelled
 * by the table timer → the default action); bot policies can answer at once.
 */
export interface BaccaratDecider {
  /** undefined = no answer (form closed): the offer timer applies the default (Pass). */
  offerBank(view: BankOfferView): Promise<BankDecision | undefined> | BankDecision | undefined;
  /** Bots only: bets for a house coup (humans place bets through the table form). */
  houseBets?(view: CoupBetView): Slip;
  /** Bots only: a punter action when betting opens (humans use the table form). */
  punt?(view: PunterView): PunterDecision;
}

/** The default action of every timed decision (§20.9: timeout = Pass). */
export const DEFAULT_BANK_DECISION: BankDecision = { kind: 'pass' };

/** Validate a bank decision against the offer; invalid answers fall back to Pass. */
export function normalizeBankDecision(d: BankDecision | undefined, v: BankOfferView): BankDecision {
  if (!d) return DEFAULT_BANK_DECISION;
  if (d.kind === 'keep') return v.keep && v.bank >= v.minBank ? d : DEFAULT_BANK_DECISION;
  if (d.kind === 'take') {
    if (v.keep) return DEFAULT_BANK_DECISION;
    const a = Math.floor(d.amount);
    return Number.isSafeInteger(a) && a >= v.minBank && a <= v.balance ? { kind: 'take', amount: a } : DEFAULT_BANK_DECISION;
  }
  return DEFAULT_BANK_DECISION;
}

/** Seated occupants in clockwise (seat) order → ids for rotation. */
export const clockwise = (seats: readonly SeatOccupant[]): string[] => [...seats].sort((a, b) => a.seat - b.seat).map((s) => s.id);
