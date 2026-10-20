# SY: network sync kit (SY-SPEC §6 step 1), handoff

Branch `p2/sy` (from feature/v020-table-ui @581d74cb). It is the kit only: no game files, no CasinoTableScreen or V2TableScreen, and none of the 4 anim client test files.

## API for game owners

### Shared clock (client): `client/anim/SmoothClock`
- `nowMs(pt)` / `nowMs()` returns the smoothed server level time in ms.
  - It is monotonic within an `epoch()`.
  - It corrects drift by ±10 % speed, and hard-snaps forward only when more than 500 ms behind.
  - It runs at 0.5× when more than 500 ms ahead.
  - It stops while paused.
- `epoch()` is bumped on a new level, a dimension change, a rejoin, or no level. Anchors from an older epoch are stale.
- `wallMs()` is the wall clock on the same test source. Use it for actor anchors.
- `observe(serverTick)` takes extra observations. `ClientTableCache` already feeds it every state packet's `game_time`, and `TableClock.sync` does too.
- `AnimClock.levelMs/sharedMs`, `MotionClock.levelMs/msSince` and `TableClock.nowTicks/msSince` all delegate to it, with unchanged signatures. `TableClock.sync` now only observes; it no longer resets per packet.
- Test hooks in `SmoothClock.Test`:
  - `freezeLevelMs(ms|NaN)`
  - `source(TimeSource)`
  - `jump(ms)`
  - `reset()`
- `AnimClock.freezeForTest/unfreezeForTest` and `MotionClock.testOverride` are `@Deprecated` and forward to these hooks. They stay through v0.2.0. Switch to `SmoothClock.Test.freezeLevelMs` when you wire your game.

### Pure core: `core/anim/sync`
- **`ResultAnchor`**
  - `of(startTick, arrivalWallMs[, sharedAtArrivalMs, epoch])` builds a spectator anchor: `t = sharedNow − startTick·50`.
  - `asActor([handoffT])` builds the actor anchor: `t = handoffT + (nowWall − arrivalWall)`.
  - `tMs(nowWallMs, sharedNowMs)` is monotonic.
  - `lateJoin(durationMs)` means draw only the settled state.
  - It is a class, not a record, because of the monotonic `lastT`.
- **`AnchorSlot.obtain(epoch, resultSeq, supplier)`** keeps one anchor per result key.
- **`WaitLoop<P>`** (`pose`, `velocity`, `nextExit`), **`HandoffSolver<P,R>`** → **`Track<P>`** (`sample(τ)`, `durationMs()`), and **`Handoff<P,R>`**:
  - `arrive(result, tArrive, minMs)` counts only the first call.
  - `sample(t)`, `done(t)`, `handoffT()`, `endT()`.
  - The solver must start at pose0/vel0 (C0 and C1) and end exactly on the result.
- **`SyncLead`**: SLOTS 4, ROULETTE 6, CRAPS 5, CARDS 4, WHEEL 4, PLINKO 4, PVP_DICE 4 (on top of SHAKE_LEAD).
  - `startTick(gameTime(), SyncLead.X)` gives the start tick.
  - Each value can be overridden with the system property `burmaldaholic.syncLead.<game>`.
- **`SeqGate`**: the per-table gate; the constant keys are `SEQ`, `EPOCH` and `GAME_TIME`.

### Server
- `CasinoTableBlockEntity.sendStateTo` now sends `syncedState(viewer)`. That is `writeClientState` plus:
  - `sync_seq`: +1 per write;
  - `sync_epoch`: random per load;
  - `game_time`, when the game did not write it.
- `syncEpoch()` is exposed for tests.

### Client gate
`ClientTableCache` drops a packet in the same epoch with `seq ≤ last`. A new epoch resets that table's cache, and packets without a seq pass. `lastSeq(pos)` is exposed for tests.

### Tests for owners
- **`HandoffHarness.run(loop, solver, result, arrivalMs[], frameMs[, minMs, dist])`** returns `maxFrameDelta`, `finalPose` and `rebuilds`. Each game writes one test with it; `HandoffToyTest` is the example.
- **`gametest/core/NetLag`** (client GameTests):
  - `install(delayMs, jitterMs, bunchEveryMs)`, `jumpTime(ms)`, `clear()`, `deferredCount()`.
  - It delays both table state packets and time packets. Time packets go through `SetTimeNetLagMixin` (`ClientPacketListener.handleSetTime`, HEAD), and through the `NetLagHook` field, which is null in production.

## Verified (26.3 only, per the owner's 2026-10-20 decision)
- `compileJava compileClientJava compileGametestJava -Pmc=26.3`: OK. It also passed on 26.2 before 26.2 support was dropped.
- `-Pmc=26.3 test --tests 'dev.nezo.burmaldaholic.core.anim.sync.*' --tests '*RawInputGuardTest'`: 15 tests, 0 failures.
  - SmoothClockCoreTest (6): steady, ±150 ms jitter, an 800 ms stall then a bunch, 15 TPS, a +2 s jump (exactly 1 snap), a −1 s jump (slows down, never rewinds, converges).
  - HandoffToyTest (8): C0 and C1 at tH, the harness bound ≤ 1.15·v·frame under late and bunched arrivals, exact final pose, no rebuilds, anchors, the seq gate, the leads.
- `-Pmc=26.3 runGameTest -Dfabric-api.gametest.filter=sync_game_tests`: "All 2 required tests passed". Blackjack and roulette states carry seq/epoch/game_time, every original state key is kept, and a replaced table gets a new epoch.

## Not verified
- **Client lane `BURMALDAHOLIC_CGT=SY`** (`gametest/core/SyncClientGameTests`, registered): not run. The coordinator's speed-up rule hands the client lane to the tester, and the client slots were saturated. It covers opening a roulette table by using it, the seq gate, the clock staying monotonic and close to level time, NetLag 300/150/1000, ±1 s jumps, and the AnimClock/MotionClock readers agreeing.
- **Server `real_flow_multiplayer` regression**: not run (it was cut by the switch to 26.3).

## Notes and risks
- SpinClock, CrapsScreen, RouletteScreen and the other game files are unchanged. Each owner wires their game per SY-SPEC §3.
- On a −1 s server jump, the clock stays ahead for up to 2 s (the observation window), then runs at 0.5×, then slews at 10 %. Converging fully takes about 6–8 s. This is by spec: the clock never goes back.

## T-SY round 2 (D1-D4 + m1-m4 fixed)
- **D1:** `SyncClientGameTests` does only relative checks on the live wall clock (monotonic, moving, readers agree, NetLag). The absolute tracking and the ±1 s jumps run under a tick-locked `SmoothClock.Test.source`, because client GameTests run lock-step.
- **D2:** `SmoothClock.tick()` advances (or accounts the pause) every client tick.
- **D3:** a change of `msPerTick` re-anchors the window on the newest observation and takes the new nominal speed.
- **D4:** the speed is measured by a least-squares slope over the window when the fit is good (RMS ≤ 120 ms, span ≥ 1 s, ±5 % deadband to nominal), and low-pass filtered (τ 500 ms). The "ahead" slowdown is now a continuous ramp from 0.9 to 0.5 between 500 and 1000 ms ahead. A single step longer than 250 ms never passes the target.
- **m1:** null pre-check before the NetLag lambda.
- **m2:** per-frame advance cache (1 ms).
- **m3:** `ResultAnchor.arrivalWallMs` is a double.
- **m4:** the AnchorSlot epoch is documented.

Verified (26.3):
- **Unit tests:** sync.* plus RawInputGuardTest, 24/24, including the tester's 9 SmoothClockAdversarialTest tests.
- **Client lane** `BURMALDAHOLIC_CGT=SY` (SyncClientGameTests + SyncAdversarialClientGameTests): BUILD SUCCESSFUL, "all adversarial client checks passed".
