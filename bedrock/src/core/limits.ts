/**
 * Bet limits (GAME_DESIGN §4.2, §12). Core owns the interface; the VIP module provides the
 * real tier via `ctx.limits.setVipProvider(...)`. Until then every player is Bronze (tier 0)
 * with max bet `vip.maxBet.bronze`.
 */
import type { Player } from '@minecraft/server';
import type { ConfigService } from './config';
import { type BetError, VIP_COLORS, VIP_TIERS, effectiveRange, validateBet, vipTierKey } from './logic/bet';
import { type Raw, chips as chipsRaw, color, t } from './logic/rawtext';

/** Implemented by the vip module. */
export interface VipProvider {
  /** 0 = Bronze … 5 = Netherite */
  tier(player: Player): number;
  /** Tier max bet (§12). */
  maxBet(player: Player): number;
}

export interface TableLimits {
  /** table/machine minimum (default 1) */
  min?: number;
  /** table maximum (owner setting, machine cap...) */
  tableMax?: number;
  /** multiply the tier max (High-Roller tables: `blackjack.highRollerMaxMultiplier`) */
  tierMultiplier?: number;
  /** minimum VIP tier to use this table/machine (error.vip_required) */
  minTier?: number;
}

export class Limits {
  private provider: VipProvider;

  constructor(private readonly config: ConfigService) {
    this.provider = {
      tier: () => 0,
      maxBet: () => this.config.int(`vip.maxBet.${VIP_TIERS[0]}`),
    };
  }

  setVipProvider(p: VipProvider): void {
    this.provider = p;
  }

  tier(player: Player): number {
    return Math.max(0, Math.min(5, Math.floor(this.provider.tier(player))));
  }

  /** Colored tier name, e.g. "§6Gold". */
  tierName(tier: number): Raw {
    return color(VIP_COLORS[Math.max(0, Math.min(5, tier))] ?? '§f', t(vipTierKey(tier)));
  }

  tierMax(player: Player, multiplier = 1): number {
    return Math.floor(this.provider.maxBet(player) * multiplier);
  }

  /** Effective [min, max] for this player at a table. */
  range(player: Player, l: TableLimits = {}): { min: number; max: number } {
    return effectiveRange({ min: l.min ?? 1, tableMax: l.tableMax, tierMax: this.tierMax(player, l.tierMultiplier) });
  }

  /** Raw error text, or undefined when the bet is allowed. Checks tier, limits and balance. */
  check(player: Player, amount: number, l: TableLimits = {}, balance?: number): Raw | undefined {
    if (l.minTier !== undefined && this.tier(player) < l.minTier) return t('gui.burmaldaholic.error.vip_required', this.tierName(l.minTier));
    const err = validateBet(amount, { min: l.min ?? 1, tableMax: l.tableMax, tierMax: this.tierMax(player, l.tierMultiplier) }, balance);
    return err ? this.errorText(player, err) : undefined;
  }

  errorText(player: Player, e: BetError): Raw {
    switch (e.key) {
      case 'gui.burmaldaholic.error.bet_too_high':
        return t(e.key, chipsRaw(e.max), this.tierName(this.tier(player)));
      case 'gui.burmaldaholic.error.bet_too_low':
      case 'gui.burmaldaholic.error.table_max':
        return t(e.key, chipsRaw('min' in e ? e.min : e.max));
      case 'gui.burmaldaholic.error.insufficient_funds':
        return t(e.key, chipsRaw(e.balance));
      default:
        return t(e.key);
    }
  }
}
