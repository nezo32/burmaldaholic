package dev.nezo.burmaldaholic.gametest.comps;

import dev.nezo.burmaldaholic.client.cashier.CounterShell;
import dev.nezo.burmaldaholic.client.comps.CompsClient;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.comps.CompsService;
import dev.nezo.burmaldaholic.comps.FreeBets;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import dev.nezo.burmaldaholic.vip.VipService;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * T-EC2 real flow: the player uses a cashier block, opens the Comps tab with a real click on the tab, redeems a
 * 10-chip token and claims the daily bonus by clicking their HitMap targets at real window pixels; every step is
 * asserted on the SERVER (points, token item, balance, claim day). Run at 1920×1080 GUI auto and 3840×2160 GUI auto.
 * Needs the EC-1 cashier wiring (cashier block → CounterScreen).
 */
public class CompsClientGameTests implements FabricClientGameTest {
	private static final Transaction TEST = Transaction.of("comps", "gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("CompsClientGameTests") && !RealFlow.enabled("COMPS")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			RealFlow f = new RealFlow(context, world);
			f.size(1920, 1080, 0);
			f.server(server -> {
				ServerPlayer p = RealFlow.player(server);
				Economies.get().setBalance(server, p.getUUID(), 0, TEST);
				VipService.setWagered(p, 1000);
				CompsService.put(p, CompsService.state(p).earn(200_000).active(CompsService.cfg().daily.minActiveMinutes * 1200L));
				return null;
			});
			BlockPos cashier = f.place(CoreContent.CASHIER);
			f.use(cashier);
			context.waitFor(mc -> mc.gui.screen() instanceof CounterShell.Holder, RealFlow.TIMEOUT);
			context.waitTicks(10);

			click(context, "tab.comps");
			waitClient(context, mc -> CompsClient.last() != null && CompsClient.last().pointsMilli() >= 200_000 && enabled("comps.redeem.token_10")
				&& enabled("comps.claim"), "the Comps tab shows 200 points, token_10 and Claim enabled");

			click(context, "comps.redeem.token_10");
			waitServer(f, server -> {
				ServerPlayer p = RealFlow.player(server);
				return tokens(p) == 1 && CompsService.state(p).pointsMilli() == 185_000;
			}, "redeeming token_10 gives one token and spends 15 points");

			f.size(3840, 2160, 0); // owner setup: 4K, GUI auto
			context.waitTicks(10);
			waitClient(context, mc -> enabled("comps.claim"), "Claim still enabled after the resize");
			click(context, "comps.claim");
			waitServer(f, server -> {
				ServerPlayer p = RealFlow.player(server);
				return Economies.get().balance(p) == 25 && CompsService.state(p).dailyClaimDay() == server.overworld().getGameTime() / 24000L;
			}, "claiming the daily bonus pays the Bronze 25 once");
			waitClient(context, mc -> CompsClient.last() != null && "CLAIMED".equals(CompsClient.last().daily()) && !enabled("comps.claim"),
				"the daily card switches to the countdown");
			context.waitTicks(5); // a later click on the (now disabled) target pays nothing
			long bal = f.server(server -> Economies.get().balance(RealFlow.player(server)));
			if (bal != 25) throw new AssertionError("daily bonus paid twice: " + bal);
			f.close();
		} finally {
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
	}

	private static int tokens(ServerPlayer p) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(FreeBets.TOKEN)) n += s.getCount();
		}
		return n;
	}

	private static boolean enabled(String id) {
		return CounterShell.current().map(s -> s.hits().targets().stream().anyMatch(t -> t.id().equals(id) && t.enabled())).orElse(false);
	}

	/** Left click at the window pixel of the centre of the counter target {@code id}; the shell must press that target. */
	private static void click(ClientGameTestContext context, String id) {
		double[] p = context.computeOnClient(mc -> {
			CounterShell s = CounterShell.current().orElseThrow(() -> new AssertionError("no counter screen"));
			Box b = s.hits().box(id).orElseThrow(() -> new AssertionError("no target '" + id + "'"));
			Viewport vp = s.viewport();
			return new double[] {vp.toWindow(b.x() + b.w() / 2.0), vp.toWindow(b.y() + b.h() / 2.0)};
		});
		context.getInput().setCursorPos(p[0], p[1]);
		context.waitTick();
		context.getInput().pressMouse(Inputs.MOUSE_LEFT);
		context.waitTicks(2);
		String pressed = context.computeOnClient(mc -> CounterShell.current().map(CounterShell::lastPressed).orElse(""));
		if (!pressed.equals(id)) throw new AssertionError("clicked '" + id + "' at " + p[0] + "," + p[1] + " but the counter pressed '" + pressed + "'");
	}

	private static void waitClient(ClientGameTestContext context, java.util.function.Predicate<net.minecraft.client.Minecraft> pred, String what) {
		for (int t = 0; t <= RealFlow.TIMEOUT; t += 2) {
			if (context.computeOnClient(pred::test)) return;
			context.waitTicks(2);
		}
		throw new AssertionError("timed out (client): " + what);
	}

	private static void waitServer(RealFlow f, Function<MinecraftServer, Boolean> pred, String what) {
		for (int t = 0; t <= RealFlow.TIMEOUT; t += 2) {
			if (f.server(pred)) return;
			f.context().waitTicks(2);
		}
		throw new AssertionError("timed out (server): " + what);
	}
}
