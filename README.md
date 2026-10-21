# Burmaldaholic

**English** · [Русский](README.ru.md)

**Burmaldaholic** (Russian: **«Бурмалдоголик»**) turns a survival world into a casino economy.
You earn chips by mining, fighting, trading and doing daily contracts. You spend them on blackjack,
Texas Hold'em, Ultimate Texas Hold'em, baccarat, slots, roulette, craps and a handful of quick games,
or on PvP duels against other players. Bots can fill the empty seats. If you run short, the Loan Shark
will lend you some, and his Debt Collectors will come for you if you don't pay him back. On top of
that, chaos events rain diamonds or mobs on you, and Last Chance lets you flip a coin with Death.

It is a Fabric mod for Minecraft Java Edition 26.3 (`burmaldaholic-<version>.jar`, sources in
[`java/`](java/)), fully localized in English and Russian.

> **No real money.** Chips exist only inside your Minecraft world. You can't buy them, sell them
> or cash them out, and the mod has no purchases, ads or external services. It is a game mechanic
> that parodies gambling, and the house wins in the long run, just like a real casino.

---

## Contents

- [Features](#features)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Turning casino mode on and off](#turning-casino-mode-on-and-off)
- [Difficulty and Hardcore](#difficulty-and-hardcore)
- [Configuration](#configuration)
- [Languages](#languages)
- [Building from source](#building-from-source)
- [Testing](#testing)
- [Releases](#releases)
- [Project layout](#project-layout)
- [Contributing](#contributing)
- [License](#license)

---

## Features

The full, normative rules are in [`docs/design/GAME_DESIGN.md`](docs/design/GAME_DESIGN.md). All
numbers below are defaults, and most can be changed ([Configuration](#configuration)).

### Economy and chips

- **Balance.** Every player has a chip account. It survives death, is shown on the HUD, and is
  where every bet comes from and every win goes to. New players start with 50 chips and a
  **Casino Card**, which opens the Casino Menu. On Java the menu is also bound to the `B` key.
- **Chip items.** Physical chips worth 1, 5, 25, 100 and 500, plus high-roller chips worth 1 000,
  5 000 and 25 000. You can trade, store, drop or steal them. The **Cashier** counter converts
  between chips and your balance and exchanges items: 1 emerald buys 9 chips and 10 chips sell for
  1 emerald, with rows for emerald blocks, diamonds and iron blocks. The Nether cashier also takes
  gold ingots and netherite scrap (Diamond VIP). Exchange ×1, ×64 (Shift-click), All or Max.
- **Ways to earn:**
  - ores, from 1 chip for coal up to 20 for diamond and 50 for ancient debris
  - hostile mobs, from 2 up to 1 000 for the first Ender Dragon, with diminishing returns so mob
    farms stop paying
  - villager trades, capped at 200 chips per day
  - **daily contracts**: 3–5 tasks a day, such as "Mine 24 iron ore" or "Win 3 blackjack hands"
- **Pawn stakes.** On coin flip, dice duel, the wheel and even-money roulette bets you can stake
  items, XP levels or even **temporary max hearts** instead of chips.
- **Chip store.** 38 offers of food, potions, enchanted books (including Mending), tools, decor and
  rare finds (a Totem of Undying, and elytra and shulker shells at End counters), with daily limits.
  It is open at every cashier and at the **casino merchant** beside it.
- **Comps, daily bonus and free bets.** Every house game earns comp points at every VIP tier: spend
  them on food plates, a suite night (a casino bed sets your spawn and gives buffs), free-bet tokens,
  fireworks, or a rented horse or strider. The cashier pays a daily bonus of 25 to 2 000 chips by
  VIP tier, and a toast shows when cashback is paid.
- **Bet entry.** Press **E** or click your bet to type an amount (`k` = thousand), halve it, double
  it or set it to Max. A refused bet says whose limit it hit: your VIP tier, the table, the casino
  owner or your balance.

### Games

| Game | Key rules |
|---|---|
| **Blackjack** | 6 decks, dealer stands on soft 17, blackjack pays 3:2, double after split, re-split up to 4 hands, insurance and even money. Up to 5 seats. |
| **Texas Hold'em** | No-Limit, 6-max cash game with side pots. Four stake levels (1/2 up to 100/200). Empty seats can be filled by **bots** at three skill tiers (Fish, Regular, Shark). The house takes a 5 % rake from pots with 2 or more human players, capped at 3 BB, and only if the hand saw a flop. |
| **Slots** | Three themed 5×3 video slots with **243 ways** to win: **Overworld Riches** (low volatility, free spins, **Treasure Hunt** bonus), **Nether Inferno** (tumbling reels with a multiplier ladder, **Piglin's Hoard** hold-and-spin bonus) and **End Void** (Gold VIP; sticky expanding wilds, **Dragon Wheel** bonus). Each machine has four progressive jackpots (**Mini, Minor, Major, Grand**): the **jackpot gem** can award a Mini or Minor on any paid base spin (a Mini comes about once in 2 000 spins), and Major and Grand are won in the bonus game. The paytable shows where each one comes from and its odds. Big outcomes start chaos events, and five scatters start Golden Hour. |
| **Baccarat** | Punto Banco from an 8-deck shoe: Player 1:1, Banker 0.95:1, Tie 8:1, Player / Banker Pair 11:1. Up to 7 seats. A player-banked **Chemin de fer** table is also available. |
| **Ultimate Texas Hold'em** | Each seat plays the dealer: Ante + Blind, then one Play bet (×4 preflop, ×2 on the flop, ×1 at the river) or a fold, plus an optional Trips bet. Up to 6 seats. |
| **European roulette** | Single zero. All standard inside and outside bets, from straight up (35:1) to even money. |
| **Craps** | Pass / Don't Pass, Come / Don't Come, Field (2 pays 2:1, 12 pays 3:1) and free odds up to 3-4-5×. Up to 6 players, with a rotating shooter. |
| **Coin flip** | Lucky Coin item, usable anywhere. Pays 0.96:1. |
| **Wheel of Fortune** | 54 segments, from Bust up to 10×. |
| **Scratch cards** | Basic card (10 chips) and Gold card (100 chips). |
| **Plinko** | 12 rows, with Low, Medium or High risk. The High-risk edge bins pay 170×. |
| **Dice duel** | 2d6 against the house (a tie on 7 goes to the house), or against another player for an escrowed pot. |

#### House edge (defaults)

| Game | RTP | House edge |
|---|---|---|
| Blackjack (basic strategy) | 99.59 % | 0.41 % |
| Blackjack insurance | 92.60 % | 7.40 % |
| Poker vs players | — | rake ≤ 5 % of raked pots |
| Slots: Overworld Riches / Nether Inferno / End Void | 98.37 / 98.42 / 98.41 % (≈ 98.4 %) | 1.63 / 1.58 / 1.59 % |
| Baccarat Banker / Player / Tie | 98.94 / 98.76 / 85.64 % | 1.06 / 1.24 / 14.36 % |
| Ultimate Texas Hold'em (optimal play) / Trips | 97.82 % of the Ante / 98.10 % | 2.19 % of the Ante / 1.90 % |
| Roulette (any bet) | 97.30 % | 2.70 % |
| Craps Pass / Come | 98.59 % | 1.41 % |
| Craps Don't Pass / Don't Come | 98.64 % | 1.36 % |
| Craps Field | 97.22 % | 2.78 % |
| Craps Odds | 100 % | 0 % |
| Coin flip | 98.00 % | 2.00 % |
| Wheel of Fortune | 95.37 % | 4.63 % |
| Scratch card Basic / Gold | 79.5 / 85.0 % | 20.5 / 15.0 % |
| Plinko Low / Medium / High | 96.56 / 96.57 / 96.70 % | ≈ 3.4 % |
| Dice duel vs house | 97.22 % | 2.78 % |

All randomness is decided on the server. Every RNG game has a Monte-Carlo or exact-enumeration
test that checks its RTP.

### Loan Shark and Debt Collectors

- The **Loan Shark** lives in village casinos. In the Nether he is replaced by the
  **Piglin Moneylender**. He offers loans from 100 to 50 000 chips, but the larger loans need a
  higher VIP tier. Interest is flat, charged once, and set by difficulty: 15 %, 20 % or 25 %.
  Each loan you repay on time lowers it, down to a minimum of 10 %.
- **Missing the deadline** puts you in default:
  - late fees are added every Minecraft day
  - 50 % of everything you earn goes to the debt
  - withdrawals, transfers and new loans are blocked
- **Debt Collector squads** then come to collect every day. A squad is made of Collectors, Repo Men
  with crossbows, an Accountant and a big Enforcer, and it grows with the size of the debt. They
  offer to settle first: pay in full, pay at least half, or refuse and fight.
  - If they kill you, they repossess up to half your balance and your most valuable item.
  - They **never break or place blocks**, and they never attack anyone except the debtor (or
    whoever hits them).
- **Peaceful**: no squads. Instead an **Asset Freeze** seizes part of your balance each day and
  blocks all wagering until the debt is paid.

### Chaos events and Golden Hour

- Every 5 minutes each player has an 8 % chance of a random event. Slot, wheel and scratch
  specials trigger events directly, and big wins can give a buff.
- Events:
  - good: chip shower, lucky buff, diamond rain, XP fountain
  - bad: curse (a mild debuff), mob wave
  - neutral: random teleport to a safe spot, weather change
- **Golden Hour** is server-wide and lasts 3 minutes. The net winnings of house-banked games are
  doubled, up to a cap per player, and the bank pays the bonus. It can start at sunset, from five
  scatters on a slot machine, or as a rare ambient event.
- Safety rules:
  - no events in Creative or Spectator, while you sleep, or right after you respawn
  - mob waves never contain creepers, never spawn in Peaceful or near a boss, and never spawn
    inside a claimed casino
  - teleports only land on safe ground and never on the Nether roof
  - debuffs are never lethal

### Lucky / Unlucky streak

Your streak runs from −10 to +10. It goes up with consecutive wins, down with consecutive losses,
and drifts back to 0 when you stop playing.

- On **RNG games only** (slots, wheel, plinko, scratch cards, coin flip), the streak can re-draw a
  losing outcome: up to 5 % of the time when you're lucky, and up to 3 % as "pity" when you're
  unlucky.
- The re-draw is capped per game, so the **house edge never drops below 1 %**.
- Table games (blackjack, poker, roulette, craps, dice duel) are always honest. On them the streak
  is only cosmetic.

### Last Chance

When you would die, and you have no Totem of Undying, the game flips a coin with Death.

- **Heads**: you survive in place with half health, a short burst of Resistance and Regeneration,
  and nothing dropped. It costs 10 % of your balance.
- **Tails**: you die normally.
- **Success chance**: 60 % on Peaceful and Easy, 50 % on Normal, 40 % on Hard.
- **Cooldown**: 1 Minecraft day, whichever way the coin lands.
- It never triggers on void damage, on `/kill`, or after losing a Soul Wager.
- **Hardcore**: turned off by default. You can opt in to **High Stakes** mode (see
  [Difficulty and Hardcore](#difficulty-and-hardcore)).

Last Chance cancels the death itself (Fabric `ALLOW_DEATH`); a held totem still takes priority. The High Stakes
scar (−2 max HP) is a permanent `max_health` attribute modifier.

### Casinos in the world

Casino buildings are generated only in newly generated chunks.

| Casino | Where | Highlights |
|---|---|---|
| **Grand Casino** | Overworld, a standalone building in six biome styles: Riviera, Oasis, Safari, Alpine, Ice Palace and Cherry | Art-deco palace with a neon CASINO marquee, a dome, galleries, a bar and a VIP floor. 32 games: 11 tables and 21 machines. |
| **Village casino** | ≈ 1 in 2 villages, in all five village styles | A picture-palace annex with vertical CASINO banners, a marquee canopy, multi-block tables (baccarat too), the Loan Shark and a loot chest. |
| **Piglin Parlor** | Beside 60 % of bastions | Craps, poker with bots, baccarat, slots and Plinko, the Piglin Moneylender. Dug into the terrain with a lit tunnel to open air and a back door into the bastion; nearby lava is sealed off. |
| **High Roller Lounge** | Beside 50 % of End Cities | High-limit tables and End Void slots, on the island surface at least 24 blocks from the city. |

To turn them off, disable the built-in `burmaldaholic:casinos` data pack when creating the world, or set
`worldgen.enabled`.

- **`/casino locate`** shows the nearest casino in your dimension (coordinates, distance and direction), also ones
  nobody has visited yet.
- **Multi-block tables.** Every table is a full 3×2 table with legs, padded rails and printed felt; slot machines are
  tall cabinets, the Wheel of Fortune and Plinko are upright 3×3 machines, and the cashier is a counter. Use any part
  to open it; breaking any part removes the whole table. Single-block tables in old worlds grow to full size when there
  is room.
- **House dealers** stand behind every house table and deal, collect, spin, push the craps stick and push the pot:
  humans (six looks) in the Overworld, piglins and a brute pit boss in the Nether, endermen in tuxedos in the End.
  Casinos from older versions get their dealers too.
- **In-world games:** anyone nearby sees the live game on the table: cards on the felt, chips, the roulette ball,
  dice, Plinko balls and spinning reels.
- House tables can't be broken by other players or destroyed by explosions.

Every table and machine can also be **crafted**, so you can build your own casino anywhere.

### VIP tiers and daily contracts

Your VIP tier depends on the total amount you have ever wagered, and tiers are never lost:

| Tier | Wagered | Max bet | Highlights |
|---|---|---|---|
| Bronze | 0 | 100 | Micro poker, basic scratch cards, loans of 100 and 500 |
| Silver | 25 000 | 250 | Low poker, gold scratch cards, 2 000 loan, +5 % contract rewards |
| Gold | 250 000 | 1 000 | End Void slots, high-roller tables, Mid poker, 10 000 loan, 2 % cashback |
| Platinum | 1 500 000 | 2 500 | 4 contract slots, −2 % loan interest, 3 % cashback |
| Diamond | 10 000 000 | 10 000 | High poker, 50 000 loan, 5 contract slots, 4 % cashback |
| Netherite | 50 000 000 | 50 000 | 5 % cashback, a win aura, a server-wide announcement when you reach it |

Cashback is paid each Minecraft day as a share of the house edge you paid that day, not of your
losses. So it can never make a game profitable for the player.

**Contracts** are refreshed each day. Their targets and rewards grow with your VIP tier. You can
re-roll a contract once a day for 10 chips.

### Multiplayer and player-owned casinos

- **Shared tables**: blackjack has 5 seats, poker 6, roulette 8 bettors and craps 6. Leave a table
  with the Leave button or by walking more than 8 blocks away. If you disconnect mid-round, the
  round finishes with the default action.
- **Casino Charter**: this block claims a casino with a radius of 24 blocks. It costs a 1 000-chip
  license fee, and each player can own one.
  - Tables the owner places inside the claim are paid from the owner's **bankroll**, so the owner
    earns the house edge.
  - A reservation rule makes sure the bankroll can always cover the worst case of every open bet.
    If the bankroll runs dry, the casino closes with "the house is broke".
  - Owners set the limits for each table and can't play at their own tables.
  - Linked tables are protected from other players and from explosions.
- **Dice duels between players**, and server-wide announcements for big wins and jackpots.

### PvP modes

Players can also bet **against each other** instead of the house. The stakes form a pot, and the
winner takes it minus a small house cut (3 % by default). The house never takes a side, so every
mode is fair. Challenges and lobbies live on the PvP page of the Casino Menu.

| Mode | Players | How it plays |
|---|---|---|
| **Coin Flip Duel** | 2 | Call it and flip; the loser may shout "Double or nothing!" |
| **Wheel Party** | 2–8 | Buy a slice of the wheel (bigger stake, bigger slice); one shared spin picks the winner |
| **Plinko Battle** | 2–6 | Everyone drops at the same risk; the bins are points |
| **Scratch Showdown** | 2–6 | Everyone scratches a card, one cell at a time, together |
| **Slot Showdown** | 2–6 | Everyone spins the same machine; wins are points, with twists like HOT symbols and KABOOM |

### Seats & Bots

Every multi-seat table (poker, Ultimate Texas Hold'em, baccarat, blackjack, roulette, craps) and
every PvP match has a seating policy: **humans only**, **bots only** (you and bots) or **mixed**
(bots fill the empty seats and give them up to arriving players). Bots come in three levels,
**Easy, Normal and Hard** (or a mix). Each bot wears a nameplate with its level, and thinking dots
show while it decides.

### Animated screens

Every game has a redesigned, animated screen: cards are dealt and flipped, chips stack and slide,
reels spin and tumble, and wins are celebrated. The screens take on the look of the casino where the
table stands. The HUD chip counter, the Casino Menu ledger, the cashier and the Loan Shark screens
use the same style.

- **Every screen fits every resolution.** All games share one framework (a top bar, the play area and a
  dock with your bet and actions). Each layout is checked from 320×240 up to 960×540 GUI pixels, which
  covers 4K with GUI scale Auto, and big windows get a roomier layout instead of a zoomed-in copy. Every
  disabled button says why.
- **Smooth on a bad connection.** Slot reels, the roulette ball, craps dice and dealt cards keep moving
  while the result travels, then settle on it without snapping or jumping back.
- Realistic motion: the roulette ball rolls and bounces into its pocket, craps dice tumble off the back
  wall, dice duels use 3D dice, and Plinko balls bounce off the pegs. Blackjack shows a shoe bar (dealt,
  remaining, cut card), so you can count cards yourself.

### Achievements

65 achievements — first bet, natural blackjack, royal flush, a Major or Grand jackpot, both extremes of the streak,
surviving through Last Chance, paying off a loan, owning a casino, PvP wins, private and bot tables, and more — are
real advancements, in their own "Burmaldaholic" tab.

---

## Compatibility

| Component | Version |
|---|---|
| Minecraft | **26.3** (`~26.3`: 26.3 and its 26.3.x patches). 26.2 is no longer supported. |
| Fabric Loader | **0.19.5** or newer |
| Fabric API | **0.161.0+26.3** (required) |
| Mod Menu | Optional: 21.0.0-beta.1. Adds the config screen. |
| Java | 25 (required by Minecraft 26.x) |

The mod must be installed on **both the server and every client**, because it adds blocks,
screens and items.

---

## Installation

### Java (Fabric)

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5+ for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version in the
   `mods` folder.
3. Put `burmaldaholic-<version>.jar` in the `mods` folder.
4. *(Optional)* Add [Mod Menu](https://modrinth.com/mod/modmenu) for an in-game config screen.
5. **On a server**, put the same jar and Fabric API in the server's `mods` folder. Every player
   needs the mod too.
6. **Casino mode is on by default** — new worlds and servers open the casino right away. To create a
   world without it, press **"Casino Mode: ON"** on the **Game** tab (right below Difficulty) so it
   reads **OFF**; the choice is saved with the world. An operator can switch it any time with
   `/casino mode on|off` (`/casino mode status` shows the current state).

---

## Turning casino mode on and off

Casino mode is a per-world switch. On **Java** it is **on by default** (since v0.1.2): every player
gets the starting chips and Casino Card on first join. Turn it off with the **Casino Mode** button
right below **Difficulty** when you create the world, or in an existing world with `/casino mode off`
(the choice is saved and kept). Worlds from v0.1.1 that were left off are switched on once by the
update — an operator can turn them back off with `/casino mode off`. When it's off, the mod goes
**dormant**: no HUD, earning, chaos, debt or Last Chance, and tables say "Casino mode is off". All
saved data (balances, loans, casinos) is kept untouched until you turn it back on.

| When | How |
|---|---|
| At world creation | **Create World → Game** tab → **"Casino Mode: ON/OFF"** button below Difficulty (**on** by default). Saved with the world in `data/burmaldaholic/mode.dat`; it is not a game rule. |
| Existing world / later (operators) | `/casino mode on\|off\|status` (permission level 2, like /gamerule; in single-player needs cheats) |

Operators have `/casino …` (alias `/burmaldaholic`, permission level 2) for casino mode, config, balances,
debt, chaos and jackpots.

Every player can run `/casino locate`: the nearest casino of your dimension (village casino, Piglin Parlor or
High Roller Lounge) with its coordinates, distance and compass direction — click the coordinates to copy them
(operators get a ready `/tp` command). Casinos nobody has visited yet are found too. Non-operators wait
`worldgen.locateCooldownSeconds` (30 s by default) between searches.

---

## Difficulty and Hardcore

Burmaldaholic **never changes** the world's difficulty, the Hardcore flag, `keepInventory` or any
other vanilla game rule. It only *reads* the difficulty to scale its own mechanics:

| Mechanic | Peaceful | Easy | Normal | Hard | Hardcore |
|---|---|---|---|---|---|
| Mob-kill rewards | — | ×1.0 | ×1.0 | ×1.25 | ×1.25 |
| Chaos mob wave | never | 3 mobs | 4 mobs | 6 mobs | 6 mobs |
| Last Chance success | 60 % | 60 % | 50 % | 40 % | off by default; High Stakes 50 % |
| Last Chance cost | 10 % of balance | 10 % | 10 % | 10 % | all chips + 1 permanent heart |
| Loan interest | 15 % | 15 % | 20 % | 25 % | 25 % |
| Late fee per day | 5 % | 5 % | 10 % | 15 % | 15 % |
| Debt Collectors | Asset Freeze instead | smaller, weaker squad | normal | bigger, tougher squad | as Hard, and a death to them is final |

**Hardcore** works fully and respects vanilla Hardcore rules:

- Last Chance is **off** by default. The opt-in **High Stakes** mode (`lastChance.hardcoreMode =
  HIGH_STAKES`) works like this:
  - Heads (50 %) saves you, but costs your whole balance, all the chips you carry, and 2 max HP
    for good.
  - Tails is a normal Hardcore death.
  - The cooldown is 5 days, and you need at least 100 chips and 8 max HP to qualify.
- Last Chance never saves you from Debt Collectors in Hardcore.
- **Soul Wager** is a coin flip where you stake your life. It is Hardcore-only, off by default
  (`wager.hardcoreSoulWager`), and asks for a double confirmation.
- Heart wagers only ever take hearts **temporarily**, for 1 Minecraft day.

---

## Configuration

Hundreds of settings cover payouts, odds, timers, loan terms, chaos weights, worldgen chances and
more. Every key, with its type, default and allowed range, is listed in
[`docs/design/CONFIG.md`](docs/design/CONFIG.md). Out-of-range values are clamped.

| | |
|---|---|
| Where it lives | `config/burmaldaholic.json` (nested JSON), with an optional per-world override in `<world>/data/burmaldaholic_config.json` |
| Single player | **Mod Menu** → Burmaldaholic → config screen |
| Server / commands | `/casino config get\|set\|reset <key> [value]`, `/casino config reload` |
| Casino-mode flag | Saved with the world (the config file never overrides it) |

Example:

```json
{ "economy": { "ore": { "diamond": 25 } }, "chaos": { "enabled": false } }
```

---

## Languages

The mod ships in **English** and **Russian** (Русский). The language follows your game client's
language automatically. You don't need to set anything, and on a server each player sees their
own language.

- Every player-facing string, including counted nouns with correct Russian plurals (1 фишка /
  2 фишки / 5 фишек), comes from one master file, [`docs/design/STRINGS.md`](docs/design/STRINGS.md).
- The rules for keys and translation are in
  [`docs/design/LOCALIZATION.md`](docs/design/LOCALIZATION.md).

---

## Building from source

| Requirements | Command | Output |
|---|---|---|
| **JDK 25**. The Gradle 9.7.1 wrapper is included. | `cd java && ./gradlew build` | `java/build/libs/burmaldaholic-<version>.jar` |

Useful extras:

```bash
./gradlew runClient [-PwithModMenu]  # dev client (run/<mc>/client)
./gradlew runServer                  # dev server
./gradlew build -Pmod_version=1.2.3  # set the version (also read from $MOD_VERSION)
```

Generated textures (pixel art from code) are committed. After changing art, regenerate them with the asset generator
in [`tools/`](tools/) (**Node.js 22**, ≥ 22.12):

```bash
cd tools && npm ci
npm run gen:assets     # rewrite the generated textures under java/
npm run check:assets   # fail if a committed texture is stale
npm test               # generator tests
```

The architecture notes are in [`docs/architecture/java.md`](docs/architecture/java.md).

## Testing

| Command | What it runs |
|---|---|
| `./gradlew build` | Compile, unit tests (including the golden-vector tests), server GameTests, lang/asset checks |
| `./gradlew test` | Fast JUnit unit tests: game math, RTP, hand evaluators |
| `./gradlew runGameTest` | Headless server GameTests |
| `xvfb-run -a ./gradlew runClientGameTest` | Real-client tests. These need a display and are not part of `build`. |
| `cd tools && npm run check:assets && npm test` | Generated textures are up to date; asset generator tests |

CI runs the build for Minecraft 26.3 on every pull request, and the asset check when the generator or the
textures change.

## Releases

Pushing a SemVer tag builds and publishes the mod. The runbook is [`docs/ci/RELEASING.md`](docs/ci/RELEASING.md).
What changed in each version is in [`CHANGELOG.md`](CHANGELOG.md).

```bash
git tag -a v1.2.3 -m "v1.2.3" && git push origin v1.2.3
```

The **Release** workflow:

1. builds the jar, with its tests
2. creates a **GitHub Release** with the jar and release notes generated from the merged PRs
   (grouped by their labels)
3. uploads the jar to **CurseForge**, if `CURSEFORGE_TOKEN` and the project ID are configured

Tags like `-beta.N` / `-rc.N` publish as beta, and `-alpha.N` as alpha. A CurseForge dry run is
available from the Actions tab.

The pipeline is built from **reusable workflows** (`workflow_call`) shared, file for file, with
[Enchantaholic](https://github.com/nezo32/enchantaholic); other Minecraft projects can call them too.
See [`docs/ci/REUSABLE_RELEASE_PIPELINE.md`](docs/ci/REUSABLE_RELEASE_PIPELINE.md).

## Project layout

```
java/                   Fabric mod (Gradle, Loom 1.18, Mojang names)
  src/main|client/      server/common and client code, one package per module
  src/test|gametest/    JUnit unit tests, Fabric GameTests
  src/main/lang/        per-module EN/RU lang fragments (generated from STRINGS.md)
  tools/                lang / advancement generators
  src/test/resources/   test fixtures, incl. golden vectors (fx/vectors, pvp)
tools/                  Node asset generator (tools/assets): the mod's generated textures, vitest
docs/design/            game design, config reference, UI, localization, strings (EN+RU)
docs/architecture/      architecture and developer guides
docs/ci/                release runbook (RELEASING.md), reusable pipeline guide
.github/workflows/      ci.yml, release.yml, labeler.yml, reusable-*.yml (shared with Enchantaholic)
scripts/                CurseForge upload script and its tests
CONTRIBUTING.md         branch flow, PR rules, local checks
```

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full rules.

1. Branch from `main` as `<type>/<kebab-name>`: `feature/<topic>` (or `feat/`, `fix/`, `hotfix/`,
   `chore/`, `docs/`, `ci/`, `build/`, `refactor/`, `perf/`, `test/`, `release/`).
2. Open a pull request to `main`. CI runs the checks, filtered by the paths you changed, plus a
   branch-name check. The aggregate **`ci-ok`** check must be green. PRs are
   squash-merged.
3. Write the PR title as an imperative sentence for players ("Add roulette table"): it becomes a
   line of the release notes. The branch prefix sets the label that picks its section.
4. Game rules and numbers must match [`GAME_DESIGN.md`](docs/design/GAME_DESIGN.md). Any new player-facing text goes into [`STRINGS.md`](docs/design/STRINGS.md) in
   **both EN and RU**. Hard-coded strings fail the build.
5. Each feature module owns its own folders. Read the ownership rules in
   [`docs/architecture/java.md`](docs/architecture/java.md) before you start.

## License

[MIT](LICENSE) © 2026 nezo
