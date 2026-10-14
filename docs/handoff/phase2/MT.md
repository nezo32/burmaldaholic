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

### MT-3 — upright machines (coordinator scope addition; API announced @9a26b4f)
- Sizes (width × height, one deep, main = bottom centre; widths odd so mirror() only swaps LEFT/RIGHT): SLOT 1×2,
  WHEEL 3×3, PLINKO 3×3 (4:3 board 2.75 × 2.0625 = 272×204 art px, bottom at 0.5), CASHIER 3×2 booth (3 wide, not 2,
  for the mirror rule). `MachineKind`, `BoardFrame.of(kind, part)` (FeltFrame equivalent for faces, compact = v0.1
  geometry the renderers draw today), `BoardFrame.scaleCompactToFull`, `MachineShapes`, per-kind part properties
  (`TableParts.SLOT_PART/UPRIGHT_PART/BOOTH_PART`), `MultiBlockTableBlock.machine(...)`.
- `machines.mjs`: blockstates, models (slot upper cabinet under the renderer's cabinet, wheel plinth + post, Plinko
  cabinet with the v0.1 face stretched over the board square + gold side frames + marquee, cashier counter + panelled
  wall + brass-framed iron-bar window), loot. `TableFrames.toFullFace` maps the v0.1 wheel / Plinko renderer content
  onto the full face. Layouts: machines stamped full (village wheel moved to x13..15).

## Verified (commands and results)
See the "Gates" section below (filled at the end).

## Not verified / open items
- Chunk-border cascade guard is covered by code review only (no GameTest unloads a neighbour chunk).
- Client GameTests ran on 26.3 only (no 26.2 client subset).
- GameTest verify mode (rotated test structures) fails for helper-relative positions in my tests (tests assume
  rotation NONE); normal runs pass.
- Old-template survey (fixtures = v0.1 `.nbt` from git in gametest resources `mt_v01/`): 59/64 grow; the 5 village UTH
  tables (against the back wall, x5..7 z5..6) stay COMPACT.
- No per-whole-table outline (per-part outlines like beds).
- W2: replace `TableFrames` with `FeltFrame`/`BoardFrame` geometry in `client/board`; delete overhang code.
- PL: draw the 272×204 board on `BoardFrame.of(PLINKO, MAIN)`; my model shows the v0.1 face stretched as a stopgap.
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
