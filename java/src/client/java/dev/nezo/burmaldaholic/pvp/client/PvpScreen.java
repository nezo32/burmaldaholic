package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Base of the pvp module's screens (lobby, generic match, result, taunts): a server-driven felt panel
 * (256 wide, PVP.md §3.11.2/§3.11.3) that grows downwards to fit wrapped text and flowed buttons, so Russian
 * labels (≈1.45× English) wrap instead of being cut (UI.md §0.1: widths = max(min, text + 10)).
 */
abstract class PvpScreen extends Screen {
	static final int FELT = 0xFF1B3A5C;
	static final int FELT_BORDER = 0xFF0B1A2C;
	static final int TEXT = 0xFFFFFFFF;
	static final int MUTED = 0xFFBBBBBB;
	static final int GOLD = 0xFFFFD700;
	static final int GREEN = 0xFF55FF55;
	static final int RED = 0xFFFF5555;
	static final int PAD = 8;
	static final int ROW = 22;
	/** Text line pitch (UI.md compact rows: 10 px). */
	static final int LINE = 10;
	static final int WIDTH = 256;

	private JsonObject state;
	private @Nullable Component error;
	private int errorTicks;
	protected int panelWidth = WIDTH;
	protected int panelHeight = 120;
	protected int left;
	protected int top;
	protected int ticks;
	/** Client tick when the state arrived (timers count down from it). */
	protected int stateTick;

	protected PvpScreen(Component title, JsonObject state) {
		super(title);
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

	void showError(Component message) {
		error = message;
		errorTicks = 80;
	}

	protected void send(String action, String arg, long value) {
		PvpScreens.action(action, matchId(), arg, value);
	}

	@Override
	protected void init() {
		panelWidth = Math.min(WIDTH, width - 8);
		left = (width - panelWidth) / 2;
		top = Math.max(4, (height - panelHeight) / 2);
		layout();
	}

	/** Adds widgets; call {@link #fitHeight} with the lowest used y. */
	protected abstract void layout();

	/** Content below the title bar (absolute coordinates). */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

	protected void fitHeight(int contentBottom) {
		int needed = Math.min(Math.max(60, contentBottom - top + 16), Math.max(60, height - 8));
		if (needed != panelHeight) {
			panelHeight = needed;
			top = Math.max(4, (height - panelHeight) / 2);
			clearWidgets();
			layout();
		}
	}

	@Override
	public void tick() {
		ticks++;
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, FELT_BORDER);
		g.fill(left, top, left + panelWidth, top + panelHeight, FELT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Component right = titleRight();
		int rw = right == null ? 0 : font.width(right);
		List<FormattedCharSequence> title = font.split(getTitle(), panelWidth - 2 * PAD - (rw > 0 ? rw + PAD : 0));
		int y = top + 6;
		for (FormattedCharSequence line : title) {
			g.text(font, line, left + PAD, y, GOLD, true);
			y += font.lineHeight;
		}
		if (right != null) {
			g.text(font, right, left + panelWidth - PAD - rw, top + 6, TEXT, true);
		}
		extractContent(g, mouseX, mouseY, a);
		super.extractRenderState(g, mouseX, mouseY, a);
		if (error != null) {
			wrap(g, font, error, left + PAD, top + panelHeight - 12, panelWidth - 2 * PAD, RED);
		}
	}

	/** Right side of the title bar (timer / balance), or null. */
	protected @Nullable Component titleRight() {
		return null;
	}

	/** Y below the (possibly wrapped) title. */
	protected int contentTop() {
		Component right = titleRight();
		int rw = right == null ? 0 : font.width(right) + PAD;
		return top + 6 + font.split(getTitle(), panelWidth - 2 * PAD - rw).size() * font.lineHeight + 4;
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

	void separator(GuiGraphicsExtractor g, int y) {
		g.fill(left + PAD, y, left + panelWidth - PAD, y + 1, 0x66FFFFFF);
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

	// ---- flow layout -----------------------------------------------------------------------------

	/** Buttons as wide as their label needs; rows wrap. */
	final class Flow {
		private final int x0;
		private final int x1;
		private int x;
		private int y;

		Flow(int y) {
			this.x0 = left + PAD;
			this.x1 = left + panelWidth - PAD;
			this.x = x0;
			this.y = y;
		}

		Button button(Component label, int minWidth, Button.OnPress onPress) {
			int w = Math.min(x1 - x0, Math.max(minWidth, font.width(label) + 10));
			if (x + w > x1 && x > x0) {
				newRow();
			}
			Button b = Button.builder(label, onPress).bounds(x, y, w, 20).build();
			addRenderableWidget(b);
			x += w + 3;
			return b;
		}

		void newRow() {
			if (x > x0) {
				x = x0;
				y += ROW;
			}
		}

		int bottom() {
			return x > x0 ? y + ROW : y;
		}
	}
}
