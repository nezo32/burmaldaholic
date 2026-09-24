// Bedrock form button icons (global.md §2.2 "Bedrock buttons", §5.2): 32², drawn as 16² pixel art at 2×
// with a 1 px ink outline, in the casino palette. Icons carry meaning; the button text carries the verb.
import { CHIPS } from '../../lib/palette.mjs';
import { drawGrid, ellipse, image, rect, setPx } from '../../lib/grid.mjs';
import { outline, scale } from '../../lib/transforms.mjs';
import { trophy } from './ui.mjs';

const LEGEND = {
  Y: 'gold', o: 'gold.shade', w: '#FFF3A0', b: 'bone', B: 'bone.shade', R: 'chip.red', r: 'chip.light', D: 'chip.dark',
  P: 'frame', p: 'lilac', G: 'bonus', n: '#8A5A30', N: '#5A3418', s: '#6A7080', S: '#C8CCD4',
};

const GRIDS = {
  wallet: [
    '................', '................', '................', '..nnnnnnnnnnn...', '.nNNNNNNNNNNNn..', '.nNnnnnnnnnnNn..',
    '.nNnnnnnnnnnNn..', '.nNnnnnnnnnYYYY.', '.nNnnnnnnnnYwoY.', '.nNnnnnnnnnYYYY.', '.nNnnnnnnnnnNn..', '.nNnnnnnnnnnNn..',
    '.nNNNNNNNNNNNn..', '..nnnnnnnnnnn...', '................', '................',
  ],
  contracts: [
    '................', '...bbbbbbbbbb...', '..bBbbbbbbbbBb..', '...bbbbbbbbbb...', '...bBBBBBBBBb...', '...bbbbbbbbbb...',
    '...bBBBBBBbbb...', '...bbbbbbbbbb...', '...bBBBBBBBBb...', '...bbbbbbbbbb...', '...bBBBBbbbbb...', '...bbbbbbbRRb...',
    '...bbbbbbRrRR...', '..bBbbbbbbRRbb..', '...bbbbbbbbbb...', '................',
  ],
  loan: [
    '................', '......oYYo......', '.......oo.......', '......YYYY......', '.....YwYYYY.....', '....YwYYYYYY....',
    '...YwYYYYYYYY...', '...YYYYYYYYYo...', '..YYYYYYYYYYYo..', '..YYYYYYYYYYYo..', '..YYYYYYYYYYoo..', '..oYYYYYYYYooRR.',
    '...oooooooooRrR.', '............RRR.', '................', '................',
  ],
  my_casino: [
    '................', '.......YY.......', '......PPPP......', '.....PPPPPP.....', '....PPPPPPPP....', '...PPPPPPPPPP...',
    '..PPPPPPPPPPPP..', '...bbbbbbbbbb...', '...bYbbbbbbYb...', '...bbbbbbbbbb...', '...bYbbYYbbYb...', '...bbbbYYbbbb...',
    '...bbbbYYbbbb...', '..oooooooooooo..', '................', '................',
  ],
  rules: [
    '................', '................', '................', '.PbbbbbbPbbbbbbP', '.PbBBBBbPbBBBBbP', '.PbbbbbbPbbbbbbP',
    '.PbBBBbbPbBBBBbP', '.PbbbbbbPbbbbbbP', '.PbBBBBbPbBBBbbP', '.PbbbbbbPbbbbbbP', '.PbBBBbbPbBBBBbP', '.PbbbbbbPbbbbbbP',
    '.PPPPPPPPPPPPPPP', '................', '................', '................',
  ],
  admin: [
    '................', '..ssssssssssss..', '..sSSSSSSSSSSs..', '..sSSSSYYSSSSs..', '..sSSSYYYYSSSs..', '..sSSYYYYYYSSs..',
    '..sSSSYYYYSSSs..', '..sSSSYSSYSSSs..', '...sSSSSSSSSs...', '...sSSSSSSSSs...', '....sSSSSSSs....', '.....sSSSSs.....',
    '......sSSs......', '.......ss.......', '................', '................',
  ],
  shop: [
    '................', '..RbRbRbRbRbRb..', '..RbRbRbRbRbRb..', '..RbRbRbRbRbRb..', '...RR.bb.RR.b...', '...n........n...',
    '...n..YYYY..n...', '...n..YwoY..n...', '...n........n...', '..nnnnnnnnnnnn..', '..nNNNNNNNNNNn..', '..nNNNNNNNNNNn..',
    '..nnnnnnnnnnnn..', '................', '................', '................',
  ],
};

/** Top-down chip 12² at (x, y) in a 16² icon. */
function chip(img, x, y, d = 25) {
  const { base, stripe } = CHIPS[d];
  ellipse(img, x + 6, y + 6, 6, 6, (px, py, r) => (r > 0.78 ? ((px + py) % 3 === 0 ? stripe : base) : r > 0.5 ? stripe : base));
}

const ARROWS = {
  down: ['..G..', '..G..', 'GGGGG', '.GGG.', '..G..'],
  up: ['..Y..', '.YYY.', 'YYYYY', '..Y..', '..Y..'],
  plus: ['..G..', '..G..', 'GGGGG', '..G..', '..G..'],
  minus: ['.....', '.....', 'RRRRR', '.....', '.....'],
};

function chipWith(mark) {
  const img = image(16, 16);
  chip(img, 1, 1);
  drawGrid(img, ARROWS[mark], LEGEND, 10, 10);
  return img;
}

function target() {
  const img = image(16, 16);
  ellipse(img, 8, 8, 7, 7, (x, y, d) => (Math.floor(d * 4) % 2 === 0 ? 'chip.red' : 'bone'));
  return img;
}

function gear() {
  const img = image(16, 16);
  ellipse(img, 8, 8, 7, 7, (x, y, d) => {
    const a = Math.atan2(y + 0.5 - 8, x + 0.5 - 8);
    const tooth = Math.cos(a * 8) > 0.2;
    if (d > 0.72 && !tooth) return null;
    return d < 0.3 ? null : d < 0.45 ? '#6A7080' : '#C8CCD4';
  });
  return img;
}

function challenge() {
  const img = target();
  rect(img, 11, 1, 11, 7, '#8A5A30');
  rect(img, 12, 1, 14, 3, 'gold');
  setPx(img, 8, 8, 'gold');
  return img;
}

const ICONS = {
  wallet: () => drawGrid(image(16, 16), GRIDS.wallet, LEGEND),
  contracts: () => drawGrid(image(16, 16), GRIDS.contracts, LEGEND),
  loan: () => drawGrid(image(16, 16), GRIDS.loan, LEGEND),
  achievements: trophy,
  challenges: challenge,
  my_casino: () => drawGrid(image(16, 16), GRIDS.my_casino, LEGEND),
  rules: () => drawGrid(image(16, 16), GRIDS.rules, LEGEND),
  settings: gear,
  admin: () => drawGrid(image(16, 16), GRIDS.admin, LEGEND),
  deposit: () => chipWith('down'),
  withdraw: () => chipWith('up'),
  buy: () => chipWith('plus'),
  sell: () => chipWith('minus'),
  shop: () => drawGrid(image(16, 16), GRIDS.shop, LEGEND),
};

export const ICON_NAMES = Object.keys(ICONS);

/** 32² icon: 16² art + ink outline, scaled 2×. */
export const icon = (name) => scale(outline(ICONS[name](), '#180A28'), 2);
