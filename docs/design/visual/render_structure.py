#!/usr/bin/env python3
"""Preview renders of structure templates: review a casino building without starting the game.

Pillow only. Reads vanilla structure .nbt (gzip, own tiny NBT reader) and draws every block as a small
voxel model (slabs, stairs, carpets, panes, fences, lanterns, chains, banners, game tables, slot cabinets…)
with a procedural texture per material family, sun light + soft shadows + ambient occlusion, and an
optional night pass with real block-light flood fill and glow halos.

Views (all written by ``--views`` / ``--all``):
    iso_se iso_sw iso_nw iso_ne   isometric from the four corners (front = +z = south is "s")
    cutaway                       roof and ceiling removed above the floor row + 3 (interior, iso)
    plan                          top-down floor plan cut at the floor row + 1 with labelled games/NPCs
    night                         iso_se at night: only light sources + what they light, glow halos

Usage:
    python3 render_structure.py                               # v0.1 templates → mockups/casinos/v2/_legacy
    python3 render_structure.py a.nbt out.png [--cut Y] [--rotate K] [--night] [--plan] [--cutaway]
    python3 render_structure.py --manifest java/build/generated/casino-pack/previews/manifest.json --all
    python3 render_structure.py --manifest … --style tudor --views iso_se,plan --scale 10
    python3 render_structure.py --selftest                    # quick renderer smoke test (no files kept)

Manifest (written by PackGen, architecture doc §8.1). Accepted shape — extra fields are ignored:
    {"pieces":     [{"id": "casino/tudor/entrance", "template": "burmaldaholic:worldgen/casino/tudor/entrance",
                     "style": "tudor"?}, …],
     "assemblies": [{"id": "tudor/floor_a+vip", "style": "tudor"?,
                     "pieces": [{"template": "…", "origin": [x, y, z], "rotation": 0..3 | "clockwise_90" …}]}]}
Templates are looked up next to the manifest (``../resourcepacks/<pack>/data/<ns>/structure/<path>.nbt``) and in
the committed v0.1 tree. Unknown block ids render MAGENTA so a typo is visible immediately; the run prints them.
"""
import argparse
import gzip
import io
import json
import math
import struct
import sys
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[3]
LEGACY_PACK = ROOT / 'java/src/main/resources/resourcepacks/casinos'
TPL = LEGACY_PACK / 'data/burmaldaholic/structure/worldgen'
OUT = Path(__file__).resolve().parent / 'mockups' / 'casinos'
V2 = OUT / 'v2'

VIEWS = ('iso_se', 'iso_sw', 'iso_nw', 'iso_ne', 'cutaway', 'plan', 'night')
VIEW_TURNS = {'iso_se': 0, 'iso_sw': 3, 'iso_nw': 2, 'iso_ne': 1}

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


def short(name):
    return name.split(':', 1)[1] if name.startswith('minecraft:') else name


class Structure:
    """Blocks of one template (or an assembly): {(x, y, z): (name, props)}, markers, size."""

    def __init__(self, size, blocks=None, markers=None, title=''):
        self.size = tuple(size)
        self.blocks = blocks if blocks is not None else {}
        self.markers = markers if markers is not None else []   # [(pos, kind, text)]
        self.title = title

    @staticmethod
    def load(path, title=None):
        root = load_nbt(path)
        palette = root['palette'] if 'palette' in root else root['palettes'][0]
        states = [(short(p['Name']), dict(p.get('Properties', {}))) for p in palette]
        s = Structure(root['size'], title=title or Path(path).stem)
        for b in root['blocks']:
            name, props = states[b['state']]
            pos = tuple(b['pos'])
            if name == 'structure_block':
                meta = (b.get('nbt') or {}).get('metadata', '')
                if meta.startswith('burmaldaholic:worldgen '):
                    s.markers.append((pos,) + _marker(meta))
                continue
            if name == 'jigsaw':
                final = (b.get('nbt') or {}).get('final_state', 'minecraft:air')
                name, props = _parse_state(final)
                s.markers.append((pos, 'jigsaw', (b.get('nbt') or {}).get('name', '')))
            if name in ('air', 'structure_void', 'cave_air', 'void_air'):
                continue
            s.blocks[pos] = (name, props)
        return s

    def rotated(self, turns):
        """Quarter turns clockwise seen from above (x, z) -> (sz-1-z, x); facings rotate along."""
        s = self
        for _ in range(turns % 4):
            sx, sy, sz = s.size
            nb = {}
            for (x, y, z), (n, p) in s.blocks.items():
                nb[(sz - 1 - z, y, x)] = (n, _rotate_props(p))
            nm = [((sz - 1 - m[0][2], m[0][1], m[0][0]),) + tuple(m[1:]) for m in s.markers]
            s = Structure((sz, sy, sx), nb, nm, s.title)
        return s


def _parse_state(state):
    name, props = state, {}
    if '[' in state:
        name, rest = state.split('[', 1)
        for kv in rest.rstrip(']').split(','):
            if '=' in kv:
                k, v = kv.split('=', 1)
                props[k.strip()] = v.strip()
    return short(name), props


def _marker(meta):
    parts = meta[len('burmaldaholic:worldgen '):].split()
    if not parts:
        return ('marker', '')
    if parts[0] == 'npc' and len(parts) >= 2:
        return ('npc', parts[1])
    if parts[0] == 'chest':
        return ('chest', parts[1] if len(parts) > 1 else '')
    if parts[0] == 'casino':
        return ('anchor', parts[1] if len(parts) > 1 else '')
    return ('marker', parts[0])


CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}


def _rotate_props(p):
    if not p:
        return p
    q = dict(p)
    for k in ('facing', 'rotation_facing'):
        if q.get(k) in CW:
            q[k] = CW[q[k]]
    if 'axis' in q and q['axis'] in ('x', 'z'):
        q['axis'] = 'z' if q['axis'] == 'x' else 'x'
    if 'orientation' in q:
        a, _, b = q['orientation'].partition('_')
        q['orientation'] = CW.get(a, a) + '_' + CW.get(b, b) if b else q['orientation']
    if 'rotation' in q and q['rotation'].isdigit():
        q['rotation'] = str((int(q['rotation']) + 4) % 16)
    sides = {d: p.get(d) for d in CW if d in p}
    for d, v in sides.items():
        q[CW[d]] = v
    return q


# ---- colours --------------------------------------------------------------------------------------

DYES = {
    'white': (233, 236, 236), 'orange': (240, 118, 19), 'magenta': (189, 68, 179), 'light_blue': (58, 175, 217),
    'yellow': (248, 197, 39), 'lime': (112, 185, 25), 'pink': (237, 141, 172), 'gray': (62, 68, 71),
    'light_gray': (142, 142, 134), 'cyan': (21, 137, 145), 'purple': (121, 42, 172), 'blue': (53, 57, 157),
    'brown': (114, 71, 40), 'green': (84, 109, 27), 'red': (160, 39, 34), 'black': (21, 21, 26),
}
CONCRETE = {
    'white': (207, 213, 214), 'orange': (224, 97, 0), 'magenta': (169, 48, 159), 'light_blue': (35, 137, 198),
    'yellow': (240, 175, 21), 'lime': (94, 168, 24), 'pink': (213, 101, 142), 'gray': (54, 57, 61),
    'light_gray': (125, 125, 115), 'cyan': (21, 119, 136), 'purple': (100, 31, 156), 'blue': (44, 46, 143),
    'brown': (96, 59, 31), 'green': (73, 91, 36), 'red': (142, 32, 32), 'black': (8, 10, 15),
}
TERRACOTTA = {
    'white': (209, 178, 161), 'orange': (161, 83, 37), 'magenta': (149, 88, 108), 'light_blue': (113, 108, 137),
    'yellow': (186, 133, 35), 'lime': (103, 117, 52), 'pink': (161, 78, 78), 'gray': (57, 42, 35),
    'light_gray': (135, 106, 97), 'cyan': (86, 91, 91), 'purple': (118, 70, 86), 'blue': (74, 59, 91),
    'brown': (77, 51, 35), 'green': (76, 83, 42), 'red': (143, 61, 46), 'black': (37, 22, 16),
}
WOODS = {  # planks, log bark, stripped log
    'oak': ((162, 130, 78), (109, 85, 50), (177, 144, 86)),
    'spruce': ((114, 84, 48), (58, 37, 16), (115, 89, 52)),
    'birch': ((192, 175, 121), (216, 215, 210), (196, 176, 118)),
    'jungle': ((160, 115, 80), (85, 67, 25), (171, 132, 84)),
    'acacia': ((168, 90, 50), (103, 96, 86), (174, 92, 59)),
    'dark_oak': ((66, 43, 20), (60, 46, 30), (72, 56, 36)),
    'mangrove': ((117, 54, 48), (84, 66, 41), (119, 54, 47)),
    'cherry': ((226, 178, 172), (54, 33, 44), (215, 145, 148)),
    'bamboo': ((193, 173, 80), (127, 143, 59), (193, 173, 80)),
    'crimson': ((101, 48, 70), (92, 25, 29), (137, 57, 90)),
    'warped': ((43, 104, 99), (58, 58, 77), (57, 150, 147)),
    'pale_oak': ((228, 217, 214), (90, 80, 75), (234, 225, 222)),
}
COLOURS = {
    'cobblestone': (122, 122, 122), 'mossy_cobblestone': (100, 118, 86), 'stone': (125, 125, 125),
    'stone_bricks': (122, 121, 122), 'mossy_stone_bricks': (115, 121, 105), 'cracked_stone_bricks': (118, 117, 118),
    'chiseled_stone_bricks': (119, 118, 119), 'smooth_stone': (158, 158, 158), 'andesite': (136, 136, 136),
    'polished_andesite': (132, 135, 134), 'diorite': (188, 188, 188), 'polished_diorite': (192, 193, 194),
    'granite': (149, 103, 85), 'polished_granite': (154, 106, 89), 'tuff': (108, 109, 102),
    'polished_tuff': (98, 104, 99), 'tuff_bricks': (98, 102, 95), 'calcite': (223, 224, 220),
    'deepslate': (80, 80, 82), 'cobbled_deepslate': (77, 77, 80), 'polished_deepslate': (72, 72, 73),
    'deepslate_bricks': (70, 70, 71), 'deepslate_tiles': (54, 54, 55), 'chiseled_deepslate': (54, 54, 55),
    'bricks': (150, 97, 83), 'mud_bricks': (137, 104, 79), 'packed_mud': (142, 106, 79),
    'sandstone': (216, 203, 155), 'cut_sandstone': (217, 206, 159), 'smooth_sandstone': (223, 214, 170),
    'chiseled_sandstone': (216, 202, 155), 'red_sandstone': (186, 99, 29), 'cut_red_sandstone': (189, 101, 31),
    'smooth_red_sandstone': (181, 98, 31), 'chiseled_red_sandstone': (183, 96, 27),
    'terracotta': (152, 94, 67), 'packed_ice': (141, 180, 250), 'ice': (145, 183, 253), 'blue_ice': (116, 167, 253),
    'snow_block': (249, 254, 254), 'snow': (249, 254, 254), 'powder_snow': (248, 253, 253),
    'quartz_block': (235, 229, 222), 'smooth_quartz': (235, 229, 222), 'quartz_pillar': (235, 230, 220),
    'chiseled_quartz_block': (231, 226, 218), 'quartz_bricks': (234, 229, 221),
    'glass': (200, 225, 235), 'tinted_glass': (44, 38, 46),
    'glowstone': (255, 214, 120), 'sea_lantern': (210, 235, 225), 'redstone_lamp': (255, 160, 70),
    'shroomlight': (240, 146, 70), 'ochre_froglight': (250, 235, 150), 'pearlescent_froglight': (240, 210, 230),
    'verdant_froglight': (215, 240, 190), 'end_rod': (250, 245, 230), 'lantern': (240, 190, 90),
    'soul_lantern': (110, 220, 230), 'jack_o_lantern': (230, 150, 30), 'torch': (255, 210, 90),
    'wall_torch': (255, 210, 90), 'soul_torch': (110, 220, 230), 'campfire': (230, 140, 50),
    'soul_campfire': (90, 210, 220), 'beacon': (120, 220, 215), 'copper_bulb': (230, 150, 90),
    'waxed_copper_bulb': (230, 150, 90),
    'gold_block': (246, 208, 61), 'raw_gold_block': (221, 169, 46), 'emerald_block': (42, 203, 87),
    'diamond_block': (98, 237, 228), 'lapis_block': (31, 67, 140), 'iron_block': (220, 220, 220),
    'netherite_block': (66, 61, 63), 'redstone_block': (175, 24, 5), 'amethyst_block': (133, 97, 191),
    'amethyst_cluster': (164, 126, 207), 'budding_amethyst': (132, 96, 186),
    'copper_block': (192, 107, 79), 'cut_copper': (191, 106, 80), 'exposed_copper': (161, 125, 103),
    'weathered_copper': (108, 153, 110), 'oxidized_copper': (82, 162, 132), 'chiseled_copper': (184, 100, 73),
    'copper_grate': (192, 107, 79),
    'blackstone': (42, 35, 40), 'polished_blackstone': (53, 48, 56), 'polished_blackstone_bricks': (48, 42, 49),
    'chiseled_polished_blackstone': (53, 48, 56), 'gilded_blackstone': (90, 70, 40),
    'cracked_polished_blackstone_bricks': (44, 37, 43), 'basalt': (80, 81, 86), 'polished_basalt': (99, 98, 100),
    'nether_bricks': (44, 21, 26), 'red_nether_bricks': (69, 7, 9), 'chiseled_nether_bricks': (47, 23, 28),
    'magma_block': (142, 63, 31), 'netherrack': (97, 38, 38), 'crimson_nylium': (130, 31, 31),
    'warped_nylium': (43, 114, 101), 'soul_sand': (81, 62, 50), 'soul_soil': (75, 57, 46),
    'nether_wart_block': (114, 2, 2), 'warped_wart_block': (22, 119, 121),
    'purpur_block': (169, 125, 169), 'purpur_pillar': (171, 129, 171), 'end_stone': (219, 222, 158),
    'end_stone_bricks': (218, 224, 162), 'obsidian': (15, 10, 24), 'crying_obsidian': (32, 10, 60),
    'prismarine': (99, 156, 151), 'prismarine_bricks': (99, 171, 158), 'dark_prismarine': (51, 91, 75),
    'bookshelf': (117, 94, 59), 'chiseled_bookshelf': (117, 94, 59), 'chest': (164, 116, 46),
    'trapped_chest': (164, 116, 46), 'barrel': (138, 102, 58), 'lectern': (173, 137, 83),
    'crafting_table': (130, 100, 60), 'loom': (142, 119, 92), 'cartography_table': (104, 87, 67),
    'smithing_table': (57, 58, 70), 'fletching_table': (197, 180, 133), 'composter': (117, 73, 41),
    'brewing_stand': (122, 101, 81), 'cauldron': (74, 73, 74), 'anvil': (68, 68, 68), 'bell': (253, 229, 96),
    'hay_block': (166, 136, 38), 'target': (226, 170, 157), 'note_block': (88, 58, 40), 'jukebox': (94, 64, 47),
    'grass_block': (95, 159, 53), 'dirt': (134, 96, 67), 'coarse_dirt': (119, 85, 59), 'rooted_dirt': (144, 103, 76),
    'dirt_path': (148, 122, 65), 'podzol': (91, 63, 24), 'mud': (60, 57, 61), 'sand': (219, 207, 163),
    'red_sand': (190, 102, 33), 'gravel': (131, 127, 126), 'clay': (160, 166, 179), 'moss_block': (89, 109, 45),
    'moss_carpet': (89, 109, 45), 'water': (63, 118, 228), 'lava': (207, 92, 20),
    'oak_leaves': (60, 128, 30), 'spruce_leaves': (50, 90, 50), 'birch_leaves': (98, 140, 60),
    'jungle_leaves': (48, 140, 20), 'acacia_leaves': (70, 130, 30), 'dark_oak_leaves': (50, 115, 25),
    'azalea_leaves': (90, 117, 44), 'flowering_azalea_leaves': (100, 112, 60), 'cherry_leaves': (229, 172, 194),
    'mangrove_leaves': (70, 120, 40),
    'iron_bars': (110, 112, 112), 'iron_chain': (60, 64, 74), 'chain': (60, 64, 74), 'lightning_rod': (192, 107, 79),
    'flower_pot': (120, 64, 49), 'candle': (229, 215, 175), 'decorated_pot': (140, 80, 60),
    'sponge': (195, 192, 74), 'slime_block': (111, 192, 91), 'honey_block': (251, 185, 52),
    'scaffolding': (170, 132, 73), 'ladder': (125, 97, 55), 'cobweb': (228, 233, 234),
    'jigsaw': (80, 70, 90), 'structure_block': (80, 70, 90), 'player_head': (70, 50, 40), 'skeleton_skull': (200, 200, 200),
    'short_grass': (95, 159, 53), 'tall_grass': (95, 159, 53), 'fern': (80, 130, 50), 'large_fern': (80, 130, 50),
    'dead_bush': (107, 79, 41), 'sweet_berry_bush': (60, 100, 50), 'cactus': (85, 127, 43),
    'pointed_dripstone': (129, 102, 89), 'dripstone_block': (134, 107, 92),
}
LIGHT = {  # block light emitted (vanilla levels)
    'glowstone': 15, 'sea_lantern': 15, 'shroomlight': 15, 'ochre_froglight': 15, 'pearlescent_froglight': 15,
    'verdant_froglight': 15, 'lantern': 15, 'jack_o_lantern': 15, 'beacon': 15, 'campfire': 15, 'lava': 15,
    'end_rod': 14, 'torch': 14, 'wall_torch': 14, 'soul_lantern': 10, 'soul_torch': 10, 'soul_wall_torch': 10,
    'soul_campfire': 10, 'magma_block': 3, 'crying_obsidian': 10, 'amethyst_cluster': 5, 'brewing_stand': 1,
    'redstone_lamp': 15, 'copper_bulb': 15, 'waxed_copper_bulb': 15, 'sea_pickle': 6, 'glow_lichen': 7,
    'conduit': 15, 'respawn_anchor': 0, 'end_gateway': 15, 'enchanting_table': 7,
}
NO_LIGHT_UNLESS_LIT = {'redstone_lamp', 'copper_bulb', 'waxed_copper_bulb', 'campfire', 'soul_campfire'}
FLORA = ('short_grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'poppy', 'dandelion', 'blue_orchid',
         'allium', 'azure_bluet', 'tulip', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley', 'sapling',
         'sweet_berry_bush', 'rose_bush', 'peony', 'lilac', 'sunflower', 'torchflower', 'pink_petals',
         'wildflowers', 'bush', 'firefly_bush', 'leaf_litter')

GAME_LABELS = [  # (substring of our block id, code, felt/screen colour)
    ('nether_cashier', 'NC', (150, 40, 40)), ('cashier', 'CA', (40, 120, 70)),
    ('blackjack', 'BJ', (24, 110, 60)), ('roulette', 'RO', (30, 100, 55)), ('craps', 'CR', (28, 104, 70)),
    ('baccarat', 'BA', (130, 30, 40)), ('uth', 'UT', (30, 70, 130)), ('poker', 'PK', (24, 96, 52)),
    ('slot_machine_netherite', 'S3', (70, 60, 80)), ('slot_machine_gold', 'S2', (226, 190, 70)),
    ('slot_machine', 'S1', (196, 112, 76)), ('wheel', 'WH', (200, 60, 50)), ('plinko', 'PL', (60, 90, 190)),
]
FAMILY = {'NC': 'cashier', 'CA': 'cashier', 'BJ': 'blackjack', 'RO': 'roulette', 'CR': 'craps', 'BA': 'baccarat',
          'UT': 'uth', 'PK': 'poker', 'S1': 'slots', 'S2': 'slots', 'S3': 'slots', 'WH': 'wheel', 'PL': 'plinko'}
REQUIRED_FAMILIES = ('cashier', 'blackjack', 'roulette', 'craps', 'baccarat', 'uth', 'poker', 'slots', 'wheel', 'plinko')
NPC_CODES = {'croupier': 'Cr', 'loan_shark': 'LS', 'baccarat_dealer': 'Bd', 'piglin_dealer': 'Pd',
             'piglin_moneylender': 'Pm', 'shulker_croupier': 'Sh'}

UNKNOWN = set()
MAGENTA = (230, 0, 230)


def game_label(name):
    if not name.startswith('burmaldaholic:'):
        return None
    n = name.split(':', 1)[1]
    for key, code, col in GAME_LABELS:
        if key in n:
            return code, col
    return None


def _dye_of(name):
    for d in sorted(DYES, key=len, reverse=True):
        if name.startswith(d + '_'):
            return d, name[len(d) + 1:]
    return None, name


def colour(name):
    """Average texture colour of a block id (no namespace for vanilla)."""
    if name in COLOURS:
        return COLOURS[name]
    if name.startswith('burmaldaholic:'):
        g = game_label(name)
        return g[1] if g else (120, 90, 60)
    if name.startswith('waxed_'):
        return colour(name[6:])
    dye, rest = _dye_of(name)
    if dye:
        if rest in ('wool', 'carpet', 'bed', 'banner', 'wall_banner', 'candle', 'shulker_box'):
            return DYES[dye]
        if rest in ('concrete', 'concrete_powder'):
            return CONCRETE[dye]
        if rest in ('terracotta', 'glazed_terracotta'):
            return TERRACOTTA[dye]
        if rest in ('stained_glass', 'stained_glass_pane'):
            return DYES[dye]
    for w, (planks, bark, stripped) in WOODS.items():
        if name.startswith(w + '_') or name.startswith('stripped_' + w + '_'):
            rest = name[len(w) + 1:] if name.startswith(w + '_') else name[len('stripped_' + w) + 1:]
            if name.startswith('stripped_'):
                return stripped
            if rest in ('log', 'wood', 'stem', 'hyphae'):
                return bark
            if rest == 'leaves':
                break
            return planks
    if name.startswith('potted_') or name.endswith('_tulip') or name in FLORA:
        return (150, 80, 50) if name.startswith('potted_') else (95, 159, 53)
    for suffix in ('_stairs', '_slab', '_wall', '_fence', '_fence_gate', '_trapdoor', '_door', '_pane', '_carpet',
                   '_pressure_plate', '_button', '_wall_sign', '_sign', '_hanging_sign'):
        if name.endswith(suffix):
            base = name[: -len(suffix)]
            for cand in (base, base + 's', base + '_block', base + '_planks', base.replace('_brick', '_bricks'),
                         base.replace('_tile', '_tiles'), base + '_bricks'):
                if cand in COLOURS:
                    return COLOURS[cand]
                c = colour(cand) if cand != name and not cand.endswith(suffix) and _known(cand) else None
                if c:
                    return c
    if name.endswith('_candle') or name.endswith('_candle_cake'):
        return (229, 215, 175)
    if 'glass' in name:
        return (190, 215, 230)
    if name.endswith('_ore'):
        return (125, 125, 125)
    UNKNOWN.add(name)
    return MAGENTA


def _known(name):
    if name in COLOURS:
        return True
    dye, rest = _dye_of(name)
    if dye and rest in ('wool', 'concrete', 'terracotta', 'stained_glass'):
        return True
    return any(name.startswith(w + '_') and name[len(w) + 1:] in ('planks',) for w in WOODS)


def emission(name, props):
    lvl = LIGHT.get(name, 0)
    if name in NO_LIGHT_UNLESS_LIT and props.get('lit', 'true') != 'true':
        return 0
    if name.endswith('candle') or name.endswith('_candles') or name == 'candles':
        return 3 * int(props.get('candles', '1')) if props.get('lit') == 'true' else 0
    if name.startswith('burmaldaholic:slot_machine') or name.startswith('burmaldaholic:wheel') \
            or name.startswith('burmaldaholic:plinko'):
        return 0
    return lvl


# ---- block models ---------------------------------------------------------------------------------
# A model is a list of boxes (x0, y0, z0, x1, y1, z1) in cell units [0, 1] with optional per-face colours.

FULL = (0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
P = 1 / 16


def _box(x0, y0, z0, x1, y1, z1, col=None, faces=None):
    return {'b': (x0 * P, y0 * P, z0 * P, x1 * P, y1 * P, z1 * P), 'c': col, 'f': faces or {}}


def _facing_box(facing, depth_from, depth_to, y0, y1, w0=0, w1=16):
    """Box hugging the `facing` side of the cell: depth measured from that side, in pixels."""
    if facing == 'north':
        return (w0, y0, depth_from, w1, y1, depth_to)
    if facing == 'south':
        return (w0, y0, 16 - depth_to, w1, y1, 16 - depth_from)
    if facing == 'west':
        return (depth_from, y0, w0, depth_to, y1, w1)
    return (16 - depth_to, y0, w0, 16 - depth_from, y1, w1)


FACE_DIR = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}


def model(name, props):
    """[{'b': box, 'c': colour override, 'f': {face: colour}}], kind: 'full' | 'glass' | 'part' | 'flora'."""
    n = name
    if n.startswith('burmaldaholic:'):
        return _game_model(n, props)
    if n.endswith('_slab'):
        t = props.get('type', 'bottom')
        if t == 'double':
            return [{'b': FULL, 'c': None, 'f': {}}], 'full'
        return [_box(0, 8 if t == 'top' else 0, 0, 16, 16 if t == 'top' else 8, 16)], 'part'
    if n.endswith('_stairs'):
        top = props.get('half', 'bottom') == 'top'
        f = props.get('facing', 'north')
        base = _box(0, 8 if top else 0, 0, 16, 16 if top else 8, 16)
        step = _box(*_facing_box(f, 0, 8, 0 if top else 8, 8 if top else 16))
        return [base, step], 'part'
    if n.endswith('_carpet') or n in ('pale_moss_carpet',):
        return [_box(0, 0, 0, 16, 1, 16)], 'part'
    if n == 'snow':
        h = 2 * int(props.get('layers', '1'))
        return [_box(0, 0, 0, 16, h, 16)], 'full' if h >= 16 else 'part'
    if n.endswith('_pane') or n == 'iron_bars' or n.endswith('glass_pane'):
        boxes = [_box(7, 0, 7, 9, 16, 9)]
        for d, (dx, dz) in FACE_DIR.items():
            if props.get(d) == 'true':
                boxes.append(_box(*_facing_box(d, 0, 7, 0, 16, 7, 9)))
        if len(boxes) == 1:
            boxes = [_box(0, 0, 7, 16, 16, 9)] if n != 'iron_bars' else boxes
        return boxes, 'glass' if 'glass' in n else 'part'
    if n.endswith('_fence'):
        boxes = [_box(6, 0, 6, 10, 16, 10)]
        for d in FACE_DIR:
            if props.get(d) == 'true':
                boxes.append(_box(*_facing_box(d, 0, 6, 12, 15, 7, 9)))
                boxes.append(_box(*_facing_box(d, 0, 6, 6, 9, 7, 9)))
        return boxes, 'part'
    if n.endswith('_wall') and not n.endswith('_banner') and 'sign' not in n and 'torch' not in n:
        boxes = [_box(4, 0, 4, 12, 16, 12)]
        for d in FACE_DIR:
            v = props.get(d, 'none')
            if v in ('low', 'tall', 'true'):
                boxes.append(_box(*_facing_box(d, 0, 4, 0, 14 if v != 'tall' else 16, 5, 11)))
        return boxes, 'part'
    if n.endswith('_fence_gate'):
        f = props.get('facing', 'north')
        axis_x = f in ('north', 'south')
        return [_box(0, 5, 7, 16, 16, 9) if axis_x else _box(7, 5, 0, 9, 16, 16)], 'part'
    if n.endswith('_trapdoor'):
        if props.get('open') == 'true':
            return [_box(*_facing_box(_opp(props.get('facing', 'north')), 0, 3, 0, 16))], 'part'
        top = props.get('half') == 'top'
        return [_box(0, 13 if top else 0, 0, 16, 16 if top else 3, 16)], 'part'
    if n.endswith('_door'):
        f = props.get('facing', 'north')
        return [_box(*_facing_box(_opp(f), 0, 3, 0, 16))], 'part'
    if n.endswith('_wall_banner'):
        f = props.get('facing', 'north')
        return [_box(*_facing_box(_opp(f), 0, 2, -14, 15, 1, 15))], 'part'
    if n.endswith('_banner'):
        return [_box(7, 0, 7, 9, 16, 9, (110, 85, 55)), _box(1, 2, 7, 15, 30, 9)], 'part'
    if n in ('lantern', 'soul_lantern'):
        y0 = 1 if props.get('hanging') == 'true' else 0
        return [_box(5, y0, 5, 11, y0 + 7, 11), _box(6, y0 + 7, 6, 10, y0 + 9, 10, (60, 60, 60))], 'part'
    if n in ('iron_chain', 'chain'):
        a = props.get('axis', 'y')
        if a == 'y':
            return [_box(7, 0, 7, 9, 16, 9)], 'part'
        return [_box(0, 7, 7, 16, 9, 9)] if a == 'x' else [_box(7, 7, 0, 9, 9, 16)], 'part'
    if n == 'end_rod' or n == 'lightning_rod':
        f = props.get('facing', 'up')
        if f in ('up', 'down'):
            return [_box(7, 0, 7, 9, 16, 9)], 'part'
        return [_box(0, 7, 7, 16, 9, 9)] if f in ('east', 'west') else [_box(7, 7, 0, 9, 9, 16)], 'part'
    if n in ('torch', 'soul_torch', 'redstone_torch'):
        return [_box(7, 0, 7, 9, 10, 9, (110, 85, 55)), _box(7, 10, 7, 9, 12, 9)], 'part'
    if n.endswith('wall_torch'):
        f = props.get('facing', 'north')
        return [_box(*_facing_box(_opp(f), 0, 4, 3, 13, 7, 9))], 'part'
    if n == 'candle' or n.endswith('_candle'):
        c = int(props.get('candles', '1'))
        boxes = [_box(7, 0, 7, 9, 6, 9)]
        if c >= 2:
            boxes.append(_box(4, 0, 9, 6, 5, 11))
        if c >= 3:
            boxes.append(_box(10, 0, 5, 12, 5, 7))
        if c >= 4:
            boxes.append(_box(10, 0, 10, 12, 4, 12))
        return boxes, 'part'
    if n == 'flower_pot' or n.startswith('potted_'):
        boxes = [_box(5, 0, 5, 11, 6, 11, (120, 64, 49))]
        if n.startswith('potted_'):
            plant = n[len('potted_'):]
            pc = (95, 159, 53)
            dye, _ = _dye_of(plant.replace('tulip', 'x'))
            if 'red' in plant or 'poppy' in plant:
                pc = (190, 40, 40)
            elif 'cactus' in plant:
                pc = (85, 127, 43)
            elif dye:
                pc = DYES[dye]
            boxes.append(_box(6, 6, 6, 10, 13, 10, (70, 130, 40)))
            boxes.append(_box(5, 11, 5, 11, 14, 11, pc))
        return boxes, 'part'
    if n in ('campfire', 'soul_campfire'):
        return [_box(0, 0, 0, 16, 7, 16, (95, 70, 45), {'top': colour(n)})], 'part'
    if n in ('chest', 'trapped_chest', 'ender_chest'):
        return [_box(1, 0, 1, 15, 14, 15)], 'part'
    if n == 'lectern':
        return [_box(0, 0, 0, 16, 2, 16), _box(4, 2, 4, 12, 13, 12), _box(0, 12, 0, 16, 16, 16, (190, 160, 110))], 'part'
    if n == 'brewing_stand':
        return [_box(7, 0, 7, 9, 14, 9, (230, 210, 120)), _box(2, 0, 2, 14, 2, 14, (90, 90, 90))], 'part'
    if n == 'bell':
        return [_box(4, 4, 4, 12, 12, 12)], 'part'
    if n.endswith('_pressure_plate'):
        return [_box(1, 0, 1, 15, 1, 15)], 'part'
    if n.endswith('_button'):
        return [_box(5, 0, 6, 11, 2, 10)], 'part'
    if n in ('ladder', 'vine', 'glow_lichen'):
        return [_box(*_facing_box(_opp(props.get('facing', 'north')), 0, 1, 0, 16))], 'part'
    if n.endswith('_sign'):
        if 'wall' in n:
            return [_box(*_facing_box(_opp(props.get('facing', 'north')), 0, 2, 4, 12))], 'part'
        return [_box(7, 0, 7, 9, 9, 9), _box(0, 8, 7, 16, 16, 9)], 'part'
    if n in ('amethyst_cluster', 'large_amethyst_bud', 'pointed_dripstone', 'sea_pickle'):
        return [_box(4, 0, 4, 12, 10, 12)], 'part'
    if n in ('player_head', 'skeleton_skull', 'wither_skeleton_skull', 'piglin_head', 'zombie_head', 'creeper_head',
             'dragon_head'):
        return [_box(4, 0, 4, 12, 8, 12)], 'part'
    if n.endswith('_wall_head') or n.endswith('_wall_skull'):
        return [_box(*_facing_box(_opp(props.get('facing', 'north')), 0, 8, 4, 12, 4, 12))], 'part'
    if n == 'water':
        return [_box(0, 0, 0, 16, 14, 16)], 'glass'
    if n in ('cauldron', 'composter'):
        return [{'b': FULL, 'c': None, 'f': {}}], 'part'
    if n.startswith('potted') or n in FLORA or n.endswith('_sapling') or n.endswith('_tulip') or n.endswith('_bush') \
            or n.endswith('_flower') or n in ('poppy', 'dandelion', 'blue_orchid', 'allium', 'azure_bluet',
                                              'oxeye_daisy', 'cornflower', 'lily_of_the_valley'):
        return [_box(4, 0, 4, 12, 12, 12)], 'flora'
    if n.endswith('_leaves'):
        return [{'b': FULL, 'c': None, 'f': {}}], 'leaves'
    if n in ('glass', 'tinted_glass', 'ice') or n.endswith('stained_glass'):
        return [{'b': FULL, 'c': None, 'f': {}}], 'glass'
    return [{'b': FULL, 'c': None, 'f': {}}], 'full'


def _opp(f):
    return {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}.get(f, f)


def _game_model(n, props):
    f = props.get('facing', 'south')
    label = game_label(n)
    code, col = label if label else ('??', (120, 90, 60))
    wood = (92, 58, 30)
    rim = (60, 38, 18)
    if code in ('S1', 'S2', 'S3'):  # cabinet, screen on the player side, gold crown
        screen = {'north': 'north', 'south': 'south', 'east': 'east', 'west': 'west'}[f]
        body = _box(1, 0, 1, 15, 16, 15, col, {screen: (40, 200, 230), 'top': shade(col, 1.2)})
        return [body, _box(3, 16, 3, 13, 18, 13, (250, 210, 70))], 'part'
    if code == 'WH':  # upright wheel disc facing the player
        disc = _box(*_facing_box(_opp(f), 5, 11, 0, 16), col, {f: (230, 200, 60), 'top': (200, 60, 50)})
        return [_box(2, 0, 2, 14, 4, 14, wood), disc, _box(7, 16, 7, 9, 18, 9, (250, 210, 70))], 'part'
    if code == 'PL':  # tall board
        board = _box(*_facing_box(_opp(f), 4, 12, 0, 16), col, {f: (220, 220, 240)})
        return [_box(2, 0, 2, 14, 3, 14, wood), board, _box(*_facing_box(_opp(f), 4, 12, 16, 18), (250, 210, 70))], 'part'
    if code in ('CA', 'NC'):  # counter with a green glass top
        return [_box(0, 0, 0, 16, 14, 16, (70, 55, 40), {f: shade(col, 1.1)}), _box(1, 14, 1, 15, 16, 15, col)], 'part'
    # card / dice table: wooden body, rim, felt top with a lighter player-side band
    felt = col
    band = shade(felt, 1.35)
    boxes = [_box(3, 0, 3, 13, 9, 13, wood), _box(0, 9, 0, 16, 12, 16, rim, {'top': felt})]
    boxes.append(_box(*_facing_box(f, 1, 3, 12, 12.6, 1, 15), band))
    return boxes, 'part'


# ---- textures -------------------------------------------------------------------------------------


def _hash(*v):
    return zlib.crc32(repr(v).encode()) & 0xffff


def pattern(name, face, u, v, n, cell):
    """Brightness multiplier of texel (u, v) of an n×n face: material families get recognisable textures."""
    h = _hash(name, face, u, v, cell) / 65535.0
    noise = 0.94 + 0.12 * h
    nm = name
    if nm.startswith('burmaldaholic:'):
        return 1.0
    if 'planks' in nm or nm.endswith('_slab') and ('oak' in nm or 'spruce' in nm or 'birch' in nm) \
            or nm.endswith('_stairs') and any(w in nm for w in WOODS):
        if face == 'top':
            return (0.86 if v % 2 == 1 and v == n - 1 else 1.0) * (0.97 + 0.06 * h)
        return (0.82 if v == n - 1 or (u == (cell[1] % 2) * 2 and v % 2 == 0) else 1.0) * (0.95 + 0.08 * h)
    if nm.endswith('_log') or nm.endswith('_wood') or nm.endswith('_stem') or nm.endswith('_hyphae'):
        if face == 'top':
            r = max(abs(u - (n - 1) / 2), abs(v - (n - 1) / 2))
            return 0.85 if r >= (n - 1) / 2 else (1.05 if int(r) % 2 == 0 else 0.92)
        return (0.82 if u % 2 == 0 else 1.0) * noise
    if 'bricks' in nm or 'brick_' in nm or nm.endswith('_tiles') or 'tile_' in nm:
        rows = max(2, n // 2)
        row = v * rows // n
        off = (row % 2) * (n // 2)
        if v % max(1, n // rows) == n // rows - 1:
            return 0.72
        if (u + off) % n == 0:
            return 0.75
        return noise
    if 'terracotta' in nm or 'concrete' in nm:
        return 0.97 + 0.06 * h
    if 'wool' in nm or 'carpet' in nm:
        return 0.9 + 0.18 * h
    if 'quartz_pillar' in nm or 'purpur_pillar' in nm or nm.startswith('quartz') or nm.startswith('smooth_'):
        if face != 'top' and 'pillar' in nm:
            return 0.9 if u in (0, n - 1) else 1.02
        return 0.98 + 0.04 * h
    if 'glass' in nm or nm in ('ice', 'packed_ice', 'blue_ice'):
        return 1.18 if (u + v) == n - 1 or (u + v) == n else 1.0
    if 'froglight' in nm or nm in ('glowstone', 'sea_lantern', 'shroomlight', 'redstone_lamp'):
        c = abs(u - (n - 1) / 2) + abs(v - (n - 1) / 2)
        return 1.12 - 0.05 * c
    if nm in ('bookshelf', 'chiseled_bookshelf') and face != 'top':
        return 0.75 if v in (0, n - 1) else (0.85 + 0.35 * h)
    if nm in ('grass_block',) and face != 'top':
        return 1.0
    if 'leaves' in nm:
        return 0.7 + 0.5 * h
    if 'gold' in nm or 'copper' in nm or nm.endswith('_block') and ('iron' in nm or 'emerald' in nm or 'diamond' in nm):
        return 1.12 if u == 0 or v == 0 else (0.86 if u == n - 1 or v == n - 1 else 1.0)
    return noise


def face_colour(name, face, base):
    """Faces that differ from the average colour (grass sides, logs' ends…)."""
    if name == 'grass_block' and face != 'top':
        return COLOURS['dirt']
    if name in ('bookshelf', 'chiseled_bookshelf') and face == 'top':
        return WOODS['oak'][0]
    if (name.endswith('_log') or name.endswith('_stem')) and face == 'top':
        for w, (planks, bark, stripped) in WOODS.items():
            if name.startswith(w) or name.startswith('stripped_' + w):
                return stripped
    if name in ('barrel',) and face == 'top':
        return (110, 82, 48)
    if name == 'hay_block' and face == 'top':
        return (180, 150, 50)
    return base


# ---- lighting -------------------------------------------------------------------------------------


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Scene:
    """Voxel scene with derived data: opaque cells, block light, models."""

    def __init__(self, st):
        self.st = st
        self.models = {}
        self.opaque = set()      # full non-translucent cubes (cull + shadow + AO)
        self.solid = set()       # anything that casts a shadow (parts too)
        self.emit = {}
        for pos, (name, props) in st.blocks.items():
            boxes, kind = model(name, props)
            self.models[pos] = (name, props, boxes, kind)
            if kind == 'full':
                self.opaque.add(pos)
            if kind in ('full', 'part', 'leaves'):
                self.solid.add(pos)
            e = emission(name, props)
            if e:
                self.emit[pos] = e
        self.light = None

    def block_light(self):
        """Vanilla-like flood fill: level-1 per step through every cell that is not an opaque cube."""
        if self.light is not None:
            return self.light
        sx, sy, sz = self.st.size
        light = dict(self.emit)
        frontier = sorted(self.emit.items(), key=lambda kv: -kv[1])
        buckets = {}
        for p, l in frontier:
            buckets.setdefault(l, []).append(p)
        for lvl in range(15, 1, -1):
            for p in buckets.get(lvl, []):
                if light.get(p, 0) != lvl:
                    continue
                x, y, z = p
                for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
                    if not (-8 <= q[0] < sx + 8 and -8 <= q[1] < sy + 8 and -8 <= q[2] < sz + 8):
                        continue
                    if q in self.opaque:
                        continue
                    if light.get(q, 0) < lvl - 1:
                        light[q] = lvl - 1
                        buckets.setdefault(lvl - 1, []).append(q)
        self.light = light
        return light


SUN = (-0.55, 1.0, 0.38)  # towards the sun: light from the upper left of the picture


def in_shadow(scene, x, y, z, steps=48):
    """Ray-march from a face point towards the sun through the solid cells."""
    lx, ly, lz = SUN
    norm = math.sqrt(lx * lx + ly * ly + lz * lz)
    dx, dy, dz = lx / norm * 0.5, ly / norm * 0.5, lz / norm * 0.5
    for i in range(1, steps):
        c = (math.floor(x + dx * i), math.floor(y + dy * i), math.floor(z + dz * i))
        if c in scene.solid:
            return True
        if c[1] > scene.st.size[1] + 2:
            return False
    return False


def vertex_ao(occ, s1, s2, c):
    a = 0 if (s1 and s2) else 3 - (s1 + s2 + c)
    return (0.58, 0.72, 0.86, 1.0)[a]


# ---- iso renderer ---------------------------------------------------------------------------------


def _font(size, bold=False):
    for f in (('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf'),):
        for d in ('/usr/share/fonts/truetype/dejavu/', ''):
            try:
                return ImageFont.truetype(d + f, size)
            except OSError:
                pass
    return ImageFont.load_default()


def render(st, out, scale=10, cut=None, title=None, rotate=0, night=False, texels=None, ground=None, subtitle=None):
    """Isometric render of a Structure (2:1 dimetric, view direction (1, 1, 1))."""
    if isinstance(st, tuple):  # legacy call style: (vox dict, size)
        vox, size = st
        st = Structure(size, {p: (n, {}) for p, n in vox.items()})
    st = st.rotated(rotate)
    if cut is not None:
        st = Structure(st.size, {p: b for p, b in st.blocks.items() if p[1] <= cut}, st.markers, st.title)
    if ground is not None:
        st = with_ground(st, ground)
    scene = Scene(st)
    light = scene.block_light() if night else None
    n_tex = texels if texels is not None else (4 if scale >= 8 else 2 if scale >= 5 else 1)

    xs = [p[0] for p in st.blocks] or [0]
    zs = [p[2] for p in st.blocks] or [0]
    ys = [p[1] for p in st.blocks] or [0]
    minx, maxx, minz, maxz, maxy = min(xs), max(xs) + 1, min(zs), max(zs) + 1, max(ys) + 2
    miny = min(ys)
    head = 34 if title else 6
    w = int((maxx - minx + maxz - minz) * scale + 4 * scale)
    h = int((maxx - minx + maxz - minz) * scale / 2 + (maxy - miny) * scale + 6 * scale + head)
    ox = (maxz - minz) * scale + 2 * scale - (minx - minz) * scale
    oy = head + (maxy) * scale + scale - (minx + minz) * scale / 2

    img = Image.new('RGB', (w, h))
    _background(img, night)
    d = ImageDraw.Draw(img, 'RGBA')

    def proj(X, Y, Z):
        return (ox + (X - Z) * scale, oy + (X + Z) * scale / 2 - Y * scale)

    items = []
    for pos, (name, props, boxes, kind) in scene.models.items():
        x, y, z = pos
        for bi, bx in enumerate(boxes):
            b = bx['b']
            depth = x + y + z + (b[0] + b[3] + b[1] + b[4] + b[2] + b[5]) / 2
            items.append((depth, bi, pos, name, props, bx, kind))
    items.sort(key=lambda it: (it[0], it[1]))
    halos = []
    for depth, bi, pos, name, props, bx, kind in items:
        x, y, z = pos
        x0, y0, z0, x1, y1, z1 = bx['b']
        X0, Y0, Z0, X1, Y1, Z1 = x + x0, y + y0, z + z0, x + x1, y + y1, z + z1
        base = bx['c'] or colour(name)
        glow = scene.emit.get(pos, 0)
        alpha = 255
        if kind == 'glass':
            alpha = 110 if 'tinted' not in name else 200
        elif kind == 'leaves':
            alpha = 235
        for face in ('top', 'south', 'east'):
            if face == 'top' and y1 >= 1 and (x, y + 1, z) in scene.opaque:
                continue
            if face == 'south' and z1 >= 1 and (x, y, z + 1) in scene.opaque:
                continue
            if face == 'east' and x1 >= 1 and (x + 1, y, z) in scene.opaque:
                continue
            if kind == 'glass' and face != 'top':
                nb = (x, y, z + 1) if face == 'south' else (x + 1, y, z)
                if nb in scene.models and scene.models[nb][0] == name:
                    continue
            fc = bx['f'].get(face)
            col = fc or face_colour(name, face, base)
            if face == 'top':
                quad = (proj(X0, Y1, Z0), proj(X1, Y1, Z0), proj(X1, Y1, Z1), proj(X0, Y1, Z1))
                dirf = 1.0
                normal = (0, 1, 0)
                centre = ((X0 + X1) / 2, Y1 + 0.01, (Z0 + Z1) / 2)
            elif face == 'south':
                quad = (proj(X0, Y1, Z1), proj(X1, Y1, Z1), proj(X1, Y0, Z1), proj(X0, Y0, Z1))
                dirf = 0.84
                normal = (0, 0, 1)
                centre = ((X0 + X1) / 2, (Y0 + Y1) / 2, Z1 + 0.01)
            else:
                quad = (proj(X1, Y1, Z1), proj(X1, Y1, Z0), proj(X1, Y0, Z0), proj(X1, Y0, Z1))
                dirf = 0.64
                normal = (1, 0, 0)
                centre = (X1 + 0.01, (Y0 + Y1) / 2, (Z0 + Z1) / 2)
            if glow:
                f = 1.15 if not night else 1.25
                lit = 1.0
            elif night:
                nb = (math.floor(centre[0]), math.floor(centre[1]), math.floor(centre[2]))
                lvl = max(light.get(nb, 0), light.get(pos, 0) if kind != 'full' else 0)
                lit = 0.07 + 0.93 * (lvl / 15.0) ** 1.5
                f = dirf * 0.9 + 0.1
            else:
                lit = 1.0
                sun = SUN[0] * normal[0] + SUN[1] * normal[1] + SUN[2] * normal[2]
                f = dirf
                if sun > 0 and in_shadow(scene, *centre):
                    f *= 0.66
            c = shade(col, f * lit)
            if night and not glow:
                c = mix(c, (255, 196, 120), 0.18 * lit) if lit > 0.2 else mix(c, (40, 52, 90), 0.35)
            nt = n_tex if (X1 - X0) * (Z1 - Z0) >= 0.2 or face != 'top' else 1
            _draw_face(d, quad, c, alpha, name, face, nt, pos, scene, bx, night or glow)
        if glow and night:
            halos.append((proj(x + 0.5, y + 0.5, z + 0.5), glow, colour(name)))
    if night and halos:
        layer = Image.new('RGB', img.size, (0, 0, 0))
        hd = ImageDraw.Draw(layer)
        for (px, py), lvl, col in halos:
            r = scale * (0.6 + lvl / 6.0)
            hd.ellipse((px - r, py - r, px + r, py + r), fill=shade(col, 0.55))
        layer = layer.filter(ImageFilter.GaussianBlur(scale * 1.2))
        img = _add(img, layer)
        d = ImageDraw.Draw(img, 'RGBA')
    if title:
        _title(d, title, subtitle, night)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out, optimize=True)
    return out


def _add(a, b):
    from PIL import ImageChops
    return ImageChops.add(a, b)


def _background(img, night):
    w, h = img.size
    d = ImageDraw.Draw(img)
    top, bottom = ((14, 18, 38), (30, 34, 60)) if night else ((172, 205, 235), (232, 238, 242))
    for yy in range(h):
        d.line([(0, yy), (w, yy)], fill=mix(top, bottom, yy / max(1, h - 1)))
    if night:
        for i in range(w * h // 1800):
            hx = _hash('star', i)
            px, py = (hx * 7919) % w, (hx * 104729 // 13) % max(1, h // 2)
            d.point((px, py), fill=(200, 205, 230))


def _title(d, title, subtitle, night):
    col = (240, 240, 245) if night else (25, 28, 36)
    d.text((10, 6), title, fill=col, font=_font(15, True))
    if subtitle:
        d.text((10, 24), subtitle, fill=shade(col, 0.75) if night else (70, 75, 85), font=_font(10))


def _draw_face(d, quad, c, alpha, name, face, n, cell, scene, bx, flat_ao):
    a, b, cc, dd = quad
    # vertex AO for full-cube faces (the classic voxel trick: three neighbours per vertex)
    ao = None
    if not flat_ao and bx['b'] == FULL:
        ao = _face_ao(scene, cell, face)
    if n <= 1 and ao is None:
        d.polygon(quad, fill=c + (alpha,))
        return
    n = max(1, n)
    for v in range(n):
        for u in range(n):
            def pt(uu, vv):
                return (a[0] + (b[0] - a[0]) * uu + (dd[0] - a[0]) * vv,
                        a[1] + (b[1] - a[1]) * uu + (dd[1] - a[1]) * vv)
            u0, u1, v0, v1 = u / n, (u + 1) / n, v / n, (v + 1) / n
            k = pattern(name, face, u, v, n, cell)
            if ao is not None:
                cu, cv = (u + 0.5) / n, (v + 0.5) / n
                k *= (ao[0] * (1 - cu) * (1 - cv) + ao[1] * cu * (1 - cv) + ao[2] * cu * cv + ao[3] * (1 - cu) * cv)
            d.polygon([pt(u0, v0), pt(u1, v0), pt(u1, v1), pt(u0, v1)], fill=shade(c, k) + (alpha,))


def _face_ao(scene, cell, face):
    """AO at the quad's 4 corners in drawing order (a, b, c, d)."""
    x, y, z = cell
    o = scene.opaque

    def occ(p):
        return 1 if p in o else 0
    if face == 'top':
        Y = y + 1
        corners = [(-1, -1), (1, -1), (1, 1), (-1, 1)]  # (X0,Z0) (X1,Z0) (X1,Z1) (X0,Z1)
        return [vertex_ao(o, occ((x + sx, Y, z)), occ((x, Y, z + sz)), occ((x + sx, Y, z + sz))) for sx, sz in corners]
    if face == 'south':
        Z = z + 1
        corners = [(-1, 1), (1, 1), (1, -1), (-1, -1)]  # (X0,Y1) (X1,Y1) (X1,Y0) (X0,Y0)
        return [vertex_ao(o, occ((x + sx, y, Z)), occ((x, y + sy, Z)), occ((x + sx, y + sy, Z))) for sx, sy in corners]
    X = x + 1
    corners = [(1, 1), (-1, 1), (-1, -1), (1, -1)]  # (Z1,Y1) (Z0,Y1) (Z0,Y0) (Z1,Y0)
    return [vertex_ao(o, occ((X, y, z + sz)), occ((X, y + sy, z)), occ((X, y + sy, z + sz))) for sz, sy in corners]


def ground_block(st):
    """Terrain that suits the palette: netherrack for Nether builds, end stone for End ones, sand, snow…"""
    names = [n for n, _ in st.blocks.values()]
    def share(*keys):
        return sum(1 for n in names if any(k in n for k in keys)) / max(1, len(names))
    if share('blackstone', 'nether', 'crimson', 'warped', 'basalt') > 0.15:
        return 'netherrack', 'netherrack'
    if share('purpur', 'end_stone', 'end_rod') > 0.15:
        return 'end_stone', 'end_stone'
    if share('ice', 'snow') > 0.12:
        return 'snow_block', 'dirt'
    if share('sandstone', 'terracotta') > 0.2 and share('sandstone') > 0.08:
        return 'sand', 'sandstone'
    return 'grass_block', 'dirt'


def with_ground(st, ground_y, margin=4, block=None):
    """Adds a terrain plate (top at ground_y) around the footprint, never over the structure's own cells."""
    top, below = (block, 'dirt') if block else ground_block(st)
    sx, sy, sz = st.size
    blocks = dict(st.blocks)
    xs = [p[0] for p in st.blocks] or [0]
    zs = [p[2] for p in st.blocks] or [0]
    for x in range(min(xs) - margin, max(xs) + margin + 1):
        for z in range(min(zs) - margin, max(zs) + margin + 1):
            for y in range(ground_y - 1, ground_y + 1):
                if (x, y, z) not in blocks and y >= 0 - 2:
                    blocks[(x, y, z)] = (top if y == ground_y else below, {})
    return Structure(st.size, blocks, st.markers, st.title)


# ---- plan (top-down) ------------------------------------------------------------------------------


def floor_row(st):
    """The row most game blocks stand on (template floor row), else 1."""
    rows = {}
    for (x, y, z), (n, p) in st.blocks.items():
        if game_label(n):
            rows[y] = rows.get(y, 0) + 1
    return max(rows, key=lambda r: (rows[r], -r)) if rows else 1


def render_plan(st, out, cell=14, cut=None, title=None, subtitle=None):
    """Top-down plan cut just above the floor row: walls dark, floors lit, games labelled with the player side."""
    fr = floor_row(st)
    cut = fr + 1 if cut is None else cut
    sx, sy, sz = st.size
    head = 40
    legend_h = 64
    w, h = sx * cell + 2 * cell + 40, sz * cell + 2 * cell + head + legend_h
    img = Image.new('RGB', (max(w, 520), h), (236, 232, 222))
    d = ImageDraw.Draw(img, 'RGBA')
    gx, gy = cell + 30, head + cell
    small, bold = _font(max(8, cell // 2 + 1)), _font(max(8, cell // 2 + 1), True)
    tops = {}
    for (x, y, z), (n, p) in st.blocks.items():
        if y <= cut and (x, z) not in tops or (y <= cut and y > tops[(x, z)][0]):
            tops[(x, z)] = (y, n, p)
    for (x, z), (y, n, p) in tops.items():
        boxes, kind = model(n, p)
        col = colour(n)
        if y >= fr and kind == 'full' and not game_label(n):
            col = shade(col, 0.55)  # wall cut at waist height
        elif y < fr - 1:
            col = mix(shade(col, 0.8), (236, 232, 222), 0.35)
        px, py = gx + x * cell, gy + z * cell
        d.rectangle((px, py, px + cell - 1, py + cell - 1), fill=col)
        if kind == 'glass' or n.endswith('_pane'):
            d.rectangle((px, py + cell // 2 - 1, px + cell - 1, py + cell // 2 + 1), fill=(120, 170, 210))
    # grid
    for x in range(0, sx + 1):
        c = (0, 0, 0, 60) if x % 5 == 0 else (0, 0, 0, 18)
        d.line([(gx + x * cell, gy), (gx + x * cell, gy + sz * cell)], fill=c)
        if x % 5 == 0 and x < sx:
            d.text((gx + x * cell + 1, gy - cell + 1), str(x), fill=(80, 80, 80), font=small)
    for z in range(0, sz + 1):
        c = (0, 0, 0, 60) if z % 5 == 0 else (0, 0, 0, 18)
        d.line([(gx, gy + z * cell), (gx + sx * cell, gy + z * cell)], fill=c)
        if z % 5 == 0 and z < sz:
            d.text((gx - 26, gy + z * cell + 1), str(z), fill=(80, 80, 80), font=small)
    # games with player-side arrows
    families = set()
    for (x, y, z), (n, p) in sorted(st.blocks.items()):
        g = game_label(n)
        if not g or y > cut + 1:
            continue
        code, col = g
        families.add(FAMILY[code])
        px, py = gx + x * cell, gy + z * cell
        d.rectangle((px, py, px + cell - 1, py + cell - 1), fill=col, outline=(20, 20, 20))
        tc = (20, 20, 20) if sum(col) > 380 else (250, 250, 250)
        d.text((px + 1, py + 1), code, fill=tc, font=bold)
        f = p.get('facing')
        if f in FACE_DIR:
            dx, dz = FACE_DIR[f]
            cx, cy = px + cell / 2 + dx * cell * 0.75, py + cell / 2 + dz * cell * 0.75
            r = cell * 0.22
            d.polygon([(cx + dx * r, cy + dz * r), (cx - dz * r - dx * r * 0.6, cy + dx * r - dz * r * 0.6),
                       (cx + dz * r - dx * r * 0.6, cy - dx * r - dz * r * 0.6)], fill=(230, 30, 30))
    for m in st.markers:
        (x, y, z), kind, text = m[0], m[1], m[2]
        px, py = gx + x * cell + cell / 2, gy + z * cell + cell / 2
        if kind == 'npc':
            r = cell * 0.45
            d.ellipse((px - r, py - r, px + r, py + r), fill=(250, 240, 120), outline=(60, 50, 0))
            d.text((px - cell * 0.35, py - cell * 0.38), NPC_CODES.get(text, text[:2]), fill=(40, 30, 0), font=bold)
        elif kind == 'chest':
            d.rectangle((px - cell * 0.4, py - cell * 0.3, px + cell * 0.4, py + cell * 0.3), fill=(170, 120, 50),
                        outline=(60, 40, 10))
        elif kind == 'anchor':
            d.text((px - 3, py - 6), '*', fill=(200, 0, 0), font=bold)
        elif kind == 'jigsaw':
            d.rectangle((px - cell * 0.3, py - cell * 0.3, px + cell * 0.3, py + cell * 0.3), outline=(120, 0, 160), width=2)
    title = title or st.title
    d.text((10, 6), title, fill=(25, 28, 36), font=_font(15, True))
    missing = [f for f in REQUIRED_FAMILIES if f not in families]
    sub = subtitle or ('plan cut at y=%d (floor row %d) · games: %s' % (cut, fr, ', '.join(sorted(families)) or 'none'))
    d.text((10, 24), sub, fill=(70, 75, 85), font=_font(10))
    ly = gy + sz * cell + cell
    legend = '  '.join('%s %s' % (c, FAMILY[c]) for c in ('CA', 'BJ', 'RO', 'CR', 'BA', 'UT', 'PK', 'S1', 'WH', 'PL'))
    d.text((10, ly), legend, fill=(40, 40, 40), font=small)
    d.text((10, ly + 14), 'red arrow = player side (facing) · yellow = NPC (Cr croupier, LS loan shark, Bd dealer…) · '
                          'brown = loot chest · purple = jigsaw · * = anchor', fill=(40, 40, 40), font=small)
    d.text((10, ly + 28), ('MISSING families: ' + ', '.join(missing)) if missing else 'all 10 game families present',
           fill=(190, 20, 20) if missing else (20, 120, 40), font=bold)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out, optimize=True)
    return out


# ---- contact sheets -------------------------------------------------------------------------------


def contact_sheet(paths, out, title, cols=4, thumb=360):
    paths = [Path(p) for p in paths if Path(p).exists()]
    if not paths:
        return None
    ims = []
    for p in paths:
        im = Image.open(p).convert('RGB')
        im.thumbnail((thumb, thumb))
        ims.append((p, im))
    rows = (len(ims) + cols - 1) // cols
    cw, ch = thumb + 12, thumb + 30
    sheet = Image.new('RGB', (cols * cw + 12, rows * ch + 44), (22, 24, 30))
    d = ImageDraw.Draw(sheet)
    d.text((12, 10), title, fill=(240, 240, 240), font=_font(18, True))
    for i, (p, im) in enumerate(ims):
        x, y = 12 + (i % cols) * cw, 44 + (i // cols) * ch
        sheet.paste(im, (x + (thumb - im.width) // 2, y + (thumb - im.height) // 2))
        d.text((x, y + thumb + 4), p.stem, fill=(200, 200, 210), font=_font(11))
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, optimize=True)
    return out


# ---- views of one structure ------------------------------------------------------------------------


def render_views(st, out_dir, stem, views=VIEWS, scale=10, ground='auto'):
    """Writes <out_dir>/<stem>_<view>.png for every view; returns the paths."""
    fr = floor_row(st)
    g = (fr - 1 if ground == 'auto' else ground)
    if ground == 'auto' and fr <= 1:
        g = 0
    sx, sy, sz = st.size
    info = '%s  %dx%dx%d  floor row %d' % (stem, sx, sy, sz, fr)
    outs = []
    for v in views:
        path = Path(out_dir) / ('%s_%s.png' % (stem, v))
        if v in VIEW_TURNS:
            render(st, path, scale=scale, rotate=VIEW_TURNS[v], title=st.title or stem,
                   subtitle=info + '  ·  view ' + v[4:].upper(), ground=g)
        elif v == 'cutaway':
            render(st, path, scale=scale, cut=fr + 3, rotate=0, title=(st.title or stem) + ' — interior',
                   subtitle=info + '  ·  cut at y=%d' % (fr + 3), ground=g)
        elif v == 'night':
            render(st, path, scale=scale, rotate=0, night=True, title=(st.title or stem) + ' — night',
                   subtitle=info + '  ·  block light only', ground=g)
        elif v == 'plan':
            render_plan(st, path, title=(st.title or stem) + ' — plan')
        else:
            raise SystemExit('unknown view ' + v)
        outs.append(path)
        print(path)
    return outs


# ---- manifest (PackGen output) --------------------------------------------------------------------

ROT_NAMES = {'none': 0, 'clockwise_90': 1, '180': 2, 'clockwise_180': 2, 'counterclockwise_90': 3}


def _rot(r):
    if isinstance(r, int):
        return r % 4
    return ROT_NAMES.get(str(r).lower(), 0)


def template_path(template, roots):
    ns, _, path = template.partition(':') if ':' in template else ('minecraft', '', template)
    for root in roots:
        for pack in (Path(root),) + tuple(sorted(Path(root).glob('*'))):
            cand = pack / 'data' / ns / 'structure' / (path + '.nbt')
            if cand.exists():
                return cand
    raise FileNotFoundError('template %s not found under %s' % (template, ', '.join(map(str, roots))))


def manifest_roots(manifest):
    m = Path(manifest).resolve().parent
    return [m.parent / 'resourcepacks', m / 'resourcepacks', m.parent, LEGACY_PACK.parent]


def assemble(pieces, roots, title):
    """Places pieces like StructureTemplate: rotation about the piece origin (pivot 0,0,0), then offset."""
    blocks, markers = {}, []
    for pc in pieces:
        st = Structure.load(template_path(pc['template'], roots))
        turns = _rot(pc.get('rotation', 0))
        ox, oy, oz = pc.get('origin', [0, 0, 0])
        for (x, y, z), (n, p) in st.blocks.items():
            rx, rz = x, z
            for _ in range(turns):
                rx, rz = -rz, rx
                p = _rotate_props(p)
            blocks[(ox + rx, oy + y, oz + rz)] = (n, p)
        for m in st.markers:
            x, y, z = m[0]
            rx, rz = x, z
            for _ in range(turns):
                rx, rz = -rz, rx
            markers.append(((ox + rx, oy + y, oz + rz),) + tuple(m[1:]))
    if not blocks:
        return Structure((1, 1, 1), title=title)
    mn = [min(p[i] for p in blocks) for i in range(3)]
    mx = [max(p[i] for p in blocks) for i in range(3)]
    shift = lambda p: (p[0] - mn[0], p[1] - mn[1], p[2] - mn[2])
    nb = {shift(p): b for p, b in blocks.items()}
    nm = [(shift(m[0]),) + tuple(m[1:]) for m in markers]
    return Structure((mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1), nb, nm, title)


def _style_of(entry):
    if entry.get('style'):
        return entry['style']
    ident = entry.get('id') or entry.get('template', '')
    parts = ident.split(':')[-1].replace('worldgen/', '').split('/')
    if parts and parts[0] == 'casino' and len(parts) > 2:
        return parts[1]
    return parts[0] if len(parts) > 1 else 'misc'


def run_manifest(manifest, style=None, views=VIEWS, scale=10, only=None, out=None):
    out = Path(out) if out else V2
    data = json.loads(Path(manifest).read_text())
    roots = manifest_roots(manifest)
    per_style = {}
    for pc in data.get('pieces', []):
        s = _style_of(pc)
        name = (pc.get('id') or pc['template']).split('/')[-1]
        if (style and s != style) or (only and name not in only):
            continue
        st = Structure.load(template_path(pc['template'], roots), title='%s / %s' % (s, name))
        per_style.setdefault(s, []).extend(render_views(st, out / s, name, views, scale))
    for asm in data.get('assemblies', []):
        s = _style_of(asm)
        if style and s != style:
            continue
        name = 'assembly_' + asm.get('id', 'x').split('/')[-1].replace('+', '_')
        if only and name not in only:
            continue
        st = assemble(asm['pieces'], roots, '%s / %s' % (s, asm.get('id', name)))
        per_style.setdefault(s, []).extend(render_views(st, out / s, name, views, scale))
    sheets = []
    for s, paths in per_style.items():
        best = [p for p in paths if p.stem.endswith(('iso_se', 'night', 'plan', 'cutaway'))]
        sheet = contact_sheet(best or paths, out / s / '_contact_sheet.png', 'Casino style: ' + s)
        if sheet:
            sheets.append(sheet)
            print(sheet)
    overview = []
    for s, paths in per_style.items():
        overview += [p for p in paths if p.stem.startswith('assembly_') and p.stem.endswith('iso_se')][:2] or \
            [p for p in paths if p.stem.endswith('iso_se')][:2]
    if overview:
        print(contact_sheet(overview, out / '_contact_sheet.png', 'Burmaldaholic casinos v0.2 — all styles', cols=4))
    if UNKNOWN:
        print('UNKNOWN block ids (rendered magenta, add a colour or fix the id):', ', '.join(sorted(UNKNOWN)))
    return per_style


# ---- legacy + concept -----------------------------------------------------------------------------


def voxels_from_template(path):
    """Back-compat helper: {(x, y, z): block id without namespace}, size."""
    st = Structure.load(path)
    return {p: n for p, (n, _) in st.blocks.items()}, st.size


def run_legacy(views=('iso_se', 'plan'), scale=10):
    out = V2 / '_legacy'
    paths = []
    for f in sorted(TPL.glob('*.nbt')):
        st = Structure.load(f, title='v0.1 ' + f.stem)
        paths += render_views(st, out, f.stem, views, scale)
    print(contact_sheet([p for p in paths if p.stem.endswith(('iso_se', 'plan'))], out / '_contact_sheet.png',
                        'v0.1 templates (frozen) — the "box" baseline', cols=4))


def selftest():
    """Tiny procedural building through every model and view (CI-cheap check that the renderer works)."""
    import tempfile
    b = {}
    for x in range(9):
        for z in range(9):
            b[(x, 0, z)] = ('dark_oak_planks', {})
            if x in (0, 8) or z in (0, 8):
                for y in range(1, 5):
                    b[(x, y, z)] = ('white_terracotta' if (x + z) % 4 else 'stripped_dark_oak_log', {'axis': 'y'})
    for x in range(-1, 10):
        b[(x, 5, -1)] = ('deepslate_tile_stairs', {'facing': 'south', 'half': 'bottom'})
        b[(x, 5, 9)] = ('deepslate_tile_stairs', {'facing': 'north', 'half': 'bottom'})
    b[(4, 1, 8)] = b[(4, 2, 8)] = None
    b = {k: v for k, v in b.items() if v}
    b[(2, 2, 8)] = ('glass_pane', {'east': 'true', 'west': 'true'})
    b[(4, 1, 4)] = ('burmaldaholic:blackjack_table', {'facing': 'south'})
    b[(2, 1, 1)] = ('burmaldaholic:slot_machine_gold', {'facing': 'south'})
    b[(6, 1, 1)] = ('burmaldaholic:wheel_of_fortune', {'facing': 'south'})
    b[(4, 1, 6)] = ('red_carpet', {})
    b[(4, 4, 4)] = ('lantern', {'hanging': 'true'})
    b[(1, 1, 7)] = ('potted_red_tulip', {})
    b[(7, 1, 7)] = ('oak_fence', {'north': 'true'})
    b[(3, 3, 1)] = ('ochre_froglight', {})
    b[(5, 1, 3)] = ('mystery_block', {})
    st = Structure((9, 7, 9), b, [((4, 1, 2), 'npc', 'croupier')], 'selftest')
    with tempfile.TemporaryDirectory() as t:
        outs = render_views(st, t, 'selftest', VIEWS, scale=8)
        assert all(Path(p).stat().st_size > 1000 for p in outs)
        assert 'mystery_block' in UNKNOWN, 'unknown ids must be reported'
        assert floor_row(st) == 1
        rot = st.rotated(1)
        assert rot.blocks[(8 - 4, 1, 4)][1]['facing'] == 'west', 'facing rotates clockwise with the piece'
        sheet = contact_sheet(outs, Path(t) / 'sheet.png', 'selftest')
        assert Path(sheet).exists()
    UNKNOWN.discard('mystery_block')
    print('selftest ok')


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('nbt', nargs='?')
    ap.add_argument('out', nargs='?')
    ap.add_argument('--manifest')
    ap.add_argument('--style')
    ap.add_argument('--only', help='comma-separated piece names (entrance,floor_a…)')
    ap.add_argument('--all', action='store_true', help='every view')
    ap.add_argument('--views', default=None, help='comma-separated: ' + ','.join(VIEWS))
    ap.add_argument('--scale', type=int, default=10)
    ap.add_argument('--cut', type=int)
    ap.add_argument('--rotate', type=int, default=0)
    ap.add_argument('--night', action='store_true')
    ap.add_argument('--plan', action='store_true')
    ap.add_argument('--cutaway', action='store_true')
    ap.add_argument('--sheet', action='store_true', help='also write contact sheets (manifest mode always does)')
    ap.add_argument('--selftest', action='store_true')
    ap.add_argument('--out', dest='outdir', help='output directory (manifest mode; default mockups/casinos/v2)')
    a = ap.parse_args(argv)
    views = tuple(a.views.split(',')) if a.views else VIEWS
    if a.selftest:
        selftest()
        return
    if a.manifest:
        run_manifest(a.manifest, a.style, views, a.scale, set(a.only.split(',')) if a.only else None, a.outdir)
        return
    if a.nbt and a.out:
        st = Structure.load(a.nbt)
        if a.plan:
            print(render_plan(st, a.out, cut=a.cut))
        elif a.cutaway:
            print(render(st, a.out, scale=a.scale, cut=floor_row(st) + 3, rotate=a.rotate, title=st.title))
        else:
            print(render(st, a.out, scale=a.scale, cut=a.cut, rotate=a.rotate, night=a.night, title=st.title))
        return
    if a.nbt:
        st = Structure.load(a.nbt)
        render_views(st, Path(a.nbt).parent, Path(a.nbt).stem, views, a.scale)
        return
    run_legacy(views if a.views else ('iso_se', 'plan'), a.scale)
    if UNKNOWN:
        print('UNKNOWN block ids:', ', '.join(sorted(UNKNOWN)))


if __name__ == '__main__':
    main(sys.argv[1:])
