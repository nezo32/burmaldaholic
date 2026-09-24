package dev.nezo.burmaldaholic.games.extras.client.pvp.plinko;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeScreen;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeView;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattle;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * Plinko Battle match screen (PVP.md §7.4), 400 × 240: your board in the centre (the ball follows the
 * revealed path, one row every 4 ticks, a peg sound per row), the ranking on the left (name, total, last
 * bin), the other players' last ball as bin strips on the right. Top bar "PLINKO BATTLE · High · Ball 2/3 ·
 * Pot 600". The final ball shows 11 rows; its last row plays at half speed when the result arrives.
 */
public final class PlinkoBattleScreen extends PvpModeScreen {
	private static final int BOARD_W = 180;
	private static final int BIN_W = 13;
	private static final int ROW_H = 10;
	private static final int SLOW_ROW_TICKS = 8;

	private int lastRowSound = -1;
	private @Nullable Component banner;
	private int bannerUntil;

	public PlinkoBattleScreen(JsonObject first) {
		super(Component.translatable("gui.burmaldaholic.pvp.plinko.title"), "gui.burmaldaholic.pvp.plinko.drop_now",
			List.of("gui.burmaldaholic.pvp.plinko.rules.1", "gui.burmaldaholic.pvp.plinko.rules.2", "gui.burmaldaholic.pvp.plinko.tiebreak"), first);
	}

	private Plinko.@Nullable Risk risk() {
		return Plinko.Risk.parse(PvpModeView.str(view.params(), "risk", ""));
	}

	private int balls() {
		return view.params().has("balls") ? view.params().get("balls").getAsInt() : 1;
	}

	private long[] row() {
		PvpModeView.StepView w = view.lastOf("ball_wait");
		if (w != null && w.data().has("row")) {
			return PvpModeView.longs(w.data(), "row");
		}
		Plinko.@Nullable Risk r = risk();
		return PlinkoBattle.pointsRow((r == null ? Plinko.Risk.LOW : r).defaults());
	}

	@Override
	protected List<Component> topBar() {
		List<Component> parts = new ArrayList<>();
		parts.add(Component.translatable("gui.burmaldaholic.pvp.plinko.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		Plinko.@Nullable Risk r = risk();
		if (r != null) {
			parts.add(Component.translatable(r.key()));
		}
		PvpModeView.StepView s = view.last();
		if (s != null && s.data().has("final") && s.data().get("final").getAsBoolean()) {
			parts.add(Component.translatable("gui.burmaldaholic.pvp.plinko.final_ball").withStyle(ChatFormatting.RED));
		} else {
			int ball = s == null ? 1 : s.round() + 1;
			parts.add(Component.translatable("gui.burmaldaholic.pvp.plinko.round", Texts.number(ball), Texts.number(balls())));
		}
		parts.add(Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(view.pot())));
		return parts;
	}

	// ---- step data ----

	/** Totals after the latest scored ball (or the final ones). */
	private long[] totals() {
		if (view.settled()) {
			return view.outcomePoints();
		}
		PvpModeView.StepView s = view.lastOf("ball_score");
		return s == null ? new long[view.size()] : PvpModeView.longs(s.data(), "totals");
	}

	private int[] standings() {
		if (view.settled()) {
			return view.rankOrder();
		}
		PvpModeView.StepView s = view.lastOf("ball_score");
		if (s != null) {
			return PvpModeView.ints(s.data(), "standings");
		}
		int[] o = new int[view.size()];
		for (int i = 0; i < o.length; i++) {
			o[i] = i;
		}
		return o;
	}

	/** Landed bin and points of each seat's latest landed ball (-1 = none yet). */
	private long[][] lastLanded() {
		int n = view.size();
		long[][] out = new long[n][];
		if (view.settled()) {
			for (JsonObject e : view.events("final_ball")) {
				int seat = PvpModeView.seat(e);
				if (seat >= 0 && seat < n && finalLanded()) {
					out[seat] = new long[] {PvpModeView.eventLong(e, "bin", -1), PvpModeView.eventLong(e, "points", 0)};
				}
			}
		}
		for (int i = view.steps().size() - 1; i >= 0; i--) {
			PvpModeView.StepView s = view.steps().get(i);
			if (s.kind().equals("drop") && s.data().has("bins") && ticks - s.arrived() >= PlinkoBattleMode.DROP_TICKS) {
				long[] bins = PvpModeView.longs(s.data(), "bins");
				long[] pts = PvpModeView.longs(s.data(), "points");
				for (int k = 0; k < n && k < bins.length; k++) {
					if (out[k] == null) {
						out[k] = new long[] {bins[k], k < pts.length ? pts[k] : 0};
					}
				}
				break;
			}
		}
		return out;
	}

	private boolean finalLanded() {
		return view.settled() && ticks - view.outcomeArrived() >= SLOW_ROW_TICKS;
	}

	/** Own ball: mask and fractional rows done, or null when no ball is in flight / shown. */
	private double @Nullable [] ownBall() {
		int you = view.you() < 0 ? 0 : view.you();
		PvpModeView.StepView d = view.lastOf("drop");
		if (d == null) {
			return null;
		}
		boolean fin = d.data().has("final") && d.data().get("final").getAsBoolean();
		if (fin && view.settled()) {
			for (JsonObject e : view.events("final_ball")) {
				if (PvpModeView.seat(e) == you) {
					double t = PlinkoBattleMode.FINAL_ROWS_SHOWN + Math.min(1.0, (ticks - view.outcomeArrived() + partial) / SLOW_ROW_TICKS);
					return new double[] {PvpModeView.eventLong(e, "mask", 0), t};
				}
			}
		}
		int[] masks = PvpModeView.ints(d.data(), "masks");
		if (you >= masks.length) {
			return null;
		}
		int rows = d.data().has("rows") ? d.data().get("rows").getAsInt() : PlinkoBattle.ROWS;
		double t = Math.min(rows, (ticks - d.arrived() + partial) / PlinkoBattleMode.ROW_TICKS);
		return new double[] {masks[you], t};
	}

	@Override
	protected void onStep(PvpModeView.StepView step) {
		switch (step.kind()) {
			case "drop" -> lastRowSound = -1;
			case "ball_score" -> {
				PvpModeView.StepView d = view.lastOf("drop");
				int[] edges = d == null ? new int[0] : PvpModeView.ints(d.data(), "edges");
				if (edges.length > 0) {
					long[] bins = PvpModeView.longs(d.data(), "bins");
					int seat = edges[0];
					showBanner(Component.translatable("msg.burmaldaholic.pvp.plinko.edge", view.name(seat), multiplier(row()[(int) bins[seat]])));
					play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
				}
			}
			case "underdog" -> {
				int[] seats = PvpModeView.ints(step.data(), "seats");
				if (seats.length > 0) {
					showBanner(Component.translatable("msg.burmaldaholic.pvp.plinko.underdog", view.name(seats[0])));
					play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void onSettled() {
		lastRowSound = PlinkoBattleMode.FINAL_ROWS_SHOWN - 1;
		for (JsonObject e : view.events("edge")) {
			if (e.has("round") && e.get("round").getAsInt() == balls() - 1) {
				int seat = PvpModeView.seat(e);
				showBanner(Component.translatable("msg.burmaldaholic.pvp.plinko.edge", view.name(seat),
					multiplier(PvpModeView.eventLong(e, "points", 0))));
				play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
				break;
			}
		}
	}

	private void showBanner(Component c) {
		banner = c;
		bannerUntil = ticks + 60;
	}

	/** Points → the multiplier text of the bin ("8.1"): points = round(multiplier × 10). */
	private static Component multiplier(long points) {
		return Texts.decimal(Payouts.formatMultiplier(points / 10.0));
	}

	@Override
	protected void onTick() {
		double[] ball = ownBall();
		if (ball != null) {
			int row = (int) Math.floor(ball[1]);
			if (row != lastRowSound && row < PlinkoBattle.ROWS && row >= 0 && ball[1] > 0) {
				lastRowSound = row;
				play(ExtrasModule.PLINKO_PEG_SOUND, 0.9f + 0.05f * row, 0.4f);
			}
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int boardX = left + 110;
		int boardY = top + 22;
		long[] row = row();
		int binsLeft = boardX + (BOARD_W - PlinkoBattle.BINS * BIN_W) / 2;
		int center = binsLeft + PlinkoBattle.BINS * BIN_W / 2;
		// pegs
		for (int r = 0; r < PlinkoBattle.ROWS; r++) {
			for (int j = 0; j <= r; j++) {
				int x = (int) Math.round(center + (j - r / 2.0) * BIN_W);
				int y = boardY + 4 + r * ROW_H;
				g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFE8E8E8);
			}
		}
		int binsY = boardY + 4 + PlinkoBattle.ROWS * ROW_H + 2;
		double[] ball = ownBall();
		int you = view.you() < 0 ? 0 : view.you();
		long[][] landed = lastLanded();
		int lit = ball != null && ball[1] >= PlinkoBattle.ROWS ? PlinkoBattle.bin((int) ball[0]) : -1;
		for (int b = 0; b < PlinkoBattle.BINS && b < row.length; b++) {
			int x = binsLeft + b * BIN_W;
			long p = row[b];
			int color = p >= 100 ? 0xFFB8860B : p >= 20 ? 0xFF2E7D32 : p >= 10 ? 0xFF3D5A80 : 0xFF8E2A2A;
			g.fill(x + 1, binsY, x + BIN_W - 1, binsY + 12, b == lit ? 0xFFFFFFFF : color);
			g.pose().pushMatrix();
			g.pose().translate(x + BIN_W / 2f, binsY + 3);
			g.pose().scale(0.5f, 0.5f);
			g.centeredText(font, Texts.number(p), 0, 0, b == lit ? 0xFF000000 : 0xFFFFFFFF);
			g.pose().popMatrix();
		}
		if (ball != null) {
			int mask = (int) ball[0];
			double t = ball[1];
			double bx;
			double by;
			if (t >= PlinkoBattle.ROWS) {
				bx = center + (PlinkoBattle.bin(mask) - PlinkoBattle.ROWS / 2.0) * BIN_W;
				by = binsY - 3;
			} else {
				int r = (int) Math.floor(t);
				double frac = t - r;
				double x0 = ballX(center, mask, r - 1);
				double x1 = ballX(center, mask, r);
				bx = x0 + (x1 - x0) * frac;
				by = boardY + 4 + (r - 1) * ROW_H + ROW_H * frac - 3 - Math.sin(Math.PI * frac) * 3;
			}
			int cx = (int) Math.round(bx);
			int cy = (int) Math.round(by);
			g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFF4040);
			g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0xFFFF4040);
		}
		// status under the board
		int statusY = binsY + 16;
		Component status = null;
		PvpModeView.StepView s = view.last();
		if (view.settled()) {
			int[] w = view.winners();
			status = w.length == 1 ? Component.translatable("gui.burmaldaholic.pvp.result.winner_title", view.name(w[0])).withStyle(ChatFormatting.GOLD)
				: Component.translatable("gui.burmaldaholic.pvp.result.dead_heat_title").withStyle(ChatFormatting.GOLD);
		} else if (s != null && s.kind().equals("ball_wait")) {
			status = Component.translatable("gui.burmaldaholic.pvp.plinko.auto_in", seconds(ticksLeft(s)));
		} else if (you < landed.length && landed[you] != null && s != null && s.kind().equals("ball_score")) {
			status = Component.translatable("gui.burmaldaholic.pvp.plinko.ball_result", multiplier(row[(int) landed[you][0]]),
				Texts.plural("unit.burmaldaholic.point", landed[you][1]));
		}
		if (status != null) {
			g.centeredText(font, status, boardX + BOARD_W / 2, statusY, MUTED);
		}
		if (banner != null && ticks < bannerUntil) {
			g.centeredText(font, banner, left + W / 2, statusY + 12, GOLD);
		}
		// left column: ranking
		int lx = left + PAD;
		int ly = top + 22;
		g.text(font, Component.translatable("gui.burmaldaholic.pvp.match.standings").withStyle(ChatFormatting.UNDERLINE), lx, ly, GOLD, true);
		ly += 12;
		long[] totals = totals();
		int[] order = standings();
		for (int k = 0; k < order.length && k < 6; k++) {
			int seat = order[k];
			if (seat < 0 || seat >= view.size()) {
				continue;
			}
			g.text(font, fit(row(k + 1, seat, seat < totals.length ? totals[seat] : 0), 100), lx, ly, TEXT, true);
			ly += 10;
			if (seat < landed.length && landed[seat] != null && landed[seat][0] >= 0 && landed[seat][0] < row.length) {
				Component last = Component.translatable("gui.burmaldaholic.pvp.plinko.ball_result", multiplier(row[(int) landed[seat][0]]),
					Texts.plural("unit.burmaldaholic.point", landed[seat][1]));
				float scale = Math.min(1f, 96f / Math.max(1, font.width(last)));
				g.pose().pushMatrix();
				g.pose().translate(lx + 4, ly);
				g.pose().scale(scale, scale);
				g.text(font, last, 0, 0, MUTED, true);
				g.pose().popMatrix();
			}
			ly += 12;
		}
		// right column: the others' last ball
		int rx = left + 296;
		int ry = top + 22;
		for (int seat = 0; seat < view.size(); seat++) {
			if (seat == view.you()) {
				continue;
			}
			g.text(font, fit(nameTag(seat), 96), rx, ry, TEXT, true);
			ry += 10;
			int litBin = seat < landed.length && landed[seat] != null ? (int) landed[seat][0] : -1;
			for (int b = 0; b < PlinkoBattle.BINS; b++) {
				int x = rx + b * 7;
				g.fill(x, ry, x + 6, ry + 6, b == litBin ? (PlinkoBattle.edge(b) ? 0xFFFFD700 : 0xFFFFFFFF) : 0xFF304030);
			}
			ry += 12;
		}
	}

	/** Ball x after {@code row} rows (row −1 = start, centred). */
	private static double ballX(int center, int mask, int row) {
		int rights = 0;
		for (int i = 0; i <= row && i < PlinkoBattle.ROWS; i++) {
			if ((mask >> i & 1) == 1) {
				rights++;
			}
		}
		return center + (rights - (row + 1) / 2.0) * BIN_W;
	}
}
