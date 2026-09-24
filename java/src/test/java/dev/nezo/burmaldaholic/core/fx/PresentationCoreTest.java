package dev.nezo.burmaldaholic.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.sound.SoundCatalog;
import dev.nezo.burmaldaholic.core.wager.Stake;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lane J-L1 wiring that needs no Minecraft bootstrap: core sounds, particle ids, the big-win rule. */
class PresentationCoreTest {
	private static Path root() {
		return Path.of(System.getProperty("burmaldaholic.projectDir", "."));
	}

	@Test
	void everyCoreCatalogSoundIsDefinedWithItsSubtitle() throws IOException {
		JsonObject sounds = JsonParser.parseString(Files.readString(root().resolve("src/main/sounds/core/sounds.json"))).getAsJsonObject();
		List<String> errors = new ArrayList<>();
		for (SoundCatalog s : SoundCatalog.ownedBy("core")) {
			if (!sounds.has(s.id())) {
				errors.add("missing " + s.id());
				continue;
			}
			String sub = sounds.getAsJsonObject(s.id()).get("subtitle").getAsString();
			if (!sub.equals("subtitles.burmaldaholic." + s.subtitle())) errors.add(s.id() + ": subtitle " + sub + " != catalog " + s.subtitle());
		}
		assertTrue(errors.isEmpty(), String.join("\n", errors));
	}

	@Test
	void coreParticlesHaveDefinitions() {
		for (String id : CoreParticles.IDS)
			assertTrue(Files.isRegularFile(root().resolve("src/main/resources/assets/burmaldaholic/particles/" + id + ".json")), id);
	}

	private static PlayResult result(String game, long bet, long payout, boolean banked, List<String> tags, boolean deferred) {
		return new PlayResult(game, bet, payout, banked, Stake.Kind.CHIPS, 0.01, tags, null, "", deferred, false);
	}

	@Test
	void bigWinBroadcastUsesTheEpicRule() {
		// 50x and net >= 500 -> EPIC
		assertTrue(BigWinBroadcast.announces(result("roulette", 100, 5000, true, List.of(), false), true, 5000));
		// 36x -> MEGA: not announced
		assertFalse(BigWinBroadcast.announces(result("roulette", 100, 3600, true, List.of(), false), true, 5000));
		// net >= bigWinThreshold at a low multiple -> EPIC
		assertTrue(BigWinBroadcast.announces(result("blackjack", 5000, 10000, true, List.of(), false), true, 5000));
		// off, deferred, slots jackpots (own broadcast), not house-banked (PvP)
		assertFalse(BigWinBroadcast.announces(result("roulette", 100, 5000, true, List.of(), false), false, 5000));
		assertFalse(BigWinBroadcast.announces(result("roulette", 100, 5000, true, List.of(), true), true, 5000));
		assertFalse(BigWinBroadcast.announces(result("slots", 100, 50000, true, List.of("epic", "jackpot"), false), true, 5000));
		assertFalse(BigWinBroadcast.announces(result("pvp_duel", 100, 50000, false, List.of(), false), true, 5000));
		assertEquals(WinTier.EPIC, BigWinBroadcast.tierOf(60000, 5000, 5000));
		assertEquals(WinTier.BIG, BigWinBroadcast.tierOf(60000, 5000, 0));
	}
}
