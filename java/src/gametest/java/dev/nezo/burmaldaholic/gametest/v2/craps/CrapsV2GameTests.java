package dev.nezo.burmaldaholic.gametest.v2.craps;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.logic.Bet;
import dev.nezo.burmaldaholic.games.craps.logic.BetKind;
import dev.nezo.burmaldaholic.games.craps.present.CrapsFlow;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.games.craps.present.CrapsPub;
import dev.nezo.burmaldaholic.games.craps.present.CrapsView;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lane D2 (craps v2): server GameTests through the REAL packet path. Every player is connected like a real one (an
 * embedded channel records what the server sent the screen), opens the table by USING the block and acts only through
 * {@link TableNetworking#receive} (menu open, in range) — never {@code onAction}. Covered: a fresh player (not seated,
 * the screen's chips) bets Pass, odds, Come and Field by packet; the odds lane on the come-out is refused with a reason
 * and costs nothing (R-CDE M6, server side); Undo / Clear take back exactly the chips put down since the roll and never
 * a contract bet; the state the server sends during the throw closes the felt and Undo on the screen (R-CDE M5); the
 * bots' bets ride in the in-world board's {@link CrapsPub} on the seat their plate uses and are published (R-CDE m2, m3).
 */
public class CrapsV2GameTests {
	private static final Transaction TEST = Transaction.of("craps", "gametest");
	private static final Map<UUID, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

	// ---- helpers ----------------------------------------------------------------------------------------------------

	private static ServerPlayer player(GameTestHelper helper, String name, long balance) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		CHANNELS.put(player.getUUID(), new EmbeddedChannel(connection));
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 1.5)));
		Economies.get().setBalance(server, player.getUUID(), balance, TEST);
		dev.nezo.burmaldaholic.core.mode.CasinoMode.set(server, true); // a filtered run can start before GameTestCasinoMode
		return player;
	}

	/** Places a craps table at (1, 1, 1) and opens it by using the block. */
	private static CrapsTableBlockEntity open(GameTestHelper helper, ServerPlayer player) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, CrapsModule.TABLE.block());
		CrapsTableBlockEntity table = helper.getBlockEntity(rel, CrapsTableBlockEntity.class);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu m && m.pos().equals(table.getBlockPos()),
			"using the block opens the craps menu (got " + player.containerMenu + ")");
		return table;
	}

	private static void act(ServerPlayer player, CrapsTableBlockEntity table, String action, CompoundTag args) {
		TableNetworking.receive(player, new TableActionPayload(table.getBlockPos(), action, args));
	}

	private static CompoundTag flat(String kind, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("kind", kind);
		t.putLong("amount", amount);
		return t;
	}

	private static CompoundTag odds(int bet, long amount) {
		CompoundTag t = new CompoundTag();
		t.putInt("bet", bet);
		t.putLong("amount", amount);
		return t;
	}

	/** What the server sent this player since the last call: error keys, and the newest table state (or null). */
	private record Sent(List<String> errors, @Nullable CompoundTag state) {}

	private static Sent drain(ServerPlayer player, CrapsTableBlockEntity table) {
		EmbeddedChannel ch = CHANNELS.get(player.getUUID());
		List<String> errors = new ArrayList<>();
		CompoundTag state = null;
		if (ch != null) {
			ch.runPendingTasks();
			ch.flush();
		}
		while (ch != null) {
			Object o = ch.readOutbound();
			if (o == null) break;
			List<Object> packets = new ArrayList<>();
			if (o instanceof ClientboundBundlePacket b) b.subPackets().forEach(packets::add);
			else packets.add(o);
			for (Object pk : packets) {
				if (pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof TableErrorPayload e) {
					errors.add(key(e.message()));
				} else if (pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof TableSyncPayload s && s.pos().equals(table.getBlockPos())) {
					state = s.state();
				} else if (pk instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
					String k = key(chat.content()); // sendError's action-bar copy (a test connection never negotiated table_error)
					if (!errors.contains(k)) errors.add(k);
				}
			}
		}
		return new Sent(errors, state);
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static long balance(GameTestHelper helper, ServerPlayer p) {
		return Economies.get().balance(helper.getLevel().getServer(), p.getUUID());
	}

	private static @Nullable Bet bet(CrapsTableBlockEntity t, ServerPlayer p, BetKind kind) {
		for (Bet b : t.table().betsOf(p.getUUID())) {
			if (b.kind() == kind) return b;
		}
		return null;
	}

	private static void finish(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			p.closeContainer();
			helper.getLevel().getServer().getPlayerList().remove(p);
			CHANNELS.remove(p.getUUID());
		}
		helper.succeed();
	}

	// ---- the tests ----------------------------------------------------------------------------------------------------

	/**
	 * The CurseForge report "craps won't let me place a bet": a FRESH player (not seated, never pressed Sit) sends the
	 * Pass Line click's packet and is seated with the bet; the odds lane on the come-out is refused with a reason and
	 * costs nothing; after the point the odds, a Come and a Field bet land, each debiting exactly its amount.
	 */
	@GameTest(maxTicks = 200)
	public void freshPlayerBetsPassOddsComeFieldByPacket(GameTestHelper helper) {
		ServerPlayer p = player(helper, "cr2-fresh", 500);
		CrapsTableBlockEntity t = open(helper, p);
		drain(p, t);
		helper.assertTrue(!t.isSeated(p), "a fresh player is not seated yet");
		act(p, t, "bet", flat("pass", 25));
		Bet pass = bet(t, p, BetKind.PASS);
		helper.assertTrue(pass != null && pass.flat() == 25, "the Pass Line click's packet lands: " + drain(p, t).errors());
		helper.assertTrue(t.isSeated(p), "the bet seats the player");
		helper.assertTrue(balance(helper, p) == 475, "Pass debits 25: " + balance(helper, p));
		drain(p, t);

		// R-CDE M6 (server side): odds before a point are refused WITH a reason and cost nothing
		act(p, t, "odds", odds(pass.id(), 25));
		Sent refused = drain(p, t);
		helper.assertTrue(!refused.errors().isEmpty(), "odds on the come-out are answered with a reason");
		helper.assertTrue(bet(t, p, BetKind.PASS).odds() == 0 && balance(helper, p) == 475, "odds on the come-out cost nothing (" + balance(helper, p) + ")");
		helper.assertTrue(refused.state() != null, "every action is answered with a state (ack)");
		// and the screen, fed that state, never sends a Pass bet from the odds lane on the come-out
		CrapsView seen = CrapsView.read(refused.state(), -1, -1, false);
		CrapsFlow.Click lane = CrapsFlow.click(seen, CrapsLayout.PASS_ODDS, 25);
		helper.assertTrue(!lane.sends() && (CrapsFlow.K + "reason.odds_need_point").equals(lane.errorKey()),
			"the odds lane on the come-out names reason.odds_need_point (got " + lane.action() + " " + lane.errorKey() + ")");

		t.roll(4, 4); // point 8 (the dice are the test's; the RNG draw is untouched)
		helper.assertTrue(t.table().point() == 8, "point 8 is on");
		act(p, t, "odds", odds(pass.id(), 50));
		helper.assertTrue(bet(t, p, BetKind.PASS).odds() == 50 && balance(helper, p) == 425, "odds 50 behind the Pass line: " + drain(p, t).errors());
		act(p, t, "bet", flat("come", 10));
		helper.assertTrue(bet(t, p, BetKind.COME) != null && balance(helper, p) == 415, "Come 10 lands: " + drain(p, t).errors());
		act(p, t, "bet", flat("field", 10));
		helper.assertTrue(bet(t, p, BetKind.FIELD) != null && balance(helper, p) == 405, "Field 10 lands: " + drain(p, t).errors());
		finish(helper, p);
	}

	/**
	 * Undo takes back exactly the newest chip (the Field: +10, nothing else moves), Clear every chip put down since the
	 * roll (the Come and the odds), never the contract Pass bet; with nothing left both are refused with a reason.
	 */
	@GameTest(maxTicks = 200)
	public void undoAndClearTakeBackExactlyTheirChips(GameTestHelper helper) {
		ServerPlayer p = player(helper, "cr2-undo", 1000);
		CrapsTableBlockEntity t = open(helper, p);
		act(p, t, "bet", flat("pass", 20));
		t.roll(3, 3); // point 6
		Bet pass = bet(t, p, BetKind.PASS);
		helper.assertTrue(pass != null && t.table().point() == 6, "Pass with point 6");
		act(p, t, "odds", odds(pass.id(), 50));
		act(p, t, "bet", flat("come", 10));
		act(p, t, "bet", flat("field", 15));
		long before = balance(helper, p);
		long escrow = t.escrowed();
		drain(p, t);
		act(p, t, "undo", new CompoundTag());
		helper.assertTrue(bet(t, p, BetKind.FIELD) == null, "Undo took the Field back");
		helper.assertTrue(balance(helper, p) == before + 15, "Undo returned exactly 15: " + before + " -> " + balance(helper, p));
		helper.assertTrue(t.escrowed() == escrow - 15, "the escrow fell by exactly 15");
		helper.assertTrue(bet(t, p, BetKind.COME) != null && bet(t, p, BetKind.PASS).odds() == 50 && bet(t, p, BetKind.PASS).flat() == 20, "Come, odds and Pass untouched by Undo");
		Sent s = drain(p, t);
		helper.assertTrue(s.state() != null && s.state().getBooleanOr("can_undo", false), "the screen still offers Undo (Come, odds)");
		act(p, t, "clear", new CompoundTag());
		helper.assertTrue(bet(t, p, BetKind.COME) == null && bet(t, p, BetKind.PASS).odds() == 0, "Clear took back the Come bet and the odds");
		helper.assertTrue(balance(helper, p) == before + 15 + 10 + 50, "Clear returned exactly 60: " + balance(helper, p));
		helper.assertTrue(bet(t, p, BetKind.PASS) != null && bet(t, p, BetKind.PASS).flat() == 20, "the contract Pass bet stays");
		s = drain(p, t);
		helper.assertTrue(s.state() != null && !s.state().getBooleanOr("can_undo", true), "nothing left to undo on the screen");
		act(p, t, "undo", new CompoundTag());
		helper.assertTrue(drain(p, t).errors().contains("gui.burmaldaholic.craps.nothing_to_undo"), "a stray Undo names the reason");
		helper.assertTrue(balance(helper, p) == before + 75, "a stray Undo moves nothing");
		finish(helper, p);
	}

	/**
	 * R-CDE M5 on the state the server SENDS: right after a roll the screen shows the throw (rolling); with that state
	 * every felt spot and Undo / Clear name {@code reason.rolling}, so no bet / undo packet leaves the screen and the
	 * server's bets and balance stay as the roll left them. Once the throw is over the same state places the bet.
	 */
	@GameTest(maxTicks = 200)
	public void theThrowClosesTheFeltOnTheScreen(GameTestHelper helper) {
		ServerPlayer p = player(helper, "cr2-roll", 1000);
		CrapsTableBlockEntity t = open(helper, p);
		act(p, t, "bet", flat("pass", 20));
		act(p, t, "bet", flat("field", 10));
		drain(p, t);
		t.roll(2, 6); // point 8; the field loses
		t.syncViewers();
		CompoundTag sent = drain(p, t).state();
		helper.assertTrue(sent != null, "the roll was sent to the screen");
		long balance = balance(helper, p);
		int bets = t.table().betsOf(p.getUUID()).size();
		CrapsView rolling = CrapsView.read(sent, -1, -1, true);
		for (String spot : CrapsLayout.SPOT_IDS) {
			CrapsFlow.Click c = CrapsFlow.click(rolling, spot, 25);
			helper.assertTrue(!c.sends() && V2Keys.REASON_ROLLING.equals(c.errorKey()), spot + " while the dice fly: " + c.action() + " " + c.errorKey());
		}
		helper.assertTrue(!CrapsFlow.tool("undo", rolling).enabled() && V2Keys.REASON_ROLLING.equals(CrapsFlow.tool("undo", rolling).reasonKey()),
			"Undo names reason.rolling");
		helper.assertTrue(!CrapsFlow.tool("clear", rolling).enabled(), "Clear closed while the dice fly");
		helper.assertTrue(balance(helper, p) == balance && t.table().betsOf(p.getUUID()).size() == bets, "nothing moved on the server");
		CrapsView settled = CrapsView.read(sent, -1, -1, false);
		CrapsFlow.Click come = CrapsFlow.click(settled, CrapsLayout.COME, 25);
		helper.assertTrue(come.sends() && "come".equals(come.kind()), "after the throw the Come click sends the bet");
		CompoundTag args = flat(come.kind(), come.amount());
		act(p, t, come.action(), args);
		helper.assertTrue(bet(t, p, BetKind.COME) != null && balance(helper, p) == balance - 25, "the Come bet lands: " + drain(p, t).errors());
		finish(helper, p);
	}

	/**
	 * R-CDE m2 / m3: with two bots at the table their (virtual) bets are in the in-world board's public layout on the seat
	 * index of the bot-aware seat header (the one the plates use), the player's stack on the player's seat, never -1, and
	 * the bots' bet moment publishes the board.
	 */
	@GameTest(maxTicks = 1200)
	public void botBetsOnTheBoardOnTheirSeats(GameTestHelper helper) {
		ServerPlayer p = player(helper, "cr2-bots", 1000);
		CrapsTableBlockEntity t = open(helper, p);
		act(p, t, "sit", new CompoundTag());
		helper.assertTrue(t.isSeated(p), "seated by the Sit packet: " + drain(p, t).errors());
		t.bots().table().requestChange(p, new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, false, false, BotSpeed.FAST), false);
		act(p, t, "bet", flat("pass", 20));
		helper.assertTrue(bet(t, p, BetKind.PASS) != null, "Pass lands: " + drain(p, t).errors());
		// a natural keeps the come-out on (T-D2 G1: with a point on, ROCK / TAG bots bet nothing, so the test waited on
		// random dice); the roll is the bots' safe point: they sit down
		t.roll(5, 6);
		long[] betAt = {-1};
		helper.onEachTick(() -> {
			if (betAt[0] < 0 && !t.botBets().shadow().bets().isEmpty()) betAt[0] = helper.getLevel().getGameTime();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(betAt[0] >= 0, "the bots bet (bots seated: " + t.bots().bots().size() + ")");
			helper.assertTrue(t.lastPublishTime() >= betAt[0], "the bots' bets were published to the board (last publish "
				+ t.lastPublishTime() + ", bots bet at " + betAt[0] + ")");
			Map<UUID, Integer> seatOf = new HashMap<>();
			List<SeatOccupant> occ = t.bots().occupants();
			for (int i = 0; i < occ.size(); i++) {
				if (occ.get(i) instanceof SeatOccupant.Human h) seatOf.put(h.id(), i);
				else if (occ.get(i) instanceof SeatOccupant.Bot b) seatOf.put(VirtualSeats.botUuid(b.key()), i);
			}
			CrapsPub pub = CrapsPub.decode(t.getUpdateTag(helper.getLevel().registryAccess()).getIntArray("craps_pub").orElseThrow());
			int botStacks = 0;
			for (CrapsPub.Stack s : pub.stacks()) {
				helper.assertTrue(s.seat() >= 0, "no stack on seat -1: " + pub.stacks());
				helper.assertTrue(occ.get(s.seat()) != null, "stack on an occupied seat " + s.seat());
				if (occ.get(s.seat()) instanceof SeatOccupant.Bot) botStacks++;
			}
			helper.assertTrue(botStacks > 0, "the bots' stacks are on the board: " + pub.stacks());
			int mine = seatOf.get(p.getUUID());
			// the player's bets as they are now (the auto roll may have resolved the Pass meanwhile) on the player's seat
			for (Bet b : t.table().betsOf(p.getUUID())) {
				helper.assertTrue(pub.stacks().stream().anyMatch(s -> s.seat() == mine && s.kind().equals(b.kind().id()) && s.flat() == b.flat()),
					"the player's " + b.kind().id() + " " + b.flat() + " on the player's seat " + mine + ": " + pub.stacks());
			}
			for (Bet b : t.botBets().shadow().bets()) {
				int seat = seatOf.getOrDefault(b.owner(), -2);
				helper.assertTrue(pub.stacks().stream().anyMatch(s -> s.seat() == seat && s.kind().equals(b.kind().id()) && s.flat() == b.flat()),
					"bot bet " + b.kind().id() + " " + b.flat() + " on its seat " + seat + ": " + pub.stacks());
			}
			// the plates read the same header: the seat header of the update tag names a bot on every bot stack's seat
			finish(helper, p);
		});
	}
	/**
	 * CB-G request: a generated casino's table preset (Lounge craps: min 100, 2 × tier max) is honoured by craps. Through
	 * the packet: a 50 Pass is refused (bet_too_low) and costs nothing, 100 lands, a bet above the tier max up to 2 × tier
	 * max lands, above that is refused with the max (as at High-Roller blackjack); the screen's state shows min 100 / max 2 × tier max.
	 */
	@GameTest(maxTicks = 200)
	public void presetLimitsAreHonoured(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		BlockPos abs = helper.absolutePos(rel);
		TablePresetProvider old = CoreServices.tablePresets();
		TablePresetProvider.TablePreset lounge = new TablePresetProvider.TablePreset("high_roller_craps", "", -1, 100, 2, 0, List.of());
		CoreServices.setTablePresets(new TablePresetProvider() {
			@Override
			public java.util.Optional<TablePreset> preset(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
				return pos.equals(abs) ? java.util.Optional.of(lounge) : old.preset(level, pos);
			}

			@Override
			public java.util.Optional<BotPreset> botDefaults(net.minecraft.server.level.ServerLevel level, BlockPos pos, String gameId) {
				return old.botDefaults(level, pos, gameId);
			}
		});
		ServerPlayer p = player(helper, "cr2-lounge", 0);
		try {
			MinecraftServer server = helper.getLevel().getServer();
			long tierMax = CoreServices.vip().maxBet(server, p.getUUID());
			long start = 4 * tierMax + 1000;
			Economies.get().setBalance(server, p.getUUID(), start, TEST);
			CrapsTableBlockEntity t = open(helper, p);
			CompoundTag state = t.writeClientState(p);
			helper.assertTrue(state.getLongOr("min", 0) == 100, "the screen shows the preset minimum 100: " + state.getLongOr("min", 0));
			helper.assertTrue(state.getLongOr("max", 0) == 2 * tierMax, "the screen shows 2 x tier max " + 2 * tierMax + ": " + state.getLongOr("max", 0));
			drain(p, t);
			act(p, t, "bet", flat("pass", 50));
			Sent low = drain(p, t);
			helper.assertTrue(bet(t, p, BetKind.PASS) == null && balance(helper, p) == start, "a 50 Pass under the preset minimum costs nothing");
			helper.assertTrue(low.errors().contains("gui.burmaldaholic.error.bet_too_low"), "a 50 Pass names the minimum: " + low.errors());
			act(p, t, "bet", flat("pass", 100));
			helper.assertTrue(bet(t, p, BetKind.PASS) != null && balance(helper, p) == start - 100, "a 100 Pass lands: " + drain(p, t).errors());
			act(p, t, "bet", flat("field", 2 * tierMax + 1));
			Sent high = drain(p, t);
			helper.assertTrue(bet(t, p, BetKind.FIELD) == null && balance(helper, p) == start - 100, "above 2 x tier max is refused: " + high.errors());
			helper.assertTrue((high.errors().contains("gui.burmaldaholic.error.table_max") || high.errors().contains("gui.burmaldaholic.error.bet_too_high")),
				"the refusal names the max: " + high.errors());
			long big = tierMax + Math.max(1, tierMax / 2); // above the tier max, inside the preset's 2 x
			act(p, t, "bet", flat("field", big));
			helper.assertTrue(bet(t, p, BetKind.FIELD) != null && balance(helper, p) == start - 100 - big,
				"a Field of " + big + " (tier max " + tierMax + ") lands at the 2 x table: " + drain(p, t).errors());
		} finally {
			CoreServices.setTablePresets(old);
		}
		finish(helper, p);
	}

	/**
	 * T-D2 V8: a VIP preset table refuses a player below its tier AT THE SEAT (like High-Roller blackjack): through the
	 * packet, "sit" and a Pass click leave them unseated, with no bet and the balance unchanged, and name the tier.
	 */
	@GameTest(maxTicks = 200)
	public void vipPresetRefusesTheSeatBelowTheTier(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
		TablePresetProvider old = CoreServices.tablePresets();
		TablePresetProvider.TablePreset vip = new TablePresetProvider.TablePreset("high_roller_craps", "", -1, 100, 2,
			dev.nezo.burmaldaholic.core.service.VipTiers.NETHERITE, List.of());
		CoreServices.setTablePresets(new TablePresetProvider() {
			@Override
			public java.util.Optional<TablePreset> preset(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
				return pos.equals(abs) ? java.util.Optional.of(vip) : old.preset(level, pos);
			}

			@Override
			public java.util.Optional<BotPreset> botDefaults(net.minecraft.server.level.ServerLevel level, BlockPos pos, String gameId) {
				return old.botDefaults(level, pos, gameId);
			}
		});
		ServerPlayer p = player(helper, "cr2-vip", 5000);
		try {
			MinecraftServer server = helper.getLevel().getServer();
			helper.assertTrue(CoreServices.vip().tier(server, p.getUUID()) < dev.nezo.burmaldaholic.core.service.VipTiers.NETHERITE, "a fresh player is below Netherite");
			CrapsTableBlockEntity t = open(helper, p);
			drain(p, t);
			act(p, t, "sit", new CompoundTag());
			Sent sit = drain(p, t);
			helper.assertTrue(!t.isSeated(p), "a below-tier player is not seated");
			helper.assertTrue(sit.errors().contains("gui.burmaldaholic.error.vip_required"), "the seat names the tier: " + sit.errors());
			act(p, t, "bet", flat("pass", 100));
			Sent bet = drain(p, t);
			helper.assertTrue(!t.isSeated(p) && bet(t, p, BetKind.PASS) == null && balance(helper, p) == 5000,
				"a Pass click neither seats nor bets: seated " + t.isSeated(p) + ", errors " + bet.errors());
		} finally {
			CoreServices.setTablePresets(old);
		}
		finish(helper, p);
	}
}
