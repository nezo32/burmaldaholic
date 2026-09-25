package dev.nezo.burmaldaholic.client.pvp.kit;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What every PvP mode screen shares on top of its own content (drawn by the pvp client module after the screen, so
 * the five modes — Slot Showdown included — get the same beats): the grudge clash at the start of a grudge match, the
 * countdown digits with their ring (server-paced: the {@code pvp.countdown} step's ticks) and the Final Reveal.
 */
public final class MatchOverlay {
	private MatchOverlay() {}

	/** The grudge clash is playing (the screens draw the held banner only after it). */
	public static boolean clashPlaying(RevealState reveal) {
		double cd = reveal.countdownElapsedMs();
		return reveal.grudge() && cd >= 0 && cd < dev.nezo.burmaldaholic.pvp.logic.PvpMotion.GRUDGE_SLIDE_MS + 1000 && !reveal.active();
	}

	public static void draw(GuiGraphicsExtractor g, Font font, int width, int height, JsonObject state, RevealState reveal) {
		double cd = reveal.countdownElapsedMs();
		if (cd >= 0 && !reveal.active()) {
			double ticksLeft = reveal.countdownTicks() - cd / 50.0;
			if (reveal.grudge() && cd < dev.nezo.burmaldaholic.pvp.logic.PvpMotion.GRUDGE_SLIDE_MS + 1000) {
				PvpDraw.grudgeBanner(g, font, width / 2, Scene.top(height) + 22, -110, width + 10, cd, true);
			}
			if (ticksLeft > 0) PvpDraw.countdown(g, font, width / 2, height / 2 - 4, ticksLeft, reveal.countdownTicks());
		}
		if (reveal.active()) FinalRevealView.draw(g, font, width, height, PvpSeat.all(state), reveal);
	}
}
