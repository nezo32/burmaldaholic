package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.client.board.BoardCard;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Hands;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.HandPub;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.SeatPub;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackWorld;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Blackjack in the world (REDESIGN.md §9.3, docs/architecture/ui-v2.md §7.6), on the board kit: the dealer's fan
 * (hole card face-down until its flip beat), every seat's hand(s) and bet stack, the shoe; the board
 * ({@code Blackjack · 10–100} / {@code Dealer 10} / {@code Steve · 18 s} → {@code Steve +350}) and a plate per seat
 * ({@code Steve} / {@code 50 · 15} → {@code +75 · BLACKJACK}). Everything from the PUBLIC tag ({@link BlackjackPub});
 * cards fly from the shoe on their deal tick (the screens' shared clock), so a passer-by sees the same deal at the same
 * moment as the players.
 */
public final class BlackjackBoardRenderer extends CardTableRenderer<BlackjackTableBlockEntity, CardTableRenderer.State> {
	static final double DEAL_MS = 320, FLIP_MS = 260;

	/** Flip memory per table. */
	private static final class Mem {
		int seq = -1;
		double holeSeenMs = -1;
		boolean holeWasHidden;
		/** S1: one first-seen anchor per deal index (a late or bunched packet's card still flies). */
		final dev.nezo.burmaldaholic.client.table.cards.DealAnchors deals = new dev.nezo.burmaldaholic.client.table.cards.DealAnchors();
	}

	public BlackjackBoardRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(BlackjackModule.TABLE.blockEntityType(), BlackjackBoardRenderer::new);
		BlockEntityRendererRegistry.register(BlackjackModule.HIGH_ROLLER_TABLE.blockEntityType(), BlackjackBoardRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true; // the felt and the board overhang the block
	}

	@Override
	protected int theme(BlackjackTableBlockEntity be) {
		return be.isHighRoller() ? 2 : super.theme(be);
	}

	@Override
	protected void fill(BlackjackTableBlockEntity be, State s, float partialTick) {
		BlackjackPub p = be.clientPub();
		Mem mem = s.cache(Mem::new);
		long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
		double now = s.levelMs;
		if (mem.seq != p.seq()) {
			mem.seq = p.seq();
			mem.holeSeenMs = -1;
			mem.holeWasHidden = false;
		}
		mem.deals.frame(p.startTick());
		// shoe
		float[] shoe = BlackjackWorld.shoe();
		BoardCard sc = s.card(shoe[0], shoe[1], 90, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, -1);
		sc.lift = 0.04f;
		sc.tilt = 0;
		// dealer
		int dn = p.dealer().length;
		for (int i = 0; i < dn; i++) {
			int code = p.dealer()[i];
			double dealU = dealU(mem, 1000 + i, now, p.startTick(), p.dealerAt()[i], DEAL_MS);
			double flipU = u(now, p.startTick(), p.dealerAt()[i], 120, FLIP_MS);
			if (i == 1) {
				if (code < 0) mem.holeWasHidden = true;
				else if (mem.holeSeenMs < 0) mem.holeSeenMs = mem.holeWasHidden ? now : 0;
				if (code >= 0 && mem.holeSeenMs > 0) flipU = Math.max(0, Math.min(1, (now - mem.holeSeenMs) / FLIP_MS));
			}
			float[] d = BlackjackWorld.dealer(i, Math.max(2, dn));
			dealt(s, shoe[0], shoe[1], d[0], d[1], 0, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, BlackjackWorld.face(code), dealU, flipU);
		}
		// seats (world order: seat index on the kit's crescent)
		int seats = seatCount(be, p);
		for (SeatPub seat : p.seats()) {
			float[] a = BlackjackWorld.seat(seat.seat(), seats);
			int hands = seat.hands().size();
			long staked = 0;
			boolean acting = p.acting() == seat.seat();
			for (int h = 0; h < hands; h++) {
				HandPub hp = seat.hands().get(h);
				staked += hp.bet();
				int n = hp.cards().length;
				for (int k = 0; k < n; k++) {
					float[] pos = BlackjackWorld.seatCard(a, h, hands, k, n);
					double dealU = dealU(mem, seat.seat() * 100L + h * 10L + k, now, p.startTick(), hp.at()[k], DEAL_MS);
					double flipU = u(now, p.startTick(), hp.at()[k], 120, FLIP_MS);
					BoardCard card = dealt(s, shoe[0], shoe[1], pos[0], pos[1], a[2], BlackjackWorld.SEAT_W, BlackjackWorld.SEAT_H,
						BlackjackWorld.face(hp.cards()[k]), dealU, flipU);
					if (card == null) continue;
					if (hp.outcome() > 0 && lost(hp.outcomeId())) card.dim = true;
					if (acting && h == p.actingHand()) card.lift += 0.01f;
				}
			}
			if (hands == 0) staked = seat.bet();
			float[] st = BlackjackWorld.stack(a);
			s.stack(st[0], st[1], staked);
			if (seat.has(BlackjackPub.SETTLED) && seat.net() > 0) {
				double ang = Math.toRadians(a[2]);
				s.stack(st[0] + 0.16f * (float) Math.cos(ang), st[1] + 0.16f * (float) Math.sin(ang), seat.net());
			}
		}
		board(be, s, p, gameTime);
		seatPlates(s, be, (i, n) -> BlackjackWorld.seat(i, seats), (i, pub, ref) -> {
			SeatPub sp = p.seat(i);
			if (sp == null) return;
			ref.acting(p.acting() == i);
			long key = (long) p.hashCode() * 31 + i;
			ref.detail(key, () -> plateLine(sp), plateColor(sp));
		});
	}

	/** The kit's crescent spreads the seats the table really has (seat header), at least the highest seat seen. */
	static int seatCount(BlackjackTableBlockEntity be, BlackjackPub p) {
		int n = Math.max(1, be.clientSeats().length);
		for (SeatPub s : p.seats()) n = Math.max(n, s.seat() + 1);
		return Math.max(n, 5);
	}

	private void board(BlackjackTableBlockEntity be, State s, BlackjackPub p, long gameTime) {
		long second = p.deadline() >= 0 ? Math.max(0, (p.deadline() - gameTime + 19) / 20) : -1;
		boolean hr = be.isHighRoller();
		s.line(BoardState.TITLE, p.min() * 31 + p.max() + (hr ? 7 : 0), () -> {
			Component title = Component.translatable(hr ? "gui.burmaldaholic.blackjack.title_high_roller" : "gui.burmaldaholic.blackjack.title");
			return p.max() > 0 ? Component.translatable("gui.burmaldaholic.cards.plaque", title, Texts.number(p.min()), Texts.number(p.max())) : title;
		}, BoardState.GOLD);
		boolean round = p.dealer().length > 0 && p.phase() != BlackjackPub.BETTING;
		if (!round) {
			s.line(BoardState.STATE, 1000 + second, () -> second >= 0 ? Component.translatable("gui.burmaldaholic.blackjack.v2.board.betting_in", second)
				: Component.translatable("gui.burmaldaholic.blackjack.v2.board.betting"), BoardState.WHITE);
			return;
		}
		s.line(BoardState.STATE, java.util.Arrays.hashCode(p.dealer()), () -> Component.translatable("gui.burmaldaholic.blackjack.v2.board.dealer",
			totalText(p.dealer())), BoardState.WHITE);
		if (p.phase() == BlackjackPub.RESULT) {
			SeatPub best = null;
			for (SeatPub sp : p.seats()) if (sp.has(BlackjackPub.SETTLED) && (best == null || sp.net() > best.net())) best = sp;
			if (best == null) return;
			SeatPub b = best;
			Component who = seatName(be, b.seat());
			s.line(BoardState.RESULT, p.hashCode(), () -> Component.translatable("gui.burmaldaholic.blackjack.v2.board.result", who, signed(b.net())),
				b.net() > 0 ? BoardState.GREEN : b.net() < 0 ? BoardState.RED : BoardState.MUTED);
		} else if (p.phase() == BlackjackPub.DEALER) {
			s.line(BoardState.RESULT, 7, () -> Component.translatable("gui.burmaldaholic.blackjack.v2.board.dealer_draws"), BoardState.MUTED);
		} else if (p.acting() >= 0) {
			Component who = seatName(be, p.acting());
			s.line(BoardState.RESULT, p.acting() * 1000L + second, () -> second >= 0
				? Component.translatable("gui.burmaldaholic.blackjack.v2.board.turn", who, second) : who, BoardState.MUTED);
		}
	}

	private static Component seatName(BlackjackTableBlockEntity be, int seat) {
		var seats = be.clientSeats();
		return seat >= 0 && seat < seats.length && seats[seat] != null ? seats[seat].displayName() : Texts.raw("?"); // literal-ok: placeholder glyph
	}

	private static @Nullable Component plateLine(SeatPub s) {
		long bet = s.hands().isEmpty() ? s.bet() : s.hands().stream().mapToLong(HandPub::bet).sum();
		if (s.has(BlackjackPub.SETTLED)) {
			return Component.translatable("gui.burmaldaholic.blackjack.v2.plate.line", signed(s.net()),
				Component.translatable("gui.burmaldaholic.blackjack.v2.stamp." + word(s)));
		}
		if (bet <= 0) return null;
		if (s.hands().isEmpty() || s.hands().get(0).cards().length == 0) return Texts.number(bet);
		MutableComponent totals = null;
		for (HandPub h : s.hands()) {
			Component t = totalText(h.cards());
			totals = totals == null ? Component.empty().append(t) : Component.translatable("gui.burmaldaholic.cards.join", totals, t);
		}
		return Component.translatable("gui.burmaldaholic.blackjack.v2.plate.line", Texts.number(bet), totals);
	}

	private static int plateColor(SeatPub s) {
		if (!s.has(BlackjackPub.SETTLED)) return BoardState.GOLD;
		return s.net() > 0 ? BoardState.GREEN : s.net() < 0 ? BoardState.RED : BoardState.MUTED;
	}

	private static boolean lost(String outcome) {
		return outcome.equals("bust") || outcome.equals("lose") || outcome.equals("dealer_blackjack") || outcome.equals("surrender");
	}

	/** The deal slide of a card on the table's first-seen anchor (S1: a late card still flies, ≥ 120 ms). */
	private static double dealU(Mem mem, long key, double nowMs, long startTick, int at, double durMs) {
		return mem.deals.u(key, nowMs, (startTick + at) * 50.0, durMs);
	}

	/** Progress 0..1 of a card's beat that starts {@code delayMs} after its deal tick. */
	static double u(double nowMs, long startTick, int at, double delayMs, double durMs) {
		double t = nowMs - (startTick + at) * 50.0 - delayMs;
		return Math.max(0, Math.min(1, t / durMs));
	}

	/** The seat's stamp word: its best hand's outcome (blackjack > even money > win > push > surrender > bust / lose). */
	public static String word(SeatPub s) {
		String best = "lose";
		int rank = -1;
		for (HandPub h : s.hands()) {
			String w = word(h.outcomeId());
			int r = switch (w) {
				case "blackjack" -> 6;
				case "even_money" -> 5;
				case "win" -> 4;
				case "push" -> 3;
				case "surrender" -> 2;
				case "bust" -> 1;
				default -> 0;
			};
			if (r > rank) {
				rank = r;
				best = w;
			}
		}
		return best;
	}

	/** Stamp word of one hand outcome ({@code gui.burmaldaholic.blackjack.v2.stamp.<word>}). */
	public static String word(String outcome) {
		return switch (outcome) {
			case "blackjack" -> "blackjack";
			case "win", "dealer_bust" -> "win";
			case "push" -> "push";
			case "bust" -> "bust";
			case "surrender" -> "surrender";
			case "even_money" -> "even_money";
			default -> "lose";
		};
	}

	public static Component signed(long net) {
		if (net > 0) return Component.translatable("gui.burmaldaholic.blackjack.v2.plus", Texts.number(net));
		if (net < 0) return Component.translatable("gui.burmaldaholic.blackjack.v2.minus", Texts.number(-net));
		return Texts.number(0);
	}

	/** Visible total of a hand (backs ignored); soft hands below 21 as {@code 7/17}. */
	public static Component totalText(int[] codes) {
		List<Card> cards = new ArrayList<>();
		for (int code : codes) if (code >= 0 && code < 52) cards.add(Card.fromCode(code));
		int[] t = Hands.displayTotals(cards);
		return t.length == 2 ? Component.translatable("gui.burmaldaholic.blackjack.v2.total_soft", t[0], t[1]) : Texts.number(t[0]);
	}
}
