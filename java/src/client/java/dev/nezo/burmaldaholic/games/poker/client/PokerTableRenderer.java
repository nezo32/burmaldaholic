package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.board.BoardCard;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.PokerText;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBeats;
import dev.nezo.burmaldaholic.games.poker.present.PokerPub;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Hold'em in the world on the board kit (docs/architecture/ui-v2.md §7.6, REDESIGN.md §9.3): the 2 × 2 oval felt, the
 * board in a row across the middle (the full 1.5 × card size), every dealt-in seat's two cards at its spot on the oval
 * (backs; faces only when the public tag has them — exposed at an all-in or shown at the showdown, so a hole card never
 * leaks), the bets toward the centre, the pot in front of the board sliding to the winner on the award beat, the
 * dealer button; the board reads {@code Texas Hold'em} / {@code Pot 120 · Turn} / {@code Alex to act} (→ the winner),
 * and each occupied seat has a plate (name / bet, Fold, All-in). Everything moves on the same {@link PokerBeats}
 * timeline as the players' screens.
 */
public final class PokerTableRenderer extends CardTableRenderer<PokerTableBlockEntity, CardTableRenderer.State> {
	/** Seat cards are a little smaller than the board's (they sit on the rail side of the oval). */
	static final float SEAT_W = BoardGeometry.Holdem.SEAT_W, SEAT_H = BoardGeometry.Holdem.SEAT_H;
	static final float BOARD_Z = BoardGeometry.Holdem.BOARD_Z, SEAT_R = BoardGeometry.Holdem.SEAT_R, BET_R = BoardGeometry.Holdem.BET_R,
		POT_Z = BoardGeometry.Holdem.POT_Z;
	/** Cards are dealt from the deck (full table: the squared stack at the oval's right end; compact: the centre). */
	static final float DECK_X = 0.5f, DECK_Z = 0.5f;
	private static final Map<PokerTableBlockEntity, Cache> CACHE = new WeakHashMap<>();

	private static final class Cache {
		int seq = -1;
		@Nullable Timeline tl;
		boolean catchUp;
		double lastT = Double.NaN;
	}

	public PokerTableRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(PokerModule.TABLE.blockEntityType(), PokerTableRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected BoardGeometry.Felt felt(PokerTableBlockEntity be) {
		return BoardGeometry.Felt.OVAL;
	}

	/** Board slot {@code i} (centre x, z). */
	static float[] board(int i) {
		return BoardGeometry.Holdem.board(i);
	}

	/** Seat spot of table seat {@code seat} of {@code n} on the oval: {x, z, yaw}; seat 0 at the front. */
	static float[] seat(int seat, int n, float r) {
		return BoardGeometry.Holdem.seat(seat, n, r);
	}

	/** A seat's bet: toward the centre, kept off the board row. */
	static float[] bet(int seat, int n) {
		return BoardGeometry.Holdem.bet(seat, n);
	}

	static @Nullable Timeline timeline(PokerPub p) {
		return PokerBeats.segment(PokerPub.kindName(p.kind()), p.args(), p.seed());
	}

	@Override
	protected void fill(PokerTableBlockEntity be, State s, float partialTick) {
		PokerPub p = be.clientPub();
		int size = Math.max(2, p.seatCount() > 0 ? p.seatCount() : be.clientSeats().length);
		s.line(BoardState.TITLE, 1, () -> PokerText.gui("title"), BoardState.GOLD);
		seatPlates(s, be, (seat, n) -> seat(seat, size, SEAT_R), (seat, pub, ref) -> plateDetail(p, seat, ref));
		if (p.seatCount() == 0) {
			s.line(BoardState.STATE, 2, () -> PokerText.gui("v2.board.waiting"), BoardState.MUTED);
			return;
		}
		Cache c = CACHE.computeIfAbsent(be, k -> new Cache());
		if (c.seq != p.seq()) {
			c.seq = p.seq();
			c.tl = timeline(p);
			c.lastT = Double.NaN;
			c.catchUp = false;
			double t0 = s.levelMs - p.startTick() * 50.0;
			if (c.tl != null && t0 > 0 && TimelineSeed.showSettled(t0, c.tl.sharedEndMs())) c.catchUp = true;
		}
		Timeline tl = c.catchUp ? null : c.tl;
		double t = s.levelMs - p.startTick() * 50.0;
		String kind = PokerPub.kindName(p.kind());
		lines(be, s, p);
		// the deck: a squared card stack (visual QA round 2), only where the full table's felt has room for it
		float dx = s.full ? BoardGeometry.Holdem.DECK_X : DECK_X, dz = s.full ? BoardGeometry.Holdem.DECK_Z : DECK_Z;
		if (s.full) s.deck(dx, dz, BoardGeometry.Holdem.DECK_YAW, BoardGeometry.CARD_W, BoardGeometry.CARD_H, deckLeft(p, size));
		// board
		for (int i = 0; i < p.board().length; i++) {
			float[] b = board(i);
			double dealU = 1;
			double flipU = 1;
			if (tl != null && (kind.equals("street") || kind.equals("finish"))) {
				Beat slide = PokerBeats.find(tl, PokerBeats.SLIDE, i);
				Beat flip = PokerBeats.find(tl, PokerBeats.FLIP, i);
				if (slide != null) dealU = slide.progress(t);
				if (flip != null) flipU = flip.progress(t);
			}
			dealt(s, dx, dz, b[0], b[1], 0, BoardGeometry.CARD_W, BoardGeometry.CARD_H, p.board()[i], dealU, flipU);
		}
		// seats (hand index k = k-th dealt seat in seat order)
		int k = 0;
		for (int seat = 0; seat < size && seat < p.seatCount(); seat++) {
			if (!p.has(seat, PokerPub.DEALT)) continue;
			int handIndex = k++;
			float[] a = seat(seat, size, SEAT_R - SEAT_H * 0.5f);
			boolean folded = p.has(seat, PokerPub.FOLDED);
			if (!folded || kind.equals("deal")) {
				for (int j = 0; j < 2; j++) {
					double dealU = 1;
					if (tl != null && kind.equals("deal")) {
						for (Beat b : tl.beats()) {
							if (b.kind().equals(PokerBeats.DEAL) && b.lane() == handIndex && b.arg(0) == j) dealU = b.progress(t);
						}
					}
					if (folded && dealU >= 1) continue;
					float off = (j - 0.5f) * SEAT_W * 0.62f;
					double ang = Math.toRadians(a[2]);
					float cx = a[0] + off * (float) Math.cos(ang);
					float cz = a[1] + off * (float) Math.sin(ang);
					int face = p.card(seat, j);
					double flipU = 1;
					if (face >= 0 && tl != null && kind.equals("finish")) flipU = showFlip(tl, t);
					BoardCard card = dealt(s, dx, dz, cx, cz, a[2] + (j - 0.5f) * 8f, SEAT_W, SEAT_H, face, dealU, flipU);
					if (card != null && p.has(seat, PokerPub.WINNER)) card.lift += 0.01f;
				}
			}
			float[] bet = bet(seat, size);
			s.stack(bet[0], bet[1], p.bets()[seat], 1);
			if (seat == p.button()) {
				float[] btn = seat(seat, size, SEAT_R - SEAT_H - 0.08f);
				s.text(btn[0] + 0.14f, BoardGeometry.TOP + 0.06f, btn[1], DEALER.getVisualOrderText(), BoardState.WHITE);
			}
		}
		// the pot: in front of the board, sliding to the winners on the award beat
		if (p.pot() > 0) {
			float px = 0.5f;
			float pz = POT_Z;
			if (tl != null && kind.equals("finish")) {
				Beat award = PokerBeats.find(tl, PokerBeats.AWARD, 0);
				int winner = firstWinner(p);
				if (award != null && winner >= 0 && t >= award.at()) {
					float[] w = bet(winner, size);
					double u = tweened(s) ? award.progress(t) : 1;
					px += (float) ((w[0] - px) * u);
					pz += (float) ((w[1] - pz) * u);
				}
			}
			s.stack(px, pz, p.pot(), 1);
		}
		sounds(be, s, c, tl, t);
		c.lastT = t;
	}

	private static final Component DEALER = Texts.raw("D");

	/** Cards left in the deck: 52 minus the board and two per dealt-in seat. */
	static int deckLeft(PokerPub p, int size) {
		int dealt = p.board().length;
		for (int seat = 0; seat < size && seat < p.seatCount(); seat++) if (p.has(seat, PokerPub.DEALT)) dealt += 2;
		return Math.max(1, BoardGeometry.DECK_CARDS - dealt);
	}

	/** Board lines: {@code Pot 120 · Turn}; {@code Alex to act} / the winner. */
	private static void lines(PokerTableBlockEntity be, BoardState s, PokerPub p) {
		int street = p.board().length;
		String streetKey = street == 0 ? "preflop" : street == 3 ? "flop" : street == 4 ? "turn" : "river";
		if (p.pot() > 0) {
			s.line(BoardState.STATE, p.pot() * 8 + street, () -> PokerText.gui("v2.board.pot_street", Texts.number(p.pot()), PokerText.gui(streetKey)),
				BoardState.WHITE);
		} else {
			s.line(BoardState.STATE, -3, () -> PokerText.gui("v2.board.next_hand"), BoardState.MUTED);
		}
		TablePublic.SeatPub[] names = be.clientSeats();
		int winner = firstWinner(p);
		int acting = -1;
		for (int i = 0; i < p.seatCount(); i++) if (p.has(i, PokerPub.ACTING)) acting = i;
		if (winner >= 0 && winner < names.length && names[winner] != null) {
			TablePublic.SeatPub w = names[winner];
			s.line(BoardState.RESULT, 1_000_000L * p.seq() + winner, () -> PokerText.gui("v2.board.winner", w.displayName(), Texts.number(p.pot())),
				BoardState.GREEN);
		} else if (acting >= 0 && acting < names.length && names[acting] != null) {
			TablePublic.SeatPub a = names[acting];
			s.line(BoardState.RESULT, 2_000_000L * p.seq() + acting, () -> PokerText.gui("v2.board.acting", a.displayName()), BoardState.MUTED);
		}
	}

	private static void plateDetail(PokerPub p, int seat, BoardState.PlateRef ref) {
		if (seat >= p.seatCount()) return;
		ref.acting(p.has(seat, PokerPub.ACTING));
		if (p.has(seat, PokerPub.WINNER)) ref.detail(7, () -> Component.translatable("gui.burmaldaholic.core.v2.stamp.win"), BoardState.GREEN);
		else if (p.has(seat, PokerPub.FOLDED)) ref.detail(1, () -> PokerText.gui("tag.fold"), BoardState.MUTED);
		else if (p.has(seat, PokerPub.ALL_IN)) ref.detail(2, () -> PokerText.gui("all_in_tag"), BoardState.RED);
		else if (p.bets()[seat] > 0) {
			long bet = p.bets()[seat];
			ref.detail(100 + bet, () -> Texts.number(bet), BoardState.GOLD);
		}
	}

	private static int firstWinner(PokerPub p) {
		for (int i = 0; i < p.seatCount(); i++) if (p.has(i, PokerPub.WINNER)) return i;
		return -1;
	}

	/** Face-up hole cards turn over with the exposure / their show beat. */
	private static double showFlip(Timeline tl, double t) {
		Beat expose = PokerBeats.find(tl, PokerBeats.EXPOSE, -1);
		if (expose != null) return expose.progress(t);
		Beat show = PokerBeats.find(tl, PokerBeats.SHOW, 0);
		return show == null ? 1 : Math.min(1, (t - show.at()) / PokerBeats.FLIP_MS);
	}

	private static void sounds(PokerTableBlockEntity be, State s, Cache c, @Nullable Timeline tl, double t) {
		if (tl == null || Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) return;
		for (Beat b : tl.beats()) {
			if (b.at() <= c.lastT || b.at() > t) continue;
			switch (b.kind()) {
				case PokerBeats.DEAL -> tableSound(be.getBlockPos(), s, "card_deal", 0.35f, 1f);
				case PokerBeats.FLIP -> tableSound(be.getBlockPos(), s, "card_flip", 0.4f, 1f + 0.06f * Math.max(0, b.lane()) % 3);
				case PokerBeats.AWARD -> tableSound(be.getBlockPos(), s, "pot_win", 0.5f, 1f);
				case PokerBeats.GATHER -> tableSound(be.getBlockPos(), s, "chip_sweep", 0.35f, 1f);
				default -> {
				}
			}
		}
	}
}
