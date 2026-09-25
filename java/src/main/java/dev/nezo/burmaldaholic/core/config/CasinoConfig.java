package dev.nezo.burmaldaholic.core.config;

import dev.nezo.burmaldaholic.core.config.sections.BaccaratConfig;
import dev.nezo.burmaldaholic.core.config.sections.BlackjackConfig;
import dev.nezo.burmaldaholic.core.config.sections.BotsConfig;
import dev.nezo.burmaldaholic.core.config.sections.CardsConfig;
import dev.nezo.burmaldaholic.core.config.sections.ChaosConfig;
import dev.nezo.burmaldaholic.core.config.sections.ContractsConfig;
import dev.nezo.burmaldaholic.core.config.sections.CoreConfig;
import dev.nezo.burmaldaholic.core.config.sections.CrapsConfig;
import dev.nezo.burmaldaholic.core.config.sections.DebugConfig;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.config.sections.LastChanceConfig;
import dev.nezo.burmaldaholic.core.config.sections.LoanConfig;
import dev.nezo.burmaldaholic.core.config.sections.MultiplayerConfig;
import dev.nezo.burmaldaholic.core.config.sections.OwnershipConfig;
import dev.nezo.burmaldaholic.core.config.sections.PokerConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.config.sections.RouletteConfig;
import dev.nezo.burmaldaholic.core.config.sections.SlotsConfig;
import dev.nezo.burmaldaholic.core.config.sections.StreakConfig;
import dev.nezo.burmaldaholic.core.config.sections.UthConfig;
import dev.nezo.burmaldaholic.core.config.sections.VipConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import dev.nezo.burmaldaholic.core.config.sections.WorldgenConfig;
import java.util.function.Supplier;

/**
 * Typed access to EVERY key of docs/design/CONFIG.md (registered by core, so the file, the config
 * screen, {@code /casino config} and client sync cover all of them from day one).
 *
 * <pre>
 * int decks = CasinoConfig.blackjack().decks;            // always call the accessor again (reloads!)
 * int diamond = CasinoConfig.economy().ore.diamond;
 * </pre>
 *
 * Values are server-authoritative; on a remote client they are the values synced from the server.
 * Need a new key? Ask core to add the field to {@code core/config/sections/<Section>Config} (and the
 * design team to add it to CONFIG.md / STRINGS.md §config).
 */
public final class CasinoConfig {
	private static ConfigHandle<CoreConfig> core;
	private static ConfigHandle<EconomyConfig> economy;
	private static ConfigHandle<ContractsConfig> contracts;
	private static ConfigHandle<WagerConfig> wager;
	private static ConfigHandle<VipConfig> vip;
	private static ConfigHandle<BlackjackConfig> blackjack;
	private static ConfigHandle<PokerConfig> poker;
	private static ConfigHandle<SlotsConfig> slots;
	private static ConfigHandle<RouletteConfig> roulette;
	private static ConfigHandle<CrapsConfig> craps;
	private static ConfigHandle<BaccaratConfig> baccarat;
	private static ConfigHandle<UthConfig> uth;
	private static ConfigHandle<CardsConfig> cards;
	private static ConfigHandle<ExtrasConfig> extras;
	private static ConfigHandle<PvpConfig> pvp;
	private static ConfigHandle<LoanConfig> loan;
	private static ConfigHandle<ChaosConfig> chaos;
	private static ConfigHandle<StreakConfig> streak;
	private static ConfigHandle<LastChanceConfig> lastChance;
	private static ConfigHandle<WorldgenConfig> worldgen;
	private static ConfigHandle<OwnershipConfig> ownership;
	private static ConfigHandle<MultiplayerConfig> multiplayer;
	private static ConfigHandle<BotsConfig> bots;
	private static ConfigHandle<DebugConfig> debug;

	private CasinoConfig() {}

	/** Core only (CoreModule.register). Section order = CONFIG.md order = file order. */
	public static void registerAll(ConfigManager m) {
		core = m.register("core", CoreConfig.class, CoreConfig::new);
		economy = m.register("economy", EconomyConfig.class, EconomyConfig::new);
		contracts = m.register("contracts", ContractsConfig.class, ContractsConfig::new);
		wager = m.register("wager", WagerConfig.class, WagerConfig::new);
		vip = m.register("vip", VipConfig.class, VipConfig::new);
		blackjack = m.register("blackjack", BlackjackConfig.class, BlackjackConfig::new);
		poker = m.register("poker", PokerConfig.class, PokerConfig::new);
		slots = m.register("slots", SlotsConfig.class, SlotsConfig::new);
		roulette = m.register("roulette", RouletteConfig.class, RouletteConfig::new);
		craps = m.register("craps", CrapsConfig.class, CrapsConfig::new);
		baccarat = m.register("baccarat", BaccaratConfig.class, BaccaratConfig::new);
		uth = m.register("uth", UthConfig.class, UthConfig::new);
		cards = m.register("cards", CardsConfig.class, CardsConfig::new);
		extras = m.register("extras", ExtrasConfig.class, ExtrasConfig::new);
		pvp = m.register("pvp", PvpConfig.class, PvpConfig::new);
		loan = m.register("loan", LoanConfig.class, LoanConfig::new);
		chaos = m.register("chaos", ChaosConfig.class, ChaosConfig::new);
		streak = m.register("streak", StreakConfig.class, StreakConfig::new);
		lastChance = m.register("lastChance", LastChanceConfig.class, LastChanceConfig::new);
		worldgen = m.register("worldgen", WorldgenConfig.class, WorldgenConfig::new);
		ownership = m.register("ownership", OwnershipConfig.class, OwnershipConfig::new);
		multiplayer = m.register("multiplayer", MultiplayerConfig.class, MultiplayerConfig::new);
		bots = m.register("bots", BotsConfig.class, BotsConfig::new);
		debug = m.register("debug", DebugConfig.class, DebugConfig::new);
	}

	private static <T> T get(ConfigHandle<T> handle, Supplier<T> fallback) {
		return handle == null ? fallback.get() : handle.get();
	}

	public static CoreConfig core() {
		return get(core, CoreConfig::new);
	}

	public static EconomyConfig economy() {
		return get(economy, EconomyConfig::new);
	}

	public static ContractsConfig contracts() {
		return get(contracts, ContractsConfig::new);
	}

	public static WagerConfig wager() {
		return get(wager, WagerConfig::new);
	}

	public static VipConfig vip() {
		return get(vip, VipConfig::new);
	}

	public static BlackjackConfig blackjack() {
		return get(blackjack, BlackjackConfig::new);
	}

	public static PokerConfig poker() {
		return get(poker, PokerConfig::new);
	}

	public static SlotsConfig slots() {
		return get(slots, SlotsConfig::new);
	}

	public static RouletteConfig roulette() {
		return get(roulette, RouletteConfig::new);
	}

	public static CrapsConfig craps() {
		return get(craps, CrapsConfig::new);
	}

	/** Baccarat and Chemin de fer (GAME_DESIGN §20). */
	public static BaccaratConfig baccarat() {
		return get(baccarat, BaccaratConfig::new);
	}

	/** Ultimate Texas Hold'em (GAME_DESIGN §21). */
	public static UthConfig uth() {
		return get(uth, UthConfig::new);
	}

	/** The card tables' presentation (theme, solo speed; animation/cards.md §8). */
	public static CardsConfig cards() {
		return get(cards, CardsConfig::new);
	}

	public static ExtrasConfig extras() {
		return get(extras, ExtrasConfig::new);
	}

	/** PvP modes (PVP.md §13). */
	public static PvpConfig pvp() {
		return get(pvp, PvpConfig::new);
	}

	/** Seats &amp; bots (BOTS.md §9). */
	public static BotsConfig bots() {
		return get(bots, BotsConfig::new);
	}

	public static LoanConfig loan() {
		return get(loan, LoanConfig::new);
	}

	public static ChaosConfig chaos() {
		return get(chaos, ChaosConfig::new);
	}

	public static StreakConfig streak() {
		return get(streak, StreakConfig::new);
	}

	public static LastChanceConfig lastChance() {
		return get(lastChance, LastChanceConfig::new);
	}

	public static WorldgenConfig worldgen() {
		return get(worldgen, WorldgenConfig::new);
	}

	public static OwnershipConfig ownership() {
		return get(ownership, OwnershipConfig::new);
	}

	public static MultiplayerConfig multiplayer() {
		return get(multiplayer, MultiplayerConfig::new);
	}

	public static DebugConfig debug() {
		return get(debug, DebugConfig::new);
	}
}
