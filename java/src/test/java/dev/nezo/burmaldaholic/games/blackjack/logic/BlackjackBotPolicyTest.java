package dev.nezo.burmaldaholic.games.blackjack.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackBotPolicy.Act;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackBotPolicy.BetMemory;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackBotPolicy.View;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Action;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Offer;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Phase;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Seat;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.SeatBet;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Turn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Blackjack atmosphere bots (BOTS.md §4.7, test plan §12.1 / §12.3); same vectors as Bedrock
 * {@code games/blackjack/logic/bots.test.ts}.
 *
 * <p>Shared-shoe card order (documented effect): bot hands take REAL cards from the same shoe, so with bots
 * seated a human's hand and the dealer's draws come from different positions of the same shuffled shoe.
 * The shuffle itself (the game rng stream) is identical with or without bots, and a human's result is the
 * same function of the cards they and the dealer actually receive — bots change WHICH cards arrive, never
 * the rules, payouts, peek, the human's options or anyone's money.
 */
class BlackjackBotPolicyTest {
	private static final BlackjackRules S17_DAS = BlackjackRules.DEFAULT;
	private static final String UPS = "23456789TA";
	private static final List<Action> LEGAL_2 = List.of(Action.HIT, Action.STAND, Action.DOUBLE);
	private static final List<Action> LEGAL_PAIR = List.of(Action.HIT, Action.STAND, Action.DOUBLE, Action.SPLIT);

	// Canonical 4–8 deck, S17, DAS, no surrender chart (dealer 2 3 4 5 6 7 8 9 T A).
	// H hit, S stand, D double (else hit), Ds double (else stand), P split.
	private static final Map<Integer, String> HARD = Map.ofEntries(
		Map.entry(5, "H H H H H H H H H H"), Map.entry(6, "H H H H H H H H H H"), Map.entry(7, "H H H H H H H H H H"),
		Map.entry(8, "H H H H H H H H H H"), Map.entry(9, "H D D D D H H H H H"), Map.entry(10, "D D D D D D D D H H"),
		Map.entry(11, "D D D D D D D D D H"), Map.entry(12, "H H S S S H H H H H"), Map.entry(13, "S S S S S H H H H H"),
		Map.entry(14, "S S S S S H H H H H"), Map.entry(15, "S S S S S H H H H H"), Map.entry(16, "S S S S S H H H H H"),
		Map.entry(17, "S S S S S S S S S S"), Map.entry(18, "S S S S S S S S S S"), Map.entry(19, "S S S S S S S S S S"),
		Map.entry(20, "S S S S S S S S S S"), Map.entry(21, "S S S S S S S S S S"));
	private static final Map<Integer, String> SOFT = Map.of(
		13, "H H H D D H H H H H", 14, "H H H D D H H H H H", 15, "H H D D D H H H H H", 16, "H H D D D H H H H H",
		17, "H D D D D H H H H H", 18, "S Ds Ds Ds Ds S S H H H", 19, "S S S S S S S S S S", 20, "S S S S S S S S S S",
		21, "S S S S S S S S S S");
	private static final Map<String, String> PAIRS = Map.of(
		"2", "P P P P P P H H H H", "3", "P P P P P P H H H H", "4", "H H H P P H H H H H", "5", "D D D D D D D D H H",
		"6", "P P P P P H H H H H", "7", "P P P P P P H H H H", "8", "P P P P P P P P P P", "9", "P P P P P S P P S S",
		"T", "S S S S S S S S S S", "A", "P P P P P P P P P P");
	/** A two-card (hard 21: three-card) non-pair hand for a hard total. */
	private static final Map<Integer, String[]> HARD_HANDS = Map.ofEntries(
		Map.entry(5, new String[] {"2H", "3D"}), Map.entry(6, new String[] {"2H", "4D"}), Map.entry(7, new String[] {"2H", "5D"}),
		Map.entry(8, new String[] {"3H", "5D"}), Map.entry(9, new String[] {"2H", "7D"}), Map.entry(10, new String[] {"2H", "8D"}),
		Map.entry(11, new String[] {"2H", "9D"}), Map.entry(12, new String[] {"2H", "KD"}), Map.entry(13, new String[] {"3H", "KD"}),
		Map.entry(14, new String[] {"4H", "KD"}), Map.entry(15, new String[] {"5H", "KD"}), Map.entry(16, new String[] {"6H", "KD"}),
		Map.entry(17, new String[] {"7H", "KD"}), Map.entry(18, new String[] {"8H", "KD"}), Map.entry(19, new String[] {"9H", "KD"}),
		Map.entry(20, new String[] {"QH", "KD"}), Map.entry(21, new String[] {"5H", "6D", "KC"}));

	private record Cell(List<Card> hand, Card up, List<Action> legal, Action want, String label) {}

	static Card c(String id) {
		return BlackjackRoundTest.c(id);
	}

	private static List<Card> cards(String... ids) {
		List<Card> out = new ArrayList<>();
		for (String id : ids) {
			out.add(c(id));
		}
		return out;
	}

	private static Card up(char u) {
		return c((u == 'T' ? "K" : String.valueOf(u)) + "S");
	}

	private static Action expect(String want, List<Action> legal) {
		boolean dbl = legal.contains(Action.DOUBLE);
		return switch (want) {
			case "H" -> Action.HIT;
			case "S" -> Action.STAND;
			case "D" -> dbl ? Action.DOUBLE : Action.HIT;
			case "Ds" -> dbl ? Action.DOUBLE : Action.STAND;
			case "P" -> Action.SPLIT;
			default -> throw new IllegalArgumentException(want);
		};
	}

	private static List<Cell> chart() {
		List<Cell> out = new ArrayList<>();
		HARD.forEach((total, row) -> {
			List<Card> hand = cards(HARD_HANDS.get(total));
			List<Action> legal = hand.size() == 2 ? LEGAL_2 : List.of(Action.HIT, Action.STAND);
			String[] w = row.split(" ");
			for (int i = 0; i < 10; i++) {
				out.add(new Cell(hand, up(UPS.charAt(i)), legal, expect(w[i], legal), "hard " + total + " v " + UPS.charAt(i)));
			}
		});
		SOFT.forEach((total, row) -> {
			int other = total - 11;
			List<Card> hand = cards("AH", (other == 10 ? "K" : String.valueOf(other)) + "D");
			String[] w = row.split(" ");
			for (int i = 0; i < 10; i++) {
				out.add(new Cell(hand, up(UPS.charAt(i)), LEGAL_2, expect(w[i], LEGAL_2), "soft " + total + " v " + UPS.charAt(i)));
			}
		});
		PAIRS.forEach((r, row) -> {
			String rank = r.equals("T") ? "K" : r;
			List<Card> hand = cards(rank + "H", rank + "D");
			String[] w = row.split(" ");
			for (int i = 0; i < 10; i++) {
				out.add(new Cell(hand, up(UPS.charAt(i)), LEGAL_PAIR, expect(w[i], LEGAL_PAIR), "pair " + r + " v " + UPS.charAt(i)));
			}
		});
		return out;
	}

	private static BotProfile bot(BotDifficulty level) {
		return new BotProfile("b" + level.id(), "creeper42", level, Personality.TAG);
	}

	private static View turn(List<Card> hand, Card up, List<Action> legal, BlackjackRules rules) {
		return new View.Turn(hand, up, legal, rules, 1);
	}

	private static Action play(BotDifficulty level, View view, BotRng rng) {
		return ((Act.Play) BlackjackBotPolicy.INSTANCE.act(bot(level), view, null, rng)).action();
	}

	// ---- HARD = perfect basic strategy ------------------------------------------------------------

	@Test
	void chartCoversHardSoftAndPairsAgainstEveryUpCard() {
		assertEquals(360, chart().size());
	}

	@Test
	void hardEqualsEveryChartCell() {
		BotRng rng = BotRng.seeded(1);
		for (Cell cell : chart()) {
			assertEquals(cell.want(), play(BotDifficulty.HARD, turn(cell.hand(), cell.up(), cell.legal(), S17_DAS), rng), cell.label());
		}
	}

	@Test
	void hardAgreesWithTheHouseEdgeBasicStrategyOnEveryCell() {
		for (Cell cell : chart()) {
			assertEquals(BasicStrategy.choose(cell.hand(), cell.up(), cell.legal()),
				BlackjackBotPolicy.tableStrategy(cell.hand(), cell.up(), cell.legal(), S17_DAS), cell.label());
		}
	}

	@Test
	void hardFollowsTheTableRules() {
		List<Action> surr = List.of(Action.HIT, Action.STAND, Action.DOUBLE, Action.SURRENDER);
		List<Action> surrPair = List.of(Action.HIT, Action.STAND, Action.DOUBLE, Action.SPLIT, Action.SURRENDER);
		BlackjackRules h17 = S17_DAS.withDealerHitsSoft17(true);
		BlackjackRules noDas = S17_DAS.withDoubleAfterSplit(false);
		BlackjackRules ls = S17_DAS.withLateSurrender(true);
		BlackjackRules h17ls = h17.withLateSurrender(true);
		// H17: 11 v A double, soft 19 v 6 double, soft 18 v 2 double
		assertEquals(Action.DOUBLE, BlackjackBotPolicy.tableStrategy(cards("2H", "9D"), up('A'), LEGAL_2, h17));
		assertEquals(Action.HIT, BlackjackBotPolicy.tableStrategy(cards("2H", "9D"), up('A'), LEGAL_2, S17_DAS));
		assertEquals(Action.DOUBLE, BlackjackBotPolicy.tableStrategy(cards("AH", "8D"), up('6'), LEGAL_2, h17));
		assertEquals(Action.STAND, BlackjackBotPolicy.tableStrategy(cards("AH", "8D"), up('6'), LEGAL_2, S17_DAS));
		assertEquals(Action.DOUBLE, BlackjackBotPolicy.tableStrategy(cards("AH", "7D"), up('2'), LEGAL_2, h17));
		// no DAS: 2-2 / 3-3 v 2–3 hit, 4-4 never, 6-6 v 2 hit
		assertEquals(Action.HIT, BlackjackBotPolicy.tableStrategy(cards("2H", "2D"), up('2'), LEGAL_PAIR, noDas));
		assertEquals(Action.SPLIT, BlackjackBotPolicy.tableStrategy(cards("3H", "3D"), up('4'), LEGAL_PAIR, noDas));
		assertEquals(Action.HIT, BlackjackBotPolicy.tableStrategy(cards("4H", "4D"), up('5'), LEGAL_PAIR, noDas));
		assertEquals(Action.HIT, BlackjackBotPolicy.tableStrategy(cards("6H", "6D"), up('2'), LEGAL_PAIR, noDas));
		// late surrender: 16 v 9–A, 15 v T; H17 adds 15 v A, 17 v A, 8-8 v A
		assertEquals(Action.SURRENDER, BlackjackBotPolicy.tableStrategy(cards("6H", "KD"), up('9'), surr, ls));
		assertEquals(Action.SURRENDER, BlackjackBotPolicy.tableStrategy(cards("5H", "KD"), up('T'), surr, ls));
		assertEquals(Action.HIT, BlackjackBotPolicy.tableStrategy(cards("5H", "KD"), up('A'), surr, ls));
		assertEquals(Action.SURRENDER, BlackjackBotPolicy.tableStrategy(cards("5H", "KD"), up('A'), surr, h17ls));
		assertEquals(Action.SURRENDER, BlackjackBotPolicy.tableStrategy(cards("7H", "KD"), up('A'), surr, h17ls));
		assertEquals(Action.STAND, BlackjackBotPolicy.tableStrategy(cards("7H", "KD"), up('A'), surr, ls));
		assertEquals(Action.SURRENDER, BlackjackBotPolicy.tableStrategy(cards("8H", "8D"), up('A'), surrPair, h17ls));
		assertEquals(Action.SPLIT, BlackjackBotPolicy.tableStrategy(cards("8H", "8D"), up('A'), surrPair, ls));
		// 16 without surrender offered: hit
		assertEquals(Action.HIT, BlackjackBotPolicy.tableStrategy(cards("6H", "KD"), up('9'), LEGAL_2, ls));
	}

	@Test
	void hardResplitsWhileLegalAndStandsOnSplitAces() {
		assertEquals(Action.SPLIT, BlackjackBotPolicy.tableStrategy(cards("8H", "8D"), up('T'), List.of(Action.HIT, Action.STAND, Action.SPLIT), S17_DAS));
		assertEquals(Action.HIT, BlackjackBotPolicy.tableStrategy(cards("8H", "8D"), up('T'), List.of(Action.HIT, Action.STAND), S17_DAS));
		assertEquals(Action.STAND, BlackjackBotPolicy.tableStrategy(cards("AH", "5D"), up('6'), List.of(Action.STAND), S17_DAS));
	}

	@Test
	void onlyEasyInsuresAboutHalfTheTime() {
		BotRng rng = BotRng.seeded(7);
		View ins = new View.Insurance(Offer.INSURANCE, cards("KH", "7D"), up('A'));
		View even = new View.Insurance(Offer.EVEN_MONEY, cards("KH", "AD"), up('A'));
		for (int i = 0; i < 2000; i++) {
			assertFalse(((Act.Insure) BlackjackBotPolicy.INSTANCE.act(bot(BotDifficulty.NORMAL), ins, null, rng)).take());
			assertFalse(((Act.Insure) BlackjackBotPolicy.INSTANCE.act(bot(BotDifficulty.HARD), even, null, rng)).take());
		}
		int takes = 0;
		int n = 20_000;
		for (int i = 0; i < n; i++) {
			if (((Act.Insure) BlackjackBotPolicy.INSTANCE.act(bot(BotDifficulty.EASY), i % 2 == 0 ? ins : even, null, rng)).take()) {
				takes++;
			}
		}
		assertEquals(0.5, takes / (double) n, 0.02);
	}

	// ---- EASY / NORMAL -----------------------------------------------------------------------------

	@Test
	void easyMimicsTheDealer() {
		BotRng rng = BotRng.seeded(3);
		for (Cell cell : chart()) {
			Action a = play(BotDifficulty.EASY, turn(cell.hand(), cell.up(), cell.legal(), S17_DAS), rng);
			assertNotEquals(Action.DOUBLE, a, cell.label());
			boolean aaOr88 = Hands.isPair(cell.hand()) && (cell.hand().getFirst().isAce() || cell.hand().getFirst().value() == 8);
			if (aaOr88) {
				assertEquals(Action.SPLIT, a, cell.label());
			} else {
				assertEquals(Hands.total(cell.hand()) < 17 ? Action.HIT : Action.STAND, a, cell.label());
			}
		}
	}

	@Test
	void normalDeviatesInFivePercentOfSoftAndDoubleSpotsOnly() {
		BotRng rng = BotRng.seeded(20260924);
		List<Cell> cells = chart();
		int spots = 0;
		int errors = 0;
		for (int i = 0; spots < 100_000; i++) {
			Cell cell = cells.get(i % cells.size());
			Action correct = BlackjackBotPolicy.tableStrategy(cell.hand(), cell.up(), cell.legal(), S17_DAS);
			Action a = play(BotDifficulty.NORMAL, turn(cell.hand(), cell.up(), cell.legal(), S17_DAS), rng);
			if (BlackjackBotPolicy.isErrorSpot(cell.hand(), correct)) {
				spots++;
				if (a != correct) {
					errors++;
					assertTrue(cell.legal().contains(a));
				}
			} else {
				assertEquals(correct, a, cell.label());
			}
		}
		assertEquals(BlackjackBotPolicy.NORMAL_ERROR_RATE, errors / (double) spots, 0.005);
	}

	@Test
	void legalizeAlwaysYieldsALegalAction() {
		SplittableRandom r = new SplittableRandom(5);
		BotRng rng = BotRng.seeded(5);
		Action[] all = Action.values();
		for (int i = 0; i < 100_000; i++) {
			List<Action> legal = new ArrayList<>();
			for (Action a : all) {
				if (r.nextBoolean()) {
					legal.add(a);
				}
			}
			if (legal.isEmpty()) {
				legal.add(Action.STAND);
			}
			List<Card> hand = List.of(Card.fromCode(r.nextInt(52)), Card.fromCode(r.nextInt(52)));
			View v = turn(hand, Card.fromCode(r.nextInt(52)), legal, S17_DAS);
			BotDifficulty level = BotDifficulty.values()[r.nextInt(3)];
			Action a = play(level, v, rng);
			assertTrue(legal.contains(a), legal + " -> " + a);
			Act wrong = BlackjackBotPolicy.INSTANCE.legalize(v, new Act.Play(all[r.nextInt(all.length)]));
			assertTrue(legal.contains(((Act.Play) wrong).action()));
		}
		// wrong phase → safe default
		assertEquals(new Act.Insure(false), BlackjackBotPolicy.INSTANCE.legalize(new View.Insurance(Offer.INSURANCE, cards("KH", "7D"), up('A')),
			new Act.Play(Action.HIT)));
		assertEquals(new Act.Play(Action.STAND), BlackjackBotPolicy.INSTANCE.legalize(turn(cards("KH", "7D"), up('A'), LEGAL_2, S17_DAS),
			new Act.Insure(true)));
	}

	// ---- virtual bets and think times ----------------------------------------------------------------

	@Test
	void easyBetsOneToFiveMinDoublesAfterALossAndResetsPastEight() {
		BotRng rng = BotRng.seeded(11);
		for (int i = 0; i < 2000; i++) {
			BetMemory m = BlackjackBotPolicy.newBetMemory(BotDifficulty.EASY, rng, 10, 0);
			assertTrue(m.base() >= 10 && m.base() <= 50 && m.base() % 10 == 0);
			assertEquals(m.base(), m.next());
		}
		BetMemory m = new BetMemory(10, 10);
		m = BlackjackBotPolicy.afterRound(BotDifficulty.EASY, m, -10, rng, 10, 0);
		assertEquals(20, m.next());
		m = BlackjackBotPolicy.afterRound(BotDifficulty.EASY, m, -20, rng, 10, 0);
		assertEquals(40, m.next());
		m = BlackjackBotPolicy.afterRound(BotDifficulty.EASY, m, -40, rng, 10, 0);
		assertEquals(80, m.next());
		m = BlackjackBotPolicy.afterRound(BotDifficulty.EASY, m, -80, rng, 10, 0);
		assertEquals(10, m.next(), "160 > 8 × min: back to the base");
		BetMemory won = BlackjackBotPolicy.afterRound(BotDifficulty.EASY, new BetMemory(10, 40), 40, rng, 10, 0);
		assertTrue(won.next() >= 10 && won.next() <= 50);
		assertEquals(won.base(), won.next());
		// the table max caps the progression
		assertEquals(30, BlackjackBotPolicy.afterRound(BotDifficulty.EASY, new BetMemory(30, 30), -30, rng, 10, 50).next());
	}

	@Test
	void normalAndHardBetFlat() {
		BotRng rng = BotRng.seeded(12);
		for (int i = 0; i < 2000; i++) {
			BetMemory n = BlackjackBotPolicy.newBetMemory(BotDifficulty.NORMAL, rng, 10, 0);
			assertTrue(n.base() >= 20 && n.base() <= 50, "" + n);
			BetMemory h = BlackjackBotPolicy.newBetMemory(BotDifficulty.HARD, rng, 10, 0);
			assertTrue(h.base() >= 50 && h.base() <= 100, "" + h);
			assertTrue(BlackjackBotPolicy.newBetMemory(BotDifficulty.HARD, rng, 10, 70).base() <= 70);
			assertEquals(n, BlackjackBotPolicy.afterRound(BotDifficulty.NORMAL, n, -n.next(), rng, 10, 0));
			assertEquals(h, BlackjackBotPolicy.afterRound(BotDifficulty.HARD, h, -h.next(), rng, 10, 0));
		}
	}

	@Test
	void thinkTimesPerLevel() {
		BotRng rng = BotRng.seeded(13);
		int[][] want = {{15, 40}, {10, 30}, {10, 25}};
		BotDifficulty[] levels = {BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD};
		for (int l = 0; l < 3; l++) {
			int lo = Integer.MAX_VALUE;
			int hi = 0;
			for (int i = 0; i < 5000; i++) {
				int t = BlackjackBotPolicy.thinkTicks(rng, levels[l], BotSpeed.NORMAL, 0.5);
				lo = Math.min(lo, t);
				hi = Math.max(hi, t);
			}
			assertEquals(want[l][0], lo);
			assertEquals(want[l][1], hi);
		}
		assertEquals(0, BlackjackBotPolicy.thinkTicks(rng, BotDifficulty.HARD, BotSpeed.INSTANT, 0.5));
		assertTrue(BlackjackBotPolicy.thinkTicks(rng, BotDifficulty.EASY, BotSpeed.FAST, 0.5) <= 20);
	}

	// ---- the table with bots: rng independence, human outcomes, money -------------------------------

	private static final UUID HUMAN = new UUID(0, 1);
	private static final int HUMAN_SEAT = 0;

	/** Records every game-rng draw ({@code bound → value}); the shuffle is its only consumer. */
	private static final class CountingRng implements IntUnaryOperator {
		final SplittableRandom inner;
		final List<Long> draws = new ArrayList<>();

		CountingRng(long seed) {
			inner = new SplittableRandom(seed);
		}

		@Override
		public int applyAsInt(int bound) {
			int v = inner.nextInt(bound);
			draws.add(((long) bound << 32) | v);
			return v;
		}
	}

	/** Game-rng draws of each shuffle, in order (a shuffle is fully determined by them). */
	private record Played(List<List<Long>> shuffles, Map<Integer, Long> ledger) {}

	/**
	 * Plays rounds like the table: seat 0 = a human (basic strategy, bet 10, declines insurance), bots on
	 * the highest seats with their policy and their OWN rng; only {@link BlackjackBotPolicy#payableSeats}
	 * of humans move money (the ledger).
	 */
	private static Played playTable(List<BotDifficulty> levels, int rounds, BiConsumer<BlackjackRound, Long> onRound) {
		CountingRng game = new CountingRng(20260924);
		BotRng botRng = BotRng.seeded(0xB07);
		Shoe shoe = new Shoe(game, 6);
		List<List<Long>> shuffles = new ArrayList<>();
		shuffles.add(List.copyOf(game.draws));
		Map<Integer, BotProfile> botSeats = new HashMap<>();
		for (int i = 0; i < levels.size(); i++) {
			botSeats.put(6 - i, new BotProfile("b" + i, "creeper42", levels.get(i), Personality.TAG));
		}
		Map<Integer, Long> ledger = new HashMap<>();
		for (int i = 0; i < rounds; i++) {
			if (shoe.needsShuffle(S17_DAS.penetration())) {
				int before = game.draws.size();
				shoe.shuffle();
				shuffles.add(List.copyOf(game.draws.subList(before, game.draws.size())));
			}
			List<SeatBet> bets = new ArrayList<>();
			bets.add(new SeatBet(HUMAN_SEAT, HUMAN, 10));
			botSeats.forEach((seat, p) -> bets.add(new SeatBet(seat, VirtualSeats.botUuid(p.key()), 25)));
			BlackjackRound r = new BlackjackRound(S17_DAS, shoe, bets);
			java.util.Set<Integer> paid = new java.util.HashSet<>();
			long[] net = {0};
			Runnable pay = () -> {
				for (Seat s : BlackjackBotPolicy.payableSeats(r, seat -> !botSeats.containsKey(seat) && !paid.contains(seat))) {
					paid.add(s.seat);
					long n = r.returnOf(s.seat) - r.stakedOf(s.seat);
					ledger.merge(s.seat, n, Long::sum);
					net[0] += n;
				}
			};
			for (int guard = 0; r.phase() != Phase.DONE && guard < 200; guard++) {
				pay.run();
				if (r.phase() == Phase.INSURANCE) {
					for (Seat s : r.pendingInsurance()) {
						BotProfile bot = botSeats.get(s.seat);
						if (bot == null) {
							r.decline(s.seat);
							continue;
						}
						Offer offer = r.offer(s.seat);
						boolean take = BlackjackBotPolicy.INSTANCE.act(bot, new View.Insurance(offer, s.hands.getFirst().cards, r.upCard()), null,
							botRng) instanceof Act.Insure ins && ins.take();
						if (offer == Offer.EVEN_MONEY) {
							r.evenMoney(s.seat, take);
						} else {
							r.insure(s.seat, take ? BlackjackRules.maxInsurance(s.bet) : 0);
						}
					}
					continue;
				}
				Turn t = r.current();
				BotProfile bot = botSeats.get(t.seat().seat);
				if (bot != null) {
					Act a = BlackjackBotPolicy.INSTANCE.act(bot, new View.Turn(t.hand().cards, r.upCard(), r.legal(t.seat().seat), S17_DAS,
						t.seat().hands.size()), null, botRng);
					if (!(a instanceof Act.Play p) || !r.act(t.seat().seat, p.action())) {
						r.standAll(t.seat().seat);
					}
				} else {
					r.act(HUMAN_SEAT, BasicStrategy.choose(t.hand().cards, r.upCard(), r.legal(HUMAN_SEAT)));
				}
			}
			pay.run();
			onRound.accept(r, net[0]);
		}
		return new Played(shuffles, ledger);
	}

	@Test
	void shoeShufflesAndGameRngDrawsAreIdenticalForNoBotsEasyNormalAndHard() {
		int shuffles = 8;
		List<List<BotDifficulty>> runs = List.of(List.of(), List.of(BotDifficulty.EASY, BotDifficulty.EASY),
			List.of(BotDifficulty.NORMAL, BotDifficulty.NORMAL), List.of(BotDifficulty.HARD, BotDifficulty.HARD));
		List<List<Long>> first = null;
		for (List<BotDifficulty> levels : runs) {
			Played p = playTable(levels, 400, (r, n) -> {});
			assertTrue(p.shuffles().size() >= shuffles, levels + ": " + p.shuffles().size());
			List<List<Long>> head = p.shuffles().subList(0, shuffles);
			if (first == null) {
				first = head;
			} else {
				assertEquals(first, head, "shuffles with " + levels);
			}
		}
	}

	@Test
	void botSeatsAreNeverPayableOnlyTheHumanMovesMoney() {
		int[] rounds = {0};
		Played p = playTable(List.of(BotDifficulty.EASY, BotDifficulty.HARD), 3000, (r, net) -> {
			rounds[0]++;
			assertEquals(r.returnOf(HUMAN_SEAT) - r.stakedOf(HUMAN_SEAT), net);
		});
		assertEquals(3000, rounds[0]);
		assertEquals(List.of(HUMAN_SEAT), List.copyOf(p.ledger().keySet()));
	}

	@Test
	void aHumansResultIsTheSameFunctionOfTheCardsTheyAndTheDealerReceive() {
		int[] checked = {0};
		playTable(List.of(BotDifficulty.EASY, BotDifficulty.NORMAL), 5000, (r, net) -> {
			Seat me = r.seat(HUMAN_SEAT);
			if (me.hands.size() != 1) {
				return; // splits: covered by the round tests
			}
			List<Card> hand = me.hands.getFirst().cards;
			List<Card> dealer = r.dealerCards();
			// Replay alone with exactly the cards the human and the dealer got, in dealing order.
			List<Card> seq = new ArrayList<>(List.of(hand.get(0), dealer.get(0), hand.get(1), dealer.get(1)));
			seq.addAll(hand.subList(2, hand.size()));
			seq.addAll(dealer.subList(2, dealer.size()));
			BlackjackRound solo = new BlackjackRound(S17_DAS, CardSource.stacked(seq, null), List.of(new SeatBet(HUMAN_SEAT, HUMAN, 10)));
			for (int g = 0; solo.phase() != Phase.DONE && g < 50; g++) {
				if (solo.phase() == Phase.INSURANCE) {
					solo.decline(HUMAN_SEAT);
				} else {
					Turn t = solo.current();
					solo.act(HUMAN_SEAT, BasicStrategy.choose(t.hand().cards, solo.upCard(), solo.legal(HUMAN_SEAT)));
				}
			}
			assertEquals(hand, solo.seat(HUMAN_SEAT).hands.getFirst().cards);
			assertEquals(me.hands.getFirst().outcome, solo.seat(HUMAN_SEAT).hands.getFirst().outcome);
			assertEquals(r.returnOf(HUMAN_SEAT), solo.returnOf(HUMAN_SEAT));
			checked[0]++;
		});
		assertTrue(checked[0] > 4500, "" + checked[0]);
	}
}
