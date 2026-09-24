/**
 * Table kind and base limits from the block variant / dealer tag / worldgen preset
 * (GAME_DESIGN §21.2, §16 High Roller Lounge). PURE.
 */
export type TableKind = 'standard' | 'high_roller' | 'player_banked';

/** Minimal view of worldgen's TablePreset (worldgen/api.ts). */
export interface PresetLike {
  readonly id: string;
  readonly minBet?: number;
  readonly tierMultiplier?: number;
  readonly minTier?: number;
}

export interface UthTableConfig {
  highRollerMinAnte: number;
  highRollerMaxMultiplier: number;
  highRollerMinVipTier: number;
  minAnte: number;
}

export interface BaseTableLimits {
  kind: TableKind;
  /** minimum Ante */
  min: number;
  tierMultiplier?: number;
  minTier?: number;
}

/**
 * Kind + limits of a table. A worldgen preset whose id mentions `high_roller` (e.g. a future
 * `high_roller_uth`) makes a High-Roller table; its minBet / tierMultiplier / minTier override
 * the config.
 */
export function tableLimits(variant: string | undefined, preset: PresetLike | undefined, c: UthTableConfig): BaseTableLimits {
  const kind: TableKind = variant === 'player_banked' ? 'player_banked' : variant === 'high_roller' || preset?.id.includes('high_roller') ? 'high_roller' : 'standard';
  if (kind === 'high_roller') {
    return {
      kind,
      min: preset?.minBet ?? c.highRollerMinAnte,
      tierMultiplier: preset?.tierMultiplier ?? c.highRollerMaxMultiplier,
      minTier: preset?.minTier ?? c.highRollerMinVipTier,
    };
  }
  return { kind, min: preset?.minBet ?? c.minAnte, tierMultiplier: preset?.tierMultiplier, minTier: preset?.minTier };
}
