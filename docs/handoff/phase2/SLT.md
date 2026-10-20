# SLT — slots: RTP ~98 %, jackpot clarity + jackpot gem, coin × multiplier, S1 sync

Branch `p2/slt` (from `feature/v020-table-ui`). Owner findings: E1 (slots feel rigged), S1 (slots desync).

## Done

### 1. RTP ~98 % (exact, `SlotRtpV2Test`)
| | Overworld | Nether | End |
|---|---|---|---|
| Lever | free spins 8/10/15 → **10/12/19** | 12/15/20 → **14/17/23** | outer ring → **12,15,20,25,30,50,100** |
| Lever-only RTP | 97.997 158 % | 98.144 598 % | 98.081 086 % |
| + jackpot gem | +0.375 % | +0.275 % | +0.325 % |
| **Total** | **98.372 158 %** | **98.419 598 %** | **98.406 086 %** |
| Owned | 97.346 949 % | 96.834 358 % | 95.628 308 % |
| Buy price / buy RTP | — | **20.8×** / 97.964 % | **107.2×** / 98.085 % |

- **Deviation from the brief:** the Nether buy is 20.8×, not ≈21.4×. At 21.4× the buy RTP is 95.26 %, which breaks the §6.3 parity rule (≤ machine and within 0.5 %) and makes `slots.validateRtp` warn. The End buy changes 109× → 107.2× for the same reason.
- Updated: `SlotDefaults`, the `SlotsV2Config` defaults, `SlotMachinesV2.DEFAULT_RTP/OWNED/FS3`, `SlotRtpV2Test`, `SlotMachinesV2Test`, `SlotsV2ConfigReviewTest`, SLOTS.md §0/§3/§4/§5.4/§6.3/§7.1/§7.3, CONFIG.md.
- Shared vectors: `slots_engine.json` was regenerated with the new `FX_DUMP_VECTORS=1` mode of `seededDrawsAndCodec` (only the free-spin counts changed; prefixes are identical). I added 4 gem draws and 2 gem timelines.
- **There is no Bedrock `defaults.ts` twin.** Bedrock was dropped on 2026-09-24 (SLOTS.md header), so there was nothing to update.
- **Config migration:** `ConfigManager` `SCHEMA_VERSION` goes 2 → 3, with a new `RETIRED_JSON` map. Old shipped defaults (awards, buy prices, outer ring) are dropped once from existing global files; custom values are kept. Test: `SlotsRetiredDefaultsTest`.

### 2. Jackpot clarity + base-game jackpot gem (SLOTS.md §5.4)
- Each paid base spin gets one extra RNG draw, drawn **last**, so all earlier draws are unchanged. Chances:
  - Overworld: Mini 250 ppm, Minor 50 ppm.
  - Nether: Mini 200 ppm, Minor 25 ppm.
  - End: Mini 150 ppm, Minor 20 ppm.
- The gem pays from the pool like any other hit (owned machines pay a fixed amount inside the cap).
- Combined Mini rate is now 1 in 2 214 / 2 232 / 1 978 spins.
- Tape flag `g1`/`g2` (`TapeCodec` validates it). Timeline beat `slots.jackpot_gem` sits at the end of the shared part, so the hunt-time visible tape stays a prefix. FX: `JackpotFx.drawGem`.
- The paytable has a **jackpot ladder** (Grand → Mini: amount + exact source + gem odds + bet-share note).
- Each jackpot meter has a hover tooltip with the same source line.
- Config: `slots.<m>.jackpot.gem` (validated: sum ≤ 1 %).
- 20 new EN+RU strings, in my own STRINGS.md subsection.

### 3. Coin × multiplier
- `BetSplit` (pure) computes the coin as `slots.<m>.coinValue` when it divides the ladder, else the ladder's gcd (5 / 10 / 50).
- The bet label reads "Coin 5 × 4". The plate keeps the total bet, and pays stay × total.
- The server sends `coin` from the full ladder, so VIP-filtered ladders always give a whole multiplier (`BetSplitTest`).

### 4. S1 sync (SY-SPEC §2/§3, the slots row)
- `ReelHandoff` (pure): when the result arrives, each pre-roll reel goes from its pose (top, speed) through a monotone Hermite curve (Fritsch–Carlson bounds) onto the timeline's landing pose (strip distance + whole turns). The existing landing and bounce then run, so the final stops equal the server's.
- `tH = min(preRollElapsed, firstLand − minMs(L))`, where `minMs` is 900 / 740 / 1 000 ms.
- `SpinClock.actor`: time is `tH + (wall − arrival)`, never late. Spectators read shared time. `t` is monotonic. Late join counts only after the whole shared part.
- The time source is swappable: `SpinClock.TimeSource` / `timeSource(...)`. It defaults to AnimClock. When p2/sy lands, point it at SmoothClock.
- Cabinet BER: `cabinetT` is monotonic per spin and reads the swappable source.
- Server: `startTick = gameTime() + START_LEAD_TICKS (4)`.

### 5. B6: slot screen on the v2 kit (K2 request; p2/k2 @48b2f766 merged)
- `SlotMachineV2Screen` now extends `V2TableScreen<SlotView>`, so its viewport hooks are the kit's final ones.
- Layout follows the `ViewportPolicy` and `TableGrid` rows:
  - **TopBar:** title, balance, `?` = paytable and jackpot ladder.
  - **PlayArea:** the cabinet and reels, from `SlotGeometry.docked(play)`. Cells are 42–44 px at 427×240…960×540. A 320-wide window gets a compact 32 px cabinet.
  - **Dock:** from the pure `SlotFlow`:
    - Bet stepper `− bet +`, with "Coin c × m" in the hint.
    - Buy / Autoplay / Turbo / Slot Showdown entry.
    - Primary: Spin / Stop / Stop autoplay.
  - Every disabled control has a reason (`FlowCheck`). Keys: Space, A, T.
- The reels, `SlotStage`, the sync handoff and the overlays are unchanged. `SlotBody` gains a docked mode (no widgets). The preview screen keeps the old layout.
- `V2ScreenUnityTest`: the slots exception is removed. `SlotV2KitTest` checks the four sizes plus 320×240: no overlaps, everything inside the PlayArea, cells ≥ 40, and the flow. `PlayFieldUnityTest` passes.
- Other owners' client tests that clicked the old slot widgets were updated:
  - `RealFlowClientGameTests` slots / VIP lock now go through the primary target and its reason.
  - `PvpRealFlowClientGameTests` slotsJoin now clicks `action.showdown`.

## Verified (commands run in `java/`, JDK 25)
- `compileJava compileClientJava compileGametestJava` on 26.2 and `-Pmc=26.3`: **BUILD SUCCESSFUL**.
- `test --tests 'dev.nezo.burmaldaholic.games.slots.*' --tests '*SlotsSpecTest' --tests '*RawInputGuardTest' --tests '*SlotsRetiredDefaultsTest' --tests '*ConfigManagerTest'`: **pass**. This ran after the sync commit, before the UI commit.
- `SlotSyncTest`: 5 tests pass.
  - Results arriving 0 / 300 / 800 / 1 500 ms late × 3 machines × 6 tapes, at 60 fps: no reel moves back by more than 0.2 cell, no frame moves more than 3.5 V·dt, and the final window equals the server's.
  - Speed stays in band, with C0/C1 continuity at the arrival and into the landing.
  - The actor clock ignores ±1 s time jumps.
  - The spectator clock stays monotonic under jumps and bunching.
  - Cabinet time stays monotonic.
- `JackpotGemTest`, `BetSplitTest`, `ConfigSpecCoverageTest`, `ConfigBinderTest`: pass. `gen_lang.py --check`: OK.
- Server GameTests `runGameTest -Dfabric-api.gametest.filter=slots`: **All 14 required tests passed**. This ran on 26.2, before the owner dropped 26.2 and before the B6 port.
- After the B6 port (26.3 only):
  - `-Pmc=26.3 compileJava compileClientJava compileGametestJava`: OK.
  - `-Pmc=26.3 test` over the slots packages + V2ScreenUnityTest + PlayFieldUnityTest + KitContractTest + RawInputGuardTest + the config tests: **181 tests**. All pass after one fix (a raw `pressMouse(0)` in my test).
- Client run `-Pmc=26.3 runClientGameTest`, `BURMALDAHOLIC_CGT=SlotsSyncClientGameTests` (entrypoint swap reverted): **NOT RUN**. It was queued behind other packages for about 1 h, then cancelled at the coordinator's wrap-up rule. **Tester: please run `BURMALDAHOLIC_CGT=SlotsSyncClientGameTests` and the slot parts of `RealFlowClientGameTests` / `PvpRealFlowClientGameTests` on 26.3.** These are the real-click proofs of the Dock SPIN, the handoff, the coin label and the meter tooltip. No screen of the ported slot UI has been seen in a real client yet.

## Not verified / risks
- The actor's reels now stop ≈ 0.7–1 s later than the server's settle (the handoff window), so the HUD balance hold is extended to cover it. The server settles at its own clock. Autoplay has no pre-roll, so it has no handoff and no extra lag.
- The handoff runs at up to 3.4 × cruise speed (blur frames) when the stop is nearly a whole turn away.
- In compact layout only the Grand and Major meters are visible (unchanged geometry). The full ladder is in the paytable.

## Requests to other packages
- **SY:** when p2/sy merges, set `SpinClock.timeSource(...)` to `SmoothClock` (or delegate `ANIM_CLOCK`), and replace `START_LEAD_TICKS` with `SyncLead`. `ReelHandoff`/`SpinClock.actor` match `ResultAnchor.asActor(tH)` semantics.
- **Core/config owner:** I bumped `ConfigManager.SCHEMA_VERSION` to 3 and added `RETIRED_JSON`. If EC also bumps the schema, merge both entry lists under one version.
