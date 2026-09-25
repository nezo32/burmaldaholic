package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.PvpMotion;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The shared Final Reveal on top of every PvP mode screen (coin, wheel, plinko, scratch and Slot Showdown;
 * extras-pvp.md §9.4, visual/extras.md §7.2): the standings as face-down plaques ({@code plaque_back}) that tremble with
 * the drumroll, each place flipping ({@code plaque_face}, medal, head, name, points) on the server's cue — last to 2nd —
 * and the remaining plaque glowing gold during the pause. The winner's flip and banner belong to the result window.
 * Rows are places, not seats: a face-down row never tells who is in it.
 */
public final class FinalRevealView {
	public static final int W = 200;
	public static final int ROW = 22;

	private FinalRevealView() {}

	public static void draw(GuiGraphicsExtractor g, Font font, int width, int height, List<PvpSeat> seats, RevealState reveal) {
		int n = seats.size();
		if (n == 0 || !reveal.active()) return;
		reveal.drum();
		double t = reveal.ticks();
		boolean still = Kit.reduceMotion();
		int h = 40 + n * ROW;
		int x = (width - W) / 2;
		int y = Math.max(4, (height - h) / 2 - 10);
		g.fill(0, 0, width, height, 0x88100818);
		Scene.card(g, x - 8, y - 6, W + 16, h + 10);
		Component title = Component.translatable("gui.burmaldaholic.pvp.match.final");
		PvpDraw.modeBanner(g, font, title, width / 2, y - 2, W + 40, 900);
		int tremble = PvpMotion.tremble(t, still);
		int revealed = reveal.revealedCount();
		for (int place = 1; place <= n; place++) {
			int ry = y + 30 + (place - 1) * ROW;
			RevealState.Placing shown = null;
			for (PvpSeat s : seats) {
				RevealState.Placing p = reveal.placing(s.index());
				if (p != null && p.place() == place && shown == null) shown = p;
			}
			if (shown == null) {
				int dx = revealed == 0 || place > 1 ? tremble : 0;
				Kit.sprite(g, Kit.pvp("plaque_back"), x + dx, ry, W, 20);
				// the pause before the winner: the remaining plaque(s) glow gold at the edges
				if (revealed > 0 && n - revealed <= 2 && !still) {
					double glow = 0.5 + 0.5 * Math.sin(Util.getMillis() / 300.0);
					Kit.frameRect(g, x - 1, ry - 1, W + 2, 22, Kit.alpha(Kit.GOLD, 0.4 + 0.4 * glow));
				}
				Kit.centered(g, font, Component.translatable("gui.burmaldaholic.pvp.match.hidden"), x + W / 2, ry + 6, Kit.alpha(Kit.BONE_SHADE, 0.7));
				continue;
			}
			double since = Util.getMillis() - shown.arrivedMs();
			double[] flip = PvpMotion.flip(since, still);
			g.pose().pushMatrix();
			g.pose().translate(x + W / 2f, ry);
			g.pose().scale((float) Math.max(0.02, flip[0]), 1);
			g.pose().translate(-(x + W / 2f), -ry);
			if (flip[1] < 0.5) {
				Kit.sprite(g, Kit.pvp("plaque_back"), x, ry, W, 20);
			} else {
				int seatIndex = shown.seat();
				PvpSeat s = seats.stream().filter(p -> p.index() == seatIndex).findFirst().orElse(seats.get(0));
				Kit.sprite(g, Kit.pvp(place == 1 ? "plaque_gold" : "plaque_face"), x, ry, W, 20);
				PvpDraw.medal(g, place, x + 4, ry + 2);
				Faces.draw(g, s.key(), s.bot(), s.name(), x + 19, ry + 2, 16);
				Kit.fit(g, font, s.plateName(), x + 39, ry + 6, W - 39 - 50, s.you() ? Kit.GOLD : Kit.BONE, true);
				Kit.right(g, font, Texts.number(shown.points()), x + W - 6, ry + 6, Kit.BONE_SHADE);
			}
			g.pose().popMatrix();
		}
	}
}
