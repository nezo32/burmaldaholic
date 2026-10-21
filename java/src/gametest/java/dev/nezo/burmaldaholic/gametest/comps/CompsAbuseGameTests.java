package dev.nezo.burmaldaholic.gametest.comps;

import dev.nezo.burmaldaholic.comps.CompsModule;
import dev.nezo.burmaldaholic.comps.CompsService;
import dev.nezo.burmaldaholic.comps.Rentals;
import dev.nezo.burmaldaholic.comps.net.CompsActionPayload;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * T-EC2 (tester): trying to break comps — redemption without points, rental horse item leaks / logout, suite night in
 * the Nether and the End (the bed must not explode).
 */
public class CompsAbuseGameTests {
	private static final Transaction TEST = Transaction.of("comps", "gametest");

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), 0, TEST);
		return p;
	}

	private static void redeem(ServerPlayer p, String id) {
		CounterSessions.resetCooldown(p);
		CompsModule.handle(p, new CompsActionPayload(CompsActionPayload.REDEEM, id, 1));
	}

	private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
		}
		return n;
	}

	@GameTest
	public void redeemWithoutEnoughPointsGivesAndChargesNothing(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		try {
			CompsService.put(p, CompsService.state(p).earn(14_999)); // 14.999 points < 15
			CounterSessions.open(p, CounterSource.command());
			redeem(p, "token_10");
			redeem(p, "snack");
			helper.assertTrue(count(p, dev.nezo.burmaldaholic.comps.FreeBets.TOKEN) == 0 && count(p, Items.COOKED_BEEF) == 0, "nothing given");
			helper.assertTrue(CompsService.state(p).pointsMilli() == 14_999, "nothing charged: " + CompsService.state(p).pointsMilli());
			// strider in the Overworld: refused, not charged
			CompsService.put(p, CompsService.state(p).earn(100_000));
			long before = CompsService.state(p).pointsMilli();
			redeem(p, "strider");
			helper.assertTrue(CompsService.state(p).pointsMilli() == before, "strider in the Overworld costs nothing");
		} finally {
			CounterSessions.close(p);
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}

	/**
	 * A rented horse: the owner rides it and opens its inventory with the inventory key (OPEN_INVENTORY packet, which
	 * no UseEntityCallback sees). Neither the saddle may come out, nor may the owner's own horse armour vanish with the
	 * horse at expiry / logout.
	 */
	@GameTest(maxTicks = 200)
	public void rentalHorseLeaksNoItems(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = player(helper);
		CompsService.put(p, CompsService.state(p).earn(100_000));
		CounterSessions.open(p, CounterSource.command());
		redeem(p, "horse");
		List<AbstractHorse> horses = helper.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(p.blockPosition()).inflate(4));
		helper.assertTrue(horses.size() == 1, "one rental horse spawned: " + horses.size());
		AbstractHorse horse = horses.getFirst();
		helper.assertTrue(p.startRiding(horse, true, true), "the owner rides the rental");
		p.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
		boolean opened = p.containerMenu instanceof HorseInventoryMenu;
		int saddlesTaken = 0;
		if (opened) {
			for (int i = 0; i < p.containerMenu.slots.size(); i++) {
				Slot s = p.containerMenu.slots.get(i);
				if (s.getItem().is(Items.SADDLE)) {
					p.containerMenu.clicked(i, 0, ContainerInput.QUICK_MOVE, p);
				}
			}
			saddlesTaken = count(p, Items.SADDLE);
		}
		p.closeContainer();
		p.stopRiding();
		CounterSessions.close(p);
		server.getPlayerList().remove(p); // logout: the tick sweep discards the rental of an offline owner
		final boolean wasOpened = opened;
		final int taken = saddlesTaken;
		helper.succeedWhen(() -> {
			helper.assertTrue(!horse.isAlive() || horse.isRemoved(), "the rental is gone after the owner left");
			helper.assertTrue(taken == 0, "the owner took " + taken + " saddle(s) out of the rental horse (inventory opened: " + wasOpened + ")");
		});
	}

	/** A bed inside a casino in the Nether / End: using it must never explode; with the day's credit it checks in. */
	@GameTest(maxTicks = 100)
	public void suiteNightBedNeverExplodesInTheNetherOrEnd(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var share = FabricLoader.getInstance().getObjectShare();
		Object casinoAt = share.get("burmaldaholic:worldgen/casino_at");
		share.put("burmaldaholic:worldgen/casino_at", (BiFunction<ServerLevel, BlockPos, Boolean>) (l, pos) -> true); // "inside a casino"
		try {
			for (ResourceKey<Level> dim : List.of(Level.NETHER, Level.END)) {
				ServerLevel level = server.getLevel(dim);
				helper.assertTrue(level != null, dim + " loaded");
				ServerPlayer p = player(helper);
				try {
					BlockPos foot = new BlockPos(8, 100, 8);
					for (BlockPos b : BlockPos.betweenClosed(foot.offset(-2, -1, -3), foot.offset(2, 3, 2))) {
						level.setBlockAndUpdate(b, b.getY() == foot.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
					BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
					level.setBlockAndUpdate(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
					level.setBlockAndUpdate(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD));
					p.teleportTo(level, foot.getX() + 0.5, foot.getY(), foot.getZ() + 2.5, Set.of(), 180, 0, true);
					helper.assertTrue(p.level() == level, "player in " + dim);
					BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(foot), Direction.UP, foot, false);
					// 1) no suite credit
					p.gameMode.useItemOn(p, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
					helper.assertTrue(level.getBlockState(foot).getBlock() instanceof BedBlock && level.getBlockState(foot.north()).getBlock() instanceof BedBlock,
						dim + ": no credit, the bed is still there (no explosion)");
					// 2) with today's credit: check in, still no explosion
					CompsService.put(p, CompsService.state(p).earn(100_000));
					CounterSessions.open(p, CounterSource.command());
					redeem(p, "suite");
					p.gameMode.useItemOn(p, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
					helper.assertTrue(level.getBlockState(foot).getBlock() instanceof BedBlock, dim + ": checked in, the bed is still there");
					helper.assertTrue(p.isAlive() && p.getHealth() >= p.getMaxHealth() - 0.01f, dim + ": the player took no damage");
					helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), dim + ": suite effects applied");
				} finally {
					CounterSessions.close(p);
					server.getPlayerList().remove(p);
				}
			}
		} finally {
			if (casinoAt != null) share.put("burmaldaholic:worldgen/casino_at", casinoAt);
		}
		helper.succeed();
	}
}
