// Table UI v2 kit sprites (lane K; docs/architecture/ui-v2.md §9, D11): everything under
// `textures/gui/sprites/core/v2/` (atlas ids `burmaldaholic:core/v2/<name>`), read by client.ui.v2.V2Sprites. Every
// component has a flat-colour fallback in Java, so a missing sprite never breaks a screen. Helpers in ./ui2/.
import { JAVA_ASSETS, json, mcmeta, png } from '../lib/emit.mjs';
import {
  BUTTON_STATES,
  BUTTON_STYLES,
  DENOMS,
  FELTS,
  GLYPHS,
  T,
  VALUE_PILLS,
  banner,
  bar,
  button,
  chip,
  chipIcon,
  chipRing,
  disc,
  felt,
  focusRing,
  glyph,
  iconButton,
  keyHint,
  pill,
  plate,
  smallPill,
  stamp,
  stampDot,
  toast,
  vignette,
} from './ui2/art.mjs';

const SPR = `${JAVA_ASSETS}/textures/gui/sprites/core/v2`;
const nine = (w, h, border) => ({ nineSlice: { width: w, height: h, border } });

export default function generate() {
  const out = [];
  const sprite = (name, img, meta) => {
    out.push(png(`${SPR}/${name}.png`, img));
    if (meta) out.push(mcmeta(`${SPR}/${name}.png`, meta));
  };
  // chips: tray (T1 20², T2 24², T3 32²), selection rings, felt discs (10², 12², 16²)
  for (const d of DENOMS) {
    sprite(`chip_${d}_s`, chip(d, 20));
    sprite(`chip_${d}`, chip(d, 24));
    sprite(`chip_${d}_l`, chip(d, 32));
    sprite(`felt_disc_${d}_s`, disc(d, 10));
    sprite(`felt_disc_${d}`, disc(d, 12));
    sprite(`felt_disc_${d}_l`, disc(d, 16));
  }
  sprite('chip_selected_ring_s', chipRing(20));
  sprite('chip_selected_ring', chipRing(24));
  sprite('chip_selected_ring_l', chipRing(32));
  sprite('chip_icon', chipIcon());
  // pills and plates
  for (const [name, [fill, rim]] of Object.entries(VALUE_PILLS)) sprite(name, pill(12, 11, fill, rim), nine(12, 11, 3));
  sprite('plate', plate(T.edge), nine(24, 18, 3));
  sprite('plate_you', plate(T.you), nine(24, 18, 3));
  sprite('plate_acting', plate(T.gold), nine(24, 18, 3));
  sprite('title_pill', smallPill(T.panel, T.panelHi), nine(16, 14, 3));
  sprite('info_pill', smallPill(T.panel, T.panelHi), nine(16, 14, 3));
  sprite('total_pill', smallPill(T.panel, T.ink), nine(16, 14, 3));
  sprite('balance_pill', pill(16, 14, T.ink, T.goldShade, { light: 0.06 }), nine(16, 14, 3));
  // stamps
  const fills = { win: T.winFill, lose: T.loseFill, push: T.pushFill, gold: T.goldFill };
  for (const [k, f] of Object.entries(fills)) {
    sprite(`stamp_${k}`, stamp(f), nine(24, 16, 4));
    sprite(`stamp_dot_${k}`, stampDot(f));
  }
  // buttons, icon buttons, key plate, glyphs
  for (const st of BUTTON_STYLES) for (const s of BUTTON_STATES) sprite(`${st}${s}`, button(st, s), nine(32, 30, 4));
  for (const s of BUTTON_STATES) sprite(`icon_button${s}`, iconButton(s), nine(20, 20, 4));
  sprite('key_hint', keyHint(), nine(10, 10, 2));
  for (const g of GLYPHS) {
    sprite(`icon/${g}`, glyph(g, 12));
    sprite(`icon/${g}_l`, glyph(g, 16));
  }
  // frame pieces
  sprite('topbar', bar(22, true), nine(32, 22, 3));
  sprite('dock', bar(44, false), nine(32, 44, 3));
  sprite('banner', banner(), nine(48, 28, 5));
  sprite('toast', toast(), nine(32, 15, 2));
  sprite('focus_ring', focusRing(), nine(16, 16, 2));
  sprite('felt_vignette', vignette(), nine(64, 64, 16));
  let seed = 7;
  for (const [theme, base] of Object.entries(FELTS)) {
    sprite(`felt_${theme}`, felt(base, (seed += 1013)));
    out.push(json(`${SPR}/felt_${theme}.png.mcmeta`, { gui: { scaling: { type: 'tile', width: 64, height: 64 } } }));
  }
  return out;
}
