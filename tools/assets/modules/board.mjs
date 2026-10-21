// In-world board kit (docs/architecture/ui-v2.md §7, §9; lane W). Java only, core-owned (a `core/` segment):
//   textures/entity/core/board/faces_w2.png                 512 × 256 card faces (37 × 49, 13 × 4) + backs (row 4)
//   textures/entity/core/board/chips_w.png                  128 × 64 chip tops (16²) + 6-disc side bands (16 × 24)
//   textures/entity/core/board/felt_w_<shape>_<theme>.png   felt tops with the rail, 64 texels per block, cutout
//   textures/entity/core/board/felt_print_<game>_<furniture>.png  full 3×2 table felt print (FeltFrame), 64 texels per block
//   textures/entity/core/board/rail_w_<theme>.png           16 × 16 apron wood
//   textures/entity/core/board/holo_backing.png             4 × 4 white (the text backing, tinted in code)
// UV contracts live in java client/board/BoardTextures; shapes in core/ui/v2/board/BoardGeometry (FELTS mirrors it).
import { png, JAVA_ASSETS } from '../lib/emit.mjs';
import { FELTS, PRINT_GAMES, PRINT_THEMES, THEMES, chipsW, facesW2, feltMarks, feltPrint, feltTop, holoBacking, railSide } from './board/art.mjs';

const T = `${JAVA_ASSETS}/textures/entity/core/board`;

export default function generate() {
  const out = [];
  out.push(png(`${T}/faces_w2.png`, facesW2()));
  out.push(png(`${T}/chips_w.png`, chipsW()));
  out.push(png(`${T}/holo_backing.png`, holoBacking()));
  for (const theme of Object.keys(THEMES)) {
    out.push(png(`${T}/rail_w_${theme}.png`, railSide(theme)));
    for (const shape of Object.keys(FELTS)) out.push(png(`${T}/felt_w_${shape}_${theme}.png`, feltTop(shape, theme)));
  }
  // full multi-block tables: the felt print decal over FeltFrame (java BoardTextures.print)
  for (const theme of Object.keys(PRINT_THEMES))
    for (const game of Object.keys(PRINT_GAMES)) out.push(png(`${T}/felt_print_${game}_${theme}.png`, feltPrint(game, theme)));
  out.push(png(`${T}/felt_marks.png`, feltMarks()));
  return out;
}
