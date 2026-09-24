// Palette tokens of global.md §2.1 (keep in sync with Java client/fx/CasinoPalette.java).
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
};

/** '#RRGGBB' or '#RRGGBBAA' → [r,g,b,a] */
export function rgba(hex) {
  const h = hex.replace('#', '');
  const n = (i) => parseInt(h.slice(i, i + 2), 16);
  return [n(0), n(2), n(4), h.length >= 8 ? n(6) : 255];
}
