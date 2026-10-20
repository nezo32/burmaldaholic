package dev.nezo.burmaldaholic.games.slots.v2.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.config.sections.SlotsV2Config;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Owner R3 (E1): coin value × multiplier over the total-bet ladder; jackpot sources in EN and RU. */
class BetSplitTest {
	@Test
	void coinIsTheLadderGcdOrAConfiguredDivisor() {
		assertEquals(5, BetSplit.coin(new SlotsV2Config.Overworld().bets, 0));
		assertEquals(10, BetSplit.coin(new SlotsV2Config.Nether().bets, 0));
		assertEquals(50, BetSplit.coin(new SlotsV2Config.End().bets, 0));
		assertEquals(25, BetSplit.coin(new SlotsV2Config.End().bets, 25), "a configured coin that divides every bet");
		assertEquals(50, BetSplit.coin(new SlotsV2Config.End().bets, 30), "one that does not falls back to the gcd");
		assertEquals(4, BetSplit.multiplier(20, 5));
		assertEquals(0, BetSplit.multiplier(22, 5));
	}

	/** Every bet a VIP tier / owner cap can offer is a subset of the ladder, so it is always a whole number of coins. */
	@Test
	void everyOfferedBetIsAWholeNumberOfCoins() {
		for (SlotsV2Config.Machine c : new SlotsV2Config.Machine[] {new SlotsV2Config.Overworld(), new SlotsV2Config.Nether(), new SlotsV2Config.End()}) {
			long coin = BetSplit.coin(c.bets, c.coinValue);
			for (int cap : new int[] {100, 250, 1000, 2500, 5000}) {
				for (int b : c.bets) {
					if (b > cap) continue;
					long mult = BetSplit.multiplier(b, coin);
					assertTrue(mult > 0, b + " / " + coin);
					assertEquals(b, mult * coin, "pays stay × the total bet");
				}
			}
		}
	}

	@Test
	void jackpotSourcesExistInBothLanguages() throws Exception {
		Path dir = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang/slots");
		for (String lang : new String[] {"en_us", "ru_ru"}) {
			JsonObject j = JsonParser.parseString(Files.readString(dir.resolve(lang + ".json"))).getAsJsonObject();
			for (Machine m : Machine.values()) {
				for (String t : SlotDefaults.TIER_KEYS) {
					String key = "gui.burmaldaholic.slots.jackpots.src." + m.id + "." + t;
					assertTrue(j.has(key), lang + " " + key);
					assertFalse(j.get(key).getAsString().isBlank());
				}
			}
			for (String k : new String[] {"bet_coin", "jackpots.title", "jackpots.share", "jackpots.row", "jackpots.gem", "fx.jackpot_gem"}) {
				assertTrue(j.has("gui.burmaldaholic.slots." + k), lang + " " + k);
			}
		}
	}
}
