package dev.nezo.burmaldaholic.gametest.v2.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpFrame;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasScreenPayload;
import dev.nezo.burmaldaholic.pvp.client.ClientPvp;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

/**
 * Lane E: the PvP screens in the table UI v2 frame (REDESIGN.md §7.8; ui-v2.md §11.2): every mode's duel screen (Coin
 * Flip Duel landing, Wheel Party bets and spin, Plinko Battle, Scratch Showdown grudge + Final Reveal), the lobby, the
 * result window and the Dice Duel invite card, at 480 × 270 (1920 × 1080 @4) and 640 × 360 (1920 × 1080 @3), English and
 * Russian. Asserts the v2 frame: every visible button inside the window, the main buttons in the Dock band, nothing
 * between the content and the TopBar. Screenshots {@code p2b_e2_pvp_<view>_<lang>_<w>}. The real PvP flows (two players,
 * every mode played to the settle through these screens) are {@code gametest/pvp/PvpRealFlowClientGameTests}.
 */
public class PvpV2ClientGameTests implements FabricClientGameTest {
	private final List<String> problems = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("E")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			RealFlow f = new RealFlow(context, world);
			context.waitTicks(20);
			String[][] runs = {{"en_us", "1920", "1080", "4"}, {"ru_ru", "1920", "1080", "4"}, {"en_us", "1920", "1080", "3"}, {"ru_ru", "1920", "1080", "3"},
				{"en_us", "1280", "720", "3"}};
			for (String[] r : runs) {
				if (!r[0].equals(f.client(mc -> mc.options.languageCode))) f.language(r[0]);
				f.size(Integer.parseInt(r[1]), Integer.parseInt(r[2]), Integer.parseInt(r[3]));
				String tag = r[0] + "_" + f.client(mc -> mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
				captures(f, tag);
			}
			f.language("en_us");
			f.size(854, 480, 0);
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ClientPvp.setStateForTests(null, "clear");
			});
			context.waitTicks(5);
		}
		if (!problems.isEmpty()) throw new AssertionError("PvP v2 frame problems:\n - " + String.join("\n - ", problems));
	}

	private void captures(RealFlow f, String tag) {
		ClientGameTestContext context = f.context();
		payload(context, "match", scratchMatch(false), 30);
		check(f, tag, "scratch_grudge");
		payload(context, "match", scratchMatch(true), 25);
		check(f, tag, "final_reveal");
		payload(context, "result", result(), 60);
		check(f, tag, "result");
		payload(context, "lobby", lobby(), 20);
		check(f, tag, "lobby");
		payload(context, "match", plinkoMatch(), 22);
		check(f, tag, "plinko");
		payload(context, "match", coinMatch(false), 10);
		check(f, tag, "coin_spin");
		context.runOnClient(mc -> ClientPvp.receive(new PvpSyncPayload("match", false, coinMatch(true).toString())));
		context.waitTicks(30);
		check(f, tag, "coin_landed");
		payload(context, "match", wheelMatch(false), 10);
		check(f, tag, "wheel_bets");
		context.runOnClient(mc -> ClientPvp.receive(new PvpSyncPayload("match", false, wheelMatch(true).toString())));
		context.waitTicks(45);
		check(f, tag, "wheel_spin");
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			ClientPvp.setStateForTests(null, "clear");
			CelebrationOverlay.get().clear();
		});
		context.waitTicks(3);
		// the Dice Duel invite card (as the server sends it to the challenged player)
		CompoundTag invite = new CompoundTag();
		invite.putInt("id", 99);
		invite.putString("name", "Alexander_the_Great");
		invite.putString("uuid", "00000000-0000-0000-0000-000000000002");
		invite.putLong("stake", 250);
		invite.putLong("ticks", 600);
		context.runOnClient(mc -> mc.getConnection().handleCustomPayload(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
			new ExtrasScreenPayload("duel_invite", true, invite))));
		context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("DuelInviteScreen"), RealFlow.TIMEOUT);
		context.waitTicks(10);
		f.shot(shotName(tag, "invite"));
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(3);
	}

	/** 2b standard name {@code p2b_e2_pvp_<view>_<lang>_<w>} from the run tag {@code <lang>_<w>x<h>}. */
	private static String shotName(String tag, String view) {
		String lang = tag.substring(0, 2);
		String w = tag.substring(tag.indexOf('_', 3) + 1).split("x")[0];
		return "p2b_e2_pvp_" + view + "_" + lang + "_" + w;
	}

	/** Screenshot + the frame checks: buttons inside the window, the Dock row in the Dock band. */
	private void check(RealFlow f, String tag, String view) {
		f.shot(shotName(tag, view));
		String p = f.client(mc -> {
			Screen s = mc.gui.screen();
			if (s == null) return "no screen";
			PvpFrame.Layout l = s instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h ? PvpFrame.layout(h.chrome().grid()) : PvpFrame.layout(s.width, s.height);
			List<String> out = new ArrayList<>();
			for (var c : s.children()) {
				if (!(c instanceof AbstractWidget w) || !w.visible) continue;
				if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > s.width || w.getY() + w.getHeight() > s.height) {
					out.add("'" + w.getMessage().getString() + "' outside the window");
				}
				if (w.getY() < l.top().bottom()) out.add("'" + w.getMessage().getString() + "' under the TopBar");
				boolean inDock = w.getY() >= l.dock().y();
				boolean inPlay = w.getY() + w.getHeight() <= l.dock().y();
				if (!inDock && !inPlay) out.add("'" + w.getMessage().getString() + "' straddles the Dock edge");
			}
			// R-CDE M7: a PvP panel grows for wrapped (RU) lines: the panel holds the content, the content ends above the Dock
			if (s instanceof dev.nezo.burmaldaholic.games.extras.client.pvp.coin.PvpPanel panel) {
				if (panel.panelHeight() < panel.contentBottom() - panel.panelTop()) {
					out.add("panel " + panel.panelHeight() + " shorter than its content " + (panel.contentBottom() - panel.panelTop()));
				}
				if (panel.contentBottom() > l.dock().y()) out.add("content bottom " + panel.contentBottom() + " below the Dock " + l.dock().y());
				if (panel.panelTop() + PvpFrame.CONTENT_TOP < l.top().bottom()) out.add("content under the TopBar");
			}
			if (s instanceof dev.nezo.burmaldaholic.games.extras.client.pvp.wheel.WheelPartyScreen wheel) {
				if (wheel.wheelBottom() > l.dock().y()) out.add("wheel bottom " + wheel.wheelBottom() + " under the Dock " + l.dock().y());
				if (wheel.wheelTop() < l.play().y()) out.add("wheel top " + wheel.wheelTop() + " under the TopBar");
			}
			return String.join("; ", out);
		});
		if (!p.isEmpty()) problems.add(tag + " " + view + ": " + p);
	}

	private void payload(ClientGameTestContext context, String kind, JsonObject state, int wait) {
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			ClientPvp.setStateForTests(null, "clear");
			ClientPvp.receive(new PvpSyncPayload(kind, true, state.toString()));
		});
		context.waitTicks(wait);
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

}
