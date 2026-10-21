# Burmaldaholic

**Mine. Bet. Regret nothing.**

A full casino game mode for Minecraft Java Edition (Fabric). Earn chips in survival, then spend them at real-rules
tables in casinos spread across the Overworld, the Nether and the End. Play with friends, against friends, or with
bots in the empty seats.

> **Play money only.** Chips exist only inside your world. You can't buy them with real money, sell them or cash them
> out. The mod has no purchases, ads or external services. It is a game mechanic that parodies gambling, and like a
> real casino, the house wins in the long run.

![screenshot: grand casino at night](TODO)

---

## Why Burmaldaholic?

- **Real rules, real tables.** Blackjack with splits and insurance, No-Limit Texas Hold'em with side pots, Ultimate
  Texas Hold'em, baccarat and chemin de fer, European roulette, full craps with odds, three 243-ways video slots,
  plus Plinko, Wheel of Fortune, scratch cards, coin flip and dice duels.
- **Casinos in all three dimensions.** A Grand Casino in six biome styles, village casinos, a Piglin Parlor beside
  bastions and a High Roller Lounge beside End Cities. `/casino locate` points you to the nearest one.
- **Tables you can see from across the room.** Every table is a real 3×2 multi-block table, and machines stand
  upright. A house dealer stands behind each table, and the cards, chips, ball and dice play out on the felt in the
  world.
- **Built for multiplayer.** Shared tables, PvP modes where players bet against each other, and bots at three skill
  levels that fill empty seats.
- **A whole economy.** VIP tiers, comp points, cashback, a daily bonus, a chip store and free-bet tokens. Borrow from
  the Loan Shark, and his Debt Collectors will come for you if you don't pay him back.
- **Smooth on a bad connection.** Reels, the roulette ball, dice and dealt cards keep moving while the result is on
  its way, then settle on it without jumping.
- **Sharp at every resolution.** From 1280×720 to 4K with GUI scale Auto, every screen uses a layout made for its
  size.

![screenshot: blackjack table with dealer and friends](TODO)

---

## The games

All randomness is decided on the server. Every random game has an exact-enumeration or Monte-Carlo test that checks
its return to player (RTP).

| Game | Highlights |
|---|---|
| **Blackjack** | 6-deck shoe, dealer stands on soft 17, blackjack pays 3:2, double after split, re-split up to 4 hands, insurance and even money. Up to 5 seats. A **shoe bar** shows dealt and remaining cards, the cut card and the discard tray, so you can count cards yourself. No count is shown for you. |
| **Texas Hold'em** | No-Limit, 6-max cash game with side pots and four stake levels. Bots at three skill levels can fill seats. Rake is 5 %, only on pots with 2 or more human players that saw a flop. |
| **Ultimate Texas Hold'em** | Play against the dealer: Ante + Blind, one Play bet (×4 preflop, ×2 on the flop, ×1 at the river), plus an optional Trips bet. A player-banked table is available too. |
| **Baccarat** | Punto Banco from an 8-deck shoe: Player 1:1, Banker 0.95:1, Tie 8:1, Player and Banker Pair 11:1. Undo takes back one chip at a time. Also includes **Chemin de fer**, where players hold the bank. |
| **European roulette** | Single zero, every standard inside and outside bet. The ball moves with real physics: it rolls, bounces off the frets and drops into its pocket. |
| **Craps** | Pass / Don't Pass, Come / Don't Come, Field and free odds up to 3-4-5×. The shooter rotates, and the dice tumble and bounce off the back wall. |
| **Video slots** | Three themed 5×3, 243-ways machines: **Overworld Riches** (free spins, Treasure Hunt bonus), **Nether Inferno** (tumbling reels, multiplier ladder, Piglin's Hoard hold-and-spin) and **End Void** (sticky expanding wilds, Dragon Wheel bonus). |
| **Plinko** | 12 rows with Low, Medium and High risk. The edge bins pay up to 170×. Balls bounce off the pegs with real physics, and you can drop several at once. |
| **Wheel of Fortune** | 54 segments, from Bust up to 10×. |
| **Scratch cards** | Basic and Gold cards, scratched one cell at a time. |
| **Coin flip and dice duel** | Quick bets against the house, or against another player for an escrowed pot. |

### Slots: about 98 % RTP and a clear jackpot ladder

- House machines return about **98.4 %**: Overworld Riches 98.37 %, Nether Inferno 98.42 %, End Void 98.41 %.
- Each machine has four progressive jackpots: **Mini, Minor, Major and Grand**. The paytable shows where each one
  comes from and its odds, and hovering a jackpot meter shows the same.
- The **jackpot gem** can land on any paid base spin and award a Mini or Minor jackpot, so a Mini comes about once in
  2 000 spins. Major and Grand are won in the bonus games.
- Your bet reads as coin × multiplier ("Coin 5 × 4"), and every win pays a multiple of your total bet.
- Autoplay, Turbo and bonus buys are available. Bonus buys are priced so the buy RTP stays at about 98 %.

![screenshot: nether inferno tumble with multiplier](TODO)

### Default house edge

| Game | RTP |
|---|---|
| Blackjack (basic strategy) | 99.59 % |
| Baccarat Banker / Player | 98.94 % / 98.76 % |
| Craps Pass / Don't Pass (Odds: 100 %) | 98.59 % / 98.64 % |
| Ultimate Texas Hold'em (optimal play) | 97.82 % of the Ante |
| Slots (house machines) | ≈ 98.4 % |
| Coin flip | 98.00 % |
| Roulette | 97.30 % |
| Plinko | ≈ 96.6 % |
| Wheel of Fortune | 95.37 % |

Every number can be changed in the config.

---

## Casinos in every dimension

Casinos generate only in new chunks. You can also craft every table and machine and build your own.

| Casino | Where | Highlights |
|---|---|---|
| **Grand Casino** | Overworld, a standalone building in six biome styles: Riviera, Oasis, Safari, Alpine, Ice Palace and Cherry | Art-deco palace with a neon CASINO marquee, a dome, galleries, a bar and a VIP floor. 32 games: 11 tables and 21 machines. |
| **Village casino** | About 1 in 2 villages, in all five village styles | A picture-palace annex with vertical CASINO banners, a marquee canopy and multi-block tables. |
| **Piglin Parlor** | Beside 60 % of bastions | Craps, poker with bots, baccarat, slots and Plinko. It is dug into the terrain with a lit tunnel to open air and a back door into the bastion, and nearby lava is sealed off. |
| **High Roller Lounge** | Beside 50 % of End Cities | High-limit tables and End Void slots, on the island surface at least 24 blocks from the city. |

- **`/casino locate`** shows the nearest casino in your dimension, with its coordinates, distance and direction. Click
  the coordinates to copy them. It also finds casinos nobody has visited yet.
- **House dealers** stand behind every house table. They deal, collect, spin the roulette wheel, push the craps stick
  and push the pot. Humans in six looks work the Overworld, piglins (and a brute pit boss) work the Nether, and
  endermen in tuxedos work the End.
- A **casino merchant** beside each cashier opens the chip store.
- House tables are protected: other players can't break them, and explosions don't destroy them.

![screenshot: piglin parlor interior with piglin dealers](TODO)
![screenshot: high roller lounge on the end island](TODO)

---

## Tables that live in the world

- Tables are full **3×2 multi-block** tables with legs, padded rails and printed felt. Slot machines are tall
  cabinets with a topper. The Wheel of Fortune and Plinko are upright 3×3 machines, and the cashier is a counter.
- Use any part of a table to open it. Breaking any part removes the whole table and drops it as one item. Tables
  from older worlds grow to full size when there is room, or stay compact.
- Anyone nearby sees the game on the table: cards on the felt, chip stacks, the roulette ball, rolling dice, Plinko
  balls and spinning reels. Bots wear nameplates with their skill level.

![screenshot: craps table mid-roll, seen in the world](TODO)

---

## Multiplayer, PvP and bots

- **Shared tables:** blackjack has 5 seats, poker 6, roulette 8 bettors and craps 6. If you disconnect mid-round, the
  round finishes with the default action.
- **PvP modes:** Coin Flip Duel, Dice Duel, Wheel Party, Plinko Battle, Scratch Showdown and Slot Showdown. Players bet
  against each other for a pot, and the house only takes a small cut (3 % by default).
- **Seats and bots:** every multi-seat table and PvP match can be humans only, bots only or mixed. Bots come in Easy,
  Normal and Hard.
- **Player-owned casinos:** claim a casino with a Casino Charter. Your bankroll pays your tables, and you earn the
  house edge.
- Server-wide announcements for big wins and jackpots.

![screenshot: pvp wheel party with four players](TODO)

---

## Economy, VIP and comps

- **Earn chips** by mining ores, killing hostile mobs (with diminishing returns for farms), trading with villagers and
  doing daily contracts.
- **Chips:** 1, 5, 25, 100, 500, and the new high-roller chips 1 000, 5 000 and 25 000.
- **Cashier:** exchange emeralds, emerald blocks, diamonds, iron blocks, gold ingots (Nether) and netherite scrap
  (Nether, Diamond VIP), in steps of ×1 or ×64, or All / Max. Shift-click exchanges 64 at a time.
- **Chip store:** spend chips on food, potions, enchanted books (including Mending), tools, decor and rare finds such
  as a Totem of Undying or an elytra (End counters only). It is open at every cashier and every casino merchant.
- **VIP tiers** from Bronze to Netherite, based on your lifetime wagers. Higher tiers bring higher limits, better
  tables, bigger loans and cosmetics. Tiers are never lost.
- **Comp points** on every house game at every tier. Spend them on food plates, a "suite night" (a bed in the casino
  sets your spawn and gives buffs), free-bet tokens, fireworks, or a rented horse or strider.
- **Daily bonus**, claimed at the cashier, from 25 chips at Bronze up to 2 000 at Netherite.
- **Cashback** from Gold VIP (2–5 %), based on the house edge you paid that day, with a toast when it is paid. Cashback
  and comps together never return more than 35 % of the house edge, so no game is profitable for the player.
- **Bet entry:** press **E** or click your bet to type an amount (with `k` for thousands), halve it, double it or set
  it to Max. If a bet is refused, the reason says whose limit it is: your VIP tier, the table, the casino owner or
  your balance.

### Debt, chaos and luck

- **Loan Shark and Debt Collectors:** borrow 100 to 50 000 chips. Miss the deadline and a collector squad comes to
  settle, or to fight. They never break blocks and never attack anyone but the debtor. On Peaceful, an Asset Freeze
  takes their place.
- **Chaos events:** chip showers, diamond rain, XP fountains, mob waves, random teleports, curses, and **Golden Hour**,
  when house-game winnings are doubled for the whole server. Safety rules always apply: no creepers, no lethal
  debuffs and no Nether-roof teleports.
- **Lucky and unlucky streaks** nudge only the instant games, and the house edge never drops below 1 %. Table games are
  always honest.
- **Last Chance:** when you would die, flip a coin with Death. Hardcore has an opt-in High Stakes version.
- **Over 60 advancements** in their own tab.

![screenshot: cashier counter with the chip store](TODO)

---

## Screens that fit any monitor

- Every game shares one framework: a top bar, the play area and a dock with your bet and actions. All screens have
  the same size and the same rules.
- Each layout is checked at sizes from 320×240 up to 960×540 in GUI pixels, which covers 4K with GUI scale Auto. Large
  windows get a roomier layout, not a zoomed-in copy.
- Every disabled button tells you why. Keyboard shortcuts work on every table, and each button shows its key.
- Casino screens are not inventory screens, so recipe viewers don't draw over them.
- Animations: cards are dealt and flipped, chips slide and stack, reels spin and tumble, and wins are celebrated.
  Screens take on the style of the casino they belong to.

![screenshot: roulette screen at 4K GUI Auto](TODO)

---

## Languages

Fully translated into **English** and **Russian**, including proper Russian plurals. Each player sees their own game
language, on a server too.

---

## Configuration and commands

- Hundreds of settings: payouts, odds, limits, timers, loans, chaos, worldgen chances, the store catalog, comps and
  more. Out-of-range values are clamped.
- Edit them in `config/burmaldaholic.json`, in-game with **Mod Menu**, or with `/casino config get|set|reset|reload`.
  An optional per-world override is supported.
- **Casino mode** is on by default. Turn it off when you create a world (the **Casino Mode** button below Difficulty),
  or later with `/casino mode on|off|status`. When it's off, the mod goes dormant and keeps all its data.
- `/casino locate` works for every player. Operators get `/casino …` (alias `/burmaldaholic`) for balances, debt,
  chaos, jackpots, the store and more.
- Burmaldaholic never changes your difficulty, Hardcore or any vanilla game rule. It only reads the difficulty to
  scale its own mechanics.

---

## Compatibility

| | |
|---|---|
| Minecraft Java Edition | **26.3** (and its 26.3.x patches) **only** |
| Loader | Fabric Loader 0.19.5 or newer |
| Required | Fabric API 0.161.0+26.3 or newer |
| Optional | Mod Menu (config screen) |
| Java | 25 |
| Sides | Required on **both** the server and every client |

Earlier versions supported 26.2. From 0.2.0 on, only 26.3 is supported.

---

## FAQ

**Is this real-money gambling?**
No. Chips are play money that exists only in your world. You can't buy chips with real money, sell them or cash them
out, and the mod has no purchases, ads or online services. The chip store sells in-game items for in-game chips, and
nothing else.

**Does it work in single-player and on servers?**
Yes, both. On a server, install the mod on the server and on every client.

**Will it conflict with JEI, REI or EMI?**
No. Casino screens are not inventory screens, so recipe-viewer panels don't cover them, and the mod doesn't depend on
any of them.

**How heavy is it?**
Casino placement uses a fast terrain probe, so world generation stays smooth. House dealers have no pathfinding AI,
and in-world tables only draw their full detail up close. All game logic runs on the server, and the screens only
animate the result.

**Can I turn things off?**
Yes. Casino mode is a per-world switch, and the config can turn off chaos events, worldgen, single structures and much
more.

**Do I need a new world?**
No. Existing worlds keep their data. New casinos appear in newly generated chunks, and casinos you already have get
their house dealers.

**Are the odds rigged?**
No. The server decides every outcome with a fair random generator, the RTP of every game is tested, and the slot
paytables show exactly where each jackpot comes from. Streaks only nudge the instant games, within published limits.

---

**Photosensitivity warning:** big wins, jackpots and Golden Hour use flashing lights and fast-moving effects. Turn on
**Reduce motion** in the Casino Menu settings to calm them down.

*Burmaldaholic is a fan-made mod. NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR
MICROSOFT.*

[Changelog](TODO) · [Source code](TODO) · [Issues](TODO)
