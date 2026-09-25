import { describe, expect, it } from 'vitest';
import generate from '../meta.mjs';
import { alphaAt } from '../slots/raster.mjs';
import {
  CHAOS_EVENTS, EVENT_TONE, LC_ANGLES, VIP_TIERS, cashierFrames, chaosCard, lcFrame, pill, plinkoFrames, vipBadge, wheelFrames,
} from './art.mjs';

const pngSize = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
const opaque = (img) => {
  let n = 0;
  for (let i = 3; i < img.data.length; i += 4) if (img.data[i]) n++;
  return n;
};
const same = (a, b) => a.w === b.w && a.h === b.h && Buffer.from(a.data).equals(Buffer.from(b.data));

describe('meta art (lane J-L3)', () => {
  const out = generate();
  const byPath = new Map(out.map((o) => [o.path, o.bytes]));
  const T = 'src/main/resources/assets/burmaldaholic/textures';

  it('writes only inside owned namespaces', () => {
    for (const o of out) {
      const ok = /\/(chaos|lastchance|vip|loan|bots|worldgen|core|extras)\//.test(o.path) || o.path.endsWith('/font/bots.json');
      expect(ok, o.path).toBe(true);
    }
  });

  it('coin sheet: 12 frames of 32², heads and tails differ by shape, edge frame is thin', () => {
    expect(pngSize(byPath.get(`${T}/gui/lastchance/coin_spin.png`))).toEqual([32, 384]);
    expect(LC_ANGLES).toHaveLength(12);
    expect(LC_ANGLES[6]).toBe(90);
    expect(same(lcFrame(0), lcFrame(11))).toBe(false);
    expect(opaque(lcFrame(6))).toBeLessThan(opaque(lcFrame(0)) / 3);
  });

  it('VIP badges: six tiers, 48², tier pips make them different shapes', () => {
    expect(VIP_TIERS).toHaveLength(6);
    for (let t = 0; t < 6; t++) {
      expect(pngSize(byPath.get(`${T}/gui/sprites/vip/badge_large_${VIP_TIERS[t]}.png`))).toEqual([48, 48]);
      if (t > 0) expect(same(vipBadge(t), vipBadge(t - 1))).toBe(false);
    }
    for (let f = 0; f < 8; f++) expect(byPath.has(`${T}/gui/sprites/vip/badge_shine_${f}.png`)).toBe(true);
  });

  it('a chaos card for every event, framed in its tone', () => {
    expect(CHAOS_EVENTS).toHaveLength(9);
    for (const e of CHAOS_EVENTS) {
      expect(EVENT_TONE[e], e).toBeDefined();
      const card = chaosCard(e);
      expect([card.w, card.h]).toEqual([64, 64]);
      expect(alphaAt(card, 32, 32)).toBe(255);
    }
  });

  it('difficulty pills differ by pips (not colour alone)', () => {
    const count = (img) => {
      let n = 0;
      for (let x = 0; x < 16; x++) if (img.data[(6 * 16 + x) * 4] === 255 && img.data[(6 * 16 + x) * 4 + 1] === 255) n++;
      return n;
    };
    expect(count(pill(0))).toBeLessThan(count(pill(1)));
    expect(count(pill(1))).toBeLessThan(count(pill(2)));
    expect(pngSize(byPath.get(`${T}/font/bots/badges.png`))).toEqual([64, 16]);
  });

  it('attract strips keep the original art in every frame except the lamps', () => {
    for (const frames of [cashierFrames(false), cashierFrames(true), wheelFrames(), plinkoFrames()]) {
      expect(frames.length).toBeGreaterThanOrEqual(2);
      let diff = 0;
      for (let i = 0; i < frames[0].data.length; i += 4) if (frames[0].data[i] !== frames[1].data[i]) diff++;
      expect(diff).toBeGreaterThan(0);
      expect(diff).toBeLessThan(40); // lamps only: the reel window / wheel face / board never change
    }
    expect(pngSize(byPath.get(`${T}/block/core/cashier_front.png`))).toEqual([16, 32]);
    expect(pngSize(byPath.get(`${T}/block/extras/plinko_machine_front.png`))).toEqual([16, 48]);
  });
});
