// Comps art (EC-2, EC-SPEC §4.3). Java only; outputs live in the comps module's namespace:
//   item/comps/free_bet_token.png   16² free-bet token: gold-rimmed felt-green chip with a bone star and a
//                                   bone "ticket" notch on the rim (reads as "not a normal chip" in the hotbar)
// The item model / item definition JSON next to it are hand-written (java/src/main/resources/...).
import { png, JAVA_ASSETS } from '../lib/emit.mjs';
import { ellipse, image, setPx } from '../lib/grid.mjs';
import { col, shade } from '../lib/palette.mjs';
import { outline } from '../lib/transforms.mjs';

const T = `${JAVA_ASSETS}/textures`;

const STAR = [
  '...#...',
  '...#...',
  '#######',
  '.#####.',
  '..###..',
  '.##.##.',
  '.#...#.',
];

export function freeBetToken() {
  let img = image(16, 16);
  const gold = col('gold');
  const goldShade = col('gold.shade');
  const felt = col('felt');
  // rim: gold, darker toward the bottom-right; face: felt green with a lighter top-left
  ellipse(img, 8, 8, 6.5, 6.5, (x, y, d) => {
    if (d > 0.78) return x + y > 17 ? goldShade : gold;
    return x + y < 13 ? shade(felt, 1.25) : felt;
  });
  // ticket notches on the rim (left / right): bone inserts
  for (const [x, y] of [[2, 7], [2, 8], [13, 7], [13, 8], [7, 2], [8, 2], [7, 13], [8, 13]]) setPx(img, x, y, col('bone'));
  // bone star in the middle
  STAR.forEach((row, dy) => [...row].forEach((c, dx) => c === '#' && setPx(img, 5 + dx, 5 + dy, col('bone'))));
  img = outline(img, col('ink'));
  return img;
}

export default function generate() {
  return [png(`${T}/item/comps/free_bet_token.png`, freeBetToken())];
}
