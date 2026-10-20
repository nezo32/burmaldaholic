// Palette tokens of global.md §2.1 (keep in sync with Java client/fx/CasinoPalette.java) plus the
// game-specific texture colours the specs name (VIP tiers, chip denominations, wood, foil, dyes).
export const PALETTE = {
  'bg.deep': '#26103C',
  'bg.darkest': '#140822',
  ink: '#180A28',
  frame: '#783CBE',
  glint: '#BE5AFF',
  lilac: '#D696FF',
  felt: '#1E5E3A',
  'felt.border': '#0E2E1C',
  'chip.red': '#D83440',
  'chip.light': '#FF6E6A',
  'chip.dark': '#8C1834',
  bone: '#F4ECF8',
  'bone.shade': '#C0B0DC',
  gold: '#FFD640',
  'gold.shade': '#B07010',
  bonus: '#80FF40',
  curse: '#6FA86A',
  'curse.bg': '#3A1450',
  cool: '#8FA8C8',
  // global.md §2.2 button fills / panel details
  'btn.fill': '#3A1A5C',
  'btn.hover': '#4A2474',
  'btn.disabled': '#2A1640',
  'btn.disabled.border': '#4A3060',
  'btn.primary': '#E8B830',
  'wood.light': '#5A3418',
  'wood.dark': '#3A2010',
  // VIP tiers (global.md §2.1)
  'vip.bronze': '#C8763C',
  'vip.silver': '#C8C8D8',
  'vip.gold': '#FFD640',
  'vip.platinum': '#E8F4FF',
  'vip.diamond': '#5CE8E0',
  'vip.netherite': '#5A4A58',
  'vip.ember': '#FF7A3C',
  // extras-pvp.md §0.2
  foil: '#B4B4B4',
  'foil.sheen': '#D8D8D8',
  'foil.shadow': '#8C8C8C',
  char: '#1A1412',
  ember: '#FF6020',
};

/**
 * THE chip values of the mod (GAME_DESIGN §3.1), smallest first: the Java twin is `ChipMath.DENOMINATIONS`. Every module
 * that draws one sprite per chip iterates this list (`DenominationSourceTest` on the Java side fails on a second list).
 */
export const CHIP_DENOMS = [1, 5, 25, 100, 500, 1000, 5000, 25000];

/** Chip colours by value: base, stripe (edge inserts) and the colour of printed digits. 1000+ are the high-roller chips. */
export const CHIPS = {
  1: { base: '#ECECEC', stripe: '#3A6FD8', digit: '#2A2A3A' },
  5: { base: '#C62828', stripe: '#F5F5F5', digit: '#FFFFFF' },
  25: { base: '#2E7D32', stripe: '#F5F5F5', digit: '#FFFFFF' },
  100: { base: '#262626', stripe: '#F5F5F5', digit: '#FFFFFF' },
  500: { base: '#6A1B9A', stripe: '#F3D34A', digit: '#FFFFFF' },
  1000: { base: '#E8C030', stripe: '#3A2010', digit: '#3A2010' },
  5000: { base: '#E07A1C', stripe: '#FFF4B0', digit: '#2A1408' },
  25000: { base: '#1FA8A0', stripe: '#E8FFF8', digit: '#0A2A2A' },
};

/** The label printed on chip art: `500`, `1K`, `25K` (Java `ChipMath.label`). */
export const chipLabel = (d) => (d >= 1000 ? `${d / 1000}K` : String(d));

/** The 16 vanilla dye colours in `DyeColor` order (Wheel Party swatches, `WheelArt.COLORS` order). */
export const DYES = [
  '#F9FFFE', '#F9801D', '#C74EBD', '#3AB3DA', '#FED83D', '#80C71F', '#F38BAA', '#474F52',
  '#9D9D97', '#169C9C', '#8932B8', '#3C44AA', '#835432', '#5E7C16', '#B02E26', '#1D1D21',
];

/** A token name or a '#hex' → '#hex'. */
export const col = (c) => (c.startsWith('#') ? c : (PALETTE[c] ?? fail(c)));
const fail = (c) => {
  throw new Error(`palette: unknown token '${c}'`);
};

/** '#RRGGBB' or '#RRGGBBAA' (or a token) → [r,g,b,a] */
export function rgba(hex) {
  const h = col(hex).replace('#', '');
  const n = (i) => parseInt(h.slice(i, i + 2), 16);
  return [n(0), n(2), n(4), h.length >= 8 ? n(6) : 255];
}

const two = (v) =>
  Math.max(0, Math.min(255, Math.round(v)))
    .toString(16)
    .padStart(2, '0')
    .toUpperCase();

/** [r,g,b,a] → '#RRGGBBAA' (alpha omitted when 255). */
export const toHex = ([r, g, b, a = 255]) => `#${two(r)}${two(g)}${two(b)}${a === 255 ? '' : two(a)}`;

/** Multiplies the RGB of a colour by k (k < 1 darker, > 1 lighter, clamped). */
export const shade = (c, k) => {
  const [r, g, b, a] = rgba(c);
  return toHex([r * k, g * k, b * k, a]);
};

/** Linear mix of two colours, t = 0 → a, 1 → b (alpha mixed too). */
export const mix = (a, b, t) => {
  const x = rgba(a);
  const y = rgba(b);
  return toHex(x.map((v, i) => v + (y[i] - v) * t));
};

/** Same colour with alpha 0..1. */
export const alpha = (c, a) => {
  const [r, g, b] = rgba(c);
  return toHex([r, g, b, a * 255]);
};
