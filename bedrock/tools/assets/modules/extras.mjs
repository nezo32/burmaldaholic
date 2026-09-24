// Extras + PvP presentation art (docs/design/visual/extras.md; animation/extras-pvp.md §10), lane B-L7 → Java only
// (the project is Java-only since 2026-09-24: no Bedrock outputs). Helpers live in ./extras/ (not loaded as modules
// by the driver: only *.mjs files directly in modules/ are). Deterministic; `--check` keeps the committed PNGs honest.
//
// Java (assets/burmaldaholic/textures/…; ownership = an `extras/` or `pvp/` folder, gradle checkAssetOwnership):
//   gui/extras/*                           code-indexed sheets + backdrops (blit with UV, not in the atlas)
//   gui/pvp/*                              PvP backdrops and code-indexed sheets
//   gui/sprites/burmaldaholic/extras/*     atlas sprites (nine-slices, animated strips) → `burmaldaholic:burmaldaholic/extras/<name>`
//   gui/sprites/burmaldaholic/pvp/*        PvP atlas sprites
//   item/extras/lucky_coin_{heads,tails}   item-model variants for the in-world toss
//   entity/extras/*, particle/extras/*     BER parts and the foil-flake particle frames
import { JAVA_ASSETS, json, mcmeta, png } from '../lib/emit.mjs';
import {
  chainPips, coinFlame, coinGlint, coinHeat, coinPad, coinShadow, coinSpinSheet, luckyCoinItem, miniCoin,
} from './extras/coin.mjs';
import { ALL_DEFS } from './extras/icons.mjs';
import { K, c, crop, hstrip, image, put, rect, validate, vstrip } from './extras/kit.mjs';
import {
  BIN_TIERS, edgeGlow, plinkoBallSheet, plinkoBinHiddenStrip, plinkoBinSheet, plinkoBoard, plinkoChuteSheet, plinkoMiniBoard, plinkoPegSheet,
} from './extras/plinko.mjs';
import {
  allInTag, badges16, badges20, botBadge, brokenChain, bubble, bubbleTail, claw, crown, flameSnuff, grudgeHalf, headFrame, lobbyRow, modeBanner, modeCard,
  modeIcons16, modeIcons40, padlock, plate, PLATES, podium, potChips, potGlow, potPlaque, rankMedals, recordChip, revealPlaque, seatEmpty, streakFlame,
  tauntIcons16, tauntIcons20, vsBadge, winnerBanner,
} from './extras/pvp.mjs';
import { BACKDROPS, SCENE, banner, marquee, sceneFrame } from './extras/scenes.mjs';
import {
  TICKET, charVignette, charred, explosionPuff, flakes, foil, foilFinal, foilShimmer, scraper, scratchEdges, scratchSymbols16, scratchSymbols20,
  scratchSymbols40, scratchSymbols40Win, scuff, ticket, ticketShadow, tornCorner, trioFrame,
} from './extras/scratch.mjs';
import {
  wheelBulbs, wheelFace, wheelFlapper, wheelHub, wheelIcons16, wheelIcons40, wheelIcons8, wheelParts, wheelPop, wheelRim, wheelStand,
} from './extras/wheel.mjs';

const T = `${JAVA_ASSETS}/textures`;
const GUI = `${T}/gui/extras`;
const GUI_PVP = `${T}/gui/pvp`;
const SPR = `${T}/gui/sprites/burmaldaholic/extras`;
const SPR_PVP = `${T}/gui/sprites/burmaldaholic/pvp`;

const nine = (w, h, border) => ({ nineSlice: { width: w, height: h, border } });

export default function generate() {
  validate(ALL_DEFS);
  const out = [];
  const sheetOut = (dir, name, img) => out.push(png('java', `${dir}/${name}.png`, img));
  const sprite = (dir, name, img, meta) => {
    out.push(png('java', `${dir}/${name}.png`, img));
    if (meta) out.push(mcmeta(`${dir}/${name}.png`, meta));
  };
  const rawMeta = (dir, name, value) => out.push(json('java', `${dir}/${name}.png.mcmeta`, value));
  /** Animated strip with non-square frames: Java needs the frame `height` in the animation section. */
  const tall = (dir, name, img, frametime, height) => {
    out.push(png('java', `${dir}/${name}.png`, img));
    rawMeta(dir, name, { animation: { frametime, height } });
  };

  // ---- scenes: backdrops (code-blitted), frames / marquees / banners (atlas) ----
  for (const g of ['coin', 'wheel', 'plinko', 'scratch']) sheetOut(GUI, `${g}_backdrop`, BACKDROPS[g]());
  sheetOut(GUI_PVP, 'arena_backdrop', BACKDROPS.arena());
  sheetOut(GUI_PVP, 'arena_backdrop_grudge', BACKDROPS.arena_grudge());
  for (const theme of Object.keys(SCENE)) {
    const dir = theme === 'pvp' || theme === 'grudge' ? SPR_PVP : SPR;
    const prefix = theme === 'pvp' ? '' : theme === 'grudge' ? 'grudge_' : `${theme}_`;
    sprite(dir, `${prefix}frame`, sceneFrame(theme), nine(64, 64, 12));
    tall(dir, `${prefix}marquee`, marquee(theme), 3, 12);
    sprite(dir, `${prefix}banner`, banner(theme), nine(48, 24, 10));
  }

  // ---- Coin Flip ----
  sheetOut(GUI, 'coin_spin', coinSpinSheet());
  sheetOut(GUI, 'coin_glint', hstrip(coinGlint()));
  sheetOut(GUI, 'coin_heat', hstrip([coinHeat('fire'), coinHeat('soul')]));
  sheetOut(GUI, 'chain_pips', hstrip(chainPips()));
  sheetOut(GUI, 'coin_mini', hstrip([miniCoin('heads', 14), miniCoin('tails', 14)]));
  sprite(SPR, 'coin_shadow', coinShadow());
  sprite(SPR, 'coin_pad', coinPad());
  tall(SPR, 'coin_flame', coinFlame('fire'), 2, 24);
  tall(SPR, 'coin_flame_soul', coinFlame('soul'), 2, 24);
  for (const kind of ['heads', 'tails']) out.push(png('java', `${T}/item/extras/lucky_coin_${kind}.png`, luckyCoinItem(kind)));

  // ---- Wheel of Fortune ----
  sheetOut(GUI, 'wheel_face', wheelFace());
  sheetOut(GUI, 'wheel_rim', wheelRim());
  sheetOut(GUI, 'wheel_bulbs', hstrip(wheelBulbs()));
  sheetOut(GUI, 'wheel_icons', wheelIcons16());
  sheetOut(GUI, 'wheel_icons_8', wheelIcons8());
  sheetOut(GUI, 'wheel_icons_40', wheelIcons40());
  sheetOut(GUI, 'wheel_parts', wheelParts());
  sprite(SPR, 'wheel_stand', wheelStand());
  sprite(SPR, 'wheel_pop', wheelPop());
  sprite(SPR, 'wheel_flapper', wheelFlapper());
  out.push(png('java', `${SPR}/wheel_hub.png`, vstrip(wheelHub())));
  rawMeta(SPR, 'wheel_hub', { animation: { frametime: 1, frames: [...Array(54).fill(0), 1, 2, 3, 4, 5] } });

  // ---- Plinko ----
  sheetOut(GUI, 'plinko_board', plinkoBoard());
  sheetOut(GUI, 'plinko_peg', plinkoPegSheet());
  sheetOut(GUI, 'plinko_ball', plinkoBallSheet());
  sheetOut(GUI, 'plinko_bins', plinkoBinSheet());
  sheetOut(GUI, 'plinko_chute', plinkoChuteSheet());
  sheetOut(GUI, 'plinko_mini_board', plinkoMiniBoard());
  tall(SPR, 'plinko_bin_hidden', plinkoBinHiddenStrip(), 3, 14);
  sprite(SPR, 'edge_glow', edgeGlow(), nine(32, 32, 12));

  // ---- Scratch Cards ----
  sheetOut(GUI, 'scratch_ticket_basic', ticket('basic'));
  sheetOut(GUI, 'scratch_ticket_gold', ticket('gold'));
  sheetOut(GUI, 'scratch_card_showdown', ticket('showdown'));
  const [sw, sh] = TICKET.solo.cell;
  const [dw, dh] = TICKET.showdown.cell;
  sheetOut(GUI, 'scratch_foil', vstrip([foil('basic', sw, sh), foil('gold', sw, sh)]));
  sheetOut(GUI, 'scratch_foil_showdown', foil('showdown', dw, dh));
  sheetOut(GUI, 'scratch_edges', vstrip(['basic', 'gold', 'showdown'].map((t) => hstrip(scratchEdges(t)))));
  sheetOut(GUI, 'scratch_scuff', scuff(sw, sh));
  sheetOut(GUI, 'scratch_symbols', scratchSymbols16());
  sheetOut(GUI, 'scratch_symbols_20', scratchSymbols20());
  sheetOut(GUI, 'scratch_symbols_40', scratchSymbols40());
  sheetOut(GUI, 'scratch_symbols_40_win', scratchSymbols40Win());
  sheetOut(GUI, 'torn_corner', hstrip(tornCorner()));
  sheetOut(GUI, 'explosion_puff', hstrip(explosionPuff()));
  sheetOut(GUI, 'flakes', flakes());
  tall(SPR, 'scratch_foil_shimmer', foilShimmer(sw, sh), 7, sh);
  sprite(SPR, 'scratch_foil_shimmer_small', foilShimmer(dw, dh), { frametime: 7 });
  sprite(SPR, 'foil_final', foilFinal(), { frametime: 3 });
  sprite(SPR, 'charred', charred(), { frametime: 10 });
  sprite(SPR, 'trio_frame', trioFrame(), nine(16, 16, 4));
  sprite(SPR, 'scraper', scraper());
  sprite(SPR, 'char_vignette', charVignette(), nine(32, 32, 12));
  sprite(SPR, 'ticket_shadow', ticketShadow(), nine(16, 16, 6));

  // ---- PvP ----
  for (const kind of Object.keys(PLATES)) sprite(SPR_PVP, `plate_${kind}`, plate(kind), nine(48, 32, 10));
  for (const kind of ['you', 'rival', 'bot']) sprite(SPR_PVP, `head_frame_${kind}`, headFrame(kind));
  for (const level of ['easy', 'normal', 'hard', 'mixed']) sprite(SPR_PVP, `bot_${level}`, botBadge(level));
  sprite(SPR_PVP, 'vs_badge', vsBadge());
  sprite(SPR_PVP, 'pot_plaque', potPlaque(), nine(48, 24, 8));
  sprite(SPR_PVP, 'pot_glow', potGlow());
  sheetOut(GUI_PVP, 'pot_chips', hstrip(['s', 'm', 'l'].map(potChips)));
  sprite(SPR_PVP, 'grudge_left', grudgeHalf('left'), nine(128, 40, 12));
  sprite(SPR_PVP, 'grudge_right', grudgeHalf('right'), nine(128, 40, 12));
  for (const kind of ['lead', 'trail', 'even']) sprite(SPR_PVP, `record_chip_${kind}`, recordChip(kind), nine(16, 10, 3));
  for (const kind of ['friendly', 'cheeky']) {
    sprite(SPR_PVP, `bubble_${kind}`, bubble(kind), nine(24, 24, 8));
    sprite(SPR_PVP, `bubble_tail_${kind}`, bubbleTail(kind));
  }
  sheetOut(GUI_PVP, 'taunt_icons', tauntIcons16());
  sheetOut(GUI_PVP, 'taunt_icons_20', tauntIcons20());
  sheetOut(GUI_PVP, 'mode_icons', modeIcons16());
  sheetOut(GUI_PVP, 'mode_icons_40', modeIcons40());
  sheetOut(GUI_PVP, 'badges', badges16());
  sheetOut(GUI_PVP, 'badges_20', badges20());
  sheetOut(GUI_PVP, 'rank_medals', hstrip(rankMedals()));
  sheetOut(GUI_PVP, 'podium', podium());
  sprite(SPR_PVP, 'crown', crown());
  sprite(SPR_PVP, 'padlock', padlock());
  sprite(SPR_PVP, 'claw', claw());
  for (const kind of ['back', 'face', 'gold']) sprite(SPR_PVP, `plaque_${kind}`, revealPlaque(kind), nine(64, 20, 4));
  sprite(SPR_PVP, 'winner_banner', winnerBanner(), nine(80, 32, 12));
  sprite(SPR_PVP, 'mode_banner', modeBanner(), nine(64, 24, 8));
  sprite(SPR_PVP, 'all_in', allInTag(), nine(24, 12, 4));
  sprite(SPR_PVP, 'seat_empty', seatEmpty(), nine(16, 16, 3));
  sprite(SPR_PVP, 'flame_red', streakFlame('red'), { frametime: 2 });
  sprite(SPR_PVP, 'flame_gold', streakFlame('gold'), { frametime: 2 });
  flameSnuff().forEach((f, i) => sprite(SPR_PVP, `flame_snuff_${i}`, f));
  brokenChain().forEach((f, i) => sprite(SPR_PVP, `broken_chain_${i}`, f));
  sprite(SPR_PVP, 'mode_card', modeCard(false), nine(48, 48, 12));
  sprite(SPR_PVP, 'mode_card_selected', modeCard(true), nine(48, 48, 12));
  sprite(SPR_PVP, 'lobby_row', lobbyRow('normal'), nine(32, 20, 6));
  sprite(SPR_PVP, 'lobby_row_host', lobbyRow('host'), nine(32, 20, 6));

  // ---- world: BER parts, particle frames ----
  out.push(png('java', `${T}/entity/extras/plinko_lamp.png`, plinkoLamp()));
  out.push(png('java', `${T}/entity/extras/plinko_ball.png`, worldBall()));
  out.push(png('java', `${T}/entity/extras/wheel_bulbs.png`, worldBulbs()));
  const fl = flakes();
  for (let i = 0; i < 4; i++) out.push(png('java', `${T}/particle/extras/foil_flake_${i}.png`, crop(fl, i * 4, 0, 4, 4)));
  return out;
}

/** 12 × 2: lamp off / on / gold (4 px each) for the Plinko BER lamp strip. */
function plinkoLamp() {
  const img = image(12, 2);
  rect(img, 0, 0, 4, 2, '#2A1A3A');
  rect(img, 4, 0, 4, 2, BIN_TIERS[2].light);
  rect(img, 8, 0, 4, 2, K.gold);
  put(img, 5, 0, c(K.white));
  put(img, 9, 0, c(K.white));
  return img;
}
/** 4 × 2: the BER ball (red, gold). */
function worldBall() {
  const img = image(4, 2);
  rect(img, 0, 0, 2, 2, '#D83440');
  put(img, 0, 0, c('#FF9A90'));
  rect(img, 2, 0, 2, 2, K.gold);
  put(img, 2, 0, c(K.goldLight));
  return img;
}
/** 8 × 4: BER bulb off / on (4 × 4 each). */
function worldBulbs() {
  const img = image(8, 4);
  rect(img, 0, 0, 4, 4, '#3A1A10');
  rect(img, 1, 1, 2, 2, '#5A3A1A');
  rect(img, 4, 0, 4, 4, '#FFB040');
  rect(img, 5, 1, 2, 2, K.goldLight);
  put(img, 5, 1, c(K.white));
  return img;
}
