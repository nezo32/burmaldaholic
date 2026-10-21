# SYW: sync wiring (SY-SPEC §2–§5 per game), handoff

Branch `p2/syw` (from feature/v020-table-ui @1e886ef0). Owner finding S1 (slots and craps snapped on a bad connection).
No money, payout or RNG change.

## Done
### One clock everywhere
- `AnimClock.localMs` / `MotionClock.localMs` now read `SmoothClock.wallMs` (production: the same nano clock as
  `Util.getMillis`; a `SmoothClock.Test.source` now drives every local beat too).
- Slots: `SpinClock.SMOOTH_CLOCK` (shared/level time = SmoothClock, wall = `SpinClock.wallNow()`). The stage, the
  pre-roll, the actor anchor and `SlotBody` share that wall. The Treasure Hunt hold start reads SmoothClock (it was raw
  `getGameTime`). The cabinet BER already read `timeSource().levelMs` → SmoothClock.
- Roulette: `RouletteScreen.rawClock` = `SmoothClock.nowMs − tick·50`. `RouletteTime` stays as a per-launch monotonic
  guard (a pass-through on a smooth input). Its wall and the RESULT clock are on SmoothClock's wall.
  - In-world (T-RO S2): `RouletteBoardRenderer` samples SmoothClock (via the BER base) plus a per-launch monotonic guard.
  - `RouletteWorldClockTest` covers it under NetLag: max frame ≤ 1.15·v·frame, rests in the server pocket. The raw-clock
    control jumps more than 3× the bound.
- Craps: `CrapsRollClocks` keeps LOCAL time (`MotionClock.localMs`, now SmoothClock's wall). Every throw is anchored on
  this client (the roller at the press, a spectator at arrival), so a server time jump never moves it. A SmoothClock
  source would make the roller snap on +1 s.
- Cards:
  - The Baccarat and Blackjack screens now use `SmoothClock.nowMs(partial)`. They used raw `getGameTime`, and Baccarat
    had no partial tick in `nowMs()`.
  - New `core/anim/sync/LateFlight` + `client/table/cards/DealAnchors`: one first-seen anchor per deal index. A late card
    still flies, ≥ 120 ms. Cards already landed when the viewer arrived, or more than 3 s stale, settle. Used by both
    screens and both in-world boards.
- Plinko / Wheel screens: their wall moved to `AnimClock.localMs` (they are actor-anchored at arrival).
- PvP dice: `duelMs` reads `SmoothClock.nowMs` directly.

### Server leads (`SyncLead`)
- Slots 4: replaces `START_LEAD_TICKS`, not doubled.
- Plinko 4 and Wheel 4: the published start, the busy gate and the pending lines all shift together. Inside the lead
  the boards no longer show the outcome: the wheel plate keeps the name, and the plinko board shows no rested ball.
- PvP dice 4: on top of SHAKE_LEAD. The cosmetic seed stays keyed on the decision tick.
- Not added, because an equivalent lead exists or a lead does not apply:
  - Roulette: RO's client `HANDOFF_LEAD_MS` 300 ms = 6 ticks.
  - Craps: local anchors, so `roll_time` only matters for a first sight.
  - Cards: a publish-early lead would show card faces about 200 ms early and move the BJ busy and baccarat settle
    timers. The first-seen anchor covers lateness instead.

### Tests
- The 4 anim client tests use `SmoothClock.Test.freezeLevelMs`. Dice keeps a `setClock` helper; Plinko's is a stub. The
  deprecated forwarders `AnimClock.freezeForTest/unfreezeForTest` and `MotionClock.testOverride` are deleted; nothing
  else used them.
- `NetLag` delays now run on `SmoothClock.wallMs`.
- New client lane **SYW**, `gametest/core/SyncWiringClientGameTests`, registered:
  - Covers plinko, wheel, slots, craps, roulette, cards (Blackjack) and PvP dice.
  - Each is played like a player, under NetLag(300, 150, 1000) plus a +1 s and a −1 s jump mid-animation, on a
    tick-locked wall.
  - Per frame, the pose may move ≤ what the game's own model moves in 1.15 frame times. The final pose must equal the
    server result, and t must never step back.
  - Shared-clock games may show the one +1 s SmoothClock snap.
  - Probes: `PlinkoScreen/WheelScreen.poseProbe` (via `ExtrasSyncProbe`), `RouletteScreen.poseProbe/ballAtProbe`,
    `Baccarat/BlackjackScreen.dealProbe`, `SpinClock.lastT`.
- Unit test `LateFlightTest`.

## Verified (26.3 only)
- `-Pmc=26.3 compileJava compileClientJava compileGametestJava`: OK.
- `-Pmc=26.3 test` over `core.anim.*`, `games.{roulette,slots,craps,extras,blackjack,baccarat}.*` and RawInputGuardTest:
  808 tests, 0 failures.
- `-Pmc=26.3 runGameTest`, all green:
  - `extras`: 12
  - `duel_game_tests`: 4
  - `slots`: 21
  - `sync_game_tests`: 2
  - `comps_token`: 15
  - `real_flow_min_bet`: 3
- Combined client run (entrypoints swapped, then reverted), `BURMALDAHOLIC_CGT=SYW,SY,CM,DD,RO,PL`:
  - SY, CM, DD, RO and PL all **passed**.
  - SYW passed 13 of 14 sub-runs:
    - plinko, wheel, craps and roulette: 0 frames over the bound, both jumps;
    - cards: 4/4 cards flew and landed, the server published 4;
    - dice: the one allowed snap on +1 s;
    - slots +1 s: OK.
  - Only **slots −1 s** (the second spin on the same screen) failed: 4 reel-frames over. That was a probe artifact. The
    test compared the last sampler's frame with the pre-roll's `preRollTop`, which only the draw refreshes, so it was
    stale at the press. The probe now samples the pre-roll model at the frame's wall and starts a new comparison at the
    press. That fix is **not yet run**, because I reached the client-run cap: 4 runs used, all with SYW work in
    progress.

## Not verified
- Lane SYW after the slots probe fix. **Tester:** run `BURMALDAHOLIC_CGT=SYW` (entrypoint SyncWiringClientGameTests).
- In-world card boards' first-seen anchors: checked by code only (the screens are covered by SYW cards).
- Baccarat on a real flow under NetLag: only Blackjack is driven; the shared `DealAnchors` code is the same.
- Full `build` / full client suite (coordinator).

## Risks / notes
- The plinko/wheel busy gate is now 200 ms longer, from the lead. `ExtrasRevealGameTests` waits `+ SyncLead.X`.
- On a −1 s jump, shared-clock games run up to 2× slower for a few seconds, by SY's design. The lane resets the
  SmoothClock epoch before each run so jumps don't cancel out.
