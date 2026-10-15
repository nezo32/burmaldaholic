#!/usr/bin/env python3
"""Generates PiglinManorShell.java (the Piglin Parlor's building shell) from the Bygone Nether "Piglin Manor" template.

Source: java/src/main/blueprints/parlor/piglin_manor_2.nbt — Bygone Nether by izofar, MIT License (see
java/src/main/resources/META-INF/licenses/bygone-nether-MIT.txt and CREDITS.md). Run from the repository root:

    python java/tools/gen_parlor_shell.py

What the conversion does (deterministic; the output is committed):
  * rotates the template a quarter turn so the manor's entrance stair (its -x side) faces +z, the casino front;
  * widens it by duplicating a plain wall bay (the slices next to the entrance gable) twice on each side, so a row of
    three full 3 x 2 casino tables fits between the corner towers (23 -> 27 wide);
  * maps blocks: cave_air -> air, the two modded blocks (bygonenether:cobbled_blackstone -> blackstone, the netherite
    bell -> air), old 'chain' -> 'iron_chain', jigsaws -> their final state, chests -> air (loot comes from our own
    marker), lava -> blackstone (the casino places its own sealed lava);
  * rotates block-state directions (facing, axis, wall / fence arms).
Nothing valuable is in the source (no gold / iron / netherite blocks).
"""
import gzip
import os
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'java', 'src', 'main', 'blueprints', 'parlor', 'piglin_manor_2.nbt')
OUT = os.path.join(ROOT, 'java', 'src', 'main', 'java', 'dev', 'nezo', 'burmaldaholic', 'worldgen', 'logic', 'pieces',
                   'PiglinManorShell.java')

# one char per palette entry (printable ASCII without quote and backslash)
ALPHABET = ''.join(chr(c) for c in range(35, 127) if chr(c) not in '\\"')

# duplicated slices (rotated x) and how often
STRETCH = {7: 2, 15: 2}


def read_nbt(path):
    data = gzip.decompress(open(path, 'rb').read())
    p = [0]

    def u(fmt, n):
        v = struct.unpack_from('>' + fmt, data, p[0])
        p[0] += n
        return v[0]

    def s():
        n = u('H', 2)
        v = data[p[0]:p[0] + n].decode('utf8')
        p[0] += n
        return v

    def pl(t):
        if t == 1: return u('b', 1)
        if t == 2: return u('h', 2)
        if t == 3: return u('i', 4)
        if t == 4: return u('q', 8)
        if t == 5: return u('f', 4)
        if t == 6: return u('d', 8)
        if t == 7:
            n = u('i', 4); v = data[p[0]:p[0] + n]; p[0] += n; return v
        if t == 8: return s()
        if t == 9:
            et = u('b', 1); n = u('i', 4); return [pl(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = u('b', 1)
                if tt == 0: return d
                k = s(); d[k] = pl(tt)
        if t == 11:
            n = u('i', 4); return [u('i', 4) for _ in range(n)]
        if t == 12:
            n = u('i', 4); return [u('q', 8) for _ in range(n)]
        raise ValueError(t)

    t = u('b', 1)
    s()
    return pl(t)


# rotation (ox, oz) -> (oz, MAXX - ox): -x -> +z, +z -> +x, -z -> -x, +x -> -z
DIR = {'north': 'west', 'east': 'north', 'south': 'east', 'west': 'south'}


def rotate_state(name, props):
    out = {}
    for k, v in props.items():
        if k in DIR or k == 'up' and name.endswith('_wall') or k == 'shape' and name.endswith('_stairs'):
            continue  # connection arms and stair corner shapes: the blueprint's finishing pass recomputes them
        elif k == 'facing' and v in DIR:
            out[k] = DIR[v]
        elif k == 'axis' and v in ('x', 'z'):
            out[k] = 'z' if v == 'x' else 'x'
        else:
            out[k] = v
    return name, out


def map_block(entry, block):
    name = entry['Name']
    props = dict(entry.get('Properties', {}))
    if name == 'minecraft:jigsaw':
        fs = block.get('nbt', {}).get('final_state', 'minecraft:air')
        name, props = fs.split('[')[0], {}
    if name in ('minecraft:cave_air', 'minecraft:chest', 'bygonenether:netherite_bell'):
        return 'minecraft:air', {}
    if name == 'bygonenether:cobbled_blackstone':
        return 'minecraft:blackstone', {}
    if name == 'minecraft:lava':
        return 'minecraft:blackstone', {}
    if name == 'minecraft:chain':
        name = 'minecraft:iron_chain'
    if name.split(':')[0] != 'minecraft':
        raise SystemExit('unmapped modded block ' + name)
    return rotate_state(name, props)


def main():
    r = read_nbt(SRC)
    sx, sy, sz = r['size']
    pal = r['palette']
    grid = {}
    for b in r['blocks']:
        ox, y, oz = b['pos']
        name, props = map_block(pal[b['state']], b)
        nx, nz = oz, sx - 1 - ox
        grid[(nx, y, nz)] = (name, props)
    rw, rd = sz, sx  # rotated width (x) and depth (z)
    xs = []
    for x in range(rw):
        xs.append(x)
        xs.extend([x] * STRETCH.get(x, 0))
    width = len(xs)
    palette = []
    index = {}
    rows = []
    for y in range(sy):
        for z in range(rd):
            line = []
            for nx, x in enumerate(xs):
                c = grid.get((x, y, z))
                if c is None:
                    line.append(' ')
                    continue
                key = c[0] + ('[' + ','.join(f'{k}={v}' for k, v in sorted(c[1].items())) + ']' if c[1] else '')
                if key not in index:
                    index[key] = len(palette)
                    palette.append(key)
                line.append(ALPHABET[index[key]])
            rows.append(''.join(line))
    if len(palette) > len(ALPHABET):
        raise SystemExit('palette too large for the char encoding: %d' % len(palette))
    java = []
    java.append('package dev.nezo.burmaldaholic.worldgen.logic.pieces;')
    java.append('')
    java.append('import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;')
    java.append('import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;')
    java.append('import java.util.function.UnaryOperator;')
    java.append('')
    java.append('/**')
    java.append(' * GENERATED by java/tools/gen_parlor_shell.py — do not edit. The building shell of the Piglin Parlor:')
    java.append(' * "Piglin Manor" (piglin_manor_2) from Bygone Nether by izofar, https://github.com/izofar/bygone-nether,')
    java.append(' * Copyright (c) 2022 izofar, MIT License (META-INF/licenses/bygone-nether-MIT.txt), rotated so the entrance faces')
    java.append(' * +z, widened by two duplicated wall bays per side, modded blocks mapped to vanilla.')
    java.append(' */')
    java.append('public final class PiglinManorShell {')
    java.append('\tprivate PiglinManorShell() {}')
    java.append('')
    java.append('\tpublic static final int SX = %d;' % width)
    java.append('\tpublic static final int SY = %d;' % sy)
    java.append('\tpublic static final int SZ = %d;' % rd)
    java.append('')
    java.append('\tprivate static final String[] PALETTE = {')
    for k in palette:
        java.append('\t\t"%s",' % k)
    java.append('\t};')
    java.append('')
    java.append('\t/** Rows y-major then z, one char per x: its index in ALPHABET is the palette index, space = structure void. */')
    java.append('\tprivate static final String ALPHABET = "' + ALPHABET + '";')
    java.append('\tprivate static final String[] ROWS = {')
    for row in rows:
        java.append('\t\t"%s",' % row)
    java.append('\t};')
    java.append('')
    java.append('\tstatic BlockSpec parse(String s) {')
    java.append('\t\tint i = s.indexOf(\'[\');')
    java.append('\t\tif (i < 0) {')
    java.append('\t\t\treturn BlockSpec.of(s);')
    java.append('\t\t}')
    java.append('\t\tString[] kv = s.substring(i + 1, s.length() - 1).split("[,=]");')
    java.append('\t\treturn BlockSpec.of(s.substring(0, i), kv);')
    java.append('\t}')
    java.append('')
    java.append('\t/** The shell cell at (x, y, z), null = structure void. */')
    java.append('\tpublic static BlockSpec at(int x, int y, int z) {')
    java.append('\t\tchar c = ROWS[y * SZ + z].charAt(x);')
    java.append("\t\treturn c == ' ' ? null : parse(PALETTE[ALPHABET.indexOf(c)]);")
    java.append('\t}')
    java.append('')
    java.append('\t/** Stamps the shell with its origin at (ox, oy, oz), every block through {@code map} (null result = skip). */')
    java.append('\tpublic static void stamp(Blueprint b, int ox, int oy, int oz, UnaryOperator<BlockSpec> map) {')
    java.append('\t\tfor (int y = 0; y < SY; y++) {')
    java.append('\t\t\tfor (int z = 0; z < SZ; z++) {')
    java.append('\t\t\t\tfor (int x = 0; x < SX; x++) {')
    java.append('\t\t\t\t\tBlockSpec s = at(x, y, z);')
    java.append('\t\t\t\t\tBlockSpec m = s == null ? null : map.apply(s);')
    java.append('\t\t\t\t\tif (m != null && b.inside(ox + x, oy + y, oz + z)) {')
    java.append('\t\t\t\t\t\tb.set(ox + x, oy + y, oz + z, m);')
    java.append('\t\t\t\t\t}')
    java.append('\t\t\t\t}')
    java.append('\t\t\t}')
    java.append('\t\t}')
    java.append('\t}')
    java.append('}')
    with open(OUT, 'w', encoding='utf8', newline='\n') as f:
        f.write('\n'.join(java) + '\n')
    print('wrote', OUT, width, sy, rd, 'palette', len(palette))


if __name__ == '__main__':
    main()
