package dev.nezo.burmaldaholic.gametest.independent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.chaos.ChaosApi;
import dev.nezo.burmaldaholic.chaos.ChaosData;
import dev.nezo.burmaldaholic.chaos.logic.GoldenHourState;
import dev.nezo.burmaldaholic.core.earnings.Earnings;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Tester-written GameTests (independent of the feature teams' own): registry/resource completeness,
 * advancements, money conservation when players leave / disconnect / tables break mid-round, offline
 * settlement without double pay, casino mode off = dormant, vanilla difficulty untouched.
 */
public class IndependentGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");
	private static final String NS = "burmaldaholic";

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static JsonObject resource(String path) {
		try (InputStream in = IndependentGameTests.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new AssertionError("cannot read " + path, e);
		}
	}

	private static boolean exists(String path) {
		return IndependentGameTests.class.getClassLoader().getResource(path) != null;
	}

	// ---- 1. registry / resources --------------------------------------------------------------

	@GameTest
	public void everyRegisteredThingHasNamesAndModels(GameTestHelper helper) {
		JsonObject en = resource("assets/burmaldaholic/lang/en_us.json");
		JsonObject ru = resource("assets/burmaldaholic/lang/ru_ru.json");
		JsonObject sounds = resource("assets/burmaldaholic/sounds.json");
		helper.assertTrue(en != null && ru != null && sounds != null, "merged lang + sounds.json on the classpath");
		List<String> errors = new ArrayList<>();
		for (var b : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(b);
			if (!id.getNamespace().equals(NS)) {
				continue;
			}
			checkKey(b.getDescriptionId(), "block " + id, en, ru, errors);
			if (!exists("assets/burmaldaholic/blockstates/" + id.getPath() + ".json")) {
				errors.add("block " + id + ": no blockstate");
			}
			if (!exists("data/burmaldaholic/loot_table/blocks/" + id.getPath() + ".json")) {
				errors.add("block " + id + ": no loot table");
			}
		}
		int items = 0;
		for (var item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (!id.getNamespace().equals(NS)) {
				continue;
			}
			items++;
			if (new ItemStack(item).getHoverName().getContents() instanceof TranslatableContents tc) {
				checkKey(tc.getKey(), "item " + id, en, ru, errors);
			} else {
				errors.add("item " + id + ": name is not translatable");
			}
			if (!exists("assets/burmaldaholic/items/" + id.getPath() + ".json")) {
				errors.add("item " + id + ": no items/ model definition (renders as missing)");
			}
		}
		for (var type : BuiltInRegistries.ENTITY_TYPE) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (id.getNamespace().equals(NS)) {
				checkKey(type.getDescriptionId(), "entity " + id, en, ru, errors);
			}
		}
		for (var sound : BuiltInRegistries.SOUND_EVENT) {
			Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
			if (id.getNamespace().equals(NS) && !sounds.has(id.getPath())) {
				errors.add("sound event " + id + ": not in sounds.json (silent)");
			}
		}
		helper.assertTrue(items >= 30, "items registered: " + items);
		helper.assertTrue(errors.isEmpty(), String.join("; ", errors));
		helper.succeed();
	}

	private static void checkKey(String key, String what, JsonObject en, JsonObject ru, List<String> errors) {
		if (!en.has(key) || !ru.has(key)) {
			errors.add(what + ": missing lang " + key + (en.has(key) ? " (ru_ru)" : ""));
		}
	}

	/** GAME_DESIGN §19: every advancement id with its parent, loaded without parse errors. */
	@GameTest
	public void allSpecAdvancementsLoadWithTheirParents(GameTestHelper helper) {
		Map<String, String> parent = Map.ofEntries(
			Map.entry("root", ""), Map.entry("first_bet", "root"), Map.entry("beginners_luck", "first_bet"),
			Map.entry("natural", "beginners_luck"), Map.entry("split_personality", "natural"), Map.entry("royal_flush", "beginners_luck"),
			Map.entry("shark_hunter", "beginners_luck"), Map.entry("three_sevens", "beginners_luck"), Map.entry("jackpot", "three_sevens"),
			Map.entry("zero_hero", "beginners_luck"), Map.entry("hot_shooter", "beginners_luck"), Map.entry("plinko_edge", "beginners_luck"),
			Map.entry("scratch_top", "first_bet"), Map.entry("on_fire", "beginners_luck"), Map.entry("black_cat", "first_bet"),
			Map.entry("loan_taken", "root"), Map.entry("knock_knock", "loan_taken"), Map.entry("hostile_takeover", "knock_knock"),
			Map.entry("clean_slate", "loan_taken"), Map.entry("not_today", "root"), Map.entry("scarred", "not_today"),
			Map.entry("heart_on_the_line", "first_bet"), Map.entry("devils_deal", "heart_on_the_line"), Map.entry("golden_hour", "root"),
			Map.entry("beam_me_up", "root"), Map.entry("the_house", "root"), Map.entry("house_always_wins", "the_house"),
			Map.entry("bankrupt", "the_house"), Map.entry("piglin_parlor", "root"), Map.entry("high_roller", "piglin_parlor"),
			Map.entry("baccarat_natural", "beginners_luck"), Map.entry("tie_streak", "baccarat_natural"), Map.entry("banco", "baccarat_natural"),
			Map.entry("bank_holder", "banco"), Map.entry("uth_four_x", "beginners_luck"), Map.entry("uth_house_seat", "uth_four_x"),
			Map.entry("uth_royal", "uth_four_x"));
		MinecraftServer server = helper.getLevel().getServer();
		List<String> errors = new ArrayList<>();
		for (Map.Entry<String, String> e : parent.entrySet()) {
			AdvancementHolder h = server.getAdvancements().get(Identifier.fromNamespaceAndPath(NS, "core/" + e.getKey()));
			if (h == null) {
				errors.add(e.getKey() + " not loaded");
				continue;
			}
			String p = h.value().parent().map(Identifier::getPath).orElse("core/");
			if (!p.equals("core/" + e.getValue())) {
				errors.add(e.getKey() + ": parent " + p + ", spec says " + e.getValue());
			}
			if (h.value().display().isEmpty()) {
				errors.add(e.getKey() + ": no display");
			}
		}
		// vip_silver … vip_netherite chain from root
		String prev = "root";
		for (String tier : List.of("silver", "gold", "platinum", "diamond", "netherite")) {
			AdvancementHolder h = server.getAdvancements().get(Identifier.fromNamespaceAndPath(NS, "core/vip_" + tier));
			if (h == null) {
				errors.add("vip_" + tier + " not loaded");
			} else if (!h.value().parent().map(Identifier::getPath).orElse("").equals("core/" + prev)) {
				errors.add("vip_" + tier + ": parent " + h.value().parent().orElse(null) + ", expected " + prev);
			}
			prev = "vip_" + tier;
		}
		long loaded = server.getAdvancements().getAllAdvancements().stream().filter(a -> a.id().getNamespace().equals(NS) && a.id().getPath().startsWith("core/")).count();
		helper.assertTrue(loaded == parent.size() + 5, "exactly the §19 set is loaded: " + loaded);
		helper.assertTrue(errors.isEmpty(), String.join("; ", errors));
		helper.succeed();
	}

	// ---- 2. money conservation ------------------------------------------------------------------

	private static CompoundTag rouletteBet(BetType type, List<Integer> nums, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("type", type.id());
		t.putIntArray("nums", nums.stream().mapToInt(Integer::intValue).toArray());
		t.putLong("amount", amount);
		return t;
	}

	private static final List<Bets.Bet> SLIP = List.of(
		new Bets.Bet(Spot.all(BetType.RED).getFirst(), 10), new Bets.Bet(Spot.of(BetType.STRAIGHT, 17), 5));

	private static Set<Long> slipReturns() {
		Set<Long> out = new HashSet<>();
		for (int r = 0; r < Wheel.POCKETS; r++) {
			out.add(Bets.totalReturn(SLIP, r, false));
		}
		return out;
	}

	private static RouletteTableBlockEntity roulette(GameTestHelper helper, ServerPlayer p) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, RouletteModule.TABLE.block());
		RouletteTableBlockEntity t = helper.getBlockEntity(pos, RouletteTableBlockEntity.class);
		t.onAction(p, "bet", rouletteBet(BetType.RED, Spot.all(BetType.RED).getFirst().numbers(), 10));
		t.onAction(p, "bet", rouletteBet(BetType.STRAIGHT, List.of(17), 5));
		return t;
	}

	/** §4.1 "roulette: spin proceeds": a player who walks away after Spin is still settled, never refunded. */
	@GameTest(maxTicks = 600)
	public void rouletteLeaverIsSettledByTheSpin(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		RouletteTableBlockEntity t = roulette(helper, p);
		helper.assertTrue(Economies.get().balance(p) == 985, "both bets debited");
		t.onAction(p, "spin", new CompoundTag());
		t.leave(p.getUUID(), LeaveReason.LEFT);
		Set<Long> possible = slipReturns();
		helper.succeedWhen(() -> {
			helper.assertTrue(t.openStakes().isEmpty(), "spin settled");
			long got = Economies.get().balance(p) - 985;
			helper.assertTrue(possible.contains(got), "return " + got + " is a real roulette outcome (a refund would be 15)");
			remove(helper, p);
		});
	}

	@GameTest
	public void rouletteBrokenWhileBettingRefundsExactly(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			roulette(helper, p);
			helper.destroyBlock(new BlockPos(1, 1, 1));
			helper.assertTrue(Economies.get().balance(p) == 1000, "nothing drawn yet: stake returned");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void rouletteBrokenAfterNoMoreBetsIsSpunNow(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		RouletteTableBlockEntity t = roulette(helper, p);
		t.onAction(p, "spin", new CompoundTag());
		Set<Long> possible = slipReturns();
		possible.remove(15L); // 15 back is only a refund (no pocket returns exactly the stake with this slip)
		helper.succeedWhen(() -> {
			helper.assertTrue(t.phase().equals("no_more_bets") || t.phase().equals("spin"), "past no more bets: " + t.phase());
			helper.destroyBlock(new BlockPos(1, 1, 1));
			long got = Economies.get().balance(p) - 985;
			remove(helper, p);
			helper.assertTrue(possible.contains(got), "review B1: settled with a fair spin, not refunded (got " + got + ")");
		});
	}

	private static BlackjackTableBlockEntity blackjackWin(GameTestHelper helper, ServerPlayer p) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity t = helper.getBlockEntity(pos, BlackjackTableBlockEntity.class);
		// player 10,10; dealer 10 up, 7 hole → 20 vs 17 (dealer stands): the player wins
		List<Card> cards = new ArrayList<>(List.of(Card.of(10), Card.of(10), Card.of(10), Card.of(7)));
		for (int i = 0; i < 10; i++) {
			cards.add(Card.of(2));
		}
		t.stackCardsForTests(cards);
		t.sit(p);
		CompoundTag bet = new CompoundTag();
		bet.putLong("amount", 10);
		t.onAction(p, "bet", bet);
		return t;
	}

	@GameTest
	public void blackjackTableBrokenMidRoundStandsAndPays(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			BlackjackTableBlockEntity t = blackjackWin(helper, p);
			helper.assertTrue(t.round() != null && Economies.get().balance(p) == 990, "dealt, stake taken");
			helper.destroyBlock(new BlockPos(1, 1, 1));
			helper.assertTrue(Economies.get().balance(p) == 1010, "auto-stand 20 vs 17 pays 1:1 (a refund would leave 1000), got "
				+ Economies.get().balance(p));
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	private static final Set<Long> COPPER_RETURNS_AT_5 = Set.of(0L, 10L, 15L, 50L, 100L, 150L, 300L, 750L);

	@GameTest
	public void slotsLeaverAndBrokenMachineSettleThePendingSpin(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			BlockPos pos = new BlockPos(1, 1, 1);
			helper.setBlock(pos, SlotsModule.MACHINES.get(Tier.COPPER).block());
			SlotMachineBlockEntity m = helper.getBlockEntity(pos, SlotMachineBlockEntity.class);
			CompoundTag bet = new CompoundTag();
			bet.putLong("line_bet", 5);
			m.onAction(p, "spin", bet);
			helper.assertTrue(m.spinning() && Economies.get().balance(p) == 995, "spinning");
			m.leave(p.getUUID(), LeaveReason.LEFT);
			long got = Economies.get().balance(p) - 995;
			helper.assertTrue(!m.spinning() && m.openStakes().isEmpty(), "settled on leave");
			helper.assertTrue(COPPER_RETURNS_AT_5.contains(got), "real copper outcome, got " + got);
			long before = Economies.get().balance(p);
			m.onAction(p, "spin", bet);
			helper.assertTrue(m.spinning(), "spinning again");
			helper.destroyBlock(pos);
			got = Economies.get().balance(p) - (before - 5);
			helper.assertTrue(COPPER_RETURNS_AT_5.contains(got), "broken machine settles the drawn spin (a refund would be 5), got " + got);
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	// ---- 3. offline settlement ------------------------------------------------------------------

	/**
	 * Disconnect mid-round during a Golden Hour: the round is auto-completed and credited offline once; the
	 * deferred PLAY_RESOLVED on the next join pays the Golden Hour bonus exactly once and never the payout again.
	 * Runs synchronously (one tick) so the server-wide Golden Hour cannot leak into other tests.
	 */
	@GameTest
	public void offlineSettlementPaysExactlyOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = player(helper, 1000);
		ChaosData data = ChaosData.get(server);
		GoldenHourState gh = data.goldenHour();
		long now = server.overworld().getGameTime();
		boolean ghWasActive = ChaosApi.goldenHourRemaining(server) > 0;
		try {
			if (!ghWasActive) {
				gh.start(now, 3600, 0);
			}
			BlackjackTableBlockEntity t = blackjackWin(helper, p);
			helper.assertTrue(Economies.get().balance(p) == 990, "stake taken");
			remove(helper, p); // disconnect
			t.leave(p.getUUID(), LeaveReason.DISCONNECT);
			helper.assertTrue(Economies.get().balance(server, p.getUUID()) == 1010, "auto-stand, paid offline: "
				+ Economies.get().balance(server, p.getUUID()));
			helper.assertTrue(PlayResults.pending(server, p.getUUID()) == 1, "round queued for the next join");
			PlayResults.deliver(p); // next join
			helper.assertTrue(Economies.get().balance(server, p.getUUID()) == 1020, "Golden Hour bonus (net 10 × 1) paid once on join: "
				+ Economies.get().balance(server, p.getUUID()));
			helper.assertTrue(PlayResults.pending(server, p.getUUID()) == 0, "mailbox emptied");
			PlayResults.deliver(p); // another join
			helper.assertTrue(Economies.get().balance(server, p.getUUID()) == 1020, "no double pay");
		} finally {
			if (!ghWasActive) {
				gh.stop(now, 0);
				data.setDirty();
			}
		}
		helper.succeed();
	}

	// ---- 4. casino mode off ---------------------------------------------------------------------

	/** §2.1 dormant mode, checked synchronously: no bets, no stakes, no chaos, no earnings; data untouched. */
	@GameTest
	public void casinoModeOffIsDormant(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		boolean before = CasinoMode.isEnabled(level);
		ServerPlayer p = player(helper, 1000);
		try {
			BlockPos pos = new BlockPos(1, 1, 1);
			helper.setBlock(pos, BlackjackModule.TABLE.block());
			BlackjackTableBlockEntity t = helper.getBlockEntity(pos, BlackjackTableBlockEntity.class);
			CasinoMode.set(server, false);
			CompoundTag bet = new CompoundTag();
			bet.putLong("amount", 10);
			t.onAction(p, "bet", bet);
			helper.assertTrue(t.round() == null && Economies.get().balance(p) == 1000, "tables take no bets");
			helper.assertTrue(!Stakes.chips(p, "extras", 10, 1, 100).isOk(), "no stakes");
			helper.assertTrue(!Stakes.hearts(p, "extras", 1).isOk(), "no heart wagers");
			String chaos = ChaosApi.trigger(p, "chip_shower", "gametest");
			helper.assertTrue(chaos == null || chaos.equals("disabled"), "no chaos: " + chaos);
			Earnings.onTrade(p, new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD), 12, 1, 0.05f));
			helper.assertTrue(Economies.get().balance(p) == 1000, "no trade earnings, balance preserved");
			CasinoMode.set(server, true);
			Earnings.onTrade(p, new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD), 12, 1, 0.05f));
			helper.assertTrue(Economies.get().balance(p) > 1000, "control: the same trade pays with casino mode on");
		} finally {
			CasinoMode.set(server, before);
			remove(helper, p);
		}
		helper.succeed();
	}

	/** §2.2: the mod never changes difficulty, hardcore or vanilla game rules (toggling casino mode included). */
	@GameTest
	public void vanillaDifficultyAndRulesUntouched(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Difficulty difficulty = server.getWorldData().getDifficulty();
		boolean hardcore = server.getWorldData().isHardcore();
		boolean keepInventory = level.getGameRules().get(GameRules.KEEP_INVENTORY);
		boolean casino = CasinoMode.isEnabled(level);
		try {
			CasinoMode.set(server, !casino);
			CasinoMode.set(server, casino);
		} finally {
			CasinoMode.set(server, casino);
		}
		helper.assertTrue(server.getWorldData().getDifficulty() == difficulty, "difficulty unchanged");
		helper.assertTrue(server.getWorldData().isHardcore() == hardcore, "hardcore flag unchanged");
		helper.assertTrue(level.getGameRules().get(GameRules.KEEP_INVENTORY) == keepInventory, "keepInventory unchanged");
		helper.succeed();
	}
}
