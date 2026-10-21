# Casino buildings — spawn diagnosis, sources, and the v0.2.0 design

Status: **decision-ready proposal** (2026-10-07). Owner feedback on v0.1.1: *"explored and found no
casinos — spawn rate really low"* and *"looks horrible, just a box"*. This document answers both:
§1–2 why they are missing and the target numbers, §3 whether we can legally reuse someone else's
build, §4–8 the chosen approach (our own buildings from a code generator) with palettes, plans,
lighting and review renders. Short root-cause note: [docs/review/casino-spawn-root-cause.md](../review/casino-spawn-root-cause.md).

Decisions the owner needs to take are marked **DECIDE**.

---

## 1. Spawn diagnosis (measured)

### 1.1 How v0.1.1 places casinos

| Casino | Mechanism (code) | Gate |
|---|---|---|
| Village "Lucky Villager" 17×10×17 | `JigsawPlacerMixin` → `VillageCasinos`: extra element of `minecraft:village/<type>/terminators` (street ends), max 1 per village | `worldgen.villageCasino.chance` 0.35 per village |
| Piglin Parlor 21×12×21 | `StructureMixin` → `CasinoStructures.appendParlor`: room beside a bastion | `piglinParlor.chance` 0.30 |
| High Roller Lounge 15×9×15 | same, on the highest End City tower top | `highRoller.chance` 0.20 |

All three additionally require `WorldgenRuntime.active()` = `worldgen.enabled` **and casino mode on**.
There is **no structure / structure_set of our own** — casinos only exist inside vanilla structures.

### 1.2 Measurement

`java/src/gametest/java/dev/nezo/burmaldaholic/gametest/worldgen/WorldgenSpawnSurvey.java` builds the
default ("normal") world preset's generators for a given seed and runs the *real* structure-start
code for every placement cell of `minecraft:villages`, `minecraft:nether_complexes` and
`minecraft:end_cities` within a radius — vanilla spread grid, weighted structure choice, biome
check, jigsaw assembly, our mixins — without generating chunks (≈3 min for 4 seeds × 3 dims × 2
passes). It is a no-op unless enabled:

```
cd java && JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dburmaldaholic.worldgenSurvey=true" ./gradlew runGameTest   # grep "[survey]"
```

Pass 1 uses the shipped chances; pass 2 forces every chance to 1 to measure the *fit rate* (does the
room physically fit where we try to put it).

**Overworld, radius 2000 blocks (12.6 km² per seed):**

| Seed | Villages | Casinos (0.35) | Nearest casino | Fit at chance 1 |
|---|---|---|---|---|
| 12345 | 10 (plains 5, snowy 3, savanna 1, taiga 1) | 3 | 880 | 7/10 (snowy 1/3, taiga 0/1) |
| 42 | 8 | 2 | 1174 | 8/8 |
| -4172144997902289642 | 9 | 2 | 1216 | 9/9 |
| 8675309 | 17 | 4 | 820 | 15/17 (desert 0/1) |
| **Total** | **44** (0.87/km²) | **11** (0.22/km² = **1 per 4.6 km²**) | **820–1216** | **39/44 = 89 %** |

**Nether, radius 1000 nether blocks:** 30 bastions → 10 Parlors (33 %), fit 30/30, nearest Parlor
211–1014 nether blocks. **End, radius 3000:** 117 End Cities → 16 Lounges (14 %), fit 117/117,
nearest 1073–1933 (End Cities start ~1000 out).

### 1.3 Root causes (ranked)

1. **Casino mode gates worldgen and is OFF by default.** New worlds where the player did not press
   the Create-World button, and every dedicated server until an op runs `/casino mode on`, generate
   the spawn area and all early exploration without casinos; there is no retro-generation. For the
   owner and the CurseForge multiplayer reporter this alone explains "none".
2. **Rare by construction.** ~1 casino per 4.6 km² ≈ one every ~2.1 km; the nearest from spawn was
   0.8–1.2 km in every seed — and only if you happen to walk to the right village.
3. **Not findable / not recognisable.** No structure id (`/locate`, explorer maps, compass mods can't
   target it); the 17×17 flat-roofed box at a street end looks like a village house
   (`mockups/casinos/current_village_casino_plains.png`).

Ruled out: data pack loading (DEFAULT_ENABLED; `casinoPackLoadedWithAllTemplates` passes), biome
tags (none used yet), dimension keys, terrain adaptation (rigid piece; failures are only the narrow
snowy/taiga/desert street ends). Survey run on 26.2 only.

## 2. Spawn targets (proposal)

Goal from the brief: *a casino within a short trip* — every ~600–1000 blocks in the Overworld.

| Variant | v0.1.1 | **Proposed** | Expected (from §1.2 rates) |
|---|---|---|---|
| **Overworld casino** (new standalone structure `burmaldaholic:casino`, §4) | — | `random_spread` **spacing 40, separation 12** (640-block cells), salt `0x5EEDCA51`→ int, biomes `#burmaldaholic:has_casino` | ~60 % of cells pass the biome → **1 per ~0.68 km², mean spacing ≈ 830 blocks**, nearest from spawn typically 300–700 |
| Village annex "Lucky Villager" | 0.35 × 89 % | **0.5** (keep street-end placement, new smaller facade piece) | +~0.4 per km² as a bonus |
| **Piglin Parlor** (bastion annex) | 0.30 | **0.6** | 1 per ~1.6 bastions; nearest ≈ 200–500 nether blocks |
| **High Roller Lounge** (End City annex) | 0.20 | **0.5** | ~1 in 2 End Cities |
| End standalone "Void Casino" (optional, v0.2.x) | — | spacing 24, separation 8, `#minecraft:has_structure/end_city` biomes | for players who skip End Cities |

`#burmaldaholic:has_casino` = union of the vanilla tags `#minecraft:has_structure/village_plains`,
`village_desert`, `village_savanna`, `village_taiga`, `village_snowy` plus `minecraft:meadow`,
`minecraft:cherry_grove`, `minecraft:forest`, `minecraft:birch_forest`, `minecraft:badlands`. Using the
vanilla structure tags keeps us correct across 26.x biome changes. The structure picks its palette
variant from the biome at the start (pool per biome tag, see §4.3).

Exclusion: `exclusion_zone { other_set: minecraft:villages, chunk_count: 3 }` so a standalone casino
never lands inside a village (the annex covers that case).

**DECIDE — casino mode vs. buildings.** Recommended: generate the *buildings* whenever
`worldgen.enabled` (default true), regardless of casino mode. The tables inside already follow casino
mode themselves (closed when off), NPCs can idle with a "closed" line, loot is chips that do nothing
while off. This removes root cause 1 entirely. Alternative if §2.1 "dormant mode does nothing" must
hold literally: keep the gate, but on `/casino mode on` tell the op "casinos only appear in new
chunks — /casino worldgen locate" and give the Croupier an explorer-map trade. Either way add
`/casino worldgen locate` (wraps `/locate structure burmaldaholic:casino`).

**Acceptance:** extend `WorldgenSpawnSurvey` with the new set; across the 4 seeds, ≥ 8 Overworld
casinos within 2000 blocks and median nearest ≤ 700 blocks; fit rate of the standalone structure
≥ 95 % (it is placed on its own, terrain-adapted with `beard_thin`).

## 3. Reusing existing builds — sources & licenses

Rule: only assets with an explicit licence that allows redistribution inside a mod (CC0, CC-BY with
attribution, MIT and similar). Searched 2026-10-07: Modrinth API (projects named casino/vegas,
structure packs filtered by licence), Planet Minecraft, CreateMod, donut.build, BuiltByBit, GitHub.

| Source | What | Licence | Usable? |
|---|---|---|---|
| [Let's Go Gambling!](https://modrinth.com/mod/letsgogambling) by BobR0ssiter | Fabric mod with a natural `poker_room` structure (13×9×11, spacing 32 / sep 8) | MIT (declared on Modrinth and in `fabric.mod.json`; no LICENSE file / source repo) | Legally yes with attribution, but it is a single small room — not better than ours. **Not used.** Useful as a reference: they also chose a standalone structure with village-like spacing |
| [Extra Professions](https://modrinth.com/mod/extra_professions) | Village houses (glazier, chandler, lumber) per village type | CC0-1.0 | Legal, but no casino content. **Not used** |
| [Casino Rocket](https://modrinth.com/mod/casino-rocket), [Diamond Casino](https://modrinth.com/mod/diamond-casino), [Tiny Casino](https://modrinth.com/mod/tiny-casino), [CasinoX](https://modrinth.com/mod/casinox-mod), [Cobble Casino](https://modrinth.com/mod/cobble-casino), [Cobblemon Casino](https://modrinth.com/mod/cobblemon-casino), [YJ's Casino](https://modrinth.com/mod/yjs-casino-and-economy) | Casino mods | MIT | Jars inspected: **no structure templates** at all |
| [CasinoCraft](https://modrinth.com/mod/casinocraft), [Casino Mod](https://modrinth.com/mod/casino-mod), [Dark Casino](https://modrinth.com/mod/darkcasino) | Casino mods | All rights reserved | No |
| [Oz_6's Medieval gambling](https://donut.build/schematic/oz6s-medieval-gambling) (39×20×51) | Litematica schematic | none stated | No (no licence = all rights reserved) |
| [Casino ODISEO](https://builtbybit.com/resources/casino-odiseo.53346/) | Paid server build | commercial, no redistribution | No |
| Planet Minecraft casino projects | Many schematics | per-project; listing pages return 403 to our tools; web search found no casino project with a CC/MIT licence | No verified candidate |

**Conclusion:** no clearly licensed, good-looking casino build exists to download. We design our own,
generated from code (below). Credits are not required; if a future piece borrows from a CC-BY/MIT
build, record it in this table and in `README.md` credits.

## 4. Chosen approach: blueprint generator → jigsaw templates

We already have the right foundation: `worldgen/logic/Grid` + `Layouts` (pure Java) + `TemplateExporter`
(writes vanilla structure `.nbt`, run by `TemplateExportTest`), with data markers for NPCs/chests and
tests on geometry. v0.1.1 looks like a box because `Layouts` only has `box`/`walls` and one flat
template per casino. Keep the pipeline (one source of truth with the marker/NPC code, deterministic,
diffable, no hand-edited binaries) and add:

### 4.1 Blueprint DSL (Java, `worldgen/logic/build/`)

Readable, palette-agnostic verbs on top of `Grid`; materials are *roles* resolved by a `Palette`:

```java
Blueprint b = Blueprint.of(31, 17, 31, palette);
b.foundation(3, 6, 27, 26);                        // role FOUNDATION, 1 layer + skirt into terrain
b.room("hall", 3, 1, 6, 27, 7, 26)                 // walls WALL, floor FLOOR, ceiling CEILING
 .frame(Frame.TIMBER, every(6))                    // pilasters + sill/lintel beams (role FRAME)
 .windows(Window.TALL_ARCH, side(EAST, WEST, BACK), every(6))
 .door(Side.FRONT, center(), width(5), height(4));
b.roof(Roof.HIP, 2, 8, 5, 28, 27, layers(4)).ridge(TRIM).eaves(EAVE_SLAB);
b.lantern(11, 11, 19, 21, Roof.HIP);               // clerestory over the gaming floor
b.portico(12, 3, 18, 5, Columns.QUARTZ).canopy(TRIM);
b.marquee("CASINO", Side.FRONT, y(9), Bulbs.EVERY_2); // pixel sign, mirrored correctly for the viewer, bulb border
b.kit(Kits.TABLE_POD, BLACKJACK, at(7, 1, 10), facing(SOUTH));
b.kit(Kits.SLOT_BANK, SLOTS_COPPER, line(5, 1, 24, 25, 1, 24), skip(13, 17));
b.kit(Kits.BAR, at(5, 1, 20), length(7));
b.kit(Kits.CASHIER_CAGE, at(19, 1, 20));
b.light(Lighting.CHANDELIER, grid(6)).ensureMinBlockLight(1);
b.jigsaw("burmaldaholic:casino/hall_east", at(27, 1, 16), EAST, target("burmaldaholic:casino/wing"));
b.marker(Markers.npc(CROUPIER), at(15, 1, 20));
```

Verbs: `foundation, room, frame, windows, door, roof (FLAT_PARAPET | HIP | GABLE | DOME | STEPPED),
lantern, portico, marquee, banner, awning, kit(...)`, `light`, `jigsaw`, `marker`. Kits are small
reusable furniture prefabs (table pod with stools and rope barrier, slot bank with ceiling neon,
bar with stools/bottles/barrels, cashier cage with iron bars, VIP sofa set, potted plants, red
carpet runner, chip-stack decorations from our own blocks). Palette roles: `FOUNDATION, FLOOR,
CARPET, WALL, FRAME, CEILING, ROOF, EAVE_SLAB, TRIM, GLASS, LIGHT, NEON_A, NEON_B, SIGN_BACK, ACCENT`.

### 4.2 Jigsaw pieces (one template each, all generated)

| Piece | Size (X×Y×Z) | Contents | Connects to |
|---|---|---|---|
| `entrance/<variant>` (start) | 31×17×9 | forecourt, lamp posts, portico, 5-wide door, marquee sign, lobby with cashier | `floor` (back) |
| `floor/<variant>_a`, `_b` | 31×13×15 | gaming floor: 4 table pods (blackjack, roulette, craps/baccarat, UTH), slot banks on two walls, clerestory | `bar` (side), `vip` (back, rare), `back` (back) |
| `bar/<variant>` | 11×9×15 | bar + Loan Shark booth, wheel of fortune / plinko alcove | — |
| `vip/<variant>` | 15×11×11 | VIP room behind a velvet-rope arch: poker table (bots), gold slots, sofa set | — (weight 1 vs `back` 2) |
| `back/<variant>` | 15×9×9 | office, loot chest marker, staff door | — |

Structure JSON (`data/burmaldaholic/worldgen/structure/casino.json`): `type: jigsaw`,
`start_pool: burmaldaholic:casino/entrance`, `size: 4`, `start_height: {absolute: 0}`,
`project_start_to_heightmap: WORLD_SURFACE_WG`, `max_distance_from_center: 48`,
`terrain_adaptation: beard_thin`, `step: surface_structures`, `biomes: #burmaldaholic:has_casino`.
Assembled footprint: 31×17×31 (entrance + floor + back) up to 45×17×41 (+bar +vip). All pieces
`rigid`; foundation skirt (3 layers of FOUNDATION below y=0) so hillsides do not float.

Piglin Parlor and High Roller Lounge stay annexes (their host decides where) but are rebuilt with the
same DSL, as 2–3 pieces placed by `CasinoStructures` (entrance hall + gaming room + vault), sizes
37×20×37 and 31×18×31.

### 4.3 Variants

The start pool for the standalone casino is chosen per biome by **separate structures sharing one
structure_set** (`casino_plains`, `casino_desert`, … each with its own biome tag, weight 1) — this is
how vanilla picks village types and avoids processor-list format differences between 26.2 and 26.3.

## 5. Palettes & materials

| Variant | Style | FLOOR / CARPET | WALL / FRAME | ROOF / TRIM | LIGHT / NEON | Accent |
|---|---|---|---|---|---|---|
| Plains, forest, meadow | Tudor gambling hall | dark oak planks / red carpet | white terracotta / stripped dark oak log | deepslate tiles, dark oak slab eaves / gold block | ochre froglight, lanterns on chains / red + yellow concrete letters | quartz pillars, red banners, potted flowers |
| Desert, badlands | Art-deco "Oasis" | smooth sandstone / cyan carpet | cut sandstone / chiseled sandstone, orange terracotta bands | flat with stepped parapet, smooth sandstone / gold block | sea lantern, ochre froglight / orange + lime concrete | palm-like jungle-log + leaf planters, cyan glass |
| Savanna | Safari lodge | acacia planks / orange carpet | acacia log / stripped acacia, white terracotta | hay-bale thatch hip roof / acacia slabs | lanterns, shroomlight / yellow + red | terracotta pots, banners |
| Taiga, cherry | Alpine lodge | spruce planks / green carpet | spruce log / stripped spruce, mossy cobble base | steep gable, spruce stairs / copper (waxed) | lanterns, campfire hearth / yellow + white | antler-style fences, barrels |
| Snowy | Ice palace | polished diorite / light-blue carpet | packed ice / quartz pillar, spruce | blue-ice dome / quartz slabs | pearlescent froglight, sea lantern / cyan + white | snow layers, white banners |
| Nether Parlor | Piglin gold vault | polished blackstone bricks / red carpet | polished blackstone, gilded blackstone / crimson stems | stepped blackstone / gold block | shroomlight, soul lanterns / gold + red | magma accents, crimson nylium planters |
| End Lounge | Void penthouse | end stone bricks / purple carpet | purpur block / purpur pillar, obsidian | flat, purpur slabs / crying obsidian | end rods, pearlescent froglight / magenta + white | amethyst, black stained glass |

Avoid redstone lamps for neon (they turn off when the template is placed without power) — use
froglights, concrete letters and lanterns; avoid gravity blocks (sand) outside foundations.

## 6. Room layout — Overworld casino (plains, 31×31, front = z 0)

```
 z   x→ 0         5        10        15        20        25       30
 0   . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .   forecourt (path), lamp posts
 2   . . . L . . . . . . . . P . . . . . P . . . . . . . . . L . .   P = portico pillars
 4   . . . . . . . . . . . . P . . . . . P . . . . . . . . . . . .   marquee CASINO above (y 8–14)
 6   . . . F W W W W W F W W W D D D D D W W F W W W W W F . . . .   D = 5-wide door, F = timber frame
 8   . . . W  [BJ]                carpet                [RO]  W
10   . . . W  [BJ]    stools      aisle      stools     [RO]  W        table pods, 3 seats each
13   . . . W                        ▒                         W        ▒ clerestory above (y 11–13)
16   . . . W  [CR]                  ▒                   [UTH] W
18   . . . W  [CR]                                      [UTH] W
20   . . . W  B B B B B B B (bar)     C C C C C C C (cashier) W        Croupier NPC at cashier
22   . . . W                                                  W
24   . . . W  $ $ $ $ $ $ $ $     (aisle)   $ $ $ $ $ $ $ $   W        $ = slot bank (copper/gold)
26   . . . F W W W W W F W W W W W J W W W W F W W W W W F . . . .   J = jigsaw → back / VIP piece
```

Circulation: straight red-carpet aisle from the door to the back jigsaw (the VIP arch when present),
tables in pods left and right, slots against the side/back walls, bar + cashier as a cross-axis
(the player sees the cashier from the door — first stop to buy chips). Min. 2 free blocks around
every table seat so the in-world table rendering stays readable and players can click every seat.

## 7. Lighting

- Hostile mobs in 26.x spawn only at block light 0: every interior air cell must have block light ≥ 1;
  the generator checks this (`ensureMinBlockLight`, flood-fill test in `LayoutsTest`). Target ≥ 10
  over tables so the felt and cards read well.
- Ceiling: chandelier kit (chain + lantern, or froglight under a trapdoor) on a 6-block grid;
  clerestory glass gives daylight to the floor.
- Facade: marquee bulbs every 2 blocks around the sign (froglights), lit portico canopy, lamp posts
  on the forecourt — the casino must be readable from ~100 blocks at night.
- Nether: shroomlight + soul lanterns; End: end rods in pairs, pearlescent froglight under black glass.

## 8. Review renders (no game needed)

`docs/design/visual/render_structure.py` (Pillow only) renders any structure `.nbt` isometrically
with flat per-block colours, optionally cut at a layer to show the floor plan. Output in
`docs/design/visual/mockups/casinos/`:

- `current_*.png` — the v0.1.1 templates (the "box").
- `concept_village_plains.png` / `_plan.png` — a quick blockout of §6 (prototype of the DSL verbs
  in Python: `hip_roof`, `sign` with correct mirroring, portico, clerestory). It is a massing/palette
  check, not final art.

When the Java generator lands, `TemplateExportTest` writes the templates and the script renders every
piece and every assembled variant (all pieces at their jigsaw offsets) so reviews happen on PNGs in PRs.

## 9. Work plan (v0.2.0)

1. Structure + structure_set + biome tags for `burmaldaholic:casino` (start with the existing village
   templates as the single piece) and the chance bumps of §2; extend `WorldgenSpawnSurvey` → verify
   the §2 acceptance numbers. Decide casino-mode coupling (§2). *Fixes the spawn complaint on its own.*
2. Blueprint DSL + kits + palettes; plains variant pieces; renders; `LayoutsTest` (bounds, light,
   seat clearance, markers inside, jigsaw pairs match).
3. Remaining variants, Parlor and Lounge rebuilds; real-flow GameTests: `/place structure
   burmaldaholic:casino` → tables usable (open by use, bet, server state), NPC spawned, loot chest
   filled, `/casino worldgen locate` finds it.
