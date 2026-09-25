# Research — Bots at every multiplayer table

Status: research input for the designer and the architect (2026-09-24). Not normative: the
decisions go into `GAME_DESIGN.md` / `CONFIG.md` / `STRINGS.md` once approved.

Goal from the user: every multiplayer table supports **humans only / bots only / humans + bots
filling free seats**, with bots of several **difficulties** (and personalities).

Contents
1. What exists today (poker bots, both editions) and what is reusable
2. Difficulty and personality design per game
3. Economics and abuse
4. UX: presentation, names, skins, delays, chatter, performance
5. Recommended shared model (enums, per-table settings, permissions, defaults)
6. Open questions

---

## 1. Existing code

### 1.1 Where the poker bots are

| Part | Java | Bedrock |
|------|------|---------|
| Decision logic (pure) | `java/src/main/java/dev/nezo/burmaldaholic/games/poker/logic/Bots.java` | `bedrock/src/games/poker/logic/bots.ts` |
| Equity / Chen | `games/poker/logic/Equity.java` | `games/poker/logic/equity.ts` (`equity()`, generator `equityJob()`) |
| Seating / fill (pure) | `games/poker/logic/PokerTable.java` (`botTarget`, `fillBots`, `makeRoom`, `BotFill`, VPIP history) | `games/poker/logic/table.ts` (same API) |
| Driver (timers, money) | `games/poker/PokerTableBlockEntity.java` (`botAct`, `startTimer("bot", …)`) | `games/poker/index.ts` (`scheduleBot`, `botJob`, `botSeq` cancel token) |
| Tests | `java/src/test/.../poker/logic/BotsTest.java` | `bedrock/src/games/poker/logic/bots.test.ts` |
| Config | `core/config/sections/PokerConfig.java` | `core/logic/config-catalog.ts` rows `poker.bot*` |
| Presets | `worldgen/logic/TablePresets.java`, `core/service/TablePresetProvider.java` (Parlor: Low stakes, ≤ 3 bots, Regular-heavy mix) | `REGULAR_HEAVY_MIX = [20, 70, 10]` in `games/poker/index.ts` |
| Owner switch | `multiplayer/MultiplayerApi.botsAllowed`, `CasinoBook.TableRow.bots` (default true) | `mp?.botsAllowed(key)` |

The two editions are line-for-line ports of each other (same thresholds, same name list, same
tests). Spec: `GAME_DESIGN.md` §7.4.

### 1.2 How they decide

- A bot sees a **View** (`Bots.View` / `BotView`): own hole cards, board, street, pot, to-call,
  stack, bet, min/max raise, **position** (early/middle/late/SB/BB), limpers, facing-raise,
  was-preflop-raiser, opponent count, and the **loosest live human's VPIP** (last 20 hands,
  only humans with ≥ 5 hands). No hidden information, no collusion between bots.
- **Tiers** (`Tier.FISH/REGULAR/SHARK`, ids `fish/regular/shark`):
  - *Fish* — no simulation. Preflop: limp with Chen ≥ 4, raise 3 BB with Chen ≥ 12, call raises
    with pocket pairs (≤ 10 BB) or Chen ≥ 6 (≤ 20 % of stack). Postflop: uses `madeCategory`
    (hand category that uses a hole card): call ≤ pot with a pair+, bet 50 % with two pair+,
    5 % bluff; otherwise check/fold. ±10 % sizing jitter.
  - *Regular* — position-based Chen open thresholds (8/7/6), 3-bet at C ≥ 11, call at C ≥ 9.
    Postflop Monte-Carlo equity E vs **random** hands (200 samples): E ≥ 0.65 raise 66 %,
    call if E ≥ pot odds + 0.05, c-bet 30 % as preflop raiser. 10 % chance to take the next-lower
    action (`lower()`).
  - *Shark* — thresholds −1, 8 % 3-bet bluff with suited connectors/aces, widens calls by 1 Chen
    vs a loose human (VPIP > 40 %). 500 samples; opponents who raised preflop are sampled from the
    top 40 % Chen range. Monsters: 20 % shove, 15 % slowplay, else 75 %; E ≥ 0.6 value 50–75 %;
    40 % flop semi-bluff with E ≥ 0.30.
- `legalize()` clamps every action to the legal set (raise → call when closed, oversize → all-in,
  fold → check when free). This is a good generic pattern for every game.
- Tier choice per seat: `pickTier(rng, mix)` from weights per stake level
  (`poker.botMix.micro/low/mid/high` = 50/40/10, 50/40/10, 30/50/20, 10/50/40).

### 1.3 Tick budgeting

- **Think time**: random `poker.botThinkMinTicks..MaxTicks` (20–60 t) before each action,
  independent of hand strength (no timing tells). Java: block-entity timer `"bot"`; Bedrock:
  `system.runTimeout`, guarded by `botSeq` + `isCurrent(...)` so a stale decision is dropped.
- **Monte-Carlo**: Java runs it synchronously on the server thread (200/500 samples is cheap).
  Bedrock spreads it with `system.runJob(this.botJob(...))`, where `equityJob()` is a generator
  that yields every 25 samples. The Bedrock "drawn outcome" play-out (§4.1 review M1) uses only
  `PLAYOUT_SAMPLES = 16` per bot action because it re-runs after every action.
- Failure policy: an exception in the bot → log + fold (both editions).

### 1.4 Funding, seating, naming, configuration

- **Funding**: house. Each bot "buys in" for `poker.botBuyInBb` (100 BB) from nothing; bot
  winnings disappear into the bank; a busted bot leaves and is replaced between hands. The code
  escrows human buy-ins in the world bank (`AccountId.HOUSE` / `bank`), so human profit taken from
  bots is paid by the bank. **At owned casinos the bots are still bank-funded** (only the rake goes
  to the owner's bankroll).
- **Seating**: `botTarget = 0` with no human; otherwise `max seats − humans − 1` (one free seat for
  walk-ins), at least 1 for a lone human; capped by preset `maxBots`. A walking-in human evicts the
  last bot (`makeRoom`, deferred to hand end). Announcements `msg.burmaldaholic.poker.bot_joins` /
  `bot_busts`.
- **Names**: fixed, non-localized list of 12 (`Bots.NAMES` / `BOT_NAMES`), unique per table
  when possible; tier shown as a coloured tag (`gui.burmaldaholic.poker.bot.fish` = Fish / Рыба, …).
- **Money semantics**: a poker hand is reported as a **PvP** `PlayResult` even when the only
  opponents are bots → no Golden Hour, no cashback, but it **does** count toward VIP lifetime
  wagered (full contribution, `VipService` adds `bet`) and the streak/contract hooks. Pots with
  a single human vs bots are **not raked** (§7.3).
- **Config**: global only — `poker.botsEnabled`, `botBuyInBb`, `botThinkMin/MaxTicks`,
  `bot.regularSamples`, `bot.sharkSamples`, `botMix.*`. Per table: owner's bots on/off
  (owned casinos only), presets for worldgen. §7.1 says the placer configures "bots on/off, tier
  mix" — this per-table setting is only partly realized (owned tables + presets).
- **Advancement**: `shark_hunter` (bust a Shark bot).

### 1.5 Other bot-like code

- Dealer NPCs (blackjack, `baccarat_dealer`, `uth_dealer`) are cosmetic entities; on Bedrock the
  NPC entity can host the table (`npc:<id>` key). No decision logic, but the entity plumbing
  (persistent, invulnerable, looks at players) is reusable for optional "patron" skins.
- Loan Shark / Debt Collectors: mob AI, not table bots.
- House automatic defaults in every table game (auto-stand, auto-check/fold, UTH
  `autoPlayMadeHands`, craps auto-roll) are the minimal "policy" each game already has — a bot
  policy is a superset of the timeout policy.
- UTH §21.3 **reference strategy R** is already specified (for tests) — it is a ready-made
  "Normal" UTH bot. OddsService (Java) exposes a **fair RNG not tied to a player** "for bots,
  ambient events"; Bedrock uses `mathRng`. Bots must keep using the fair, non-streak RNG.

### 1.6 Defects found while reading (worth a task)

1. **Fish folds AA to any raise above 10 BB.** In `fish()` the "raise with C ≥ 12" branch only
   fires when `currentBet < 3 BB`; facing a larger raise the pocket-pair call is capped at 10 BB and
   the Chen-6 call at 20 % of the stack, so a preflop shove makes a Fish fold every hand, aces
   included. A human who shoves every hand wins the blinds from Fish tables (~+1.5 BB per hand
   when no Regular/Shark wakes up). Fix: premium hands (Chen ≥ 12 or pairs ≥ TT) always call/shove.
2. **Fish never floats**: facing any bet without a pair it folds, so "bet every flop" wins ≈ 2/3
   of pots against it. Easy bots should be beatable, but not by one trivial rule.
3. Regular/Shark postflop equity is against **random** hands regardless of the betting (Shark
   narrows only preflop raisers). Calling a river bet with equity vs random hands overcalls against
   value-heavy bettors and folds too often to bluffs → exploitable by pure value-betting.
4. Name list contains **"Diamond Dave"**, a well-known nickname of a real musician (David Lee Roth)
   — replace (see §4.3).
5. Poker hands vs bots count 100 % toward VIP wagered and update the streak — see §3.

### 1.7 Reusable for a shared core bot framework

| Keep | Generalize to |
|------|---------------|
| `View` record built from public state only | `BotView<G>` per game, built by the game, never containing hidden cards |
| `decide(tier, view, precomputed, rng)` pure function | `BotPolicy<V, A>.decide(profile, view, rng)` |
| `samplesFor` + `equityJob` generator | `BotPolicy.work(view)` returning an optional generator/`Callable` for heavy work; Bedrock runs it with `system.runJob`, Java inline or on a budget |
| `legalize(view, action)` | mandatory last step in every policy (per game) |
| `pickTier(rng, mix)` weighted pick | `pickDifficulty(rng, weights)` in core |
| `botTarget`, `fillBots`, `makeRoom` | core `BotSeating` (target from seat policy, keep-a-seat rule, eviction for humans) |
| name list + uniqueness per table | core `BotRoster` (localized name keys, personalities) |
| think timer + `botSeq` token | core `BotScheduler` (delay by difficulty × decision complexity, cancel on state change) |
| VPIP tracking | core `OpponentStats` (per human per game: VPIP/PFR/aggression, bet style) — only Hard reads it |

---

## 2. Difficulty and personality design per game

### 2.0 Principles (all games)

- **Difficulty only changes decisions, never the RNG.** Cards, shoes, dice, wheel, reels, plinko
  paths and PvP tapes are drawn exactly as for humans, from the same fair RNG, before/independent of
  any bot choice. No bot sees hidden cards, the shoe order, or another bot's hand.
- **Three difficulties**: `EASY`, `NORMAL`, `HARD`. Poker keeps its flavour labels
  Fish / Regular / Shark as the display names of the three difficulties (strings exist).
- **Personality** is an orthogonal parameter set (looseness, aggression, bluff rate, bet style,
  chattiness, tilt). A profile = difficulty + personality + name. Personalities are what make
  luck-only games feel alive; difficulty only matters where a decision changes EV.
- **Where difficulty is meaningless** (roulette, craps, baccarat punto banco, PvP MUST modes,
  UTH/blackjack as house-vs-house atmosphere) the per-table option is presented as **"Style"**
  (or hidden) rather than pretending there is a skill level.
- **Reaction delay** is per difficulty and per decision complexity; Easy may leak a timing tell
  (see 2.1), Normal/Hard never correlate delay with hand strength.

### 2.1 Texas Hold'em (skill game — difficulty matters)

Notation: C = Chen score, E = equity, po = toCall / (pot + toCall), b = bet / pot.

**New ingredient for Normal/Hard: range-aware equity.** Instead of random opponents, each live
opponent is sampled from a range narrowed by their actions this hand (cheap to implement with the
existing Chen percentile machinery):

| Opponent action (latest street) | Sample from top … % of Chen hands |
|------|------|
| none / checked / limped | 100 % (Normal), 70 % (Hard, removes the hands that would have raised: sample 100 % then reject top 8 %) |
| called a bet | 60 % |
| bet ≤ ½ pot / preflop open | 45 % |
| bet ½–1 pot / 3-bet | 25 % |
| raise postflop / overbet / all-in | 12 % |

Ranges only narrow (take the tightest seen). Hard additionally uses the opponent's measured
VPIP: a human with VPIP 50 % who bets is given a range twice as wide as the table (clamped).

**Policies**

| | Easy ("Fish") | Normal ("Regular") | Hard ("Shark") |
|---|---|---|---|
| Preflop open | limp C ≥ 4 (65 %) / raise 3 BB C ≥ 10; no position | Chen open by position 8/7/6/6 (EP/MP/LP/SB) — current | By position: EP 9, MP 8, CO 7, BTN 5, SB 6; size 2.5 BB (+1 per limper); steal from CO/BTN/SB with C ≥ 4 when folded to (40 %) |
| Facing raise | call pairs, any ace, suited broadways if ≤ 15 % stack; **always call/shove with C ≥ 12 or TT+** (fix §1.6.1) | 3-bet C ≥ 11, call C ≥ 9 | Polarized 3-bet: value C ≥ 11, bluff 10 % with suited A2–A5 / suited connectors in position; call C ≥ 8 in position, 9 OOP; vs 4-bet/shove: continue with top 5 % (QQ+, AK) or by pot odds × stack depth |
| Postflop equity | none — made-hand + draw counting (flush draw 9 outs, OESD 8) | 300 samples, ranges above | 600–800 samples, ranges above + VPIP scaling |
| Value bet | two pair+: 50 % pot | E ≥ 0.65: 66 % pot | E ≥ 0.70 (0.58 vs calling stations): 60–80 % pot, overbet 125 % on river with nuts 20 % |
| Call | any pair ≤ pot; any draw ≤ ½ pot; **floats 35 %** with two overcards on the flop to bets ≤ ½ pot | E ≥ po + 0.05 | E ≥ po, **plus minimum-defence**: vs bet b defend so that fold ≤ b/(1+b) of its range — implemented as "call when E is in the top 1/(1+b) of this bot's equity distribution this street" (compute the percentile from the same samples) |
| Bluff | 5 % random bet; 10 % "maniac" personality | c-bet 50 % heads-up / 25 % multiway as PFR, never river bluffs | Balanced river bluffing: bluff-to-value ratio b/(1+b) (pot bet → 1 bluff per 2 value bets), bluffs picked from the lowest-E hands that had draws; flop semi-bluff E ≥ 0.30 at 40 %; float in position with E ≥ 0.25 heads-up; check-raise OOP with E ≥ 0.8 at 30 % |
| Position awareness | none | preflop only | preflop + postflop (IP: float/bet more, OOP: check-raise, check-call) |
| Opponent model | none | none | VPIP / PFR / aggression-factor per human (last 50 hands): vs station (VPIP > 40 %, AF < 1) no bluffs, thin value; vs nit (VPIP < 15 %) steal more, fold more to its raises; vs maniac (AF > 3) call down wider |
| Mistakes | 15 % random "next-lower" or "next-higher" action; tilt: after losing a pot > 40 BB plays 1 C looser for 5 hands | 8 % next-lower action | none (randomness only through mixed strategies) |
| Think time (ticks) | quick 10–30 / **tell**: +40 t when E is high (a readable tell for beginners) | 20–60 uniform | 20–60 uniform, +0–40 on big decisions (to-call > 25 % stack) regardless of hand |

**Personalities** (weights per difficulty; parameters shift the thresholds above):

| Personality | ΔChen open | Aggression (raise:call) | Bluff × | Allowed at |
|------|------|------|------|------|
| Rock (tight-passive) | +2 | 0.5× | 0.3 | Easy, Normal |
| Calling Station (loose-passive) | −3 | 0.5×, calls 1.5× wider | 0.2 | Easy |
| Maniac (loose-aggressive, spewy) | −3 | 2× | 3 | Easy |
| TAG (tight-aggressive) | 0 | 1× | 1 | Normal, Hard |
| LAG (loose-aggressive, solid) | −1.5 | 1.4× | 1.5 | Hard |

**Target win rates** (to be verified by the simulation suite, §3.5), vs a solid human
(ABC tight-aggressive): Easy −40 to −80 BB/100, Normal −5 to −15 BB/100, Hard −3 to +3. Vs weak
humans (loose-passive, VPIP 45 %): Hard +10 to +25 BB/100, Normal ≈ 0 to +10. That meets
"Hard near break-even or +EV vs weak humans".

Cost: Hard's range-aware MC is `samples × opponents` evaluations; 800 samples × 5 opponents × one
7-card eval is ≈ 4 000 evaluations — trivial on Java; on Bedrock spread over ≈ 4–8 ticks with
`runJob` (existing pattern, yield every 25 samples). The equity percentile for minimum-defence
reuses the same samples (no extra cost).

### 2.2 Baccarat — Chemin de fer (§20.9)

Current spec: **fixed tableau** (both hands draw by §20.3, no draw-on-5 choice). So a chemmy bot
has only money decisions:

| Decision | Easy | Normal | Hard |
|------|------|------|------|
| Take the bank (offered) | 60 % | 40 % | 30 %, only when its bankroll allows ≥ 10 coups at the table's typical bet |
| Bank size B | random 2–5 × `minBank` | 10 × table min | `min(bot cap, 3 × median punter stake of the last 5 coups × seats)` so the coverage is used, not wasted |
| Keep the bank after a win | keep until it loses ("hot hand") | pass after 3 wins (gambler's fallacy flavour) | keep while `B ≤ bot bank cap`, else pass (variance control) |
| Punt stake | random 1–3 × min, chases losses ×2 (Martingale personality) | flat 2–5 × min | flat, sized to fill open coverage ≤ its cap |
| Banco | 20 % when affordable | 5 % | never (only variance) |

Every one of these choices is EV-neutral **per chip** (banker −1.06 %, punter −1.24 % incl. rake),
so difficulty is flavour; the table option should read "Style" for chemmy.

If the designer later adds the **classic choices** (punter's option on 5, banker's discretionary
spots) as `baccarat.chemmy.classicChoices`: Easy random 50/50; Normal "always draw on 5" (the
common house rule, ≈ optimal); Hard the game-theoretic mix computed offline (the EV gap between
these is < 0.1 % of the stake, so even then it is mostly flavour). Keep the fixed tableau as the
default — it keeps §20.2's exact odds and avoids timers.

Bots at the **house coup** (no banker): atmosphere bettors, see 2.5.

### 2.3 Ultimate Texas Hold'em (§21, §21.9)

**Bots as fellow players at a shared house table.** Each seat plays only the dealer, so a bot's
play never affects a human's EV; it only adds company and shared "sweat" on the board.

| Street | Easy | Normal (= strategy R, §21.3) | Hard (≈ optimal) |
|------|------|------|------|
| Preflop | ×4 with any pair or any Ace; 15 % random ×3 ("scared money"); else check | ×4 per R: pairs 33+, any A, Kx suited, K5o+, Q6s+/Q8o+, J8s+/JTo | R's list (never ×3), which matches published near-optimal play |
| Flop | ×2 with any pair (incl. board-only pairs — mistake) or a flush draw | R: hidden pair (not 22), two pair+, 4-flush with hidden T+ | same + "hidden pair or better, or a hidden-card 4-flush" |
| River | bet ×1 with any pair, else fold 70 % / call 30 % | exact enumeration of the 990 dealer hands (R) | same (it is the optimal river rule; "fewer than 21 dealer outs" is the manual shortcut) |
| Trips | random 50 % of the time, 1 × Ante | never | never (−1.9 %) |

Measured edges to publish in the UI tooltip: Easy ≈ 4–6 % (to be simulated), Normal 2.27 %,
Hard ≈ 2.19 %. Cost: the river enumeration is 990 × 2 evaluations per bot — run inside the
street's shared decision window; on Bedrock one bot per tick via `runJob`.

**Bots as banker (player-banked table §21.9).** A bot in the dealer seat makes **no decisions**
(the dealer hand is mechanical), so a "bot banker" is just a house round with a name on it.
Recommendation: **no bot banker**; when no human banks, the existing
`uth.pvp.houseRoundsWhenNoBanker` house round runs (optionally presented with a named bot dealer
for flavour, money = house, no rake).

**Bots must not sit as real-money seats against a human banker** — the seats have −2.2 % edge,
so a human bank facing house-funded bot seats would farm the house (+2.27 % before rake,
+0.46 % after the 1 % rake, and the rake itself goes to the owner at owned casinos). When a human
banks, bot seats either leave or play with **virtual chips that the bank does not settle**.

### 2.4 Blackjack (fellow seated players)

Purely atmospheric: each hand plays the dealer; other players' decisions consume cards but do
not change a human's expected value (card removal by unseen play is EV-neutral).

| | Easy | Normal | Hard |
|------|------|------|------|
| Play | "mimic the dealer": hit < 17, never double; split only A-A and 8-8; takes insurance 50 % | basic strategy (6D S17 DAS) with 5 % errors on soft totals/doubles | perfect basic strategy |
| Bet style | random, chases losses (Martingale personality) | flat | flat, "never takes insurance" line in chat |
| Think time | 15–40 t per action | 10–30 t | 10–25 t |

Rules for atmosphere bots at blackjack: at most 2 bot seats; they act in seat order like humans
but never use the 20 s timer (a bot decision is ≤ 40 t); they never delay BETTING (bots bet
instantly and count as "Deal" pressed). Popular quips: "Never split tens!", "Dealer's showing a
six, easy money." (localized).

### 2.5 House tables: roulette, craps, baccarat punto banco (atmosphere bettors)

All bets are −EV and independent of other bettors, so there is no difficulty — only **betting
styles**. Styles are named personalities with a bet-selection function over the game's bet list:

| Game | Styles (examples) |
|------|------|
| Roulette | *Red Lover* (red/black even-money, flips colour after 3 losses), *Lucky Number* (straight on a fixed favourite number + its neighbours), *Dozen Grinder* (two dozens), *Martingale Maria* (doubles even-money after a loss, resets at cap), *Sprinkler* (5–8 random inside chips), *Zero Hero* (0 + first four) |
| Craps | *Right-way grinder* (Pass + full 3-4-5× odds — the "Hard" play, 0.37 %), *Dark-side* (Don't Pass + lay, quips when the table groans), *Field Fan*, *Come-bet ladder* |
| Baccarat PB | *Banker Only* (the "Hard" play, 1.06 %), *Trend Follower* (bets the last winner — reads the bead plate), *Chop Chaser* (bets against the last winner), *Tie Hunter* (Tie + pairs every few coups) |

Sizing: `min + k × step`, k by personality (conservative 1–2, high-roller 5–10), always within
the table min/max and the table's reservation rule would *not* apply because the chips are
virtual (§3.2). If the designer wants a difficulty label here, map Hard = lowest-edge bets only,
Easy = high-edge bets; it changes nothing for humans.

Craps: bots may join the **shooter rotation** only with `craps.bots.canShoot` (default false —
humans keep the dice); when they shoot they roll after 20–40 t (never the 20 s timer).

### 2.6 PvP extras (`PVP.md`): coin flip duel & double-or-nothing, wheel party, plinko battle, slot showdown, scratch showdown

PVP.md pillar 4: **no decisions inside a MUST match**; the whole tape is drawn at START. The only
bot choices are around matches, and all are EV-neutral (Lemmas 1–3). Difficulty therefore maps to
personality only, and **the tape must be drawn from the same distribution for bots and humans**
(no "easy bot draws worse reels").

| Mode | Bot decisions | Easy / Normal / Hard mapping |
|------|------|------|
| Coin Flip Duel | accept a challenge (stake within bot cap), call a side, Double or nothing as loser, Let it ride as winner | Easy: always Double / Let it ride (max drama, capped by `maxDoubles`); Normal: 50 %; Hard: walks away / takes the money (variance-averse). All EV-neutral (Lemma 3) |
| Wheel Party | join, stake size, top-ups | Easy: min stake + random top-ups; Normal: median stake; Hard: stakes up to the cap early. Win chance = share, EV = −R share (Lemma 2) — unaffected |
| Plinko Battle | join; risk is the host's | none beyond press-Drop timing |
| Slot Showdown | join; spins count is the host's | none beyond press-Spin timing |
| Scratch Showdown | join | none beyond press-Scratch timing |
| Rematch | accept rematch | Easy 80 %, Normal 50 %, Hard 30 % (after a loss: +20 % "grudge") |

Usefulness: bots let a lone player open a lobby ("Lucky Steve joins your Plinko Battle"). Rules:
bots fill lobbies only after `pvp.bots.fillAfterTicks` (e.g. 400 t) with no human joiner, never
more than `pvp.bots.maxPerMatch` (e.g. N−1 but ≤ 3), never in duels unless the player explicitly
challenges "the house bot" from the hub. NICE decision modes (Scratch Poker, Wheel Heist,
Plinko Bumpers, tournaments) would need real policies later — follow the 2.1 pattern.

---

## 3. Economics and abuse

### 3.1 Who pays for bots — the core rule

**Bot money is house money, and it must never become owner money or a free mint for humans
beyond what the game's own edge allows.** Two funding kinds:

| Kind | Used when | Settlement |
|------|------|------|
| **Virtual chips** (no economy movement at all) | the bot plays against the house or only beside humans: roulette, craps, baccarat PB, blackjack, UTH house rounds, bots-only showcase tables | nothing is debited/credited; no VIP/streak/contract/cashback; shown with a "virtual" marker in logs only |
| **Real house-funded stack** | the bot is a counterparty of humans: poker, chemin de fer (bank or punter), PvP lobbies | debit/credit the bank (house tables) or the owner's bankroll (owned casinos, §3.3); tracked by a per-table **bot ledger** |

### 3.2 EV per game — can humans farm bots?

| Game | Human EV vs bots | Farmable? | Safeguard |
|------|------|------|------|
| Poker vs Easy | strongly + (skill) | **yes** — the only real risk | stake gates, daily cap, bot bankroll, VIP weighting (3.4) |
| Poker vs Normal | + for good players | mildly | same |
| Poker vs Hard | ≈ 0 for good, − for weak | no | — |
| Chemmy, human banks vs bot punters | banker +1.24 % before rake, −1.06 % after 5 % rake | no — **unless the rake goes to an owner who colludes** (owner + alt = +1.24 %) | rake on chips won from bots goes to the bank sink, never to a bankroll |
| Chemmy, human punts vs bot bank | −1.24 % | no | bot bank takes no rake (house) |
| UTH, human banks vs bot seats | +2.27 % | **yes** | bots never place real bets against a human bank (2.3) |
| UTH / blackjack / roulette / craps / baccarat house tables | unchanged (bots virtual) | no | virtual chips |
| PvP modes vs bots | −rake/N, same as vs humans | no; variance only | rake from bot-involved matches never to an owner; bots excluded from rivalry/distinct-opponent counts |

Scale of the poker risk (to size caps): with ≈ 60 hands/hour (bot think 20–60 t, animations) and a
competent human beating a Fish-heavy table by ≈ 40 BB/100 → ≈ 24 BB/hour: Micro ≈ 48, Low ≈ 240,
Mid ≈ 1 200, High ≈ 4 800 chips/hour. For comparison a diamond ore pays 20. Micro/Low vs Fish is
fine ("poker is the skill game", §7.4); Mid/High vs Fish is a mint and must be gated.

### 3.3 Owned casinos

- Current code: bots at owned poker tables are **bank-funded**; the owner toggles them. An owner
  can therefore host an "Easy bot farm" for friends paid by the world bank.
- **Recommendation**: at owned tables bots are **funded by the owner's bankroll** (bot buy-ins
  reserved like a stake, bot results settle into the bankroll). The owner chooses difficulty and
  pays for generous bots — self-balancing. If the bankroll cannot fund a bot buy-in, no bot sits
  ("bots can't afford a seat" line on the charter). Owner still cannot play at own tables;
  an owner's alt beating owner-funded bots only moves the owner's own chips (already possible with
  chip items). Alternative for simplicity: bots disabled at owned tables.
- Rake: keep "pots where the only human faces bots are not raked"; for multi-human pots compute the
  rake on the pot **minus bot contributions** so bot chips never turn into rake for the owner.
- Charter statistics gain "Bot results" (net of owner-funded bots).

### 3.4 Concrete safeguards (poker; the pattern applies to any future skill game)

1. **Stake gates** (default difficulty weights, Easy/Normal/Hard):
   Micro 45/45/10, Low 35/50/15, Mid 10/55/35, High 0/45/55. `EASY` is not allowed above Low
   unless an operator changes `bots.poker.allowedByStake`.
2. **Per-table bot bankroll**: each table may fund at most `bots.tableBuyInsPerDay` (default 10)
   bot buy-ins per Minecraft day; when exhausted, busted bots are not replaced until the next day
   ("The regulars went home"). Bounds the mint per table.
3. **Per-player daily cap vs house bots**: `bots.dailyNetWinCapBb` (default 300 BB at the table's
   stake) of net profit from bot chips per player per MC day. Reaching it does not confiscate
   anything; it **upgrades** that player's table: new bots are Hard only and Easy bots leave at the
   next hand ("Word got around about you…"). Softer and more fun than a block.
4. **Adaptive heat** (optional, Normal default on): a human whose rolling net vs bots is
   > +20 BB/100 over ≥ 200 hands shifts the next fills one difficulty up.
5. **VIP / contracts / streak**:
   - VIP lifetime wagered: chips a human puts into a pot count at `bots.vipWagerWeight` (0.5) for the
     part matched by bots, 1.0 for the part matched by humans. Rationale: playing Easy bots is
     +EV, so full weight would make it the cheapest (negative-cost) VIP ladder.
   - Streak (§14): poker hands whose opponents are all bots do not update the streak (same
     reasoning as PVP.md amendment 1).
   - Contracts: `play_poker` counts (it is a time task); `wager` counts with the weight above.
   - Golden Hour / cashback: never (already PvP-classified) — keep.
6. **Advancements**: `shark_hunter` is bot-specific (keep; Hard bots only). `royal_flush` ("win a PvP
   pot") — count vs bots (a royal is luck). PvP-count advancements (`pvp_rampage`, `pvp_full_house`,
   `pvp_revenge`), rivalry records, grudge matches and win-streak announcements **exclude bots**.
   Chemmy `banco` / `bank_holder` and UTH `uth_house_seat`: count only when at least one human
   is on the other side.
7. **Collusion vs bots** (two humans soft-playing and squeezing bots): the multi-human pot is
   raked, and the per-player daily cap applies to each; bots never collude with each other.
8. **Debtors**: Loan Shark debtors are already barred from chemin de fer coups and PvP modes
   (§20.9, PVP.md §3.2). Apply the same rule when the counterparties are real-money bots, so the bot
   seats don't become a way around it. Poker keeps its current rule (it is allowed now).

### 3.5 Verification (recommend adding to both editions' tests)

A seeded **exploit regression suite** in the pure logic layer: simulate N = 50 000 hands of each
difficulty against scripted strategies — *always-shove*, *always-c-bet*, *calling-station*,
*nit*, *ABC-TAG* — and assert win-rate bands (e.g. Easy vs ABC ≤ −30 BB/100, Hard vs every script
≥ −5 BB/100 ± 3σ). This would have caught §1.6.1. Same idea for UTH (edge per difficulty) and
blackjack (basic strategy table test).

---

## 4. UX

### 4.1 What others do

- **Minecraft casino mods**: the popular ones (e.g. CasinoCraft on CurseForge) ship card games
  for multiplayer but *no computer players*; players must bring friends. Bots at a
  6-max poker table are a differentiator of this mod — worth presenting clearly.
- **Real-money online casinos** forbid bots at player-vs-player tables; where a "table of others"
  is shown it is either real players (live-dealer "bet behind") or clearly labelled demo/practice.
  Offline poker/casino apps expose explicit "Play vs computer" with Easy / Medium / Hard.
  Lesson: **always label bots** (tag + colour), never pass them off as players; this also keeps
  trust ("are the bots rigged?") — the answer must be "they see only what you see".
- Social-casino apps use named NPC characters with portraits and one-line barks — that is the
  right register for Minecraft.

### 4.2 Presentation

- Seat label: `Name [Fish]` (poker, existing) / `Name [Bot · Easy]` elsewhere; difficulty colours
  green / yellow / red. Tooltip on Java: personality ("Calling Station"), bot bankroll left today.
- Table settings screen (placer / owner): **Seats: Humans only · Humans + bots · Bots only**,
  **Difficulty: Easy · Normal · Hard · Mixed** (Mixed = stake-level weights), **Max bots**,
  **Bot chatter on/off**, **Bot speed: Normal · Fast**. Bedrock: one ModalForm with dropdowns.
- Join message and "bots leave when you sit" notice: humans always have priority for seats.
- "Bots only" = **showcase mode**: runs only while a player is within `multiplayer.spectatorRadius`,
  virtual chips, slower pace, pauses otherwise. Great for worldgen casinos and for learning
  (watch Sharks play), zero economic risk.

### 4.3 Names

- Move names to **localized keys** `bot.burmaldaholic.name.<id>` (stable ASCII id, localized display)
  so RU players get Russian names; the id is saved with the seat. Uniqueness per table/lobby.
- Rules: no real people (celebrities, streamers, poker pros, Minecraft personalities such as Notch
  / Dream / Technoblade), no slurs, no names that imitate staff/ops. Replace "Diamond Dave".
- Suggested pools (24 each; personalities attached as defaults):

| id | EN | RU |
|------|------|------|
| lucky_steve | Lucky Steve | Везунчик Стив |
| grandpa_pavel | Grandpa Pavel | Дед Павел |
| creeper42 | Creeper42 | Крипер42 |
| mr_blocksworth | Mr. Blocksworth | Мистер Блоксворт |
| diamond_dora (replaces Diamond Dave) | Diamond Dora | Алмазная Дора |
| aunt_zoya | Aunt Zoya | Тётя Зоя |
| redstone_rick | Redstone Rick | Рэдстоун Рик |
| nether_nick | Nether Nick | Незер Ник |
| emerald_emma | Emerald Emma | Изумрудная Эмма |
| sir_oinksalot | Sir Oinksalot | Сэр Хрюкенс |
| baba_valya | Baba Valya | Баба Валя |
| enderman_ed | Enderman Ed | Эндермен Эд |
| uncle_grisha | Uncle Grisha | Дядя Гриша |
| kuzmich | Old Kuzmich | Кузьмич |
| cobble_carl | Cobblestone Carl | Булыжник Карл |
| gold_goldie | Goldie Nuggets | Голди Самородок |
| piglin_pete | Piglin Pete | Пиглин Петя |
| slime_sam | Slimy Sam | Слизняк Сэм |
| witch_winnie | Winnie the Witch | Ведьма Вини |
| captain_boat | Captain Boat | Капитан Лодка |
| bee_bea | Bea the Beekeeper | Пчеловод Беа |
| torch_tanya | Torch Tanya | Таня-Факел |
| axolotl_al | Axolotl Al | Аксолотль Ал |
| lady_luckless | Lady Luckless | Леди Невезуха |

Worldgen flavour: Piglin Parlor bots draw from piglin-themed names; End lounge from ender names.

### 4.4 Skins / bodies

- Default: **virtual seats** (no entities) — name + tag rendered in the table UI (Java custom
  screen + block-entity renderer text; Bedrock action-bar / form rows). Zero entity cost.
- Optional (NICE, `bots.showBodies`): cosmetic "patron" entities reusing the dealer-NPC plumbing
  (stationary, invulnerable, non-persistent, removed when the table empties): villager / piglin /
  wandering-trader looks per casino theme; on Bedrock a single entity type with variants. Cap
  bodies per world (`bots.maxBodies`, e.g. 16) because Bedrock entity count and Java rendering
  cost add up.

### 4.5 Thinking delays and pace

- Poker: keep 20–60 t, Hard +0–40 t on big decisions (hand-independent), Easy optional tell
  (§2.1). `Bot speed: Fast` halves all delays.
- House games: bots never extend a timer; they bet at a random moment inside the first 3–8 s of
  BETTING and are always "Ready". A table with only bots and one human feels like a real table but
  deals as fast as solo play.

### 4.6 Chat quips

- Localized lines `msg.burmaldaholic.bot.quip.<event>.<n>` (3–6 per event), picked by personality:
  events `join`, `bust`, `big_win`, `bad_beat`, `fold_to_shove`, `hero_call`, `blackjack`,
  `seven_out`, `natural`, `banco`, `bank_pass`, `pvp_win`, `pvp_loss`.
- Limits: per table ≤ 1 quip / 200 t and ≤ 3 / minute; Hard bots speak half as often; never
  reveal hidden information ("I had the flush" only after showdown); player setting
  "Show bot chatter" (default on), table toggle. Sent only to players within the spectator radius.

### 4.7 Performance limits

| Limit | Default | Why |
|------|------|------|
| Poker bots per table | seats − humans − 1 (existing) | keep a seat for walk-ins |
| Atmosphere bots per house table | 3 (roulette, craps, baccarat), 2 (blackjack, UTH), 2 (chemmy) | pace + screen space |
| Active bot tables per world | Java 24, Bedrock 12 (`bots.maxActiveTables`) | tick budget |
| Heavy jobs (MC equity, UTH river enumeration) | Bedrock: at most 2 concurrent `runJob`s per world, yield every 25 samples; Java: inline ≤ 1 000 samples, else split across ticks | Bedrock script watchdog / slow-tick warnings |
| Bots active only while | a human is seated (fill) or within spectator radius (showcase) | no idle CPU in unloaded/empty casinos |
| Drawn-outcome play-out (Bedrock poker M1) | keep `PLAYOUT_SAMPLES = 16`; for Hard use its policy with 16 samples | runs after every action |

---

## 5. Recommendations — shared model

### 5.1 Core types (both editions, pure logic in `core/bots`)

```text
enum SeatPolicy   { HUMANS_ONLY, HUMANS_AND_BOTS, BOTS_ONLY }          // per table
enum BotDifficulty{ EASY, NORMAL, HARD }                               // poker labels: fish/regular/shark (legacy ids stay valid)
enum BotFunding   { VIRTUAL, HOUSE, OWNER_BANKROLL }                   // derived, not configurable (§3.1, §3.3)

record Personality(String id, double looseness, double aggression, double bluff,
                   String betStyle, double chattiness, boolean tells)
record BotProfile(String nameId, BotDifficulty difficulty, Personality personality)

interface BotPolicy<V, A> {                    // one per game
    A decide(BotProfile p, V view, Rng rng);   // pure; last step = legalize(view, a)
    default Job<Double> work(BotProfile p, V view) { return null; } // optional heavy precompute
}

record TableBotSettings(SeatPolicy seats, BotDifficulty fixedOrNull /* null = Mixed */,
                        int maxBots, boolean chatter, boolean fast)
```

Core services: `BotRoster` (names, personalities, uniqueness), `BotSeating` (target/evict —
generalized `botTarget`/`fillBots`/`makeRoom`), `BotScheduler` (delay + cancel token), `BotBudget`
(Bedrock `runJob` limiter), `BotLedger` (per-table daily buy-ins, per-player net vs bots, owner
bankroll funding), `OpponentStats` (for Hard), `BotChatter`.

### 5.2 Who can change what

| Setting | House table (placed) | Worldgen table | Owned table |
|------|------|------|------|
| Seat policy, difficulty, max bots, chatter, speed | placer (stored in the block) + ops | ops only (preset) | owner (charter screen), within global caps |
| Allowed difficulties per stake, caps, weights, funding rules | server config only | | |

Global keys (new section `bots`, per-game overrides `<game>.bots.*`):
`bots.enabled`, `bots.maxActiveTables`, `bots.tableBuyInsPerDay` (10), `bots.dailyNetWinCapBb` (300),
`bots.vipWagerWeight` (0.5), `bots.adaptiveHeat` (true), `bots.showBodies` (false),
`bots.chatterCooldownTicks` (200), `bots.poker.allowedByStake`, `bots.poker.mix.<stake>` (replaces
`poker.botMix.*` — keep old keys as aliases), `bots.owned.funding` (`OWNER_BANKROLL` | `DISABLED`),
`craps.bots.canShoot` (false), `pvp.bots.fillAfterTicks` (400), `pvp.bots.maxPerMatch` (3).

### 5.3 Defaults per game

| Game | Default seat policy | Difficulty | Funding | Notes |
|------|------|------|------|------|
| Poker | Humans + bots | Mixed by stake (§3.4.1) | house / owner bankroll | existing behaviour + safeguards |
| Chemin de fer | Humans + bots (≤ 2) | Style (Mixed) | house / owner bankroll; rake from bot chips → sink | bots may bank at house tables |
| UTH house | Humans only (placed), Humans + bots ≤ 2 (worldgen) | Normal | virtual | atmosphere |
| UTH player-banked | Humans only while a human banks | — | — | no bot banker; house rounds as now |
| Blackjack | Humans only (placed), Humans + bots ≤ 2 (worldgen) | Normal | virtual | atmosphere |
| Roulette / craps / baccarat PB | Humans only (placed), Humans + bots ≤ 3 (worldgen) | Style | virtual | bots never shoot by default |
| PvP lobbies | fill after 20 s with no joiner | Style | house; rake → sink | never in duels unless asked |
| Any table | "Bots only" = showcase, virtual chips | any | virtual | runs only with an onlooker |

### 5.4 Priority

1. Fix the poker Fish shove bug (§1.6.1) and add the exploit regression suite.
2. Core `bots` package + seat policy + per-table settings; port poker onto it (Fish/Regular/Shark →
   EASY/NORMAL/HARD).
3. Economy rules: owner-bankroll funding, rake excludes bot chips, VIP weight, streak exclusion,
   stake gates, daily cap, table bankroll.
4. Chemmy bots (real stakes) → UTH/blackjack atmosphere (virtual) → roulette/craps/baccarat styles
   → PvP lobby fillers → names/quips/bodies.
5. Hard poker (range-aware equity, MDF, opponent model) last — it is the biggest logic change.

## 6. Open questions for the designer

- Is "bots only" showcase wanted at player-placed tables, or worldgen only?
- Owned casinos: owner-funded bots, or simply no bots? (Research favours owner-funded.)
- Should Mid/High poker allow Easy bots at all if an operator wants a "fun server"? (Config says
  yes via `bots.poker.allowedByStake`; default no.)
- Chemmy: keep the fixed tableau (recommended) — then no draw-decision difficulty exists.
- VIP weight 0.5 vs excluding bot-matched chips entirely.

Sources consulted for §4.1 / §2.3: CurseForge listing of CasinoCraft
(https://www.curseforge.com/minecraft/mc-mods/casinocraft), Wizard of Odds UTH strategy
(https://wizardofodds.com/games/ultimate-texas-hold-em/), Upswing UTH basic strategy
(https://upswingpoker.com/ultimate-texas-holdem-rules-basic-strategy/).
