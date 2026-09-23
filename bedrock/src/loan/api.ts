/**
 * Public API of the loan module for other modules (types only + service name).
 * Consumers: `ctx.services.get<LoanApi>(LOAN_SERVICE)` (may be undefined; handle it).
 * Core already sees owed/inDefault through `ctx.economy.owed / inDefault` (debt provider).
 */
import type { Dimension, Entity, Player, Vector3 } from '@minecraft/server';

export const LOAN_SERVICE = 'loan';

/** Entity ids owned by the loan module. */
export const LOAN_SHARK_ID = 'burmaldaholic:loan_shark';
export const PIGLIN_MONEYLENDER_ID = 'burmaldaholic:piglin_moneylender';
export const SQUAD_ENTITY_IDS = ['burmaldaholic:debt_collector', 'burmaldaholic:repo_man', 'burmaldaholic:accountant', 'burmaldaholic:enforcer'] as const;
/** Every Debt Collector squad member spawned by a wave carries this tag. */
export const SQUAD_TAG = 'burmaldaholic_loan_squad';
/** Player tag while a squad is hunting them (collectors target it). */
export const DEBTOR_TAG = 'burmaldaholic_loan_debtor';

export type LoanStatus = 'none' | 'active' | 'default';

export interface LoanApi {
  /** Current debt (0 without a loan or while dormant). */
  owed(player: Player): number;
  status(player: Player): LoanStatus;
  inDefault(player: Player): boolean;
  /**
   * Asset Freeze (§5.6): in default while collectors are off (Peaceful or
   * `loan.collectors.enabled=false`). The player must not wager at all; game modules should
   * refuse bets with `gui.burmaldaholic.error.in_default` when this is true.
   */
  isWagerFrozen(player: Player): boolean;
  /** True for Debt Collector squad units (by type), e.g. Last Chance in Hardcore (§5.7). */
  isSquadMember(entity: Entity): boolean;
  /** Pay towards the debt from the balance; returns chips actually paid. */
  pay(player: Player, amount: number): number;
  /** Open the loan status / pay screen (Casino Card). */
  openStatus(player: Player): Promise<void>;
  /**
   * Place a Loan Shark (worldgen village casino) or Piglin Moneylender (Piglin Parlor).
   * The location becomes his home: after being killed he respawns there 1 MCD later.
   */
  spawnShark(dimension: Dimension, location: Vector3, variant?: 'shark' | 'piglin'): Entity | undefined;
}
