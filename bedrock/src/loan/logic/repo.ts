/**
 * Repossession (GAME_DESIGN §5.5): when a squad member kills the debtor, half the balance and the
 * single highest-appraised item stack (§4.3.1 table, value = appraisal × count) go to the debt.
 * Also the dialogue variant counts (LOCALIZATION.md §1.2). PURE.
 */
import { seizeAmount } from './loan';

export interface StackInfo {
  /** inventory slot, or index into a list of dropped item entities */
  slot: number;
  typeId: string;
  amount: number;
}

/** The stack with the highest appraisal × count (ties: first). Unappraised items are ignored. */
export function bestAppraised<T extends StackInfo>(stacks: readonly T[], appraisal: (typeId: string) => number): { stack: T; value: number } | undefined {
  let best: { stack: T; value: number } | undefined;
  for (const s of stacks) {
    const per = appraisal(s.typeId);
    if (!(per > 0) || s.amount <= 0) continue;
    const value = per * s.amount;
    if (!best || value > best.value) best = { stack: s, value };
  }
  return best;
}

export interface RepossessionPlan {
  /** chips moved from the balance to the debt */
  seized: number;
  /** debt reduction credited for the item (≤ owed after the chips) */
  itemCredit: number;
}

/** seized = min(owed, floor(balance × pct)); the item then covers up to the rest of the debt. */
export function planRepossession(balance: number, owed: number, percent: number, itemValue: number): RepossessionPlan {
  const seized = seizeAmount(balance, percent, owed);
  const itemCredit = Math.max(0, Math.min(owed - seized, Math.floor(itemValue)));
  return { seized, itemCredit };
}

/** `wager.appraisal.<id>` config key for a vanilla item id, or undefined for other items. */
export const appraisalKey = (typeId: string): string | undefined => (typeId.startsWith('minecraft:') ? `wager.appraisal.${typeId.slice(10)}` : undefined);

/** Number of variants per dialogue base key (STRINGS.md). */
export const VARIANTS = {
  'dialog.burmaldaholic.loan.greeting': 5,
  'dialog.burmaldaholic.loan.given': 4,
  'dialog.burmaldaholic.loan.repaid': 4,
  'dialog.burmaldaholic.loan.overdue': 5,
  'dialog.burmaldaholic.loan.refuse_vip': 3,
  'dialog.burmaldaholic.loan.piglin_greeting': 2,
  'dialog.burmaldaholic.collector.demand': 5,
  'dialog.burmaldaholic.collector.hostile': 5,
  'dialog.burmaldaholic.collector.paid': 4,
  'dialog.burmaldaholic.collector.partial': 3,
  'dialog.burmaldaholic.collector.repossess': 3,
  'dialog.burmaldaholic.accountant.audit': 3,
  'dialog.burmaldaholic.enforcer.line': 3,
} as const;
export type DialogueKey = keyof typeof VARIANTS;
