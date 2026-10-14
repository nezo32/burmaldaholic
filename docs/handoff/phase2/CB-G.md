# CB-G: house games (break/loot protection, presets). PAUSED, WIP

Branch `p2/cb-g` (base `feature/v020-casino-buildings` @fb57e0f). Status: code written, **never compiled, no tests run**.

## Done (code only)
- **M9 protection.** New `worldgen/HouseGames.java` is registered with one line in `WorldgenModule` (`HouseGames.register();`).
  - A "house table" is a `CasinoTableBlock` (all game blocks, including cashiers, slots, wheel and plinko) inside any complete `CasinoIndex` record box of the same dimension.
  - The table must also lack the persistent BE attachment `burmaldaholic:player_placed_table`. `CasinoTableBlock.setPlacedBy` sets it when a player places a table, so player-placed tables stay normal, inside or outside casinos.
  - `PlayerBlockBreakEvents.BEFORE`: a non-op (below level 2, Survival or Creative) is refused and sees the overlay `msg.burmaldaholic.worldgen.house_table_protected`. This hook is separate from `Ownership.beforeBreak` and both run. Either one can cancel.
  - The protection is always on: no config key, and casino mode does not matter.
- **`core/table/CasinoTableBlock.java`** gets a `HouseTables` hook interface (static setter) and 3 overrides:
  - `getDrops` returns nothing for a house table. This covers ops, `/setblock destroy` and withers.
  - `onExplosionHit` is a no-op for a house table, so TNT, creepers and fireballs leave it standing. This goes further than the spec's "no drop only".
  - `setPlacedBy` sets the player-placed mark.
  - The spec only listed the getDrops hook. The other two are in the same file, which CB-G owns.
- **Pistons:** I made no change. Vanilla pistons don't move blocks that have block entities, so tables are immovable. `pistonsDoNotMoveTables` covers pushing and sticky-pulling but has not been run.
- **`CasinoIndex.recordAt(dim, BlockPos)`:** only does an index scan. It never loads chunks, and the preset lookups are unchanged (fb57e0f behaviour kept).
- **M10:** `GAME_OF` is now `Map.ofEntries` and includes `baccarat_table_player_banked` and `uth_table_player_banked`.
- **M11:** `TablePresets` changes:
  - PIGLIN_PARLOR baccarat → `parlor_baccarat`.
  - HIGH_ROLLER craps → `high_roller_craps` (100 / 2× / Gold).
  - HIGH_ROLLER poker → `high_roller_poker`, with fixed "mid" stakes (needs Gold VIP, the same tier as the Lounge).
  - `WorldgenPresets.of` builds all three.
- **STRINGS:** added the worldgen row `msg.burmaldaholic.worldgen.house_table_protected` (EN and RU) and ran `python java/tools/gen_lang.py`. That changed the en_us and ru_ru lang files.
- **Tests written, not run:**
  - Unit: `NpcShopAndPresetsTest` (updated expectations, plus `theV2ParlorAndLoungeGetTheirPresets`) and `WorldgenBotPresetsTest` (`everyBotCapableGameIsMapped`, player-banked ids).
  - Server GameTests in `WorldgenGameTests`:
    - `houseTablesCannotBeLooted` (synthetic record): Survival and Creative non-ops are refused with the message; the op break leaves no item; TNT and MOB explosions leave the table standing; a player-placed table inside the casino drops; a table outside casinos drops.
    - `pistonsDoNotMoveTables`
    - `tudorHouseGames`: real Tudor via `CasinoQa.place` at (-12000, 8000): non-op, op and TNT checks, and VIP player-banked bot defaults.
    - `parlorAndLoungePresets`

## Verified
Nothing. The only build attempt first failed because the Gradle daemon was out of memory (machine memory pressure). The retry was killed by the winddown.

## Next steps, in order
1. `./gradlew compileJava compileClientJava compileGametestJava` and fix compile errors. Check these first:
   - `ServerPlayer.nameAndId()`
   - `PlayerList.op/deop(NameAndId)`
   - `stack.useOn(...)` returning `InteractionResult.consumesAction()`
   - The switch on `Layouts` constants in `TablePresets`
2. `test --tests '*NpcShopAndPresetsTest' --tests '*WorldgenBotPresetsTest'`
3. `runGameTest -Dfabric-api.gametest.filter=WorldgenGameTests`. Check that the mock player is not op by default (the test deops explicitly). Check that the explosion asserts are meaningful (the synthetic test uses power 2 so the outside table at (7,1,7) is out of range).
4. Full `./gradlew build` and `compileJava compileClientJava compileGametestJava -Pmc=26.3`.

## Requests / risks
- **D2 (craps):** craps ignores `preset()` limits. `high_roller_craps` minBet 100 / 2× / Gold is only data until `CrapsTableBlockEntity.minBet/limits` read it. Today it only grants the `high_roller` advancement and marks the table as generated.
- **Multiplayer owner:** a charter near a generated casino might link house tables to a player casino. `claimError` doesn't exclude CasinoIndex boxes. Not changed here.
- Tables that players placed inside a casino box before this version have no mark, so they become house tables (no drop, even for ops).
- Withers and the dragon can still remove house tables, though no item drops.
