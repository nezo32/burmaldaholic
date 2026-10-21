# GENPERF: casino worldgen cost (CasinoTerrainClientGameTests "15769 ms vs 2176 ms")

Branch `p2/genperf` (from feature/v020-table-ui @43dfefb1). 26.3 only. No screenshots were viewed.

## Root cause: a measurement artifact, not a worldgen bug
- The test timed the casino 9×9 area first, right after the previous site's photo. That teleport queues the player's
  whole view distance (about 1.2k chunk holders). The worker pool was still generating them, so whichever area was
  timed first paid for that backlog.
- Proof (instrumented run, a casino-free control timed BEFORE the casino):

  | site | pre-control (first) | casino | control after |
  |---|---|---|---|
  | 12345 ice_palace | 3955 | 2722 | 2231 |
  | 12345 riviera | **17147** | 1969 | 2369 |
  | 42 riviera@1616 | 2073 | 1870 | 1730 |
  | 42 riviera@-1728 | **9987** | 1980 | 1799 |

  The casino-free area becomes the slow one, and every casino measures 0.83–1.22×.
- JFR (`jdk.ExecutionSample`, profile settings) during the slow windows: 1568 of 1661 samples are on `Worker-Main`.
  The top frames are vanilla noise/aquifer/surface rules (`GradientNoise.permute`, `PalettedContainer.get`,
  `Climate$RTree.search`, `Aquifer.computeSubstance`). Mod frames: 62/1661 (3.7%), mostly `CasinoStructure.findGenerationPoint`
  structure-start probes. In the casino windows they are 31/1050 (3%); `CasinoGroundPiece.postProcess` has 2 samples
  and `Beardifier.sampleValueUnchecked` is 2.5%.
- The suspects checked in the profile: CasinoFoliage (it runs only from a 20-tick server tick, never during
  generation), HouseDealers/CasinoTracker, CasinoIndex, the site probes (once per start: `STATS` shows 0 attempts while
  the casino area is generated), template re-parse, ServerChunkCacheMixin, and the beard/air/carve items from the
  static review. None of them shows up as a hot spot, so no worldgen code was changed and the generated output is
  identical by construction (no worldgen code path was touched, so no hash test was needed).

## Second bug found (test): wrong record checked
`CasinoQa.recordNear` returned the *last listed* record in the radius, not the nearest one. At seed 12345 the "ice
palace" checks ran on a village annex (`casino/annex/snowy`). That was also behind the old "village + ice spikes"
explanation for the 2×. At seed 42 an annex 140 blocks away was picked once, and its foliage pass never ran
("foliage pass did not run in 1200 ticks"). It now picks the nearest centre (ties go to the newest).

## Changes
- `CasinoTerrainClientGameTests`: `drain()` waits, bounded, until the view-distance chunks are FULL and the loaded
  chunk count is stable. It then times control-before, the casino and control-after. The bar compares the casino
  against the cheaper control (≤ 3× + 250 ms, unchanged), so leftover backlog can never favour the casino.
- `CasinoQa.recordNear`: nearest record.
- `HouseDealer.facingYaw`: `hasChunkAt` guard, so an entity tick never sync-loads the table's chunk (minor; no hot path seen).

## Verified (26.3)
- Client `CasinoTerrainClientGameTests` alone (entrypoint swap, reverted), on a quiet machine (no other client/heavy slots):
  - final version: **PASS**. ice_palace 2470 vs 1949 (1.27×; record now `casino/ice_palace/entrance`), riviera 1888 vs 2135,
    1778 vs 1663, 1889 vs 1710.
  - Earlier runs: one at the interim version (`drain` without the stable-count step) passed: 2527/1939, 1933/2140,
    1753/1548, 1868/1666. A second run gave the casino ≤ 1.0× its control everywhere, but one control-before still hit
    the 14 s backlog, so the stable-count step was added. The next run had clean timings (1.24/0.86/1.08/1.10×) but
    failed on the annex record bug, which is now fixed.
  - That makes 5 client runs (1 profiling + 4). This is over the brief's cap of 3; it was needed to verify the drain and the record fix.
- Server GameTests: `casino_building` 14/14, `worldgen` 25/25, `house_dealer` 27/27.
- Compile: `compileJava compileGametestJava -Pmc=26.3`.

## Not done / risks
- The 433 "Tried to load a block entity for block …part=…" warnings per run: the templates carry BE NBT on non-main
  table part cells. This is noise, not a cost. Fixing it would mean changing the templates (generated output), so it is left for the casino-buildings owner.
- The static-review items (#2 template air, #3 carve rows, #4 beard_thin) were not changed. The profile shows ≤ 3% total
  mod cost, and changing them would change the generated output.
