package dev.nezo.burmaldaholic.gametest.v2.extras;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.games.extras.present.MachinePub;
import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.server.ScratchData;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Item;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lane E server GameTests through the REAL packet handlers (docs/architecture/ui-v2.md §11; v0.1.2 rule): a test player
 * USES the Plinko / Wheel machine block (the menu opens like for a player), then the very payloads the v2 screens send —
 * encoded and decoded with their codecs, handed to the player's packet listener — drop the ball / spin the wheel / flip
 * the coin; the SERVER settles exactly the bet, the machine publishes its in-world face (the plate name, the bins of the
 * dropped risk), and the table answers every action (the {@code ack} the v2 watchdog waits for grows).
 */
public class ExtrasV2GameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	@SuppressWarnings("removal")
	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	/** Client → server exactly as the network does it: the payload through its codec, then the player's listener. */
	@SuppressWarnings("unchecked")
	private static <T extends CustomPacketPayload> void c2s(ServerPlayer p, T payload, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), p.level().registryAccess());
		codec.encode(buf, payload);
		T back = codec.decode(buf);
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(back));
	}

	/** Places a machine facing the player and USES it (the block's use path opens the menu). */
	private static BlockPos machine(GameTestHelper helper, ServerPlayer p, net.minecraft.world.level.block.Block block) {
		BlockPos rel = new BlockPos(1, 1, 2);
		helper.setBlock(rel, block.defaultBlockState());
		BlockPos abs = helper.absolutePos(rel);
		var state = helper.getLevel().getBlockState(abs);
		state.useWithoutItem(helper.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
		return abs;
	}

	private static CompoundTag chips(long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("stake", "chips");
		t.putLong("amount", amount);
		return t;
	}

	private static long ack(GameTestHelper helper, BlockPos pos, ServerPlayer p) {
		return helper.getLevel().getBlockEntity(pos) instanceof CasinoTableBlockEntity t ? t.writeClientState(p).getLongOr("ack", -1) : -1;
	}

	@GameTest(maxTicks = 200)
	public void plinkoDropThroughThePacketSettlesTheBet(GameTestHelper helper) {
		CasinoConfig.chaos().enabled = false;
		ServerPlayer p = player(helper, 1000);
		BlockPos pos = machine(helper, p, ExtrasModule.PLINKO.block());
		helper.assertTrue(p.containerMenu instanceof dev.nezo.burmaldaholic.core.table.CasinoTableMenu, "using the machine opens its menu");
		long ack0 = ack(helper, pos, p);
		CompoundTag args = chips(20);
		args.putString("risk", "high");
		c2s(p, new TableActionPayload(pos, "drop", args), TableActionPayload.CODEC);
		helper.runAfterDelay(2, () -> {
			PlinkoBlockEntity be = (PlinkoBlockEntity) helper.getLevel().getBlockEntity(pos);
			CompoundTag r = be.writeClientState(p).getCompoundOrEmpty("result");
			helper.assertTrue(r.getIntOr("seq", -1) >= 0, "the drop was played");
			long net = r.getLongOr("net", 0);
			helper.assertValueEqual(Economies.get().balance(p), 1000 + net, "the balance moved by exactly the server's net");
			helper.assertTrue(be.plinkoSync() != null, "the drop is published to the world");
			MachinePub pub = be.machinePub();
			helper.assertValueEqual(pub.name(), p.getGameProfile().name(), "the plate names the player");
			helper.assertValueEqual(pub.risk(), 2, "the bins of the dropped risk");
			helper.assertValueEqual(pub.centi().length, 13, "13 bins");
			helper.assertTrue(ack(helper, pos, p) > ack0, "the table answered (ack " + ack0 + " → " + ack(helper, pos, p) + ")");
			remove(helper, p);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void wheelSpinThroughThePacketSettlesTheBet(GameTestHelper helper) {
		CasinoConfig.chaos().enabled = false;
		ServerPlayer p = player(helper, 1000);
		BlockPos pos = machine(helper, p, ExtrasModule.WHEEL.block());
		c2s(p, new TableActionPayload(pos, "spin", chips(25)), TableActionPayload.CODEC);
		helper.runAfterDelay(2, () -> {
			WheelBlockEntity be = (WheelBlockEntity) helper.getLevel().getBlockEntity(pos);
			CompoundTag r = be.writeClientState(p).getCompoundOrEmpty("result");
			helper.assertTrue(r.getIntOr("seq", -1) >= 0, "the spin was played");
			helper.assertValueEqual(Economies.get().balance(p), 1000 + r.getLongOr("net", 0), "the balance moved by exactly the server's net");
			helper.assertValueEqual(be.lastPlayerName(), p.getGameProfile().name(), "the plate names the player");
			remove(helper, p);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aBetAboveTheBalanceIsRefusedAndNothingMoves(GameTestHelper helper) {
		ServerPlayer p = player(helper, 10);
		BlockPos pos = machine(helper, p, ExtrasModule.PLINKO.block());
		CompoundTag args = chips(500);
		args.putString("risk", "low");
		c2s(p, new TableActionPayload(pos, "drop", args), TableActionPayload.CODEC);
		helper.runAfterDelay(2, () -> {
			helper.assertValueEqual(Economies.get().balance(p), 10L, "refused: not a chip moved");
			helper.assertTrue(((PlinkoBlockEntity) helper.getLevel().getBlockEntity(pos)).plinkoSync() == null, "nothing published");
			remove(helper, p);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void coinFlipThroughThePacket(GameTestHelper helper) {
		CasinoConfig.chaos().enabled = false;
		ServerPlayer p = player(helper, 1000);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExtrasModule.LUCKY_COIN));
		CompoundTag args = chips(10);
		args.putString("side", "tails");
		c2s(p, new ExtrasActionPayload("coin", "flip", args), ExtrasActionPayload.CODEC);
		// the v2 watchdog's resync of the item games: "refresh" is answered (no error, nothing moves)
		c2s(p, new ExtrasActionPayload("coin", "refresh", new CompoundTag()), ExtrasActionPayload.CODEC);
		helper.runAfterDelay(2, () -> {
			long b = Economies.get().balance(p);
			helper.assertTrue(b == 990 || b > 1000, "lost exactly the 10 or won: " + b);
			remove(helper, p);
			helper.succeed();
		});
	}

	private static CompoundTag args(Object... kv) {
		CompoundTag t = new CompoundTag();
		for (int i = 0; i < kv.length; i += 2) {
			if (kv[i + 1] instanceof Long l) t.putLong((String) kv[i], l);
			else if (kv[i + 1] instanceof Integer n) t.putInt((String) kv[i], n);
			else t.putString((String) kv[i], String.valueOf(kv[i + 1]));
		}
		return t;
	}

	/**
	 * E2 (R-CDE m5): the Dice Duel invite card is answered through the packet by a player who does NOT hold Dice — the
	 * server opened the card for them. Decline (also what Esc sends) takes the challenge off the book; Accept plays it
	 * (chips conserved, less the rake). A roll from the card's player is still refused (no house game without the item).
	 */
	@GameTest(maxTicks = 200)
	public void diceInviteIsAnsweredWithoutHoldingDice(GameTestHelper helper) {
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExtrasModule.DICE));
		c2s(a, new ExtrasActionPayload("dice", "challenge", args("target", b.getUUID().toString(), "amount", 50L)), ExtrasActionPayload.CODEC);
		var first = dev.nezo.burmaldaholic.games.extras.server.DiceGame.incoming(b);
		helper.assertTrue(first.size() == 1, "the challenge reached B: " + first);
		c2s(b, new ExtrasActionPayload("dice", "decline", args("id", first.getFirst().id())), ExtrasActionPayload.CODEC);
		helper.assertTrue(dev.nezo.burmaldaholic.games.extras.server.DiceGame.incoming(b).isEmpty(), "B's decline from the card took the challenge off the book");
		helper.assertTrue(dev.nezo.burmaldaholic.games.extras.server.DiceGame.outgoing(a).isEmpty(), "A no longer waits");
		// a roll vs the house from the card's player (no Dice in hand) is still ignored
		long bBefore = Economies.get().balance(b);
		c2s(b, new ExtrasActionPayload("dice", "roll", chips(10)), ExtrasActionPayload.CODEC);
		helper.assertTrue(Economies.get().balance(b) == bBefore, "no house roll without the item");
		// again, and B accepts: the duel is played, chips conserved (less the rake)
		c2s(a, new ExtrasActionPayload("dice", "challenge", args("target", b.getUUID().toString(), "amount", 50L)), ExtrasActionPayload.CODEC);
		var second = dev.nezo.burmaldaholic.games.extras.server.DiceGame.incoming(b);
		helper.assertTrue(second.size() == 1, "the second challenge reached B");
		long total = Economies.get().balance(a) + Economies.get().balance(b);
		c2s(b, new ExtrasActionPayload("dice", "accept", args("id", second.getFirst().id())), ExtrasActionPayload.CODEC);
		long rake = dev.nezo.burmaldaholic.games.extras.logic.DiceDuel.pvpPayout(50, CasinoConfig.extras().diceDuel.pvpRakePercent).rake();
		long after = Economies.get().balance(a) + Economies.get().balance(b);
		helper.assertTrue(dev.nezo.burmaldaholic.games.extras.server.DiceGame.incoming(b).isEmpty(), "accepted");
		helper.assertTrue(after == total || after == total - rake, "chips conserved: " + total + " -> " + after + " (rake " + rake + ")");
		remove(helper, a);
		remove(helper, b);
		helper.succeed();
	}

	// ---- B4 (owner playtest 1): scratch cards stay in their slot ------------------------------------------------

	private static void scratch(ServerPlayer p, String action, String id, int cell) {
		CompoundTag t = new CompoundTag();
		t.putString("kind", "basic");
		t.putString("id", id);
		t.putInt("cell", cell);
		c2s(p, new ExtrasActionPayload(ScratchGame.SCREEN, action, t), ExtrasActionPayload.CODEC);
	}

	/** Slots holding {@code item}: "slot:count" each. */
	private static List<String> slotsOf(ServerPlayer p, Item item) {
		List<String> out = new ArrayList<>();
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) out.add(i + ":" + inv.getItem(i).getCount());
		}
		return out;
	}

	private static ScratchData store(GameTestHelper helper) {
		return ScratchData.get(helper.getLevel().getServer());
	}

	/**
	 * B4: two cards scratched from a stack of 5 in hotbar slot 4 (through the packets, cell by cell): the stack never
	 * leaves slot 4 and nothing is written on it while scratching; each finish takes exactly one card from it, the two
	 * used cards end up in ONE stack, and each card's prize is paid once, at its ninth cell.
	 */
	@GameTest(maxTicks = 100)
	public void scratchCardsStayInTheirSlot(GameTestHelper helper) {
		ServerPlayer p = player(helper, 0);
		var inv = p.getInventory();
		inv.setItem(4, new ItemStack(ExtrasModule.SCRATCH_CARD, 5));
		inv.setSelectedSlot(4);
		ScratchGame.use(p, inv.getItem(4), Scratch.Kind.BASIC);
		long paid = 0;
		for (int card = 0; card < 2; card++) {
			scratch(p, "scratch", "", 0);
			String id = store(helper).active(p.getUUID(), "basic");
			helper.assertTrue(id != null, "card " + card + " is active server-side");
			CompoundTag face = store(helper).face(id);
			helper.assertTrue(face != null && face.getIntOr("mask", 0) == 1, "mask 1 held server-side: " + face);
			long prize = face.getLongOr("prize", 0);
			for (int cell = 1; cell < 9; cell++) {
				helper.assertTrue(slotsOf(p, ExtrasModule.SCRATCH_CARD).equals(List.of("4:" + (5 - card))),
					"card " + card + " cell " + cell + ": the stack stays in slot 4: " + slotsOf(p, ExtrasModule.SCRATCH_CARD));
				helper.assertTrue(ScratchGame.data(inv.getItem(4)) == null, "nothing written on the stack");
				long before = Economies.get().balance(p);
				scratch(p, "scratch", id, cell);
				if (cell < 8) helper.assertTrue(Economies.get().balance(p) == before, "no payout before the ninth cell");
			}
			paid += prize;
			helper.assertTrue(store(helper).active(p.getUUID(), "basic") == null && store(helper).face(id) == null, "card " + card + " forgotten");
			helper.assertTrue(Economies.get().balance(p) == paid, "prize paid once: " + Economies.get().balance(p) + " vs " + paid);
		}
		helper.assertTrue(slotsOf(p, ExtrasModule.SCRATCH_CARD).equals(List.of("4:3")), "two cards left slot 4: " + slotsOf(p, ExtrasModule.SCRATCH_CARD));
		helper.assertTrue(slotsOf(p, ExtrasModule.SCRATCH_CARD_USED).size() == 1 && inv.countItem(ExtrasModule.SCRATCH_CARD_USED) == 2,
			"the used cards stack: " + slotsOf(p, ExtrasModule.SCRATCH_CARD_USED));
		remove(helper, p);
		helper.succeed();
	}

	/**
	 * B4: disconnecting mid-scratch neither dupes nor loses: the card stays in the stack, nothing is paid, the card
	 * (face + mask) waits server-side; after rejoining, the same card resumes (no re-draw, even when the screen lost
	 * its id), and finishing it takes one card and pays its own prize once.
	 */
	@GameTest(maxTicks = 100)
	public void scratchDisconnectMidCardNoDupeNoLoss(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		UUID uuid = UUID.randomUUID();
		ServerPlayer p = join(helper, uuid);
		p.getInventory().setItem(2, new ItemStack(ExtrasModule.SCRATCH_CARD, 3));
		p.getInventory().setSelectedSlot(2);
		ScratchGame.use(p, p.getInventory().getItem(2), Scratch.Kind.BASIC);
		scratch(p, "scratch", "", 0);
		String id = store(helper).active(uuid, "basic");
		helper.assertTrue(id != null, "card started");
		scratch(p, "scratch", id, 4);
		scratch(p, "scratch", id, 8);
		long prize = store(helper).face(id).getLongOr("prize", 0);
		List<ItemStack> saved = new ArrayList<>();
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) saved.add(p.getInventory().getItem(i).copy());
		helper.assertTrue(p.getInventory().countItem(ExtrasModule.SCRATCH_CARD) == 3 && p.getInventory().countItem(ExtrasModule.SCRATCH_CARD_USED) == 0,
			"mid-card: all 3 cards still in the stack, no used card");
		server.getPlayerList().remove(p); // disconnect
		helper.assertTrue(Economies.get().balance(server, uuid) == 0, "nothing paid at the disconnect");
		helper.assertTrue(id.equals(store(helper).active(uuid, "basic")) && store(helper).face(id).getIntOr("mask", 0) == (1 | 1 << 4 | 1 << 8),
			"the card waits server-side with its mask");
		ServerPlayer back = join(helper, uuid);
		if (back.getInventory().countItem(ExtrasModule.SCRATCH_CARD) == 0) {
			// a test player's inventory may not be reloaded from player data: put back what vanilla saved at logout
			for (int i = 0; i < saved.size(); i++) back.getInventory().setItem(i, saved.get(i));
		}
		helper.assertTrue(back.getInventory().countItem(ExtrasModule.SCRATCH_CARD) == 3, "the stack is whole after rejoining");
		back.getInventory().setSelectedSlot(2);
		ScratchGame.use(back, back.getInventory().getItem(2), Scratch.Kind.BASIC);
		scratch(back, "scratch", "", 1); // a screen that lost the id: still the same card, never a re-draw
		helper.assertTrue(id.equals(store(helper).active(uuid, "basic")) && store(helper).face(id).getIntOr("mask", 0) == (1 | 2 | 1 << 4 | 1 << 8),
			"resumed the same card");
		scratch(back, "all", id, -1);
		helper.assertTrue(back.getInventory().countItem(ExtrasModule.SCRATCH_CARD) == 2 && back.getInventory().countItem(ExtrasModule.SCRATCH_CARD_USED) == 1,
			"one card taken, one used card given");
		helper.assertTrue(Economies.get().balance(server, uuid) == prize, "its own prize paid once: " + Economies.get().balance(server, uuid) + " vs " + prize);
		helper.assertTrue(store(helper).active(uuid, "basic") == null, "card forgotten");
		remove(helper, back);
		helper.succeed();
	}

	/** A server player with a fixed UUID (and a real listener): joining again after a disconnect is the same player. */
	private static ServerPlayer join(GameTestHelper helper, UUID id) {
		var level = helper.getLevel();
		GameProfile profile = new GameProfile(id, "sc" + id.toString().substring(0, 8));
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(level.getServer(), id, 0, TEST);
		return p;
	}
}
