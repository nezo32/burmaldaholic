package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.features.TumbleView;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.HuntBoard;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotGeometry.Rect;
import dev.nezo.burmaldaholic.games.slots.v2.present.WinHistory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The two side panels (slots.md §4.5–§4.9, §4.12, SLOTS.md §10.5; lane J-L9b). Left, the FEATURE panel: in the base
 * game the ways count, the Nether multiplier ladder and the feature guide (symbol + count + what it starts); in free
 * spins the counter plate with its digit flip, the bonus-win plate and the machine's extra (×2 plate / ladder / sticky
 * pips); in the Treasure Hunt the opened-chests counter (with "Opening…" while a pick waits for the server) and the
 * bonus total; in Piglin's Hoard the respin pips, coins and total. Right, the WIN panel: the rolling WIN amount in a
 * value plate in tier colours (Returned in grey, F9), the last settled wins and a mini paytable preview (the top
 * three symbols' 5-of-a-kind pays at the current bet; click opens the paytable). The label line under the reels and
 * the balance line. Every text fits its box in English and Russian (1.45 × budget): labels over plates, values
 * inside plates.
 */
public final class SidePanels {
	private static final int INK = 0xFF180A28;
	private static final int HEAD = 0xFFD6C6F0;
	private static final int GOLD = 0xFFFFD640;
	private static final int BONE = 0xFFF4ECF8;
	private static final int GREY = 0xFFC0B0DC;

	private SidePanels() {}

	private static int innerX(SlotLayout l, boolean right) {
		return (right ? l.rightPanelX : l.leftPanelX) + 4;
	}

	private static int innerW(SlotLayout l) {
		return l.panelW - 8;
	}

	private static int innerY(SlotLayout l) {
		return l.panelY + 5;
	}

	private static int plateH(SlotLayout l) {
		return l.compact ? 18 : 22;
	}

	/** Centre of the free-spin counter plate (plates fly here). */
	public static int[] featureCenter(SlotLayout l) {
		return new int[] {innerX(l, false) + innerW(l) / 2, innerY(l) + 19};
	}

	/** Centre of the WIN value plate. */
	public static int[] winCenter(SlotLayout l) {
		return new int[] {innerX(l, true) + innerW(l) / 2, innerY(l) + 11 + plateH(l) / 2};
	}

	/** The mini paytable preview (click → the paytable); null when the panel has no room for it (compact). */
	public static Rect paytableArea(SlotLayout l) {
		if (l.compact) return null;
		return new Rect(innerX(l, true) - 1, innerY(l) + 64, innerW(l) + 2, 60);
	}

	public static void draw(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model) {
		Font font = s.font();
		for (int x : new int[] {l.leftPanelX, l.rightPanelX}) {
			if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.machine(s.machine(), "side_panel"), x, l.panelY, l.panelW, l.panelH);
			else SlotDraw.panel(g, x, l.panelY, l.panelW, l.panelH, 0xC0140822, 0xFF3A1450, 0x40D696FF);
		}
		int bottom = l.panelY + l.panelH - 3;
		g.enableScissor(l.leftPanelX + 2, l.panelY + 2, l.leftPanelX + l.panelW - 2, l.panelY + l.panelH - 2);
		feature(g, l, s, font, innerX(l, false), innerY(l), innerW(l), bottom);
		g.disableScissor();
		g.enableScissor(l.rightPanelX + 2, l.panelY + 2, l.rightPanelX + l.panelW - 2, l.panelY + l.panelH - 2);
		win(g, l, s, model, font, innerX(l, true), innerY(l), innerW(l), bottom);
		g.disableScissor();
	}

	// ---- feature panel ----------------------------------------------------------------------------------------------

	private static void feature(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, Font font, int x, int y, int w, int bottom) {
		int[] fs = s.freeSpins().counter(s);
		if (s.hunt().covers(s)) {
			hunt(g, s, font, x, y, w);
			return;
		}
		if (s.hoard().covers(s)) {
			hoard(g, s, font, x, y, w);
			return;
		}
		if (fs != null) {
			freeSpins(g, l, s, font, fs, x, y, w, bottom);
			return;
		}
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.ways"), x + w / 2, y, w, 0xFFD696FF);
		y += 14;
		if (s.machine() == Machine.NETHER) y = ladder(g, s, font, x, y, w, bottom, l.compact ? 16 : 17) + 2;
		guide(g, s, font, x, y, w, bottom);
	}

	/** Free spins: the counter plate ("3 of 12", digit flip), the bonus win plate, then the machine's extra. */
	private static void freeSpins(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, Font font, int[] fs, int x, int y, int w, int bottom) {
		double punch = s.freeSpins().punch(s);
		double flip = s.reduceMotion() ? 1 : FeatureMotion.digitFlip(fs[2]);
		title(g, font, "gui.burmaldaholic.slots.fs.name." + s.machine().id, "gui.burmaldaholic.slots.panel.fs", x, y, w, 0xFFFF9AE8);
		Component counter = Component.translatable("gui.burmaldaholic.slots.panel.fs_of", Texts.number(fs[0] + 1), Texts.number(fs[1]));
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y + 19);
		g.pose().scale((float) punch, (float) punch);
		SlotDraw.plate(g, -w / 2, -8, w, 16, 0xFF6A2A8A, 0xFF2A0A44, GOLD);
		g.pose().popMatrix();
		g.enableScissor(x, y + 12, x + w, y + 26);
		int dy = (int) Math.round((1 - flip) * 10);
		SlotDraw.centeredFit(g, font, counter, x + w / 2, y + 15 + dy, w - 4, 0xFFFFFFFF);
		g.disableScissor();
		int ny = valueBlock(g, font, Component.translatable("gui.burmaldaholic.slots.panel.bonus_win"), Texts.number(s.freeSpins().featureTotal(s)), x, y + 31, w,
			GOLD, 16);
		switch (s.machine()) {
			case NETHER -> ladder(g, s, font, x, ny, w, bottom, 16);
			case END -> sticky(g, s, font, x, ny, w);
			case OVERWORLD -> {
				boolean glow = s.winShow().current(s) != null;
				SlotDraw.plate(g, x + w / 2 - 14, ny, 28, 14, glow ? 0xFFFFD640 : 0xFF6A5A20, glow ? 0xFFB07010 : 0xFF3A2A10, 0xFFFFE680);
				SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(s.def().fsMultiplier())), x + w / 2, ny + 3, 26,
					INK);
			}
		}
	}

	/** Treasure Hunt: opened chests (+ "Opening…" while the pressed chest waits for the server, D6) and the bonus total. */
	private static void hunt(GuiGraphicsExtractor g, SlotStage s, Font font, int x, int y, int w) {
		HuntBoard b = s.hunt().board();
		title(g, font, "gui.burmaldaholic.slots.bonus.pick", "gui.burmaldaholic.slots.panel.hunt", x, y, w, GOLD);
		int ny = valueBlock(g, font, Component.translatable("gui.burmaldaholic.slots.panel.opened"), Texts.number(b.opened()), x, y + 12, w, BONE, 16);
		if (b.pending() >= 0 && !b.ended()) {
			double p = s.reduceMotion() ? 1 : 0.6 + 0.4 * Math.sin(s.now() / 150.0);
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.panel.opening"), x + w / 2, ny - 1, w, SlotDraw.withAlpha(0xFFFFE680, p));
		}
		valueBlock(g, font, Component.translatable("gui.burmaldaholic.slots.panel.bonus_win"), Texts.number(b.totalTimesBet() * s.bet()), x, ny + 12, w, GOLD, 16);
	}

	/** Piglin's Hoard: respin pips, coins, the collected total. */
	private static void hoard(GuiGraphicsExtractor g, SlotStage s, Font font, int x, int y, int w) {
		title(g, font, "gui.burmaldaholic.slots.bonus.hold", "gui.burmaldaholic.slots.panel.hoard", x, y, w, 0xFFFFC400);
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.panel.respins"), x + w / 2, y + 12, w, HEAD);
		int left = s.hoard().respinsLeft(s);
		for (int i = 0; i < 3; i++) {
			int px = x + w / 2 - 16 + i * 12;
			if (CabinetArt.ART) SlotSprites.blit(g, i < left ? SlotSprites.PIP_ON : SlotSprites.PIP_OFF, px, y + 22, 8, 8);
			else SlotDraw.disc(g, px + 4, y + 26, 4, i < left ? GOLD : 0xFF3A2A40);
		}
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.hold.coins", Texts.number(s.hoard().coins(s))), x + w / 2, y + 33, w, BONE);
		valueBlock(g, font, Component.translatable("gui.burmaldaholic.slots.panel.bonus_win"), Texts.number(s.hoard().collected(s)), x, y + 45, w, GOLD, 16);
	}

	/** A panel title: the full name, or its short form when the full one would shrink below ¾ (compact, Russian). */
	private static void title(GuiGraphicsExtractor g, Font font, String key, String shortKey, int x, int y, int w, int color) {
		Component full = Component.translatable(key);
		Component text = font.width(full) * 3 > w * 4 ? Component.translatable(shortKey) : full;
		SlotDraw.centeredFit(g, font, text, x + w / 2, y, w, color);
	}

	/** Label over an inset value plate; returns the y under the plate. */
	private static int valueBlock(GuiGraphicsExtractor g, Font font, Component label, Component value, int x, int y, int w, int color, int h) {
		SlotDraw.centeredFit(g, font, label, x + w / 2, y, w, HEAD);
		int py = y + 10;
		if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.VALUE_PLATE, x, py, w, h);
		else SlotDraw.plate(g, x, py, w, h, 0xFF0C0616, 0xFF0C0616, GOLD);
		SlotDraw.centeredFit(g, font, value, x + w / 2, py + (h - 8) / 2 + 1, w - 8, color);
		return py + h + 4;
	}

	/** Nether: the ladder plates; the lit one pops when it steps up. Falls back to the lit plate alone without room. */
	private static int ladder(GuiGraphicsExtractor g, SlotStage s, Font font, int x, int y, int w, int bottom, int step) {
		boolean free = s.tumble().inFreeSpins(s);
		int[] ladder = TumbleView.ladder(s, free);
		int lit = s.tumble().multiplier(s);
		double pop = s.reduceMotion() ? 1 : FeatureMotion.platePop(s.tumble().plateSince(s));
		boolean all = y + ladder.length * step <= bottom + 2;
		int row = 0;
		for (int i = 0; i < ladder.length; i++) {
			boolean on = ladder[i] == lit;
			if (!all && !on) continue;
			int py = y + row++ * step;
			int pw = Math.min(w, 72);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, py + 8);
			if (on) g.pose().scale((float) pop, (float) pop);
			if (CabinetArt.ART && (!on || CabinetArt.ANIMATED_STRIPS)) SlotSprites.blit(g, on ? SlotSprites.LADDER_LIT : SlotSprites.LADDER, -pw / 2, -8, pw, 16);
			else SlotDraw.plate(g, -pw / 2, -8, pw, 16, on ? 0xFFFF7A1A : 0xFF3A2020, on ? 0xFFC02010 : 0xFF1C1014, on ? GOLD : 0xFF5A3A30);
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(ladder[i])), 0, -4, pw - 4,
				on ? 0xFFFFFFFF : 0xFF8A6A60);
			g.pose().popMatrix();
		}
		return y + row * step;
	}

	/** End: "Sticky reels: 2/3" with three pips that fill with the egg colour. */
	private static void sticky(GuiGraphicsExtractor g, SlotStage s, Font font, int x, int y, int w) {
		int mask = s.frames() == null ? 0 : s.frames().stickyMask;
		int ny = SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.sticky", Texts.number(Integer.bitCount(mask))), x, y, w, 0xFFD696FF);
		for (int i = 0; i < 3; i++) {
			int px = x + w / 2 - 16 + i * 12;
			boolean on = (mask & (1 << i)) != 0;
			SlotDraw.disc(g, px + 4, ny + 4, 4, on ? 0xFFB040FF : 0xFF2A1A3A);
			if (on) SlotDraw.disc(g, px + 3, ny + 3, 1, 0xFFFFFFFF);
		}
	}

	/** Feature guide rows (base game): the symbol, how many start it, and what it starts. */
	private static void guide(GuiGraphicsExtractor g, SlotStage s, Font font, int x, int y, int w, int bottom) {
		MachineDef def = s.def();
		Machine m = s.machine();
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] {index(def, SymbolRole.SCATTER), 3, true, "fs"});
		switch (m) {
			case OVERWORLD -> rows.add(new Object[] {index(def, SymbolRole.BONUS), 3, false, "hunt"});
			case NETHER -> rows.add(new Object[] {index(def, SymbolRole.COIN), def.features().holdTrigger(), true, "hoard"});
			case END -> rows.add(new Object[] {index(def, SymbolRole.BONUS), 3, false, "wheel"});
		}
		if (y + 12 + rows.size() * 21 <= bottom) {
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.panel.features"), x + w / 2, y, w, HEAD);
			y += 12;
		}
		for (Object[] r : rows) {
			if (y + 18 > bottom + 2) return;
			int sym = (int) r[0];
			if (sym >= 0) symbol(g, m, sym, x, y + 1);
			Component count = (boolean) r[2] ? Component.translatable("gui.burmaldaholic.slots.panel.count_plus", Texts.number((int) r[1]))
				: Texts.number((int) r[1]);
			SlotDraw.textFit(g, font, count, x + 19, y, w - 19, GOLD);
			SlotDraw.textFit(g, font, Component.translatable("gui.burmaldaholic.slots.panel." + r[3]), x + 19, y + 9, w - 19, BONE);
			y += 21;
		}
	}

	private static void symbol(GuiGraphicsExtractor g, Machine m, int sym, int x, int y) {
		SlotSprites.sheet(g, SlotSprites.texture(m.id + "_symbols_16.png"), 32, 176, 0, sym * 16, 16, 16, x, y, 16, 16, 0xFFFFFFFF);
	}

	private static int index(MachineDef def, SymbolRole role) {
		for (int i = 0; i < def.roles().length; i++) if (def.roles()[i] == role) return i;
		return -1;
	}

	// ---- win panel --------------------------------------------------------------------------------------------------

	private static void win(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model, Font font, int x, int y, int w, int bottom) {
		long amount = s.bigWin().panelAmount(s);
		WinTier tier = s.bigWin().panelTier(s);
		boolean returned = tier == WinTier.RETURN;
		SlotDraw.centeredFit(g, font, Component.translatable(returned ? "gui.burmaldaholic.slots.panel.returned" : "gui.burmaldaholic.slots.panel.win"), x + w / 2,
			y, w, returned ? GREY : HEAD);
		int ph = plateH(l);
		int py = y + 11;
		if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.VALUE_PLATE, x, py, w, ph);
		else SlotDraw.plate(g, x, py, w, ph, 0xFF0C0616, 0xFF0C0616, GOLD);
		Component value = Texts.number(amount);
		int color = returned ? GREY : !tier.isWin() || amount <= 0 ? 0xFF6A5A7A : switch (tier) {
			case MEGA -> 0xFFFF8A1A;
			case EPIC, JACKPOT -> 0xFFFF40C0;
			case WIN -> 0xFFFFFFFF;
			default -> GOLD;
		};
		int sc = l.compact || returned || !tier.isWin() || amount <= 0 ? 1 : SlotDraw.fitScale(font, value, 2, w - 8);
		if (sc > 1) SlotDraw.outlined(g, font, value, x + w / 2f, py + ph / 2f, sc, color, INK);
		else SlotDraw.centeredFit(g, font, value, x + w / 2, py + (ph - 8) / 2 + 1, w - 6, color);
		// last settled wins (newest first)
		int ry = py + ph + 4;
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.panel.recent"), x + w / 2, ry, w, HEAD);
		ry += 10;
		WinHistory h = s.history();
		int lines = l.compact ? 3 : 2;
		if (h.size() == 0) SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.panel.none"), x + w / 2, ry, w, 0xFF6A5A7A);
		for (int i = 0; i < Math.min(lines, h.size()) && ry + 9 <= bottom; i++) {
			WinHistory.Entry e = h.get(i);
			SlotDraw.chip(g, x + 4, ry + 3);
			int c = e.returned() ? GREY : e.amount() >= 5 * e.bet() ? GOLD : BONE;
			SlotDraw.textRight(g, font, Texts.number(e.amount()), x + w, ry, w - 11, c);
			ry += 10;
		}
		Rect pay = paytableArea(l);
		if (pay != null && model.def != null) miniPaytable(g, s, model, font, x, pay.y() + 2, w);
	}

	/** The three best-paying symbols: icon, ×5 and the 5-of-a-kind pay at the current bet. */
	private static void miniPaytable(GuiGraphicsExtractor g, SlotStage s, SlotModel model, Font font, int x, int y, int w) {
		MachineDef def = model.def;
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.common.paytable"), x + w / 2, y, w, HEAD);
		y += 11;
		List<Integer> pays = new ArrayList<>();
		for (int i = 0; i < def.roles().length; i++) if (def.roles()[i] == SymbolRole.PAY) pays.add(i);
		pays.sort((a, b) -> Integer.compare(def.paysFifths()[b][2], def.paysFifths()[a][2]));
		for (int k = 0; k < Math.min(3, pays.size()); k++) {
			int sym = pays.get(k);
			symbol(g, def.machine(), sym, x, y - 4 + k * 15);
			SlotDraw.textFit(g, font, Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(5)), x + 17, y + k * 15, 14, GREY);
			long pay = (long) def.paysFifths()[sym][2] * model.bet() / 5;
			SlotDraw.textRight(g, font, Texts.number(pay), x + w, y + k * 15, w - 32, GOLD);
		}
	}

	// ---- lines ------------------------------------------------------------------------------------------------------

	/** The line under the reels: the current symbol win, a feature hint, or the error line (on a dark backing). */
	public static void label(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model) {
		Font font = s.font();
		Component line = null;
		int color = BONE;
		int cx = l.wx + l.windowW() / 2;
		int maxW = l.windowW() + 2 * l.border;
		if (model.errorKey != null && s.now() - model.errorAt < 3000) {
			line = Component.translatable(model.errorKey);
			color = 0xFFFF6E6A;
			// the error line slides in from the left (global §2.2)
			double u = Math.min(1, (s.now() - model.errorAt) / 150.0);
			g.pose().pushMatrix();
			g.pose().translate((float) ((1 - u) * -20), 0);
			backed(g, font, line, cx, l.labelY, maxW, color);
			g.pose().popMatrix();
			return;
		}
		if (s.active()) line = s.winShow().label(s);
		if (line == null && model.autoLeft >= 0) line = Component.translatable("gui.burmaldaholic.slots.auto_left", Texts.number(model.autoLeft));
		if (line == null && model.autoSummary != null && !s.spinning()) line = model.autoSummary;
		if (line != null) backed(g, font, line, cx, l.labelY, maxW, color);
	}

	private static void backed(GuiGraphicsExtractor g, Font font, Component line, int cx, int y, int maxW, int color) {
		int w = Math.min(maxW, font.width(line)) + 8;
		g.fill(cx - w / 2, y - 2, cx + w / 2, y + 9, 0xB0100818);
		SlotDraw.centeredFit(g, font, line, cx, y, maxW, color);
	}

	public static void balance(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model) {
		Component bal = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(model.balance));
		SlotDraw.chip(g, l.left + 10, l.balanceY + 3);
		SlotDraw.textFit(g, s.font(), bal, l.left + 17, l.balanceY, l.width / 2 - 12, BONE);
	}
}
