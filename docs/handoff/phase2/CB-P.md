# CB-P handoff — casino worldgen platform (perf, fit rate, runtime safety)

Branch `p2/cb-p` (worktree `E:/projects/mods/minecraft/bw-p2-cb-p`), base `p2b-base-cb` (8844eb9, merged).
`fix/poker-realflow-timing` had no build.gradle fix beyond main; the selector fix came with the base.

## Done (by finding)

| Item | What |
|---|---|
| R-CB M5 | `WorldgenRuntime`: `active()` / `standaloneActive()` only read volatile fields. They are recomputed on the server thread: overworld `ServerLevelEvents.LOAD`, `CasinoMode.onChange`, the config listener (immediately on the server thread, otherwise marked dirty for the next tick), and every 20 ticks. Both are false on `SERVER_STOPPED`. No worldgen thread reads DataStorage. |
| config | `worldgen.standalone.enabled` (default true, no schema bump) gates only standalone starts. Added the CONFIG.md row and the EN/RU STRINGS config row; `gen_lang.py` regenerated (LF). |
| M6a | `structure/TerrainProbe` takes column heights from final-density samples near the neighbouring probe's height, not from full noise columns. It works through reflection on both versions: 26.2 uses `router().finalDensity().compute`; 26.3 uses `getSampler(..).sampleVolume` on 12-sample column windows. It falls back to vanilla `getBaseHeight` for flat worlds and on failure. |
| M6b | The pack no longer writes `project_start_to_heightmap`; the jigsaw paid for a full column only for the fit to move it again. A prefit is implemented (start rotation and element predicted from `setLargeFeatureSeed`) but is **off by default**: it measured slower once the jigsaw costs ~0.1 ms. A rotation-independent prefit cannot work, because the four rotations about the chunk corner share only one column. |
| M6b/M7 exact fit | `structure/FitSite` (pure) samples: box corners; the 3×3 door throat on the start template's +z face; the union outline every ≤ 4 blocks (shared walls skipped); box centres. Each shift gets a coarse screen (outline every 16 blocks), then the full set. 13 shifts: 0, ±16 on each axis, the four ±16 diagonals, ±32 on each axis. **FLOOD** rejects: water at the throat; deep water at more than 1/16 of probes; any water at more than 1/8; or a water bed more than range+4 below the highest dry ground. **FLAT** rejects: the trimmed span (n/24 dropped at each end) exceeds `max_height_range`. The floor goes to the throat's ground (median), clamped to the span; terrain more than 8 above it rejects that shift. |
| M7 foundation skirt | `structure/CasinoGroundPiece`, registered as piece type `burmaldaholic:casino_ground`, adds two pieces to every start. **CARVE** comes first: it clears every piece box above the floor before the templates are placed. **SKIRT** comes last: it continues each column's solid bottom row down to the ground (at most 16 blocks), using the column's first full foundation block, else stone bricks. `max_height_range` stays 12; range 6 rejected 68–75 % of biome-valid cells. The contract now records spacing 28/10 and `start_height -2`. |
| M6c | One column cache per placement cell (thread-local), shared by every style tried in the cell and by both stages. |
| M6d | `CasinoStructure.STATS` only collects with `-Dburmaldaholic.worldgenStats=true` or when a survey enables it. It records reasons per biome: OK / FLAT / FLOOD / INCOMPLETE / BIOME. |
| M6e | `StructureStartMixin` memoises the D6 context per start object. |
| M6f | `SERVER_STARTING` prewarms `CasinoPack.pieces()` and `Layouts.all()`. |
| M13 | Reject reasons per biome are logged in `WorldgenSpawnSurvey`, and rejected cells near spawn in the fit test. Hill biome tags were **not** shrunk: rejection is already ≤ 33 %, and every removed biome lowers casino density. |
| minor | VERDICTS is now a per-instance `WeakHashMap<RandomState, …>`, cleared on stop. `REQUIRED_POOLS` now includes `rear`, ordered entrance, floor, rear, bar (CB-G's codec test appends after `"bar"]`). Structure JSON sets `liquid_settings: ignore_waterlogging` (the field exists on 26.2 and 26.3). The QA timing gate stays 30× unless `-Dburmaldaholic.requireCasinoV2=true`; build.gradle now forwards that property plus `worldgenStats` and `worldgenSurvey`. For /locate, `StructureCheckMixin` returns NOT_PRESENT for a gated casino without touching vanilla's per-level cache. |
| CasinoQa | Walkability accepts a feet-cell collision ≤ 0.5 (snow layers, slab) with head clearance (the second cell up is also checked when the shape is above 0.2). BFS step rule: walking climbs ≤ 0.6, jumping ≤ 1.25 with headroom. The cut-terrain check ignores logs and leaves: Tudor's dark-oak ceiling beams had been counted as 107 "cut" cells. A floating-column report now names the block. |
| "Saving chunks" hang | Root cause: `GameTestServer` inherits `MinecraftServer.forceSynchronousWrites() = true`, so every test-world chunk is written with DSYNC. jstack during the hang showed the server thread parked in `ChunkMap.saveAllChunks`; the only busy thread was an IO worker in `FileDispatcherImpl.pwrite0`. Fix: gametest mixin `GameTestServerMixin` returns false (new `burmaldaholic-gametest.mixins.json`, listed in gametest `fabric.mod.json`, a CB-P-owned shared file). GameTest `game_test_server_skips_sync_writes` checks it. Measured on the `casino_building_game_tests_*` run: overworld save 28–33 min → **38 s**. |
| tests | `FitSiteTest`: rotation matches the assembler; the start site lies inside every assembly in all 4 rotations; the throat is outside the building and in front of it; outline ≤ 4; coarse set ⊂ fine set; golden 41×41 case. PackFilesTest golden updated. New GameTests: `worldgen_runtime_snapshot_tracks_mode`, `standalone_disabled_keeps_annexes`, `game_test_server_skips_sync_writes`. The fit test now takes median nearest over 8 seeds. Probe benchmark `worldgen_spawn_survey_terrain_probe_accuracy` runs with `-Dburmaldaholic.worldgenSurvey=true`. |

## Verified (exact commands)

GameTests ran as `rm -rf build/run/gameTest/world; with-heavy-lock.sh ./gradlew --no-daemon --max-workers=6 -Dorg.gradle.jvmargs=-Xmx4g runGameTest -Dfabric-api.gametest.filter=<filter>`. Earlier runs used `--max-workers=4 -Xmx3g`. The machine was shared throughout.

- **Unit tests:** `test --tests '*FitSiteTest' '*PackFilesTest' '*AssemblyTest' '*PurityTest' '*RawInputGuardTest' '*TemplateExportTest'` → green (FitSiteTest 4/4).
- **`burmaldaholic-gametest:*casino_building_game_tests_*` (26.2, final code, fdac94a):** 9/10, finished in 1.5 min.
  - Fit test passes: `placed 77 of 115 biome-valid cells (33% rejected) · start gen median 10,51 ms p99 32,34 ms`.
  - Over 8 seeds the nearest casinos are [104, 312, 386, 398, 525, 713, 890, 1240] → median 461.
  - With standalone off: 0 of 22 cells placed; 2 annexes in 3 villages.
  - Shutdown save took 38 s.
  - The one failure is `places_every_casino_in_every_rotation`, and only for `casino/annex/snowy` (see open item 2).
  - Baseline at the CB base, for comparison: 42 % rejected, median 30–60 ms, p99 75–172 ms, 4-seed median nearest 890.
- **`burmaldaholic-gametest:*worldgen*` (26.2):** 19/20. The one failure (the codec test, caused by required-pool order) was fixed afterwards by putting `bar` last; it has not been re-run on its own, but the full build below covers it.
- **Timing gate under `-Dburmaldaholic.requireCasinoV2=true` (5 / 25 ms):** **not met** on this shared box: median 8.7–14.5 ms, p99 21.6–40 ms. In the same runs vanilla `getBaseHeight` cost 2.7–4.8 ms per column, against about 1 ms on an idle desktop. Contract §9.5 has been amended with the measured numbers.
- **Survey on 26.2** (`-Dburmaldaholic.worldgenSurvey=true -Dburmaldaholic.worldgenSurvey.casinosOnly=true`, plus `.sweep=true` for the sweep): rejection 24–36 %. Each evaluated start reads about 115 columns at about 0.1 ms each. Probe accuracy: surface within ±1 block for 93–97 % of columns, within ±2 for 96–98 %.
- **26.3:** `runGameTest -Pmc=26.3`, filter `burmaldaholic-gametest:worldgen_spawn_survey_*` → 3/3. Rejection 26–35 %; probe within ±1 for 96–100 %. **26.3 is slower**: about 0.3–0.5 ms per column even with volume windows, so the survey median is 29–38 ms (see open item 1).
- **Client, `CasinoTerrainClientGameTests` only, 3 locked runs** (default worldgen, 2 seeds, 4 casinos; entrypoint swap restored each time):
  - Run 1: Tudor casinos had 107–127 "cut terrain" cells and one casino had 3 floating columns.
  - Run 2, with carve and skirt: the same counts. The cells turned out to be the template's own log beams, not terrain.
  - Run 3, with the CasinoQa fix: ice_palace@12345 ok, tudor@12345 ok, tudor@42 ok. tudor@42 (-1728,1616) still has **3 floating foundation columns** at -1733,67,1659, on the front edge. The cause is not yet known; one candidate is a tree or feature block placed after the structure, and the report now names the block.
  - Chunk generation around a casino cost 0.8–2.2× the control area; the earlier outlier was 128 s against 5.8 s while the machine was swapping.
- **Full `./gradlew build` and the 26.3 compile:** see the "Final gates" section at the end of this file.

## Open items

1. **26.3 cost.** A single density sample costs about 0.2 ms on 26.3 (compiled samplers). Grid volumes cost about 0.13–0.16 ms per column, but only pay off when the whole grid is needed. Two candidate next steps; this is an owner/architect call:
   - sample each outline edge as one 1×12×N line volume on 26.3;
   - use `CasinoStructure.fineSpacing = 8` on 26.3 only (about −25 % time; on 26.2 it gave the same rejection rate).
2. **Snowy annex reachability (CB-A).** The cause is not the walkability threshold. A plan dump of the QA placement of `casino/annex/snowy` showed:
   - the forecourt in front of the door is at feet y −62, while the ground outside is at feet y −60;
   - the row just outside the box has a 2-high balustrade (posts at every other x, with a 5-wide gap).

   So entering means a 2-block drop that cannot be walked back. Other annexes pass with the same placement. Suggested fix for CB-A: a step or ramp at the gap, or raise the forecourt.
3. **Floating columns at tudor@42 front edge** (3 columns). Re-run the client terrain test once; the message now names the block.
4. **`CasinoLocator` (not CB-P's file):** skip the standalone host while `!WorldgenRuntime.standaloneActive()`. Today it searches anyway and finds nothing, which wastes budget.
5. **CR (single-piece 41×41 casino):** set `PackFiles.REQUIRED_POOLS` to the new pools (D5) and keep the door on the template's +z face (the door throat assumes it). The fit, carve, skirt and prefit need nothing else. Re-run the survey afterwards: a 41×41 site should reject fewer cells than today's ~47×47 assembly.

## Shared-file touches
- `java/build.gradle`: three system properties are forwarded to the GameTest JVM.
- gametest `fabric.mod.json` (CB-P owns it for the CB track): `mixins` entry added.

## Risks
- `TerrainProbe` is an approximation: rare errors of ±3–8 blocks; the trimmed span absorbs isolated ones. Reflection failures fall back to exact columns (slower, still correct).
- The CARVE piece clears whole piece boxes above the floor. A hill can show a clean vertical cut along the walls, up to 8 blocks high (the cut limit).
- `StructureCheckMixin`: while the standalone gate is closed, `/locate` also reports no standalone casino that was already generated.
- The new piece type `burmaldaholic:casino_ground` is saved in chunk data. Removing it later would drop those pieces when old starts load (harmless; vanilla logs and skips unknown piece types).

## Final gates (per the "test only what changed" policy)

- **26.2 compile + unit tests:** `./gradlew compileJava compileClientJava compileGametestJava test --tests 'dev.nezo.burmaldaholic.worldgen.*' --tests '*RawInputGuardTest'` → BUILD SUCCESSFUL, 248 tests, 0 failures.
- **26.3 compile:** `./gradlew compileJava compileClientJava compileGametestJava -Pmc=26.3` → BUILD SUCCESSFUL.
- **Filtered server GameTests (26.2):**
  - `burmaldaholic-gametest:*worldgen*`: 20/20.
  - `burmaldaholic-gametest:*casino*`: 35/36. The only failure is `places_every_casino_in_every_rotation` for `casino/annex/snowy` (CB-A, open item 2). Fit: 33 % rejected, median 12.8 ms, p99 30.6 ms.
  - `burmaldaholic-gametest:*locate*`: 7/7.
- **Client:** 3 locked `CasinoTerrainClientGameTests` runs, results above.
- **Two full `./gradlew build` runs** happened before the policy change (238 GameTests). Both had the snowy-annex failure plus 3–5 bot tests failing in the same batch as the casino tests:
  - `chemmy_bots_punt_against_ahuman_bank`
  - `atmosphere_bots_never_change_the_human_round`
  - `bots_watch_under_ahuman_banker`
  - `wheel_party_with_bots`, `bots_fill_then_yield_to_human` (first run only)

  The same bot tests pass alone (`*bots*`: 27/27). Two possible causes were seen:
  - The first build's runtime GameTest toggled casino mode, whose listeners reset bot tables. That toggle is now removed.
  - The long synchronous survey tests (fit test: 8 seeds, several seconds) block the server thread in the same batch. The second build failed this way after the toggle was gone.

  Unresolved. For integration it is either a batch-isolation item for the casino survey tests (e.g. its own GameTest environment/batch) or a tolerance item for the bot tests' "safe point" timing.

## Follow-up (coordinator decisions, 2026-10-15)

1. **Median nearest over 8 seeds:** accepted.
2. **26.3 cost:** done on 26.3 only. `CasinoStructure.fineSpacing263 = 8`, and row-volume sampling is implemented (`TerrainProbe.line`, `FitSite.lines`, `Cell.prefetch`). Survey on 26.3 (`-Pmc=26.3 -Dburmaldaholic.worldgenSurvey=true .casinosOnly=true`):
   - row prefetch on: median 93–99 ms, so it is left **off** (`-Dburmaldaholic.casinoRows=true` turns it on to compare). Each row fetches every side for every shift and loses the early exit.
   - row prefetch off, fine spacing 8: median **17.6–21.6 ms**, p99 67–115 ms, 27–35 % rejected. That is under the ~30 ms ceiling. 26.2 is unchanged: median 11.5 ms, p99 25.9 ms, 33 %. The numbers are in contract §9.5.

   `FitSiteTest.rowsCoverEveryOutlineColumn` is added.
3. **CasinoLocator:** `hosts()` and `standaloneHost()` leave out the standalone casino while `standaloneActive()` is false. New GameTest `locate_game_tests_standalone_disabled_drops_the_standalone_host` (`*locate*`: 8/8).
4. **Floating Tudor columns:** found by inspecting the client world's region file (seed 42, column -1733,1659). The column lies in the empty corner of the L-shaped assembly's union box, outside every piece, so it is not building at all. It is natural grass/dirt/stone with a 3-high natural cave at y 64–66. The start's NBT does hold both `casino_ground` pieces. So this was a QA false positive, not a skirt gap: `CasinoQa.terrainProblems` now skips natural-ground base blocks. Nothing in the skirt or fit needed changing. The client run that found it (client4) passed 3/4 casinos with this column as the only problem; there has been no client run since the fix.
5. **Bot tests in full builds:** every `CasinoBuildingGameTests`, `WorldgenSpawnSurvey` and `OtherworldFitSurvey` test now runs in environment `burmaldaholic:casino_worldgen` (`CasinoQa.ENV`, data file `test_environment/casino_worldgen.json`). The log shows it running as its own batch (12 tests). A full build was not run, per the test-only-what-changed policy.

Gates (26.2 unless noted):
- compile 26.2 and 26.3: pass.
- unit tests `dev.nezo.burmaldaholic.worldgen.*` + RawInputGuardTest: pass.
- `*locate*` 8/8, `*worldgen*` 20/20.
- `*casino*` 35/36; the only failure is the snowy annex, which goes to CR.
