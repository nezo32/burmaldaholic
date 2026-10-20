package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.client.ui.v2.V2Input;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The REAL-FLOW helper of the v2 client GameTests (docs/architecture/ui-v2.md §11.2): tests act like a player — place a
 * table, open it by USING the block (a right click through the client's game mode, so the server's {@code useWithoutItem}
 * opens the menu), click the widgets the player sees by their HitMap id at the real window pixel
 * ({@link Viewport#toWindow}), press keys — and assert the SERVER state. Every wait has a timeout and fails with what it
 * waited for.
 *
 * <pre>{@code
 * RealFlow f = new RealFlow(context, world);
 * f.size(1920, 1080, 4);                                  // 480 × 270 virtual GUI
 * BlockPos pos = f.place(BlackjackModule.TABLE.block());
 * BlackjackScreen s = f.open(pos, BlackjackScreen.class);
 * f.click("primary");                                     // Sit down
 * f.waitServer(pos, BlackjackTableBlockEntity.class, be -> be.seats().occupied().size() == 1, "seated");
 * f.click("chip.2");                                      // 25 into the bet
 * f.shot("v2_blackjack_en_us_480_bet");
 * f.close();                                              // before the world closes
 * }</pre>
 *
 * Lane filter: {@code BURMALDAHOLIC_CGT=A,K} runs only those lanes' classes ({@link #enabled}); unset = all.
 */
public final class RealFlow {
	/** Default timeout of every wait (ticks). */
	public static final int TIMEOUT = 200;

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;

	public RealFlow(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
	}

	public ClientGameTestContext context() {
		return context;
	}

	public TestSingleplayerContext world() {
		return world;
	}

	/** True when the lane's tests should run ({@code BURMALDAHOLIC_CGT} unset, or listing the lane / its class). */
	public static boolean enabled(String lane) {
		String f = System.getenv("BURMALDAHOLIC_CGT");
		boolean on = f == null || f.isBlank() || Arrays.stream(f.split(",")).map(String::trim).anyMatch(s -> s.equalsIgnoreCase(lane));
		if (on) System.out.println("[CGT] lane " + lane + " runs (BURMALDAHOLIC_CGT=" + f + ")");
		return on;
	}

	// ---- window, GUI scale, language ------------------------------------------------------------------------------

	/** Resizes the window to {@code fbW × fbH} and sets the GUI scale (0 = auto), then waits for the re-layout. */
	public void size(int fbW, int fbH, int guiScale) {
		context.getInput().resizeWindow(fbW, fbH);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(guiScale);
			mc.resizeGui();
		});
		context.waitTicks(5);
	}

	/** The virtual GUI of the open v2 screen (or of the window when none). */
	public Viewport viewport() {
		return context.computeOnClient(mc -> {
			if (mc.gui.screen() instanceof V2Chrome.Holder h) return h.chrome().viewport();
			return Viewport.of(mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale());
		});
	}

	/** Switches the language (reloads resources; waits for the reload overlay to go). */
	public void language(String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitFor(mc -> mc.gui.overlay() == null, 1200);
		context.waitTicks(10);
	}

	// ---- the world -------------------------------------------------------------------------------------------------

	public static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Runs on the server and returns the result. */
	public <T> T server(Function<MinecraftServer, T> f) {
		return world.getServer().computeOnServer(f::apply);
	}

	/**
	 * Places {@code block} two blocks in front of the player (facing the player), clearing the block above, and returns
	 * its position. {@code dx} shifts it sideways (several tables in one test).
	 */
	public BlockPos place(Block block, int dx) {
		return server(server -> {
			ServerPlayer p = player(server);
			BlockPos pos = p.blockPosition().offset(dx, 0, 2);
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
			var state = block.defaultBlockState();
			if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
				state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
			}
			server.overworld().setBlockAndUpdate(pos, state);
			return pos;
		});
	}

	public BlockPos place(Block block) {
		return place(block, 0);
	}

	/** {@link #place(Block)} of a table type, e.g. {@code place(BlackjackModule.TABLE)}. */
	public BlockPos place(dev.nezo.burmaldaholic.core.table.TableType<?> type) {
		return place(type.block(), 0);
	}

	/** Places the table and opens it by using it: the usual first step of a real-flow test. */
	public <T extends Screen> T placeAndOpen(dev.nezo.burmaldaholic.core.table.TableType<?> type, Class<T> screen) {
		return open(place(type), screen);
	}

	/** Uses the block like a right click of the player (empty hand, the top face). */
	public void use(BlockPos pos) {
		context.runOnClient(mc -> {
			if (mc.player == null || mc.gameMode == null) throw new AssertionError("no player");
			mc.player.getInventory().setSelectedSlot(8);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		});
	}

	/** Uses the block and waits for the screen of {@code type}. */
	public <T extends Screen> T open(BlockPos pos, Class<T> type) {
		use(pos);
		context.waitFor(mc -> type.isInstance(mc.gui.screen()), TIMEOUT);
		context.waitTicks(5);
		return context.computeOnClient(mc -> type.cast(mc.gui.screen()));
	}

	/** Waits until the block entity at {@code pos} (of {@code type}) satisfies {@code pred} on the SERVER. */
	public <B extends BlockEntity> void waitServer(BlockPos pos, Class<B> type, Predicate<B> pred, String what) {
		waitServer(pos, type, pred, what, TIMEOUT);
	}

	public <B extends BlockEntity> void waitServer(BlockPos pos, Class<B> type, Predicate<B> pred, String what, int timeout) {
		for (int t = 0; t <= timeout; t += 2) {
			boolean ok = server(server -> {
				BlockEntity be = server.overworld().getBlockEntity(pos);
				return type.isInstance(be) && pred.test(type.cast(be));
			});
			if (ok) return;
			context.waitTicks(2);
		}
		throw new AssertionError("timed out after " + timeout + " ticks waiting for the server: " + what);
	}

	/** Waits until the open screen's chrome satisfies {@code pred}. */
	public void waitChrome(Predicate<V2Chrome> pred, String what) {
		for (int t = 0; t <= TIMEOUT; t += 2) {
			boolean ok = context.computeOnClient(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && pred.test(h.chrome()));
			if (ok) return;
			context.waitTicks(2);
		}
		throw new AssertionError("timed out waiting for the screen: " + what);
	}

	// ---- input -----------------------------------------------------------------------------------------------------

	/** The layout box of target {@code id} of the open v2 screen. */
	public Optional<Box> box(String id) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof V2Chrome.Holder h ? h.chrome().hits().box(id) : Optional.empty());
	}

	/** True when the target exists and is enabled. */
	public boolean enabledTarget(String id) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
			&& h.chrome().hits().targets().stream().anyMatch(t -> t.id().equals(id) && t.enabled()));
	}

	/** Window pixel of the centre of target {@code id} (what the player clicks). */
	public double[] windowPoint(String id) {
		Box b = box(id).orElseThrow(() -> new AssertionError("no target '" + id + "' on " + screenName()));
		Viewport vp = viewport();
		return new double[] {vp.windowX(b.x() + b.w() / 2.0), vp.windowY(b.y() + b.h() / 2.0)}; // frame-relative → window (§3.7)
	}

	/** Left click on the centre of target {@code id} (real cursor + button, through the mouse mixin and the HitMap). */
	public void click(String id) {
		clickButton(id, Inputs.MOUSE_LEFT);
	}

	/** Right click (e.g. remove a chip). */
	public void rightClick(String id) {
		clickButton(id, Inputs.MOUSE_RIGHT);
	}

	private void clickButton(String id, int button) {
		double[] p = windowPoint(id);
		context.getInput().setCursorPos(p[0], p[1]);
		context.waitTick();
		context.getInput().pressMouse(button);
		context.waitTicks(2);
		String pressed = context.computeOnClient(mc -> mc.gui.screen() instanceof V2Chrome.Holder h ? h.chrome().lastPressed() : "");
		if (!pressed.equals(id)) throw new AssertionError("clicked '" + id + "' at " + p[0] + "," + p[1] + " but the screen pressed '" + pressed + "'");
	}

	/** Moves the cursor over target {@code id} (hover states) without clicking. */
	public void hover(String id) {
		double[] p = windowPoint(id);
		context.getInput().setCursorPos(p[0], p[1]);
		context.waitTicks(2);
	}

	/** Presses a key by its {@link dev.nezo.burmaldaholic.core.ui.v2.KeyMap} name (e.g. {@code KeyMap.SPACE}), as the running version's key code. */
	public void key(String name) {
		context.getInput().pressKey(V2Input.keyCode(name));
		context.waitTicks(2);
	}

	/** The status line 1 text of the open v2 screen ("" = none). */
	public String status() {
		return context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof V2Chrome.Holder h)) return "";
			var c = h.chrome().status.line1();
			return c == null ? "" : c.getString();
		});
	}

	public String screenName() {
		return context.computeOnClient(mc -> mc.gui.screen() == null ? "no screen" : mc.gui.screen().getClass().getSimpleName());
	}

	// ---- output and cleanup ----------------------------------------------------------------------------------------

	/** Screenshot {@code name} (moves the cursor to a corner first so no hover state leaks into the shot). */
	public Path shot(String name) {
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		return context.takeScreenshot(name);
	}

	/** Closes every screen (the server closes the menu) and lets the server drain. Call before the world closes. */
	public void close() {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().forEach(ServerPlayer::closeContainer));
		context.waitTicks(5);
	}

	/** The client (for ad-hoc reads). */
	public <T> T client(Function<Minecraft, T> f) {
		return context.computeOnClient(f::apply);
	}
}
