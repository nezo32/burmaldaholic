# WG handoff: casino placement in the Nether and End (owner playtest W1, W2)

Branch `p2/wg` (from `p2/cr-2`, CR-2 `f53e1095` merged). Owns placement: `CasinoStructures` append logic,
`logic/Geometry`, `logic/site/*`, `structure/DensityField`, `structure/CasinoTunnelPiece`, and the placement tests.

## Done
- **W1 Nether Parlor buried** — `logic/site/NetherSite` (pure) picks the site.
  - Candidates: 4 sides × floor heights base+{0..18 step 3} × shifts {0, ±8} along the side, scored on the noise terrain.
  - Terrain model: `structure/DensityField`, the final density at noise-cell corners interpolated like the generator,
    cached per start; `TerrainProbe.densityAt` is new and additive.
  - Rules:
    - the floor is at or above the lava sea (y 32); no lava inside or above the envelope;
    - at least 60 % of the ground under the foundation is solid;
    - a front tunnel goes from `FRONT_ENTRY` to the nearest open air at least 3 blocks high, and the model walks on
      from there at least 8 blocks out (`walksAway`);
    - a back tunnel goes from one step outside `BACK_ENTRY` into a bastion room (air in the bastion template,
      `HostCells`), else into open air next to the bastion.
  - No front route means no Parlor.
- **`CasinoTunnelPiece`** (`burmaldaholic:casino_tunnel`):
  - a 3-wide × 4-high corridor with polished-blackstone-brick floor and stairs (±1 block per step);
  - the shell is sealed wherever it holds a fluid or touches one (blackstone; glass at eye level where lava was), and
    gravel in the ceiling is replaced;
  - a shroomlight every 5 steps.
- **W2 End Lounge capping the city** — `logic/site/EndSite` (pure):
  - the whole template box is at least 24 blocks (XZ, full height) from every city piece box;
  - the door faces the city and the closest fitting site wins;
  - the plate row (`HighRollerLounge.FLOOR`) goes on the highest plate sample, with a spread of at most 4 (the
    piece's own skirt);
  - the ground at `STAIR_FOOT` must be between one block below the foot and the plate;
  - a `CasinoGroundPiece` SKIRT continues the skirt down to the ground wherever the probe was off.
- Non-noise generators (flat test worlds) and the v0.1 layouts keep the v0.1 placement.
- `/casino locate` and `CasinoIndex` are unchanged: the Lounge and the Parlor are still pieces of their host start,
  and the tunnel and skirt pieces are not casino pieces.

## Verified (26.3 is authoritative; the 26.2 runs happened before the owner dropped 26.2)
- `-Pmc=26.3 compileJava compileClientJava compileGametestJava`: OK.
- `-Pmc=26.3 test --tests 'dev.nezo.burmaldaholic.worldgen.*'`: **314/314 passed**. This includes `NetherSiteTest`,
  `EndSiteTest`, `TunnelTest` and CR-2's `CasinoSiteContractTest`.
- `-Pmc=26.3 runGameTest` with filter `worldgen_game_tests`: 11/11 passed; with filter `locate_game_tests`: 7/7 passed
  (`/casino locate` predicted path).
- `-Pmc=26.3 runClientGameTest`, only `CasinoSiteClientGameTests` (`BURMALDAHOLIC_CGT=WG`, temporary entrypoint swap,
  reverted), in a real default world, seed 12345:
  - **Nether: 3/3 Parlors reachable** on foot to open air, and each back tunnel opens into the bastion.
  - **End: the Lounge is 24+ blocks from every city piece, registered, and its door is reached from end stone.**
- Server GameTests `-Dfabric-api.gametest.filter=casino_site -Dburmaldaholic.worldgenSurvey=true`:
  - **26.2**: 3/3 passed.
    - Nether: 3/3 Parlors were reached on foot from the entry to open air 8+ blocks out, with no cell next to lava.
      Each back tunnel opens into the bastion.
    - End: the Lounge stood 24+ blocks from all 11 city pieces, was registered in `CasinoIndex`, and its door was
      reached from end stone (up/down 1 block only).
  - **26.3**: passed, but the Nether and End checks were skipped. The 26.3 GameTest Nether is flat and its End is
    not a noise End. These checks therefore also run in `CasinoSiteClientGameTests`, in a real default-preset world.
- Survey over 8 seeds (terrain model, same on 26.2 and 26.3):
  - Parlors: **218/241 = 90 %** placed and reachable; back door into the bastion for 201/218. Front tunnel median is
    about 3 blocks, max 22.
  - Lounges: **194/211 = 91 %** placed beside the city.
  - Generating all of these took about 44 s in total.

## Not verified
- No full suites (TEST ONLY WHAT CHANGED); no screenshots (owner rule).
- The real-world client check covers one seed (3 bastions, 1 End City). The 8-seed numbers come from the terrain model.

## Requests / notes to other packages
- CR-2: none open. The placement uses `PiglinParlor.FRONT_ENTRY` / `BACK_ENTRY` and
  `HighRollerLounge.FLOOR` / `PZ` / `STAIR_FOOT`.
- CB-1/CB-5 files touched (minimal): `StructureMixin` captures the generator, `RandomState` and height;
  `CasinoStructureTypes` registers `casino_tunnel`; `TerrainProbe.densityAt` was added; `fabric.mod.json` lists the
  two new test classes; `WorldgenGameTests.endCityGetsHighRollerLounge` now allows "no site on the main island".

## Risks
- The terrain model has no carvers or features. A carver cave or lava spring can still change the real terrain. The
  tunnel piece seals fluids around the corridor, but not along the Parlor envelope's open faces. A Nether lava spring
  could pour into the forecourt pocket; vanilla has the same risk for fortresses.
- About 10 % of bastions and 9 % of End Cities get no casino (no reachable site). This is by design: no buried casinos.
- Worldgen cost: the probe is per structure start, and the survey averaged about 100 ms per start on a loaded machine.
