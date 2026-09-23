#!/usr/bin/env node
/* global console */
// Generate the core pack's 16×16 textures (chips, Casino Card, cashier, shared table
// felt/wood) as PNG files under packs/core/RP/textures/. Deterministic; no text baked in.
//   node tools/gen-textures.mjs
import { Buffer } from 'node:buffer';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const RP = path.join(ROOT, 'packs/core/RP/textures');

// ---- tiny PNG encoder (RGBA8) ---------------------------------------------------------
const CRC = new Int32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});
function crc32(buf) {
  let c = -1;
  for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
export function encodePng(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

// ---- drawing helpers ------------------------------------------------------------------
const hex = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), 255];
const shade = (c, f) => [...c.slice(0, 3).map((v) => Math.max(0, Math.min(255, Math.round(v * f)))), c[3]];
function canvas(fill = [0, 0, 0, 0]) {
  const px = Buffer.alloc(16 * 16 * 4);
  const set = (x, y, c) => {
    if (x < 0 || y < 0 || x > 15 || y > 15) return;
    px.set(c, (y * 16 + x) * 4);
  };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(x, y, fill);
  return { px, set };
}
function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 2 ** 32);
}

function chip(base, stripe) {
  const { px, set } = canvas();
  const c = hex(base);
  const st = hex(stripe);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const dx = x - 7.5;
      const dy = y - 7.5;
      const r = Math.sqrt(dx * dx + dy * dy);
      if (r > 7.2) continue;
      const ang = (Math.atan2(dy, dx) + Math.PI) / (2 * Math.PI);
      let col;
      if (r > 5.6) col = Math.floor(ang * 12) % 2 === 0 ? st : shade(c, 0.85); // edge spots
      else if (r > 4.4) col = shade(c, 0.7); // inner ring
      else if (r > 3.6) col = st;
      else col = shade(c, 1.08);
      if (r > 6.6) col = shade(col, 0.75); // rim shadow
      set(x, y, col);
    }
  return px;
}

function card() {
  const { px, set } = canvas();
  const gold = hex('#e0b33a');
  const felt = hex('#1f6b3a');
  for (let y = 2; y < 14; y++)
    for (let x = 1; x < 15; x++) {
      const edge = y === 2 || y === 13 || x === 1 || x === 14;
      set(x, y, edge ? shade(gold, 0.8) : y === 5 || y === 6 ? hex('#101010') : felt);
    }
  for (let x = 3; x < 6; x++) for (let y = 8; y < 11; y++) set(x, y, gold); // chip emblem
  set(4, 9, shade(gold, 0.6));
  return px;
}

function noiseTile(base, seed, amp = 0.12) {
  const r = rng(seed);
  const { px, set } = canvas();
  const c = hex(base);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(x, y, shade(c, 1 - amp + r() * 2 * amp));
  return { px, set };
}

function planks(base, seed) {
  const t = noiseTile(base, seed, 0.06);
  for (let x = 0; x < 16; x++) {
    t.set(x, 3, shade(hex(base), 0.7));
    t.set(x, 7, shade(hex(base), 0.7));
    t.set(x, 11, shade(hex(base), 0.7));
    t.set(x, 15, shade(hex(base), 0.7));
  }
  return t;
}

function cashierFront(wood, metal, seed) {
  const t = planks(wood, seed);
  const m = hex(metal);
  for (let y = 1; y < 9; y++)
    for (let x = 2; x < 14; x++) {
      const bar = x % 3 === 2 || y === 1 || y === 8;
      t.set(x, y, bar ? m : hex('#1b1b1b'));
    }
  for (let x = 0; x < 16; x++) t.set(x, 10, shade(m, 0.8)); // counter ledge
  return t.px;
}

function cashierTop(wood, metal, seed) {
  const t = planks(wood, seed);
  const m = hex(metal);
  for (let i = 0; i < 16; i++) {
    t.set(i, 0, m);
    t.set(i, 15, m);
    t.set(0, i, m);
    t.set(15, i, m);
  }
  return t.px;
}

function felt() {
  const t = noiseTile('#1e7a3f', 11, 0.07);
  const gold = hex('#c9a13a');
  for (let i = 0; i < 16; i++) {
    t.set(i, 0, gold);
    t.set(0, i, gold);
    t.set(i, 15, gold);
    t.set(15, i, gold);
  }
  return t.px;
}

const out = [];
function write(rel, px) {
  const file = path.join(RP, rel + '.png');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, encodePng(16, 16, px));
  out.push(rel);
}

// Chips (GAME_DESIGN §3.1 colors)
write('items/core/chip_1', chip('#ececec', '#3a6fd8'));
write('items/core/chip_5', chip('#c62828', '#f5f5f5'));
write('items/core/chip_25', chip('#2e7d32', '#f5f5f5'));
write('items/core/chip_100', chip('#262626', '#f5f5f5'));
write('items/core/chip_500', chip('#6a1b9a', '#f3d34a'));
write('items/core/casino_card', card());
// Cashier (wood + brass) and Nether cashier (blackstone + gold)
write('blocks/core/cashier_front', cashierFront('#8a5a2b', '#c9a13a', 3));
write('blocks/core/cashier_side', planks('#8a5a2b', 4).px);
write('blocks/core/cashier_top', cashierTop('#9c6a36', '#c9a13a', 5));
write('blocks/core/nether_cashier_front', cashierFront('#2b2528', '#f0c419', 6));
write('blocks/core/nether_cashier_side', noiseTile('#2b2528', 7, 0.1).px);
write('blocks/core/nether_cashier_top', cashierTop('#352d31', '#f0c419', 8));
// Shared table textures for game modules (terrain names burmaldaholic_table_felt / _side)
write('blocks/core/table_felt', felt());
write('blocks/core/table_side', planks('#5b3a1e', 9).px);
console.info(`wrote ${out.length} textures under packs/core/RP/textures/`);
