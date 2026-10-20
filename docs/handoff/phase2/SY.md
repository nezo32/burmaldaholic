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

## Verified
- `compileJava compileClientJava compileGametestJava`: passed on 26.2 (before the owner dropped 26.2) and on `-Pmc=26.3`.
- `test --tests 'dev.nezo.burmaldaholic.core.anim.sync.*' --tests '*RawInputGuardTest'`: 15 tests, 0 failures.
  - SmoothClockCoreTest scenarios: steady, ±150 ms jitter, an 800 ms stall then a bunch, 15 TPS, a +2 s jump (exactly 1 snap), −1 s, epoch reset, pause.
- Server GameTests, `runGameTest -Dfabric-api.gametest.filter=sync_game_tests`: the 2 SyncGameTests (blackjack and roulette state carry seq, epoch and game_time; a replaced table gets a new epoch) passed on 26.2. The 26.3 result is in the report.
- Client lane `BURMALDAHOLIC_CGT=SY` (SyncClientGameTests): see the report.

## Notes and risks
- SpinClock, CrapsScreen, RouletteScreen and the other game files are unchanged. Each owner wires their game per SY-SPEC §3.
- On a −1 s server jump, the clock stays ahead for up to 2 s (the observation window), then runs at 0.5×, then slews at 10 %. Converging fully takes about 6–8 s. This is by spec: the clock never goes back.
