package dev.nezo.burmaldaholic.games.uth.client;

import dev.nezo.burmaldaholic.client.board.BoardCard;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import dev.nezo.burmaldaholic.games.uth.present.UthPub;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Ultimate Texas Hold'em in the world on the board kit (docs/architecture/ui-v2.md §7.6, REDESIGN.md §9.3): the
 * crescent felt, the dealer's two cards at the dealer side (face down until the showdown), the board row in the middle,
 * each dealt-in seat's two cards (backs; faces only at the showdown — the public tag never carries a hole card earlier)
 * and its chips, all on the same {@link UthBeats} timeline as the screens. The board reads the game, the street
 * ({@code Place your bets} / {@code Flop} …) and the winners; each occupied seat has a plate (name / chips · Play ×4,
 * Folded or WIN).
 */
public final class UthTableRenderer extends CardTableRenderer<UthTableBlockEntity, CardTableRenderer.State> {
	static final float SEAT_W = BoardGeometry.CARD_W * 0.78f, SEAT_H = BoardGeometry.CARD_H * 0.78f, BOARD_Z = 0.50f;
	private static final Map<UthTableBlockEntity, Cache> CACHE = new WeakHashMap<>();

	private static final class Cache {
		int seq = -1;
		boolean catchUp;
		double lastT = Double.NaN;
	}

	public UthTableRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		for (TableType<UthTableBlockEntity> type : java.util.List.of(UthModule.TABLE, UthModule.HIGH_ROLLER_TABLE, UthModule.PLAYER_BANKED_TABLE)) {
			BlockEntityRendererRegistry.register(type.blockEntityType(), UthTableRenderer::new);
		}
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected BoardGeometry.Felt felt(UthTableBlockEntity be) {
		return BoardGeometry.Felt.CRESCENT;
	}

	static float[] board(int i) {
		float pitch = BoardGeometry.CARD_W + 0.03f;
		return new float[] {0.5f + (i - 2) * pitch, BOARD_Z};
	}

	/** The deck: right of the dealer's cards. */
	static float[] deck() {
		float[] d = BoardGeometry.dealerSlot(BoardGeometry.Felt.CRESCENT, 1, 2);
		return new float[] {d[0] + 0.38f, d[1]};
	}

	private static int seats(UthTableBlockEntity be) {
		return Math.max(1, Math.max(be.clientSeats().length, 1));
	}

	@Override
	protected void fill(UthTableBlockEntity be, State s, float partialTick) {
		UthPub p = be.clientPub();
		String titleKey = be.isHighRoller() ? "gui.burmaldaholic.uth.title_high_roller"
			: be.playerBanked() ? "gui.burmaldaholic.uth.title_player_banked" : "gui.burmaldaholic.uth.title";
		s.line(BoardState.TITLE, titleKey.hashCode(), () -> Component.translatable(titleKey), BoardState.GOLD);
		int n = seats(be);
		seatPlates(s, be, (seat, count) -> BoardGeometry.crescentSeat(seat, n), (seat, pub, ref) -> plateDetail(p, seat, ref));
		if (s.back == BACK_CRIMSON) s.back = BACK_EMERALD; // the UTH deck is emerald in the overworld
		lines(be, s, p);
		if (p.seq() == 0) return;
		Cache c = CACHE.computeIfAbsent(be, k -> new Cache());
		Timeline full = be.clientTimeline();
		double t = s.levelMs - p.startTick() * 50.0;
		if (c.seq != p.seq()) {
			c.seq = p.seq();
			c.lastT = Double.NaN;
			c.catchUp = full != null && t > 0 && TimelineSeed.showSettled(t, full.sharedEndMs());
		}
		Timeline tl = c.catchUp ? null : full;
		int kind = p.kind();
		boolean dealing = kind == UthPub.DEAL && tl != null;
		boolean showdown = kind == UthPub.SHOWDOWN;
		float[] deck = deck();
		// the deck: a squared card stack (visual QA round 2), not a single card lying there
		if (kind != 0) s.deck(deck[0], deck[1], 0, SEAT_W, SEAT_H, BoardGeometry.DECK_CARDS - 7 - 2 * n);
		boolean gathered = kind == UthPub.RESULT && tl != null && beat(tl, UthBeats.GATHER, -1, t) >= 1;
		if (kind == 0 || gathered) {
			sounds(be, s, c, tl, t);
			c.lastT = t;
			return;
		}
		// the dealer's hand (faces from the showdown on)
		for (int j = 0; j < 2; j++) {
			float[] d = BoardGeometry.dealerSlot(BoardGeometry.Felt.CRESCENT, j, 2);
			double dealU = dealing ? beat(tl, UthBeats.DEALER_DEAL, j, t) : 1;
			int face = p.dealer().length == 2 ? p.dealer()[j] : -1;
			double flipU = showdown && tl != null ? beat(tl, UthBeats.DEALER_FLIP, j, t) : 1;
			dealt(s, deck[0], deck[1], d[0], d[1], 0, BoardGeometry.CARD_W, BoardGeometry.CARD_H, face, dealU, flipU);
		}
		// the board: five backs from the deal on, faces as the streets turn them
		for (int i = 0; i < 5; i++) {
			float[] b = board(i);
			double dealU = dealing ? beat(tl, UthBeats.BOARD, i, t) : 1;
			int face = i < p.board().length ? p.board()[i] : -1;
			double flipU = 1;
			if (tl != null && (kind == UthPub.FLOP || kind == UthPub.RIVER)) {
				Beat f = find(tl, UthBeats.FLIP, i);
				if (f != null) flipU = f.progress(t);
			}
			dealt(s, deck[0], deck[1], b[0], b[1], 0, BoardGeometry.CARD_W, BoardGeometry.CARD_H, face, dealU, flipU);
		}
		// the dealt-in seats
		for (int lane = 0; lane < p.seats().length; lane++) {
			float[] a = BoardGeometry.crescentSeat(p.seats()[lane], n);
			if (!p.has(lane, UthPub.FOLDED)) {
				for (int j = 0; j < 2; j++) {
					double dealU = 1;
					if (dealing) {
						for (Beat b : tl.beats()) if (b.kind().equals(UthBeats.DEAL) && b.lane() == lane && b.arg(0) == j) dealU = b.progress(t);
					}
					float off = (j - 0.5f) * SEAT_W * 0.62f;
					double ang = Math.toRadians(a[2]);
					int face = p.cards()[lane * 2 + j];
					double flipU = showdown && tl != null ? beat(tl, UthBeats.REVEAL, -1, t) : 1;
					BoardCard card = dealt(s, deck[0], deck[1], a[0] + off * (float) Math.cos(ang), a[1] + off * (float) Math.sin(ang), a[2] + (j - 0.5f) * 8f,
						SEAT_W, SEAT_H, face, dealU, flipU);
					if (card != null && kind == UthPub.RESULT && !p.has(lane, UthPub.WON) && face >= 0) card.dim = true;
				}
			}
			// the chips between the seat and the dealer
			float sx = a[0] + (0.5f - a[0]) * 0.22f;
			float sz = a[1] + (0.10f - a[1]) * 0.22f - SEAT_H * 0.5f;
			s.stack(sx, sz, p.bets()[lane], 1);
		}
		sounds(be, s, c, tl, t);
		c.lastT = t;
	}

	private static void lines(UthTableBlockEntity be, BoardState s, UthPub p) {
		int kind = p.kind();
		String key = switch (kind) {
			case 0 -> "gui.burmaldaholic.uth.v2.board.bets_open";
			case UthPub.DEAL -> "gui.burmaldaholic.poker.preflop";
			case UthPub.FLOP -> "gui.burmaldaholic.poker.flop";
			case UthPub.RIVER -> "gui.burmaldaholic.poker.river";
			default -> "gui.burmaldaholic.poker.showdown";
		};
		s.line(BoardState.STATE, key.hashCode(), () -> Component.translatable(key), kind == 0 ? BoardState.MUTED : BoardState.WHITE);
		if (kind != UthPub.RESULT) return;
		TablePublic.SeatPub[] names = be.clientSeats();
		long h = p.seq();
		for (int lane = 0; lane < p.seats().length; lane++) if (p.has(lane, UthPub.WON)) h = h * 31 + p.seats()[lane];
		final long key2 = h;
		s.line(BoardState.RESULT, key2, () -> {
			MutableComponent line = Component.empty();
			int shown = 0;
			for (int lane = 0; lane < p.seats().length && shown < 3; lane++) {
				int seat = p.seats()[lane];
				if (!p.has(lane, UthPub.WON) || seat < 0 || seat >= names.length || names[seat] == null) continue;
				if (shown++ > 0) line.append(Texts.raw(", "));
				line.append(names[seat].displayName());
			}
			if (shown == 0) return null;
			return Component.translatable("gui.burmaldaholic.uth.v2.board.result", line, Component.translatable("gui.burmaldaholic.core.v2.stamp.win"));
		}, BoardState.GREEN);
	}

	private static void plateDetail(UthPub p, int seat, BoardState.PlateRef ref) {
		int lane = -1;
		for (int i = 0; i < p.seats().length; i++) if (p.seats()[i] == seat) lane = i;
		if (lane < 0 || p.kind() == 0) return;
		int play = p.flags()[lane] >> UthPub.PLAY_SHIFT;
		long bet = p.bets()[lane];
		if (p.kind() == UthPub.RESULT && p.has(lane, UthPub.WON)) {
			ref.detail(1, () -> Component.translatable("gui.burmaldaholic.core.v2.stamp.win"), BoardState.GREEN);
		} else if (p.has(lane, UthPub.FOLDED)) {
			ref.detail(2, () -> Component.translatable("gui.burmaldaholic.uth.tag.folded"), BoardState.MUTED);
		} else if (play > 0) {
			ref.detail(1000 + bet * 8 + play, () -> Component.empty().append(Texts.number(bet)).append(Texts.raw(" · "))
				.append(Component.translatable("gui.burmaldaholic.uth.tag.play", Texts.number(play))), BoardState.GOLD);
		} else if (bet > 0) {
			ref.detail(1000 + bet * 8, () -> Texts.number(bet), BoardState.GOLD);
		}
	}

	private static @Nullable Beat find(Timeline tl, String kind, int lane) {
		for (Beat b : tl.beats()) if (b.kind().equals(kind) && b.lane() == lane) return b;
		return null;
	}

	private static double beat(@Nullable Timeline tl, String kind, int lane, double t) {
		if (tl == null) return 1;
		Beat b = find(tl, kind, lane);
		return b == null ? 1 : b.progress(t);
	}

	private static void sounds(UthTableBlockEntity be, State s, Cache c, @Nullable Timeline tl, double t) {
		if (tl == null || Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) return;
		for (Beat b : tl.beats()) {
			if (b.at() <= c.lastT || b.at() > t) continue;
			switch (b.kind()) {
				case UthBeats.DEAL, UthBeats.DEALER_DEAL -> tableSound(be.getBlockPos(), s, "card_deal", 0.35f, 1f);
				case UthBeats.BOARD -> {
					if (b.lane() == 0) tableSound(be.getBlockPos(), s, "card_slide", 0.4f, 1f);
				}
				case UthBeats.FLIP, UthBeats.DEALER_FLIP -> tableSound(be.getBlockPos(), s, "card_flip", 0.4f, 1f);
				case UthBeats.GATHER -> tableSound(be.getBlockPos(), s, "card_gather", 0.4f, 1f);
				default -> {
				}
			}
		}
	}
}
