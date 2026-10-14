# v0.1.3 — `/casino locate`

Branch `fix/v013-bets-locate`. Any player can find the nearest casino in their dimension.

## Behaviour

`/casino locate` is registered with `CasinoCommands.extendForPlayers`, so it needs no permission level.

1. **Players only.** If the console runs it, the command fails with `locate.players_only`.
2. **Casino mode off.** The command is refused with `locate.mode_off`.
3. **Cooldown.** A non-operator must wait `worldgen.locateCooldownSeconds` (default 30, range 0–3600, 0 = no wait) between searches. The wait uses monotonic time (`System.nanoTime`), is kept in memory per UUID and is cleared when the server stops. It is charged BEFORE the search runs, so a search that throws still costs it; only a `wrong_dimension` answer refunds it. Expired entries are pruned whenever a new one is recorded. It survives reconnects, so relogging does not skip it. Operators and the singleplayer / LAN host (`CasinoCommands.isIntegratedHost`) don't wait. A refused search reports the seconds left with `locate.cooldown`.
4. **Known casinos.** The command takes the nearest complete `CasinoIndex` record in the player's dimension.
5. **Casinos nobody has visited yet.** This search runs only while `WorldgenRuntime.active()` is true and the world has `generate-structures` on.
   - **Hosts.** The hosts are the `#minecraft:village` tag, `bastion_remnant` and `end_city`. A host counts only if `ChunkGeneratorStructureState.getPlacementsForStructure` returns a `RandomSpreadStructurePlacement` for it in this level. Without a host and without a known casino, the answer is `wrong_dimension`.
   - **Search order.** Each host's grid is walked ring by ring, like vanilla `/locate`, using `LocateRules.ringCells`. Start chunks outside the world border are skipped before anything is generated.
   - **Radius.** The search radius in chunks is `min(cap, distance to the known casino)`. Caps: villages 64, bastions 48, End Cities 96 (`LocateRules.capChunks`).
   - **Per candidate start chunk:**
     1. The chunk must pass `LocateRules.withinRadius`.
     2. It must pass `LocateRules.gate`, the same gate generation uses.
     3. `StructureManager.checkStructurePresence` must not return `START_NOT_PRESENT`.
     4. The chunk is loaded with `getChunk(x, z, STRUCTURE_STARTS)`. Its `ChunkAccess.getStartForStructure` must contain a `PoolElementStructurePiece` that `CasinoElements.layoutOf` recognises.
   - **Only real casinos.** A reported casino always exists in a real structure start.
   - **Stopping.**
     - A ring is skipped once it cannot beat the best hit. The test is `(r-1)·spacing − 8 > best`, which allows for the 8 chunks a casino can lie from its start chunk.
     - The whole search also stops after a 2 s wall-clock budget (`CasinoLocator.BUDGET_NANOS`). The budget is checked before every cell, before every holder's `checkStructurePresence` and before every `getChunk(STRUCTURE_STARTS)`.
     - **Server-wide limit.** At most one grid search starts per second on the whole server (`LocateRules.GRID_SEARCH_GAP_NANOS`, monotonic). Over the limit the answer comes from the index only: the known casino marked as cut short, or `locate.busy` when nothing is known.
     - **Honest answers.** `Outcome.truncated` is set when the budget ran out or the limit skipped the grid search. A found casino then gets the suffix `locate.cut_short`; no hit gives `locate.none_partial` instead of `locate.none`.
   - **Announcement.** `locate.searching` is sent just before this search starts.
6. **Output.**
   - **Message.** A known casino uses `locate.found`; one nobody has visited yet uses `locate.found_unexplored`. The arguments are: the kind name (`CasinoKind.nameKey()`), the coordinates `[x, y, z]` in green, the distance in metres and the compass direction (`gui.burmaldaholic.worldgen.dir.*`). The direction comes from `LocateRules.direction`, where north is −Z.
   - **Clicking the coordinates.** Operators get `ClickEvent.SuggestCommand("/tp @s x y z")`. Everyone else gets `ClickEvent.CopyToClipboard("x y z")`. A hover text explains which.
   - **No hit.** The command answers `locate.none` with the radius searched. If the unvisited-casino search did not run, it answers `locate.none_known`.

### Deviations from the researcher's design

- **Y is real, not `~`.** Every casino pool element uses `rigid` projection, so the piece's bounding box Y is the final Y. That holds for unvisited casinos too. The reported point is the centre column at `box.minY + 1`. This avoids a `/tp x ~ z` into a Nether wall.
- **Hosts come from the level, not a fixed dimension table.** In vanilla the result is the same: Overworld → villages, Nether → bastions, End → End Cities. A dimension whose biomes cannot hold its host simply has none. The 26.3 GameTest Nether is an example: it is a flat level with no bastion biomes. `wrong_dimension` is returned only when the level has neither a host nor a known casino.
- **Ring stop.** The design said to stop one ring after the first hit. The search instead uses the exact rule "stop when the ring can no longer beat the best hit", which allows for how far a casino can lie from its host's start chunk.
- **Extra keys and messages.** `locate.none_known`, `locate.players_only`, `locate.searching`, `locate.busy`, `locate.none_partial`, `locate.cut_short`, the two hover texts and the config label `config.burmaldaholic.worldgen.locateCooldownSeconds`. The config label sits in the STRINGS.md config-label table, next to the other `worldgen.*` labels, not in `## worldgen`.
- **Singleplayer host.** The host is exempt from the cooldown, like operators. Only operators get the `/tp` suggestion.
- **`search(level, x, z, searching, Limits)` and `locate(player, exempt, nowNanos, searching, structuresGenerate)` are public.** The GameTest server never generates structures, so the GameTests create the starts themselves and pass `structuresGenerate = true`. `Limits` also carries the server-wide gate and the budget, so tests can force a refused or zero-budget search.

### Gate parity (verified)

- **Village gate.** `VillageCasinos` gates on the start piece's position with seed 0. For vanilla villages that position is `(chunkPos.getMinBlockX(), y, chunkPos.getMinBlockZ())` of the start chunk. This was verified by reading the bytecode of `JigsawStructure.findGenerationPoint` and `JigsawPlacement.addPieces` on both 26.2 and 26.3:
  - villages have no `start_jigsaw_name`;
  - the heightmap projection only calls `move(0, dy, 0)`.

  So `LocateRules.gate(VILLAGE_CASINO, chance, _, cx, cz) = Gates.pass(chance, 0, cx<<4, cz<<4, SALT_VILLAGE)`.
- **Bastion and End City gates.** These use `Gates.pass(chance, levelSeed, startChunk.x, startChunk.z, SALT_*)`. `ChunkGenerator.createStructures` passes `ChunkGeneratorStructureState.getLevelSeed()` and the start chunk to `Structure.generate` on both versions.
- **Confirmed in real generation.** The GameTest `gatePreFilterMatchesRealGeneration` creates real starts at chance 0.5. Every start that holds a casino passed the locator's gate:
  - 26.2: 57 starts, 27 casinos, 29 gate passes;
  - 26.3: 42 starts, 20 casinos, 29 gate passes.

## Files

- `java/src/main/java/dev/nezo/burmaldaholic/worldgen/CasinoLocator.java` (new): the command, the cooldown, index lookup, grid search and confirmation.
- `java/src/main/java/dev/nezo/burmaldaholic/worldgen/logic/LocateRules.java` (new, pure): gate parity, caps, rings, radius and ring pruning, nearest candidate, 8-point direction, cooldown and server-wide gap (monotonic nanos, wrap-safe).
- `java/src/main/java/dev/nezo/burmaldaholic/worldgen/WorldgenModule.java`: `CasinoLocator.register()`, and the cooldown map is cleared on `SERVER_STOPPED`.
- `java/src/main/java/dev/nezo/burmaldaholic/core/config/sections/WorldgenConfig.java`: new key `locateCooldownSeconds`, `@Range(0, 3600)`, default 30.
  - No `SCHEMA_VERSION` bump and no `RETIRED_DEFAULTS` entry. A missing key keeps the field default (`ConfigManager.bindSection` binds onto `defaults()`), and the normalised global file is rewritten with it.
- `docs/design/CONFIG.md`: the new row.
- `docs/design/STRINGS.md`: 19 rows under `## worldgen` plus one config label. `java/src/main/lang/worldgen/{en_us,ru_ru}.json` were regenerated with `gen_lang.py` and converted to LF.
- `README.md`, `README.ru.md`: the player command.
- `java/src/test/java/dev/nezo/burmaldaholic/worldgen/logic/LocateRulesTest.java` (new, 7 tests): directions, cooldown (including a `nanoTime` wrap), server-wide gap, nearest candidate, rings, caps, and gate parity on golden SplitMix vectors computed independently in Python.
- `java/src/gametest/java/dev/nezo/burmaldaholic/gametest/worldgen/LocateGameTests.java` (new, registered in `java/src/gametest/resources/fabric.mod.json`):
  - `locateFindsIndexedCasinoForPlainPlayerThenCoolsDown`: uses a fake index record. The server-wide limit is checked first: a second grid search 0.5 s later only gets the index answer marked as cut short, and one 1.1 s later runs in full. Then a non-operator mock player runs the API and the real `/casino locate` command. The second search is refused by the cooldown; an exempt search and a search after 31 s both succeed.
  - `locateRefusedWhileModeOff`
  - `hostsFollowTheDimension`
  - `predictsUnexploredCasinoForEveryHost`: for every host type the test server can generate, forces the chance to 1 and creates the starts of 9 grid cells. The test then expects the nearest one to be reported as unvisited, not cut short, and inside a real casino piece. On 26.2 this covers villages, Piglin Parlors and High Roller Lounges; on 26.3 only villages, because its GameTest Nether and End are flat levels without bastions or End Cities.
  - `cutShortAndBusyAnswersSaySo`: a zero budget gives NONE + truncated (or the far known casino + truncated in a reused world); a refused grid search gives BUSY (or the index answer + truncated). With a known casino near, both give FOUND + truncated.
  - `gatePreFilterMatchesRealGeneration`

## Verification (Java now allowed)

- **26.2.** `./gradlew compileJava compileClientJava compileGametestJava test mergeLang checkLinkage` passes. `checkLinkage` checked 1783 classes against 26.3 and Fabric API 0.161.0+26.3. `LocateRulesTest`: 7 of 7.
- **26.2 GameTests.** `*locate*`: 6 of 6. The full server run (before the review fixes) passed 219 of 219.
- **26.3.** `./gradlew compileJava compileClientJava compileGametestJava -Pmc=26.3` passes. `runGameTest -Pmc=26.3` filtered to `*locate*` passes 6 of 6.
- **Lang.** `python java/tools/gen_lang.py --check` reports OK.
- **Not run.** Client GameTests; nothing client-side changed.

## Why the APIs link on both versions

These were checked in the class files with a small class-file reader, and `checkLinkage` passes:

- **`StructurePlacement` changed kind.** It is a class on 26.2 and an interface on 26.3. `CasinoLocator` therefore never calls a `StructurePlacement` method. It only checks `instanceof RandomSpreadStructurePlacement`, which is a class on both versions, and calls these methods on that class: `spacing()`, `getPotentialStructureChunk(JII)`.
- **`StructureManager.getStartForStructure` changed signature.** It takes `(SectionPos, Structure, StructureAccess)` on 26.2 and `(Structure, StructureAccess)` on 26.3. `CasinoLocator` uses `ChunkAccess.getStartForStructure(Structure)` instead, which is identical on both.
- **Identical on both versions:**
  - `StructureManager.checkStructurePresence(ChunkPos, Structure, StructurePlacement, boolean)` and `shouldGenerateStructures()`;
  - `ServerChunkCache.getGeneratorState()`;
  - `ChunkGeneratorStructureState.getPlacementsForStructure(Holder)` and `getLevelSeed()`;
  - `ServerLevel.getChunk(int, int, ChunkStatus)` and `structureManager()`;
  - `Level.getWorldBorder()` and `WorldBorder.isWithinBounds(ChunkPos)`;
  - `StructureStart.isValid()` and `getPieces()`;
  - `PoolElementStructurePiece.getElement()` and `getBoundingBox()`;
  - `StructureTags.VILLAGE`;
  - `BuiltinStructures.BASTION_REMNANT` and `END_CITY`;
  - `ClickEvent.SuggestCommand(String)`, `ClickEvent.CopyToClipboard(String)` and `HoverEvent.ShowText`.

## Risks and notes

- **Server-thread stall.** `getChunk(…, STRUCTURE_STARTS)` blocks the server thread, as vanilla `/locate` does. The bounds are the caps, the gate pre-filter (only `chance` of the cells generate), ring pruning, the 2 s budget (checked before every structure check and chunk load), one grid search per second server-wide, and the 30 s cooldown for non-operators. A single `getChunk` that is already running is not interrupted, so one call can still overshoot the budget by one chunk's structure-start generation. On worlds where many chunks are already stored, `checkStructurePresence` may read chunk NBT from disk.
- **Chance changed after generation.** If `*.chance` changed after a start was generated, an unvisited casino generated under the old chance may be skipped by the gate pre-filter. Fully generated casinos are still found through the index.
- **Data-pack or mod hosts.** Village structures added by data packs to `#minecraft:village` are searched, but their start may not sit at the chunk's min corner (a `start_jigsaw_name` moves it). For them the village gate may disagree with generation, which can hide a casino; it can never produce a false hit. Vanilla villages are exact.
- **Distance is horizontal.** The direction is computed from the player's position at the time of the command.
