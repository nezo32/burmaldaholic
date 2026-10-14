package dev.nezo.burmaldaholic.gametest.realflow;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.table.cards.CardTableScreen;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.core.economy.Economies;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * REAL-FLOW helper for client GameTests (lane A4): drive the game the way a player does and assert on the server.
 *
 * <ul>
 *   <li>{@link #window} — the real window size and GUI scale (1920 × 1080 at GUI 4 / 3, …).</li>
 *   <li>{@link #place} / {@link #useBlock} — put a casino block in front of the player and right-click it with the
 *       real use key (crosshair on the block) → the server's {@code useWithoutItem} → the menu → the screen.</li>
 *   <li>{@link #useItem} — hold an item and press the use key at the sky (lucky coin, dice, scratch card).</li>
 *   <li>{@link #widget} / {@link #click(ClientGameTestContext, String)} — find a widget by the translation key of its
 *       message (or a predicate) in the open screen, including widgets owned by screen helpers (a reflective scan of
 *       the screen's fields), and click its centre with the real mouse: cursor → window pixels via the GUI scale, the
 *       {@link FitScaled} factor and the card-table canvas transform.</li>
 *   <li>{@link #clickGui} — click GUI coordinates (custom-drawn areas: scratch cells, bet spots).</li>
 *   <li>{@link #awaitServer} / {@link #awaitScreen} / {@link #awaitClient} — bounded waits with a readable failure.</li>
 * </ul>
 *
 * <p>Bug reproductions that fail today run only with {@code -Dburmaldaholic.realflow.known=true} ({@link #KNOWN}).
 */
public final class RealFlow {
	/** Run the known-bug reproductions (they fail until fixed). */
	public static final boolean KNOWN = Boolean.getBoolean("burmaldaholic.realflow.known");
	/** Take the 1920 × 1080 GUI 4 / 3 screenshot tour. */
	public static final boolean SCREENS = Boolean.getBoolean("burmaldaholic.realflow.screens");
	public static final int TIMEOUT = 200;

	private RealFlow() {}

	// ---- window -------------------------------------------------------------------------------------------------------

	/** Resizes the real window and sets the GUI scale (0 = Auto). */
	public static void window(ClientGameTestContext ctx, int width, int height, int guiScale) {
		ctx.getInput().resizeWindow(width, height);
		ctx.waitTicks(2);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(guiScale);
			mc.resizeGui();
		});
		ctx.waitTicks(3);
	}

	public static String windowInfo(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " gui "
			+ mc.getWindow().getGuiScale() + " -> " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
	}

	// ---- server helpers -------------------------------------------------------------------------------------------------

	public static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	public static long balance(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(s -> Economies.get().balance(player(s)));
	}

	public static void setBalance(TestSingleplayerContext world, long amount) {
		world.getServer().runCommand("casino balance set @p " + amount);
	}

	/**
	 * Places {@code block} {@code distance} blocks in front of the player (+Z side), facing the player, with air above,
	 * and turns the player to face +Z. Returns the position.
	 */
	public static BlockPos place(TestSingleplayerContext world, Block block, int dx, int distance) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			BlockPos base = p.blockPosition();
			BlockPos pos = base.offset(dx, 0, distance);
			for (int y = 0; y < 3; y++) {
				for (int x = -1; x <= 1; x++) {
					server.overworld().setBlockAndUpdate(pos.offset(x, y, 0), Blocks.AIR.defaultBlockState());
				}
			}
			BlockState state = block.defaultBlockState();
			if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
				state = state.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
			}
			server.overworld().setBlockAndUpdate(pos, state);
			return pos;
		});
	}

	/** Right-clicks the block with the real use key (crosshair on it). */
	public static void useBlock(ClientGameTestContext ctx, BlockPos pos) {
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(2);
		ctx.getInput().lookAt(pos);
		ctx.waitTicks(2);
		String hit = ctx.computeOnClient(mc -> mc.hitResult == null ? "none" : mc.hitResult.getType() + " " + mc.hitResult.getLocation());
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(1);
		lastHit = hit;
	}

	static String lastHit = "";

	/** Holds {@code stack} in the main hand and presses the use key looking at the sky. */
	public static void useItem(ClientGameTestContext ctx, TestSingleplayerContext world, ItemStack stack) {
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
		ctx.waitTicks(3);
		ctx.getInput().lookAt(0f, -90f);
		ctx.waitTicks(1);
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(1);
	}

	/** The key mapping with this name (the player's own binding, e.g. {@code key.burmaldaholic.open_menu}). */
	public static net.minecraft.client.KeyMapping keyMapping(ClientGameTestContext ctx, String name) {
		return ctx.computeOnClient(mc -> {
			for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
				if (k.getName().equals(name)) return k;
			}
			throw new AssertionError("no key mapping " + name);
		});
	}

	// ---- waits ------------------------------------------------------------------------------------------------------------

	/** Waits (bounded) for {@code condition} on the server thread; fails with {@code what}. */
	public static void awaitServer(ClientGameTestContext ctx, TestSingleplayerContext world, String what, Predicate<MinecraftServer> condition,
			int timeoutTicks) {
		for (int waited = 0; ; waited += 2) {
			if (world.getServer().computeOnServer(condition::test)) return;
			if (waited >= timeoutTicks) throw new AssertionError("timed out (" + timeoutTicks + " ticks) waiting for: " + what);
			ctx.waitTicks(2);
		}
	}

	public static void awaitClient(ClientGameTestContext ctx, String what, Predicate<Minecraft> condition, int timeoutTicks) {
		for (int waited = 0; ; waited += 2) {
			if (ctx.computeOnClient(condition::test)) return;
			if (waited >= timeoutTicks) throw new AssertionError("timed out (" + timeoutTicks + " ticks) waiting for: " + what);
			ctx.waitTicks(2);
		}
	}

	public static <T extends Screen> T awaitScreen(ClientGameTestContext ctx, Class<T> type, int timeoutTicks) {
		try {
			awaitClient(ctx, "screen " + type.getSimpleName(), mc -> type.isInstance(mc.gui.screen()), timeoutTicks);
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + " (open: " + screenName(ctx) + ", last hit: " + lastHit + ")");
		}
		return ctx.computeOnClient(mc -> type.cast(mc.gui.screen()));
	}

	public static String screenName(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
	}

	public static void closeScreen(ClientGameTestContext ctx, TestSingleplayerContext world) {
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runOnServer(server -> player(server).closeContainer());
		ctx.waitTicks(3);
	}

	// ---- widgets ----------------------------------------------------------------------------------------------------------

	/** True when the component (or any sibling / argument) is the translation {@code key}, or its text equals it. */
	public static boolean hasKey(Component c, String key) {
		if (c == null) return false;
		if (c.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key)) return true;
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a && hasKey(a, key)) return true;
			}
		}
		for (Component s : c.getSiblings()) {
			if (hasKey(s, key)) return true;
		}
		return c.getString().equals(key);
	}

	public static Predicate<AbstractWidget> key(String key) {
		return w -> hasKey(w.getMessage(), key);
	}

	/** Every widget reachable from the open screen: its children plus widgets held in its fields (2 levels deep). */
	public static List<AbstractWidget> widgets(Screen screen) {
		Map<Object, Boolean> seen = new IdentityHashMap<>();
		List<AbstractWidget> out = new ArrayList<>();
		for (GuiEventListener l : screen.children()) collect(l, out, seen, 0);
		scanFields(screen, out, seen, 0);
		return out;
	}

	private static void collect(Object o, List<AbstractWidget> out, Map<Object, Boolean> seen, int depth) {
		if (o == null || seen.put(o, Boolean.TRUE) != null) return;
		if (o instanceof AbstractWidget w) out.add(w);
		if (o instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c) {
			for (GuiEventListener l : c.children()) collect(l, out, seen, depth);
		}
	}

	private static void scanFields(Object owner, List<AbstractWidget> out, Map<Object, Boolean> seen, int depth) {
		if (depth > 2) return;
		for (Class<?> k = owner.getClass(); k != null && k != Object.class && !k.getName().startsWith("net.minecraft."); k = k.getSuperclass()) {
			for (Field f : k.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
				Object v;
				try {
					f.setAccessible(true);
					v = f.get(owner);
				} catch (ReflectiveOperationException | RuntimeException e) {
					continue;
				}
				if (v instanceof AbstractWidget) {
					collect(v, out, seen, depth);
				} else if (v instanceof Collection<?> col) {
					for (Object e : List.copyOf(col)) {
						if (e instanceof AbstractWidget) collect(e, out, seen, depth);
					}
				} else if (v != null && v.getClass().getName().startsWith("dev.nezo.") && seen.put(v, Boolean.TRUE) == null) {
					scanFields(v, out, seen, depth + 1);
				}
			}
		}
	}

	/** One-line description of the visible widgets (diagnostics). */
	public static String describe(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			Screen s = mc.gui.screen();
			if (s == null) return "no screen";
			StringBuilder b = new StringBuilder(s.getClass().getSimpleName()).append(": ");
			for (AbstractWidget w : widgets(s)) {
				if (!w.visible) continue;
				b.append('[').append(w.getMessage().getString()).append(w.active ? "" : " (off)").append(" @").append(w.getX()).append(',').append(w.getY())
					.append(' ').append(w.getWidth()).append('x').append(w.getHeight()).append("] ");
			}
			return b.toString();
		});
	}

	/** Snapshot of a widget: its GUI box and window centre. */
	public record WidgetRef(String label, boolean active, int x, int y, int w, int h, double windowX, double windowY, double windowW,
			double windowH) {}

	/** Finds the first visible widget matching {@code match}, or null. */
	public static WidgetRef widget(ClientGameTestContext ctx, Predicate<AbstractWidget> match) {
		return ctx.computeOnClient(mc -> {
			Screen s = mc.gui.screen();
			if (s == null) return null;
			for (AbstractWidget w : widgets(s)) {
				if (w.visible && match.test(w)) {
					double[] c = guiToWindow(mc, s, w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
					double[] o = guiToWindow(mc, s, w.getX(), w.getY());
					double[] e = guiToWindow(mc, s, w.getX() + w.getWidth(), w.getY() + w.getHeight());
					return new WidgetRef(w.getMessage().getString(), w.active, w.getX(), w.getY(), w.getWidth(), w.getHeight(), c[0], c[1], e[0] - o[0],
						e[1] - o[1]);
				}
			}
			return null;
		});
	}

	public static WidgetRef widget(ClientGameTestContext ctx, String key) {
		return widget(ctx, key(key));
	}

	/**
	 * Window pixel position of a point in the screen's own coordinates (widget space): undoes the card-table canvas
	 * transform, the {@link FitScaled} compact factor and the GUI scale.
	 */
	public static double[] guiToWindow(Minecraft mc, Screen s, double x, double y) {
		if (s instanceof CardTableScreen) {
			int ox = (int) field(s, "ox");
			int oy = (int) field(s, "oy");
			float fk = (float) field(s, "fk");
			x = ox + x * fk;
			y = oy + y * fk;
		}
		float fit = s instanceof FitScaled f ? f.fitScale() : 1f;
		double sx = mc.getWindow().getScreenWidth() / (double) mc.getWindow().getGuiScaledWidth();
		double sy = mc.getWindow().getScreenHeight() / (double) mc.getWindow().getGuiScaledHeight();
		return new double[] {x * fit * sx, y * fit * sy};
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

	/** Clicks the widget whose message carries {@code key}; fails when it is missing or inactive. */
	public static WidgetRef click(ClientGameTestContext ctx, String key) {
		return click(ctx, key, key(key));
	}

	public static WidgetRef click(ClientGameTestContext ctx, String what, Predicate<AbstractWidget> match) {
		WidgetRef w = widget(ctx, match);
		if (w == null) throw new AssertionError("no widget '" + what + "' on " + describe(ctx));
		if (!w.active()) throw new AssertionError("widget '" + what + "' is disabled on " + describe(ctx));
		clickWindow(ctx, w.windowX(), w.windowY());
		return w;
	}

	/** Clicks a point given in the open screen's GUI coordinates. */
	public static void clickGui(ClientGameTestContext ctx, double x, double y) {
		double[] p = ctx.computeOnClient(mc -> guiToWindow(mc, mc.gui.screen(), x, y));
		clickWindow(ctx, p[0], p[1]);
	}

	public static void clickWindow(ClientGameTestContext ctx, double x, double y) {
		ctx.getInput().setCursorPos(x, y);
		ctx.waitTick();
		ctx.getInput().pressMouse(Inputs.MOUSE_LEFT);
		ctx.waitTicks(2);
	}
}
