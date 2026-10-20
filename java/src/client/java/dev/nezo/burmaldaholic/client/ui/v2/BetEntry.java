package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetAmount;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The bet-entry kit control of every betting Dock (lane HR; owner playtest E4: "500 is the max chip; pressing 200 times
 * for 100k is hell"). Opened by a click on the bet readout or the {@code E} key; a strip above the Dock with
 * {@code [½] [ amount ] [×2] [Max] [OK]}. Digits / {@code k} / {@code m} type (keys, through {@link V2Input} — Minecraft
 * 26.3 renumbered key codes), Backspace deletes, Enter = OK, Esc closes, the mouse wheel over the field (or the readout)
 * steps by a chip. Every value is clamped to {@code min(table max, VIP max, balance)} ({@link BetAmount}); a clamp says
 * whose cap it was in the status line.
 *
 * <p>OK sets the bet ({@link BetPort#amount}) in single-spot games; in multi-spot games (or a port that cannot set an
 * amount) the value becomes the unit a felt click places ({@link BetBar#unit}). Pure maths in {@link BetAmount}; this
 * class draws, lays out and keeps the typed text.
 */
public final class BetEntry {
	public static final String OPEN = "entry.open", HALF = "entry.half", FIELD = "entry.field", DOUBLE = "entry.double", MAX = "entry.max",
		OK = "entry.ok";
	/** Translation keys (STRINGS.md core › lane HR). */
	public static final String K = "gui.burmaldaholic.core.v2.entry.";
	public static final String KEY_OPEN = K + "open", KEY_HINT = K + "hint", KEY_OK = K + "ok", KEY_HALF = K + "half", KEY_DOUBLE = K + "double",
		KEY_UNIT = K + "unit", KEY_COST = K + "cost", KEY_MAX = "gui.burmaldaholic.common.max";

	private boolean open;
	private String text = "";
	private BetAmount.Mode mode = BetAmount.Mode.BET;
	private BetAmount.Cost cost = BetAmount.ONE;
	private Box panel = Box.EMPTY, half = Box.EMPTY, field = Box.EMPTY, dbl = Box.EMPTY, max = Box.EMPTY, ok = Box.EMPTY;
	private String pressedId = "";
	private long pressedAt = -1;
	private long openedAt;

	public boolean isOpen() {
		return open;
	}

	/** The typed text ("" = empty). */
	public String text() {
		return text;
	}

	/** The amount in the field now (0 = empty). */
	public long value() {
		return BetAmount.parse(text);
	}

	public BetAmount.Mode mode() {
		return mode;
	}

	/** What the field's amount costs on the table ({@link BetPort#entryCost}). */
	public BetAmount.Cost cost() {
		return cost;
	}

	/** Opens with {@code initial} in the field (typing replaces it until the first edit). */
	void open(BetAmount.Mode mode, BetAmount.Cost cost, long initial) {
		this.open = true;
		this.mode = mode;
		this.cost = cost;
		this.text = initial > 0 ? Long.toString(initial) : "";
		this.fresh = true;
		this.openedAt = Util.getMillis();
	}

	/** True right after opening: the first typed digit replaces the shown amount. */
	private boolean fresh;

	public void close() {
		open = false;
		panel = half = field = dbl = max = ok = Box.EMPTY;
	}

	void set(long value) {
		text = value > 0 ? Long.toString(value) : "";
		fresh = false;
	}

	/** A typed character ({@link BetAmount#type}). */
	void type(char c) {
		if (fresh) {
			text = "";
			fresh = false;
		}
		text = BetAmount.type(text, c);
	}

	void backspace() {
		fresh = false;
		text = BetAmount.backspace(text);
	}

	/** Lays the strip out above the Dock, starting at {@code anchorX}, kept inside the PlayArea. */
	void layout(TableGrid grid, Font font, int anchorX) {
		if (!open) return;
		int h = grid.tier.buttonH;
		int sq = Math.max(h, font.width(Component.translatable(KEY_HALF)) + 8);
		int fw = Math.max(font.width(Texts.number(888_888_888L)), font.width(Texts.number(Math.max(1, value())))) + 12;
		int mw = V2Button.width(font, Component.translatable(KEY_MAX), null);
		int dw = Math.max(h, font.width(Component.translatable(KEY_DOUBLE)) + 8);
		int ow = V2Button.width(font, Component.translatable(KEY_OK), null);
		int gap = 2, pad = 3;
		int w = pad * 2 + sq + fw + dw + mw + ow + gap * 4;
		Box play = grid.play;
		int x = Math.max(play.x() + 2, Math.min(anchorX - pad, play.right() - 2 - w));
		int y = grid.dock.y() - h - pad * 2 - 2;
		panel = new Box(x, y, Math.min(w, play.w() - 4), h + pad * 2);
		int cx = x + pad;
		half = new Box(cx, y + pad, sq, h);
		cx = half.right() + gap;
		field = new Box(cx, y + pad, fw, h);
		cx = field.right() + gap;
		dbl = new Box(cx, y + pad, dw, h);
		cx = dbl.right() + gap;
		max = new Box(cx, y + pad, mw, h);
		cx = max.right() + gap;
		ok = new Box(cx, y + pad, ow, h);
	}

	/** Registers the strip's targets (call LAST: they sit on top of the felt). */
	void hits(HitMap hits) {
		if (!open || panel.isEmpty()) return;
		hits.add(HALF, half, true);
		hits.add(FIELD, field, true);
		hits.add(DOUBLE, dbl, true);
		hits.add(MAX, max, true);
		hits.add(OK, ok, true);
	}

	public Box panel() {
		return panel;
	}

	void pressed(String id) {
		pressedId = id;
		pressedAt = Util.getMillis();
	}

	/** Whether {@code id} is one of the strip's targets. */
	public static boolean owns(String id) {
		return id.startsWith("entry.");
	}

	public void draw(GuiGraphicsExtractor g, Font font, int mx, int my, @Nullable BetView view) {
		if (!open || panel.isEmpty()) return;
		g.nextStratum();
		V2Draw.pill(g, panel, CasinoPalette.V2_INK, CasinoPalette.V2_GOLD_SHADE);
		long now = Util.getMillis();
		button(g, font, HALF, half, Component.translatable(KEY_HALF), mx, my, now, V2Sprites.ButtonStyle.NORMAL);
		button(g, font, DOUBLE, dbl, Component.translatable(KEY_DOUBLE), mx, my, now, V2Sprites.ButtonStyle.NORMAL);
		button(g, font, MAX, max, Component.translatable(KEY_MAX), mx, my, now, V2Sprites.ButtonStyle.NORMAL);
		button(g, font, OK, ok, Component.translatable(KEY_OK), mx, my, now, V2Sprites.ButtonStyle.PRIMARY);
		V2Draw.pill(g, field, CasinoPalette.V2_PANEL_HI, CasinoPalette.V2_GOLD);
		long v = value();
		Component shown = text.isEmpty() ? Component.translatable(V2Keys.BET) : Texts.number(v);
		int color = text.isEmpty() ? CasinoPalette.V2_MUTED : fresh ? V2Draw.alpha(CasinoPalette.V2_GOLD, 0.75f) : CasinoPalette.V2_GOLD;
		int tw = font.width(shown);
		int tx = field.right() - 6 - tw;
		V2Draw.text(g, font, shown, tx, field.y() + (field.h() - 8) / 2, 1, color, true);
		if (!fresh && (now - openedAt) / 500 % 2 == 0) g.fill(field.right() - 5, field.y() + 3, field.right() - 4, field.bottom() - 3, CasinoPalette.V2_GOLD);
	}

	private void button(GuiGraphicsExtractor g, Font font, String id, Box b, Component label, int mx, int my, long now, V2Sprites.ButtonStyle style) {
		V2Button.draw(g, font, b, label, null, style, true, b.contains(mx, my), id.equals(pressedId) && now - pressedAt < 120, false);
	}

	/** The keys the open strip takes: digits, k / m, Backspace / Delete, Enter, Esc (everything else is swallowed too). */
	public static boolean typingKey(@Nullable String name) {
		return name != null && (name.length() == 1 || name.equals(KeyMap.BACKSPACE) || name.equals(KeyMap.DELETE) || name.equals(KeyMap.ENTER)
			|| name.equals(KeyMap.ESCAPE));
	}
}
