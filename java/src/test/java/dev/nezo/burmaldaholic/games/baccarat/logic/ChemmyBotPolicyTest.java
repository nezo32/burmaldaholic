package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.baccarat.logic.ChemmyBotPolicy.Facts;
import dev.nezo.burmaldaholic.games.baccarat.logic.ChemmyBotPolicy.OfferView;
import dev.nezo.burmaldaholic.games.baccarat.logic.ChemmyBotPolicy.Punt;
import dev.nezo.burmaldaholic.games.baccarat.logic.ChemmyBotPolicy.PuntKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.ChemmyBotPolicy.PuntView;
import dev.nezo.burmaldaholic.games.baccarat.logic.SeatDecider.BankChoice;
import dev.nezo.burmaldaholic.games.baccarat.logic.SeatDecider.BankDecision;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** BOTS.md §4.4 chemin de fer Style numbers (same vectors as Bedrock {@code logic/bots.test.ts}). */
class ChemmyBotPolicyTest {
	static final Facts FACTS = new Facts(10, 20, 500, 2, 2, List.of());

	static BotProfile bot(BotDifficulty level) {
		return new BotProfile("b0000001", "creeper42", level, Personality.TAG);
	}

	static OfferView offer() {
		return new OfferView(false, 20, 20, 1000, 0, FACTS);
	}

	static OfferView offer(boolean keep, long bank, long balance, int wins, Facts f) {
		return new OfferView(keep, bank, 20, balance, wins, f);
	}

	static PuntView punt(long open, long balance, boolean banco, boolean human, long base, int mult) {
		return new PuntView(300, open, 10, 500, balance, banco, human, base, mult, FACTS);
	}

	static <V, A> double rate(BotPolicy<V, A> policy, BotProfile b, V v, Predicate<A> pred, int n) {
		BotRng rng = BotRng.seeded(1);
		int k = 0;
		for (int i = 0; i < n; i++) {
			if (pred.test(policy.act(b, v, null, rng))) {
				k++;
			}
		}
		return (double) k / n;
	}

	@Test
	void takesTheBankBySyle() {
		Predicate<BankDecision> take = a -> a.choice() == BankChoice.TAKE;
		assertEquals(0.6, rate(ChemmyBotPolicy.BANK, bot(BotDifficulty.EASY), offer(), take, 20000), 0.02);
		assertEquals(0.4, rate(ChemmyBotPolicy.BANK, bot(BotDifficulty.NORMAL), offer(), take, 20000), 0.02);
		assertEquals(0.3, rate(ChemmyBotPolicy.BANK, bot(BotDifficulty.HARD), offer(), take, 20000), 0.02);
	}

	@Test
	void hardBanksOnlyWhenItsChipsLastTenCoups() {
		Facts f = new Facts(10, 20, 500, 2, 2, List.of(20L, 20L, 20L));
		Predicate<BankDecision> take = a -> a.choice() == BankChoice.TAKE;
		assertEquals(0, rate(ChemmyBotPolicy.BANK, bot(BotDifficulty.HARD), offer(false, 20, 199, 0, f), take, 5000));
		assertTrue(rate(ChemmyBotPolicy.BANK, bot(BotDifficulty.HARD), offer(false, 20, 200, 0, f), take, 5000) > 0.25);
	}

	@Test
	void neverBanksWithoutAHumanPunter() {
		Facts noHuman = new Facts(10, 20, 500, 0, 1, List.of());
		for (BotDifficulty l : List.of(BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD)) {
			assertEquals(0, rate(ChemmyBotPolicy.BANK, bot(l), offer(false, 20, 1000, 0, noHuman), a -> a.choice() != BankChoice.PASS, 2000));
			assertEquals(BankChoice.PASS, ChemmyBotPolicy.BANK.act(bot(l), offer(true, 100, 1000, 0, noHuman), null, BotRng.seeded(1)).choice());
		}
	}

	private static long takes(BotDifficulty l, OfferView v, BotRng rng) {
		for (int i = 0; i < 200; i++) {
			BankDecision a = ChemmyBotPolicy.BANK.act(bot(l), v, null, rng);
			if (a.choice() == BankChoice.TAKE) {
				return a.amount();
			}
		}
		return -1;
	}

	@Test
	void bankAmounts() {
		BotRng rng = BotRng.seeded(9);
		TreeSet<Long> easy = new TreeSet<>();
		for (int i = 0; i < 4000; i++) {
			BankDecision a = ChemmyBotPolicy.BANK.act(bot(BotDifficulty.EASY), offer(), null, rng);
			if (a.choice() == BankChoice.TAKE) {
				easy.add(a.amount());
			}
		}
		assertEquals(List.of(40L, 60L, 80L, 100L), new ArrayList<>(easy), "EASY 2–5 × minBank");
		assertEquals(100, takes(BotDifficulty.NORMAL, offer(), rng), "NORMAL 10 × table min");
		assertEquals(3 * 30 * 2, takes(BotDifficulty.HARD, offer(false, 20, 1000, 0, new Facts(10, 20, 500, 2, 2, List.of(10L, 30L, 50L, 20L, 40L))), rng),
			"HARD 3 × median × seated humans");
		assertEquals(500, takes(BotDifficulty.HARD, offer(false, 20, 5000, 0, new Facts(10, 20, 500, 2, 2, List.of(400L))), rng), "capped");
		assertEquals(60, takes(BotDifficulty.NORMAL, offer(false, 20, 60, 0, FACTS), rng), "never more than its chips");
		assertEquals(20, takes(BotDifficulty.NORMAL, offer(false, 20, 1000, 0, new Facts(1, 20, 500, 2, 2, List.of())), rng), "never below the min bank");
		assertEquals(0, rate(ChemmyBotPolicy.BANK, bot(BotDifficulty.EASY), offer(false, 20, 19, 0, FACTS), a -> a.choice() == BankChoice.TAKE, 500));
	}

	@Test
	void keepRules() {
		BotRng r = BotRng.seeded(4);
		assertEquals(BankChoice.KEEP, ChemmyBotPolicy.BANK.act(bot(BotDifficulty.EASY), offer(true, 5000, 1000, 12, FACTS), null, r).choice(), "hot hand");
		assertEquals(BankChoice.KEEP, ChemmyBotPolicy.BANK.act(bot(BotDifficulty.NORMAL), offer(true, 100, 1000, 2, FACTS), null, r).choice());
		assertEquals(BankChoice.PASS, ChemmyBotPolicy.BANK.act(bot(BotDifficulty.NORMAL), offer(true, 100, 1000, 3, FACTS), null, r).choice(),
			"passes after 3 wins");
		assertEquals(BankChoice.KEEP, ChemmyBotPolicy.BANK.act(bot(BotDifficulty.HARD), offer(true, 500, 1000, 1, FACTS), null, r).choice());
		assertEquals(BankChoice.PASS, ChemmyBotPolicy.BANK.act(bot(BotDifficulty.HARD), offer(true, 501, 1000, 1, FACTS), null, r).choice(),
			"HARD keeps while B ≤ cap");
	}

	@Test
	void puntStakes() {
		BotRng rng = BotRng.seeded(1);
		ChemmyBotPolicy.Memory m = ChemmyBotPolicy.newMemory(BotDifficulty.EASY, 10, rng);
		assertTrue(List.of(10L, 20L, 30L).contains(m.base));
		List<Integer> seq = new ArrayList<>();
		for (long net : new long[] {-1, -1, -1, -1, 5, -1}) {
			seq.add(m.mult);
			ChemmyBotPolicy.afterPunt(m, BotDifficulty.EASY, net);
		}
		assertEquals(List.of(1, 2, 4, 8, 1, 1), seq, "Martingale, reset after 8×");
		assertEquals(2, m.mult);
		ChemmyBotPolicy.Memory n = ChemmyBotPolicy.newMemory(BotDifficulty.NORMAL, 10, rng);
		assertEquals(0, n.base % 10);
		assertTrue(n.base >= 20 && n.base <= 50);
		ChemmyBotPolicy.afterPunt(n, BotDifficulty.NORMAL, -10);
		assertEquals(1, n.mult, "NORMAL stays flat");
		assertEquals(Punt.bet(80), ChemmyBotPolicy.PUNT.act(bot(BotDifficulty.EASY), punt(300, 1000, false, false, 20, 4), null, rng));
		assertEquals(Punt.bet(40), ChemmyBotPolicy.PUNT.act(bot(BotDifficulty.NORMAL), punt(300, 1000, false, false, 40, 1), null, rng));
		assertEquals(Punt.bet(170), ChemmyBotPolicy.PUNT.act(bot(BotDifficulty.HARD), punt(170, 1000, true, false, 30, 1), null, rng),
			"HARD fills the open coverage");
		assertEquals(Punt.bet(500), ChemmyBotPolicy.PUNT.act(bot(BotDifficulty.HARD), new PuntView(900, 900, 10, 500, 1000, true, false, 30, 1, FACTS), null, rng),
			"… up to its cap");
	}

	@Test
	void puntLegalize() {
		BotRng r = BotRng.seeded(1);
		assertEquals(Punt.bet(15), ChemmyBotPolicy.PUNT.act(bot(BotDifficulty.NORMAL), punt(15, 1000, false, false, 40, 1), null, r), "snapped to the open coverage");
		assertEquals(Punt.NONE, ChemmyBotPolicy.PUNT.act(bot(BotDifficulty.NORMAL), punt(5, 1000, false, false, 40, 1), null, r), "below the minimum");
		assertEquals(Punt.bet(25), ChemmyBotPolicy.PUNT.act(bot(BotDifficulty.NORMAL), punt(300, 25, false, false, 40, 1), null, r), "its chips");
		assertEquals(Punt.NONE, ChemmyBotPolicy.PUNT.legalize(punt(300, 1000, true, true, 40, 1), Punt.BANCO), "never Banco while a human bets");
		assertEquals(Punt.NONE, ChemmyBotPolicy.PUNT.legalize(punt(300, 1000, false, false, 40, 1), Punt.BANCO));
		assertEquals(Punt.NONE, ChemmyBotPolicy.PUNT.legalize(punt(300, 299, true, false, 40, 1), Punt.BANCO), "cannot cover");
	}

	@Test
	void bancoByStyle() {
		Predicate<Punt> banco = a -> a.kind() == PuntKind.BANCO;
		PuntView v = punt(300, 1000, true, false, 30, 1);
		assertEquals(0.2, rate(ChemmyBotPolicy.PUNT, bot(BotDifficulty.EASY), v, banco, 20000), 0.02);
		assertEquals(0.05, rate(ChemmyBotPolicy.PUNT, bot(BotDifficulty.NORMAL), v, banco, 20000), 0.01);
		assertEquals(0, rate(ChemmyBotPolicy.PUNT, bot(BotDifficulty.HARD), v, banco, 20000));
		assertEquals(0, rate(ChemmyBotPolicy.PUNT, bot(BotDifficulty.EASY), punt(300, 1000, true, true, 30, 1), banco, 20000));
	}

	@Test
	void botPuntersBetLast() {
		// deadline in 60 t: an offset of 60 acts now, 59 waits (bots bet in the last 20–100 t)
		assertFalse(ChemmyBotPolicy.puntDue(61, 339, 1, 60, 30));
		assertTrue(ChemmyBotPolicy.puntDue(60, 340, 1, 60, 30));
		assertFalse(ChemmyBotPolicy.puntDue(-1, 340, 1, 60, 30), "no deadline running: betting is closing");
		// alone against a human banker: after the think delay
		assertFalse(ChemmyBotPolicy.puntDue(400, 29, 0, 60, 30));
		assertTrue(ChemmyBotPolicy.puntDue(400, 30, 0, 60, 30));
	}

	@Test
	void botStakesNeverReduceTheCoverageAHumanAsksFor() {
		Map<String, Long> punts = new LinkedHashMap<>();
		punts.put("bot:a", 100L);
		punts.put("h1", 50L);
		punts.put("bot:b", 150L);
		Map<String, Long> refunds = ChemmyBotPolicy.makeRoomForHuman(punts, k -> k.startsWith("bot:"), 180);
		assertEquals(List.of(Map.entry("bot:b", 150L), Map.entry("bot:a", 30L)), new ArrayList<>(refunds.entrySet()));
		assertEquals(List.of(Map.entry("bot:a", 70L), Map.entry("h1", 50L)), new ArrayList<>(punts.entrySet()));
		ChemmyBank<String> bank = new ChemmyBank<>();
		bank.take("banker", 300);
		punts.forEach(bank::addPunt);
		assertEquals(180, bank.checkPunt("h2", 180, 10, 1000, 300).accepted(), "the human gets all it asked for");
	}

	@Test
	void capBuyInMedian() {
		assertEquals(500, ChemmyBotPolicy.botBankCap(50, 10, 20));
		assertEquals(20, ChemmyBotPolicy.botBankCap(5, 1, 20));
		assertEquals(1000, ChemmyBotPolicy.buyIn(500, 20));
		assertEquals(3, ChemmyBotPolicy.median(List.of(5L, 1L, 3L)));
		assertEquals(2, ChemmyBotPolicy.median(List.of(1L, 2L, 3L, 10L)));
		assertEquals(0, ChemmyBotPolicy.median(List.of()));
	}
}
