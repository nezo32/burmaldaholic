package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackWorld;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The runtime part of a full table's felt print (visual QA round 1): what the baked {@code felt_print_<game>_*} cannot
 * carry — the LOCALIZED felt texts (curved along the printed arcs, laid flat on the felt, in the board font: "BLACKJACK
 * PAYS 3 TO 2", "INSURANCE PAYS 2 TO 1", the box names of baccarat, the UTH dealer rule, the Hold'em pot) and the
 * per-seat betting spots, whose count is configurable ({@code felt_marks.png}: a double ring per blackjack seat, the
 * ante / blind / trips / play group per UTH seat). Design coordinates: called with the pose already mapped onto the felt
 * ({@link BoardRenderer#mapDesign}). Texts within 8 blocks, spots within the felt LOD; printed in the felt's light (they
 * darken with it at night). Positions follow the game renderers (BlackjackWorld, UthTableRenderer, BaccaratBoardRenderer).
 */
final class FeltPrint {
	/** Print inks: gold lines / spots, cream text (the generator's INK). */
	static final int GOLD = 0xFFD8B860, CREAM = 0xE6EFE6C8;
	/** Text lift above the felt (under the cards' 1/64). */
	private static final float TEXT_Y = BoardGeometry.TOP + 1 / 160f, MARK_Y = BoardGeometry.TOP + 1 / 200f;
	/** Dealer centre of the crescent arcs (design). */
	private static final float AX = 0.5f, AZ = 0.10f;

	/** One felt text split into glyph sequences (cached per key + language). */
	private static final class Prepared {
		final String lang;
		final FormattedCharSequence[] chars;
		final float[] widths;
		final float total;

		Prepared(String lang, Font font, String text) {
			this.lang = lang;
			int n = text.codePointCount(0, text.length());
			chars = new FormattedCharSequence[n];
			widths = new float[n];
			float t = 0;
			int i = 0;
			for (int off = 0; off < text.length();) {
				int cp = text.codePointAt(off);
				String ch = new String(Character.toChars(cp));
				chars[i] = FormattedCharSequence.forward(ch, Style.EMPTY);
				widths[i] = font.width(ch);
				t += widths[i];
				i++;
				off += Character.charCount(cp);
			}
			total = t;
		}
	}

	private static final Map<String, Prepared> CACHE = new HashMap<>();

	private FeltPrint() {}

	static void clear() {
		CACHE.clear();
	}

	private static Prepared text(Font font, String lang, String key, Component c) {
		Prepared p = CACHE.get(key);
		if (p == null || !p.lang.equals(lang)) {
			p = new Prepared(lang, font, c.getString());
			CACHE.put(key, p);
		}
		return p;
	}

	/** Draws the runtime print of a full table (pose mapped to design coordinates). */
	static void submit(PoseStack pose, SubmitNodeCollector out, Font font, BoardState s, int seats) {
		TableGame g = s.tableGame;
		if (!s.full || g == null) return;
		int light = s.feltLight;
		boolean text = s.showsPlates();
		switch (g) {
			case BLACKJACK -> {
				int n = Math.max(5, seats);
				for (int i = 0; i < n; i++) {
					float[] st = BlackjackWorld.stack(BlackjackWorld.seat(i, n));
					mark(pose, out, 0, st[0], st[1], 0.23f, GOLD, light);
				}
				if (text) {
					var c = CasinoConfig.blackjack();
					int[] r = ratio(c.blackjackPayout);
					arc(pose, out, font, s, "bj_pays", Component.translatable("gui.burmaldaholic.core.board.felt.bj_pays", r[0], r[1]), 0.42f, 0.325f, 0.0072f,
						light);
					if (c.insurance) {
						arc(pose, out, font, s, "insurance", Component.translatable("gui.burmaldaholic.core.board.felt.insurance"), 0.55f, 0.445f, 0.0068f, light);
					}
					arc(pose, out, font, s, c.dealerHitsSoft17 ? "bj_h17" : "bj_s17", Component.translatable(
						c.dealerHitsSoft17 ? "gui.burmaldaholic.core.board.felt.bj_dealer_h17" : "gui.burmaldaholic.core.board.felt.bj_dealer"), 0.33f, 0.25f, 0.0052f,
						light);
				}
			}
			case BACCARAT -> {
				if (text) {
					var c = CasinoConfig.baccarat();
					straight(pose, out, font, s, "player", Component.translatable("gui.burmaldaholic.core.board.felt.player"), 0.10f, 0.62f, 0.40f, 0.0075f, light);
					straight(pose, out, font, s, "banker", Component.translatable("gui.burmaldaholic.core.board.felt.banker"), 0.90f, 0.62f, 0.40f, 0.0075f, light);
					straight(pose, out, font, s, "tie", Component.translatable("gui.burmaldaholic.core.board.felt.tie", c.tiePays), 0.50f, 0.70f, 0.26f, 0.006f,
						light);
					Component pair = Component.translatable("gui.burmaldaholic.core.board.felt.pair");
					straight(pose, out, font, s, "pair", pair, -0.32f, 0.52f, 0.26f, 0.006f, light);
					straight(pose, out, font, s, "pair", pair, 1.32f, 0.52f, 0.26f, 0.006f, light);
				}
			}
			case UTH -> {
				int n = Math.max(1, seats);
				float seatH = BoardGeometry.CARD_H * 0.78f;
				for (int i = 0; i < n; i++) {
					float[] a = BoardGeometry.crescentSeat(i, n);
					float sx = a[0] + (0.5f - a[0]) * 0.22f;
					float sz = a[1] + (0.10f - a[1]) * 0.22f - seatH * 0.5f;
					mark(pose, out, 1, sx - 0.08f, sz, 0.11f, GOLD, light); // ante
					mark(pose, out, 1, sx + 0.08f, sz, 0.11f, GOLD, light); // blind
					mark(pose, out, 3, sx, sz - 0.11f, 0.11f, GOLD, light); // trips
					mark(pose, out, 2, sx, sz + 0.09f, 0.09f, 0xFFEFE6C8, light); // play
				}
				if (text) {
					arc(pose, out, font, s, "uth_q", Component.translatable("gui.burmaldaholic.core.board.felt.uth_qualifies"), 0.53f, 0.245f, 0.0058f, light);
				}
			}
			case POKER -> {
				if (text) straight(pose, out, font, s, "pot", Component.translatable("gui.burmaldaholic.core.board.felt.pot"), 0.5f, 0.59f, 0.3f, 0.0065f, light);
			}
			default -> {
			}
		}
	}

	/** {@code payout} (profit multiple, e.g. 1.5) as a small fraction {a, b} (3 to 2, 6 to 5, 2 to 1 …). */
	static int[] ratio(double payout) {
		for (int b = 1; b <= 10; b++) {
			double a = payout * b;
			if (Math.abs(a - Math.rint(a)) < 1e-6) return new int[] {(int) Math.rint(a), b};
		}
		return new int[] {(int) Math.round(payout * 10), 10};
	}

	/** A betting mark (atlas cell 0..3) centred on design (x, z), {@code size} blocks wide, tinted. */
	private static void mark(PoseStack pose, SubmitNodeCollector out, int cell, float x, float z, float size, int argb, int light) {
		float h = size / 2, y = MARK_Y;
		float u0 = cell / 4f, u1 = (cell + 1) / 4f;
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.MARKS), (p, vc) -> {
			Quads.v(p, vc, x - h, y, z + h, u0, 1, argb, light, 0, 1, 0);
			Quads.v(p, vc, x + h, y, z + h, u1, 1, argb, light, 0, 1, 0);
			Quads.v(p, vc, x + h, y, z - h, u1, 0, argb, light, 0, 1, 0);
			Quads.v(p, vc, x - h, y, z - h, u0, 0, argb, light, 0, 1, 0);
		});
	}

	/**
	 * Text laid flat on the felt along the front of an ellipse round the dealer (radii rx, rz, design), centred on the
	 * players' axis, glyph tops toward the dealer (upright for the players); shrinks to fit ±70° of the arc.
	 */
	private static void arc(PoseStack pose, SubmitNodeCollector out, Font font, BoardState s, String key, Component c, float rx, float rz, float px, int light) {
		Prepared t = text(font, s.lang, key, c);
		if (t.total <= 0) return;
		// arc length of ±70° (numeric): fit the text into 85 % of it
		double len = 0;
		for (int k = -70; k < 70; k++) {
			double a = Math.toRadians(k + 0.5);
			len += Math.toRadians(1) * Math.hypot(rx * Math.cos(a), rz * Math.sin(a));
		}
		float scale = (float) Math.min(px, len * 0.85 / t.total);
		// walk the arc from the left end; θ = 0 is the players' axis (front), growing toward +x
		double theta = -halfAngle(rx, rz, t.total * scale / 2);
		for (int i = 0; i < t.chars.length; i++) {
			double half = t.widths[i] * scale / 2;
			theta += step(rx, rz, theta, half);
			float x = AX + (float) (rx * Math.sin(theta));
			float z = AZ + (float) (rz * Math.cos(theta));
			// tangent (dx/dθ, dz/dθ) = (rx cos θ, −rz sin θ): the glyph's x axis
			float yaw = (float) Math.atan2(-rz * Math.sin(theta), rx * Math.cos(theta));
			glyph(pose, out, t.chars[i], t.widths[i], x, z, yaw, scale, light);
			theta += step(rx, rz, theta, half);
		}
	}

	/** Angle that covers arc length {@code s} starting at θ = 0 (numeric, 0.5° steps). */
	private static double halfAngle(float rx, float rz, double s) {
		double a = 0, acc = 0, d = Math.toRadians(0.5);
		while (acc < s && a < Math.PI / 2) {
			acc += d * Math.hypot(rx * Math.cos(a), rz * Math.sin(a));
			a += d;
		}
		return a;
	}

	/** Δθ that advances arc length {@code s} from θ. */
	private static double step(float rx, float rz, double theta, double s) {
		double speed = Math.hypot(rx * Math.cos(theta), rz * Math.sin(theta));
		return speed < 1e-6 ? 0 : s / speed;
	}

	/** A straight line of text centred on design (x, z), fitted into {@code maxW} blocks. */
	private static void straight(PoseStack pose, SubmitNodeCollector out, Font font, BoardState s, String key, Component c, float x, float z, float maxW, float px,
			int light) {
		Prepared t = text(font, s.lang, key, c);
		if (t.total <= 0) return;
		float scale = Math.min(px, maxW / t.total);
		float cx = x - t.total * scale / 2;
		for (int i = 0; i < t.chars.length; i++) {
			glyph(pose, out, t.chars[i], t.widths[i], cx + t.widths[i] * scale / 2, z, 0, scale, light);
			cx += t.widths[i] * scale;
		}
	}

	/**
	 * One glyph lying on the felt, centred on (x, z), its baseline along {@code yaw} (radians from +x toward +z), its top
	 * toward −z (the dealer). Font x → the tangent, font y (down) → toward the players; seen from above it reads upright.
	 */
	private static void glyph(PoseStack pose, SubmitNodeCollector out, @Nullable FormattedCharSequence ch, float width, float x, float z, float yaw, float scale,
			int light) {
		if (ch == null) return;
		pose.pushPose();
		pose.translate(x, TEXT_Y, z);
		pose.mulPose(Axis.YP.rotation(-yaw));
		pose.mulPose(Axis.XP.rotationDegrees(90));
		pose.scale(scale, scale, scale);
		out.submitText(pose, -width / 2f, -3.5f, ch, false, Font.DisplayMode.POLYGON_OFFSET, light, CREAM, 0, 0);
		pose.popPose();
	}
}
