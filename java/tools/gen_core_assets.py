#!/usr/bin/env python3
"""Generate the core placeholder assets (16x16 textures, models, blockstates, loot, recipes).

    python3 java/tools/gen_core_assets.py

Pure Python (zlib PNG writer, no Pillow). Textures are simple procedural art with NO baked-in text
(chip value is shown by colour + tooltip), so artists can replace them 1:1 later. Re-running
overwrites only the files listed here. Asset names are core ids from java/config/core-ids.txt.
"""
from __future__ import annotations

import json
import struct
import zlib
from pathlib import Path

JAVA = Path(__file__).resolve().parent.parent
ASSETS = JAVA / "src" / "main" / "resources" / "assets" / "burmaldaholic"
DATA = JAVA / "src" / "main" / "resources" / "data"


def png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    body = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(body)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


CLEAR = (0, 0, 0, 0)


def chip(base, stripe):
    """Round casino chip seen slightly from above: rim with 8 edge stripes, inner ring, centre inlay."""
    px = [[CLEAR] * 16 for _ in range(16)]
    cx = cy = 7.5
    import math
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, (y - cy) * 1.0
            r = math.hypot(dx, dy)
            if r > 7.6:
                continue
            ang = (math.degrees(math.atan2(dy, dx)) + 360) % 360
            if r > 6.2:  # rim with edge spots
                c = stripe if int(ang // 22.5) % 2 == 0 else base
                px[y][x] = shade(c, 0.8 if dy > 3 else 1.0)
            elif r > 5.2:
                px[y][x] = shade(base, 0.7)
            elif r > 3.0:
                px[y][x] = shade(base, 1.05 - 0.04 * dy)
            else:
                px[y][x] = shade(stripe, 0.95 - 0.03 * dy)
    return px


def card():
    px = [[CLEAR] * 16 for _ in range(16)]
    gold, dark, green = (212, 175, 55), (30, 30, 36), (24, 110, 60)
    for y in range(3, 13):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (3, 12)
            px[y][x] = shade(gold if edge else dark, 1.0)
    for x in range(2, 14):
        px[5][x] = shade(green, 1.0)
    for y, x in ((8, 3), (8, 4), (9, 3), (9, 4)):
        px[y][x] = shade(gold, 1.1)
    for x in range(7, 13):
        px[10][x] = shade((150, 150, 160), 1.0)
    return px


def block_face(kind, base, accent, trim):
    px = [[shade(base, 1.0) for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if (x * 7 + y * 3) % 11 == 0:
                px[y][x] = shade(base, 0.9)
            if y in (0, 15) or x in (0, 15):
                px[y][x] = shade(trim, 1.0)
    if kind == "front":  # counter with a brass grille
        for y in range(2, 9):
            for x in range(2, 14):
                px[y][x] = shade(accent, 1.0) if (x % 3 == 0 or y in (2, 8)) else shade((20, 20, 24), 1.0)
        for x in range(1, 15):
            px[10][x] = shade(trim, 1.2)
            px[11][x] = shade(trim, 0.8)
        for y, x in ((13, 7), (13, 8)):
            px[y][x] = shade(accent, 1.1)
    elif kind == "top":
        for y in range(3, 13):
            for x in range(3, 13):
                px[y][x] = shade((30, 100, 55), 1.0)
        for y, x in ((7, 7), (7, 8), (8, 7), (8, 8)):
            px[y][x] = shade(accent, 1.0)
    return px


def write_json(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


CHIPS = {
    1: ((236, 236, 236), (60, 90, 200)),
    5: ((200, 30, 35), (245, 245, 245)),
    25: ((30, 150, 60), (245, 245, 245)),
    100: ((28, 28, 32), (230, 200, 60)),
    500: ((120, 45, 170), (245, 235, 120)),
}

CASHIERS = {
    "cashier": ((110, 72, 40), (205, 160, 60), (70, 45, 25)),
    "nether_cashier": ((45, 28, 32), (240, 190, 50), (110, 20, 25)),
}


def main() -> None:
    for value, (base, stripe) in CHIPS.items():
        name = f"chip_{value}"
        png(ASSETS / "textures" / "item" / "core" / f"{name}.png", chip(base, stripe))
        write_json(ASSETS / "models" / "item" / "core" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"burmaldaholic:item/core/{name}"}})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"burmaldaholic:item/core/{name}"}})

    png(ASSETS / "textures" / "item" / "core" / "casino_card.png", card())
    write_json(ASSETS / "models" / "item" / "core" / "casino_card.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "burmaldaholic:item/core/casino_card"}})
    write_json(ASSETS / "items" / "casino_card.json", {"model": {"type": "minecraft:model", "model": "burmaldaholic:item/core/casino_card"}})

    for name, (base, accent, trim) in CASHIERS.items():
        for face in ("front", "side", "top"):
            png(ASSETS / "textures" / "block" / "core" / f"{name}_{face}.png", block_face(face, base, accent, trim))
        model = f"burmaldaholic:block/core/{name}"
        write_json(ASSETS / "models" / "block" / "core" / f"{name}.json", {
            "parent": "minecraft:block/orientable",
            "textures": {f: f"burmaldaholic:block/core/{name}_{f}" for f in ("front", "side", "top")},
        })
        write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {
            "facing=north": {"model": model},
            "facing=east": {"model": model, "y": 90},
            "facing=south": {"model": model, "y": 180},
            "facing=west": {"model": model, "y": 270},
        }})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": model}})
        write_json(DATA / "burmaldaholic" / "loot_table" / "blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [{"type": "minecraft:item", "name": f"burmaldaholic:{name}"}]}],
            "random_sequence": f"burmaldaholic:blocks/{name}",
        })

    # Recipes (GAME_DESIGN.md §3.2, §3.3).
    write_json(DATA / "burmaldaholic" / "recipe" / "cashier.json", {
        "type": "minecraft:crafting_shaped",
        "key": {"G": "minecraft:gold_ingot", "E": "minecraft:emerald", "I": "minecraft:iron_ingot", "C": "minecraft:chest"},
        "pattern": ["GEG", "ICI", "III"],
        "result": {"id": "burmaldaholic:cashier"},
    })
    write_json(DATA / "burmaldaholic" / "recipe" / "casino_card.json", {
        "type": "minecraft:crafting_shapeless",
        "ingredients": ["minecraft:paper", "minecraft:gold_nugget", "minecraft:emerald"],
        "result": {"id": "burmaldaholic:casino_card"},
    })

    # Soul Wager damage type (§4.4): bypasses armor, effects, totems (bypasses_invulnerability).
    write_json(DATA / "burmaldaholic" / "damage_type" / "soul_wager.json",
               {"exhaustion": 0.0, "message_id": "burmaldaholic.soul_wager", "scaling": "never"})
    for tag in ("bypasses_armor", "bypasses_effects", "bypasses_enchantments", "bypasses_invulnerability", "bypasses_resistance", "no_knockback"):
        write_json(DATA / "minecraft" / "tags" / "damage_type" / f"{tag}.json", {"values": ["burmaldaholic:soul_wager"]})
    print("core assets written")


if __name__ == "__main__":
    main()
