package dev.nezo.burmaldaholic.lastchance.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.lastchance.net.CoinFlipPayload;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The coin-flip animation (client only, purely cosmetic): the coin spins for {@link #SPIN_TICKS}
 * under "Last Chance…", then lands on heads/tails with the result title and subtitle, and fades out.
 * Drawn above the HUD, so it is also visible behind the death screen on tails. Long (Russian) lines
 * are wrapped to the screen width.
 */
final class CoinFlipOverlay {
	static final int SPIN_TICKS = 30;
	static final int HOLD_TICKS = 50;
	static final int FADE_TICKS = 15;
	private static final int TOTAL = SPIN_TICKS + HOLD_TICKS + FADE_TICKS;
	private static final int COIN = 32;
	private static final Identifier HEADS = Burmaldaholic.id("textures/gui/lastchance/coin_heads.png");
	private static final Identifier TAILS = Burmaldaholic.id("textures/gui/lastchance/coin_tails.png");

	private static CoinFlipPayload current;
	private static int age = -1;

	private CoinFlipOverlay() {}

	static void start(CoinFlipPayload payload) {
		current = payload;
		age = 0;
	}

	static void reset() {
		current = null;
		age = -1;
	}

	static boolean active() {
		return current != null && age >= 0 && age < TOTAL;
	}

	static void tick() {
		if (current != null && ++age >= TOTAL) {
			reset();
		}
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!active()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float t = age + delta.getGameTimeDeltaPartialTick(false);
		int alpha = t > SPIN_TICKS + HOLD_TICKS ? Mth.clamp((int) (255 * (1 - (t - SPIN_TICKS - HOLD_TICKS) / FADE_TICKS)), 0, 255) : 255;
		if (alpha <= 4) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int top = g.guiHeight() / 5;
		int maxWidth = Math.max(80, g.guiWidth() - 32);
		boolean spinning = t < SPIN_TICKS;

		// title: "Last Chance…" while spinning, then HEADS!/TAILS…
		Component title = Component.translatable(spinning ? "msg.burmaldaholic.lastchance.flip_title"
			: current.heads() ? "msg.burmaldaholic.lastchance.heads_title" : "msg.burmaldaholic.lastchance.tails_title");
		int titleColor = spinning ? 0xFFFFFF : current.heads() ? 0xFFD54A : 0xFF5555;
		int y = drawScaled(g, font, title, cx, top, 2.0f, maxWidth, argb(alpha, titleColor));

		// the coin: horizontal squash = cos(angle), faces alternate each half turn, eases out
		float width;
		boolean headsUp;
		if (spinning) {
			float progress = t / SPIN_TICKS;
			float angle = (float) (Math.PI * 7 * (1 - (1 - progress) * (1 - progress)));
			float c = Mth.cos(angle);
			width = Math.max(2, Math.abs(c) * COIN);
			headsUp = c >= 0;
		} else {
			width = COIN;
			headsUp = current.heads();
		}
		int bounce = spinning ? (int) (Mth.sin(t / SPIN_TICKS * (float) Math.PI) * -12) : 0;
		int coinTop = y + 6 + bounce;
		int x0 = Math.round(cx - width / 2);
		int x1 = Math.round(cx + width / 2);
		g.blit(headsUp ? HEADS : TAILS, x0, coinTop, x1, coinTop + COIN, 0, 1, 0, 1);
		y += 6 + COIN + 6;

		if (!spinning) {
			Component sub = Component.translatable(!current.heads() ? "msg.burmaldaholic.lastchance.tails_subtitle"
				: current.highStakes() ? "msg.burmaldaholic.lastchance.hardcore.title" : "msg.burmaldaholic.lastchance.heads_subtitle");
			drawScaled(g, font, sub, cx, y, 1.0f, maxWidth, argb(alpha, current.heads() && current.highStakes() ? 0xFF5555 : 0xE0E0E0));
		}
	}

	/** Centered, word-wrapped text at {@code scale}; returns the y below it. */
	private static int drawScaled(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, float scale, int maxWidth, int color) {
		List<FormattedCharSequence> lines = font.split(text, Math.max(20, (int) (maxWidth / scale)));
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		int ly = 0;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, -font.width(line) / 2, ly, color, true);
			ly += font.lineHeight + 1;
		}
		g.pose().popMatrix();
		return y + Math.round(ly * scale);
	}

	private static int argb(int alpha, int rgb) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}
}
