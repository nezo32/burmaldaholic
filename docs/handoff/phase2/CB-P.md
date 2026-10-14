# CB-P handoff — casino worldgen platform (perf, fit rate, runtime safety)

Branch `p2/cb-p` (worktree `E:/projects/mods/minecraft/bw-p2-cb-p`), base `p2b-base-cb` (8844eb9, merged).
`fix/poker-realflow-timing` had no build.gradle fix beyond main; the selector fix arrived with the base.

## Done (by finding)

| Item | What |
|---|---|
| R-CB M5 | `WorldgenRuntime`: `active()` / `standaloneActive()` read volatile fields only. Recomputed on the server thread on overworld `ServerLevelEvents.LOAD`, `CasinoMode.onChange`, config listener (at once on the server thread, else dirty → next tick), every 20 ticks; false on `SERVER_STOPPED`. No DataStorage path from worldgen threads. |
| config | `worldgen.standalone.enabled` (default true, no schema bump) gates only standalone starts. CONFIG.md row, STRINGS config row EN/RU, `gen_lang.py` regenerated (LF). |
| M6a | `structure/TerrainProbe`: heights from final-density samples around the neighbour's height instead of noise columns. 26.2 `router().finalDensity().compute`, 26.3 `getSampler(...).sampleVolume` on 12-sample column windows; both by reflection, vanilla `getBaseHeight` fallback (flat worlds, failures). |
| M6b | Pack drops `project_start_to_heightmap` (the jigsaw sampled a full column only to be moved). Prefit (predicted rotation + start element from `setLargeFeatureSeed`) implemented, **off by default**: measured slower once the jigsaw costs ~0.1 ms. Rotation-independent prefit is impossible (4 rotations about the chunk corner share one column). |
| M6b/M7 exact fit | `structure/FitSite` (pure): box corners, 3×3 door throat (start template +z face), union outline ≤ 4 blocks (shared walls skipped), box centres. Coarse screen (outline 16) per shift, then the full set. 13 shifts (0, ±16 axes, ±16 diagonals, ±32 axes). FLOOD: water at the throat, deep water at > 1/16 of probes, water at > 1/8, or a bed > range+4 below the dry top. FLAT: trimmed span (n/24 each end) > `max_height_range`. |
| M6c | Column cache per placement cell (thread-local), shared by every style tried in the cell and both stages. |
| M6d | `CasinoStructure.STATS` only with `-Dburmaldaholic.worldgenStats=true` or when a survey enables it; per-biome reasons OK/FLAT/FLOOD/INCOMPLETE/BIOME. |
| M6e | `StructureStartMixin` memoises the D6 context per start object. |
| M6f | `SERVER_STARTING` prewarms `CasinoPack.pieces()` + `Layouts.all()`. |
| M7 | `max_height_range` stays 12 (measured: 6 → 68–75 % rejected); contract amended with the sweep. Spacing 28/10, `start_height -2` written into the contract. |
| M13 | Reasons per biome in `WorldgenSpawnSurvey` and near-spawn rejections in the fit test log. Hill biome tags NOT shrunk: rejection is already 24–36 % and every biome removed lowers density (the median-nearest gate was the binding one). |
| minor | VERDICTS → per-instance `WeakHashMap<RandomState, …>`, cleared on stop. `REQUIRED_POOLS` + `rear` (order entrance, floor, rear, bar — CB-G's codec test appends after `"bar"]`). `liquid_settings: ignore_waterlogging` (field exists on 26.2 and 26.3). QA timing gate: 30× unless `-Dburmaldaholic.requireCasinoV2=true` (build.gradle now forwards it, plus `worldgenStats`, `worldgenSurvey`). /locate: `StructureCheckMixin` answers NOT_PRESENT for a gated casino without touching vanilla's per-level cache. |
| CasinoQa | walkability: feet-cell collision ≤ 0.5 (snow layers, slab) with head clearance (2nd cell when > 0.2); BFS rises: walk ≤ 0.6, jump ≤ 1.25 with headroom. |
| tests | `FitSiteTest` (rotation = assembler's, start site inside every assembly × 4 rotations, throat outside/in front, outline ≤ 4, coarse ⊂ fine, golden 41×41), PackFilesTest golden, GameTests `worldgen_runtime_snapshot_tracks_mode`, `standalone_disabled_keeps_annexes`, fit test median-nearest over 8 seeds, probe benchmark `worldgen_spawn_survey_terrain_probe_accuracy` (needs `-Dburmaldaholic.worldgenSurvey=true`). |

## Verified (exact commands; machine shared with ~6 agents, often near its commit limit)

All GameTests via `rm -rf build/run/gameTest/world; ./gradlew --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g runGameTest
-Dfabric-api.gametest.filter=<filter>`.

- `test --tests '*FitSiteTest' '*PackFilesTest' '*AssemblyTest' '*PurityTest' '*RawInputGuardTest' '*TemplateExportTest'` → green (FitSite 4/4).
- Filter `burmaldaholic-gametest:*casino_building_game_tests_*` (26.2): 9/10. FIT PASSES:
  `placed 81 of 115 biome-valid cells (30% rejected) · start gen median 11,19 ms p99 31,66 ms` (another run 8.67/21.59);
  8 seeds nearest [104, 312, 386, 398, 525, 703, 713, 1240] → median 461; standalone off: 0 of 22 placed, villages 2/3
  annexes. Baseline at the CB base: 42 % rejected, median 30–60 ms, p99 75–172 ms, 4-seed median nearest 890.
  FAIL: `places_every_casino_in_every_rotation` — only `casino/annex/snowy` (see below).
- Filter `burmaldaholic-gametest:*worldgen*` (26.2): 19/20 → the one failure (`worldgen_game_tests_casino_codec_determinism_and_completeness`, required-pool order) fixed after the run by putting `bar` last; not re-run.
- `-Dburmaldaholic.requireCasinoV2=true` timing (5/25 ms): **not met** on this machine (median 8.7–14.5, p99 21.6–40 ms). Vanilla `getBaseHeight` cost 2.7–4.8 ms a column in the same runs (idle desktops: ~1 ms), so idle numbers should be 2–4× lower; contract §9.5 amended with the measured numbers.
- Survey 26.2 (`-Dburmaldaholic.worldgenSurvey=true -Dburmaldaholic.worldgenSurvey.casinosOnly=true`, sweep with `.sweep=true`): rejected 24–36 %, ~115 columns × ~0.1 ms per evaluated start, probe accuracy surface ±1 for 93–97 %, ±2 for 96–98 %.
- 26.3: `runGameTest -Pmc=26.3` filter `burmaldaholic-gametest:worldgen_spawn_survey_*` → 3/3; rejected 26–35 %; probe ±1 96–100 %. **26.3 is slower**: a lone density sample costs ~0.2 ms (compiled samplers), so ~0.3–0.5 ms per column even with volume windows; survey median 29–38 ms vs the old fit's ~10 columns (vanilla 26.3 `getBaseHeight` is 0.6–1.2 ms). See next steps.
- Client: `CasinoTerrainClientGameTests` only, one locked run — RESULT: see the bottom of this file.
- `./gradlew build`, `build -Pmc=26.3` compile: see the bottom of this file.

## Not done / open

1. **26.3 cost.** Options measured: grid volumes cost ~0.13–0.16 ms per column (vs 0.5 single) but only pay when the whole grid is needed. Next step: sample each outline edge as one line volume (1×12×N, step 4) on 26.3; or `fineSpacing 8` on 26.3 only (≈ −25 % time, same rejects on 26.2). Not decided — owner/architect call.
2. **Snowy annex reachability (CB-A).** Not the walkability threshold: a plan dump of the QA placement (`casino/annex/snowy` r0) shows the forecourt row in front of the door at feet y −62 while the ground outside is at feet y −60, with a 2-high balustrade (posts at every other x, 5-wide gap) on the row outside the box: entering means a 2-block drop that cannot be walked back. Other annexes pass with the same placement. CasinoQa now accepts snow layers/slabs anyway (physics fix), the failure remains → CB-A: add a step/ramp at the gap or raise the forecourt.
3. Hill biome tags unchanged (see M13 row). CR's 41×41 single piece will change rejects; re-run the survey.
4. `CasinoLocator` (not mine): skip the standalone host while `!WorldgenRuntime.standaloneActive()` (today it searches and finds nothing — wasted budget).
5. CR: a single-piece casino must set `PackFiles.REQUIRED_POOLS` to its pools (D5) — `completeStyles` and the codec use it. The fit, prefit and door throat need nothing else (the throat assumes the entrance on the start template's +z face).

## Requests to other packages
- CB-A: snowy annex forecourt (item 2).
- CasinoLocator owner: item 4.
- CR: item 5; keep the door on the template's +z face (door throat).

## Risks
- `TerrainProbe` is an approximation (rare ±3–8 errors; trimmed span absorbs isolated ones). Reflection paths are checked at class load; failure falls back to exact columns (slower, still correct).
- `StructureCheckMixin`: while gated, `/locate` also reports no already-generated standalone casino.
- Dropping `project_start_to_heightmap` makes the fit the only height source: a mod/datapack overriding our structure JSON with a projection still works (one extra column).
