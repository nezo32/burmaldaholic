package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/**
 * Stratum-2 panels of the slot screen (slots.md §4.14, SLOTS.md §10.5): the scrollable PAYTABLE (slides in from the
 * right, 200 ms outCubic; pays at the current bet, feature rules, the pick-me fairness note, RTP and max win), the
 * BUY confirm panel (scale-in 150 ms) and the AUTOPLAY panel (spin count, loss limit, stop on a feature). Drawn and
 * hit-tested here so the reel stage below keeps running.
 */
public final class Overlays {
	public enum Mode {
		NONE,
		PAYTABLE,
		BUY,
		AUTO
	}

	public enum Action {
		NONE,
		CLOSE,
		BUY_CONFIRM,
		AUTO_START,
		CONSUMED
	}

	private Mode mode = Mode.NONE;
	private long openedAt;
	private int scroll;
	private int maxScroll;
	/** Autoplay choices. */
	public int autoCount = -1;
	public int lossLimit = -1;
	public boolean stopOnFeature = true;
	private final List<int[]> hits = new ArrayList<>();

	public Mode mode() {
		return mode;
	}

	public void open(Mode m, long now) {
		mode = m;
		openedAt = now;
		scroll = 0;
	}

	public void close() {
		mode = Mode.NONE;
	}

	public void scroll(double dy) {
		if (mode == Mode.PAYTABLE) scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(dy) * 12));
	}

	public void draw(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model, int mouseX, int mouseY) {
		hits.clear();
		if (mode == Mode.NONE) return;
		long ms = s.now() - openedAt;
		g.fill(l.left, l.top, l.left + l.width, l.top + l.height, 0x90000000);
		switch (mode) {
			case PAYTABLE -> paytable(g, l, s, model, ms);
			case BUY -> buy(g, l, s, model, ms, mouseX, mouseY);
			case AUTO -> auto(g, l, s, model, ms, mouseX, mouseY);
			default -> {
			}
		}
	}

	// ---- paytable --------------------------------------------------------------------------------------------------

	private void paytable(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model, long ms) {
		Font font = s.font();
		double u = s.reduceMotion() ? 1 : Ease.OUT_CUBIC.apply(Math.min(1, ms / 200.0));
		int w = l.width - 40;
		int x = (int) (l.left + 20 + (1 - u) * (l.width - 20));
		int y = l.top + 14;
		int h = l.height - 28;
		SlotDraw.panel(g, x, y, w, h, 0xF0180A28, 0xFF783CBE, 0x60D696FF);
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.paytable.title"), x + w / 2, y + 5, w - 30, 0xFFFFD640);
		closeBox(g, x + w - 14, y + 3);
		int top = y + 18;
		int bottom = y + h - 6;
		int tx = x + 8;
		int tw = w - 16;
		g.enableScissor(x + 2, top, x + w - 2, bottom);
		int cy = top - scroll;
		MachineDef def = model.def;
		Machine m = def.machine();
		cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.paytable.per_way"), tx, cy, tw, 0xFFF4ECF8) + 4;
		for (int sym = 0; sym < def.roles().length; sym++) {
			if (def.roles()[sym] != SymbolRole.PAY) continue;
			int[] p = def.paysFifths()[sym];
			Component row = Component.translatable("gui.burmaldaholic.slots.paytable.row", name(m, sym), chips(p[0], model), chips(p[1], model), chips(p[2], model));
			SlotSprites.sheet(g, SlotSprites.texture(m.id + "_symbols_16.png"), 32, 176, 0, sym * 16, 16, 16, tx, cy - 3, 16, 16, 0xFFFFFFFF);
			cy = para(g, font, row, tx + 20, cy + 1, tw - 20, 0xFFFFFFFF) + 6;
		}
		int wild = index(def, SymbolRole.WILD);
		int scatter = index(def, SymbolRole.SCATTER);
		int bonus = def.machine() == Machine.NETHER ? index(def, SymbolRole.COIN) : index(def, SymbolRole.BONUS);
		cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.paytable.wild", name(m, wild), name(m, scatter), name(m, bonus)), tx,
			cy, tw, 0xFFF4ECF8) + 4;
		int[] sp = def.scatterFifths();
		if (sp[0] > 0) {
			cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.paytable.scatter", name(m, scatter), chips(sp[0], model), chips(sp[1], model),
				chips(sp[2], model)), tx, cy, tw, 0xFFFFFFFF) + 4;
		}
		int[] fs = def.freeSpins();
		cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.fs", name(m, scatter), Texts.number(fs[0]), Texts.number(fs[1]), Texts.number(fs[2]),
			Texts.number(def.retrigger())), tx, cy, tw, 0xFFF4ECF8) + 4;
		switch (m) {
			case OVERWORLD -> {
				cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.fs_mult", Texts.number(def.fsMultiplier())), tx, cy, tw, 0xFFF4ECF8) + 4;
				cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.pick", name(m, bonus)), tx, cy, tw, 0xFFF4ECF8) + 4;
				cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.pick_fair"), tx, cy, tw, 0xFFC0B0DC) + 4;
			}
			case NETHER -> {
				cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.tumble", ladder(def.ladder()), ladder(def.ladderFree())), tx, cy, tw, 0xFFF4ECF8) + 4;
				cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.hold", name(m, bonus)), tx, cy, tw, 0xFFF4ECF8) + 4;
			}
			case END -> {
				cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.sticky", name(m, wild)), tx, cy, tw, 0xFFF4ECF8) + 4;
				cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.wheel", name(m, bonus)), tx, cy, tw, 0xFFF4ECF8) + 4;
			}
		}
		cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.help.anticipation"), tx, cy, tw, 0xFFC0B0DC) + 4;
		if (model.rtpBasisPoints > 0) cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.paytable.rtp", percent(model.rtpBasisPoints)), tx, cy, tw, 0xFFF4ECF8) + 2;
		cy = para(g, font, Component.translatable("gui.burmaldaholic.slots.paytable.max_win", Texts.number(def.capMultiple())), tx, cy, tw, 0xFFF4ECF8) + 2;
		g.disableScissor();
		maxScroll = Math.max(0, cy + scroll - bottom);
		hits.add(new int[] {x + w - 14, y + 3, 11, 11, Action.CLOSE.ordinal()});
	}

	private static MutableComponent ladder(int[] ladder) {
		MutableComponent c = Component.empty();
		for (int i = 0; i < ladder.length; i++) {
			if (i > 0) c.append(Texts.raw(" "));
			c.append(Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(ladder[i])));
		}
		return c;
	}

	private static MutableComponent percent(int bp) {
		return Texts.decimal(String.format(Locale.ROOT, "%d.%02d", bp / 100, bp % 100));
	}

	private static Component name(Machine m, int sym) {
		return Component.translatable(SymbolStyle.nameKey(m, sym));
	}

	private static MutableComponent chips(int fifths, SlotModel model) {
		return Texts.chips((long) fifths * model.bet() / 5);
	}

	private static int index(MachineDef def, SymbolRole role) {
		for (int i = 0; i < def.roles().length; i++) if (def.roles()[i] == role) return i;
		return 0;
	}

	private static int para(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int w, int color) {
		for (FormattedCharSequence line : font.split(text, w)) {
			g.text(font, line, x, y, color, true);
			y += 10;
		}
		return y;
	}

	private static void closeBox(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 11, y + 11, 0xFF3A1450);
		SlotDraw.line(g, x + 3, y + 3, x + 8, y + 8, 1, 0xFFFFFFFF);
		SlotDraw.line(g, x + 8, y + 3, x + 3, y + 8, 1, 0xFFFFFFFF);
	}

	// ---- buy ---------------------------------------------------------------------------------------------------------

	private void buy(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model, long ms, int mx, int my) {
		Font font = s.font();
		double sc = s.reduceMotion() ? 1 : 0.9 + 0.1 * Ease.OUT_BACK.apply(Math.min(1, ms / 150.0));
		int w = Math.min(l.width - 40, 260);
		int h = 110;
		int x = l.left + (l.width - w) / 2;
		int y = l.top + (l.height - h) / 2;
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y + h / 2f);
		g.pose().scale((float) sc, (float) sc);
		g.pose().translate(-(x + w / 2f), -(y + h / 2f));
		SlotDraw.panel(g, x, y, w, h, 0xF0180A28, 0xFFFFD640, 0x60FFE680);
		Component feature = Component.translatable("gui.burmaldaholic.slots.fs.name." + model.def.machine().id);
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.buy.confirm_title", feature), x + w / 2, y + 6, w - 12, 0xFFFFD640);
		Component body = Component.translatable("gui.burmaldaholic.slots.buy.confirm_body", Texts.chips(model.buyPrice()), feature,
			Texts.number(model.def.freeSpins()[0]), percent(model.buyRtpBasisPoints > 0 ? model.buyRtpBasisPoints : model.rtpBasisPoints));
		para(g, font, body, x + 8, y + 22, w - 16, 0xFFF4ECF8);
		button(g, font, Component.translatable("gui.burmaldaholic.slots.buy.confirm"), x + 12, y + h - 26, w / 2 - 18, 20, true, mx, my, Action.BUY_CONFIRM);
		button(g, font, Component.translatable("gui.burmaldaholic.common.back"), x + w / 2 + 6, y + h - 26, w / 2 - 18, 20, false, mx, my, Action.CLOSE);
		g.pose().popMatrix();
	}

	// ---- autoplay ----------------------------------------------------------------------------------------------------

	private void auto(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model, long ms, int mx, int my) {
		Font font = s.font();
		int w = Math.min(l.width - 40, 280);
		int h = 140;
		int x = l.left + (l.width - w) / 2;
		int y = l.top + (l.height - h) / 2;
		SlotDraw.panel(g, x, y, w, h, 0xF0180A28, 0xFF783CBE, 0x60D696FF);
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.auto.title"), x + w / 2, y + 6, w - 12, 0xFFFFD640);
		int ry = y + 22;
		SlotDraw.textFit(g, font, Component.translatable("gui.burmaldaholic.slots.auto.count"), x + 8, ry, w - 16, 0xFFF4ECF8);
		int cx = x + 8;
		ry += 11;
		for (int i = 0; i < model.autoCounts.length; i++) {
			Component c = Texts.number(model.autoCounts[i]);
			int bw = Math.max(26, font.width(c) + 10);
			chip(g, font, c, cx, ry, bw, autoCount == i, mx, my, 100 + i);
			cx += bw + 4;
		}
		ry += 20;
		SlotDraw.textFit(g, font, Component.translatable("gui.burmaldaholic.slots.auto.loss_limit"), x + 8, ry, w - 16, 0xFFF4ECF8);
		ry += 11;
		cx = x + 8;
		for (int i = 0; i < model.lossLimits.length; i++) {
			Component c = Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(model.lossLimits[i]));
			int bw = Math.max(26, font.width(c) + 10);
			chip(g, font, c, cx, ry, bw, lossLimit == i, mx, my, 200 + i);
			cx += bw + 4;
		}
		ry += 22;
		Component feat = Component.translatable("gui.burmaldaholic.slots.auto.stop_feature");
		chip(g, font, feat, x + 8, ry, Math.min(w - 16, font.width(feat) + 20), stopOnFeature, mx, my, 300);
		button(g, font, Component.translatable("gui.burmaldaholic.slots.auto.start"), x + 12, y + h - 24, w / 2 - 18, 18, true, mx, my, Action.AUTO_START);
		button(g, font, Component.translatable("gui.burmaldaholic.common.back"), x + w / 2 + 6, y + h - 24, w / 2 - 18, 18, false, mx, my, Action.CLOSE);
	}

	private void chip(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int w, boolean on, int mx, int my, int id) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
		SlotDraw.plate(g, x, y, w, 16, on ? 0xFF8A3AAA : 0xFF2A1A3A, on ? 0xFF4A1A6A : 0xFF180A28, on ? 0xFFFFD640 : hover ? 0xFFD696FF : 0xFF5A3A70);
		SlotDraw.centeredFit(g, font, text, x + w / 2, y + 4, w - 4, 0xFFFFFFFF);
		hits.add(new int[] {x, y, w, 16, id});
	}

	private void button(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int w, int h, boolean primary, int mx, int my, Action action) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
		int top = primary ? 0xFFFFC400 : 0xFF3A2A4A;
		int bottom = primary ? 0xFFB07010 : 0xFF1E1428;
		SlotDraw.plate(g, x, y - (hover ? 1 : 0), w, h, top, bottom, hover ? 0xFFFFFFFF : primary ? 0xFFFFF0A0 : 0xFF8C7AA0);
		SlotDraw.centeredFit(g, font, text, x + w / 2, y + (h - 8) / 2 - (hover ? 1 : 0), w - 6, primary ? 0xFF180A28 : 0xFFFFFFFF);
		hits.add(new int[] {x, y, w, h, action.ordinal()});
	}

	/** Mouse click on the overlay; returns what the screen should do. */
	public Action click(double mx, double my, SlotModel model) {
		if (mode == Mode.NONE) return Action.NONE;
		for (int[] h : hits) {
			if (mx < h[0] || mx >= h[0] + h[2] || my < h[1] || my >= h[1] + h[3]) continue;
			int id = h[4];
			if (id >= 300) {
				stopOnFeature = !stopOnFeature;
				return Action.CONSUMED;
			}
			if (id >= 200) {
				lossLimit = id - 200;
				return Action.CONSUMED;
			}
			if (id >= 100) {
				autoCount = id - 100;
				return Action.CONSUMED;
			}
			return Action.values()[id];
		}
		return Action.CONSUMED; // clicks never fall through a modal panel
	}
}
