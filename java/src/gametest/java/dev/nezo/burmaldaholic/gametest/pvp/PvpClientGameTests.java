package dev.nezo.burmaldaholic.gametest.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * PvP screens in English and Russian (PVP.md §3.11, §16.9): the hub (Casino Menu → Challenges, main and New
 * match views), a 6-seat lobby with a host, an ALL-IN player, a bot and empty seats, the generic live-match
 * screen, the result window after the last→first reveal, and the taunt picker. The lobby / match / result states
 * are synthetic payloads (the engine is not needed to lay the screens out). Screenshots {@code jtest_pvp_<lang>_<view>}
 * and a layout report {@code jtest_pvp_layout.txt} (labels wider than their button, widgets outside the screen).
 */
public class PvpClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			world.getServer().runCommand("casino balance set @p 12500");
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				menu(context, world, lang + "_hub", server -> {
					ServerPlayer p = first(server);
					CasinoMenu.pages().stream().filter(pg -> pg.id().equals("challenges")).findFirst().ifPresent(pg -> pg.action(p, "p:view:main", 0));
					CasinoMenu.open(p, "challenges");
				});
				menu(context, world, lang + "_hub_new", server -> {
					ServerPlayer p = first(server);
					CasinoMenu.pages().stream().filter(pg -> pg.id().equals("challenges")).findFirst().ifPresent(pg -> pg.action(p, "p:view:new", 0));
					CasinoMenu.open(p, "challenges");
				});
				payload(context, lang + "_lobby", "lobby", lobby(), 20);
				payload(context, lang + "_match", "match", match(), 20);
				payload(context, lang + "_result", "result", result(), 90);
				context.runOnClient(mc -> dev.nezo.burmaldaholic.client.pvp.PvpScreens.openTaunts(mc.gui.screen()));
				context.waitTicks(5);
				shot(context, lang + "_taunts");
			}
		} finally {
			write(context);
			language(context, "en_us");
		}
	}

	private static ServerPlayer first(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private void menu(ClientGameTestContext context, TestSingleplayerContext world, String name, Consumer<MinecraftServer> opener) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		world.getServer().runOnServer(opener::accept);
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			report.add(name + ": screen did not open");
			return;
		}
		context.waitTicks(10);
		shot(context, name);
	}

	private void payload(ClientGameTestContext context, String name, String kind, JsonObject state, int wait) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		context.runOnClient(mc -> ClientPvp.receive(new PvpSyncPayload(kind, true, state.toString())));
		context.waitTicks(wait);
		shot(context, name);
	}

	private void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot("jtest_pvp_" + name);
		for (String p : context.computeOnClient(mc -> inspect(mc, mc.gui.screen()))) {
			report.add(name + ": " + p);
		}
	}

	// ---- synthetic states (the PvpMatchView shape) ---------------------------------------------

	private static JsonObject person(int index, String name, boolean bot, long stake, boolean host, boolean you, boolean allIn) {
		JsonObject p = new JsonObject();
		p.addProperty("index", index);
		p.addProperty("key", bot ? "bot:b000000" + index : "00000000-0000-0000-0000-00000000000" + index);
		p.addProperty("name", bot ? "gui.burmaldaholic.bots.name.creeper42" : name);
		p.addProperty("bot", bot);
		p.addProperty("tagKey", bot ? "gui.burmaldaholic.bots.style.hard" : "");
		p.addProperty("stake", stake);
		p.addProperty("allIn", allIn);
		p.addProperty("pressed", false);
		p.addProperty("rematch", index == 1);
		p.addProperty("host", host);
		p.addProperty("you", you);
		if (!bot && !you) {
			p.addProperty("wins", 3);
			p.addProperty("losses", 5);
		}
		return p;
	}

	private static JsonObject base(String mode, String state, int you) {
		JsonObject o = new JsonObject();
		o.addProperty("id", "k3j9x0aa");
		o.addProperty("mode", mode);
		o.addProperty("state", state);
		o.addProperty("you", you);
		o.addProperty("spectator", false);
		o.addProperty("host", 0);
		o.addProperty("rakePercent", "3");
		o.addProperty("entry", 100);
		o.addProperty("min", 2);
		o.addProperty("max", 6);
		o.addProperty("equalStakes", true);
		o.addProperty("grudge", false);
		o.addProperty("policy", "mixed");
		o.addProperty("balance", 12500);
		o.add("params", new JsonObject());
		o.add("steps", new JsonArray());
		o.addProperty("final", -1);
		return o;
	}

	private static JsonObject lobby() {
		JsonObject o = base("slots", "LOBBY", 1);
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Alexander_the_Great", false, 100, true, false, true));
		ps.add(person(1, "Steve", false, 100, false, true, false));
		ps.add(person(2, "", true, 100, false, false, false));
		o.add("participants", ps);
		o.addProperty("pot", 300);
		o.addProperty("rake", 9);
		o.addProperty("botsToFill", 2);
		o.addProperty("ticksLeft", 1160);
		return o;
	}

	private static JsonObject match() {
		JsonObject o = base("scratch", "DRAWN", 0);
		o.addProperty("host", 0);
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Steve", false, 200, true, true, false));
		ps.add(person(1, "Alexander_the_Great", false, 200, false, false, true));
		o.add("participants", ps);
		o.addProperty("pot", 400);
		o.addProperty("rake", 12);
		o.addProperty("grudge", true);
		o.addProperty("ticksLeft", -1);
		JsonArray steps = new JsonArray();
		for (int i = 0; i < 4; i++) {
			JsonObject s = new JsonObject();
			s.addProperty("kind", "cell");
			s.addProperty("ticks", 40);
			s.addProperty("round", i);
			s.addProperty("waitForAll", true);
			s.add("data", new JsonObject());
			steps.add(s);
		}
		o.add("steps", steps);
		return o;
	}

	private static JsonArray arr(long... v) {
		JsonArray a = new JsonArray();
		for (long x : v) {
			a.add(x);
		}
		return a;
	}

	private static JsonObject result() {
		JsonObject o = base("slots", "SETTLED", 1);
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Alexander_the_Great", false, 100, true, false, true));
		ps.add(person(1, "Steve", false, 100, false, true, false));
		ps.add(person(2, "", true, 100, false, false, false));
		ps.add(person(3, "Notch", false, 100, false, false, false));
		o.add("participants", ps);
		o.addProperty("pot", 400);
		o.addProperty("rake", 12);
		o.addProperty("ticksLeft", -1);
		JsonObject r = new JsonObject();
		r.add("order", arr(1, 3, 0, 2));
		r.add("points", arr(1900, 3400, 20, 2150));
		r.add("winners", arr(1));
		r.add("payouts", arr(0, 388, 0, 0));
		r.add("places", arr(3, 1, 4, 2));
		o.add("result", r);
		return o;
	}

	// ---- helpers (as in the other client tests) --------------------------------------------------

	private static List<String> inspect(Minecraft mc, Screen screen) {
		List<String> out = new ArrayList<>();
		if (screen == null) {
			out.add("no screen");
			return out;
		}
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) {
				continue;
			}
			String label = w.getMessage().getString();
			if (w instanceof AbstractButton && !label.isEmpty() && mc.font.width(w.getMessage()) > w.getWidth() - 4) {
				out.add("label overflows button: \"" + label + "\"");
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
			Files.writeString(dir.resolve("jtest_pvp_layout.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
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
