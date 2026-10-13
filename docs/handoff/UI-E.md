# Handoff: lane UI-E (extras + PvP screens, table UI v2)

- **Branch:** `worktree-agent-aff40330cf445df3c` (not pushed). Head is the commit that adds this file; code head before it: `721c014`.
- **Scope (ui-v2.md §12, lane E):** v2 screens for Plinko, Wheel, Coin Flip, Scratch and Dice built on the kit (`client.ui.v2`) with the shared bet bar. PvP screens (challenge, invite, duel and result for every mode) reskinned onto the v2 frame. In-world Plinko board and Wheel plates. Assets in `tools/assets/modules/extras*`. Strings are in the `extras` and `pvp` "Table UI v2 (lane E)" sections of `docs/design/STRINGS.md`.

## Merged
- K kit `worktree-agent-ad31507f1451e0775` at `a659844`
- W board kit `worktree-agent-a60d35a15f5cae539` at `c3e5147`
- Hotfix `fix/pvp-modes` at `6f4dc7b`. Its `PvpRealFlowClientGameTests` were adapted to click the v2 `felt.battle` / `felt.party` targets.

## Done
- Presenter layer `main/.../games/extras/present/`: `MachinePub`, `LocalBet`, `ExtrasView`, `ExtrasFlow`, `ScratchFlow`, `ExtrasKeys`, `ExtrasLayout`.
- Client screens: `PlinkoScreen`, `WheelScreen`, `CoinFlipScreen`, `ScratchScreen`, `DiceScreen`, `DuelInviteScreen`, built on `ExtrasSceneScreen` and `SceneKit`.
- PvP kit in `client/pvp/kit`: `PvpFrame`, `InviteCard`, `KitButton`.
- Upright `PlinkoMachineRenderer`. Wheel/Plinko result plates now use `HoloScale.BOARD_BASE`.
- Generated fills `gui/extras/fill_*.png`.
- Tests:
  - unit: `MachinePubTest`, `ExtrasFlowTest`
  - server GameTests: `ExtrasV2GameTests`
  - client GameTests: `ExtrasV2ClientGameTests`, `PvpV2ClientGameTests`
- Last commit `35a263d`:
  - PvP side layouts keep Close and "?" in the Dock.
  - Plinko keeps Top prize visible when the Battle toggle is shown.
  - The dice cup overlay is removed from the invite card.

## Verified
- After `35a263d` and the K/W merges: `compileGametestJava test` passed (`721c014`).
- Before `35a263d` (head `1330f88`):
  - `build` passed.
  - `runGameTest`: 190 passed.
  - `-Pmc=26.3` compiles.
  - `npm run check:assets` and `npm test` (59) passed.
  - Client run cg4: all EXTRAS_V2 flows were OK (EN/RU at 480×270, 640×360 and 2560×1440) and the in-world shots were OK.
  - `PvpV2ClientGameTests` FAILED with "Close/? straddles the Dock edge" (PlinkoBattleScreen). `35a263d` is meant to fix this but it has not been re-run.
  - `PvpRealFlowClientGameTests` did not run because of that failure.

## Next steps (in order)
1. Run the client tests one at a time: `docs/handoff/UI-E/cgt.sh <log> dev.nezo.burmaldaholic.gametest.v2.extras.ExtrasV2ClientGameTests,dev.nezo.burmaldaholic.gametest.v2.pvp.PvpV2ClientGameTests,dev.nezo.burmaldaholic.gametest.pvp.PvpRealFlowClientGameTests`. Fix the paths inside the script for your machine. It swaps the client gametest entrypoints in `fabric.mod.json` and restores them on exit.
2. Confirm that every PvP mode (plinko battle, wheel party, coin duel, dice duel and the others) plays end to end through the v2 screens in `PvpRealFlowClientGameTests`.
3. Review the shots in `build/run/clientGameTest/screenshots` against `docs/design/visual/v2/mockups/v2_plinko_480.png`. Check the invite card name width now that the cup is gone, and the readability of the in-world plates.
4. Run `./gradlew build` and `./gradlew build -Pmc=26.3`. Merge any newer K, W or hotfix commits first.

## Known risks
- The new `sideControlPositions()` contract: pos[2] and pos[3] (Close and rules) are always overridden to the Dock row in `PvpModeScreen.init`.
- `PlinkoScreen` side panel at 480×270 with PvP on shows only one row of last balls (2 pills).

## Scratch (copied here)
`docs/handoff/UI-E/`: `cgt.sh` (client test runner), `g.sh` (server gradle with the locks), `sheet.py` (contact sheet), `plinko_480_open_cg4.png` (last v2 Plinko shot).
