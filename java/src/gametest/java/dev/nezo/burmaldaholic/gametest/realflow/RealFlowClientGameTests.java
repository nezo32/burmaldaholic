package dev.nezo.burmaldaholic.gametest.realflow;

import static dev.nezo.burmaldaholic.gametest.realflow.RealFlow.*;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * REAL-FLOW audit of every game outside the A1–A3 lanes (lane A4): each scenario opens the game the way a player does
 * (right-click the block with the use key, or use the item in hand), clicks the real widgets with the mouse and asserts
 * the SERVER outcome (a resolved play, the balance, an item). At the default 1920 × 1080 window with GUI scale Auto (4).
 *
 * <p>Scenarios marked {@code known} reproduce bugs that fail today; they run only with
 * {@code -Dburmaldaholic.realflow.known=true} (else they are listed as skipped). Known bugs reproduced here:
 * <ul>
 *   <li>{@code poker_bot_settings_gear}: the table-settings gear that BotsClientModule adds to every bot table screen
 *       is never drawn on the card tables (CardTableScreen#extractRenderState skips super) and its hit box goes through
 *       the canvas transform, which puts it outside the window (x ≈ 1968 of 1920 at GUI 4): table settings (bots,
 *       private table, invites) are unreachable from the poker / blackjack / baccarat screens (UTH has its own button).</li>
 *   <li>{@code slots_vip_lock_is_explained}: a machine above the player's VIP tier (Netherite) shows a greyed-out SPIN and
 *       no reason (SlotMachineV2Screen: {@code vip_ok=false} → {@code playable=false} only).</li>
 * </ul>
 * Also: the 1920 × 1080 GUI 4 / 3 screenshot tour ({@link ScreenTour}) with {@code -Dburmaldaholic.realflow.screens=true}.
 * Every result lands in {@code realflow_report.txt} in the run directory.
 */
public class RealFlowClientGameTests implements FabricClientGameTest {
	/** Game ids of every resolved play (CasinoEvents.PLAY_RESOLVED), in order. */
	static final List<String> PLAYS = new CopyOnWriteArrayList<>();
	private static boolean listening;

	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("RF") && !dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("hotfix") && !dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("RealFlowClientGameTests")) return; // lane gate (BURMALDAHOLIC_CGT)
		if (!listening) {
			listening = true;
			CasinoEvents.PLAY_RESOLVED.register((player, result) -> PLAYS.add(result.gameId()));
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(ctx).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(ctx, world)) {
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runOnServer(s -> CasinoConfig.chaos().enabled = false);
			window(ctx, 1920, 1080, 0);
			report.add("window: " + windowInfo(ctx));
			setBalance(world, 50_000);

			step("slots_vip_lock_is_explained", false, () -> slotsVipLock(ctx, world));
			// the Netherite machine needs a VIP tier (slotsV2.machines.*.minVipTier): play every machine as a Netherite VIP
			world.getServer().runCommand("casino vip wagered @p 3000000");
			for (Tier tier : Tier.values()) {
				step("slots_" + tier.name().toLowerCase(), false, () -> slots(ctx, world, tier));
			}
			world.getServer().runCommand("casino vip wagered @p 0");
			step("wheel_spin", false, () -> wheel(ctx, world));
			step("plinko_drop", false, () -> plinko(ctx, world));
			step("coin_flip_item", false, () -> coin(ctx, world));
			step("dice_item", false, () -> dice(ctx, world));
			step("scratch_item", false, () -> scratch(ctx, world));
			step("cashier_withdraw_deposit", false, () -> cashier(ctx, world));
			step("cashier_shop_scratch", false, () -> cashierShop(ctx, world));
			step("hud_balance_sync", false, () -> hud(ctx, world));
			step("hud_hidden_under_casino_screens", false, () -> hudHidden(ctx, world));
			step("casino_menu_key", false, () -> menuKey(ctx, world));
			step("poker_sit_down", false, () -> pokerSit(ctx, world));
			step("poker_bot_settings_gear", false, () -> pokerGear(ctx, world));
			closeScreen(ctx, world);
			if (SCREENS) report.addAll(ScreenTour.run(ctx, world));
		} finally {
			window(ctx, 1280, 720, 0);
			write(ctx);
		}
		if (!failures.isEmpty()) throw new AssertionError("real-flow failures: " + String.join(" | ", failures));
	}

	// ---- runner -----------------------------------------------------------------------------------------------------------

	private void step(String name, boolean known, Runnable body) {
		if (known && !KNOWN) {
			report.add("SKIP (known bug, -Dburmaldaholic.realflow.known=true) " + name);
			return;
		}
		try {
			body.run();
			report.add("PASS " + name);
		} catch (AssertionError | RuntimeException e) {
			String line = (known ? "FAIL (known) " : "FAIL ") + name + ": " + e.getMessage();
			report.add(line);
			failures.add(line);
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.error("[realflow] {}", line, e);
		}
	}

	private void write(ClientGameTestContext ctx) {
		Path dir = ctx.computeOnClient(mc -> mc.gameDirectory.toPath());
		try {
			Files.write(dir.resolve("realflow_report.txt"), report, StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			// best effort
		}
		report.forEach(l -> dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.info("[realflow] {}", l));
	}

	/** Waits until a play of {@code gameId} resolved after {@code from} plays. */
	private static void awaitPlay(ClientGameTestContext ctx, String gameId, int from) {
		awaitClient(ctx, "a resolved " + gameId + " play (seen: " + PLAYS.subList(from, PLAYS.size()) + ")",
			mc -> PLAYS.subList(from, PLAYS.size()).stream().anyMatch(g -> g.contains(gameId)), TIMEOUT);
	}

	// ---- slots -------------------------------------------------------------------------------------------------------------

	private void slots(ClientGameTestContext ctx, TestSingleplayerContext world, Tier tier) {
		closeScreen(ctx, world);
		BlockPos pos = place(world, SlotsModule.MACHINES.get(tier).block(), 0, 2);
		ctx.waitTicks(3);
		useBlock(ctx, pos);
		awaitScreen(ctx, dev.nezo.burmaldaholic.games.slots.client.SlotMachineV2Screen.class, 60);
		ctx.waitTicks(20); // the entrance and the first state
		long before = balance(world);
		int from = PLAYS.size();
		clickV2When(ctx, "primary"); // v2 kit (owner B6, SLT): Spin is the Dock primary
		awaitPlay(ctx, "slot", from);
		report.add("  slots " + tier + ": balance " + before + " -> " + balance(world));
		closeScreen(ctx, world);
		removeBlock(world, pos);
	}

	/**
	 * Fixed (hotfix A9): a machine above the player's VIP tier (Netherite by default) opens LOCKED: SPIN disabled, its
	 * caption "Locked", its message / tooltip and the line under the reels "Requires VIP tier X — you are Y".
	 */
	private void slotsVipLock(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		world.getServer().runCommand("casino vip wagered @p 0");
		BlockPos pos = place(world, SlotsModule.MACHINES.get(Tier.NETHERITE).block(), 0, 2);
		try {
			ctx.waitTicks(3);
			useBlock(ctx, pos);
			awaitScreen(ctx, dev.nezo.burmaldaholic.games.slots.client.SlotMachineV2Screen.class, 60);
			ctx.waitTicks(20);
			// v2 kit (owner B6, SLT): SPIN is the Dock primary; disabled, its reason is the VIP lock
			String gate = ctx.computeOnClient(mc -> mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h
				? (h.chrome().primary.gate().enabled() ? "enabled" : String.valueOf(h.chrome().primary.gate().reasonKey())) : "no v2 screen");
			if (!"gui.burmaldaholic.slots.vip_lock".equals(gate)) throw new AssertionError("SPIN on a machine above the player's VIP tier: " + gate);
			String reason = ctx.computeOnClient(mc -> {
				Object model = readField(mc.gui.screen(), "model");
				Object r = model == null ? null : readField(model, "lockReason");
				return r instanceof net.minecraft.network.chat.Component c && keyPrefix(c, "gui.burmaldaholic.slots.vip_lock") ? c.getString() : null;
			});
			if (reason == null) throw new AssertionError("SPIN is disabled (VIP tier too low) but the screen gives no reason");
			report.add("  slots VIP lock: " + reason);
			// clicking the locked SPIN plays nothing (its reason flashes in the status line)
			int from = PLAYS.size();
			double[] at = ctx.computeOnClient(mc -> mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h
				? h.chrome().hits().box("primary").map(b -> new double[] {h.chrome().viewport().toWindow(b.x() + b.w() / 2.0),
					h.chrome().viewport().toWindow(b.y() + b.h() / 2.0)}).orElse(null) : null);
			if (at == null) throw new AssertionError("no SPIN target: " + describe(ctx));
			clickWindow(ctx, at[0], at[1]);
			ctx.waitTicks(10);
			if (PLAYS.size() != from) throw new AssertionError("a locked machine played: " + PLAYS.subList(from, PLAYS.size()));
			if (SCREENS) ctx.takeScreenshot("realflow_slots_vip_lock");
		} finally {
			closeScreen(ctx, world);
			removeBlock(world, pos);
		}
	}

	static Object readField(Object o, String name) {
		for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
			try {
				var f = k.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (ReflectiveOperationException e) {
				// up
			}
		}
		return null;
	}

	// ---- extras machines -------------------------------------------------------------------------------------------------

	private void wheel(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		BlockPos pos = place(world, ExtrasModule.WHEEL.block(), 0, 2);
		ctx.waitTicks(3);
		useBlock(ctx, pos);
		awaitScreenNamed(ctx, "WheelScreen");
		ctx.waitTicks(15);
		int from = PLAYS.size();
		clickV2When(ctx, "primary"); // table UI v2 (lane E): Spin is the primary
		awaitPlay(ctx, ExtrasGames.WHEEL, from);
		closeScreen(ctx, world);
		removeBlock(world, pos);
	}

	private void plinko(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		BlockPos pos = place(world, ExtrasModule.PLINKO.block(), 0, 2);
		ctx.waitTicks(3);
		useBlock(ctx, pos);
		awaitScreenNamed(ctx, "PlinkoScreen");
		ctx.waitTicks(15);
		int from = PLAYS.size();
		clickV2When(ctx, "primary"); // v2: Drop is the primary
		awaitPlay(ctx, ExtrasGames.PLINKO, from);
		closeScreen(ctx, world);
		removeBlock(world, pos);
	}

	// ---- item games ------------------------------------------------------------------------------------------------------

	private void coin(ClientGameTestContext ctx, TestSingleplayerContext world) {
		useItem(ctx, world, new ItemStack(ExtrasModule.LUCKY_COIN));
		awaitScreenNamed(ctx, "CoinFlipScreen");
		ctx.waitTicks(15);
		clickV2When(ctx, "felt.heads"); // v2: the HEADS side of the coin card
		int from = PLAYS.size();
		clickV2When(ctx, "primary"); // Flip
		awaitPlay(ctx, ExtrasGames.COIN, from);
		closeScreen(ctx, world);
	}

	private void dice(ClientGameTestContext ctx, TestSingleplayerContext world) {
		useItem(ctx, world, new ItemStack(ExtrasModule.DICE));
		awaitScreenNamed(ctx, "DiceScreen");
		ctx.waitTicks(15);
		clickV2When(ctx, "chip.0"); // v2: the first tray chip
		int from = PLAYS.size();
		clickV2When(ctx, "primary"); // Roll
		awaitPlay(ctx, ExtrasGames.DICE, from);
		closeScreen(ctx, world);
	}

	private void scratch(ClientGameTestContext ctx, TestSingleplayerContext world) {
		useItem(ctx, world, new ItemStack(ExtrasModule.SCRATCH_CARD));
		awaitScreenNamed(ctx, "ScratchScreen");
		ctx.waitTicks(15);
		// scratch one cell by clicking the foil (the real input path), then the rest with "Scratch all"
		clickV2When(ctx, "felt.cell.0"); // v2: the first foil cell
		ctx.waitTicks(10);
		int from = PLAYS.size();
		clickV2When(ctx, "primary"); // Scratch all
		awaitPlay(ctx, ExtrasGames.SCRATCH, from);
		closeScreen(ctx, world);
	}

	// ---- economy -----------------------------------------------------------------------------------------------------------

	private void cashier(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		world.getServer().runOnServer(s -> player(s).getInventory().clearContent());
		BlockPos pos = place(world, CoreContent.CASHIER.block(), 0, 2);
		ctx.waitTicks(3);
		useBlock(ctx, pos);
		awaitScreen(ctx, dev.nezo.burmaldaholic.client.cashier.CashierScreen.class, 60);
		ctx.waitTicks(15);
		long before = balance(world);
		// type an amount into the box the way a player does, then Withdraw
		click(ctx, "amount box", w -> w instanceof net.minecraft.client.gui.components.EditBox);
		ctx.getInput().typeChars("250");
		ctx.waitTicks(2);
		click(ctx, "gui.burmaldaholic.cashier.withdraw");
		awaitServer(ctx, world, "balance down by 250 after Withdraw (was " + before + ")", s -> balanceOf(s) == before - 250, 60);
		long chips = world.getServer().computeOnServer(s -> carried(player(s)));
		if (chips != 250) throw new AssertionError("carried chips after withdrawing 250: " + chips);
		ctx.waitTicks(5);
		click(ctx, "gui.burmaldaholic.cashier.deposit_all");
		awaitServer(ctx, world, "balance back after Deposit all", s -> balanceOf(s) == before, 60);
		closeScreen(ctx, world);
		removeBlock(world, pos);
	}

	private void cashierShop(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		world.getServer().runOnServer(s -> player(s).getInventory().clearContent());
		BlockPos pos = place(world, CoreContent.CASHIER.block(), 0, 2);
		ctx.waitTicks(3);
		useBlock(ctx, pos);
		awaitScreen(ctx, dev.nezo.burmaldaholic.client.cashier.CashierScreen.class, 60);
		ctx.waitTicks(15);
		click(ctx, "gui.burmaldaholic.cashier.shop");
		ctx.waitTicks(5);
		click(ctx, "a scratch card offer", w -> hasKey(w.getMessage(), "gui.burmaldaholic.cashier.shop.buy_item")
			&& hasKey(w.getMessage(), ExtrasModule.SCRATCH_CARD.getDescriptionId()));
		awaitServer(ctx, world, "a scratch card in the inventory", s -> player(s).getInventory().countItem(ExtrasModule.SCRATCH_CARD) > 0, 60);
		closeScreen(ctx, world);
		removeBlock(world, pos);
	}

	private void hud(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		setBalance(world, 12_345);
		awaitClient(ctx, "the client balance (HUD) shows 12345, has " + ctx.computeOnClient(mc -> ClientCasinoState.balance()),
			mc -> ClientCasinoState.balance() == 12_345, 60);
		setBalance(world, 50_000);
	}

	private void menuKey(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		ctx.getInput().pressKey(RealFlow.keyMapping(ctx, "key.burmaldaholic.open_menu"));
		awaitScreenNamed(ctx, "CasinoMenuScreen");
		closeScreen(ctx, world);
	}

	// ---- poker -------------------------------------------------------------------------------------------------------------

	private BlockPos pokerTable(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		BlockPos pos = place(world, PokerModule.TABLE.block(), 0, 2);
		ctx.waitTicks(3);
		useBlock(ctx, pos);
		awaitScreen(ctx, dev.nezo.burmaldaholic.games.poker.client.PokerScreen.class, 60);
		ctx.waitTicks(20);
		return pos;
	}

	private void pokerSit(ClientGameTestContext ctx, TestSingleplayerContext world) {
		BlockPos pos = pokerTable(ctx, world);
		long before = balance(world);
		// table UI v2 (lane B): the stakes are action buttons "action.~pick:<level>", the primary is Buy in
		String pick = ctx.computeOnClient(mc -> mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h
			? h.chrome().hits().targets().stream().filter(t -> t.enabled() && t.id().startsWith("action.~pick:")).map(t -> t.id()).findFirst().orElse(null)
			: null);
		if (pick == null) throw new AssertionError("no open stakes level on the poker screen: " + describe(ctx));
		clickV2(ctx, pick);
		ctx.waitTicks(3);
		clickV2(ctx, "primary");
		awaitServer(ctx, world, "the buy-in debited after Buy in", s -> balanceOf(s) < before, 60);
		boolean seated = world.getServer().computeOnServer(s -> s.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity be
			&& be.isSeated(player(s)));
		report.add("  poker: seated(generic seats)=" + seated + ", balance " + before + " -> " + balance(world));
		awaitClient(ctx, "Stand up offered", mc -> v2Point(mc, "action.stand_up") != null, 100);
		clickV2(ctx, "action.stand_up");
		awaitServer(ctx, world, "the stack back after Stand up", s -> balanceOf(s) == before, 100);
		closeScreen(ctx, world);
		removeBlock(world, pos);
	}

	/**
	 * Fixed (hotfix A9): a bot table's settings gear is reachable on the card tables. Table UI v2: the TopBar gear
	 * ({@code top.settings}) of a bot table opens its Table settings. Checked at 1920 x 1080 and 2560 x 1440, GUI 2 / 3 / 4:
	 * find the gear, check it maps inside the window, click it where it is drawn and get the Table settings screen.
	 */
	private void pokerGear(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		BlockPos pos = place(world, PokerModule.TABLE.block(), 0, 2);
		int[][] sizes = {{1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}, {2560, 1440, 2}, {2560, 1440, 3}, {2560, 1440, 4}};
		try {
			for (int[] size : sizes) {
				closeScreen(ctx, world);
				window(ctx, size[0], size[1], size[2]);
				useBlock(ctx, pos);
				awaitScreen(ctx, dev.nezo.burmaldaholic.games.poker.client.PokerScreen.class, 60);
				ctx.waitTicks(20);
				String at = size[0] + "x" + size[1] + " GUI " + size[2];
				double[] gear = ctx.computeOnClient(mc -> v2Point(mc, "top.settings"));
				if (gear == null) throw new AssertionError(at + ": no TopBar gear: " + describe(ctx));
				int[] win = ctx.computeOnClient(mc -> new int[] {mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()});
				report.add("  poker gear " + at + " at window " + Math.round(gear[0]) + "," + Math.round(gear[1]) + " (window " + win[0] + "x" + win[1] + ")");
				if (gear[0] < 0 || gear[0] > win[0] || gear[1] < 0 || gear[1] > win[1]) {
					throw new AssertionError(at + ": the gear maps outside the window (" + Math.round(gear[0]) + ", " + Math.round(gear[1]) + ")");
				}
				if (SCREENS) ctx.takeScreenshot("realflow_poker_gear_" + size[0] + "_gui" + size[2]);
				clickWindow(ctx, gear[0], gear[1]);
				awaitScreen(ctx, dev.nezo.burmaldaholic.bots.client.TableSettingsScreen.class, 40);
			}
		} finally {
			closeScreen(ctx, world);
			removeBlock(world, pos);
			window(ctx, 1920, 1080, 0);
		}
	}

	/** Window pixel of the centre of v2 HitMap target {@code id} (enabled), or null. */
	private static double @org.jspecify.annotations.Nullable [] v2Point(net.minecraft.client.Minecraft mc, String id) {
		if (!(mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h)) return null;
		var chrome = h.chrome();
		for (var t : chrome.hits().targets()) {
			if (!t.id().equals(id) || !t.enabled()) continue;
			var vp = chrome.viewport();
			return new double[] {vp.toWindow(t.box().x() + t.box().w() / 2.0), vp.toWindow(t.box().y() + t.box().h() / 2.0)};
		}
		return null;
	}

	/** Waits until v2 target {@code id} is enabled, then clicks it. */
	private static void clickV2When(ClientGameTestContext ctx, String id) {
		awaitClient(ctx, "v2 target " + id, mc -> v2Point(mc, id) != null, 100);
		clickV2(ctx, id);
	}

	/** Clicks v2 target {@code id} where it is drawn (fails when it is missing or disabled). */
	private static void clickV2(ClientGameTestContext ctx, String id) {
		double[] at = ctx.computeOnClient(mc -> v2Point(mc, id));
		if (at == null) throw new AssertionError("no enabled v2 target '" + id + "': " + describe(ctx));
		clickWindow(ctx, at[0], at[1]);
	}

	/** Fixed (hotfix A9): the chip-counter HUD is not drawn while a casino screen is open (it peeked out top-left). */
	private void hudHidden(ClientGameTestContext ctx, TestSingleplayerContext world) {
		closeScreen(ctx, world);
		if (ctx.computeOnClient(mc -> dev.nezo.burmaldaholic.client.hud.CasinoHud.casinoScreenOpen(mc))) {
			throw new AssertionError("no screen open, yet the HUD counts as hidden");
		}
		ctx.getInput().pressKey(RealFlow.keyMapping(ctx, "key.burmaldaholic.open_menu"));
		awaitScreenNamed(ctx, "CasinoMenuScreen");
		if (!ctx.computeOnClient(mc -> dev.nezo.burmaldaholic.client.hud.CasinoHud.casinoScreenOpen(mc))) {
			throw new AssertionError("the casino HUD is still drawn under the casino menu");
		}
		closeScreen(ctx, world);
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------------

	private static long balanceOf(net.minecraft.server.MinecraftServer s) {
		return dev.nezo.burmaldaholic.core.economy.Economies.get().balance(player(s));
	}

	static boolean keyPrefix(net.minecraft.network.chat.Component c, String prefix) {
		if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().startsWith(prefix)) return true;
		for (net.minecraft.network.chat.Component s : c.getSiblings()) {
			if (keyPrefix(s, prefix)) return true;
		}
		return false;
	}

	static void removeBlock(TestSingleplayerContext world, BlockPos pos) {
		world.getServer().runOnServer(s -> s.overworld().removeBlock(pos, false));
	}

	static void awaitScreenNamed(ClientGameTestContext ctx, String simpleName) {
		try {
			awaitClient(ctx, "screen " + simpleName, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals(simpleName), 60);
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + " (open: " + screenName(ctx) + ", last hit: " + lastHit + ")");
		}
	}

	static long carried(net.minecraft.server.level.ServerPlayer p) {
		long sum = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack st = p.getInventory().getItem(i);
			sum += dev.nezo.burmaldaholic.core.chips.ChipItem.valueOf(st);
		}
		return sum;
	}
}
