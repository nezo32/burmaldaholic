# CB-C handoff — CB-C1 (clearance rule) READY; CB-C2 ON HOLD (replaced by CR)

Branch `p2/cb-c`. It merges `p2b-base-cb` (feature/v020-casino-buildings @8844eb9) and `p2/mt` (MTa @0271be8).

## Done (CB-C1)
- `worldgen/logic/blueprint/TableClearance.java` (pure) is the multi-block clearance rule from MT-DESIGN §6. All
  footprint, walkway and dealer offsets come from `core/table/parts/TableFootprint`. The old overhang sampling,
  blocksOverhang, the ±2 rule and MIN_PITCH are deleted.
  - R1 integrity: each footprint cell holds the table block with the right `part` + `facing`.
  - R2 support: the block below each cell is a full cube, a top or double slab, or top stairs.
  - R3 walkway: r−1..1 at f+1, rows y and y+1, are walkable (air, carpet, pressure plate) and reachable from the entries.
  - R4 dealer: `TableFootprint.dealerCell` is air or a lectern at y, and air at y+1.
  - R5 spacing: the Chebyshev gap between any two game footprints on one row is ≥ 2 (slot next to slot exempt).
    The zones (footprint ∪ walkway) of two tables are disjoint. No dealer cell lies in another table's zone.
  - R6 headroom: zone × y+1..y+3 is air.
  - Machine rule unchanged from the first CB-C1 draft: side cells r±1 walkable for y..y+1 (slots) or y..y+2
    (wheel, plinko), and the column above air (slot y+1, plinko y+1..2, wheel y+1..3).
- `Checks.run` now runs the TableClearance rule and the new FORBIDDEN check, and `isGame` knows footprint cells.
  - FORBIDDEN covers gold/raw_gold/iron/raw_iron/diamond/emerald/netherite/lapis/redstone blocks, beacon,
    ancient_debris, dragon head/wall head/egg, the gold/iron weighted pressure plates, and every `*shulker_box`.
    Waxed copper is allowed.
  - Allowlists: `PENDING_CLEARANCE` (all 30 Overworld style pieces, 5 annexes, parlor, lounge) and
    `PENDING_VALUABLES` (parlor, lounge). Owners and CR only DELETE entries.
- DSL: `Blueprint.game(g,…)` stamps `TableFootprint.cells` with `part` + `facing`, and the GameSpot keeps the main pos.
  `Game.tableGame()` maps games to TableGame.
  - Legacy pieces (`Blueprint.compactTables()`, defaulting to true for ids in PENDING_CLEARANCE) still place a
    compact 1×1 table with no `part` property, so the default state is SINGLE. They also keep the legacy pod, so
    their layouts and goldens are unchanged.
- `Kits.tablePod` (§6): full table, carpet walkway r−1..1 at f+1 (with air at y+1), stools at r±2 f+1, dealer NPC
  or lectern at the dealer cell (f−2; roulette r−1), keep-clear over footprint ∪ walkway for y+1..y+3. It is 5 wide
  and 4 deep.
- Earlier WIP still applies:
  - `Kits.machineAlcove`: jambs at r±2, lintel at y+4; the alcove is 5 wide and 6 tall.
  - `Shapes.collisionTop` added. The new rule no longer uses it.
  - Tudor floor_a pot removed and floor_b rope barrier moved. These are building edits; CR replaces those pieces.
- Tests:
  - `ChecksTest` has one test per rule: R1, R2, R3 (stool fixture, y+1 block, unreachable, pressure plate OK),
    R4, R5 (pitch 3 fails / 4 passes, facing tables 1 free row fails / 2 pass, machine gap, dealer in zone), R6,
    machine side cells (bank passes, pot next to slot fails, jamb fails, alcove passes), forbidden blocks (waxed
    copper OK), and pending pieces staying compact.
  - `PieceChecksTest` runs `Checks.run` and the forbidden scan over `CasinoPack.pieces()`.
  - KitsTest and BlueprintSkeletonTest are updated for the 3×2 pod.

## Verified (2026-10-14, JAVA_HOME jdk-25.0.4.1+1, `--max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g`)
- `./gradlew compileJava compileClientJava compileGametestJava test`: BUILD SUCCESSFUL, 0 failures.
  This includes RawInputGuardTest (1/1) and all worldgen.logic tests.
- `./gradlew compileJava compileClientJava compileGametestJava -Pmc=26.3`: BUILD SUCCESSFUL.
- Not run: server GameTests, client GameTests, and the full `build --no-daemon`.

## Not done / on hold
CB-C2 is ON HOLD: Tudor rug, SignFont N, Ice Palace sign, felt zone, lanterns, Oasis. For notes, the rug is
`medallion(b, Suit.CLUB, 15, 5)` in OverworldCasino, and Suit.DIAMOND is the 4-fold symmetric choice.

## For CR / MT
- New pieces must not use a PENDING id. They get full tables and must pass R1–R6 and FORBIDDEN.
- Stamped templates carry `part=…`. Until MT-1 adds the `part` property to the table blocks, such a template would
  load as 6 compact SINGLE tables. Do not ship a non-legacy piece before MT-1 is merged into the CB branch.
- Phase 3 FootprintContractTest: TableClearance uses TableFootprint directly, so there are no copied constants
  left to compare.
