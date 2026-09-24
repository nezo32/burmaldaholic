package dev.nezo.burmaldaholic.gametest.poker;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.table.cards.TableTheme;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.client.PokerScreen;
import dev.nezo.burmaldaholic.games.poker.logic.Cards;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBeats;
import dev.nezo.burmaldaholic.games.poker.present.PokerPub;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.client.UthScreen;
import dev.nezo.burmaldaholic.games.uth.present.UthPub;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Card tables B in a real client (lane J-L5): the redesigned Texas Hold'em and Ultimate Texas Hold'em screens.
 * <ul>
 *   <li>live: a Hold'em hand against bots (the deal segment, the viewer's turn, the showdown / result) and a UTH round
 *   (the deal, the pre-flop decision, the showdown, the result) played through the server — the screens must end on
 *   the server's state and never show a card before it was published;</li>
 *   <li>the mockups rebuilt from a fixed state: {@code cards_holdem_showdown} (EN, RU, and the bastion theme as
 *   {@code cards_holdem_nether}) and {@code cards_uth_decision} (EN, RU, the End theme).</li>
 * </ul>
 * Screenshots {@code jl5_*} land in {@code build/run/clientGameTest/screenshots}; failures are collected and thrown at
 * the end; a short report {@code jl5_report.txt} lists what was checked.
 */
public class PokerClientGameTests implements FabricClientGameTest {
	private static final BlockPos[] TABLE = new BlockPos[1];
	private final List<String> failures = new ArrayList<>();
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			pokerLive(context, world);
			pokerMockups(context, world);
			uthLive(context, world);
			uthMockups(context, world);
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = true);
		} finally {
			context.runOnClient(mc -> {
				TableTheme.force(null);
				FxSettings.get().reduceMotion = false;
			});
			guiScale(context, 0);
			language(context, "en_us");
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("card tables B: " + String.join("; ", failures));
	}

	// ---- Texas Hold'em, live ---------------------------------------------------------------------------------------

	private void pokerLive(ClientGameTestContext context, TestSingleplayerContext world) {
		guiScale(context, 2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos pos = player.blockPosition().offset(2, 0, 0);
			TABLE[0] = pos;
			server.overworld().setBlockAndUpdate(pos, PokerModule.TABLE.block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof PokerTableBlockEntity table) {
				player.openMenu(table);
				table.sendStateTo(player);
			}
		});
		context.waitForScreen(PokerScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("jl5_poker_join_en");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			if (server.overworld().getBlockEntity(TABLE[0]) instanceof PokerTableBlockEntity table) {
				CompoundTag args = new CompoundTag();
				args.putString("level", "micro");
				args.putLong("amount", 200);
				table.onAction(player, "buy_in", args);
			}
		});
		try {
			context.waitFor(mc -> mc.gui.screen() instanceof PokerScreen s && "deal".equals(s.fxKind()), 1200);
			context.waitTicks(14);
			context.takeScreenshot("jl5_poker_deal_en");
		} catch (RuntimeException | AssertionError e) {
			failures.add("poker: no deal segment");
		}
		try {
			context.waitFor(mc -> mc.gui.screen() instanceof PokerScreen screen && screen.isMyTurn(), 2400);
			context.waitTicks(3);
			context.takeScreenshot("jl5_poker_turn_en");
		} catch (RuntimeException | AssertionError e) {
			failures.add("poker: the viewer's turn never came");
		}
		// no board card on the screen that the server has not published
		boolean[] leak = {false};
		boolean[] runout = {false};
		int[] shots = {0};
		for (int i = 0; i < 600; i++) {
			boolean done = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (!(server.overworld().getBlockEntity(TABLE[0]) instanceof PokerTableBlockEntity table)) return true;
				CompoundTag st = table.writeClientState(player);
				PokerPub pub = table.buildPub();
				if (pub.board().length > st.getIntArray("board").orElse(new int[0]).length) leak[0] = true;
				CompoundTag legal = st.getCompoundOrEmpty("legal");
				if (!legal.isEmpty()) {
					CompoundTag act = new CompoundTag();
					// push all-in on the flop to see the slow run-out
					boolean shove = st.getIntArray("board").orElse(new int[0]).length >= 3 && legal.getBooleanOr("can_raise", false);
					act.putString("kind", shove ? "all_in" : legal.getBooleanOr("can_check", false) ? "check" : "call");
					act.putInt("seq", legal.getIntOr("seq", 0));
					table.onAction(player, "act", act);
				}
				if (st.getCompoundOrEmpty("fx").getStringOr("kind", "").equals("finish") && st.getCompoundOrEmpty("fx").getIntArray("args")
					.map(a -> a.length > 1 && a[1] > 0).orElse(false)) runout[0] = true;
				return !st.getBooleanOr("live", true) && !st.getBooleanOr("presenting", true) && !st.getListOrEmpty("result").isEmpty();
			});
			if (!done && runout[0] && i % 6 == 0) context.takeScreenshot("jl5_poker_runout_" + i);
			boolean presenting = context.computeOnClient(mc -> mc.gui.screen() instanceof PokerScreen p && "finish".equals(p.fxKind()));
			if (!done && presenting && shots[0]++ < 3) context.takeScreenshot("jl5_poker_finish_" + shots[0]);
			if (done) break;
			context.waitTicks(5);
		}
		if (leak[0]) failures.add("poker: the public tag showed more board than the viewer's state");
		context.waitTicks(10);
		context.takeScreenshot("jl5_poker_result_en");
		String end = context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof PokerScreen s)) return "screen closed";
			return null;
		});
		if (end != null) failures.add("poker: " + end);
		report.add("poker live: deal / turn / result screenshots; run-out seen: " + runout[0]);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			if (server.overworld().getBlockEntity(TABLE[0]) instanceof PokerTableBlockEntity table) table.onAction(player, "stand_up", new CompoundTag());
		});
		context.waitTicks(5);
	}

	// ---- Texas Hold'em, the mockups ------------------------------------------------------------------------------

	private void pokerMockups(ClientGameTestContext context, TestSingleplayerContext world) {
		for (String lang : List.of("en_us", "ru_ru")) {
			language(context, lang);
			for (int scale : new int[] {2, 3}) {
				guiScale(context, scale);
				pokerMockup(context, world, "jl5_poker_showdown_" + lang.substring(0, 2) + "_s" + scale, TableTheme.VILLAGE, true);
			}
		}
		language(context, "en_us");
		guiScale(context, 2);
		pokerMockup(context, world, "jl5_poker_nether_en", TableTheme.BASTION, true);
		pokerMockup(context, world, "jl5_poker_end_result_en", TableTheme.END, false);
		context.runOnClient(mc -> FxSettings.get().reduceMotion = true);
		pokerMockup(context, world, "jl5_poker_showdown_reduced_en", TableTheme.VILLAGE, true);
		context.runOnClient(mc -> FxSettings.get().reduceMotion = false);
	}

	private void pokerMockup(ClientGameTestContext context, TestSingleplayerContext world, String name, TableTheme theme, boolean midAward) {
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			TableTheme.force(theme);
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.closeContainer();
			server.overworld().setBlockAndUpdate(TABLE[0], Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(TABLE[0], PokerModule.TABLE.block().defaultBlockState());
			if (server.overworld().getBlockEntity(TABLE[0]) instanceof PokerTableBlockEntity table) player.openMenu(table);
		});
		context.waitForScreen(PokerScreen.class);
		context.waitTicks(3);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof PokerScreen s && mc.level != null) s.acceptState(showdownState(mc, midAward));
		});
		context.waitTicks(midAward ? 6 : 30);
		context.takeScreenshot(name);
		context.runOnClient(mc -> TableTheme.force(null));
	}

	private static Tag text(Minecraft mc, String s) {
		var ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		return ComponentSerialization.CODEC.encodeStart(ops, Component.literal(s)).result().orElseGet(CompoundTag::new);
	}

	private static Tag key(Minecraft mc, String key) {
		var ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		return ComponentSerialization.CODEC.encodeStart(ops, Component.translatable(key)).result().orElseGet(CompoundTag::new);
	}

	private static int[] cards(String s) {
		return Cards.parseAll(s);
	}

	/**
	 * The mockup {@code cards_holdem_showdown.png}: six seats, the viewer "Nezo" at the bottom, Mira wins with a flush,
	 * Bo is all-in with three kings, Eli mucks, Alex and Vern folded, MONSTER POT!, the pot mid-slide (or, with
	 * {@code midAward} false, the settled result with the chat line and the next-hand timer).
	 */
	private static CompoundTag showdownState(Minecraft mc, boolean midAward) {
		CompoundTag st = new CompoundTag();
		st.putString("phase", "result");
		st.putLong("balance", 8940);
		st.putBoolean("enabled", true);
		st.putBoolean("seated", true);
		st.putString("stake", "low");
		st.putInt("table_size", 6);
		st.putLong("bb", 10);
		st.putLong("sb", 5);
		st.putInt("hand_no", 12);
		st.putInt("button", 1);
		st.putBoolean("live", false);
		st.putBoolean("presenting", midAward);
		st.putIntArray("board", cards("Kh 9h 2c 5h Js"));
		st.putLong("pot_total", 2480);
		PokerBeats.Finish f = new PokerBeats.Finish(false, 0, false, 3, 1);
		var tl = PokerBeats.finish(f, PokerBeats.Pacing.DEFAULT, 5);
		int awardAt = PokerBeats.find(tl, PokerBeats.AWARD, 0).at();
		long now = mc.level.getGameTime();
		CompoundTag fx = new CompoundTag();
		fx.putInt("seq", 40 + (midAward ? 1 : 2));
		fx.putString("kind", "finish");
		fx.putLong("start", midAward ? now - (awardAt + 100) / 50 : now - 400);
		fx.putIntArray("args", new int[] {0, 0, 0, 3, 1});
		fx.putInt("seed", 5);
		fx.putBoolean("done", !midAward);
		fx.putString("moment", "monster");
		st.put("fx", fx);
		ListTag awards = new ListTag();
		CompoundTag a = new CompoundTag();
		a.putInt("pot", 0);
		a.putLong("amount", 2480);
		a.putIntArray("seats", new int[] {5});
		a.putLongArray("shares", new long[] {2480});
		awards.add(a);
		st.put("awards", awards);
		ListTag table = new ListTag();
		table.add(seat(mc, 0, "Nezo", false, null, null, 1180, cards("Ts Tc"), "gui.burmaldaholic.poker.hand.pair", -1, false, false, false, 0, true));
		table.add(seat(mc, 1, "", true, "slime_sam", "easy", 1020, null, null, -1, true, false, false, 1, false));
		table.add(seat(mc, 2, "", true, "sir_oinksalot", "hard", 0, cards("Kc Ks"), "gui.burmaldaholic.poker.hand.three_of_a_kind", 79, false, true,
			false, 2, false));
		table.add(seat(mc, 3, "Alex", false, null, null, 640, null, null, -1, true, false, false, 3, false));
		table.add(seat(mc, 4, "", true, "bee_bea", "normal", 2410, null, null, -1, false, false, true, 4, false));
		table.add(seat(mc, 5, "Mira", false, null, null, midAward ? 1220 : 3700, cards("Ah 7h"), "gui.burmaldaholic.poker.hand.flush", 47, false,
			false, false, 5, false));
		st.put("table", table);
		ListTag result = new ListTag();
		if (!midAward) {
			CompoundTag timers = new CompoundTag();
			timers.putLong("next_hand", 60);
			st.put("timers", timers);
			result.add(text(mc, "Mira wins 2 480 with a flush"));
		}
		st.put("result", result);
		return st;
	}

	private static CompoundTag seat(Minecraft mc, int index, String name, boolean bot, String botName, String level, long stack, int[] cards,
			String handKey, int best, boolean folded, boolean allIn, boolean mucked, int handIndex, boolean you) {
		CompoundTag t = new CompoundTag();
		t.putInt("index", index);
		t.put("name", text(mc, name));
		t.putBoolean("bot", bot);
		if (botName != null) t.putString("bot_name", botName);
		if (level != null) t.putString("level", level);
		t.putBoolean("you", you);
		t.putLong("stack", stack);
		t.putBoolean("folded", folded);
		t.putBoolean("all_in", allIn);
		t.putInt("hand_index", handIndex);
		if (mucked) t.putBoolean("mucked", true);
		if (cards != null) t.putIntArray("cards", cards);
		else if (!folded && !mucked) t.putInt("hidden", 2);
		if (mucked) t.putInt("hidden", 2);
		if (handKey != null) t.put("hand", key(mc, handKey));
		if (best >= 0) t.putInt("best", best);
		return t;
	}

	// ---- Ultimate Texas Hold'em ----------------------------------------------------------------------------------

	private void uthLive(ClientGameTestContext context, TestSingleplayerContext world) {
		guiScale(context, 2);
		BlockPos pos = TABLE[0].offset(0, 0, 3);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.closeContainer();
			server.overworld().setBlockAndUpdate(pos, UthModule.TABLE.block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity table) {
				player.openMenu(table);
				table.sit(player);
				table.sendStateTo(player);
			}
		});
		context.waitForScreen(UthScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("jl5_uth_betting_en");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			if (server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity table) {
				table.stackDeckForTests(uthDeck());
				CompoundTag args = new CompoundTag();
				args.putLong("ante", 25);
				args.putLong("trips", 5);
				table.onAction(player, "bet", args); // a single player: dealt at once
			}
		});
		try {
			context.waitFor(mc -> mc.gui.screen() instanceof UthScreen s && s.fxKind() == UthPub.DEAL, 400);
			context.waitTicks(16);
			context.takeScreenshot("jl5_uth_deal_en");
		} catch (RuntimeException | AssertionError e) {
			failures.add("uth: no deal segment");
		}
		context.waitTicks(50);
		context.takeScreenshot("jl5_uth_preflop_en");
		// check the pre-flop and the flop, then bet 1× at the river: the showdown and the result
		for (String action : List.of("check", "check", "bet_1x")) {
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity table) {
					table.onAction(player, action, new CompoundTag());
					table.syncViewers();
				}
			});
			context.waitTicks(30);
		}
		context.waitTicks(30);
		context.takeScreenshot("jl5_uth_showdown_en");
		try {
			context.waitFor(mc -> mc.gui.screen() instanceof UthScreen s && s.fxKind() == UthPub.RESULT, 600);
			context.waitTicks(10);
			context.takeScreenshot("jl5_uth_result_en");
		} catch (RuntimeException | AssertionError e) {
			failures.add("uth: the round did not settle");
		}
		report.add("uth live: betting / deal / pre-flop / showdown / result screenshots");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().closeContainer());
		context.waitTicks(5);
	}

	/** A deck giving the viewer A♠ K♠ and a straight on the board (top = index 0: seat, dealer, seat, dealer, board). */
	private static int[] uthDeck() {
		List<Integer> order = new ArrayList<>();
		for (int c : Cards.parseAll("As 3d Ks 8c Qs Jd Td 2h 7c")) order.add(c);
		for (int c = 0; c < 52; c++) if (!order.contains(c)) order.add(c);
		return order.stream().mapToInt(Integer::intValue).toArray();
	}

	private void uthMockups(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos pos = TABLE[0].offset(0, 0, 3);
		for (String lang : List.of("en_us", "ru_ru")) {
			language(context, lang);
			for (int scale : new int[] {2, 3}) {
				guiScale(context, scale);
				uthMockup(context, world, pos, "jl5_uth_decision_" + lang.substring(0, 2) + "_s" + scale, TableTheme.VILLAGE);
			}
		}
		language(context, "en_us");
		guiScale(context, 2);
		uthMockup(context, world, pos, "jl5_uth_decision_end_en", TableTheme.END);
		uthMockup(context, world, pos, "jl5_uth_decision_bastion_en", TableTheme.BASTION);
	}

	private void uthMockup(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, String name, TableTheme theme) {
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			TableTheme.force(theme);
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.closeContainer();
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos, UthModule.TABLE.block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity table) player.openMenu(table);
		});
		context.waitForScreen(UthScreen.class);
		context.waitTicks(3);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof UthScreen s && mc.level != null) s.acceptState(uthDecisionState(mc));
		});
		context.waitTicks(8);
		// "Bet 4×" hovered: the ghost Play stack with its glow and amount (mockup)
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof UthScreen s) s.previewBetForTests("bet_4x");
		});
		context.waitTicks(4);
		context.takeScreenshot(name);
		context.runOnClient(mc -> TableTheme.force(null));
	}

	/** The mockup {@code cards_uth_decision.png}: pre-flop, the viewer holds A♠ K♠, Mira (bot) plays ×4, Shelly thinks. */
	private static CompoundTag uthDecisionState(Minecraft mc) {
		CompoundTag st = new CompoundTag();
		st.putString("phase", "preflop");
		st.putLong("balance", 4300);
		st.putInt("seat", 2);
		st.putInt("seat_count", 6);
		st.putLong("min", 10);
		st.putLong("max", 1000);
		st.putString("variant", "standard");
		st.putBoolean("allow3x", true);
		st.putBoolean("trips_enabled", true);
		CompoundTag timers = new CompoundTag();
		timers.putLong("decide", 240);
		st.put("timers", timers);
		CompoundTag pays = new CompoundTag();
		pays.putDouble("blind_royal", 500);
		pays.putDouble("blind_straightFlush", 50);
		pays.putDouble("blind_quads", 10);
		pays.putDouble("blind_fullHouse", 3);
		pays.putDouble("blind_flush", 1.5);
		pays.putDouble("blind_straight", 1);
		pays.putInt("trips_royal", 50);
		pays.putInt("trips_straightFlush", 40);
		pays.putInt("trips_quads", 30);
		pays.putInt("trips_fullHouse", 8);
		pays.putInt("trips_flush", 7);
		pays.putInt("trips_straight", 4);
		pays.putInt("trips_trips", 3);
		st.put("pays", pays);
		CompoundTag fx = new CompoundTag();
		fx.putInt("seq", 77);
		fx.putInt("kind", UthPub.DEAL);
		fx.putLong("start", mc.level.getGameTime() - 200);
		fx.putInt("seats", 3);
		fx.putInt("seed", 3);
		st.put("fx", fx);
		st.put("board", new IntArrayTag(new int[0]));
		ListTag players = new ListTag();
		players.add(uthPlayer(0, "", true, "emerald_emma", "normal", 25, 0, 4, "play", null));
		players.add(uthPlayer(2, "Nezo", false, null, null, 25, 5, 0, "deciding", Cards.parseAll("As Ks")));
		players.add(uthPlayer(4, "", true, "diamond_dora", "hard", 25, 0, 0, "deciding", null));
		players.getCompoundOrEmpty(1).putBoolean("you", true);
		st.put("players", players);
		st.putInt("deciding", 2);
		st.putBoolean("in_round", true);
		st.putString("my_hand", "high_card");
		ListTag legal = new ListTag();
		for (String d : List.of("check", "bet_3x", "bet_4x")) legal.add(StringTag.valueOf(d));
		st.put("legal", legal);
		return st;
	}

	private static CompoundTag uthPlayer(int seat, String name, boolean bot, String botName, String level, long ante, long trips, int play, String tag,
			int[] cards) {
		CompoundTag p = new CompoundTag();
		p.putInt("seat", seat);
		p.putString("name", name);
		if (bot) {
			p.putBoolean("bot", true);
			p.putString("bot_name", botName);
			p.putString("bot_level", level);
		}
		p.putLong("ante", ante);
		p.putLong("trips", trips);
		p.putInt("play", play);
		p.putString("tag", tag);
		if (cards != null) p.put("cards", new IntArrayTag(cards));
		return p;
	}

	// ---- helpers --------------------------------------------------------------------------------------------------

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(2);
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(40);
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>(report);
			lines.addAll(failures.stream().map(f -> "FAIL " + f).toList());
			Files.writeString(dir.resolve("jl5_report.txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
