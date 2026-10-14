# CB-C handoff (PAUSED, WIP)

Branch `p2/cb-c` (base `feature/v020-casino-buildings` @fb57e0f). CB-C1 is NOT signalled ready yet.

## Done (uncommitted-quality WIP, committed as "WIP: CB-C paused")
- `blueprint/TableClearance.java` (new, pure): BOARD/MACHINE/NONE classes. Felt dims (CRESCENT 2.25x1.25, RECT 2.0x1.4,
  OVAL 2.0x2.0), `inside`/`covers` 5x5 sampling copied from UI-branch BoardGeometry/BoardRenderer (pointer comment),
  `blocksOverhang(top) = top > FELT_Y - 0.05` with FELT_Y = 1 + 1/64 (mirrors W2's planned predicate).
  BOARD: every covered neighbour cell at table y must have collision top <= felt; no other game at local r=+-1/+-2, f=0
  (pitch >= 3). MACHINE: side cells r+-1 walkable for y..y+1 (SLOTS) / y..y+2 (WHEEL, PLINKO: renderer disc/board reach
  0.3 into side cells up to y+2.78); column above air (slots y+1, plinko y+1..2, wheel y+1..3 for its name plate);
  SLOTS next to SLOTS exempt.
- `Shapes.collisionTop(BlockSpec)`: vanilla collision tops (carpet 1/16, bottom slab 0.5, lectern 15/16, fences 1.5...).
- `Checks`: `PENDING_CLEARANCE` (5 annex ids, casino/parlor, casino/lounge) and `PENDING_VALUABLES` (parlor, lounge);
  `run()` calls `TableClearance.check` unless pending.
- `Kits.tablePod`: removed top-slab stools at r+-1. `Kits.machineAlcove`: jambs moved to r+-2 (3 rows), BULBs y+3,
  TRIM lintel + NEON_A keystone at y+4, BULB crown y+5 (alcove now 5 wide, 6 tall).
- `OverworldCasino`: floor_a dropped pot at (27,1) next to the gold slot bank; floor_b north rope barrier moved to x 14..16.
- `PieceChecksTest`: design checks + valuables scan now over `CasinoPack.pieces()` (pending lists respected).

## Verified
- `./gradlew test --tests '*PieceChecksTest*'` BEFORE the floor_a/floor_b fixes: 10 failures, all expected
  (slot (26,1) side cell / pot; craps (11,5) felt cell blocked by rope end_rod). Annex/Parlor/Lounge passed (pending).
- After the fixes: NOT verified. The rerun crashed (gradle daemon OOM, machine memory exhausted).

## Next steps (in order)
1. `./gradlew compileJava test --tests 'dev.nezo.burmaldaholic.worldgen.logic*'` and fix whatever fails (StylesTest/goldens
   may change because of the alcove/pod edits).
2. Add the ChecksTest cases: stool, jamb, pot fixtures fail; slot bank passes; blackjacks 2 apart fail, 3 apart pass.
3. Commit CB-C1 and SendMessage main "CB-C1 ready @<sha>".
4. CB-C2: Tudor lobby medallion is `medallion(b, Suit.CLUB, 15, 5)` (OverworldCasino ~500), which is the "hooked cross".
   Switch it to Suit.DIAMOND and check plan renders from all 4 rotations (docs/design/visual/render_structure.py).
   SignFont `N` 5 wide with a diagonal. Ice Palace sign navy backing, felt zone, side lanterns, Oasis C/S.

## Requests to other packages
- CB-O: PiglinParlor machineAlcove (line ~353) has jambs at r+-1. Under this rule, WHEEL/PLINKO need r+-1 free for y..y+2
  and the column above free (wheel to y+3). Delete your PENDING entries once you pass. With the 5-wide N, "CASINO" gets
  wider, so re-check the Parlor marquee against the east gate tower.
- CB-A: annex stools/stairs at r+-1 and the slot/wheel adjacency fail the rule. Delete your annex PENDING entries.
- X (phase 3): BoardClearanceContractTest should compare TableClearance.Felt/FELT_Y/covers to BoardGeometry.

## Risks
- Lectern at the dealer cell passes only because its collision top (15/16) is below the threshold of 0.9656.
- The machine rule is stricter than the spec text ("y and y+1"). It was derived from the renderer extents.
