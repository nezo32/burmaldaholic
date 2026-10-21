# Handoff — lane UI-A (blackjack + baccarat, table UI v2 + in-world boards)

Branch: `worktree-agent-a51292b1a957e7a6b` (head: the commit adding this file; previous head `b3389fc`).
Contract: `docs/architecture/ui-v2.md` (lane A, §12). Design: `docs/design/visual/v2/REDESIGN.md` + mockups.

## Merged
- architect base `worktree-agent-ac6c9ab0bc1843843` (e19ec97, reset point)
- K kit `worktree-agent-ad31507f1451e0775` up to `39658b6`
- W board kit `worktree-agent-a60d35a15f5cae539` up to `c3e5147`-era (last merge before b3389fc; W was still enlarging text/cards — merge again)
- A1 hotfix `worktree-agent-ade08d82d0aecc860` @ `a54a9ad` (conflicts resolved: core `dispatchAction` keeps K's `resync` + hotfix's sync after sit/leave; fabric.mod.json = K list + `RealBetFlowClientGameTests`; old blackjack/baccarat screens replaced by v2 — hotfix's old-screen edits dropped)

## Done
- Pure (main): `games/blackjack/present/{BlackjackPub, BlackjackView, BlackjackFlow (implements kit Flow), BlackjackLayout, BlackjackWorld}`,
  `games/baccarat/present/{BaccaratPub, BaccaratView, BaccaratFlow, BaccaratLayout}`.
- BE public tags (no rule change): `BlackjackTableBlockEntity`/`BaccaratTableBlockEntity` `buildPub()`, `getUpdateTag` (+ kit `writePublic` via super), block update on change from `syncViewers`, client decode cache `clientPub()`. Hole card is never public face-down; baccarat cards follow the reveal.
- Screens rebuilt on `V2TableScreen`: `games/blackjack/client/BlackjackScreen` (local bet draft + Deal, Rebet (queued on result), insurance / even money, split, surrender, Leave on result, crescent rail arc, stamps, banner, celebration ≥10×), `games/baccarat/client/BaccaratScreen` (house multi-spot boxes P.PAIR·P·T·B·B.PAIR, undo/clear/rebet/×2, chemmy take/keep/pass/punt/banco, squeeze + third-card lines in the status).
- In-world: `BlackjackBoardRenderer`, `BaccaratBoardRenderer` on W's `CardTableRenderer`/`BoardRenderer` (felt, cards dealt from the shoe on their tick, hole flip, stacks, board lines, seat plates with detail lines).
- Shared: `client/table/cards/{CardsV2 (XL cards, deal/flip), RailArc}`; assets `tools/assets/modules/cards{.mjs,/faces.mjs}` → `faces_xl[_classic].png`, `backs_xl.png` (generated).
- Strings: STRINGS.md blackjack/baccarat `### Table UI v2 (lane A …)` + regenerated lang.
- Tests: unit `BlackjackPubTest`, `BlackjackFlowTest` (exhaustive), `BlackjackLayoutTest` (all sizes), `BaccaratPresentTest`; server GameTests `gametest/v2/{blackjack,baccarat}/*V2GameTests` (public tag); client real-flow `gametest/v2/blackjack/BlackjackV2ClientGameTests` (+ `CardBoards` world shots 3/5/8 m), `gametest/v2/baccarat/BaccaratV2ClientGameTests`; hotfix `RealBetFlowClientGameTests` re-pointed to v2 HitMap ids for blackjack/baccarat.

## Verified
- Unit tests (blackjack/baccarat present + core.ui.v2) green; `compileClientJava compileGametestJava` green after the hotfix + W + K merges (2026-10-13). `tools`: gen:assets / check:assets / npm test green (55 tests) when XL cards were added.
- Last client run (2026-10-13, interrupted by the wind-down): BlackjackV2ClientGameTests passed every step for EN 480×270, 640×360 @4, 640×360 @3, 427×240 and RU 480×270, plus insurance + split (screenshots in `docs/handoff/UI-A/`). Baccarat client test, CardBoards world shots and RealBetFlowClientGameTests had NOT run yet.
- NOT run: full `build`, `build -Pmc=26.3`, `runGameTest` (server GameTests incl. hotfix `RealFlowTableGameTests` and my `*V2GameTests`).

## Next steps (in order)
1. Merge W again (bigger text/cards) and K; compile.
2. Run client tests: `docs/handoff/UI-A/a_cgt.sh <fqcn…>` temporarily limits fabric.mod.json client entrypoints (restores it after; never commit while it runs). Run BaccaratV2ClientGameTests, BlackjackV2ClientGameTests (incl. CardBoards world shots), RealBetFlowClientGameTests; fix failures; look at the world shots (`v2a_world_*`) vs `world_blackjack_concept.png`.
3. `build`, `build -Pmc=26.3`, `runGameTest` (check `RealFlowTableGameTests` + `BlackjackV2GameTests`/`BaccaratV2GameTests`).
4. Visual polish vs mockups: result banner has no free region at 480×270 when the hero has cards (dealer row ↔ hero outcome band ~8 px) → status carries the result; consider a smaller banner at T2 (K) or smaller dealer cards at T2. Compare baccarat shots (never captured yet).
5. Optionally delete blackjack/baccarat rows from W's `BoardKit.interim()` (my registration overrides it already).

## Known bugs / risks
- K: key-hint plates show raw keys `key.keyboard.h` etc. (missing translations) — K lane.
- Baccarat Undo = `unbet` + re-`bet` of the previous amount (two packets; the server has no single-chip undo).
- Blackjack chips are a local draft (server takes one bet per round; a per-chip server bet would auto-deal a solo player).
- `RealBetFlowClientGameTests` roulette/craps parts still use old-screen widgets (lanes C/D).
- Server GameTests for v2 call BE methods (not the packet receiver); the real packet path is covered by the client tests and by the hotfix's `RealFlowTableGameTests` (TableNetworking.receive).

## Scratch
- `docs/handoff/UI-A/a_gradle.sh` (lock-respecting gradle wrapper), `a_cgt.sh` (run selected client test classes). Paths inside are absolute to this worktree.
