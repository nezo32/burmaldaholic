package dev.nezo.burmaldaholic.gametest.tables;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.client.BaccaratScreen;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.client.CrapsScreen;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteScreen;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.client.UthScreen;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * v0.1.3 regression on the table UI v2 screens: real mouse buttons and real key presses through the vanilla input path.
 *
 * <p>Minecraft 26.3 (SDL3) renumbered mouse buttons (left 0 → 1, right 1 → 3) and every key code; the jar is compiled on
 * 26.2 and javac inlines numeric constants, so a raw comparison silently fails there (v0.1.2: "chips do nothing"). The
 * kit resolves input at runtime ({@code V2Input} / {@code Inputs}); this test presses the running version's codes
 * ({@link Inputs#MOUSE_LEFT}, {@link Inputs#MOUSE_RIGHT}, {@code RealFlow.key(name)}) and asserts the SERVER:
 * <ul>
 * <li>blackjack: Space sits, key 2 + left click adds chips, right click on a chip takes one back, Space deals;
 * <li>UTH: left click sits, key 1 + left click build the Ante, right click takes back, Space deals;
 * <li>baccarat: key 1 arms a chip, key P bets the Player box, right click unbets it, a left click bets it again;
 * <li>roulette: key 1 arms, a left click on Straight 17 bets it;
 * <li>craps: left click sits, a chip and a left click on Pass bet it, right click on the felt undoes it.
 * </ul>
 * Run with {@code -Pmc=26.3} to cover the renumbered input.
 */
public class RealInputClientGameTests implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();
	private final List<String> log = new ArrayList<>();
	private int lane;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create(); var q = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			RealFlow f = new RealFlow(context, world);
			f.size(1920, 1080, 0); // GUI scale Auto, the owner's setup
			log.add("input: MOUSE_LEFT " + Inputs.MOUSE_LEFT + " MOUSE_RIGHT " + Inputs.MOUSE_RIGHT + " SPACE " + Inputs.KEY_SPACE + " 1 " + Inputs.KEY_1);
			game(f, "blackjack", this::blackjack);
			game(f, "uth", this::uth);
			game(f, "baccarat", this::baccarat);
			game(f, "roulette", this::roulette);
			game(f, "craps", this::craps);
			f.close();
			context.waitTicks(10);
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			for (String l : log) System.out.println("[real-input] " + l);
			for (String fl : failures) System.out.println("[real-input] FAIL " + fl);
		}
		if (!failures.isEmpty()) throw new AssertionError("real input on the v2 tables: " + String.join("; ", failures));
	}

	private void game(RealFlow f, String name, Consumer<RealFlow> body) {
		try {
			body.accept(f);
			log.add(name + ": ok");
		} catch (AssertionError | RuntimeException e) {
			f.context().takeScreenshot("v013_v2_input_fail_" + name);
			failures.add(name + ": " + e.getMessage() + " (status '" + safeStatus(f) + "')");
		} finally {
			f.close();
			f.context().waitTicks(5);
		}
	}

	// ---- games ---------------------------------------------------------------------------------------------------------

	private void blackjack(RealFlow f) {
		BlockPos pos = open(f, BlackjackModule.TABLE.block(), BlackjackScreen.class);
		UUID me = me(f);
		waitPrimary(f, "sit");
		f.key(KeyMap.SPACE); // Space = the primary (Sit down)
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated by Space");
		long[] chips = chips(f);
		f.key(KeyMap.KEY_2); // + chips[1]
		f.click(KeyMap.Ids.chip(0)); // + chips[0]
		f.click(KeyMap.Ids.chip(0)); // + chips[0]
		f.rightClick(KeyMap.Ids.chip(0)); // - chips[0]
		long want = chips[1] + chips[0];
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == 0, "the chips stay a local draft");
		waitPrimary(f, "bet");
		f.key(KeyMap.SPACE); // Deal
		// (a natural settles at once, so the stake alone may already be gone: last_bet tells)
		f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == want
			|| t.writeClientState(RealFlow.player(t.getLevel().getServer())).getLongOr("last_bet", 0) == want,
			"Space dealt the bet " + want + " (key 2, left, left, right on the chips)");
		log.add("blackjack: dealt " + want);
	}

	private void uth(RealFlow f) {
		BlockPos pos = open(f, UthModule.TABLE.block(), UthScreen.class);
		UUID me = me(f);
		waitPrimary(f, "sit");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, UthTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated by a left click");
		long[] chips = chips(f);
		long start = ante(f);
		f.key(KeyMap.KEY_1); // + chips[0]
		check(ante(f) == start + chips[0], "key 1 adds chip " + chips[0] + " to the Ante: " + start + " -> " + ante(f));
		f.click(KeyMap.Ids.chip(0)); // + chips[0]
		check(ante(f) == start + 2 * chips[0], "a left click on the chip adds it: Ante " + ante(f));
		f.rightClick(KeyMap.Ids.chip(0)); // - chips[0]
		long want = start + chips[0];
		check(ante(f) == want, "a right click on the chip takes it back: Ante " + ante(f) + ", want " + want);
		f.key(KeyMap.SPACE); // Deal
		f.waitServer(pos, UthTableBlockEntity.class, t -> t.round() != null && t.round().seat(me) != null && t.round().seat(me).ante == want,
			"Space dealt the Ante " + want);
		log.add("uth: dealt Ante " + want);
	}

	private void baccarat(RealFlow f) {
		BlockPos pos = open(f, BaccaratModule.TABLE.block(), BaccaratScreen.class);
		UUID me = me(f);
		waitPrimary(f, "sit");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated by a left click");
		long chip = chips(f)[0];
		f.key(KeyMap.KEY_1); // arm chips[0]
		f.key(KeyMap.P); // P = the Player box
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.PLAYER, 0L) == chip, "keys 1 + P bet " + chip + " on Player");
		f.rightClick(KeyMap.Ids.felt("box.player"));
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.PLAYER, 0L) == 0, "a right click unbets Player");
		f.click(KeyMap.Ids.felt("box.player"));
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.PLAYER, 0L) == chip, "a left click bets Player again");
		log.add("baccarat: Player " + chip);
	}

	private void roulette(RealFlow f) {
		BlockPos pos = open(f, RouletteModule.TABLE.block(), RouletteScreen.class);
		UUID me = me(f);
		waitPrimary(f, "sit");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.seats().occupied().size() == 1, "seated by a left click");
		long chip = chips(f)[0];
		f.key(KeyMap.KEY_1); // arm chips[0]
		Spot s17 = Spot.of(BetType.STRAIGHT, 17);
		f.click(RouletteLayout.spotId(s17));
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> Bets.amountOn(be.betsOf(me), s17) == chip, "a left click bets " + chip + " on 17");
		log.add("roulette: 17 x " + chip);
	}

	private void craps(RealFlow f) {
		BlockPos pos = open(f, CrapsModule.TABLE.block(), CrapsScreen.class);
		UUID me = me(f);
		waitPrimary(f, "sit");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.seats().occupied().size() == 1, "seated by a left click");
		long chip = chips(f)[0];
		f.click(KeyMap.Ids.chip(0));
		f.click(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(me).stream().anyMatch(b -> b.flat() == chip), "a left click bets Pass " + chip);
		f.rightClick(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(me).isEmpty(), "a right click on the felt undoes the Pass bet");
		log.add("craps: Pass " + chip + " placed and undone");
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	private <T extends net.minecraft.client.gui.screens.Screen> BlockPos open(RealFlow f, Block block, Class<T> screen) {
		f.world().getServer().runCommand("casino balance set @p 5000");
		f.world().getServer().runCommand("execute as @p at @s run tp @s ~7 ~ ~ 0 0"); // a fresh spot per table
		f.context().waitTicks(5);
		BlockPos pos = f.place(block, (lane++ % 3) - 1);
		f.context().waitTicks(5);
		f.open(pos, screen);
		f.context().waitTicks(10);
		return pos;
	}

	private static UUID me(RealFlow f) {
		return f.server(server -> RealFlow.player(server).getUUID());
	}

	private static void waitPrimary(RealFlow f, String action) {
		f.waitChrome(c -> action.equals(c.primary.gate().action()) && c.primary.gate().enabled(), "primary '" + action + "' enabled");
	}

	private static long[] chips(RealFlow f) {
		f.waitChrome(c -> c.bet.visible() && c.bet.chips().length >= 2, "the chip tray");
		return f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().bet.chips().clone());
	}

	private static long ante(RealFlow f) {
		f.context().waitTicks(2);
		return f.client(mc -> mc.gui.screen() instanceof UthScreen s ? s.draft().ante() : -1L);
	}

	private static String safeStatus(RealFlow f) {
		try {
			return f.status();
		} catch (RuntimeException e) {
			return "?";
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
