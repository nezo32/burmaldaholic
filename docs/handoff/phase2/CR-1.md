# CR-1 handoff — DSL upgrades + the Grand Casino

Branch `p2/cr-1` (from `p2/cb-c` @65aae7c). Worktree `E:/projects/mods/minecraft/bw-p2-cr-1`.
Design source: `findings/CASINO-REDESIGN.md` §2–§5, MT-DESIGN §1/§6, CB-C handoff.

**Do not ship before MT-1 is merged.** Tables are stamped as real 3×2 multi-block tables (`part=…`). Until the
table blocks have the `part` property, every table loads in the world as six compact SINGLE tables (expected).

## Done

### 1. DSL (`worldgen/logic/blueprint/**`)
- `Ornament` (new): every verb draws through public `Blueprint` verbs, so it resolves roles through the palette and
  works inside `mirrorX`.
  - `column` (base, `axis=y` shaft, capital; optional stair flares only into free cells), `pilaster`.
  - `arch` (elliptical intrados in half blocks: upside-down stairs toward the jamb where the curve steps, top slabs
    where it is half a block, an archivolt ring, a keystone, spandrel fill), `archProfile`.
  - `cornice` (ring, outer corners via the finishing pass), `corniceRun`, `entablature` (architrave / dentil frieze
    / cornice), `stringCourse`.
  - `balustrade` (wall posts + slab rail, piers every n).
  - `flight` (straight stair: stringer, head room, returns the landing), `twinFlight` (mirrored pair).
  - `discCells` / `ringCells` / `disc` / `ring`; `drum` (glass between compass piers + band course);
    `dome` (half-ellipsoid shell, stairs on terrace edges, slabs on half rows, ribs on the 8 compass lines, hollow,
    registered as interior); `lantern` (crown lantern + lightning-rod finial).
  - `gradient` (role A→B probability fade along an axis), `blend` (speckle), `hash` (deterministic).
  - `checker`, `diamond` (♦, mirror-symmetric), `sunburst`, `coffers` (beam grid, alternate coffers lit).
  - `neon` (letters of the light-emitting NEON_A/NEON_B roles on a SIGN_BACK board, a BULB chaser with dark
    sockets; optionally raised).
- `Blueprint`:
  - `mirrorX(axis2, draw)` symmetry helper: draws twice, the second pass mirrored (cells and states via the new
    `Mirror.x`, games + player sides, NPC/chest markers, keep-clear/interior/aisle boxes, entries, jigsaws).
  - `slice(id, z0, z1)`: design one building, cut it into jigsaw pieces (finishing pass on the whole first, so cuts
    are seamless; refuses a cut through a table footprint). `finish()`.
  - `uplightGames()` + `uplightCells(spot)`: auto table lighting — UPLIGHT floor tiles under the players' corner
    cells (r ±1, f +1), carpet removed; path tile → up → over the walkway → over the main block = 11, never inside
    the board/headroom zone.
  - Finishing pass now writes wall connections (`Shapes.isWall`; low/tall/none + post like vanilla).
- Roles: COLUMN, CAPITAL, PLINTH, CORNICE, ARCH, BAND, BALUSTER, RAIL, DOME, RIB, GILT, UPLIGHT, WATER_BASIN,
  BASIN_RIM, WALL_ALT, COFFER — optional, each falls back to a base role, so old palettes are unchanged.
  `Palette.derive(id)` for per-biome swaps; `Material` full blocks may carry fixed state (`[lit=true,…]`).
- `SignFont`: I is one stroke (SMALL and LARGE); SMALL N is 5 wide with a true stem-to-stem diagonal (LARGE N was
  already diagonal). "CASINO" in SMALL is 23 wide (odd → centres on an axis). Golden render test.

### 2. Grand Casino (`worldgen/logic/pieces/GrandCasino.java`, `GrandPalettes.java`)
- 41 × 37 × 41, axis x = 20, front +z. Designed whole, sliced into `casino/<slot>/entrance` (z22..40, start, anchor),
  `casino/<slot>/floor` (z7..21), `casino/<slot>/bar` (z0..6) — exactly the required pools. Jigsaws
  `burmaldaholic:grand/{entrance_back,floor_front,floor_back,bar_front}` in the floor-block row at x = 20.
- Plan (floor block y2, row 3; gallery floor y8, row 9; ceiling y14):
  - Hall: two felt pits ("Salle Europe": 2 roulette, craps, baccarat + Baccarat Dealer; "Salle Amérique": 2 blackjack,
    UTH, poker), rows facing each other over a 3-row aisle, a red runner with gilt edges, a ♦ medallion with a glowing
    core under the dome oculus, colonnades (x7/x33) carrying U-shaped galleries, twin stairs up the side aisles,
    3+3 copper slot banks under the galleries, coffered ceiling, grand chandelier on a chain from the dome.
  - Rear: bar (15 long) under the VIP gallery, Loan Shark booth, loot chest, lounge sofas; rear arcade of 5 arches.
  - VIP gallery (row 9): poker #2, player-banked UTH (centre) and baccarat, 2 netherite slots; balustrades.
  - Lobby: checker marble, coffers, cashier cage + Croupier, wheel, plinko; inner arcade of 3 arches.
  - Loggia arcade (single + paired quartz columns, 5 arches) carrying the marquee façade: CASINO in froglight neon
    with a lit-copper-bulb chaser and a gilt frame, a stepped art-deco crown above (black, neon flutes, bulbs on the
    steps, keystone + end-rod rays; double-faced so it also glows to the back).
  - Pavilion towers (x1..7 / x33..39): two storeys of arched windows, a ♦ neon medallion, cornice with corner
    lanterns, steep copper roof with quartz hips, glazed belvedere with a light, finial. West: duel pit; east: 4 gold
    slots. Doors from the lobby.
  - Roofs: entablature on all walls, copper mansard strip with œil-de-bœuf dormers, a light well, an attic cylinder,
    a glazed lit drum, a ribbed copper dome (r 6) with lantern and finial.
  - Forecourt: sunburst paving, red runner, two lit fountains (sea lanterns under still water), lamp posts,
    bollards, hedges, topiary, a stepped terrace edge.
  - Night: neon sign + crown, end-rod "neon tubes" on both edges of every pilaster on all four sides, sconces in every
    bay, tower lanterns, belvedere lights, lit drum, fountain lights, lamp posts.
- 26 games (8 + 3 tables, 15 machines), 3 NPCs (Croupier, Baccarat Dealer, Loan Shark), 1 chest. Every table is
  a full 3×2 table obeying R1–R6; every game is lit ≥ 10 by uplight tiles; no forbidden blocks.
- Variants (palette swap only): Riviera (slot `tudor`), Oasis, Safari, Alpine, Ice Palace (slots = their names),
  Cherry (palette + pieces `casino/grand_cherry/*` buildable, no slot → not in the pack).
- Registration: `OverworldPieces.all()` now returns the Grand Casino (15 pieces); the five old styles are
  `OverworldPieces.legacy()` (code kept until Q cleanup, built compact, not in the pack). `Checks.PENDING_CLEARANCE`:
  deleted the `entrance` and `bar` ids of the five slots (reused by the Grand Casino, which passes R1–R6).

### 3. Small cross-package edits (please review, owners)
- `Assembly.budget` (CB-P): counts one game per block entity — a multi-block table counts once (its `main` cell).
  Without this the 11 tables count 66 and the budget (40) fails.
- `PlaceholderPieces` (CB-P): `.compactTables(true)` (their ids include `entrance`/`bar`, no longer PENDING).
- `CasinoBuildingGameTests.casinoRegistryLoads`: checks `PackFiles.REQUIRED_POOLS` instead of a hard-coded `rear`.
- `CasinoPhotoClientGameTests`: `-Dburmaldaholic.casinoPhotos=standalone` (every standalone structure);
  `CasinoClientQa.vantagePoints`: extra `hall_high` shot for big casinos.
- `docs/design/visual/render_structure.py`: `night_sw/night_nw/night_ne` views (night on every side), colours for
  oxidized cut copper and chiseled tuff.

## Verified
(see below — filled in at the end)

## Requests
### CB-P (PackFiles / PackGen / structure)
1. Rename style slot `tudor` → `riviera` before v0.2.0 ships (STYLE_BIOMES key, `WorldgenModule` biome→style map,
   `CasinoQa.STYLES`, client test defaults). Then change one line: `GrandCasino.Variant.RIVIERA` slot.
2. Optional sixth slot `cherry` (biome `minecraft:cherry_grove`, taken out of `alpine`): add it to STYLE_BIOMES and
   set `GrandCasino.Variant.CHERRY` slot to `cherry` and add it to `Variant.active()`.
3. Fit: the footprint is 41×41 (was 44×47 for Tudor). Re-run the fit survey; if rejection > 40 %, tell CR and the
   forecourt (z35..40) can be cut off into an optional jigsaw piece or dropped (building core is z0..34).
4. The structure is 37 rows tall (foundation 2 below ground): nothing in PackFiles depends on it, noted for the
   terrain probe's headroom.

### MT
- After MT-1 merges into the CB branch, re-run the server GameTests below without the single-table trick; they
  should pass as they did in single-table mode.
