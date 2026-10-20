package dev.nezo.burmaldaholic.gametest.v2.extras;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.games.extras.server.ScratchData;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tester (T-E2) GameTests for B4 + money: the scratch stack never moves; no dupe / loss when the stack leaves the
 * inventory mid-card (dropped, death drops), with a full inventory at the finish, and the same outcome on resume.
 * Everything goes through the real packet handler (ExtrasActionPayload codec → the player's listener).
 */
public class TesterE2ScratchGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		// survival: a creative player's Inventory.add deletes what does not fit (vanilla), which would hide a lost card
		p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), 0, TEST);
		return p;
	}

	private static void scratch(ServerPlayer p, String action, String id, int cell) {
		CompoundTag t = new CompoundTag();
		t.putString("kind", "basic");
		t.putString("id", id);
		t.putInt("cell", cell);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), p.level().registryAccess());
		ExtrasActionPayload.CODEC.encode(buf, new ExtrasActionPayload(ScratchGame.SCREEN, action, t));
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(ExtrasActionPayload.CODEC.decode(buf)));
	}

	private static ScratchData store(GameTestHelper helper) {
		return ScratchData.get(helper.getLevel().getServer());
	}

	private static long balance(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	private static int fresh(ServerPlayer p) {
		return p.getInventory().countItem(ExtrasModule.SCRATCH_CARD);
	}

	private static int used(ServerPlayer p) {
		return p.getInventory().countItem(ExtrasModule.SCRATCH_CARD_USED);
	}

	private static List<ItemEntity> dropped(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(8));
	}

	private static int droppedCount(GameTestHelper helper, ServerPlayer p, net.minecraft.world.item.Item item) {
		return dropped(helper, p).stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static List<String> nonCobble(ServerPlayer p) {
		List<String> out = new java.util.ArrayList<>();
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack st = p.getInventory().getItem(i);
			if (!st.isEmpty() && !st.is(Items.COBBLESTONE)) out.add(i + ":" + st);
		}
		return out;
	}

	/** Starts a card from a stack of {@code n} in slot {@code slot} and reveals {@code cells} cells; returns its id. */
	private static String start(GameTestHelper helper, ServerPlayer p, int slot, int n, int... cells) {
		p.getInventory().setItem(slot, new ItemStack(ExtrasModule.SCRATCH_CARD, n));
		p.getInventory().setSelectedSlot(slot);
		ScratchGame.use(p, p.getInventory().getItem(slot), Scratch.Kind.BASIC);
		scratch(p, "scratch", "", cells[0]);
		String id = store(helper).active(p.getUUID(), "basic");
		helper.assertTrue(id != null, "card started");
		for (int i = 1; i < cells.length; i++) {
			scratch(p, "scratch", id, cells[i]);
		}
		return id;
	}

	/**
	 * The stack is DROPPED mid-card (Q on the hotbar): nothing more is revealed or paid while it is gone (no "all", no
	 * cell); picked up into another slot, the SAME card (same face, same mask) resumes and pays its own prize once,
	 * taking exactly one card.
	 */
	@GameTest(maxTicks = 100)
	public void droppedStackMidCardNoDupeSameOutcome(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		String id = start(helper, p, 3, 4, 0, 1);
		CompoundTag face = store(helper).face(id);
		ItemStack stack = p.getInventory().removeItemNoUpdate(3); // dropped / moved away
		scratch(p, "scratch", id, 2);
		scratch(p, "all", id, -1);
		scratch(p, "scratch", "", 5);
		CompoundTag now = store(helper).face(id);
		helper.assertTrue(now != null && now.getIntOr("mask", 0) == face.getIntOr("mask", 0), "nothing revealed without the stack: " + now);
		helper.assertTrue(balance(p) == 0 && used(p) == 0, "nothing paid, no used card without the stack");
		helper.assertTrue(id.equals(store(helper).active(p.getUUID(), "basic")), "still the active card");
		p.getInventory().setItem(7, stack); // picked up into another slot
		scratch(p, "all", "", -1); // a screen that lost the id
		helper.assertTrue(store(helper).active(p.getUUID(), "basic") == null, "finished");
		helper.assertTrue(fresh(p) == 3 && used(p) == 1, "one card taken, one used: fresh " + fresh(p) + " used " + used(p));
		helper.assertTrue(p.getInventory().getItem(7).getCount() == 3, "taken from the stack it was picked up into");
		helper.assertTrue(balance(p) == face.getLongOr("prize", -1), "its own prize, once: " + balance(p) + " vs " + face.getLongOr("prize", -1));
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	/**
	 * Death mid-card (the inventory drops on the ground): no payout, the card waits; the cards on the ground are plain
	 * fresh cards (no face, no id: the outcome is not on the item); picking them up resumes the same card.
	 */
	@GameTest(maxTicks = 100)
	public void deathMidCardNoLossNoDupe(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		String id = start(helper, p, 0, 2, 4);
		CompoundTag face = store(helper).face(id);
		p.getInventory().dropAll(); // what death does without keepInventory
		helper.assertTrue(fresh(p) == 0 && droppedCount(helper, p, ExtrasModule.SCRATCH_CARD) == 2, "both cards on the ground");
		for (ItemEntity e : dropped(helper, p)) {
			helper.assertTrue(ScratchGame.data(e.getItem()) == null, "nothing on the dropped item");
		}
		scratch(p, "all", id, -1);
		helper.assertTrue(balance(p) == 0 && store(helper).face(id).getIntOr("mask", 0) == face.getIntOr("mask", 0), "nothing revealed/paid while dead");
		for (ItemEntity e : dropped(helper, p)) { // respawn + pick up
			p.getInventory().add(e.getItem().copy());
			e.discard();
		}
		scratch(p, "all", id, -1);
		helper.assertTrue(fresh(p) == 1 && used(p) == 1 && balance(p) == face.getLongOr("prize", -1),
			"the same card finished: fresh " + fresh(p) + " used " + used(p) + " paid " + balance(p) + " vs " + face.getLongOr("prize", -1));
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	/**
	 * Full inventory at the ninth cell: a stack of 1 frees its slot (the used card lands there); a stack of 2 leaves no
	 * room, so the used card is dropped at the player, never lost; the prize is paid either way.
	 */
	@GameTest(maxTicks = 100)
	public void fullInventoryAtFinish(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		var inv = p.getInventory();
		for (int i = 0; i < 36; i++) inv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		String id = start(helper, p, 5, 2, 0);
		long prize = store(helper).face(id).getLongOr("prize", -1);
		helper.assertTrue(inv.getItem(5).getCount() == 2, "stack untouched while scratching");
		scratch(p, "all", id, -1);
		helper.assertTrue(inv.getItem(5).is(ExtrasModule.SCRATCH_CARD) && inv.getItem(5).getCount() == 1, "one card taken from slot 5");
		helper.assertTrue(used(p) + droppedCount(helper, p, ExtrasModule.SCRATCH_CARD_USED) == 1, "the used card dropped, not lost: inv "
			+ used(p) + " ground " + droppedCount(helper, p, ExtrasModule.SCRATCH_CARD_USED) + " slots " + nonCobble(p) + " items in level "
			+ helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(64)).stream()
				.map(e -> e.getItem() + "@" + e.blockPosition()).toList() + " player@" + p.blockPosition());
		helper.assertTrue(balance(p) == prize, "prize paid: " + balance(p) + " vs " + prize);
		// the last card: its slot empties and the used card goes there
		String id2 = start(helper, p, 5, 1, 0);
		long prize2 = store(helper).face(id2).getLongOr("prize", -1);
		scratch(p, "all", id2, -1);
		helper.assertTrue(fresh(p) == 0 && used(p) >= 1, "last card used: fresh " + fresh(p) + " used " + used(p));
		helper.assertTrue(balance(p) == prize + prize2, "both prizes paid once");
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	/**
	 * Re-draw check: the stack is handed to another player mid-card. The first player's card stays parked (outcome
	 * bound to the player, not the item); the second player scratches a NEW card. Money stays consistent: each card
	 * finished pays exactly its own face; total cards taken = cards finished.
	 */
	@GameTest(maxTicks = 100)
	public void stackHandedToAnotherPlayerMidCard(GameTestHelper helper) {
		ServerPlayer a = player(helper);
		ServerPlayer b = player(helper);
		String id = start(helper, a, 0, 3, 0, 1, 2, 3, 4, 5, 6, 7);
		ItemStack stack = a.getInventory().removeItemNoUpdate(0);
		b.getInventory().setItem(0, stack);
		b.getInventory().setSelectedSlot(0);
		ScratchGame.use(b, b.getInventory().getItem(0), Scratch.Kind.BASIC);
		scratch(b, "scratch", "", 0);
		String idB = store(helper).active(b.getUUID(), "basic");
		helper.assertTrue(idB != null && !idB.equals(id), "b got a new card (a's draw is parked on a): " + idB);
		long prizeB = store(helper).face(idB).getLongOr("prize", -1);
		scratch(b, "all", idB, -1);
		helper.assertTrue(balance(b) == prizeB && fresh(b) == 2 && used(b) == 1, "b's card paid its own prize");
		helper.assertTrue(id.equals(store(helper).active(a.getUUID(), "basic")) && balance(a) == 0, "a's card still parked, unpaid");
		helper.getLevel().getServer().getPlayerList().remove(a);
		helper.getLevel().getServer().getPlayerList().remove(b);
		helper.succeed();
	}
}
