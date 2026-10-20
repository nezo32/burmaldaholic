# MT — multi-block casino tables and machines (Phase 2b)

Branch `p2/mt` (from main v0.1.3 `d71d905`). Spec: `.burmaldaholic-tools/MT-DESIGN.md` (★1 carpet pops ON, ★2 3×2 for
craps/roulette, ★3 felt 14/16) + coordinator scope addition MT-3 (upright machines).

## Done

### MTa — pure API (`core/table/parts/`, announced @0271be8)
- `TablePart` (SINGLE default, MAIN, LEFT, RIGHT, BACK_LEFT, BACK, BACK_RIGHT; MT-3 adds UPPER_LEFT/UPPER/UPPER_RIGHT,
  TOP_LEFT/TOP/TOP_RIGHT with `u()`), `TableGame` (BLACKJACK/BACCARAT/UTH crescent, POKER oval, ROULETTE, CRAPS rect;
  `ofBlockName`), `LocalCell`, `TableFootprint` (cells, cellOffset, mainOffset/mainPos inverse, dealer cell/offset/pos,
  bounds, footprint-local ↔ world `toWorld/toLocal`, `seatPoint/seatOffset`, SEAT_HEIGHT 1.6), `FeltFrame.of(game, part)`
  (FELT_Y 14/16, RAIL_TOP 15/16, CONTENT_LIFT 1/64, RAIL 1/8, `onFelt`, `partOrigin`), `MigrationDecision`.
- Frame: footprint-local x = players' right (`FACING.getCounterClockWise()`), z toward the players, main block
  x,z ∈ [0,1]; a 3×2 table spans x∈[-1,2], z∈[-1,1], felt centre (0.5, 0).
- Seat points: compact keeps the v0.1 half circle (r 1.1, seat 0 at the dealer's right — the old SeatPoints ordering);
  full tables 0.35 outside the outer rail (rounded where the felt is), oval 360° minus the dealer slot.
- **Deviation:** roulette bowl Ø1.0 (fills column r-1); Ø1.5 would overhang the footprint.

### MT-1 — block, world interactions, routing, migration
- `MultiBlockTableBlock` (extends `CasinoTableBlock`, so `instanceof CasinoTableBlock` holds) for the 12 table blocks
  (`TableRegistrar` routes by registry name; game modules untouched). `part` default SINGLE; player placement → MAIN +
  parts (BFS); refusal with `msg.burmaldaholic.table.no_room` (plural, blocked-cell count; border, height, loaded,
  replaceable or carpet, no entity, floor) and the item is kept; carpets pop (★1).
- Shapes per facing × part at felt height 14/16 with stepped arcs on rounded corners; noOcclusion; `forceSolidOn` +
  `isValidSpawn(false)`. **Not set:** `pushReaction(BLOCK)` — the constant is `PushReaction.IMMOVEABLE` in 26.3, so
  naming it breaks one version; parts are block-entity-block states, which vanilla pistons never move (GameTest proves
  it). `isSuffocating/isViewBlocking` left default (not a full cube → false; `isViewBlocking` changed its lambda type in
  26.3).
- Integrity: `updateShape` checks the neighbour in the update direction against the footprint → AIR; guarded for
  WorldGenRegion (`!(level instanceof Level)`), unloaded neighbours, and our own placement (`TableParts.placing()`).
  First tick of a MAIN: missing parts regrow when free, else fold to SINGLE (BE kept).
- Breaking any part → `TableParts.breakFromPart`: main destroyed with drops (one item, none in creative / house
  table), other parts cleared without drops + particles, the hit part left for the vanilla break so the break events
  fire for it. Explosions on a part route to the main's `onExplosionHit` (idempotent, ≤1 item). Loot: `any_of
  block_state_property part ∈ {main, single}` + `survives_explosion`, AND in code: `MultiBlockTableBlock.getDrops`
  returns nothing for non-controller parts. **Finding (affects the whole mod on 26.3):** 26.3 changed the loot predicate
  format (`"conditions": [{"condition": "minecraft:block_state_property", …}]` → `"condition": {"type":
  "minecraft:match_block", "blocks", "state"}`) and silently ignores the 26.2 form — so on 26.3 every part dropped an
  item, and every v0.1 loot table's `survives_explosion` is ignored (tables/machines always drop in explosions). The
  part rule is now enforced in Java (same signature both versions); the explosion-survival roll on 26.3 is an open item
  for whoever owns data packs (needs a per-version data overlay or code). Mirror swaps parts; rotate FACING only.
- Use any part → main (`openAt`). `CasinoTableMenu.stillValid`, the BE's seat-leave check and bot claimants use
  `TableParts.inRange` (centre distance for compact, nearest point of the footprint box for full).
- Routing (§4): `Ownership.beforeBreak/afterBreak/onUseBlock/isExplosionProof`, `ServerExplosionMixin`,
  `BotInteractions`; bot plates use `TableFootprint.seatOffset` (plate allowed over a table part).
- **CB-G hook (coordinator request):** `TableProtection` SPI (`core/table/parts`), no-op default; CB-G installs it.
  Called with the MAIN pos only: `dropsAllowed` (part/main break, explosions), `explosionProof` (explosion filter, whole
  table), `breakAllowed` (break event, any part), `playerPlaced` (`CasinoTableBlock.setPlacedBy`). `HouseGames.mainOf`
  should become `TableParts.mainOf`.
- Migration (§5): BE saves `mt=1`; loaded without it → legacy; first tick a legacy SINGLE runs
  `TableParts.tryUnfold` (GROW = same block SINGLE→MAIN, BE + stakes kept, then parts; dealer NPCs in a cell step to the
  dealer cell; COMPACT/RETRY per `MigrationDecision`). RETRY keeps the legacy flag (no `mt` saved). Sneak-use with an
  empty hand on any SINGLE retries; first use per session shows `msg.burmaldaholic.table.compact_hint`.
- Renderers on main (W2 owns the new board renderer): `client/table/TableFrames` maps the v0.1 1×1 content onto the full
  felt (cards/chips ×1.6 at the felt centre and height; roulette rotor on the bowl; craps dice), applied in
  `CardTableRenderer`, `RouletteTableRenderer`, `CrapsTableRenderer`; `SpectatorBlockEntityRenderer.shouldRenderOffScreen
  = true`; `SpectatorRenderState` carries `tablePart/tableGame/machineKind`.

### MT-2 — assets, layouts, worldgen, client
- Generator `tools/assets/modules/tables/furniture.mjs` (driver hook: one-line `modules/furniture.mjs`, kept out of
  `tables.mjs` because its art test asserts textures-only outputs): blockstates (4 facings × 7 parts), per-part baked
  models (turned legs, apron + kick band, wood slab, brass/gold trim line, felt 14/16, padded leather rail with rounded
  top 15/16 — craps 16/16 walls, crescent dealer ledge, dealer chip tray, roulette wheel housing with a static wheel and
  brass ring), 3×2 item miniatures, three themes (standard oak/green, high roller dark oak/gold/royal felt, player-banked
  mahogany/red) under `textures/block/core/tables/<theme>/`, loot tables. Vitest `furniture.test.mjs`.
- Layouts re-laid (`game()` stamps full footprints): village BJ (3,10) roulette (12,10) UTH (3,14) baccarat (12,14),
  croupier → (1,15), baccarat dealer → (12,12); parlor craps (6,7) poker (14,7) baccarat (6,13), piglin dealer → (14,5);
  lounge BJ HR (4,5) roulette HR (10,5) baccarat HR (4,11) UTH HR (10,11). Templates regenerated (TemplateExportTest).
  `MultiBlockLayoutsTest` = clearance R1–R6 on every layout (CB-C contract below).

### Visual passes 1–2 (owner bar "perfect", coordinator + VQA round 1 `findings/VQA-INWORLD.md` §MT)
- Tables (`tables/table_geom.mjs`): rounded rails, aprons and the brass band are **rotated segments** (6 per quarter arc;
  26.x model elements accept any `axis`/`angle`, identical format on 26.2 and 26.3 — checked in `CuboidRotation`), so the
  curves are smooth (no 1-px sawtooth); straight runs are axis boxes; hidden fills stay as columns. Rail section: outer drop
  (y 12.5–13.5, 1 px past the footprint), cushion (13.5–15), bevelled cap (15–15.5 → **FeltFrame.RAIL_TOP = 15.5/16**),
  brass lip on the felt side; proud lit brass band (light_emission 6); deep apron with dark kick band; 4-segment turned legs
  with brass ferrules; poker on twin pedestals with splayed feet; dealer tray = brass rim + 7 chip columns; lit table-limit
  placard (dealer side); roulette: lit tote board on a brass pole, raised bowl rim (y 13–17) with a lit brass band, bowl
  floor at y 13–13.5 with a static wheel that the RO rotor (Ø ≥ bowl opening, drawn at FELT_Y) covers whenever it renders
  (no second wheel; no hook needed). Palettes per VQA: standard leather #5A2E16, HR felt #4A2C86/#5A38A0/#3E2470 with black
  leather + gold stitching, player-banked felt #5E1A2A + oxblood leather; 3-tone wood with seams; leather with tufts and
  stitching.
- Machines (`tables/machines.mjs`, `machine_art.mjs`): complete without any renderer and lit at night (light_emission).
  Slots: tier cabinet panels, chrome edges, lit side strips, belly art, coin tray, lit button deck, lever (chrome + red
  ball), reel window aligned with the slot renderer's reels (y 19–28) behind a proud chrome bezel, lit topper. Wheel:
  bevelled plinth with lit bulbs, rear A-frame, brass rim ring (24 segments rotated about Z, 3.5 px deep) round the
  renderer's disc, hub cap, flapper. Plinko: the cabinet FRAMES `BoardFrame.of(PLINKO, MAIN)` (no baked face any more):
  base with coin slot + bin-tray lip, lit bulb pillars, lit crown marquee (sign art, no words), night-purple backing behind
  the board. Cashier: quartz counter 15/16 with brass edge and kick, three raised panels, money tray, two banker's lamps
  (lit green shades), glass partition in a brass frame with an arched brass grille window, lit dark-red sign plate with
  the localized word drawn by `client/table/CashierSignRenderer` (registered in CoreClientModule); Nether booth uses
  polished blackstone, gilded blackstone, crimson glass and soul-cyan lamps.

### MT-3 — upright machines (coordinator scope addition; API announced @9a26b4f)
- Sizes (width × height, one deep, main = bottom centre; widths odd so mirror() only swaps LEFT/RIGHT): SLOT 1×2,
  WHEEL 3×3, PLINKO 3×3 (4:3 board 2.75 × 2.0625 = 272×204 art px, bottom at 0.5), CASHIER 3×2 booth (3 wide, not 2,
  for the mirror rule). `MachineKind`, `BoardFrame.of(kind, part)` (FeltFrame equivalent for faces, compact = v0.1
  geometry the renderers draw today), `BoardFrame.scaleCompactToFull`, `MachineShapes`, per-kind part properties
  (`TableParts.SLOT_PART/UPRIGHT_PART/BOOTH_PART`), `MultiBlockTableBlock.machine(...)`.
- `machines.mjs`: blockstates, per-part models (see visual passes), part-aware loot. `TableFrames.toFullFace` maps the v0.1 wheel / Plinko renderer content
  onto the full face. Layouts: machines stamped full (village wheel moved to x13..15).

### R3 (2026-10-20) — VQA r2 closed by tests, real dealers, 26.3 loot
- VQA r2 specs 1–7 (`findings/VQA-INWORLD.md` round 2): d88cdf1f covered placard, slot topper/reels/lever, cashier plate +
  cash wall, Plinko panels + bulbs, HR band, leather. Finished here: **roulette rim** re-laid (d88's rim sat at 16.75,
  BELOW RO's rotor wall top ~16.9, and its inner wall / track ring were inside RO's wheel at r 6.0–6.75): outer face
  stepped 8 → 7.5 → 7.25 px, lit brass top band, dark inner wall r 6.75, cream ball-track lip r 6.5–6.75 at y 17–17.5,
  top `RIM.top` = 17.5 (2 px above the rail); **wheel-of-fortune rim** now bulbs2 (two colours) at light 15. The Plinko
  marquee emblem stays out: PL draws the localized title on MT's crown. No screenshots: every item is a deterministic
  contract in `tools/assets/modules/tables/vqa-r2.test.mjs` (geometry + pixel asserts, 15 tests).
- **Dealer API (for DL):** `TableFootprint.dealerCell(main, facing, game)` (= dealerPos; f −2, roulette r −1 behind the
  wheel), `dealerFacing(facing)` (= FACING, toward the table), `dealerYaw(facing)`; level-aware
  `TableParts.dealerSpot(level, anyCellPos)` → `DealerSpot(main, cell, facing, game, compact)` with `feet()` / `yaw()`
  (compact tables: the block directly behind), null for machines; check `TableParts.standable` before spawning.
  `TableFootprintTest.dealerCellBehindTheTableFacingIt` (every game × facing: outside footprint and walkway, one step
  ahead is BACK / roulette BACK_LEFT). `MultiBlockLayoutsTest` R4 now also asserts the dealer cell is reachable on foot
  from the table's walkway (flood fill) on every v0.1 layout.
- **26.3 loot (mod-wide):** all 19 `survives_explosion` tables are MT's (tables + machines). New `tables/loot.mjs`
  writes BOTH formats in every pool / entry (`conditions` + `block_state_property` for 26.2, `condition` + `match_block`
  for 26.3; each version ignores the other's keys). GameTest `lootTablesWorkOnThisVersion` evaluates the JSON directly
  (controller 1 item, parts 0, huge blast 0). `casino_charter` has no conditions (unaffected). Legacy
  `java/tools/gen_core_assets.py` still writes 26.2-only cashier loot: never rerun it for loot (MT's generator owns it).

- **T-CB D4 / D5 (CB-G HouseGames wired):** brought CB-G's `worldgen/HouseGames.java` (p2/cb-g @5f053bad, verbatim
  except `mainOf` → `TableParts.mainPos` and one `HouseTableProtection.install()` line in `register()`),
  `CasinoIndex.recordAt` (same hunk as CB-G), `HouseGames.register()` in WorldgenModule and the
  `house_table_protected` STRINGS row. New `worldgen/HouseTableProtection` implements MT's `TableProtection`:
  dropsAllowed / explosionProof from HouseGames, `playerPlaced` sets the persistent player-placed attachment on the
  MAIN block entity when the placer is a player; break permission stays with HouseGames' own break event. GameTest
  `houseTablesInsideCasinosAndPlayerTables`: real TNT and creeper (MOB) blasts at a part of a house table → whole
  table stays, 0 items; non-op break of a part refused; a survival player's item-use placement inside the casino is
  marked, the player breaks a part → table gone, exactly 1 item; TNT destroys a player's table (≤ 1 item).
  Merging p2/cb-g later: HouseGames.java conflicts in 2 lines (take MT's), CasinoIndex/STRINGS identical hunks.

- **Preset guard (BOT request):** `CasinoTableBlockEntity.preset()` returns empty without asking the provider when
  the BE is removed or its chunk isn't loaded (`hasChunkAt`), so a preset lookup never loads a chunk or reads state
  during unload. GameTest `presetLookupNeverLoadsAChunk`.
- **R3 verification (2026-10-20, 26.3 only from the owner's decision on):** tools `npm run gen:assets && check:assets
  && npm test` → 75/75; `compileJava compileClientJava compileGametestJava -Pmc=26.3` OK (26.2 compiled OK before the
  decision); `test --tests core.table.parts.* RawInputGuardTest worldgen.logic* BotsLogicTest` → 103 tests, 0
  failures; `runGameTest -Dfabric-api.gametest.filter=multi_block -Pmc=26.3` → all 15 passed (the first 14 also passed
  on 26.2); `-Dfabric-api.gametest.filter=worldgen -Pmc=26.3` → all 15 passed. Client lane not run (no client changes).

## Requests (R3)
- CB-C1 / CR: `TableClearance` R4 and `Kits.tablePod` still allow a lectern in the dealer cell — with real dealers it
  must be two blocks of air on a floor, reachable from the walkway (see `MultiBlockLayoutsTest.reachable`). Kits Javadoc
  line "dealer (NPC or lectern) at f −1" is stale (f −1 is the table's back row).
- RO: rim top is 17.5/16 again (≥ your Y_WALL), opening r 6.5 px, nothing of MT's inside r 6.75 below y 17.

## Verified (commands and results)
All in `java/` with JDK 25, heavy runs via `with-heavy-lock.sh`, client runs nested in `with-client-lock.sh`.
- Final (2026-10-15, after visual pass 2, policy "test only what changed"): `compileJava compileClientJava
  compileGametestJava` on 26.2 and `-Pmc=26.3` → OK; `test --tests 'dev.nezo.burmaldaholic.core.table.parts.*'
  --tests '*RawInputGuardTest' --tests '*worldgen.logic*' --tests '*BotsLogicTest'` → OK; `runGameTest
  -Dfabric-api.gametest.filter=multi_block` → **All 12 required tests passed** on 26.2 and on `-Pmc=26.3`.
- Tools: `npm ci && npm run gen:assets && npm run check:assets && npm test` → assets OK, 60/60 (furniture tests 5/5).
- Earlier full gates (before the policy change): `./gradlew build` (26.2) → all 233 server GameTests passed (one run
  failed only on an asset contract test mid-regeneration, fixed); full 26.3 `runGameTest` found the 26.3 loot-format
  issue (now fixed in code, see MT-1).
- Worldgen: `runGameTest -Dfabric-api.gametest.filter=worldgen` → 15/15 (26.2); old-template survey 59/64 GROW.
- Client (CGT=MT, 26.3, real window 1280×720, one JVM): `MultiBlockTableClientGameTests` passed (use key on the far
  BACK_LEFT part opens the main block's screen; walking into the table is stopped). Shots: `.burmaldaholic-tools/shots/MT/
  run1` (first models), `run2` (machines v1), `run3` (visual pass 2: tables, machines, night). The wood-seam removal
  after run3 is texture-only and was not re-shot.
- javap (26.2 vs 26.3 jars): all §7 APIs identical except `PushReaction` constants (BLOCK → IMMOVEABLE: not used),
  `Properties.isViewBlocking` lambda type (not used), `Block.playerDestroy` signature (not overridden), and inlined
  `LevelEvent.PARTICLES_DESTROY_BLOCK` (2001 vs 2014: not used); model elements: `light_emission` and
  `rotation {axis, angle}` (any angle) parse the same on both.

## Not verified / open items
- Chunk-border cascade guard is covered by code review only (no GameTest unloads a neighbour chunk).
- Client GameTests ran on 26.3 only (no 26.2 client subset).
- GameTest verify mode (rotated test structures) fails for helper-relative positions in my tests (tests assume
  rotation NONE); normal runs pass.
- Old-template survey (fixtures = v0.1 `.nbt` from git in gametest resources `mt_v01/`): 59/64 grow; the 5 village UTH
  tables (against the back wall, x5..7 z5..6) stay COMPACT.
- No per-whole-table outline (per-part outlines like beds).
- W2: replace `TableFrames` with `FeltFrame`/`BoardFrame` geometry in `client/board`; delete overhang code.
- PL: draw the 272×204 board on `BoardFrame.of(PLINKO, MAIN)` (x −0.875..1.875, y 0.5..2.5625, face z 15.5/16) — the
  cabinet frames exactly that opening (pillars x −1..−0.875 and 1.875..2, bin lip under it, marquee above). On p2/mt alone
  the opening shows a night-purple backing (+ the v0.1 ball/lamps scaled by `TableFrames.toFullFace`).
- RO: draw the rotor opaque at `FeltFrame.FELT_Y` centred on `wheelCx/wheelCz` with radius ≥ 6.75 px so it covers the
  static wheel in the bowl floor; idle ball resting in a pocket is RO's.
- Games' own distance checks (poker `PokerTableBlockEntity:733`, uth `:1270`, baccarat/blackjack spectator radius)
  still measure from the main block centre (≤1.6 blocks off at the far corner) — owners may switch to
  `TableParts.inRange`.

## Requests to other packages
- CB-G: install `TableProtection` (see Javadoc) instead of calling `HouseGames` from core; `HouseGames.mainOf` →
  `TableParts.mainOf`.
- CB-C1: clearance rules — tables: footprint 3×2, walkway r-1..1 at f+1, dealer cell (r, -2) (roulette r-1), R5 gap ≥ 2
  between any two game footprints, zones (footprint ∪ walkway) disjoint, dealer cell outside other zones, headroom
  y+1..y+3 over footprint ∪ walkway; machines: footprint = bottom row of `MachineKind.localCells()`, walkway = the row in
  front of it, slot–slot pairs exempt from R5. Reference implementation: `test/.../worldgen/logic/MultiBlockLayoutsTest`.
- W2 / RO / CM: `FeltFrame.of(game, part)`, `TableFootprint.toWorld`, roulette `wheelCx/wheelCz/wheelDiameter`.
