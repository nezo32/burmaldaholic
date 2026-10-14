#!/usr/bin/env python3
"""CB-4 scratch renderer: isometric views with shaped blocks (slabs, carpets, thin rods), 4 rotations,
plan cuts and a night mode. Reuses the NBT reader + colour table of docs/design/visual/render_structure.py."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import render_structure as rs  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

EXTRA = {
    'lava': (230, 110, 20), 'orange_stained_glass': (235, 140, 50), 'chiseled_polished_blackstone': (62, 56, 66),
    'crimson_stem': (110, 30, 40), 'warped_planks': (43, 104, 99), 'iron_bars': (150, 150, 150),
    'iron_chain': (70, 75, 85), 'campfire': (255, 170, 60), 'piglin_wall_head': (230, 150, 140),
    'piglin_head': (230, 150, 140), 'red_wall_banner': (170, 40, 35), 'black_wall_banner': (25, 25, 30),
    'yellow_carpet': (240, 190, 40), 'chiseled_bookshelf': (120, 90, 55), 'brewing_stand': (120, 110, 100),
    'red_candle': (190, 50, 40), 'yellow_candle': (240, 200, 70), 'purple_candle': (130, 60, 180),
    'potted_crimson_fungus': (160, 40, 40), 'potted_warped_fungus': (30, 140, 130), 'soul_lantern': (90, 220, 230),
    'structure_block': (255, 0, 255), 'magenta_carpet': (190, 70, 180), 'white_stained_glass': (240, 240, 255),
    'magenta_stained_glass': (200, 80, 200), 'amethyst_cluster': (200, 160, 250), 'large_amethyst_bud': (180, 140, 240),
    'small_amethyst_bud': (170, 130, 230), 'purple_shulker_box': (120, 60, 160), 'magenta_shulker_box': (190, 70, 180),
    'white_shulker_box': (220, 220, 225), 'dragon_head': (30, 30, 35), 'potted_allium': (170, 110, 220),
    'purple_stained_glass': (120, 50, 170), 'black_stained_glass': (28, 22, 36), 'gilded_blackstone': (110, 80, 40),
}
rs.COLOURS.update(EXTRA)
LIGHTS = rs.LIGHTS | {'lava', 'campfire', 'soul_lantern', 'crying_obsidian', 'amethyst_cluster', 'end_rod', 'shroomlight',
                      'ochre_froglight', 'pearlescent_froglight', 'lantern', 'large_amethyst_bud'}
THIN = ('iron_chain', 'end_rod', 'iron_bars', 'campfire', 'candle', 'brewing_stand', 'banner', 'potted_', 'amethyst_bud',
        'amethyst_cluster', 'lantern')


def shape(name, props):
    """(x0, y0, z0, x1, y1, z1) in block units."""
    if name.endswith('_carpet'):
        return (0, 0, 0, 1, 0.08, 1)
    if name.endswith('_slab'):
        t = props.get('type', 'bottom')
        return (0, 0.5, 0, 1, 1, 1) if t == 'top' else (0, 0, 0, 1, 1, 1) if t == 'double' else (0, 0, 0, 1, 0.5, 1)
    if name.endswith('_head'):
        return (0.25, 0, 0.25, 0.75, 0.5, 0.75)
    if 'end_rod' in name:
        f = props.get('facing', 'up')
        if f in ('east', 'west'):
            return (0, 0.4, 0.4, 1, 0.6, 0.6)
        if f in ('north', 'south'):
            return (0.4, 0.4, 0, 0.6, 0.6, 1)
        return (0.4, 0, 0.4, 0.6, 1, 0.6)
    if 'wall_banner' in name:
        return (0.1, -0.9, 0.85, 0.9, 1, 1)
    for t in THIN:
        if t in name:
            if 'lantern' in name:
                return (0.3, 0.1, 0.3, 0.7, 0.7, 0.7)
            if 'bars' in name:
                return (0.0, 0, 0.4, 1, 1, 0.6) if props.get('east') == 'true' else (0.4, 0, 0, 0.6, 1, 1)
            return (0.35, 0, 0.35, 0.65, 0.8, 0.65)
    return (0, 0, 0, 1, 1, 1)


def load(path):
    root = rs.load_nbt(path)
    pal = root['palette']
    vox = {}
    for b in root['blocks']:
        p = pal[b['state']]
        n = p['Name'].split(':', 1)[1] if p['Name'].startswith('minecraft:') else p['Name']
        if n in ('air', 'structure_void', 'cave_air', 'jigsaw'):
            continue
        vox[tuple(b['pos'])] = (n, p.get('Properties', {}))
    return vox, tuple(root['size'])


def rotate(vox, size, r):
    sx, sy, sz = size
    out = {}
    for (x, y, z), v in vox.items():
        for _ in range(r % 4):
            x, z = z, sx - 1 - x
            sx, sz = sz, sx
        sx, sy, sz = size
        out[(x, y, z)] = v
    return out, (size if r % 2 == 0 else (sz, sy, sx))


def rot_props(props, r):
    return props


def render(vox, size, out, scale=12, cut=None, title=None, night=False):
    sx, sy, sz = size
    items = [(p, v) for p, v in vox.items() if cut is None or p[1] <= cut]
    w = (sx + sz) * scale + 2 * scale
    h = (sx + sz) * scale // 2 + sy * scale + 4 * scale + 18
    bg = (8, 8, 14, 255) if night else (36, 38, 48, 255)
    img = Image.new('RGBA', (w, h), bg)
    d = ImageDraw.Draw(img)
    ox, oy = sz * scale + scale, sy * scale + 2 * scale + 18
    full = {p for p, (n, pr) in items if shape(n, pr) == (0, 0, 0, 1, 1, 1)}
    items.sort(key=lambda it: (it[0][0] + it[0][2], it[0][1]))

    def P(x, y, z):
        return (ox + (x - z) * scale, oy + (x + z) * scale / 2 - y * scale)

    for (x, y, z), (name, props) in items:
        x0, y0, z0, x1, y1, z1 = shape(name, props)
        c = rs.colour(name)
        glow = name in LIGHTS or (name.startswith('burmaldaholic:') and False)
        if night and not glow:
            c = rs.shade(c, 0.28)
        top = rs.shade(c, 1.15 if not glow else 1.3)
        left = rs.shade(c, 0.8 if not glow else 1.1)
        right = rs.shade(c, 0.64 if not glow else 1.0)
        X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
        isfull = (x, y, z) in full
        if not (isfull and (x, y + 1, z) in full):
            d.polygon([P(X0, Y1, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X0, Y1, Z1)], fill=top)
        if not (isfull and (x, y, z + 1) in full):
            d.polygon([P(X0, Y0, Z1), P(X1, Y0, Z1), P(X1, Y1, Z1), P(X0, Y1, Z1)], fill=left)
        if not (isfull and (x + 1, y, z) in full):
            d.polygon([P(X1, Y0, Z0), P(X1, Y0, Z1), P(X1, Y1, Z1), P(X1, Y1, Z0)], fill=right)
    if night:
        glowimg = Image.new('RGBA', img.size, (0, 0, 0, 0))
        g = ImageDraw.Draw(glowimg)
        for (x, y, z), (name, props) in items:
            if name in LIGHTS:
                cx, cy = P(x + 0.5, y + 0.5, z + 0.5)
                c = rs.colour(name)
                r = scale * 1.6
                g.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c + (40,))
        img = Image.alpha_composite(img, glowimg)
        d = ImageDraw.Draw(img)
    if title:
        d.text((6, 3), title, fill=(235, 235, 235))
    img.save(out)
    return out


def main():
    src = sys.argv[1]
    outdir = Path(sys.argv[2])
    outdir.mkdir(parents=True, exist_ok=True)
    views = sys.argv[3].split(',') if len(sys.argv) > 3 else ['r0', 'r2']
    vox, size = load(src)
    stem = Path(src).stem
    for v in views:
        night = 'n' in v
        cut = None
        r = 0
        for part in v.split('_'):
            if part.startswith('r'):
                r = int(part[1:])
            if part.startswith('c'):
                cut = int(part[1:])
        rv, rsize = rotate(vox, size, r)
        if 'f' in v:
            rv = {(x, rsize[1] - 1 - y, z): b for (x, y, z), b in rv.items()}
        out = outdir / f'{stem}_{v}.png'
        render(rv, rsize, out, cut=cut, night=night, title=f'{stem} {v} {size}')
        print(out)


if __name__ == '__main__':
    main()
