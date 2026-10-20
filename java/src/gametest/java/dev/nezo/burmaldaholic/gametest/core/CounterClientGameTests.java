package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.cashier.ClientStore;
import dev.nezo.burmaldaholic.client.cashier.CounterScreen;
import dev.nezo.burmaldaholic.client.cashier.CounterShell;
import dev.nezo.burmaldaholic.client.cashier.ExchangeTab;
import dev.nezo.burmaldaholic.client.cashier.StoreTab;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Lane EC (EC-1, EC-SPEC Tests "client"): the cashier counter opened by USING the block, driven with real input at real
 * window pixels — Shift+click on a quantity button sends ×64, and the keyboard path (← / → direction, Enter ×1,
 * Shift+Enter ×64, Ctrl+Enter All, A deposit all, 2 store tab, / search, Enter buy) — asserting SERVER state. Sizes:
 * 1920 × 1080 GUI 4 (480 × 270) and 1280 × 720 GUI 3 (427 × 240, the narrow store), EN and RU.
 */
public class CounterClientGameTests implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("EC") && !RealFlow.enabled("CounterClientGameTests")) return; // lane gate (BURMALDAHOLIC_CGT)
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			RealFlow f = new RealFlow(context, world);
			BlockPos pos = f.place(CoreContent.CASHIER.block());
			run(f, pos, "en_us", 1920, 1080, 4);
			run(f, pos, "en_us", 1280, 720, 3);
			f.language("ru_ru");
			run(f, pos, "ru_ru", 1920, 1080, 4);
			run(f, pos, "ru_ru", 1280, 720, 3); // T-EC1: RU at 427 x 240
			f.language("en_us");
			f.close();
		} finally {
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
		if (!failures.isEmpty()) throw new AssertionError("counter: " + failures);
	}

	private void run(RealFlow f, BlockPos pos, String lang, int w, int h, int gs) {
		String at = lang + " " + w + "x" + h + "@" + gs;
		ClientGameTestContext ctx = f.context();
		f.close();
		f.size(w, h, gs);
		f.server(s -> {
			RealFlow.player(s).getInventory().clearContent();
			return null;
		});
		f.world().getServer().runCommand("casino balance set @p 1000000");
		ctx.waitTicks(5);
		f.open(pos, CounterScreen.class);
		CounterFlow.awaitEnabled(ctx, "exchange.row.emerald.out.1");
		fits(f, at + " exchange");
		// Shift+click on x1 = x64
		CounterFlow.click(ctx, "exchange.row.emerald.out.1", true);
		awaitCount(f, Items.EMERALD, 64, at + " Shift+click x1 sends x64");
		ctx.waitTicks(6);
		// keyboard: the emerald row is selected (the click); -> = out, Enter x1, Shift+Enter x64
		CounterFlow.key(ctx, Inputs.KEY_RIGHT, false, false);
		CounterFlow.key(ctx, Inputs.KEY_RETURN, false, false);
		awaitCount(f, Items.EMERALD, 65, at + " Enter = x1");
		ctx.waitTicks(6);
		CounterFlow.key(ctx, Inputs.KEY_RETURN, true, false);
		awaitCount(f, Items.EMERALD, 129, at + " Shift+Enter = x64");
		ctx.waitTicks(6);
		// <- = in, Ctrl+Enter = All
		CounterFlow.key(ctx, Inputs.KEY_LEFT, false, false);
		check(f.client(mc -> mc.gui.screen() instanceof CounterShell.Holder hh && hh.shell().tab("exchange").map(t -> !((ExchangeTab) t).selectedOut()).orElse(false)),
			at + " <- selects the in direction");
		CounterFlow.key(ctx, Inputs.KEY_RETURN, false, true);
		awaitCount(f, Items.EMERALD, 0, at + " Ctrl+Enter hands in all");
		ctx.waitTicks(6);
		// withdraw 100 with the chip button, then A = deposit all
		CounterFlow.click(ctx, "exchange.chip.100");
		CounterFlow.click(ctx, "exchange.withdraw");
		awaitChips(f, true, at + " withdrew chips");
		ctx.waitTicks(6);
		CounterFlow.key(ctx, Inputs.KEY_A, false, false);
		awaitChips(f, false, at + " A deposits all");
		ctx.waitTicks(4);
		boolean wide = f.client(mc -> mc.gui.screen() instanceof CounterShell.Holder hh && !hh.shell().layout().narrow());
		check(!wide || f.client(mc -> mc.gui.screen() instanceof CounterShell.Holder hh
			&& hh.shell().tab("exchange").map(t -> ((ExchangeTab) t).trayHasMove()).orElse(false)), at + " the counting tray replays the deposit");
		if (wide) CounterFlow.click(ctx, "exchange.tray"); // click skips the flights
		// 2 = Store tab; / search "bread"; Enter buys x1; Shift+click x16 buys x64
		int v = f.client(mc -> ClientStore.version());
		CounterFlow.key(ctx, Inputs.KEY_2, false, false);
		check(CounterFlow.lastPressed(ctx).equals("tab.store"), at + " key 2 selects the store tab");
		ctx.waitFor(mc -> ClientStore.version() > v, RealFlow.TIMEOUT);
		CounterFlow.awaitEnabled(ctx, "store.tile.bread");
		CounterFlow.hover(ctx, "store.tile.bread");
		fits(f, at + " store");
		ctx.getInput().scroll(-1);
		ctx.waitTicks(3);
		check(f.client(mc -> store(mc) != null && store(mc).scrollRow() == 1), at + " mouse wheel scrolls the grid one row");
		check(CounterFlow.box(ctx, "store.tile.bread").isEmpty(), at + " the first row scrolled out of the grid");
		ctx.getInput().scroll(1);
		ctx.waitTicks(3);
		check(f.client(mc -> store(mc) != null && store(mc).scrollRow() == 0), at + " wheel up scrolls back");
		CounterFlow.key(ctx, Inputs.KEY_SLASH, false, false);
		check(f.client(mc -> store(mc) != null && store(mc).searchFocused()), at + " / focuses the search");
		String query = lang.equals("ru_ru") ? "хлеб" : "bread";
		ctx.getInput().typeChars(query);
		ctx.waitTicks(2);
		CounterFlow.key(ctx, Inputs.KEY_RETURN, false, false); // leaves the search
		check(f.client(mc -> store(mc) != null && store(mc).shown().size() == 1 && store(mc).shown().getFirst().id().equals("bread")),
			at + " search finds exactly the bread");
		CounterFlow.key(ctx, Inputs.KEY_RETURN, false, false);
		awaitCount(f, Items.BREAD, 1, at + " Enter buys x1");
		ctx.waitTicks(6);
		CounterFlow.awaitEnabled(ctx, "store.qty.16");
		CounterFlow.click(ctx, "store.qty.16", true);
		awaitCount(f, Items.BREAD, 65, at + " Shift+click x16 buys x64");
		check(f.client(mc -> store(mc) != null && store(mc).qty() == 1), at + " Shift+click does not change the selected quantity");
		ctx.waitTicks(6);
		// key 1 back to exchange, Esc closes
		CounterFlow.key(ctx, Inputs.KEY_1, false, false);
		check(CounterFlow.lastPressed(ctx).equals("tab.exchange"), at + " key 1 selects the exchange tab");
		ctx.getInput().pressKey(Inputs.KEY_ESCAPE);
		ctx.waitTicks(5);
		check(f.client(mc -> mc.gui.screen() == null), at + " Esc closes the counter");
	}

	private static StoreTab store(net.minecraft.client.Minecraft mc) {
		return mc.gui.screen() instanceof CounterShell.Holder h ? h.shell().tab("store").map(t -> (StoreTab) t).orElse(null) : null;
	}

	private void fits(RealFlow f, String at) {
		List<String> bad = f.client(mc -> mc.gui.screen() instanceof CounterShell.Holder hh ? CounterFitCheck.check(mc, hh.shell()) : List.of("no counter"));
		for (String b : bad) failures.add(at + " layout: " + b);
	}

	private void check(boolean ok, String what) {
		if (!ok) failures.add(what);
	}

	private void awaitCount(RealFlow f, Item item, int n, String what) {
		for (int t = 0; t <= 100; t += 2) {
			int have = f.server(s -> RealFlow.player(s).getInventory().countItem(item));
			if (have == n) return;
			f.context().waitTicks(2);
		}
		failures.add(what + " (have " + f.server(s -> RealFlow.player(s).getInventory().countItem(item)) + ", want " + n + ")");
	}

	private void awaitChips(RealFlow f, boolean some, String what) {
		for (int t = 0; t <= 100; t += 2) {
			boolean has = f.server(s -> {
				var inv = RealFlow.player(s).getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).getItem() instanceof dev.nezo.burmaldaholic.core.chips.ChipItem) return true;
				}
				return false;
			});
			if (has == some) return;
			f.context().waitTicks(2);
		}
		failures.add(what);
	}
}
