# Handoff — lane UI-W (in-world board kit, docs/architecture/ui-v2.md §7, §7.7)

**Branch** `worktree-agent-a60d35a15f5cae539`. Head: the commit that adds this file. The code before it is `32fc4d6`.
The branch starts from the architect's commit `e19ec97`.

## Done
- **W0 contract (`e5e3724`)**
  - `client/board/**`:
    - `BoardRenderer<T,S>` base: felt overhang. When a block is beside the table the felt falls back to the 1×1 top and
      the content is scaled down to fit.
    - Painters for felt, cards and chips; seat plates and the floating board; distance-based text size and LOD with
      hysteresis; `BoardStats`.
    - `SeatsBoardRenderer`, a temporary board for blackjack and baccarat, registered by `BoardKit` from
      `CoreClientModule`.
  - `core/ui/v2/board/BoardGeometry`, plus `PlateLayout`, which now knows about obstacles and remembers the previous
    frame.
  - `core/table/TablePublic`, plus the `writePublic` hook and throttled seat publishing in `CasinoTableBlockEntity`.
  - `CardTableRenderer` is now a thin subclass of the new base; poker and UTH are unchanged.
- **Bot tags:** `[BOT]` tags are hidden near tables that have a board (`bots/client/BoardAwareTextDisplayRenderer`).
- **Assets and strings:** sprites in `tools/assets/modules/board.mjs` and `board/art.mjs`; STRINGS section
  `core › In-world board`.
- **Tests:** `BoardGeometryTest`, `TablePublicTest`, and the client test `gametest/v2/board/BoardClientGameTests`
  (shots at 3/5/8/12 m and several angles, raised camera, blocked, poker, a 20-table perf scene).
- **Readability pass (`32fc4d6`, compiled + unit-tested on 26.2, NOT re-shot)**
  - Text sized by `HoloScale.readablePx`: target cap height ≥ 18 px up to 5 m and ≥ 13 px at 8 m.
  - Optional 2× lines: `line(..., 2f)` and `detail(..., 2f)`.
  - Card fans on the plates and the board within 6.5 m: `PlateRef.cards()` and `BoardState.boardCards()`.
  - Panels are 90 % ink with an accent frame; text has a drop shadow.
  - Cards lean 30°; chips have radius 0.085.

## Verified
- At `c3e5147` (Oct 7):
  - `build` passed on 26.2 and on `-Pmc=26.3` (167 GameTests each).
  - `check:assets` and vitest passed (55 tests).
  - `BoardClientGameTests` passed. Report: `docs/handoff/UI-W/board_report.txt`. The perf scene measured 0.47 ms per
    frame for 20 tables.
- At `32fc4d6`:
  - Merged the K branch at `a659844`.
  - `compileClientJava`, `compileGametestJava` and `core.ui.v2.*` tests passed on 26.2.
  - Not yet checked: 26.3, `build`, and the client test.

## Next steps (in order)
1. `build -Pmc=26.3` at head (the readability code uses only the APIs already used before).
2. Run the client test alone, via `docs/handoff/UI-W/cgt_inner.sh` under both locks. Re-shoot the set and measure from
   the screenshots, not from the formulas: ≥ 18 px glyphs at 5 m and ≥ 13 px at 8 m at 1920×1080. Check that the cards
   and card fans read comfortably at 5 m and that plates stay disjoint now that they are taller with fans. If plates
   stack too high, shrink the fan to the plate width or show the fan only for the viewer and the acting seat.
3. Chip value labels near or on hover (not done; bets are shown on the plates).
4. Add `/casino debug board` (not done; `BoardStats` exists).
5. Full `build` and `build -Pmc=26.3`, then merge the later K commits (K is now at `39658b6`) and the hotfix branch.

## Risks
- `EntityRenderers.register(TEXT_DISPLAY, …)` overrides the vanilla renderer for all text displays.
- Plates from neighbouring tables can overlap each other (only plates within one table are resolved).
- Card fans make plates taller, so expect more vertical stacking.

## Cross-lane
- Games that override `getUpdateTag` (poker, UTH, roulette, craps) must call `writePublic(t);` or their plates show
  no names.
- Lane A's registration replaces the temporary blackjack/baccarat boards.
- Worldgen spacing: the blocks beside a table at its height must be free. Two tables side by side need 2 free blocks
  between them (centre-to-centre ≥ 3).

## Files
- Screenshots: `docs/handoff/UI-W/*.png`. The full set is in the scratch folder `W/shots_final/`.
- Gradle runners: `gw.sh` and `cgt_inner.sh`; the paths inside them are for this container.
