# BOT — bot avatars on multi-block tables (phase 2, R3)

Branch `p2/bot` (from `p2/w2`). Request from W2: in W2's client lane, blackjack `botTags` found 0 bot nameplates at a 3×2 table.

## Root causes
1. **Spectator viewers were ignored.** `BotAvatars.tick` skipped every player in spectator mode when it built the list of
   active tables. The W2 client lane switches to `gamemode spectator` before `botTags`, so at the next sync (every 20 t)
   the table dropped out of the list and its plates were discarded. A real spectator saw the same thing. Seat points were
   not the cause: `seatPoint` already used `TableFootprint.seatOffset` (MT), which puts seats 0.35 outside the rail.
2. **Carpet floors blocked every plate.** `clear()` required the block under the plate (the seat column at table height)
   to have no collision, or to be a casino table. Generated casinos lay carpet on the table level (`Layouts` boxes at
   y = 1), and carpet has a collision box, so no seat on a carpeted floor ever got a plate. The block below may now be
   free, low (collision top ≤ 0.5: carpet, slab) or a casino table. The plate's own block must still be free.

## Unload safety
- `AtmosphereBots.tableBots()` returns null (it creates nothing) while the table's chunk is unloaded. `table()` reads
  presets only when the chunk is loaded.
- `BotPresets.of` returns empty for an unloaded position. The worldgen provider calls `level.getBlockState`, which would
  load the chunk.
- `BotTables.of` (`at` / `near` / `known` / `remember`) skips block entities in unloaded chunks.
- `BotAvatars.refreshThinking` skips tables whose chunk is unloaded (`getBlockEntity` would load it). `clear()` checks
  `isLoaded` before it reads blocks.

## Tests
`gametest/bots/BotAvatarGameTests` (registered in the gametest `fabric.mod.json`) drives the real server tick with a mock
viewer standing in front of the table.
- 6 tests, one each for blackjack, baccarat, poker, UTH, roulette and craps. Each places a full table in all 4 facings
  and seats 2 bots. It asserts: exactly one plate per bot, within 0.01 of `TableFootprint.seatOffset` at
  `SEAT_HEIGHT`; no seat column on a cell of the table; the plate's block free; plate count equal to bot count.
- `spectatorViewerOnCarpetSeesPlates`: covers root causes 1 and 2.
- `legacyCompactTablePlates`: `part=single`, facing east.
- `unloadedTableLookupsNeverLoadTheChunk`: a blackjack block entity 30k blocks away. A probing preset provider must
  never be reached, the chunk stays unloaded, and nothing throws.
- The tests raise `bots.avatars.maxEntities` to at least 64 so that the parallel tests fit in the plate budget.

## Verified
All on 26.3 only (owner: 26.2 dropped):
- `./gradlew --no-daemon -Pmc=26.3 compileJava compileClientJava compileGametestJava` → exit 0. 26.2 was compiled once
  before the decision, also exit 0.
- `runGameTest -Pmc=26.3 -Dfabric-api.gametest.filter=bot_avatar` (heavy lock) → **All 9 required tests passed**.
  Earlier runs failed only because of the test harness: nested `runAfterDelay` polls never ran (now a `GameTestSequence`),
  and Hold'em needs a buy-in before `seatBot`.
- `test -Pmc=26.3 --tests 'dev.nezo.burmaldaholic.bots.*' --tests 'dev.nezo.burmaldaholic.core.bots.*' --tests '*RawInputGuardTest' --tests '*TableFootprintTest'` → 97 tests, 0 failures.

## Not verified
- No client run (NO SCREENSHOTS; W2's `botTags` lane should now pass. The coordinator or W2 should re-run `BURMALDAHOLIC_CGT=W`).
- 26.2 not compiled or run (owner: 26.2 dropped).

## Requests to other packages
- Core / table owners: `CasinoTableBlockEntity.preset()` and Poker/UTH `tableBots()` call the preset provider without
  an `isLoaded` guard. They are reached only through `BotTables` (now guarded) or gameplay (loaded), but a one-line
  `isLoaded(worldPosition)` guard in `preset()` would make every path safe.

## Risks
- Spectator-mode players now keep plates alive within `spectatorRadius`, which is the intended behaviour for viewers.
