package dev.nezo.burmaldaholic.games.extras.client.pvp.plinko;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.kit.Faces;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.client.PlinkoDraw;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeScreen;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeView;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattle;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Plinko Battle (PVP.md §7.4; visual/extras.md §5.3, extras-pvp.md §6.2): your board at the solo size (no scaling)
 * with the bins labelled in points, and the other players' 56² mini boards on the right (leaders first) with their
 * name and total. Every DROP step drops all balls together along the paths the server sent with that step; your pegs
 * flash and sound, the others' landings plink softly. UNDERDOG turns the next ball gold; an EDGE bin erupts with the
 * edge glow and banner. The final ball: every cap is foil, all balls drop through row 11 and hang at the last peg; at
 * each player's Final Reveal cue their ball drops into its bin and the foil bursts off. Controls sit in the board's
 * upper corners (the board keeps its size).
 */
public final class PlinkoBattleScreen extends PvpModeScreen {
	private static final int BX = 14;
	private static final int BY = 28;
	private static final int MX = 300;
	private static final int ROW_MS = PlinkoBattleMode.ROW_TICKS * 50;

	private int lastRowSound = -1;
	private long bannerAt = -1;
	private @Nullable Component banner;
	private long edgeAt = -1;
	private final java.util.Map<Integer, Long> finalDropAt = new java.util.HashMap<>();
	private final java.util.Map<Integer, Long> stepAt = new java.util.HashMap<>();

	public PlinkoBattleScreen(JsonObject first) {
		super(Component.translatable("gui.burmaldaholic.pvp.plinko.title"), "gui.burmaldaholic.pvp.plinko.drop_now",
			List.of("gui.burmaldaholic.pvp.plinko.rules.1", "gui.burmaldaholic.pvp.plinko.rules.2", "gui.burmaldaholic.pvp.plinko.tiebreak"), first);
	}

	@Override
	protected boolean sideControls() {
		return true;
	}

	@Override
	protected int[][] sideControlPositions() {
		return new int[][] {{300, 150, 88}, {300, 174, 43}, {345, 174, 43}, {300, 198, 20}};
	}

	private Plinko.@Nullable Risk risk() {
		return Plinko.Risk.parse(PvpModeView.str(view.params(), "risk", ""));
	}

	private int balls() {
		return view.params().has("balls") ? view.params().get("balls").getAsInt() : 1;
	}

	private long[] row() {
		PvpModeView.StepView w = view.lastOf("ball_wait");
		if (w != null && w.data().has("row")) return PvpModeView.longs(w.data(), "row");
		Plinko.@Nullable Risk r = risk();
		return PlinkoBattle.pointsRow((r == null ? Plinko.Risk.LOW : r).defaults());
	}

	private boolean finalBall() {
		PvpModeView.StepView s = view.lastOf("ball_wait");
		return s != null && s.data().has("final") && s.data().get("final").getAsBoolean();
	}

	@Override
	protected List<Component> topBar() {
		List<Component> parts = new ArrayList<>();
		Plinko.@Nullable Risk r = risk();
		Component title = Component.translatable("gui.burmaldaholic.pvp.plinko.title");
		parts.add(r == null ? title : Component.translatable("gui.burmaldaholic.pvp.plinko.title_risk", title, Component.translatable(r.key())));
		PvpModeView.StepView s = view.last();
		parts.add(finalBall() ? Component.translatable("gui.burmaldaholic.pvp.plinko.final_ball")
			: Component.translatable("gui.burmaldaholic.pvp.plinko.round", Texts.number(s == null ? 1 : s.round() + 1), Texts.number(balls())));
		return parts;
	}

	/** Totals after the latest scored ball (or the final ones). */
	private long[] totals() {
		if (view.settled()) return view.outcomePoints();
		PvpModeView.StepView s = view.lastOf("ball_score");
		long[] t = s == null ? new long[view.size()] : PvpModeView.longs(s.data(), "totals");
		for (int i = 0; i < t.length; i++) {
			JsonObject p = view.placing(i);
			if (p != null) t[i] = p.get("points").getAsLong();
		}
		return t;
	}

	private int[] standings() {
		if (view.settled()) return view.rankOrder();
		PvpModeView.StepView s = view.lastOf("ball_score");
		if (s != null) return PvpModeView.ints(s.data(), "standings");
		int[] o = new int[view.size()];
		for (int i = 0; i < o.length; i++) o[i] = i;
		return o;
	}

	@Override
	protected void onStep(PvpModeView.StepView step) {
		long now = Util.getMillis();
		stepAt.put(view.steps().size() - 1, now);
		switch (step.kind()) {
			case "drop" -> lastRowSound = -1;
			case "ball_wait" -> {
				if (step.data().has("final") && step.data().get("final").getAsBoolean()) {
					raise(Component.translatable("gui.burmaldaholic.pvp.plinko.final_ball"));
				}
			}
			case "ball_score" -> {
				PvpModeView.StepView d = view.lastOf("drop");
				int[] edges = d == null ? new int[0] : PvpModeView.ints(d.data(), "edges");
				if (edges.length > 0) {
					raise(Component.translatable("gui.burmaldaholic.anim.plinko.edge_banner"));
					edgeAt = now;
					Kit.vanilla("entity.firework_rocket.twinkle", 1f, 1f);
				}
			}
			case "underdog" -> {
				if (PvpModeView.ints(step.data(), "seats").length > 0) {
					raise(Component.translatable("gui.burmaldaholic.pvp.wheel.underdog_tag"));
					Kit.vanilla("item.firecharge.use", 0.5f, 1f);
				}
			}
			default -> {
			}
		}
	}

	private void raise(Component c) {
		banner = c;
		bannerAt = Util.getMillis();
	}

	/** Ms since the step arrived (local clock of the shared step). */
	private double sinceStep(PvpModeView.StepView s) {
		int idx = view.steps().indexOf(s);
		Long at = stepAt.get(idx);
		return at == null ? 1e9 : Util.getMillis() - at;
	}

	/** Underdog seats of the current ball (gold ball). */
	private boolean underdog(int seat) {
		PvpModeView.StepView u = view.lastOf("underdog");
		if (u == null) return false;
		for (int s : PvpModeView.ints(u.data(), "seats")) if (s == seat) return true;
		return false;
	}

	/** Ball of a seat: {mask, rowsDone, landedBin (−1)} for the current drop, or null. */
	private double @Nullable [] ball(int seat) {
		PvpModeView.StepView d = view.lastOf("drop");
		if (d == null || seat < 0) return null;
		int[] masks = PvpModeView.ints(d.data(), "masks");
		if (seat >= masks.length) return null;
		boolean fin = d.data().has("final") && d.data().get("final").getAsBoolean();
		double ms = sinceStep(d);
		int rows = d.data().has("rows") ? d.data().get("rows").getAsInt() : PlinkoBattle.ROWS;
		if (fin) {
			JsonObject e = view.finalEvent("final_ball", seat);
			if (e != null) {
				long at = finalDropAt.computeIfAbsent(seat, k -> Util.getMillis());
				double t = PlinkoBattleMode.FINAL_ROWS_SHOWN + Math.min(1, (Util.getMillis() - at) / (double) (2 * ROW_MS));
				int mask = (int) PvpModeView.eventLong(e, "mask", masks[seat]);
				return new double[] {mask, t, t >= PlinkoBattle.ROWS ? PvpModeView.eventLong(e, "bin", -1) : -1};
			}
			return new double[] {masks[seat], Math.min(rows, ms / ROW_MS), -1};
		}
		double t = Math.min(PlinkoBattle.ROWS, ms / ROW_MS);
		int[] bins = PvpModeView.ints(d.data(), "bins");
		return new double[] {masks[seat], t, t >= PlinkoBattle.ROWS && seat < bins.length ? bins[seat] : -1};
	}

	@Override
	protected void onTick() {
		int you = Math.max(0, view.you());
		double[] b = ball(you);
		if (b == null) return;
		int row = (int) Math.floor(b[1]);
		if (row != lastRowSound && row >= 0 && row < PlinkoBattle.ROWS && b[1] > 0) {
			lastRowSound = row;
			Kit.mod(ExtrasModule.PLINKO_PEG_SOUND, 0.5f, PlinkoAnim.pegPitch((int) b[0], row));
			if (row == PlinkoBattle.ROWS - 1) FxSounds.play("plinko_bin", 0.8f, 1f);
		}
	}

	@Override
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int bx = left + BX;
		int by = top + BY;
		int you = Math.max(0, view.you());
		long[] row = row();
		double[] mults = new double[row.length];
		for (int i = 0; i < row.length; i++) mults[i] = row[i] / 10.0;
		PlinkoDraw.board(g, bx, by);
		double[] b = ball(you);
		PvpModeView.StepView d = view.lastOf("drop");
		double ms = d == null ? -1 : sinceStep(d);
		boolean rm = Kit.reduceMotion();
		int path = b == null ? 0 : (int) b[0];
		PlinkoDraw.pegs(g, bx, by, b != null && b[1] < PlinkoBattle.ROWS && !rm && Kit.flashes()
			? (r, j) -> PlinkoAnim.pegState(path, ROW_MS, ms + PlinkoAnim.RELEASE_MS, r, j) : null);
		PlinkoDraw.chute(g, bx, by, b != null && b[1] < 1);
		boolean hidden = finalBall() && (b == null || b[2] < 0);
		int lit = b == null ? -1 : (int) b[2];
		// your ball: rows at 200 ms (the step's pace), hanging at the last peg until the final cue
		if (b != null && b[1] < PlinkoBattle.ROWS) {
			double t = b[1];
			double bob = finalBall() && t >= PlinkoBattleMode.FINAL_ROWS_SHOWN && !rm ? Math.sin(Util.getMillis() / 150.0) : 0;
			PlinkoAnim.Ball ball = PlinkoAnim.sample(path, ROW_MS, PlinkoAnim.RELEASE_MS + t * ROW_MS);
			PlinkoDraw.ball(g, bx, by, ball.x(), ball.y() + bob, ball.roll(), ball.sx(), ball.sy(), underdog(you), 1);
		}
		// bins in points (foil during the final ball until your cue)
		for (int k = 0; k < 13 && k < row.length; k++) {
			int x = bx + (int) Math.round(PlinkoAnim.binX(k)) - 9;
			int y = by + PlinkoAnim.CAP_Y;
			if (hidden) {
				Kit.sprite(g, Kit.extras("plinko_bin_hidden"), x, y, 18, 14);
				continue;
			}
			int t = PlinkoAnim.tier(mults[k]);
			Kit.region(g, PlinkoDraw.BINS, 90, 28, t * 18, k == lit ? 14 : 0, 18, 14, x, y);
			Kit.centeredFit(g, font, Texts.number(row[k]), x + 9, y + 3, 16, k == lit ? Kit.WHITE : Kit.BONE, false);
		}
		if (edgeAt >= 0 && !rm && Kit.flashes()) {
			double since = Util.getMillis() - edgeAt;
			if (since < 1200) Kit.sprite(g, Kit.extras("edge_glow"), bx, by, PlinkoDraw.BOARD_W, PlinkoDraw.BOARD_H, Kit.fade(0.6 * (1 - since / 1200)));
		}
		// the others' mini boards (leaders first), with name and total
		int[] order = standings();
		long[] totals = totals();
		List<PvpSeat> seats = view.plateSeats();
		int shown = 0;
		for (int seat : order) {
			if (seat == view.you() || shown >= 2) continue;
			PvpSeat s = seats.stream().filter(p -> p.index() == seat).findFirst().orElse(null);
			if (s == null) continue;
			int y = top + 28 + shown * 58;
			int mx = left + MX;
			double[] ob = ball(seat);
			boolean edge = ob != null && ob[2] >= 0 && PlinkoBattle.edge((int) ob[2]);
			PlinkoDraw.mini(g, mx, y, ob == null ? 0 : (int) ob[0], ob == null ? -1 : ob[1], ob == null ? -1 : (int) ob[2], underdog(seat), edge);
			Faces.draw(g, s.key(), s.bot(), s.name(), mx + 58, y, 12);
			Kit.fit(g, font, s.plateName(), mx + 58, y + 14, 30, Kit.BONE, true);
			Kit.fit(g, font, Texts.number(seat < totals.length ? totals[seat] : 0), mx + 58, y + 26, 30, Kit.GOLD, true);
			if (s.pressed()) PvpDraw.readyTick(g, mx + 70, y - 2);
			JsonObject placing = view.placing(seat);
			if (placing != null) PvpDraw.medal(g, placing.get("place").getAsInt(), mx + 72, y + 38);
			shown++;
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int you = Math.max(0, view.you());
		long[] totals = totals();
		// your total on a small plaque over the board
		Component mine = Component.translatable("gui.burmaldaholic.pvp.plinko.your_total", Texts.number(you < totals.length ? totals[you] : 0));
		int w = font.width(mine) + 16;
		Kit.sprite(g, Kit.pvp("pot_plaque"), left + BX + 8, top + BY + 6, w, 18);
		g.text(font, mine, left + BX + 16, top + BY + 11, Kit.GOLD, true);
		PvpModeView.StepView s = view.last();
		if (s != null && s.kind().equals("ball_wait") && !view.settled()) {
			Kit.centeredFit(g, font, Component.translatable("gui.burmaldaholic.pvp.plinko.auto_in", seconds(ticksLeft(s))), left + BX + 136, top + BY + 20,
				120, MUTED, true);
		}
		if (banner != null) {
			PvpDraw.modeBanner(g, font, banner, left + BX + 136, top + 70, 260, Util.getMillis() - bannerAt);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	/** Points → the multiplier text of the bin ("8.1"): points = round(multiplier × 10). */
	static Component multiplier(long points) {
		return Texts.decimal(Payouts.formatMultiplier(points / 10.0));
	}
}
