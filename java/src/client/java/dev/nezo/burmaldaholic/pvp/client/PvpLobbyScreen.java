package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.PvpText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * PvP lobby view (PVP.md §3.11.3), 256 wide:
 * <pre>
 *  Slot Showdown — lobby                                       ⏱ 58
 *  Entry 100 · Pot 300 · House cut 3%        Players: 3/6
 *  1 ◆ Alex (host)      ALL-IN      Head-to-head 3–5
 *  2 ◆ You
 *  3   Empty seat
 *  Empty seats at the start: bots fill 2 bots
 *  [Start now] [Leave lobby] [Taunt…]                     Balance: 12 500
 * </pre>
 * Rows whose right part does not fit (Russian) continue on a second line.
 */
final class PvpLobbyScreen extends PvpScreen {
	/** One drawn line: left text, optional right text (right-aligned, or on its own line when it does not fit). */
	private record Line(Component left, @Nullable Component right, int color, boolean separatorBefore) {}

	private final List<Line> lines = new ArrayList<>();
	private final List<Integer> lineY = new ArrayList<>();
	private int buttonsY;
	private int balanceY;

	PvpLobbyScreen(JsonObject state) {
		super(Component.translatable("gui.burmaldaholic.pvp.lobby.title", game(state)), state);
	}

	@Override
	protected void onState(JsonObject oldState, JsonObject newState) {
		// title keeps the mode (same match)
	}

	long secondsLeft() {
		long left = num(state(), "ticksLeft", -1);
		return left < 0 ? -1 : PvpText.seconds(left - (ticks - stateTick));
	}

	@Override
	protected @Nullable Component titleRight() {
		long s = secondsLeft();
		return s < 0 ? null : Texts.raw("⏱ " + s); // literal-ok: clock glyph + number
	}

	private void buildLines() {
		lines.clear();
		JsonObject s = state();
		List<JsonObject> ps = participants(s);
		long max = Math.max(ps.size(), num(s, "max", ps.size()));
		MutableComponent money = Component.translatable("gui.burmaldaholic.pvp.lobby.entry", Texts.chips(num(s, "entry", 0)));
		money.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(num(s, "pot", 0))));
		money.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.pvp.lobby.rake", percent(s)));
		lines.add(new Line(money, null, TEXT, false));
		lines.add(new Line(Component.translatable("gui.burmaldaholic.pvp.lobby.players", Texts.number(ps.size()), Texts.number(max)), null, MUTED, false));
		if (bool(s, "grudge")) {
			lines.add(new Line(Component.translatable("gui.burmaldaholic.pvp.grudge.title").withStyle(ChatFormatting.BOLD), null, RED, false));
		}
		boolean first = true;
		for (int seat = 0; seat < max; seat++) {
			JsonObject p = seat < ps.size() ? ps.get(seat) : null;
			Component left;
			Component right = null;
			int color = TEXT;
			if (p == null) {
				left = Texts.raw((seat + 1) + "   ").append(Component.translatable("gui.burmaldaholic.common.seat_empty"));
				color = MUTED;
			} else {
				Component n = name(p);
				if (bool(p, "host")) {
					n = Component.translatable("gui.burmaldaholic.pvp.lobby.host", n);
				}
				left = Texts.raw((seat + 1) + " ◆ ").append(n);
				if (bool(p, "you")) {
					color = GREEN;
				}
				MutableComponent r = Component.empty();
				if (bool(p, "allIn")) {
					r.append(Component.translatable("gui.burmaldaholic.pvp.all_in_tag").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
				}
				if (!bool(s, "equalStakes")) {
					if (!r.getSiblings().isEmpty()) {
						r.append(Texts.raw("  "));
					}
					r.append(Texts.chips(num(p, "stake", 0)));
				}
				if (p.has("wins")) {
					if (!r.getSiblings().isEmpty()) {
						r.append(Texts.raw("  "));
					}
					r.append(Component.translatable("gui.burmaldaholic.pvp.lobby.record", Texts.number(num(p, "wins", 0)), Texts.number(num(p, "losses", 0)))
						.withStyle(ChatFormatting.GRAY));
				}
				right = r.getSiblings().isEmpty() ? null : r;
			}
			lines.add(new Line(left, right, color, first));
			first = false;
		}
		long fill = num(s, "botsToFill", 0);
		if (fill > 0) {
			lines.add(new Line(Component.translatable("gui.burmaldaholic.pvp.bots.will_fill", Texts.plural("unit.burmaldaholic.bot", fill)), null, MUTED,
				true));
		} else if (ps.size() < 2) {
			lines.add(new Line(Component.translatable("gui.burmaldaholic.pvp.lobby.need_more"), null, MUTED, true));
		}
		long secs = secondsLeft();
		if (secs >= 0) {
			lines.add(new Line(Component.translatable("gui.burmaldaholic.pvp.lobby.starts_in", Texts.plural("unit.burmaldaholic.second_acc", secs)), null,
				secs <= 10 ? GOLD : MUTED, fill <= 0 && ps.size() >= 2));
		}
	}

	@Override
	protected void layout() {
		buildLines();
		int w = panelWidth - 2 * PAD;
		int y = contentTop();
		lineY.clear();
		for (Line l : lines) {
			if (l.separatorBefore()) {
				y += 4;
			}
			lineY.add(y);
			int lw = font.width(l.left());
			int rw = l.right() == null ? 0 : font.width(l.right());
			if (l.right() == null || lw + rw + 12 <= w) {
				y += Math.max(1, font.split(l.left(), w).size()) * LINE;
			} else {
				y += font.split(l.left(), w).size() * LINE + font.split(l.right(), w - 12).size() * LINE;
			}
		}
		buttonsY = y + 6;
		Flow flow = new Flow(buttonsY);
		JsonObject s = state();
		boolean host = num(s, "host", -2) == num(s, "you", -1) && num(s, "you", -1) >= 0;
		int players = participants(s).size();
		boolean mixed = "mixed".equals(str(s, "policy", ""));
		if (host) {
			if (mixed && num(s, "botsToFill", 0) > 0) {
				flow.button(Component.translatable("gui.burmaldaholic.pvp.bots.start_with_bots").withStyle(ChatFormatting.GREEN), 60,
					b -> send("start", "", 0));
			} else {
				flow.button(Component.translatable("gui.burmaldaholic.pvp.lobby.start").withStyle(ChatFormatting.GREEN), 60,
					b -> send("start", "", 0)).active = players >= 2;
			}
		}
		if (num(s, "you", -1) >= 0) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.lobby.leave"), 60, b -> {
				send("leave", "", 0);
				onClose();
			});
			flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), 50, b -> PvpScreens.openTaunts(this));
		}
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
		balanceY = flow.bottom() + 2;
		fitHeight(balanceY + 10);
	}

	@Override
	public void tick() {
		super.tick();
		if ((ticks - stateTick) % 20 == 0 && num(state(), "ticksLeft", -1) >= 0) {
			buildLines(); // countdown text
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int w = panelWidth - 2 * PAD;
		int x = left + PAD;
		for (int i = 0; i < lines.size() && i < lineY.size(); i++) {
			Line l = lines.get(i);
			int y = lineY.get(i);
			if (l.separatorBefore()) {
				separator(g, y - 3);
			}
			int lw = font.width(l.left());
			int end = wrap(g, font, l.left(), x, y, w, l.color());
			if (l.right() != null) {
				int rw = font.width(l.right());
				if (lw + rw + 12 <= w) {
					g.text(font, l.right(), x + w - rw, y, TEXT, true);
				} else {
					wrap(g, font, l.right(), x + 12, end, w - 12, TEXT);
				}
			}
		}
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(num(state(), "balance", 0)));
		g.text(font, balance, left + panelWidth - PAD - font.width(balance), balanceY, GOLD, true);
	}
}
