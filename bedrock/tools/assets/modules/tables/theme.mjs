// Table themes per casino location (docs/design/visual/tables.md §2): Village casino, Piglin Parlor (bastion),
// High Roller Lounge (End). Hex only; brand tokens from docs/branding/branding.md where a role matches.
export const THEME_ORDER = ['village', 'bastion', 'end'];

export const THEME = {
  village: {
    felt: '#1E5E3A', // branding felt
    feltLight: '#2A7448',
    feltDark: '#154A2C',
    feltDeep: '#0E2E1C',
    line: '#EDE2C4', // chalk-cream layout lines
    lineShade: '#9FB89A',
    rail: ['#8A5530', '#744624', '#5E381C', '#442612'], // padded leather, light → dark
    railStud: '#FFD640',
    trim: '#FFD640',
    trimLight: '#FFF1A0',
    trimDark: '#B07010',
    plate: '#3A2412',
    plateLight: '#5A3A1E',
    accent: '#5FC23A',
    accentDark: '#2E7A1E',
    wood: ['#9A5E32', '#7E4A26', '#643A1E', '#4A2A14'], // walnut (bowl, buttons)
    track: ['#E6C28A', '#D2A868', '#B88C50'], // ball track (polished maple)
    cone: ['#A0522D', '#86401F', '#6A3018', '#4E2210'], // mahogany cone
    btn: ['#9C6430', '#84522A', '#5E381C'],
    btnHover: ['#B47838', '#9C6430', '#6E4422'],
  },
  bastion: {
    felt: '#5A1418',
    feltLight: '#72202A',
    feltDark: '#44101A',
    feltDeep: '#240608',
    line: '#FFD640',
    lineShade: '#B07010',
    rail: ['#4A4250', '#3C3440', '#2E2834', '#1C1822'], // polished blackstone
    railStud: '#FF7A1A',
    trim: '#FFD640',
    trimLight: '#FFF4A0',
    trimDark: '#B07010',
    plate: '#1C1418',
    plateLight: '#3A2A2E',
    accent: '#FF7A1A',
    accentDark: '#A02A00',
    wood: ['#4A4250', '#3C3440', '#2E2834', '#1C1822'],
    track: ['#F0C860', '#D8A838', '#B8861C'], // gold track
    cone: ['#3C3440', '#2E2834', '#241E28', '#18141C'],
    btn: ['#3C3440', '#2E2834', '#18141C'],
    btnHover: ['#4E4658', '#3C3440', '#1C1822'],
  },
  end: {
    felt: '#2A1A4C',
    feltLight: '#38245E',
    feltDark: '#1E123A',
    feltDeep: '#100822',
    line: '#F4ECF8', // pearl (end rod)
    lineShade: '#A77BA7',
    rail: ['#2A2036', '#1E1628', '#140C1C', '#0A0610'], // obsidian
    railStud: '#F4ECF8',
    trim: '#E8F4FF', // platinum
    trimLight: '#FFFFFF',
    trimDark: '#8FA8C8',
    plate: '#140C1C',
    plateLight: '#2A2036',
    accent: '#40E0A0',
    accentDark: '#1A7A58',
    wood: ['#C8A0C8', '#A77BA7', '#8E648E', '#6E4A6E'], // purpur
    track: ['#E8E4C0', '#D0CAA0', '#B0A87C'], // end stone
    cone: ['#3A2A4A', '#2A1E38', '#1E1428', '#140C1C'],
    btn: ['#A77BA7', '#8E648E', '#5A3A5A'],
    btnHover: ['#C8A0C8', '#A77BA7', '#6E4A6E'],
  },
};

/** European wheel order (clockwise from 0) and red numbers (GAME_DESIGN §9). */
export const WHEEL_ORDER = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26];
export const REDS = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);
export const pocketColour = (n) => (n === 0 ? 'green' : REDS.has(n) ? 'red' : 'black');

/** Pocket / cell colours (fixed across themes: the casino standard). [light, base, shade, deep]. */
export const POCKET = {
  red: ['#FF6E6A', '#D83440', '#A8202E', '#6E1020'],
  black: ['#4A4050', '#26202C', '#18141C', '#0C0A10'],
  green: ['#6AE070', '#2E9A48', '#1E7434', '#0E4A1E'],
};

/** Seat tints (animation/tables.md §0.4), seats 1–8. */
export const SEATS = ['#4FC3F7', '#FFB74D', '#BA68C8', '#81C784', '#F06292', '#FFF176', '#A1887F', '#90A4AE'];

export const INK = '#180A28';
export const BONE = '#F4ECF8';
export const GOLD = ['#FFF1A0', '#FFD640', '#E8A820', '#B07010'];
