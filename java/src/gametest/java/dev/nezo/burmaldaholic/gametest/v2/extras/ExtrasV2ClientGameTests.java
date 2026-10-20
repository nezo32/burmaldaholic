package dev.nezo.burmaldaholic.gametest.v2.extras;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Lane E REAL-FLOW client GameTests of the v2 extras screens (docs/architecture/ui-v2.md §11.2): each game is opened the
 * way a player does (USE the Plinko / Wheel machine block; right-click the Lucky Coin, the Dice, a Scratch Card), played
 * by clicking the kit's widgets by HitMap id at the real window pixel (chips, tools, toggles, the primary), and the
 * SERVER is asserted (the stake taken, the round settled: the balance moved by exactly the server's net). At 480 × 270
 * (1920 × 1080 @4) in English and Russian, then 640 × 360 (1920 × 1080 @3 and 2560 × 1440 @4). Negative paths: the
 * primary with no bet shows the reason and sends nothing; chips above the maximum say why. Screenshots
 * {@code p2b_e2_<game>_<state>_<lang>_<w>}. The in-world machines run in {@link ExtrasBoardClientGameTests}.
 */
public class ExtrasV2ClientGameTests implements FabricClientGameTest {
	private static final long START = 5000;
	private final List<String> problems = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("E")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			RealFlow f = new RealFlow(context, world);
			context.waitTicks(20);
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			// the PHASE2B §0.7 standard set (EN + RU at 480 and 640, EN elsewhere). Plinko and Dice run at 480 / 640 only: their
			// screens moved to packages PL and DCb (frozen for E2), these are regression checks of the E2 behaviour
			String[][] runs = {{"en_us", "1920", "1080", "4"}, {"ru_ru", "1920", "1080", "4"}, {"en_us", "1920", "1080", "3"}, {"ru_ru", "1920", "1080", "3"},
				{"en_us", "1280", "720", "3"}, {"en_us", "1920", "1080", "2"}, {"en_us", "2560", "1440", "2"}};
			for (String[] r : runs) {
				if (!r[0].equals(f.client(mc -> mc.options.languageCode))) f.language(r[0]);
				f.size(Integer.parseInt(r[1]), Integer.parseInt(r[2]), Integer.parseInt(r[3]));
				String tag = r[0] + "_" + f.viewport().w() + "x" + f.viewport().h();
				boolean core = r[3].equals("4") || r[3].equals("3") && r[1].equals("1920");
				if (core) run(f, tag, "plinko", () -> plinko(f, tag));
				run(f, tag, "wheel", () -> wheel(f, tag));
				run(f, tag, "coin", () -> coin(f, tag));
				run(f, tag, "scratch", () -> scratch(f, tag));
				if (core) run(f, tag, "dice", () -> dice(f, tag));
			}
			run(f, "en_us_two", "plinko_two_machines", () -> plinkoTwoMachines(f));
			f.language("en_us");
			f.size(854, 480, 0);
			f.close();
		}
		if (!problems.isEmpty()) throw new AssertionError("extras v2 real-flow problems:\n - " + String.join("\n - ", problems));
		// the in-world machines (upright Plinko board, wheel plate) in their own world
		new ExtrasBoardClientGameTests().runTest(context);
	}

	private interface Body {
		void run();
	}

	private void run(RealFlow f, String tag, String name, Body body) {
		try {
			setBalance(f, START);
			body.run();
			System.out.println("EXTRAS_V2 ok " + tag + " " + name);
		} catch (RuntimeException | AssertionError e) {
			problems.add(tag + " " + name + ": " + e.getMessage());
			f.context().takeScreenshot("v2e_fail_" + tag + "_" + name);
		} finally {
			f.context().runOnClient(mc -> CelebrationOverlay.get().clear());
			f.close();
		}
	}

	/** 2b standard name {@code p2b_e2_<game>_<state>_<lang>_<w>} from the run tag {@code <lang>_<w>x<h>}. */
	static String name(String tag, String what) {
		String lang = tag.substring(0, 2);
		String w = tag.substring(tag.indexOf('_', 3) + 1).split("x")[0];
		return "p2b_e2_" + what + "_" + lang + "_" + w;
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static void setBalance(RealFlow f, long amount) {
		f.server(s -> {
			ServerPlayer p = RealFlow.player(s);
			Economies.get().setBalance(s, p.getUUID(), amount, dev.nezo.burmaldaholic.core.economy.Economy.Transaction.of("core", "gametest"));
			return null;
		});
		f.context().waitTicks(2);
	}

	private static long balance(RealFlow f) {
		return f.server(s -> Economies.get().balance(RealFlow.player(s)));
	}

	/** Opens a machine by USING its block and waits for the v2 screen. */
	private static BlockPos machine(RealFlow f, net.minecraft.world.level.block.Block block) {
		BlockPos pos = f.place(block);
		f.context().waitTicks(3);
		f.use(pos);
		waitV2(f, "the machine's v2 screen opens");
		return pos;
	}

	/** Puts {@code item} in the first hotbar slot and uses it (right click in the air). */
	private static void useItem(RealFlow f, Item item, int count) {
		f.server(s -> {
			ServerPlayer p = RealFlow.player(s);
			p.getInventory().setItem(0, new ItemStack(item, count));
			return null;
		});
		f.context().waitTicks(4);
		f.context().runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		});
		waitV2(f, "the item's v2 screen opens");
	}

	private static void waitV2(RealFlow f, String what) {
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder, RealFlow.TIMEOUT);
		f.context().waitTicks(8);
		if (!f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder)) throw new AssertionError(what);
	}

	/** Waits until the server balance is {@code pred}. */
	private static long waitBalance(RealFlow f, java.util.function.LongPredicate pred, String what) {
		for (int t = 0; t <= RealFlow.TIMEOUT; t += 2) {
			long b = balance(f);
			if (pred.test(b)) return b;
			f.context().waitTicks(2);
		}
		throw new AssertionError("timed out waiting for the balance: " + what + " (now " + balance(f) + ")");
	}

	private static boolean primaryEnabled(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().primary.gate().enabled());
	}

	private static String primaryReason(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().primary.gate().reasonKey() != null
			? h.chrome().primary.gate().reasonKey() : "");
	}

	/** Clicks the kit's Clear, then the primary: nothing may reach the server, the status must say why. */
	private static void noBetRefused(RealFlow f, String tag, String game) {
		f.click(KeyMap.Ids.tool("clear"));
		long before = balance(f);
		if (primaryEnabled(f)) throw new AssertionError(game + ": primary enabled with no bet");
		f.click(KeyMap.Ids.PRIMARY);
		f.context().waitTicks(10);
		if (balance(f) != before) throw new AssertionError(game + ": a click without a bet reached the server");
		if (f.status().isEmpty()) throw new AssertionError(game + ": the disabled primary shows no reason");
		f.shot(name(tag, game + "_nobet"));
	}

	/** chip.k by index of the tray (0 = smallest). */
	private static void chips(RealFlow f, int... indexes) {
		for (int i : indexes) f.click(KeyMap.Ids.chip(i));
	}

	// ---- the games -------------------------------------------------------------------------------------------------

	private void plinko(RealFlow f, String tag) {
		BlockPos pos = machine(f, ExtrasModule.PLINKO.block());
		f.shot(name(tag, "plinko_open"));
		noBetRefused(f, tag, "plinko");
		f.click(KeyMap.Ids.felt("risk.high"));
		chips(f, 1, 1); // 5 + 5
		f.shot(name(tag, "plinko_bet"));
		long before = balance(f);
		f.click(KeyMap.Ids.PRIMARY); // Drop
		f.waitServer(pos, PlinkoBlockEntity.class, be -> be.plinkoSync() != null, "the drop is published");
		f.context().waitTicks(14);
		f.shot(name(tag, "plinko_drop"));
		long net = f.server(s -> {
			var be = (PlinkoBlockEntity) s.overworld().getBlockEntity(pos);
			return be.writeClientState(RealFlow.player(s)).getCompoundOrEmpty("result").getLongOr("net", Long.MIN_VALUE);
		});
		if (net == Long.MIN_VALUE) throw new AssertionError("no plinko result on the server");
		waitBalance(f, b -> b == before + net, "plinko settles " + net);
		f.context().waitTicks(60);
		f.shot(name(tag, "plinko_landed"));
		if (!primaryEnabled(f)) throw new AssertionError("Drop not enabled again after the landing: " + primaryReason(f));
	}

	private void wheel(RealFlow f, String tag) {
		BlockPos pos = machine(f, ExtrasModule.WHEEL.block());
		noBetRefused(f, tag, "wheel");
		chips(f, 2); // 25
		f.shot(name(tag, "wheel_bet"));
		long before = balance(f);
		f.key(KeyMap.SPACE); // keyboard parity: Space = Spin
		f.waitServer(pos, WheelBlockEntity.class, be -> be.wheelSync() != null, "the spin is published");
		f.context().waitTicks(30);
		f.shot(name(tag, "wheel_spin"));
		long net = f.server(s -> ((WheelBlockEntity) s.overworld().getBlockEntity(pos)).writeClientState(RealFlow.player(s)).getCompoundOrEmpty("result")
			.getLongOr("net", Long.MIN_VALUE));
		waitBalance(f, b -> b == before + net, "wheel settles " + net);
		f.context().waitTicks(70);
		f.shot(name(tag, "wheel_stop"));
	}

	private void coin(RealFlow f, String tag) {
		useItem(f, ExtrasModule.LUCKY_COIN, 1);
		f.shot(name(tag, "coin_open"));
		noBetRefused(f, tag, "coin");
		f.click(KeyMap.Ids.felt("tails"));
		chips(f, 2);
		long before = balance(f);
		f.click(KeyMap.Ids.PRIMARY); // Flip
		long after = waitBalance(f, b -> b != before, "the flip settles");
		if (after != before - 25 && after <= before) throw new AssertionError("coin: unexpected balance " + before + " → " + after);
		f.context().waitTicks(12);
		f.shot(name(tag, "coin_air"));
		f.context().waitTicks(40);
		f.shot(name(tag, "coin_landed"));
	}

	private void scratch(RealFlow f, String tag) {
		useItem(f, ExtrasModule.SCRATCH_CARD, 2);
		f.shot(name(tag, "scratch_open"));
		f.click(KeyMap.Ids.felt("cell.0")); // a click auto-swipes one cell (the server draws the card)
		f.context().waitTicks(20);
		f.click(KeyMap.Ids.PRIMARY); // Scratch all
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().primary.gate().action().equals("new")
			|| !(mc.gui.screen() instanceof V2Chrome.Holder), RealFlow.TIMEOUT);
		f.context().waitTicks(10);
		f.shot(name(tag, "scratch_done"));
		boolean used = f.server(s -> RealFlow.player(s).getInventory().countItem(ExtrasModule.SCRATCH_CARD_USED) >= 1
			|| RealFlow.player(s).getInventory().countItem(ExtrasModule.SCRATCH_CARD) <= 1);
		if (!used) throw new AssertionError("the scratched ticket was not consumed on the server");
	}

	private void dice(RealFlow f, String tag) {
		useItem(f, ExtrasModule.DICE, 1);
		noBetRefused(f, tag, "dice");
		chips(f, 2);
		long before = balance(f);
		f.click(KeyMap.Ids.PRIMARY); // Roll
		f.context().waitTicks(30);
		f.shot(name(tag, "dice_roll"));
		// the result is revealed (the banner carries the server's net, a tie included)
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().banner.summary() != null, 400);
		long net = f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().banner.net());
		long after = balance(f);
		if (after != before + net) throw new AssertionError("dice: the banner says " + net + " but the balance moved " + (after - before));
		f.shot(name(tag, "dice_result"));
	}

	/** Reads a field of the open screen (the screens are package-private; tests read their local state). */
	private static Object screenField(RealFlow f, String field) {
		return f.client(mc -> {
			Object s = mc.gui.screen();
			for (Class<?> k = s.getClass(); k != null; k = k.getSuperclass()) {
				try {
					java.lang.reflect.Field fl = k.getDeclaredField(field);
					fl.setAccessible(true);
					return fl.get(s);
				} catch (NoSuchFieldException e) {
					// up the hierarchy
				} catch (IllegalAccessException e) {
					throw new AssertionError(e);
				}
			}
			throw new AssertionError("no field " + field + " on " + s.getClass().getSimpleName());
		});
	}

	private static long localBet(RealFlow f) {
		return ((dev.nezo.burmaldaholic.games.extras.present.LocalBet) screenField(f, "bet")).amount();
	}

	/**
	 * R-CDE m5: two Plinko machines keep their own bet (the bet was one static for every machine); the hotfix's per-risk
	 * minimum shows as the Risk toggle's tooltip (real hover).
	 */
	private void plinkoTwoMachines(RealFlow f) {
		f.size(1920, 1080, 4);
		BlockPos a = f.place(ExtrasModule.PLINKO.block(), -2);
		BlockPos b = f.place(ExtrasModule.PLINKO.block(), 2);
		f.context().waitTicks(3);
		f.use(a);
		waitV2(f, "machine A opens");
		f.click(KeyMap.Ids.tool("clear"));
		chips(f, 1, 1); // 5 + 5 (the machine's tray at 1 – 20: chips 1 and 5)
		long betA = localBet(f);
		f.hover(KeyMap.Ids.felt("risk.high"));
		f.context().waitTicks(3);
		Object tip = screenField(f, "riskTip");
		if (!(tip instanceof net.minecraft.network.chat.Component c) || !c.getString().matches(".*[0-9].*")) {
			throw new AssertionError("no per-risk minimum tooltip on the High toggle: " + tip);
		}
		f.context().takeScreenshot("p2b_e2_plinko_risk_tooltip_en_480");
		f.close();
		f.use(b);
		waitV2(f, "machine B opens");
		f.click(KeyMap.Ids.tool("clear"));
		chips(f, 0); // 1
		long betB = localBet(f);
		if (betB == betA) throw new AssertionError("the chips on B gave the same bet as A (" + betA + ")");
		f.close();
		f.use(a);
		waitV2(f, "machine A reopens");
		if (localBet(f) != betA) throw new AssertionError("machine A reopened on " + localBet(f) + ", not its own bet " + betA + " (B has " + betB + ")");
		f.close();
		f.use(b);
		waitV2(f, "machine B reopens");
		if (localBet(f) != betB) throw new AssertionError("machine B reopened on " + localBet(f) + ", not its own bet " + betB);
	}
}
