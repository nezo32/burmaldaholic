package dev.nezo.burmaldaholic.gametest.v2.craps;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.games.craps.present.CrapsFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
				// PHASE2 standard set: 480 x 270 (1920 x 1080 GUI 4) and 640 x 360 (GUI 3) in EN and RU, 427 x 240 (1280 x 720 GUI 3) EN.
				// "fresh" rounds: no Sit, no chip picked, the felt click alone; the others sit first and arm a chip.
				round(f, 1920, 1080, 4, "en_us", 0, true);
				round(f, 1920, 1080, 3, "en_us", 2, false);
				round(f, 1280, 720, 3, "en_us", 4, true);
				f.language("ru_ru");
				round(f, 1920, 1080, 4, "ru_ru", 6, true);
				round(f, 1920, 1080, 3, "ru_ru", 8, false);
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

	/** Line 1 of the status (the flash after a refused click): its translation key, or its text. */
	private static String statusKey(RealFlow f) {
		return f.client(mc -> {
			if (!(mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h)) return "no v2 screen";
			Component c = h.chrome().status.line1();
			return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
		});
	}

	private static void waitSettled(RealFlow f) {
		for (int t = 0; t < RealFlow.TIMEOUT && throwShown(f); t += 2) context(f).waitTicks(2);
		if (throwShown(f)) throw new AssertionError("the throw never settled");
	}

	private static boolean throwShown(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof CrapsScreen s && s.throwShown());
	}

	private static long myOnTable(RealFlow f, BlockPos pos) {
		return myBets(f, pos).stream().mapToLong(b -> b.flat() + b.odds()).sum();
	}

	private static long myFlat(RealFlow f, BlockPos pos, BetKind kind) {
		return myBets(f, pos).stream().filter(b -> b.kind() == kind).mapToLong(Bet::flat).sum();
	}

	private void round(RealFlow f, int fw, int fh, int gs, String lang, int lane, boolean fresh) {
		f.size(fw, fh, gs);
		String tag = "p2_d2_craps_%s_" + lang + "_" + f.viewport().w();
		f.server(server -> {
			Economies.get().setBalance(server, RealFlow.player(server).getUUID(), 12500,
				dev.nezo.burmaldaholic.core.economy.Economy.Transaction.of("craps", "gametest"));
			return null;
		});
		BlockPos pos = f.place(CrapsModule.TABLE.block(), lane - 4);
		f.open(pos, CrapsScreen.class);
		String vp = f.viewport().w() + "x" + f.viewport().h() + " " + lang;
		f.shot(tag.formatted("open"));

		long before = balance(f);
		if (fresh) {
			// 1. the CurseForge report: a fresh player, no Sit pressed, no chip picked: the Pass Line click alone bets
			f.click(CrapsLayout.PASS);
			f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS),
				"fresh player: the Pass Line click bets (" + vp + ", status " + f.status() + ")");
			boolean seated = f.server(server -> ((CrapsTableBlockEntity) server.overworld().getBlockEntity(pos)).seats().occupied().size() == 1);
			if (!seated) throw new AssertionError("the Pass click did not seat the player (" + vp + ")");
		} else {
			// 1. sit down with the primary; Roll without a line bet names the reason and rolls nothing
			f.waitChrome(c -> "sit".equals(c.primary.gate().action()) && c.primary.gate().enabled(), "Sit down offered (" + vp + ")");
			f.click("primary");
			f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.seats().occupied().size() == 1, "seated by the primary");
			f.waitChrome(c -> "roll".equals(c.primary.gate().action()), "Roll shown");
			f.click("primary");
			if (f.status().isEmpty()) throw new AssertionError("no reason for the disabled Roll");
			if (rolls(f, pos) != 0) throw new AssertionError("rolled without a line bet");
			armChip(f, 25);
			f.click(CrapsLayout.PASS);
			f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS && b.flat() == 25),
				"Pass 25 on the server");
		}
		long passFlat = myFlat(f, pos, BetKind.PASS);
		if (balance(f) != before - passFlat) throw new AssertionError("pass bet debited " + passFlat + ": " + before + " -> " + balance(f));
		// negatives on the come-out: Come is closed; the odds lane names its reason and never places a Pass bet (R-CDE M6)
		f.click(CrapsLayout.COME);
		context(f).waitTicks(4);
		if (myBets(f, pos).stream().anyMatch(b -> b.kind() == BetKind.COME)) throw new AssertionError("Come accepted on the come-out");
		long onTable = myOnTable(f, pos);
		f.click(CrapsLayout.PASS_ODDS);
		context(f).waitTicks(4);
		if (myOnTable(f, pos) != onTable) throw new AssertionError("the odds lane on the come-out changed the bets (" + vp + ")");
		String lane0 = statusKey(f);
		if (!(CrapsFlow.K + "reason.odds_need_point").equals(lane0)) throw new AssertionError("odds lane on the come-out shows " + lane0 + " (" + vp + ")");
		f.shot(tag.formatted("odds_need_point"));
		context(f).waitTicks(60); // the flash fades
		f.shot(tag.formatted("bet"));

		// 2. roll until a point is on (naturals / craps resolve the line: bet again by clicking). On the first throw a felt
		// click and a right click while the dice fly change nothing on the server and name reason.rolling (R-CDE M5).
		int rolled = 0;
		boolean rollingChecked = false;
		while (point(f, pos) == 0) {
			if (++rolled > MAX_ROLLS) throw new AssertionError("no point in " + MAX_ROLLS + " rolls");
			if (myBets(f, pos).stream().noneMatch(b -> b.kind() == BetKind.PASS)) {
				waitSettled(f); // the felt is closed while the dice fly (R-CDE M5)
				f.waitChrome(c -> c.hits().box(CrapsLayout.PASS).isPresent(), "felt");
				f.click(CrapsLayout.PASS);
				f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().hasLineBet(playerId(f)), "Pass again");
			}
			rollOnce(f, pos, rolled % 2 == 0);
			if (!rollingChecked && throwShown(f)) {
				rollingChecked = true;
				long bal = balance(f);
				int n = myBets(f, pos).size();
				f.click(CrapsLayout.FIELD);
				String k = statusKey(f);
				f.rightClick(CrapsLayout.PASS);
				String k2 = statusKey(f);
				context(f).waitTicks(4);
				if (balance(f) != bal || myBets(f, pos).size() != n) throw new AssertionError("a click while the dice fly moved the server (" + vp + ")");
				if (!V2Keys.REASON_ROLLING.equals(k)) throw new AssertionError("felt click while rolling shows " + k + " (" + vp + ")");
				if (!V2Keys.REASON_ROLLING.equals(k2)) throw new AssertionError("right click while rolling shows " + k2 + " (" + vp + ")");
				f.shot(tag.formatted("rolling"));
			}
		}
		if (!rollingChecked) throw new AssertionError("the throw was never seen in flight (" + vp + ")");
		int pt = point(f, pos);
		context(f).waitTicks(60); // the throw, the puck and the chip beat
		f.shot(tag.formatted("point"));

		// 3. odds behind the Pass line: click the odds lane
		f.click(CrapsLayout.PASS_ODDS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().anyMatch(b -> b.kind() == BetKind.PASS && b.odds() > 0),
			"odds behind the Pass line (point " + pt + ")");
		// 4. a Come and a Field bet; Undo (the tool) takes back exactly the Field, a right click on the felt the Come
		f.click(CrapsLayout.COME);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().anyMatch(b -> b.kind() == BetKind.COME),
			"Come bet");
		f.click(CrapsLayout.FIELD);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().anyMatch(b -> b.kind() == BetKind.FIELD),
			"Field bet");
		long field = myFlat(f, pos, BetKind.FIELD);
		long come = myFlat(f, pos, BetKind.COME);
		long beforeUndo = balance(f);
		f.shot(tag.formatted("field"));
		f.waitChrome(c -> c.bet.toolGate("undo").enabled(), "Undo enabled after the Field bet (status: " + f.status() + ")");
		f.click("tool.undo");
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().noneMatch(b -> b.kind() == BetKind.FIELD),
			"Field taken back by Undo (" + vp + ")");
		if (balance(f) != beforeUndo + field) throw new AssertionError("Undo returned " + (balance(f) - beforeUndo) + ", the Field was " + field);
		if (myBets(f, pos).stream().noneMatch(b -> b.kind() == BetKind.COME)) throw new AssertionError("Undo also took the Come bet");
		context(f).waitTicks(3);
		f.rightClick(CrapsLayout.COME);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(playerId(f)).stream().noneMatch(b -> b.kind() == BetKind.COME),
			"right click took the Come bet back (" + vp + ")");
		if (balance(f) != beforeUndo + field + come) throw new AssertionError("right click returned " + (balance(f) - beforeUndo - field) + ", Come was " + come);
		f.shot(tag.formatted("odds"));

		// 7. roll until the point is made or sevened out; every roll pays exactly the server's returns
		while (point(f, pos) != 0) {
			if (++rolled > MAX_ROLLS) throw new AssertionError("point " + pt + " unresolved after " + MAX_ROLLS + " rolls");
			rollOnce(f, pos, false);
		}
		context(f).waitTicks(36); // the chip beat + the banner
		f.shot(tag.formatted("resolved"));
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
	 * The in-world board (never seen before phase 2): a table with the player's Pass + odds, a Field bet, a point on and
	 * two bots' (virtual) bets, turned to each of the 4 FACINGs and seen from the PLAYER side (the side the table faces) at
	 * 3 and 6 blocks: the print must read from there, every stack sit on its spot, the plates on their seats. Screenshots
	 * {@code p2_d2_craps_world_<facing>_<d>_<lang>} (+ one RU).
	 */
	private void inWorld(RealFlow f) {
		f.size(1920, 1080, 4);
		BlockPos pos = f.place(CrapsModule.TABLE.block(), 0);
		f.server(server -> {
			ServerPlayer p = RealFlow.player(server);
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			be.bots().table().requestChange(p, new dev.nezo.burmaldaholic.core.bots.logic.BotSettings(dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy.MIXED,
				2, dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.NORMAL, false, false, dev.nezo.burmaldaholic.core.bots.logic.BotSpeed.FAST), false);
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
		// the bots bet at their own moment (60-160 ticks after the safe point)
		for (int i = 0; i < 300 && f.server(server -> ((CrapsTableBlockEntity) server.overworld().getBlockEntity(pos)).botBets().shadow().bets().isEmpty()); i += 10) {
			context(f).waitTicks(10);
		}
		String bots = f.server(server -> {
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			return be.bots().bots().size() + " bots, pub " + be.pub().stacks();
		});
		System.out.println("[craps world] " + bots);
		for (net.minecraft.core.Direction facing : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH,
			net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
			f.server(server -> {
				var level = server.overworld();
				level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(
					net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing));
				return null;
			});
			for (int d : new int[] {3, 6}) {
				int dd = d;
				f.server(server -> {
					ServerPlayer p = RealFlow.player(server);
					// the player side = the side the table faces: stand there, look back at the felt
					float yaw = facing.getOpposite().toYRot();
					p.teleportTo(server.overworld(), pos.getX() + 0.5 + facing.getStepX() * dd, pos.getY(), pos.getZ() + 0.5 + facing.getStepZ() * dd,
						java.util.Set.of(), yaw, dd <= 3 ? 35f : 22f, true);
					return null;
				});
				context(f).waitTicks(40);
				f.shot("p2_d2_craps_world_" + facing.getSerializedName() + "_" + d + "_en_us");
			}
		}
		f.language("ru_ru");
		context(f).waitTicks(20);
		f.shot("p2_d2_craps_world_west_6_ru_ru");
		f.language("en_us");
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
		if (space) f.key(KeyMap.SPACE);
		else f.click("primary");
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().rollCount() == n + 1, "the roll after pressing " + (space ? "Space" : "Roll"));
		long ret = lastReturns(f, pos);
		long after = balance(f);
		if (after != before + ret) throw new AssertionError("roll " + (n + 1) + ": balance " + before + " -> " + after + ", server returns " + ret);
	}
}
