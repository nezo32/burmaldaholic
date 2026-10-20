# CR-1 handoff: DSL upgrades + the Grand Casino

Branch `p2/cr-1`, worktree `E:/projects/mods/minecraft/bw-p2-cr-1`. It is based on `p2/cb-c` and has merged `p2/mt` (MT-1, MT-2, MT-3a, MT-3b and the MT visual passes) and `p2/cb-p` (fit, carve/skirt platform).
Design source: `findings/CASINO-REDESIGN.md` §2–§5, MT-DESIGN §1/§6, the CB-C and CB-P handoffs, and coordinator/VQA feedback.

- Renders: `docs/design/visual/mockups/casinos/grand/<variant>/` (iso from 4 sides, night from 4 sides, plan, cutaway) and `_contact_sheet.png`. These are from round 1 and predate the round-2 façade and sign. They were not regenerated (R3: no screenshot review).
- In-game shots (26.3, real client): `E:/projects/mods/minecraft/.burmaldaholic-tools/shots/CR-1/` (`jcasino_casino_<slot>_<view>.png`).

## Done

### 1. DSL (`worldgen/logic/blueprint/**`)

**`Ornament` (new).** Every verb draws through public `Blueprint` verbs. It resolves roles through the palette, so a biome swap is free, and it works inside `mirrorX`.

| Group | Verbs |
|---|---|
| Columns | `column`: a base, a shaft with `axis=y` and a capital. Optional stair flares go only into free cells. `pilaster`. |
| Arches | `arch`: an elliptical intrados in half-block steps, with upside-down stairs toward the jamb where the curve steps, top slabs where it is half a block, an archivolt ring, a keystone and spandrel fill. `archProfile`. |
| Cornices | `cornice` (a ring; the finishing pass makes outer corners), `corniceRun`, `entablature` (architrave, dentil frieze, cornice), `stringCourse`. |
| Rails and stairs | `balustrade` (wall posts, a slab rail, a pier every n). `flight`: a straight stair with stringer and head room that returns its landing. `twinFlight`. |
| Circles and domes | `discCells`, `ringCells`, `disc`, `ring`. `drum`: glass between compass piers, with a band course. `dome`: a half-ellipsoid shell with stair terrace edges and slab half-rows, ribs on the 8 compass lines, hollow and registered as interior. `lantern`: lantern plus finial. |
| Texture | `gradient` (role A→B fade along an axis), `blend` (speckle), `hash` (deterministic). |
| Patterns | `checker`, `diamond` (mirror-symmetric ♦), `sunburst`, `coffers`. |
| Signage | `neon`: letters of the light-emitting NEON roles on a SIGN_BACK board, with a BULB chaser. |

**`Blueprint`.**
- `mirrorX(axis2, draw)` is the symmetry helper. It mirrors cells and states (through `Mirror.x`), games and their player sides, markers, boxes, entries and jigsaws.
- `slice(id, z0, z1)` cuts one building into jigsaw pieces. The finishing pass runs on the whole building first, so the cuts are seamless. `finish()`.
- `uplightGames()` / `uplightCells()` is the auto table lighting: UPLIGHT tiles go under the players' corner cells (r ±1, f +1) and never enter the board or headroom volume.
- `game()` stamps full multi-block tables (3×2) and, since MT-3, upright machines: slot 1×2, wheel and Plinko 3×3, cashier 3×2. Each cell gets its `part` and `facing`.
- The finishing pass now writes wall connections.

**Rules.**
- `TableClearance` adds an upright-machine rule: every cell has its part and facing, the bottom row is supported, and the front walkway across the machine's width (rows y and y + 1) is walkable and reachable. `footprint` and `cells3d` are machine-aware.
- `Checks.lightProbe` measures light above a table's main block, and at the player's head for an upright machine.

**Roles.** New: COLUMN, CAPITAL, PLINTH, CORNICE, ARCH, BAND, BALUSTER, RAIL, DOME, RIB, GILT, UPLIGHT, WATER_BASIN, BASIN_RIM, WALL_ALT, COFFER and CARPET_PATTERN. They are optional and fall back to a base role, so old palettes still build.
- `Palette.derive` makes per-biome swaps.
- A `Material` full block can carry fixed state, e.g. `[lit=true]`.

**Kits.**
- `tablePod` leaves the dealer cell as two blocks of air, as the MT contract test requires.
- `slotBank`, `machineAlcove` and `cashierCage` support full upright machines.

**`SignFont`.** The I is a single stroke, and SMALL N is 5 wide with a true diagonal. "CASINO" is 23 columns wide (odd), so it centres on the axis. There is a golden render test.

### 2. Grand Casino (`worldgen/logic/pieces/GrandCasino.java`, `GrandPalettes.java`)

**Size and pieces.** 41 × 37 × 41, axis x = 20, front +z. It is designed as one building and sliced into three pieces, which are exactly the required pools:
- `casino/<slot>/entrance` (z22..40): start piece, anchor, the door on the +z face.
- `casino/<slot>/floor` (z7..21).
- `casino/<slot>/bar` (z0..6).

**Exterior.**
- Front: two pavilion towers with arched windows, a ♦ neon medallion, corner lanterns, neon pilaster lines and floodlights. Each has a steep copper roof with quartz hips, a glazed lit belvedere and a finial.
- Between the towers is the loggia arcade (single and paired quartz columns, 5 arches) carrying the marquee façade. It is the CASINO board in froglight neon: letters 5 rows high, a fully lit chase frame of lit copper bulbs alternating with froglights, and a gilt hood and jambs. It sits one block behind the towers, so it reads from the diagonals.
- Above the board is the art-deco crown: black tiers with neon flutes and lit step edges, a keystone and end-rod rays. It is double-faced, so it also glows to the back.
- Sides and back: every bay has a pilaster with end-rod neon, ground windows under a hood moulding, an arched upper window with a balconette, a lantern under the balconette and a floor uplight in the apron.
- Roof: an entablature on all walls, a copper mansard with œil-de-bœuf dormers, a light well glazed as a skylight grid, an attic cylinder, a lit glass drum, and a ribbed copper dome with lantern and finial.
- Forecourt: sunburst paving, red runner, lit fountains, lamp posts, bollards, hedges and topiary.

**Interior.**
- Hall pits: a calm casino carpet (the deep CARPET colour with a subtle CARPET_PATTERN diamond lattice) and dark walkways framing every table. A shaded pendant lamp hangs over every table, one row above its board volume, so every felt is lit to at least 12 (tested). Each pit has a chandelier and planters.
- Centre: a red/gilt runner and a ♦ medallion with a glowing core under the oculus.
- Colonnades carry U-shaped galleries, reached by twin stairs.
- Bar under the VIP gallery, behind a rear arcade.
- VIP gallery: poker, player-banked UTH and baccarat, 2 netherite slots.
- Lobby: the cashier booth (with the Croupier) faces the Wheel of Fortune in its lit alcove.
- West pavilion: Plinko and the duel pit. East pavilion: gold slots.

**Contents.** 32 games:
- 11 tables: 8 in the hall, 3 VIP.
- 21 machines: 6 copper slots, 10 gold, 2 netherite, plus cashier, wheel and Plinko.

3 NPCs (Croupier, Baccarat Dealer, Loan Shark) and 1 chest. All tables are full 3×2 multi-block tables and all machines are full upright machines. The building passes R1–R6, the machine rule and FORBIDDEN (no valuable blocks).

**Variants (palette swap only).** Each variant is its own structure slot `casino_<slot>`:

| Slot | Biomes |
|---|---|
| `riviera` | plains, meadow, forests |
| `oasis` | desert, badlands |
| `safari` | savanna |
| `alpine` | taiga |
| `ice_palace` | snowy |
| `cherry` | cherry grove (new) |

**Registration.**
- `OverworldPieces.all()` returns the 18 Grand Casino pieces.
- The old five styles remain as `OverworldPieces.legacy()`: built compact, not in the pack, code kept until Q cleanup.
- `Checks.PENDING_CLEARANCE` no longer lists the `entrance` and `bar` ids, which the Grand Casino reuses.

### 3. Cross-package edits (owners please review)

| Owner | File | Change |
|---|---|---|
| CB-P | `PackFiles` | Slot `tudor` renamed to `riviera`. New `cherry` slot (cherry_grove moved out of `alpine`). `REQUIRED_POOLS` = entrance, floor, bar. |
| CB-P | `WorldgenModule` | Plains maps to `riviera`. |
| CB-P | `Assembly` | Counts one game per block entity, i.e. a table's `main` cell. |
| CB-P | `PlaceholderPieces` | Compact. |
| CB-P | `PackFilesTest` | Golden updated. |
| CB-5 / QA | `CasinoQa` | Counts multi-block games once (by the `main` cell). Reads machine light at the player's head. `STYLES` updated. `-Dburmaldaholic.casinoQaOnly=standalone`. |
| CB-5 / QA | `CasinoBuildingGameTests` | The registry test uses `REQUIRED_POOLS`. |
| CB-5 / QA | `CasinoClientQa` | Front of a standalone structure = its lit marquee side. Extra `hall_high` shot. |
| CB-5 / QA | `CasinoPhotoClientGameTests` | `=standalone` mode. Default target `riviera`. |
| CB-5 / QA | `CasinoPlayClientGameTests` | Default target `riviera`. |
| MT | `MultiBlockLayoutsTest` | Counts only v0.1 tables. Skips `PENDING_CLEARANCE` (legacy, compact) pieces. |
| — | `render_structure.py` | `night_sw/nw/ne` views, plus copper and tuff colours. |

## Round 2 (VQA specs) and R3 follow-up

Round 2 (commit 26d615fe) covers all 7 VQA r2 specs:
1. A one-line LARGE CASINO sign with solid 1-block NEON_A strokes on a black board. Bulbs are only on the top and bottom rows.
2. Façade depth: the towers stand 2 blocks forward, with a round-arch arcade on columns with bases and capitals. The sign is recessed under a projecting stair cornice, with a bulb canopy over the door, a parapet of at most 4 rows and 2 flag masts.
3. A chandelier or shaded pendant over every table.
4. A calm carpet: a deep-red field with gold borders per zone; the medallion is only in the lobby.
5. Colonnades at pitch 8.
6. No stools; the player row is clear.
7. Lobby: the cashier booth and the wheel face the door, with pilasters and sconces.

R3 (commit 658e5cd0):
- **Bug found by the new test.** The 27-wide LARGE "CASINO" ran under the tower walls at x7 and x33. That hid the C's left stem and the O's right stem.
- **Fix.** LARGE A and C are now 3 wide, so the word is 25 columns and fills exactly the visible x8..32. LARGE D has cut corners, so it no longer reads as O (O and D differed by only 2 cells).
- **New legibility tests (instead of screenshots).** In `SignFontTest`:
  - each LARGE glyph matches the expected letter mask;
  - all letter glyphs are distinct;
  - each CASINO letter is at least 3 cells away from every other glyph;
  - N is at least 4 cells away from R, D, H, M, W, U, O and K.

  In `GrandCasinoTest.theMarqueeSpellsCasinoInSolidStrokes`, the board as built in all 6 variants matches the mask cell by cell: NEON_A for strokes and SIGN_BACK elsewhere.
- **For package DL.** `GrandCasinoTest.everyTableDealerCellIsTwoBlocksOfAir` checks that every table's `TableFootprint.dealerCell` (via `TableClearance.dealer`) is air at y and y+1, with no lectern. It covers all 6 variants, both the whole building and the slices (22 table spots per variant). The baccarat table still carries the `BACCARAT_DEALER` NPC marker on its cell; DL may replace it.
- **Note for CR-2.** The narrower LARGE A, C and D change any CR-2 sign drawn in LARGE. Re-run CR-2's sign tests after merging p2/cr-1.

## Verified

Environment: JDK 25.0.4.1+1. Heavy runs used `with-heavy-lock.sh ./gradlew --no-daemon --max-workers=6 -Dorg.gradle.jvmargs=-Xmx4g`, following the "test only what changed" policy.

- **Compile:** `compileJava compileClientJava compileGametestJava`, and the same with `-Pmc=26.3`. BUILD SUCCESSFUL, after merging MT, which carries the 26.3 `PushReaction` fix.
- **Unit tests:** `test --tests 'dev.nezo.burmaldaholic.worldgen.*' --tests 'dev.nezo.burmaldaholic.core.table.*' --tests '*RawInputGuardTest'`. BUILD SUCCESSFUL on the head after the CB-P merge. Coverage includes:
  - OrnamentTest (every verb, the mirror, slice, uplights, wall connections);
  - GrandCasinoTest (all 6 variants pass `Checks`, both whole and sliced; 32 games, all full multi-block; budget; slices tile the building; symmetric skyline; dome and towers crown the skyline; every felt lit ≥ 12; deterministic export);
  - PieceChecksTest, with the Grand Casino in no PENDING list;
  - the SignFont golden render;
  - ChecksTest `uprightMachines`;
  - MT's MultiBlockLayoutsTest, whose R1–R6 also run over the Grand Casino;
  - RawInputGuardTest.
- **Server GameTests (26.2):**
  - Command: `-Dburmaldaholic.casinoQaOnly=standalone` (JAVA_TOOL_OPTIONS), `runGameTest -Dfabric-api.gametest.filter=burmaldaholic-gametest:casino_building_game_tests*`, with real MT multi-block tables and machines and CB-P's fit, carve and skirt.
  - Result: **11/11 passed**.
  - Placement of all 6 variants in every rotation passed, including the in-world light poll.
  - Every game opens and takes a bet through the real packet path; the Croupier sells a scratch card.
  - One record per start, and an incomplete assembly is rejected.
  - Registry: 6 of 6 styles. Template palettes: 32 templates ok.
  - Idle tick 0.05–0.40 ms.
  - Fit: 37 % rejected; start gen median 16.9 ms, p99 39.7 ms; 8-seed median nearest 461.
- **Client, one locked photo run on 26.3:**
  - Command: `with-client-lock.sh with-heavy-lock.sh ./gradlew … runClientGameTest -Pmc=26.3`, `-Dburmaldaholic.casinoPhotos=standalone`, with a temporary entrypoint swap that was restored afterwards.
  - Output: 48 shots (6 variants × front, aerial, door_in, floor_to_door, bar_side, hall_high, front_night, aerial_night) in `.burmaldaholic-tools/shots/CR-1/`, plus `_sheet_<view>.png` contact sheets.
  - Earlier runs (3) went to diagnosing the 26.3 compile and the photo harness's entrance detection.
- **Renders:** `render_structure.py --manifest … --views iso_se,iso_sw,iso_nw,iso_ne,night,night_sw,night_nw,night_ne,plan,cutaway` for all 6 variants, in `docs/design/visual/mockups/casinos/grand/`.
- **Not run:** a full `build` (per policy); the client terrain test; GameTests on 26.3.

- **R3 verification (2026-10-20, head after 658e5cd0):**
  - `test --tests 'dev.nezo.burmaldaholic.worldgen.*' --tests 'dev.nezo.burmaldaholic.core.table.*' --tests '*RawInputGuardTest'`: 324 tests, 0 failures. This includes R1–R6 and FORBIDDEN for all 6 variants (whole and sliced), the new sign tests and the dealer-cell test.
  - `runGameTest -Dfabric-api.gametest.filter='burmaldaholic-gametest:casino_building_game_tests*'` with `casinoQaOnly=standalone`, on 26.2 under the heavy lock: **11/11 passed**. Templates are regenerated by PackGen (25 pieces), and 32 templates resolve their palettes. Every game in all 6 casinos opened and took a bet. Fit: 37 % rejected, 8-seed median nearest 461.
  - `compileJava compileClientJava compileGametestJava -Pmc=26.3`: OK. A first attempt failed with missing MC client symbols, because I had run it at the same time as another Gradle run in the same build directory. It passed when run alone.
  - Not run: client GameTests (no visual-only change needs one under R3), full `build`, GameTests on 26.3.

## Open items / requests

- **CB-P:** the fit test (`casinoFitRateAndGenerationTime`) should be re-run on this merged branch. On the pre-CB-P base it failed with the same numbers as CB-P's pre-fix baseline (42 % rejected, median nearest 890), so it was not caused by the building.
- **CB-A:** the annexes, Parlor and Lounge are still legacy (compact, PENDING). The snowy annex reachability failure is pre-existing (CB-P open item 2).
- **Integration:** the in-game photos and server GameTests were run on this branch. Re-run them after the final CB/MT merges.
