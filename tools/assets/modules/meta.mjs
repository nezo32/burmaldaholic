// World & meta FX art (lane J-L3: chaos, Golden Hour, Last Chance, VIP, Debt Collectors, attract mode, bot plates).
// Java only; every output lives in its owning module's namespace (Java checkAssetOwnership):
//   gui/lastchance/coin_spin.png (32 × 384, 12 frames), coin_crack_{0,1}.png     Last Chance coin (code-indexed)
//   gui/sprites/lastchance/vignette                                              white vignette (tinted in code)
//   gui/sprites/chaos/{vignette,card_<event>,gh_plaque,gh_sun}                   chaos cards, Golden Hour countdown
//   gui/sprites/vip/{badge_large_<tier>,badge_shine_<n>}                         VIP tier-up
//   gui/sprites/loan/{vignette,collector_card}                                   Debt Collectors' arrival
//   font/bots/badges.png + font/bots.json                                        bot difficulty pills (U+E500–E503)
//   block/core/{cashier,nether_cashier}_front, block/extras/{wheel_of_fortune,plinko_machine}_front (+ .mcmeta)
//                                                                                attract strips (global.md §4.13)
// Ownership: this module is the only writer of the four block fronts above (frame 0 keeps the extras / core art;
// the lamp frames are J-L3's). The core and extras modules must not emit them — meta-art.test.mjs checks it; art
// changes to those fronts go into ./meta/art.mjs (CASHIER / WHEEL / PLINKO rows).
import { json, mcmeta, png, JAVA_ASSETS } from '../lib/emit.mjs';
import { hstrip, strip } from '../lib/grid.mjs';
import {
  CHAOS_EVENTS, VIP_TIERS, cashierFrames, glowDot, runeFrame, chaosCard, collectorCard, ghPlaque, ghSun, lcCrack, lcFrame, pill, plinkoFrames, vignette, vipBadge,
  vipShine, wheelFrames,
} from './meta/art.mjs';

const T = `${JAVA_ASSETS}/textures`;

export default function generate() {
  const out = [];
  const sprite = (path, img, meta) => {
    out.push(png(`${T}/gui/sprites/${path}.png`, img));
    if (meta) out.push(mcmeta(`${T}/gui/sprites/${path}.png`, meta));
  };

  // ---- Last Chance ----
  out.push(png(`${T}/gui/lastchance/coin_spin.png`, strip(Array.from({ length: 12 }, (_, k) => lcFrame(k)))));
  for (const s of [0, 1]) out.push(png(`${T}/gui/lastchance/coin_crack_${s}.png`, lcCrack(s)));
  sprite('lastchance/vignette', vignette());

  // ---- chaos + Golden Hour ----
  sprite('chaos/vignette', vignette());
  for (const e of CHAOS_EVENTS) sprite(`chaos/card_${e}`, chaosCard(e));
  sprite('chaos/gh_plaque', ghPlaque(), { nineSlice: { width: 32, height: 16, border: 6 } });
  sprite('chaos/gh_sun', strip([0, 1, 2, 3].map(ghSun)), { frametime: 4 });

  // ---- VIP ----
  VIP_TIERS.forEach((id, t) => sprite(`vip/badge_large_${id}`, vipBadge(t)));
  for (let f = 0; f < 8; f++) sprite(`vip/badge_shine_${f}`, vipShine(f));

  // ---- Debt Collectors ----
  sprite('loan/vignette', vignette());
  sprite('loan/collector_card', collectorCard());

  // ---- bot plates: difficulty pills font ----
  out.push(png(`${T}/font/bots/badges.png`, hstrip([0, 1, 2, 3].map(pill))));
  out.push(json(`${JAVA_ASSETS}/font/bots.json`, {
    providers: [{ type: 'bitmap', file: 'burmaldaholic:font/bots/badges.png', height: 8, ascent: 7, chars: [''] }],
  }));

  // ---- world particles (types registered by chaos / worldgen; client providers in their client modules) ----
  const particle = (module, name, frames) => {
    frames.forEach((f, i) => out.push(png(`${T}/particle/${module}/${name}_${i}.png`, f)));
    out.push(json(`${JAVA_ASSETS}/particles/${module}/${name}.json`, { textures: frames.map((_, i) => `burmaldaholic:${module}/${name}_${i}`) }));
  };
  particle('chaos', 'rune', [0, 1, 2, 3].map(runeFrame));
  particle('worldgen', 'bulb', [0, 1].map((f) => glowDot(f)));
  particle('worldgen', 'mote', [0, 1].map((f) => glowDot(f, true)));

  // ---- attract strips (block textures; frame 0 = the original art) ----
  const blockStrip = (path, frames, animation) => {
    out.push(png(`${T}/block/${path}.png`, strip(frames)));
    out.push(json(`${T}/block/${path}.png.mcmeta`, { animation }));
  };
  const flicker = { frames: [{ index: 0, time: 30 }, { index: 1, time: 4 }, { index: 0, time: 12 }, { index: 1, time: 3 }] };
  blockStrip('core/cashier_front', cashierFrames(false), flicker);
  blockStrip('core/nether_cashier_front', cashierFrames(true), flicker);
  blockStrip('extras/wheel_of_fortune_front', wheelFrames(), { frametime: 8 });
  blockStrip('extras/plinko_machine_front', plinkoFrames(), { frametime: 6 });
  return out;
}
