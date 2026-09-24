// 16 × 16 icon art of the extras and PvP screens (docs/design/visual/extras.md §3–§8): string grids in the house
// pixel style (Minecraft item shapes, 1 px ink edge drawn by iconCell, light top-left). `{id, pal, grid, glow}`.
// No text anywhere; numbers and names are drawn by code.
import { chipTop, image, over, put, c } from './kit.mjs';

const I = (id, pal, grid, glow = {}) => ({ id: `extras_${id}`, pal, grid, glow });

// ---- coin emblems (masks, embossed onto the coin face; shapes, never colour, tell the faces apart) ----------------
export const EMBLEM_HEADS = I('emblem_heads', { '#': '#000000' }, [
  '................',
  '.....#..#..#....',
  '.....########...',
  '....##########..',
  '....##########..',
  '....######.###..',
  '....##########..',
  '....###########.',
  '....##########..',
  '....#######.##..',
  '....##########..',
  '.....#####......',
  '.....#####......',
  '...#########....',
  '..###########...',
  '................',
]);

export const EMBLEM_TAILS = I('emblem_tails', { '#': '#000000' }, [
  '................',
  '.###........###.',
  '####........####',
  '.####......####.',
  '...##......##...',
  '....##....##....',
  '.....######.....',
  '....###..###....',
  '....##....##....',
  '....###..###....',
  '.....######.....',
  '....##....##....',
  '...##......##...',
  '..##........##..',
  '.##..........##.',
  '................',
]);

// ---- scratch symbols (prize ranks 1–6 + creeper; Showdown adds foot + charred) ---------------------------------------
const INGOT_GRID = [
  '................',
  '................',
  '................',
  '................',
  '......kkkkkkkk..',
  '.....khhhhhhwwk.',
  '....khhhhhhhhwk.',
  '...khhhhhhhhwdk.',
  '..kkkkkkkkkkkdk.',
  '..kmmmmmmmmmkdk.',
  '..kmlmmmmmmmkdk.',
  '..kmmmmmmmmmkk..',
  '..kssssssssskk..',
  '..kkkkkkkkkkk...',
  '................',
  '................',
];

export const COAL = I('coal', { k: '#0A0A0E', d: '#26262C', m: '#3A3A42', l: '#6A6A76', w: '#9A9AA8' }, [
  '................',
  '................',
  '......kkkk......',
  '....kkmmlmkk....',
  '...kmmlwlmmdk...',
  '..kmmmmlmmmmdk..',
  '..kmlmmmmdmmdk..',
  '.kmlwmmmdmmmmdk.',
  '.kmmlmmdmmlmmdk.',
  '.kmmmmmmmlwmddk.',
  '..kdmmmmmmlmdk..',
  '..kdmdmmmmmddk..',
  '...kddmmdmddk...',
  '....kkddddkk....',
  '......kkkk......',
  '................',
]);

export const IRON = I('iron', { k: '#26262E', h: '#E8E8EE', w: '#FFFFFF', m: '#B4B4C0', l: '#D8D8E0', d: '#7A7A88', s: '#8E8E9C' }, INGOT_GRID);
export const GOLD_INGOT = I('gold_ingot', { k: '#5C3A00', h: '#FFE870', w: '#FFFFFF', m: '#FFC400', l: '#FFF4B0', d: '#B07010', s: '#D89010' }, INGOT_GRID, { h: '#FFF4B0', m: '#FFE070' });

export const EMERALD = I('emerald', { k: '#0A3A14', d: '#0E7A2E', G: '#1EC050', g: '#12A040', l: '#8AF0A8', w: '#FFFFFF' }, [
  '................',
  '......kkkk......',
  '.....klwGGk.....',
  '....kllGGGGk....',
  '...kllGGGGGdk...',
  '...klGGgGGGdk...',
  '..klGGGgGGGGdk..',
  '..klGGGgGGGGdk..',
  '..kGGGGgGGGGdk..',
  '..kGGGGgGGGdk...',
  '...kGGGgGGGdk...',
  '...kGGGGGGGdk...',
  '....kGGGGGdk....',
  '.....kdGGdk.....',
  '......kkkk......',
  '................',
], { G: '#50F080', g: '#30D060', d: '#20A040' });

export const DIAMOND = I('diamond', { k: '#0C3A40', d: '#1A9A94', c: '#2EC8C0', l: '#6EEEE4', w: '#FFFFFF' }, [
  '................',
  '................',
  '...kkkkkkkkkk...',
  '..kllwwlllcclk..',
  '.kllwwllllcccdk.',
  'kllllllllcccccdk',
  'kddddddddddddddk',
  '.kcllllllllccdk.',
  '..kcllllllccdk..',
  '...kcllllccdk...',
  '....kcllccdk....',
  '.....kclcdk.....',
  '......kcdk......',
  '.......kk.......',
  '................',
  '................',
], { l: '#B0FFF8', c: '#50E8E0' });

/** Nether star: an 8-point star rasterised from r(θ) (4 long + 4 short points), white-hot core, lilac tips. */
function starGrid() {
  const rows = [];
  for (let y = 0; y < 16; y++) {
    let row = '';
    for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8;
      const dy = y + 0.5 - 8;
      const d = Math.hypot(dx, dy);
      const t = Math.atan2(dy, dx);
      const r = 3.3 + 4.6 * Math.max(0, Math.cos(2 * t)) ** 4 + 1.9 * Math.max(0, -Math.cos(2 * t)) ** 4;
      const q = d / r;
      row += q > 1 ? '.' : q < 0.3 ? 'w' : q < 0.55 ? 'y' : q < 0.8 ? 'b' : dx + dy < 0 ? 'l' : 'p';
    }
    rows.push(row);
  }
  return rows;
}

export const NETHER_STAR = I('nether_star', { w: '#FFFFFF', b: '#E8E0F8', y: '#FFF4B0', l: '#D696FF', p: '#9A6AD0' }, starGrid(), { y: '#FFFFFF', l: '#FFB0FF', b: '#FFF4B0' });

export const CREEPER = I('creeper', { k: '#0A2A0A', G: '#3FA535', g: '#2E8A28', l: '#6CD060', d: '#1A5A18', f: '#101010' }, [
  '................',
  '.kkkkkkkkkkkkkk.',
  '.kGlGGgGGlGGGgk.',
  '.kGGGGGGGgGlGGk.',
  '.kgGffGGGGffGGk.',
  '.kGGffGlGGffgGk.',
  '.kGlGGGggGGGGGk.',
  '.kGGGGffffGGlGk.',
  '.kgGGffffffGGGk.',
  '.kGGGffffffGgGk.',
  '.kGlGffGGffGGGk.',
  '.kGGGffGGffGlGk.',
  '.kgGGGGlGGGGGgk.',
  '.kGGgGGGGgGGGGk.',
  '.kkkkkkkkkkkkkk.',
  '................',
], { f: '#FFFFFF' });

export const RABBIT_FOOT = I('rabbit_foot', { k: '#3A2210', t: '#C8A070', T: '#E8C898', d: '#8A6038', y: '#FFD640', Y: '#B07010', p: '#F0B0B0' }, [
  '................',
  '..........kkk...',
  '.........kTTTk..',
  '........kTTTTTk.',
  '........kTtTtTk.',
  '.......kTttttdk.',
  '......kTtttttdk.',
  '.....kTttttddk..',
  '....kTtttttdk...',
  '...kTttttddk....',
  '..kyyttttddk....',
  '..kyYytddkk.....',
  '.kyYYYkkk.......',
  '.kYYYk..........',
  '..kkk...........',
  '................',
], { t: '#FFE0A8', T: '#FFF4D0' });

export const CHARRED = I('charred', { k: '#0A0606', a: '#1A1412', b: '#2E2420', e: '#FF6020', E: '#FFB040', g: '#4A3A34' }, [
  '................',
  '..kkkkkkkkkkkk..',
  '.kaabaagaabaaak.',
  '.kabbaaaaaaebak.',
  '.kaaaabagaaaaak.',
  '.kagaaaaaabaaak.',
  '.kaaabeaaaaagak.',
  '.kbaaaaaEaaaaak.',
  '.kaaagaaaabaaak.',
  '.kaaaaaabaaaebk.',
  '.kabaeaaaaagaak.',
  '.kaaaaagaaaaaak.',
  '.kaaEaaaaabaaak.',
  '.kkkkkkkkkkkkkk.',
  '................',
  '................',
], { e: '#FFE070', E: '#FFFFFF' });

/** Scratch symbol set in prize-rank order (+ creeper, foot, charred): sheet columns 0–8. */
export const SCRATCH_SYMBOLS = [COAL, IRON, GOLD_INGOT, EMERALD, DIAMOND, NETHER_STAR, CREEPER, RABBIT_FOOT, CHARRED];

// ---- wheel segment icons (16 px, procedural chips + the gem/creeper art) ----------------------------------------------
function chipIcon(stack, { bust = false, half = false } = {}) {
  const img = image(16, 16);
  const pos = stack === 1 ? [[8, 8]] : stack === 2 ? [[6, 10], [10, 6]] : [[4.5, 11], [11.5, 11], [8, 5]];
  const col = bust ? ['#6A6A72', '#3A3A40'] : stack === 1 ? ['#3D5A80', '#F4ECF8'] : stack === 2 ? ['#2E7D32', '#F4ECF8'] : ['#9C27B0', '#FFD640'];
  const r = stack === 3 ? 4.2 : stack === 2 ? 5 : 6.5;
  for (const [x, y] of pos) chipTop(img, x, y, r, col[0], col[1]);
  if (bust) {
    const crack = [[9, 1], [8, 2], [8, 3], [7, 4], [8, 5], [9, 6], [8, 7], [7, 8], [7, 9], [8, 10], [7, 11], [6, 12], [6, 13], [7, 14]];
    for (const [x, y] of crack) put(img, x, y, c('#140822'));
    for (const [x, y] of crack) put(img, x + 1, y, c('#9A9AA8'));
  }
  if (half) {
    const h = image(16, 16);
    chipTop(h, 8, 8, 6.5, '#6C8EBF', '#F4ECF8');
    for (let y = 0; y < 16; y++) for (let x = 8; x < 16; x++) put(h, x, y, [0, 0, 0, 0]);
    // dashed ghost outline of the missing half
    for (let a = -80; a <= 80; a += 20) {
      const rad = (a * Math.PI) / 180;
      put(h, Math.round(8 + Math.cos(rad) * 6.5), Math.round(8 + Math.sin(rad) * 6.5), c('#C0B0DC'));
    }
    return h;
  }
  return img;
}

export const WHEEL_CODES = ['B', 'C', 'H', 'M', 'D', 'T', 'E', 'X'];
/** Wedge colours (extras-pvp §3.2) and light/dark companions. */
export const WHEEL_KIND = {
  B: { name: 'bust', hex: '#3A3A3A', light: '#5A5A5E', dark: '#242426' },
  C: { name: 'creeper', hex: '#3FA535', light: '#6CD060', dark: '#256E20' },
  H: { name: 'half', hex: '#6C8EBF', light: '#9CB8E0', dark: '#48648E' },
  M: { name: 'money_back', hex: '#3D5A80', light: '#6080AA', dark: '#26405E' },
  D: { name: 'double', hex: '#2E7D32', light: '#4EA852', dark: '#1C5420' },
  T: { name: 'triple', hex: '#9C27B0', light: '#C050D8', dark: '#6A1A7A' },
  E: { name: 'emerald', hex: '#00C853', light: '#50F090', dark: '#00883A' },
  X: { name: 'diamond', hex: '#4FC3F7', light: '#A0E4FF', dark: '#2A8AC0' },
};

/** 16 × 16 image of a wheel segment icon. */
export function wheelIcon(code) {
  switch (code) {
    case 'B':
      return chipIcon(1, { bust: true });
    case 'C':
      return drawDef(CREEPER);
    case 'H':
      return chipIcon(1, { half: true });
    case 'M':
      return chipIcon(1);
    case 'D':
      return chipIcon(2);
    case 'T':
      return chipIcon(3);
    case 'E':
      return drawDef(EMERALD);
    case 'X':
      return drawDef(DIAMOND);
    default:
      throw new Error(`wheel icon ${code}`);
  }
}

/** 8 × 8 wheel-face icons (hand-sized for the 54-wedge face: 1 px shapes, readable at 1×). */
const MINI = {
  B: ['..kkkk..', '.kgKgggk', 'kggKgggk', 'kgggKggk', 'kggKgggk', 'kgggKggk', '.kggKgk.', '..kkkk..'],
  C: ['kkkkkkkk', 'kGGGGGGk', 'kffGGffk', 'kffGGffk', 'kGGffGGk', 'kGffffGk', 'kGfGGfGk', 'kkkkkkkk'],
  H: ['..kkk...', '.kbbk.w.', 'kbwbk..w', 'kbbbk...', 'kbwbk..w', 'kbbbk...', '.kbbk.w.', '..kkk...'],
  M: ['..kkkk..', '.kbwwbk.', 'kbwbbwbk', 'kwbbbbwk', 'kwbbbbwk', 'kbwbbwbk', '.kbwwbk.', '..kkkk..'],
  D: ['...kkkk.', '..kgwwgk', '.kkkkwwk', 'kgwwgkgk', 'kwggwk..', 'kwggwk..', 'kgwwgk..', '.kkkk...'],
  T: ['...kk...', '..kppk..', '..kpyk..', 'kk.kk.kk', 'kppk.kpp', 'kpyk.kpy', '.kk...kk', '........'],
  E: ['...kk...', '..kGGk..', '.kGlGGk.', '.kGlGGk.', '.kGGGGk.', '.kGGGdk.', '..kGdk..', '...kk...'],
  X: ['........', '.kkkkkk.', 'kllwlcck', 'kddddddk', '.kclcck.', '..kccdk.', '...kdk..', '....k...'],
};
const MINI_PAL = {
  k: '#140822', g: '#6A6A72', K: '#E8E8EE', G: '#3FA535', f: '#101010', b: '#6C8EBF', w: '#F4ECF8', p: '#9C27B0', y: '#FFD640',
  l: '#A0FFC0', d: '#1A5A28', c: '#2EC8C0',
};
export function wheelIconMini(code) {
  const pal = code === 'M' ? { ...MINI_PAL, b: '#3D5A80' } : code === 'D' ? { ...MINI_PAL, g: '#2E7D32' } : code === 'X' ? { ...MINI_PAL, l: '#B0FFF8', d: '#1A9A94', c: '#4FC3F7', w: '#FFFFFF' } : MINI_PAL;
  return drawDef({ id: `mini_${code}`, pal, grid: MINI[code] });
}

// ---- mode icons (PvP hub cards, toasts, tabs) ------------------------------------------------------------------------
export const MODE_COIN = I('mode_coin', { k: '#5C3A00', y: '#FFD640', h: '#FFF4B0', d: '#B07010', m: '#E8B830' }, [
  '................',
  '.....kkkkkk.....',
  '...kkyyyyyykk...',
  '..kyhhyyyyyydk..',
  '..kyhyymmyyydk..',
  '.kyhyymddmyyydk.',
  '.kyhymdyydmyydk.',
  '.kyyymdyydmyydk.',
  '.kyyymdyydmyydk.',
  '.kyyyymddmyyydk.',
  '.kyyyyymmyyyddk.',
  '..kyyyyyyyyddk..',
  '..kdyyyyyyyddk..',
  '...kkddddddkk...',
  '.....kkkkkk.....',
  '................',
]);

export const MODE_WHEEL = I('mode_wheel', { k: '#2A1408', r: '#D83440', w: '#F4ECF8', g: '#2E7D32', b: '#3D5A80', y: '#FFD640', n: '#7A4A24', d: '#B07010' }, [
  '.......rr.......',
  '......krrk......',
  '....kkkrrkkk....',
  '...knrrwwggnk...',
  '..knrrrwwgggnk..',
  '.knwwrrwwggbbnk.',
  '.knwwwryygbbbnk.',
  '.knggggyyrrrrnk.',
  '.knbbbwyyrwwwnk.',
  '.knbbwwwrrwwwnk.',
  '..knbwwrrrggnk..',
  '...knwwrrgggk...',
  '....kknnnnkk....',
  '......kddk......',
  '.....kddddk.....',
  '....kkkkkkkk....',
]);

export const MODE_PLINKO = I('mode_plinko', { k: '#101830', p: '#C8C8D8', w: '#FFFFFF', r: '#D83440', l: '#FF6E6A', b: '#3D5A80', y: '#FFD640', g: '#2E7D32' }, [
  '................',
  '.......kk.......',
  '......krlk......',
  '......krrk......',
  '.......kk.......',
  '.....w.....w....',
  '................',
  '...w.....w.....w',
  '................',
  '.w.....w.....w..',
  '................',
  'kkkkkkkkkkkkkkkk',
  'krrkbbkyykbbkrrk',
  'krrkbbkyykbbkrrk',
  'kkkkkkkkkkkkkkkk',
  '................',
]);

export const MODE_SCRATCH = I('mode_scratch', { k: '#3A2A10', p: '#F4ECD8', s: '#B4B4B4', S: '#D8D8D8', d: '#8C8C8C', y: '#FFD640', g: '#1E9A3E' }, [
  '................',
  '.kkkkkkkkkkkkkk.',
  '.kppppppppppppk.',
  '.kpsSsspppsSspk.',
  '.kpSssspypsssdk.',
  '.kpssdspppssdsk.',
  '.kppppppppppppk.',
  '.kpsSsspppsSspk.',
  '.kpsssdpgpssdsk.',
  '.kpssdspppsdssk.',
  '.kppppppppppppk.',
  '.kpsSspyypsSspk.',
  '.kpssdpyypssdsk.',
  '.kppppppppppppk.',
  '.kkkkkkkkkkkkkk.',
  '................',
]);

export const MODE_SLOTS = I('mode_slots', { k: '#180A28', r: '#D83440', R: '#8C1834', y: '#FFD640', d: '#B07010', w: '#F4ECD8', c: '#E83030', g: '#2E9A3E', v: '#9C27B0' }, [
  '................',
  '..kkkkkkkkkkkk..',
  '.kyyyyyyyyyyyyk.',
  '.kykkkkkkkkkkyk.',
  '.kykwwkwwkwwkyk.',
  '.kykwckwvkwckykk',
  '.kykccgvvkccgyky',
  '.kykwckwvkwckykd',
  '.kykwwkwwkwwkykk',
  '.kykkkkkkkkkkyk.',
  '.kyyyyyyyyyyyyk.',
  '.krrrrrrrrrrrrk.',
  '.krRRRrRRrRRRrk.',
  '.krrrrrrrrrrrrk.',
  '.kkkkkkkkkkkkkk.',
  '................',
]);

export const MODES = { coin: MODE_COIN, wheel: MODE_WHEEL, plinko: MODE_PLINKO, scratch: MODE_SCRATCH, slots: MODE_SLOTS };

// ---- PvP badges ------------------------------------------------------------------------------------------------------
export const SWORDS = I('swords', { k: '#180A28', s: '#E8E8EE', S: '#9A9AA8', h: '#8A5220', y: '#FFD640' }, [
  '................',
  'kk............kk',
  'ksk..........ksk',
  '.ksk........ksk.',
  '..kSk......kSk..',
  '...kSk....kSk...',
  '....kSk..kSk....',
  '.....kSkkSk.....',
  '......kSSk......',
  '.....kSkkSk.....',
  '..kkkSk..kSkkk..',
  '..kyyk....kyyk..',
  '...kyk....kyk...',
  '..khk......khk..',
  '.khk........khk.',
  '.kk..........kk.',
]);

export const CLAW = I('claw', { k: '#3A0000', r: '#FF2A2A', R: '#8B0000', w: '#FFB0A0' }, [
  '................',
  '..k.....k.....k.',
  '..rk....rk....rk',
  '...rk....rk....r',
  '...wrk...wrk...w',
  '....rrk...rrk...',
  '....wrk...wrk...',
  '.....rrk...rrk..',
  '.....Rrk...Rrk..',
  '......rrk...rrk.',
  '......Rrk...Rrk.',
  '.......Rk....Rk.',
  '.......Rk....Rk.',
  '........k.....k.',
  '................',
  '................',
]);

export const SKULL = I('skull', { k: '#180A28', w: '#F4ECF8', b: '#C0B0DC', e: '#FF2A2A' }, [
  '................',
  '................',
  '....kkkkkkkk....',
  '...kwwwwwwwwk...',
  '..kwwwwwwwwwbk..',
  '..kwkkwwwkkwbk..',
  '..kwkekwwkekbk..',
  '..kwkkwwwkkwbk..',
  '..kwwwwkwwwwbk..',
  '...kwwkkkwwbk...',
  '....kwwwwwbk....',
  '....kwkwkwbk....',
  '....kkkkkkkk....',
  '................',
  '................',
  '................',
], { e: '#FFE070' });

export const CROWN = I('crown', { k: '#5C3A00', y: '#FFD640', h: '#FFF4B0', d: '#B07010', r: '#D83440', b: '#4FC3F7', g: '#40D060' }, [
  '................',
  '................',
  '................',
  '.k.....kk.....k.',
  'khk...khhk...khk',
  'kyk...kyyk...kyk',
  'kyyk.kyyyyk.kyyk',
  'kyyykyyyyyykyyyk',
  'kyyyyyyyyyyyyyyk',
  'kyryyybyygyyyryk',
  'kyyyyyyyyyyyyyyk',
  'khhhhhhhhhhhhhhk',
  'kddddddddddddddk',
  '.kkkkkkkkkkkkkk.',
  '................',
  '................',
]);

export const PADLOCK = I('padlock', { k: '#180A28', s: '#9A9AA8', S: '#D8D8E0', y: '#FFD640', d: '#B07010', h: '#FFF4B0' }, [
  '................',
  '................',
  '.....kkkkkk.....',
  '....kSSSSSSk....',
  '...kSk....kSk...',
  '...ksk....ksk...',
  '...ksk....ksk...',
  '..kkkkkkkkkkkk..',
  '..khhhhhhhhhhk..',
  '..kyyyyyyyyydk..',
  '..kyyyykkyyydk..',
  '..kyyyykkyyydk..',
  '..kyyyyykyyydk..',
  '..kdddddddddddk.',
  '..kkkkkkkkkkkk..',
  '................',
]);

export const BOT = I('bot', { k: '#180A28', s: '#8FA8C8', S: '#C8D8EC', d: '#4A5A70', e: '#5CE8E0', r: '#D83440', y: '#FFD640' }, [
  '.......ky.......',
  '.......kk.......',
  '...kkkkkkkkkk...',
  '..kSSSSSSSSSSk..',
  '..kSssssssssdk..',
  '.kkSkkkssskkdkk.',
  'kskSkekssskekdsk',
  'kskSkkkssskkkdsk',
  '.kkSssssssssdkk.',
  '..kSsskrrrksdk..',
  '..kSssssssssdk..',
  '..kddddddddddk..',
  '...kkkkkkkkkk...',
  '....ksdkksdk....',
  '....kkkk.kkkk...',
  '................',
]);

export const CHECK = I('check', { k: '#0A2A08', g: '#80FF40', d: '#40A020' }, [
  '................',
  '................',
  '............kk..',
  '...........kgk..',
  '..........kggk..',
  '.........kggk...',
  '..kk....kggk....',
  '.kgdk..kggk.....',
  '.kkgdkkggk......',
  '...kgdggk.......',
  '....kggk........',
  '.....kk.........',
  '................',
  '................',
  '................',
  '................',
]);

export const FLAME = I('flame', { k: '#3A0A00', r: '#C83A08', o: '#FF7A1A', y: '#FFE070', w: '#FFFFFF' }, [
  '................',
  '.......k........',
  '......kok.......',
  '......kook..k...',
  '.....kooook.kk..',
  '..k..koooookok..',
  '..kk.kooyookok..',
  '..kokooyyyookok.',
  '.kooooyyyyyooork',
  '.koooyyywyyyoork',
  '.krooyywwwyyoork',
  '.kroooyywyyoorrk',
  '..krroooyooorrk.',
  '...krrrooorrrk..',
  '....kkkkkkkkk...',
  '................',
]);

// ---- taunt pictograms (language-free; extras-pvp §9.7) -------------------------------------------------------------------
const SKIN = { k: '#180A28', s: '#E8B888', S: '#C08858', w: '#F4ECF8', y: '#FFD640', d: '#B07010', g: '#40D060', G: '#1E7A30', r: '#D83440', R: '#8C1834', b: '#3D5A80', B: '#6080AA', a: '#6A6A72', A: '#9A9AA8', t: '#2A1A30' };

export const TAUNTS = {
  gg: I('taunt_gg', SKIN, [
    '................',
    '................',
    '................',
    '.kk..........kk.',
    'kBBk........kRrk',
    'kbBBk.kkkk.kRRrk',
    'kbbBkkssssk.kRrk',
    'kbbBksSssSskkRrk',
    'kbbkssSssSsssRrk',
    'kbbkssSssSsssrrk',
    '.kkksssssssSkkk.',
    '....kSsssssk....',
    '.....kkkkkk.....',
    '................',
    '................',
    '................',
  ]),
  luck: I('taunt_luck', SKIN, [
    '................',
    '.....kkk.kkk....',
    '....kgggkgggk...',
    '....kgGgkgGgk...',
    '.kkk.kggkggk.kk.',
    'kgggk.kgggk.kggk',
    'kgGggkkgGkkgggGk',
    '.kgggggkggggggk.',
    'kgGggkkgGkkggGgk',
    'kgggk.kgggk.kggk',
    '.kkk.kggkggk.kk.',
    '....kgGgkgGgk...',
    '....kgggkggGk...',
    '.....kkk.kGk....',
    '..........kGk...',
    '...........kk...',
  ]),
  wow: I('taunt_wow', SKIN, [
    '................',
    '......kkkk......',
    '.....kyyyyk.....',
    '.....kyyyyk.....',
    '.....kyyyyk.....',
    '.....kyyyyk.....',
    '.....kyyydk.....',
    '......kyyk......',
    '......kydk......',
    '......kydk......',
    '.......kk.......',
    '................',
    '......kkkk......',
    '......kyyk......',
    '......kddk......',
    '.......kk.......',
  ]),
  rigged: I('taunt_rigged', SKIN, [
    '................',
    '.kkkkkkkk.......',
    '.kwwwwwwwk......',
    '.kwkwwwwwk......',
    '.kwwwkwwwk......',
    '.kwwwwwkwk...kk.',
    '.kwwwwwwwk..kAk.',
    '.kAAAAAAAk.kAAk.',
    '..kkkkkkk.kAAk..',
    '.........kAAk...',
    '....kk..kAAk....',
    '...kAAkkAAk.....',
    '...kAaAAAk......',
    '....kAaak.......',
    '...kakkk........',
    '...kk...........',
  ]),
  again: I('taunt_again', SKIN, [
    '................',
    '.....kkkkk......',
    '....kyyyyyk.k...',
    '...kyykkkyykyk..',
    '..kyyk...kyyyyk.',
    '..kyk....kyyyyk.',
    '..kyk.....kkkk..',
    '..kyk...........',
    '..kyk......kk...',
    '..kyk.....kyk...',
    '..kyyk...kyyk...',
    '...kyykkkyyk....',
    '....kyyyyyk.....',
    '.....kkkkk......',
    '................',
    '................',
  ]),
  steel: I('taunt_steel', SKIN, [
    '................',
    '................',
    '................',
    '.kkkkkkkkkkkkk..',
    'kAAAAAAAAAAAAAk.',
    'kAwwwwwwwwwwwAk.',
    '.kkaAAAAAAAAak..',
    '...kkaAAAAakk...',
    '.....kaAAak.....',
    '.....kaAAak.....',
    '....kaaAAaak....',
    '...kaaaaaaaak...',
    '..kkkkkkkkkkkk..',
    '................',
    '................',
    '................',
  ]),
  bye: I('taunt_bye', SKIN, [
    '................',
    '....k.k.k.......',
    '...ksksksk......',
    '...ksksksk.k....',
    '...ksksksksk....',
    '...kssssssk.....',
    '..kkssssssk.....',
    '.ksksssssk......',
    '.kssssssSk......',
    '..ksssssk...kk..',
    '...kSSSk...krrk.',
    '....kkk...krwrk.',
    '..........krrk..',
    '.......kk..kk...',
    '......krrk......',
    '.......kk.......',
  ]),
  respect: I('taunt_respect', SKIN, [
    '................',
    '................',
    '.....kkkkkk.....',
    '....kttttttk....',
    '....kttttttk....',
    '....kttttttk....',
    '....kttttttk....',
    '....krrrrrrk....',
    '..kkkttttttkkk..',
    '.kttttttttttttk.',
    '..kkkkkkkkkkkk..',
    '................',
    '....k......k....',
    '.....k....k.....',
    '......kkkk......',
    '................',
  ]),
};
export const TAUNT_ORDER = ['gg', 'luck', 'wow', 'rigged', 'again', 'steel', 'bye', 'respect'];

// ---- helpers -----------------------------------------------------------------------------------------------------------
function drawDef(def) {
  const img = image(def.grid[0].length, def.grid.length);
  def.grid.forEach((row, y) => [...row].forEach((ch, x) => ch !== '.' && put(img, x, y, c(def.pal[ch]))));
  return img;
}

/** Centre a 16 × 16 image in a new 16 × 16 (identity helper for procedural icons that are already centred). */
export const as16 = (img) => over(image(16, 16), img, 0, 0);

export const ALL_DEFS = [
  EMBLEM_HEADS, EMBLEM_TAILS, ...SCRATCH_SYMBOLS, MODE_COIN, MODE_WHEEL, MODE_PLINKO, MODE_SCRATCH, MODE_SLOTS, SWORDS, CLAW, SKULL, CROWN, PADLOCK,
  BOT, CHECK, FLAME, ...Object.values(TAUNTS),
];
