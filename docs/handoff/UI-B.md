# Handoff — lane UI-B (Hold'em + UTH, table UI v2)

Branch `worktree-agent-adf68662b2ca6ddcf`. Head: the commit that adds this file (parent `346f848`).
Base: architect branch `worktree-agent-ac6c9ab0bc1843843` (`e19ec97`). Contract: `docs/architecture/ui-v2.md`.

## Merged
- K (kit) `worktree-agent-ad31507f1451e0775`, last at `39658b6`.
- W (board kit) `worktree-agent-a60d35a15f5cae539`, last at `c3e5147`.
- The v0.1.2 hotfix was NOT available yet (no hotfix branch existed). Merge it when it exists and re-run the tests below.

## Done
Pure code (`java/src/main/.../games/{poker,uth}/present/`):
- `PokerLayout` — the oval table: board, pot, the viewer (hero cards, plate, stack pill, hand pill, bet), 2–9 seats. A
  free-box search keeps everything collision-free. A tag line under each plate holds hand names and actions (≤ 7 seats;
  on 8–9 seats the tag takes the stack pill's place).
- `PokerView` + `PokerFlow` — gates for every state: stakes picker, buy-in stepper, Fold / Check / Call / Raise to / Bet
  / All-in, top-up form, sit out / in, stand up. Also stepper math and the raise presets (½ pot, ¾, pot, all-in).
- `UthBetDraft` — the local bet editor: add, remove (right click), undo, clear, rebet, ×2, armed spot Ante / Trips.
- `UthView` + `UthFlow` — the BetBar gates, **Change bet** (primary after Deal; any chip or tool also changes the bet:
  the screen sends `clear` and keeps the amounts), the decision rows, the player-bank controls.
  `UthLimits` gives a reason for every Deal refusal.
- `UthTableLayout` — dealer, board, hero, Trips / Ante / Blind / Play spots, side columns for 0–5 seats.

Client:
- `games/poker/client/PokerScreen` and `games/uth/client/UthScreen` are rewritten on `V2TableScreen`.
- `V2Cards` maps tier → card atlas size. T3 uses L until lane A's XL atlas lands.
- `PokerTableRenderer` and `UthTableRenderer` are on the board kit: geometry, board lines, and seat plates with details.
  Hole cards appear only through the public tag (showdown / all-in).
- `writePublic(t)` is added to the poker and UTH `getUpdateTag`.

Strings: STRINGS.md `poker › Table UI v2 (lane B)` and `uth › Table UI v2 (lane B)`, regenerated with gen_lang. Do NOT
pass `--prune`: it deletes other lanes' keys.

Tests:
- Unit: `PokerLayoutTest`, `PokerFlowTest`, `UthFlowTest`, `UthTableLayoutTest`. They cover every size × seat count,
  never-stuck gates, and the EN + RU keys.
- Server GameTest: `gametest/v2/uth/UthV2GameTests` (change bet through the action handler).
- Client real flow: `gametest/v2/poker/PokerV2ClientGameTests` covers both games.
- The old `poker/PokerClientGameTests` now respects `BURMALDAHOLIC_CGT=B`.

## Verified
- `build` (26.2, includes the server GameTests): passed on 2026-10-07 at `d66d721`.
- `build -Pmc=26.3`: compiles. Its runGameTest failed once in an unrelated slots test
  (`slots_game_tests_v2restart_mid_treasure_hunt_settles_once`); it looks flaky, but nobody has re-checked it.
- Client run at `51c968c` + K/W:
  - Poker: the whole flow passed (pick stakes, stepper, buy-in, raise / call by clicking, result, 640 / RU shots,
    stand up).
  - UTH: passed through sit, chips, Trips spot, right click, undo, Deal and the decisions. It then failed only at
    reading the result. That read is fixed in commit "Lane B real flow: read the UTH result while it is shown" but has NOT
    been re-run.
- Compile (client + gametest) after the last merge: OK.

## Next steps (in order)
1. Re-run the client tests with only lane B. Swap `java/src/gametest/resources/fabric.mod.json` locally:
   `docs/handoff/UI-B/limit.py` swaps in, `git checkout` restores. Never commit the swap. Then run
   `BURMALDAHOLIC_CGT=B xvfb-run ... runClientGameTest` (see `docs/handoff/UI-B/cgt.sh`).
2. Check the UTH showdown shots (`v2_uth_*_showdown`). Check the UTH "Change bet" client path: it only runs if the fake
   second seat keeps betting open. Otherwise the server GameTest covers it.
3. Merge the hotfix when it exists. Keep its UTH / poker server fixes, and re-point its real-flow tests to the v2 ids
   (`primary`, `chip.N`, `felt.ante`, `felt.trips`, `action.<id>`, `stepper.minus/plus`).
4. Merge W again (it is enlarging text and cards). Run the in-world shots (`BoardClientGameTests`) on poker / UTH.
5. Final checks: `build`, `build -Pmc=26.3`, and the full `runClientGameTest`.

## Known bugs / risks
- Key plates on letter keys show `key.keyboard.r` (K's key-name lookup). This is K's issue; I reported it in this
  handoff and did not fix it in lane B.
- At T3, hero cards are L (37×49) centred in the XL box until lane A ships the XL atlas.
- `PokerPub` has no stacks: in-world plate details show bet / Fold / All-in / WIN, not the stack. The spec wanted
  "stack · last action".
- In the UTH decision shot taken right after the deal, the cards are still in flight. This is timing, not a bug.
- The poker stepper is an ActionRow button labelled `−  N  +`. Lane B adds the `stepper.minus` / `stepper.plus` hits on
  top of it (registered after each relayout in `refresh()`). If K re-lays out internally, those hits vanish until the
  next refresh.

## Cross-lane
- K: key names; maybe a real stepper component.
- A: XL card atlas (`faces_xl`).
- W: `PokerPub` / `UthPub` fields if the plates should show stacks / nets.

## Files outside the repo
- Screenshots: `java/build/run/clientGameTest/screenshots/*v2_poker_*`, `*v2_uth_*` (not committed).
- Helper scripts copied to `docs/handoff/UI-B/`:
  - `cgt.sh` — client run.
  - `build.sh` — build.
  - `t.sh` / `fail.py` — unit tests.
  - `limit.py` — temp fabric.mod.json swap.

  Their paths point at this worktree; adjust them.
