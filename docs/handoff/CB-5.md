# Handoff — lane CB-5 (Preview + QA), casino buildings v0.2.0

Branch: `worktree-agent-affcf8598c6661906` (head: the commit adding this file). Base: architect contract `febc3cd`
(docs/architecture/casino-buildings.md). Local test branch `cb5-integ` (head `87ea8ea`) = my lane + CB-1 `601f8d3` +
CB-2 `c2b7d78` + CB-4 `a5c4c85` (CB-3 `dd1325e` NOT included, see risks). Do not push `cb5-integ`; it is a scratch merge.

## Done (all committed on the lane branch)
- `docs/design/visual/render_structure.py` (Pillow only): voxel models (slabs, stairs, carpets, panes, fences, walls,
  doors, banners, lanterns, chains, candles, pots, heads, game tables/slots/wheel/plinko with screen on the player side),
  procedural textures per material, sun shadows + vertex AO, night pass (block-light flood fill, halos only for lights
  the viewer can see), views `iso_se iso_sw iso_nw iso_ne cutaway(+game tags) plan(labelled games, player-side arrows,
  NPCs, chests, jigsaws, "MISSING families") night`, contact sheets, `--manifest` (PackGen format incl. assemblies),
  `--out`, `--style`, `--only`, `--selftest`. Unknown ids render magenta and are listed.
- Renders: `docs/design/visual/mockups/casinos/v2/_legacy/` (v0.1 baseline), `v2/_contact_sheet.png` (Tudor, Parlor, Lounge).
- GameTests in `java/src/gametest/java/dev/nezo/burmaldaholic/gametest/worldgen/`:
  - `CasinoQa` (shared): target discovery by id (v0.2 structures / v0.2 single layouts / v0.1), placement like worldgen
    (`/place structure` or pool element), region reset between runs, game spots read from the world, structural/light/
    terrain checks, REAL FLOW (mock player: join-teleport ack, player-loaded packet, `handleUseItemOn` from the player
    cell, bets through `TableActionPayload` via `handleCustomPayload`, croupier via `handleInteract` + `MenuRequestPayload`),
    server-message capture from the mock connection, BALANCE_CHANGED counter, version-tolerant reflection for
    26.2/26.3 (Structure.generate, GenerationContext, RandomState.create, climate sampler, teleport ack).
  - `CasinoBuildingGameTests` (server, 8 tests): registry, template palettes resolve, every casino × 4 placements
    (facing, support, player cell, reachable on foot, families, board clearance, light ≥10 over games / no dark walkable
    rooms for v2), every game opened + bet by real packets, one record per start, D5 complete-or-absent, fit rate +
    start-gen time (4 seeds, 2000 blocks), idle tick budget.
  - `CasinoPlayClientGameTests` (walk in with W along a server-computed path, right-click every game, bet via widgets
    found by label key at GUI 4 and 3 on 1920×1080, client-packet fallback; report `casino_qa/jcasino_play_report.txt`),
    `CasinoPhotoClientGameTests` (5 vantage points + night, 1920×1080, `jcasino_<casino>_<view>.png`;
    `-Dburmaldaholic.casinoPhotos=all`), `CasinoTerrainClientGameTests` (default worldgen, 2 seeds, locate, floating /
    cut terrain / reachability, chunk-gen cost vs control, photo; pre-merge it checks village annexes at chance 1).
  - Registered in `java/src/gametest/resources/fabric.mod.json` (CB-5 owns it).

## Verified
- Lane branch: `compileJava compileClientJava test compileGametestJava` green (Oct 7); `runGameTest` filter
  `*:casino_building*` 8/8 green (Oct 7, again after each change); gametest compile on `-Pmc=26.3`: my files OK.
- `cb5-integ` (Oct 13): 7/8 green; FAIL `casinoFitRateAndGenerationTime`: 42 % of biome-valid cells rejected (> 40 %
  contract) — real CB-1 tuning finding. Measured: 41 starts/4 seeds, median nearest 890 (1 style only), start-gen
  median 52–75 ms, p99 160–220 ms (contract 5/25 ms; gate here is 30× unless `-Dburmaldaholic.requireCasinoV2=true`).
  Tudor idle cost 0.4–0.8 ms/tick (22 tables). Every game in Tudor/Parlor/Lounge opened and bet on by real packets.
- Client (Oct 7, v0.1 casino): photo + terrain tests passed (2 seeds, annexes ok); play test opened all 9 games;
  walk-over-table bug fixed afterwards; the following play run hung in the Fabric world close (see risks) → fixed
  (no ESC/pause menu) but NOT re-verified. Client run on `cb5-integ` was started and aborted by the wind-down.

## Next steps (in order)
1. Re-run `runClientGameTest` with only the three Casino* client tests (see `docs/handoff/CB-5/cb5_client.sh`, it runs
   a copy with restricted entrypoints) on the integrated pieces; check `jcasino_play_report.txt` and screenshots.
2. After all lanes merge: `runGameTest` with `-Dburmaldaholic.requireCasinoV2=true` (5 styles, contract numbers).
3. Hand CB-1 the fit finding (42 % rejected, 52–75 ms start gen) and CB-2 nothing blocking (light/reach pass).
4. Regenerate renders after merge: `cd java && ./gradlew generateCasinoPack && python3 ../docs/design/visual/render_structure.py --manifest build/generated/casino-pack/previews/manifest.json --all` (or PackGen standalone, as done here).
5. Register CB-4's `OtherworldFitSurvey` in `fabric.mod.json` when CB-4 merges (class absent on this branch).

## Known bugs / risks
- CB-2 vs CB-3 conflict: both add `pieces/Style.java`, `OverworldStyles.java`, `AnnexPieces.java`; CB-3's Style uses
  `blueprint.RoofKind/FacadeKind`, CB-2's nested ones — `OverworldCasino` (CB-2) does not compile against CB-3's Style.
  Integrator must reconcile.
- Pre-existing: `WorldgenSpawnSurvey` (CB-1) does not compile on 26.3 → `build -Pmc=26.3` red regardless of CB-5.
- Real-flow UI findings (v0.1 screens): blackjack / roulette / UTH bets have no widget path the generic driver can
  click (chips are drawn hit areas, roulette needs a felt click); bets go through the client packet fallback. Slots,
  wheel, cashier bet by clicking. Parlor poker is VIP-gated by its preset (Low stakes) — tests grant VIP.
- Client GameTest world close can deadlock under heavy machine load (render thread in IntegratedServer.halt, server in
  the Fabric phaser). Kill the JVM if the log is idle >10 min.
- A full server-test run once logged a StackOverflowError while stopping (many loaded chunks); mitigated by region reuse.

## Scratch (outside repo)
`/tmp/claude-0/.../scratchpad/`: `cb5_client/` (copy used for client runs), `pg/` (standalone PackGen classes + out
manifest), `real*/` renders. Copied here: `docs/handoff/CB-5/cb5_gw.sh`, `cb5_client.sh`, `tudor_floor_a_vip_night.png`.
