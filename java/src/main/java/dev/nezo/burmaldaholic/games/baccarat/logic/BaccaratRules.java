package dev.nezo.burmaldaholic.games.baccarat.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Punto Banco tableau (GAME_DESIGN §20.1, §20.3). PURE. The rules are mechanical: nobody decides
 * anything, so a whole coup can be drawn at once from the shoe.
 */
public final class BaccaratRules {
	private BaccaratRules() {}

	public enum Side {
		PLAYER("player"), BANKER("banker"), TIE("tie");

		private final String id;

		Side(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Side byOrdinal(int i) {
			return values()[Math.floorMod(i, 3)];
		}
	}

	/** Hand total: sum of card points mod 10. */
	public static int total(List<Card> cards) {
		int t = 0;
		for (Card c : cards) {
			t += c.points();
		}
		return t % 10;
	}

	/** Two-card 8 or 9. */
	public static boolean isNatural(int twoCardTotal) {
		return twoCardTotal >= 8;
	}

	/** §20.3 rule 2: Player draws on 0–5, stands on 6–7 (never called with a natural). */
	public static boolean playerDraws(int playerTotal) {
		return playerTotal <= 5;
	}

	/**
	 * §20.3 rules 3–4: does the Banker draw?
	 *
	 * @param bankerTotal Banker's two-card total (0–7; naturals are handled before)
	 * @param playerThird point value 0–9 of Player's third card, or -1 if Player stood
	 */
	public static boolean bankerDraws(int bankerTotal, int playerThird) {
		if (playerThird < 0) {
			return bankerTotal <= 5;
		}
		return switch (bankerTotal) {
			case 0, 1, 2 -> true;
			case 3 -> playerThird != 8;
			case 4 -> playerThird >= 2 && playerThird <= 7;
			case 5 -> playerThird >= 4 && playerThird <= 7;
			case 6 -> playerThird == 6 || playerThird == 7;
			default -> false;
		};
	}

	/** Deals a full coup in the §20.1 order (P1, B1, P2, B2, then third cards). */
	public static Coup deal(Supplier<Card> draw) {
		List<Card> player = new ArrayList<>(3);
		List<Card> banker = new ArrayList<>(3);
		player.add(draw.get());
		banker.add(draw.get());
		player.add(draw.get());
		banker.add(draw.get());
		int p = total(player);
		int b = total(banker);
		if (!isNatural(p) && !isNatural(b)) {
			int playerThird = -1;
			if (playerDraws(p)) {
				Card third = draw.get();
				player.add(third);
				playerThird = third.points();
			}
			if (bankerDraws(b, playerThird)) {
				banker.add(draw.get());
			}
		}
		return new Coup(player, banker);
	}

	/** A dealt coup: 2–3 cards per hand. */
	public record Coup(List<Card> player, List<Card> banker) {
		public Coup {
			player = List.copyOf(player);
			banker = List.copyOf(banker);
		}

		public int playerTotal() {
			return total(player);
		}

		public int bankerTotal() {
			return total(banker);
		}

		public Side winner() {
			int p = playerTotal();
			int b = bankerTotal();
			return p > b ? Side.PLAYER : b > p ? Side.BANKER : Side.TIE;
		}

		/** First two Player cards of the same rank (K♠K♥ yes, K+Q no). */
		public boolean playerPair() {
			return player.get(0).rank() == player.get(1).rank();
		}

		public boolean bankerPair() {
			return banker.get(0).rank() == banker.get(1).rank();
		}

		public boolean playerNatural() {
			return isNatural(total(player.subList(0, 2)));
		}

		public boolean bankerNatural() {
			return isNatural(total(banker.subList(0, 2)));
		}

		/** The winning hand is a two-card 9 (§20.7 natural-9 flourish / baccarat_natural). */
		public boolean winnerNaturalNine() {
			return switch (winner()) {
				case PLAYER -> player.size() == 2 && playerTotal() == 9;
				case BANKER -> banker.size() == 2 && bankerTotal() == 9;
				case TIE -> false;
			};
		}

		/** Cards in deal order: P1, B1, P2, B2, [P3], [B3]. */
		public List<Card> dealOrder() {
			List<Card> out = new ArrayList<>(6);
			out.add(player.get(0));
			out.add(banker.get(0));
			out.add(player.get(1));
			out.add(banker.get(1));
			if (player.size() > 2) {
				out.add(player.get(2));
			}
			if (banker.size() > 2) {
				out.add(banker.get(2));
			}
			return out;
		}

		public int[] playerCodes() {
			return player.stream().mapToInt(Card::code).toArray();
		}

		public int[] bankerCodes() {
			return banker.stream().mapToInt(Card::code).toArray();
		}

		public static Coup fromCodes(int[] player, int[] banker) {
			List<Card> p = new ArrayList<>();
			List<Card> b = new ArrayList<>();
			for (int c : player) {
				p.add(Card.fromCode(c));
			}
			for (int c : banker) {
				b.add(Card.fromCode(c));
			}
			if (p.size() < 2 || p.size() > 3 || b.size() < 2 || b.size() > 3) {
				throw new IllegalArgumentException("bad coup");
			}
			return new Coup(p, b);
		}
	}
}
