#!/usr/bin/env python3
"""Full-screen card-table mockups (docs/design/visual/cards.md §10), composed from the REAL generated art.

    python3 docs/design/visual/mockups/render_cards.py            # regenerates the art first (node), then renders
    python3 docs/design/visual/mockups/render_cards.py --no-gen   # uses the committed PNGs as they are

Every sprite, atlas, table and backdrop comes from java/src/main/resources/assets/burmaldaholic/textures (written by
bedrock/tools/assets/modules/cards.mjs). The screen is composed at GUI scale 1 on the 427 x 240 layout canvas
(854 x 480 at GUI scale 2) and upscaled x2 with nearest sampling, exactly like the game draws it. Runtime text uses
the vanilla font read from a local Minecraft client jar (Fabric Loom cache) when one exists; otherwise a small
built-in fallback font is used (the layout stays the same). Card rank indices use the generated
`burmaldaholic:core/card_index` font. Needs Pillow.
"""
from __future__ import annotations

import glob
import io
import json
import os
import subprocess
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
TEX = ROOT / 'java/src/main/resources/assets/burmaldaholic/textures'
SPR = TEX / 'gui/sprites/core/cards'
GUI = TEX / 'gui/core/cards'
CORE = TEX / 'gui/sprites/core'
OUT = Path(__file__).resolve().parent
W, H = 427, 240

# ------------------------------------------------------------------------------------------------ images
_cache: dict[str, Image.Image] = {}


def load(path: Path) -> Image.Image:
    key = str(path)
    if key not in _cache:
        _cache[key] = Image.open(path).convert('RGBA')
    return _cache[key]


def spr(name: str) -> Image.Image:
    return load(SPR / f'{name}.png')


def gui(name: str) -> Image.Image:
    return load(GUI / f'{name}.png')


def core(name: str) -> Image.Image:
    return load(CORE / f'{name}.png')


def frame(img: Image.Image, i: int, n: int) -> Image.Image:
    """Frame i of an n-frame vertical strip."""
    h = img.height // n
    return img.crop((0, i * h, img.width, (i + 1) * h))


def paste(dst: Image.Image, src: Image.Image, x: int, y: int, alpha: float = 1.0) -> None:
    if alpha < 1:
        src = src.copy()
        a = src.getchannel('A').point(lambda v: int(v * alpha))
        src.putalpha(a)
    dst.alpha_composite(src, (int(x), int(y))) if x >= 0 and y >= 0 else _paste_clip(dst, src, int(x), int(y))


def _paste_clip(dst, src, x, y):
    cx, cy = max(0, -x), max(0, -y)
    part = src.crop((cx, cy, src.width, src.height))
    dst.alpha_composite(part, (x + cx, y + cy))


def tint(img: Image.Image, hexc: str, alpha: float = 1.0) -> Image.Image:
    r, g, b = hexrgb(hexc)
    out = img.copy()
    px = out.load()
    for yy in range(out.height):
        for xx in range(out.width):
            pr, pg, pb, pa = px[xx, yy]
            if pa:
                px[xx, yy] = (pr * r // 255, pg * g // 255, pb * b // 255, int(pa * alpha))
    return out


def dim(img: Image.Image, k: float) -> Image.Image:
    out = img.copy()
    px = out.load()
    for yy in range(out.height):
        for xx in range(out.width):
            pr, pg, pb, pa = px[xx, yy]
            if pa:
                l = (pr * 30 + pg * 59 + pb * 11) // 100
                px[xx, yy] = (int((pr * 0.4 + l * 0.6) * k), int((pg * 0.4 + l * 0.6) * k), int((pb * 0.4 + l * 0.6) * k), pa)
    return out


def hexrgb(h: str):
    h = h.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def nine(dst, img: Image.Image, x, y, w, h, border: int) -> None:
    """Java GUI nine-slice (edges and centre tiled, as `stretch_inner: false`)."""
    b = border
    sw, sh = img.width, img.height
    parts = {}
    xs = [(0, b), (b, sw - b), (sw - b, sw)]
    ys = [(0, b), (b, sh - b), (sh - b, sh)]
    for j, (y0, y1) in enumerate(ys):
        for i, (x0, x1) in enumerate(xs):
            parts[i, j] = img.crop((x0, y0, x1, y1))
    tx = [(x, b), (x + b, w - 2 * b), (x + w - b, b)]
    ty = [(y, b), (y + b, h - 2 * b), (y + h - b, b)]
    for j in range(3):
        for i in range(3):
            p = parts[i, j]
            dx, dw = tx[i]
            dy, dh = ty[j]
            if dw <= 0 or dh <= 0:
                continue
            for oy in range(0, dh, p.height):
                for ox in range(0, dw, p.width):
                    piece = p.crop((0, 0, min(p.width, dw - ox), min(p.height, dh - oy)))
                    dst.alpha_composite(piece, (dx + ox, dy + oy))


# ------------------------------------------------------------------------------------------------ fonts
class Font:
    """Vanilla bitmap font (ascii + nonlatin_european) from a local client jar, or a tiny fallback."""

    def __init__(self):
        self.glyphs: dict[str, Image.Image] = {}
        jar = self._jar()
        if jar:
            self._load_jar(jar)

    @staticmethod
    def _jar():
        for p in sorted(glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-client.jar')), reverse=True):
            return p
        return None

    def _load_jar(self, jar):
        z = zipfile.ZipFile(jar)
        prov = json.loads(z.read('assets/minecraft/font/include/default.json'))['providers']
        for p in prov:
            if p.get('type') != 'bitmap' or p.get('height', 8) != 8:
                continue
            f = p['file'].split(':')[1]
            sheet = Image.open(io.BytesIO(z.read(f'assets/minecraft/textures/{f}'))).convert('RGBA')
            rows = p['chars']
            cw, ch = sheet.width // len(rows[0]), sheet.height // len(rows)
            for r, row in enumerate(rows):
                for c, char in enumerate(row):
                    if char in ('\u0000', ' ') or char in self.glyphs:
                        continue
                    g = sheet.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch))
                    bbox = g.getchannel('A').getbbox()
                    if not bbox:
                        continue
                    self.glyphs[char] = g.crop((0, 0, bbox[2], ch))

    def width(self, s: str) -> int:
        w = 0
        for ch in s:
            if ch == ' ':
                w += 4
            elif ch in self.glyphs:
                w += self.glyphs[ch].width + 1
            else:
                w += 6
        return w

    def draw(self, dst, s: str, x: int, y: int, colour: str = '#F4ECF8', shadow: bool = True, center: bool = False, right: bool = False):
        if center:
            x -= self.width(s) // 2
        if right:
            x -= self.width(s)
        if shadow:
            r, g, b = hexrgb(colour)
            self._draw(dst, s, x + 1, y + 1, '#%02X%02X%02X' % (r // 4, g // 4, b // 4))
        self._draw(dst, s, x, y, colour)
        return self.width(s)

    def _draw(self, dst, s, x, y, colour):
        cx = x
        for ch in s:
            if ch == ' ':
                cx += 4
                continue
            g = self.glyphs.get(ch)
            if g is None:
                g = FALLBACK.get(ch.upper()) or FALLBACK['?']
            dst.alpha_composite(tint(g, colour), (cx, y))
            cx += g.width + 1


def _fallback():
    """3 x 5 capitals/digits (only used when no client jar is available)."""
    rows = {
        'A': '.#.#.####.##.#', '?': '##..#.#...#.', '0': '####.##.##.####',
    }
    out = {}
    for k, v in rows.items():
        img = Image.new('RGBA', (3, 8))
        for i, c in enumerate(v[:15]):
            if c == '#':
                img.putpixel((i % 3, 1 + i // 3), (255, 255, 255, 255))
        out[k] = img
    return out


FALLBACK = _fallback()
FONT: Font  # set in main


class IndexFont:
    """The generated `card_index` font (8 x 8 cells, rows '0123456789AJQKX', 'ТВДК')."""

    ROWS = ['0123456789AJQKX', 'ТВДК']

    def __init__(self):
        sheet = load(TEX / 'font/core/card_index.png')
        self.g = {}
        for r, row in enumerate(self.ROWS):
            for c, ch in enumerate(row):
                cell = sheet.crop((c * 8, r * 8, c * 8 + 8, r * 8 + 8))
                bb = cell.getchannel('A').getbbox()
                self.g[ch] = cell.crop((0, 1, bb[2], 7))

    def width(self, s):
        return sum(self.g[c].width + 1 for c in s) - 1

    def draw(self, dst, s, x, y, colour, rotate=False):
        img = Image.new('RGBA', (self.width(s), 6))
        cx = 0
        for c in s:
            img.alpha_composite(tint(self.g[c], colour), (cx, 0))
            cx += self.g[c].width + 1
        if rotate:
            img = img.rotate(180)
        dst.alpha_composite(img, (x, y))


# ------------------------------------------------------------------------------------------------ cards
SUITS = {'S': 0, 'H': 1, 'D': 2, 'C': 3}
SUIT_COL = {'S': '#231C38', 'H': '#D42A3A', 'D': '#2A5ED8', 'C': '#0F7E66'}
RANK_IDX = {r: i for i, r in enumerate(['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'])}
SIZE = {'l': (37, 49), 'm': (21, 29), 's': (13, 17)}
BACKS = ['navy', 'burgundy', 'crimson', 'emerald', 'bastion', 'end']
BACK_COL = {'l': 0, 'm': 37, 's': 58}
IDX: IndexFont


def card_img(code: str | None, size='l', back='navy', ru=False) -> Image.Image:
    """code like 'QH', '10S'; None = back."""
    w, h = SIZE[size]
    if code is None:
        row = BACKS.index(back)
        return gui('backs').crop((BACK_COL[size], row * 49, BACK_COL[size] + w, row * 49 + h))
    rank, suit = code[:-1], code[-1]
    atlas = gui(f'faces_{size}')
    c, r = RANK_IDX[rank], SUITS[suit]
    img = atlas.crop((c * w, r * h, (c + 1) * w, (r + 1) * h)).copy()
    idx = {'A': 'Т', 'K': 'К', 'Q': 'Д', 'J': 'В'}.get(rank, rank) if ru else rank
    col = SUIT_COL[suit]
    IDX.draw(img, idx, 2, 3, col)
    if size == 'l':
        IDX.draw(img, idx, w - 2 - IDX.width(idx), h - 9, col, rotate=True)
    return img


def draw_card(dst, code, x, y, size='l', back='navy', shadow=True, lift=0, glow=False, dimmed=False, sideways=False, ru=False):
    img = card_img(code, size, back, ru)
    if sideways:
        img = img.rotate(90, expand=True)
    if dimmed:
        img = dim(img, 0.72)
    if shadow and size in ('l', 'm') and not sideways:
        paste(dst, spr(f'fx/shadow_{size}'), x - 1, y - 1 - lift + 1)
    elif shadow and sideways:
        sh = Image.new('RGBA', img.size, (10, 4, 18, 90))
        paste(dst, sh, x + 2, y + 2)
    if glow and size in ('l', 'm'):
        paste(dst, frame(spr(f'fx/glow_{size}'), 2, 4), x - 3, y - 3 - lift)
    paste(dst, img, x, y - lift)


def draw_hand(dst, codes, x, y, step, **kw):
    for i, c in enumerate(codes):
        draw_card(dst, c, x + i * step, y, **kw)


# ------------------------------------------------------------------------------------------------ chips, text, widgets
DENOMS = [500, 100, 25, 5, 1]


def break_amount(n: int, cap=6):
    out = []
    for d in DENOMS:
        while n >= d and len(out) < cap:
            out.append(d)
            n -= d
    return out[::-1] if out else [1]


def chip_stack(dst, cx, base_y, amount: int, label=True, tint_hex=None, hatch=False, label_col='#F4ECF8', max_discs=5):
    discs = sorted(break_amount(amount, max_discs), reverse=True)
    for i, d in enumerate(discs):
        disc = spr(f'chip/disc_{d}') if not tint_hex else tint(spr('chip/disc_tint'), tint_hex)
        paste(dst, disc, cx - 6, base_y - 8 - i * 2)
        if hatch:
            paste(dst, spr('chip/disc_hatch'), cx - 6, base_y - 8 - i * 2)
    if label:
        FONT.draw(dst, fmt(amount), cx, base_y + 1, label_col, center=True)


def fmt(n: int) -> str:
    s = f'{n:,}'.replace(',', ' ')
    return s.replace(' ', ' ')


def total_badge(dst, x, y, text, gold=False):
    w = FONT.width(text) + 6
    nine(dst, spr('badge/total_gold' if gold else 'badge/total'), x, y, w, 11, 3)
    FONT.draw(dst, text, x + 3, y + 2, '#180A28' if gold else '#F4ECF8', shadow=not gold)
    return w


def tag(dst, cx, y, text, colour='#180A28'):
    w = FONT.width(text) + 8
    nine(dst, spr('tag/bubble'), cx - w // 2, y, w, 11, 4)
    paste(dst, spr('tag/tail'), cx - 2, y + 10)
    FONT.draw(dst, text, cx - w // 2 + 4, y + 2, colour, shadow=False)


def stamp(dst, cx, cy, text, kind='gold', rot=-6):
    w = FONT.width(text) + 14
    img = Image.new('RGBA', (w, 16))
    nine(img, spr(f'stamp/{kind}'), 0, 0, w, 16, 5)
    FONT.draw(img, text, 7, 4, '#FFFFFF' if kind != 'gold' else '#5A2A00', shadow=kind != 'gold')
    img = img.rotate(-rot, resample=Image.NEAREST, expand=True)
    paste(dst, img, cx - img.width // 2, cy - img.height // 2)


def button(dst, x, y, w, text, theme='village', icon=None, family='table', state='normal', hotkey=None):
    if family == 'table':
        name = f'button/table_{theme}' + ('' if state == 'normal' else f'_{state}')
        nine(dst, spr(name), x, y, w, 20, 4)
    else:
        name = 'widget/casino_button' + ('' if family == 'secondary' else f'_{family}') + ('' if state == 'normal' else f'_{state}')
        nine(dst, core(name), x, y, w, 20, 3)
    tw = FONT.width(text) + (14 if icon else 0)
    tx = x + (w - tw) // 2
    if icon:
        ic = spr(f'icon/{icon}')
        if state == 'disabled':
            ic = dim(ic, 0.6)
        paste(dst, ic, tx, y + 4)
        tx += 14
    col = '#8A7A9A' if state == 'disabled' else ('#3A1A00' if family == 'primary' else '#F4ECF8')
    FONT.draw(dst, text, tx, y + 6, col, shadow=family != 'primary' and state != 'disabled')


def plaque(dst, x, y, w, text, theme):
    nine(dst, spr(f'panel/plaque_{theme}'), x, y, w, 18, 7)
    FONT.draw(dst, text, x + w // 2, y + 5, '#FFD640', center=True)


def hud_balance(dst, amount, theme):
    text = fmt(amount)
    w = FONT.width(text) + 20
    x = W - w - 5
    nine(dst, core('panel/hud'), x, 3, w, 14, 3)
    paste(dst, core('fx/chip_25'), x + 4, 6)
    FONT.draw(dst, text, x + 15, 6, '#FFD640')


SKINS = {
    'alex': ('#E8A04A', '#F0C49A', '#3A7A3A'),
    'steve': ('#4A2A1A', '#C8906A', '#3A5AA8'),
    'nezo': ('#1A1A2A', '#E0B088', '#8A2ABE'),
    'mira': ('#C84A8A', '#F0C8A8', '#2A7A8A'),
}


def player_head(name: str) -> Image.Image:
    hair, skin, eye = SKINS.get(name, SKINS['steve'])
    rows = ['hhhhhhhh', 'hhhhhhhh', 'hsssssh.', 'sssssss.', 'sWessWes', 'ssssssss', 'ssmmmsss', 'ssssssss']
    img = Image.new('RGBA', (8, 8))
    pal = {'h': hair, 's': skin, 'W': '#FFFFFF', 'e': eye, 'm': '#8A4A3A', '.': skin}
    for yy, row in enumerate(rows):
        for xx, c in enumerate(row):
            img.putpixel((xx, yy), (*hexrgb(pal[c]), 255))
    return img.resize((16, 16), Image.NEAREST)


def seat_plate(dst, x, y, name, sub, state='normal', avatar=None, bot=None, level=None, width=None, sub_col='#FFD640', head=None, thinking=False):
    if width is None:
        width = max(FONT.width(name) + (13 if level else 0), FONT.width(sub), 14) + 30
    nine(dst, spr(f'seat/plate_{state}'), x, y, width, 22, 6)
    paste(dst, spr('seat/avatar_frame_gold' if state in ('me', 'winner') else 'seat/avatar_frame'), x + 2, y + 1)
    face = spr(f'bot/{bot}') if bot else player_head(head or name.lower())
    if state == 'folded':
        face = dim(face, 0.6)
    paste(dst, face, x + 4, y + 3)
    tx = x + 24
    ncol = '#8A7A9A' if state == 'folded' else '#F4ECF8'
    if level:
        paste(dst, spr(f'bot/badge_{level}'), tx, y + 3)
        tx += 13
    FONT.draw(dst, name, tx, y + 4, ncol)
    if thinking:
        paste(dst, frame(spr('bot/thinking'), 1, 3), x + 24, y + 14)
    else:
        FONT.draw(dst, sub, x + 24, y + 13, '#8A7A9A' if state == 'folded' else sub_col)


def text_on_felt(dst, s, cx, y, theme):
    FONT.draw(dst, s, cx, y, PRINT[theme], shadow=False, center=True)


PRINT = {'village': '#E8C860', 'bastion': '#FFC850', 'end': '#E8E4A8'}
PRINT_ALPHA = 0.55


def print_sprite(dst, name, x, y, theme, alpha=PRINT_ALPHA, colour=None):
    paste(dst, tint(spr(f'print/{name}'), colour or PRINT[theme], alpha), x, y)


def console(dst, theme):
    nine(dst, spr(f'panel/console_{theme}'), 0, 206, W, 34, 7)


def base(theme, shape, title, balance, table_y=18) -> tuple[Image.Image, int, int]:
    img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    paste(img, gui(f'backdrop_{theme}'), 0, 0)
    tx, ty = 10, table_y
    paste(img, gui(f'table_{shape}_{theme}'), tx, ty)
    console(img, theme)
    plaque(img, 4, 1, FONT.width(title) + 24, title, theme)
    hud_balance(img, balance, theme)
    return img, tx, ty


def chip_rack(dst, x, y, selected=25):
    for i, d in enumerate([1, 5, 25, 100, 500]):
        cx = x + i * 25
        if d == selected:
            paste(dst, spr('chip/select'), cx - 2, y - 2)
        paste(dst, spr(f'chip/big_{d}'), cx, y - (2 if d == selected else 0))


def save(img: Image.Image, name: str):
    out = img.convert('RGB').resize((W * 2, H * 2), Image.NEAREST)
    out.save(OUT / name, optimize=True)
    print('wrote', OUT / name)


# ================================================================================================ scenes
# Anchors are table-local (table art at screen (10, 18)); they mirror visual/cards.md §5 exactly.
BJ_SEATS = [(56, 70), (92, 128), (316, 128), (352, 70)]  # other seats' bet spots (far left … far right)
BJ_ME = (204, 154)


def toward_dealer(cx, cy, k=30):
    dx, dy = 204 - cx, -cy
    n = (dx * dx + dy * dy) ** 0.5
    return int(cx + dx / n * k), int(cy + dy / n * k)


def blackjack(theme='village', name='cards_blackjack_split.png'):
    img, tx, ty = base(theme, 'crescent', 'Blackjack · 1–100', 12_480)
    T = lambda x, y: (tx + x, ty + y)  # noqa: E731
    back = {'village': 'navy', 'bastion': 'bastion', 'end': 'end'}[theme]
    # dealer side: rack, shoe, tray, dealer hand (up card + hole card, tucked)
    paste(img, spr(f'prop/tray_{theme}'), *T(12, 12))
    paste(img, spr('prop/tray_fill').crop((0, 6, 24, 12)), *T(15, 25))
    paste(img, spr(f'prop/shoe_{theme}'), *T(356, 12))
    dx, dy = T(166, 14)
    draw_card(img, None, dx + 22, dy + 2, back=back)
    draw_card(img, 'KS', dx, dy)
    total_badge(img, dx - 24, dy + 4, '10')
    paste(img, spr(f'prop/rack_{theme}'), *T(164, -1))
    # rules + insurance band
    text_on_felt(img, 'BLACKJACK PAYS 3 TO 2', tx + 204, ty + 66, theme)
    print_sprite(img, 'insurance', *T(88, 74), theme)
    text_on_felt(img, 'INSURANCE PAYS 2 TO 1', tx + 204, ty + 80, theme)
    # other seats: spot, M cards toward the dealer, plate below the spot
    others = [
        ('Mira', 'mira', None, None, 50, ['9C', '7D'], '16', 'normal', None),
        ('Vern', None, 'villager', 'normal', 100, ['AH', 'KD'], 'BJ', 'normal', ('BLACKJACK!', 'gold')),
        ('Steve', 'steve', None, None, 25, ['5S', '6H', '10C'], '21', 'normal', None),
        ('Piggy', None, 'piglin', 'easy', 25, ['QC', '4S', '9H'], '23', 'folded', ('BUST', 'red')),
    ]
    for (cx, cy), (who, head, bot, lvl, bet, cards, tot, st, stp) in zip(BJ_SEATS, others):
        print_sprite(img, 'spot', *T(cx - 14, cy - 14), theme)
        w = 21 + 9 * (len(cards) - 1)
        sx, sy = T(cx - w // 2, cy - 48)
        draw_hand(img, cards, sx, sy, 9, size='m', dimmed=st == 'folded')
        total_badge(img, sx - 4, sy - 11, tot, gold=tot in ('21', 'BJ'))
        if st != 'folded':
            chip_stack(img, *T(cx, cy + 4), bet, label=False, hatch=bot is not None)
        px, py = {0: (cx - 62, cy + 12), 3: (cx - 12, cy + 12)}.get(BJ_SEATS.index((cx, cy)), (cx - 26, cy + 14))
        seat_plate(img, *T(px, py), who, fmt(bet) if st != 'folded' else 'Bust', 'folded' if st == 'folded' else 'normal', bot=bot, level=lvl, head=head)
        if stp:
            stamp(img, sx + w // 2, sy + 14, stp[0], stp[1], rot=-6 if stp[1] == 'gold' else 6)
    # the viewer: split hands — hand 1 stood on 18, hand 2 (active) doubled: 8 + 3 + sideways 9 = 20
    print_sprite(img, 'spot', *T(BJ_ME[0] - 14, BJ_ME[1] - 14), theme)
    h1x, h1y = T(136, 104)
    draw_hand(img, ['8S', 'QH'], h1x, h1y, 14)
    total_badge(img, h1x, h1y - 12, '18')
    paste(img, tint(spr('fx/progress'), '#C0B0DC').resize((51, 1)), h1x, h1y + 51)
    h2x, h2y = T(214, 104)
    paste(img, frame(spr('fx/glow_l'), 2, 4), h2x - 3, h2y - 3)
    draw_hand(img, ['8D', '3C'], h2x, h2y, 14)
    draw_card(img, '9H', h2x + 24, h2y + 20, sideways=True)
    total_badge(img, h2x, h2y - 12, '20', gold=True)
    chip_stack(img, *T(190, 164), 50, label=True)
    chip_stack(img, *T(218, 164), 100, label=True)
    # console: action buttons with icons (Double just pressed on hand 2 → Hit/Double disabled while in flight)
    FONT.draw(img, 'Your turn · hand 2 of 2', 8, 212, '#FFD640')
    FONT.draw(img, 'Bet 50 + 50 (doubled)', 8, 224, '#C0B0DC')
    bx = 152
    button(img, bx, 213, 60, 'Hit', theme, 'hit', state='disabled')
    button(img, bx + 64, 213, 66, 'Stand', theme, 'stand', family='primary', state='highlighted')
    button(img, bx + 134, 213, 70, 'Double', theme, 'double', state='disabled')
    button(img, bx + 208, 213, 62, 'Split', theme, 'split', state='disabled')
    save(img, name)


def holdem(theme='village', name='cards_holdem_showdown.png'):
    img, tx, ty = base(theme, 'oval', "Texas Hold'em · 5/10", 8_940)
    T = lambda x, y: (tx + x, ty + y)  # noqa: E731
    back = {'village': 'crimson', 'bastion': 'bastion', 'end': 'end'}[theme]
    # pot (above the board) + deck and muck
    print_sprite(img, 'pot', *T(180, 22), theme, alpha=0.35)
    paste(img, spr(f'prop/deck_{back}'), *T(130, 18))
    # board: the winner's best five lifted with a glow, unused cards dimmed
    board = ['KH', '9H', '2C', '5H', 'JS']
    best = {0, 1, 3}
    for i, c in enumerate(board):
        bx, by = T(106 + i * 40, 56)
        print_sprite(img, 'slot_l', bx, by, theme, alpha=0.3)
        draw_card(img, c, bx, by, lift=3 if i in best else 0, glow=i in best, dimmed=i not in best)
    # the pot, mid-slide to the winner (500 ms inOutCubic), with the monster-pot stamp
    chip_stack(img, *T(256, 126), 1_240, label=False, max_discs=6)
    chip_stack(img, *T(270, 128), 1_240, label=False, max_discs=6)
    FONT.draw(img, '2 480', tx + 263, ty + 129, '#FFD640', center=True)
    stamp(img, *T(204, 34), 'MONSTER POT!', 'gold', rot=-4)
    # six seats (6-max): me bottom centre, then clockwise
    seat_plate(img, *T(34, 140), 'Vern', '1 020', 'folded', bot='villager', level='normal')
    seat_plate(img, *T(-2, 64), 'Bo', '0', 'normal', bot='brute', level='hard')
    draw_hand(img, ['KS', 'KD'], *T(64, 50), 12, size='m')
    tag(img, *T(84, 83), 'Three kings')
    stamp(img, *T(34, 58), 'ALL-IN', 'red', rot=-5)
    seat_plate(img, *T(150, -8), 'Alex', '640', 'folded', head='alex')
    draw_hand(img, [None, None], *T(226, -4), 6, size='m', back=back, dimmed=True)
    seat_plate(img, *T(336, 64), 'Eli', '2 410', 'normal', bot='enderman', level='easy')
    draw_hand(img, [None, None], *T(314, 50), 6, size='m', back=back, dimmed=True)
    tag(img, *T(330, 36), 'Mucks')
    seat_plate(img, *T(308, 140), 'Mira', '3 700', 'winner', head='mira')
    paste(img, frame(spr('fx/glow_m'), 2, 4), *T(292 - 3, 108 - 3))
    paste(img, frame(spr('fx/glow_m'), 2, 4), *T(315 - 3, 108 - 3))
    draw_card(img, 'AH', *T(292, 108), size='m')
    draw_card(img, '7H', *T(315, 108), size='m')
    tag(img, *T(340, 100), 'Flush, ace high')
    paste(img, spr('prop/dealer_button'), *T(116, 124))
    # the viewer: hole cards over the rail, plate below; lost the pot
    mx, my = T(164, 118)
    draw_card(img, '10D', mx, my, dimmed=True)
    draw_card(img, '10C', mx + 41, my, dimmed=True)
    tag(img, mx + 39, my - 8, 'Pair of tens')
    seat_plate(img, *T(174, 168), 'Nezo', '1 180', 'me', head='nezo')
    # console
    FONT.draw(img, 'Mira wins 2 480 with a flush, ace high', 8, 212, '#FFD640')
    FONT.draw(img, 'Next hand in 3 s', 8, 224, '#8A7A9A')
    button(img, 246, 213, 84, 'Show cards', theme, 'pair')
    button(img, 334, 213, 86, 'Sit out', theme, 'leave')
    save(img, name)


def uth(theme='village', name='cards_uth_decision.png'):
    img, tx, ty = base(theme, 'crescent', "Ultimate Texas Hold'em", 4_300)
    T = lambda x, y: (tx + x, ty + y)  # noqa: E731
    paste(img, spr('prop/deck_emerald'), *T(352, 12))
    # dealer: two backs; rack in front of the dealer
    dx, dy = T(164, 12)
    draw_card(img, None, dx, dy, back='emerald')
    draw_card(img, None, dx + 41, dy, back='emerald')
    paste(img, spr(f'prop/rack_{theme}'), *T(164, -1))
    # board: 5 backs (the preflop decision)
    for i in range(5):
        bx, by = T(106 + i * 40, 64)
        print_sprite(img, 'slot_l', bx, by, theme, alpha=0.3)
        draw_card(img, None, bx, by, back='emerald')
    # paytable panel (top-left)
    nine(img, spr('panel/road'), *T(16, 12), 112, 46, 4)
    FONT.draw(img, 'Blind pays', tx + 22, ty + 16, '#FFD640')
    for i, (h, p) in enumerate([('Royal flush', '500:1'), ('Str. flush', '50:1'), ('Four kind', '10:1')]):
        FONT.draw(img, h, tx + 22, ty + 26 + i * 9, '#C0B0DC', shadow=False)
        FONT.draw(img, p, tx + 123, ty + 26 + i * 9, '#F4ECF8', shadow=False, right=True)
    # the viewer: hole cards over the rail (left), four circles in a row (right); labels above the circles
    circ = [('trips', 204, 25), ('ante', 238, 50), ('blind', 272, 50), ('play', 306, 0)]
    for kind, cx, amt in circ:
        FONT.draw(img, {'trips': 'Trips', 'ante': 'Ante', 'blind': 'Blind', 'play': 'Play'}[kind], tx + cx, ty + 121, PRINT[theme], shadow=False, center=True)
        print_sprite(img, f'uth_{kind}', *T(cx - 12, 134), theme)
        if amt:
            chip_stack(img, *T(cx, 151), amt, label=False)
    paste(img, frame(spr('fx/spot_glow'), 2, 4), *T(306 - 17, 146 - 17))
    ghost = Image.new('RGBA', (W, H))
    chip_stack(ghost, *T(306, 151), 200, label=False)
    paste(img, ghost, 0, 0, alpha=0.45)
    FONT.draw(img, '+200', tx + 322, ty + 138, '#FFD640')
    mx, my = T(100, 128)
    draw_card(img, 'AS', mx, my)
    draw_card(img, 'KS', mx + 41, my)
    tag(img, mx + 36, my - 11, 'Ace-king suited')
    # other seats: Mira (already Play ×4), Shelly the bot (deciding)
    draw_hand(img, [None, None], *T(52, 70), 7, size='m', back='emerald')
    chip_stack(img, *T(86, 104), 250, label=False)
    seat_plate(img, *T(22, 108), 'Mira', 'Play ×4', 'normal', head='mira')
    draw_hand(img, [None, None], *T(352, 64), 7, size='m', back='emerald')
    chip_stack(img, *T(340, 96), 100, label=False, hatch=True)
    seat_plate(img, *T(332, 100), 'Shelly', '', 'active', bot='shulker', level='hard', thinking=True)
    # console
    FONT.draw(img, 'Pre-flop: bet 3× or 4× the ante, or check', 8, 212, '#F4ECF8')
    FONT.draw(img, 'At risk: 125 · 12 s', 8, 224, '#8A7A9A')
    button(img, 222, 213, 64, 'Bet 4×', theme, 'raise', family='primary', state='highlighted')
    button(img, 290, 213, 64, 'Bet 3×', theme, 'play', family='primary')
    button(img, 358, 213, 62, 'Check', theme, 'check')
    save(img, name)


def baccarat(theme='village', name='cards_baccarat_third_card.png'):
    img, tx, ty = base(theme, 'crescent', 'Baccarat · Punto Banco', 21_900)
    T = lambda x, y: (tx + x, ty + y)  # noqa: E731
    paste(img, spr(f'prop/shoe_{theme}'), *T(348, 12))
    paste(img, spr(f'prop/tray_{theme}'), *T(20, 12))
    paste(img, spr('prop/tray_fill').crop((0, 4, 24, 12)), *T(23, 23))
    paste(img, spr(f'prop/rack_{theme}'), *T(164, -1))
    PB = {'player': '#3A6BE0', 'banker': '#D03030', 'tie': '#2FA64A', 'pair': '#FFD640'}
    # hand panels: Player (left) wins; third cards are dealt sideways
    for side, x0 in (('player', 58), ('banker', 214)):
        win = side == 'player'
        nine(img, tint(spr('print/box'), PB[side], 0.95 if win else 0.55), *T(x0, 14), 136, 68, 5)
        paste(img, tint(spr(f'print/emblem_{side}'), PB[side], 0.95), *T(x0 + 5, 18))
        FONT.draw(img, side.upper(), tx + x0 + 19, ty + 19, '#F4ECF8' if win else '#C0B0DC', shadow=win)
    draw_card(img, '4C', *T(64, 30))
    draw_card(img, '2D', *T(104, 30))
    draw_card(img, '3H', *T(144, 36), sideways=True, glow=False)
    total_badge(img, *T(176, 17), '9', gold=True)
    draw_card(img, 'KD', *T(220, 30), dimmed=True)
    draw_card(img, '7S', *T(260, 30), dimmed=True)
    total_badge(img, *T(332, 17), '7')
    stamp(img, *T(204, 86), 'PLAYER WINS  9 : 7', 'gold', rot=-3)
    # bet boxes (shared) with the bettors' seat-tinted mini stacks
    boxes = [('pair', 'P.PAIR 11:1', 62, 38), ('player', 'PLAYER 1:1', 80, 104), ('tie', 'TIE 8:1', 44, 188), ('banker', 'BANKER .95', 80, 236), ('pair', 'B.PAIR 11:1', 62, 320)]
    for kind, label, bw, bx0 in boxes:
        win = kind == 'player'
        if win:
            glow = frame(spr('fx/spot_glow'), 2, 4).resize((bw + 8, 36), Image.NEAREST)
            paste(img, glow, *T(bx0 - 4, 94), alpha=0.9)
        nine(img, tint(spr('print/box'), PB[kind], 0.95 if win else 0.6), *T(bx0, 98), bw, 28, 5)
        FONT.draw(img, label, tx + bx0 + bw // 2, ty + 101, '#F4ECF8' if win else PRINT[theme], shadow=win, center=True)
    chip_stack(img, *T(132, 124), 100, label=False)
    chip_stack(img, *T(148, 124), 100, label=False)
    FONT.draw(img, '+100', tx + 158, ty + 112, '#80FF40')
    chip_stack(img, *T(262, 124), 25, label=False, tint_hex='#FFB74D')
    chip_stack(img, *T(278, 124), 50, label=False, hatch=True)
    chip_stack(img, *T(210, 124), 5, label=False, tint_hex='#BA68C8')
    # bead road (6 rows; the newest bead drops in, runtime letters P/B/T)
    nine(img, spr('panel/road'), *T(150, 130), 108, 44, 4)
    road = 'PPBBTPBBPPPBPBBBTPBPPBBPBPPBBPPBBBP'
    for i, r in enumerate(road):
        col, row = divmod(i, 4)
        k = {'P': 'player', 'B': 'banker', 'T': 'tie'}[r]
        bx, by = T(154 + col * 10, 134 + row * 9)
        paste(img, spr(f'bead/{k}'), bx, by)
        FONT._draw(img, r, bx + 2, by + 1, '#FFFFFF')
        if i in (3, 13):
            paste(img, spr('bead/pair_player'), bx - 1, by - 1)
        if i == 22:
            paste(img, spr('bead/pair_banker'), bx + 7, by + 7)
    nb = T(154 + 9 * 10, 134 + 0 * 9 - 3)
    paste(img, spr('bead/player'), *nb)
    FONT._draw(img, 'P', nb[0] + 2, nb[1] + 1, '#FFFFFF')
    # seats (up to 7; four shown) along the rail
    seat_plate(img, *T(18, 132), 'Nezo', 'Player 100', 'winner', head='nezo')
    seat_plate(img, *T(300, 132), 'Steve', 'Banker 25', 'normal', head='steve')
    seat_plate(img, *T(44, 156), 'Wanda', 'Banker 50', 'normal', bot='witch', level='normal')
    seat_plate(img, *T(278, 156), 'Mira', 'Tie 5', 'normal', head='mira')
    # console
    FONT.draw(img, 'Player draws a 3 · Banker stands on 7', 8, 212, '#F4ECF8')
    FONT.draw(img, 'You win 100 · next coup in 4 s', 8, 224, '#80FF40')
    button(img, 250, 213, 84, 'Rebet', theme, 'rebet')
    button(img, 338, 213, 84, 'Clear bets', theme, 'clear')
    save(img, name)


def holdem_nether():
    holdem('bastion', 'cards_holdem_nether.png')


def blackjack_end():
    blackjack('end', 'cards_blackjack_end.png')


def blackjack_compact(theme='village', name='cards_blackjack_compact.png'):
    """GUI 284 x 160 (854 x 480 at GUI scale 3): compact table, M cards for the viewer and dealer, S for the others."""
    cw, ch = 284, 160
    img = Image.new('RGBA', (cw, ch), (0, 0, 0, 255))
    paste(img, gui(f'backdrop_{theme}').crop((72, 40, 72 + cw, 40 + ch)), 0, 0)
    tx, ty = 2, 12
    paste(img, gui(f'table_crescent_{theme}_compact'), tx, ty)
    nine(img, spr(f'panel/console_{theme}'), 0, 137, cw, 23, 7)
    plaque(img, 2, -2, FONT.width('Blackjack') + 20, 'Blackjack', theme)
    text = fmt(12_480)
    wv = FONT.width(text) + 20
    nine(img, core('panel/hud'), cw - wv - 3, 1, wv, 12, 3)
    paste(img, core('fx/chip_25'), cw - wv + 1, 3)
    FONT.draw(img, text, cw - wv + 12, 3, '#FFD640')
    T = lambda x, y: (tx + x, ty + y)  # noqa: E731
    paste(img, spr(f'prop/rack_{theme}').crop((0, 0, 80, 10)), *T(100, -1))
    paste(img, spr(f'prop/shoe_{theme}'), *T(238, 4))
    draw_card(img, 'KS', *T(118, 10), size='m')
    draw_card(img, None, *T(138, 11), size='m')
    total_badge(img, *T(100, 12), '10')
    text_on_felt(img, 'BLACKJACK PAYS 3 TO 2', tx + 140, ty + 44, theme)
    for (cx, cy, cards, tot) in [(46, 58, ['9C', '7D'], '16'), (226, 58, ['5S', '6H', '10C'], '21')]:
        w = 13 + 7 * (len(cards) - 1)
        draw_hand(img, cards, *T(cx - w // 2, cy - 20), 7, size='s', shadow=False)
        total_badge(img, *T(cx - w // 2 - 2, cy - 32), tot, gold=tot == '21')
        chip_stack(img, *T(cx, cy + 8), 25, label=False)
    draw_hand(img, ['8S', 'QH'], *T(92, 62), 9, size='m')
    total_badge(img, *T(92, 51), '18')
    paste(img, frame(spr('fx/glow_m'), 2, 4), *T(152 - 3, 62 - 3))
    draw_hand(img, ['8D', '3C'], *T(152, 62), 9, size='m')
    total_badge(img, *T(152, 51), '11', gold=True)
    chip_stack(img, *T(128, 104), 50)
    chip_stack(img, *T(152, 104), 50)
    button(img, 4, 139, 50, 'Hit', theme, 'hit')
    button(img, 57, 139, 58, 'Stand', theme, 'stand')
    button(img, 118, 139, 64, 'Double', theme, 'double', family='primary')
    button(img, 185, 139, 52, 'Split', theme, 'split', state='disabled')
    FONT.draw(img, 'Hand 2', 242, 145, '#FFD640')
    out = img.convert('RGB').resize((cw * 3, ch * 3), Image.NEAREST)
    out.save(OUT / name, optimize=True)
    print('wrote', OUT / name)


def main(argv):
    global FONT, IDX
    if '--no-gen' not in argv:
        subprocess.run(['node', 'tools/gen-assets.mjs', '--module', 'cards', '--edition', 'java'], cwd=ROOT / 'bedrock', check=True)
    FONT = Font()
    IDX = IndexFont()
    blackjack()
    holdem()
    uth()
    baccarat()
    holdem_nether()
    blackjack_end()
    blackjack_compact()


if __name__ == '__main__':
    main(sys.argv[1:])
