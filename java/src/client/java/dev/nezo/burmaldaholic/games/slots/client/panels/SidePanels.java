package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.features.TumbleView;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.HuntBoard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The feature panel (left: free-spin counter with its digit flip and the feature total, the Nether ladder plates,
 * the End sticky pips, the Overworld ×2 plate, hunt / hoard totals) and the win panel (right: the rolling WIN amount
 * in tier colours, Returned in grey, the last win), the label line under the reels and the balance line
 * (slots.md §4.5–§4.9, §4.12, SLOTS.md §10.5). Every box wraps / shrinks so Russian fits (1.45 × budget).
 */
public final class SidePanels {
	private SidePanels() {}

	public static int[] featureCenter(SlotLayout l) {
		return new int[] {l.leftPanelX + l.panelW / 2, l.compact ? l.panelY + 6 : l.panelY + 30};
	}

	public static int[] winCenter(SlotLayout l) {
		return new int[] {l.rightPanelX + l.panelW / 2, l.compact ? l.panelY + 6 : l.panelY + 30};
	}

	public static void draw(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model) {
		Font font = s.font();
		if (l.compact) {
			drawCompact(g, l, s, model, font);
			return;
		}
		int bg = 0xC0140822;
		SlotDraw.panel(g, l.leftPanelX, l.panelY, l.panelW, l.panelH, bg, 0xFF3A1450, 0x40D696FF);
		SlotDraw.panel(g, l.rightPanelX, l.panelY, l.panelW, l.panelH, bg, 0xFF3A1450, 0x40D696FF);
		feature(g, l, s, font, l.leftPanelX + 3, l.panelY + 4, l.panelW - 6);
		win(g, l, s, model, font, l.rightPanelX + 3, l.panelY + 4, l.panelW - 6);
	}

	private static void drawCompact(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model, Font font) {
		Component left = featureLine(s);
		if (left != null) SlotDraw.textFit(g, font, left, l.leftPanelX, l.panelY + 2, l.panelW, 0xFFFFD640);
		long amount = s.bigWin().panelAmount(s);
		WinTier tier = s.bigWin().panelTier(s);
		Component right = tier == WinTier.RETURN ? Component.translatable("gui.burmaldaholic.slots.returned", Texts.chips(amount))
			: amount > 0 ? Component.translatable("gui.burmaldaholic.slots.win", Texts.chips(amount))
			: s.lastWin() > 0 ? Component.translatable("gui.burmaldaholic.slots.last_win", Texts.chips(s.lastWin())) : null;
		if (right != null) {
			int w = Math.min(l.panelW, font.width(right));
			SlotDraw.textFit(g, font, right, l.rightPanelX + l.panelW - w, l.panelY + 2, l.panelW, tier == WinTier.RETURN ? 0xFFC0B0DC : 0xFFFFFFFF);
		}
	}

	/** One-line summary of the running feature (compact mode). */
	private static Component featureLine(SlotStage s) {
		int[] fs = s.freeSpins().counter(s);
		if (fs != null) return Component.translatable("gui.burmaldaholic.slots.fs.left", Texts.number(fs[0] + 1), Texts.number(fs[1]));
		if (s.hoard().covers(s)) return Component.translatable("gui.burmaldaholic.slots.hold.respins", Texts.number(s.hoard().respinsLeft(s)));
		if (s.hunt().covers(s)) return Component.translatable("gui.burmaldaholic.slots.pick.opened", Texts.number(s.hunt().board().opened()));
		if (s.machine() == Machine.NETHER) return Component.translatable("gui.burmaldaholic.slots.tumble.mult", Texts.number(s.tumble().multiplier(s)));
		return Component.translatable("gui.burmaldaholic.slots.ways");
	}

	private static void feature(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, Font font, int x, int y, int w) {
		int[] fs = s.freeSpins().counter(s);
		if (fs != null) {
			// counter plate "Spin 3 of 12": the number flips (old slides up, new from below)
			double punch = s.freeSpins().punch(s);
			double flip = s.reduceMotion() ? 1 : FeatureMotion.digitFlip(fs[2]);
			Component title = Component.translatable("gui.burmaldaholic.slots.fs.name." + s.machine().id);
			SlotDraw.centeredFit(g, font, title, x + w / 2, y, w, 0xFFFF9AE8);
			Component counter = Component.translatable("gui.burmaldaholic.slots.fs.left", Texts.number(fs[0] + 1), Texts.number(fs[1]));
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y + 16);
			g.pose().scale((float) punch, (float) punch);
			SlotDraw.plate(g, -w / 2, -5, w, 14, 0xFF6A2A8A, 0xFF2A0A44, 0xFFFFD640);
			g.enableScissor(x, y + 11, x + w, y + 25);
			int dy = (int) Math.round((1 - flip) * 10);
			SlotDraw.centeredFit(g, font, counter, 0, -2 + dy, w - 4, 0xFFFFFFFF);
			g.disableScissor();
			g.pose().popMatrix();
			Component total = Component.translatable("gui.burmaldaholic.slots.fs.total", Texts.chips(s.freeSpins().featureTotal(s)));
			SlotDraw.wrap(g, font, total, x, y + 30, w, 0xFFFFD640);
			y += 54;
		} else if (s.hunt().covers(s)) {
			HuntBoard b = s.hunt().board();
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.bonus.pick"), x + w / 2, y, w, 0xFFFFD640);
			SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.pick.opened", Texts.number(b.opened())), x, y + 14, w, 0xFFF4ECD8);
			SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.bonus.total", Texts.chips(b.totalTimesBet() * s.bet())), x, y + 38, w, 0xFFFFD640);
			return;
		} else if (s.hoard().covers(s)) {
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.bonus.hold"), x + w / 2, y, w, 0xFFFFC400);
			SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.hold.respins", Texts.number(s.hoard().respinsLeft(s))), x, y + 14, w, 0xFFF4ECD8);
			SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.hold.coins", Texts.number(s.hoard().coins(s))), x, y + 26, w, 0xFFF4ECD8);
			SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.bonus.total", Texts.chips(s.hoard().collected(s))), x, y + 46, w, 0xFFFFD640);
			return;
		} else {
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.ways"), x + w / 2, y, w, 0xFFD696FF);
			y += 14;
		}
		switch (s.machine()) {
			case NETHER -> ladder(g, s, font, x, y, w);
			case END -> sticky(g, s, font, x, y, w);
			case OVERWORLD -> {
				if (fs != null) {
					boolean glow = s.winShow().current(s) != null;
					SlotDraw.plate(g, x + w / 2 - 14, y, 28, 14, glow ? 0xFFFFD640 : 0xFF6A5A20, glow ? 0xFFB07010 : 0xFF3A2A10, 0xFFFFE680);
					SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(s.def().fsMultiplier())), x + w / 2, y + 3, 26,
						0xFF180A28);
				}
			}
		}
	}

	/** Nether: the 4 ladder plates; the lit one pops when it steps up. */
	private static void ladder(GuiGraphicsExtractor g, SlotStage s, Font font, int x, int y, int w) {
		boolean free = s.tumble().inFreeSpins(s);
		int[] ladder = TumbleView.ladder(s, free);
		int lit = s.tumble().multiplier(s);
		double pop = s.reduceMotion() ? 1 : FeatureMotion.platePop(s.tumble().plateSince(s));
		for (int i = 0; i < ladder.length; i++) {
			int py = y + i * 18;
			boolean on = ladder[i] == lit;
			int pw = Math.min(w, 72);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, py + 8);
			if (on) g.pose().scale((float) pop, (float) pop);
			if (CabinetArt.ART && (!on || CabinetArt.ANIMATED_STRIPS)) SlotSprites.blit(g, on ? SlotSprites.LADDER_LIT : SlotSprites.LADDER, -pw / 2, -8, pw, 16);
			else SlotDraw.plate(g, -pw / 2, -8, pw, 16, on ? 0xFFFF7A1A : 0xFF3A2020, on ? 0xFFC02010 : 0xFF1C1014, on ? 0xFFFFD640 : 0xFF5A3A30);
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(ladder[i])), 0, -4, pw - 4,
				on ? 0xFFFFFFFF : 0xFF8A6A60);
			g.pose().popMatrix();
		}
	}

	/** End: "Sticky reels: 2/3" with three pips that fill with the egg colour. */
	private static void sticky(GuiGraphicsExtractor g, SlotStage s, Font font, int x, int y, int w) {
		if (s.freeSpins().counter(s) == null) return;
		int mask = s.frames() == null ? 0 : s.frames().stickyMask;
		SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.sticky", Texts.number(Integer.bitCount(mask))), x, y, w, 0xFFD696FF);
		for (int i = 0; i < 3; i++) {
			int px = x + w / 2 - 16 + i * 12;
			boolean on = (mask & (1 << i)) != 0;
			SlotDraw.disc(g, px + 4, y + 26, 4, on ? 0xFFB040FF : 0xFF2A1A3A);
			if (on) SlotDraw.disc(g, px + 3, y + 25, 1, 0xFFFFFFFF);
		}
	}

	private static void win(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model, Font font, int x, int y, int w) {
		long amount = s.bigWin().panelAmount(s);
		WinTier tier = s.bigWin().panelTier(s);
		if (tier == WinTier.RETURN) {
			// Returned: grey, no pulse, no coins (never a win colour, F9)
			SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.returned", Texts.chips(amount)), x, y, w, 0xFFC0B0DC);
		} else if (tier.isWin() && amount > 0) {
			int color = switch (tier) {
				case NICE -> 0xFFFFD640;
				case BIG -> 0xFFFFD640;
				case MEGA -> 0xFFFF8A1A;
				case EPIC, JACKPOT -> 0xFFFF40C0;
				default -> 0xFFFFFFFF;
			};
			SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.win", Component.empty()), x + w / 2, y, w, 0xFFF4ECF8);
			int sc = SlotDraw.fitScale(font, Texts.chips(amount), 2, w - 2);
			SlotDraw.outlined(g, font, Texts.chips(amount), x + w / 2f, y + 18, sc, color, 0xFF180A28);
		}
		if (s.lastWin() > 0) {
			SlotDraw.wrap(g, font, Component.translatable("gui.burmaldaholic.slots.last_win", Texts.chips(s.lastWin())), x, y + l.panelH - 34, w, 0xFFC0B0DC);
		}
		Component bet = Component.translatable("gui.burmaldaholic.slots.bet", Texts.chips(model.bet()));
		SlotDraw.textFit(g, font, bet, x, y + l.panelH - 14, w, 0xFFFFFFFF);
	}

	/** The line under the reels: the current symbol win, a feature hint, or the error line. */
	public static void label(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model) {
		Font font = s.font();
		Component line = null;
		int color = 0xFFF4ECF8;
		if (model.errorKey != null && s.now() - model.errorAt < 3000) {
			line = Component.translatable(model.errorKey);
			color = 0xFFFF6E6A;
			// the error line slides in from the left (global §2.2)
			double u = Math.min(1, (s.now() - model.errorAt) / 150.0);
			g.pose().pushMatrix();
			g.pose().translate((float) ((1 - u) * -20), 0);
			SlotDraw.centeredFit(g, font, line, l.wx + l.windowW() / 2, l.labelY, l.width - 20, color);
			g.pose().popMatrix();
			return;
		}
		if (s.active()) line = s.winShow().label(s);
		if (line == null && model.autoLeft >= 0) line = Component.translatable("gui.burmaldaholic.slots.auto_left", Texts.number(model.autoLeft));
		if (line != null) SlotDraw.centeredFit(g, font, line, l.wx + l.windowW() / 2, l.labelY, l.width - 20, color);
	}

	public static void balance(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, SlotModel model) {
		Component bal = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(model.balance));
		SlotDraw.textFit(g, s.font(), bal, l.left + 6, l.balanceY, l.width / 2, 0xFFF4ECF8);
	}
}
