#!/usr/bin/env python3
"""Full-screen table mockups (854 x 480 = GUI 427 x 240 at GUI scale 2) composed from the REAL generated art.

    cd tools && node assets/gen-assets.mjs --module tables      # (re)draw the art first
    python3 docs/design/visual/render_tables.py                # needs Pillow

Every sprite comes from java/src/main/resources/assets/burmaldaholic/textures/gui/{tables,sprites/tables}; nothing
here is drawn except the text (a Minecraft-like 5 x 7 bitmap font, standing in for the game font) and the dim /
highlight washes the screen code applies. Geometry follows docs/design/visual/tables.md section 6.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
TEX = ROOT / 'java/src/main/resources/assets/burmaldaholic/textures'
GUI = TEX / 'gui/tables'
SPR = TEX / 'gui/sprites/tables'
OUT = Path(__file__).resolve().parent / 'mockups'
W, H = 427, 240

_cache = {}


def img(path):
    if path not in _cache:
        _cache[path] = Image.open(path).convert('RGBA')
    return _cache[path]


def gui(rel):
    return img(GUI / f'{rel}.png')


def spr(rel):
    return img(SPR / f'{rel}.png')


def frame(im, i, h=None, w=None, cols=1):
    """Frame i of a sheet: vertical strip (h) or grid (w, h, cols)."""
    w = w or im.width
    h = h or w
    x, y = (i % cols) * w, (i // cols) * h
    return im.crop((x, y, x + w, y + h))


def paste(canvas, im, x, y, alpha=1.0):
    if alpha < 1:
        im = im.copy()
        a = im.getchannel('A').point(lambda v: int(v * alpha))
        im.putalpha(a)
    canvas.alpha_composite(im, (int(x), int(y)))


def nine(canvas, im, x, y, w, h, b):
    """Nine-slice with tiled edges and centre (vanilla GUI sprite behaviour)."""
    sw, sh = im.size
    parts = {}
    xs = [(0, b), (b, sw - b), (sw - b, sw)]
    ys = [(0, b), (b, sh - b), (sh - b, sh)]
    tx = [(x, b), (x + b, w - 2 * b), (x + w - b, b)]
    ty = [(y, b), (y + b, h - 2 * b), (y + h - b, b)]
    for j in range(3):
        for i in range(3):
            src = im.crop((xs[i][0], ys[j][0], xs[i][1], ys[j][1]))
            parts[i, j] = src
            dx, dw = tx[i]
            dy, dh = ty[j]
            if dw <= 0 or dh <= 0 or src.width == 0 or src.height == 0:
                continue
            for yy in range(dy, dy + dh, src.height):
                for xx in range(dx, dx + dw, src.width):
                    cw = min(src.width, dx + dw - xx)
                    ch = min(src.height, dy + dh - yy)
                    canvas.alpha_composite(src.crop((0, 0, cw, ch)), (xx, yy))


def wash(canvas, x, y, w, h, rgba):
    canvas.alpha_composite(Image.new('RGBA', (w, h), rgba), (x, y))


def tile(canvas, im, x, y, w, h):
    region = Image.new('RGBA', (w, h))
    for yy in range(0, h, im.height):
        for xx in range(0, w, im.width):
            region.alpha_composite(im, (xx, yy))
    canvas.alpha_composite(region, (x, y))


# ---- a Minecraft-like bitmap font (stand-in for the game font; the art itself never contains words) ----------------
F = {
    'A': '.###.|#...#|#...#|#####|#...#|#...#|#...#', 'B': '####.|#...#|####.|#...#|#...#|#...#|####.',
    'C': '.###.|#...#|#....|#....|#....|#...#|.###.', 'D': '####.|#...#|#...#|#...#|#...#|#...#|####.',
    'E': '#####|#....|###..|#....|#....|#....|#####', 'F': '#####|#....|###..|#....|#....|#....|#....',
    'G': '.####|#....|#..##|#...#|#...#|#...#|.###.', 'H': '#...#|#...#|#####|#...#|#...#|#...#|#...#',
    'I': '###|.#.|.#.|.#.|.#.|.#.|###', 'J': '....#|....#|....#|....#|#...#|#...#|.###.',
    'K': '#...#|#..#.|###..|#..#.|#...#|#...#|#...#', 'L': '#....|#....|#....|#....|#....|#....|#####',
    'M': '#...#|##.##|#.#.#|#...#|#...#|#...#|#...#', 'N': '#...#|##..#|#.#.#|#..##|#...#|#...#|#...#',
    'O': '.###.|#...#|#...#|#...#|#...#|#...#|.###.', 'P': '####.|#...#|####.|#....|#....|#....|#....',
    'Q': '.###.|#...#|#...#|#...#|#...#|#..#.|.##.#', 'R': '####.|#...#|####.|#...#|#...#|#...#|#...#',
    'S': '.####|#....|.###.|....#|....#|#...#|.###.', 'T': '#####|..#..|..#..|..#..|..#..|..#..|..#..',
    'U': '#...#|#...#|#...#|#...#|#...#|#...#|.###.', 'V': '#...#|#...#|#...#|#...#|.#.#.|.#.#.|..#..',
    'W': '#...#|#...#|#...#|#...#|#.#.#|##.##|#...#', 'X': '#...#|.#.#.|..#..|.#.#.|#...#|#...#|#...#',
    'Y': '#...#|.#.#.|..#..|..#..|..#..|..#..|..#..', 'Z': '#####|...#.|..#..|.#...|#....|#....|#####',
    'a': '.....|.....|.###.|....#|.####|#...#|.####', 'b': '#....|#....|#.##.|##..#|#...#|#...#|####.',
    'c': '.....|.....|.###.|#...#|#....|#...#|.###.', 'd': '....#|....#|.##.#|#..##|#...#|#...#|.####',
    'e': '.....|.....|.###.|#...#|#####|#....|.####', 'f': '..##|.#..|####|.#..|.#..|.#..|.#..',
    'g': '.....|.....|.####|#...#|#...#|.####|....#|####.', 'h': '#....|#....|#.##.|##..#|#...#|#...#|#...#',
    'i': '#|.|#|#|#|#|#', 'j': '....#|.....|....#|....#|....#|....#|#...#|.###.',
    'k': '#...|#...|#..#|#.#.|##..|#.#.|#..#', 'l': '#.|#.|#.|#.|#.|#.|.#',
    'm': '.....|.....|##.#.|#.#.#|#.#.#|#...#|#...#', 'n': '.....|.....|####.|#...#|#...#|#...#|#...#',
    'o': '.....|.....|.###.|#...#|#...#|#...#|.###.', 'p': '.....|.....|#.##.|##..#|#...#|####.|#....|#....',
    'q': '.....|.....|.##.#|#..##|#...#|.####|....#|....#', 'r': '.....|.....|#.##.|##..#|#....|#....|#....',
    's': '.....|.....|.####|#....|.###.|....#|####.', 't': '.#..|.#..|####|.#..|.#..|.#..|..##',
    'u': '.....|.....|#...#|#...#|#...#|#...#|.####', 'v': '.....|.....|#...#|#...#|#...#|.#.#.|..#..',
    'w': '.....|.....|#...#|#...#|#.#.#|#.#.#|.####', 'x': '.....|.....|#...#|.#.#.|..#..|.#.#.|#...#',
    'y': '.....|.....|#...#|#...#|#...#|.####|....#|####.', 'z': '.....|.....|#####|...#.|..#..|.#...|#####',
    '0': '.###.|#...#|#..##|#.#.#|##..#|#...#|.###.', '1': '..#..|.##..|..#..|..#..|..#..|..#..|#####',
    '2': '.###.|#...#|....#|..##.|.#...|#...#|#####', '3': '.###.|#...#|....#|..##.|....#|#...#|.###.',
    '4': '...##|..#.#|.#..#|#...#|#####|....#|....#', '5': '#####|#....|####.|....#|....#|#...#|.###.',
    '6': '..##.|.#...|#....|####.|#...#|#...#|.###.', '7': '#####|#...#|....#|...#.|..#..|..#..|..#..',
    '8': '.###.|#...#|#...#|.###.|#...#|#...#|.###.', '9': '.###.|#...#|#...#|.####|....#|...#.|.##..',
    ' ': '...', '!': '#|#|#|#|#|.|#', ':': '.|#|.|.|.|#|.', '.': '.|.|.|.|.|.|#', ',': '.|.|.|.|.|#|#',
    '+': '.....|..#..|..#..|#####|..#..|..#..|.....', '-': '.....|.....|.....|#####|.....|.....|.....',
    "'": '#|#|.|.|.|.|.', '·': '.|.|.|#|.|.|.', '—': '.......|.......|.......|#######|.......|.......|.......',
    '/': '....#|...#.|...#.|..#..|.#...|.#...|#....', '(': '..#|.#.|#..|#..|#..|.#.|..#', ')': '#..|.#.|..#|..#|..#|.#.|#..',
    '%': '#...#|#..#.|...#.|..#..|.#...|.#..#|#...#', '?': '.###.|#...#|....#|...#.|..#..|.....|..#..',
}
GLYPHS = {k: v.split('|') for k, v in F.items()}


def text_w(s, scale=1):
    return sum((len(GLYPHS[ch][0]) + 1) * scale for ch in s) - scale


def text(canvas, s, x, y, rgb=(255, 255, 255), scale=1, shadow=True, centre=False):
    if centre:
        x = x - text_w(s, scale) // 2
    x, y = int(x), int(y)
    px = canvas.load()
    sh = tuple(v // 4 for v in rgb)
    for dx, dy, col in ([(scale, scale, sh)] if shadow else []) + [(0, 0, rgb)]:
        cx = x + dx
        for ch in s:
            g = GLYPHS[ch]
            for j, row in enumerate(g):
                for i, p in enumerate(row):
                    if p != '#':
                        continue
                    for a in range(scale):
                        for b in range(scale):
                            xx, yy = cx + i * scale + a, y + dy + j * scale + b
                            if 0 <= xx < canvas.width and 0 <= yy < canvas.height:
                                px[xx, yy] = col + (255,)
            cx += (len(g[0]) + 1) * scale
    return text_w(s, scale)


GOLD = (255, 214, 64)
BONE = (244, 236, 248)
LILAC = (214, 150, 255)
GREEN = (128, 255, 64)
RED = (255, 110, 106)

# ---- shared frame -------------------------------------------------------------------------------------------------------
RAIL = (4, 21, 419, 184)  # x, y, w, h
FELT = (14, 31, 399, 164)  # inside the rail (rail inset 10)


def base(theme, title, balance, seats=()):
    c = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    c.alpha_composite(gui(f'core/backdrop_{theme}').crop((106, 60, 106 + W, 60 + H)))
    tile(c, gui(f'core/felt_{theme}'), *FELT)
    nine(c, spr(f'core/rail_{theme}'), *RAIL, 14)
    # top bar: title plate, seat plates, balance plate
    tw = text_w(title) + 12
    nine(c, spr(f'core/plate_{theme}'), 4, 3, tw, 15, 5)
    text(c, title, 10, 7, BONE)
    x = 4 + tw + 6
    for name, kind, tint in seats:
        w = text_w(name) + 20
        nine(c, spr(f'core/seat_{kind}_{theme}'), x, 3, w, 15, 5)
        stripe = spr('core/seat_stripe').copy()
        if tint:
            r, g, b = tint
            px = stripe.load()
            for yy in range(stripe.height):
                for xx in range(stripe.width):
                    p = px[xx, yy]
                    px[xx, yy] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3])
        paste(c, spr('core/bot_badge') if kind == 'bot' else stripe, x + 2 if kind == 'bot' else x + 3, 5 if kind == 'bot' else 5)
        text(c, name, x + 16, 7, GOLD if kind == 'active' else BONE)
        x += w + 4
    bw = text_w(balance) + 26
    nine(c, spr(f'core/plate_gold_{theme}'), W - 4 - bw, 3, bw, 15, 5)
    paste(c, spr('core/stack/chip_500'), W - 4 - bw + 4, 5)
    text(c, balance, W - 4 - bw + 19, 7, GOLD)
    icon_buttons(c, theme, ['rules', 'leave'], W - 4 - bw - 46, 1)
    return c


def label(c, theme, s, cx, y):
    w = text_w(s) + 8
    nine(c, spr(f'core/plate_{theme}'), cx - w // 2, y, w, 13, 5)
    text(c, s, cx, y + 3, GOLD, centre=True)


def chip_stack(c, d, x, y, n, seat=None, alpha=1.0):
    ch = spr(f'core/stack/chip_{d}')
    if seat:
        ch = ch.copy()
        px = ch.load()
        for yy in range(ch.height):
            for xx in range(ch.width):
                p = px[xx, yy]
                if p[3] and not (p[0] < 40 and p[1] < 20):
                    px[xx, yy] = (p[0] * seat[0] // 255, p[1] * seat[1] // 255, p[2] * seat[2] // 255, p[3])
    for k in range(n):
        paste(c, ch, x - 6, y - 8 - 3 * k, alpha)


def chip_tray(c, theme, selected, x0=8, y0=212):
    for i, d in enumerate([1, 5, 25, 100, 500]):
        x = x0 + i * 27
        paste(c, spr(f'core/chip_well_{theme}'), x - 1, y0 - 1)
        paste(c, spr(f'core/chip_{d}'), x, y0 - (2 if d == selected else 0))
        if d == selected:
            paste(c, frame(spr('core/chip_select'), 1, 28), x - 2, y0 - 4)


def icon_buttons(c, theme, names, x0, y0, hover=None):
    for i, n in enumerate(names):
        x = x0 + i * 22
        paste(c, spr(f'core/icon_button_{theme}' + ('_hover' if n == hover else '')), x, y0)
        ic = spr(f'core/icon/{n}')
        paste(c, ic, x + 10 - ic.width // 2, y0 + 9 - ic.height // 2)


# ---- roulette -------------------------------------------------------------------------------------------------------------
LAY = (110, 36)  # layout top-left on screen
TRACK = (110, 142)
CELL = (20, 22)
ZERO_W = 18
ORDER = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7,
         28, 12, 35, 3, 26]
REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}


def cell_rect(n):
    col, row = (n - 1) // 3, 2 - (n - 1) % 3
    return LAY[0] + ZERO_W + col * CELL[0], LAY[1] + row * CELL[1], CELL[0], CELL[1]


def cell_centre(n):
    x, y, w, h = cell_rect(n)
    return x + w // 2, y + h // 2


LINE = {'village': (237, 226, 196), 'bastion': (255, 214, 64), 'end': (244, 236, 248)}


def roulette_table(c, theme, history, result_idx):
    paste(c, frame(gui(f'roulette/wheel_mini_{theme}'), result_idx, 72, 72, 10), 22, 34)
    for i, n in enumerate(history[:8]):
        col = 'green' if n == 0 else 'red' if n in REDS else 'black'
        x, y = 22 + (i % 2) * 26, 112 + (i // 2) * 13
        paste(c, spr(f'roulette/pill_{col}' + ('_new' if i == 0 else '')), x, y)
        text(c, str(n), x + 10, y + 2, BONE)
    paste(c, gui(f'roulette/layout_{theme}'), *LAY)
    paste(c, gui(f'roulette/racetrack_{theme}'), *TRACK)
    ox = LAY[0] + ZERO_W
    yo = LAY[1] + 3 * CELL[1] + 16
    text(c, 'EVEN', ox + 40 + 20, yo + 5, LINE[theme], centre=True)
    text(c, 'ODD', ox + 160 + 20, yo + 5, LINE[theme], centre=True)
    for label, x in (('Tier', 50), ('Orphelins', 117), ('Voisins', 186), ('Zero', 243)):
        text(c, label, TRACK[0] + x, TRACK[1] + 18, LINE[theme], centre=True)


def roulette_controls(c, theme, phase, sub, spin_state='', hover_icon=None):
    chip_tray(c, theme, 25)
    icon_buttons(c, theme, ['undo', 'clear', 'rebet', 'racetrack'], 146, 214, hover_icon)
    text(c, phase, 296, 211, GOLD, centre=True)
    text(c, sub, 296, 222, BONE, centre=True)
    paste(c, spr('core/spin_button' + spin_state), 373, 189)


def roulette_betting(theme='village', name='roulette_betting'):
    c = base(theme, 'European Roulette', '12 500', (('Alex', 'other', (79, 195, 247)), ('Ivan', 'bot', None)))
    roulette_table(c, theme, [26, 3, 17, 0, 32, 11, 8, 19], 36)
    # my chips: straight 17 (25+5), split 8/11 (5), corner 20/21/23/24 (25), red (100), 2nd dozen (25)
    x, y = cell_centre(17)
    chip_stack(c, 25, x, y + 5, 2)
    chip_stack(c, 5, x + 4, y + 5, 1)
    x, y, w, h = cell_rect(8)
    chip_stack(c, 5, x + w, y + h // 2 + 5, 2)
    x, y, w, h = cell_rect(21)
    chip_stack(c, 25, x + w, y + 5, 1)
    ox, oy = LAY[0] + ZERO_W, LAY[1] + 66
    chip_stack(c, 100, ox + 100, oy + 30, 3)
    chip_stack(c, 25, ox + 120, oy + 13, 2)
    # another player's chips (seat tint, 70 %, under mine)
    xx, yy = cell_centre(32)
    chip_stack(c, 'grey', xx, yy + 5, 2, seat=(79, 195, 247))
    chip_stack(c, 'grey', ox + 22, oy + 30, 1, seat=(79, 195, 247))
    # hover: split 26/29 with the ghost chip on the edge
    for n in (26, 29):
        nine(c, spr('roulette/cell_hover'), *cell_rect(n), 3)
    x, y, w, h = cell_rect(26)
    chip_stack(c, 25, x + w, y + h // 2 + 5, 1, alpha=0.55)
    # racetrack: hovering "neighbours of 17" shows the five covered cells
    roulette_controls(c, theme, 'Place your bets', 'Total bet 190', hover_icon='rebet')
    paste(c, frame(spr('core/timer'), 3, 16), 352, 213)
    return c


def roulette_spin(theme='village'):
    c = base(theme, 'European Roulette', '12 310', (('Alex', 'other', (79, 195, 247)), ('Ivan', 'bot', None)))
    roulette_table(c, theme, [26, 3, 17, 0, 32, 11, 8, 19], 36)
    x, y = cell_centre(17)
    chip_stack(c, 25, x, y + 5, 2)
    chip_stack(c, 100, LAY[0] + ZERO_W + 100, LAY[1] + 96, 3)
    roulette_controls(c, theme, 'No more bets', 'The ball is spinning', spin_state='_disabled')
    wash(c, 14, 31, 399, 164, (8, 4, 16, 120))  # NO_MORE_BETS dim wipe
    wash(c, 0, 205, 427, 35, (8, 4, 16, 90))
    # the big wheel rises over the dimmed layout
    cx, cy = 213, 124
    paste(c, gui('roulette/wheel_shadow'), cx - 110, cy - 112)
    paste(c, gui(f'roulette/wheel_bowl_{theme}'), cx - 104, cy - 104)
    paste(c, frame(gui('roulette/wheel_head'), 29, 152, 152, 10), cx - 76, cy - 76)
    # ball on the track (r = 0.94 Rw) with its trail, just past a deflector
    import math
    ball = frame(spr('roulette/ball'), 0, 7)
    for k, a in ((3, 0.2), (2, 0.4), (1, 0.6), (0, 1.0)):
        ang = math.radians(205 + k * 7)
        paste(c, ball, cx + 86 * math.sin(ang) - 3, cy - 86 * math.cos(ang) - 3, a)
    paste(c, frame(spr('core/spark'), 1, 7), cx + 84 * math.sin(math.radians(202.5)) - 3, cy - 84 * math.cos(math.radians(202.5)) - 3)
    nine(c, spr(f'core/banner_{theme}'), cx - 50, 24, 100, 20, 10)
    text(c, 'No more bets', cx, 30, GOLD, centre=True)
    return c


def roulette_win(theme='village', name='roulette_win'):
    c = base(theme, 'European Roulette', '12 670', (('Alex', 'other', (79, 195, 247)), ('Ivan', 'bot', None)))
    roulette_table(c, theme, [17, 26, 3, 17, 0, 32, 11, 8], ORDER.index(17))
    ox, oy = LAY[0] + ZERO_W, LAY[1] + 66
    # win outlines: 17, 2nd dozen, 1-18, black, odd, column 2
    win = frame(spr('roulette/cell_win'), 2, 12)
    nine(c, win, *cell_rect(17), 4)
    for rx, ry, rw, rh in ((ox + 80, oy, 81, 16), (ox, oy + 16, 41, 16), (ox + 120, oy + 16, 41, 16), (ox + 160, oy + 16, 41, 16),
                           (ox + 240, LAY[1] + 22, 20, 22)):
        nine(c, win, rx, ry, rw, rh, 4)
    # the dolly stands on 17; winning stacks with their payout discs; losing bets already swept
    x, y = cell_centre(17)
    chip_stack(c, 25, x + 1, y + 9, 2)
    for k in range(4):
        paste(c, spr('core/stack/chip_100'), x + 8, y + 1 - 3 * k)
    paste(c, spr('roulette/dolly_shadow'), x - 9, y + 3)
    paste(c, frame(spr('roulette/dolly'), 2, 14), x - 10, y - 12)
    label(c, theme, '+1 750', x + 18, y - 22)
    chip_stack(c, 25, ox + 120, oy + 13, 2)
    chip_stack(c, 25, ox + 128, oy + 13, 3)
    label(c, theme, '+50', ox + 134, oy - 2)
    nine(c, spr('core/banner_win'), 184, 148, 150, 26, 10)
    text(c, '17 Black', 259, 153, BONE, centre=True)
    text(c, 'Straight up! +1 800', 259, 163, GOLD, centre=True)
    roulette_controls(c, theme, '', '', spin_state='_disabled')
    return c


def roulette_themed(theme):
    c = roulette_betting(theme)
    return c


# ---- craps ---------------------------------------------------------------------------------------------------------------
CR = (20, 33)
CRW = 386


def craps_rects():
    y0 = 9
    box_w = (CRW - 64 - 2) // 6
    place = {n: (66 + k * box_w, y0, box_w - 1, 36) for k, n in enumerate([4, 5, 6, 8, 9, 10])}
    return {
        'place': place, 'dc': (0, y0, 64, 71), 'come': (66, 48, CRW - 66, 32), 'field': (0, 83, CRW, 30),
        'fieldLabel': (0, 83, 85, 30), 'dp': (0, 116, CRW, 16), 'pass': (0, 135, CRW, 18), 'odds': (0, 154, CRW, 8),
    }


def craps_point(theme='village'):
    c = base(theme, 'Craps', '8 940', (('Steve', 'active', (255, 183, 77)), ('Alex', 'other', (79, 195, 247)), ('Ivan', 'bot', None)))
    paste(c, gui(f'craps/layout_{theme}'), *CR)
    R = craps_rects()
    lab = LINE[theme]

    def at(r):
        return CR[0] + r[0], CR[1] + r[1], r[2], r[3]

    x, y, w, h = at(R['dc'])
    text(c, "DON'T", x + w // 2, y + 26, lab, centre=True)
    text(c, 'COME', x + w // 2, y + 36, lab, centre=True)
    x, y, w, h = at(R['come'])
    text(c, 'COME', x + w // 2, y + 12, lab, scale=2, centre=True)
    x, y, w, h = at(R['fieldLabel'])
    text(c, 'FIELD', x + w // 2, y + 8, lab, scale=2, centre=True)
    x, y, w, h = at(R['dp'])
    text(c, "DON'T PASS BAR", x + w // 2, y + 5, lab, centre=True)
    x, y, w, h = at(R['pass'])
    text(c, 'PASS LINE', x + w // 2, y + 6, lab, centre=True)
    x, y, w, h = at(R['odds'])
    text(c, 'ODDS', x + 6, y, lab, shadow=True)
    # point ON: puck on the 8 box, the box glows
    x, y, w, h = at(R['place'][8])
    paste(c, frame(spr('craps/point_glow'), 2, 40), x - 2, y - 2)
    paste(c, frame(spr('craps/puck'), 4, 18), x + w - 17, y - 3)
    text(c, 'ON', x + w - 8, y + 3, (38, 32, 44), shadow=False, centre=True)
    # chips: pass line + odds behind it, a come bet travelled to the 5, a place bet on 6, field
    px, py, pw, ph = at(R['pass'])
    chip_stack(c, 25, px + 300, py + 14, 2)
    ox, oy, ow, oh = at(R['odds'])
    chip_stack(c, 25, px + 308, oy + 8, 3)
    x, y, w, h = at(R['place'][5])
    chip_stack(c, 5, x + w // 2, y + h - 3, 2)
    x, y, w, h = at(R['place'][6])
    chip_stack(c, 'grey', x + w // 2, y + h - 3, 2, seat=(79, 195, 247))
    fx, fy, fw, fh = at(R['field'])
    chip_stack(c, 5, fx + 300, fy + fh - 4, 1)
    # dice resting on the come area (3 + 5), total badge "8" in the point colour
    dice = gui('craps/dice_small')
    cx, cy, cw, chh = at(R['come'])
    shadow = spr('craps/die_shadow')
    for i, (face, dx) in enumerate(((3, 40), (5, 62))):
        paste(c, shadow, cx + dx, cy + 20)
        paste(c, frame(dice, face - 1, 18, 18, 8), cx + dx, cy + 6)
    paste(c, spr('craps/total_point'), cx + 86, cy + 3)
    text(c, '8', cx + 98, cy + 11, GOLD, centre=True)
    # controls
    chip_tray(c, theme, 25)
    icon_buttons(c, theme, ['undo', 'clear'], 146, 214)
    text(c, 'Point is 8', 268, 211, GOLD, centre=True)
    text(c, 'Pass 50 · Odds 75 · Come 10', 268, 222, BONE, centre=True)
    paste(c, spr('core/roll_button'), 373, 189)
    return c


# ---- dice duel -----------------------------------------------------------------------------------------------------------
def dice_duel(theme='village'):
    c = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    c.alpha_composite(gui(f'core/backdrop_{theme}').crop((106, 60, 106 + W, 60 + H)))
    t = 'Dice Duel'
    tw = text_w(t) + 12
    nine(c, spr(f'core/plate_{theme}'), 4, 3, tw, 15, 5)
    text(c, t, 10, 7, BONE)
    bw = text_w('12 550') + 26
    nine(c, spr(f'core/plate_gold_{theme}'), W - 4 - bw, 3, bw, 15, 5)
    paste(c, spr('core/stack/chip_500'), W - 4 - bw + 4, 5)
    text(c, '12 550', W - 4 - bw + 19, 7, GOLD)
    ax, ay = 33, 34
    paste(c, gui(f'extras/dice_duel_arena_{theme}'), ax, ay)
    # name plates over the lanes
    for name, kind, x in (('You', 'you', ax + 96), ('Dealer', 'other', ax + 250)):
        w = text_w(name) + 20
        nine(c, spr(f'core/seat_{kind}_{theme}'), x - w // 2, ay + 16, w, 15, 5)
        paste(c, spr('core/seat_stripe'), x - w // 2 + 3, ay + 18)
        text(c, name, x - w // 2 + 16, ay + 20, BONE)
    # cups at the outer edges, tipped after the throw
    cup = spr('extras/dice_duel_cup')
    paste(c, frame(cup, 5, 40), ax + 8, ay + 64)
    paste(c, frame(cup, 5, 40).transpose(Image.FLIP_LEFT_RIGHT), ax + 312, ay + 64)
    big = gui('extras/dice_duel_dice_big')
    # your dice (4 + 5 = 9) win-glow; the dealer's (2 + 4 = 6) plain
    for face, x in ((4, ax + 62), (5, ax + 96)):
        paste(c, frame(big, (face - 1) * 13 + 5, 40, 40, 13), x, ay + 66)
    for face, x in ((2, ax + 226), (4, ax + 260)):
        paste(c, frame(big, (face - 1) * 13, 40, 40, 13), x, ay + 66)
    # plaques: yours gold (scaled up by the code 1.3 → shown at 1:1 here), theirs cracked and dimmed
    paste(c, spr('extras/dice_duel_plaque_win'), ax + 80, ay + 40)
    text(c, '9', ax + 96, ay + 44, GOLD, scale=2, centre=True)
    paste(c, spr('extras/dice_duel_plaque_lose'), ax + 244, ay + 40, 0.75)
    text(c, '6', ax + 260, ay + 44, (140, 124, 168), scale=2, centre=True)
    paste(c, frame(spr('extras/dice_duel_crack'), 1, 24, 32), ax + 244, ay + 40)
    paste(c, frame(spr('extras/dice_duel_vs'), 2, 32), ax + 164, ay + 54)
    # chips: the dealer pushes a matching stack across the divider
    chip_stack(c, 25, ax + 150, ay + 124, 2)
    chip_stack(c, 25, ax + 200, ay + 124, 2, alpha=0.7)
    text(c, '+50', ax + 150, ay + 100, GOLD, centre=True)
    # outcome + controls
    nine(c, spr('core/banner_win'), 130, 178, 168, 24, 10)
    text(c, 'You win! 9 beats 6', 214, 182, GOLD, centre=True)
    text(c, '+50 chips', 214, 191, BONE, centre=True)
    chip_tray(c, theme, 25)
    icon_buttons(c, theme, ['clear', 'rules', 'leave'], 146, 214)
    text(c, 'Stake 50', 250, 218, BONE)
    paste(c, spr('core/roll_button_hover'), 373, 189)
    return c


def roulette_compact(theme='village'):
    """GUI 284 x 160 (854 x 480 at GUI scale 3): compact layout, mini wheel, racetrack behind its toggle."""
    cw, chh = 284, 160
    c = Image.new('RGBA', (cw, chh), (0, 0, 0, 255))
    c.alpha_composite(gui(f'core/backdrop_{theme}').crop((178, 100, 178 + cw, 100 + chh)))
    tile(c, gui(f'core/felt_{theme}'), 12, 23, 260, 104)
    nine(c, spr(f'core/rail_{theme}'), 2, 13, 280, 124, 14)
    nine(c, spr(f'core/plate_{theme}'), 2, 1, 70, 12, 5)
    text(c, 'Roulette', 8, 3, BONE)
    nine(c, spr(f'core/plate_gold_{theme}'), 216, 1, 66, 12, 5)
    text(c, '12 500', 234, 3, GOLD)
    lx, ly = 64, 32
    paste(c, gui(f'roulette/layout_compact_{theme}'), lx, ly)
    for i, n in enumerate([26, 3, 17, 0, 32, 11]):
        col = 'green' if n == 0 else 'red' if n in REDS else 'black'
        paste(c, spr(f'roulette/pill_{col}' + ('_new' if i == 0 else '')), 20, 30 + i * 13)
        text(c, str(n), 30, 32 + i * 13, BONE)
    # chips: straight 17, red; the hover ghost on the 26/29 split
    chip_stack(c, 25, lx + 12 + 5 * 14 + 7, ly + 15 + 11, 2)
    chip_stack(c, 100, lx + 12 + 2 * 28 + 14, ly + 45 + 11 + 9, 2)
    for n in (26, 29):
        col, row = (n - 1) // 3, 2 - (n - 1) % 3
        nine(c, spr('roulette/cell_hover'), lx + 12 + col * 14, ly + row * 15, 14, 15, 3)
    text(c, 'EVEN', lx + 12 + 28 + 14, ly + 45 + 11 + 2, LINE[theme], centre=True)
    text(c, 'ODD', lx + 12 + 4 * 28 + 14, ly + 45 + 11 + 2, LINE[theme], centre=True)
    text(c, 'Place your bets', 142, 112, GOLD, centre=True)
    for i, d in enumerate([1, 5, 25, 100, 500]):
        paste(c, spr(f'core/chip_{d}'), 4 + i * 25, 136)
    paste(c, frame(spr('core/chip_select'), 1, 28), 4 + 2 * 25 - 2, 134)
    icon_buttons(c, theme, ['undo', 'clear', 'racetrack'], 132, 138)
    text(c, 'Bet 60', 214, 144, GOLD, centre=True)
    paste(c, spr('core/spin_button'), 236, 112)
    return c


def save(c, name, scale=2):
    OUT.mkdir(parents=True, exist_ok=True)
    c.convert('RGB').resize((c.width * scale, c.height * scale), Image.NEAREST).save(OUT / f'tables_{name}.png', optimize=True)


def main():
    save(roulette_betting('village'), 'roulette_betting')
    save(roulette_spin('village'), 'roulette_spin')
    save(roulette_win('village'), 'roulette_win')
    save(craps_point('village'), 'craps_point')
    save(dice_duel('village'), 'dice_duel_reveal')
    save(roulette_betting('bastion'), 'roulette_bastion')
    save(craps_point('end'), 'craps_end')
    save(dice_duel('end'), 'dice_duel_end')
    save(roulette_spin('bastion'), 'roulette_spin_bastion')
    save(roulette_compact('end'), 'roulette_compact_end', 3)


if __name__ == '__main__':
    main()
