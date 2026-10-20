import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import generate, { OUTPUTS } from '../npc.mjs';
import { allFaces, ENDERMAN, PIGLIN, PLAYER, VILLAGER } from './uv.mjs';

const pngSize = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
const alphaAt = (img, x, y) => img.data[(y * img.w + x) * 4 + 3];

describe('npc art (DL-SPEC §3)', () => {
  const outputs = OUTPUTS();

  it('makes every pool skin with the expected size', () => {
    const count = (re) => outputs.filter(([p]) => re.test(p)).length;
    expect(count(/dealer\/human_\d\.png$/)).toBe(6);
    expect(count(/dealer\/piglin_\d\.png$/)).toBe(4);
    expect(count(/dealer\/pit_boss\.png$/)).toBe(1);
    expect(count(/dealer\/ender_\d\.png$/)).toBe(3);
    expect(count(/croupier\/villager_\d\.png$/)).toBe(4);
    for (const [path, img, model] of outputs) {
      expect([img.w, img.h], path).toEqual([model.w, model.h]);
      expect(path).toMatch(/textures\/entity\/worldgen\//); // worldgen-owned (checkAssetOwnership)
    }
    for (const o of generate()) {
      const [w, h] = pngSize(o.bytes);
      const egg = o.path.endsWith('spawn_egg.png');
      expect(w, o.path).toBe(egg ? 16 : 64);
      expect(h, o.path).toBe(o.path.includes('ender_') ? 32 : egg ? 16 : 64);
    }
  });

  it('fills every base UV face and leaves everything outside the UV boxes transparent', () => {
    for (const [path, img, model] of outputs) {
      const inside = new Uint8Array(model.w * model.h);
      for (const f of allFaces(model)) {
        for (let y = f.y; y < f.y + f.h; y++) {
          for (let x = f.x; x < f.x + f.w; x++) {
            inside[y * model.w + x] = 1;
            if (f.layer === 'base') expect(alphaAt(img, x, y), `${path} ${f.box}.${f.side} (${x},${y})`).toBe(255);
          }
        }
      }
      for (let y = 0; y < model.h; y++) {
        for (let x = 0; x < model.w; x++) {
          if (!inside[y * model.w + x]) expect(alphaAt(img, x, y), `${path} outside (${x},${y})`).toBe(0);
        }
      }
    }
  });

  it('keeps the UV layouts inside their textures, base boxes never overlapping', () => {
    for (const model of [PLAYER, PIGLIN, VILLAGER, ENDERMAN]) {
      const owner = new Map();
      for (const f of allFaces(model)) {
        expect(f.x + f.w <= model.w && f.y + f.h <= model.h, `${f.box}.${f.side}`).toBe(true);
        if (f.layer !== 'base') continue;
        for (let y = f.y; y < f.y + f.h; y++) {
          for (let x = f.x; x < f.x + f.w; x++) {
            const k = y * model.w + x;
            const prev = owner.get(k);
            // enderman arms and legs share one limb box (same box name), nothing else may overlap
            expect(prev === undefined || prev === f.box, `${f.box} overlaps ${prev} at (${x},${y})`).toBe(true);
            owner.set(k, f.box);
          }
        }
      }
    }
  });

  it('gives every skin a distinct image and is deterministic', () => {
    const hashes = generate().map((o) => createHash('sha256').update(o.bytes).digest('hex'));
    expect(new Set(hashes).size).toBe(hashes.length);
    const again = generate().map((o) => createHash('sha256').update(o.bytes).digest('hex'));
    expect(again).toEqual(hashes);
  });
});
