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
import io.netty.buffer.Unpooled;
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
}
