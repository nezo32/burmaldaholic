package dev.nezo.burmaldaholic.gametest.v2.board;

import dev.nezo.burmaldaholic.client.board.BoardCard;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A blackjack round mid-play for the board kit's captures (docs/design/visual/v2/world_blackjack_concept.png): the
 * dealer's 10 + hole card, four seats (a bust, a blackjack, the viewer on 15 and his turn, a 17), their stacks and the
 * plates. Names come from the table's public seat header where seated, else the concept's names. Client-only (a
 * {@link dev.nezo.burmaldaholic.client.board.BoardRenderer#demo} filler); never reads anything private.
 */
final class BoardDemo {
	/** Seats used (of 7) and their hands. */
	private static final int[] SEATS = {1, 2, 4, 5};
	private static final String[] NAMES = {"Creeper42", "Mira", "Steve", "Axolotl Al"};
	private static final int[][] HANDS = {{card(10, 0), card(6, 3), card(9, 1)}, {card(14, 1), card(13, 0)}, {card(9, 2), card(6, 0)}, {card(10, 3), card(7, 2)}};
	private static final long[] BETS = {25, 50, 50, 10};
	private static final int SEAT_COUNT = 7;

	private BoardDemo() {}

	/** Card id: rank 2..14 (ace 14), suit 0 ♠ 1 ♥ 2 ♦ 3 ♣. */
	static int card(int rank, int suit) {
		return (rank - 2) * 4 + suit;
	}

	/** The demo filler; {@code viewerSeat} = which demo seat is "you" (−1 none). */
	static void blackjack(BlockEntity be, BoardState s, int viewerSeat) {
		Component table = be.getBlockState().getBlock().getName();
		s.line(BoardState.TITLE, 1, () -> Component.translatable("gui.burmaldaholic.core.board.title_limits", table, Texts.number(1), Texts.number(100)),
			BoardState.GOLD);
		// the dealer's total is the prominent line (2×); the dealer's cards repeat on the board at a readable size
		s.line(BoardState.STATE, 2, () -> Component.translatable("gui.burmaldaholic.core.board.dealer", Texts.number(10)), BoardState.WHITE, 2f);
		s.boardCards(card(10, 0), -1);
		s.line(BoardState.RESULT, 3, () -> Component.translatable("gui.burmaldaholic.core.board.turn_seconds", Texts.raw(name(be, 2)), Texts.number(18)),
			BoardState.MUTED);
		// dealer: the 10 up, the hole card down; the shoe at the dealer's right
		float[] d0 = BoardGeometry.dealerSlot(s.felt, 0, 2), d1 = BoardGeometry.dealerSlot(s.felt, 1, 2);
		s.card(d0[0], d0[1], 0, card(10, 0));
		s.card(d1[0], d1[1], 0, -1);
		BoardCard shoe = s.card(BoardGeometry.dealerSlot(s.felt, 0, 1)[0] + 0.62f, d0[1] - 0.02f, 90, -1);
		shoe.tilt = 0;
		for (int k = 0; k < SEATS.length; k++) {
			float[] p = BoardGeometry.crescentSeat(SEATS[k], SEAT_COUNT);
			double a = Math.toRadians(p[2]);
			float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
			int[] hand = HANDS[k];
			for (int j = 0; j < hand.length; j++) {
				float lx = (j - (hand.length - 1) / 2f) * 0.085f;
				float lz = -0.02f * j;
				BoardCard c = s.card(p[0] + lx * cos - lz * sin, p[1] + lx * sin + lz * cos, p[2] + (j - 0.5f) * 5f, hand[j]);
				if (k == 0) c.dim = true; // the bust hand greys out
			}
			// the bet circle in front of the cards (toward the player)
			float sz = 0.24f;
			s.stack(p[0] - sz * sin, p[1] + sz * cos, BETS[k]);
			float[] an = BoardGeometry.plateAnchor(s.felt, p[0], p[1]);
			BoardState.PlateRef plate = s.plate(SEATS[k], an[0], an[1], an[2]);
			if (plate == null) continue;
			String name = name(be, k);
			plate.name(name.hashCode(), () -> Texts.raw(name), 0).viewer(k == viewerSeat).acting(k == 2).cards(hand);
			long bet = BETS[k];
			switch (k) {
				case 0 -> plate.detail(10, () -> Component.translatable("gui.burmaldaholic.core.board.bet_total", Texts.number(bet), Component.translatable(
					"gui.burmaldaholic.fx.tier.loss")), BoardState.RED);
				case 1 -> plate.detail(11, () -> Component.translatable("gui.burmaldaholic.core.board.bet_total", Texts.raw("+75"), Texts.raw("BLACKJACK")),
					BoardState.GOLD);
				case 2 -> plate.detail(12, () -> Component.translatable("gui.burmaldaholic.core.board.bet_total", Texts.number(bet), Texts.number(15)),
					BoardState.GOLD, 2f); // the acting seat: its status is the prominent line
				default -> plate.detail(13, () -> Component.translatable("gui.burmaldaholic.core.board.bet_total", Texts.number(bet), Texts.number(17)),
					BoardState.GOLD);
			}
		}
	}

	/** The seated name for demo seat {@code k} from the public header (real players / bots), else the concept's. */
	private static String name(BlockEntity be, int k) {
		if (be instanceof CasinoTableBlockEntity t) {
			TablePublic.SeatPub[] seats = t.clientSeats();
			int seat = SEATS[k];
			if (seat < seats.length && seats[seat] != null && !seats[seat].bot()) return seats[seat].name();
		}
		return NAMES[k];
	}
}
