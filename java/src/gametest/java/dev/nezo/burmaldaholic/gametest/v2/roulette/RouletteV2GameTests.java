package dev.nezo.burmaldaholic.gametest.v2.roulette;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.present.RoulettePub;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * Lane C (roulette): server GameTests of the v2 work that go through the real packet handlers
 * (docs/architecture/ui-v2.md §11). Registered up front by the kit lane (K) so no lane edits {@code fabric.mod.json};
 * lane C owns this file and adds its {@code @GameTest} methods here.
 *
 * <p>Phase 2 C2 (R-CDE m4): when casino mode goes off, the table refunds the open bets AND publishes the idle public
 * board (no player or bot stacks), so the in-world board never shows a stale round.
 */
public class RouletteV2GameTests {
	private static final Transaction TEST = Transaction.of("roulette", "gametest");
	private static final long START = 5000;
	private static final int[] RED = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};

	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 1.5)));
		Economies.get().setBalance(server, player.getUUID(), START, TEST);
		return player;
	}

	private static RouletteTableBlockEntity open(GameTestHelper helper, ServerPlayer player) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, RouletteModule.TABLE.block());
		RouletteTableBlockEntity table = helper.getBlockEntity(rel, RouletteTableBlockEntity.class);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu m && m.pos().equals(table.getBlockPos()), "using the block opens the table");
		return table;
	}

	private static void act(ServerPlayer p, RouletteTableBlockEntity t, String action, CompoundTag args) {
		TableNetworking.receive(p, new TableActionPayload(t.getBlockPos(), action, args));
	}

	private static CompoundTag bet(String type, long amount, int... nums) {
		CompoundTag a = new CompoundTag();
		a.putString("type", type);
		a.putIntArray("nums", nums);
		a.putLong("amount", amount);
		return a;
	}

	/** What the watchers' update tag says (the in-world board decodes exactly this). */
	private static RoulettePub sent(GameTestHelper helper, RouletteTableBlockEntity t) {
		CompoundTag tag = t.getUpdateTag(helper.getLevel().registryAccess());
		List<String> names = new ArrayList<>();
		tag.getListOrEmpty("roulette_pub_names").forEach(n -> names.add(n.asString().orElse("")));
		return RoulettePub.decode(tag.getLongArray("roulette_pub").orElseThrow(), names);
	}

	/** Turns casino mode off for exactly one table tick (synchronous: no other test ticks meanwhile), then back on. */
	private static void tickWithModeOff(GameTestHelper helper, RouletteTableBlockEntity t) {
		ServerLevel level = helper.getLevel();
		boolean before = CasinoMode.isEnabled(level);
		try {
			CasinoMode.set(level.getServer(), false);
			t.tick(level);
		} finally {
			CasinoMode.set(level.getServer(), before);
		}
	}

	private static void finish(GameTestHelper helper, ServerPlayer p) {
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	/** m4: bets on the layout, casino mode off → the bets are refunded and the idle public board is published at once. */
	@GameTest
	public void modeOffPublishesIdleBoard(GameTestHelper helper) {
		ServerPlayer p = survivalPlayer(helper, "c2-modeoff");
		RouletteTableBlockEntity t = open(helper, p);
		act(p, t, "bet", bet("straight", 25, 17));
		act(p, t, "bet", bet("red", 10, RED));
		helper.assertTrue(t.stakeOf(p.getUUID()) == 35, "two bets by packet: stake " + t.stakeOf(p.getUUID()));
		helper.assertTrue(t.pub().stacks().size() == 2, "the board shows the two stacks: " + t.pub().stacks());
		long now = helper.getLevel().getGameTime();
		tickWithModeOff(helper, t);
		helper.assertTrue(t.stakeOf(p.getUUID()) == 0 && Economies.get().balance(helper.getLevel().getServer(), p.getUUID()) == START,
			"mode off refunds the bets");
		helper.assertTrue(t.pubSentAtForTesting() >= now, "mode off published the board (last sent " + t.pubSentAtForTesting() + ", now " + now + ")");
		RoulettePub pub = sent(helper, t);
		helper.assertTrue(pub.stacks().isEmpty() && pub.players().stream().allMatch(pl -> pl.total() == 0), "the published board is idle: " + pub);
		helper.assertTrue("betting".equals(t.roundPhase()) && t.sync().result() < 0, "the wheel is idle: " + t.roundPhase());
		finish(helper, p);
	}

	/**
	 * m4 with atmosphere bots: their virtual stacks leave the published board too. Bots join at a safe point (the next
	 * BETTING after the change), so the player spins once, then bets again and waits for the bots' stacks.
	 */
	@GameTest(maxTicks = 3000)
	public void modeOffClearsBotStacks(GameTestHelper helper) {
		ServerPlayer p = survivalPlayer(helper, "c2-modeoff-bots");
		RouletteTableBlockEntity t = open(helper, p);
		act(p, t, "bet", bet("straight", 5, 17));
		t.bots().table().requestChange(p, new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, false, false, BotSpeed.FAST), false);
		act(p, t, "spin", new CompoundTag());
		boolean[] spun = {false};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0]) return;
			if (!"betting".equals(t.roundPhase())) {
				spun[0] = true;
				return;
			}
			if (!spun[0]) return;
			if (t.stakeOf(p.getUUID()) == 0) act(p, t, "bet", bet("straight", 5, 17));
			boolean botStacks = t.pub().players().stream().anyMatch(pl -> pl.bot() && pl.total() > 0);
			if (!botStacks) return;
			done[0] = true;
			long now = helper.getLevel().getGameTime();
			tickWithModeOff(helper, t);
			RoulettePub pub = sent(helper, t);
			helper.assertTrue(t.pubSentAtForTesting() >= now, "mode off published the board");
			helper.assertTrue(pub.stacks().isEmpty() && pub.players().stream().allMatch(pl -> pl.total() == 0),
				"no bot or player stacks after mode off: " + pub);
			finish(helper, p);
		});
	}
}
