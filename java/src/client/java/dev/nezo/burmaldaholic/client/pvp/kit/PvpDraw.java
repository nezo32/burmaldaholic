package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.core.pvp.logic.Taunts;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.PvpMotion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The PvP pieces of visual/extras.md §7: seat plates with heads and badges, the pot, taunt bubbles, mode banners,
 * the grudge banner, the VS badge, the countdown digits, ready ticks and place medals. Every piece is drawn from
 * the generated sprites; text comes from translation keys.
 */
public final class PvpDraw {
	public static final Identifier BADGES = Kit.sheet("pvp/badges");
	public static final Identifier MEDALS = Kit.sheet("pvp/rank_medals");
	public static final Identifier POT_CHIPS = Kit.sheet("pvp/pot_chips");
	public static final Identifier TAUNT_ICONS = Kit.sheet("pvp/taunt_icons");
	public static final Identifier MODE_ICONS = Kit.sheet("pvp/mode_icons");
	public static final Identifier MODE_ICONS_40 = Kit.sheet("pvp/mode_icons_40");
	public static final Identifier PODIUM = Kit.sheet("pvp/podium");
	/** badges.png order: rival, grudge claw, nemesis, bot, check, padlock. */
	public static final int BADGE_CLAW = 1;
	public static final int BADGE_NEMESIS = 2;
	public static final int BADGE_CHECK = 4;
	public static final int BADGE_PADLOCK = 5;
	public static final int PLATE_H = 30;
	private static final int CHEEKY_TEXT = 0xFF8C1834;
	private static final int FRIENDLY_TEXT = 0xFF1C4A2A;

	private PvpDraw() {}

	public enum PlateKind {
		YOU, RIVAL, BOT, GRUDGE, WINNER, LOSER;

		String sprite() {
			return "plate_" + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The plate kind of a seat: you, a bot, the grudge rival, or another player. */
	public static PlateKind kindOf(PvpSeat s, boolean grudge) {
		if (s.you()) return PlateKind.YOU;
		if (s.bot()) return PlateKind.BOT;
		return grudge ? PlateKind.GRUDGE : PlateKind.RIVAL;
	}

	/**
	 * A seat plate {@code w × 30}: head frame + face, name (gold for you) at (29, 6), score line at (29, 17), badges
	 * right-aligned at y 16 (bot difficulty, head-to-head record, ALL-IN, streak flame).
	 */
	public static void plate(GuiGraphicsExtractor g, Font font, PvpSeat s, PlateKind kind, int x, int y, int w, @Nullable Component score) {
		Kit.sprite(g, Kit.pvp(kind.sprite()), x, y, w, PLATE_H);
		String frame = s.bot() ? "head_frame_bot" : s.you() ? "head_frame_you" : "head_frame_rival";
		Kit.sprite(g, Kit.pvp(frame), x + 5, y + 5, 20, 20);
		Faces.draw(g, s.key(), s.bot(), s.name(), x + 7, y + 7, 16);
		int bx = x + w - 6;
		// badges, right to left
		if (s.bot() && !s.level().isEmpty()) {
			bx -= 22;
			Kit.sprite(g, Kit.pvp("bot_" + s.level()), bx, y + 16, 22, 11);
		}
		if (s.hasRecord() && !s.you()) {
			Component rec = Component.translatable("gui.burmaldaholic.pvp.record_chip", Texts.number(s.wins()), Texts.number(s.losses()));
			int rw = font.width(rec) + 6;
			bx -= rw + 2;
			String chip = switch (PvpMotion.recordChip(s.wins(), s.losses())) {
				case 0 -> "record_chip_lead";
				case 1 -> "record_chip_trail";
				default -> "record_chip_even";
			};
			Kit.sprite(g, Kit.pvp(chip), bx, y + 16, rw, 10);
			g.text(font, rec, bx + 3, y + 17, Kit.BONE, false);
		}
		if (s.allIn()) {
			Component tag = Component.translatable("gui.burmaldaholic.pvp.all_in_tag");
			int tw = Math.min(font.width(tag) + 8, 48);
			bx -= tw + 2;
			int shake = !Kit.reduceMotion() ? (int) ((Util.getMillis() / 62) % 2) : 0;
			Kit.sprite(g, Kit.pvp("all_in"), bx + shake, y + 15, tw, 12);
			Kit.fit(g, font, tag, bx + shake + 4, y + 17, tw - 6, Kit.BONE, false);
		}
		int nameW = Math.max(20, bx - (x + 29) - 2);
		Kit.fit(g, font, s.plateName(), x + 29, y + 6, w - 29 - 6, kind == PlateKind.YOU ? Kit.GOLD : Kit.BONE, true);
		if (score != null) Kit.fit(g, font, score, x + 29, y + 17, nameW, Kit.BONE_SHADE, true);
	}

	/** The ready tick above a plate (that seat pressed the step button). */
	public static void readyTick(GuiGraphicsExtractor g, int x, int y) {
		Kit.region(g, BADGES, 96, 16, BADGE_CHECK * 16, 0, 16, 16, x, y);
	}

	/** Grudge claw over the underdog's plate corner. */
	public static void claw(GuiGraphicsExtractor g, int x, int y) {
		Kit.sprite(g, Kit.pvp("claw"), x, y, 16, 16);
	}

	/** The pot: glow + chip pile (s / m / l) + plaque "Pot N", centred on {@code cx}. */
	public static void pot(GuiGraphicsExtractor g, Font font, long pot, long stake, int cx, int y) {
		Component label = Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.number(pot));
		int pw = Math.max(64, font.width(label) + 16);
		int total = 64 + 4 + pw;
		int x0 = cx - total / 2;
		Kit.sprite(g, Kit.pvp("pot_glow"), x0 - 4, y + 18, 72, 24);
		int pile = PvpMotion.potPile(pot, stake);
		Kit.region(g, POT_CHIPS, 192, 36, pile * 64, 0, 64, 36, x0, y);
		Kit.sprite(g, Kit.pvp("pot_plaque"), x0 + 68, y + 16, pw, 18);
		g.text(font, label, x0 + 68 + pw / 2 - font.width(label) / 2, y + 21, Kit.GOLD, true);
	}

	/**
	 * A taunt bubble whose tail points down at (tailX, bottomY): {@code bubble_friendly|cheeky}, the pictogram and the
	 * line. {@code ageTicks} fades it out over its last 10 ticks and pops it in over 3.
	 */
	public static void bubble(GuiGraphicsExtractor g, Font font, int line, int tailX, int bottomY, int maxW, double ageTicks) {
		if (!Taunts.valid(line)) return;
		boolean friendly = Taunts.friendly(line);
		Component text = Component.translatable(Taunts.key(line));
		int bw = Math.min(maxW, font.width(text) + 30);
		int x = Math.max(4, tailX - 16);
		int y = bottomY - 22 - 6;
		double pop = Kit.reduceMotion() ? 1 : Math.min(1, ageTicks / 3.0);
		double fade = ageTicks > 50 ? Math.max(0, 1 - (ageTicks - 50) / 10.0) : 1;
		if (fade <= 0.02) return;
		g.pose().pushMatrix();
		g.pose().translate(tailX, bottomY);
		float s = (float) (0.6 + 0.4 * pop);
		g.pose().scale(s, s);
		g.pose().translate(-tailX, -bottomY);
		String kind = friendly ? "friendly" : "cheeky";
		Kit.sprite(g, Kit.pvp("bubble_" + kind), x, y, bw, 22, Kit.fade(fade));
		Kit.sprite(g, Kit.pvp("bubble_tail_" + kind), tailX - 5, y + 21, 10, 8, Kit.fade(fade));
		Kit.region(g, TAUNT_ICONS, 128, 16, line * 16, 0, 16, 16, x + 5, y + 3, 16, 16, Kit.fade(fade));
		Kit.fit(g, font, text, x + 24, y + 7, bw - 28, Kit.alpha(friendly ? FRIENDLY_TEXT : CHEEKY_TEXT, fade), false);
		g.pose().popMatrix();
	}

	/** A mode banner (No more bets, FINAL BALL, ALL SQUARE, UNDERDOG, EDGE) at {@code ms} since it was raised. */
	public static void modeBanner(GuiGraphicsExtractor g, Font font, Component label, int cx, int y, int maxW, double ms) {
		double[] m = PvpMotion.banner(ms, Kit.reduceMotion());
		if (m[1] <= 0.02) return;
		int scale = font.width(label) * 2 + 32 <= maxW ? 2 : 1;
		int w = Math.min(maxW, font.width(label) * scale + 32);
		int h = scale == 2 ? 28 : 20;
		int yy = (int) Math.round(y + m[0]);
		g.pose().pushMatrix();
		g.pose().translate(cx, yy + h / 2f);
		g.pose().scale((float) m[2], (float) m[2]);
		g.pose().translate(-cx, -(yy + h / 2f));
		Kit.sprite(g, Kit.pvp("mode_banner"), cx - w / 2, yy, w, h, Kit.fade(m[1]));
		Kit.big(g, font, label, cx, yy + (h - 8 * scale) / 2f, scale, Kit.alpha(Kit.GOLD, m[1]), Kit.INK);
		g.pose().popMatrix();
	}

	/**
	 * The grudge banner (§9.5): the torn halves slide in from the edges and clash in the centre; {@code GRUDGE MATCH}
	 * in bold bone with a dark red outline. {@code ms} since the match screen opened; {@code hold} keeps it (the match
	 * layout of the mockup shows it for the whole match).
	 */
	public static void grudgeBanner(GuiGraphicsExtractor g, Font font, int cx, int y, int screenLeft, int screenRight, double ms, boolean hold) {
		double[] m = PvpMotion.grudge(ms, Kit.reduceMotion());
		double slide = m[0];
		double alpha = hold ? 1 : m[1];
		if (alpha <= 0.02) return;
		int shake = (int) Math.round(m[2]);
		int lx = (int) Math.round(cx - 100 + (screenLeft - (cx - 100)) * (1 - slide)) + shake;
		int rx = (int) Math.round(cx - 2 + (screenRight - (cx - 2)) * (1 - slide)) + shake;
		Kit.sprite(g, Kit.pvp("grudge_left"), lx, y, 102, 28, Kit.fade(alpha));
		Kit.sprite(g, Kit.pvp("grudge_right"), rx, y, 102, 28, Kit.fade(alpha));
		if (slide >= 0.99) {
			Component t = Component.translatable("gui.burmaldaholic.pvp.grudge.title").withStyle(net.minecraft.ChatFormatting.BOLD);
			Kit.big(g, font, t, cx + shake, y + 10, 1, Kit.alpha(Kit.BONE, alpha), 0xFF5A0000);
		}
	}

	/** VS badge (split starburst) popping between two plates. */
	public static void vs(GuiGraphicsExtractor g, Font font, int cx, int cy, double scale) {
		if (scale <= 0.02) return;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale((float) scale, (float) scale);
		Kit.sprite(g, Kit.pvp("vs_badge"), -24, -16, 48, 32);
		Kit.big(g, font, Component.translatable("gui.burmaldaholic.pvp.vs").withStyle(net.minecraft.ChatFormatting.BOLD), 0, -4, 1, Kit.BONE, Kit.INK);
		g.pose().popMatrix();
	}

	/** Big countdown digit (4×) centred at (cx, cy) with the ring; {@code ticksLeft} of {@code totalTicks}. */
	public static void countdown(GuiGraphicsExtractor g, Font font, int cx, int cy, double ticksLeft, int totalTicks) {
		if (ticksLeft <= 0) return;
		int secs = (int) Math.ceil(ticksLeft / 20.0);
		double into = (secs * 20 - ticksLeft) * 50;
		double[] d = PvpMotion.countdownDigit(into, Kit.reduceMotion());
		int color = PvpMotion.countdownColor(secs);
		Kit.ring(g, cx, cy, 30, 1 - ticksLeft / Math.max(1, totalTicks), color, 0x60180A28);
		Kit.big(g, font, Texts.number(secs), cx, cy - 16 * (float) d[0], 4 * (float) d[0], Kit.alpha(color, d[1]), Kit.INK);
	}

	/** Place medal (1–6) with the place as text. */
	public static void medal(GuiGraphicsExtractor g, int place, int x, int y) {
		int p = Math.max(1, Math.min(6, place));
		Kit.region(g, MEDALS, 72, 16, (p - 1) * 12, 0, 12, 16, x, y);
	}

	/** Mode icon (coin, wheel, plinko, scratch, slots), 16 px. */
	public static void modeIcon(GuiGraphicsExtractor g, String mode, int x, int y) {
		int i = modeIndex(mode);
		if (i >= 0) Kit.region(g, MODE_ICONS, 80, 16, i * 16, 0, 16, 16, x, y);
	}

	public static int modeIndex(String mode) {
		return switch (mode) {
			case "coin" -> 0;
			case "wheel" -> 1;
			case "plinko" -> 2;
			case "scratch" -> 3;
			case "slots" -> 4;
			default -> -1;
		};
	}
}
