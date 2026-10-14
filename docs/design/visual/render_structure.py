#!/usr/bin/env python3
"""Isometric preview of structure templates — review a casino building without starting the game.

    python3 docs/design/visual/render_structure.py                         # current templates + concept
    python3 docs/design/visual/render_structure.py path/to/a.nbt out.png   # any structure .nbt

Reads vanilla structure .nbt (gzip, own tiny NBT reader — no dependencies but Pillow) and draws each
block as an isometric cube with a flat colour per block id (top lighter, sides darker; light sources
glow). It is a *massing / palette* preview, not a texture-accurate render: it answers "is this a box?",
"does the facade read?", "is the palette coherent?". `--cut Y` removes everything above layer Y to show
the floor plan (interior). The concept blockout from docs/design/CASINO_BUILDINGS.md §5 is generated
by `concept_village_plains()` below (prototype of the layout DSL, see §4 of the doc).
"""
import gzip
import io
import struct
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
TPL = ROOT / 'java/src/main/resources/resourcepacks/casinos/data/burmaldaholic/structure/worldgen'
OUT = Path(__file__).resolve().parent / 'mockups' / 'casinos'

# ---- NBT ------------------------------------------------------------------------------------------


def _read(buf, tag):
    if tag == 1:
        return struct.unpack('>b', buf.read(1))[0]
    if tag == 2:
        return struct.unpack('>h', buf.read(2))[0]
    if tag == 3:
        return struct.unpack('>i', buf.read(4))[0]
    if tag == 4:
        return struct.unpack('>q', buf.read(8))[0]
    if tag == 5:
        return struct.unpack('>f', buf.read(4))[0]
    if tag == 6:
        return struct.unpack('>d', buf.read(8))[0]
    if tag == 7:
        n = struct.unpack('>i', buf.read(4))[0]
        return buf.read(n)
    if tag == 8:
        n = struct.unpack('>H', buf.read(2))[0]
        return buf.read(n).decode('utf-8')
    if tag == 9:
        sub = buf.read(1)[0]
        n = struct.unpack('>i', buf.read(4))[0]
        return [_read(buf, sub) for _ in range(n)]
    if tag == 10:
        out = {}
        while True:
            t = buf.read(1)[0]
            if t == 0:
                return out
            n = struct.unpack('>H', buf.read(2))[0]
            name = buf.read(n).decode('utf-8')
            out[name] = _read(buf, t)
    if tag == 11:
        n = struct.unpack('>i', buf.read(4))[0]
        return list(struct.unpack('>%di' % n, buf.read(4 * n)))
    if tag == 12:
        n = struct.unpack('>i', buf.read(4))[0]
        return list(struct.unpack('>%dq' % n, buf.read(8 * n)))
    raise ValueError('tag %d' % tag)


def load_nbt(path):
    raw = Path(path).read_bytes()
    if raw[:2] == b'\x1f\x8b':
        raw = gzip.decompress(raw)
    buf = io.BytesIO(raw)
    t = buf.read(1)[0]
    n = struct.unpack('>H', buf.read(2))[0]
    buf.read(n)
    return _read(buf, t)


def voxels_from_template(path):
    """{(x, y, z): block id without namespace}, size."""
    root = load_nbt(path)
    palette = root['palette'] if 'palette' in root else root['palettes'][0]
    names = [p['Name'].split(':', 1)[1] if p['Name'].startswith('minecraft:') else p['Name'] for p in palette]
    vox = {}
    for b in root['blocks']:
        name = names[b['state']]
        if name in ('air', 'structure_void', 'cave_air'):
            continue
        vox[tuple(b['pos'])] = name
    return vox, tuple(root['size'])


# ---- colours --------------------------------------------------------------------------------------

COLOURS = {
    'oak_planks': (162, 130, 78), 'spruce_planks': (114, 84, 48), 'dark_oak_planks': (66, 43, 20),
    'acacia_planks': (168, 90, 50), 'birch_planks': (192, 175, 121), 'mangrove_planks': (117, 54, 48),
    'cherry_planks': (226, 178, 172), 'stripped_dark_oak_log': (72, 56, 36), 'dark_oak_log': (60, 46, 30),
    'stripped_oak_log': (177, 144, 86), 'spruce_log': (58, 37, 16), 'oak_log': (109, 85, 50),
    'cobblestone': (122, 122, 122), 'mossy_cobblestone': (100, 118, 86), 'stone_bricks': (122, 121, 122),
    'polished_andesite': (132, 135, 134), 'smooth_stone': (158, 158, 158), 'deepslate_tiles': (54, 54, 55),
    'polished_deepslate': (72, 72, 73), 'deepslate_bricks': (70, 70, 71),
    'cut_sandstone': (217, 206, 159), 'smooth_sandstone': (223, 214, 170), 'chiseled_sandstone': (216, 202, 155),
    'sandstone': (216, 203, 155), 'orange_terracotta': (161, 83, 37), 'terracotta': (152, 94, 67),
    'white_terracotta': (209, 178, 161), 'red_terracotta': (143, 61, 46), 'black_terracotta': (37, 22, 16),
    'packed_ice': (141, 180, 250), 'snow_block': (249, 254, 254), 'blue_ice': (116, 167, 253),
    'quartz_block': (235, 229, 222), 'smooth_quartz': (235, 229, 222), 'quartz_pillar': (235, 230, 220),
    'chiseled_quartz_block': (231, 226, 218), 'quartz_bricks': (234, 229, 221),
    'red_carpet': (160, 39, 34), 'green_carpet': (84, 109, 27), 'black_carpet': (20, 21, 25),
    'red_wool': (160, 39, 34), 'green_wool': (84, 109, 27), 'black_wool': (20, 21, 25), 'purple_carpet': (121, 42, 172),
    'glass_pane': (200, 225, 235), 'glass': (200, 225, 235), 'tinted_glass': (44, 38, 46),
    'black_stained_glass': (25, 25, 25), 'red_stained_glass': (153, 51, 51), 'yellow_stained_glass': (229, 229, 51),
    'glowstone': (255, 214, 120), 'sea_lantern': (210, 235, 225), 'redstone_lamp': (255, 160, 70),
    'shroomlight': (240, 146, 70), 'ochre_froglight': (250, 235, 150), 'pearlescent_froglight': (240, 210, 230),
    'verdant_froglight': (215, 240, 190), 'end_rod': (250, 245, 230), 'lantern': (240, 190, 90),
    'gold_block': (246, 208, 61), 'raw_gold_block': (221, 169, 46), 'emerald_block': (42, 203, 87),
    'redstone_block': (175, 24, 5), 'copper_block': (192, 107, 79), 'waxed_copper_block': (192, 107, 79),
    'waxed_cut_copper': (191, 106, 80), 'waxed_oxidized_cut_copper': (80, 153, 126),
    'blackstone': (42, 35, 40), 'polished_blackstone': (53, 48, 56), 'polished_blackstone_bricks': (48, 42, 49),
    'gilded_blackstone': (90, 70, 40), 'crimson_planks': (101, 48, 70), 'crimson_nylium': (130, 31, 31),
    'nether_bricks': (44, 21, 26), 'red_nether_bricks': (69, 7, 9), 'magma_block': (142, 63, 31),
    'purpur_block': (169, 125, 169), 'purpur_pillar': (171, 129, 171), 'end_stone_bricks': (218, 224, 162),
    'obsidian': (15, 10, 24), 'crying_obsidian': (32, 10, 60), 'amethyst_block': (133, 97, 191),
    'bookshelf': (117, 94, 59), 'chest': (164, 116, 46), 'barrel': (138, 102, 58), 'jigsaw': (80, 70, 90),
    'oak_stairs': (162, 130, 78), 'dark_oak_stairs': (66, 43, 20), 'spruce_stairs': (114, 84, 48),
    'dark_oak_slab': (66, 43, 20), 'oak_fence': (162, 130, 78), 'dark_oak_fence': (66, 43, 20),
    'grass_block': (95, 159, 53), 'dirt_path': (148, 122, 65), 'potted_red_tulip': (150, 80, 50),
    'flower_pot': (120, 64, 49), 'red_banner': (160, 39, 34), 'lime_concrete': (94, 168, 24),
    'red_concrete': (142, 32, 32), 'yellow_concrete': (240, 175, 21), 'black_concrete': (8, 10, 15),
    'white_concrete': (207, 213, 214), 'magenta_concrete': (169, 48, 159), 'cyan_concrete': (21, 119, 136),
}
LIGHTS = {'glowstone', 'sea_lantern', 'redstone_lamp', 'shroomlight', 'ochre_froglight', 'pearlescent_froglight',
          'verdant_froglight', 'end_rod', 'lantern', 'jack_o_lantern'}


def colour(name):
    if name in COLOURS:
        return COLOURS[name]
    if name.startswith('burmaldaholic:'):
        n = name.split(':', 1)[1]
        if 'slot' in n:
            return (226, 190, 70) if 'gold' in n else (90, 80, 100) if 'netherite' in n else (196, 112, 76)
        if 'cashier' in n:
            return (40, 120, 70)
        return (30, 110, 55)  # felt tables
    for suffix in ('_stairs', '_slab', '_wall', '_fence', '_fence_gate', '_trapdoor', '_door', '_pane', '_carpet'):
        if name.endswith(suffix):
            base = name[: -len(suffix)]
            for cand in (base, base + '_planks', base + '_block', base.replace('brick', 'bricks')):
                if cand in COLOURS:
                    return COLOURS[cand]
    if 'glass' in name:
        return (190, 215, 230)
    return (200, 0, 200)  # unknown: loud magenta so it gets a colour entry


# ---- renderer -------------------------------------------------------------------------------------


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def render(vox, size, out, scale=8, cut=None, title=None, rotate=0):
    """Isometric (2:1) render. rotate: quarter turns about Y so the facade faces the viewer."""
    sx, sy, sz = size
    items = []
    for (x, y, z), name in vox.items():
        if cut is not None and y > cut:
            continue
        if rotate % 4 == 2:  # turn 180 degrees so the z = 0 facade faces the viewer
            x, z = sx - 1 - x, sz - 1 - z
        items.append(((x, y, z), name))
    w = (sx + sz) * scale + 2 * scale
    h = (sx + sz) * scale // 2 + sy * scale + 4 * scale + (16 if title else 0)
    img = Image.new('RGBA', (w, h), (24, 26, 34, 255))
    d = ImageDraw.Draw(img)
    ox, oy = sz * scale + scale, sy * scale + 2 * scale + (16 if title else 0)
    occupied = {p for p, _ in items}
    # painter's order: far to near (small x+z first), bottom to top
    items.sort(key=lambda it: (it[0][0] + it[0][2], it[0][1]))
    for (x, y, z), name in items:
        c = colour(name)
        glow = name in LIGHTS
        top = shade(c, 1.15 if not glow else 1.25)
        left = shade(c, 0.78 if not glow else 1.05)
        right = shade(c, 0.62 if not glow else 0.95)
        px = ox + (x - z) * scale
        py = oy + (x + z) * scale // 2 - y * scale
        s, hs = scale, scale // 2
        t = [(px, py - hs), (px + s, py), (px, py + hs), (px - s, py)]
        if (x, y + 1, z) not in occupied:
            d.polygon(t, fill=top)
        if (x, y, z + 1) not in occupied:
            d.polygon([(px - s, py), (px, py + hs), (px, py + hs + s), (px - s, py + s)], fill=left)
        if (x + 1, y, z) not in occupied:
            d.polygon([(px, py + hs), (px + s, py), (px + s, py + s), (px, py + hs + s)], fill=right)
    if title:
        d.text((6, 3), title, fill=(235, 235, 235))
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    return out


# ---- concept blockout (prototype of the layout DSL from CASINO_BUILDINGS.md §4) ---------------------


class Blockout:
    """Minimal stand-in for the Java `Grid` + the proposed DSL verbs (box / walls / roof / sign)."""

    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.v = {}

    def set(self, x, y, z, b):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            if b is None:
                self.v.pop((x, y, z), None)
            else:
                self.v[(x, y, z)] = b

    def box(self, x0, y0, z0, x1, y1, z1, b):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, b)

    def walls(self, x0, y0, z0, x1, y1, z1, b):
        for y in range(y0, y1 + 1):
            self.box(x0, y, z0, x1, y, z0, b)
            self.box(x0, y, z1, x1, y, z1, b)
            self.box(x0, y, z0, x0, y, z1, b)
            self.box(x1, y, z0, x1, y, z1, b)

    def hip_roof(self, x0, y, z0, x1, z1, b, eave=None, layers=99):
        """Stepped hip roof: each layer insets by one until it closes (or `layers`, then a flat deck)."""
        k = 0
        while x0 + k <= x1 - k and z0 + k <= z1 - k and k < layers:
            self.walls(x0 + k, y + k, z0 + k, x1 - k, y + k, z1 - k, b if k else (eave or b))
            k += 1
        if k == layers:
            self.box(x0 + k, y + k - 1, z0 + k, x1 - k, y + k - 1, z1 - k, b)
        return y + k

    def sign(self, word, x0, y0, z, letters=('red_concrete', 'yellow_concrete'), back='black_concrete'):
        """Front-facing pixel sign; each letter alternates material, backed by a frame."""
        glyphs = {
            'C': ['###', '#..', '#..', '#..', '###'], 'A': ['###', '#.#', '###', '#.#', '#.#'],
            'S': ['###', '#..', '###', '..#', '###'], 'I': ['#', '#', '#', '#', '#'],
            'N': ['##.', '#.#', '#.#', '#.#', '#.#'], 'O': ['###', '#.#', '#.#', '#.#', '###'],
        }
        col = 0
        width = sum(len(glyphs[c][0]) + 1 for c in word) - 1
        self.box(x0 - 1, y0 - 1, z, x0 + width, y0 + 5, z, back)
        for i, ch in enumerate(word):
            g = glyphs[ch]
            for row, line in enumerate(g):
                for dx, px in enumerate(line):
                    if px == '#':
                        self.set(x0 + width - 1 - (col + dx), y0 + 4 - row, z - 1, letters[i % 2])  # viewer faces +z: mirror x
            col += len(g[0]) + 1
        # bulb border (marquee)
        for x in range(x0 - 1, x0 + width + 1, 2):
            self.set(x, y0 + 5, z - 1, 'ochre_froglight')
            self.set(x, y0 - 1, z - 1, 'ochre_froglight')


def concept_village_plains():
    """'Lucky Villager' v2, plains: 31 x 16 x 31 = facade piece (z 0..8) + gaming hall + back wing."""
    b = Blockout(31, 17, 31)
    P = {'found': 'stone_bricks', 'floor': 'dark_oak_planks', 'wall': 'white_terracotta', 'frame': 'stripped_dark_oak_log',
         'roof': 'deepslate_tiles', 'trim': 'gold_block', 'carpet': 'red_carpet', 'light': 'ochre_froglight'}
    # plaza / forecourt (z 0..5): path + lamp posts
    b.box(0, 0, 0, 30, 0, 5, 'dirt_path')
    for x in (3, 27):
        b.box(x, 1, 2, x, 3, 2, 'dark_oak_fence')
        b.set(x, 4, 2, 'lantern')
    # main hall 25 x 21 footprint (x 3..27, z 6..26)
    b.box(3, 0, 6, 27, 0, 26, P['found'])
    b.box(4, 0, 7, 26, 0, 25, P['floor'])
    b.walls(3, 1, 6, 27, 7, 26, P['wall'])
    for x in (3, 9, 15, 21, 27):       # timber frame pilasters
        b.box(x, 1, 6, x, 7, 6, P['frame'])
        b.box(x, 1, 26, x, 7, 26, P['frame'])
    for z in (6, 11, 16, 21, 26):
        b.box(3, 1, z, 3, 7, z, P['frame'])
        b.box(27, 1, z, 27, 7, z, P['frame'])
    b.box(3, 7, 6, 27, 7, 6, P['frame'])
    # tall arched windows
    for x in (6, 12, 18, 24):
        b.box(x, 2, 26, x + 1, 5, 26, 'glass_pane')
    for z in (8, 13, 18, 23):
        b.box(3, 2, z, 3, 5, z + 1, 'glass_pane')
        b.box(27, 2, z, 27, 5, z + 1, 'glass_pane')
    # entrance portico + double-height door
    b.box(12, 0, 3, 18, 0, 5, 'polished_andesite')
    b.box(13, 1, 6, 17, 4, 6, None)
    for x in (12, 18):
        b.box(x, 1, 3, x, 6, 3, 'quartz_pillar')
    b.box(11, 7, 2, 19, 7, 5, 'dark_oak_slab')
    b.box(11, 7, 2, 19, 7, 2, P['trim'])
    # marquee sign over the portico
    b.sign('CASINO', 7, 9, 4)
    b.box(6, 8, 5, 24, 8, 5, P['frame'])  # sign bracket on the eave
    # roof: stepped hip roof + central lantern (clerestory) over the gaming floor
    b.hip_roof(2, 8, 5, 28, 27, P['roof'], eave='dark_oak_slab', layers=4)
    b.box(11, 11, 11, 19, 13, 21, None)          # clerestory lantern over the gaming floor
    b.walls(11, 11, 11, 19, 13, 21, 'glass')
    for (x, z) in ((11, 11), (19, 11), (11, 21), (19, 21)):
        b.box(x, 11, z, x, 13, z, P['frame'])
    b.hip_roof(10, 14, 10, 20, 22, P['roof'], eave='dark_oak_slab', layers=3)
    b.box(15, 16, 16, 15, 16, 16, P['trim'])
    # interior (visible with --cut): carpet aisle, tables, slot bank, bar
    b.box(14, 1, 7, 16, 1, 25, P['carpet'])
    for (x, z) in ((7, 10), (7, 16), (21, 10), (21, 16)):
        b.box(x, 1, z, x + 2, 1, z + 1, 'burmaldaholic:blackjack_table')
    for x in range(5, 26, 2):
        if 13 <= x <= 17:
            continue
        b.set(x, 1, 24, 'burmaldaholic:slot_machine_copper')
        b.set(x, 2, 24, 'burmaldaholic:slot_machine_copper')
    b.box(5, 1, 20, 11, 1, 20, 'dark_oak_planks')  # bar counter
    b.box(19, 1, 20, 25, 1, 20, 'burmaldaholic:cashier')
    for (x, z) in ((8, 13), (22, 13), (15, 18)):
        b.set(x, 7, z, 'ochre_froglight')
    return b.v, b.size


def main(argv):
    if len(argv) >= 2:
        cut = None
        if '--cut' in argv:
            i = argv.index('--cut')
            cut = int(argv[i + 1])
            argv = argv[:i] + argv[i + 2:]
        vox, size = voxels_from_template(argv[0])
        print(render(vox, size, argv[1], cut=cut, title=Path(argv[0]).stem))
        return
    for name in ('village_casino_plains', 'village_casino_desert', 'piglin_parlor', 'high_roller_lounge'):
        vox, size = voxels_from_template(TPL / (name + '.nbt'))
        print(render(vox, size, OUT / ('current_' + name + '.png'), title='CURRENT ' + name + ' %dx%dx%d' % size))
    vox, size = concept_village_plains()
    print(render(vox, size, OUT / 'concept_village_plains.png', rotate=2, title='CONCEPT village casino v2 (plains) 31x17x31'))
    print(render(vox, size, OUT / 'concept_village_plains_plan.png', cut=1, rotate=2, title='CONCEPT v2 floor plan (cut at y=1)'))


if __name__ == '__main__':
    main(sys.argv[1:])
