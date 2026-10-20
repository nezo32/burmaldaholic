package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.client.ui.v2.BetEntry;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.client.BaccaratScreen;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.client.CrapsScreen;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Tester T-HR, REAL FLOW on every other v2 betting Dock: E opens the bet-entry strip, digit keys type, Enter / OK commit,
 * ½ ×2 Max click at their real pixels, the mouse wheel over the field steps, Esc closes the strip (not the screen), and
 * a typed amount far above the limits never reaches the server. Multi-spot games (roulette, craps, punto banco) get the
 * amount as the per-click unit; UTH / Plinko as the bet. Soft assertions: every defect is listed, then one failure.
 * Filter {@code BURMALDAHOLIC_CGT=THR}.
 */
public class HighRollerEntryClientGameTests implements FabricClientGameTest {
	private final List<String> fails = new ArrayList<>();
	/** A Bronze high roller (as HighRollerClientGameTests): the default Bronze max (100) would clamp every typed amount. */
	private static final int VIP_MAX = 20_000;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("THR")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		int[] saved = new int[1];
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 1000000");
			world.getServer().runOnServer(server -> {
				CasinoConfig.chaos().enabled = false;
				CasinoConfig.contracts().enabled = false;
				CasinoConfig.streak().enabled = false;
				saved[0] = CasinoConfig.vip().maxBet.bronze;
				CasinoConfig.vip().maxBet.bronze = VIP_MAX;
			});
			world.getServer().runCommand("time set noon");
			RealFlow f = new RealFlow(context, world);
			f.language("en_us");
			f.size(1920, 1080, 3);
			UUID me = f.server(server -> RealFlow.player(server).getUUID());
			step("roulette", () -> roulette(f));
			step("craps", () -> craps(f, me));
			step("baccarat", () -> baccarat(f, me));
			step("uth", () -> uth(f, me));
			step("plinko", () -> plinko(f));
			f.close();
		} finally {
			context.runOnClient(mc -> {
				if (saved[0] > 0) CasinoConfig.vip().maxBet.bronze = saved[0];
			});
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
		if (!fails.isEmpty()) throw new AssertionError("T-HR bet entry defects:\n  " + String.join("\n  ", fails));
	}

	private void step(String name, Runnable r) {
		try {
			r.run();
		} catch (AssertionError | RuntimeException e) {
			fails.add(name + ": " + e.getMessage());
		}
	}

	private void check(boolean ok, String what) {
		if (!ok) fails.add(what);
	}

	private static <T> T chrome(RealFlow f, java.util.function.Function<V2Chrome, T> g) {
		return f.client(mc -> g.apply(((V2Chrome.Holder) mc.gui.screen()).chrome()));
	}

	private static void typeAndEnter(RealFlow f, String digits) {
		f.key(KeyMap.E);
		f.waitChrome(c -> c.entry.isOpen() && f.box(BetEntry.OK).isPresent(), "E opens the entry");
		for (char ch : digits.toCharArray()) f.key(String.valueOf(ch));
		f.key(KeyMap.ENTER);
		f.waitChrome(c -> !c.entry.isOpen(), "Enter closes the entry");
	}

	private static BlockPos fresh(RealFlow f, net.minecraft.world.level.block.Block block) {
		return dev.nezo.burmaldaholic.gametest.v2.blackjack.BlackjackV2ClientGameTests.fresh(f, block);
	}

	// ---- roulette: unit, wheel, Esc, overflow --------------------------------------------------------------------------

	private void roulette(RealFlow f) {
		BlockPos pos = fresh(f, RouletteModule.TABLE.block());
		f.open(pos, RouletteScreen.class);
		f.click("primary");
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> be.seats().occupied().size() == 1, "seated");
		f.waitChrome(c -> c.bet.visible() && c.bet.view().canBet(), "bets open");
		long max = chrome(f, c -> c.bet.view().max());
		typeAndEnter(f, "250");
		long unit = chrome(f, c -> c.bet.unit());
		check(unit == 250, "roulette: typed 250 should become the felt unit, got " + unit);
		Spot s17 = Spot.of(BetType.STRAIGHT, 17);
		f.click(RouletteLayout.spotId(s17));
		f.waitServer(pos, RouletteTableBlockEntity.class, be -> Bets.amountOn(be.betsOf(be.seats().occupied().getFirst().player()), s17) == 250,
			"one click places the typed 250 on 17");
		// Esc closes the strip but not the screen
		f.key(KeyMap.E);
		f.waitChrome(c -> c.entry.isOpen(), "E opens again");
		f.key(KeyMap.ESCAPE);
		f.context().waitTicks(3);
		boolean screenOpen = f.client(mc -> mc.gui.screen() instanceof RouletteScreen);
		check(screenOpen, "roulette: Esc on the open strip closed the whole screen");
		if (!screenOpen) return;
		check(!chrome(f, c -> c.entry.isOpen()), "roulette: Esc should close the strip");
		// wheel over the readout opens the strip and steps
		f.key(KeyMap.E);
		f.waitChrome(c -> c.entry.isOpen() && f.box(BetEntry.FIELD).isPresent(), "E opens for the wheel");
		long v0 = chrome(f, c -> c.entry.value());
		f.hover(BetEntry.FIELD);
		f.context().getInput().scroll(1);
		f.context().waitTicks(2);
		long v1 = chrome(f, c -> c.entry.value());
		check(v1 > v0, "roulette: wheel up over the field should raise " + v0 + ", got " + v1);
		f.context().getInput().scroll(-1);
		f.context().waitTicks(2);
		long v2 = chrome(f, c -> c.entry.value());
		check(v2 < v1, "roulette: wheel down should lower " + v1 + ", got " + v2);
		// overflow typed: 99 999 999 999 clamps to the room under the maximum, status says why
		f.key(KeyMap.BACKSPACE);
		for (int i = 0; i < 6; i++) f.key(KeyMap.BACKSPACE);
		for (char ch : "99999999999".toCharArray()) f.key(String.valueOf(ch));
		f.key(KeyMap.ENTER);
		f.waitChrome(c -> !c.entry.isOpen(), "Enter closes");
		long clamped = chrome(f, c -> c.bet.unit());
		String status = f.status();
		check(clamped > 0 && (max <= 0 || clamped <= max - 250), "roulette: overflow unit clamped to the room under max " + max + ", got " + clamped);
		check(!status.isEmpty(), "roulette: the clamp should say whose cap in the status");
		f.click(RouletteLayout.spotId(Spot.of(BetType.STRAIGHT, 20)));
		f.context().waitTicks(10);
		long total = f.server(s -> s.overworld().getBlockEntity(pos) instanceof RouletteTableBlockEntity be
			? Bets.totalStaked(be.betsOf(be.seats().occupied().getFirst().player())) : -1L);
		check(max <= 0 || total <= max, "roulette: server total " + total + " must stay within " + max);
		f.click("tool.clear");
		f.close();
	}

	// ---- craps: unit ---------------------------------------------------------------------------------------------------

	private void craps(RealFlow f, UUID me) {
		BlockPos pos = fresh(f, CrapsModule.TABLE.block());
		f.open(pos, CrapsScreen.class);
		f.waitChrome(c -> "sit".equals(c.primary.gate().action()) && c.primary.gate().enabled(), "Sit offered");
		f.click("primary");
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.seats().occupied().size() == 1, "seated");
		f.waitChrome(c -> "roll".equals(c.primary.gate().action()), "Roll shown");
		typeAndEnter(f, "75");
		check(chrome(f, c -> c.bet.unit()) == 75, "craps: unit 75");
		f.click(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().betsOf(me).stream().anyMatch(b -> b.flat() == 75), "Pass 75 on the server");
		f.close();
	}

	// ---- baccarat: unit, ½ / ×2 / Max / OK by pixel --------------------------------------------------------------------

	private void baccarat(RealFlow f, UUID me) {
		BlockPos pos = fresh(f, BaccaratModule.TABLE.block());
		f.open(pos, BaccaratScreen.class);
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
		f.waitChrome(c -> c.bet.visible() && c.bet.view().canBet(), "bets open");
		typeAndEnter(f, "300");
		check(chrome(f, c -> c.bet.unit()) == 300, "baccarat: unit 300");
		f.click(KeyMap.Ids.felt("box.banker"));
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(dev.nezo.burmaldaholic.games.baccarat.logic.BetKind.BANKER, 0L) == 300,
			"banker 300 by one click");
		f.key(KeyMap.E);
		f.waitChrome(c -> c.entry.isOpen() && f.box(BetEntry.DOUBLE).isPresent(), "E opens");
		check(chrome(f, c -> c.entry.value()) == 300, "baccarat: the strip starts at the unit 300, got " + chrome(f, c -> c.entry.value()));
		f.click(BetEntry.DOUBLE);
		check(chrome(f, c -> c.entry.value()) == 600, "baccarat: x2 -> 600, got " + chrome(f, c -> c.entry.value()));
		f.click(BetEntry.HALF);
		f.click(BetEntry.HALF);
		check(chrome(f, c -> c.entry.value()) == 150, "baccarat: two halves -> 150, got " + chrome(f, c -> c.entry.value()));
		f.click(BetEntry.MAX);
		long mx = chrome(f, c -> c.entry.value());
		long room = chrome(f, c -> c.bet.view().roomUnderMax());
		check(mx == Math.min(room, chrome(f, c -> c.bet.view().balance())), "baccarat: Max = room under max " + room + ", got " + mx);
		check(!f.status().isEmpty(), "baccarat: Max says whose cap");
		f.click(BetEntry.HALF);
		f.click(BetEntry.OK);
		long unit = chrome(f, c -> c.bet.unit());
		f.click(KeyMap.Ids.felt("box.player"));
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(dev.nezo.burmaldaholic.games.baccarat.logic.BetKind.PLAYER, 0L) == unit,
			"player " + unit + " by one click");
		f.click(KeyMap.Ids.tool("clear"));
		f.close();
	}

	// ---- UTH: the amount is the Ante ---------------------------------------------------------------------------------

	private void uth(RealFlow f, UUID me) {
		BlockPos pos = fresh(f, UthModule.TABLE.block());
		f.open(pos, UthScreen.class);
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, UthTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
		f.waitChrome(c -> c.bet.visible() && c.bet.view().canBet(), "bets open");
		typeAndEnter(f, "40");
		long cost = chrome(f, c -> c.bet.view().myTotal());
		check(cost == 80, "uth: typed Ante 40 = cost 80 (Ante + Blind), got " + cost);
		// reopening must show the Ante (40), not the cost (80): OK must not silently double the bet
		f.key(KeyMap.E);
		f.waitChrome(c -> c.entry.isOpen(), "E opens again");
		long shown = chrome(f, c -> c.entry.value());
		check(shown == 40, "uth: the strip reopens with the Ante 40, shows " + shown);
		f.key(KeyMap.ENTER);
		f.waitChrome(c -> !c.entry.isOpen(), "Enter");
		long after = chrome(f, c -> c.bet.view().myTotal());
		check(after == 80, "uth: open + Enter without typing must not change the bet (cost 80), got " + after);
		// Max: the Ante the player can afford with the Blind (cost <= balance)
		f.key(KeyMap.E);
		f.waitChrome(c -> c.entry.isOpen() && f.box(BetEntry.MAX).isPresent(), "E opens for Max");
		f.click(BetEntry.MAX);
		f.click(BetEntry.OK);
		long maxCost = chrome(f, c -> c.bet.view().myTotal());
		long bal = chrome(f, c -> c.bet.view().balance());
		check(maxCost <= bal, "uth: Max must leave an affordable cost; cost " + maxCost + " > balance " + bal);
		f.key(KeyMap.E);
		for (int i = 0; i < 12; i++) f.key(KeyMap.BACKSPACE);
		f.key("4");
		f.key("0");
		f.key(KeyMap.ENTER);
		f.waitChrome(c -> !c.entry.isOpen(), "Enter");
		f.click(KeyMap.Ids.PRIMARY); // Deal
		f.waitServer(pos, UthTableBlockEntity.class, t -> t.stakeOf(me) == 80, "server took Ante 40 + Blind 40");
		f.close();
	}

	// ---- Plinko (extras): the amount is the bet ------------------------------------------------------------------------

	private void plinko(RealFlow f) {
		BlockPos pos = f.place(ExtrasModule.PLINKO.block(), 3);
		f.context().waitTicks(3);
		f.use(pos);
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder, RealFlow.TIMEOUT);
		f.context().waitTicks(8);
		f.waitChrome(c -> c.bet.visible(), "Plinko bet bar");
		typeAndEnter(f, "30");
		check(chrome(f, c -> c.bet.view().myTotal()) == 30, "plinko: bet 30, got " + chrome(f, c -> c.bet.view().myTotal()));
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, PlinkoBlockEntity.class, be -> be.plinkoSync() != null, "the drop is published");
		long stake = f.server(s -> ((PlinkoBlockEntity) s.overworld().getBlockEntity(pos)).writeClientState(RealFlow.player(s))
			.getCompoundOrEmpty("result").getLongOr("stake", -1));
		check(stake == 30, "plinko: server stake 30, got " + stake);
		f.context().waitTicks(60);
		f.close();
	}
}
