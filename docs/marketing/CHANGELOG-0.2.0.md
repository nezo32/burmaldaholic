# Burmaldaholic 0.2.0

The biggest update so far: new casinos in every dimension, real multi-block tables with house dealers, a chip store
and comps, slots at about 98 % RTP, smooth animations on bad connections, and screens that fit every resolution.
Most of the fixes come from the owner's 4-day playtest of 0.1.3.

## Breaking

- **Minecraft 26.2 support has been dropped.** 0.2.0 runs on Minecraft Java Edition **26.3** (and its 26.3.x patches)
  only, with Fabric API 0.161.0+26.3 and Java 25. Stay on 0.1.3 if you play on 26.2.
- Exchange rates changed (see Improved). The Gold VIP emerald bonus is gone, and the old two-item cashier shop has
  been replaced by the chip store.
- The slot machines' old default payouts and bonus-buy prices are replaced once in existing config files. Values you
  changed yourself are kept.

## New

### Casinos and the world
- **Grand Casino:** a new standalone art-deco casino in the Overworld, in six biome styles (Riviera, Oasis, Safari,
  Alpine, Ice Palace and Cherry). It has a neon CASINO marquee, a dome, galleries, a bar, a VIP floor and 32 games.
- **New village casino:** a picture-palace annex in all five village styles, with vertical CASINO banners, a marquee
  canopy and baccarat.
- **Rebuilt Piglin Parlor** on a piglin-manor shell, with a back door into the bastion.
- **High Roller Lounge** is now its own building on the End island surface, beside the city.
- **Multi-block tables:** every table is a full 3×2 table with legs, padded rails and printed felt. Slot machines are
  tall cabinets, the Wheel of Fortune and Plinko are upright 3×3 machines, and the cashier is a counter. Use any part
  to open it. Breaking any part removes the whole table. Single-block tables in old worlds grow to full size when
  there is room.
- **House dealers** stand behind every house table and deal, collect, spin, push the craps stick and push the pot.
  Humans (6 looks) in the Overworld, piglins and a brute pit boss in the Nether, endermen in tuxedos in the End. The
  shulker croupier is now an Ender Croupier. Casinos from older versions get their dealers too.
- **Casino merchant** beside every cashier. It opens the chip store.
- **In-world games:** passers-by see the live game on the table, with cards on the felt, chips, the roulette ball,
  dice, Plinko balls and spinning reels.

### Economy
- **Chip store:** 38 offers of food, potions, enchanted books (including Mending), tools, decor and rares (Totem of
  Undying, and elytra and shulker shells at End counters), with categories, search and daily limits. Open at every
  cashier, every merchant and with `/casino store` (operators).
- **Comps:** every house game earns comp points at every VIP tier. Spend them on food plates, a suite night (a bed in
  the casino sets your spawn and gives buffs), free-bet tokens, fireworks, and horse or strider rentals.
- **Free-bet tokens:** hold one and bet at coin flip, the wheel, Plinko, blackjack or slots. Winnings are paid in
  chips.
- **Daily bonus:** 25 to 2 000 chips a day, depending on your VIP tier.
- **Visible cashback:** a toast when it's paid, and the Comps tab shows today's estimate.
- **High-roller chips** worth 1 000, 5 000 and 25 000.

### Tables and screens
- **Bet entry on every table:** press **E** or click your bet to type an amount (`k` = thousand), halve it, double it
  or set it to Max.
- **Blackjack shoe bar:** dealt and remaining cards, the cut card and the discard count, so you can count cards
  yourself. The shoe is saved with the world and reshuffles only at the cut card, between rounds.
- **Realistic motion:** the roulette ball rolls and bounces into its pocket, craps dice tumble off the back wall, dice
  duels use real 3D dice, and Plinko balls bounce off the pegs, several at a time.
- **Slots jackpot ladder:** the paytable and the jackpot meters show where Mini, Minor, Major and Grand come from and
  their odds.
- **Jackpot gem:** any paid base spin can award a Mini or Minor jackpot, so a Mini comes about once in 2 000 spins.
- **Coin × multiplier** bet display on slots.
- Chemin de fer asks you to confirm a large Banco.

## Improved

- **Slots RTP raised to about 98 %:** Overworld Riches 98.37 %, Nether Inferno 98.42 %, End Void 98.41 %. The game was
  never rigged, but it paid too little and hid where the jackpots come from. Bonus-buy prices were adjusted to match.
- **Smooth on a bad connection:** slot reels, the roulette ball, craps dice and dealt cards keep moving while the
  result travels, then settle on it without snapping or jumping back. A shared smoothed clock replaces the raw world
  time, and the server starts each animation slightly ahead.
- **Every screen fits every resolution:** all games share one 480×270 framework, with the same size and layout rules.
  Each layout is checked from 320×240 up to 960×540 GUI pixels, including 4K with GUI scale Auto. Big windows get a
  roomier layout instead of a zoomed-in copy.
- **Cashier:** exchange ×1, ×64, All or Max. Shift-click exchanges 64. Exchanged items never drop at your feet, and a
  counting tray shows the chips moving.
- **Exchange rates:** emerald 9 → chips / 10 chips → 1, plus new rows for emerald blocks, diamonds, iron blocks,
  Nether gold ingots (6 / 8) and netherite scrap (Diamond VIP).
- **Bet refusals say why:** the reason names whose limit it is (your VIP tier, the table, the casino owner or your
  balance), and every disabled button explains itself.
- **Baccarat:** Undo and right-click take back one chip at a time.
- **Blackjack:** split hands sit side by side, results are stamped on the cards, and the insurance bet shows on the
  felt.
- PvP screens and the slot screen moved to the shared framework. The slot dock folds extra buttons into **More** on
  small screens.
- House tables can't be broken by other players or destroyed by explosions, and they never drop. Tables you place
  yourself inside a casino still drop normally.

## Fixed

- **Duel results kept reopening:** the result window of a finished PvP match opened again every few minutes, in every
  mode.
- **Big-win chat line showed boxes (tofu):** the jackpot and big-win announcement is readable again.
- **JEI, REI and EMI drew over casino screens:** they no longer do.
- **Scratch cards jumped between inventory slots:** the stack now stays in its slot, used cards stack, and a
  disconnect mid-card neither loses nor duplicates a card.
- **Desync on bad internet:** reels, dice and the roulette ball no longer jump or snap when the result arrives late.
- **Slots felt rigged:** RTP is raised and the jackpot sources are explained (see Improved).
- **Nether casinos were buried in rock:** the Piglin Parlor now picks an open site above the lava sea, digs a lit
  tunnel to open air, opens a back door into the bastion and seals nearby lava.
- **The End casino sat on top of End City towers:** the High Roller Lounge now stands on the island surface, at least
  24 blocks from the city, and you can walk to its door.
- **No dealers at most tables, and every NPC looked the same:** every house table has a dealer, with several looks per
  dimension.
- **Blackjack couldn't be counted:** the shoe count is now shown and saved, and streaks never affect blackjack draws.
- **Max chip was 500, and big bets took many clicks:** high-roller chips and typed bet entry fix both.
- **Cashier needed spam-clicking:** All, Max and ×64 (Shift) exchange everything in one click.
- **Chips had little use:** the chip store, comps, the daily bonus and visible cashback give them one.
- **Bet refusals didn't explain whose cap applied:** they do now.
- **Screens had different sizes:** every game now uses the same frame.
- UTH: Max and the typed amount now count the whole stake (Ante, Blind and Play), so a bet is never refused after
  you confirm it.
- Bot nameplates were missing on carpeted floors and for players in spectator mode.
- Rare crashes when a chunk with a casino table unloaded.
- Valuable blocks (gold blocks, shulker boxes and similar) were removed from casino decor.
