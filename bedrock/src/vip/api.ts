/**
 * Public API of the vip module for other modules (types only + service name).
 * Provided in onWorldLoad: ctx.services.provide(VIP_SERVICE, impl).
 * Tier / max bet are also available without this service through `ctx.limits`.
 */
import type { Player } from '@minecraft/server';

export const VIP_SERVICE = 'vip';

/** Daily contract ids (GAME_DESIGN §3.4.4). */
export type VipContractId =
  | 'mine_iron' | 'mine_coal' | 'mine_diamond' | 'kill_zombie' | 'kill_skeleton' | 'kill_creeper' | 'kill_any'
  | 'trade' | 'fish' | 'harvest' | 'wager' | 'win_blackjack' | 'spin_slots' | 'roulette_red' | 'play_poker'
  | 'explore_nether' | 'smelt';

export interface VipPromotion {
  player: Player;
  /** tier before (0..4) */
  from: number;
  /** new tier (1..5) */
  to: number;
}

export interface VipApi {
  /** Lifetime chips wagered (W, never decreases). */
  lifetimeWagered(player: Player): number;
  /** 0 = Bronze … 5 = Netherite (same as ctx.limits.tier). */
  tier(player: Player): number;
  /** Daily cashback rate of the player's tier (0 below Gold). */
  cashbackRate(player: Player): number;
  /** Called once per tier gained (achievements, loan offers...). */
  onPromoted(listener: (e: VipPromotion) => void): void;
  /**
   * Report progress on a contract the vip module cannot observe itself. Roulette must call
   * `reportContract(p, 'roulette_red')` for each winning bet on red; the first call also adds
   * `roulette_red` to the contract pool (it is left out until a game reports it).
   */
  reportContract(player: Player, id: VipContractId, amount?: number): void;
  /** Mark an externally reported contract as available without reporting progress yet. */
  registerContractSource(id: VipContractId): void;
  /** Unlocked vip_* milestones (GAME_DESIGN §19 ids, e.g. 'vip_gold'). */
  milestones(player: Player): readonly string[];
}
