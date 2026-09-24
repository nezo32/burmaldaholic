package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.sections.BotsConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** J-G6 worldgen bot presets (BOTS.md §2.3, §3.3, §7.1) — same vectors as Bedrock's worldgen/logic/bots.test.ts. */
class WorldgenBotPresetsTest {
	/** Same reading as {@code TableBots.defaultsFor(game, true)} over the shipped config defaults. */
	private static BotSettings worldgenDefaults(BotsConfig cfg, String game) {
		BotsConfig.TableDefaults t = cfg.table.get(game);
		if (t == null) {
			return BotSettings.HUMANS_ONLY;
		}
		SeatPolicy policy = t.worldgenPolicy == SeatPolicy.BOTS_ONLY ? SeatPolicy.MIXED : t.worldgenPolicy;
		return new BotSettings(policy, t.worldgenCount, t.difficulty, cfg.keepFreeSeatDefault, true, BotSpeed.NORMAL);
	}

	private static BotSettings worldgenDefaults(String game) {
		return worldgenDefaults(new BotsConfig(), game);
	}

	private record Table(String game, String block, CasinoKind kind, WorldgenBotPresets.Preset preset, BotSettings settings) {}

	/** Every bot-capable table of a layout: game family → effective defaults + preset. */
	private static List<Table> tablesOf(Layout l) {
		List<Table> out = new ArrayList<>();
		for (int x = 0; x < l.size().x(); x++) {
			for (int y = 0; y < l.size().y(); y++) {
				for (int z = 0; z < l.size().z(); z++) {
					BlockSpec b = l.grid().get(x, y, z);
					if (b == null) {
						continue;
					}
					Optional<String> game = WorldgenBotPresets.gameOf(b.name());
					if (game.isEmpty()) {
						continue;
					}
					WorldgenBotPresets.Preset p = WorldgenBotPresets.presetFor(l.kind(), b.name());
					out.add(new Table(game.get(), b.name(), l.kind(), p, WorldgenBotPresets.apply(worldgenDefaults(game.get()), p)));
				}
			}
		}
		return out;
	}

	@Test
	void nameThemePerCasinoType() {
		assertEquals(Map.of(CasinoKind.VILLAGE_CASINO, BotRoster.Theme.ANY, CasinoKind.PIGLIN_PARLOR, BotRoster.Theme.PIGLIN,
			CasinoKind.HIGH_ROLLER, BotRoster.Theme.ENDER), WorldgenBotPresets.CASINO_NAME_THEME);
		assertEquals(WorldgenBotPresets.Preset.theme(BotRoster.Theme.ANY), WorldgenBotPresets.preset(CasinoKind.VILLAGE_CASINO, null));
		assertEquals(WorldgenBotPresets.Preset.theme(BotRoster.Theme.PIGLIN), WorldgenBotPresets.preset(CasinoKind.PIGLIN_PARLOR, null));
		assertEquals(WorldgenBotPresets.Preset.theme(BotRoster.Theme.ENDER),
			WorldgenBotPresets.preset(CasinoKind.HIGH_ROLLER, TablePresets.HIGH_ROLLER_BLACKJACK));
		assertEquals(BotRoster.Theme.ANY, WorldgenBotPresets.nameTheme(null));
	}

	@Test
	void parlorPokerIsMixedWithTheParlorMix() {
		assertEquals(List.of(20, 70, 10), WorldgenBotPresets.PARLOR_POKER_MIX);
		Table poker = tablesOf(Layouts.piglinParlorLayout()).stream().filter(t -> t.game().equals("poker")).findFirst().orElseThrow();
		assertEquals(new WorldgenBotPresets.Preset(BotRoster.Theme.PIGLIN, List.of(20, 70, 10), null, null, BotDifficulty.MIXED), poker.preset());
		assertArrayEquals(new int[] {20, 70, 10}, poker.preset().levelMixArray());
		assertEquals(SeatPolicy.MIXED, poker.settings().policy());
		assertEquals(3, poker.settings().count());
		assertEquals(BotDifficulty.MIXED, poker.settings().difficulty());
		// the core Parlor poker preset (Low stakes, 3 bots, Regular-heavy) agrees
		assertEquals(WorldgenBotPresets.PARLOR_POKER_MIX, dev.nezo.burmaldaholic.core.service.TablePresetProvider.TablePreset.REGULAR_HEAVY);
		assertEquals(poker.settings().count(), dev.nezo.burmaldaholic.core.service.TablePresetProvider.TablePreset.parlorPoker().pokerBots());
	}

	@Test
	void parlorMixDraws20_70_10() {
		BotRng rng = BotRng.seeded(7);
		int[] mix = WorldgenBotPresets.preset(CasinoKind.PIGLIN_PARLOR, TablePresets.PARLOR_POKER).levelMixArray();
		Map<BotDifficulty, Integer> n = new EnumMap<>(BotDifficulty.class);
		for (int i = 0; i < 10_000; i++) {
			n.merge(BotDifficulty.MIXED.pick(rng, mix), 1, Integer::sum);
		}
		assertEquals(0.2, n.getOrDefault(BotDifficulty.EASY, 0) / 10_000.0, 0.02);
		assertEquals(0.7, n.getOrDefault(BotDifficulty.NORMAL, 0) / 10_000.0, 0.02);
		assertEquals(0.1, n.getOrDefault(BotDifficulty.HARD, 0) / 10_000.0, 0.02);
	}

	@Test
	void generatedTablesUseTheWorldgenColumns() {
		Map<String, BotSettings> want = Map.of(
			"poker", new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.MIXED, true, true, BotSpeed.NORMAL),
			"blackjack", new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, true, true, BotSpeed.NORMAL),
			"roulette", new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.MIXED, true, true, BotSpeed.NORMAL),
			"craps", new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.MIXED, true, true, BotSpeed.NORMAL),
			"baccarat", new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.MIXED, true, true, BotSpeed.NORMAL),
			"uth", new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, true, true, BotSpeed.NORMAL));
		List<Table> all = new ArrayList<>();
		all.addAll(tablesOf(Layouts.village(VillageStyle.values()[0])));
		all.addAll(tablesOf(Layouts.piglinParlorLayout()));
		all.addAll(tablesOf(Layouts.highRollerLayout()));
		// village: blackjack, roulette, uth; parlor: craps, poker, baccarat; lounge: HR blackjack, roulette, baccarat, uth
		for (Table t : all) {
			assertEquals(want.get(t.game()), t.settings(), t.block());
			assertEquals(WorldgenBotPresets.CASINO_NAME_THEME.get(t.kind()), t.preset().nameTheme(), t.block());
			if (!t.game().equals("poker")) {
				assertNull(t.preset().levelMixArray(), t.block());
			}
		}
		Set<String> games = new HashSet<>();
		all.forEach(t -> games.add(t.game()));
		assertTrue(games.containsAll(List.of("poker", "blackjack", "roulette", "craps", "baccarat", "uth")), games.toString());
		assertEquals(List.of("blackjack", "roulette", "uth"),
			tablesOf(Layouts.village(VillageStyle.values()[0])).stream().map(Table::game).sorted().toList());
		assertEquals(List.of("baccarat", "craps", "poker"), tablesOf(Layouts.piglinParlorLayout()).stream().map(Table::game).sorted().toList());
		assertEquals(List.of("baccarat", "blackjack", "roulette", "uth"),
			tablesOf(Layouts.highRollerLayout()).stream().map(Table::game).distinct().sorted().toList());
	}

	@Test
	void everyVillageStyleGetsTheSameDefaults() {
		for (VillageStyle style : VillageStyle.values()) {
			List<Table> tables = tablesOf(Layouts.village(style));
			assertEquals(List.of("blackjack", "roulette", "uth"), tables.stream().map(Table::game).sorted().toList(), style.name());
			for (Table t : tables) {
				assertEquals(WorldgenBotPresets.Preset.theme(BotRoster.Theme.ANY), t.preset());
			}
		}
	}

	@Test
	void neverBotsOnlyOverridesApplyCountClamped() {
		BotSettings base = new BotSettings(SeatPolicy.BOTS_ONLY, 4, BotDifficulty.HARD, false, false, BotSpeed.FAST);
		assertEquals(base.withPolicy(SeatPolicy.MIXED), WorldgenBotPresets.apply(base, WorldgenBotPresets.Preset.theme(BotRoster.Theme.ANY)));
		assertEquals(SeatPolicy.MIXED, WorldgenBotPresets.apply(base, null).policy());
		assertEquals(new BotSettings(SeatPolicy.MIXED, 0, BotDifficulty.EASY, false, false, BotSpeed.FAST),
			WorldgenBotPresets.apply(base, new WorldgenBotPresets.Preset(BotRoster.Theme.ANY, List.of(), SeatPolicy.BOTS_ONLY, -2, BotDifficulty.EASY)));
		WorldgenBotPresets.Preset parlor = WorldgenBotPresets.preset(CasinoKind.PIGLIN_PARLOR, TablePresets.PARLOR_POKER);
		// an admin who sets poker worldgenCount 5 changes the Parlor too (the preset keeps only the mix)
		BotsConfig cfg = new BotsConfig();
		cfg.table.get("poker").worldgenCount = 5;
		assertEquals(5, WorldgenBotPresets.apply(worldgenDefaults(cfg, "poker"), parlor).count());
		// worldgenPolicy BOTS_ONLY in config → MIXED
		cfg.table.get("poker").worldgenPolicy = SeatPolicy.BOTS_ONLY;
		assertEquals(SeatPolicy.MIXED, WorldgenBotPresets.apply(worldgenDefaults(cfg, "poker"), parlor).policy());
		// HUMANS_ONLY in config wins over the Parlor's MIXED difficulty preset
		assertEquals(SeatPolicy.HUMANS_ONLY,
			WorldgenBotPresets.apply(worldgenDefaults("poker").withPolicy(SeatPolicy.HUMANS_ONLY), parlor).policy());
	}

	@Test
	void gameOfBotTables() {
		assertEquals(Optional.of("poker"), WorldgenBotPresets.gameOf(Layouts.POKER));
		assertEquals(Optional.of("blackjack"), WorldgenBotPresets.gameOf(Layouts.BLACKJACK_HIGH_ROLLER));
		assertEquals(Optional.of("baccarat"), WorldgenBotPresets.gameOf(Layouts.BACCARAT));
		assertEquals(Optional.empty(), WorldgenBotPresets.gameOf(Layouts.SLOTS_GOLD));
		assertEquals(Optional.empty(), WorldgenBotPresets.gameOf(Layouts.CASHIER));
		assertEquals(Optional.empty(), WorldgenBotPresets.gameOf(null));
	}

	@Test
	void themedNamePoolsComeFirst() {
		Map<CasinoKind, List<String>> pools = Map.of(CasinoKind.PIGLIN_PARLOR, BotRoster.PIGLIN, CasinoKind.HIGH_ROLLER, BotRoster.ENDER,
			CasinoKind.VILLAGE_CASINO, BotRoster.ANY);
		int[] mix = WorldgenBotPresets.preset(CasinoKind.PIGLIN_PARLOR, TablePresets.PARLOR_POKER).levelMixArray();
		for (var e : pools.entrySet()) {
			BotRoster.Theme theme = WorldgenBotPresets.preset(e.getKey(), null).nameTheme();
			List<String> pool = e.getValue();
			for (long seed = 1; seed <= 200; seed++) {
				BotRng rng = BotRng.seeded(seed);
				Set<String> used = new HashSet<>();
				List<String> names = new ArrayList<>();
				for (int i = 0; i < 7; i++) {
					BotProfile b = BotRoster.create(rng, BotDifficulty.MIXED, mix, theme, used, true);
					used.add(b.nameId());
					names.add(b.nameId());
				}
				assertEquals(7, new HashSet<>(names).size());
				int k = Math.min(7, pool.size());
				assertEquals(k, names.stream().filter(pool::contains).count(), e.getKey() + " seed " + seed);
				assertTrue(pool.containsAll(names.subList(0, k)), e.getKey() + " seed " + seed);
			}
		}
	}
}
