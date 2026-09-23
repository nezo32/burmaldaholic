// Generates the worldgen module's binary assets into packs/worldgen/:
//  - casino `.mcstructure` templates (little-endian NBT) -> BP/structures/burmaldaholic/
//    Layouts live in src/worldgen/logic/layouts.ts (pure TS); this script bundles
//    src/worldgen/logic/generate.ts with esbuild on the fly and writes its output.
//  - 64×64 NPC skins (croupier, piglin dealer, shulker croupier) -> RP/textures/entity/worldgen/
//    painted on the box-UV layout of RP/models/entity/worldgen/*.geo.json. No text baked in.
//
//   cd bedrock && node src/worldgen/tools/gen-assets.mjs          # write files
//   cd bedrock && node src/worldgen/tools/gen-assets.mjs --check  # exit 1 if files are stale
import { Buffer } from 'node:buffer';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';
import * as esbuild from 'esbuild';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..'); // bedrock/
const out = path.join(root, 'packs/worldgen');
const check = process.argv.includes('--check');

// ---- structures (from the pure TS layouts) ----------------------------------------------------
async function structureFiles() {
  const result = await esbuild.build({
    entryPoints: [path.join(root, 'src/worldgen/logic/generate.ts')],
    bundle: true,
    write: false,
    format: 'esm',
    platform: 'neutral',
    target: 'es2023',
    logLevel: 'warning',
  });
  const code = result.outputFiles[0].text;
  const mod = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
  return mod.generatedFiles().map((f) => ({ path: f.path, bytes: Buffer.from(f.bytes) }));
}

// ---- tiny PNG encoder (RGBA8) -----------------------------------------------------------------
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
function pngChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encodePng(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---- painting ---------------------------------------------------------------------------------
const hex = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), 255];
const shade = (c, f) => [...c.slice(0, 3).map((v) => Math.max(0, Math.min(255, Math.round(v * f)))), 255];

function canvas(size = 64) {
  const px = Buffer.alloc(size * size * 4);
  let seed = 7;
  const noise = () => {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    return 0.93 + ((seed >> 8) % 100) / 100 / 7;
  };
  const set = (x, y, c) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    px.set(c, (y * size + x) * 4);
  };
  const rect = (x, y, w, h, c, textured = true) => {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) set(x + i, y + j, textured ? shade(c, noise()) : c);
  };
  /** Paint a box-UV cube: faces default to `c`; `faces` overrides (top/bottom/right/front/left/back). */
  const cube = (u, v, w, h, d, c, faces = {}) => {
    const f = (name) => faces[name] ?? c;
    rect(u + d, v, w, d, f('top'));
    rect(u + d + w, v, w, d, f('bottom'));
    rect(u, v + d, d, h, f('right'));
    rect(u + d, v + d, w, h, f('front'));
    rect(u + d + w, v + d, d, h, f('left'));
    rect(u + 2 * d + w, v + d, w, h, f('back'));
    return { front: (x, y) => [u + d + x, v + d + y], back: (x, y) => [u + 2 * d + w + x, v + d + y] };
  };
  return { px, set, rect, cube, png: () => encodePng(size, size, px) };
}

function croupier() {
  const skin = hex('#c98f6b');
  const suit = hex('#1d1d24');
  const shirt = hex('#eeeeee');
  const tie = hex('#b3122e');
  const trousers = hex('#2a2a33');
  const c = canvas();
  const head = c.cube(0, 0, 8, 10, 8, skin, { top: hex('#3b2a1e') });
  // hair band, eyes, brows, mouth
  c.rect(...head.front(0, 0), 8, 2, hex('#3b2a1e'));
  for (const x of [1, 5]) {
    c.rect(...head.front(x, 4), 2, 1, shirt, false);
    c.set(...head.front(x + (x === 1 ? 1 : 0), 4), hex('#1b5e20'));
    c.rect(...head.front(x, 3), 2, 1, hex('#3b2a1e'), false);
  }
  c.rect(...head.front(2, 8), 4, 1, hex('#7a4b35'), false);
  c.cube(24, 0, 2, 4, 2, shade(skin, 0.9));
  const body = c.cube(16, 20, 8, 12, 6, suit);
  c.rect(...body.front(3, 0), 2, 12, shirt);
  c.rect(...body.front(2, 0), 4, 2, tie, false);
  c.set(...body.front(4, 5), hex('#d4af37'));
  c.set(...body.front(4, 8), hex('#d4af37'));
  const arm = c.cube(44, 22, 4, 12, 4, suit);
  c.rect(...arm.front(0, 10), 4, 2, skin);
  c.rect(44, 36, 16, 2, skin);
  c.cube(0, 22, 4, 12, 4, trousers, { bottom: hex('#101010') });
  c.rect(0, 36, 16, 2, hex('#101010'), false);
  return c.png();
}

function piglinDealer() {
  const skin = hex('#e8a3a0');
  const snout = hex('#f2bcb5');
  const vest = hex('#8e1b2b');
  const gold = hex('#f2c230');
  const c = canvas();
  const head = c.cube(0, 0, 10, 8, 8, skin);
  for (const x of [2, 6]) {
    c.rect(...head.front(x, 3), 2, 1, hex('#ffffff'), false);
    c.set(...head.front(x + (x === 2 ? 1 : 0), 3), hex('#1a1a1a'));
  }
  c.rect(...head.front(1, 7), 2, 1, hex('#fff3d6'), false); // tusks
  c.rect(...head.front(7, 7), 2, 1, hex('#fff3d6'), false);
  const nose = c.cube(28, 0, 4, 3, 1, snout);
  c.set(...nose.front(1, 1), hex('#8d4b4b'));
  c.set(...nose.front(2, 1), hex('#8d4b4b'));
  c.cube(40, 0, 1, 5, 4, shade(skin, 0.92));
  c.cube(50, 0, 1, 5, 4, shade(skin, 0.92));
  const body = c.cube(16, 16, 8, 12, 4, vest, { right: skin, left: skin });
  c.rect(...body.front(3, 0), 2, 12, hex('#f5f0e1')); // shirt under the open vest
  for (const y of [2, 5, 8]) c.set(...body.front(2, y), gold);
  c.rect(...body.front(0, 11), 8, 1, gold, false); // belt
  c.cube(40, 16, 4, 12, 4, skin, { top: vest });
  c.rect(44, 20, 4, 3, vest);
  c.cube(0, 16, 4, 12, 4, hex('#5a3a22'), { bottom: hex('#2b1d12') });
  return c.png();
}

function shulkerCroupier() {
  const shell = hex('#8e5a9e');
  const head = hex('#e6e0a6');
  const c = canvas();
  c.cube(0, 0, 16, 12, 16, shell, { top: shade(shell, 1.1) });
  c.rect(16, 16, 16, 12, shade(shell, 0.95));
  const lid = { front: (x, y) => [16 + x, 16 + y] };
  c.rect(...lid.front(6, 10), 4, 2, hex('#b3122e'), false); // bow tie on the lid rim
  c.cube(0, 28, 16, 8, 16, shade(shell, 0.85));
  const h = c.cube(0, 52, 6, 6, 6, head);
  c.set(...h.front(1, 2), hex('#1a1a1a'));
  c.set(...h.front(4, 2), hex('#1a1a1a'));
  c.rect(...h.front(2, 4), 2, 1, hex('#8c7a3a'), false);
  return c.png();
}

function textureFiles() {
  return [
    { path: 'RP/textures/entity/worldgen/croupier.png', bytes: croupier() },
    { path: 'RP/textures/entity/worldgen/piglin_dealer.png', bytes: piglinDealer() },
    { path: 'RP/textures/entity/worldgen/shulker_croupier.png', bytes: shulkerCroupier() },
  ];
}

// ---- write / check ----------------------------------------------------------------------------
let stale = 0;
for (const f of [...(await structureFiles()), ...textureFiles()]) {
  const file = path.join(out, f.path);
  const same = fs.existsSync(file) && fs.readFileSync(file).equals(f.bytes);
  if (same) continue;
  stale++;
  if (check) {
    process.stderr.write(`stale: ${path.relative(root, file)}\n`);
    continue;
  }
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, f.bytes);
  process.stdout.write(`wrote ${path.relative(root, file)} (${f.bytes.length} bytes)\n`);
}
if (check && stale) process.exit(1);
process.stdout.write(check ? 'worldgen assets up to date\n' : `done (${stale} changed)\n`);
