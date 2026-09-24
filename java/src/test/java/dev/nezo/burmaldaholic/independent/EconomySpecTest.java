package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Ledger;
import dev.nezo.burmaldaholic.core.text.Plural;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import dev.nezo.burmaldaholic.loan.logic.LoanRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Plural helper incl. the actual RU strings, loans (§5.2–5.4), ledger atomicity and the balance cap (§3.1). */
class EconomySpecTest {
	private static final long MAX = 1_000_000_000L;
	private static final UUID A = new UUID(4, 1);
	private static final UUID B = new UUID(4, 2);

	private static JsonObject lang(String module, String code) throws Exception {
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		return JsonParser.parseString(Files.readString(root.resolve("src/main/lang/" + module + "/" + code + ".json"))).getAsJsonObject();
	}

	@ParameterizedTest(name = "{0} {1}")
	@CsvSource({
		"1,фишка", "2,фишки", "4,фишки", "5,фишек", "11,фишек", "12,фишек", "14,фишек", "20,фишек", "21,фишка", "22,фишки",
		"25,фишек", "101,фишка", "111,фишек", "112,фишек", "1001,фишка", "0,фишек", "1000000,фишек",
	})
	void russianChipForms(long n, String word) throws Exception {
		JsonObject ru = lang("core", "ru_ru");
		String s = ru.get(Plural.key("unit.burmaldaholic.chip", n)).getAsString();
		assertEquals("%1$s " + word, s);
	}

	@Test
	void englishPluralsAreSingularOnlyForOne() throws Exception {
		JsonObject en = lang("core", "en_us");
		assertEquals("%1$s chip", en.get(Plural.key("unit.burmaldaholic.chip", 1)).getAsString());
		assertEquals("%1$s chips", en.get(Plural.key("unit.burmaldaholic.chip", 21)).getAsString(), "EN p21 is plural");
		assertEquals("%1$s chips", en.get(Plural.key("unit.burmaldaholic.chip", 0)).getAsString());
	}

	// ---- loans -----------------------------------------------------------------------------------

	private static LoanRules.RateConfig rates(double base) {
		return new LoanRules.RateConfig(base, 0.02, 5, 0.10, 0.02);
	}

	@Test
	void interestPerDifficultyAndStanding() {
		assertEquals(LoanRules.Band.EASY, LoanRules.band(0, false), "Peaceful uses the Easy rate");
		assertEquals(LoanRules.Band.HARD, LoanRules.band(2, true), "Hardcore = Hard");
		assertEquals(115, LoanRules.due(100, LoanRules.rate(rates(0.15), 0, 0)));
		assertEquals(600, LoanRules.due(500, LoanRules.rate(rates(0.20), 0, 0)));
		assertEquals(2500, LoanRules.due(2000, LoanRules.rate(rates(0.25), 0, 0)));
		// 0.25 − 5×0.02 − 0.02 = 0.13
		assertEquals(0.13, LoanRules.rate(rates(0.25), 9, 3), 1e-9);
		// floored at 0.10
		assertEquals(0.10, LoanRules.rate(rates(0.15), 5, 5), 1e-9);
		assertEquals(56_500, LoanRules.due(50_000, 0.13));
		assertEquals(11, LoanRules.due(9, 0.15), "ceil(10.35)");
	}

	@Test
	void lateFeesAreSimpleAndCapped() {
		LoanRecord rec = new LoanRecord();
		LoanRules.Product rent = new LoanRules.Product(1, "rent", 500, 3, 0);
		LoanRules.take(rec, rent, 0.20, 1000);
		assertEquals(600, rec.owed);
		assertEquals(1000 + 3 * 24_000, rec.deadlineTick);
		LoanRules.AdvanceConfig cfg = new LoanRules.AdvanceConfig(0.10, 2.0, new int[] {24_000, 2_400});
		LoanRules.advance(rec, rec.deadlineTick - 1, cfg);
		assertEquals(LoanRecord.Status.ACTIVE, rec.status);
		LoanRules.AdvanceEvents ev = LoanRules.advance(rec, rec.deadlineTick, cfg);
		assertTrue(ev.defaulted());
		assertTrue(ev.lateFees().isEmpty(), "no fee at the deadline itself");
		ev = LoanRules.advance(rec, rec.deadlineTick + 24_000, cfg);
		assertEquals(List.of(60L), ev.lateFees(), "ceil(600 × 10 %)");
		// partial payment does not change the fee base (owedAtDeadline, simple interest)
		LoanRules.pay(rec, 300, rec.deadlineTick + 24_001, 5);
		ev = LoanRules.advance(rec, rec.deadlineTick + 3 * 24_000, cfg);
		assertEquals(List.of(60L, 60L), ev.lateFees(), "two boundaries caught up at once");
		assertEquals(600 + 60 - 300 + 120, rec.owed);
		ev = LoanRules.advance(rec, rec.deadlineTick + 100 * 24_000, cfg);
		assertTrue(rec.owed <= 1200, "capped at 2 × due at issue");
		assertEquals(1200, rec.owed);
		// paid in full from DEFAULT: standing reset, cooldown 5 MCD
		long now = rec.deadlineTick + 100 * 24_000 + 5;
		LoanRules.PayResult paid = LoanRules.pay(rec, 5_000, now, 5);
		assertEquals(1200, paid.paid(), "never takes more than owed");
		assertTrue(paid.fromDefault());
		assertEquals(0, rec.goodStanding);
		assertEquals(LoanRules.TakeError.COOLDOWN, LoanRules.takeError(rec, rent, 5, now + 5 * 24_000 - 1));
		assertEquals(null, LoanRules.takeError(rec, rent, 5, now + 5 * 24_000));
	}

	@Test
	void onTimeRepaymentBuildsStanding() {
		LoanRecord rec = new LoanRecord();
		LoanRules.Product pocket = new LoanRules.Product(0, "pocket", 100, 3, 0);
		LoanRules.take(rec, pocket, 0.15, 0);
		assertEquals(LoanRules.TakeError.ONE_AT_A_TIME, LoanRules.takeError(rec, pocket, 0, 10));
		assertEquals(LoanRules.TakeError.VIP, LoanRules.takeError(new LoanRecord(), new LoanRules.Product(2, "business", 2000, 5, 1), 0, 0));
		LoanRules.PayResult r = LoanRules.pay(rec, 115, 100, 5);
		assertTrue(r.onTime());
		assertEquals(1, rec.goodStanding);
		assertEquals(40, LoanRules.garnishAmount(81, 50, 40), "garnish ≤ owed");
		assertEquals(40, LoanRules.garnishAmount(81, 50, 1000), "floor(81 / 2)");
		assertEquals(50, LoanRules.seizeAmount(101, 50, 1000));
	}

	// ---- ledger --------------------------------------------------------------------------------

	@Test
	void batchIsAllOrNothing() {
		Ledger l = new Ledger();
		l.setBalance(A, 100, MAX);
		l.setBalance(B, 10, MAX);
		Ledger.Commit c = l.commit(List.of(new Ledger.Leg(AccountId.player(A), -50), new Ledger.Leg(AccountId.player(B), -20),
			new Ledger.Leg(AccountId.HOUSE, 70)), MAX, null);
		assertFalse(c.ok());
		assertEquals(100, l.balance(A), "A untouched when B cannot pay");
		assertEquals(10, l.balance(B));
	}

	@Test
	void balanceCapLosesTheExcess() {
		Ledger l = new Ledger();
		l.setBalance(A, MAX - 10, MAX);
		Ledger.Commit c = l.commit(List.of(new Ledger.Leg(AccountId.HOUSE, -100), new Ledger.Leg(AccountId.player(A), 100)), MAX, null);
		assertTrue(c.ok());
		assertEquals(MAX, l.balance(A));
		assertEquals(90, c.lostToCap());
		l.setBalance(B, MAX * 5, MAX);
		assertEquals(MAX, l.balance(B), "admin set is capped too");
		l.setBalance(B, -5, MAX);
		assertEquals(0, l.balance(B), "never negative");
	}

	/** Regression: a credit to a bankroll that no longer exists (charter broken) threw mid-commit after debiting the player. */
	@Test
	void creditToMissingBankrollFailsCleanly() {
		Ledger l = new Ledger();
		l.setBalance(A, 100, MAX);
		Ledger.Commit c = l.commit(List.of(new Ledger.Leg(AccountId.player(A), -40), new Ledger.Leg(AccountId.bankroll("multiplayer:charter/gone"), 40)),
			MAX, null);
		assertFalse(c.ok());
		assertEquals(100, l.balance(A), "player not debited");
	}

	@Test
	void bankrollCannotGoNegative() {
		Ledger l = new Ledger();
		l.openBankroll("b", A);
		l.setBalance(B, 0, MAX);
		Ledger.Commit c = l.commit(List.of(new Ledger.Leg(AccountId.bankroll("b"), -1), new Ledger.Leg(AccountId.player(B), 1)), MAX, null);
		assertFalse(c.ok());
		assertEquals(0, l.balance(B));
	}
}
