package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** BOTS.md §5.1 / §5.3 / §5.4: rake routing, heat attribution and bot share of a chemmy coup with bots. */
class ChemmyBotMoneyTest {
	static final Predicate<String> IS_BOT = k -> k.startsWith("bot:");
	static final int RAKE_BP = 500;

	static ChemmyBank<String> bank(String banker, long amount, Object... punts) {
		ChemmyBank<String> b = new ChemmyBank<>();
		b.take(banker, amount);
		for (int i = 0; i < punts.length; i += 2) {
			b.addPunt((String) punts[i], ((Number) punts[i + 1]).longValue());
		}
		return b;
	}

	@Test
	void aBotBankerPaysNoRakeAndHeatIsTheHumansResult() {
		ChemmyBank<String> b = bank("bot:x", 200, "h", 50);
		ChemmyBotMoney.Result<String> lose = ChemmyBotMoney.settle(b, Side.BANKER, RAKE_BP, IS_BOT, k -> true);
		assertEquals(0, lose.rake());
		assertEquals(50, lose.bankDelta());
		assertEquals(250, b.bank());
		assertEquals(-50, lose.heat().get("h"));
		assertTrue(lose.onlyBots("h"));

		ChemmyBotMoney.Result<String> win = ChemmyBotMoney.settle(bank("bot:x", 200, "h", 50), Side.PLAYER, RAKE_BP, IS_BOT, k -> true);
		assertEquals(50, win.heat().get("h"));
		assertEquals(1.0, win.botShare().get("h"));
		// Owner-funded bot: no heat.
		assertTrue(ChemmyBotMoney.settle(bank("bot:x", 200, "h", 50), Side.PLAYER, RAKE_BP, IS_BOT, k -> false).heat().isEmpty());
	}

	@Test
	void aHumanBankersRakeOnBotChipsGoesToTheSink() {
		ChemmyBotMoney.Result<String> r = ChemmyBotMoney.settle(bank("h1", 500, "h2", 100, "bot:a", 100), Side.BANKER, RAKE_BP, IS_BOT, k -> true);
		assertEquals(10, r.rake());
		assertEquals(5, r.rakeToSink());
		assertEquals(5, r.rakeToOwner());
		assertEquals(190, r.bankDelta());
		assertEquals(100 - 5, r.heat().get("h1"), "bot punters' settled stakes minus the rake on them");
		assertEquals(0.5, r.botShare().get("h1"));
		assertTrue(r.vsBots("h1"));
		assertFalse(r.onlyBots("h1"));
		assertEquals(1, r.humanPunts());

		ChemmyBotMoney.Result<String> lost = ChemmyBotMoney.settle(bank("h1", 500, "h2", 100, "bot:a", 100), Side.PLAYER, RAKE_BP, IS_BOT, k -> true);
		assertEquals(-100, lost.heat().get("h1"));
		assertEquals(0, lost.rakeToOwner() + lost.rakeToSink());
		assertEquals(0, ChemmyBotMoney.settle(bank("h1", 500, "h2", 100), Side.BANKER, RAKE_BP, IS_BOT, k -> true).rakeToSink(),
			"no bots: all rake is ordinary");
	}

	@Test
	void humansOnlyIsUnchanged() {
		ChemmyBank<String> a = bank("h1", 500, "h2", 120, "h3", 80);
		ChemmyBank<String> b = bank("h1", 500, "h2", 120, "h3", 80);
		ChemmyBotMoney.Result<String> r = ChemmyBotMoney.settle(a, Side.BANKER, RAKE_BP, IS_BOT, k -> true);
		ChemmyBank.Settlement<String> plain = b.settle(Side.BANKER, RAKE_BP);
		assertEquals(plain, r.base());
		assertEquals(a.bank(), b.bank());
		assertFalse(r.vsBots("h2"));
		assertFalse(r.vsBots("h1"));
		assertTrue(r.heat().isEmpty());
	}
}
