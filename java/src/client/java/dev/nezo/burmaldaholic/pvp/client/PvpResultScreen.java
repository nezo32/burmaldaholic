package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.Faces;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.PvpMotion;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The result window (PVP.md §3.11.1; visual/extras.md §7.2 "Result", mockup {@code extras_pvp_result.png}): the end of
 * the Final Reveal. The places 2…n were flipped on the server's cues in the mode screen's overlay, so here the
 * standings plaques are face up and the winner's plaque flips gold, then bursts into the winner banner with the rays
 * turning behind it, the crown drops onto the winner's head on the podium, confetti falls and your payout rolls up.
 * A dead heat reads DEAD HEAT! over the shared places. [Rematch] [Taunt…] [Close], the rematch count, the chip counter.
 * Reduce motion: no rays turning, no confetti, the end state at once.
 */
final class PvpResultScreen extends PvpScreen {
	private static final Identifier CONFETTI = Kit.sheet("core/fx/confetti");
	private static final int SX = 262;
	private static final int FLIP_AT = 0;
	private static final int BANNER_AT = 200;
	private static final int ROLL_MS = 900;

	PvpResultScreen(JsonObject state) {
		super(Component.translatable("gui.burmaldaholic.pvp.result.title"), state);
	}

	private JsonObject result() {
		JsonObject r = state().getAsJsonObject("result");
		return r == null ? new JsonObject() : r;
	}

	private int rows() {
		return ints(result(), "order").length;
	}

	/** Ms since the screen opened (the winner reveal). */
	private double since() {
		return Kit.reduceMotion() ? 1e6 : Util.getMillis() - openedAt;
	}

	/** Test hook kept for the client GameTests: the reveal at its end state. */
	boolean revealDone() {
		return since() >= BANNER_AT + ROLL_MS;
	}

	private int[] winners() {
		return ints(result(), "winners");
	}

	private boolean isWinner(int seat) {
		for (int w : winners()) if (w == seat) return true;
		return false;
	}

	private Component banner() {
		int[] w = winners();
		if (w.length > 1) return Component.translatable("gui.burmaldaholic.pvp.result.dead_heat_title");
		JsonObject p = w.length == 0 ? null : participant(state(), w[0]);
		return Component.translatable("gui.burmaldaholic.pvp.result.winner_title", p == null ? Component.empty() : PvpSeat.of(p).plateName());
	}

	private long myPayout() {
		long you = num(state(), "you", -1);
		long[] payouts = longs(result(), "payouts");
		return you >= 0 && you < payouts.length ? payouts[(int) you] : 0;
	}

	private @Nullable Component rematchLine() {
		int ready = 0;
		int humans = 0;
		for (JsonObject p : participants(state())) {
			if (!bool(p, "bot")) {
				humans++;
				if (bool(p, "rematch")) ready++;
			}
		}
		return ready == 0 ? null : Component.translatable("gui.burmaldaholic.pvp.result.rematch_ready", Texts.number(ready), Texts.number(humans));
	}

	@Override
	protected void layout() {
		JsonObject s = state();
		long you = num(s, "you", -1);
		JsonObject me = you < 0 ? null : participant(s, you);
		int x = 50;
		if (me != null && "SETTLED".equalsIgnoreCase(str(s, "state", ""))) {
			Component rm = Component.translatable("gui.burmaldaholic.pvp.rematch");
			int w = labelWidth(rm, 90);
			button(x, 210, w, rm, KitButton.Style.PRIMARY, b -> send("rematch", "", 0)).active(!bool(me, "rematch")).selected(true);
			x += w + 6;
			Component taunt = Component.translatable("gui.burmaldaholic.pvp.taunt.button");
			int tw = labelWidth(taunt, 60);
			button(x, 210, tw, taunt, KitButton.Style.SECONDARY, b -> PvpScreens.openTaunts(this));
			x += tw + 4;
		}
		Component close = Component.translatable("gui.burmaldaholic.common.close");
		button(x, 210, labelWidth(close, 60), close, KitButton.Style.SECONDARY, b -> onClose());
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		JsonObject s = state();
		JsonObject r = result();
		double t = since();
		boolean still = Kit.reduceMotion();
		int cx = left + 130;
		// rays behind the winner banner
		double bannerP = t < BANNER_AT ? 0 : still ? 1 : Ease.OUT_BACK.apply(Math.min(1, (t - BANNER_AT) / 300.0));
		if (bannerP > 0) {
			float rot = still ? 0 : (float) (t / 4000.0 * Math.PI);
			g.pose().pushMatrix();
			g.pose().translate(cx, top + 56);
			g.pose().rotate(rot);
			Kit.sprite(g, Kit.core("fx/rays"), -64, -64, 128, 128, Kit.fade(0.7 * Math.min(1, bannerP)));
			g.pose().popMatrix();
		}
		// podium with heads (1st centre, 2nd left, 3rd right)
		List<PvpSeat> seats = PvpSeat.all(s);
		int[] order = ints(r, "order");
		int[] places = ints(r, "places");
		int px0 = left + 30;
		int py0 = top + 118;
		Kit.region(g, PvpDraw.PODIUM, 200, 60, 0, 0, 200, 60, px0, py0);
		int[][] spots = {{100, 0}, {32, 20}, {168, 32}};
		for (int k = 0; k < Math.min(3, order.length); k++) {
			int seat = order[k];
			PvpSeat p = seats.stream().filter(q -> q.index() == seat).findFirst().orElse(null);
			if (p == null) continue;
			int place = seat < places.length && places[seat] > 0 ? places[seat] : k + 1;
			int spot = Math.min(2, place - 1);
			int dx = spots[spot][0];
			int top0 = spots[spot][1];
			if (k > 0 && spot == 0) {
				dx += k == 1 ? -30 : 30; // a dead heat shares the top step
			}
			boolean win = isWinner(seat);
			double show = win && !still ? Math.min(1, Math.max(0, (t - BANNER_AT) / 200.0)) : 1;
			if (show <= 0) continue;
			Faces.draw(g, p.key(), p.bot(), p.name(), px0 + dx - 12, py0 + top0 - 26, 24);
			Kit.big(g, font, Texts.number(place).copy().withStyle(ChatFormatting.BOLD), px0 + dx, py0 + top0 + 17, 1, Kit.BONE, Kit.INK);
			if (win && winners().length == 1) {
				double drop = still ? 1 : Ease.OUT_BOUNCE.apply(Math.min(1, Math.max(0, (t - BANNER_AT - 150) / 300.0)));
				int cy = (int) Math.round(py0 + top0 - 36 - 20 * (1 - drop));
				if (t >= BANNER_AT + 150 || still) Kit.sprite(g, Kit.pvp("crown"), px0 + dx - 8, cy, 16, 12);
			}
		}
		// confetti
		if (!still && t > BANNER_AT) confetti(g, t - BANNER_AT);
		// winner banner
		if (bannerP > 0) {
			Component b = banner().copy().withStyle(ChatFormatting.BOLD);
			int bw = Math.max(140, font.width(b) + 36);
			g.pose().pushMatrix();
			g.pose().translate(cx, top + 29);
			g.pose().scale((float) bannerP, (float) bannerP);
			Kit.sprite(g, Kit.pvp("winner_banner"), -bw / 2, -15, bw, 30);
			Kit.centeredFit(g, font, b, 0, -5, bw - 24, Kit.GOLD, true);
			g.pose().popMatrix();
		} else {
			Kit.big(g, font, Component.translatable("gui.burmaldaholic.pvp.match.final"), cx, top + 24, 1, Kit.BONE_SHADE, Kit.INK);
		}
		// standings: face-up plaques with medals; the winner's flips gold
		Kit.text(g, font, Component.translatable("gui.burmaldaholic.pvp.result.standings"), left + SX, top + 16, GOLD);
		long[] points = longs(r, "points");
		int n = Math.min(order.length, 6);
		int pitch = n > 4 ? 18 : 24;
		for (int k = 0; k < n; k++) {
			int seat = order[k];
			PvpSeat p = seats.stream().filter(q -> q.index() == seat).findFirst().orElse(null);
			if (p == null) continue;
			int y = top + 28 + k * pitch;
			int x = left + SX - 4;
			boolean win = isWinner(seat);
			double[] flip = win ? PvpMotion.flip(t - FLIP_AT, still) : new double[] {1, 1};
			g.pose().pushMatrix();
			g.pose().translate(x + 64, y);
			g.pose().scale((float) Math.max(0.02, flip[0]), 1);
			g.pose().translate(-(x + 64), -y);
			if (flip[1] < 0.5) {
				Kit.sprite(g, Kit.pvp("plaque_back"), x, y, 128, pitch > 18 ? 20 : 16);
			} else {
				int ph = pitch > 18 ? 20 : 16;
				Kit.sprite(g, Kit.pvp(win ? "plaque_gold" : "plaque_face"), x, y, 128, ph);
				int place = seat < places.length && places[seat] > 0 ? places[seat] : k + 1;
				PvpDraw.medal(g, place, x + 4, y + (ph - 16) / 2);
				Component pts = Texts.number(seat < points.length ? points[seat] : 0);
				int pw = font.width(pts);
				int nameW = 128 - 20 - pw - 10 - (p.bot() ? 24 : 0);
				Kit.fit(g, font, p.plateName(), x + 20, y + ph / 2 - 4, nameW, win ? Kit.GOLD : Kit.BONE, true);
				if (p.bot() && !p.level().isEmpty()) {
					int nw = Math.min(nameW, font.width(p.plateName()));
					Kit.sprite(g, Kit.pvp("bot_" + p.level()), x + 20 + nw + 3, y + ph / 2 - 5, 22, 11);
				}
				g.text(font, pts, x + 122 - pw, y + ph / 2 - 4, Kit.BONE_SHADE, true);
			}
			g.pose().popMatrix();
		}
		// your line: the payout rolls up
		int iy = top + 28 + n * pitch + 4;
		int ih = 62;
		Scene.inset(g, left + SX - 4, iy, 128, ih);
		long payout = myPayout();
		long you = num(s, "you", -1);
		int tx = left + SX;
		if (you >= 0) {
			if (payout > 0) {
				Kit.fit(g, font, Component.translatable(winners().length > 1 ? "gui.burmaldaholic.pvp.result.split_short" : "gui.burmaldaholic.pvp.result.you_take"),
					tx, iy + 5, 120, TEXT, true);
				double roll = still ? 1 : Ease.OUT_CUBIC.apply(Math.min(1, Math.max(0, (t - BANNER_AT) / ROLL_MS)));
				long shown = t >= BANNER_AT + ROLL_MS ? payout : Math.round(payout * roll);
				Kit.bigLeft(g, font, Texts.raw("+").append(Texts.number(shown)), tx + 1, iy + 17, 2, Kit.BONUS, Kit.INK); // literal-ok: sign
			} else {
				JsonObject me = participant(s, you);
				Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.pvp.result.you_lose", Texts.chips(me == null ? 0 : num(me, "stake", 0))), tx,
					iy + 5, 120, MUTED);
			}
		}
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.pvp.result.pot_short", Texts.number(num(s, "pot", 0)), Texts.number(num(s, "rake", 0))),
			tx, iy + 39, 120, MUTED, true);
		Component rm = rematchLine();
		if (rm != null) Kit.fit(g, font, rm, tx, iy + 50, 120, GOLD, true);
		Scene.chipCounter(g, font, Texts.number(num(s, "balance", 0)), left + Scene.W - 90, top + 212, false);
	}

	private void confetti(GuiGraphicsExtractor g, double ms) {
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.hash(matchId()));
		for (int k = 0; k < 34; k++) {
			double x0 = rng.nextDouble() * 250;
			double speed = 0.02 + rng.nextDouble() * 0.03;
			double phase = rng.nextDouble() * 1000;
			int f = rng.nextInt(3);
			double y = ((ms * speed + phase * speed * 4) % 150) + 30;
			double x = x0 + 6 * Math.sin((ms + phase) / 300.0);
			double fade = ms > 6000 ? Math.max(0, 1 - (ms - 6000) / 1000) : 1;
			if (fade <= 0) return;
			Kit.region(g, CONFETTI, 24, 8, f * 8, 0, 8, 8, left + 16 + (int) x, top + (int) y, 8, 8, Kit.fade(fade));
		}
	}
}
