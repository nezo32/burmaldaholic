/**
 * Casino-mode resolution. PURE.
 *
 * Precedence (first defined wins):
 *  1. World override: dynamic property `burmaldaholic:casino_mode` (set from the in-game admin form).
 *  2. Pack setting `burmaldaholic:casino_mode` (manifest v3 pack settings, chosen when the pack is
 *     applied to the world, e.g. at world creation; read via world.getPackSettings()).
 *  3. Default: true - applying the add-on to a world *is* turning casino mode on.
 */
/** World dynamic property (docs/design GAME_DESIGN §2) and pack-setting name share one id. */
export const CASINO_MODE_PROP = 'burmaldaholic:casino_mode';
export const PACK_SETTING_ENABLED = CASINO_MODE_PROP;

export function resolveCasinoEnabled(worldOverride: unknown, packSettings: Record<string, unknown> | undefined): boolean {
  if (typeof worldOverride === 'boolean') return worldOverride;
  const ps = packSettings?.[PACK_SETTING_ENABLED];
  if (typeof ps === 'boolean') return ps;
  return true;
}

/**
 * First-op Setup form (GAME_DESIGN §2.1, Bedrock). Stored in world dynamic property
 * `burmaldaholic:core.setup` as JSON. Until answered the mode is ON with defaults; a dismissed
 * form re-appears on that op's next join, max 3 times, then defaults are kept silently.
 */
export const SETUP_PROP = 'burmaldaholic:core.setup';
export const SETUP_MAX_DISMISSALS = 3;

export interface SetupState {
  done: boolean;
  /** dismissals per player id */
  dismissed: Record<string, number>;
}

export function parseSetup(raw: unknown): SetupState {
  try {
    const o = typeof raw === 'string' ? (JSON.parse(raw) as Partial<SetupState>) : {};
    return { done: o.done === true, dismissed: typeof o.dismissed === 'object' && o.dismissed ? o.dismissed : {} };
  } catch {
    return { done: false, dismissed: {} };
  }
}

export function shouldShowSetup(s: SetupState, playerId: string, isOperator: boolean): boolean {
  return isOperator && !s.done && (s.dismissed[playerId] ?? 0) < SETUP_MAX_DISMISSALS;
}

export function dismissSetup(s: SetupState, playerId: string): SetupState {
  return { done: s.done, dismissed: { ...s.dismissed, [playerId]: (s.dismissed[playerId] ?? 0) + 1 } };
}
