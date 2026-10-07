package dev.nezo.burmaldaholic.gametest.v2.craps;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.client.CrapsScreen;
import dev.nezo.burmaldaholic.games.craps.logic.Bet;
import dev.nezo.burmaldaholic.games.craps.logic.BetKind;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane D (craps): REAL-FLOW client GameTests of the v2 screen (docs/architecture/ui-v2.md §11.2). The player opens the
 * table by USING the block, sits with the primary, arms a chip and clicks the PASS LINE, rolls with the primary (Space
 * works too) until a point is on, adds odds by clicking the odds lane, puts a Come and a Field bet down, and keeps
 * rolling until the point is made or sevened out; after every roll the SERVER balance has moved by exactly the server's
 * returns. Negative paths: Roll with no line bet (the reason, no roll), a felt click on a closed spot (the reason, no
 * bet). At 480 × 270 (1920 × 1080 @4) in EN and RU, and at 640 × 360 (2560 × 1440 @4, 1920 × 1080 @3) and 427 × 240
 * (1280 × 720 @3). Screenshots {@code v2_craps_<lang>_<w>_<step>}. Run only this lane: {@code BURMALDAHOLIC_CGT=D}.
 */
public class CrapsV2ClientGameTests implements FabricClientGameTest {
	private static final int MAX_ROLLS = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("D")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			RealFlow f = new RealFlow(context, world);
			try {
				round(f, 1920, 1080, 4, "en_us", 0);
				round(f, 2560, 1440, 4, "en_us", 2);
				round(f, 1920, 1080, 3, "en_us", 4);
				f.language("ru_ru");
				round(f, 1920, 1080, 4, "ru_ru", 6);
				round(f, 1280, 720, 3, "ru_ru", 8);
				f.language("en_us");
				inWorld(f);
			} finally {
				f.close();
				f.language("en_us");
				f.size(1280, 800, 0);
			}
		}
	}

	private static long balance(RealFlow f) {
		return f.server(server -> Economies.get().balance(RealFlow.player(server)));
	}

	private static List<Bet> myBets(RealFlow f, BlockPos pos) {
		return f.server(server -> {
			ServerPlayer p = RealFlow.player(server);
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			return be == null ? List.of() : be.table().betsOf(p.getUUID()).stream().map(Bet::copy).toList();
		});
	}

	private static int rolls(RealFlow f, BlockPos pos) {
		return f.server(server -> ((CrapsTableBlockEntity) server.overworld().getBlockEntity(pos)).table().rollCount());
	}

	private static int point(RealFlow f, BlockPos pos) {
		return f.server(server -> ((CrapsTableBlockEntity) server.overworld().getBlockEntity(pos)).table().point());
	}

	/** Sum of the viewer's returns of the last roll, from the server's own client state. */
	private static long lastReturns(RealFlow f, BlockPos pos) {
		return f.server(server -> {
			ServerPlayer p = RealFlow.player(server);
			CompoundTag s = ((CrapsTableBlockEntity) server.overworld().getBlockEntity(pos)).writeClientState(p);
			long ret = 0;
			var list = s.getListOrEmpty("last_res");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompoundOrEmpty(i);
				if (t.getBooleanOr("mine", false)) ret += t.getLongOr("ret", 0);
			}
			return ret;
		});
	}

	private static void armChip(RealFlow f, long denom) {
		long[] chips = f.client(mc -> mc.gui.screen() instanceof CrapsScreen s ? s.chrome().bet.chips() : new long[0]);
		int idx = -1;
		for (int i = 0; i < chips.length; i++) if (chips[i] == denom) idx = i;
		if (idx < 0) throw new AssertionError("no chip " + denom + " in the tray");
		f.click("chip." + idx);
	}

	private static void waitCanRoll(RealFlow f) {
		f.waitChrome(c -> c.primary.gate().enabled() && "roll".equals(c.primary.gate().action()), "Roll enabled");
	}

	private void round(RealFlow f, int fw, int fh, int gs, String lang, int lane) {
		f.size(fw, fh, gs);
		String tag = "v2_craps_" + lang + "_" + f.viewport().w();
		BlockPos pos = f.place(CrapsModule.TABLE.block(), lane - 4);
		f.open(pos, CrapsScreen.class);
		String vp = f.viewport().w() + "x" + f.viewport().h();
		f.shot(tag + "_open");

		// 1. sit down with the primary
		f.waitChrome(c -> "sit".equals(c.primary.gate().action()) && c.primary.gate().enabled(), "Sit down offered (" + vp + ")");
		f.click("primary");
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.seats().occupied().size() == 1, "seated by the primary");

		// 2. negative: Roll without a line bet names the reason and rolls nothing
		f.waitChrome(c -> "roll".equals(c.primary.gate().action()), "Roll shown");
		f.click("primary");
		String reason = f.status();
		if (reason.isEmpty()) throw new AssertionError("no reason for the disabled Roll");
		if (rolls(f, pos) != 0) throw new AssertionError("rolled without a line bet");

		// 3. bet the Pass line by clicking: arm 25, click the felt
		long before = balance(f);
		armChip(f, 25);
		f.click(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS && b.flat() == 25),
			"Pass 25 on the server");
		if (balance(f) != before - 25) throw new AssertionError("pass bet debited 25: " + before + " -> " + balance(f));
		// negative: Come is closed on the come-out
		f.click(CrapsLayout.COME);
		context(f).waitTicks(4);
		if (myBets(f, pos).stream().anyMatch(b -> b.kind() == BetKind.COME)) throw new AssertionError("Come accepted on the come-out");
		f.shot(tag + "_bet");

		// 4. roll until a point is on (naturals / craps resolve the line: bet again by clicking)
		int rolled = 0;
		while (point(f, pos) == 0) {
			if (++rolled > MAX_ROLLS) throw new AssertionError("no point in " + MAX_ROLLS + " rolls");
			if (myBets(f, pos).stream().noneMatch(b -> b.kind() == BetKind.PASS)) {
				f.waitChrome(c -> c.hits().box(CrapsLayout.PASS).isPresent(), "felt");
				f.click(CrapsLayout.PASS);
				f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().hasLineBet(playerId(f)), "Pass again");
			}
			rollOnce(f, pos, rolled % 2 == 0);
		}
		int pt = point(f, pos);
		f.shot(tag + "_point");

		// 5. odds behind the Pass line: click the odds lane
		f.click(CrapsLayout.PASS_ODDS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().anyMatch(b -> b.kind() == BetKind.PASS && b.odds() > 0),
			"odds behind the Pass line (point " + pt + ")");
		// 6. a Come and a Field bet, then Undo the Field with the tool
		f.click(CrapsLayout.COME);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().anyMatch(b -> b.kind() == BetKind.COME),
			"Come bet");
		f.click(CrapsLayout.FIELD);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().anyMatch(b -> b.kind() == BetKind.FIELD),
			"Field bet");
		f.click("tool.undo");
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().noneMatch(b -> b.kind() == BetKind.FIELD),
			"Field taken back by Undo");
		f.shot(tag + "_odds");

		// 7. roll until the point is made or sevened out; every roll pays exactly the server's returns
		while (point(f, pos) != 0) {
			if (++rolled > MAX_ROLLS) throw new AssertionError("point " + pt + " unresolved after " + MAX_ROLLS + " rolls");
			rollOnce(f, pos, false);
		}
		context(f).waitTicks(30); // the chip beat + the banner
		f.shot(tag + "_resolved");
		f.close();
		// leaving plays any travelled Come bet out: the seat frees, no stake stays open
		f.server(server -> {
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			be.leave(RealFlow.player(server).getUUID(), dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason.LEFT);
			server.overworld().removeBlock(pos, false);
			return null;
		});
		context(f).waitTicks(4);
	}

	/**
	 * The in-world board: a table with the player's Pass + odds, a Field bet and a point on, seen from 3 and 6 blocks
	 * (print, stacks, puck ON, dice at rest, the board lines and the plate). Screenshots {@code v2_craps_world_<d>}.
	 */
	private void inWorld(RealFlow f) {
		f.size(1920, 1080, 4);
		BlockPos pos = f.place(CrapsModule.TABLE.block(), 0);
		f.server(server -> {
			ServerPlayer p = RealFlow.player(server);
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			CompoundTag pass = new CompoundTag();
			pass.putString("kind", "pass");
			pass.putLong("amount", 25);
			be.onAction(p, "bet", pass);
			be.roll(4, 4);
			Bet line = be.table().betsOf(p.getUUID()).get(0);
			CompoundTag odds = new CompoundTag();
			odds.putInt("bet", line.id());
			odds.putLong("amount", 50);
			be.onAction(p, "odds", odds);
			CompoundTag field = new CompoundTag();
			field.putString("kind", "field");
			field.putLong("amount", 10);
			be.onAction(p, "bet", field);
			return null;
		});
		for (int d : new int[] {3, 6}) {
			int dd = d;
			f.server(server -> {
				ServerPlayer p = RealFlow.player(server);
				// the table faces north (towards where the player stood): stand north of it, look south and down at the felt
				p.teleportTo(server.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5 - dd, java.util.Set.of(), 0f, dd <= 3 ? 35f : 22f, true);
				return null;
			});
			context(f).waitTicks(60);
			f.shot("v2_craps_world_" + d);
		}
		f.server(server -> {
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			be.leave(RealFlow.player(server).getUUID(), dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason.LEFT);
			return null;
		});
		context(f).waitTicks(4);
	}

	private static UUID playerId(RealFlow f) {
		return f.server(server -> RealFlow.player(server).getUUID());
	}

	private static ClientGameTestContext context(RealFlow f) {
		return f.context();
	}

	/** One roll by the player (the primary, or Space), and the money check of that roll. */
	private static void rollOnce(RealFlow f, BlockPos pos, boolean space) {
		waitCanRoll(f);
		int n = rolls(f, pos);
		long before = balance(f);
		if (space) f.key(32);
		else f.click("primary");
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().rollCount() == n + 1, "the roll after pressing " + (space ? "Space" : "Roll"));
		long ret = lastReturns(f, pos);
		long after = balance(f);
		if (after != before + ret) throw new AssertionError("roll " + (n + 1) + ": balance " + before + " -> " + after + ", server returns " + ret);
	}
}
