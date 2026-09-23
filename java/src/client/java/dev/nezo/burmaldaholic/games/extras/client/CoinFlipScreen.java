package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.CoinFlip;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Coin Flip screen (UI.md §9): bet selector with optional pawn stake, [Heads] [Tails], and — in Hardcore with
 * the Soul Wager enabled — a red [Wager your soul] button leading to a warning, a type-to-confirm word and a
 * 5-second hold (the server enforces the hold). The flip animation (30 ticks) is cosmetic: the result is
 * already settled when it arrives.
 */
final class CoinFlipScreen extends ExtrasScreen {
	private static final int FLIP_TICKS = 30;
	private static final int COIN_Y = 58;
	private static long lastAmount = 1;

	private enum Mode {
		NORMAL, WARN, TYPE, HOLD
	}

	private final BetSelector bet;
	private Mode mode = Mode.NORMAL;
	private CoinFlip.Side soulSide = CoinFlip.Side.HEADS;
	private EditBox word;
	private String typed = "";
	private boolean holding;
	private int holdStart;
	private int holdX;
	private int holdY;
	private int holdW;
	private int shownSeq = -1;
	private int animStart = -1000;
	private int textY;

	CoinFlipScreen(CompoundTag state) {
		super("coin", Component.translatable("gui.burmaldaholic.extras.coin.title"), state, 230, 180);
		this.bet = new BetSelector(true, lastAmount, this::rebuildWidgets);
		CompoundTag r = state.getCompoundOrEmpty("result");
		shownSeq = r.getIntOr("seq", -1);
	}

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (seq != shownSeq && seq >= 0) {
			shownSeq = seq;
			animStart = ticks;
		}
	}

	private boolean flipping() {
		return ticks - animStart < FLIP_TICKS;
	}

	@Override
	protected void layout() {
		CompoundTag s = state();
		switch (mode) {
			case NORMAL -> {
				textY = top + 20;
				Flow flow = flow(COIN_Y + 66);
				bet.build(flow, s);
				flow.newRow();
				for (CoinFlip.Side side : CoinFlip.Side.values()) {
					Button b = flow.button(Component.translatable(side.key()), 70, btn -> flip(side));
					b.active = !flipping();
				}
				if (s.getBooleanOr("soul", false)) {
					flow.newRow();
					flow.button(Component.translatable("gui.burmaldaholic.extras.soul.button").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 90, btn -> {
						mode = Mode.WARN;
						rebuildWidgets();
					});
				}
				fitHeight(flow.bottom());
			}
			case WARN -> {
				Component warning = Component.translatable("gui.burmaldaholic.extras.soul.warning", Texts.chips(s.getLongOr("soul_value", 0)));
				int y = 34 + wrappedHeight(warning, panelWidth - 2 * PAD) + 6;
				Flow flow = flow(y);
				flow.button(Component.translatable("gui.burmaldaholic.common.confirm").withStyle(ChatFormatting.RED), 70, b -> {
					mode = Mode.TYPE;
					typed = "";
					rebuildWidgets();
				});
				flow.button(Component.translatable("gui.burmaldaholic.common.cancel"), 70, b -> cancelSoul());
				fitHeight(flow.bottom());
			}
			case TYPE -> {
				Flow flow = flow(48);
				int x = flow.reserve(panelWidth - 2 * PAD);
				word = new EditBox(font, x, flow.y(), panelWidth - 2 * PAD, 20, Component.translatable("gui.burmaldaholic.extras.soul_confirm_word"));
				word.setMaxLength(24);
				word.setValue(typed);
				word.setResponder(v -> typed = v);
				addRenderableWidget(word);
				setInitialFocus(word);
				flow.newRow();
				flow.button(Component.translatable("gui.burmaldaholic.common.confirm").withStyle(ChatFormatting.RED), 70, b -> {
					if (wordMatches(typed)) {
						mode = Mode.HOLD;
						rebuildWidgets();
					} else {
						showError(Component.translatable("gui.burmaldaholic.extras.soul.confirm_prompt", Component.translatable("gui.burmaldaholic.extras.soul_confirm_word")));
					}
				});
				flow.button(Component.translatable("gui.burmaldaholic.common.cancel"), 70, b -> cancelSoul());
				fitHeight(flow.bottom());
			}
			case HOLD -> {
				Flow flow = flow(24);
				for (CoinFlip.Side side : CoinFlip.Side.values()) {
					Component label = Component.translatable(side.key());
					flow.button(side == soulSide ? label.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : label, 70, b -> {
						soulSide = side;
						rebuildWidgets();
					});
				}
				flow.gap(4);
				holdX = left + PAD;
				holdY = flow.y();
				holdW = panelWidth - 2 * PAD;
				flow.gap(24);
				flow.button(Component.translatable("gui.burmaldaholic.common.cancel"), 70, b -> cancelSoul());
				fitHeight(flow.bottom());
			}
		}
	}

	static boolean wordMatches(String typed) {
		String t = typed.trim().toLowerCase(Locale.ROOT);
		return !t.isEmpty() && (t.equals(I18n.get("gui.burmaldaholic.extras.soul_confirm_word").toLowerCase(Locale.ROOT))
			|| t.equals("deal") || t.equals("сделка"));
	}

	private void cancelSoul() {
		holding = false;
		mode = Mode.NORMAL;
		send("soul_cancel", new CompoundTag());
		rebuildWidgets();
	}

	private void flip(CoinFlip.Side side) {
		lastAmount = Math.max(1, bet.amount());
		CompoundTag args = bet.args();
		args.putString("side", side.id());
		send("flip", args);
	}

	@Override
	public void tick() {
		super.tick();
		if (ticks - animStart == FLIP_TICKS) {
			rebuildWidgets();
		}
		if (mode == Mode.HOLD && holding && ticks - holdStart >= 100) {
			holding = false;
			CompoundTag args = new CompoundTag();
			args.putString("side", soulSide.id());
			send("soul", args);
			mode = Mode.NORMAL;
			rebuildWidgets();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (mode == Mode.HOLD && event.button() == 0 && inHold(event.x(), event.y())) {
			holding = true;
			holdStart = ticks;
			send("soul_arm", new CompoundTag());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (holding) {
			holding = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	private boolean inHold(double x, double y) {
		return x >= holdX && x < holdX + holdW && y >= holdY && y < holdY + 20;
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		CompoundTag s = state();
		int w = panelWidth - 2 * PAD;
		switch (mode) {
			case NORMAL -> {
				int y = textY;
				g.text(font, bet.line(s), left + PAD, y, TEXT, true);
				long value = bet.value(s);
				if (value > 0) {
					long win = Payouts.floorPay(value, s.getDoubleOr("payout", CoinFlip.DEFAULT_PAYOUT));
					g.text(font, Component.translatable("gui.burmaldaholic.extras.coin.payout", Texts.chips(win)), left + PAD, y + 11, MUTED, true);
				}
				g.text(font, Component.translatable("gui.burmaldaholic.common.limits", Texts.number(s.getLongOr("min", 1)), Texts.number(s.getLongOr("max", 1))),
					left + PAD, y + 22, MUTED, true);
				drawCoin(g, a);
			}
			case WARN -> {
				g.text(font, Component.translatable("gui.burmaldaholic.extras.soul.warning_title").withStyle(ChatFormatting.BOLD), left + PAD, top + 20, ERROR, true);
				wrapped(g, Component.translatable("gui.burmaldaholic.extras.soul.warning", Texts.chips(s.getLongOr("soul_value", 0))), left + PAD, top + 34, w, TEXT);
			}
			case TYPE -> wrapped(g, Component.translatable("gui.burmaldaholic.extras.soul.confirm_prompt",
				Component.translatable("gui.burmaldaholic.extras.soul_confirm_word").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)), left + PAD, top + 22, w, TEXT);
			case HOLD -> {
				g.fill(holdX, holdY, holdX + holdW, holdY + 20, 0xFF4A0C0C);
				if (holding) {
					int p = (int) Math.min(holdW, (ticks - holdStart + a) * holdW / 100.0);
					g.fill(holdX, holdY, holdX + p, holdY + 20, 0xFFB01818);
				}
				Art.frame(g, holdX, holdY, holdW, 20, 0xFFFF5555);
				g.centeredText(font, Component.translatable("gui.burmaldaholic.extras.soul.hold"), holdX + holdW / 2, holdY + 6, TEXT);
			}
		}
	}

	private void drawCoin(GuiGraphicsExtractor g, float a) {
		CompoundTag r = state().getCompoundOrEmpty("result");
		int cx = left + panelWidth / 2;
		int cy = top + COIN_Y + 16;
		int w = panelWidth - 2 * PAD;
		if (r.getIntOr("seq", -1) < 0) {
			Art.disc(g, cx, cy, 13, 1.0, 0xFFB8860B);
			Art.disc(g, cx, cy, 11, 1.0, GOLD);
			return;
		}
		if (flipping()) {
			double t = ticks - animStart + a;
			Art.disc(g, cx, cy - (int) (Math.sin(Math.PI * t / FLIP_TICKS) * 10), 13, Math.abs(Math.cos(t * 0.7)), GOLD);
			g.centeredText(font, Component.translatable("gui.burmaldaholic.extras.coin.flipping"), cx, cy + 22, MUTED);
			return;
		}
		boolean heads = "heads".equals(r.getStringOr("landed", "heads"));
		Art.disc(g, cx, cy, 13, 1.0, 0xFFB8860B);
		Art.disc(g, cx, cy, 11, 1.0, heads ? GOLD : 0xFFE0C060);
		Component side = Component.translatable("gui.burmaldaholic.extras.coin." + (heads ? "heads" : "tails"));
		boolean win = r.getBooleanOr("win", false);
		Component line = win
			? Component.translatable("gui.burmaldaholic.extras.coin.result_win", side, Texts.chips(r.getLongOr("net", 0))).withStyle(ChatFormatting.GREEN)
			: Component.translatable("gui.burmaldaholic.extras.coin.result_lose", side).withStyle(ChatFormatting.RED);
		int lw = font.width(line);
		if (lw <= w) {
			g.centeredText(font, line, cx, cy + 22, TEXT);
		} else {
			wrapped(g, line, left + PAD, cy + 18, w, TEXT);
		}
	}
}
