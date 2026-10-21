# CB-FIX: post-integration fixes on feature/v020-casino-buildings (from 901d15f0)

- **P1 deadlock (fixed):** `CasinoNpcs.spawn` hands its ServerLevelAccessor (WorldGenRegion on worker threads) to
  `HouseDealer.setHome(BlockGetter, …)`; `HouseDealers.floorHeight` throws on a live ServerLevel off the server thread.
  Test `HouseDealerGameTests.floorHeightRefusesLiveLevelOffThread`. The Terrain client lane no longer hangs.
- **O2 walker (test issue, fixed in the walker):** the 13 stuck waypoints were BFS shortcuts over slot stools, end-rod
  lamp posts, a trapdoor and a quartz-stair bench. A real player walks around them on the floor. `CasinoQa.floorPath` is
  a weighted search that stands only on floors, low blocks or real staircases, and `walk` holds jump. The new server test
  `CasinoBuildingGameTests.everyGameReachableOnTheFloor` passes: 32 of 32 games are reachable on foot.
- **O3 leaves (fixed):** `CasinoFoliage` runs on the main thread once every chunk within one chunk of a casino box is
  loaded. It removes the non-persistent leaves inside the box and runs once per casino per session. Test
  `foliageSweepClearsTreeLeavesIndoors`. In the Terrain run it cleared 116 and 73 leaves; all three riviera sites came
  out ok.
- **Ice palace 2.1× (documented, not fixed):** at seed 12345 `casino_ice_palace@1584,1104` takes 5115 ms against 2363 ms.
  The measured area also holds a snowy village (the record found was `casino/annex/snowy`) and ice-spikes terrain.
  The control area has neither. Ice palace is a palette swap of riviera, and the riviera sites measure 0.96–1.2×. No
  per-block loop in casino code was found. To make it a fair test, compare against a control area in the same biome
  without a village.
- Gate (26.3, build/run/gameTest wiped): `build` passed, server GameTests 296/296. Client run: CasinoPlay passed with
  0 failures (42 opens, 32 games). Terrain failed on the ice-palace timing only. No screenshots were viewed.
