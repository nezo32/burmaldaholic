// Card-table presentation art (docs/design/visual/cards.md): Blackjack, Texas Hold'em, Ultimate Texas Hold'em,
// Baccarat / Chemin de fer. JAVA ONLY (the project dropped Bedrock). Shared by the four games, so every file is
// core-owned (gradle checkAssetOwnership): the requested `textures/gui/cards/` lives at `textures/gui/core/cards/`.
//
//   textures/gui/core/cards/             UV-blitted atlases and big pictures
//     faces_{l,m,s}[_classic].png          13 × 4 faces (rows ♠ ♥ ♦ ♣, columns A 2 … 10 J Q K); four-colour default
//     backs.png                            6 designs (rows) × L | M | S | W
//     table_{crescent,oval}_<theme>[_compact].png, backdrop_<theme>.png
//   textures/gui/sprites/core/cards/…    sprite ids `burmaldaholic:core/cards/<path>` (nine-slice / animation .mcmeta)
//   textures/font/core/card_index.png + font/core/card_index.json   the rank-index font (runtime text)
//   textures/entity/core/cards/faces.png, chips.png                  the BER atlases (in-world tables)
//   textures/particle/core/card_suit_<n>.png                          tumbling-suit particle frames
//
// Helpers live in ./cards/ (the driver only loads *.mjs directly in modules/). Deterministic (gen-assets --check).
import { JAVA_ASSETS, json, mcmeta, png } from '../lib/emit.mjs';
import { BACK_ORDER, backAtlas, faceAtlas, indexFontProvider, indexFontSheet, worldAtlas } from './cards/faces.mjs';
import {
  DENOMS, bead, chipBig, chipDisc, chipHatch, chipSelect, dealerButton, deck, discardTray, emblemPrint, pairDot, printBox, printInsurance, printPot, printSlot, printSpot,
  printUth, rack, shoe, spotGlow, trayFill,
} from './cards/props.mjs';
import { hstrip, vstrip } from './cards/px.mjs';
import { BACKDROP, backdrop, tableArt } from './cards/table.mjs';
import { THEME_ORDER } from './cards/theme.mjs';
import {
  BADGE_LEVELS, BOT_AVATARS, ICON_NAMES, PLATE_STATES, STAMP_KINDS, avatarFrame, botAvatar, botBadge, cardGlow, cardShadow, consoleBar, curl, emote, icon, plaque,
  progress, roadPanel, seatPlate, shimmer, stamp, suitParticle, tableButton, tag, tagTail, thinking, totalBadge,
} from './cards/ui.mjs';

const T = `${JAVA_ASSETS}/textures`;
const GUI = `${T}/gui/core/cards`;
const SPR = `${T}/gui/sprites/core/cards`;

/** Table sizes (GUI px): full layout (GUI ≥ 427 × 240) and compact (≥ 284 × 160). */
export const TABLE_SIZES = { full: [408, 184], compact: [280, 124] };

export default function generate() {
  const out = [];
  const gui = (name, img) => out.push(png('java', `${GUI}/${name}.png`, img));
  const sprite = (name, img, meta) => {
    out.push(png('java', `${SPR}/${name}.png`, img));
    if (meta) out.push(mcmeta(`${SPR}/${name}.png`, meta));
  };
  const nine = (w, h, border) => ({ nineSlice: { width: w, height: h, border } });

  // ---- cards ----
  for (const size of ['l', 'm', 's']) {
    gui(`faces_${size}`, faceAtlas(size, true));
    gui(`faces_${size}_classic`, faceAtlas(size, false));
  }
  gui('backs', backAtlas());

  // ---- tables + rooms ----
  for (const theme of THEME_ORDER) {
    for (const shape of ['crescent', 'oval']) {
      gui(`table_${shape}_${theme}`, tableArt(shape, theme, ...TABLE_SIZES.full));
      gui(`table_${shape}_${theme}_compact`, tableArt(shape, theme, ...TABLE_SIZES.compact));
    }
    gui(`backdrop_${theme}`, backdrop(theme));
  }

  // ---- chips ----
  for (const d of DENOMS) {
    sprite(`chip/disc_${d}`, chipDisc(d));
    sprite(`chip/big_${d}`, chipBig(d));
  }
  sprite('chip/disc_tint', chipDisc('tint'));
  sprite('chip/disc_hatch', chipHatch());
  sprite('chip/select', chipSelect());

  // ---- prints (white, tinted by the theme's print colour) + glows ----
  sprite('print/spot', printSpot());
  for (const k of ['trips', 'ante', 'blind', 'play']) sprite(`print/uth_${k}`, printUth(k));
  sprite('print/box', printBox(), nine(32, 24, 5));
  sprite('print/slot_l', printSlot(37, 49));
  sprite('print/slot_m', printSlot(21, 29));
  sprite('print/pot', printPot());
  sprite('print/insurance', printInsurance());
  for (const k of ['player', 'banker', 'tie', 'pair']) sprite(`print/emblem_${k}`, emblemPrint(k));
  sprite('fx/spot_glow', vstrip([0, 1, 2, 3].map(spotGlow)), { frametime: 3 });

  // ---- props ----
  for (const theme of THEME_ORDER) {
    sprite(`prop/shoe_${theme}`, shoe(theme));
    sprite(`prop/tray_${theme}`, discardTray(theme));
    sprite(`prop/rack_${theme}`, rack(theme));
  }
  sprite('prop/tray_fill', trayFill());
  for (const d of BACK_ORDER) sprite(`prop/deck_${d}`, deck(d));
  sprite('prop/dealer_button', dealerButton());
  for (const k of ['player', 'banker', 'tie']) sprite(`bead/${k}`, bead(k));
  sprite('bead/pair_player', pairDot('player'));
  sprite('bead/pair_banker', pairDot('banker'));

  // ---- seats, bots ----
  for (const s of PLATE_STATES) sprite(`seat/plate_${s}`, seatPlate(s), nine(40, 22, 6));
  sprite('seat/avatar_frame', avatarFrame(false));
  sprite('seat/avatar_frame_gold', avatarFrame(true));
  for (const b of BOT_AVATARS) sprite(`bot/${b}`, botAvatar(b));
  for (const l of BADGE_LEVELS) sprite(`bot/badge_${l}`, botBadge(l));
  sprite('bot/thinking', vstrip([0, 1, 2].map(thinking)), { frametime: 10 });
  for (const k of ['happy', 'grumpy']) sprite(`bot/emote_${k}`, emote(k));

  // ---- panels, stamps, tags, badges ----
  for (const theme of THEME_ORDER) {
    sprite(`panel/plaque_${theme}`, plaque(theme), nine(48, 20, 7));
    sprite(`panel/console_${theme}`, consoleBar(theme), nine(48, 28, 7));
    for (const st of ['normal', 'highlighted', 'disabled'])
      sprite(`button/table_${theme}${st === 'normal' ? '' : `_${st}`}`, tableButton(theme, st), nine(64, 20, 4));
  }
  sprite('panel/road', roadPanel(), nine(24, 24, 4));
  for (const k of Object.keys(STAMP_KINDS)) sprite(`stamp/${k}`, stamp(k), nine(32, 16, 5));
  sprite('tag/bubble', tag(), nine(24, 11, 4));
  sprite('tag/tail', tagTail());
  sprite('badge/total', totalBadge(false), nine(14, 11, 3));
  sprite('badge/total_gold', totalBadge(true), nine(14, 11, 3));

  // ---- card fx ----
  sprite('fx/glow_l', vstrip([0, 1, 2, 3].map((f) => cardGlow(f, 'l'))), { frametime: 3 });
  sprite('fx/glow_m', vstrip([0, 1, 2, 3].map((f) => cardGlow(f, 'm'))), { frametime: 3 });
  sprite('fx/shimmer', shimmer());
  sprite('fx/curl_l', curl(37, 6));
  sprite('fx/curl_m', curl(21, 4));
  sprite('fx/shadow_l', cardShadow('l'));
  sprite('fx/shadow_m', cardShadow('m'));
  sprite('fx/progress', progress());

  // ---- action icons ----
  for (const n of ICON_NAMES) sprite(`icon/${n}`, icon(n));

  // ---- font, world, particles ----
  out.push(png('java', `${T}/font/core/card_index.png`, indexFontSheet()));
  out.push(json('java', `${JAVA_ASSETS}/font/core/card_index.json`, indexFontProvider()));
  out.push(png('java', `${T}/entity/core/cards/faces.png`, worldAtlas()));
  out.push(png('java', `${T}/entity/core/cards/chips.png`, hstrip(DENOMS.map((d) => chipDisc(d)))));
  for (let s = 0; s < 4; s++) for (let f = 0; f < 2; f++) out.push(png('java', `${T}/particle/core/card_suit_${s * 2 + f}.png`, suitParticle(s, f)));

  void BACKDROP;
  return out;
}
