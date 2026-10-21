package dev.nezo.burmaldaholic.gametest.slots;

import dev.nezo.burmaldaholic.client.ui.v2.Stepper;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotMachinesV2;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.client.SlotBody;
import dev.nezo.burmaldaholic.games.slots.client.SlotMachineV2Screen;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.panels.Overlays;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotLayout;
import dev.nezo.burmaldaholic.games.slots.client.pvp.ShowdownPanel;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFlow;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * T-SLT (owner B6 on slots): the slot screen on the v2 kit at 427×240, 480×270, 640×360 and 960×540. Per size: layout
 * asserts (every target inside the frame, no overlaps, the cabinet inside the PlayArea and clear of the Dock) and every
 * Dock control clicked at its REAL window pixel with the SERVER effect asserted: bet −/+ (the spin's server bet), Spin
 * (server round at that bet), Stop (skip during the spin), Turbo (server flag), Auto (overlay start → server autoplay,
 * primary Stop autoplay → ended), Buy (overlay confirm → bought server round), Showdown (panel). During every spin the
 * screen's balance never shows the payout before the reels stop and ends equal to the server's; the final window is the
 * server's. No screenshots. Lane {@code SLOTSB6}.
 */
public class SlotsDockClientGameTests implements FabricClientGameTest {
	private static final int[][] SIZES = {{854, 480, 2, 427, 240}, {960, 540, 2, 480, 270}, {1280, 720, 2, 640, 360}, {1920, 1080, 2, 960, 540}};
	private final List<String> failures = new ArrayList<>();
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("SLOTSB6") && !RealFlow.enabled("SlotsDockClientGameTests")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 1000000");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			RealFlow f = new RealFlow(context, world);
			BlockPos pos = f.place(SlotsModule.MACHINES.get(Tier.GOLD).block()); // Nether: has Buy
			f.server(server -> {
				if (server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be) SlotMachinesV2.cfg(be.machineV2()).minVipTier = 0;
				return null;
			});
			for (int i = 0; i < SIZES.length; i++) {
				int[] s = SIZES[i];
				String tag = s[3] + "x" + s[4];
				try {
					size(f, s, tag, pos, i == 0 || i == SIZES.length - 1);
				} catch (Throwable t) {
					failures.add(tag + ": " + t);
				}
				f.close();
			}
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = true);
		} finally {
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			report.forEach(l -> System.out.println("[T-SLT] " + l));
		}
		if (!failures.isEmpty()) throw new AssertionError("slots dock B6: " + String.join("; ", failures));
	}

	private void size(RealFlow f, int[] s, String tag, BlockPos pos, boolean buy) {
		ClientGameTestContext ctx = f.context();
		f.size(s[0], s[1], s[2]);
		f.open(pos, SlotMachineV2Screen.class);
		ctx.waitFor(mc -> body(mc) != null && body(mc).model().def != null && body(mc).layout() != null, RealFlow.TIMEOUT);
		ctx.waitTicks(10);
		// ---- layout ----
		List<String> lay = f.client(mc -> {
			List<String> out = new ArrayList<>();
			SlotMachineV2Screen sc = (SlotMachineV2Screen) mc.gui.screen();
			V2Chrome c = sc.chrome();
			Viewport vp = c.viewport();
			report.add(tag + ": fb " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " -> " + vp + " play " + c.grid().play);
			if (vp.frameW() != s[3] || vp.frameH() != s[4]) out.add("frame " + vp.frameW() + "x" + vp.frameH() + " (window clamped?)");
			Box screen = c.grid().screen;
			List<HitMap.Target> ts = c.hits().targets();
			for (HitMap.Target t : ts) if (!t.box().inside(screen)) out.add(t.id() + " " + t.box() + " outside the frame " + screen);
			for (int i = 0; i < ts.size(); i++) {
				for (int j = i + 1; j < ts.size(); j++) {
					if (overlap(ts.get(i).box(), ts.get(j).box())) out.add(ts.get(i).id() + " overlaps " + ts.get(j).id());
				}
			}
			for (String id : new String[] {KeyMap.Ids.PRIMARY, Stepper.MINUS, Stepper.PLUS}) {
				if (c.hits().box(id).isEmpty()) out.add("no target " + id);
			}
			// D3: the full ActionRow, or (narrow frames) More with the four actions in its menu
			boolean more = sc.dockMore();
			report.add(tag + ": dock " + (more ? "compact (More)" : "full"));
			for (String a : SECONDARY) {
				if (more ? c.hits().box(KeyMap.Ids.action(a)).isPresent() : c.hits().box(KeyMap.Ids.action(a)).isEmpty()) {
					out.add((more ? "folded action still in the Dock: " : "no target ") + KeyMap.Ids.action(a));
				}
			}
			if (more && c.hits().box(KeyMap.Ids.action(SlotFlow.MORE)).isEmpty()) out.add("compact Dock without More");
			SlotLayout l = sc.body().layout();
			Box cab = new Box(l.wx, l.wy, l.windowW(), l.windowH());
			if (!cab.inside(c.grid().play)) out.add("reel window " + cab + " outside the PlayArea " + c.grid().play);
			for (HitMap.Target t : ts) if (t.id().startsWith("action.") || t.id().startsWith("stepper.") || t.id().equals(KeyMap.Ids.PRIMARY)) {
				if (overlap(t.box(), cab)) out.add("reel window overlaps " + t.id());
			}
			if (l.cell < 32) out.add("cell " + l.cell + " < 32");
			return out;
		});
		lay.forEach(p -> failures.add(tag + " layout: " + p));
		if (f.client(mc -> ((SlotMachineV2Screen) mc.gui.screen()).dockMore())) menuLayout(f, tag);
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		// ---- bet stepper ----
		int i0 = f.client(mc -> body(mc).model().betIndex);
		int n = f.client(mc -> body(mc).model().bets.length);
		if (i0 >= n - 1) {
			f.click(Stepper.MINUS);
			i0 = f.client(mc -> body(mc).model().betIndex);
		}
		f.click(Stepper.PLUS);
		int i1 = f.client(mc -> body(mc).model().betIndex);
		if (i1 != i0 + 1) failures.add(tag + ": bet + moved " + i0 + " -> " + i1);
		f.click(Stepper.MINUS);
		int i2 = f.client(mc -> body(mc).model().betIndex);
		if (i2 != i0) failures.add(tag + ": bet − moved " + i1 + " -> " + i2);
		f.click(Stepper.PLUS);
		long bet = f.client(mc -> body(mc).model().bet());
		// ---- spin (natural) with balance hold + final window ----
		spin(f, pos, tag, bet, false);
		// ---- spin + Stop ----
		spin(f, pos, tag, bet, true);
		// ---- turbo ----
		act(f, SlotFlow.TURBO);
		f.waitServer(pos, SlotMachineBlockEntity.class, be -> Boolean.TRUE.equals(this.<Map<UUID, Boolean>>field(be, "turboV2").get(me)), tag + " turbo on");
		act(f, SlotFlow.TURBO);
		f.waitServer(pos, SlotMachineBlockEntity.class, be -> !Boolean.TRUE.equals(this.<Map<UUID, Boolean>>field(be, "turboV2").get(me)), tag + " turbo off");
		// ---- autoplay: Dock Auto → overlay (loss limit, Start) → server autoplay; primary Stop autoplay → ended ----
		act(f, SlotFlow.AUTO);
		ctx.waitTicks(3);
		if (f.client(mc -> body(mc).overlays().mode()) != Overlays.Mode.AUTO) failures.add(tag + ": Auto did not open the autoplay panel");
		else {
			clickOverlay(f, h -> h[4] >= 200 && h[4] < 300, tag + " loss limit");
			clickOverlay(f, h -> h[4] == Overlays.Action.AUTO_START.ordinal(), tag + " auto start");
			f.waitServer(pos, SlotMachineBlockEntity.class, be -> this.field(be, "autoV2") != null, tag + " server autoplay started");
			// during autoplay the primary is Stop Auto, and Space (the primary's key) stops the autoplay (review T-SLT)
			f.waitChrome(c -> SlotFlow.STOP_AUTO.equals(primaryAction(c)), tag + " primary Stop Auto during autoplay");
			f.key(KeyMap.SPACE);
			f.waitServer(pos, SlotMachineBlockEntity.class, be -> this.field(be, "autoV2") == null && !be.spinning(), tag + " server autoplay ended", 1200);
			ctx.waitFor(mc -> body(mc) != null && !body(mc).stage().spinning(), RealFlow.TIMEOUT);
			ctx.waitTicks(10);
		}
		// ---- buy: Dock Buy → overlay confirm → bought server round ----
		act(f, SlotFlow.BUY);
		ctx.waitTicks(3);
		if (f.client(mc -> body(mc).overlays().mode()) != Overlays.Mode.BUY) failures.add(tag + ": Buy did not open the buy panel");
		else if (buy) {
			long before = f.server(server -> Economies.get().balance(RealFlow.player(server)));
			clickOverlay(f, h -> h[4] == Overlays.Action.BUY_CONFIRM.ordinal(), tag + " buy confirm");
			f.waitServer(pos, SlotMachineBlockEntity.class, be -> be.roundTape() != null && be.roundTape().bought(), tag + " server bought round");
			long price = f.client(mc -> body(mc).model().buyPrice());
			long after = f.server(server -> Economies.get().balance(RealFlow.player(server)));
			if (before - after != price && f.server(server -> server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be && be.spinning())) {
				failures.add(tag + ": buy took " + (before - after) + " != price " + price);
			}
			// fast-forward the feature with the primary (Stop) until the server settles
			for (int t = 0; t < 3000 && f.server(server -> server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be && be.spinning()); t += 10) {
				if (f.enabledTarget(KeyMap.Ids.PRIMARY) && f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder hh && SlotFlow.SKIP.equals(primaryAction(hh.chrome())))) {
					try {
						f.click(KeyMap.Ids.PRIMARY);
					} catch (AssertionError ignored) {
						// a celebration took the click
					}
				}
				pickIfAsked(f);
				ctx.waitTicks(8);
			}
			ctx.waitFor(mc -> body(mc) != null && !body(mc).stage().spinning(), 1200);
			ctx.waitTicks(20);
		} else {
			ctx.runOnClient(mc -> body(mc).overlays().close());
			ctx.waitTicks(2);
		}
		// ---- Slot Showdown entry ----
		boolean sdEnabled = f.client(mc -> mc.gui.screen() instanceof SlotMachineV2Screen sc && sc.currentView() != null
			&& SlotFlow.secondary(sc.currentView()).stream().anyMatch(g -> g.action().equals(SlotFlow.SHOWDOWN) && g.enabled()));
		if (sdEnabled) {
			act(f, SlotFlow.SHOWDOWN);
			ctx.waitTicks(3);
			boolean open = f.client(mc -> this.<ShowdownPanel>field(mc.gui.screen(), "showdown").open());
			if (!open) failures.add(tag + ": Showdown did not open its panel");
		} else {
			failures.add(tag + ": Showdown entry disabled while idle");
		}
		report.add(tag + ": dock ok");
	}

	private static final String[] SECONDARY = {SlotFlow.BUY, SlotFlow.AUTO, SlotFlow.TURBO, SlotFlow.SHOWDOWN};

	/** Presses a secondary action like a player: its ActionRow button, or (compact Dock) More, then its menu entry. */
	private void act(RealFlow f, String action) {
		String id = KeyMap.Ids.action(action);
		if (f.box(id).isPresent()) {
			f.click(id);
			return;
		}
		f.click(KeyMap.Ids.action(SlotFlow.MORE));
		f.context().waitTicks(2);
		String entry = SlotMachineV2Screen.moreId(action);
		if (f.box(entry).isEmpty()) throw new AssertionError("More menu without " + entry);
		f.click(entry);
		f.context().waitTicks(2);
		if (f.client(mc -> ((SlotMachineV2Screen) mc.gui.screen()).moreOpen())) failures.add("More menu stayed open after " + entry);
	}

	/** D3: More opens a menu of the four actions, inside the frame, clear of each other and of the Dock controls; More closes it. */
	private void menuLayout(RealFlow f, String tag) {
		f.click(KeyMap.Ids.action(SlotFlow.MORE));
		f.context().waitTicks(2);
		List<String> out = f.client(mc -> {
			List<String> o = new ArrayList<>();
			SlotMachineV2Screen sc = (SlotMachineV2Screen) mc.gui.screen();
			V2Chrome c = sc.chrome();
			if (!sc.moreOpen()) o.add("More did not open its menu");
			Box screen = c.grid().screen;
			List<HitMap.Target> ts = c.hits().targets();
			for (String a : SECONDARY) {
				Box b = c.hits().box(SlotMachineV2Screen.moreId(a)).orElse(null);
				if (b == null) {
					o.add("menu without " + a);
					continue;
				}
				if (!b.inside(screen)) o.add("more." + a + " " + b + " outside the frame");
				if (b.y() + b.h() > c.grid().dock.y()) o.add("more." + a + " reaches into the Dock");
				for (HitMap.Target t : ts) {
					if (t.id().equals(SlotMachineV2Screen.moreId(a))) continue;
					boolean dockOrMenu = t.id().startsWith("more.") || t.id().startsWith("action.") || t.id().startsWith("stepper.") || t.id().equals(KeyMap.Ids.PRIMARY);
					if (dockOrMenu && overlap(b, t.box())) o.add("more." + a + " overlaps " + t.id());
				}
			}
			return o;
		});
		out.forEach(p -> failures.add(tag + " menu: " + p));
		f.click(KeyMap.Ids.action(SlotFlow.MORE));
		f.context().waitTicks(2);
		if (f.client(mc -> ((SlotMachineV2Screen) mc.gui.screen()).moreOpen())) failures.add(tag + ": More did not close its menu");
	}

	/** One spin through the real primary; asserts the server bet, the balance hold, the final window. */
	private void spin(RealFlow f, BlockPos pos, String tag, long bet, boolean stop) {
		ClientGameTestContext ctx = f.context();
		long b0 = f.server(server -> Economies.get().balance(RealFlow.player(server)));
		long shown0 = f.client(mc -> body(mc).model().balance);
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, SlotMachineBlockEntity.class, be -> be.roundTape() != null || !be.spinning(), tag + " server round");
		long serverBet = f.server(server -> server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be && be.roundTape() != null ? (Long) be.roundTape().bet() : (Long) (-1L));
		if (serverBet != -1 && serverBet != bet) failures.add(tag + ": server spun " + serverBet + " but the stepper shows " + bet);
		boolean stopped = false;
		boolean began = false;
		int ticks = 0;
		for (; ticks < 1200; ticks++) {
			Object[] s = f.client(mc -> {
				SlotBody b = body(mc);
				if (b == null) return null;
				SlotStage st = b.stage();
				return new Object[] {st.spinning(), st.finished(), b.model().balance, mc.gui.screen() instanceof V2Chrome.Holder h ? primaryAction(h.chrome()) : ""};
			});
			if (s == null) {
				failures.add(tag + ": screen closed during the spin");
				return;
			}
			boolean spinning = (boolean) s[0];
			began |= spinning;
			long shown = (long) s[2];
			if (spinning && shown != shown0 && shown != shown0 - bet) failures.add(tag + ": balance " + shown + " shown while the reels spin (start " + shown0 + ")");
			if (stop && !stopped && spinning && SlotFlow.SKIP.equals(s[3]) && ticks > 4) {
				f.click(KeyMap.Ids.PRIMARY);
				stopped = true;
			}
			if (began && (boolean) s[1] && !spinning) break; // finished is also true while idle before the click lands
			ctx.waitTick();
		}
		f.waitServer(pos, SlotMachineBlockEntity.class, be -> !be.spinning(), tag + " server settled", 600);
		ctx.waitTicks(10);
		long server = f.server(sv -> Economies.get().balance(RealFlow.player(sv)));
		long shown = f.client(mc -> body(mc).model().balance);
		if (shown != server) failures.add(tag + ": shown balance " + shown + " != server " + server + " after the reels stopped");
		int[] rest = f.server(sv -> sv.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be ? be.restCells() : null);
		int[] cells = f.client(mc -> body(mc).stage().frames() == null ? null : body(mc).stage().frames().snapshot().cells());
		if (rest != null && cells != null && !Arrays.equals(rest, cells)) failures.add(tag + ": final window off the server's");
		if (stop && !stopped) failures.add(tag + ": primary never offered Stop during the spin");
		report.add(tag + (stop ? " stop-spin " : " spin ") + ticks + " ticks, balance " + b0 + " -> " + server);
	}

	private void pickIfAsked(RealFlow f) {
		String pick = f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
			? h.chrome().hits().targets().stream().filter(t -> t.enabled() && t.id().startsWith("action.~pick")).map(HitMap.Target::id).findFirst().orElse(null)
			: null);
		if (pick != null) {
			try {
				f.click(pick);
			} catch (AssertionError ignored) {
				// moved
			}
		}
	}

	private static String primaryAction(V2Chrome c) {
		return c.primary.gate() == null ? "" : c.primary.gate().action();
	}

	private void clickOverlay(RealFlow f, java.util.function.Predicate<int[]> which, String what) {
		f.context().waitTicks(2);
		double[] p = f.client(mc -> {
			SlotMachineV2Screen sc = (SlotMachineV2Screen) mc.gui.screen();
			List<int[]> hits = this.field(sc.body().overlays(), "hits");
			Viewport vp = sc.chrome().viewport();
			for (int[] h : hits) if (which.test(h)) return new double[] {vp.windowX(h[0] + h[2] / 2.0), vp.windowY(h[1] + h[3] / 2.0)};
			return null;
		});
		if (p == null) throw new AssertionError("no overlay button for " + what);
		f.context().getInput().setCursorPos(p[0], p[1]);
		f.context().waitTick();
		f.context().getInput().pressMouse(dev.nezo.burmaldaholic.client.input.Inputs.MOUSE_LEFT);
		f.context().waitTicks(3);
	}

	private static SlotBody body(Minecraft mc) {
		return mc.gui.screen() instanceof SlotMachineV2Screen s ? s.body() : null;
	}

	private static boolean overlap(Box a, Box b) {
		return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
	}

	@SuppressWarnings("unchecked")
	private <T> T field(Object o, String name) {
		try {
			Class<?> c = o.getClass();
			while (c != null) {
				try {
					Field fl = c.getDeclaredField(name);
					fl.setAccessible(true);
					return (T) fl.get(o);
				} catch (NoSuchFieldException e) {
					c = c.getSuperclass();
				}
			}
			throw new AssertionError("no field " + name);
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}
}
