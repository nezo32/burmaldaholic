"""Mockups for the v2 table UI redesign (docs/design/visual/v2/REDESIGN.md).

Renders every screen at its GUI size (480 x 270 = 1920 x 1080 at GUI scale 4; 640 x 360 = 2560 x 1440 at GUI
scale 4) and saves it upscaled x4 with nearest neighbour, exactly as the game would show it. Uses the real generated
art from java/src/main/resources/assets/burmaldaholic/textures (felt, cards, shoe, rack, roulette bowl) and the vanilla
font (read from the Loom Minecraft jar if present, else the repo's mcfont.py approximation). Sprites that the
redesign adds (chip_v2, plate_v2, stamp_v2, the big button ...) are drawn procedurally here as stand-ins; the
generator (tools/assets) must produce them natively (REDESIGN.md §9).

    python3 docs/design/visual/v2/render_v2.py            # writes docs/design/visual/v2/*.png

In-world concept renders (world_*.png) are a tiny perspective projection of textured quads (no numpy).
"""
import glob
import math
import os
import sys
import zipfile
from io import BytesIO

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
TEX = os.path.join(ROOT, 'java/src/main/resources/assets/burmaldaholic/textures/')
OUT = HERE

# ---- colour tokens (REDESIGN.md §5) -----------------------------------------------------------------------------------
C = {
    'ink': (24, 10, 40), 'bg': (20, 8, 34), 'panel': (34, 18, 47), 'panel.hi': (52, 30, 72), 'edge': (120, 60, 190),
    'text': (244, 236, 248), 'muted': (192, 176, 220), 'gold': (255, 214, 64), 'gold.shade': (176, 112, 16),
    'win': (128, 255, 64), 'lose': (255, 110, 106), 'push': (143, 168, 200), 'you': (92, 232, 224),
    'felt': (30, 94, 58), 'felt.print': (232, 217, 160), 'rail': (90, 52, 24), 'rail.dark': (58, 32, 16),
    'rail.hi': (138, 86, 40), 'red': (216, 52, 64), 'black': (38, 34, 44), 'green': (30, 140, 70),
    'btn': (58, 26, 92), 'btn.hi': (74, 36, 116), 'scrim': (14, 6, 24),
}
CHIP = {1: ((236, 236, 236), (58, 111, 216), (30, 30, 40)), 5: ((198, 40, 40), (245, 245, 245), (255, 255, 255)),
        25: ((46, 125, 50), (245, 245, 245), (255, 255, 255)), 100: ((38, 38, 38), (245, 245, 245), (255, 255, 255)),
        500: ((106, 27, 154), (243, 211, 74), (255, 255, 255))}


def tex(path):
    return Image.open(TEX + path).convert('RGBA')


# ---- font --------------------------------------------------------------------------------------------------------------
class Font:
    def __init__(self):
        self.sheet = None
        for jar in sorted(glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-client.jar'))):
            try:
                with zipfile.ZipFile(jar) as z:
                    self.sheet = Image.open(BytesIO(z.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
                break
            except (KeyError, OSError):
                pass
        if self.sheet is None:
            sys.path.insert(0, os.path.join(HERE, '..', 'mockups'))
            import mcfont  # noqa: E402
            self.mc = mcfont
        self.cache = {}

    def glyph(self, ch):
        if ch in self.cache:
            return self.cache[ch]
        o = ord(ch)
        if o > 255:
            o = ord('?')
        gx, gy = (o % 16) * 8, (o // 16) * 8
        g = self.sheet.crop((gx, gy, gx + 8, gy + 8))
        w = 0
        for x in range(8):
            for y in range(8):
                if g.getpixel((x, y))[3] > 0:
                    w = x + 1
        if ch == ' ':
            w = 3
        self.cache[ch] = (g, w)
        return self.cache[ch]

    def width(self, s, scale=1):
        if self.sheet is None:
            return self.mc.width(s, scale=scale)
        return sum(self.glyph(c)[1] + 1 for c in s) * scale - (scale if s else 0)

    def draw(self, img, x, y, s, color, scale=1, shadow=True):
        if self.sheet is None:
            self.mc.draw(img, x, y, s, color + (255,) if len(color) == 3 else color, shadow=shadow, scale=scale)
            return
        col = color[:3]
        for pass_, (dx, dy, cc) in enumerate(([(scale, scale, tuple(v // 4 for v in col))] if shadow else []) + [(0, 0, col)]):
            cx = x
            for ch in s:
                g, w = self.glyph(ch)
                mask = g.split()[3]
                if scale != 1:
                    mask = mask.resize((8 * scale, 8 * scale), Image.NEAREST)
                img.paste(Image.new('RGBA', mask.size, cc + (255,)), (cx + dx, y + dy), mask)
                cx += (w + 1) * scale


F = Font()


def text(img, x, y, s, color=C['text'], scale=1, align='l', shadow=True):
    w = F.width(s, scale)
    if align == 'c':
        x -= w // 2
    elif align == 'r':
        x -= w
    F.draw(img, x, y, s, color, scale, shadow)
    return w


# ---- primitives --------------------------------------------------------------------------------------------------------
def rect(img, x, y, w, h, fill, outline=None):
    d = ImageDraw.Draw(img)
    d.rectangle((x, y, x + w - 1, y + h - 1), fill=fill, outline=outline)


def bevel(img, x, y, w, h, fill, hi, lo, edge=None):
    """A pixel-art plate: 1 px dark edge, 1 px highlight on top/left, 1 px shade bottom/right."""
    rect(img, x, y, w, h, edge or C['ink'])
    rect(img, x + 1, y + 1, w - 2, h - 2, fill)
    rect(img, x + 1, y + 1, w - 2, 1, hi)
    rect(img, x + 1, y + h - 2, w - 2, 1, lo)


def blend(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def tile(img, t, x0, y0, w, h):
    for ty in range(y0, y0 + h, t.height):
        for tx in range(x0, x0 + w, t.width):
            img.alpha_composite(t.crop((0, 0, min(t.width, x0 + w - tx), min(t.height, y0 + h - ty))), (tx, ty))


def blit(img, s, x, y):
    img.alpha_composite(s, (int(x), int(y)))


def shade(img, box, color, alpha):
    ov = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).rectangle(box, fill=color + (alpha,))
    img.alpha_composite(ov)


# ---- tiers & grid (REDESIGN.md §2) -------------------------------------------------------------------------------------
class Grid:
    def __init__(self, W, H):
        self.W, self.H = W, H
        self.t3 = H >= 330
        self.m = 8 if self.t3 else 6
        self.tb = 26 if self.t3 else 22          # top bar
        self.db = 56 if self.t3 else 44          # dock
        self.py0 = self.tb
        self.py1 = H - self.db
        self.ph = self.py1 - self.py0
        self.card = (49, 65) if self.t3 else (37, 49)        # hero cards (you, dealer, board)
        self.card2 = (37, 49) if self.t3 else (21, 29)       # other seats
        self.chip = 32 if self.t3 else 24                    # tray chip
        self.bchip = 16 if self.t3 else 12                   # chip on the felt
        self.btn_h = 36 if self.t3 else 30
        self.prim_w = 132 if self.t3 else 92


# ---- art ---------------------------------------------------------------------------------------------------------------
FELT = tex('gui/tables/core/felt_village.png')
FACES_L = tex('gui/core/cards/faces_l.png')
FACES_M = tex('gui/core/cards/faces_m.png')
BACKS = tex('gui/core/cards/backs.png')
SHOE = tex('gui/sprites/core/cards/prop/shoe_village.png')
RACK = tex('gui/sprites/core/cards/prop/rack_village.png')
TRAY = tex('gui/sprites/core/cards/prop/tray_village.png')
SUITCOL = [(35, 28, 56), (212, 42, 58), (42, 94, 216), (15, 126, 102)]
RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K']


def card_img(code, size):
    """code = suit*13 + rank-1; size (w, h); -1 = back. XL is the L art scaled (the generator draws XL natively)."""
    w, h = size
    if code < 0:
        col = {37: 0, 21: 37, 13: 58}.get(w, 0)
        base = BACKS.crop((col, 2 * 49, col + (w if w in (37, 21) else 37), 2 * 49 + (h if w in (37, 21) else 49)))
        return base if base.size == size else base.resize(size, Image.NEAREST)
    r, s = code % 13, code // 13
    if w == 21:
        im = FACES_M.crop((r * 21, s * 29, r * 21 + 21, s * 29 + 29)).copy()
    else:
        im = FACES_L.crop((r * 37, s * 49, r * 37 + 37, s * 49 + 49)).copy()
        if size != (37, 49):
            im = im.resize(size, Image.NEAREST)
    text(im, 2, 2, RANKS[r], SUITCOL[s], shadow=False)
    return im


def draw_card(img, code, x, y, size, dim=False, glow=False):
    w, h = size
    shade(img, (x + 1, y + 2, x + w, y + h + 1), (0, 0, 0), 90)
    if glow:
        rect(img, x - 2, y - 2, w + 4, h + 4, C['gold'])
    c = card_img(code, size)
    if dim:
        c = Image.blend(c, Image.new('RGBA', c.size, (40, 30, 50, 255)), 0.45)
        c.putalpha(card_img(code, size).split()[3])
    blit(img, c, x, y)


def cards(img, codes, x, y, size, step, **kw):
    for i, c in enumerate(codes):
        draw_card(img, c, x + i * step, y, size, **kw)


def fan_width(n, size, step):
    return size[0] + (n - 1) * step


def chip(img, x, y, d, denom, selected=False, label=True):
    """chip_v2: a top-down chip with its value printed big in the centre (new sprite, REDESIGN.md §9)."""
    base, stripe, ink = CHIP[denom]
    im = Image.new('RGBA', (d, d), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    dr.ellipse((0, 0, d - 1, d - 1), fill=C['ink'])
    dr.ellipse((1, 1, d - 2, d - 2), fill=base)
    for k in range(8):
        a = k * math.pi / 4
        cx, cy = (d - 1) / 2 + math.cos(a) * (d / 2 - 3), (d - 1) / 2 + math.sin(a) * (d / 2 - 3)
        dr.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=stripe)
    inner = d // 2 - 5
    dr.ellipse((d // 2 - inner - 1, d // 2 - inner - 1, d // 2 + inner, d // 2 + inner), fill=blend(base, (0, 0, 0), 0.25))
    if label:
        s = str(denom)
        tw = F.width(s)
        F.draw(im, (d - tw) // 2, (d - 8) // 2 + 1, s, ink if denom != 1 else (30, 30, 40), 1, shadow=denom != 1)
    if selected:
        rect(img, x - 2, y + d + 1, d + 4, 2, C['gold'])
        y -= 3
        ImageDraw.Draw(img).ellipse((x - 2, y - 2, x + d + 1, y + d + 1), outline=C['gold'], width=2)
    blit(img, im, x, y)


def felt_chip(img, cx, cy, d, amount, label=True, mine=True):
    """A bet on the felt: a small stack + its amount in a pill to the right (never smaller than 1x text)."""
    denom = max([v for v in CHIP if v <= amount] or [1])
    for k in range(min(4, 1 + amount // max(denom, 1) // 2)):
        im = Image.new('RGBA', (d, d), (0, 0, 0, 0))
        base, stripe, _ = CHIP[denom]
        ImageDraw.Draw(im).ellipse((0, 0, d - 1, d - 1), fill=C['ink'])
        ImageDraw.Draw(im).ellipse((1, 1, d - 2, d - 2), fill=base)
        ImageDraw.Draw(im).ellipse((3, 3, d - 4, d - 4), outline=stripe)
        blit(img, im, cx - d // 2, cy - d // 2 - 2 * k)
    if label:
        s = str(amount)
        w = F.width(s) + 5
        x = cx + d // 2 - 2
        y = cy - d // 2 - 9 - 2 * min(3, amount // max(denom, 1) // 2)
        bevel(img, x, y, w, 11, C['you'] if mine else C['panel'], C['text'] if mine else C['panel.hi'], C['ink'], C['ink'])
        text(img, x + 3, y + 2, s, C['ink'] if mine else C['text'], shadow=False)


def pill(img, x, y, s, fg=C['text'], bg=C['panel'], scale=1, align='l'):
    w = F.width(s, scale) + 6 * scale // max(1, scale) + (4 if scale > 1 else 0)
    h = 8 * scale + 5
    if align == 'c':
        x -= w // 2
    elif align == 'r':
        x -= w
    bevel(img, x, y, w, h, bg, blend(bg, (255, 255, 255), 0.18), blend(bg, (0, 0, 0), 0.35))
    text(img, x + (w - F.width(s, scale)) // 2, y + 3, s, fg, scale)
    return w


def stamp(img, cx, cy, s, kind, scale=2):
    col = {'win': (46, 140, 40), 'lose': (176, 36, 46), 'push': (74, 90, 120), 'bj': C['gold.shade']}[kind]
    fg = {'win': C['text'], 'lose': C['text'], 'push': C['text'], 'bj': C['gold']}[kind]
    w = F.width(s, scale) + 10
    h = 8 * scale + 8
    x, y = cx - w // 2, cy - h // 2
    bevel(img, x, y, w, h, col, blend(col, (255, 255, 255), 0.3), blend(col, (0, 0, 0), 0.4), C['ink'])
    rect(img, x + 2, y + 2, w - 4, 1, blend(col, (255, 255, 255), 0.45))
    text(img, cx, y + 4, s, fg, scale, 'c')


def button(img, x, y, w, h, label, kind='normal', key=None, icon=None):
    pal = {'primary': (C['gold'], (255, 240, 160), C['gold.shade'], C['ink']),
           'normal': ((78, 40, 124), (112, 70, 164), (40, 18, 64), C['text']),
           'danger': ((150, 30, 40), (200, 60, 70), (90, 16, 24), C['text']),
           'disabled': ((44, 30, 58), (54, 40, 70), (30, 20, 40), (120, 104, 140))}[kind]
    bevel(img, x, y, w, h, pal[0], pal[1], pal[2], C['ink'])
    rect(img, x + 1, y + h - 3, w - 2, 1, pal[2])
    lw = F.width(label)
    kx = 0
    if key:
        kw = F.width(key) + 4
        kx = kw + 4
    tx = x + (w - lw - kx) // 2
    text(img, tx, y + (h - 8) // 2, label, pal[3], shadow=kind != 'primary')
    if key:
        kxx = tx + lw + 4
        bevel(img, kxx, y + (h - 10) // 2, F.width(key) + 4, 10, blend(pal[0], (0, 0, 0), 0.3), blend(pal[0], (0, 0, 0), 0.2),
              blend(pal[0], (0, 0, 0), 0.5), blend(pal[0], (0, 0, 0), 0.6))
        text(img, kxx + 2, y + (h - 10) // 2 + 1, key, pal[3] if kind != 'primary' else C['ink'], shadow=False)


def icon_button(img, x, y, sz, glyph, active=False, hint=None):
    bevel(img, x, y, sz, sz, C['btn.hi'] if active else C['btn'], (96, 56, 140), (34, 14, 56))
    text(img, x + sz // 2, y + (sz - 8) // 2, glyph, C['gold'] if active else C['text'], align='c')


def plate(img, x, y, name, face=(120, 90, 60), you=False, active=False, w=74, sub=None):
    """plate_v2: one line, avatar + name, never any numbers (those live on the felt)."""
    h = 18
    edge = C['you'] if you else (C['gold'] if active else C['edge'])
    bevel(img, x, y, w, h, C['panel'], C['panel.hi'], C['ink'], C['ink'])
    rect(img, x, y, w, 1, edge)
    rect(img, x, y + h - 1, w, 1, edge)
    rect(img, x + 2, y + 2, 14, 14, face)
    rect(img, x + 4, y + 7, 3, 2, (255, 255, 255))
    rect(img, x + 10, y + 7, 3, 2, (255, 255, 255))
    rect(img, x + 5, y + 7, 1, 2, C['ink'])
    rect(img, x + 11, y + 7, 1, 2, C['ink'])
    s = name
    while F.width(s) > w - 22 and len(s) > 2:
        s = s[:-2] + '.'
    text(img, x + 19, y + 5, s, C['you'] if you else C['text'])
    if active:
        rect(img, x + 2, y + h - 3, w - 4, 1, C['gold'])


def timer_bar(img, x, y, w, frac, urgent=False):
    rect(img, x, y, w, 3, C['ink'])
    rect(img, x + 1, y + 1, int((w - 2) * frac), 1, C['lose'] if urgent else C['gold'])


# ---- frame: top bar, felt, dock (REDESIGN.md §3) -----------------------------------------------------------------------
def frame(g, title, limits, balance, delta=None, players=None):
    img = Image.new('RGBA', (g.W, g.H), C['bg'] + (255,))
    tile(img, FELT, 0, g.py0, g.W, g.ph)
    # vignette on the felt edges
    ov = Image.new('RGBA', (g.W, g.H), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    for k in range(10):
        od.rectangle((k, g.py0 + k, g.W - 1 - k, g.py1 - 1 - k), outline=(0, 0, 0, 70 - k * 7))
    img.alpha_composite(ov)
    # top bar
    rect(img, 0, 0, g.W, g.tb, C['ink'])
    rect(img, 0, g.tb - 2, g.W, 1, C['gold.shade'])
    rect(img, 0, g.tb - 1, g.W, 1, C['rail.dark'])
    ty = (g.tb - 8) // 2
    x = g.m
    x += pill(img, x, ty - 3, title, C['gold'], C['panel']) + 4
    x += pill(img, x, ty - 3, limits, C['muted'], C['panel']) + 4
    if players:
        pill(img, x, ty - 3, players, C['muted'], C['panel'])
    # right: balance pill (big), then icons
    bx = g.W - g.m
    s = balance
    bw = F.width(s) + 22
    bevel(img, bx - bw, ty - 3, bw, 14, C['panel'], C['panel.hi'], C['ink'], C['gold.shade'])
    chip(img, bx - bw + 2, ty - 1, 10, 25, label=False)
    text(img, bx - 4, ty, s, C['gold'], align='r')
    if delta:
        pill(img, bx - bw - 4, ty - 3, delta, C['ink'], C['win'] if delta.startswith('+') else C['lose'], align='r')
        bw += F.width(delta) + 12
    ix = bx - bw - 6
    for glyph in ('x', '?', '#'):
        ix -= 16
        icon_button(img, ix, ty - 3, 14, glyph)
    # dock
    rect(img, 0, g.py1, g.W, g.db, C['ink'])
    rect(img, 0, g.py1, g.W, 1, C['gold.shade'])
    rect(img, 0, g.py1 + 1, g.W, 1, C['rail.dark'])
    return img


def chip_tray(img, g, x, y, sel=25, denoms=(1, 5, 25, 100, 500)):
    p = g.chip + (4 if g.t3 else 2)
    for i, d in enumerate(denoms):
        chip(img, x + i * p, y, g.chip, d, selected=d == sel)
    return x + len(denoms) * p


def bet_tools(img, g, x, y):
    sz = 20 if not g.t3 else 24
    for i, glyph in enumerate(('<', 'x', 'R', '2')):
        icon_button(img, x + i * (sz + 2), y, sz, glyph)
    return x + 4 * (sz + 2)


def status(img, x, y, line1, line2=None, c1=C['gold'], big=None):
    text(img, x, y, line1, c1)
    if line2:
        text(img, x, y + 11, line2, C['muted'])


def save(img, name, scale=4):
    img = img.convert('RGB').resize((img.width * scale, img.height * scale), Image.NEAREST)
    img.save(os.path.join(OUT, name))
    print('wrote', name, img.size)


def felt_print(img, cx, y, s, alpha=150):
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    text(layer, cx, y, s, C['felt.print'], align='c', shadow=False)
    a = layer.split()[3].point(lambda v: v * alpha // 255)
    layer.putalpha(a)
    img.alpha_composite(layer)


def arc_rail(img, g, cx, cy, rx, ry, th=7):
    """The player-side padded rail: an ellipse arc (drawn under the dock)."""
    d = ImageDraw.Draw(img)
    d.ellipse((cx - rx - th, cy - ry - th, cx + rx + th, cy + ry + th), outline=C['rail.dark'], width=th + 2)
    d.ellipse((cx - rx - th + 1, cy - ry - th + 1, cx + rx + th - 1, cy + ry + th - 1), outline=C['rail'], width=th)
    d.ellipse((cx - rx - 3, cy - ry - 3, cx + rx + 3, cy + ry + 3), outline=C['rail.hi'], width=1)
    d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), outline=C['gold.shade'], width=1)


def dealer_rail(img, g):
    rect(img, 0, g.py0, g.W, 8, C['rail'])
    rect(img, 0, g.py0 + 1, g.W, 1, C['rail.hi'])
    rect(img, 0, g.py0 + 7, g.W, 1, C['gold.shade'])


# ======================================================================================================================
# Blackjack
# ======================================================================================================================
def bj_seats(g):
    """Seat anchors (cards' bottom-centre) as fractions: x of W, y of the play area. You are always seat 2."""
    fr = [(0.10, 0.56), (0.27, 0.74), (0.50, 0.80), (0.73, 0.74), (0.90, 0.56)]
    return [(int(g.W * fx), int(g.py0 + g.ph * fy)) for fx, fy in fr]


def blackjack(W, H, name):
    g = Grid(W, H)
    img = frame(g, 'Blackjack', '1 - 100', '12 450', players='4/5')
    dealer_rail(img, g)
    blit(img, RACK, g.W // 2 - 40, g.py0 + 1)
    blit(img, SHOE, g.W - g.m - 52, g.py0 + 12)
    blit(img, TRAY, g.m + 10, g.py0 + 12)
    # dealer
    cw, ch = g.card
    step = cw // 2 + 2
    dc = [13 * 1 + 9, -1]
    fx = g.W // 2 - fan_width(2, g.card, step) // 2
    dy = g.py0 + 14
    cards(img, dc, fx, dy, g.card, step)
    pill(img, fx - 6, dy + 6, '10', C['text'], C['panel'], scale=2, align='r')
    text(img, fx + fan_width(2, g.card, step) + 6, dy + 8, 'Dealer', C['muted'])
    text(img, fx + fan_width(2, g.card, step) + 6, dy + 19, 'stands on 17', C['muted'])
    # felt prints
    py = dy + ch + 10
    felt_print(img, g.W // 2, py, 'BLACKJACK PAYS 3 TO 2')
    felt_print(img, g.W // 2, py + 11, 'INSURANCE PAYS 2 TO 1', 110)
    seats = bj_seats(g)
    arc_rail(img, g, g.W // 2, g.py0 - int(g.ph * 0.05), int(g.W * 0.52), int(g.ph * 0.98))
    hands = [
        ('Creeper42', [13 * 2 + 9, 13 * 3 + 5, 13 * 0 + 6], 25, 'BUST', 'lose'),
        ('Mira', [0, 13 * 2 + 12], 50, 'BLACKJACK', 'bj'),
        ('You', [13 * 0 + 7, 13 * 1 + 2], 50, None, None),
        (None, None, 0, None, None),
        ('Axolotl Al', [13 * 3 + 8, 13 * 1 + 7], 10, None, None),
    ]
    faces = [(70, 140, 60), (230, 170, 140), (120, 90, 60), None, (220, 120, 170)]
    for i, (sx, sy) in enumerate(seats):
        nm, hc, bet, st, kind = hands[i]
        you = i == 2
        size = g.card if you else g.card2
        stp = (size[0] // 2 + 2) if you else size[0] // 2 + 1
        pw = 80 if g.t3 else 74
        plate_y = sy + (6 if not you else 10)
        if nm is None:
            # empty seat: a dashed spot and a "sit" hint
            ImageDraw.Draw(img).ellipse((sx - 12, sy - 26, sx + 12, sy - 2), outline=blend(C['felt'], C['felt.print'], 0.5))
            felt_print(img, sx, sy - 18, 'Open', 120)
            continue
        fw = fan_width(len(hc), size, stp)
        cy = sy - size[1] - 18
        cards(img, hc, sx - fw // 2, cy, size, stp, dim=kind == 'lose', glow=you)
        tot = {0: '25', 1: '21', 2: '10', 4: '17'}[i] if i != 0 else '25'
        if you:
            pill(img, sx - fw // 2 - 4, cy + 2, tot, C['gold'], C['panel'], scale=2, align='r')
        else:
            pill(img, sx, cy - 15, tot, C['text'], C['panel'], align='c')
        felt_chip(img, sx - 6, sy - 9, g.bchip, bet, mine=you)
        if st:
            stamp(img, sx, cy + size[1] // 2, st, kind, scale=1)
        plate(img, sx - pw // 2, plate_y, nm, faces[i], you=you, active=you, w=pw)
    # dock: decision state
    by = g.py1 + (g.db - g.btn_h) // 2
    status(img, g.m + 2, g.py1 + 8, 'Your turn', 'Bet 50  -  hand 1 of 1')
    timer_bar(img, g.m + 2, g.py1 + 31, 110, 0.7)
    labels = [('Hit', 'H'), ('Stand', 'S'), ('Double', 'D'), ('Split', 'P')]
    bw = 84 if g.t3 else 70
    x0 = g.W - g.m - 4 * bw - 3 * 4
    for i, (lb, k) in enumerate(labels):
        button(img, x0 + i * (bw + 4), by, bw, g.btn_h, lb, 'disabled' if lb == 'Split' else 'normal', key=k)
    save(img, name)


def blackjack_betting(W, H, name):
    g = Grid(W, H)
    img = frame(g, 'Blackjack', '1 - 100', '12 500', players='3/5')
    dealer_rail(img, g)
    blit(img, RACK, g.W // 2 - 40, g.py0 + 1)
    blit(img, SHOE, g.W - g.m - 52, g.py0 + 12)
    blit(img, TRAY, g.m + 10, g.py0 + 12)
    felt_print(img, g.W // 2, g.py0 + 74, 'BLACKJACK PAYS 3 TO 2')
    felt_print(img, g.W // 2, g.py0 + 85, 'INSURANCE PAYS 2 TO 1', 110)
    arc_rail(img, g, g.W // 2, g.py0 - int(g.ph * 0.05), int(g.W * 0.52), int(g.ph * 0.98))
    seats = bj_seats(g)
    for i, (sx, sy) in enumerate(seats):
        if i == 2:
            # your spot: pulsing gold ring + the hint
            d = ImageDraw.Draw(img)
            d.ellipse((sx - 18, sy - 40, sx + 18, sy - 4), outline=C['gold'], width=2)
            d.ellipse((sx - 22, sy - 44, sx + 22, sy), outline=blend(C['gold'], C['felt'], 0.6), width=1)
            felt_chip(img, sx, sy - 22, g.bchip + 2, 50)
            pill(img, sx, sy - 62, 'Your bet', C['ink'], C['gold'], align='c')
            plate(img, sx - 37, sy + 10, 'You', (120, 90, 60), you=True, active=True)
        elif i in (1, 4):
            felt_chip(img, sx, sy - 22, g.bchip, 25 if i == 1 else 10, mine=False)
            plate(img, sx - 37, sy + 6, ['', 'Mira', '', '', 'Axolotl Al'][i], (230, 170, 140))
        else:
            ImageDraw.Draw(img).ellipse((sx - 12, sy - 34, sx + 12, sy - 10), outline=blend(C['felt'], C['felt.print'], 0.5))
            felt_print(img, sx, sy - 26, 'Open', 120)
    # dock: the shared BetBar
    ty = g.py1 + (g.db - g.chip) // 2
    x = chip_tray(img, g, g.m + 2, ty)
    x = bet_tools(img, g, x + 6, g.py1 + (g.db - 20) // 2)
    text(img, x + 8, g.py1 + 8, 'Bet', C['muted'])
    text(img, x + 8, g.py1 + 19, '50', C['gold'], scale=2)
    status(img, x + 52, g.py1 + 8, 'Chips add to your bet', 'Deal in 12 s', C['text'])
    button(img, g.W - g.m - g.prim_w, g.py1 + (g.db - g.btn_h) // 2, g.prim_w, g.btn_h, 'Deal', 'primary', key='Space')
    save(img, name)


def blackjack_result(W, H, name):
    g = Grid(W, H)
    img = frame(g, 'Blackjack', '1 - 100', '12 550', delta='+125', players='4/5')
    dealer_rail(img, g)
    blit(img, RACK, g.W // 2 - 40, g.py0 + 1)
    blit(img, SHOE, g.W - g.m - 52, g.py0 + 12)
    cw, ch = g.card
    step = cw // 2 + 2
    dc = [13 * 1 + 9, 13 * 0 + 6, 13 * 2 + 1]
    fx = g.W // 2 - fan_width(3, g.card, step) // 2
    dy = g.py0 + 14
    cards(img, dc, fx, dy, g.card, step)
    pill(img, fx - 6, dy + 6, '19', C['text'], C['panel'], scale=2, align='r')
    arc_rail(img, g, g.W // 2, g.py0 - int(g.ph * 0.05), int(g.W * 0.52), int(g.ph * 0.98))
    sx, sy = bj_seats(g)[2]
    hc = [0, 13 * 3 + 12]
    fw = fan_width(2, g.card, step)
    cards(img, hc, sx - fw // 2, sy - ch - 18, g.card, step, glow=True)
    felt_chip(img, sx - 20, sy - 9, g.bchip, 50)
    felt_chip(img, sx + 14, sy - 9, g.bchip, 75)
    stamp(img, sx, sy - ch - 18 + ch // 2, 'BLACKJACK +75', 'bj', 1)
    plate(img, sx - 37, sy + 10, 'You', (120, 90, 60), you=True, active=True)
    # the round banner (1.5 s, then it folds into the dock status)
    bw, bh = 232, 28
    bx, by = g.W // 2 - bw // 2, dy + ch + 3
    bevel(img, bx, by, bw, bh, C['panel'], C['panel.hi'], C['ink'], C['gold'])
    text(img, bx + 10, by + 6, 'YOU WIN', C['gold'], 2)
    text(img, bx + bw - 10, by + 6, '+125', C['win'], 2, 'r')
    status(img, g.m + 2, g.py1 + 8, 'Blackjack! +75', 'Next round in 3 s', C['win'])
    button(img, g.W - g.m - g.prim_w, g.py1 + (g.db - g.btn_h) // 2, g.prim_w, g.btn_h, 'Rebet 50', 'primary', key='Space')
    button(img, g.W - g.m - g.prim_w - 74, g.py1 + (g.db - g.btn_h) // 2, 70, g.btn_h, 'Leave', 'normal')
    save(img, name)


# ======================================================================================================================
# Roulette
# ======================================================================================================================
REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
WHEEL_ORDER = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18,
               29, 7, 28, 12, 35, 3, 26]


def wheel(img, cx, cy, r, angle=0.0, ball=None):
    """A procedural top-down wheel (stand-in for wheel_bowl_m / head_m; the generator draws them)."""
    d = ImageDraw.Draw(img)
    d.ellipse((cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4), fill=C['rail.dark'])
    d.ellipse((cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2), fill=C['rail'])
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=C['gold.shade'])
    n = 37
    for i, num in enumerate(WHEEL_ORDER):
        a0 = angle + i * 360 / n - 90
        col = C['green'] if num == 0 else (C['red'] if num in REDS else C['black'])
        d.pieslice((cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2), a0, a0 + 360 / n, fill=col, outline=C['gold.shade'])
    ri = int(r * 0.62)
    d.ellipse((cx - ri, cy - ri, cx + ri, cy + ri), fill=(120, 70, 30), outline=C['gold'])
    d.ellipse((cx - ri + 4, cy - ri + 4, cx + ri - 4, cy + ri - 4), fill=(96, 56, 24))
    for k in range(4):
        a = math.radians(angle + k * 90)
        d.line((cx, cy, cx + math.cos(a) * ri * 0.8, cy + math.sin(a) * ri * 0.8), fill=C['gold'], width=2)
    d.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=C['gold'])
    if ball is not None:
        a = math.radians(ball)
        bx, by = cx + math.cos(a) * (r * 0.82), cy + math.sin(a) * (r * 0.82)
        d.ellipse((bx - 2, by - 2, bx + 2, by + 2), fill=(250, 250, 250), outline=C['ink'])


def roulette(W, H, name, spinning=False):
    g = Grid(W, H)
    img = frame(g, 'European Roulette', '1 - 500', '12 090', players='2')
    # left column: wheel + history
    col_w = int(g.W * (0.26 if g.t3 else 0.25))
    r = col_w // 2 - 8
    wcx, wcy = g.m + col_w // 2, g.py0 + 10 + r + 4
    wheel(img, wcx, wcy, r, 13 if not spinning else 47, ball=200 if spinning else None)
    hy = wcy + r + 12
    text(img, g.m + 4, hy, 'Last numbers', C['muted'])
    hist = [17, 0, 32, 8, 19, 26, 3, 11]
    for i, n in enumerate(hist):
        col = C['green'] if n == 0 else (C['red'] if n in REDS else C['black'])
        x = g.m + 4 + (i % 4) * 26
        y = hy + 12 + (i // 4) * 15
        bevel(img, x, y, 23, 13, col, blend(col, (255, 255, 255), 0.25), blend(col, (0, 0, 0), 0.4))
        text(img, x + 12, y + 3, str(n), C['text'], align='c')
        if i == 0:
            rect(img, x - 1, y - 1, 25, 1, C['gold'])
    # the layout (cells scale with the tier)
    cell = 30 if g.t3 else 23
    lx = g.m + col_w + 6
    lw = g.W - g.m - lx
    cell = min(cell, (lw - 4) // 14)
    oh0 = 17 if not g.t3 else 22
    ch_ = min(int(cell * 1.8), (g.ph - 10 - 2 * oh0 - 34) // 3)
    gx = lx + cell
    gy = g.py0 + 10
    d = ImageDraw.Draw(img)
    rect(img, lx, gy, cell, ch_ * 3, C['green'], C['felt.print'])
    text(img, lx + cell // 2, gy + ch_ * 3 // 2 - 4, '0', C['text'], align='c')
    for c in range(12):
        for rr in range(3):
            n = c * 3 + (3 - rr)
            col = C['red'] if n in REDS else C['black']
            x, y = gx + c * cell, gy + rr * ch_
            rect(img, x, y, cell, ch_, blend(C['felt'], (0, 0, 0), 0.1), C['felt.print'])
            bevel(img, x + 2, y + 2, cell - 4, ch_ - 4, col, blend(col, (255, 255, 255), 0.2), blend(col, (0, 0, 0), 0.35), col)
            text(img, x + cell // 2, y + ch_ // 2 - 4, str(n), C['text'], align='c')
    for rr in range(3):
        rect(img, gx + 12 * cell, gy + rr * ch_, cell, ch_, C['felt'], C['felt.print'])
        text(img, gx + 12 * cell + cell // 2, gy + rr * ch_ + ch_ // 2 - 4, '2:1', C['felt.print'], align='c', shadow=False)
    oh = 17 if not g.t3 else 22
    for k, s in enumerate(['1st 12', '2nd 12', '3rd 12']):
        x = gx + k * 4 * cell
        rect(img, x, gy + 3 * ch_, 4 * cell, oh, C['felt'], C['felt.print'])
        text(img, x + 2 * cell, gy + 3 * ch_ + oh // 2 - 4, s, C['felt.print'], align='c', shadow=False)
    for k, s in enumerate(['1-18', 'EVEN', 'RED', 'BLACK', 'ODD', '19-36']):
        x = gx + k * 2 * cell
        y = gy + 3 * ch_ + oh
        rect(img, x, y, 2 * cell, oh, C['felt'], C['felt.print'])
        if s == 'RED':
            d.polygon([(x + cell, y + 3), (x + 2 * cell - 6, y + oh // 2), (x + cell, y + oh - 4), (x + 6, y + oh // 2)], fill=C['red'])
        elif s == 'BLACK':
            d.polygon([(x + cell, y + 3), (x + 2 * cell - 6, y + oh // 2), (x + cell, y + oh - 4), (x + 6, y + oh // 2)], fill=C['black'])
        else:
            text(img, x + cell, y + oh // 2 - 4, s, C['felt.print'], align='c', shadow=False)
    lay_bottom = gy + 3 * ch_ + 2 * oh
    # bets (chips with readable value pills)
    def at(num):
        c = (num - 1) // 3
        rr = 3 - ((num - 1) % 3 + 1)
        return gx + c * cell + cell // 2, gy + rr * ch_ + ch_ // 2
    bets = [(at(17), 10), ((at(8)[0] + cell // 2, at(8)[1]), 5), ((at(20)[0] + cell // 2, at(20)[1] - ch_ // 2), 5),
            ((gx + 5 * cell, gy + 3 * ch_ + oh + oh // 2), 10), ((gx + 6 * cell, gy + 3 * ch_ + oh // 2), 25)]
    for (bx, by), a in bets:
        felt_chip(img, bx, by, g.bchip, a)
    if spinning:
        shade(img, (lx, gy, g.W - g.m, lay_bottom), C['scrim'], 60)
        x, y = at(26)
        rect(img, x - cell // 2, y - ch_ // 2, cell, ch_, None, C['gold'])
    # racetrack strip (call bets) as a tab row
    ry = lay_bottom + 8
    tabs = ['Tiers', 'Orphelins', 'Voisins', 'Zero']
    tw = (g.W - g.m - lx) // 4
    for k, s in enumerate(tabs):
        button(img, lx + k * tw, ry, tw - 4, 18 if not g.t3 else 22, s, 'normal')
    # dock
    ty = g.py1 + (g.db - g.chip) // 2
    if spinning:
        status(img, g.m + 2, g.py1 + 8, 'No more bets', 'The ball is spinning...', C['gold'])
        text(img, g.W // 2, g.py1 + 8, 'Your bets', C['muted'], align='c')
        text(img, g.W // 2, g.py1 + 19, '55', C['gold'], 2, 'c')
        button(img, g.W - g.m - g.prim_w, g.py1 + (g.db - g.btn_h) // 2, g.prim_w, g.btn_h, 'Spinning', 'disabled')
    else:
        x = chip_tray(img, g, g.m + 2, ty, sel=5)
        x = bet_tools(img, g, x + 6, g.py1 + (g.db - 20) // 2)
        text(img, x + 8, g.py1 + 8, 'Bet', C['muted'])
        text(img, x + 8, g.py1 + 19, '55', C['gold'], scale=2)
        status(img, x + 52, g.py1 + 8, 'Click the layout', 'Spin in 14 s', C['text'])
        button(img, g.W - g.m - g.prim_w, g.py1 + (g.db - g.btn_h) // 2, g.prim_w, g.btn_h, 'Spin', 'primary', key='Space')
    save(img, name)


# ======================================================================================================================
# Poker (Texas Hold'em)
# ======================================================================================================================
def poker(W, H, name):
    g = Grid(W, H)
    img = frame(g, "Texas Hold'em", '1/2', '12 150', players='5/6')
    cx, cy = g.W // 2, g.py0 + g.ph // 2 - 4
    rx, ry = int(g.W * 0.43), int(g.ph * 0.40)
    d = ImageDraw.Draw(img)
    # the dark room outside the oval, the oval rail
    shade(img, (0, g.py0, g.W, g.py1), (10, 4, 18), 120)
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=255)
    felt = Image.new('RGBA', img.size)
    tile(felt, FELT, 0, 0, g.W, g.H)
    img.paste(felt, (0, 0), mask)
    d.ellipse((cx - rx - 9, cy - ry - 9, cx + rx + 9, cy + ry + 9), outline=C['rail.dark'], width=2)
    d.ellipse((cx - rx - 8, cy - ry - 8, cx + rx + 8, cy + ry + 8), outline=C['rail'], width=7)
    d.ellipse((cx - rx - 5, cy - ry - 5, cx + rx + 5, cy + ry + 5), outline=C['rail.hi'], width=1)
    d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), outline=C['gold.shade'], width=1)
    # board + pot
    cw, ch = g.card
    gap = 4
    bw = 5 * cw + 4 * gap
    bx, by = cx - bw // 2, cy - ch // 2 - 8
    board = [13 * 0 + 12, 13 * 1 + 6, 13 * 3 + 12, -2, -2]
    for i, c in enumerate(board):
        x = bx + i * (cw + gap)
        if c == -2:
            rect(img, x, by, cw, ch, None, blend(C['felt'], C['felt.print'], 0.4))
        else:
            draw_card(img, c, x, by, g.card)
    pw_ = F.width('Pot') + 6 + F.width('120', 2)
    text(img, cx - pw_ // 2, by + ch + 10, 'Pot', C['muted'])
    text(img, cx + pw_ // 2, by + ch + 5, '120', C['gold'], 2, 'r')
    # seats around the oval; you bottom centre
    seats = [(0.5, 1.0, 'You'), (0.10, 0.72, 'Redstone Rick'), (0.10, 0.18, 'Lady Luck'), (0.5, -0.05, 'Iron Ivan'),
             (0.90, 0.18, 'Diamond Dora'), (0.90, 0.72, None)]
    acts = {1: ('Call 10', 10), 2: ('Fold', 0), 3: ('Raise 20', 20), 4: ('Check', 0)}
    for i, (fx, fy, nm) in enumerate(seats):
        sx = int(cx + (fx - 0.5) * 2 * rx * 1.02)
        sy = int(cy - ry + fy * 2 * ry)
        pw = 84
        if nm is None:
            pill(img, sx, sy - 6, 'Open seat', C['muted'], C['panel'], align='c')
            continue
        if i == 0:
            hc = [13 * 0 + 0, 13 * 0 + 11]
            fw = fan_width(2, g.card, cw // 2 + 4)
            ytop = g.py1 - ch - 4
            cards(img, hc, sx - fw // 2, ytop, g.card, cw // 2 + 4, glow=True)
            pill(img, sx + fw // 2 + 6, ytop + 4, 'Two pair', C['gold'], C['panel'])
            felt_chip(img, sx + fw // 2 + 14, ytop + 34, g.bchip, 10)
            plate(img, sx - fw // 2 - pw - 8, g.py1 - 22, nm, (120, 90, 60), you=True, active=True, w=pw)
            pill(img, sx - fw // 2 - 8, g.py1 - 38, '188', C['gold'], C['panel'], align='r')
            continue
        tx = sx + (24 if fx < 0.5 else -24 if fx > 0.5 else 52)
        ty = sy + (14 if fy < 0.5 else -26)
        folded = i == 2
        if not folded:
            cards(img, [-1, -1], tx - 12, ty - 4, g.card2, 10)
        py = sy - 9
        px = sx - pw // 2
        if fx < 0.3:
            px = g.m
        elif fx > 0.7:
            px = g.W - g.m - pw - 30
        plate(img, px, py, nm, [(70, 140, 60), (230, 170, 140), (160, 160, 170), (90, 60, 140)][i - 1], active=i == 3, w=pw)
        pill(img, px + pw + 2, py, ['200', '0', '176', '200'][i - 1] if not folded else 'Fold', C['muted'] if folded else C['gold'], C['panel'])
        a, amt = acts[i]
        if amt:
            felt_chip(img, int(sx + (cx - sx) * 0.38), int(sy + (cy - sy) * 0.38), g.bchip, amt, mine=False)
    # dealer button
    d.ellipse((cx + 70, cy + 44, cx + 82, cy + 56), fill=C['text'], outline=C['ink'])
    text(img, cx + 76, cy + 46, 'D', C['ink'], align='c', shadow=False)
    # dock: action row with a raise stepper
    status(img, g.m + 2, g.py1 + 8, 'Your move  -  to call 10', 'Auto-fold in 24 s', C['gold'])
    timer_bar(img, g.m + 2, g.py1 + 31, 120, 0.8)
    by = g.py1 + (g.db - g.btn_h) // 2
    x = g.W - g.m - g.prim_w
    button(img, x, by, g.prim_w, g.btn_h, 'Raise to 40', 'primary', key='R')
    sx0 = x - 4 - 66
    bevel(img, sx0, by, 66, g.btn_h, C['panel'], C['panel.hi'], C['ink'])
    text(img, sx0 + 6, by + (g.btn_h - 8) // 2, '-', C['gold'])
    text(img, sx0 + 33, by + (g.btn_h - 8) // 2, '40', C['text'], align='c')
    text(img, sx0 + 58, by + (g.btn_h - 8) // 2, '+', C['gold'])
    button(img, sx0 - 4 - 70, by, 70, g.btn_h, 'Call 10', 'normal', key='C')
    button(img, sx0 - 8 - 140, by, 66, g.btn_h, 'Fold', 'danger', key='F')
    save(img, name)


# ======================================================================================================================
# Craps
# ======================================================================================================================
def die(img, x, y, s, v):
    bevel(img, x, y, s, s, (246, 240, 232), (255, 255, 255), (200, 190, 180), C['ink'])
    pips = {1: [(1, 1)], 2: [(0, 0), (2, 2)], 3: [(0, 0), (1, 1), (2, 2)], 4: [(0, 0), (2, 0), (0, 2), (2, 2)],
            5: [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)], 6: [(0, 0), (2, 0), (0, 1), (2, 1), (0, 2), (2, 2)]}[v]
    q = (s - 4) / 3
    for px, py in pips:
        cx, cy = x + 2 + q * px + q / 2, y + 2 + q * py + q / 2
        rect(img, int(cx - 1), int(cy - 1), 3 if s > 14 else 2, 3 if s > 14 else 2, C['red'] if v == 1 else C['ink'])


def craps(W, H, name):
    g = Grid(W, H)
    img = frame(g, 'Craps', '1 - 200', '12 025', players='3')
    x0, y0 = g.m + 4, g.py0 + 8
    w = g.W - 2 * g.m - 8
    lw = lambda s: None
    # place numbers row
    nums = [4, 5, 6, 8, 9, 10]
    dcw = int(w * 0.17)
    pw = (w - dcw) // 6
    rh = int(g.ph * 0.25)
    rect(img, x0, y0, dcw, rh * 2 - 4, None, C['felt.print'])
    felt_print(img, x0 + dcw // 2, y0 + rh - 10, "DON'T", 200)
    felt_print(img, x0 + dcw // 2, y0 + rh, 'COME', 200)
    for i, n in enumerate(nums):
        x = x0 + dcw + i * pw
        rect(img, x, y0, pw, rh, None, C['felt.print'])
        text(img, x + pw // 2, y0 + 6, str(n), C['felt.print'], 3 if g.t3 else 2, 'c', shadow=False)
        if n == 8:
            rect(img, x + 1, y0 + 1, pw - 2, rh - 2, None, C['gold'])
            # the ON puck
            ImageDraw.Draw(img).ellipse((x + pw - 18, y0 + 3, x + pw - 3, y0 + 18), fill=C['text'], outline=C['ink'])
            text(img, x + pw - 10, y0 + 7, 'ON', C['ink'], align='c', shadow=False)
    felt_chip(img, x0 + dcw + 1 * pw + pw // 2 - 8, y0 + rh - 10, g.bchip, 10)
    # come area with the dice
    cy = y0 + rh + 2
    rect(img, x0 + dcw, cy, w - dcw, rh - 6, None, C['felt.print'])
    text(img, x0 + dcw + 70, cy + rh // 2 - 11, 'COME', C['felt.print'], 2, 'c', shadow=False)
    ds = 22 if g.t3 else 18
    die(img, x0 + dcw + (w - dcw) // 2 - ds - 4, cy + (rh - 6 - ds) // 2, ds, 3)
    die(img, x0 + dcw + (w - dcw) // 2 + 4, cy + (rh - 6 - ds) // 2 - 3, ds, 5)
    pill(img, x0 + dcw + (w - dcw) // 2 + ds + 12, cy + (rh - 6 - 21) // 2, '8', C['gold'], C['panel'], 2)
    text(img, x0 + dcw + (w - dcw) // 2 + ds + 40, cy + (rh - 6) // 2 - 4, 'point', C['felt.print'], shadow=False)
    # field
    fy = cy + rh - 2
    fh = int(g.ph * 0.17)
    rect(img, x0, fy, w, fh, None, C['felt.print'])
    text(img, x0 + 40, fy + fh // 2 - 8, 'FIELD', C['felt.print'], 2, 'c', shadow=False)
    vals = ['2', '3', '4', '9', '10', '11', '12']
    for k, v in enumerate(vals):
        x = x0 + 84 + k * (w - 100) // 6
        col = C['gold'] if v in ('2', '12') else C['felt.print']
        text(img, x, fy + 4, v, col, 2 if g.t3 else 1, 'c', shadow=False)
        if v in ('2', '12'):
            text(img, x, fy + 16, 'x2' if v == '2' else 'x3', C['gold'], 1, 'c', shadow=False)
    felt_chip(img, x0 + w - 40, fy + fh // 2, g.bchip, 5)
    # don't pass + pass line
    dy = fy + fh + 2
    lh = int(g.ph * 0.10)
    rect(img, x0, dy, w, lh, None, C['felt.print'])
    felt_print(img, x0 + w // 2, dy + lh // 2 - 4, "DON'T PASS BAR", 200)
    py = dy + lh + 2
    rect(img, x0, py, w, lh + 4, None, C['gold'])
    text(img, x0 + w // 2, py + (lh + 4) // 2 - 4, 'PASS LINE', C['felt.print'], 1, 'c', shadow=False)
    felt_chip(img, x0 + w // 2 + 60, py + (lh + 4) // 2, g.bchip, 50)
    felt_chip(img, x0 + w // 2 + 110, py + (lh + 4) // 2, g.bchip, 75)
    text(img, x0 + w // 2 + 136, py + (lh + 4) // 2 - 4, 'odds', C['felt.print'], shadow=False)
    # dock
    ty = g.py1 + (g.db - g.chip) // 2
    x = chip_tray(img, g, g.m + 2, ty, sel=5)
    x = bet_tools(img, g, x + 6, g.py1 + (g.db - 20) // 2)
    text(img, x + 8, g.py1 + 8, 'On table', C['muted'])
    text(img, x + 8, g.py1 + 19, '140', C['gold'], scale=2)
    status(img, x + 62, g.py1 + 8, 'Point is 8', 'Roll in 18 s', C['gold'])
    button(img, g.W - g.m - g.prim_w, g.py1 + (g.db - g.btn_h) // 2, g.prim_w, g.btn_h, 'Roll', 'primary', key='Space')
    save(img, name)


# ======================================================================================================================
# Plinko (extras: the same frame, dock and BetBar)
# ======================================================================================================================
def plinko(W, H, name):
    g = Grid(W, H)
    img = frame(g, 'Plinko', '1 - 50', '12 017')
    shade(img, (0, g.py0, g.W, g.py1), (30, 14, 80), 200)
    board = tex('gui/extras/plinko_board.png')
    bwid = board.width
    bx = (g.W - bwid) // 2 - 30
    by = g.py0 + (g.ph - board.height) // 2
    if board.height > g.ph - 4:
        board = board.crop((0, board.height - (g.ph - 4), bwid, board.height))
        by = g.py0 + 2
    blit(img, board, bx, by)
    # bins with big readable multipliers
    mult = ['33', '11', '4', '2', '1', '.6', '.3', '.6', '1', '2', '4', '11', '33']
    binw = (bwid - 16) // 13
    for k, m in enumerate(mult):
        x = bx + 8 + k * binw
        hot = k in (0, 12)
        col = C['gold'] if hot else (C['win'] if m in ('2', '4', '11') else (C['you'] if m == '1' else C['lose']))
        bevel(img, x, g.py1 - 22, binw - 1, 18, C['panel'], C['panel.hi'], C['ink'], col)
        text(img, x + binw // 2, g.py1 - 17, m, col, align='c')
    # ball mid-drop
    ImageDraw.Draw(img).ellipse((bx + bwid // 2 + 14, by + 92, bx + bwid // 2 + 22, by + 100), fill=C['red'], outline=C['ink'])
    # right column: risk + last results
    rx = bx + bwid + 10
    text(img, rx, g.py0 + 10, 'Risk', C['muted'])
    for k, s in enumerate(['Low', 'Medium', 'High']):
        button(img, rx, g.py0 + 21 + k * 22, g.W - g.m - rx, 20, s, 'primary' if s == 'Medium' else 'normal')
    text(img, rx, g.py0 + 95, 'Last balls', C['muted'])
    for k, (m, c) in enumerate([('x2', C['win']), ('x0.6', C['lose']), ('x1', C['you']), ('x4', C['win'])]):
        pill(img, rx + (k % 2) * 46, g.py0 + 107 + (k // 2) * 16, m, c, C['panel'])
    text(img, rx, g.py0 + 145, 'Top prize', C['muted'])
    text(img, rx, g.py0 + 156, 'x33', C['gold'], 2)
    # dock (the shared BetBar)
    ty = g.py1 + (g.db - g.chip) // 2
    x = chip_tray(img, g, g.m + 2, ty, sel=5)
    x = bet_tools(img, g, x + 6, g.py1 + (g.db - 20) // 2)
    text(img, x + 8, g.py1 + 8, 'Bet', C['muted'])
    text(img, x + 8, g.py1 + 19, '20', C['gold'], scale=2)
    status(img, x + 52, g.py1 + 8, 'Ball 1 dropping', 'Win up to 660', C['text'])
    button(img, g.W - g.m - g.prim_w, g.py1 + (g.db - g.btn_h) // 2, g.prim_w, g.btn_h, 'Drop', 'primary', key='Space')
    save(img, name)


# ======================================================================================================================
# Grid diagram and component sheet
# ======================================================================================================================
def grid_diagram(W, H, name):
    g = Grid(W, H)
    img = frame(g, 'Title', 'limits', '12 500', players='seats')
    ov = Image.new('RGBA', img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    cols = 12
    gut = 4
    cw = (g.W - 2 * g.m - (cols - 1) * gut) / cols
    for k in range(cols):
        x = g.m + k * (cw + gut)
        od.rectangle((x, g.py0, x + cw, g.py1 - 1), fill=(255, 80, 120, 40))
    img.alpha_composite(ov)
    text(img, g.W // 2, g.py0 + 8, 'PLAY AREA  %d x %d  (12 cols, gutter 4, margin %d)' % (g.W, g.ph, g.m), C['gold'], align='c')
    text(img, g.W // 2, g.py0 + 20, 'tier %s  -  hero card %dx%d  -  seat card %dx%d  -  tray chip %d' % (
        'T3' if g.t3 else 'T2', g.card[0], g.card[1], g.card2[0], g.card2[1], g.chip), C['text'], align='c')
    text(img, g.W // 2, g.H - g.db // 2 - 4, 'DOCK  h %d:  BetBar | status | primary' % g.db, C['muted'], align='c')
    ty = g.py1 + (g.db - g.chip) // 2
    rect(img, g.m, ty, 140, g.chip, None, C['you'])
    rect(img, g.W - g.m - g.prim_w, g.py1 + (g.db - g.btn_h) // 2, g.prim_w, g.btn_h, None, C['gold'])
    save(img, name)


def components(name):
    W, H = 480, 270
    img = Image.new('RGBA', (W, H), C['bg'] + (255,))
    tile(img, FELT, 0, 0, W, 120)
    text(img, 6, 4, 'chip_v2 (24 / 32)', C['gold'])
    x = 6
    for dn in (1, 5, 25, 100, 500):
        chip(img, x, 16, 24, dn)
        x += 28
    for dn in (1, 5, 25, 100, 500):
        chip(img, x, 12, 32, dn)
        x += 36
    chip(img, x + 4, 16, 24, 25, selected=True)
    text(img, 6, 50, 'felt bet + value pill', C['gold'])
    felt_chip(img, 16, 80, 12, 50)
    felt_chip(img, 70, 80, 12, 125, mine=False)
    felt_chip(img, 130, 80, 16, 500)
    text(img, 200, 50, 'stamp_v2', C['gold'])
    stamp(img, 230, 74, 'WIN +50', 'win', 1)
    stamp(img, 290, 74, 'BUST', 'lose', 1)
    stamp(img, 340, 74, 'PUSH', 'push', 1)
    stamp(img, 410, 74, 'BLACKJACK', 'bj', 1)
    text(img, 6, 90, 'plate_v2 (74 x 18)', C['gold'])
    plate(img, 6, 100, 'You', you=True, active=True)
    plate(img, 86, 100, 'Creeper42', (70, 140, 60))
    plate(img, 166, 100, 'Very long bot name', (230, 170, 140), active=True)
    text(img, 6, 126, 'buttons (h 30 / 36)', C['gold'])
    button(img, 6, 138, 104, 30, 'Deal', 'primary', key='Space')
    button(img, 114, 138, 70, 30, 'Hit', 'normal', key='H')
    button(img, 188, 138, 70, 30, 'Fold', 'danger', key='F')
    button(img, 262, 138, 70, 30, 'Split', 'disabled', key='P')
    for k, gph in enumerate(('<', 'x', 'R', '2')):
        icon_button(img, 340 + k * 23, 143, 20, gph)
    text(img, 6, 176, 'type scale', C['gold'])
    text(img, 6, 188, 'Caption / muted 1x', C['muted'])
    text(img, 6, 199, 'Body 1x', C['text'])
    text(img, 120, 186, '12 450', C['gold'], 2)
    text(img, 120, 206, 'hero number 2x', C['muted'])
    text(img, 230, 182, 'YOU WIN', C['gold'], 3)
    text(img, 230, 210, 'display 3x (T3) / 2x (T2)', C['muted'])
    text(img, 6, 226, 'tokens', C['gold'])
    x = 6
    for k in ('ink', 'panel', 'edge', 'text', 'muted', 'gold', 'win', 'lose', 'push', 'you', 'felt', 'felt.print', 'rail'):
        rect(img, x, 238, 20, 12, C[k], C['ink'])
        x += 36
    x = 6
    for k in ('ink', 'panel', 'edge', 'text', 'muted', 'gold', 'win', 'lose', 'push', 'you', 'felt', 'felt.print', 'rail'):
        text(img, x, 254 + (8 if (x // 36) % 2 else 0) - 4, k[:6], C['muted'], shadow=False)
        x += 36
    save(img, name)


# ======================================================================================================================
# In-world concept renders (REDESIGN.md §7)
# ======================================================================================================================
def solve(a, b):
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        for r in range(n):
            if r != c and m[r][c] != 0:
                f = m[r][c] / m[c][c]
                for k in range(c, n + 1):
                    m[r][k] -= f * m[c][k]
    return [m[i][n] / m[i][i] for i in range(n)]


def persp_coeffs(dst, src):
    a, b = [], []
    for (x, y), (u, v) in zip(dst, src):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        b.append(u)
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        b.append(v)
    return solve(a, b)


class Cam:
    def __init__(self, W, H, eye, target, fov=70):
        self.W, self.H = W, H
        self.e = eye
        dx, dy, dz = (target[i] - eye[i] for i in range(3))
        self.yaw = math.atan2(dx, dz)
        self.pitch = math.atan2(dy, math.hypot(dx, dz))
        self.f = (H / 2) / math.tan(math.radians(fov / 2))

    def p(self, pt):
        x, y, z = (pt[i] - self.e[i] for i in range(3))
        cy, sy = math.cos(-self.yaw), math.sin(-self.yaw)
        x, z = x * cy + z * sy, -x * sy + z * cy
        cp, sp = math.cos(-self.pitch), math.sin(-self.pitch)
        y, z = y * cp + z * sp, -y * sp + z * cp
        z = max(z, 0.05)
        return (self.W / 2 + self.f * x / z, self.H / 2 - self.f * y / z), z


def quad(img, cam, corners, texture=None, color=None, alpha=255):
    pts = [cam.p(c)[0] for c in corners]
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).polygon(pts, fill=alpha)
    if texture is None:
        img.paste(Image.new('RGBA', img.size, color + (255,)), (0, 0), mask)
        return
    tw, th = texture.size
    co = persp_coeffs(pts, [(0, 0), (tw, 0), (tw, th), (0, th)])
    warped = texture.transform(img.size, Image.PERSPECTIVE, co, Image.NEAREST)
    m2 = Image.composite(warped.split()[3], Image.new('L', img.size, 0), mask)
    img.paste(warped, (0, 0), m2)


def box(img, cam, x0, y0, z0, x1, y1, z1, top, side, front):
    quad(img, cam, [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], color=top)
    quad(img, cam, [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)], color=front)


def sky_floor(W, H, cam):
    img = Image.new('RGBA', (W, H), (48, 18, 40, 255))
    # casino floor: dark carpet tiles
    for i in range(-14, 15):
        for j in range(-6, 14):
            col = (70, 22, 34) if (i + j) % 2 else (86, 28, 42)
            quad(img, cam, [(i, 0, j + 1), (i + 1, 0, j + 1), (i + 1, 0, j), (i, 0, j)], color=col)
    return img


def billboard(img, cam, pos, lines, px_per_block=None, scale_px=0.0125):
    """Holo text: one text pixel = scale_px blocks; dark 40% backing; drawn facing the camera."""
    (sx, sy), z = cam.p(pos)
    ppb = cam.f / z
    k = scale_px * ppb  # screen px per text px
    tw = max(F.width(s) for s, _ in lines) + 6
    th = len(lines) * 10 + 4
    surf = Image.new('RGBA', (tw, th), (0, 0, 0, 0))
    rect(surf, 0, 0, tw, th, (16, 8, 26))
    surf.putalpha(150)
    for i, (s, c) in enumerate(lines):
        text(surf, tw // 2, 3 + i * 10, s, c, align='c')
    big = surf.resize((max(1, int(tw * k)), max(1, int(th * k))), Image.NEAREST)
    LABELS.append((z, big, int(sx - big.width / 2), int(sy - big.height)))
    return k


LABELS = []


def flush_labels(img):
    """The overlap resolver of REDESIGN.md §7.4: nearest first; a plate that would overlap one already placed moves up
    by the overlap (+2 px) — the in-game version does the same in screen space each frame (<= 12 plates)."""
    placed = []
    for z, big, x, y in sorted(LABELS, key=lambda t: t[0]):
        moved = True
        while moved:
            moved = False
            for (px, py, pw, ph) in placed:
                if x < px + pw and px < x + big.width and y < py + ph and py < y + big.height:
                    y = py - big.height - 2
                    moved = True
        placed.append((x, y, big.width, big.height))
        img.alpha_composite(big, (x, y))
    LABELS.clear()


def world_table(name, game):
    W, H = 1920, 1080
    eye = (1.7, 2.05, -2.7)   # a standing player 3.2 blocks away, 3/4 view
    cam = Cam(W, H, eye, (0.1, 1.15, 0.15))
    img = sky_floor(W, H, cam)
    # back wall of the casino
    quad(img, cam, [(-30, 12, 6), (30, 12, 6), (30, 0, 6), (-30, 0, 6)], color=(48, 18, 40))
    quad(img, cam, [(-30, 0.6, 5.98), (30, 0.6, 5.98), (30, 0, 5.98), (-30, 0, 5.98)], color=(70, 30, 20))
    for k in range(-11, 12, 2):
        quad(img, cam, [(k, 3.2, 5.97), (k + 0.4, 3.2, 5.97), (k + 0.4, 2.8, 5.97), (k, 2.8, 5.97)], color=(255, 214, 120))
    # the pedestal block (the real 1 x 1 block) and the rendered top (2.25 x 1.25 blocks, REDESIGN.md §7.2)
    box(img, cam, -0.3, 0, -0.3, 0.3, 0.86, 0.3, (60, 34, 16), (60, 34, 16), (74, 42, 20))
    hw, hd = (1.125, 0.625) if game != 'roulette' else (1.0, 0.7)
    box(img, cam, -hw - 0.08, 0.86, -hd - 0.08, hw + 0.08, 1.0, hd + 0.08, C['rail'], C['rail'], C['rail.dark'])
    # felt top texture (oversampled felt + prints)
    ft = Image.new('RGBA', (360, 200))
    tile(ft, FELT, 0, 0, 360, 200)
    fd = ImageDraw.Draw(ft)
    if game == 'blackjack':
        fd.arc((-40, -260, 400, 190), 20, 160, fill=C['felt.print'], width=2)
        text(ft, 180, 70, 'BLACKJACK PAYS 3 TO 2', C['felt.print'], 1, 'c', shadow=False)
        for sxp in (40, 110, 180, 250, 320):
            yy = 150 - abs(sxp - 180) // 3
            fd.ellipse((sxp - 13, yy - 13, sxp + 13, yy + 13), outline=C['felt.print'], width=1)
    elif game == 'roulette':
        for c in range(12):
            for r in range(3):
                n = c * 3 + 3 - r
                col = C['red'] if n in REDS else C['black']
                fd.rectangle((150 + c * 17, 30 + r * 22, 150 + c * 17 + 15, 30 + r * 22 + 20), fill=col)
                text(ft, 158 + c * 17, 36 + r * 22, str(n), C['text'], align='c', shadow=False)
        fd.rectangle((132, 30, 148, 94), fill=C['green'])
        wheel(ft, 70, 100, 62, 20)
    quad(img, cam, [(-hw, 1.0, hd), (hw, 1.0, hd), (hw, 1.0, -hd), (-hw, 1.0, -hd)], texture=ft)
    # cards: 1.5x real size, lying flat but lifted 1/32 and tilted 20 degrees towards the rail they face (§7.3)
    def flat_card(code, x, z, w=0.22, h=0.30, tilt=20):
        t = card_img(code, (37, 49)).resize((74, 98), Image.NEAREST)
        dy = math.sin(math.radians(tilt)) * h
        dz = math.cos(math.radians(tilt)) * h
        quad(img, cam, [(x - w / 2, 1.01 + dy, z + dz / 2), (x + w / 2, 1.01 + dy, z + dz / 2),
                        (x + w / 2, 1.01, z - dz / 2), (x - w / 2, 1.01, z - dz / 2)], texture=t)

    def stack(x, z, n, col):
        for k in range(n):
            yy = 1.0 + k * 0.025
            (cx, cy), zz = cam.p((x, yy, z))
            r = 0.07 * cam.f / zz
            ImageDraw.Draw(img).ellipse((cx - r, cy - r * 0.35, cx + r, cy + r * 0.35), fill=col, outline=C['ink'])

    if game == 'blackjack':
        flat_card(13 + 9, -0.12, 0.42)
        flat_card(-1, 0.12, 0.42)
        seats = [(-0.85, -0.05), (-0.45, -0.35), (0.0, -0.45), (0.45, -0.35), (0.85, -0.05)]
        hands = [[22, 44], [0, 25], [7, 15], None, [47, 33]]
        for (x, z), h in zip(seats, hands):
            if not h:
                continue
            for k, c in enumerate(h):
                flat_card(c, x - 0.07 + k * 0.13, z + 0.12, 0.2, 0.27)
            stack(x, z - 0.12, 4, CHIP[25][0])
        billboard(img, cam, (0, 2.35, 0.55), [('BLACKJACK  -  1-100', C['gold']), ('Dealer  10', C['text']),
                                               ('Steve\'s turn  -  18 s', C['muted'])], scale_px=0.012)
        plates = [[('Creeper42', C['text']), ('25  BUST', C['lose'])], [('Mira', C['text']), ('50  BLACKJACK', C['gold'])],
                  [('Steve', C['you']), ('50  -  15', C['gold'])], None, [('Axolotl Al', C['text']), ('10  -  17', C['gold'])]]
        for (x, z), p in zip(seats, plates):
            if p:
                billboard(img, cam, (x * 1.12, 1.30, z * 1.4 - 0.32), p, scale_px=0.0095)
    else:
        stack(0.3, -0.3, 3, CHIP[5][0])
        stack(-0.2, -0.25, 5, CHIP[25][0])
        stack(0.7, -0.4, 2, CHIP[100][0])
        billboard(img, cam, (0, 2.3, 0.5), [('ROULETTE', C['gold']), ('26  BLACK', C['text']), ('Steve +350', C['win'])],
                  scale_px=0.014)
        billboard(img, cam, (-0.7, 1.30, -0.85), [('Steve', C['you']), ('55 on 5 bets', C['gold'])], scale_px=0.0095)
        billboard(img, cam, (0.7, 1.30, -0.85), [('Alex', C['text']), ('30  -  lost', C['lose'])], scale_px=0.0095)
        # the ball resting in pocket 26 + the dolly on 26
        (bx, by), zz = cam.p((-0.62, 1.03, -0.05))
        ImageDraw.Draw(img).ellipse((bx - 6, by - 4, bx + 6, by + 4), fill=(250, 250, 250), outline=C['ink'])
    flush_labels(img)
    img.convert('RGB').save(os.path.join(OUT, name))
    print('wrote', name)


def main():
    blackjack(480, 270, 'v2_blackjack_turn_480.png')
    blackjack(640, 360, 'v2_blackjack_turn_640.png')
    blackjack_betting(480, 270, 'v2_blackjack_betting_480.png')
    blackjack_result(480, 270, 'v2_blackjack_result_480.png')
    roulette(480, 270, 'v2_roulette_betting_480.png')
    roulette(640, 360, 'v2_roulette_betting_640.png')
    roulette(480, 270, 'v2_roulette_spin_480.png', spinning=True)
    poker(480, 270, 'v2_poker_turn_480.png')
    poker(640, 360, 'v2_poker_turn_640.png')
    craps(480, 270, 'v2_craps_point_480.png')
    plinko(480, 270, 'v2_plinko_480.png')
    grid_diagram(480, 270, 'v2_grid_480.png')
    grid_diagram(640, 360, 'v2_grid_640.png')
    components('v2_components.png')
    world_table('world_blackjack_concept.png', 'blackjack')
    world_table('world_roulette_concept.png', 'roulette')


if __name__ == '__main__':
    main()
