package dev.nezo.burmaldaholic.core.anim.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratReveal;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackBeats;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRules;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.CardSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors of the card tables (docs/architecture/animation.md §3.4, topic {@code cards}): {@link CardMotion}
 * samples, the blackjack publication schedules ({@link BlackjackBeats}) of fixed rounds and every baccarat reveal shape
 * ({@link BaccaratReveal}), plus the honesty properties of §0.7.3 (schedules independent of unrevealed card values).
 * Regenerate after an intended change: {@code FX_DUMP_VECTORS=1 ./gradlew test --tests '*CardsVectorsTest'}.
 */
class CardsVectorsTest {
	private static final BlackjackRules RULES = new BlackjackRules(6, 0.75, false, 1.5, true, 4, false, true, false);
	private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

	@Test
	void vectorsMatch() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors/cards.json");
		JsonObject actual = compute();
		if (System.getenv("FX_DUMP_VECTORS") != null || !Files.isRegularFile(file)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
			Files.createDirectories(file.getParent());
			Files.writeString(file, gson.toJson(actual) + "\n", StandardCharsets.UTF_8);
			return;
		}
		JsonObject expected = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals(expected, actual, "cards.json vectors changed (regenerate with FX_DUMP_VECTORS=1 if intended)");
	}

	private static JsonObject compute() {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		// motion samples
		JsonArray motion = new JsonArray();
		CardMotion.Pose p = new CardMotion.Pose();
		for (double t : new double[] {0, 0.25, 0.5, 0.75, 1}) {
			JsonObject s = new JsonObject();
			s.addProperty("t", t);
			CardMotion.deal(p, 358, 30, 166, 14, t, 0, CardMotion.seed(1, 2, 3), false);
			s.addProperty("dealX", round(p.x));
			s.addProperty("dealY", round(p.y));
			s.addProperty("dealRot", round(p.rot));
			s.addProperty("dealScale", round(p.scale));
			CardMotion.flip(p, t);
			s.addProperty("flipScaleX", round(p.scaleX));
			s.addProperty("flipFace", p.face);
			s.addProperty("squeeze", round(CardMotion.squeeze(t)));
			CardMotion.gather(p, 200, 100, 22, 30, t, true, false);
			s.addProperty("gatherX", round(p.x));
			s.addProperty("gatherAlpha", round(p.alpha));
			motion.add(s);
		}
		o.add("motion", motion);
		// blackjack schedules
		JsonObject bj = new JsonObject();
		bj.addProperty("stand", blackjack(List.of(10, 7, 9, 8), 1, List.of("stand")).timeline().toCanonicalJson());
		bj.addProperty("hitBust", blackjack(List.of(10, 7, 6, 8, 9), 1, List.of("hit")).timeline().toCanonicalJson());
		bj.addProperty("twoSeatsDealerDraws", blackjack(List.of(10, 9, 6, 10, 8, 10, 5, 2), 2, List.of("stand", "stand")).timeline()
			.toCanonicalJson());
		bj.addProperty("split", blackjack(List.of(8, 10, 8, 7, 3, 2, 9), 1, List.of("split", "stand", "stand")).timeline().toCanonicalJson());
		bj.addProperty("double", blackjack(List.of(6, 10, 5, 7, 9), 1, List.of("double")).timeline().toCanonicalJson());
		bj.addProperty("peekTen", blackjack(List.of(9, 10, 8, 5), 1, List.of("stand")).timeline().toCanonicalJson());
		o.add("blackjack", bj);
		// baccarat reveal shapes
		JsonArray bac = new JsonArray();
		for (boolean squeeze : new boolean[] {true, false}) {
			for (int[] shape : new int[][] {{2, 2, 1}, {2, 2, 0}, {3, 2, 0}, {2, 3, 0}, {3, 3, 0}}) {
				BaccaratReveal.Config cfg = new BaccaratReveal.Config(6, 6, 20, 10, squeeze, 160);
				BaccaratReveal r = BaccaratReveal.build(shape[0], shape[1], shape[2] == 1, cfg);
				JsonObject c = new JsonObject();
				c.addProperty("squeeze", squeeze);
				c.addProperty("player", shape[0]);
				c.addProperty("banker", shape[1]);
				c.addProperty("natural", shape[2] == 1);
				c.addProperty("total", r.total());
				c.addProperty("timeline", r.timeline().toCanonicalJson());
				bac.add(c);
			}
		}
		BaccaratReveal capped = BaccaratReveal.build(3, 3, false, BaccaratReveal.Config.DEFAULT.withCap(120));
		JsonObject c = new JsonObject();
		c.addProperty("cap", 120);
		c.addProperty("total", capped.total());
		bac.add(c);
		o.add("baccarat", bac);
		return o;
	}

	private static double round(double v) {
		return Math.round(v * 1e6) / 1e6;
	}

	/**
	 * Plays a round from stacked ranks (seat order deal: s1, s2, …, dealer up, s1, s2, …, hole, then draws) with
	 * {@code seats} seats; each action applies to the current turn, one tick apart after the deal gate.
	 */
	private static BlackjackBeats blackjack(List<Integer> ranks, int seats, List<String> actions) {
		return blackjack(ranks, 0, seats, actions);
	}

	private static BlackjackBeats blackjack(List<Integer> ranks, int suit, int seats, List<String> actions) {
		List<Card> cards = new ArrayList<>();
		for (int r : ranks) cards.add(Card.of(r, suit));
		for (int i = 0; i < 20; i++) cards.add(Card.of(2 + (i % 3), suit)); // small cards for any further draw
		List<BlackjackRound.SeatBet> bets = new ArrayList<>();
		for (int s = 0; s < seats; s++) bets.add(new BlackjackRound.SeatBet(s, s == 0 ? A : B, 10));
		BlackjackRound round = new BlackjackRound(RULES, CardSource.stacked(cards, null), bets);
		long now = 1000;
		BlackjackBeats beats = new BlackjackBeats(round, now, BlackjackBeats.Config.DEFAULT);
		for (String a : actions) {
			if (round.phase() == BlackjackRound.Phase.INSURANCE) {
				for (BlackjackRound.Seat s : round.pendingInsurance()) round.decline(s.seat);
			}
			BlackjackRound.Turn t = round.current();
			if (t == null) break;
			now = Math.max(now + 1, beats.busyUntil());
			round.act(t.seat().seat, BlackjackRound.Action.byId(a));
			beats.sync(round, now);
		}
		return beats;
	}

	@Test
	void blackjackPublishesOneCardPerBeatAndHidesTheHole() {
		BlackjackBeats b = blackjack(List.of(10, 7, 9, 8), 1, List.of());
		// deal: seat, up, seat, hole at 0, 6, 12, 18 ticks
		assertEquals(0, b.dealerAt(1000).size());
		assertEquals(1, b.dealerAt(1006).size());
		assertEquals(1, b.handsAt(0, 1000).getFirst().size());
		assertEquals(2, b.dealerAt(1018).size());
		assertEquals(-1, b.dealerAt(1018).get(1).code(), "the hole card is a back until the flip");
		assertTrue(b.busy(1018));
		assertEquals(1018 + BlackjackBeats.READABLE_TICKS, b.busyUntil(), "no peek on a 7");
		BlackjackBeats peek = blackjack(List.of(9, 10, 8, 5), 1, List.of());
		assertEquals(1024, peek.peekTick(), "the peek beat follows the deal");
		assertEquals(1024 + BlackjackBeats.Config.DEFAULT.peekTicks(), peek.busyUntil());
	}

	@Test
	void holeIsRevealedOnlyAtTheFlipAndDealerDrawsArePaced() {
		BlackjackBeats b = blackjack(List.of(10, 5, 9, 10, 2, 3, 4), 1, List.of("stand"));
		long flip = b.holeFlipTick();
		assertTrue(flip > 0);
		assertEquals(-1, b.dealerAt(flip - 1).get(1).code());
		assertTrue(b.dealerAt(flip).get(1).code() >= 0);
		List<BlackjackBeats.Ev> d = b.dealerAt(Long.MAX_VALUE / 4);
		assertTrue(d.size() >= 3, "dealer 15 draws");
		assertEquals(flip + BlackjackBeats.Config.DEFAULT.dealerDrawTicks(), d.get(2).tick());
		assertTrue(b.busyUntil() >= d.getLast().tick() + BlackjackBeats.READABLE_TICKS);
	}

	@Test
	void splitCardKeepsItsId() {
		List<Card> cards = new ArrayList<>(List.of(Card.of(8), Card.of(10, 1), Card.of(8, 2), Card.of(7, 3), Card.of(3), Card.of(2, 1)));
		for (int i = 0; i < 10; i++) cards.add(Card.of(2 + i % 3, 2));
		BlackjackRound round = new BlackjackRound(RULES, CardSource.stacked(cards, null), List.of(new BlackjackRound.SeatBet(0, A, 10)));
		BlackjackBeats b = new BlackjackBeats(round, 0, BlackjackBeats.Config.DEFAULT);
		int movedId = b.handsAt(0, 100).getFirst().get(1).id();
		round.act(0, BlackjackRound.Action.SPLIT);
		b.sync(round, 200);
		List<List<BlackjackBeats.Ev>> hands = b.handsAt(0, 1000);
		assertEquals(2, hands.size());
		assertEquals(movedId, hands.get(1).getFirst().id(), "the split card slides to the new hand");
		assertEquals(200, hands.get(0).get(1).tick());
		assertEquals(206, hands.get(1).get(1).tick());
	}

	@Test
	void blackjackScheduleIgnoresUnrevealedValues() {
		// same public structure (stand on 19, no peek, the dealer draws once), different card values → same schedule
		String a = blackjack(List.of(10, 7, 9, 8), 0, 1, List.of("stand")).timeline().toCanonicalJson();
		String b = blackjack(List.of(9, 8, 10, 7), 1, 1, List.of("stand")).timeline().toCanonicalJson();
		String c = blackjack(List.of(9, 6, 10, 9), 2, 1, List.of("stand")).timeline().toCanonicalJson();
		assertEquals(a, b);
		assertEquals(a, c, "the hole value does not change the timing");
	}

	@Test
	void baccaratRevealShapes() {
		BaccaratReveal.Config cfg = BaccaratReveal.Config.DEFAULT;
		BaccaratReveal nat = BaccaratReveal.build(2, 2, true, cfg);
		BaccaratReveal longest = BaccaratReveal.build(3, 3, false, cfg);
		assertEquals(90, nat.total());
		assertTrue(longest.total() <= cfg.capTicks());
		assertEquals(-1, nat.dealAt(0, 2));
		// P1 flips, P2 squeezed; third cards squeezed
		assertEquals(BaccaratReveal.Kind.FLIP, longest.revealStep(0, 0).kind());
		assertEquals(BaccaratReveal.Kind.SQUEEZE, longest.revealStep(0, 1).kind());
		assertEquals(BaccaratReveal.Kind.SQUEEZE, longest.revealStep(1, 2).kind());
		// every squeeze window is the same length (§0.7.7)
		for (BaccaratReveal.Step s : longest.steps()) if (s.kind() == BaccaratReveal.Kind.SQUEEZE) assertEquals(20, s.dur());
		// the four-card part is identical whatever follows (the third cards are unknown until dealt)
		for (int[] shape : new int[][] {{2, 2}, {3, 2}, {2, 3}, {3, 3}}) {
			BaccaratReveal r = BaccaratReveal.build(shape[0], shape[1], false, cfg);
			for (int side = 0; side < 2; side++) for (int i = 0; i < 2; i++) {
				assertEquals(longest.dealAt(side, i), r.dealAt(side, i));
				assertEquals(longest.revealAt(side, i), r.revealAt(side, i));
			}
		}
		// the Banker third card comes earlier when the Player stood (public at that beat)
		assertTrue(BaccaratReveal.build(2, 3, false, cfg).dealAt(1, 2) < longest.dealAt(1, 2));
		BaccaratReveal off = BaccaratReveal.build(3, 3, false, new BaccaratReveal.Config(6, 6, 20, 10, false, 160));
		for (BaccaratReveal.Step s : off.steps()) assertTrue(s.kind() != BaccaratReveal.Kind.SQUEEZE);
		BaccaratReveal capped = BaccaratReveal.build(3, 3, false, cfg.withCap(120));
		for (BaccaratReveal.Step s : capped.steps()) if (s.kind() == BaccaratReveal.Kind.SQUEEZE) assertTrue(s.dur() >= 10);
	}
}
