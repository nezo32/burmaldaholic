# Handoff — lane UI-D (craps v2: screen, in-world board, sprites, strings, real-flow tests)

Branch `worktree-agent-a37c8ab1842efc996` (base: architect `worktree-agent-ac6c9ab0bc1843843` / e19ec97). Head: see `git log -1`
(the commit adding this file, on top of f51b7b1).

## Merged
- K (kit) `worktree-agent-ad31507f1451e0775`: K0 a0b6cb0, K1 up to 17bb674 (NOT yet 39658b6 — merge again).
- W (board kit) `worktree-agent-a60d35a15f5cae539`: up to c3e5147 (W is still enlarging text/cards — merge again).
- Hotfix A1 `worktree-agent-ade08d82d0aecc860` @ a54a9ad (conflicts resolved in `CasinoTableBlockEntity.handleAction`
  — keeps K's `resync` + ack and the hotfix's sync after `sit` — and `gametest/resources/fabric.mod.json`).

## Done
- Pure (main) `games/craps/present/`: `CrapsLayout` (felt boxes per GUI size, spot ids `felt.*`, stack/puck/dice/banner
  boxes), `CrapsView` (state tag → view), `CrapsFlow` (gates, status lines, felt-click resolution: flat bet / odds / reason;
  `CrapsFlow.FLOW` implements the kit `Flow`), `CrapsPub` (public stacks + per-seat net for the board),
  `CrapsBoardGeometry` (in-world print geometry, mirrors `tools/assets/modules/tables/craps_v2.mjs`).
- `CrapsTableBlockEntity`: `pub()` in the update tag (`craps_pub`), `writePublic(t)` in `getUpdateTag`, throttled
  publish (≤ 4/s) on bets / sit / leave. No rule/money/RNG change.
- Client: `CrapsScreen` rebuilt on `V2TableScreen` (multi-spot BetBar, Undo/Clear, Sit → Roll primary, dice throw on the
  felt, puck, value pills, nets on settle, banner + balance delta, rules overlay). `CrapsBoardRenderer` replaces
  `CrapsTableRenderer` (deleted): print quad, stacks, 0.12-block dice on the shared throw path, puck, board lines, plates.
- Sprites (`tables/craps_v2.mjs` + 1 import line in `tables.mjs`): `gui/tables/craps/v2/dice_{14,18,22}`, `puck_{12,16,20}`,
  `entity/craps/board_print.png`. `check:assets` OK.
- Strings: STRINGS.md `craps › Table UI v2 (lane D)`, lang regenerated (only craps fragments).
- Tests: `CrapsLayoutTest` (every size, collisions, + dumps `build/craps_layout_<w>.txt`), `CrapsFlowTest` (exhaustive +
  `FlowCheck` + EN/RU keys), `CrapsPubTest`, `CrapsBoardGeometryTest`; `gametest/v2/craps/CrapsV2ClientGameTests`
  (sit → negative Roll → arm chip → Pass → roll to point (primary + Space) → odds lane → Come → Field → Undo → roll to
  resolution, money checked per roll; 480/640/427 EN+RU; in-world shots at 3/6 blocks). Hotfix
  `RealBetFlowClientGameTests.craps` re-pointed to v2 HitMap ids.

## Verified (2026-10-13)
- `compileClientJava compileGametestJava test --tests games.craps.*` green after the hotfix merge.
- `runGameTest`: all 175 required tests passed (incl. hotfix `RealFlowTableGameTests` craps solo / with bots).
- `compileClientJava -Pmc=26.3`: only errors were in W's files (fixed by W since); re-check.
- Client GameTest (2026-10-07, before the Undo fix): sit, bet, rolls, point, odds, Come, Field all passed at 480×270 EN;
  failed at "Field taken back by Undo" (Undo gated while the throw animated — fixed in b624d91, NOT re-run yet).
  Screenshots of that run looked very close to the mockup (were in java/build/run/clientGameTest/screenshots, since wiped).

## Next steps (in order)
1. Merge K (≥ 39658b6) and W (latest); compile client + gametest; `test --tests games.craps.*`.
2. `BURMALDAHOLIC_CGT=D runClientGameTest` (xvfb screen ≥ 2560×1440, see `UI-D/gw.sh`); fix failures; compare
   `v2_craps_*` shots with `docs/design/visual/v2/v2_craps_point_480.png`, and `v2_craps_world_3/6`.
3. Run the hotfix `RealBetFlowClientGameTests` (not lane-filtered) — craps part now clicks v2 ids.
4. Final `build`, `build -Pmc=26.3`, full `runClientGameTest`.

## Known risks / open points
- In-world board never seen in a screenshot yet (print orientation vs FACING, stack positions, plate seat spots
  `CrapsBoardRenderer.seatSpot`).
- `CrapsPub` seat index = `seats().seatOf(owner)`; bot-aware seat model may number seats differently (plates vs stacks).
- Dice snap from the throw's rest point into the fixed slots `die1/die2` after the throw (small jump).
- Readout caption is the kit's "N spots" (mockup says "On table") — kit (K) decision.
- No server packet test in `gametest/v2/craps/CrapsV2GameTests` (still K's stub); the hotfix's `RealFlowTableGameTests`
  covers craps through `TableNetworking.receive`.
- `gen_lang.py --prune` touches other lanes' fragments (baccarat, worldgen) — revert those if you run it.

## Files in docs/handoff/UI-D/
- `gw.sh` (lock-respecting gradle wrapper, slots 1/2/client), `preview.py` + `p480.png` (box preview of the 480×270 layout
  from `build/craps_layout_480.txt`: `python3 preview.py 480 270 <txt> out.png`).
