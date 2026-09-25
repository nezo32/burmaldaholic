package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider.TablePreset;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoNpcs;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.npc.CasinoNpcEntity;
import dev.nezo.burmaldaholic.worldgen.npc.NpcContent;
import dev.nezo.burmaldaholic.worldgen.npc.NpcInteractions;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;

/** Casino staff (Croupier shop, Piglin Dealer), table presets and the Parlor advancement in a real world. */
public class WorldgenNpcGameTests {
	private static final Transaction TEST = Transaction.of("worldgen", "gametest");

	@SuppressWarnings("removal")
	private static void withPlayer(GameTestHelper helper, long balance, BlockPos at, Consumer<ServerPlayer> body) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		BlockPos abs = helper.absolutePos(at);
		player.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		try {
			Economies.get().setBalance(server, player.getUUID(), balance, TEST);
			body.accept(player);
		} finally {
			server.getPlayerList().remove(player);
		}
	}

	/** Registers a fake generated casino covering the test area (removed again by {@link #forget}). */
	private static String casinoAround(GameTestHelper helper, CasinoKind kind) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		String dim = level.dimension().identifier().toString();
		String id = CasinoRecord.idFor(dim, o.getX(), o.getY(), o.getZ()) + "|test";
		CasinoIndex.get(level.getServer()).setAnchor(id, dim, "test", kind,
			new Geometry.Box(o.getX(), o.getY(), o.getZ(), o.getX() + 7, o.getY() + 5, o.getZ() + 7)); // inside the 8×8 test area only
		return id;
	}

	private static void forget(GameTestHelper helper, String id) {
		CasinoIndex.get(helper.getLevel().getServer()).removeIf(r -> r.id().equals(id));
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	@GameTest
	public void worldgenSpawnsItsOwnStaff(GameTestHelper helper) {
		for (NpcRole role : new NpcRole[] {NpcRole.CROUPIER, NpcRole.PIGLIN_DEALER, NpcRole.SHULKER_CROUPIER}) {
			Entity e = CasinoNpcs.spawn(helper.getLevel(), role, helper.absolutePos(new BlockPos(2, 1, 2)), 90f);
			helper.assertTrue(e instanceof CasinoNpcEntity npc && npc.role() == role, role + " spawned from its marker role");
			helper.assertTrue(e.entityTags().contains(CasinoNpcs.NPC_TAG), "tagged as worldgen NPC");
			e.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void croupierSellsWithVipAndFundsChecks(GameTestHelper helper) {
		CasinoNpcEntity croupier = helper.spawn(NpcContent.CROUPIER, new BlockPos(2, 1, 2));
		withPlayer(helper, 30, new BlockPos(3, 1, 3), player -> {
			NpcInteractions.interact(player, croupier);
			CasinoMenu.request(player, NpcInteractions.PAGE, "buy:lucky_coin", 0);
			helper.assertTrue(Economies.get().balance(player) == 5, "Lucky Coin costs 25, balance " + Economies.get().balance(player));
			helper.assertTrue(count(player, ExtrasModule.LUCKY_COIN) == 1, "coin handed out");
			CasinoMenu.request(player, NpcInteractions.PAGE, "buy:lucky_coin", 0);
			helper.assertTrue(Economies.get().balance(player) == 5 && count(player, ExtrasModule.LUCKY_COIN) == 1, "not enough chips: nothing sold");
			Economies.get().setBalance(player.level().getServer(), player.getUUID(), 1000, TEST);
			CasinoMenu.request(player, NpcInteractions.PAGE, "buy:scratch_card_gold", 0);
			helper.assertTrue(Economies.get().balance(player) == 1000 && count(player, ExtrasModule.SCRATCH_CARD_GOLD) == 0,
				"gold scratch card needs Silver VIP");
			CasinoMenu.request(player, NpcInteractions.PAGE, "buy:scratch_card", 0);
			helper.assertTrue(Economies.get().balance(player) == 990 && count(player, ExtrasModule.SCRATCH_CARD) == 1, "basic card for 10");
			// Walked away: the shop page is gone, nothing is sold.
			player.snapTo(player.getX() + 40, player.getY(), player.getZ());
			CasinoMenu.request(player, NpcInteractions.PAGE, "buy:scratch_card", 0);
			helper.assertTrue(Economies.get().balance(player) == 990, "no sale out of range");
		});
		croupier.discard();
		helper.succeed();
	}

	@GameTest
	public void piglinDealerOpensNearestTable(GameTestHelper helper) {
		BlockPos far = new BlockPos(5, 1, 1);
		BlockPos near = new BlockPos(1, 1, 1);
		helper.setBlock(far, PokerModule.TABLE.block());
		helper.setBlock(near, RouletteModule.TABLE.block());
		CasinoNpcEntity dealer = helper.spawn(NpcContent.PIGLIN_DEALER, new BlockPos(2, 1, 2));
		withPlayer(helper, 100, new BlockPos(3, 1, 3), player -> {
			NpcInteractions.interact(player, dealer);
			helper.assertTrue(player.containerMenu instanceof CasinoTableMenu menu && menu.pos().equals(helper.absolutePos(near)),
				"the nearest table (roulette) opened, got " + player.containerMenu);
			player.closeContainer();
		});
		dealer.discard();
		helper.succeed();
	}

	@GameTest
	public void presetsFollowTheGeneratedCasino(GameTestHelper helper) {
		BlockPos poker = new BlockPos(1, 1, 1);
		BlockPos blackjack = new BlockPos(3, 1, 1);
		BlockPos roulette = new BlockPos(5, 1, 1);
		helper.setBlock(poker, PokerModule.TABLE.block());
		helper.setBlock(blackjack, BlackjackModule.TABLE.block());
		helper.setBlock(roulette, RouletteModule.TABLE.block());
		CasinoTableBlockEntity pokerTable = helper.getBlockEntity(poker, CasinoTableBlockEntity.class);
		BlackjackTableBlockEntity bj = helper.getBlockEntity(blackjack, BlackjackTableBlockEntity.class);
		RouletteTableBlockEntity rt = helper.getBlockEntity(roulette, RouletteTableBlockEntity.class);
		helper.assertTrue(pokerTable.preset().isEmpty() && !bj.isHighRoller() && !rt.isHighRoller(), "ordinary tables outside casinos");

		String parlor = casinoAround(helper, CasinoKind.PIGLIN_PARLOR);
		try {
			TablePreset p = pokerTable.preset().orElseThrow();
			helper.assertTrue(p.id().equals("parlor_poker") && p.pokerStakes().equals("low") && p.pokerBots() == 3
				&& p.pokerBotMix().equals(TablePreset.REGULAR_HEAVY), "Parlor poker: Low, 3 bots, Regular-heavy");
			helper.assertTrue(bj.preset().isEmpty() && !bj.isHighRoller(), "Parlor blackjack stays standard");
		} finally {
			forget(helper, parlor);
		}
		String lounge = casinoAround(helper, CasinoKind.HIGH_ROLLER);
		try {
			helper.assertTrue(bj.preset().map(TablePreset::minTier).orElse(0) == dev.nezo.burmaldaholic.core.service.VipTiers.GOLD
				&& bj.isHighRoller(), "Lounge blackjack is a High-Roller table (Gold VIP)");
			helper.assertTrue(rt.isHighRoller() && rt.preset().map(TablePreset::minBet).orElse(0L) == 100, "Lounge roulette: min 100");
			helper.assertTrue(pokerTable.preset().isEmpty(), "no poker preset in the Lounge");
		} finally {
			forget(helper, lounge);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	@SuppressWarnings("removal")
	public void enteringParlorGrantsAdvancement(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
		player.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		String parlor = casinoAround(helper, CasinoKind.PIGLIN_PARLOR);
		helper.succeedWhen(() -> {
			helper.assertTrue(CasinoAdvancements.has(player, "piglin_parlor"), "piglin_parlor granted on entering");
			forget(helper, parlor);
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}
}
