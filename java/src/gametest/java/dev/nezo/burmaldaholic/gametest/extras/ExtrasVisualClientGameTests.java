package dev.nezo.burmaldaholic.gametest.extras;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.menu.ClientCasinoMenu;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import net.minecraft.client.CameraType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.pvp.client.ClientPvp;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The redesigned extras and PvP screens (lane J-L7; visual/extras.md mockups {@code extras_coin_flip},
 * {@code extras_wheel_landing}, {@code extras_plinko_drop}, {@code extras_scratch_half}, {@code extras_pvp_match},
 * {@code extras_pvp_result}), in English and Russian: real rounds for the solo games (idle, mid-animation, landed) and
 * synthetic match views for the PvP screens (the Scratch Showdown grudge match, the Final Reveal overlay, the result
 * window, the lobby, Plinko Battle, Coin Flip Duel, Wheel Party). Themed variants: the Golden ticket and the grudge
 * arena. Screenshots {@code jtest_extras_<lang>_<view>}; a layout report {@code jtest_extras_layout.txt} (labels wider
 * than their button, widgets off screen).
 */
public class ExtrasVisualClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 12250");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			inWorld(context, world);
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				coin(context, world, lang);
				wheel(context, world, lang);
				plinko(context, world, lang);
				scratch(context, world, lang, false);
				pvp(context, lang);
				hub(context, world, lang);
				compact(context, world, lang);
			}
			language(context, "en_us");
			scratch(context, world, "en_us_gold", true);
			compactGrudge(context); // the themed variant at a small GUI: the grudge arena
		} finally {
			write(context);
			language(context, "en_us");
		}
	}

	// ---- solo games ----------------------------------------------------------------------------------------------

	private void coin(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		open(context, world, server -> CoinFlipGame.open(player(server)));
		shot(context, lang + "_coin_idle");
		world.getServer().runOnServer(server -> {
			CompoundTag args = new CompoundTag();
			args.putString("side", "heads");
			args.putString("stake", "chips");
			args.putLong("amount", 100);
			CoinFlipGame.action(player(server), "flip", args);
		});
		context.waitTicks(9);
		shot(context, lang + "_coin_air");
		context.waitTicks(28);
		shot(context, lang + "_coin_landed");
		clearCelebration(context);
	}

	private void wheel(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		BlockPos pos = table(context, world, ExtrasModule.WHEEL);
		shot(context, lang + "_wheel_idle");
		world.getServer().runOnServer(server -> action(server, pos, "spin", chips(50)));
		context.waitTicks(30);
		shot(context, lang + "_wheel_spin");
		context.waitTicks(55);
		shot(context, lang + "_wheel_stop");
		clearCelebration(context);
	}

	private void plinko(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		BlockPos pos = table(context, world, ExtrasModule.PLINKO);
		CompoundTag args = chips(20);
		args.putString("risk", "medium");
		world.getServer().runOnServer(server -> action(server, pos, "drop", args));
		context.waitTicks(30);
		shot(context, lang + "_plinko_drop");
		context.waitTicks(40);
		shot(context, lang + "_plinko_landed");
		clearCelebration(context);
	}

	private void scratch(ClientGameTestContext context, TestSingleplayerContext world, String lang, boolean gold) {
		open(context, world, server -> {
			ServerPlayer p = player(server);
			ItemStack card = new ItemStack(gold ? ExtrasModule.SCRATCH_CARD_GOLD : ExtrasModule.SCRATCH_CARD, 3);
			p.getInventory().add(card);
			ScratchGame.use(p, card, gold ? Scratch.Kind.GOLD : Scratch.Kind.BASIC);
		});
		shot(context, lang + "_scratch_fresh");
		// click two cells (auto-swipe), then drag across a third without releasing: the half-scratched foil
		for (int cell : new int[] {0, 1, 5}) {
			click(context, cell);
			context.waitTicks(12);
		}
		double[] a = cellWindow(context, 3, 4, 40);
		context.getInput().setCursorPos(a[0], a[1]);
		context.getInput().holdMouse(0);
		for (int k = 1; k <= 10; k++) {
			double[] b = cellWindow(context, 3, 4 + k * 3, 40 - k * 3.2);
			context.getInput().setCursorPos(b[0], b[1]);
			context.waitTicks(1);
		}
		shot(context, lang + "_scratch_half");
		context.getInput().releaseMouse(0);
		context.waitTicks(20);
		// scratch the rest: every remaining cell by click, then the end emphasis
		for (int cell : new int[] {2, 4, 6, 7, 8}) {
			click(context, cell);
			context.waitTicks(8);
		}
		context.waitTicks(30);
		shot(context, lang + "_scratch_done");
		clearCelebration(context);
	}

	private void click(ClientGameTestContext context, int cell) {
		double[] c = cellWindow(context, cell, 30, 22);
		context.getInput().setCursorPos(c[0], c[1]);
		context.getInput().pressMouse(0);
	}

	/** Window position of a point inside a scratch cell (solo layout: ticket at (20, 30), cells 60 × 44, pitch 64 × 48). */
	private static double[] cellWindow(ClientGameTestContext context, int cell, double dx, double dy) {
		return context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			int w = mc.getWindow().getGuiScaledWidth();
			int h = mc.getWindow().getGuiScaledHeight();
			int px = Scene.left(w);
			int py = Scene.top(h);
			double x = px + 32 + (cell % 3) * 64 + dx;
			double y = py + 66 + (cell / 3) * 48 + dy;
			return new double[] {x * scale, y * scale};
		});
	}

	// ---- in-world spectator animations (lane J-L7 finish) ---------------------------------------------------------------

	/**
	 * The machines as a spectator sees them: a wheel and a Plinko machine facing the player (third person), a spin, a drop
	 * and a coin flip at the same tick: mid-flight (the coin still edge-on, the ball on the face, the wheel turning), the
	 * coin's face after its landing, the wheel stopped on its segment with the Plinko lamp lit.
	 */
	private void inWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		BlockPos[] at = new BlockPos[2];
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.closeContainer();
			BlockPos base = p.blockPosition();
			at[0] = base.offset(4, 0, -2);
			at[1] = base.offset(4, 0, 1);
			server.overworld().setBlockAndUpdate(at[0], ExtrasModule.WHEEL.block().defaultBlockState()
				.setValue(CasinoTableBlock.FACING, Direction.WEST));
			server.overworld().setBlockAndUpdate(at[1], ExtrasModule.PLINKO.block().defaultBlockState()
				.setValue(CasinoTableBlock.FACING, Direction.WEST));
		});
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			if (mc.player != null) {
				mc.player.setYRot(-90f);
				mc.player.setXRot(4f);
			}
		});
		context.waitTicks(20);
		shot(context, "inworld_idle");
		world.getServer().runOnServer(server -> {
			action(server, at[0], "spin", chips(50));
			CompoundTag drop = chips(20);
			drop.putString("risk", "high");
			action(server, at[1], "drop", drop);
			CompoundTag flip = chips(10);
			flip.putString("side", "heads");
			CoinFlipGame.action(player(server), "flip", flip);
		});
		context.waitTicks(9);
		shot(context, "inworld_flight");
		context.waitTicks(15);
		shot(context, "inworld_coin_face");
		context.runOnClient(mc -> CelebrationOverlay.get().clear());
		context.waitTicks(58);
		shot(context, "inworld_stop");
		boolean[] synced = context.computeOnClient(mc -> new boolean[] {
			mc.level.getBlockEntity(at[0]) instanceof WheelBlockEntity w && w.wheelSync() != null,
			mc.level.getBlockEntity(at[1]) instanceof PlinkoBlockEntity pl && pl.plinkoSync() != null});
		if (!synced[0] || !synced[1]) report.add("in-world sync missing on the client: wheel " + synced[0] + ", plinko " + synced[1]);
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			CelebrationOverlay.get().clear();
		});
		world.getServer().runOnServer(server -> {
			server.overworld().removeBlock(at[0], false);
			server.overworld().removeBlock(at[1], false);
		});
		context.waitTicks(5);
	}

	// ---- the PvP hub page of the Casino Menu (extras.md §7.2) ----------------------------------------------------------------

	private void hub(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		open(context, world, server -> CasinoMenu.open(player(server), "challenges"));
		context.waitTicks(10);
		shot(context, lang + "_hub_live");
		// a busy hub: a record, the nemesis, an invite, three open lobbies (one with its own stake), every create card
		context.runOnClient(mc -> ClientCasinoMenu.setPageForTests("challenges", hubLines(), hubButtons()));
		context.waitTicks(12);
		shot(context, lang + "_hub");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
	}

	private static Component t(String key, Object... args) {
		return Component.translatable(key, args);
	}

	private static Component lit(String s) {
		return Component.literal(s);
	}

	private static List<ClientCasinoMenu.Line> hubLines() {
		List<ClientCasinoMenu.Line> l = new ArrayList<>();
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.title"), 0xFFD700));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.record", lit("12"), lit("7"), lit("+1,450")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.nemesis", lit("Notch"), lit("2"), lit("5")), 0xFF8888));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.pending"), 0xFFD700));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.invite.body", lit("Alex"), t("gui.burmaldaholic.pvp.game.coin"), lit("250")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.nearby", lit("3")), 0xFFD700));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.lobby_row", t("gui.burmaldaholic.pvp.game.scratch"), lit("Notch"), lit("2"), lit("6")),
			0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.lobby_row", t("gui.burmaldaholic.pvp.game.plinko"),
			t("block.burmaldaholic.plinko_machine"), lit("3"), lit("6")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.lobby_row", t("gui.burmaldaholic.pvp.game.wheel"),
			t("block.burmaldaholic.wheel_of_fortune"), lit("4"), lit("8")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(Component.empty(), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.machine_hint"), 0xAAAAAA));
		return l;
	}

	private static List<ClientCasinoMenu.Button> hubButtons() {
		List<ClientCasinoMenu.Button> b = new ArrayList<>();
		b.add(new ClientCasinoMenu.Button("p:accept:m1", t("gui.burmaldaholic.pvp.hub.accept", lit("Alex"), t("gui.burmaldaholic.pvp.game.coin")), true,
			false, 0));
		b.add(new ClientCasinoMenu.Button("p:decline:m1", t("gui.burmaldaholic.pvp.hub.decline", lit("Alex")), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:join:m2", t("gui.burmaldaholic.pvp.lobby.join", lit("200")), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:join:m3", t("gui.burmaldaholic.pvp.lobby.join", lit("100")), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:join:m4", t("gui.burmaldaholic.pvp.hub.join"), true, true, 10));
		b.add(new ClientCasinoMenu.Button("p:new:coin", t("gui.burmaldaholic.pvp.game.coin"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:new:scratch", t("gui.burmaldaholic.pvp.game.scratch"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:view:new", t("gui.burmaldaholic.pvp.hub.new_match"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:view:rivals", t("gui.burmaldaholic.pvp.hub.rivals"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:invites", t("gui.burmaldaholic.pvp.toggle", t("gui.burmaldaholic.pvp.settings.invites"),
			t("gui.burmaldaholic.common.on")), true, false, 0));
		return b;
	}

	// ---- compact layout (GUI scale 3–4: the full scene at a lower whole scale, nothing overlaps or clips) ----------------

	private void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeDisplay();
		});
		context.waitTicks(5);
	}

	private void compact(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		int before = context.computeOnClient(mc -> mc.options.guiScale().get());
		try {
			guiScale(context, 4);
			open(context, world, server -> CoinFlipGame.open(player(server)));
			fit(context, lang + "_compact_coin");
			table(context, world, ExtrasModule.WHEEL);
			fit(context, lang + "_compact_wheel");
			table(context, world, ExtrasModule.PLINKO);
			fit(context, lang + "_compact_plinko");
			payload(context, "match", scratchMatch(false), 30);
			fit(context, lang + "_compact_pvp_scratch");
			payload(context, "result", result(), 60);
			fit(context, lang + "_compact_pvp_result");
			payload(context, "lobby", lobby(), 20);
			fit(context, lang + "_compact_pvp_lobby");
			guiScale(context, 3);
			payload(context, "match", plinkoMatch(), 22);
			fit(context, lang + "_compact3_pvp_plinko");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		} finally {
			guiScale(context, before);
		}
	}

	private void compactGrudge(ClientGameTestContext context) {
		int before = context.computeOnClient(mc -> mc.options.guiScale().get());
		try {
			guiScale(context, 4);
			payload(context, "match", scratchMatch(true), 25);
			fit(context, "en_us_compact_pvp_grudge_reveal");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		} finally {
			guiScale(context, before);
		}
	}

	/** A compact screenshot plus the fit check: drawn below its GUI scale, the full panel inside the screen's GUI. */
	private void fit(ClientGameTestContext context, String name) {
		shot(context, name);
		String problem = context.computeOnClient(mc -> {
			Screen s = mc.gui.screen();
			if (!(s instanceof FitScaled f)) return "not a fit screen: " + (s == null ? "none" : s.getClass().getSimpleName());
			if (mc.getWindow().getGuiScaledWidth() >= 408 && mc.getWindow().getGuiScaledHeight() >= 240) return null;
			if (f.fitScale() >= 1f) return "not scaled down at GUI " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight();
			if (s.width < 400 || s.height < 240) return "the full panel does not fit: " + s.width + "x" + s.height;
			return null;
		});
		if (problem != null) report.add(name + ": " + problem);
	}

	// ---- pvp -------------------------------------------------------------------------------------------------------

	private void pvp(ClientGameTestContext context, String lang) {
		payload(context, "match", scratchMatch(false), 30);
		shot(context, lang + "_pvp_scratch_grudge");
		payload(context, "match", scratchMatch(true), 25);
		shot(context, lang + "_pvp_final_reveal");
		payload(context, "result", result(), 60);
		shot(context, lang + "_pvp_result");
		payload(context, "lobby", lobby(), 20);
		shot(context, lang + "_pvp_lobby");
		payload(context, "match", plinkoMatch(), 22);
		shot(context, lang + "_pvp_plinko");
		payload(context, "match", coinMatch(false), 10);
		shot(context, lang + "_pvp_coin_spin");
		context.runOnClient(mc -> ClientPvp.receive(new PvpSyncPayload("match", false, coinMatch(true).toString())));
		context.waitTicks(30);
		shot(context, lang + "_pvp_coin_landed");
		payload(context, "match", wheelMatch(false), 10);
		shot(context, lang + "_pvp_wheel_bets");
		context.runOnClient(mc -> ClientPvp.receive(new PvpSyncPayload("match", false, wheelMatch(true).toString())));
		context.waitTicks(45);
		shot(context, lang + "_pvp_wheel_spin");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static JsonObject person(int index, String name, boolean bot, long stake, boolean you, boolean allIn, boolean pressed) {
		JsonObject p = new JsonObject();
		p.addProperty("index", index);
		p.addProperty("key", bot ? "bot:b000000" + index : "00000000-0000-0000-0000-00000000000" + index);
		p.addProperty("name", bot ? "gui.burmaldaholic.bots.name.creeper42" : name);
		p.addProperty("bot", bot);
		p.addProperty("level", bot ? "hard" : "");
		p.addProperty("tagKey", "");
		p.addProperty("stake", stake);
		p.addProperty("allIn", allIn);
		p.addProperty("pressed", pressed);
		p.addProperty("rematch", index == 1);
		p.addProperty("host", index == 0);
		p.addProperty("you", you);
		if (!bot && !you) {
			p.addProperty("wins", 3);
			p.addProperty("losses", 5);
		}
		return p;
	}

	private static JsonObject base(String mode, String state, int you, boolean grudge) {
		JsonObject o = new JsonObject();
		o.addProperty("id", "k3j9x0" + mode.charAt(0));
		o.addProperty("mode", mode);
		o.addProperty("state", state);
		o.addProperty("you", you);
		o.addProperty("spectator", false);
		o.addProperty("host", 0);
		o.addProperty("rakePercent", "3");
		o.addProperty("rakeBp", 300);
		o.addProperty("entry", 200);
		o.addProperty("min", 2);
		o.addProperty("max", 6);
		o.addProperty("equalStakes", true);
		o.addProperty("grudge", grudge);
		o.addProperty("policy", "mixed");
		o.addProperty("balance", 12250);
		o.addProperty("ticksLeft", -1);
		o.add("params", new JsonObject());
		o.addProperty("final", -1);
		o.add("placings", new JsonArray());
		o.add("taunts", new JsonArray());
		return o;
	}

	private static JsonObject step(String kind, int ticks, int round, boolean wait, JsonObject data) {
		JsonObject s = new JsonObject();
		s.addProperty("kind", kind);
		s.addProperty("ticks", ticks);
		s.addProperty("round", round);
		s.addProperty("waitForAll", wait);
		s.add("data", data);
		return s;
	}

	private static JsonArray arr(long... v) {
		JsonArray a = new JsonArray();
		for (long x : v) a.add(x);
		return a;
	}

	private static JsonObject obj(Object... kv) {
		JsonObject o = new JsonObject();
		for (int i = 0; i < kv.length; i += 2) {
			Object v = kv[i + 1];
			if (v instanceof Number n) o.addProperty((String) kv[i], n);
			else if (v instanceof Boolean b) o.addProperty((String) kv[i], b);
			else if (v instanceof String s) o.addProperty((String) kv[i], s);
			else o.add((String) kv[i], (com.google.gson.JsonElement) v);
		}
		return o;
	}

	/** Scratch Showdown grudge match at cell 3 of 9 (you, a HARD bot, the grudge rival); {@code reveal}: the Final Reveal. */
	private static JsonObject scratchMatch(boolean reveal) {
		JsonObject o = base("scratch", "DRAWN", 0, true);
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Steve", false, 200, true, true, true));
		ps.add(person(1, "", true, 200, false, false, true));
		ps.add(person(2, "Notch", false, 200, false, false, false));
		o.add("participants", ps);
		o.addProperty("pot", 600);
		o.addProperty("rake", 18);
		JsonArray steps = new JsonArray();
		steps.add(step("pvp.countdown", 60, -1, false, new JsonObject()));
		int[][] symbols = {{0, 1, 4}, {2, 3, 1}, {2, 1, 7}, {2, 6, 1}, {0, 1, 1}};
		long[][] scores = {{1, 2, 10}, {4, 7, 12}, {7, 9, 12}, {12, 9, 14}, {13, 11, 15}};
		int cells = reveal ? 8 : 3;
		for (int c = 0; c < cells; c++) {
			int[] sy = symbols[Math.min(c, symbols.length - 1)];
			long[] sc = scores[Math.min(c, scores.length - 1)];
			steps.add(step("cell_wait", 40, c, true, obj("cell", c + 1, "final", false)));
			JsonArray burns = new JsonArray();
			if (c == 1) burns.add(obj("seat", 2, "cell", 1));
			steps.add(step("cell", 20, c, false, obj("cell", c + 1, "symbols", arr(sy[0], sy[1], sy[2]), "scores", arr(sc[0], sc[1], sc[2]),
				"mult", arr(1, 1, c >= 2 ? 2 : 1), "burns", burns)));
		}
		steps.add(step("cell_wait", 40, cells, true, obj("cell", cells + 1, "final", cells == 8)));
		if (reveal) {
			steps.add(step("pvp.final", 40, -1, false, new JsonObject()));
			o.addProperty("final", 3);
			JsonArray placings = new JsonArray();
			placings.add(obj("place", 3, "seat", 1, "points", 150, "events",
				new JsonArray()));
			o.add("placings", placings);
		} else {
			o.add("taunts", new JsonArray());
			o.getAsJsonArray("taunts").add(obj("seat", 2, "line", 3, "age", 5));
		}
		o.add("steps", steps);
		return o;
	}

	private static JsonObject result() {
		JsonObject o = base("scratch", "SETTLED", 0, false);
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Steve", false, 200, true, false, false));
		ps.add(person(1, "Notch", false, 200, false, false, false));
		ps.add(person(2, "", true, 200, false, false, false));
		o.add("participants", ps);
		o.addProperty("pot", 400);
		o.addProperty("rake", 12);
		o.add("steps", new JsonArray());
		JsonObject r = new JsonObject();
		r.add("order", arr(0, 1, 2));
		r.add("points", arr(3400, 2150, 20));
		r.add("winners", arr(0));
		r.add("payouts", arr(388, 0, 0));
		r.add("places", arr(1, 2, 3));
		o.add("result", r);
		o.add("payouts", arr(388, 0, 0));
		return o;
	}

	private static JsonObject lobby() {
		JsonObject o = base("slots", "LOBBY", 1, false);
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Alexander_the_Great", false, 100, false, true, false));
		ps.add(person(1, "Steve", false, 100, true, false, false));
		ps.add(person(2, "", true, 100, false, false, false));
		o.add("participants", ps);
		o.addProperty("pot", 300);
		o.addProperty("rake", 9);
		o.addProperty("botsToFill", 2);
		o.addProperty("ticksLeft", 1160);
		o.add("steps", new JsonArray());
		return o;
	}

	private static JsonObject plinkoMatch() {
		JsonObject o = base("plinko", "DRAWN", 0, false);
		o.add("params", obj("risk", "medium", "balls", 3));
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Steve", false, 100, true, false, false));
		ps.add(person(1, "Notch", false, 100, false, false, true));
		ps.add(person(2, "", true, 100, false, false, true));
		o.add("participants", ps);
		o.addProperty("pot", 300);
		JsonArray row = arr(330, 110, 40, 20, 10, 6, 3, 6, 10, 20, 40, 110, 330);
		JsonArray steps = new JsonArray();
		steps.add(step("pvp.countdown", 60, -1, false, new JsonObject()));
		steps.add(step("ball_wait", 80, 0, true, obj("ball", 1, "balls", 3, "final", false, "row", row)));
		steps.add(step("drop", 48, 0, false, obj("ball", 1, "balls", 3, "masks", arr(0b101101010110, 0b000011110000, 0b111100001010), "rows", 12,
			"bins", arr(7, 4, 6), "points", arr(6, 10, 3), "edges", new JsonArray())));
		steps.add(step("ball_score", 40, 0, false, obj("ball", 1, "balls", 3, "totals", arr(6, 10, 3), "standings", arr(1, 0, 2))));
		steps.add(step("ball_wait", 80, 1, true, obj("ball", 2, "balls", 3, "final", false, "row", row)));
		steps.add(step("drop", 48, 1, false, obj("ball", 2, "balls", 3, "masks", arr(0b110110101101, 0b011010010110, 0b100100100100), "rows", 12,
			"bins", arr(8, 6, 4), "points", arr(10, 3, 10), "edges", new JsonArray())));
		o.add("steps", steps);
		return o;
	}

	private static JsonObject coinMatch(boolean landed) {
		JsonObject o = base("coin", "DRAWN", 0, false);
		o.add("params", obj("heads", true, "stake", 250));
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Steve", false, 250, true, false, false));
		ps.add(person(1, "Notch", false, 250, false, true, false));
		o.add("participants", ps);
		o.addProperty("pot", 500);
		o.addProperty("link", 2);
		JsonArray steps = new JsonArray();
		steps.add(step("countdown", 60, -1, false, obj("heads0", true)));
		steps.add(step("spin", 20, 0, false, new JsonObject()));
		if (landed) steps.add(step("land", 30, 0, false, obj("heads", false, "winner", 1)));
		o.add("steps", steps);
		return o;
	}

	private static JsonObject wheelMatch(boolean spin) {
		JsonObject o = base("wheel", "DRAWN", 0, false);
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Steve", false, 100, true, false, false));
		ps.add(person(1, "Notch", false, 50, false, false, false));
		ps.add(person(2, "", true, 200, false, false, false));
		ps.add(person(3, "Alex", false, 30, false, false, false));
		o.add("participants", ps);
		o.addProperty("pot", 380);
		o.addProperty("ticksLeft", 300);
		JsonArray steps = new JsonArray();
		if (spin) steps.add(step("spin", 100, 0, false, obj("angle1000", 222000, "winner", 2, "hair", -1)));
		o.add("steps", steps);
		return o;
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static CompoundTag chips(long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("stake", "chips");
		t.putLong("amount", amount);
		return t;
	}

	private static void action(MinecraftServer server, BlockPos pos, String action, CompoundTag args) {
		if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) {
			table.onAction(player(server), action, args);
		}
	}

	private int nextTable;

	private BlockPos table(ClientGameTestContext context, TestSingleplayerContext world, TableType<?> type) {
		int dz = nextTable++ % 3 - 1;
		BlockPos[] at = new BlockPos[1];
		open(context, world, server -> {
			ServerPlayer p = player(server);
			BlockPos pos = p.blockPosition().offset(2, 0, dz);
			at[0] = pos;
			server.overworld().setBlockAndUpdate(pos, type.block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity t) {
				p.openMenu(t);
				t.sendStateTo(p);
			}
		});
		return at[0];
	}

	private void open(ClientGameTestContext context, TestSingleplayerContext world, Consumer<MinecraftServer> opener) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			player(server).closeContainer();
			opener.accept(server);
		});
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			report.add("screen did not open");
		}
		context.waitTicks(10);
	}

	private void payload(ClientGameTestContext context, String kind, JsonObject state, int wait) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		context.runOnClient(mc -> ClientPvp.receive(new PvpSyncPayload(kind, true, state.toString())));
		context.waitTicks(wait);
	}

	private static void clearCelebration(ClientGameTestContext context) {
		context.waitTicks(10);
		context.runOnClient(mc -> CelebrationOverlay.get().clear());
	}

	private void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot("jtest_extras_" + name);
		for (String p : context.computeOnClient(mc -> inspect(mc, mc.gui.screen()))) report.add(name + ": " + p);
	}

	private static List<String> inspect(Minecraft mc, Screen screen) {
		List<String> out = new ArrayList<>();
		if (screen == null) {
			out.add("no screen");
			return out;
		}
		out.add("screen " + screen.getClass().getSimpleName());
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) continue;
			String label = w.getMessage().getString();
			if (w instanceof AbstractButton && !label.isEmpty() && mc.font.width(w.getMessage()) > w.getWidth() - 4) {
				out.add("label squeezed (" + mc.font.width(w.getMessage()) + " > " + (w.getWidth() - 4) + "): \"" + label + "\"");
			}
			if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height) {
				out.add("widget outside the screen: \"" + label + "\"");
			}
		}
		return out;
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("jtest_extras_layout.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(20);
	}
}
