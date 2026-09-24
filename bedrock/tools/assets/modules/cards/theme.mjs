// Card-table themes (docs/design/visual/cards.md §2, §7): one material set per casino location.
// Colours are '#RRGGBB' strings (brand palette, docs/branding/branding.md, plus the material colours named here).
// `print` = the tint of the white felt-print sprites (drawn at 55 % alpha). Game art (cards, chips) is theme-neutral; the room, the rail, the felt, the shoe, the tray, the rack and
// the secondary buttons change with the location.

export const THEME_ORDER = ['village', 'bastion', 'end'];

export const THEMES = {
  // Village parlour: green baize, walnut rail with a leather pad, brass trim, a warm lamp-lit room.
  village: {
    felt: '#1E5E3A',
    feltLight: '#2C7A4C',
    feltDark: '#134227',
    print: '#E8C860',
    printSoft: '#F4ECF8',
    pad: '#5A2E1A',
    padLight: '#8A4A2A',
    padDark: '#34180C',
    stitch: '#C8904A',
    trim: '#FFD640',
    trimShade: '#B07010',
    wood: '#6A3E1E',
    woodLight: '#8E5A2E',
    woodDark: '#3A2010',
    wall: '#3C1E2E',
    wallLight: '#562A40',
    wallPattern: '#6E3A52',
    wainscot: '#5A3418',
    wainscotLight: '#7A4A24',
    wainscotDark: '#3A2010',
    floor: '#2A160C',
    glow: '#FFB24A',
    accent: '#FFD640',
  },
  // Piglin bastion parlour: crimson felt, polished blackstone rail, gold studs, lava light from below.
  bastion: {
    felt: '#5E1A26',
    feltLight: '#7C2632',
    feltDark: '#3E0E18',
    print: '#FFC850',
    printSoft: '#FFB08A',
    pad: '#2A2230',
    padLight: '#463A4E',
    padDark: '#140E1A',
    stitch: '#FFD640',
    trim: '#FFD640',
    trimShade: '#B07010',
    wood: '#2E2632',
    woodLight: '#4A3E50',
    woodDark: '#16101C',
    wall: '#221A24',
    wallLight: '#342A38',
    wallPattern: '#FFB020',
    wainscot: '#2E2632',
    wainscotLight: '#443848',
    wainscotDark: '#140E18',
    floor: '#1A1014',
    glow: '#FF6020',
    accent: '#FF7A3C',
  },
  // End City High Roller lounge: violet felt, purpur rail with end-stone trim, lilac prints, void and stars.
  end: {
    felt: '#2E1A4E',
    feltLight: '#43286C',
    feltDark: '#1C0E34',
    print: '#E8E4A8',
    printSoft: '#D696FF',
    pad: '#7C4F7C',
    padLight: '#A97AA9',
    padDark: '#4E2E52',
    stitch: '#E8E4A8',
    trim: '#E8E4A8',
    trimShade: '#9C9A64',
    wood: '#A97AA9',
    woodLight: '#C9A0C9',
    woodDark: '#5E3A62',
    wall: '#140822',
    wallLight: '#26103C',
    wallPattern: '#BE5AFF',
    wainscot: '#7C4F7C',
    wainscotLight: '#A97AA9',
    wainscotDark: '#4E2E52',
    floor: '#DCDCA0',
    glow: '#D696FF',
    accent: '#BE5AFF',
  },
};

/** Four-colour deck (default, colour-blind safe: hue AND luminance differ; shapes carry the suit anyway). */
export const SUIT_ORDER = ['spades', 'hearts', 'diamonds', 'clubs'];
export const SUITS_4C = {
  spades: { base: '#231C38', light: '#5A4E7A', dark: '#0E0A18' },
  hearts: { base: '#D42A3A', light: '#FF7A72', dark: '#86142A' },
  diamonds: { base: '#2A5ED8', light: '#7AA6FF', dark: '#14307A' },
  clubs: { base: '#0F7E66', light: '#52C8A2', dark: '#064234' },
};
/** Classic two-colour deck (opt-in setting): black/red, same shapes. */
export const SUITS_2C = {
  spades: SUITS_4C.spades,
  hearts: SUITS_4C.hearts,
  diamonds: SUITS_4C.hearts,
  clubs: SUITS_4C.spades,
};

/** Seat tints, seats 1–8 (animation/tables.md §0.4). */
export const SEAT_TINTS = ['#4FC3F7', '#FFB74D', '#BA68C8', '#81C784', '#F06292', '#FFF176', '#A1887F', '#90A4AE'];

/** Paper and ink of the card faces. */
export const PAPER = { face: '#FBF6EA', hi: '#FFFFFF', shade: '#E2D8C4', edge: '#B8AA92', ink: '#180A28' };
