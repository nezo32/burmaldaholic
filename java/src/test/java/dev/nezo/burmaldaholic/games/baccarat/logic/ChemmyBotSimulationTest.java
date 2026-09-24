package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Coup;
import dev.nezo.burmaldaholic.games.baccarat.logic.SeatDecider.BankChoice;
import dev.nezo.burmaldaholic.games.baccarat.logic.SeatDecider.BankDecision;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * BOTS.md §12.1 / §12.3 for chemin de fer, on a pure model of the table as {@code BaccaratTableBlockEntity}
 * wires it (the same accounts: the human's wallet, the world bank that escrows every chip — bot chips
 * included — and an owner's bankroll):
 * <ul>
 *   <li>the Style never changes the game RNG: shoe order, burns and the number of RNG calls are identical
 *       for every style and without bots;</li>
 *   <li>money is conserved every coup (house- and bankroll-funded bots); a bot bank pays no rake; bots
 *       never punt against a bot bank;</li>
 *   <li>the human's EV against a bot bank is the fixed −1.24 % per chip in every style.</li>
 * </ul>
 */
class ChemmyBotSimulationTest {
	static final long MIN = 10;
	static final long MIN_BANK = 20;
	static final long CAP = ChemmyBotPolicy.botBankCap(50, MIN, MIN_BANK);
	static final int RAKE_BP = 500;

	record Run(String cards, long gameCalls, long humanNet, long humanStaked, long botBankRake, long botVsBot, List<Long> totals) {}

	/** @param level null = no bots; bankroll = the bots are funded by an owner's bankroll, else by the bank */
	static Run simulate(BotDifficulty level, int coups, boolean bankroll, long seed) {
		long[] calls = {0};
		Random fair = new Random(seed);
		IntUnaryOperator gameRng = bound -> {
			calls[0]++;
			return fair.nextInt(bound);
		};
		BotRng botRng = BotRng.seeded(seed ^ 0xB07L);
		BaccaratShoe shoe = new BaccaratShoe(8);
		long human = 1_000_000;
		long house = 0;
		long ownerBankroll = 100_000;
		long t0 = human + house + ownerBankroll;
		List<Long> totals = new ArrayList<>();
		StringBuilder cards = new StringBuilder();
		// seated bots: free chips (their stake / bank lives in ChemmyBank)
		Map<String, Long> free = new LinkedHashMap<>();
		Map<String, BotProfile> profiles = new LinkedHashMap<>();
		if (level != null) {
			for (int i = 0; i < 2; i++) {
				BotProfile p = new BotProfile(BotProfile.newId(botRng), "creeper42", level, Personality.TAG);
				long buyIn = ChemmyBotPolicy.buyIn(CAP, MIN_BANK);
				if (bankroll) {
					ownerBankroll -= buyIn; // BotPurses.fund: bankroll → bank escrow
					house += buyIn;
				} // BANK purse: minted by the bank (nothing moves)
				free.put(p.key(), buyIn);
				profiles.put(p.key(), p);
			}
		}
		ChemmyBank<String> bank = new ChemmyBank<>();
		long humanNet = 0;
		long humanStaked = 0;
		long botBankRake = 0;
		long botVsBot = 0;
		ChemmyBotPolicy.Facts facts = new ChemmyBotPolicy.Facts(MIN, MIN_BANK, CAP, 1, 1, List.of(10L));
		for (int i = 0; i < coups; i++) {
			// BANK_OFFER: a bot may take the bank (bot rng only)
			if (!bank.held()) {
				for (Map.Entry<String, BotProfile> e : profiles.entrySet()) {
					String k = e.getKey();
					BankDecision d = ChemmyBotPolicy.BANK.act(e.getValue(), new ChemmyBotPolicy.OfferView(false, MIN_BANK, MIN_BANK, free.get(k), 0, facts), null,
						botRng);
					if (d.choice() == BankChoice.TAKE) {
						free.merge(k, -d.amount(), Long::sum);
						bank.take(k, d.amount());
						break;
					}
				}
			}
			// DEAL: the game rng only
			if (shoe.needsShuffle(0.8, 8)) {
				shoe.shuffle(gameRng, 8, true);
			}
			Coup coup = BaccaratRules.deal(() -> shoe.draw(gameRng));
			cards.append(coup.player()).append('|').append(coup.banker()).append(' ');
			if (!bank.held()) {
				totals.add(human + house + ownerBankroll - t0);
				continue;
			}
			// BETTING: the human flat 10; other bots watch while a bot banks (no bot-vs-bot money)
			ChemmyBank.Punt p = bank.checkPunt("h", 10, MIN, 1000, CAP);
			if (p.error().isEmpty()) {
				human -= p.accepted();
				house += p.accepted();
				bank.addPunt("h", p.accepted());
			}
			for (String k : bank.punts().keySet()) {
				if (k.startsWith("bot:")) {
					botVsBot++;
				}
			}
			long staked = bank.punts().getOrDefault("h", 0L);
			boolean bankerBot = bank.banker().startsWith("bot:");
			ChemmyBotMoney.Result<String> r = ChemmyBotMoney.settle(bank, coup.winner(), RAKE_BP, k -> k.startsWith("bot:"), k -> !bankroll);
			if (bankerBot) {
				botBankRake += r.rake();
			}
			long ret = r.base().punterReturns().getOrDefault("h", 0L);
			human += ret;
			house -= ret;
			humanNet += ret - staked;
			humanStaked += staked;
			if (r.rakeToOwner() > 0 && bankroll) {
				house -= r.rakeToOwner();
				ownerBankroll += r.rakeToOwner();
			}
			// RESULT → keep / pass (bot rng)
			String owner = bank.banker();
			BotProfile prof = profiles.get(owner);
			boolean keep = false;
			if (prof != null && coup.winner() != BaccaratRules.Side.PLAYER && bank.bank() >= MIN_BANK) {
				keep = ChemmyBotPolicy.BANK.act(prof, new ChemmyBotPolicy.OfferView(true, bank.bank(), MIN_BANK, free.get(owner), bank.wins(), facts), null, botRng)
					.choice() == BankChoice.KEEP;
			}
			if (!keep) {
				free.merge(owner, bank.close(), Long::sum);
			}
			totals.add(human + house + ownerBankroll - t0);
		}
		// The human leaves: every bot leaves at the next safe point (holdings back to their purses).
		if (bank.held()) {
			free.merge(bank.banker(), bank.close(), Long::sum);
		}
		for (long held : free.values()) {
			if (bankroll) {
				house -= held; // BotPurses.settle: bank escrow → bankroll
				ownerBankroll += held;
			} // BANK purse: the bank sinks it
		}
		totals.add(human + house + ownerBankroll - t0);
		return new Run(cards.toString(), calls[0], humanNet, humanStaked, botBankRake, botVsBot, totals);
	}

	@Test
	void styleNeverChangesTheGameRng() {
		Run none = simulate(null, 1000, false, 42);
		for (BotDifficulty l : List.of(BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD)) {
			Run r = simulate(l, 1000, false, 42);
			assertEquals(none.cards(), r.cards(), "shoe order and burns identical: " + l);
			assertEquals(none.gameCalls(), r.gameCalls(), "game RNG call count identical: " + l);
		}
	}

	@ParameterizedTest
	@EnumSource(value = BotDifficulty.class, names = {"EASY", "NORMAL", "HARD"})
	void moneyIsConservedEveryCoup(BotDifficulty level) {
		for (boolean bankroll : new boolean[] {false, true}) {
			Run r = simulate(level, 3000, bankroll, 7);
			assertEquals(0, r.botBankRake(), "a bot bank never pays rake");
			assertEquals(0, r.botVsBot(), "no bot-vs-bot coup");
			assertTrue(r.humanStaked() > 0);
			assertTrue(r.totals().stream().allMatch(x -> x == 0), "human + bank + bankroll constant (bankroll=" + bankroll + ")");
		}
	}

	@Test
	void humanEvAgainstABotBankIsTheSameInEveryStyle() {
		for (BotDifficulty l : List.of(BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD)) {
			Run r = simulate(l, 60_000, false, 11);
			double ev = (double) r.humanNet() / r.humanStaked();
			assertEquals(-0.0124, ev, 0.012, "punter EV per chip vs a " + l + " bot bank");
		}
	}
}
