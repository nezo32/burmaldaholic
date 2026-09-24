# Burmaldaholic

**English** · [Русский](README.ru.md)

**Burmaldaholic** (Russian: **«Бурмалдоголик»**) turns a survival world into a casino economy.
You earn chips by mining, fighting, trading and doing daily contracts. You spend them on blackjack,
Texas Hold'em, slots, roulette, craps and a handful of quick games. If you run short, the Loan Shark
will lend you some, and his Debt Collectors will come for you if you don't pay him back. On top of
that, chaos events rain diamonds or mobs on you, and Last Chance lets you flip a coin with Death.

It ships for both editions of Minecraft 26.2–26.3, and the two follow the same rules and numbers:

| Edition | Package | Folder |
|---|---|---|
| Java | Fabric mod (`burmaldaholic-<version>.jar`) | [`java/`](java/) |
| Bedrock | Add-on (`Burmaldaholic-<version>.mcaddon`, behavior pack + resource pack) | [`bedrock/`](bedrock/) |

Both editions are fully localized in English and Russian.

> **No real money.** Chips exist only inside your Minecraft world. You can't buy them, sell them
> or cash them out, and the mod has no purchases, ads or external services. It is a game mechanic
> that parodies gambling, and the house wins in the long run, just like a real casino.

---

## Contents

- [Features](#features)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Turning casino mode on](#turning-casino-mode-on)
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
- **Chip items.** Physical chips worth 1, 5, 25, 100 and 500. You can trade, store, drop or steal
  them. The **Cashier** block converts between chips and your balance and exchanges emeralds:
  1 emerald buys 8 chips (9 at Gold VIP) and 10 chips sell for 1 emerald. The Nether cashier also
  takes gold ingots.
- **Ways to earn:**
  - ores, from 1 chip for coal up to 20 for diamond and 50 for ancient debris
  - hostile mobs, from 2 up to 1 000 for the first Ender Dragon, with diminishing returns so mob
    farms stop paying
  - villager trades, capped at 200 chips per day
  - **daily contracts**: 3–5 tasks a day, such as "Mine 24 iron ore" or "Win 3 blackjack hands"
- **Pawn stakes.** On coin flip, dice duel, the wheel and even-money roulette bets you can stake
  items, XP levels or even **temporary max hearts** instead of chips.

### Games

| Game | Key rules |
|---|---|
| **Blackjack** | 6 decks, dealer stands on soft 17, blackjack pays 3:2, double after split, re-split up to 4 hands, insurance and even money. Up to 5 seats. |
| **Texas Hold'em** | No-Limit, 6-max cash game with side pots. Four stake levels (1/2 up to 100/200). Empty seats can be filled by **bots** at three skill tiers (Fish, Regular, Shark). The house takes a 5 % rake from pots with 2 or more human players, capped at 3 BB, and only if the hand saw a flop. |
| **Slots** | Three machines: Copper Bandit (1 line), Golden Reels (3 lines) and Netherite High Roller (5 lines, Gold VIP). Golden Reels and Netherite have **progressive jackpots**. Special symbols start chaos events, and the Clock symbol starts Golden Hour. |
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
| Slots: Copper Bandit / Golden Reels / Netherite High Roller | 89.76 / 93.71 / 96.04 % | 10.24 / 6.29 / 3.96 % |
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
  doubled, up to a cap per player, and the bank pays the bonus. It can start at sunset, from the
  Netherite slot Clock symbol, or as a rare ambient event.
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

The two editions do this differently, but the result is the same:

| | Java | Bedrock |
|---|---|---|
| How it saves you | Cancels the death (Fabric `ALLOW_DEATH`). A held totem still takes priority. | Cancels the lethal hit before it lands (stable `beforeEvents.entityHurt`), so you never actually die. |
| High Stakes scar (−2 max HP) | A permanent `max_health` attribute modifier | The script API can't change max health, so healing and health are capped instead |

### Casinos in the world

Casino buildings are generated only in newly generated chunks.

| Structure | Where | Contents |
|---|---|---|
| **Lucky Villager** (village casino) | ≈ 1 in 3 villages. Five village styles. | Cashier, blackjack, roulette, 3 Copper Bandits, a Golden Reels machine, Wheel of Fortune, Loan Shark, Croupier, loot chest |
| **Piglin Parlor** | 30 % of bastions | Craps, poker (with bots), 2 Golden Reels, Plinko, Nether cashier, Piglin Dealers, Piglin Moneylender, loot |
| **High Roller Lounge** | 20 % of End Cities, on a tower's top floor | 2 Netherite slots, high-roller blackjack and roulette, cashier, Shulker Croupier, loot |

Where casinos appear differs between editions:

| | Java | Bedrock |
|---|---|---|
| Village casinos | Built **inside** villages, as one of the village's houses | Built **next to** villages. Add-ons can't extend vanilla village layouts, so a script finds the village and builds on a free lot nearby. |
| Piglin Parlor / High Roller Lounge | Part of the bastion or End City | Placed next to the bastion or on the End City in the same way |
| Turning it off | Disable the built-in `burmaldaholic:casinos` data pack when creating the world, or set `worldgen.enabled` | Set `worldgen.enabled` |

Every table and machine can also be **crafted**, so you can build your own casino anywhere.

### VIP tiers and daily contracts

Your VIP tier depends on the total amount you have ever wagered, and tiers are never lost:

| Tier | Wagered | Max bet | Highlights |
|---|---|---|---|
| Bronze | 0 | 100 | Micro poker, basic scratch cards, loans of 100 and 500 |
| Silver | 5 000 | 250 | Low poker, gold scratch cards, 2 000 loan, +5 % contract rewards |
| Gold | 25 000 | 1 000 | Netherite slots, high-roller tables, Mid poker, 10 000 loan, 2 % cashback |
| Platinum | 100 000 | 2 500 | 4 contract slots, −2 % loan interest, 3 % cashback |
| Diamond | 500 000 | 10 000 | High poker, 50 000 loan, 5 contract slots, 4 % cashback |
| Netherite | 2 500 000 | 50 000 | 5 % cashback, a win aura, a server-wide announcement when you reach it |

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

### Achievements

The same 35 achievements exist on both editions: first bet, natural blackjack, royal flush,
jackpot, both extremes of the streak, surviving through Last Chance, paying off a loan, owning a
casino, and more.

- **Java**: real advancements, in their own "Burmaldaholic" tab.
- **Bedrock**: add-ons can't add advancements, so there is an **Achievements** page in the Casino
  Menu, with a toast when you unlock one.

---

## Compatibility

### Java Edition

| Component | Version |
|---|---|
| Minecraft | **26.2 – 26.3**. One jar covers both (`>=26.2 <26.4`). |
| Fabric Loader | **0.19.5** or newer |
| Fabric API | **0.161.0+26.2** / **0.161.0+26.3** (required) |
| Mod Menu | Optional: 20.0.2 (26.2) / 21.0.0-beta.1 (26.3). Adds the config screen. |
| Java | 25 (required by Minecraft 26.x) |

The mod must be installed on **both the server and every client**, because it adds blocks,
screens and items.

### Bedrock Edition

| Component | Version |
|---|---|
| Minecraft Bedrock | **1.26.30 or newer** (Bedrock 26.30, the same drop as Java 26.2). Also runs on 1.26.40 and 1.26.50 (the same drop as Java 26.3). |
| Script API | Stable `@minecraft/server` 2.8.0 and `@minecraft/server-ui` 2.1.0 only |
| Experiments | **None needed.** No beta APIs, no experimental toggles. |

All the game logic runs as world-side script. The add-on works in single player, in
multiplayer worlds, and on Bedrock Dedicated Server.

---

## Installation

### Java (Fabric)

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5+ for Minecraft 26.2 or 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version in the
   `mods` folder.
3. Put `burmaldaholic-<version>.jar` in the `mods` folder.
4. *(Optional)* Add [Mod Menu](https://modrinth.com/mod/modmenu) for an in-game config screen.
5. **On a server**, put the same jar and Fabric API in the server's `mods` folder. Every player
   needs the mod too.
6. **Turn casino mode on** — it is off by default. When creating a world, press **"Casino Mode:
   OFF"** on the **Game** tab (right below Difficulty) so it reads **ON**; the choice is saved with
   the world. For an existing world or a server, an operator runs `/casino mode on`
   (`/casino mode status` shows the current state).

### Bedrock (add-on)

1. Open `Burmaldaholic-<version>.mcaddon`: double-click it on Windows, or use "Open with
   Minecraft" on mobile. Minecraft imports both packs.
2. **Create New World** → **Behavior Packs** → activate **Burmaldaholic**. The resource pack is
   activated with it, because the two packs depend on each other. Make sure both are listed as
   active.
3. Press the pack's **gear (settings) button** and check the **Casino mode** toggle. It is on by
   default.
4. Create the world. The first operator who joins gets a short **Setup** form: casino mode,
   Last Chance in Hardcore, and chaos events.

**Bedrock Dedicated Server (1.26.30+):**

1. Copy the two packs into `behavior_packs/` and `resource_packs/`.
2. List them in `worlds/<world>/world_behavior_packs.json` and `world_resource_packs.json`.
3. Set `texturepack-required=true` in `server.properties`.

The pack UUIDs are in [`bedrock/pack.json`](bedrock/pack.json).

If the pack settings button doesn't show up on your version, nothing is lost: casino mode
defaults to **on**, and operators can still change it with `/burmaldaholic:casino`.

---

## Turning casino mode on

Casino mode is a per-world switch. On **Java** it is **off by default**: turn it on with the
**Casino Mode** button right below **Difficulty** when you create the world, or in an existing world
with `/casino mode on` (everyone then gets the starting chips and Casino Card). On **Bedrock** it
is on by default. When it's off, the mod goes
**dormant**: no HUD, earning, chaos, debt or Last Chance, and tables say "Casino mode is off". All
saved data (balances, loans, casinos) is kept untouched until you turn it back on.

| | Java | Bedrock |
|---|---|---|
| At world creation | **Create World → Game** tab → **"Casino Mode: ON/OFF"** button below Difficulty (**off** by default). Saved with the world in `data/burmaldaholic/mode.dat`; it is not a game rule. | The **Casino mode** pack setting (the gear button on the behavior pack), then the first-op **Setup** form |
| Existing world / later (operators) | `/casino mode on\|off\|status` (permission level 2, like /gamerule; in single-player needs cheats) | `/burmaldaholic:casino` → admin form → World settings, or `/scriptevent burmaldaholic:admin casino_mode true\|false` |

Useful player commands on Bedrock: `/burmaldaholic:menu`, `/burmaldaholic:balance`,
`/burmaldaholic:loan`, `/burmaldaholic:vip`, `/burmaldaholic:contracts`.

Java operators have `/casino …` (alias `/burmaldaholic`, permission level 2) for casino mode, config, balances,
debt, chaos and jackpots.

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
- On Bedrock you pick this in the first-op Setup form. On Java it's a config key.
- Last Chance never saves you from Debt Collectors in Hardcore.
- **Soul Wager** is a coin flip where you stake your life. It is Hardcore-only, off by default
  (`wager.hardcoreSoulWager`), and asks for a double confirmation.
- Heart wagers only ever take hearts **temporarily**, for 1 Minecraft day.

---

## Configuration

About 355 settings cover payouts, odds, timers, loan terms, chaos weights, worldgen chances and
more. Every key, with its type, default and allowed range, is listed in
[`docs/design/CONFIG.md`](docs/design/CONFIG.md). Both editions use the same key names, and
out-of-range values are clamped.

| | Java | Bedrock |
|---|---|---|
| Where it lives | `config/burmaldaholic.json` (nested JSON), with an optional per-world override in `<world>/data/burmaldaholic_config.json` | A world dynamic property holding only the values you changed |
| Single player | **Mod Menu** → Burmaldaholic → config screen | **Casino Card → Admin → World settings**, or `/burmaldaholic:casino`, with typed controls for each module |
| Server / commands | `/casino config get\|set\|reset <key> [value]`, `/casino config reload` | `/scriptevent burmaldaholic:config set\|reset\|get <key> [value]` |
| Casino-mode flag | Game rule (the config file never overrides it) | Pack setting / admin form |

Example (Java):

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
- Both editions use the same translation keys. The rules for keys and translation are in
  [`docs/design/LOCALIZATION.md`](docs/design/LOCALIZATION.md).

---

## Building from source

| Edition | Requirements | Command | Output |
|---|---|---|---|
| Java | **JDK 25**. The Gradle 9.7.1 wrapper is included. | `cd java && ./gradlew build` | `java/build/libs/burmaldaholic-<version>.jar` |
| Bedrock | **Node.js 22** (≥ 22.12) | `cd bedrock && npm ci && npm run build` | `bedrock/dist/Burmaldaholic-<version>.mcaddon` (unpacked in `bedrock/build/BP`, `bedrock/build/RP`) |

Useful extras:

```bash
# Java
./gradlew build -Pmc=26.3            # compile + test against 26.3 instead of 26.2
./gradlew runClient [-PwithModMenu]  # dev client (run/<mc>/client)
./gradlew runServer                  # dev server
./gradlew build -Pmod_version=1.2.3  # set the version (also read from $MOD_VERSION)

# Bedrock
npm run build:dev && npm run deploy  # unminified build copied into com.mojang development packs (set MC_COM_MOJANG)
VERSION=1.2.3 npm run build          # set the pack version (MOD_VERSION also works)
```

The architecture notes are in [`docs/architecture/java.md`](docs/architecture/java.md) and
[`docs/architecture/bedrock.md`](docs/architecture/bedrock.md).

## Testing

| Edition | Command | What it runs |
|---|---|---|
| Java | `./gradlew build` | Compile, unit tests, server GameTests, lang/asset checks, and a linkage check against 26.3 |
| Java | `./gradlew test` | Fast JUnit unit tests: game math, RTP, hand evaluators |
| Java | `./gradlew runGameTest` | Headless server GameTests |
| Java | `xvfb-run -a ./gradlew runClientGameTest` | Real-client tests. These need a display and are not part of `build`. |
| Bedrock | `npm test` | Vitest unit tests for all the pure game logic |
| Bedrock | `npm run lint` | `tsc` against the stable 2.8.0 typings (catches beta APIs), ESLint, architecture and hard-coded-string checks, pack-schema validation |
| Bedrock | `npm run check:lang` | EN/RU key and placeholder parity |

CI runs the Java build for both 26.2 and 26.3, and the Bedrock build, test and lint, on every pull
request.

## Releases

Pushing a SemVer tag builds and publishes both editions. The runbook is [`docs/ci/RELEASING.md`](docs/ci/RELEASING.md).

```bash
git tag -a v1.2.3 -m "v1.2.3" && git push origin v1.2.3
```

The **Release** workflow:

1. builds the jar and the `.mcaddon`, with their tests
2. creates a **GitHub Release** with both files and release notes generated from the merged PRs
   (grouped by their labels)
3. uploads both files to **CurseForge**, if `CURSEFORGE_TOKEN` and the project IDs are configured

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
  tools/                lang / asset / advancement generators
bedrock/                Bedrock add-on (TypeScript, esbuild, vitest)
  src/core/             economy, config, HUD, forms, tables, admin
  src/games/            blackjack, poker, slots, roulette, craps, extras
  src/{loan,chaos,lastchance,worldgen,vip,multiplayer}/
  packs/<module>/       per-module BP/RP content, merged by the build
  lang/<module>/        per-module en_US / ru_RU fragments
  pack.json             pack identity: UUIDs, min engine, script module versions
docs/design/            game design, config reference, UI, localization, strings (EN+RU)
docs/architecture/      per-edition architecture and developer guides
docs/ci/                release runbook (RELEASING.md), reusable pipeline guide
.github/workflows/      ci.yml, release.yml, labeler.yml, reusable-*.yml (shared with Enchantaholic)
scripts/                CurseForge upload script and its tests
CONTRIBUTING.md         branch flow, PR rules, local checks
```

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full rules.

1. Branch from `main` as `<type>/<kebab-name>`: `feature/<topic>` (or `feat/`, `fix/`, `hotfix/`,
   `chore/`, `docs/`, `ci/`, `build/`, `refactor/`, `perf/`, `test/`, `release/`).
2. Open a pull request to `main`. CI runs the checks for each edition, filtered by the paths you
   changed, plus a branch-name check. The aggregate **`ci-ok`** check must be green. PRs are
   squash-merged.
3. Write the PR title as an imperative sentence for players ("Add roulette table"): it becomes a
   line of the release notes. The branch prefix sets the label that picks its section.
4. Game rules and numbers must match [`GAME_DESIGN.md`](docs/design/GAME_DESIGN.md) on both
   editions. Any new player-facing text goes into [`STRINGS.md`](docs/design/STRINGS.md) in
   **both EN and RU**. Hard-coded strings fail the build.
5. Each feature module owns its own folders. Read the ownership rules in the architecture doc for
   your edition before you start.

## License

[MIT](LICENSE) © 2026 nezo
