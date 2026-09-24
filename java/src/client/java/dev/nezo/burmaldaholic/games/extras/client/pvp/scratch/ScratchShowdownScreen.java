package dev.nezo.burmaldaholic.games.extras.client.pvp.scratch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.GuiParticlePool;
import dev.nezo.burmaldaholic.client.pvp.kit.Faces;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.PlateRow;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.client.ScratchDraw;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeScreen;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeView;
import dev.nezo.burmaldaholic.games.extras.logic.anim.ScratchMask;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard.Sym;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Scratch Showdown (PVP.md §8.5; visual/extras.md §6.5, mockup {@code extras_pvp_match.png}; extras-pvp.md §8.2): the
 * arena with a plate and a holo Showdown card per player (you first), the pot, the step counter, taunt bubbles and the
 * held grudge banner. Each revealed cell auto-swipes on every card, rippling 60 ms apart in seat order, with silver
 * flakes; the symbols settle; trios get gold frames and a line; a creeper swells, sends a spark along an arc to the
 * cell the server burned, which explodes and chars (the card shakes, the score counts down); a Rabbit's Foot runs a gold
 * light round the card and stamps its ×2 / ×4. The current cell shimmers fast inside a gold frame while the server
 * waits for Scratch!. Cell 9 stays gold "?" foil until that player's Final Reveal cue swipes it (then the card dims
 * with its medal); the winner's card gets the gold border. 4–6 players: two rows of cropped cards, the controls stay.
 */
public final class ScratchShowdownScreen extends PvpModeScreen {
	private static final Identifier CARD = Kit.sheet("extras/scratch_card_showdown");
	private static final int CELL = 24;
	private static final int PITCH = 26;
	private static final int RIPPLE_MS = 60;
	private static final int CARD_W = 86;
	private static final int CARD_H = 98;

	private final GuiParticlePool flakes = new GuiParticlePool();
	/** Per seat, per cell: foil masks and when their swipe started (local ms, −1 none). */
	private ScratchMask[][] masks = new ScratchMask[0][];
	private long[][] swipeAt = new long[0][];
	/** When a cell's symbol appeared (settle scale). */
	private long[][] shownAt = new long[0][];
	private final List<long[]> burns = new ArrayList<>();
	private final List<long[]> feet = new ArrayList<>();
	private final List<long[]> fizzles = new ArrayList<>();
	private long[] scoreFrom = new long[0];
	private long[] scoreTo = new long[0];
	private long[] scoreAt = new long[0];
	private long lastFrame;

	public ScratchShowdownScreen(JsonObject first) {
		super(Component.translatable("gui.burmaldaholic.pvp.scratch.title"), "gui.burmaldaholic.pvp.scratch.scratch_now",
			List.of("gui.burmaldaholic.pvp.scratch.rules.1", "gui.burmaldaholic.pvp.scratch.rules.2", "gui.burmaldaholic.pvp.scratch.rules.3",
				"gui.burmaldaholic.pvp.scratch.rules.4", "gui.burmaldaholic.pvp.scratch.tiebreak"), first);
		ensure();
		// opened late: everything already revealed shows at rest
		Board[] b = boards();
		for (int i = 0; i < b.length; i++) {
			for (int c = 0; c < ShowdownCard.CELLS; c++) {
				if (b[i].cells()[c] >= 0) masks[i][c].clear();
			}
			scoreFrom[i] = scoreTo[i] = b[i].score();
		}
	}

	private void ensure() {
		int n = view.size();
		if (masks.length == n) return;
		masks = new ScratchMask[n][ShowdownCard.CELLS];
		swipeAt = new long[n][ShowdownCard.CELLS];
		shownAt = new long[n][ShowdownCard.CELLS];
		for (int i = 0; i < n; i++) {
			for (int c = 0; c < ShowdownCard.CELLS; c++) {
				masks[i][c] = ScratchMask.showdown();
				swipeAt[i][c] = -1;
				shownAt[i][c] = 0;
			}
		}
		scoreFrom = new long[n];
		scoreTo = new long[n];
		scoreAt = new long[n];
	}

	/** Cells revealed so far (0…9). */
	private int revealed() {
		if (view.settled()) return ShowdownCard.CELLS;
		PvpModeView.StepView s = view.lastOf("cell");
		return s == null ? 0 : s.round() + 1;
	}

	@Override
	protected List<Component> topBar() {
		PvpModeView.StepView s = view.last();
		int cell = s == null ? 1 : Math.min(ShowdownCard.CELLS, s.round() + 1);
		return List.of(Component.translatable("gui.burmaldaholic.pvp.scratch.title"),
			Component.translatable("gui.burmaldaholic.pvp.match.step_of", Texts.number(cell), Texts.number(ShowdownCard.CELLS)));
	}

	/** Board per seat: symbol per cell (−1 = unscratched), burned flags, score, multiplier. */
	private record Board(int[] cells, boolean[] burned, long score, int mult) {}

	private Board[] boards() {
		int n = view.size();
		Board[] out = new Board[n];
		int[][] cells = new int[n][ShowdownCard.CELLS];
		boolean[][] burned = new boolean[n][ShowdownCard.CELLS];
		long[] scores = new long[n];
		int[] mult = new int[n];
		for (int[] c : cells) Arrays.fill(c, -1);
		Arrays.fill(mult, 1);
		for (PvpModeView.StepView s : view.steps()) {
			if (!s.kind().equals("cell")) continue;
			int c = s.round();
			int[] symbols = PvpModeView.ints(s.data(), "symbols");
			long[] sc = PvpModeView.longs(s.data(), "scores");
			int[] m = PvpModeView.ints(s.data(), "mult");
			for (int i = 0; i < n; i++) {
				if (c >= 0 && c < ShowdownCard.CELLS && i < symbols.length) cells[i][c] = symbols[i];
				if (i < sc.length) scores[i] = sc[i];
				if (i < m.length) mult[i] = m[i];
			}
			if (s.data().has("burns")) {
				for (JsonElement e : s.data().getAsJsonArray("burns")) {
					JsonObject b = e.getAsJsonObject();
					int seat = PvpModeView.seat(b);
					int cell = (int) PvpModeView.eventLong(b, "cell", -1);
					if (seat >= 0 && seat < n && cell >= 0 && cell < ShowdownCard.CELLS) burned[seat][cell] = true;
				}
			}
		}
		// the final cell: from the seat's Final Reveal cue (or the settled outcome)
		for (int i = 0; i < n; i++) {
			JsonObject e = view.finalEvent("final_cell", i);
			if (e != null) {
				cells[i][ShowdownCard.CELLS - 1] = (int) PvpModeView.eventLong(e, "symbol", -1);
				mult[i] = (int) PvpModeView.eventLong(e, "mult", mult[i]);
				scores[i] = PvpModeView.eventLong(e, "score", scores[i]);
			}
		}
		if (view.settled()) {
			for (JsonObject e : view.events("creeper")) {
				int seat = PvpModeView.seat(e);
				int cell = (int) PvpModeView.eventLong(e, "cell", -1);
				if (seat >= 0 && seat < n && cell >= 0 && cell < ShowdownCard.CELLS) burned[seat][cell] = true;
			}
			long[] pts = view.outcomePoints();
			for (int i = 0; i < n && i < pts.length; i++) scores[i] = pts[i];
		}
		for (int i = 0; i < n; i++) out[i] = new Board(cells[i], burned[i], scores[i], mult[i]);
		return out;
	}

	@Override
	protected void onStep(PvpModeView.StepView step) {
		ensure();
		long now = Util.getMillis();
		if (step.kind().equals("cell")) {
			int c = step.round();
			List<PvpSeat> order = PlateRow.ordered(view.plateSeats());
			for (int k = 0; k < order.size(); k++) {
				int seat = order.get(k).index();
				if (seat < masks.length && c >= 0 && c < ShowdownCard.CELLS) swipeAt[seat][c] = now + (long) k * RIPPLE_MS;
			}
			FxSounds.play("scratch", 0.8f, 1f);
			Board[] b = boards();
			for (int i = 0; i < b.length && i < scoreTo.length; i++) retarget(i, b[i].score(), now + 600);
		} else if (step.kind().equals("cell_events")) {
			JsonObject d = step.data();
			if (d.has("creepers")) {
				for (JsonElement e : d.getAsJsonArray("creepers")) {
					JsonObject o = e.getAsJsonObject();
					burns.add(new long[] {PvpModeView.seat(o), step.round(), PvpModeView.eventLong(o, "cell", -1), now});
				}
				if (!d.getAsJsonArray("creepers").isEmpty()) Kit.vanilla("entity.creeper.primed", 1f, 1f);
			}
			if (d.has("fizzles")) {
				for (JsonElement e : d.getAsJsonArray("fizzles")) fizzles.add(new long[] {PvpModeView.seat(e.getAsJsonObject()), step.round(), now});
				if (!d.getAsJsonArray("fizzles").isEmpty()) Kit.vanilla("block.fire.extinguish", 0.4f, 1f);
			}
			if (d.has("feet")) {
				for (JsonElement e : d.getAsJsonArray("feet")) {
					JsonObject o = e.getAsJsonObject();
					feet.add(new long[] {PvpModeView.seat(o), PvpModeView.eventLong(o, "mult", 2), now});
				}
				if (!d.getAsJsonArray("feet").isEmpty()) {
					Kit.vanilla("block.amethyst_block.chime", 1f, 1f);
					Kit.vanilla("entity.rabbit.jump", 1f, 1f);
				}
			}
		}
	}

	private void retarget(int seat, long to, long at) {
		long shown = shownScore(seat);
		scoreFrom[seat] = shown;
		scoreTo[seat] = to;
		scoreAt[seat] = at;
	}

	private long shownScore(int seat) {
		if (seat >= scoreTo.length) return 0;
		double p = Kit.reduceMotion() ? 1 : Math.min(1, Math.max(0, (Util.getMillis() - scoreAt[seat]) / 400.0));
		return Math.round(scoreFrom[seat] + (scoreTo[seat] - scoreFrom[seat]) * p);
	}

	@Override
	protected void onTick() {
		ensure();
		// a Final Reveal cue revealed a seat's cell 9: swipe it now
		long now = Util.getMillis();
		for (int i = 0; i < masks.length; i++) {
			JsonObject e = view.finalEvent("final_cell", i);
			int last = ShowdownCard.CELLS - 1;
			if (e != null && swipeAt[i][last] < 0 && !masks[i][last].isClear()) {
				swipeAt[i][last] = now;
				Board[] b = boards();
				if (i < b.length) retarget(i, b[i].score(), now + 300);
			}
		}
	}

	// ---- geometry ------------------------------------------------------------------------------------------------

	private boolean twoRows() {
		return view.size() > 3;
	}

	@Override
	protected boolean sideControls() {
		return twoRows();
	}

	/** Panel-local origin of the card at display position k. */
	private int[] cardAt(int k) {
		if (!twoRows()) return new int[] {PlateRow.plateX(k) + 12, 103};
		return new int[] {12 + (k % 3) * 94, (k / 3) == 0 ? 42 : 138};
	}

	private int cardH() {
		return twoRows() ? 86 : CARD_H;
	}

	private int cellX(int k, int c) {
		return left + cardAt(k)[0] + 6 + (c % 3) * PITCH;
	}

	private int cellY(int k, int c) {
		return top + cardAt(k)[1] + (twoRows() ? 4 : 16) + (c / 3) * PITCH;
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	private void advance() {
		long now = Util.getMillis();
		float dt = lastFrame == 0 ? 16 : Math.min(50, now - lastFrame);
		lastFrame = now;
		flakes.step(now, dt);
		boolean rm = Kit.reduceMotion();
		Board[] b = boards();
		for (int i = 0; i < masks.length; i++) {
			for (int c = 0; c < ShowdownCard.CELLS; c++) {
				ScratchMask m = masks[i][c];
				if (swipeAt[i][c] < 0 || now < swipeAt[i][c] || m.isClear()) continue;
				if (i < b.length && b[i].cells()[c] < 0) continue; // the value is not public yet
				double p = (now - swipeAt[i][c]) / (double) (rm ? 150 : ScratchMask.SWIPE_MS);
				if (rm || p >= 1) {
					m.clear();
					shownAt[i][c] = now;
				} else {
					double[] a = ScratchMask.swipePoint(CELL, CELL, Math.max(0, p - 0.1));
					double[] q = ScratchMask.swipePoint(CELL, CELL, p);
					if (m.eraseLine(a[0], a[1], q[0], q[1], ScratchMask.swipeRadius(CELL)) > 0 && !rm) {
						int k = PlateRow.position(view.plateSeats(), i);
						if (k >= 0) ScratchDraw.spray(flakes, cellX(k, c) + (float) q[0], cellY(k, c) + (float) q[1], 1, SeedMix.mix(i, c, (int) now), now, false);
					}
				}
			}
		}
	}

	@Override
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ensure();
		advance();
		long now = Util.getMillis();
		boolean rm = Kit.reduceMotion();
		List<PvpSeat> seats = view.plateSeats();
		List<PvpSeat> order = PlateRow.ordered(seats);
		Board[] boards = boards();
		int current = waiting() || (view.last() != null && "cell_wait".equals(view.last().kind())) ? revealed() : -1;
		int[] winners = view.winners();
		for (int k = 0; k < order.size() && k < 6; k++) {
			int seat = order.get(k).index();
			if (seat >= boards.length) continue;
			Board b = boards[seat];
			int[] at = cardAt(k);
			int cx = left + at[0];
			int cy = top + at[1];
			int shake = burnShake(seat, now);
			cx += shake;
			if (twoRows()) Kit.region(g, CARD, CARD_W, CARD_H, 0, 12, CARD_W, 86, cx, cy);
			else Kit.region(g, CARD, CARD_W, CARD_H, 0, 0, CARD_W, CARD_H, cx, cy);
			boolean won = contains(winners, seat);
			if (won) Kit.frameRect(g, cx - 1, cy - 1, CARD_W + 2, cardH() + 2, Kit.GOLD);
			for (int c = 0; c < ShowdownCard.CELLS; c++) {
				int x = cellX(k, c) + shake;
				int y = cellY(k, c);
				int sym = b.cells()[c];
				ScratchMask m = masks[seat][c];
				boolean finalCell = c == ShowdownCard.CELLS - 1;
				if (sym >= 0 && !m.untouched()) {
					if (b.burned()[c] && burnDone(seat, c, now)) {
						Kit.sprite(g, Kit.extras("charred"), x, y, CELL, CELL);
					} else {
						double settle = rm || shownAt[seat][c] == 0 ? 1 : Math.min(1, (now - shownAt[seat][c]) / 400.0);
						double sc = rm ? 1 : 1.2 - 0.2 * Ease.OUT_BACK.apply(settle);
						g.pose().pushMatrix();
						g.pose().translate(x + 12, y + 12);
						g.pose().scale((float) sc, (float) sc);
						ScratchDraw.symbol20(g, sym, -10, -10);
						g.pose().popMatrix();
					}
				}
				if (!m.isClear()) {
					if (finalCell && m.untouched()) {
						Kit.sprite(g, Kit.extras("foil_final"), x, y, CELL, CELL);
						Kit.big(g, font, Texts.raw("?").withStyle(net.minecraft.ChatFormatting.BOLD), x + 12, y + 8, 1, Kit.GOLD, Kit.INK); // literal-ok: symbol
					} else {
						ScratchDraw.foil(g, m, x, y, true, 0, ScratchDraw.ROW_SHOWDOWN, true);
					}
				}
				if (c == current && m.untouched() && !finalCell) {
					Kit.sprite(g, Kit.extras("scratch_foil_shimmer_small"), x, y, CELL, CELL);
					Kit.sprite(g, Kit.extras("trio_frame"), x - 2, y - 2, 28, 28);
				}
			}
			trios(g, k, b, shake);
			burnFx(g, seat, k, now);
			footFx(g, seat, cx, cy, now);
			// the final place medal once this seat was revealed
			JsonObject placing = view.placing(seat);
			if (placing != null) {
				if (!won) g.fill(cx, cy, cx + CARD_W, cy + cardH(), 0x4D100818);
				PvpDraw.medal(g, placing.get("place").getAsInt(), cx + CARD_W - 14, cy - 4);
			}
		}
		ScratchDraw.flakes(g, flakes, now);
	}

	private static boolean contains(int[] a, int v) {
		for (int x : a) if (x == v) return true;
		return false;
	}

	/** Gold frames around each trio (three surviving copies of a valued symbol) and the line through them. */
	private void trios(GuiGraphicsExtractor g, int k, Board b, int shake) {
		for (int s = 0; s < Sym.VALUED; s++) {
			int[] cells = new int[3];
			int n = 0;
			for (int c = 0; c < ShowdownCard.CELLS && n < 3; c++) {
				if (b.cells()[c] == s && !b.burned()[c] && masks.length > 0) cells[n++] = c;
			}
			if (n < 3) continue;
			for (int c : cells) Kit.sprite(g, Kit.extras("trio_frame"), cellX(k, c) - 1 + shake, cellY(k, c) - 1, 26, 26);
			for (int j = 0; j < 2; j++) {
				Kit.line(g, cellX(k, cells[j]) + 12 + shake, cellY(k, cells[j]) + 12, cellX(k, cells[j + 1]) + 12 + shake, cellY(k, cells[j + 1]) + 12, 1,
					Kit.GOLD);
			}
		}
	}

	private int burnShake(int seat, long now) {
		if (Kit.reduceMotion()) return 0;
		for (long[] b : burns) {
			if (b[0] != seat) continue;
			long t = now - b[3];
			if (t >= 650 && t < 800) return (int) Math.round(2 * Math.sin(t / 20.0));
		}
		return 0;
	}

	private boolean burnDone(int seat, int cell, long now) {
		for (long[] b : burns) {
			if (b[0] == seat && b[2] == cell) return now - b[3] >= 650;
		}
		return true;
	}

	/** Creeper → spark along an arc → explosion puff on the burned cell (extras-pvp §8.2). */
	private void burnFx(GuiGraphicsExtractor g, int seat, int k, long now) {
		boolean rm = Kit.reduceMotion();
		for (long[] b : burns) {
			if (b[0] != seat) continue;
			long t = now - b[3];
			if (t > 1000) continue;
			int from = (int) b[1];
			int to = (int) b[2];
			if (from < 0 || from >= ShowdownCard.CELLS || to < 0 || to >= ShowdownCard.CELLS) continue;
			double fx = cellX(k, from) + 12;
			double fy = cellY(k, from) + 12;
			if (t < 400) {
				double sw = rm ? 1 : 1 + 0.25 * Math.sin(Math.PI * t / 400.0);
				boolean flash = Kit.flashes() && !rm && (t / 100) % 2 == 0;
				int x = cellX(k, from);
				int y = cellY(k, from);
				int grow = (int) Math.round(12 * (sw - 1));
				g.fill(x - grow, y - grow, x + CELL + grow, y + CELL + grow, flash ? 0x60FFFFFF : 0x3040FF40);
			} else if (t < 650) {
				double p = (t - 400) / 250.0;
				double tx = cellX(k, to) + 12;
				double ty = cellY(k, to) + 12;
				for (int gi = 3; gi >= 0; gi--) {
					double q = Math.max(0, p - gi * 0.06);
					double x = fx + (tx - fx) * q;
					double y = fy + (ty - fy) * q - 12 * 4 * q * (1 - q);
					Kit.region(g, ScratchDraw.FLAKES, 32, 4, 4 * 4, 0, 4, 4, (int) x - 2, (int) y - 2, 4, 4, Kit.fade(gi == 0 ? 1 : 0.4 - gi * 0.1));
				}
			} else {
				int f = Math.min(3, (int) ((t - 650) / 90));
				Kit.region(g, ScratchDraw.PUFF, 64, 16, f * 16, 0, 16, 16, cellX(k, to) + 4, cellY(k, to) + 4);
			}
		}
		for (long[] fz : fizzles) {
			if (fz[0] != seat || now - fz[2] > 600) continue;
			int c = (int) fz[1];
			if (c < 0 || c >= ShowdownCard.CELLS) continue;
			int f = Math.min(3, (int) ((now - fz[2]) / 150));
			Kit.region(g, ScratchDraw.PUFF, 64, 16, f * 16, 0, 16, 16, cellX(k, c) + 4, cellY(k, c) + 4, 16, 16, 0xFF9A9A9A);
		}
	}

	/** Rabbit's Foot: a gold light runs round the card, then the ×2 / ×4 stamp slams onto the corner. */
	private void footFx(GuiGraphicsExtractor g, int seat, int cx, int cy, long now) {
		long[] last = null;
		for (long[] f : feet) if (f[0] == seat) last = f;
		int mult = seat < boards().length ? boards()[seat].mult() : 1;
		if (last == null && mult <= 1) return;
		long t = last == null ? 10_000 : now - last[2];
		boolean rm = Kit.reduceMotion();
		if (t < 500 && !rm) {
			double p = t / 500.0;
			int per = 2 * (CARD_W + cardH());
			int d = (int) (p * per);
			int x;
			int y;
			if (d < CARD_W) {
				x = cx + d;
				y = cy;
			} else if (d < CARD_W + cardH()) {
				x = cx + CARD_W;
				y = cy + d - CARD_W;
			} else if (d < 2 * CARD_W + cardH()) {
				x = cx + CARD_W - (d - CARD_W - cardH());
				y = cy + cardH();
			} else {
				x = cx;
				y = cy + cardH() - (d - 2 * CARD_W - cardH());
			}
			g.fill(x - 2, y - 2, x + 3, y + 3, Kit.GOLD);
		}
		if (mult > 1 && (t >= 500 || rm)) {
			double s = rm ? 1 : 2 - Ease.OUT_BACK.apply(Math.min(1, (t - 500) / 200.0));
			Component badge = Component.translatable("gui.burmaldaholic.pvp.scratch.multiplier", Texts.number(mult));
			int bw = font.width(badge) + 8;
			g.pose().pushMatrix();
			g.pose().translate(cx + CARD_W - bw / 2f, cy + 4);
			g.pose().scale((float) s, (float) s);
			Kit.sprite(g, Kit.core("cards/stamp/gold"), -bw / 2, -6, bw, 13);
			Kit.centered(g, font, badge, 0, -4, Kit.INK);
			g.pose().popMatrix();
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<PvpSeat> seats = view.plateSeats();
		long entry = seats.isEmpty() ? 1 : Math.max(1, seats.get(0).stake());
		Board[] boards = boards();
		if (!twoRows()) {
			PvpDraw.pot(g, font, view.pot(), entry, left + W / 2 - 12, top + 36);
			PlateRow.draw(g, font, view.raw(), seats, left, top + 72, s -> score(s, boards), view.settled() ? view.winners() : null);
		} else {
			PvpDraw.pot(g, font, view.pot(), entry, left + 342, top + 30);
			List<PvpSeat> order = PlateRow.ordered(seats);
			for (int k = 0; k < order.size() && k < 6; k++) {
				PvpSeat s = order.get(k);
				int[] at = cardAt(k);
				int x = left + at[0];
				int y = top + at[1] - 14;
				Kit.sprite(g, Kit.pvp(s.you() ? "lobby_row_host" : "lobby_row"), x, y, CARD_W, 13);
				Faces.draw(g, s.key(), s.bot(), s.name(), x + 3, y + 2, 9);
				Component sc = score(s, boards);
				int sw = sc == null ? 0 : font.width(sc);
				Kit.fit(g, font, s.plateName(), x + 14, y + 3, CARD_W - 20 - sw, s.you() ? Kit.GOLD : Kit.BONE, false);
				if (sc != null) g.text(font, sc, x + CARD_W - 4 - sw, y + 3, Kit.BONE_SHADE, false);
				if (s.pressed()) PvpDraw.readyTick(g, x + CARD_W - 12, y - 10);
			}
		}
		PvpModeView.StepView s = view.last();
		if (s != null && s.kind().equals("cell_wait") && !view.settled()) {
			Kit.centeredFit(g, font, Component.translatable("gui.burmaldaholic.pvp.scratch.auto_in", seconds(ticksLeft(s))), left + W / 2,
				top + (twoRows() ? 228 : 196) - 10, 200, MUTED, true);
		}
	}

	private @Nullable Component score(PvpSeat s, Board[] boards) {
		if (s.index() >= boards.length) return null;
		long v = shownScore(s.index());
		int mult = boards[s.index()].mult();
		return mult > 1 ? Component.translatable("gui.burmaldaholic.pvp.scratch.score_mult", Texts.number(v), Texts.number(mult)) : Texts.number(v);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (!twoRows()) PlateRow.bubbles(g, font, view.raw(), view.plateSeats(), left, top + 72);
	}
}
