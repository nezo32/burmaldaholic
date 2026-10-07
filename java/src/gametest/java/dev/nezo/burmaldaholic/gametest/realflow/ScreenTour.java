package dev.nezo.burmaldaholic.gametest.realflow;

import static dev.nezo.burmaldaholic.gametest.realflow.RealFlow.*;

import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * The 1920 × 1080 screenshot tour (lane A4; {@code -Dburmaldaholic.realflow.screens=true}): every game screen opened the
 * player's way at GUI scale 4 (Auto at 1080p: a 480 × 270 GUI) and 3 (640 × 360). Screenshots
 * {@code screenshots/realflow_gui<scale>_<screen>.png}; a layout report (widgets off screen, labels wider than their
 * button, overlapping widgets, click targets under 24 window pixels) is returned as lines.
 */
final class ScreenTour {
	private ScreenTour() {}

	static List<String> run(ClientGameTestContext ctx, TestSingleplayerContext world) {
		List<String> out = new ArrayList<>();
		Map<String, Runnable> screens = new LinkedHashMap<>();
		for (Tier t : Tier.values()) screens.put("slots_" + t.name().toLowerCase(), block(ctx, world, SlotsModule.MACHINES.get(t)));
		screens.put("wheel", block(ctx, world, ExtrasModule.WHEEL));
		screens.put("plinko", block(ctx, world, ExtrasModule.PLINKO));
		screens.put("coin", item(ctx, world, () -> new ItemStack(ExtrasModule.LUCKY_COIN)));
		screens.put("dice", item(ctx, world, () -> new ItemStack(ExtrasModule.DICE)));
		screens.put("scratch", item(ctx, world, () -> new ItemStack(ExtrasModule.SCRATCH_CARD)));
		screens.put("cashier", block(ctx, world, CoreContent.CASHIER));
		screens.put("poker", block(ctx, world, PokerModule.TABLE));
		screens.put("blackjack", block(ctx, world, BlackjackModule.TABLE));
		screens.put("roulette", block(ctx, world, RouletteModule.TABLE));
		screens.put("craps", block(ctx, world, CrapsModule.TABLE));
		screens.put("baccarat", block(ctx, world, BaccaratModule.TABLE));
		screens.put("uth", block(ctx, world, UthModule.TABLE));
		screens.put("casino_menu", () -> {
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(2);
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_B);
		});
		for (int scale : new int[] {4, 3}) {
			window(ctx, 1920, 1080, scale);
			out.add("== gui " + scale + ": " + windowInfo(ctx));
			for (Map.Entry<String, Runnable> e : screens.entrySet()) {
				try {
					e.getValue().run();
					awaitClient(ctx, "a screen for " + e.getKey(), mc -> mc.gui.screen() != null, 60);
					ctx.waitTicks(30); // entrance animation + first state
					String name = "realflow_gui" + scale + "_" + e.getKey();
					ctx.takeScreenshot(name);
					for (String issue : layout(ctx)) out.add(name + ": " + issue);
				} catch (AssertionError | RuntimeException ex) {
					out.add(e.getKey() + " @gui" + scale + ": could not open: " + ex.getMessage());
				}
				closeScreen(ctx, world);
				clearArea(world);
			}
		}
		window(ctx, 1920, 1080, 0);
		return out;
	}

	private static BlockPos last;

	private static Runnable block(ClientGameTestContext ctx, TestSingleplayerContext world, TableType<?> type) {
		return () -> {
			closeScreen(ctx, world);
			last = place(world, type.block(), 0, 3);
			ctx.waitTicks(3);
			useBlock(ctx, last);
		};
	}

	private static Runnable item(ClientGameTestContext ctx, TestSingleplayerContext world, Supplier<ItemStack> stack) {
		return () -> useItem(ctx, world, stack.get());
	}

	private static void clearArea(TestSingleplayerContext world) {
		BlockPos p = last;
		last = null;
		if (p != null) world.getServer().runOnServer(s -> s.overworld().removeBlock(p, false));
	}

	/** Layout problems of the open screen, in window pixels. */
	static List<String> layout(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			List<String> issues = new ArrayList<>();
			Screen s = mc.gui.screen();
			if (s == null) return issues;
			int ww = mc.getWindow().getScreenWidth();
			int wh = mc.getWindow().getScreenHeight();
			float fit = s instanceof FitScaled f ? f.fitScale() : 1f;
			if (fit < 1f) issues.add("compact fit " + fit);
			List<double[]> boxes = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (AbstractWidget w : widgets(s)) {
				if (!w.visible) continue;
				double[] a = guiToWindow(mc, s, w.getX(), w.getY());
				double[] b = guiToWindow(mc, s, w.getX() + w.getWidth(), w.getY() + w.getHeight());
				String label = w.getMessage().getString();
				if (label.isEmpty()) label = w.getClass().getSimpleName();
				if (a[0] < 0 || a[1] < 0 || b[0] > ww + 0.5 || b[1] > wh + 0.5) {
					issues.add("off screen: '" + label + "' window [" + (int) a[0] + "," + (int) a[1] + " .. " + (int) b[0] + "," + (int) b[1] + "]");
				}
				double tw = b[0] - a[0];
				double th = b[1] - a[1];
				if (tw < 24 || th < 24) issues.add("tiny target: '" + label + "' " + (int) tw + "x" + (int) th + " px");
				int textW = mc.font.width(w.getMessage());
				if (textW > w.getWidth() - 2 && !w.getMessage().getString().isEmpty()) {
					issues.add("label wider than button: '" + label + "' " + textW + " > " + w.getWidth() + " (shrunk or clipped)");
				}
				for (int i = 0; i < boxes.size(); i++) {
					double[] o = boxes.get(i);
					if (a[0] < o[2] - 1 && b[0] > o[0] + 1 && a[1] < o[3] - 1 && b[1] > o[1] + 1) {
						issues.add("overlap: '" + label + "' with '" + names.get(i) + "'");
					}
				}
				boxes.add(new double[] {a[0], a[1], b[0], b[1]});
				names.add(label);
			}
			return issues;
		});
	}
}
