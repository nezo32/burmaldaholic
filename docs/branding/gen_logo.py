#!/usr/bin/env python3
"""Generates the Burmaldaholic logo: 40x40 pixel art in the Enchantaholic style, upscaled.

Outputs (paths relative to the repo root):
  docs/branding/curseforge_logo.png                 400x400, CurseForge project avatar
  bedrock/assets/pack_icon.png                      256x256, Bedrock pack icon
  java/src/main/resources/assets/burmaldaholic/icon.png  128x128, Fabric/Mod Menu icon

Usage: python3 docs/branding/gen_logo.py   (needs Pillow)
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
N = 40  # grid size

# Palette: frame colors shared with Enchantaholic, chip colors are Burmaldaholic's own.
BG = (0x26, 0x10, 0x3C)
DARK = (0x14, 0x08, 0x22)
INK = (0x18, 0x0A, 0x28)
FRAME = (0x78, 0x3C, 0xBE)
LILAC = (0xD6, 0x96, 0xFF)
GLINT = (0xBE, 0x5A, 0xFF)
WHITE = (0xFF, 0xFF, 0xFF)
GOLD = (0xFF, 0xD6, 0x40)
GOLD_SHADE = (0xB0, 0x70, 0x10)
GREEN = (0x80, 0xFF, 0x40)
RED = (0xD8, 0x34, 0x40)
RED_LIGHT = (0xFF, 0x6E, 0x6A)
RED_DARK = (0x8C, 0x18, 0x34)
BONE = (0xF4, 0xEC, 0xF8)
BONE_SHADE = (0xC0, 0xB0, 0xDC)

px = [[BG for _ in range(N)] for _ in range(N)]


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[y][x] = c


# Frame: dark edge, purple line, gold corner studs.
for i in range(N):
    for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
        put(x, y, DARK)
    for (x, y) in ((i, 1), (i, N - 2), (1, i), (N - 2, i)):
        if 1 <= i <= N - 2:
            put(x, y, FRAME)
for (cx, cy) in ((1, 1), (N - 2, 1), (1, N - 2), (N - 2, N - 2)):
    put(cx, cy, GOLD)

# Big casino chip, upper left, with a drop shadow.
CX, CY, R = 17.5, 17.5, 12.6
for y in range(N):
    for x in range(N):
        d = math.hypot(x + 0.5 - CX - 1, y + 0.5 - CY - 1)
        if d <= R + 0.6 and px[y][x] == BG:
            put(x, y, DARK)  # shadow offset down-right
for y in range(N):
    for x in range(N):
        dx, dy = x + 0.5 - CX, y + 0.5 - CY
        d = math.hypot(dx, dy)
        if d > R:
            continue
        ang = (math.degrees(math.atan2(dy, dx)) + 360 + 15) % 360
        light = (-dx - dy) / (R * 1.41)  # +1 top-left, -1 bottom-right
        if d > R - 1.1:
            c = INK
        elif d > R - 3.6:  # edge band with 8 white inserts
            if (ang % 45) < 17:
                c = BONE if light > -0.35 else BONE_SHADE
            else:
                c = RED_LIGHT if light > 0.55 else (RED if light > -0.45 else RED_DARK)
        elif d > R - 4.5:
            c = INK
        elif d > R - 5.6:
            c = GOLD if light > -0.2 else GOLD_SHADE
        else:
            c = RED_LIGHT if light > 0.6 else (RED if light > -0.5 else RED_DARK)
        put(x, y, c)

# Centre emblem: a gold diamond suit (plain shape, no text).
ex, ey = int(CX), int(CY)
for dy in range(-4, 5):
    w = 3 - abs(dy) if abs(dy) <= 3 else -1
    for dx in range(-w, w + 1):
        put(ex + dx, ey + dy, GOLD if (dx <= 0 and dy <= 0) or dx + dy < 0 else GOLD_SHADE)
put(ex - 1, ey - 1, WHITE)

# Sparkles (same crosses as Enchantaholic).
def sparkle(x, y, c, big=False):
    put(x, y, WHITE if big else c)
    for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(x + a, y + b, c)
    if big:
        for (a, b) in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            put(x + a, y + b, LILAC)


sparkle(5, 6, LILAC, big=True)
sparkle(33, 7, LILAC)
sparkle(30, 17, GLINT)

# Gold up-chevrons (winnings climbing), lower right.
def chevron(x, y):
    shape = ["...#...", "..###..", ".##.##.", "##...##"]
    for j, row in enumerate(shape):
        for i, ch in enumerate(row):
            if ch == "#":
                put(x + i, y + j, GOLD if j < 2 or i in (0, 1, 5, 6) and j == 2 else GOLD_SHADE)
    for i in range(7):  # ink under-shadow
        if px[y + 4][x + i] == BG:
            put(x + i, y + 4, INK)


for k in range(3):
    chevron(29, 22 + k * 4)

# Green plus, bottom centre (arms two pixels thick, ink outline like Enchantaholic's).
PLUS = ["..##..", "..##..", "######", "######", "..##..", "..##.."]
for j, row in enumerate(PLUS):
    for i, ch in enumerate(row):
        if ch == "#":
            for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if px[31 + j + b][19 + i + a] == BG:
                    put(19 + i + a, 31 + j + b, INK)
for j, row in enumerate(PLUS):
    for i, ch in enumerate(row):
        if ch == "#":
            put(19 + i, 31 + j, GREEN)

# A die, bottom left: ink outline, bone face with a shaded side, four pips.
DX, DY, S = 4, 28, 8
for y in range(S):
    for x in range(S):
        edge = x in (0, S - 1) or y in (0, S - 1)
        put(DX + x, DY + y, INK if edge else (BONE_SHADE if x == S - 2 or y == S - 2 else BONE))
for (x, y) in ((2, 2), (5, 2), (2, 5), (5, 5)):
    put(DX + x, DY + y, RED_DARK)

img = Image.new("RGB", (N, N))
for y in range(N):
    for x in range(N):
        img.putpixel((x, y), px[y][x])

outputs = {
    "docs/branding/curseforge_logo.png": 400,
    "bedrock/assets/pack_icon.png": 256,
    "java/src/main/resources/assets/burmaldaholic/icon.png": 128,
}
for rel, size in outputs.items():
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((size, size), Image.NEAREST).save(path)
    print("wrote", rel)
