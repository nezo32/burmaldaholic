#!/usr/bin/env python3
"""Full-screen mockups of the extras / PvP / Casino Menu redesign (docs/design/visual/extras.md §11).

Every sprite comes from the REAL generated textures in java/src/main/resources/assets/burmaldaholic/textures (run
`cd tools && npm run gen:assets` first). Screens are composed in GUI pixels at 427 × 240 (854 × 480 at GUI scale 2),
then scaled ×2 with nearest sampling over a blurred, dimmed world, as Minecraft draws a screen. Text uses mcfont.py
(a vanilla-like 8 px font) and stands for translated strings; player faces are stand-ins for PlayerFaceRenderer.

    python3 docs/design/visual/mockups/render_extras.py        # needs Pillow; writes extras_*.png next to this file
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mcfont  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
TEX = ROOT / 'java/src/main/resources/assets/burmaldaholic/textures'
GW, GH = 427, 240  # GUI size at 854 × 480, GUI scale 2

INK = (24, 10, 40)
GOLD = (255, 214, 64)
GOLD_SHADE = (176, 112, 16)
BONE = (244, 236, 248)
BONE_SHADE = (192, 176, 220)
BONUS = (128, 255, 64)
RED = (216, 52, 64)
RED_L = (255, 110, 106)
LILAC = (214, 150, 255)
COOL = (143, 168, 200)

_cache = {}


def tex(rel):
    if rel not in _cache:
        _cache[rel] = Image.open(TEX / rel).convert('RGBA')
    return _cache[rel]


def spr(rel, frame=0, fh=None):
    """Atlas sprite `gui/sprites/<rel>.png`; animated strips return frame `frame` (square frames unless `fh`)."""
    img = tex(f'gui/sprites/{rel}.png')
    meta = TEX / f'gui/sprites/{rel}.png.mcmeta'
    anim = json.loads(meta.read_text()).get('animation') if meta.exists() else None
    if anim is not None:
        fh = fh or anim.get('height') or img.width
        n = img.height // fh
        frame %= n
        return img.crop((0, frame * fh, img.width, (frame + 1) * fh))
    return img


def cell(rel, i, w, h, row=0):
    return tex(rel).crop((i * w, row * h, (i + 1) * w, (row + 1) * h))


def paste(dst, src, x, y, alpha=1.0):
    if alpha < 1:
        src = src.copy()
        a = src.getchannel('A').point(lambda v: int(v * alpha))
        src.putalpha(a)
    dst.alpha_composite(src, (int(x), int(y)))


def nine(dst, src, x, y, w, h, border):
    """Java GUI nine-slice with tiled edges and centre (the default, `stretch_inner` false)."""
    b = border
    sw, sh = src.size
    pieces = {
        'tl': (0, 0, b, b), 'tr': (sw - b, 0, sw, b), 'bl': (0, sh - b, b, sh), 'br': (sw - b, sh - b, sw, sh),
        't': (b, 0, sw - b, b), 'b': (b, sh - b, sw - b, sh), 'l': (0, b, b, sh - b), 'r': (sw - b, b, sw, sh - b), 'c': (b, b, sw - b, sh - b),
    }
    P = {k: src.crop(v) for k, v in pieces.items()}

    def tile(img, x0, y0, ww, hh):
        if ww <= 0 or hh <= 0:
            return
        tw, th = img.size
        for yy in range(0, hh, th):
            for xx in range(0, ww, tw):
                piece = img.crop((0, 0, min(tw, ww - xx), min(th, hh - yy)))
                dst.alpha_composite(piece, (x0 + xx, y0 + yy))

    tile(P['c'], x + b, y + b, w - 2 * b, h - 2 * b)
    tile(P['t'], x + b, y, w - 2 * b, b)
    tile(P['b'], x + b, y + h - b, w - 2 * b, b)
    tile(P['l'], x, y + b, b, h - 2 * b)
    tile(P['r'], x + w - b, y + b, b, h - 2 * b)
    dst.alpha_composite(P['tl'], (x, y))
    dst.alpha_composite(P['tr'], (x + w - b, y))
    dst.alpha_composite(P['bl'], (x, y + h - b))
    dst.alpha_composite(P['br'], (x + w - b, y + h - b))


def nine_sprite(dst, rel, x, y, w, h):
    meta = json.loads((TEX / f'gui/sprites/{rel}.png.mcmeta').read_text())
    nine(dst, spr(rel), x, y, w, h, meta['gui']['scaling']['border'])


def text(dst, x, y, s, col=BONE, **kw):
    return mcfont.draw(dst, x, y, s, col, **kw)


def ctext(dst, cx, y, s, col=BONE, **kw):
    return mcfont.center(dst, cx, y, s, col, **kw)


def rtext(dst, rx, y, s, col=BONE, **kw):
    return mcfont.right(dst, rx, y, s, col, **kw)


def button(dst, x, y, w, label, family='', state='', h=20, icon=None):
    """Core CasinoButton (widget/casino_button[_primary|_danger][_highlighted|_disabled])."""
    name = 'core/widget/casino_button' + (f'_{family}' if family else '') + (f'_{state}' if state else '')
    nine_sprite(dst, name, x, y, w, h)
    if family == 'primary' and state != 'disabled':
        col = INK
        shadow = False
    elif state == 'disabled':
        col, shadow = BONE_SHADE, True
    elif state == 'highlighted' and family == '':
        col, shadow = GOLD, True
    else:
        col, shadow = BONE, True
    tw = mcfont.width(label)
    iw = 0 if icon is None else icon.width + 3
    tx = x + (w - tw - iw) // 2 + iw
    if icon is not None:
        paste(dst, icon, tx - iw, y + (h - icon.height) // 2)
    text(dst, tx, y + (h - 8) // 2 + 1, label, col, shadow=shadow)


def world_bg(seed=1, dim=150, blur=7):
    """A blurred, dimmed stand-in for the world behind a screen (sky, hills, grass), 854 × 480."""
    W, H = GW * 2, GH * 2
    img = Image.new('RGBA', (W, H))
    px = img.load()
    rnd = random.Random(seed)
    hills = [rnd.random() * 6 for _ in range(4)]
    for y in range(H):
        for x in range(W):
            t = y / H
            col = (int(120 + 60 * (1 - t)), int(168 + 50 * (1 - t)), 255)
            h1 = 250 + 30 * math.sin(x * 0.01 + hills[0]) + 12 * math.sin(x * 0.037 + hills[1])
            h2 = 300 + 18 * math.sin(x * 0.02 + hills[2])
            if y > h1:
                col = (84, 150, 70)
            if y > h2:
                col = (72, 128, 56) if (x // 16 + y // 16) % 2 else (78, 136, 60)
            if y > 400:
                col = (110, 80, 50)
            px[x, y] = col + (255,)
    img = img.filter(ImageFilter.GaussianBlur(blur))
    dark = Image.new('RGBA', (W, H), (16, 10, 24, dim))
    img.alpha_composite(dark)
    return img


def finish(gui, name, seed=1, **bg_kw):
    big = gui.resize((GW * 2, GH * 2), Image.NEAREST)
    bg = world_bg(seed, **bg_kw)
    bg.alpha_composite(big)
    bg.convert('RGB').save(HERE / f'extras_{name}.png')
    print('wrote', HERE / f'extras_{name}.png')


def new_gui():
    return Image.new('RGBA', (GW, GH), (0, 0, 0, 0))


# ---- stand-in faces (PlayerFaceRenderer draws real skins in game) -------------------------------------------------
FACES = {
    'steve': (['HHHHHHHH', 'HHHHHHHH', 'HSSSSSSH', 'SSSSSSSS', 'SWBSSBWS', 'SSSNNSSS', 'SSMMMMSS', 'SSSSSSSS'],
              {'H': (43, 29, 14), 'S': (185, 138, 110), 'W': (255, 255, 255), 'B': (74, 58, 176), 'N': (138, 90, 64), 'M': (106, 58, 42)}),
    'alex': (['HHHHHHHH', 'HHHHHHHH', 'HHSSSSSH', 'HSSSSSSS', 'HSWGSGWS', 'HSSSSSSS', 'HSSMMSSS', 'HSSSSSSS'],
             {'H': (224, 138, 56), 'S': (240, 200, 160), 'W': (255, 255, 255), 'G': (40, 150, 70), 'M': (200, 110, 100)}),
    'rival': (['HHHHHHHH', 'HHHHHHHH', 'HSSSSSSH', 'SSSSSSSS', 'SKRSSRKS', 'SSSNNSSS', 'SSMMMMSS', 'SMSSSSMS'],
              {'H': (20, 20, 26), 'S': (150, 104, 80), 'K': (255, 255, 255), 'R': (160, 20, 30), 'N': (110, 70, 50), 'M': (60, 30, 24)}),
    'creeper': (['GGgGGGGg', 'GgGGgGGG', 'GKKGGKKG', 'GKKGgKKG', 'gGGKKGGG', 'GGKKKKGg', 'GGKGGKGG', 'GgGGGGgG'],
                {'G': (80, 190, 70), 'g': (50, 150, 50), 'K': (16, 16, 16)}),
}


def face(name, size=16):
    rows, pal = FACES[name]
    img = Image.new('RGBA', (8, 8))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            img.putpixel((x, y), pal[ch] + (255,))
    return img.resize((size, size), Image.NEAREST)


def chip_counter(dst, x, y, amount, golden=False):
    w = mcfont.width(amount) + 26
    nine_sprite(dst, 'core/hud/chip_counter' + ('_golden' if golden else ''), x, y, w, 16)
    paste(dst, spr('core/hud/chip_icon', 1), x + 3, y + 2)
    text(dst, x + 18, y + 4, amount, GOLD)
    return w


def sparkles(dst, pts, frame=0):
    for i, (x, y) in enumerate(pts):
        paste(dst, spr('core/fx/sparkle', frame + i), x - 3, y - 3)


def panel_scene(dst, backdrop, frame_sprite, marquee=None, title=None, banner=None, title_w=150):
    X, Y, W, H = 13, 0, 400, 240
    paste(dst, tex(backdrop), X, Y)
    nine_sprite(dst, frame_sprite, X, Y, W, H)
    if marquee:
        m = spr(marquee, 1)
        for mx in range(X + 12, X + W - 12, m.width):
            paste(dst, m.crop((0, 0, min(m.width, X + W - 12 - mx), m.height)), mx, 12)
    if title:
        bx = X + W // 2 - title_w // 2
        nine_sprite(dst, banner, bx, 3, title_w, 22)
        ctext(dst, X + W // 2, 10, title, GOLD)
    return X, Y, W, H


# =====================================================================================================================
# 1. Coin Flip — mid-air
# =====================================================================================================================
def coin_flip():
    g = new_gui()
    X, Y, W, H = panel_scene(g, 'gui/extras/coin_backdrop.png', 'burmaldaholic/extras/coin_frame', 'burmaldaholic/extras/coin_marquee',
                             'Lucky Coin', 'burmaldaholic/extras/coin_banner')
    cx = X + W // 2
    # landing pad + the shrinking shadow (the coin is near its apex)
    pad = spr('burmaldaholic/extras/coin_pad')
    paste(g, pad, cx - pad.width // 2, 148)
    sh = spr('burmaldaholic/extras/coin_shadow').resize((28, 7), Image.NEAREST)
    paste(g, sh, cx - 14, 164, 0.55)
    # coin: spin frame 2 near the apex, 2 ghost frames trailing below (motion)
    for f, dy, a in [(0, 36, 0.14), (1, 18, 0.28)]:
        paste(g, cell('gui/extras/coin_spin.png', f, 64, 64), cx - 32, 44 + dy, a)
    paste(g, cell('gui/extras/coin_spin.png', 2, 64, 64), cx - 32, 40)
    sparkles(g, [(cx - 40, 48), (cx + 38, 62), (cx + 30, 34), (cx - 30, 92)], 1)
    ctext(g, cx, 194, 'The coin is in the air…', LILAC)
    # left: your call
    lx = X + 22
    nine_sprite(g, 'core/panel/inset', lx - 4, 38, 104, 128)
    text(g, lx, 44, 'Your call', GOLD)
    heads = cell('gui/extras/coin_mini.png', 0, 14, 14)
    tails = cell('gui/extras/coin_mini.png', 1, 14, 14)
    button(g, lx, 58, 96, 'Heads', 'primary', 'highlighted', icon=heads)
    button(g, lx, 82, 96, 'Tails', icon=tails)
    text(g, lx, 110, 'Bet', BONE_SHADE)
    nine_sprite(g, 'core/panel/inset', lx, 120, 96, 18)
    paste(g, spr('core/fx/chip_25'), lx + 4, 125)
    text(g, lx + 16, 125, '250', GOLD)
    button(g, lx, 142, 30, '-')
    button(g, lx + 33, 142, 30, '+')
    button(g, lx + 66, 142, 30, 'Max')
    # right: history (chain pips) and streak
    rx = X + W - 124
    nine_sprite(g, 'core/panel/inset', rx - 4, 38, 106, 128)
    text(g, rx, 44, 'Last flips', GOLD)
    hist = [2, 5, 4, 2, 3, 4, 2, 2, 5, 1]
    for i, p in enumerate(hist):
        paste(g, cell('gui/extras/chain_pips.png', p, 16, 16), rx + (i % 5) * 19, 58 + (i // 5) * 19)
    text(g, rx, 104, 'Heads 6 · Tails 4', BONE)
    paste(g, spr('core/hud/flame_1'), rx, 118)
    text(g, rx + 11, 118, 'Lucky streak ×3', BONUS)
    text(g, rx, 134, 'Pays 1.96×', BONE_SHADE)
    text(g, rx, 146, 'Win: 490', BONE_SHADE)
    # bottom: balance + controls
    chip_counter(g, X + 16, H - 30, '12,250')
    button(g, cx - 50, H - 34, 100, 'Flip again', 'primary', 'disabled')
    button(g, X + W - 76, H - 34, 60, 'Leave')
    finish(g, 'coin_flip', 1)


# =====================================================================================================================
# 2. Wheel of Fortune — landing (stop beat)
# =====================================================================================================================
SEG = 'XBMBDBMBHBDBMBTBMBDBHBMBDBE' + 'BMBDBHBMBTBMBDBHBMBTBCMHDMB'
KIND = {'B': ('Bust', '×0'), 'C': ('Creeper', '×0'), 'H': ('Half back', '×0.5'), 'M': ('Money back', '×1'), 'D': ('Double', '×2'),
        'T': ('Triple', '×3'), 'E': ('Emerald', '×5'), 'X': ('Diamond', '×10')}
ORDER = 'BCHMDTEX'


def wheel_landing():
    g = new_gui()
    X, Y, W, H = panel_scene(g, 'gui/extras/wheel_backdrop.png', 'burmaldaholic/extras/wheel_frame', None,
                             'Wheel of Fortune', 'burmaldaholic/extras/wheel_banner', 170)
    target = 14  # a Triple
    n = len(SEG)
    s = 360 / n
    face = tex('gui/extras/wheel_face.png').copy()
    # dim every wedge except the landed one (65 %), brighten the landed wedge (white overlay 35 %)
    fp = face.load()
    R = face.width / 2
    for y in range(face.height):
        for x in range(face.width):
            p = fp[x, y]
            if not p[3]:
                continue
            dx, dy = x + 0.5 - R, y + 0.5 - R
            if math.hypot(dx, dy) < 22:
                continue
            phi = (math.degrees(math.atan2(dx, -dy)) + 360) % 360
            i = int(((phi + s / 2) % 360) // s) % n
            if i == target:
                fp[x, y] = tuple(int(c + (255 - c) * 0.3) for c in p[:3]) + (255,)
            else:
                fp[x, y] = tuple(int(c * 0.62) for c in p[:3]) + (255,)
    settle = 1.2  # rest jitter inside the wedge (seeded in game)
    rot = face.rotate(target * s + settle, resample=Image.NEAREST)
    wx, wy = X + 22, 32
    stand = spr('burmaldaholic/extras/wheel_stand')
    paste(g, stand, wx + 92 - stand.width // 2, wy + 168)
    rim = tex('gui/extras/wheel_rim.png')
    paste(g, rot, wx + 12, wy + 12)
    paste(g, rim, wx, wy)
    bulbs = [cell('gui/extras/wheel_bulbs.png', i, 8, 8) for i in range(3)]
    for i in range(24):
        a = math.radians(i * 15 + 7.5)
        bx = round(92 + math.sin(a) * 85.5 - 0.5)
        by = round(92 - math.cos(a) * 85.5 - 0.5)
        paste(g, bulbs[2 if i % 2 == 0 else 1], wx + bx - 4, wy + by - 4)
    hub = spr('burmaldaholic/extras/wheel_hub', 57)
    paste(g, hub, wx + 92 - 14, wy + 92 - 14)
    flap = spr('burmaldaholic/extras/wheel_flapper').rotate(-6, resample=Image.NEAREST, expand=True)
    paste(g, flap, wx + 92 - flap.width // 2, wy - 2)
    # pop-out: the landed icon at 2× on a gold burst, the segment name under it
    pop = spr('burmaldaholic/extras/wheel_pop')
    px, py = wx + 92 - 24, wy + 12
    paste(g, pop, px, py)
    paste(g, cell('gui/extras/wheel_icons_40.png', ORDER.index('T'), 40, 40), px + 4, py + 4)
    lw = mcfont.width('Triple ×3') + 16
    nine_sprite(g, 'burmaldaholic/pvp/mode_banner', wx + 92 - lw // 2, py + 46, lw, 16)
    ctext(g, wx + 92, py + 50, 'Triple ×3', GOLD)
    sparkles(g, [(px - 4, py + 6), (px + 52, py + 14), (px + 46, py - 2)], 2)
    # right: legend, bet, result
    rx = X + 212
    nine_sprite(g, 'core/panel/inset', rx - 4, 32, 180, 110)
    text(g, rx, 37, 'Segments', GOLD)
    counts = {c: SEG.count(c) for c in ORDER}
    for i, c in enumerate(ORDER):
        yy = 48 + i * 11
        paste(g, cell('gui/extras/wheel_icons_8.png', i, 8, 8), rx, yy)
        name, mult = KIND[c]
        col = GOLD if c == 'T' else BONE
        text(g, rx + 12, yy, f'{name} {mult}', col)
        rtext(g, rx + 172, yy, f'{counts[c]}/54', BONE_SHADE)
    nine_sprite(g, 'core/panel/inset', rx - 4, 146, 180, 34)
    text(g, rx, 151, 'Bet', BONE_SHADE)
    paste(g, spr('core/fx/chip_100'), rx + 22, 150)
    text(g, rx + 34, 151, '100', GOLD)
    text(g, rx, 166, 'You win', BONE)
    text(g, rx + 42, 166, '+300', BONUS, bold=True)
    button(g, rx - 4, 186, 58, 'Bet -')
    button(g, rx + 57, 186, 58, 'Bet +')
    button(g, rx + 118, 186, 58, 'Max')
    button(g, rx - 4, 210, 116, 'Spin', 'primary')
    button(g, rx + 118, 210, 58, 'Leave')
    chip_counter(g, X + 16, H - 30, '12,700')
    finish(g, 'wheel_landing', 2)


# =====================================================================================================================
# 3. Plinko — a drop in progress
# =====================================================================================================================
MED = [33, 11, 4, 2, 1, 0.6, 0.3, 0.6, 1, 2, 4, 11, 33]


def tier(m):
    return 0 if m < 1 else 1 if m == 1 else 2 if m <= 3 else 3 if m <= 33 else 4


def fmt(m):
    return str(int(m)) if m == int(m) else str(m)


def plinko_drop():
    g = new_gui()
    X, Y, W, H = panel_scene(g, 'gui/extras/plinko_backdrop.png', 'burmaldaholic/extras/plinko_frame', None,
                             'Plinko', 'burmaldaholic/extras/plinko_banner', 110)
    bx, by = X + 14, 28
    board = tex('gui/extras/plinko_board.png')
    paste(g, board, bx, by)
    cx, pitchx, pitchy, row0, biny = 136, 20, 12, 30, 176
    path = [1, 0, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0]  # rights / lefts (server path)
    row = 7
    rights = [sum(path[:r]) for r in range(13)]
    pegs = [cell('gui/extras/plinko_peg.png', i, 7, 7) for i in range(3)]
    for r in range(12):
        for j in range(r + 1):
            x = cx + (j - r / 2) * pitchx
            y = row0 + r * pitchy
            st = 0
            if r == row - 1 and j == rights[r]:
                st = 1
            elif r in (row - 2, row - 3) and j == rights[r]:
                st = 2
            paste(g, pegs[st], bx + x - 3, by + y - 3)
    bins = tex('gui/extras/plinko_bins.png')
    for k, m in enumerate(MED):
        t = tier(m)
        cap = bins.crop((t * 18, 0, t * 18 + 18, 14))
        x = bx + cx + (k - 6) * pitchx - 9
        paste(g, cap, x, by + biny)
        mcfont.center(g, x + 9, by + biny + 4, fmt(m), BONE)
    chute = cell('gui/extras/plinko_chute.png', 1, 28, 16)
    paste(g, chute, bx + cx - 14, by + 4)

    def ball_at(r, q):
        x0 = cx + (rights[r] - r / 2) * pitchx
        x1 = cx + (rights[r + 1] - (r + 1) / 2) * pitchx
        e = -(math.cos(math.pi * q) - 1) / 2
        hop = 3.5 * 0.93 ** r
        return x0 + (x1 - x0) * e, row0 + r * pitchy + pitchy * q * q - hop * 4 * q * (1 - q) - 4

    balls = [cell('gui/extras/plinko_ball.png', i, 9, 9) for i in range(4)]
    for k, (q, a) in enumerate([(-0.9, 0.1), (-0.6, 0.25), (-0.3, 0.45)]):
        rr = row - 1 if q < 0 else row
        x, y = ball_at(rr, 1 + q)
        paste(g, balls[k], bx + x - 4, by + y - 4, a)
    x, y = ball_at(row - 1, 0.35)
    paste(g, balls[2], bx + x - 4, by + y - 4)
    # right column
    rx = X + 296
    nine_sprite(g, 'core/panel/inset', rx - 4, 28, 96, 66)
    text(g, rx, 33, 'Risk', GOLD)
    button(g, rx - 2, 44, 92, 'Low', h=14)
    button(g, rx - 2, 60, 92, 'Medium', '', 'highlighted', h=14)
    button(g, rx - 2, 76, 92, 'High', h=14)
    nine_sprite(g, 'core/panel/inset', rx - 4, 98, 96, 30)
    text(g, rx, 103, 'Bet', BONE_SHADE)
    paste(g, spr('core/fx/chip_25'), rx + 22, 102)
    text(g, rx + 34, 103, '50', GOLD)
    text(g, rx, 115, 'Top: ×33', BONE_SHADE)
    button(g, rx - 4, 132, 46, '-')
    button(g, rx + 46, 132, 46, '+')
    button(g, rx - 4, 156, 96, 'Drop', 'primary', 'disabled')
    text(g, rx, 182, 'Last balls', GOLD)
    for i, m in enumerate([2, 0.6, 11, 1]):
        t = tier(m)
        cap = bins.crop((t * 18, 14, t * 18 + 18, 28))
        paste(g, cap, rx + i * 22, 194)
        mcfont.center(g, rx + i * 22 + 9, 198, fmt(m), (255, 255, 255))
    chip_counter(g, rx - 4, 214, '8,940')
    finish(g, 'plinko_drop', 3)


# =====================================================================================================================
# 4. Scratch Cards — a basic ticket, half scratched
# =====================================================================================================================
SYM = ['coal', 'iron', 'gold', 'emerald', 'diamond', 'star', 'creeper', 'foot', 'charred']
BASIC = {0: '10', 1: '20', 2: '50', 3: '100', 4: '500', 5: '2,500'}


def foil_partial(dst, foil, edges, x, y, w, h, erased):
    """Draws foil on the sub-tiles still covered, with the per-theme scratch-edge autotile on torn borders."""
    nx, ny = w // 4, h // 4

    def on(a, b):
        return 0 <= a < nx and 0 <= b < ny and not erased(a, b)

    for b in range(ny):
        for a in range(nx):
            if not on(a, b):
                continue
            m = (0 if on(a, b - 1) else 1) | (0 if on(a + 1, b) else 2) | (0 if on(a, b + 1) else 4) | (0 if on(a - 1, b) else 8)
            tile = foil.crop((a * 4, b * 4, a * 4 + 4, b * 4 + 4))
            e = edges.crop((m * 4, 0, m * 4 + 4, 4))
            if m:
                ep = e.load()
                tp = tile.load()
                for j in range(4):
                    for i in range(4):
                        if (i in (0, 3) and j in (0, 3)) and ep[i, j][3] == 0:
                            tp[i, j] = (0, 0, 0, 0)
            dst.alpha_composite(tile, (x + a * 4, y + b * 4))
            dst.alpha_composite(e, (x + a * 4, y + b * 4))


def scratch_half():
    g = new_gui()
    X, Y, W, H = panel_scene(g, 'gui/extras/scratch_backdrop.png', 'burmaldaholic/extras/scratch_frame', None,
                             'Scratch Cards', 'burmaldaholic/extras/scratch_banner', 150)
    tx, ty = X + 20, 30
    nine_sprite(g, 'burmaldaholic/extras/ticket_shadow', tx + 3, ty + 4, 212, 196)
    paste(g, tex('gui/extras/scratch_ticket_basic.png'), tx, ty)
    ctext(g, tx + 106, ty + 15, 'LUCKY MINER', (14, 90, 58), shadow=False, bold=True)
    foil = tex('gui/extras/scratch_foil.png').crop((0, 0, 60, 44))
    edges = tex('gui/extras/scratch_edges.png').crop((0, 0, 64, 4))
    scuff = tex('gui/extras/scratch_scuff.png')
    cells = [('open', 2), ('open', 4), ('foil', None), ('half', 2), ('foil', None), ('open', 0), ('open', 3), ('open', 2), ('foil', None)]
    for i, (st, sym) in enumerate(cells):
        x = tx + 12 + (i % 3) * 64
        y = ty + 36 + (i // 3) * 48
        if st in ('open', 'half'):
            paste(g, cell('gui/extras/scratch_symbols_40.png', sym, 40, 40), x + 10, y - 3)
            ctext(g, x + 30, y + 35, BASIC[sym], (58, 32, 16), shadow=False)
            paste(g, scuff, x, y)
        if st == 'foil':
            paste(g, foil, x, y)
            paste(g, spr('burmaldaholic/extras/scratch_foil_shimmer', 3 + i), x, y)
        if st == 'half':
            rnd_e = random.Random(21)
            noise = [[rnd_e.random() * 2.2 for _ in range(15)] for _ in range(11)]

            def erased(a, b):
                return a + 1.5 * b + noise[b][a] < 16
            foil_partial(g, foil, edges, x, y, 60, 44, erased)
    # flakes + scraper at the cursor on the half cell
    fl = tex('gui/extras/flakes.png')
    rnd = random.Random(7)
    hx, hy = tx + 12 + 0 * 64, ty + 36 + 48
    for k in range(12):
        paste(g, fl.crop(((k % 4) * 4, 0, (k % 4) * 4 + 4, 4)), hx + 30 + rnd.randint(-8, 26), hy + 18 + rnd.randint(-12, 22))
    paste(g, spr('burmaldaholic/extras/scraper'), hx + 30, hy + 12)
    ctext(g, tx + 106, ty + 180, 'Drag to scratch, or click a cell', BONE, shadow=True)
    # right column: prize table and actions
    rx = X + 244
    nine_sprite(g, 'core/panel/casino', rx - 4, 30, 148, 126)
    text(g, rx + 2, 36, 'Match 3 to win', GOLD)
    for k in range(6):
        yy = 48 + k * 15
        paste(g, cell('gui/extras/scratch_symbols_20.png', 5 - k, 20, 20), rx - 2, yy - 5)
        text(g, rx + 22, yy + 1, ['2,500', '500', '100', '50', '20', '10'][k], GOLD if k == 0 else BONE)
        rtext(g, rx + 140, yy + 1, ['1 in 10,000', '1 in 500', '1 in 100', '1 in 33', '1 in 10', '1 in 5'][k], BONE_SHADE)
    paste(g, cell('gui/extras/scratch_symbols_20.png', 6, 20, 20), rx - 2, 135)
    text(g, rx + 22, 141, 'Three creepers bite!', (111, 168, 106))
    button(g, rx - 4, 160, 73, 'Basic', '', 'highlighted')
    button(g, rx + 71, 160, 73, 'Golden')
    button(g, rx - 4, 184, 148, 'Scratch all', 'primary')
    button(g, rx - 4, 208, 73, 'New (10)')
    button(g, rx + 71, 208, 73, 'Leave')
    chip_counter(g, X + 16, H - 14 - 6, '3,120')
    finish(g, 'scratch_half', 4)


# =====================================================================================================================
# 5. PvP — Scratch Showdown grudge match, 2 players + 1 bot
# =====================================================================================================================
def plate(g, kind, x, y, w, name, score, head, badges=()):
    nine_sprite(g, f'burmaldaholic/pvp/plate_{kind}', x, y, w, 30)
    hf = spr(f'burmaldaholic/pvp/head_frame_{"bot" if kind == "bot" else "rival" if kind in ("rival", "grudge") else "you"}')
    paste(g, hf, x + 5, y + 5)
    paste(g, face(head), x + 7, y + 7)
    text(g, x + 29, y + 6, name, GOLD if kind == 'you' else BONE)
    text(g, x + 29, y + 17, score, BONE_SHADE)
    bx = x + w - 6
    for b in badges:
        bx -= b.width + 2
        paste(g, b, bx, y + 16)


def showdown_card(g, x, y, cells):
    paste(g, tex('gui/extras/scratch_card_showdown.png'), x, y)
    fo = tex('gui/extras/scratch_foil_showdown.png')
    for i, st in enumerate(cells):
        cx0 = x + 6 + (i % 3) * 26
        cy0 = y + 16 + (i // 3) * 26
        if st == 'foil':
            paste(g, fo, cx0, cy0)
            paste(g, spr('burmaldaholic/extras/scratch_foil_shimmer_small', i + 2), cx0, cy0)
        elif st == 'final':
            paste(g, spr('burmaldaholic/extras/foil_final', 2), cx0, cy0)
            ctext(g, cx0 + 12, cy0 + 8, '?', GOLD, bold=True)
        elif st == 'charred':
            paste(g, spr('burmaldaholic/extras/charred', 1), cx0, cy0)
        elif st == 'current':
            paste(g, fo, cx0, cy0)
            paste(g, spr('burmaldaholic/extras/scratch_foil_shimmer_small', 5), cx0, cy0)
            nine(g, spr('burmaldaholic/extras/trio_frame'), cx0 - 2, cy0 - 2, 28, 28, 4)
        else:
            s, trio = st
            if trio:
                nine(g, spr('burmaldaholic/extras/trio_frame'), cx0 - 1, cy0 - 1, 26, 26, 4)
            paste(g, cell('gui/extras/scratch_symbols_20.png', SYM.index(s), 20, 20), cx0 + 2, cy0 + 2)


def pvp_match():
    g = new_gui()
    X, Y, W, H = 13, 0, 400, 240
    paste(g, tex('gui/pvp/arena_backdrop_grudge.png'), X, Y)
    nine_sprite(g, 'burmaldaholic/pvp/grudge_frame', X, Y, W, H)
    cx = X + W // 2
    text(g, X + 16, 15, 'Scratch Showdown', GOLD)
    rtext(g, X + W - 16, 15, 'Step 4/9', GOLD)
    # grudge banner (hold phase): the torn halves have clashed in the centre
    nine_sprite(g, 'burmaldaholic/pvp/grudge_left', cx - 100, 22, 102, 28)
    nine_sprite(g, 'burmaldaholic/pvp/grudge_right', cx - 2, 22, 102, 28)
    ctext(g, cx, 32, 'GRUDGE MATCH', BONE, bold=True, outline=(90, 0, 0))
    sparkles(g, [(cx - 4, 24), (cx + 6, 52)], 0)
    # seats: you (left), the bot (centre), the grudge rival (right); plates over their cards
    cols = [X + 22, X + 157, X + 292]
    you_cells = [('coal', False), ('gold', True), 'current', ('gold', True), 'foil', ('gold', True), 'foil', 'foil', 'final']
    bot_cells = [('iron', False), ('emerald', False), 'current', 'foil', ('creeper', False), 'foil', 'foil', 'foil', 'final']
    rival_cells = [('diamond', False), 'charred', 'current', ('iron', False), 'foil', 'foil', ('foot', False), 'foil', 'final']
    rec = Image.new('RGBA', (24, 10))
    nine(rec, spr('burmaldaholic/pvp/record_chip_trail'), 0, 0, 24, 10, 3)
    text(rec, 3, 1, '3-5', BONE, shadow=False)
    claw = tex('gui/pvp/badges.png').crop((16, 0, 32, 16))
    py = 72
    plate(g, 'you', cols[0] - 12, py, 110, 'You', '320 ×2', 'steve')
    nine_sprite(g, 'burmaldaholic/pvp/all_in', cols[0] + 52, py + 15, 40, 12)
    text(g, cols[0] + 58, py + 17, 'ALL-IN', BONE, shadow=False)
    paste(g, spr('burmaldaholic/pvp/flame_red', 3), cols[0] + 36, py + 3)
    plate(g, 'bot', cols[1] - 12, py, 110, 'Creeper42', '150', 'creeper', [spr('burmaldaholic/pvp/bot_hard')])
    plate(g, 'grudge', cols[2] - 12, py, 110, 'Notch', '210', 'rival', [rec])
    paste(g, claw, cols[2] - 12, py - 6)
    for x, cells_ in zip(cols, [you_cells, bot_cells, rival_cells]):
        showdown_card(g, x, py + 31, cells_)
    # ready ticks on the plates of players who pressed Scratch!
    chk = tex('gui/pvp/badges.png').crop((64, 0, 80, 16))
    paste(g, chk, cols[0] + 84, py - 12)
    paste(g, chk, cols[1] + 84, py - 12)
    # pot (left of centre) and the rival's taunt bubble (right)
    paste(g, spr('burmaldaholic/pvp/pot_glow'), cx - 82, 54)
    paste(g, cell('gui/pvp/pot_chips.png', 1, 64, 36), cx - 78, 36)
    nine_sprite(g, 'burmaldaholic/pvp/pot_plaque', cx - 10, 52, 64, 18)
    ctext(g, cx + 22, 57, 'Pot 600', GOLD)
    bw = mcfont.width("It's rigged!") + 30
    bx0, by0 = cols[2] - 4, 44
    nine_sprite(g, 'burmaldaholic/pvp/bubble_cheeky', bx0, by0, bw, 22)
    paste(g, spr('burmaldaholic/pvp/bubble_tail_cheeky'), bx0 + 12, by0 + 21)
    paste(g, cell('gui/pvp/taunt_icons.png', 3, 16, 16), bx0 + 5, by0 + 3)
    text(g, bx0 + 24, by0 + 7, "It's rigged!", (140, 24, 52), shadow=False)
    # controls
    button(g, cx - 110, H - 33, 90, 'Scratch!', 'primary', 'highlighted')
    button(g, cx - 16, H - 33, 60, 'Taunt…')
    button(g, cx + 48, H - 33, 60, 'Close')
    finish(g, 'pvp_match', 5)


# =====================================================================================================================
# 6. PvP — result (podium, winner banner, standings)
# =====================================================================================================================
def pvp_result():
    g = new_gui()
    X, Y, W, H = 13, 0, 400, 240
    paste(g, tex('gui/pvp/arena_backdrop.png'), X, Y)
    nine_sprite(g, 'burmaldaholic/pvp/frame', X, Y, W, H)
    cx = X + W // 2
    rays = spr('core/fx/rays').resize((128, 128), Image.NEAREST)
    paste(g, rays, cx - 64 - 70, -8, 0.7)
    # winner banner
    nine_sprite(g, 'burmaldaholic/pvp/winner_banner', cx - 70 - 70, 14, 140, 30)
    ctext(g, cx - 70, 24, 'Steve WINS!', GOLD, bold=True)
    # podium with heads (1st centre)
    pod = tex('gui/pvp/podium.png')
    px0, py0 = cx - 70 - 100, 118
    paste(g, pod, px0, py0)
    for (name, dx, top, place) in [('steve', 100, 0, '1'), ('rival', 32, 20, '2'), ('creeper', 168, 32, '3')]:
        paste(g, face(name, 24), px0 + dx - 12, py0 + top - 26)
        ctext(g, px0 + dx, py0 + top + 17, place, BONE, bold=True)
    paste(g, spr('burmaldaholic/pvp/crown'), px0 + 100 - 8, py0 - 36)
    conf = tex('gui/core/fx/confetti.png')
    rnd = random.Random(11)
    for k in range(34):
        f = rnd.randrange(3)
        paste(g, conf.crop((f * 8, 0, f * 8 + 8, 8)).rotate(rnd.choice([0, 90]), expand=True), X + 16 + rnd.randrange(250), 46 + rnd.randrange(66))
    # standings: revealed plaques with medals
    sx = X + 262
    text(g, sx, 16, 'Final standings', GOLD)
    rows = [('gold', 1, 'Steve', '3,400'), ('face', 2, 'Notch', '2,150'), ('face', 3, 'Creeper42', '20')]
    medals = tex('gui/pvp/rank_medals.png')
    for i, (kind, place, name, pts) in enumerate(rows):
        y = 30 + i * 24
        nine_sprite(g, f'burmaldaholic/pvp/plaque_{kind}', sx - 4, y, 128, 20)
        paste(g, medals.crop(((place - 1) * 12, 0, place * 12, 16)), sx, y + 2)
        text(g, sx + 16, y + 6, name, GOLD if place == 1 else BONE)
        rtext(g, sx + 118, y + 6, pts, BONE_SHADE)
    paste(g, spr('burmaldaholic/pvp/bot_hard'), sx + 16 + mcfont.width('Creeper42') + 4, y + 5)
    nine_sprite(g, 'core/panel/inset', sx - 4, 106, 128, 62)
    text(g, sx, 111, 'You take the pot', BONE)
    text(g, sx, 123, '+388', BONUS, scale=2, outline=INK)
    text(g, sx, 145, 'Pot 400 · cut 12', BONE_SHADE)
    text(g, sx, 156, 'Rematch? 1/3', GOLD)
    button(g, cx - 110 - 50, H - 30, 90, 'Rematch', 'primary', 'highlighted')
    button(g, cx - 14 - 50, H - 30, 60, 'Taunt…')
    button(g, cx + 50 - 50, H - 30, 60, 'Close')
    chip_counter(g, X + W - 90, H - 28, '12,888')
    finish(g, 'pvp_result', 6)


# =====================================================================================================================
# 7 / 8. Casino Menu — wallet tab, Loan Shark tab
# =====================================================================================================================
TABS = ['wallet', 'vip', 'contracts', 'loan', 'achievements', 'challenges', 'pvp', 'my_casino', 'rules']
TAB_LABEL = {'wallet': 'Wallet', 'vip': 'VIP', 'contracts': 'Contracts', 'loan': 'Loan', 'achievements': 'Trophies', 'challenges': 'Challenges',
             'pvp': 'PvP', 'my_casino': 'My Casino', 'rules': 'Rules'}
TAB_INDEX = ['wallet', 'vip', 'contracts', 'loan', 'achievements', 'challenges', 'pvp', 'my_casino', 'rules', 'cashier', 'settings']


def menu_shell(g, selected, loan=False):
    X, Y, W, H = 13, 0, 400, 240
    paste(g, tex('gui/core/menu/loan_backdrop.png' if loan else 'gui/core/menu/lobby_backdrop.png'), X, Y)
    nine_sprite(g, 'core/menu/shell' + ('_loan' if loan else ''), X, Y, W, H)
    # header plate + balance plaque
    nine_sprite(g, 'core/menu/header', X + 16, 4, 112, 20)
    ctext(g, X + 72, 10, 'Casino Menu', GOLD)
    nine_sprite(g, 'core/menu/balance', X + W - 106, 4, 90, 20)
    paste(g, spr('core/hud/chip_icon', 0), X + W - 101, 8)
    rtext(g, X + W - 23, 10, '12,500', GOLD)
    # tabs: icon-only bookmarks, the selected one shows its label
    icons = tex('gui/core/menu/tab_icons.png')
    x = X + 16
    for t in TABS:
        sel = t == selected
        w = (mcfont.width(TAB_LABEL[t]) + 30) if sel else 26
        name = 'core/menu/tab' + ('_loan' if t == 'loan' else '') + ('_selected' if sel else '')
        nine_sprite(g, name, x, 26 if sel else 28, w, 22 if sel else 20)
        i = TAB_INDEX.index(t)
        paste(g, icons.crop((i * 16, 0, i * 16 + 16, 16)), x + 5, 29 if sel else 30)
        if sel:
            text(g, x + 24, 33, TAB_LABEL[t], GOLD if not loan else RED_L)
        x += w + 2
    nine_sprite(g, 'core/menu/page' + ('_loan' if loan else ''), X + 16, 47, W - 32, H - 63)
    return X, Y, W, H


def menu_wallet():
    g = new_gui()
    X, Y, W, H = menu_shell(g, 'wallet')
    px, py = X + 24, 54
    # balance hero
    text(g, px + 4, py + 2, 'Balance', BONE_SHADE)
    text(g, px + 4, py + 13, '12,500', GOLD, scale=2, outline=INK)
    text(g, px + 76, py + 22, 'chips', GOLD)
    rows = [('Wagered, all time', '48,200', BONE), ('Net today', '+1,240', BONUS), ('Biggest win', '5,000 (Slots)', BONE), ('Streak', 'Lucky ×3', BONUS)]
    for i, (k, v, col) in enumerate(rows):
        y = py + 38 + i * 14
        nine_sprite(g, 'core/menu/row' + ('_alt' if i % 2 else ''), px, y, 200, 14)
        text(g, px + 6, y + 3, k, BONE_SHADE)
        rtext(g, px + 194, y + 3, v, col)
    paste(g, spr('core/hud/flame_1'), px + 134, py + 38 + 3 * 14 + 3)
    # VIP progress
    y = py + 98
    paste(g, cell('gui/core/menu/tab_icons_20.png', 1, 20, 20), px + 2, y)
    text(g, px + 26, y + 2, 'Bronze', (200, 118, 60))
    text(g, px + 26, y + 12, 'Next: Silver', BONE_SHADE)
    nine_sprite(g, 'core/menu/progress', px + 90, y + 6, 110, 10)
    fill = spr('core/menu/progress_fill_gold')
    for fx in range(px + 92, px + 92 + 70, 8):
        paste(g, fill.crop((0, 0, min(8, px + 92 + 70 - fx), 6)), fx, y + 8)
    rtext(g, px + 200, y + 18, '3,500 / 5,000', BONE_SHADE)
    text(g, px + 4, y + 30, 'Max bet 100 · Cashback 0%', BONE_SHADE)
    # right: chip breakdown stacks
    rx = X + 238
    nine_sprite(g, 'core/panel/inset', rx, py, 140, 118)
    text(g, rx + 6, py + 5, 'In your pocket', GOLD)
    denoms = [('1', 8), ('5', 14), ('25', 20), ('100', 26), ('500', 12)]
    for i, (d, n) in enumerate(denoms):
        sx = rx + 12 + i * 26
        side = spr(f'core/fx/chip_side_{d}')
        for k in range(n):
            paste(g, side, sx, py + 96 - k * 2)
        paste(g, spr(f'core/fx/chip_{d}'), sx + 2, py + 96 - n * 2 - 7)
        ctext(g, sx + 6, py + 104, d, BONE)
    button(g, rx, py + 124, 68, 'Cashier', icon=cell('gui/core/menu/tab_icons.png', 9, 16, 16))
    button(g, rx + 72, py + 124, 68, 'Settings', icon=cell('gui/core/menu/tab_icons.png', 10, 16, 16))
    finish(g, 'menu_wallet', 7)


def menu_loan():
    g = new_gui()
    X, Y, W, H = menu_shell(g, 'loan', loan=True)
    px, py = X + 24, 54
    shark = tex('gui/core/menu/shark.png')
    paste(g, shark, px, py)
    # speech + status
    nine_sprite(g, 'burmaldaholic/pvp/bubble_cheeky', px + 76, py + 4, 150, 24)
    paste(g, spr('burmaldaholic/pvp/bubble_tail_cheeky').transpose(Image.FLIP_LEFT_RIGHT).rotate(-90, expand=True), px + 70, py + 12)
    text(g, px + 82, py + 12, 'Late fees are adding up…', (140, 24, 52), shadow=False)
    text(g, px + 76, py + 34, 'Loan Shark', RED_L, bold=True)
    text(g, px + 76, py + 46, 'You owe 5,250', BONE)
    text(g, px + 76, py + 57, 'Overdue by 1 day', RED_L)
    # debt meter
    nine_sprite(g, 'core/menu/debt_meter', px, py + 76, 200, 12)
    fill = spr('core/menu/progress_fill_red')
    for fx in range(px + 2, px + 2 + 176, 8):
        paste(g, fill.crop((0, 0, min(8, px + 178 - fx), 6)), fx, py + 79)
    paste(g, spr('core/menu/debt_skull'), px + 196, py + 76)
    text(g, px, py + 92, 'Collectors are on their way', BONE_SHADE)
    # contract with the OVERDUE stamp
    nine_sprite(g, 'core/menu/contract', px, py + 106, 200, 50)
    text(g, px + 8, py + 112, 'Rent: 2,500 → 3,000', (58, 32, 16), shadow=False)
    text(g, px + 8, py + 123, 'Due: day 12', (58, 32, 16), shadow=False)
    text(g, px + 8, py + 140, 'x ______________', (138, 106, 60), shadow=False)
    stamp = spr('core/menu/stamp_overdue')
    paste(g, stamp, px + 118, py + 118)
    ctext(g, px + 154, py + 127, 'OVERDUE', RED, shadow=False, bold=True)
    # right: offers
    rx = X + 240
    text(g, rx, py + 2, 'Borrow', RED_L)
    offers = [('Pocket money', '500 → 575', False), ('Rent', '2,500 → 3,000', True), ('Business', 'VIP Silver', True)]
    for i, (name, line, locked) in enumerate(offers):
        y = py + 14 + i * 30
        is_locked = i == 2
        nine_sprite(g, 'core/menu/offer' + ('_locked' if is_locked else ''), rx, y, 138, 26)
        text(g, rx + 8, y + 4, name, BONE if not is_locked else (106, 122, 136))
        text(g, rx + 8, y + 15, line, BONE_SHADE if not is_locked else (74, 90, 104))
        if is_locked:
            paste(g, spr('burmaldaholic/pvp/padlock'), rx + 120, y + 7)
    text(g, rx, py + 106, 'No new loans while', BONE_SHADE)
    text(g, rx, py + 116, 'you are overdue', BONE_SHADE)
    button(g, rx, py + 132, 138, 'Pay all (5,250)', 'danger')
    button(g, rx, py + 156, 66, 'Pay…')
    button(g, rx + 72, py + 156, 66, 'Close')
    finish(g, 'menu_loan', 8)


# =====================================================================================================================
# 9. HUD — chip counter, delta pill and toasts over the world
# =====================================================================================================================
def hud():
    g = new_gui()
    chip_counter(g, 4, 4, '12,500')
    nine_sprite(g, 'core/hud/delta_up', 60, 6, mcfont.width('+388') + 8, 12)
    text(g, 64, 8, '+388', BONUS)
    paste(g, spr('core/hud/flame_1'), 6, 24)
    text(g, 17, 24, 'Lucky ×3', BONUS)
    chip_counter(g, 4, 40, '25,000', golden=True)
    text(g, 4, 58, 'Golden Hour 4:12', GOLD)
    # toasts (top right)
    tx = GW - 164
    for i, (kind, title, sub, icon) in enumerate([
        ('achievement', 'Achievement!', 'Edge of Glory', cell('gui/core/menu/tab_icons_20.png', 4, 20, 20)),
        ('pvp', 'Notch challenges you', 'Scratch Showdown · 200', cell('gui/pvp/mode_icons.png', 3, 16, 16)),
        ('loan', 'Payment overdue', 'You owe 5,250', cell('gui/core/menu/tab_icons_20.png', 3, 20, 20)),
    ]):
        y = 4 + i * 36
        paste(g, spr(f'core/toast/{kind}'), tx, y)
        paste(g, icon, tx + 16 - icon.width // 2, y + 16 - icon.height // 2)
        text(g, tx + 32, y + 7, title, GOLD if kind != 'loan' else RED_L)
        text(g, tx + 32, y + 18, sub, BONE)
        if kind == 'pvp':
            for x in range(tx + 32, tx + 150):
                g.putpixel((x, y + 28), GOLD + (255,) if x < tx + 110 else (90, 70, 20, 255))
    # hotbar hint (vanilla): a darker strip at the bottom
    finish(g, 'hud', 9, dim=20, blur=2)


if __name__ == '__main__':
    coin_flip()
    wheel_landing()
    plinko_drop()
    scratch_half()
    pvp_match()
    pvp_result()
    menu_wallet()
    menu_loan()
    hud()
