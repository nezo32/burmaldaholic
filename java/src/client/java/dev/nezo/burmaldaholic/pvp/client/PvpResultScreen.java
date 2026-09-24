package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.RevealOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Result window (PVP.md §3.11.1 "Result panel": ranking, pot, rake, payout, [Rematch] [Taunt…] [Close]).
 * The ranking is revealed like the shared Final Reveal (§3.11.4): from the last place up to the winner, one row
 * every {@link #STEP} ticks, then the "NAME WINS!" / "DEAD HEAT!" banner and your own line.
 */
final class PvpResultScreen extends PvpScreen {
	static final int STEP = 10;

	private int rowsY;
	private int footerY;

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

	/** Rows visible now (all once the reveal is over). */
	int visible() {
		return RevealOrder.visibleRows(rows(), ticks, STEP);
	}

	boolean revealDone() {
		return visible() >= rows();
	}

	/** Skips the animation (screenshots / reopen). */
	void finishReveal() {
		ticks = Math.max(ticks, rows() * STEP);
	}

	private Component banner() {
		JsonObject r = result();
		int[] winners = ints(r, "winners");
		if (winners.length > 1) {
			return Component.translatable("gui.burmaldaholic.pvp.result.dead_heat_title");
		}
		JsonObject w = winners.length == 0 ? null : participant(state(), winners[0]);
		return Component.translatable("gui.burmaldaholic.pvp.result.winner_title", w == null ? Component.empty() : plainName(w));
	}

	private Component yourLine() {
		JsonObject s = state();
		long you = num(s, "you", -1);
		if (you < 0) {
			return Component.empty();
		}
		long[] payouts = longs(result(), "payouts");
		long payout = you < payouts.length ? payouts[(int) you] : 0;
		JsonObject me = participant(s, you);
		long stake = me == null ? 0 : num(me, "stake", 0);
		if (payout <= 0) {
			return Component.translatable("gui.burmaldaholic.pvp.result.you_lose", Texts.chips(stake));
		}
		return ints(result(), "winners").length > 1 ? Component.translatable("gui.burmaldaholic.pvp.result.split", Texts.chips(payout))
			: Component.translatable("gui.burmaldaholic.pvp.result.you_win", Texts.chips(payout));
	}

	private Component potLine() {
		JsonObject s = state();
		long pot = num(s, "pot", 0);
		long rake = num(s, "rake", 0);
		return Component.translatable("gui.burmaldaholic.pvp.result.pot_line", Texts.chips(pot), Texts.chips(rake), Texts.chips(pot - rake));
	}

	private Component row(int rankPos, boolean shown) {
		JsonObject r = result();
		int[] order = ints(r, "order");
		int i = order[rankPos];
		int[] places = ints(r, "places");
		long place = i < places.length && places[i] > 0 ? places[i] : rankPos + 1;
		if (!shown) {
			return Component.translatable("gui.burmaldaholic.pvp.match.row", Texts.number(place), Component.translatable("gui.burmaldaholic.pvp.match.hidden"),
				Texts.raw("…"));
		}
		JsonObject p = participant(state(), i);
		long[] points = longs(r, "points");
		MutableComponent name = p == null ? Component.empty() : name(p).copy();
		if (p != null && bool(p, "allIn")) {
			name.append(Texts.raw(" ")).append(Component.translatable("gui.burmaldaholic.pvp.all_in_tag").withStyle(ChatFormatting.RED));
		}
		return Component.translatable("gui.burmaldaholic.pvp.match.row", Texts.number(place), name,
			Texts.plural("unit.burmaldaholic.point", i < points.length ? points[i] : 0));
	}

	private Component payoutOf(int rankPos) {
		int i = ints(result(), "order")[rankPos];
		long[] payouts = longs(result(), "payouts");
		long v = i < payouts.length ? payouts[i] : 0;
		return v > 0 ? Texts.raw("+").append(Texts.number(v)) : Component.empty();
	}

	@Override
	protected void layout() {
		int w = panelWidth - 2 * PAD;
		int y = contentTop();
		y += wrappedHeight(banner(), w) + 4; // banner space is reserved from the start (no jump)
		y += LINE + 2; // "Final standings"
		rowsY = y;
		for (int k = 0; k < rows(); k++) {
			y += wrappedHeight(row(k, true), w - 40);
		}
		y += 4;
		footerY = y;
		y += wrappedHeight(yourLine(), w) + wrappedHeight(potLine(), w);
		if (rematchLine() != null) {
			y += LINE;
		}
		Flow flow = new Flow(y + 6);
		JsonObject s = state();
		long you = num(s, "you", -1);
		JsonObject me = you < 0 ? null : participant(s, you);
		if (me != null && "SETTLED".equalsIgnoreCase(str(s, "state", ""))) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.rematch").withStyle(ChatFormatting.GREEN), 60,
				b -> send("rematch", "", 0)).active = !bool(me, "rematch");
			flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), 50, b -> PvpScreens.openTaunts(this));
		}
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
		fitHeight(flow.bottom());
	}

	private @Nullable Component rematchLine() {
		int ready = 0;
		int humans = 0;
		for (JsonObject p : participants(state())) {
			if (!bool(p, "bot")) {
				humans++;
				if (bool(p, "rematch")) {
					ready++;
				}
			}
		}
		return ready == 0 ? null : Component.translatable("gui.burmaldaholic.pvp.rematch.waiting", Texts.number(ready), Texts.number(humans));
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int w = panelWidth - 2 * PAD;
		int x = left + PAD;
		int y = contentTop();
		if (revealDone()) {
			wrap(g, font, banner().copy().withStyle(ChatFormatting.BOLD), x, y, w, GOLD);
		} else {
			g.text(font, Component.translatable("gui.burmaldaholic.pvp.match.final"), x, y, MUTED, true);
		}
		g.text(font, Component.translatable("gui.burmaldaholic.pvp.result.standings"), x, rowsY - LINE - 2, TEXT, true);
		int n = rows();
		int shownFrom = n - visible(); // rank positions ≥ shownFrom are revealed (last place first)
		int ry = rowsY;
		for (int k = 0; k < n; k++) {
			boolean shown = k >= shownFrom;
			Component row = row(k, shown);
			int color = !shown ? MUTED : isWinner(k) ? GOLD : TEXT;
			int end = wrap(g, font, row, x, ry, w - 40, color);
			if (shown) {
				Component pay = payoutOf(k);
				g.text(font, pay, x + w - font.width(pay), ry, GREEN, true);
			}
			ry = end;
		}
		separator(g, footerY - 3);
		if (revealDone()) {
			int yy = wrap(g, font, yourLine(), x, footerY, w, GREEN);
			yy = wrap(g, font, potLine(), x, yy, w, MUTED);
			Component rm = rematchLine();
			if (rm != null) {
				g.text(font, rm, x, yy, GOLD, true);
			}
		}
	}

	private boolean isWinner(int rankPos) {
		int i = ints(result(), "order")[rankPos];
		for (int wIdx : ints(result(), "winners")) {
			if (wIdx == i) {
				return true;
			}
		}
		return false;
	}
}
