package dev.nezo.burmaldaholic.gametest.v2.roulette;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteScreen;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane C (roulette v2, docs/architecture/ui-v2.md §11.2–11.4): the real player flow on the v2 screen — open the table by
 * USING the block, place a straight, a split, a corner, a street, a dozen, red and a call bet by CLICKING the layout at
 * 480 × 270 (1920 × 1080 @4) and 640 × 360 (2560 × 1440 @4, 1920 × 1080 @3), spin with the primary, assert every
 * stake and the payout on the SERVER — plus the in-world board at 3 / 5 / 8 blocks. Screenshots {@code v2c_*}, report
 * {@code v2c_report.txt}. Run only this lane: {@code BURMALDAHOLIC_CGT=C}.
 */
public class RouletteV2ClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	/** The spectator bettor (a second, server-side player), created once per world. */
	private ServerPlayer other;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("C")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			RealFlow f = new RealFlow(context, world);
			// PHASE2 C2 / PHASE2B §0.7: a FRESH player places every bet type by clicking, at the real players' sizes
			step("en 480", () -> round(f, "en_us", 1920, 1080, 4, "480"));
			step("ru 480", () -> round(f, "ru_ru", 1920, 1080, 4, "480"));
			step("en 640", () -> round(f, "en_us", 1920, 1080, 3, "640"));
			step("ru 640", () -> round(f, "ru_ru", 1920, 1080, 3, "640"));
			step("en 427", () -> round(f, "en_us", 1280, 720, 3, "427"));
			step("en 1280 framed", () -> round(f, "en_us", 2560, 1440, 2, "1280"));
			f.close();
			f.language("en_us");
			context.getInput().resizeWindow(1920, 1080);
			guiScale(context, 0);
			context.waitTicks(5);
			step("world", () -> inWorld(context, world));
			if (other != null) world.getServer().runOnServer(server -> server.getPlayerList().remove(other));
		} finally {
			guiScale(context, 0);
			language(context, "en_us");
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("roulette v2: " + String.join("; ", failures));
	}

	private void step(String name, Runnable r) {
		try {
			r.run();
			report.add(name + ": ok");
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": " + e.getMessage());
		}
	}

	// ---- the real player flow ---------------------------------------------------------------------------------------

	/** One spot of every {@link BetType} (both split directions, both trios); the call bets are clicked after them. */
	static List<Spot> everyBetType() {
		List<Spot> out = new ArrayList<>(List.of(Spot.of(BetType.STRAIGHT, 0), Spot.of(BetType.SPLIT, 8, 11), Spot.of(BetType.SPLIT, 17, 18),
			Spot.of(BetType.SPLIT, 0, 2), Spot.of(BetType.STREET, 4, 5, 6), Spot.of(BetType.TRIO, 0, 1, 2), Spot.of(BetType.TRIO, 0, 2, 3),
			Spot.of(BetType.FIRST_FOUR, 0, 1, 2, 3), Spot.of(BetType.CORNER, 20, 21, 23, 24), Spot.of(BetType.SIX_LINE, 31, 32, 33, 34, 35, 36)));
		for (BetType t : List.of(BetType.COLUMN, BetType.DOZEN)) {
			for (int i = 1; i <= 3; i++) out.add(Spot.of(t, Spot.outsideNumbers(t, i)));
		}
		for (BetType t : List.of(BetType.RED, BetType.BLACK, BetType.ODD, BetType.EVEN, BetType.LOW, BetType.HIGH)) {
			out.add(Spot.of(t, Spot.outsideNumbers(t, 0)));
		}
		return out;
	}

	private static RouletteTableBlockEntity be(MinecraftServer server, BlockPos pos) {
		return server.overworld().getBlockEntity(pos) instanceof RouletteTableBlockEntity be ? be : null;
	}

	/**
	 * A fresh player (not seated, no chip armed, no last slip, new table) opens the table by USING it and clicks the felt
	 * straight away (the first chip seats them); then arms the smallest chip and clicks one spot of every bet type and the
	 * four call bets, each stake asserted on the SERVER; value pills while betting; Spin; at the result the banner is clear
	 * of the winning cell and the primary is Repeat (enabled), pressed → the slip is back when betting opens; Clear; then a
	 * second player spins alone and this player, without a bet, sees the number ("17 RED") in the banner.
	 */
	private void round(RealFlow f, String lang, int fbW, int fbH, int gui, String w) {
		f.close();
		f.language(lang);
		f.size(fbW, fbH, gui);
		String tag = lang + "_" + w;
		BlockPos pos = f.place(RouletteModule.TABLE.block(), 0);
		f.server(server -> {
			RouletteTableBlockEntity be = be(server, pos);
			if (be != null) be.setThemeForTesting(0);
			return 0;
		});
		f.open(pos, RouletteScreen.class);
		report.add(tag + ": viewport " + f.viewport());
		boolean seated = f.server(server -> be(server, pos).isSeated(RealFlow.player(server)));
		if (seated) throw new AssertionError("not a fresh player: already seated");
		f.shot("p2_c2_roulette_open_" + tag);
		// 1. the fresh player's very first click: straight 17, no chip armed, no Sit
		Spot first = Spot.of(BetType.STRAIGHT, 17);
		f.click(RouletteLayout.spotId(first));
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> Bets.amountOn(be.betsOf(owner(be)), first) > 0, "first click on 17 places a bet");
		long firstAmount = f.server(server -> Bets.amountOn(be(server, pos).betsOf(RealFlow.player(server).getUUID()), first));
		report.add(tag + ": first click placed " + firstAmount + " on 17 (seated by the bet)");
		List<Bet> expected = new ArrayList<>(List.of(new Bet(first, firstAmount)));
		// 2. arm the smallest chip, every bet type
		long chip = f.client(mc -> mc.gui.screen() instanceof RouletteScreen sc ? sc.chrome().bet.chips()[0] : -1L);
		f.click("chip.0");
		int n = 0;
		for (Spot s : everyBetType()) {
			f.click(RouletteLayout.spotId(s));
			expected = Bets.merge(expected, new Bet(s, chip));
			long want = Bets.amountOn(expected, s);
			f.waitServer(pos, RouletteTableBlockEntity.class, be -> Bets.amountOn(be.betsOf(owner(be)), s) == want, tag + " stake " + want + " on " + s.key());
			if (++n == 4) {
				f.context().waitTicks(3);
				int pills = f.client(mc -> mc.gui.screen() instanceof RouletteScreen sc ? sc.valuePillsDrawn() : -1);
				report.add(tag + ": value pills with 5 stacks: " + pills);
				if (pills <= 0) throw new AssertionError("no value pills on the own stacks while betting (" + pills + ")");
				f.shot("p2_c2_roulette_betting_" + tag);
			}
		}
		for (Racetrack.Section sec : Racetrack.Section.values()) {
			f.click(RouletteLayout.callId(sec));
			expected = Bets.mergeAll(expected, Racetrack.bets(sec, chip));
			long total = Bets.totalStaked(expected);
			f.waitServer(pos, RouletteTableBlockEntity.class, be -> Bets.totalStaked(be.betsOf(owner(be))) == total, tag + " call " + sec.id() + ", total " + total);
		}
		List<Bet> slip = expected;
		long staked = Bets.totalStaked(slip);
		if (!f.server(server -> sameSlip(be(server, pos).betsOf(RealFlow.player(server).getUUID()), slip))) {
			throw new AssertionError("server slip differs from the clicked bets");
		}
		report.add(tag + ": every bet type placed by clicks, " + slip.size() + " spots, staked " + staked + " (chip " + chip + ")");
		f.context().waitTicks(3);
		f.shot("p2_c2_roulette_allbets_" + tag);
		// 3. spin with the primary
		long before = f.server(server -> Economies.get().balance(RealFlow.player(server)));
		f.click("primary");
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.roundPhase().equals("spin"), "spin", 120);
		f.context().waitTicks(50);
		f.shot("p2_c2_roulette_spin_" + tag);
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.roundPhase().equals("result"), "result", 400);
		int result = f.server(server -> be(server, pos).sync().result());
		resultChecks(f, tag, result, false);
		f.shot("p2_c2_roulette_result_" + tag);
		long after = f.server(server -> Economies.get().balance(RealFlow.player(server)));
		long ret = Bets.totalReturn(slip, result, CasinoConfig.roulette().laPartage);
		report.add(tag + ": result " + result + ", staked " + staked + ", returned " + ret + ", balance " + before + " -> " + after);
		if (after - before != ret) throw new AssertionError("balance moved " + (after - before) + " but the settle returns " + ret + " (result " + result + ")");
		// 4. Repeat during the result: enabled, queued, the slip is back when betting opens
		boolean stillResult = f.server(server -> be(server, pos).roundPhase().equals("result"));
		f.click("primary");
		boolean queued = f.client(mc -> mc.gui.screen() instanceof RouletteScreen sc && sc.rebetQueued());
		report.add(tag + ": Repeat pressed during the result: " + stillResult + ", queued " + queued);
		if (stillResult && !queued) throw new AssertionError("Repeat during the result did not queue");
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.roundPhase().equals("betting") && Bets.totalStaked(be.betsOf(owner(be))) == staked,
			"Repeat " + staked + " placed when betting opened", 200);
		f.context().waitTicks(4);
		f.shot("p2_c2_roulette_rebet_" + tag);
		f.click("tool.clear");
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.betsOf(owner(be)).isEmpty(), "Clear");
		// 5. spectator: another player spins alone; this one has no bet and sees the number in the banner
		spectator(f, pos, tag);
	}

	/** At the result: the banner shows, never over the winning cell; for a bettor the primary is Repeat and enabled. */
	private void resultChecks(RealFlow f, String tag, int result, boolean spectator) {
		f.waitChrome(c -> c.banner.active() && !c.banner.box().isEmpty(), tag + " the result banner shows");
		Object[] got = f.client(mc -> {
			if (!(mc.gui.screen() instanceof RouletteScreen sc)) return null;
			var c = sc.chrome();
			RouletteLayout l = RouletteLayout.of(c.grid());
			var g = c.primary.gate();
			return new Object[] {c.banner.box(), l.cell(result), c.banner.summary() == null ? "" : c.banner.summary().getString(), g.action(), g.enabled(),
				c.banner.compact()};
		});
		if (got == null) throw new AssertionError("the roulette screen closed");
		var box = (dev.nezo.burmaldaholic.core.ui.v2.Box) got[0];
		var cell = (dev.nezo.burmaldaholic.core.ui.v2.Box) got[1];
		report.add(tag + (spectator ? " spectator" : "") + ": banner '" + got[2] + "' at " + box + ((Boolean) got[5] ? " compact" : "") + ", winning cell " + cell
			+ ", primary " + got[3] + " enabled " + got[4]);
		if (box.intersects(cell)) throw new AssertionError("the banner " + box + " covers the winning cell " + result + " " + cell);
		if (spectator) {
			String want = f.client(mc -> RouletteScreen.numberWord(result).getString());
			if (!want.equals(got[2])) throw new AssertionError("spectator banner '" + got[2] + "', expected '" + want + "'");
		} else if (!"rebet".equals(got[3]) || !(Boolean) got[4]) {
			throw new AssertionError("at the result the primary is " + got[3] + " enabled " + got[4] + ", expected Repeat enabled");
		}
	}

	private void spectator(RealFlow f, BlockPos pos, String tag) {
		f.server(server -> {
			if (other == null) other = fakePlayer(server, "Spectated");
			Economies.get().setBalance(server, other.getUUID(), 5000, dev.nezo.burmaldaholic.core.economy.Economy.Transaction.of("roulette", "gametest"));
			other.teleportTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
			RouletteTableBlockEntity be = be(server, pos);
			be.sit(other);
			bet(be, other, "straight", 10, 17);
			be.onAction(other, "spin", new CompoundTag());
			return 0;
		});
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.roundPhase().equals("result"), "spectated spin reaches the result", 400);
		int result = f.server(server -> be(server, pos).sync().result());
		resultChecks(f, tag, result, true);
		f.shot("p2_c2_roulette_spectator_" + tag);
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.roundPhase().equals("betting"), "betting after the spectated spin", 200);
	}

	/** A second player on the integrated server (a mock connection), like the server real-flow tests. */
	private static ServerPlayer fakePlayer(MinecraftServer server, String name) {
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
		ServerPlayer p = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
		var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, p, cookie);
		return p;
	}

	private static java.util.UUID owner(RouletteTableBlockEntity be) {
		return be.seats().occupied().isEmpty() ? new java.util.UUID(0, 0) : be.seats().occupied().getFirst().player();
	}

	private static boolean sameSlip(List<Bet> a, List<Bet> b) {
		if (a.size() != b.size()) return false;
		for (Bet x : b) if (Bets.amountOn(a, x.spot()) != x.amount()) return false;
		return true;
	}

	// ---- the in-world board ------------------------------------------------------------------------------------------

	private void inWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos[] pos = new BlockPos[2];
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			BlockPos stand = p.blockPosition().offset(0, 0, 20);
			var level = server.overworld();
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -1; dz <= 12; dz++) {
					for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(stand.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(stand.offset(dx, -1, dz), Blocks.POLISHED_ANDESITE.defaultBlockState());
				}
			}
			pos[1] = stand;
			pos[0] = stand.offset(0, 0, 9);
			level.setBlockAndUpdate(pos[0], RouletteModule.TABLE.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH));
			// stand at the table first (sit() needs the player in reach; the earlier rounds left them 20 blocks away)
			p.teleportTo(pos[0].getX() + 0.5, pos[0].getY(), pos[0].getZ() - 1.5);
			if (level.getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be) {
				be.setThemeForTesting(0);
				be.sit(p);
				bet(be, p, "straight", 25, 17);
				bet(be, p, "split", 10, 8, 11);
				bet(be, p, "corner", 5, 20, 21, 23, 24);
				bet(be, p, "red", 20, redNumbers());
				bet(be, p, "dozen", 25, range(13, 24));
			}
		});
		context.waitTicks(30);
		for (float d : new float[] {3f, 5f, 8f}) {
			stand(context, world, pos[0], d);
			context.takeScreenshot("v2c_world_betting_" + (int) d + "m");
		}
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be) be.onAction(player(server), "spin", new CompoundTag());
		});
		stand(context, world, pos[0], 3f);
		context.waitTicks(20 + 40);
		context.takeScreenshot("v2c_world_spin_3m");
		context.waitTicks(80);
		context.takeScreenshot("v2c_world_result_3m");
		stand(context, world, pos[0], 5f);
		context.takeScreenshot("v2c_world_result_5m");
		int[] result = world.getServer().computeOnServer(server -> server.overworld().getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be
			? new int[] {be.sync().result()} : new int[] {-9});
		int shown = context.computeOnClient(mc -> mc.level != null && mc.level.getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be
			&& be.clientSync() != null ? be.clientSync().result() : -2);
		int stacks = context.computeOnClient(mc -> mc.level != null && mc.level.getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be
			? be.clientPub().stacks().size() : -1);
		report.add("world: server result " + result[0] + ", client sync " + shown + ", public stacks " + stacks);
		if (shown != result[0]) failures.add("in-world wheel result " + shown + " != server " + result[0]);
		if (stacks < 5) failures.add("in-world stacks " + stacks + " < 5");
	}

	/** Stands {@code d} blocks in front of the table (it faces north: the player is north of it), looking at the felt. */
	private static void stand(ClientGameTestContext context, TestSingleplayerContext world, BlockPos table, float d) {
		double eye = 1.62;
		double y = table.getY();
		double z = table.getZ() + 0.5 - d;
		world.getServer().runCommand("tp @p " + (table.getX() + 0.5) + " " + y + " " + z);
		float pitch = (float) Math.toDegrees(Math.atan2(eye - 1.0, d));
		for (int i = 0; i < 3; i++) {
			context.runOnClient(mc -> {
				if (mc.player != null) {
					mc.player.setYRot(0f);
					mc.player.setXRot(pitch);
					mc.player.setYHeadRot(0f);
				}
			});
			context.waitTicks(2);
		}
		context.waitTicks(4);
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static void bet(RouletteTableBlockEntity be, ServerPlayer p, String type, long amount, int... nums) {
		CompoundTag args = new CompoundTag();
		args.putString("type", type);
		args.putIntArray("nums", nums);
		args.putLong("amount", amount);
		be.onAction(p, "bet", args);
	}

	static int[] range(int a, int b) {
		int[] out = new int[b - a + 1];
		for (int i = a; i <= b; i++) out[i - a] = i;
		return out;
	}

	static int[] redNumbers() {
		return new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
	}

	static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	static void language(ClientGameTestContext context, String code) {
		boolean same = context.computeOnClient(mc -> mc.getLanguageManager().getSelected().equals(code));
		if (same) return;
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitFor(mc -> mc.gui.overlay() == null, 1200);
		context.waitTicks(20);
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>(report);
			failures.forEach(f -> lines.add("FAIL " + f));
			Files.writeString(dir.resolve("v2c_report.txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
