package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinChain;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Coin Flip Duel live screen (PVP.md §4.5, 256 × 180), registered with {@code client.pvp.PvpScreens} as mode
 * {@code coin} and fed by the pvp module's match sync ({@link MatchView}). Shows both duelists with their sides,
 * stakes and the pot; plays the countdown / spin / landing from the revealed steps ({@code countdown},
 * {@code spin}, {@code land}); then the chain controls: Double or nothing — Heads / — Tails / Walk away for the
 * chain loser, Let it ride / Take the money for the winner (from the sync's {@code decision}), and Rematch /
 * Taunt… / Close when the chain is over. Decisions go to the server as {@code decide} actions.
 */
public final class CoinDuelScreen extends PvpPanel implements PvpScreens.ModeScreen {
	private static final int COIN_R = 18;

	private MatchView view;
	private int steps;
	private int stepStartTick;
	private int syncTick;
	private String sentDecision = "";

	public CoinDuelScreen(JsonObject state) {
		super(Component.translatable("gui.burmaldaholic.pvp.coin.title"), 256, 180);
		this.view = new MatchView(state);
		this.steps = view.steps().size();
	}

	@Override
	public Screen screen() {
		return this;
	}

	@Override
	public void update(JsonObject state) {
		MatchView next = new MatchView(state);
		int n = next.steps().size();
		if (n != steps || !next.id().equals(view.id())) {
			stepStartTick = ticks;
		}
		boolean decisionChanged = !MatchView.str(next.decision(), "id", "").equals(MatchView.str(view.decision(), "id", ""))
			|| !next.id().equals(view.id());
		if (decisionChanged) {
			sentDecision = "";
		}
		steps = n;
		syncTick = ticks;
		view = next;
		rebuild();
	}

	private boolean heads0() {
		return MatchView.bool(view.params(), "heads", true);
	}

	private MatchView.StepView lastStep() {
		var all = view.steps();
		return all.isEmpty() ? null : all.getLast();
	}

	private static void send(String action, CompoundTag args) {
		ClientPlayNetworking.send(new CoinDuelNet.Action(action, args));
	}

	private void decide(String decision, long option) {
		CompoundTag a = new CompoundTag();
		a.putString("decision", decision);
		a.putLong("option", option);
		// the question this screen shows (stale answers are dropped by the engine, review wave 2 m2)
		JsonObject d = view.decision();
		if (d != null) {
			a.putString("match", MatchView.str(d, "match", view.id()));
			a.putLong("seq", MatchView.lng(d, "seq", -1));
		}
		send("decide", a);
		sentDecision = decision;
		rebuild();
	}

	@Override
	protected int layout() {
		int y = top + 20 + 3 * font.lineHeight + 2 * COIN_R + 14;
		int w = panelWidth - 2 * PAD;
		for (Component line : infoLines()) {
			y += wrappedHeight(line, w) + 1;
		}
		Flow flow = new Flow(y + 4);
		JsonObject d = view.decision();
		String id = d == null ? "" : MatchView.str(d, "id", "");
		if (d != null && !id.equals(sentDecision)) {
			if (id.equals(CoinChain.DON_OFFER)) {
				if (MatchView.str(d, "blocked", "").isEmpty()) {
					flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.don_heads").withStyle(ChatFormatting.GOLD), 80,
						b -> decide(CoinChain.DON_OFFER, CoinChain.DON_HEADS));
					flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.don_tails").withStyle(ChatFormatting.GOLD), 80,
						b -> decide(CoinChain.DON_OFFER, CoinChain.DON_TAILS));
				}
				flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.walk_away"), 60, b -> decide(CoinChain.DON_OFFER, CoinChain.WALK_AWAY));
			} else if (id.equals(CoinChain.LET_IT_RIDE)) {
				flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.let_it_ride").withStyle(ChatFormatting.GOLD), 70,
					b -> decide(CoinChain.LET_IT_RIDE, CoinChain.RIDE));
				flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.take_money"), 70, b -> decide(CoinChain.LET_IT_RIDE, CoinChain.TAKE));
			}
			flow.newRow();
		} else if (view.settled()) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.rematch"), 60, b -> {
				CompoundTag a = new CompoundTag();
				a.putString("id", view.id());
				send("rematch", a);
				b.active = false;
			});
		}
		flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), 50, b -> {
			if (minecraft != null) {
				minecraft.gui.setScreen(new TauntScreen(this, line -> {
					CompoundTag a = new CompoundTag();
					a.putInt("line", line);
					send("taunt", a);
				}));
			}
		});
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
		return flow.bottom();
	}

	/** Result / chain / decision text under the coin. */
	private java.util.List<Component> infoLines() {
		java.util.List<Component> out = new java.util.ArrayList<>();
		MatchView.StepView land = view.step("land");
		if (land != null) {
			boolean h = MatchView.bool(land.data(), "heads", true);
			int winner = MatchView.integer(land.data(), "winner", -1);
			out.add(Component.translatable("gui.burmaldaholic.pvp.coin.landed", side(h).copy().withStyle(ChatFormatting.BOLD)).withStyle(ChatFormatting.GOLD));
			long pay = view.payout(winner);
			if (pay < 0) {
				pay = view.pot() - PvpMath.rake(view.pot(), view.rakeBasisPoints());
			}
			out.add(Component.translatable("gui.burmaldaholic.pvp.coin.takes", view.name(winner), Texts.chipsAcc(pay)));
		}
		JsonObject chain = view.chain();
		int loser = MatchView.integer(chain, "loser", -1);
		long deficit = MatchView.lng(chain, "deficit", 0);
		if (loser >= 0 && deficit > 0) {
			out.add(Component.translatable("gui.burmaldaholic.pvp.coin.chain", view.name(loser), Texts.chips(deficit)));
		}
		JsonObject d = view.decision();
		if (d != null && !MatchView.str(d, "id", "").equals(sentDecision)) {
			long dd = MatchView.lng(d, "deficit", deficit);
			String blocked = MatchView.str(d, "blocked", "");
			if (MatchView.str(d, "id", "").equals(CoinChain.DON_OFFER)) {
				if (blocked.equals(CoinChain.DON_UNAFFORDABLE)) {
					out.add(Component.translatable(CoinChain.DON_UNAFFORDABLE, Texts.chips(MatchView.lng(d, "blockedArg", dd))).withStyle(ChatFormatting.GRAY));
				} else if (!blocked.isEmpty()) {
					out.add(Component.translatable(CoinChain.DON_LIMIT).withStyle(ChatFormatting.GRAY));
				} else {
					out.add(Component.translatable("gui.burmaldaholic.pvp.coin.don_explain", Texts.chips(dd), Texts.chips(2 * dd)));
				}
			} else {
				int other = view.you() == 0 ? 1 : 0;
				Component called = side(MatchView.integer(d, "called", 1) != CoinChain.DON_TAILS);
				out.add(Component.translatable("gui.burmaldaholic.pvp.coin.don_request", view.name(other), Texts.chips(dd), called, Texts.chips(dd)));
			}
			int remaining = MatchView.integer(d, "ticksLeft", -1);
			if (remaining >= 0) {
				out.add(Component.translatable("gui.burmaldaholic.common.timer", seconds(remaining - (ticks - syncTick))).withStyle(ChatFormatting.GRAY));
			}
		}
		return out;
	}

	private static Component side(boolean heads) {
		return Component.translatable(heads ? "gui.burmaldaholic.extras.coin.heads" : "gui.burmaldaholic.extras.coin.tails");
	}

	@Override
	protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int w = panelWidth - 2 * PAD;
		int y = top + 20;
		int cx = left + panelWidth / 2;
		// duelists: participant 0 left, 1 right
		for (int i = 0; i < 2; i++) {
			MatchView.Seat s = view.seat(i);
			Component name = s == null ? Texts.raw("?") : s.name();
			if (s != null && s.allIn()) {
				name = name.copy().append(" ").append(Component.translatable("gui.burmaldaholic.pvp.all_in_tag").withStyle(ChatFormatting.RED));
			}
			boolean h = i == 0 ? heads0() : !heads0();
			MutableComponent sideTag = Texts.raw("[").append(side(h)).append("]");
			long stake = s == null ? 0 : s.stake();
			int x = i == 0 ? left + PAD : left + panelWidth - PAD;
			draw(g, name, x, y, i == view.you() ? GOLD : TEXT, i == 1);
			draw(g, sideTag, x, y + font.lineHeight, MUTED, i == 1);
			draw(g, Texts.chips(stake), x, y + 2 * font.lineHeight, TEXT, i == 1);
		}
		Component vs = Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(view.pot()));
		g.text(font, vs, cx - font.width(vs) / 2, y + font.lineHeight, GOLD, true);
		// the coin
		int cy = y + 3 * font.lineHeight + COIN_R + 6;
		MatchView.StepView last = lastStep();
		String kind = last == null ? "" : last.kind();
		int elapsed = ticks - stepStartTick;
		double scale = 1.0;
		String face = "?";
		if (kind.equals("countdown")) {
			int remaining = Math.max(0, last.ticks() - elapsed);
			face = Integer.toString(Math.max(1, (int) Math.ceil(remaining / Math.max(1.0, last.ticks() / 3.0))));
		} else if (kind.equals("spin")) {
			double t = elapsed + partial;
			scale = Math.abs(Math.cos(t * 0.6 / (1 + t / 20.0)));
			face = "";
		} else if (view.step("land") != null) {
			face = MatchView.bool(view.step("land").data(), "heads", true) ? "H" : "T";
		}
		disc(g, cx, cy, COIN_R + 1, scale, 0xFF8A6A10);
		disc(g, cx, cy, COIN_R - 1, scale, GOLD);
		if (!face.isEmpty() && scale > 0.5) {
			g.text(font, Texts.raw(face).withStyle(ChatFormatting.BOLD), cx - font.width(face) / 2, cy - 4, 0xFF5A3A00, false);
		}
		y = cy + COIN_R + 8;
		for (Component line : infoLines()) {
			y = wrap(g, line, left + PAD, y, w, TEXT) + 1;
		}
	}

	private void draw(GuiGraphicsExtractor g, Component text, int x, int y, int color, boolean alignRight) {
		g.text(font, text, alignRight ? x - font.width(text) : x, y, color, true);
	}

	/** Filled ellipse from horizontal spans; {@code xScale} squashes it (the spinning coin). */
	static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, double xScale, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy) * xScale);
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (ticks % 20 == 0 && view.decision() != null) {
			rebuild(); // timer line height may change
		}
	}
}
