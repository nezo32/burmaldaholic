# Bedrock review wave 2: re-review

## Re-review

**Branch:** `worktree-agent-aaf0f54e81d19ae4a` @ `6132b49`. It merges the wave `ff2190b`, the tester fixes `d8e79ac` and the review fixes. I reviewed it read-only in a detached worktree, using `git diff ff2190b..6132b49 -- bedrock`.

**Checks** (run in the worktree's `bedrock/`): `npm ci`, `npm run build`, `npm test` and `npm run lint` all pass. The build produces the `.mcaddon` with 174 files. Vitest ran 89 files and 1 403 tests, all passing. Lint covers `tsc`, `eslint`, `check-arch`, `check-strings` and `validate-packs`, and all are OK.

### Verdict: **APPROVE**

The two majors and all seven minors are fixed, and each fix has a test. I found no regression in the money paths. One new minor is listed below. It does not block the merge.

### Findings verified

- **M1 (credits to a closed bankroll)**
  - **Tombstone:** `processClosing` now calls `closeBankrollTo`, which tombstones the bankroll id (`bankroll_closed.<id>` → owner) and pays the owner through `creditById`. `creditById` is offline-safe.
  - **`Economy.transact`:** it removes every leg that targets a closed id before calling `planTransaction`. That function does not require the legs to sum to zero, so the rest of the transaction still commits. After the commit, a net credit goes to the owner. A net debit fails the whole transaction atomically.
  - **Other bankroll calls:** `reserve` and `release` refuse a closed id, and `settleBankroll` routes a late net result to the owner.
  - **Late-credit paths:** I followed every one of them.
    - Bot purse returns (`purses.ts`), both at a normal leave and when m5 forces a bot out.
    - PvP bot refunds, payouts and the rake share (`pvp/service.ts:387`).
    - The chemin de fer rake (`chemmy.ts:786` and `:989`).
    - The poker rake (`poker/index.ts:1048`).
    - `wagers.record` and the `multiplayer.route` fallback. For both of these, a debit to a closed id fails, exactly as it did before against a missing id.
  - **UTH:** `payOutBankroll` tombstones to the banker.
  - **Id reuse:** casino ids come from a monotonic counter (`store.nextId`) and UTH ids are random, so a tombstone can never catch a new bankroll.
- **M2 (drawn bot stacks):** `saveDrawn` now saves the humans' and the bots' stacks from the same play-out, through `drawnHoldings` and `TableBots.setStack`. While the table is live, `holding()` still prefers the live model stack. The end of the hand overwrites the drawn stacks with the real result. A restart therefore returns each stack exactly once.
- **m1:** mid-round, `requestEnd` defers the request. The hook for poker is `inHand`; for chemin de fer it is `phase === 'coup'`. The request is applied at the next safe point: claimants keep their claims and no bot fills that round. `endSession` clears the flag.
- **m2:** questions carry an engine-wide `seq`. `decide` and `stillAsked` filter on both the match id and the seq.
- **m3:** `addBot` refuses a house purse while any seated human is sulking.
- **m4:** the server-wide broadcast needs at least 2 humans in the match.
- **m5:** at the safe point, `samePurse` forces out any MONEY bot whose purse no longer matches the table.
- **m6:** a failed settlement is retried with backoff through the no-reveal branch. The guard stops a retry after a refund or a drop.
- **m7:** the human's escrow is taken before the bot yields. If the yield fails, the escrow is refunded, and `index` is set correctly.
- **Tester fixes:**
  - **Slot Showdown params:** `acceptParams` falls back to decode, and then validate.
  - **Wheel Party:** the lobby timer restarts only when the countdown was running.
  - **Settlement retry and backoff:** covered by m6 above.
  - **Bot quips:** they draw from the bot RNG (`bots.newRng('pvp:chatter')`), not from `mathRng` or the fair RNG.
  - **VIP rounding:** a relative epsilon is added before `floor`.
  - **Plinko and Scratch:** the point tables are snapshotted in `encodeParams`, which both the UI and `newMatch` call. Chain and rematch keep the snapshot. Records without one fall back to the live config.
  - **Slot Showdown records:** `packRecord` and `unpackRecord` are lossless for every `Participant` and `SeatOccupant` field, and full-form records still load.

### Remaining finding

- **minor: tombstones are never pruned** (`bedrock/src/core/economy.ts:340`). Every UTH player-bank session goes through `payOutBankroll` and adds a permanent world dynamic property. Every broken charter adds one as well. Over a long-lived world these accumulate without bound. `resetReservations` scans every dynamic property id at boot, so boot also slows down. Suggested fix: store the tombstones in a single capped or aged map, for example pruned after N MC days, or skip tombstoning a UTH bank that has no bots or late-credit paths.

### Notes

- In chemin de fer, `midRound` covers only the `coup` phase. A "send bots home" during `offer` or `house` still unseats at once. That goes through the existing `onUnseat` bank return, and I found no money issue.
- As the Resolution already says, the Java lang fragment `java/src/main/lang/bots/ru_ru.json` still needs regenerating with `gen_lang.py`.
