# CB-G — House games (break/loot protection, presets)

Branch `p2/cb-g`. Base: `feature/v020-casino-buildings` @fb57e0f, then merged `p2b-base-cb` (8844eb9, v0.1.3).
Findings: R-CB M9, M10, M11.

## Done
- **M9 house-table protection** — `worldgen/HouseGames.java` (registered by one line in `WorldgenModule`).
  - House table = `CasinoTableBlock` (tables, slots, wheel, Plinko, cashiers) whose MAIN block is inside any complete
    `CasinoIndex` record box of its dimension (all kinds: standalone, v0.2 + v0.1 annexes, Parlor, Lounge) and whose
    main BE lacks the persistent attachment `burmaldaholic:player_placed_table`. Always on, independent of casino mode.
  - `PlayerBlockBreakEvents.BEFORE` (alongside `Ownership.beforeBreak`; either may cancel): non-op (below level 2,
    Survival or Creative) → cancelled + overlay `msg.burmaldaholic.worldgen.house_table_protected`; op in Survival →
    the hook removes the main block with `level.destroyBlock(main, false, player)` and cancels vanilla (no item);
    op in Creative → vanilla (no drop anyway). Game mode read from `player.gameMode` (mock players override isCreative).
  - `CasinoIndex.recordAt(dim, BlockPos)` — index scan only; no chunk loads (fb57e0f preset behaviour untouched).
  - `CasinoTableBlock.java` is **unchanged** (MT owns it in phase 2b) — see contract below.
- **M10** `WorldgenBotPresets.GAME_OF` → `Map.ofEntries` + `baccarat_table_player_banked`, `uth_table_player_banked`.
- **M11** `TablePresets`: PIGLIN_PARLOR baccarat → `parlor_baccarat`; HIGH_ROLLER craps → `high_roller_craps`
  (min 100, 2× tier max, Gold VIP); HIGH_ROLLER poker → `high_roller_poker` (fixed `mid` stakes = Gold VIP, the Lounge
  tier; `high` would need Diamond). `WorldgenPresets.of` builds them.
- STRINGS worldgen row `msg.burmaldaholic.worldgen.house_table_protected` (EN/RU) + `gen_lang.py`.

## Contract with MT (multi-block tables; MT owns `CasinoTableBlock` / `TableParts`)
All static, in `dev.nezo.burmaldaholic.worldgen.HouseGames`, always called with the **main** block position / BE:
| MT call site | Call | Meaning |
|---|---|---|
| `TableParts.removeAll` drop decision (every removal: ops, `/setblock … destroy`, withers) | `dropsAllowed(level, mainPos, mainBe, @Nullable player)` | false → no item |
| `onExplosionHit` on any part (or the explosion position filter, part → main) | `explosionProof(level, mainPos, mainBe)` | true → table stays, no item |
| `setPlacedBy` (placer is a Player) | `markPlayerPlaced(mainBe)` | the table is the player's even inside a casino |
| optional | `breakAllowed(level, mainPos, mainBe, player)`, `isHouseTable(...)`, `isPlayerPlaced(be)` | same rules |
- `HouseGames.mainOf(level, pos, state)` is the part → main seam used by the BEFORE hook; today identity. **MT-1
  integration: replace its body with `TableParts.mainOf`** (one line). With MT the op path `destroyBlock(main,false)`
  lets the parts cascade to air through MT's integrity rule (no drop).
- Until MT lands the explosion / no-drop-for-other-paths / player-placed mark are **decisions only** (tested at decision
  level); MT's MultiBlockTableGameTests should assert end-to-end "CB-G house part no drop", TNT at a house table → table
  stays, player-placed table inside a casino drops.
- Pistons: tables have block entities → vanilla never pushes/pulls them (MT adds `pushReaction(BLOCK)`);
  `pistonsDoNotMoveTables` proves push + sticky pull on today's blocks.

## Verified (2026-10-14, Windows, JDK 25.0.4)
- `./gradlew --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g compileJava compileClientJava compileGametestJava test --tests '*NpcShopAndPresetsTest' --tests '*WorldgenBotPresetsTest'`
  → BUILD SUCCESSFUL; NpcShopAndPresetsTest 5/5, WorldgenBotPresetsTest 9/9.
- `rm -rf java/build/run/gameTest/world; ./gradlew --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g runGameTest -Dfabric-api.gametest.filter=worldgen_`
  → "All 22 required tests passed" (WorldgenGameTests incl. new `houseTablesCannotBeLooted`, `pistonsDoNotMoveTables`,
  `tudorHouseGames`, `parlorAndLoungePresets`; WorldgenNpcGameTests incl. updated Lounge poker expectation).
- An earlier broader filter (`WorldgenGameTests`, which also matched other classes) showed two failures NOT from CB-G:
  `casino_building_game_tests_places_every_casino_in_every_rotation` (casino/annex/snowy player cells unreachable — CB-A)
  and `casino_building_game_tests_casino_fit_rate_and_generation_time` (median nearest 890 > 700 — CB-P).
- `./gradlew --no-daemon --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g build` → runGameTest (all server GameTests): "2 required
  tests failed" — only the two CB-A/CB-P ones above; every other test incl. all CB-G ones passed. The GameTest server
  then hung in "Saving chunks" (> 25 min idle) and was killed, so `build` reported FAILED (41 min).
- `./gradlew --no-daemon --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g test` → BUILD SUCCESSFUL, 1452 tests, 0 failures.
- `./gradlew --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g compileJava compileClientJava compileGametestJava -Pmc=26.3` → BUILD SUCCESSFUL.
- Not run: client GameTests (CB-G has no UI); `build -Pmc=26.3` full.

## Tests added / changed
- Unit: `NpcShopAndPresetsTest` (new expectations + `theV2ParlorAndLoungeGetTheirPresets`), `WorldgenBotPresetsTest`
  (`everyBotCapableGameIsMapped`, player-banked ids).
- Server: `WorldgenGameTests.houseTablesCannotBeLooted` (synthetic record: non-op Survival + Creative refused with
  message, op removes without item, table outside casinos and player-marked table inside drop normally, decisions for
  explosions/drops), `pistonsDoNotMoveTables`, `tudorHouseGames` (real Tudor via `/place structure`: non-op refused,
  op no item, roulette explosion-proof decision; Tudor VIP piece template placed + registered → player-banked
  baccarat/UTH use the worldgen bot defaults), `parlorAndLoungePresets` (v2 Parlor/Lounge through CoreServices presets).
  `WorldgenNpcGameTests.presetsFollowTheGeneratedCasino`: Lounge poker now `high_roller_poker`/mid.

## Requests / risks
- **MT**: the three calls above + `mainOf` swap.
- **D2/CM (craps)**: craps ignores `preset()` limits; `high_roller_craps` is data (it already grants the `high_roller`
  advancement and marks the table generated) until craps reads minBet / tierMax / minTier.
- **Multiplayer**: a charter near a generated casino may link house tables (`claimError` ignores CasinoIndex boxes).
- Tables players placed inside a casino box before this version are unmarked → treated as house tables.
- Withers/dragon may still remove house tables (no item once MT applies `dropsAllowed`).
- The op break is cancelled-and-done by the hook, so `PlayerBlockBreakEvents.AFTER` listeners do not fire for it.
