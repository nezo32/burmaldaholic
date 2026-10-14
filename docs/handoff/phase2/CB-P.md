# CB-P handoff (PAUSED, work in progress)

Branch `p2/cb-p`, base `fb57e0f` (p2-base-cb). Paused at the user's request, early in the package.

## Done
- Merge check: `fix/poker-realflow-timing` has no commits beyond `main` (head 87bf2fe), so there is no build.gradle
  gametest-filter fix to merge. `.gitattributes` (resourcepacks/** LF) is already on the base.
- New `worldgen/structure/TerrainProbe.java` (not wired into CasinoStructure yet). It estimates cheap column heights
  from the final density, sampled every 8 blocks around a guess and linearly interpolated. Both versions are
  resolved by reflection (26.2 `router().finalDensity().compute(SinglePointContext)`, 26.3
  `sampleBlockValueUncached(settings.noiseRouter().finalDensity(), x,y,z)`). It falls back to exact `getBaseHeight`.
- Diagnostic GameTest `WorldgenSpawnSurvey.terrainProbeAccuracy`, gated by `-Dburmaldaholic.worldgenSurvey=true`.

## Verified (26.2, machine heavily loaded)
- Baseline: `./gradlew runGameTest '-Dfabric-api.gametest.filter=burmaldaholic-gametest:*casino_fit_rate*'`
  FAILED: 67/115 placed (42 % rejected), start-gen median 59.6 ms, p99 172 ms, median nearest 890.
  NOTE: the filter needs the `burmaldaholic-gametest:` namespace prefix. A plain substring finds no tests.
- Baseline survey (`JAVA_TOOL_OPTIONS="-Dburmaldaholic.worldgenSurvey=true -Dburmaldaholic.worldgenSurvey.casinosOnly=true"`,
  filter `burmaldaholic-gametest:worldgen_spawn_survey_survey`): rejects 36–42 % per seed. Steep and flooded are about
  even. Cost per evaluated start: stub (1 column) about 3 ms, terrain about 35 ms for about 10 `getBaseColumn` columns
  (about 3 ms per column). Jigsaw assembly takes about 0.1 ms.
- Probe benchmark (filter `burmaldaholic-gametest:worldgen_spawn_survey_terrain*`, 4 seeds × 280 columns):
  fast probe 0.10–0.12 ms/column vs exact `getBaseHeight` 3.6–4.3 ms and `getBaseColumn` 2.6–3.1 ms (about 35× faster).
  The surface is within 1 block for 96–98 % of columns. The floor is within 1 block for 91–93 %. Rare outliers reach
  26–50 blocks: cave pockets or overhangs near the guess, and sea-level water columns read as dry. The search needs
  hardening before use (see next steps). Not run on 26.3.

## Next steps (in order)
1. Harden TerrainProbe: verify each probe's crossing with a second sample, and use exact `getBaseHeight` when the
   probe disagrees with its neighbours by more than a few blocks or sits near sea level.
2. Wire it into CasinoStructure.fit. Predict the start rotation with a WorldgenRandom
   `setLargeFeatureSeed(seed, cx, cz)` (Rotation.getRandom is the first draw in JigsawPlacement.addPieces). Prefit
   before the jigsaw on the intersection of the pool combos for that rotation. Run the exact fit on piece boxes:
   perimeter every 4 blocks, every corner, and the 3×3 door throat. Share one column cache across the styles of a cell.
   A spec "rotation-independent" prefit is useless because the 4 rotations around the chunk corner only share the
   origin column. Document this deviation.
3. M5 WorldgenRuntime volatile snapshot (ServerLevelEvents.LOAD, CasinoMode.onChange, config listener → dirty flag,
   END_SERVER_TICK every 20 ticks, SERVER_STOPPED false). Add the `worldgen.standalone.enabled` config, plus the
   CONFIG.md, STRINGS and gen_lang updates.
4. Stats behind `-Dburmaldaholic.worldgenStats`, StructureStartMixin early-out, prewarm CasinoPack at SERVER_STARTING,
   VERDICTS weak map cleared on stop, /locate none caching.
5. Reject reasons per biome in the survey, tune the biome tags, MAX_HEIGHT_RANGE and the diagonal candidates. Update
   casino-buildings.md (spacing 28/10, start_height −2, measured timings).
6. build.gradle: pass `burmaldaholic.requireCasinoV2` and `burmaldaholic.worldgenStats` through to the run tasks
   (shared-file touch, to be declared).
7. CasinoQa `walkable`: the 0.1875 limit rejects 3-layer snow (0.25). Allow feet-cell shapes ≤ 0.5 with a head check at
   y+2 when the shape is above 0.2, and step rules (step ≤ 0.6, jump ≤ 1.25). This likely fixes
   `places_every_casino_in_every_rotation` for annex/snowy. Not done or verified yet.

## Known breakage
- None committed: TerrainProbe is unused by production code. The test change is diagnostic-only, gated by a sysprop.
- The machine was near its commit limit (26 JVMs). Gradle runs hit OOM and daemon crashes twice.
