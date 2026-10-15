package dev.nezo.burmaldaholic.gametest.v2.blackjack;

import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Lane A (blackjack): REAL-FLOW client GameTests of the v2 screen (docs/architecture/ui-v2.md §11.2). The table is
 * opened by USING the block, every step is a click on the widget's real window pixel (the kit's HitMap through the mouse
 * mixin), and every step asserts the SERVER: sit → bet (chips build the draft, Deal sends it) → hit → stand → settle
 * (balance change = the server's settle) → Rebet. Also: Deal with no bet shows its reason and sends nothing; a dropped
 * bet packet shows the watchdog reason; insurance (declined) and a split are played by clicking. Sizes: 1920 × 1080 @4
 * (480 × 270), 2560 × 1440 @4 and 1920 × 1080 @3 (640 × 360), 1280 × 720 @3 (427 × 240); EN and RU. Then the in-world
 * board seen from 3, 5 and 8 blocks. Screenshots {@code v2a_bj_*}, {@code v2a_world_*}.
 */
public class BlackjackV2ClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("A")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 100000");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			world.getServer().runOnServer(server -> {
				dev.nezo.burmaldaholic.core.config.CasinoConfig.contracts().enabled = false; // rewards would move the balance
				dev.nezo.burmaldaholic.core.config.CasinoConfig.streak().enabled = false;
			});
			world.getServer().runCommand("time set noon");
			RealFlow f = new RealFlow(context, world);
			int dx = -12;
			for (String lang : List.of("en_us", "ru_ru")) {
				f.language(lang);
				round(f, lang, 1920, 1080, 4, dx += 3, lang.equals("en_us"));
				round(f, lang, 1920, 1080, 3, dx += 3, false);
				if (lang.equals("en_us")) {
					round(f, lang, 1280, 720, 0, dx += 3, false); // Auto → 427 × 240
					round(f, lang, 2560, 1440, 2, dx += 3, false); // 1280 × 720 framed (PHASE2B §0.7)
				}
			}
			f.language("en_us");
			f.size(1920, 1080, 4);
			insuranceAndSplit(f, dx + 3);
			rebetShort(f);
			multiSeat(f, "en_us", 1920, 1080, 4);
			f.language("ru_ru");
			multiSeat(f, "ru_ru", 1920, 1080, 3);
			f.language("en_us");
			f.close();
			CardBoards.run(context, world);
		} finally {
			V2Chrome.dropNextSends = 0;
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
	}

	/** One full round by clicking, at one window size. */
	private static void round(RealFlow f, String lang, int fw, int fh, int scale, int dx, boolean negatives) {
		f.size(fw, fh, scale);
		String tag = "v2a_bj_" + lang + "_" + f.viewport().w() + "x" + f.viewport().h();
		BlockPos pos = fresh(f, BlackjackModule.TABLE.block());
		f.open(pos, BlackjackScreen.class);
		f.shot(tag + "_open");
		// sit (the primary says Sit down)
		f.click(KeyMap.Ids.PRIMARY);
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated by the Sit primary");
		if (negatives) {
			// Deal with no bet: the reason shows, nothing reaches the server
			f.click(KeyMap.Ids.PRIMARY);
			String want = f.client(mc -> Component.translatable(V2Keys.REASON_PLACE_BET).getString());
			if (!f.status().equals(want)) throw new AssertionError("no-bet Deal: status '" + f.status() + "', expected '" + want + "'");
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == 0 && t.round() == null, "nothing placed");
		}
		// player 10 + 6 = 16 vs dealer 9 up / 8 in the hole; the hit is a 4 (20); the dealer stands on 17
		f.server(server -> {
			table(server, pos).stackCardsForTests(List.of(Card.of(10, Card.SPADES), Card.of(9, Card.HEARTS), Card.of(6, Card.CLUBS),
				Card.of(8, Card.DIAMONDS), Card.of(4, Card.HEARTS), Card.of(5), Card.of(5)));
			return null;
		});
		int chip25 = chipIndex(f, 25);
		f.click(KeyMap.Ids.chip(chip25));
		f.click(KeyMap.Ids.chip(chip25));
		// DES blackjack P0 (A-0001): with 50 drafted and Deal enabled the status is the chips hint, never "Place a bet first"
		String placeFirst = f.client(mc -> Component.translatable(V2Keys.REASON_PLACE_BET).getString());
		String chipsAdd = f.client(mc -> Component.translatable(V2Keys.STATUS_CHIPS_ADD).getString());
		f.waitChrome(c -> c.primary.gate().enabled(), "Deal enabled with 50 drafted");
		if (f.status().equals(placeFirst) || !f.status().equals(chipsAdd)) {
			throw new AssertionError("betting status '" + f.status() + "', expected '" + chipsAdd + "'");
		}
		f.shot(tag + "_bet");
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == 0, "chips build a local draft (nothing sent yet)");
		long before = f.server(server -> Economies.get().balance(RealFlow.player(server)));
		if (negatives) {
			// a dropped bet packet: the watchdog says so and resyncs; the bet is then sent again by a click
			V2Chrome.dropNextSends = 1;
			f.click(KeyMap.Ids.PRIMARY);
			String noAnswer = f.client(mc -> Component.translatable(V2Keys.REASON_NO_ANSWER).getString());
			for (int i = 0; i < 40 && !f.status().equals(noAnswer); i++) f.context().waitTicks(2);
			if (!f.status().equals(noAnswer)) throw new AssertionError("dropped bet: status '" + f.status() + "'");
			f.shot(tag + "_watchdog");
			f.context().waitTicks(10);
		}
		f.click(KeyMap.Ids.PRIMARY); // Deal
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.round() != null && t.stakeOf(me) == 50, "bet 50 placed and dealt");
		reveal(f, pos);
		f.waitChrome(c -> c.hits().targets().stream().anyMatch(t -> t.id().equals(KeyMap.Ids.action("hit")) && t.enabled()), "Hit enabled");
		// DES blackjack P0: the Dock says "Bet 50 · hand 1 of 1" (it said Bet 0)
		String hand = f.client(mc -> Component.translatable("gui.burmaldaholic.blackjack.v2.status.hand", dev.nezo.burmaldaholic.core.text.Texts.number(50), 1, 1)
			.getString());
		String line2 = line2(f);
		if (!line2.equals(hand)) throw new AssertionError("turn Dock line 2 '" + line2 + "', expected '" + hand + "'");
		f.shot(tag + "_turn");
		f.click(KeyMap.Ids.action("hit"));
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> mine(t, me) != null && mine(t, me).hands.getFirst().cards.size() == 3, "hit took a card");
		reveal(f, pos);
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("stand"), "Stand enabled");
		f.click(KeyMap.Ids.PRIMARY); // Stand
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "result".equals(t.phase()), "settled");
		reveal(f, pos);
		// DES cross-cutting 4 / R-AB m11: the result banner finds a place (the dealer <-> hero gap; compact at T2)
		f.waitChrome(c -> c.banner.active() && !c.banner.box().isEmpty(), "result banner on screen");
		f.shot(tag + "_banner");
		long net = f.server(server -> {
			BlackjackRound r = table(server, pos).round();
			BlackjackRound.Seat s = mine(table(server, pos), me);
			return r.returnOf(s.seat) - r.stakedOf(s.seat);
		});
		if (net != 50) throw new AssertionError("20 vs 17 should win +50, the server settled " + net);
		reveal(f, pos);
		f.context().waitTicks(30);
		long after = f.server(server -> Economies.get().balance(RealFlow.player(server)));
		if (after - before != net) throw new AssertionError("balance moved " + (after - before) + ", the server settled " + net);
		f.shot(tag + "_result");
		// Rebet 50 on the result: queued, placed when the next betting phase opens (single player: dealt at once)
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("rebet"), "Rebet offered on the result");
		f.server(server -> {
			table(server, pos).stackCardsForTests(List.of(Card.of(10), Card.of(10), Card.of(10), Card.of(9), Card.of(5)));
			return null;
		});
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.round() != null && t.round().seats().size() == 1 && t.stakeOf(me) == 50
			&& t.round().seats().getFirst().hands.getFirst().cards.getFirst().rank() == 10 && t.round() != null, "rebet 50 placed", 400);
		f.server(server -> {
			BlackjackTableBlockEntity t = table(server, pos);
			t.onAction(RealFlow.player(server), "stand", new net.minecraft.nbt.CompoundTag());
			t.revealAllForTests();
			return null;
		});
		// let this table pay out completely before the next size measures balances
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "result".equals(t.phase()) && t.openStakes().isEmpty(), "the rebet round settled", 400);
		f.context().waitTicks(20);
		f.close();
	}

	/** Insurance offered (dealer ace up) → declined by clicking; then a pair split by clicking. */
	private static void insuranceAndSplit(RealFlow f, int dx) {
		BlockPos pos = fresh(f, BlackjackModule.TABLE.block());
		f.open(pos, BlackjackScreen.class);
		f.click(KeyMap.Ids.PRIMARY);
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
		// 10 + 9 vs an ace up (insurance offered), 7 in the hole
		f.server(server -> {
			table(server, pos).stackCardsForTests(List.of(Card.of(10), Card.of(1, Card.SPADES), Card.of(9), Card.of(7), Card.of(5)));
			return null;
		});
		int chip = chipIndex(f, 25);
		f.click(KeyMap.Ids.chip(chip));
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.round() != null && t.round().phase() == BlackjackRound.Phase.INSURANCE, "insurance offered");
		reveal(f, pos);
		f.waitChrome(c -> c.hits().targets().stream().anyMatch(t -> t.id().equals(KeyMap.Ids.action("no_insurance")) && t.enabled()), "No insurance");
		f.shot("v2a_bj_en_us_insurance");
		// take the insurance with the primary: the insurance bet then shows on the felt (DES blackjack P2, A-0021)
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("insurance"), "Insure offered");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> mine(t, me) != null && mine(t, me).insurance > 0, "insurance taken");
		f.context().waitTicks(6);
		f.shot("v2a_bj_en_us_insured");
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.round() != null && t.round().phase() != BlackjackRound.Phase.INSURANCE, "declined");
		reveal(f, pos);
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("stand"), "Stand after insurance");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "result".equals(t.phase()), "settled after insurance");
		reveal(f, pos);
		// split: 8 + 8 vs 9 up
		f.server(server -> {
			BlackjackTableBlockEntity t = table(server, pos);
			t.fastForwardForTests();
			return null;
		});
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "betting".equals(t.phase()) || t.round() == null, "next round", 400);
		f.server(server -> {
			table(server, pos).stackCardsForTests(List.of(Card.of(8, Card.SPADES), Card.of(9), Card.of(8, Card.HEARTS), Card.of(8), Card.of(10), Card.of(10),
				Card.of(5)));
			return null;
		});
		f.waitChrome(c -> c.bet.visible() && c.hits().targets().stream().anyMatch(t -> t.id().equals(KeyMap.Ids.chip(chip)) && t.enabled()), "chips open");
		f.click(KeyMap.Ids.chip(chip));
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.round() != null && mine(t, me) != null, "dealt 8 + 8");
		reveal(f, pos);
		f.waitChrome(c -> c.hits().targets().stream().anyMatch(t -> t.id().equals(KeyMap.Ids.action("split")) && t.enabled()), "Split enabled");
		f.click(KeyMap.Ids.action("split"));
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> mine(t, me) != null && mine(t, me).hands.size() == 2, "split into two hands");
		reveal(f, pos);
		f.shot("v2a_bj_en_us_split");
		String splitHand = f.client(mc -> Component.translatable("gui.burmaldaholic.blackjack.v2.status.hand", dev.nezo.burmaldaholic.core.text.Texts.number(50), 1, 2)
			.getString());
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("stand"), "Stand hand 1");
		if (!line2(f).equals(splitHand)) throw new AssertionError("split Dock line 2 '" + line2(f) + "', expected '" + splitHand + "'");
		for (int h = 0; h < 2; h++) {
			f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("stand"), "Stand hand " + (h + 1));
			f.click(KeyMap.Ids.PRIMARY);
			reveal(f, pos);
			f.context().waitTicks(4);
		}
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "result".equals(t.phase()), "split settled", 400);
		reveal(f, pos);
		f.context().waitTicks(20);
		f.shot("v2a_bj_en_us_split_result");
		f.close();
	}

	/** Line 2 of the Dock status ("" = none). */
	static String line2(RealFlow f) {
		return f.client(mc -> {
			if (!(mc.gui.screen() instanceof V2Chrome.Holder h)) return "";
			Component c = h.chrome().status.line2();
			return c == null ? "" : c.getString();
		});
	}

	/**
	 * Review R-AB m9: Rebet pressed on the result is queued; when the next betting phase opens and the balance no longer
	 * covers it, the screen flashes "Not enough chips" and sends nothing.
	 */
	private static void rebetShort(RealFlow f) {
		BlockPos pos = fresh(f, BlackjackModule.TABLE.block());
		f.open(pos, BlackjackScreen.class);
		f.click(KeyMap.Ids.PRIMARY);
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
		f.server(server -> {
			table(server, pos).stackCardsForTests(List.of(Card.of(10), Card.of(10), Card.of(9), Card.of(8), Card.of(5)));
			return null;
		});
		int chip = chipIndex(f, 25);
		f.click(KeyMap.Ids.chip(chip));
		f.click(KeyMap.Ids.chip(chip));
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.round() != null && mine(t, me) != null, "dealt");
		reveal(f, pos);
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("stand"), "Stand");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "result".equals(t.phase()) && t.openStakes().isEmpty(), "settled");
		reveal(f, pos);
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("rebet"), "Rebet on the result");
		f.click(KeyMap.Ids.PRIMARY); // queued for the next round
		f.world().getServer().runCommand("casino balance set @p 10");
		f.context().waitTicks(10);
		f.server(server -> {
			table(server, pos).fastForwardForTests();
			return null;
		});
		String notEnough = f.client(mc -> Component.translatable(V2Keys.REASON_NOT_ENOUGH).getString());
		// the next betting phase opens; only the queued Rebet's flash says "Not enough chips" there (the betting primary's own
		// reason with 10 chips and no draft is "Place a bet first")
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "betting".equals(t.phase()) || t.round() == null, "the next betting phase", 600);
		f.waitChrome(c -> c.status.line1() != null && c.status.line1().getString().equals(notEnough) && c.bet.visible(),
			"the queued Rebet flashes Not enough chips in the betting phase");
		f.shot("v2a_bj_en_us_rebet_short");
		f.context().waitTicks(20);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == 0 && t.round() == null || t.round() != null && mine(t, me) == null,
			"nothing sent for the queued Rebet");
		f.world().getServer().runCommand("casino balance set @p 100000");
		f.close();
	}

	/** A table with two bots and the viewer (DES: multi-seat never captured): betting, a turn and the result. */
	private static void multiSeat(RealFlow f, String lang, int fw, int fh, int scale) {
		f.size(fw, fh, scale);
		String tag = "v2a_bj_" + lang + "_" + f.viewport().w() + "x" + f.viewport().h() + "_multi";
		BlockPos pos = fresh(f, BlackjackModule.TABLE.block());
		f.open(pos, BlackjackScreen.class);
		f.click(KeyMap.Ids.PRIMARY);
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
		// two bots join once a player is seated (the bots settings ask for a seated actor), as CardBoards does
		f.server(server -> {
			var two = new dev.nezo.burmaldaholic.core.bots.logic.BotSettings(dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy.MIXED, 2,
				dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.NORMAL, false, false, dev.nezo.burmaldaholic.core.bots.logic.BotSpeed.FAST);
			table(server, pos).bots().table().requestChange(RealFlow.player(server), two, false);
			table(server, pos).botSafePointForTests();
			return null;
		});
		f.context().waitTicks(10);
		int chip = chipIndex(f, 25);
		f.click(KeyMap.Ids.chip(chip));
		f.click(KeyMap.Ids.chip(chip));
		f.context().waitTicks(20);
		f.shot(tag + "_bet");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.round() != null && mine(t, me) != null, "dealt with bots", 600);
		for (int i = 0; i < 60; i++) {
			if (f.enabledTarget(KeyMap.Ids.action("hit")) || f.enabledTarget(KeyMap.Ids.action("no_insurance"))) break;
			boolean over = f.server(server -> "result".equals(table(server, pos).phase()));
			if (over) break;
			f.context().waitTicks(10);
		}
		reveal(f, pos);
		f.shot(tag + "_turn");
		for (int i = 0; i < 60; i++) {
			boolean over = f.server(server -> "result".equals(table(server, pos).phase()));
			if (over) break;
			if (f.enabledTarget(KeyMap.Ids.action("no_insurance"))) f.click(KeyMap.Ids.action("no_insurance"));
			else if (f.enabledTarget(KeyMap.Ids.PRIMARY) && f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
				&& h.chrome().primary.gate().action().equals("stand"))) f.click(KeyMap.Ids.PRIMARY);
			f.context().waitTicks(10);
		}
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> "result".equals(t.phase()), "multi-seat round settled", 600);
		reveal(f, pos);
		f.context().waitTicks(10);
		f.shot(tag + "_result");
		f.context().waitTicks(30);
		f.shot(tag + "_result_late");
		f.close();
	}

	/** Walks the player 7 blocks along +x (a clear spot, the old table out of the way) and places a table in reach. */
	public static BlockPos fresh(RealFlow f, net.minecraft.world.level.block.Block block) {
		f.close();
		f.world().getServer().runCommand("execute as @p at @s run tp @s ~7 ~ ~ 0 0");
		f.context().waitTicks(5);
		return f.place(block, 0);
	}

	private static void reveal(RealFlow f, BlockPos pos) {
		f.server(server -> {
			table(server, pos).revealAllForTests();
			return null;
		});
		f.context().waitTicks(4);
	}

	private static int chipIndex(RealFlow f, long denom) {
		f.waitChrome(c -> c.bet.visible() && c.bet.chips().length > 0, "chip tray");
		return f.client(mc -> {
			long[] chips = ((V2Chrome.Holder) mc.gui.screen()).chrome().bet.chips();
			for (int i = 0; i < chips.length; i++) if (chips[i] == denom) return i;
			throw new AssertionError("no " + denom + " chip in " + java.util.Arrays.toString(chips));
		});
	}

	static BlackjackTableBlockEntity table(net.minecraft.server.MinecraftServer server, BlockPos pos) {
		return (BlackjackTableBlockEntity) server.overworld().getBlockEntity(pos);
	}

	static BlackjackRound.Seat mine(BlackjackTableBlockEntity t, UUID me) {
		if (t.round() == null) return null;
		for (BlackjackRound.Seat s : t.round().seats()) if (s.player.equals(me)) return s;
		return null;
	}

}
