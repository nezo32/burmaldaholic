package dev.nezo.burmaldaholic.games.slots.client.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotSymbols;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Slot Showdown match screen (PVP.md §5.5 Java): one panel per player (name, 3×3 reels, total, rank, this
 * spin's points, ALL-IN tag), header "SLOT SHOWDOWN · tier · Spin r/N · HOT: X ×2 · Pot", [Spin!]. Compact
 * (&lt; 400 px wide): your panel at 2× on the left, ranking list on the right. Final round: totals show "???"
 * until the engine's Final Reveal / result.
 *
 * <p>Registered with {@link PvpScreens} for mode {@code slots}; the pvp client module creates / updates it
 * from the match sync (public state + revealed steps only). Expected sync fields (tolerant, all optional):
 * {@code id, state, params{tier,spins}, pot, you (participant index), participants[{index, key, name, bot,
 * level, allIn}], step{kind, round, ticks, data}} or {@code steps[]} (all revealed steps), {@code ticksLeft},
 * {@code points[]} (after SETTLE). Step data: see {@code games.slots.pvp.ShowdownTimeline}.
 */
public final class SlotShowdownScreen extends Screen implements PvpScreens.ModeScreen {
	private static final int GOLD = 0xFFFFD24A;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int GRAY = 0xFFB0B0B0;
	private static final int RED = 0xFFFF5555;
	private static final int GREEN = 0xFF55FF55;
	private static final int PANEL_W = 124;
	private static final int PANEL_H = 76;
	private static final int CELL = 16;
	private static final long SPIN_MS = 2000;

	/**
	 * How Spin! reaches the server (the pvp client module wires its "press" packet here; PVP.md §5.5). Unset:
	 * the button is hidden and players press Spin! from the slot machine's Showdown panel.
	 */
	private static @Nullable Consumer<String> pressAction;

	public static void setPressAction(@Nullable Consumer<String> action) {
		pressAction = action;
	}

	public static void register() {
		PvpScreens.register("slots", SlotShowdownScreen::new);
		// Spin! from the match screen goes through the pvp client module's action packet (engine press, PVP.md §5.5)
		if (pressAction == null) {
			setPressAction(matchId -> PvpScreens.action("press", matchId, "", 0));
		}
	}

	private JsonObject state;
	private final List<Player> players = new ArrayList<>();
	private int round = -1;
	private int spins = 5;
	private int hot = -1;
	private boolean finalRound;
	private boolean waiting;
	private long[] totals = new long[0];
	private long[] spinPoints = new long[0];
	private boolean totalsHidden;
	private int[][] grids = new int[0][];
	private final List<Component> log = new ArrayList<>();
	private final int[] flash = new int[6];
	private long spinStart;
	private String lastStepKey = "";
	private int ticksLeft = -1;
	private int left;
	private int top;
	private int w;
	private int h;
	private boolean compact;

	private record Player(int index, Component name, boolean you, boolean allIn) {}

	public SlotShowdownScreen(JsonObject initial) {
		super(Component.translatable("gui.burmaldaholic.pvp.slots.title"));
		update(initial);
	}

	@Override
	public Screen screen() {
		return this;
	}

	// ---- state -----------------------------------------------------------------------------------

	@Override
	public void update(JsonObject s) {
		this.state = s == null ? new JsonObject() : s;
		readPlayers();
		JsonObject params = obj(state, "params");
		spins = intOr(params, "spins", spins);
		ticksLeft = intOr(state, "ticksLeft", -1);
		if (state.has("steps") && state.get("steps").isJsonArray()) {
			for (JsonElement e : state.getAsJsonArray("steps")) {
				if (e.isJsonObject()) {
					apply(e.getAsJsonObject());
				}
			}
		} else if (state.has("step") && state.get("step").isJsonObject()) {
			apply(state.getAsJsonObject("step"));
		}
		if (state.has("points") && state.get("points").isJsonArray()) {
			totals = longs(state.getAsJsonArray("points"));
			totalsHidden = false;
		}
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	private void readPlayers() {
		players.clear();
		int you = intOr(state, "you", -1);
		String me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID().toString() : "";
		JsonArray ps = state.has("participants") && state.get("participants").isJsonArray() ? state.getAsJsonArray("participants") : new JsonArray();
		for (int i = 0; i < ps.size(); i++) {
			JsonObject p = ps.get(i).isJsonObject() ? ps.get(i).getAsJsonObject() : new JsonObject();
			int index = intOr(p, "index", i);
			String name = strOr(p, "name", "?");
			boolean bot = p.has("bot") && p.get("bot").getAsBoolean();
			Component display = bot ? Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(name)) : Texts.raw(name);
			boolean isYou = index == you || me.equals(strOr(p, "key", strOr(p, "id", "")));
			players.add(new Player(index, display, isYou, p.has("allIn") && p.get("allIn").getAsBoolean()));
		}
	}

	/** Applies one revealed step (idempotent per kind + round). */
	private void apply(JsonObject step) {
		String kind = strOr(step, "kind", "");
		int r = intOr(step, "round", -1);
		JsonObject data = obj(step, "data");
		String key = kind + "#" + r;
		boolean fresh = !key.equals(lastStepKey);
		switch (kind) {
			case "round_wait" -> {
				round = r;
				waiting = true;
				hot = intOr(data, "hot", -1);
				finalRound = data.has("final") && data.get("final").getAsBoolean();
				spins = intOr(data, "spins", spins);
			}
			case "underdog" -> {
				if (fresh && data.has("seats")) {
					for (JsonElement e : data.getAsJsonArray("seats")) {
						log(Component.translatable("msg.burmaldaholic.pvp.slots.underdog", name(e.getAsInt())));
					}
				}
			}
			case "spin" -> {
				round = r;
				waiting = false;
				finalRound = data.has("final") && data.get("final").getAsBoolean();
				JsonArray g = data.has("grids") ? data.getAsJsonArray("grids") : new JsonArray();
				grids = new int[g.size()][];
				for (int i = 0; i < g.size(); i++) {
					grids[i] = ints(g.get(i).getAsJsonArray());
				}
				if (finalRound) {
					totalsHidden = true;
				}
				if (fresh) {
					spinStart = System.currentTimeMillis();
					playSpin();
				}
			}
			case "score" -> {
				round = r;
				waiting = false;
				if (data.has("totals")) {
					totals = longs(data.getAsJsonArray("totals"));
				}
				if (data.has("points")) {
					spinPoints = longs(data.getAsJsonArray("points"));
				}
				if (fresh && data.has("events")) {
					for (JsonElement e : data.getAsJsonArray("events")) {
						event(e.getAsJsonObject());
					}
				}
			}
			default -> {
			}
		}
		lastStepKey = key;
	}

	private void event(JsonObject e) {
		int seat = intOr(e, "seat", -1);
		switch (strOr(e, "kind", "")) {
			case "kaboom" -> {
				log(Component.translatable("msg.burmaldaholic.pvp.slots.kaboom", name(seat), Texts.number(longOr(e, "before")),
					Texts.number(longOr(e, "after"))));
				flash(seat, 20);
			}
			case "swap" -> {
				int other = intOr(e, "other", -1);
				log(Component.translatable("msg.burmaldaholic.pvp.slots.swap", name(seat), name(other), Texts.number(longOr(e, "from")),
					Texts.number(longOr(e, "to"))));
				flash(seat, 20);
				flash(other, 20);
			}
			case "time_warp" -> log(Component.translatable("msg.burmaldaholic.pvp.slots.time_warp", name(seat)));
			case "star" -> log(Component.translatable("msg.burmaldaholic.pvp.slots.star", name(seat), Texts.number(longOr(e, "points"))));
			default -> {
			}
		}
	}

	private void flash(int seat, int ticks) {
		if (seat >= 0 && seat < flash.length) {
			flash[seat] = ticks;
		}
	}

	private void log(Component c) {
		log.add(c);
		while (log.size() > 3) {
			log.removeFirst();
		}
	}

	private Component name(int index) {
		for (Player p : players) {
			if (p.index() == index) {
				return p.name();
			}
		}
		return Texts.raw("#" + (index + 1));
	}

	// ---- widgets -------------------------------------------------------------------------------

	@Override
	protected void init() {
		compact = width < 420;
		w = compact ? 320 : 400;
		h = compact ? 220 : 240;
		left = (width - w) / 2;
		top = (height - h) / 2;
		rebuildWidgets();
	}

	@Override
	protected void rebuildWidgets() {
		clearWidgets();
		String matchId = strOr(state, "id", "");
		if (pressAction != null && waiting && !matchId.isEmpty()) {
			Component label = Component.translatable("gui.burmaldaholic.pvp.slots.spin_now");
			int bw = Math.max(80, font.width(label) + 8);
			addRenderableWidget(Button.builder(label, b -> pressAction.accept(matchId)).bounds(left + 8, top + h - 26, bw, 20).build());
		}
	}

	@Override
	public void tick() {
		for (int i = 0; i < flash.length; i++) {
			if (flash[i] > 0) {
				flash[i]--;
			}
		}
		if (ticksLeft > 0) {
			ticksLeft--;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private void playSpin() {
		Minecraft mc = Minecraft.getInstance();
		if (CoreSounds.SLOT_SPIN != null) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(CoreSounds.SLOT_SPIN, 1.0f));
		}
	}

	// ---- rendering -----------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(left - 1, top - 1, left + w + 1, top + h + 1, 0xFF0E2E1C);
		g.fill(left, top, left + w, top + h, 0xF01E5E3A);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		header(g);
		int[] ranks = ranks();
		if (compact) {
			compactBody(g, ranks);
		} else {
			for (int k = 0; k < players.size() && k < 6; k++) {
				int x = left + 8 + (k % 3) * (PANEL_W + 4);
				int y = top + 30 + (k / 3) * (PANEL_H + 4);
				panel(g, players.get(k), x, y, 1, ranks);
			}
		}
		int y = top + h - 52;
		for (Component c : log) {
			for (var line : font.split(c, w - 16)) {
				if (y < top + h - 28) {
					g.text(font, line, left + 8, y, GRAY, true);
					y += 10;
				}
			}
		}
		if (ticksLeft > 0 && waiting) {
			Component auto = Component.translatable("gui.burmaldaholic.pvp.slots.auto_in", Texts.plural("unit.burmaldaholic.second", (ticksLeft + 19) / 20));
			g.text(font, auto, left + w - 8 - font.width(auto), top + h - 20, GRAY, true);
		}
	}

	private void header(GuiGraphicsExtractor g) {
		MutableComponent head = Component.translatable("gui.burmaldaholic.pvp.slots.title").copy();
		JsonObject params = obj(state, "params");
		String tier = strOr(params, "tier", "");
		dev.nezo.burmaldaholic.games.slots.logic.Tier t = dev.nezo.burmaldaholic.games.slots.logic.Tier.byId(tier);
		if (t != null) {
			head.append(Texts.raw(" · ")).append(Component.translatable("block.burmaldaholic." + t.blockName()));
		}
		g.text(font, head, left + 8, top + 6, GOLD, true);
		List<Component> second = new ArrayList<>();
		if (round >= 0) {
			second.add(finalRound ? Component.translatable("gui.burmaldaholic.pvp.slots.final_spin")
				: Component.translatable("gui.burmaldaholic.pvp.slots.round", Texts.number(round + 1), Texts.number(spins)));
		}
		if (hot >= 0) {
			second.add(Component.translatable("gui.burmaldaholic.pvp.slots.hot", Component.translatable(Symbol.byOrdinal(hot).translationKey())));
		}
		if (state.has("pot")) {
			second.add(Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(state.get("pot").getAsLong())));
		}
		int x = left + 8;
		for (Component c : second) {
			g.text(font, c, x, top + 17, TEXT, true);
			x += font.width(c) + 10;
		}
	}

	private void compactBody(GuiGraphicsExtractor g, int[] ranks) {
		Player you = null;
		for (Player p : players) {
			if (p.you()) {
				you = p;
			}
		}
		if (you != null) {
			panel(g, you, left + 8, top + 30, 1, ranks);
		}
		int x = left + 8 + PANEL_W + 8;
		int y = top + 30;
		for (int place = 0; place < players.size(); place++) {
			for (Player p : players) {
				if (ranks.length > p.index() && ranks[p.index()] == place + 1) {
					Component row = Component.translatable("gui.burmaldaholic.pvp.match.row", Texts.number(place + 1), p.name(), totalText(p.index()));
					g.text(font, clip(row, left + w - 8 - x), x, y, p.you() ? GOLD : TEXT, true);
					y += 11;
				}
			}
		}
	}

	private void panel(GuiGraphicsExtractor g, Player p, int x, int y, int scale, int[] ranks) {
		int i = p.index();
		boolean shaking = i < flash.length && flash[i] > 0;
		int dx = shaking ? (flash[i] % 2 == 0 ? 1 : -1) : 0;
		g.fill(x + dx, y, x + dx + PANEL_W, y + PANEL_H, shaking ? 0xC0801010 : p.you() ? 0xC0404018 : 0xC0101010);
		Component name = p.allIn() ? Component.translatable("gui.burmaldaholic.pvp.bots.tagged", p.name(), Component.translatable("gui.burmaldaholic.pvp.all_in_tag"))
			: p.name();
		g.text(font, clip(name, PANEL_W - 6), x + dx + 3, y + 3, p.you() ? GOLD : TEXT, true);
		int gx = x + dx + 4;
		int gy = y + 14;
		long elapsed = System.currentTimeMillis() - spinStart;
		int[] grid = i < grids.length ? grids[i] : null;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				g.fill(gx + c * (CELL + 2), gy + r * (CELL + 2), gx + c * (CELL + 2) + CELL, gy + r * (CELL + 2) + CELL, 0xFFEFE6D2);
				Symbol s;
				if (grid == null || grid.length != 9) {
					continue;
				}
				boolean spinningCol = elapsed < SPIN_MS * (c + 2) / 4;
				s = spinningCol ? Symbol.byOrdinal(ThreadLocalRandom.current().nextInt(6)) : Symbol.byOrdinal(grid[r * 3 + c]);
				g.item(SlotSymbols.icon(s), gx + c * (CELL + 2), gy + r * (CELL + 2));
				if (!spinningCol && hot >= 0 && s.ordinal() == hot) {
					g.fill(gx + c * (CELL + 2), gy + r * (CELL + 2) + CELL - 2, gx + c * (CELL + 2) + CELL, gy + r * (CELL + 2) + CELL, 0xFFFF7A1A);
				}
			}
		}
		int tx = gx + 3 * (CELL + 2) + 4;
		int rank = i < ranks.length ? ranks[i] : 0;
		if (rank > 0 && !totalsHidden) {
			g.text(font, Texts.raw("#" + rank), tx, gy, GRAY, true);
		}
		g.text(font, totalText(i), tx, gy + 12, TEXT, true);
		if (!totalsHidden && i < spinPoints.length && System.currentTimeMillis() - spinStart >= SPIN_MS) {
			g.text(font, Texts.raw("+" + spinPoints[i]), tx, gy + 24, spinPoints[i] > 0 ? GREEN : GRAY, true);
		}
		if (shaking) {
			g.text(font, Component.translatable("gui.burmaldaholic.pvp.slots.kaboom_title"), tx, gy + 36, RED, true);
		}
	}

	/** First wrapped line only (names are the only thing that may be cut, UI.md §0.1). */
	private net.minecraft.util.FormattedCharSequence clip(Component c, int width) {
		var lines = font.split(c, Math.max(10, width));
		return lines.isEmpty() ? net.minecraft.util.FormattedCharSequence.EMPTY : lines.getFirst();
	}

	private Component totalText(int i) {
		if (totalsHidden) {
			return Component.translatable("gui.burmaldaholic.pvp.match.hidden");
		}
		return Texts.number(i < totals.length ? totals[i] : 0);
	}

	/** 1-based rank per participant index by total (ties share a rank). */
	private int[] ranks() {
		int n = 0;
		for (Player p : players) {
			n = Math.max(n, p.index() + 1);
		}
		int[] out = new int[n];
		for (Player p : players) {
			int i = p.index();
			long mine = i < totals.length ? totals[i] : 0;
			int better = 0;
			for (Player q : players) {
				long theirs = q.index() < totals.length ? totals[q.index()] : 0;
				if (theirs > mine) {
					better++;
				}
			}
			out[i] = better + 1;
		}
		return out;
	}

	// ---- json helpers --------------------------------------------------------------------------

	private static JsonObject obj(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
	}

	private static int intOr(JsonObject o, String k, int def) {
		try {
			return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static long longOr(JsonObject o, String k) {
		try {
			return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String strOr(JsonObject o, String k, String def) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
	}

	private static int[] ints(JsonArray a) {
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsInt();
		}
		return out;
	}

	private static long[] longs(JsonArray a) {
		long[] out = new long[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsLong();
		}
		return out;
	}
}
