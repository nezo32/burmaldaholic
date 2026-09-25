package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.Bots;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Seats &amp; Bots core in a real world (BOTS.md §12.4): admission and yielding at the safe point, pending
 * settings, BOTS_ONLY and host rules, private tables, purses (bank / owner bankroll) with conservation,
 * orphan return after a crash, daily buy-ins, heat stages, atmosphere caps. Uses a scripted {@link BotTable}.
 */
public class BotsGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest_bots");

	/** A minimal table: seats of occupants, humans in sit-down order, bot stacks. */
	static class FakeTable implements BotTable {
		final String key = "gametest:" + UUID.randomUUID();
		final String game;
		final BotRole role;
		final SeatOccupant[] seats;
		final List<UUID> order = new ArrayList<>();
		final Map<String, Long> stacks = new HashMap<>();
		final Map<String, Integer> sinceBb = new HashMap<>();
		final @Nullable BlockPos pos;
		long buyIn;
		boolean active = true;

		FakeTable(String game, BotRole role, int seats, long buyIn, @Nullable BlockPos pos) {
			this.game = game;
			this.role = role;
			this.seats = new SeatOccupant[seats];
			this.buyIn = buyIn;
			this.pos = pos;
		}

		/** Same table key (a reloaded block entity). */
		FakeTable reloaded() {
			FakeTable t = new FakeTable(game, role, seats.length, buyIn, pos) {
				@Override
				public String botTableKey() {
					return FakeTable.this.key;
				}
			};
			return t;
		}

		void sit(ServerPlayer p) {
			for (int i = 0; i < seats.length; i++) {
				if (seats[i] == null) {
					seats[i] = new SeatOccupant.Human(p.getUUID(), p.getName().getString());
					order.add(p.getUUID());
					return;
				}
			}
			throw new IllegalStateException("no free seat for " + p.getName().getString());
		}

		void leave(ServerPlayer p) {
			for (int i = 0; i < seats.length; i++) {
				if (seats[i] != null && seats[i].key().equals(p.getUUID().toString())) {
					seats[i] = null;
				}
			}
			order.remove(p.getUUID());
		}

		int bots() {
			return (int) Arrays.stream(seats).filter(o -> o != null && o.isBot()).count();
		}

		int seatOf(String key) {
			for (int i = 0; i < seats.length; i++) {
				if (seats[i] != null && seats[i].key().equals(key)) {
					return i;
				}
			}
			return -1;
		}

		@Override
		public String botGameId() {
			return game;
		}

		@Override
		public BotRole botRole() {
			return role;
		}

		@Override
		public int botSeatCount() {
			return seats.length;
		}

		@Override
		public List<UUID> seatedHumans() {
			return List.copyOf(order);
		}

		@Override
		public List<SeatOccupant> occupants() {
			return Arrays.asList(seats.clone());
		}

		@Override
		public boolean seatBot(SeatOccupant.Bot bot, long stack) {
			for (int i = 0; i < seats.length; i++) {
				if (seats[i] == null) {
					seats[i] = bot;
					stacks.put(bot.key(), stack);
					return true;
				}
			}
			return false;
		}

		@Override
		public long unseatBot(String botKey) {
			int s = seatOf(botKey);
			if (s >= 0) {
				seats[s] = null;
			}
			Long st = stacks.remove(botKey);
			return st == null ? 0 : st;
		}

		@Override
		public SeatingMath.YieldRule yieldRule() {
			return "poker".equals(game) ? SeatingMath.YieldRule.POKER_BIG_BLIND : SeatingMath.YieldRule.HIGHEST_SEAT;
		}

		@Override
		public long botBuyIn() {
			return buyIn;
		}

		@Override
		public String botTableKey() {
			return key;
		}

		@Override
		public @Nullable BlockPos botTablePos() {
			return pos;
		}

		@Override
		public boolean botTableActive() {
			return active;
		}

		@Override
		public int handsSinceBigBlind(String botKey) {
			return sinceBb.getOrDefault(botKey, Integer.MAX_VALUE);
		}
	}

	private static BotSettings mixed(int count, boolean keepFree, BotDifficulty d) {
		return new BotSettings(SeatPolicy.MIXED, count, d, keepFree, false, BotSpeed.NORMAL);
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, @Nullable BlockPos at) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), 0, TEST);
		if (at != null) {
			p.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
		}
		return p;
	}

	private static void cleanup(GameTestHelper helper, TableBots bots, ServerPlayer... players) {
		bots.endSession(helper.getLevel());
		for (ServerPlayer p : players) {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	/** Runs {@code body} with the table at {@code pos} owned by a fresh bankroll holding {@code funds}. */
	private static void owned(GameTestHelper helper, BlockPos pos, long funds, Consumer<String> body) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy eco = Economies.get();
		String bankroll = "gametest_bots_" + UUID.randomUUID();
		eco.bankrolls(server).open(bankroll, UUID.randomUUID());
		if (funds > 0) {
			eco.transfer(server, AccountId.HOUSE, AccountId.bankroll(bankroll), funds, TEST);
		}
		TableOwnershipProvider prev = CoreServices.tableOwnership();
		OwnedTable table = new OwnedTable(UUID.randomUUID(), bankroll, 0, 0, true);
		CoreServices.setTableOwnership((level, p) -> p.equals(pos) ? Optional.of(table) : prev.owner(level, p));
		try {
			body.accept(bankroll);
		} finally {
			CoreServices.setTableOwnership(prev);
			eco.bankrolls(server).close(bankroll);
		}
	}

	private static long bankroll(GameTestHelper helper, String id) {
		return Economies.get().bankrolls(helper.getLevel().getServer()).get(id).orElseThrow().balance();
	}

	// ---- admission / yield / pending / host --------------------------------------------------------

	/** S1 + S3: bots fill, a claimant waits for the bot that just posted the BB and sits at the safe point. */
	@GameTest
	public void admitAndYieldAtSafePoint(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 100, pos);
		TableBots bots = new TableBots(t, mixed(5, false, BotDifficulty.NORMAL), OwnerControls.unowned(6));
		ServerPlayer a = player(helper, pos);
		ServerPlayer b = player(helper, pos);
		try {
			helper.assertTrue(bots.admit(a).orElseThrow(), "A sits at the empty table");
			t.sit(a);
			TableBots.SafePointResult r = bots.safePoint(level);
			helper.assertTrue(r.joined().size() == 5 && t.bots() == 5, "keep-free off: 5 bots fill the table, got " + r.joined().size());
			helper.assertTrue(a.getUUID().equals(bots.host()), "A is the host");
			helper.assertTrue(r.joined().stream().allMatch(x -> x.purse.equals(Purse.BANK) && x.stack == 100), "house bots, bank purse, buy-in 100");
			TableBots.SeatedBot bb = r.joined().get(2);
			t.sinceBb.put(bb.key(), 0);
			Result<Boolean> claim = bots.admit(b);
			helper.assertTrue(claim.isOk() && !claim.value(), "B becomes a claimant");
			helper.assertTrue(bots.claimants().equals(List.of(b.getUUID())), "claim recorded");
			helper.assertTrue(bots.yieldingBots().equals(List.of(bb.key())), "the BB bot is marked as leaving");
			helper.assertTrue(t.bots() == 5, "nothing changes before the safe point");
			int bbSeat = t.seatOf(bb.key());
			TableBots.SafePointResult r2 = bots.safePoint(level);
			helper.assertTrue(r2.left().size() == 1 && r2.left().getFirst().key().equals(bb.key()), "the BB bot yields");
			helper.assertTrue(r2.seatedClaimants().equals(List.of(b.getUUID())) && r2.joined().isEmpty(), "B is seated, nobody joins");
			helper.assertTrue(t.seats[bbSeat] == null, "B takes that seat");
			t.sit(b);
			helper.assertTrue(bots.claimants().isEmpty() && t.bots() == 4, "2 humans + 4 bots");
			TableBots.SafePointResult r3 = bots.safePoint(level);
			helper.assertTrue(r3.joined().isEmpty() && r3.left().isEmpty(), "stable");
		} finally {
			cleanup(helper, bots, a, b);
		}
		helper.assertTrue(t.bots() == 0 && bots.bots().isEmpty(), "endSession: every bot leaves");
		helper.succeed();
	}

	/** S2, S5, S7, S8: walk-in seat, pending count change, host handover, last human leaves. */
	@GameTest
	public void pendingSettingsHostAndSessionEnd(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 100, pos);
		BotSettings defaults = mixed(5, true, BotDifficulty.NORMAL);
		TableBots bots = new TableBots(t, defaults, OwnerControls.unowned(6));
		ServerPlayer a = player(helper, pos);
		ServerPlayer b = player(helper, pos);
		try {
			bots.admit(a);
			t.sit(a);
			helper.assertTrue(bots.safePoint(level).joined().size() == 4, "S1: 4 bots, one seat free");
			helper.assertTrue(bots.admit(b).orElseThrow(), "S2: B sits at once in the free seat");
			t.sit(b);
			helper.assertTrue(bots.safePoint(level).left().size() == 1 && t.bots() == 3, "S2: next hand one bot leaves");
			helper.assertFalse(bots.requestChange(b, defaults.withCount(1), false).isOk(), "B is not the host");
			Result<BotSettings> ch = bots.requestChange(a, defaults.withCount(2), false);
			helper.assertTrue(ch.isOk() && bots.pending() != null && bots.pending().count() == 2, "S5: pending");
			helper.assertTrue(t.bots() == 3 && bots.settings().count() == 5, "not applied mid-hand");
			TableBots.SafePointResult r = bots.safePoint(level);
			helper.assertTrue(r.settingsApplied() && bots.pending() == null && t.bots() == 2, "S5: applied at the safe point, 2 bots");
			helper.assertFalse(bots.requestChange(a, defaults.withPolicy(SeatPolicy.BOTS_ONLY).withCount(3), false).isOk(), "S4: others seated");
			t.leave(a);
			bots.safePoint(level);
			helper.assertTrue(b.getUUID().equals(bots.host()), "S7: B becomes host");
			t.leave(b);
			TableBots.SafePointResult end = bots.safePoint(level);
			helper.assertTrue(end.left().size() == 2 && t.bots() == 0, "S8: all bots leave");
			helper.assertTrue(!bots.inSession() && bots.settings().equals(defaults) && bots.host() == null, "S8: defaults restored");
		} finally {
			cleanup(helper, bots, a, b);
		}
		helper.succeed();
	}

	/** BOTS_ONLY: only the host sits; the session ends when the host leaves. */
	@GameTest
	public void botsOnlySession(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 100, pos);
		BotSettings defaults = mixed(5, true, BotDifficulty.NORMAL);
		TableBots bots = new TableBots(t, defaults, OwnerControls.unowned(6));
		ServerPlayer a = player(helper, pos);
		ServerPlayer c = player(helper, pos);
		try {
			bots.admit(a);
			t.sit(a);
			bots.safePoint(level);
			BotSettings solo = new BotSettings(SeatPolicy.BOTS_ONLY, 3, BotDifficulty.HARD, true, true, BotSpeed.FAST);
			helper.assertTrue(bots.requestChange(a, solo, false).isOk(), "host alone may pick BOTS_ONLY");
			bots.safePoint(level);
			helper.assertTrue(t.bots() == 3 && bots.bots().stream().allMatch(x -> x.profile.level() == BotDifficulty.HARD), "3 HARD bots");
			helper.assertTrue(bots.settings().effectiveSpeed() == BotSpeed.FAST, "FAST applies in BOTS_ONLY");
			helper.assertFalse(bots.admit(c).isOk(), "someone else's BOTS_ONLY table");
			t.leave(a);
			bots.safePoint(level);
			helper.assertTrue(t.bots() == 0 && !bots.inSession(), "host left: bots leave, defaults back");
			helper.assertTrue(bots.admit(c).orElseThrow(), "the table is open again");
		} finally {
			cleanup(helper, bots, a, c);
		}
		helper.succeed();
	}

	/** S6: private table; seated humans auto-invited; host invites expire with the session. */
	@GameTest
	public void privateTable(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("blackjack", BotRole.ATMOSPHERE, 5, 0, pos);
		TableBots bots = new TableBots(t, BotSettings.HUMANS_ONLY, OwnerControls.unowned(5));
		ServerPlayer a = player(helper, pos);
		ServerPlayer b = player(helper, pos);
		ServerPlayer c = player(helper, pos);
		try {
			bots.admit(a);
			t.sit(a);
			bots.admit(b);
			t.sit(b);
			helper.assertFalse(bots.setPrivate(b, true, false).isOk(), "only the host switches it");
			helper.assertTrue(bots.setPrivate(a, true, false).isOk(), "host switches private on");
			helper.assertTrue(bots.access().mayJoin(b.getUUID(), false), "B auto-invited");
			Result<Boolean> r = bots.admit(c);
			helper.assertFalse(r.isOk(), "C is not invited");
			helper.assertTrue(r.error().getString().contains(a.getName().getString()) || r.error().toString().contains("private_table"),
				"error names the host");
			helper.assertTrue(bots.invite(a, c.getUUID()).isOk(), "host invites C");
			helper.assertTrue(bots.admit(c).orElseThrow(), "C may sit now");
			bots.endSession(helper.getLevel());
			helper.assertFalse(bots.access().isPrivate(), "private reverts to the default");
			helper.assertFalse(bots.access().sessionInvites().contains(c.getUUID()), "host invites expire");
		} finally {
			cleanup(helper, bots, a, b, c);
		}
		helper.succeed();
	}

	// ---- purses ---------------------------------------------------------------------------------------

	/** S13 + S14: bankroll purse opt-in, affordability, conservation when bots leave. */
	@GameTest
	public void ownedBankrollPurseConservation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		owned(helper, pos, 150, bankroll -> {
			FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 200, pos);
			TableBots bots = new TableBots(t, mixed(3, true, BotDifficulty.NORMAL), OwnerControls.unowned(6));
			ServerPlayer h = player(helper, pos);
			try {
				bots.admit(h);
				t.sit(h);
				bots.safePoint(level);
				helper.assertTrue(t.bots() == 0, "owned table default = Atmosphere only: no money bots");
				bots.setLimits(new OwnerControls(BotsMode.ALLOWED, true, 5, false));
				bots.safePoint(level);
				helper.assertTrue(t.bots() == 0 && bots.bankrollShort() && bankroll(helper, bankroll) == 150, "S13: 150 < 200, no bot sits");
				Economies.get().transfer(server, AccountId.HOUSE, AccountId.bankroll(bankroll), 9850, TEST);
				TableBots.SafePointResult r = bots.safePoint(level);
				helper.assertTrue(r.joined().size() == 3 && !bots.bankrollShort(), "3 bots buy in");
				helper.assertTrue(r.joined().stream().allMatch(x -> x.purse.equals(Purse.bankroll(bankroll))), "bankroll purse");
				helper.assertTrue(bankroll(helper, bankroll) == 9400 && Bots.stacksOut(bankroll) == 600, "S14: bankroll 9 400, stacks out 600");
				// the human wins 150 from one bot (the pot is escrowed in the bank)
				TableBots.SeatedBot loser = r.joined().getFirst();
				bots.setStack(loser.key(), 50);
				t.stacks.put(loser.key(), 50L);
				Economies.get().deposit(server, h.getUUID(), 150, Transaction.payout("gametest"));
				bots.endSession(level);
				helper.assertTrue(bankroll(helper, bankroll) == 9850, "S14: 450 back, bankroll 9 850: " + bankroll(helper, bankroll));
				helper.assertTrue(Bots.stacksOut(bankroll) == 0, "nothing out");
				helper.assertTrue(bankroll(helper, bankroll) + Economies.get().balance(server, h.getUUID()) == 10_000, "conserved");
			} finally {
				cleanup(helper, bots, h);
			}
		});
		helper.succeed();
	}

	/** S12 / §3.5: a crash leaves bankroll bots' chips in the bank; the reloaded table returns them once. */
	@GameTest
	public void orphanReturnAfterRestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		owned(helper, pos, 10_000, bankroll -> {
			FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 200, pos);
			BotSettings defaults = mixed(2, true, BotDifficulty.MIXED);
			TableBots bots = new TableBots(t, defaults, OwnerControls.unowned(6));
			bots.setLimits(new OwnerControls(BotsMode.ALLOWED, true, 5, false));
			ServerPlayer h = player(helper, pos);
			TableBots after = null;
			try {
				bots.admit(h);
				t.sit(h);
				TableBots.SafePointResult r = bots.safePoint(level);
				helper.assertTrue(r.joined().size() == 2 && bankroll(helper, bankroll) == 9600, "2 bots bought in");
				bots.setStack(r.joined().get(0).key(), 300); // won 100 from the human before the crash
				CompoundTag saved = new CompoundTag();
				bots.save(saved);
				t.active = false; // crash: the old block entity is gone, endSession never ran

				FakeTable reloaded = t.reloaded();
				after = new TableBots(reloaded, BotSettings.HUMANS_ONLY, OwnerControls.unowned(6));
				after.load(saved);
				helper.assertTrue(after.bots().isEmpty() && !after.inSession(), "bots are not restored");
				helper.assertTrue(after.defaults().equals(defaults), "defaults restored");
				helper.assertTrue(after.limits().botsMode() == BotsMode.ALLOWED, "owner limits restored");
				TableBots.SafePointResult r2 = after.safePoint(level); // nobody seated yet
				helper.assertTrue(r2.joined().isEmpty(), "no session without humans");
				helper.assertTrue(bankroll(helper, bankroll) == 10_100, "300 + 200 returned: " + bankroll(helper, bankroll));
				after.safePoint(level);
				helper.assertTrue(bankroll(helper, bankroll) == 10_100, "returned exactly once");
			} finally {
				if (after != null) {
					after.endSession(level);
				}
				// the crashed instance never ends its session (its chips were returned by the ledger)
				helper.getLevel().getServer().getPlayerList().remove(h);
			}
		});
		helper.succeed();
	}

	/** §5.1: a table funds at most bots.tableBuyInsPerDay house buy-ins per MCD; busted bots are not replaced after that. */
	@GameTest
	public void dailyHouseBuyIns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 100, pos);
		TableBots bots = new TableBots(t, mixed(4, true, BotDifficulty.NORMAL), OwnerControls.unowned(6));
		ServerPlayer h = player(helper, pos);
		try {
			int left = BotLedger.buyInsLeft(server, t.key);
			for (int i = 0; i < left - 2 && left != Integer.MAX_VALUE; i++) {
				BotLedger.countBuyIn(server, t.key);
			}
			helper.assertTrue(BotLedger.buyInsLeft(server, t.key) == 2, "2 buy-ins left today");
			bots.admit(h);
			t.sit(h);
			TableBots.SafePointResult r = bots.safePoint(level);
			helper.assertTrue(r.joined().size() == 2 && BotLedger.buyInsLeft(server, t.key) == 0, "only 2 of 4 bots sit");
			TableBots.SeatedBot busted = r.joined().getFirst();
			bots.setStack(busted.key(), 0);
			t.stacks.put(busted.key(), 0L);
			TableBots.SafePointResult r2 = bots.safePoint(level);
			helper.assertTrue(r2.left().size() == 1 && r2.joined().isEmpty() && t.bots() == 1, "busted bot leaves, not replaced");
		} finally {
			cleanup(helper, bots, h);
		}
		helper.succeed();
	}

	/** §5.4: Word got around (HARD only at poker), then sulking (house bots leave). */
	@GameTest
	public void heatStages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 100, pos);
		TableBots bots = new TableBots(t, mixed(3, true, BotDifficulty.EASY), OwnerControls.unowned(6));
		ServerPlayer h = player(helper, pos);
		try {
			long cap = BotLedger.threshold(server, h.getUUID());
			helper.assertTrue(cap > 0, "heat on by default");
			bots.admit(h);
			t.sit(h);
			bots.safePoint(level);
			helper.assertTrue(t.bots() == 3 && bots.bots().stream().allMatch(b -> b.profile.level() == BotDifficulty.EASY), "3 EASY bots");
			BotLedger.record(server, h.getUUID(), cap + 10);
			helper.assertTrue(BotLedger.hardOnly(server, h.getUUID()) && !BotLedger.sulking(server, h.getUUID()), "word got around");
			TableBots.SafePointResult r = bots.safePoint(level);
			helper.assertTrue(r.left().size() == 3 && r.joined().size() == 3, "EASY bots leave, replaced");
			helper.assertTrue(bots.bots().stream().allMatch(b -> b.profile.level() == BotDifficulty.HARD), "only HARD bots now");
			BotLedger.record(server, h.getUUID(), cap * 2);
			helper.assertTrue(BotLedger.sulking(server, h.getUUID()), "sulking");
			bots.safePoint(level);
			helper.assertTrue(t.bots() == 0, "house bots refuse the player");
			BotLedger.reset(server, h.getUUID());
			bots.safePoint(level);
			helper.assertTrue(t.bots() == 3, "after a reset the bots are back");
		} finally {
			BotLedger.reset(server, h.getUUID());
			cleanup(helper, bots, h);
		}
		helper.succeed();
	}

	/** §4.7 / §5.2: atmosphere bots are capped per game and never touch money, even at owned tables. */
	@GameTest
	public void atmosphereBotsAreFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		owned(helper, pos, 1000, bankroll -> {
			FakeTable t = new FakeTable("blackjack", BotRole.ATMOSPHERE, 5, 0, pos);
			TableBots bots = new TableBots(t, mixed(4, true, BotDifficulty.NORMAL), OwnerControls.unowned(5));
			ServerPlayer h = player(helper, pos);
			try {
				bots.admit(h);
				t.sit(h);
				TableBots.SafePointResult r = bots.safePoint(level);
				helper.assertTrue(r.joined().size() == 2, "bots.atmosphere.maxPerTable.blackjack = 2, got " + r.joined().size());
				helper.assertTrue(r.joined().stream().allMatch(b -> b.purse.equals(Purse.NONE) && b.stack == 0), "virtual");
				helper.assertTrue(bankroll(helper, bankroll) == 1000, "bankroll untouched");
				bots.setLimits(new OwnerControls(BotsMode.OFF, true, 4, false));
				bots.safePoint(level);
				helper.assertTrue(t.bots() == 0, "owner turned bots off");
			} finally {
				cleanup(helper, bots, h);
			}
		});
		helper.succeed();
	}

	/** Review wave 3: {@code /casino bots heat <player> reset} clears today's net AND the adaptive-heat stats. */
	@GameTest
	public void heatResetCommandAlsoResetsAdaptiveHeat(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer h = player(helper, null);
		var cfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.bots();
		boolean adaptive = cfg.adaptiveHeat;
		try {
			cfg.adaptiveHeat = true;
			long cap = BotLedger.threshold(server, h.getUUID());
			BotLedger.record(server, h.getUUID(), cap + 1);
			for (int i = 0; i < 400; i++) {
				BotLedger.recordPokerHand(server, h.getUUID(), 1.0); // +100 BB/100 over 400 hands
			}
			helper.assertTrue(BotLedger.adaptive(server, h.getUUID()) && BotLedger.hardOnly(server, h.getUUID()), "hot player");
			int ok;
			try {
				// mock players all share one name and a UUID counts as an entity selector: target by a unique tag
				String tag = "heat_reset_" + Long.toHexString(h.getUUID().getLeastSignificantBits() & 0xffffffL);
				h.addTag(tag);
				ok = server.getCommands().getDispatcher().execute("casino bots heat @a[tag=" + tag + ",limit=1] reset", server.createCommandSourceStack());
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
				helper.fail("command failed: " + e.getMessage());
				ok = 0;
			}
			helper.assertTrue(ok == 1, "command ran");
			helper.assertTrue(BotLedger.netToday(server, h.getUUID()) == 0, "net reset");
			helper.assertTrue(!BotLedger.adaptive(server, h.getUUID()), "adaptive-heat stats reset too");
		} finally {
			cfg.adaptiveHeat = adaptive;
			BotLedger.reset(server, h.getUUID());
			server.getPlayerList().remove(h);
		}
		helper.succeed();
	}

	/**
	 * Reviewer B: a new difficulty at a table that holds the whole world budget ({@code bots.maxActive}) replaces
	 * its bots instead of stranding the table with none ("none available"): the leaving bots free their own slots.
	 */
	@GameTest
	public void relevelAtAWorldFullTableKeepsItsBots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 100, pos);
		TableBots bots = new TableBots(t, mixed(4, true, BotDifficulty.EASY), OwnerControls.unowned(6));
		ServerPlayer a = player(helper, pos);
		var cfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.bots();
		int maxActive = cfg.maxActive;
		int buyIns = cfg.tableBuyInsPerDay;
		try {
			cfg.tableBuyInsPerDay = 0; // unlimited: this test is about the world budget
			bots.admit(a);
			t.sit(a);
			bots.safePoint(level);
			helper.assertTrue(t.bots() == 4, "4 EASY bots, got " + t.bots());
			cfg.maxActive = Bots.active(); // this table's bots use up the whole budget
			Result<BotSettings> r = bots.requestChange(a, mixed(4, true, BotDifficulty.HARD), false);
			helper.assertTrue(r.isOk(), "the host changes the level");
			TableBots.SafePointResult sp = bots.safePoint(level);
			helper.assertTrue(sp.left().size() == 4, "every EASY bot leaves, got " + sp.left().size());
			helper.assertTrue(t.bots() == 4 && bots.bots().stream().allMatch(b -> b.profile.level() == BotDifficulty.HARD),
				"4 HARD bots take their slots, got " + t.bots());
			helper.assertTrue(Bots.active() <= cfg.maxActive, "world budget respected");
		} finally {
			cfg.maxActive = maxActive;
			cfg.tableBuyInsPerDay = buyIns;
			cleanup(helper, bots, a);
		}
		helper.succeed();
	}

	/**
	 * Reviewer B: an atmosphere bot the game dropped on its own (VirtualSeats.resolve: a human was seated on its
	 * seat and no seat was left) is forgotten at the next safe point, not counted as seated forever.
	 */
	@GameTest
	public void droppedAtmosphereBotIsForgotten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("roulette", BotRole.ATMOSPHERE, 6, 0, pos);
		TableBots bots = new TableBots(t, mixed(3, true, BotDifficulty.NORMAL), OwnerControls.unowned(6));
		ServerPlayer h = player(helper, pos);
		try {
			bots.admit(h);
			t.sit(h);
			bots.safePoint(level);
			int n = t.bots();
			helper.assertTrue(n >= 1 && bots.bots().size() == n, "atmosphere bots seated: " + n);
			String dropped = bots.bots().getFirst().key();
			t.seats[t.seatOf(dropped)] = null; // the game dropped it
			bots.safePoint(level);
			helper.assertTrue(bots.bot(dropped) == null, "the dropped bot is forgotten");
			helper.assertTrue(bots.bots().size() == t.bots(), "core and the table agree: " + bots.bots().size() + " vs " + t.bots());
		} finally {
			cleanup(helper, bots, h);
		}
		helper.succeed();
	}

	/**
	 * Reviewer B: the limits an owner edits (and the charter switch sync starts from) at an owned table are the
	 * owned defaults (Atmosphere only, private forbidden) until the owner saved some, never the raw unowned ones
	 * (Allowed, private on) — or the first edit would silently allow bankroll-funded money bots.
	 */
	@GameTest
	public void ownedTableLimitsStartFromOwnedDefaults(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 100, pos);
		TableBots bots = new TableBots(t, mixed(4, true, BotDifficulty.NORMAL), OwnerControls.unowned(6));
		helper.assertTrue(bots.ownerLimits(level).equals(OwnerControls.unowned(6)), "unowned: the stored limits");
		owned(helper, pos, 1000, bankroll -> {
			OwnerControls l = bots.ownerLimits(level);
			helper.assertTrue(l.equals(OwnerControls.ownedDefaults(6)), "owned, never saved: owned defaults, got " + l);
			helper.assertTrue(bots.effectiveLimits(level).botsMode() == BotsMode.ATMOSPHERE, "effective: Atmosphere only");
			OwnerControls saved = new OwnerControls(BotsMode.ALLOWED, false, 3, false);
			bots.setLimits(saved);
			helper.assertTrue(bots.ownerLimits(level).equals(saved), "saved: the owner's");
		});
		helper.succeed();
	}

	/**
	 * Review wave 2, M1 + m5: the charter breaks while owner-funded bots sit at the table. Their chips come back
	 * after the close and follow the tombstone to the (offline) owner; once the table is no longer owned, the
	 * next safe point sends the bankroll bots home (their purse is no longer this table's), and the table's bank
	 * bots never serve an owned casino.
	 */
	@GameTest
	public void charterBreakSendsBotChipsToTheOwner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Economy eco = Economies.get();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		UUID owner = UUID.randomUUID(); // offline
		String bankroll = "jint2_break_" + UUID.randomUUID();
		eco.bankrolls(server).open(bankroll, owner);
		eco.transfer(server, AccountId.HOUSE, AccountId.bankroll(bankroll), 10_000, TEST);
		TableOwnershipProvider prev = CoreServices.tableOwnership();
		OwnedTable table = new OwnedTable(owner, bankroll, 0, 0, true);
		CoreServices.setTableOwnership((l, p) -> p.equals(pos) ? Optional.of(table) : prev.owner(l, p));
		FakeTable t = new FakeTable("poker", BotRole.MONEY, 6, 200, pos);
		TableBots bots = new TableBots(t, mixed(2, true, BotDifficulty.NORMAL), OwnerControls.unowned(6));
		bots.setLimits(new OwnerControls(BotsMode.ALLOWED, true, 5, false));
		ServerPlayer h = player(helper, pos);
		try {
			bots.admit(h);
			t.sit(h);
			TableBots.SafePointResult r = bots.safePoint(level);
			helper.assertTrue(r.joined().size() == 2 && r.joined().stream().allMatch(b -> b.purse.equals(Purse.bankroll(bankroll))), "2 owner-funded bots");
			TableBots.SeatedBot winner = r.joined().get(0);
			bots.setStack(winner.key(), 350); // it won 150 from the human (pot escrowed in the bank)
			t.stacks.put(winner.key(), 350L);
			// the charter breaks mid-hand: the rest is paid out, the bankroll is closed (tombstoned to the owner)
			long rest = eco.bankrolls(server).get(bankroll).orElseThrow().balance();
			helper.assertTrue(rest == 9600, "10 000 − 2 × 200 buy-ins");
			eco.transfer(server, AccountId.bankroll(bankroll), AccountId.player(owner), rest, TEST);
			eco.bankrolls(server).close(bankroll);
			CoreServices.setTableOwnership(prev); // the table is no longer owned
			// m5: at the next safe point the bankroll bots leave with what they hold (their purse is not this table's)
			TableBots.SafePointResult after = bots.safePoint(level);
			helper.assertTrue(after.left().size() == 2, "both bankroll bots left: " + after.left().size());
			helper.assertTrue(after.joined().stream().allMatch(b -> b.purse.equals(Purse.BANK)), "house bots may sit at the unowned table");
			helper.assertTrue(eco.bankrolls(server).get(bankroll).isEmpty(), "the closed bankroll is never re-created");
			helper.assertTrue(Economies.get().balance(server, owner) == 9600 + 350 + 200,
				"M1: the owner got the payout and both stacks (offline): " + Economies.get().balance(server, owner));
			helper.assertTrue(eco.bankrolls(server).closedOwner(bankroll).orElseThrow().equals(owner), "tombstone kept while fresh");
		} finally {
			CoreServices.setTableOwnership(prev);
			cleanup(helper, bots, h);
		}
		helper.succeed();
	}
}
