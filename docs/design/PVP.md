# Burmaldaholic — Player-vs-Player Modes [pvp]

Status: **v1.0 draft, implementation-ready for MUST items**. Owner: game design (PvP).
Audience: Java (Fabric) team, Bedrock (Script API) team, testers, localization.

This file is **normative for the `pvp` module**, like `GAME_DESIGN.md` is for the other modules. It was
written while another designer edits `GAME_DESIGN.md`, `CONFIG.md`, `UI.md`, `STRINGS.md` and
`LOCALIZATION.md`, so **everything PvP lives here** and is merged later (§0.2 says where each part
goes). Until the merge, this file wins over the others for anything with a `pvp` key or id.

Conventions are the ones of `GAME_DESIGN.md` (chips are integers, world-time ticks, 20 t = 1 s,
server-side RNG, "tier max" = VIP max bet §12) and of `LOCALIZATION.md` (key grammar, `%1$s`
placeholders, number-class plurals `.p1/.p21/.p2/.p5`).

---

## 0. Summary

### 0.1 What PvP adds

Players bet **against each other** instead of the house. Their stakes form a **pot**; the pot minus
a small **house cut** (rake, default 3 %) goes to the winner(s). The house never takes the other side,
never shifts odds, and never pays anything it did not collect — so every PvP mode is **fair: each
player's expected value is exactly "minus their share of the rake"** (§3.5).

| Mode | Players | One-liner | Priority |
|------|---------|-----------|----------|
| **Coin Flip Duel** (§4) | 2 | Call it, flip it, and the loser may shout **"Double or nothing!"** to get back to all square | **MUST** |
| **Slot Showdown** (§5) | 2–6 | Everyone spins the same machine type N times; line wins are points; a **HOT symbol** each spin, **KABOOM** halves you, **Ender Pearl swaps** you with the leader, the last place gets an **Underdog Boost** | **MUST** |
| **Wheel Party** (§6) | 2–8 | Buy a slice of the wheel — bigger stake, bigger slice. One shared spin. The pot goes to whoever's slice the pointer lands on | **MUST** |
| **Plinko Battle** (§7) | 2–6 | Everyone drops at once at the same risk; bins are points; underdog boost on the last ball | **MUST** |
| **Scratch Showdown** (§8) | 2–6 | Everyone gets a card; nine cells scratched **together, one at a time**; creepers blow up your best cell, Rabbit's Feet double the card | **MUST** |
| PvP core (§3): challenges, lobbies, escrow, rake, tape persistence, rivalry, win streaks, grudge matches, taunts, rematch, reveal choreography | — | the shared frame all modes use | **MUST** |
| Spectator side bets (§9.1) | spectators | Pari-mutuel "tote" on any chance-only match | NICE |
| Tournaments (§9.2) | 4–32 | Knockout brackets or timed leaderboards, prize pool from entries, trophies | NICE |
| Floating scoreboard (§9.3) | — | In-world hologram above the anchor machine | NICE |
| Coin Series (§10.1) | 2 | Best of 3 / 5 | NICE |
| Jackpot Race (§10.2) | 2–6 | First to hit the target line takes the pot | NICE |
| Wheel Heist (§10.3) | 2–6 | Loot / Steal / Bankrupt / Double / Shield wheel, three rounds | NICE |
| Plinko Bumpers (§10.4) | 2–6 | One bumper per player to push an opponent's ball to the centre | NICE |
| Scratch Poker (§10.5) | 2–4 | Hidden cards, two betting rounds | NICE |
| Dice Duel on the PvP core (§10.6) | 2 | Migrate the existing PvP Dice Duel (§11.5) to this frame | NICE |

### 0.2 Merge plan (for the doc owner, after the Baccarat/UTH edit lands)

| This file | Goes to |
|-----------|---------|
| §1–§10 rules, math, state machines | `GAME_DESIGN.md` new **§22 PvP [pvp]** (module table gets a `pvp` row) |
| §11 advancements | `GAME_DESIGN.md` §19 table (append) |
| §12 sounds and particles | `GAME_DESIGN.md` §22 + `UI.md` §0.1 sounds line |
| §13 config | `CONFIG.md` new `## pvp` section (after `extras`) |
| §4.x/§5.x… "UI" subsections and §3.11 | `UI.md` new **§14 PvP** |
| §15 strings | `STRINGS.md` new `## pvp` section; units into §core Units; advancements into §advancements; config labels into §config; subtitles into §sounds |
| §15.0 glossary | `LOCALIZATION.md` §6 new table "6.7 PvP"; add `pvp` to the module list in §1.1 |
| §16 tests | `GAME_DESIGN.md` §22 "Test method" |

**Proposed amendments to existing text** (need the owner's sign-off; implement PvP as written here):

1. **§14 streak** — "Update on each settled house-banked **or PvP** wager" becomes "on each settled
   house-banked wager, and on PvP wagers only when `pvp.affectsStreak` = true (default **false**)".
   Reason: two accounts ping-ponging 10-chip PvP matches could pump a +10 streak for almost nothing
   and then enjoy the lucky re-draw on house games (§3.12). Dice Duel PvP follows the same key.
2. **§4.1** play-out list gains: "PvP modes: the whole match (the *tape*) is drawn at START and
   persisted before any reveal; STARTED matches are settled from the tape, LOBBY entries are
   refunded" (§3.6).
3. **§13.3 Golden Hour** "all except PvP poker and PvP dice" → "all except PvP (poker, dice and every
   `pvp` mode)".
4. **UI.md §2** Casino Menu "Challenges" tab content becomes the PvP hub (§3.11.2); the key
   `gui.burmaldaholic.menu.challenges` is unchanged.
5. **§18.2** owned casinos: "Owner **cannot** play at their own tables" also covers PvP matches
   anchored at their machines; PvP rake at owned machines goes to the bankroll (§3.4).

---

## 1. Design pillars

1. **Everybody watches the same moment.** Every mode ends in one shared reveal that the whole room
   sees (titles, sounds, particles). PvP is a *social event*, not a private form.
2. **Short.** A match lasts 5–40 s. Rematch is one button.
3. **Symmetric chaos.** Swingy mechanics (KABOOM, SWAP, Underdog Boost, creepers, Rabbit's Feet,
   near-misses) make comebacks common and stories memorable — and because each rule treats all
   players identically, none of them changes anyone's expected value (§3.5).
4. **No decisions inside a MUST match.** All MUST modes are pure chance after START: the full outcome
   is drawn at once, so leaving, disconnecting or a server stop can never change a result. The only
   decision is *between* matches (Double or nothing, Rematch). Decision-heavy modes are NICE.
5. **Rivalry gives meaning.** Head-to-head records, win-streak call-outs, grudge matches and taunts
   turn 50/50 coin flips into feuds.
6. **Both editions look alike.** Java gets custom screens; Bedrock gets the same drama through
   titles, action bar, sounds and particles, with forms only for set-up and results (§3.11).

---

## 2. Scope, priorities and team split

### 2.1 MUST for v0.1.0

- PvP core (§3) complete, including rivalry records, win-streak announcements, grudge banner,
  taunts, rematch, the "Final Reveal" choreography, and the PvP hub in the Casino Menu.
- The five MUST modes (§4–§8) with their MUST advancements (§11) and vanilla sounds (§12).
- Everything is chips only: **no pawn stakes, no Soul Wager in PvP** (pawn stakes are house-only,
  §4.3 m4).

### 2.2 NICE (later)

Side bets, tournaments, floating scoreboard, custom PvP sound events, owner per-machine PvP
settings, and the NICE modes of §10. They are specified far enough that their keys and strings are
reserved and nothing in MUST has to change to add them.

### 2.3 Four developers per edition

The core contract (§3.14) is written first — it is small (interfaces + pure functions) — so all four
can start at once against stubs.

| Dev | Owns | Depends on | Estimate per edition |
|-----|------|------------|----------------------|
| **A — core** | §3: match registry, invite + lobby flows, escrow/settle/rake (one atomic transaction each), tape persistence and load-time settle, eligibility check, rivalry + win streak + grudge, rematch, taunts, Final Reveal kit, PvP hub, Java lobby screen / Bedrock lobby forms | economy `batch`/`transact`, `ctx.tables`, HUD | 4–5 h |
| **B — coin + wheel** | §4 Coin Flip Duel (Lucky Coin on player, Double or nothing chain), §6 Wheel Party (wheel block buttons, arcs, spin) | A's contract | 3–4 h |
| **C — slots** | §5 Slot Showdown (reuses the solo `slots` line evaluator; adds scoring rules and the multi-player reel view) | A's contract, slots evaluator | 3–4 h |
| **D — plinko + scratch** | §7 Plinko Battle (reuses the solo path generator), §8 Scratch Showdown (new card logic) | A's contract | 3–4 h |

Advancements: each dev adds the triggers of their mode (Java criteria / Bedrock achievement hook);
dev A adds `pvp_first_win`, `pvp_rampage`, `pvp_revenge`, `pvp_full_house`, `pvp_all_in`.

---

## 3. PvP core [pvp]

### 3.1 Terms

| Term | Meaning |
|------|---------|
| **Match** | One PvP game instance: mode, parameters, participants, stakes, tape, result. Id = 8-char base-36 string. |
| **Duel** | A 2-player match created by a **challenge** (invite) to one named player. |
| **Lobby** | A 2–N player match that others join; anchored at a machine block (slots, wheel, plinko) or at the host's position (scratch). |
| **Host** | The player who created the match (always a participant). |
| **Anchor** | The block (or, for anchor-less lobbies, the host's position at creation) that defines distance checks, the owned-casino link and where effects play. |
| **Stake / entry** | Chips a participant puts in. Equal for everyone except Wheel Party. |
| **Pot** | Σ stakes. |
| **Rake / house cut** | The house's share of the pot (§3.4). |
| **Tape** | Every random value of the match, drawn once at START (§3.6). |
| **Seat order** | A uniformly random permutation of the participants drawn into the tape; used for every tie-break that needs an order. |

### 3.2 Eligibility (checked at invite, accept, lobby create, join, top-up, rematch and Double or nothing)

A player may take part only if **all** hold (first failing rule gives the error shown):

| # | Rule | Error key |
|---|------|-----------|
| 1 | Casino mode on, `pvp.enabled`, the mode's `pvp.<mode>.enabled` | `gui.burmaldaholic.error.casino_off` / `gui.burmaldaholic.error.disabled` |
| 2 | Not in Spectator game mode | `gui.burmaldaholic.pvp.error.spectator` |
| 3 | No outstanding debt at all (owed = 0), not in default, no Asset Freeze (§5.6, §5.8 rule 2) | `gui.burmaldaholic.pvp.error.debt` (for the other player: `…error.target_unavailable`, which does not reveal why) |
| 4 | Not busy: not seated at any table, not in another PvP match or lobby, not in a solo round | `gui.burmaldaholic.error.busy` / `…pvp.error.busy_target` |
| 5 | Same dimension as the anchor and within `pvp.joinRadius` (16) blocks of it (duel: of the challenger) | `…pvp.error.other_dimension` / `…pvp.error.too_far` |
| 6 | Stake ≥ `pvp.minStake` (10) and ≤ the player's **tier max** (and ≤ the lobby cap for Wheel Party) | `…pvp.error.stake_min` / `gui.burmaldaholic.error.bet_too_high` / `…pvp.error.over_target_max` |
| 7 | Balance ≥ stake | `gui.burmaldaholic.error.insufficient_funds` / `…pvp.error.cant_afford_target` |
| 8 | Not the same player as another participant | `…pvp.error.self` |
| 9 | Machine rules of the anchor: machine VIP requirement (Netherite slots: Gold VIP), owned machine is linked and open (not "inactive" or owner-closed; an **insolvent** casino still allows PvP because the bankroll pays nothing), and the player is **not the owner** | `gui.burmaldaholic.error.vip_required` / `gui.burmaldaholic.error.table_closed` / `…pvp.error.owner` |
| 10 | Target accepts PvP challenges (per-player setting `Accept PvP challenges`, default on) | `…pvp.error.no_invites` |
| 11 | Target has not declined a challenge from this challenger in the last `pvp.declineCooldownTicks` (600) | `…pvp.error.cooldown_target` |
| 12 | Challenger has fewer than `pvp.maxPendingInvites` (1) outgoing invites | `…pvp.error.pending` |

Rules 3–7 are re-checked at the moment money moves (accept / join / rematch / Double or nothing).
Distance is **not** checked after START: walking away never forfeits a match (§3.6).

### 3.3 Matchmaking

#### 3.3.1 Duels (Coin Flip Duel; Scratch Showdown against one named player)

- **Start points:** use the **Lucky Coin on a player** (Java `UseEntityCallback`; Bedrock
  `world.beforeEvents.playerInteractWithEntity` with the coin in hand; fallback: coin used on air
  while `getEntitiesFromViewDirection` finds a player within 6 blocks) → Coin Flip Duel set-up;
  Casino Menu → Challenges → *New match…* (any duel mode, pick an opponent from players within
  `pvp.joinRadius`); Java command `/casino pvp challenge <player> <mode> <amount> [heads|tails]`.
- The challenger fills the set-up (stake, side) → eligibility for both → **invite** sent. Nothing is
  debited yet.
- Target is told by chat + action bar + sound (`pvp_challenge`, §12). Java chat line carries clickable
  `[Accept]` / `[Decline]` components (`ClickEvent.RunCommand("/casino pvp accept <id>")`). Bedrock:
  opening the Casino Menu while an invite is pending shows the invite form first (no surprise form
  is pushed onto a player who may be fighting); the chat line says so.
- Invite states: `PENDING` → `ACCEPTED` | `DECLINED` | `EXPIRED` (after `pvp.inviteTimeoutTicks`, 600 =
  30 s) | `WITHDRAWN` (challenger cancels, leaves the radius before the answer, disconnects, or casino
  mode turns off).
- **Accept** = one atomic transaction debiting both stakes (§3.4). If it fails (someone spent the
  chips), both are told and the invite dies.

#### 3.3.2 Lobbies (Slot Showdown, Plinko Battle, Wheel Party; Scratch Showdown with "anyone nearby")

- **Host:** uses the machine → the machine's normal form/screen has an extra entry *Start a …* →
  set-up (entry, spins/balls/risk, cap) → host's entry is escrowed immediately → lobby opens.
  Scratch Showdown lobbies are created from the hub and anchored at the host's position.
- Announcement to every player within `pvp.joinRadius` of the anchor (chat + sound).
- **Join:** use a machine of the same kind within `pvp.<mode>.linkRadius` (8) of the anchor — or the
  anchor itself — and pick *Join …*; or hub → *Open lobbies nearby*. The entry is escrowed at join.
  Several players may join through the same machine (the match is virtual).
- **Leave** before START: the entry is refunded at once.
- **Start** when any of: host presses *Start* with ≥ 2 participants; the lobby is full; the lobby timer
  `pvp.lobbyTimeoutTicks` (1800 = 90 s from creation) ends with ≥ 2 participants. With < 2 at the
  timer, or if the host leaves before anyone joined, the lobby is **cancelled** and refunded. If the
  host leaves a lobby with others in it, the earliest joiner becomes host.
- Anchor broken, casino mode off, owned machine closed/unlinked before START → cancelled, refunded.

### 3.4 Money: escrow, settlement, rake

**Escrow.** Stakes move from balances in one atomic call — Java
`eco.batch(server).debit(player(a), s)…credit(HOUSE, pot).commit(Transaction.bet("pvp"))`; Bedrock
`economy.transact([{account: a, delta: -s}, …, {account: 'bank', delta: pot}], reason)`. The bank is
used as the escrow holder (it is unbounded, so parking the pot there is equivalent to a separate
account); the match record stores what is owed back. Nothing else may touch it.

**Rake** (integer only, no floating point, same in both editions):
```
rake = floor((pot × pvp.rakeBasisPoints + 5000) / 10000)       // round half up; 300 bp = 3 %
W    = pot − rake                                              // what the players get back
```
Owned casino: if the anchor is linked to a charter at START (recorded in the match), the rake is
credited to that **bankroll** in the settlement transaction (stats line "PvP house cut collected");
otherwise it stays in the bank (sink, §3.5 of GAME_DESIGN). The bankroll is never debited and never
reserved for PvP. The owner can't take part (§3.2 rule 9).

**Payout.** Winners are determined by the mode's pure `score()` (§3.14). With k tied winners each gets
`floor(W / k)`; the `W mod k` odd chips go one each to the tied winners earliest in **seat order**
(random, from the tape). Settlement = one atomic transaction: `bank −W−rake`, winners `+…`, bankroll
`+rake` (if owned). Winners who are offline are credited through the offline deposit path (loan
garnishment and the balance cap apply exactly as for any credit).

**Not affected by house mechanics:** no streak re-draw (§14 is never consulted in `pvp`), no Golden
Hour bonus, no VIP cashback (cashback is on house theoretical loss only), no progressive jackpot
contribution or award, no chaos events triggered by PvP results (specials only score; a KABOOM
spawns nothing), no big-win `lucky_buff` roll.

**Counted:** each participant's stake counts as *wagered* once per match for VIP lifetime wagered
(`pvp.countsTowardVip`, default true) and for the `wager` contract; other contracts (`spin_slots`, …)
do not count PvP spins. Statistics: lifetime PvP wins/losses/net per player. The big-win broadcast
(`core.announceBigWins`) uses the PvP broadcast message instead (§3.11.5).

### 3.5 Fairness (the proof every mode relies on)

Let the participants be 1…N with stakes s_i, pot P = Σ s_i, rake R, W = P − R.

**Lemma 1 (exchangeable modes, equal stakes s).** If the joint distribution of the tape is invariant
under permuting the participants (all draws i.i.d. per participant plus a uniformly random seat
order) and `score()` treats participants only through their tape data and seat position, then every
participant has the same distribution of payout. Since Σ payouts = W exactly (ties and odd chips
included), each participant's expected payout is W/N and

```
EV_i = W/N − s = (Ns − R)/N − s = −R/N.
```
Every rule in this file that looks "unfair in the moment" — Underdog Boost, KABOOM, Pearl SWAP,
Time Warp, creepers, Rabbit's Feet, odd-chip order — is a symmetric function of the state, so
Lemma 1 holds for Coin Flip Duel, Slot Showdown, Plinko Battle and Scratch Showdown.

**Lemma 2 (proportional mode, Wheel Party).** u is uniform on the integers `[0, P)`; participant i owns
`[C_{i−1}, C_i)` with `C_i − C_{i−1} = s_i`. So `Pr(i wins) = s_i / P` exactly, and
```
EV_i = (s_i / P) · W − s_i = −s_i · R / P        (the rake is shared pro rata)
```
**Lemma 3 (Double or nothing).** Each flip of the chain is a fresh fair 50/50 flip with equal stakes,
so each flip has EV −R_k/2 for both players whatever the history; decisions to continue or stop do
not change any flip's EV (a sum of fair bets is fair: optional stopping cannot create an edge).

**House take.** The house's take is exactly R per match, ≈ `pvp.rakeBasisPoints` of every pot, and
nothing else. With the default 300 bp a player pays on average 3 % of what they stake, which is more
than the cheapest house game (coin flip, 2 %) — so PvP is never the cheapest way to farm VIP
wagered (§3.12).

Examples (defaults): Coin Flip Duel 100 v 100 → P 200, R 6, winner +94, EV −3 each. Slot Showdown
4 × 100 → P 400, R 12, winner +288, EV −3 each. Wheel Party 50/150/800 → P 1000, R 30, W 970;
win chances 5 % / 15 % / 80 %; EV −1.5 / −4.5 / −24 (sum −30 ✓).

### 3.6 Match state machine, persistence and play-out

```
          ┌──────────── duel ────────────┐
INVITED ──accept (escrow both)──────────▶ STARTING
   │ decline / timeout / withdraw → CLOSED (nothing was escrowed)
LOBBY (entries escrowed) ──start rule §3.3.2──▶ STARTING
   │ cancel (<2 at timeout, anchor gone, casino off, host alone leaves) → CANCELLED (refund all)
STARTING: re-check rules 1, 3, 7 for everyone whose stake is not yet escrowed; draw the TAPE
          (seat order + every random value of the mode); persist {state: DRAWN, tape}
          ─────────────────────────────▶ REVEAL
REVEAL:   the mode's timeline plays (§3.11.4); no money moves; leaving, disconnecting,
          walking away and anchor breakage change nothing
          ── timeline ends │ casino mode off │ server stop │ load after crash ──▶ SETTLE
SETTLE:   one atomic transaction (payouts + rake); rivalry, win streak, statistics,
          advancements; persist {state: SETTLED}  ─▶ RESULT (rematch window §3.10) ─▶ CLOSED
```

- **Persistence.** Java: `SavedData` `burmaldaholic_pvp` (matches, invites are not saved). Bedrock:
  world dynamic property `burmaldaholic:pvp:<id>` (JSON) + an index property `burmaldaholic:pvp_index`.
  The largest tape (Slot Showdown, 6 players × 10 spins × 9 cells + 10 hot symbols) is < 700 chars.
  The DRAWN record is written **before the first reveal packet/title** (Bedrock: synchronously in
  the same tick; Java: mark dirty and, for a `DRAWN` write, call the level's save-data flush if the
  API allows, else accept the §4.1 crash caveat).
- **Tape confidentiality.** Java never sends unrevealed parts of the tape to clients (the screen
  receives each step as it is revealed). Bedrock is server-only anyway.
- **Play-out on load / stop.** On world load: `LOBBY` → refund every entry (offline-safe, message on
  join `msg.burmaldaholic.pvp.lobby.refunded`); `DRAWN` → settle from the tape (offline-safe, message on
  join `msg.burmaldaholic.pvp.result.offline`); `SETTLED` → nothing. Java may instead settle `DRAWN`
  matches at the stop, before the world saves (same result).
- **Casino mode off mid-match:** invites withdrawn; lobbies refunded; `DRAWN` matches settle at once
  (animation skipped, `msg.burmaldaholic.pvp.result.casino_off`). This follows §4.1: drawn rounds are
  played out, undrawn bets refunded.
- **Disconnect / leave / walk away during REVEAL:** the match keeps playing; the leaver gets
  `msg.burmaldaholic.pvp.result.away` (if still online) and the result later.
- **Per mode play-out:**

| Mode | Drawn at | Choices after START | Disconnect / stop / casino off |
|------|----------|---------------------|--------------------------------|
| Coin Flip Duel | accept (one flip per link) | none inside a flip; Double or nothing is a **new** match | settle the flip; a pending Double-or-nothing offer lapses (= walk away) |
| Slot Showdown | START (all spins, hot symbols, seat order) | *Spin!* only speeds up the timeline | settle from tape |
| Wheel Party | START (= "No more bets") | none | settle from tape |
| Plinko Battle | START (all paths, seat order) | *Drop!* only speeds up | settle from tape |
| Scratch Showdown | START (all cells, seat order) | *Scratch!* only speeds up | settle from tape |

### 3.7 Timers (defaults)

| Timer | Key | Default |
|-------|-----|---------|
| Invite answer | `pvp.inviteTimeoutTicks` | 600 (30 s) |
| Lobby fill | `pvp.lobbyTimeoutTicks` | 1800 (90 s) |
| Human decision (Double or nothing offer/answer, rematch) | `pvp.decisionTimeoutTicks` | 300 (15 s — UI.md §13 minimum) |
| Pre-match countdown ("3… 2… 1…") | `pvp.countdownTicks` | 60 |
| Settled-match history kept (for rematch/records) | `pvp.historyTicks` | 6000 |

Pacing timers (auto-spin, auto-drop, auto-scratch) are per mode; they are not decisions, so they
may be shorter than 15 s.

### 3.8 Rivalry, win streaks, grudge matches

**Head-to-head record** per ordered pair (A→B): wins, losses, net chips, and the current *run*
(+n = A won the last n against B, −n = lost the last n). Stored per player (Java attachment;
Bedrock player property `burmaldaholic:pvp_record`, JSON, the 50 most recent rivals kept, LRU).
Updated at SETTLE: in a match with a unique winner, the winner gets a win against each loser and
each loser a loss against the winner (losers do not score against each other). Split winners score
nothing against each other and a win against each loser. Records are shown in invites, lobbies and
the hub (**Nemesis** = the rival with the worst net for you, if ≤ −3 matches).

**PvP win streak** per player (separate from §14 Lucky streak, which PvP never touches): +1 on a
match win (a split win counts), reset to 0 on a loss. Announced at the thresholds
`pvp.streakAnnounce` = [3, 5, 10] (three message tiers: heating / rampage / legendary) to players
within `pvp.announceRadius` (32), and server-wide for the top tier. A streak ≥ the first threshold
that is broken gives the "streak broken" call-out naming the breaker.

**Grudge match.** In a 2-player match, if one side's run against the other is ≤ −`pvp.grudgeLosses`
(3), the match is a **GRUDGE MATCH**: banner title for both, ravager roar, the lobby/invite shows it.
Winning it as the underdog gives `pvp_revenge`. It changes no money (a rake waiver would let
colluding accounts dodge the rake; see §3.12).

### 3.9 Taunts

- 8 fixed, translated lines (§15.4): *GG, well played · Good luck. You'll need it · No way! · It's
  rigged! · Again. Right now. · Nerves of steel · Say bye to your chips · Respect*.
- Only participants, from invite acceptance until the rematch window ends. Delivered to participants
  and players within `pvp.announceRadius` as chat `msg.burmaldaholic.pvp.taunt.say` plus a villager
  "yes" (friendly lines) or "no" (cheeky lines) sound at the sender.
- Cooldown `pvp.taunts.cooldownTicks` (100) per player, max `pvp.taunts.maxPerMatch` (5). Muted
  entirely by `pvp.taunts.enabled` = false. No free text (no moderation burden, fully translated).

### 3.10 Rematch

After SETTLE every participant sees **Rematch** for `pvp.decisionTimeoutTicks`. Duel: both must press.
Lobby: the rematch starts when all have answered or the timer ends, with every participant who
pressed (≥ 2 needed), same mode, same parameters, a fresh tape. Stakes are escrowed at the moment the
rematch starts (eligibility re-checked; anyone who can't cover it is dropped with the error). The
grudge flag is recomputed. Wheel Party rematch re-opens the lobby with everyone's previous stake
pre-filled (they may change it).

### 3.11 UI and drama — shared parts

#### 3.11.1 Edition split

| Need | Java (custom screens, server-driven like UI.md §0.2) | Bedrock (forms + HUD channels) |
|------|------------------------------------------------------|--------------------------------|
| Set-up, join, invite answer | Screens (below) | ModalForm / ActionForm / MessageForm |
| Live match | Mode screen; when closed, a one-line **match ticker** HUD overlay (top-centre, under the boss bar) | **No forms during REVEAL.** Action bar ticker (every 2–4 t while animating, else on change); titles/subtitles for big moments; chat for the round log |
| Final reveal | Screen banner sequence + titles | Titles/subtitles sequence |
| Result | Result panel on the screen: ranking, pot, rake, payout, [Rematch] [Taunt] [Close] | ActionForm "Result": body ranking; buttons Rematch · Taunt… · Close |
| Spectators | Players within `pvp.announceRadius` get the ticker overlay line and titles for the Final Reveal winner only | Same, via action bar + final title |

**Bedrock title channel vs HUD.** The HUD uses sentinel titles (UI.md §1). While a PvP title is on
screen for a player (`fadeIn + stay + fadeOut`), core must **pause HUD title refreshes** for that
player (queue them). Dev A adds `ctx.hud.holdTitle(p, ticks)`.

**Bedrock form rule for invites.** Never push a form unasked: invites arrive by chat/action
bar/sound; the form appears when the player opens the Casino Menu (§3.3.1). Result forms are shown
at the end of a match the player took part in (they expect it); `UserBusy` retry as UI.md §0.3.

**Russian length.** All Java widths use `max(minWidth, textWidth + 8)` and wrap (UI.md §0.1). Bedrock
buttons were checked to be ≤ 24 RU characters (§15 notes the longest). Action-bar lines are ≤ 44 EN
characters so RU (×1.45) stays ≤ 64.

#### 3.11.2 PvP hub (Casino Menu → Challenges)

Java: the existing *Challenges* tab becomes a 3-part panel (256 × 200):
```
 Your record: 12 wins · 9 losses · net +340        Nemesis: Alex (1–5)
 ─ Pending ───────────────────────────────────────────────────────────
  Alex · Coin Flip Duel · 200         [Accept] [Decline]      ⏱ 22
 ─ Open lobbies nearby ───────────────────────────────────────────────
  Slot Showdown · Golden Reels · 100 · 3/6                     [Join]
 ─────────────────────────────────────────────────────────────────────
 [New match…]  [Head-to-head]  [Dice Duel]  (NICE: [Tournaments] [Side bets])
```
Bedrock ActionForm "Challenges": body = record line (+ nemesis line); buttons: one *Accept: <name> ·
<game>* and one *Decline: <name>* per pending invite (max 2 invites shown, newest first) · *Open
lobbies nearby (n)* · *New match…* · *Head-to-head* · *Dice Duel* (existing flow) · Back.
- *New match…* → ActionForm of duel-capable modes (Coin Flip Duel, Scratch Showdown) + a body line
  explaining that machine modes start at their machines → mode set-up ModalForm (§4.4, §8.4).
- *Head-to-head* → ActionForm body listing up to 10 rivals `Name: W–L · net N`, button Back.
- Settings page gains toggle *Accept PvP challenges* (`gui.burmaldaholic.pvp.settings.invites`).

#### 3.11.3 Lobby view

Java `PvpLobbyScreen` 256 × 200 (compact: same, rows 10 px):
```
 Slot Showdown — Golden Reels                                   ⏱ 58
 Entry 100 · 5 spins each · Pot 300 · House cut 3%
 ─────────────────────────────────────────────────────────────────────
 1 ◆ Alex (host)    ALL-IN       Head-to-head 3–5
 2 ◆ You                          —
 3 ◆ Bob                          Head-to-head 1–0
 4   Empty seat
 ─────────────────────────────────────────────────────────────────────
 [Start] (host only, ≥2)   [Leave lobby]   [Taunt…]           ⛁ 12 500
```
Bedrock: the host gets an ActionForm (body = the same lines; buttons *Start now* (≥ 2) · *Leave
lobby* · *Taunt…*), re-shown by the server when someone joins/leaves (`closeAllForms` + show).
Joiners get no form: the action bar shows `gui.burmaldaholic.pvp.lobby.waiting_bar` every 20 t;
using the anchor again opens the same ActionForm without *Start now*.

#### 3.11.4 The Final Reveal (shared choreography, all multi-player modes)

Each mode's last step (last spin / ball / cell) is **not** revealed per player as it lands. Instead:

| t (ticks) | Everyone in the match | Spectators in radius |
|-----------|----------------------|----------------------|
| 0 | Title "Final results…", drumroll: `note.basedrum` / `block.note_block.basedrum` 8 hits accelerating over 40 t | action bar "Final results…" |
| 40, 60, … | For each place from **last to 2nd**: subtitle `#k: Name — points` + bell note (pitch 0.8 → 1.6 rising); `angry_villager` particles at that player | — |
| +30 (pause) | silence | — |
| end | Title **"NAME WINS!"** + subtitle payout; win jingle + level-up sound; totem particles + firework twinkle at the winner; losers get `burmaldaholic:lose` | title "NAME WINS!" |

Two players: skip straight from the drumroll to the winner. Max length 40 + 5 × 20 + 30 + 40 =
210 t (10.5 s) at 6 players. Ties: the title is "Dead heat!" with the split.

#### 3.11.5 Announcements

- Match start and result: chat to participants; result also to players within
  `pvp.announceRadius`.
- Server-wide chat if the pot ≥ `pvp.announceServerWidePot` (5000) or the winner is on a top-tier
  streak (`msg.burmaldaholic.pvp.result.broadcast`).
- ALL-IN: if a participant's escrow leaves them with balance 0, their name gets the tag **ALL-IN** /
  «ВА-БАНК» everywhere in that match and the countdown adds one extra drum hit per all-in player.

### 3.12 Anti-abuse and collusion

| Risk | Rule |
|------|------|
| Chip laundering between accounts | PvP only moves chips players could already hand over as chip items; the extra route it opens is closed for debtors (rule 3: no PvP while owing — you can't win your loan money off an alt, nor dump it). Every transfer costs ≥ the rake. |
| Streak farming (tiny PvP matches to reach +10 and get lucky re-draws at the house) | PvP never updates the §14 streak (`pvp.affectsStreak` false). |
| VIP wagered farming | Counted (it is real risk), but the rake makes it cost 3 % per chip, more than house coin flip (2 %). Config validation: if `pvp.countsTowardVip` and `pvp.rakeBasisPoints` < 200, log a warning and show it on the admin page (`gui.burmaldaholic.menu.admin.rtp_warning` style line, `…pvp.admin.rake_warning`). |
| Rake dodging via grudge/tournament bonuses | Nothing waives the rake in MUST. Tournament "added prize" (NICE) can only be added by operators. |
| Throwing a match | MUST modes have no choices after START → nothing to throw. Side bets (NICE) only on chance-only modes and never by participants or the anchor's owner. |
| Owner self-dealing | Owner cannot join PvP at own machines; rake to bankroll is visible on the charter stats. |
| Leaving/crashing to avoid a loss | Tape drawn and persisted before any reveal; settle from tape. |
| Invite spam | 1 outgoing invite at a time; 30 s decline cooldown per pair; per-player "accept challenges" toggle. |
| Taunt spam | Fixed lines, cooldown, per-match cap, off switch. |
| Advancement farming with alts | Cosmetic only; `pvp_rampage` needs ≥ 2 distinct opponents, `pvp_full_house` needs 5 distinct opponents in one match, `pvp_revenge` needs a real 3-loss run. |
| Timeouts / stalling | Every wait has a timer; defaults are the safe option (decline, walk away, auto-spin). |

### 3.13 Commands

Java (`/casino pvp …`, permission 0 unless noted): `challenge <player> <mode> <amount> [heads|tails]`,
`accept [id]`, `decline [id]`, `leave`, `record [player]`, `taunt <id>`; ops (level 2): `list`,
`cancel <matchId>` (LOBBY → refund; DRAWN → settle now). Bedrock: players use items/menus; ops use
`/scriptevent burmaldaholic:pvp list|cancel <id>` and Casino Card → Admin → PvP matches.

### 3.14 Mode contract (write this first; all devs code against it)

Pure, engine-free, shared test vectors in both editions:
```
interface PvpMode<P, T> {
  id: 'coin' | 'slots' | 'wheel' | 'plinko' | 'scratch';
  minPlayers: number; maxPlayers: number;
  anchor: 'none' | 'slot_machine' | 'wheel_of_fortune' | 'plinko_machine';
  equalStakes: boolean;                                  // false only for wheel
  validate(params: P): ErrorKey | undefined;             // ranges from §13
  draw(rng: Rng, n: number, params: P): T;               // ALL randomness, incl. seatOrder
  score(tape: T, stakes: number[], params: P): Outcome;  // pure; deterministic
  timeline(tape: T, outcome: Outcome, params: P): Step[];// what to reveal at which tick
}
Outcome { points: number[]; rankOrder: number[]; winners: number[]; events: Event[] }  // events: kaboom, swap, …
```
Core owns everything else (§3.1–§3.13) and exposes to modes: `revealStep(match, step)` (fans out to
screens / action bar / titles), `sound(match, id, who)`, `particles(match, id, who)`.

---

## 4. Coin Flip Duel [pvp.coin] — MUST

### 4.1 Rules

- 2 players. The **challenger** picks the stake S (`pvp.minStake` … own tier max) and a side (Heads
  / Tails); the **target** gets the other side. The invite shows S, the side and the head-to-head.
- On accept both S are escrowed, the flip is drawn (`heads = rng.nextBoolean()`) and persisted, then
  the countdown plays (`pvp.coin.countdownTicks`, 60 t; +20 t per ALL-IN player), then the coin lands.
- Winner receives `W = 2S − rake`. No ties.

### 4.2 Double or nothing (the chain)

After a flip the **loser** (only) sees *Double or nothing — Heads* / *— Tails* / *Walk away* for
`pvp.decisionTimeoutTicks`. The loser calls the side. Then the **winner** sees *Let it ride* / *Take
the money* (same timer; timeout = take the money). If both agree, a new linked flip starts at once.

Chain arithmetic (this is what makes it "double or nothing"): let D = the loser's total deficit in
the chain *before rake* (after the first flip D = S). **The next flip's stake is D for each player.**

| Flip | Stake each | If the chain loser loses again | If the chain loser wins |
|------|-----------|--------------------------------|--------------------------|
| 1 | S | D = S | (roles swap: the other player is the loser, D = S) |
| 2 | S | D = 2S | **ALL SQUARE** — both back to 0 before rake; chain ends |
| 3 | 2S | D = 4S | all square |
| 4 | 4S | D = 8S | all square |
| 5 | 8S | D = 16S (chain over) | all square |

- The chain ends at "all square", when anyone walks away / takes the money, or after
  `pvp.coin.maxDoubles` (4) doubles (so at most 5 flips; the largest stake is 8S).
- A Double or nothing offer is only shown when D ≤ both players' tier max and both balances ≥ D;
  otherwise the button is replaced by a disabled line (Java tooltip / Bedrock body line) with the
  reason (`gui.burmaldaholic.pvp.coin.don_limit` / `…don_unaffordable`).
- Each flip is its own match record (id, tape, rake). The chain is only a link (`chainOf`, `link#`).
  A server stop between flips ends the chain; a stop during a flip settles that flip.

### 4.3 State machine

```
INVITED ─accept→ ESCROW(S,S) → DRAWN(flip) → COUNTDOWN(60t) → LANDED → SETTLE
                → OFFER_LOSER (15 s: DoN Heads | DoN Tails | Walk away | timeout = walk away)
                   → OFFER_WINNER (15 s: Let it ride | Take the money | timeout = take the money)
                      → ESCROW(D,D) → DRAWN … (link+1)          │ else → RESULT (Rematch window) → CLOSED
```

### 4.4 Payout math

Fair coin, equal stakes: EV = −rake/2 per flip per player (Lemma 1, 3). Worked chain (S = 100,
300 bp): flip 1 B wins (pot 200, rake 6: B +94, A −100). Flip 2 (stake 100) B wins (B +94, A −100).
Flip 3 (stake 200, rake 12) B wins (B +188, A −200). Flip 4 (stake 400, rake 24) **A wins**: A +376.
Totals: A = −100 −100 −200 +376 = **−24**; B = 94 + 94 + 188 − 400 = **−24**; rake 6+6+12+24 = 48 ✓.
"All square (minus the house's cut)".

### 4.5 UI

**Java** `CoinDuelScreen` 256 × 180, opens for both duelists on accept:
```
        Alex            vs            You
       [HEADS]                      [TAILS]
        200 ⛁          Pot 400        200 ⛁         Head-to-head 3–5
                   ( coin sprite, 12-frame spin,
                     slows down over the countdown )
 ──────────────────────────────────────────────────────────────────
  "TAILS!  You take 388"                                (ResultBanner)
 [Double or nothing — Heads] [Double or nothing — Tails] [Walk away]      (loser)
 [Let it ride] [Take the money]                                            (winner, on offer)
 [Rematch] [Taunt…] [Close]                                                (after the chain ends)
```
Set-up: the coin used on a player opens a 200 × 140 panel: `BetSelector` (UI.md §0.2), side toggle
Heads | Tails, head-to-head line, [Throw down the gauntlet]. Chain status line under the pot:
"Chain: Alex down 400".

**Bedrock**
1. Set-up ModalForm "Coin Flip Duel": label (opponent, head-to-head, limits), slider + text field
   stake, dropdown Heads/Tails, submit *Throw down the gauntlet*.
2. Invite MessageForm (from the Casino Menu): body invite text + side + record + expiry;
   buttons *Accept* / *Decline*.
3. Countdown: titles "3", "2", "1" (subtitle "Alex: Heads · You: Tails"), `note.hat` pitch 1.0/1.2/1.4;
   then the action bar animates "◐ ◓ ◑ ◒" for 20 t; title **HEADS!** / **TAILS!**, subtitle
   "Alex takes 388".
4. Loser ActionForm: body result + chain status + explanation; buttons *Double or nothing — Heads* ·
   *Double or nothing — Tails* · *Walk away* · *Taunt…*.
5. Winner MessageForm on offer: body `…coin.don_request`; *Let it ride* / *Take the money*.
6. End ActionForm: result lines of the whole chain; *Rematch* · *Taunt…* · *Close*.

Spectators within `pvp.announceRadius`: action bar "Alex vs Bob — 200 on the line" at start and the
landing title's text as an action-bar line.

### 4.6 Advancements, sounds, particles

Advancements: `pvp_all_square` (win a Double-or-nothing flip as the chain loser). Sounds: invite
horn, countdown hats, `burmaldaholic:coin_flip` at launch, win/lose, `random.levelup` for "ALL
SQUARE". Particles: `crit` burst at the coin's landing (in front of the winner), totem at the
winner. See §12.

---

## 5. Slot Showdown [pvp.slots] — MUST

### 5.1 Rules

- 2 … `pvp.slots.maxPlayers` (6). Anchor: a slot machine; the **tier** of the anchor is the match
  tier (Copper Bandit / Golden Reels / Netherite High Roller). Joiners use a machine of the same
  tier within `pvp.slots.linkRadius` (8) blocks of the anchor, or the anchor. Netherite: Gold VIP.
- Host sets: entry E (≥ `pvp.minStake`, ≤ host tier max) and spins N ∈ `pvp.slots.spinChoices`
  ([3, 5, 10]; default 5).
- Every participant gets N spins of that tier's machine, **drawn exactly like a solo spin** (9 cells
  i.i.d. from the tier's symbol weights, §8.1), evaluated on the tier's paylines (1 / 3 / 5) with the
  solo evaluator. No money moves per spin; line wins become **points**.
- Spins happen in **rounds**: round r = everyone's r-th spin, revealed together.

### 5.2 Scoring

Per line, `linePoints` = the solo line multiplier (e.g. Diamond ×50 → 50; 1 leading berry → 2; 2 → 3;
three Wilds → 200/250), with these PvP-specific values for specials:

| Line result | Points | Effect |
|-------------|--------|--------|
| Three Creepers (Copper, Gold) / three TNT (Netherite) | 0 | **KABOOM**: your total *before this spin* is halved (floor). Several KABOOM lines in one spin halve once. |
| Three Ender Pearls | 10 | **SWAP**: after the round's points are added, if you are not the leader, swap totals with the leader. |
| Three Clocks (Netherite) | 50 | **TIME WARP**: your next spin's points ×2 (no effect on the last spin). No Golden Hour. |
| Three Nether Stars (Gold, Netherite) | `pvp.slots.starPoints` (500) | none (no jackpot in PvP) |

**HOT symbol.** Each round the tape holds one regular paying symbol drawn uniformly from {Sweet
Berries, Apple, Golden Carrot, Emerald, Diamond, Redstone Seven}. Every line whose paying combination
is that symbol (including Wild substitutions and berry partials) scores ×2 that round
(`pvp.slots.hotSymbol`).

**Underdog Boost.** Before the final round, every player whose total is **strictly the lowest** (not
all tied) scores ×2 on the final spin (`pvp.slots.underdogBoost`).

**Round resolution order** (all players at once):
1. `spinPoints_i = Σ_lines linePoints × (2 if the line's symbol is HOT)`, then × 2 for TIME WARP pending,
   × 2 for Underdog Boost (multipliers stack; max ×8).
2. For every player with a KABOOM line this spin: `total_i = floor(total_i / 2)`.
3. `total_i += spinPoints_i`.
4. SWAPs in seat order: for each player with a Pearl line who is not *the* leader, swap totals with
   the leader (leader = highest total; among tied leaders the earliest in seat order, excluding the
   swapper).
5. Set TIME WARP pending for players with a Clock line (cleared after use).

**Winner:** highest total; tie-break 1 = more paying lines over the match; tie-break 2 = higher best
single-spin points; still tied → split (§3.4).

### 5.3 State machine and timeline

```
LOBBY → START (tape: seatOrder, hot[1..N], grid[player][1..N]) → for r in 1..N:
   ROUND_WAIT (announce HOT; up to pvp.slots.spinIntervalTicks = 100 t, or until every online
               participant pressed Spin!) → ROUND_SPIN (40 t reels) → ROUND_SCORE (show points,
               KABOOM / SWAP / TIME WARP events 20 t each) 
   before round N: UNDERDOG announcement (20 t)
   round N: reels spin, points hidden → FINAL REVEAL (§3.11.4) → SETTLE → RESULT
```
Typical length at N = 5: 5 × (5 + 2 + 1) s + 6 s ≈ 46 s max; ≈ 25 s when everyone presses Spin!.

### 5.4 Payout math

Equal entries, i.i.d. spins per player, symmetric scoring → Lemma 1: EV = −rake/N. Reference numbers
(tests, §16.3): mean points per spin without HOT/boosts: Copper 0.897598, Golden Reels 2.793399,
Netherite 4.746735; P(KABOOM on a spin) 0.003375 / 0.001535 / 0.001080. Copper matches often end low
(P(a player scores 0 in 5 spins) = 0.7455⁵ ≈ 0.23): that is why HOT and the tie-breaks exist.

### 5.5 UI

**Java** `SlotShowdownScreen` 400 × 240 (compact < 400 px wide: 320 × 220):
```
 SLOT SHOWDOWN · Golden Reels · Spin 3/5 · HOT: Diamond ×2 🔥         Pot 600  ⏱ 4
 ┌ Alex ───────┐ ┌ You ────────┐ ┌ Bob ────────┐
 │ ▣ ▣ ▣  #1   │ │ ▣ ▣ ▣  #2   │ │ ▣ ▣ ▣  #3   │    each panel 124 × 76: name (16 chars),
 │ ▣ ▣ ▣  57   │ │ ▣ ▣ ▣  42   │ │ ▣ ▣ ▣  12   │    3×3 reels at 16 px, total, rank,
 │ ▣ ▣ ▣ +20   │ │ ▣ ▣ ▣  +0   │ │ ▣ ▣ ▣ +50   │    this spin's points, ALL-IN tag
 └─────────────┘ └─────────────┘ └─────────────┘
 ┌ Cid …        (row 2 for players 4–6)
 [SPIN!]  [Taunt…]  [Rules]                                          ⛁ 12 500
```
Compact mode: your panel at 2× size on the left, a ranking list (name · total · last spin) on the
right. KABOOM: panel shakes 10 t, red flash, score counts down. SWAP: two panels' totals fly across
(20 t). HOT symbol cells glow. Final round: totals show "???" until the Final Reveal.
Host set-up: panel on the machine screen: entry `BetSelector`, spins toggle 3 | 5 | 10, [Open lobby].

**Bedrock**
- Machine ActionForm gains *Start a Slot Showdown* and, when a lobby is open within range,
  *Join Showdown: 100 · 2/6*.
- Host set-up ModalForm: label (tier, limits), dropdown spins (3/5/10), slider + text entry,
  submit *Open lobby*. Join: MessageForm "Entry 100 · 5 spins · Pot 300" *Join* / *Cancel*.
- Round: title (fade 0/30/10) "Spin 3/5" + subtitle "HOT: Diamond ×2"; then 40 t action-bar reel
  animation of **your** grid (as the solo flow, UI.md §6), then action bar
  `gui.burmaldaholic.pvp.match.bar` ("3/5 · You 42 (#2) · Alex 57") for 60 t and a one-line chat
  standings log. Using the machine during ROUND_WAIT = *Spin!*. Using it at other times opens a
  read-only ActionForm "Standings" (body: ranking + your last grid; buttons *Taunt…*, *Close*).
- Events: KABOOM → title "KABOOM!" to the victim, chat line to all, explosion sound (no damage);
  SWAP → title "SWAP!" to both, enderman teleport sound; Underdog → subtitle to the boosted player.
- Final: §3.11.4 titles, then the Result ActionForm.

### 5.6 Advancements, sounds, particles

`pvp_phoenix` (win after a KABOOM hit you). Sounds: `slot_spin` per round, HOT = firecharge,
KABOOM = explosion at 0.5 volume, SWAP = enderman teleport, TIME WARP = bell. Particles: `flame` on
HOT cells' machine, `explosion` puff at the victim's machine, `portal` between swapped players'
machines (Java) / at both (Bedrock).

---

## 6. Wheel Party [pvp.wheel] — MUST

### 6.1 Rules

- 2 … `pvp.wheel.maxPlayers` (8). Anchor: a Wheel of Fortune block. Players join by using the anchor
  (only the anchor — it is one wheel).
- Host sets the **cap** C (max total stake per player, `pvp.minStake` … host tier max) and their own
  stake. Each participant stakes `s_i ∈ [pvp.minStake, min(C, own tier max)]` and may **top up** (total
  ≤ that bound) until *No more bets*. Stakes are escrowed at join / top-up.
- The wheel is repainted: each participant owns one coloured **slice** with arc = `s_i / P`
  (colours = the 16 dye colours in seat-join order; slices in join order).
- As soon as 2 players are in, the **spin countdown** `pvp.wheel.countdownTicks` (600 = 30 s) starts
  (it does not reset on joins). Host may *Spin the wheel!* any time with ≥ 2 players. At
  `pvp.wheel.noMoreBetsTicks` (60) before the spin: "No more bets!" — joins and top-ups close. The
  lobby timer of §3.3.2 does not apply (the countdown replaces it); with < 2 players after
  `pvp.lobbyTimeoutTicks` the party is cancelled and refunded.
- START = No more bets: draw `u = uniform integer in [0, P)` (Java `rng.nextLong(P)`, Bedrock
  `Math.floor(Math.random() * P)`; P ≤ 8 × tier max < 2⁵³). The owner of the slice containing u wins
  W = P − rake.

### 6.2 Spin presentation

- Pointer angle at rest: `360° × (u + 0.5) / P` measured from slice 0's start; the wheel turns 3 full
  turns + that angle, ease-out over 100 t.
- **By a hair**: if u is within `ceil(P × 0.02)` of a slice boundary, the message *By a hair!* names the
  neighbouring slice's owner, and Java slows the last 20 t.
- **Underdog**: if the winner's share `s_i / P ≤ 0.10` → "UNDERDOG!" broadcast to the radius + firework
  twinkle + `pvp_underdog`.

### 6.3 State machine

```
LOBBY(host staked) ─2nd player→ COUNTDOWN(600 t; joins/top-ups allowed)
   ─ host "Spin" or countdown hits noMoreBets → NO_MORE_BETS (60 t) → START (draw u, persist)
   → SPIN (100 t) → RESULT_TITLE (40 t) → SETTLE → RESULT (rematch window, stakes pre-filled)
```

### 6.4 Payout math

Lemma 2: `Pr(i) = s_i / P`, `EV_i = −s_i × R / P`. Example (300 bp): stakes 50 / 150 / 800 → P 1000,
R 30; slices [0,50) [50,200) [200,1000); u = 49 → first player, u = 50 → second, u = 199 → second,
u = 200 → third. Each player loses 3 % of their stake on average — the same rate for everyone,
however big or small their slice.

### 6.5 UI

**Java** `WheelPartyScreen` 256 × 220:
```
      ◢■■■■◣           Slice                     Stake    Share
    ◢  wheel ◣         ■ Alex (host)              800     80 %
    ■  140 px  ■  ▼    ■ Bob                      150     15 %
    ◥  slices ◤        ■ You                       50      5 %   UNDERDOG
      ◥■■■■◤           Pot 1 000 · House cut 3 %
 Stake: [BetSelector ≤ cap 1 000]  [Add to my slice]       Spins in 0:18
 [Spin the wheel!] (host)   [Taunt…]   [Leave] (before No more bets)
```
Slices are drawn as arcs of the player's colour with their head icon at the arc centre (arcs < 6°
get no icon). Legend width fits a 16-char name + RU "ТЁМНАЯ ЛОШАДКА" tag in a second line.

**Bedrock**
- Wheel ActionForm gains *Start a Wheel Party* / *Join the party: 3/8* / *Add to my slice*.
- Host ModalForm: slider "Max stake per player" (cap), slider + text "Your stake", submit *Open lobby*.
- Join / top-up ModalForm: label with the legend lines (name — stake (share)), slider + text stake.
- During countdown: action bar `…wheel.bar` ("Pot 1 000 · your slice 5% · spins in 18 s") every 20 t
  to participants; chat on each join/top-up (`…wheel.top_up`).
- Spin: action bar shows the slice owner's **name in their colour** under the pointer, stepping
  through slices (fast → slow, `wheel_tick` per step, 100 t); title "NAME WINS!" + subtitle payout
  and share; then Result ActionForm.

### 6.6 Advancements, sounds, particles

`pvp_underdog`. Sounds: `wheel_tick` per slice passed, bell on stop, firework twinkle on underdog.
Particles: `happy_villager` ring at the wheel on the win, totem at the winner.

---

## 7. Plinko Battle [pvp.plinko] — MUST

### 7.1 Rules

- 2 … `pvp.plinko.maxPlayers` (6). Anchor: a Plinko machine; joiners use any Plinko machine within
  `pvp.plinko.linkRadius` (8) of it.
- Host sets entry E, risk (Low / Medium / High — one risk for everyone) and balls B ∈
  `pvp.plinko.ballChoices` ([1, 3, 5], default 3).
- Each ball is exactly a solo Plinko drop (12 independent left/right flips, bin k ~ Binomial(12, ½)).
- **Points per ball = `round(multiplier × 10)`** of the risk row:

| Bin | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|-----|---|---|---|---|---|---|---|---|---|---|----|----|----|
| Low | 100 | 30 | 16 | 14 | 10 | 10 | 5 | 10 | 10 | 14 | 16 | 30 | 100 |
| Medium | 330 | 110 | 40 | 20 | 10 | 6 | 3 | 6 | 10 | 20 | 40 | 110 | 330 |
| High | 1700 | 240 | 81 | 20 | 6 | 2 | 2 | 2 | 6 | 20 | 81 | 240 | 1700 |

(the rows are derived from the live config `extras.plinko.low/medium/high`, so they follow any change.)
- Rounds: round b = everyone's b-th ball, dropped simultaneously.
- **Underdog Boost** (`pvp.plinko.underdogBoost`): strictly-last before the final ball scores ×2 on it.
- **EDGE!** Bin 0 or 12 → announcement to the radius (fun only).
- Winner: highest total; tie-break = best single ball; then split.

### 7.2 State machine and timeline

```
LOBBY → START (tape: seatOrder, path[player][1..B] as 12-bit masks) → for b in 1..B:
   BALL_WAIT (≤ pvp.plinko.roundIntervalTicks = 80 t or all pressed Drop!) → DROP (48 t: 12 rows × 4 t)
   → BALL_SCORE (40 t, standings) ; before ball B: UNDERDOG (20 t)
   ball B: drops, bins hidden → FINAL REVEAL → SETTLE → RESULT
```

### 7.3 Payout math

Lemma 1 (equal entries, i.i.d. paths, symmetric rules): EV = −rake/N. Mean points per ball (exact,
4096 paths): Low 9.65625, Medium 9.6572265625, High 9.669921875 (= 10 × solo RTP). On High, one edge
ball (1 in 2048 per ball) nearly always wins, which is the point: everyone holds their breath on the
last row.

### 7.4 UI

**Java** `PlinkoBattleScreen` 400 × 240: your board in the centre (180 × 180, balls animated along the
tape path), left column ranking (6 rows: name, total, last bin ×m), right column the other players'
last ball as a small bin strip (13 cells, the landed bin lit). Top bar "PLINKO BATTLE · High · Ball
2/3 · Pot 600". Final ball: all boards drop together, the last row runs at half speed (slow-mo 8 t).
Buttons [Drop!] [Taunt…] [Rules]. Set-up on the machine screen: entry `BetSelector`, risk
Low | Medium | High, balls 1 | 3 | 5.

**Bedrock**: machine ActionForm gains *Start a Plinko Battle* / *Join the battle: 100 · 2/6*. Host
ModalForm: dropdown risk, dropdown balls, slider + text entry. During the match: title "Ball 2/3",
your path on the action bar ("◀ ▶ ▶ ◀ …", 4 t per row, `plinko_peg` per row), then "Bin ×8.1 — 81
points" and the standings bar; chat standings line per ball. Using the machine during BALL_WAIT =
*Drop!*. Final Reveal titles, Result ActionForm.

### 7.5 Advancements, sounds, particles

`pvp_full_house` can be earned here (any mode). Sounds: `plinko_peg` per row, EDGE = firework
twinkle + bell. Particles: `end_rod` at the machine when a ball lands in an edge bin.

---

## 8. Scratch Showdown [pvp.scratch] — MUST

### 8.1 The showdown card

A dedicated PvP card (no item needed; it is virtual). 3 × 3 cells, **each cell i.i.d.** from:

| Symbol | Weight | Value | Effect |
|--------|--------|-------|--------|
| Coal | 30 | 1 | |
| Iron | 25 | 2 | |
| Gold | 18 | 3 | |
| Emerald | 12 | 5 | |
| Diamond | 6 | 10 | |
| Nether Star | 1 | 25 | |
| Creeper | 5 | — | **burns** the highest-value surviving cell revealed so far (it becomes charred, worth 0, no longer counts for trios); if none, fizzles |
| Rabbit's Foot | 3 | — | card multiplier ×2 (two feet ×4; a third does nothing) |
| **Total** | 100 | | |

**Score** = `(Σ surviving cell values, where every symbol with ≥ 3 surviving copies counts double)
× 2^min(feet, 2)`. Tie-break: highest single surviving cell value; then split.

Cells are revealed in a fixed order 1…9 (row-major) — the order matters for creepers, and it is the
same for everyone.

### 8.2 Rules

- 2 … `pvp.scratch.maxPlayers` (6). Created from the hub: *vs one player* (duel invite, §3.3.1) or
  *open lobby* (anchored at the host's position; players within `pvp.joinRadius` join from the hub).
- Entry E (≥ `pvp.minStake`, ≤ tier max). Everyone gets one card from the tape.
- The 9 cells are scratched **for everybody at the same time**, one cell per step: each step waits up
  to `pvp.scratch.revealIntervalTicks` (40 t) or until every online participant pressed *Scratch!*.
  After each step everyone sees everyone's cells and running scores.
- Step 9 is hidden and resolved by the Final Reveal (§3.11.4).

### 8.3 State machine

```
INVITED│LOBBY → START (tape: seatOrder, cells[player][1..9]) → for c in 1..8: STEP_WAIT → REVEAL_CELL (20 t)
   → events (creeper burn / golden foot, 20 t) → STEP 9 hidden → FINAL REVEAL → SETTLE → RESULT
```

### 8.4 Payout math

Lemma 1: EV = −rake/N. Exact distribution (DP over the 8^9 sequences, §16.6): mean score 38.816,
s.d. 21.85, median 34, P(score = 0) = 3.2 × 10⁻⁵, max 1400 (7 stars + 2 feet), P(2 feet) = 0.02816,
P(two players tie before the tie-break) = 0.01997.

Worked card: Coal, Coal, Coal, Diamond, Creeper, Iron, Rabbit's Foot, Emerald, Gold → the Creeper
burns the Diamond; survivors Coal×3 (trio → 6), Iron 2, Emerald 5, Gold 3 = 16; one foot → **32**.

### 8.5 UI

**Java** `ScratchShowdownScreen` 400 × 240: up to 6 mini-cards (3 × 3 cells at 20 px, silver until
scratched) in a 3 × 2 grid, each with name, running score, feet multiplier badge "×2", ALL-IN tag.
Two players: two big cards (40 px cells). The current cell pulses on every card; scratching plays a
per-card silver-dust particle sprite (GUI only). Creeper: the burned cell cracks to black; foot: card
border turns gold. [Scratch!] [Taunt…] [Rules]. Top bar "SCRATCH SHOWDOWN · Cell 5/9 · Pot 400".

**Bedrock**: hub → New match… → *Scratch Showdown* → ModalForm: dropdown opponent ("Open lobby
(anyone nearby)" first, then nearby players), slider + text entry, submit. During the match: after
each cell the action bar shows **your** card compactly ("⛏ C C C | D ✖ I | ? ? ?  = 16") for 40 t, then
the standings bar; chat line per step for events ("Creeper! Bob loses a Diamond"). The Casino Card
used during STEP_WAIT = *Scratch!*; using it at other times opens a read-only ActionForm with every
card as 3 glyph rows (≤ 6 cards × 4 lines = 24 body lines). Final Reveal titles, Result ActionForm.

Glyphs: reuse the scratch-card glyph sheet for Coal/Iron/Gold/Emerald/Diamond/Star/Creeper; add one
glyph for Rabbit's Foot (U+E1A0, both editions) and one "charred" glyph (U+E1A1); unscratched = ▒.

### 8.6 Advancements, sounds, particles

`pvp_lucky_feet` (win with two Rabbit's Feet on your card). Sounds: `scratch` per step, creeper cell
= `random.fuse` / `entity.creeper.primed` then a soft explosion, foot = amethyst chime. Particles:
`wax_on` (Java) / `villager_happy` (Bedrock) on a foot, `smoke` on a burn at the player.
