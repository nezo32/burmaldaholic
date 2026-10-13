# Handoff — lane UI-C (roulette v2: screen + in-world board)

Branch `worktree-agent-a82d39566f52909c9` (head: see `git log -1`; the commit adding this file).
Base: architect `worktree-agent-ac6c9ab0bc1843843` (docs/architecture/ui-v2.md).

## Merged
- K (kit) `worktree-agent-ad31507f1451e0775` up to `39658b6`.
- W (board kit) `worktree-agent-a60d35a15f5cae539` up to `c3e5147`.
- Hotfix real-flow tests `worktree-agent-ade08d82d0aecc860` @ `a54a9ad` (conflicts resolved: `CasinoTableBlockEntity.dispatchAction`
  keeps K's `resync` + the hotfix's sync-after-sit; `fabric.mod.json` keeps both lists).

## Done
- Pure (`games/roulette/present`): `RouletteLayout` (wheel column, history pills, 14 × (3 + 2 rows) layout, call-bet row,
  precise hit-testing via `LayoutGrid`, stacks snapped off the printed numbers, value-tag placement, HitMap ids
  `felt.<spotKey>` / `felt.call.<section>`), `RouletteFlow` (implements kit `Flow<RouletteView>`), `RouletteView`,
  `RoulettePub` (public board: bettors, ≤12 stacks, top win, last numbers), `RouletteWorld` (in-world geometry).
- Server: `RouletteTableBlockEntity` writes `roulette_pub` + `writePublic(t)` in `getUpdateTag`, throttled publish (≤4/s),
  `rebet_total` in client state, test accessors `betsOf(uuid)`, `roundPhase()`.
- Client: `RouletteScreen` rebuilt on `V2TableScreen` (multi-spot BetBar, felt catch-all target + per-spot targets,
  calls, hover readouts, spin keeps bets visible + dimmed, gold winning cell, losing stacks fade, payout tags, banner/delta,
  ball cues); `RouletteFeltView` (procedural layout), `RouletteWheelView.medium` (104/144 wheels);
  `RouletteBoardRenderer` (+ `RouletteChips`) on W's `BoardRenderer` (felt, printed layout, wheel+ball on the shared path,
  player-coloured stacks, gold result cell, board lines, seat plates). Old `RouletteTableRenderer` deleted.
- Art (`tools/assets/modules/tables*.mjs`): `wheel_bowl_m{104,144}_<θ>`, `wheel_head_m{104,144}`, `history_pill_*`,
  `entity/roulette/layout_w_<θ>`. Strings: STRINGS.md `roulette › Table UI v2 (lane C)` (EN+RU), lang regenerated.
- Tests: `RouletteLayoutTest`, `RouletteFlowTest` (+ kit `FlowCheck`), `RoulettePubTest`, `RouletteWorldTest`;
  client `gametest/v2/roulette/RouletteV2ClientGameTests` (real flow: use block → Sit by primary → arm chip → click
  straight/split/corner/street/six-line/zero-split/dozen/red + Voisins → server stakes asserted → Spin → balance delta ==
  server settle → Rebet primary → Clear; EN/RU at 1920@4, 2560@4, 1920@3, 1280@3; world shots 3/5/8 m).
  `tables/RealBetFlowClientGameTests` roulette part re-pointed at v2 HitMap targets (`clickTarget`).

## Verified (2026-10-07)
- `build` (26.2) green; `build -Pmc=26.3`: one flaky slots GameTest once, `runGameTest -Pmc=26.3` re-run green (167/167).
- Unit tests `games.roulette.*` green after the hotfix merge (compileClientJava/compileGametestJava green).
- Client run (only lane C + RealBetFlow registered locally): ru 1920@4, en 2560@4, ru 1920@3 full real flow PASSED
  (report `docs/handoff/UI-C/v2c_report.txt`, shots in `docs/handoff/UI-C/`). Failures were test-setup only:
  en 1920@4 / en 1280@3 tables at dx ±6 out of reach (now ±2), world part could not sit (player far; now teleported).
  RealBetFlowClientGameTests did not run in that pass (my class threw first).

## Next steps (in order)
1. Re-run client tests (temporarily list only `RouletteV2ClientGameTests` + `tables.RealBetFlowClientGameTests` in
   `gametest/resources/fabric.mod.json`, don't commit that): fix anything left; check world shots `v2c_world_*`.
2. Compare shots with `docs/design/visual/v2/v2_roulette_*` and `world_roulette_concept.png`; tune: ResultBanner region
   (currently wheel column/play; banner not seen in the result shot), others' stacks use one colour (server `others`
   has no owner), RU status line ellipsis.
3. Merge latest W (larger text/cards) and K, re-run, then full `build`, `build -Pmc=26.3`, `runClientGameTest`.
4. Optional: racetrack overlay (neighbours bets) via the TopBar; old `TablesClientGameTests` roulette part still uses
   compact-frame flags (harmless).

## Risks
- Shared scratchpad names collide between agents (my `cgt.log` was truncated by another agent): lane C scripts live in
  `scratchpad/laneC/` (`run.sh <log> <1|2|client> gradle args`).
- `RouletteChips` draws its own small discs (kit discs 0.14 block are too large for a roulette cell).
