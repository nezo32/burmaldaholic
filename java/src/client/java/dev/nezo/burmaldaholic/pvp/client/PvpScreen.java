package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Base of the pvp module's screens (lobby, generic match, result, taunts; visual/extras.md §7): the arena scene (the
 * grudge variant in a grudge match) as a 400 × 240 panel, the title at the top left and a right-aligned status at the
 * top right (or a title banner), casino buttons and the error line. Server-driven: renders the last
 * {@code PvpSyncPayload} state; buttons send {@code PvpActionPayload}s.
 */
abstract class PvpScreen extends dev.nezo.burmaldaholic.client.ui.CasinoScreen {
	static final int TEXT = Kit.BONE;
	static final int MUTED = Kit.BONE_SHADE;
	static final int GOLD = Kit.GOLD;
	static final int GREEN = Kit.BONUS;
	static final int RED = Kit.RED_LIGHT;
	static final int PAD = 8;
	static final int ROW = 22;
	/** Text line pitch (UI.md compact rows: 10 px). */
	static final int LINE = 10;
	static final int WIDTH = Scene.W;

	private JsonObject state;
	protected int panelWidth = Scene.W;
	protected int panelHeight = Scene.H;
	protected int left;
	protected int top;
	protected int ticks;
	/** Client tick when the state arrived (timers count down from it). */
	protected int stateTick;
	/** Local time the screen opened (entrance motion). */
	protected final long openedAt = Util.getMillis();

	protected PvpScreen(Component title, JsonObject state) {
		super(title, Scene.W, Scene.H);
		this.state = state;
	}

	JsonObject state() {
		return state;
	}

	String matchId() {
		return str(state, "id", "");
	}

	void acceptState(JsonObject s) {
		JsonObject old = state;
		state = s;
		stateTick = ticks;
		onState(old, s);
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	protected void onState(JsonObject oldState, JsonObject newState) {}

	protected void send(String action, String arg, long value) {
		PvpScreens.action(action, matchId(), arg, value);
	}

	protected Scene scene() {
		return bool(state, "grudge") ? Scene.PVP_GRUDGE : Scene.PVP;
	}

	@Override
	protected void init() {
		super.init();
		left = Scene.left(width);
		top = Scene.top(height);
		layout();
	}

	@Override
	protected boolean showBanner() {
		return false;
	}

	@Override
	protected boolean showBalance() {
		return false;
	}

	@Override
	protected int errorY() {
		return top + 188;
	}

	/** Adds widgets (panel-local positions through {@link #button}). */
	protected abstract void layout();

	/** Content (absolute coordinates) over the scene, under the widgets. */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

	protected KitButton button(int x, int y, int w, Component label, KitButton.Style style, Consumer<KitButton> onPress) {
		KitButton b = KitButton.of(left + x, top + y, w, label, style, onPress);
		addRenderableWidget(b);
		return b;
	}

	/** Width for a label: {@code max(min, text + 12)}. */
	protected int labelWidth(Component label, int min) {
		return Math.max(min, font.width(label) + 12);
	}

	@Override
	public void tick() {
		super.tick();
		ticks++;
	}

	/** The title drawn on the scene's banner ({@code null}: the top-left title line instead). */
	protected @Nullable Component bannerTitle() {
		return null;
	}

	@Override
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Scene sc = scene();
		sc.backdrop(g, left, top);
		sc.frame(g, font, left, top, bannerTitle());
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (bannerTitle() == null && titleLine()) {
			Component right = titleRight();
			int rw = right == null ? 0 : font.width(right);
			Kit.fit(g, font, getTitle(), left + 16, top + 15, Scene.W - 32 - (rw > 0 ? rw + 12 : 0), GOLD, true);
			if (right != null) g.text(font, right, left + Scene.W - 16 - rw, top + 15, GOLD, true);
		}
		extractContent(g, mouseX, mouseY, a);
	}

	/** Draw the title line at the top left (the result window's banner replaces it). */
	protected boolean titleLine() {
		return true;
	}

	/** Right side of the title line (timer / step), or null. */
	protected @Nullable Component titleRight() {
		return null;
	}

	/** Y below the title line. */
	protected int contentTop() {
		return top + 28;
	}

	// ---- text helpers ----------------------------------------------------------------------------

	static int wrap(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			g.text(font, line, x, y, color, true);
			y += LINE;
		}
		return y;
	}

	int wrappedHeight(Component text, int width) {
		return font.split(text, width).size() * LINE;
	}

	// ---- JSON helpers --------------------------------------------------------------------------

	static String str(JsonObject o, String k, String d) {
		JsonElement e = o == null ? null : o.get(k);
		return e == null || !e.isJsonPrimitive() ? d : e.getAsString();
	}

	static long num(JsonObject o, String k, long d) {
		JsonElement e = o == null ? null : o.get(k);
		try {
			return e == null || !e.isJsonPrimitive() ? d : e.getAsLong();
		} catch (NumberFormatException ex) {
			return d;
		}
	}

	static boolean bool(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}

	static List<JsonObject> participants(JsonObject s) {
		List<JsonObject> out = new ArrayList<>();
		JsonArray a = s.getAsJsonArray("participants");
		if (a != null) {
			for (JsonElement e : a) {
				if (e.isJsonObject()) {
					out.add(e.getAsJsonObject());
				}
			}
		}
		return out;
	}

	static @Nullable JsonObject participant(JsonObject s, long index) {
		for (JsonObject p : participants(s)) {
			if (num(p, "index", -1) == index) {
				return p;
			}
		}
		return null;
	}

	static long[] longs(JsonObject o, String k) {
		JsonArray a = o == null ? null : o.getAsJsonArray(k);
		if (a == null) {
			return new long[0];
		}
		long[] out = new long[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsLong();
		}
		return out;
	}

	static int[] ints(JsonObject o, String k) {
		long[] l = longs(o, k);
		int[] out = new int[l.length];
		for (int i = 0; i < l.length; i++) {
			out[i] = (int) l[i];
		}
		return out;
	}

	static Component game(JsonObject s) {
		return Component.translatable("gui.burmaldaholic.pvp.game." + str(s, "mode", "coin"));
	}

	/** Player name, "You" for yourself, bots as "[BOT] Name [Style]" (BOTS.md §4.3). */
	static Component name(JsonObject p) {
		if (bool(p, "you")) {
			return Component.translatable("gui.burmaldaholic.common.you");
		}
		return plainName(p);
	}

	static Component plainName(JsonObject p) {
		if (bool(p, "bot")) {
			Component n = Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(str(p, "name", "")));
			String tag = str(p, "tagKey", "");
			return tag.isEmpty() ? n : Component.translatable("gui.burmaldaholic.pvp.bots.tagged", n, Component.translatable(tag));
		}
		return Texts.raw(str(p, "name", "?"));
	}

	static Component percent(JsonObject s) {
		return Component.translatable("gui.burmaldaholic.pvp.percent", Texts.decimal(str(s, "rakePercent", "3")));
	}
}
