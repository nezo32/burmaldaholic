package dev.nezo.burmaldaholic.gametest.tables;

import dev.nezo.burmaldaholic.client.table.cards.CardTableScreen;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.client.UthScreen;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.realflow.RealFlow;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * v0.1.3 regression (owner report on v0.1.2: "BJ / UTH chips are drawn, clicking them does nothing; UTH Deal says place
 * a bet"): the chip rack clicked through the REAL input path at the window sizes real players have — the window really
 * resized to 3840 × 2160 / 2560 × 1440 / 1920 × 1080 and the GUI scale set in the options (Auto included), the cursor
 * placed in raw window pixels by {@code TestInput}, the vanilla {@code MouseHandler} scaling it to GUI coordinates.
 * The target is the chip rectangle the screen itself draws ({@link CardTableScreen#chipRackSlot} / the UTH rack),
 * mapped to window pixels the way vanilla maps back (window = gui × screenWidth / guiScaledWidth). A chip click must
 * add to the client draft; Deal must then put the bet on the server. Every click is also logged as the screen received it.
 *
 * <p>Root cause it guards: MC 26.3 (SDL3 input) renumbered mouse buttons (InputConstants LEFT 0 → 1, RIGHT 1 → 3); the
 * canvas hit tests compared {@code button} with the literals 0 / 1, so on 26.3 a left click on a chip was dropped
 * (blackjack) or read as a right click (UTH). Fails on 26.3 ({@code -Pmc=26.3}) until the screens resolve the button ids
 * at runtime. The press uses {@code InputConstants.MOUSE_BUTTON_LEFT} (inlined per compiled version, as {@code -Pmc} builds).
 */
public class RealInputClientGameTests implements FabricClientGameTest {
	/** {window w, h, GUI scale option (0 = Auto), tag}. */
	private static final Object[][] VIEWS = {
		{3840, 2160, 0, "2160p_auto"},
		{3840, 2160, 4, "2160p_gui4"},
		{1920, 1080, 0, "1080p_auto"},
		{1920, 1080, 3, "1080p_gui3"},
		{2560, 1440, 0, "1440p_auto"},
	};

	private final List<String> failures = new ArrayList<>();
	private final List<String> log = new ArrayList<>();
	private static final List<String> CLICKS = new ArrayList<>();
	private int tableNo;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create(); var q = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			for (Object[] v : VIEWS) {
				String tag = (String) v[3];
				context.runOnClient(mc -> mc.gui.setScreen(null));
				RealFlow.window(context, (int) v[0], (int) v[1], (int) v[2]);
				log.add(tag + ": window " + context.computeOnClient(RealInputClientGameTests::windowInfo));
				blackjack(context, world, tag);
				uth(context, world, tag);
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(20);
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			for (String l : log) System.out.println("[real-input] " + l);
			for (String f : failures) System.out.println("[real-input] FAIL " + f);
		}
		if (!failures.isEmpty()) throw new AssertionError("real input chip clicks: " + String.join("; ", failures));
	}

	private static String windowInfo(Minecraft mc) {
		var w = mc.getWindow();
		return "screen " + w.getScreenWidth() + "x" + w.getScreenHeight() + " fb " + w.getWidth() + "x" + w.getHeight() + " guiScale "
			+ w.getGuiScale() + " gui " + w.getGuiScaledWidth() + "x" + w.getGuiScaledHeight();
	}

	// ---- blackjack -----------------------------------------------------------------------------------------------------

	private void blackjack(ClientGameTestContext context, TestSingleplayerContext world, String tag) {
		String name = tag + " blackjack";
		BlockPos pos = open(context, world, BlackjackModule.TABLE.block(), BlackjackScreen.class, name);
		if (pos == null) return;
		if (!clickButton(context, name, "gui.burmaldaholic.blackjack.sit")) return;
		if (!awaitServer(context, world, name + ": seated", s -> table(s, pos).isSeated(RealFlow.player(s)))) return;
		context.waitTicks(10);
		long before = context.computeOnClient(mc -> mc.gui.screen() instanceof BlackjackScreen s ? (long) field(s, "pendingBet") : -1L);
		clickChip(context, name, 1); // the 5 chip
		long after = context.computeOnClient(mc -> mc.gui.screen() instanceof BlackjackScreen s ? (long) field(s, "pendingBet") : -1L);
		context.takeScreenshot("v013_" + tag + "_blackjack_after_chip");
		log.add(name + ": pendingBet " + before + " -> " + after);
		if (after != before + 5) {
			failures.add(name + ": chip 5 clicked through the real mouse, pendingBet " + before + " -> " + after + " (" + lastClick() + ")");
			closeAndClear(context, world, pos);
			return;
		}
		if (!clickButton(context, name, "gui.burmaldaholic.common.deal")) return;
		awaitServer(context, world, name + ": bet of 5 on the server",
			s -> table(s, pos).stakeOf(RealFlow.player(s).getUUID()) == 5 || table(s, pos).writeClientState(RealFlow.player(s)).getLongOr("last_bet", 0) == 5);
		closeAndClear(context, world, pos);
	}

	// ---- UTH ---------------------------------------------------------------------------------------------------------

	private void uth(ClientGameTestContext context, TestSingleplayerContext world, String tag) {
		String name = tag + " uth";
		BlockPos pos = open(context, world, UthModule.TABLE.block(), UthScreen.class, name);
		if (pos == null) return;
		if (!clickButton(context, name, "gui.burmaldaholic.poker.sit_down")) return;
		if (!awaitServer(context, world, name + ": seated", s -> table(s, pos).isSeated(RealFlow.player(s)))) return;
		context.waitTicks(10);
		long before = context.computeOnClient(mc -> mc.gui.screen() instanceof UthScreen s ? s.pendingForTests()[0] : -1L);
		clickChip(context, name, 1);
		long after = context.computeOnClient(mc -> mc.gui.screen() instanceof UthScreen s ? s.pendingForTests()[0] : -1L);
		context.takeScreenshot("v013_" + tag + "_uth_after_chip");
		log.add(name + ": pending ante " + before + " -> " + after);
		if (after <= before) {
			failures.add(name + ": chip 5 clicked through the real mouse, pending ante " + before + " -> " + after + " (" + lastClick() + ")");
			closeAndClear(context, world, pos);
			return;
		}
		if (!clickButton(context, name, "gui.burmaldaholic.common.deal")) return;
		awaitServer(context, world, name + ": ante on the server", s -> {
			var r = ((dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity) table(s, pos)).round();
			var seat = r == null ? null : r.seat(RealFlow.player(s).getUUID());
			return seat != null && seat.ante == after;
		});
		closeAndClear(context, world, pos);
	}

	// ---- flow helpers ------------------------------------------------------------------------------------------------

	private BlockPos open(ClientGameTestContext context, TestSingleplayerContext world, Block block, Class<? extends Screen> type, String name) {
		world.getServer().runCommand("casino balance set @p 50");
		BlockPos pos = RealFlow.place(world, block, (tableNo++ % 3) - 1, 2);
		context.waitTicks(5);
		RealFlow.useBlock(context, pos);
		try {
			RealFlow.awaitScreen(context, type, 200);
		} catch (AssertionError e) {
			failures.add(name + ": " + e.getMessage());
			return null;
		}
		context.runOnClient(mc -> {
			Screen s = mc.gui.screen();
			ScreenMouseEvents.allowMouseClick(s).register((sc, e) -> {
				synchronized (CLICKS) {
					CLICKS.add("screen got gui (" + e.x() + ", " + e.y() + ") btn " + e.button() + "; ox/oy/fk " + field(sc, "ox") + "/" + field(sc, "oy") + "/"
						+ field(sc, "fk") + " size " + sc.width + "x" + sc.height + " compact " + field(sc, "compact") + " canvas ("
						+ ((e.x() - (int) field(sc, "ox")) / (float) field(sc, "fk")) + ", " + ((e.y() - (int) field(sc, "oy")) / (float) field(sc, "fk"))
						+ "); mouseHandler raw (" + mc.mouseHandler.xpos() + ", " + mc.mouseHandler.ypos() + ")");
				}
				return true;
			});
		});
		context.waitTicks(20);
		return pos;
	}

	private void closeAndClear(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);
		world.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
		context.waitTicks(5);
	}

	private static String lastClick() {
		synchronized (CLICKS) {
			return CLICKS.isEmpty() ? "the screen got no click" : CLICKS.getLast();
		}
	}

	/** Raw window pixels of a canvas point: canvas → GUI (ox + x·fk) → window (the inverse of vanilla's xpos·guiW/screenW). */
	private static double[] canvasToWindow(Minecraft mc, Screen s, double cx, double cy) {
		double gx = (int) field(s, "ox") + cx * (float) field(s, "fk");
		double gy = (int) field(s, "oy") + cy * (float) field(s, "fk");
		var w = mc.getWindow();
		return new double[] {gx * w.getScreenWidth() / w.getGuiScaledWidth(), gy * w.getScreenHeight() / w.getGuiScaledHeight()};
	}

	private void clickChip(ClientGameTestContext context, String name, int index) {
		String[] info = new String[1];
		double[] at = context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof CardTableScreen s)) return null;
			int[] r = s instanceof UthScreen ? uthChip(s, index) : s.chipRackSlot(index);
			if (r == null) return null;
			double[] p = canvasToWindow(mc, s, r[0] + r[2] / 2.0, r[1] + r[3] / 2.0);
			info[0] = "chip " + index + " drawn canvas rect " + r[0] + "," + r[1] + " " + r[2] + "x" + r[3] + " -> window (" + p[0] + ", " + p[1] + ")"
				+ " guiScale*gui (" + (((int) field(s, "ox") + (r[0] + r[2] / 2.0) * (float) field(s, "fk")) * mc.getWindow().getGuiScale()) + ")";
			return p;
		});
		if (at == null) {
			failures.add(name + ": no chip rack on screen (" + RealFlow.describe(context) + ")");
			return;
		}
		log.add(name + ": " + info[0]);
		click(context, at[0], at[1]);
		log.add(name + ": " + lastClick());
	}

	/** The UTH rack as drawn (UthScreen#chipRect): chips at (8 + 25i, 211) full / (4 + 25i, 138) compact, 22 × 22. */
	private static int[] uthChip(CardTableScreen s, int i) {
		return s.compact() ? new int[] {4 + i * 25, 138, 22, 22} : new int[] {8 + i * 25, 211, 22, 22};
	}

	private boolean clickButton(ClientGameTestContext context, String name, String key) {
		RealFlow.WidgetRef w = RealFlow.widget(context, key);
		for (int i = 0; i < 50 && (w == null || !w.active()); i++) {
			context.waitTicks(2);
			w = RealFlow.widget(context, key);
		}
		if (w == null || !w.active()) {
			failures.add(name + ": no active " + key + " (" + RealFlow.describe(context) + ")");
			return false;
		}
		click(context, w.windowX(), w.windowY());
		log.add(name + ": clicked " + key + " -> " + lastClick());
		return true;
	}

	private static void click(ClientGameTestContext context, double x, double y) {
		context.getInput().setCursorPos(x, y);
		context.waitTicks(1);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private boolean awaitServer(ClientGameTestContext context, TestSingleplayerContext world, String what,
			java.util.function.Predicate<net.minecraft.server.MinecraftServer> cond) {
		try {
			RealFlow.awaitServer(context, world, what, cond, 200);
			return true;
		} catch (AssertionError e) {
			failures.add(e.getMessage() + " (" + RealFlow.describe(context) + ")");
			return false;
		}
	}

	private static CasinoTableBlockEntity table(net.minecraft.server.MinecraftServer server, BlockPos pos) {
		return (CasinoTableBlockEntity) server.overworld().getBlockEntity(pos);
	}

	private static Object field(Object o, String name) {
		for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
			try {
				Field f = k.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (NoSuchFieldException e) {
				// up
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalStateException("no field " + name + " on " + o.getClass());
	}
}
