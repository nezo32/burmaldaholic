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
