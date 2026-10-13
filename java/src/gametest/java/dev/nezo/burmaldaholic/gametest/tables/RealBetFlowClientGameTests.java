package dev.nezo.burmaldaholic.gametest.tables;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.table.cards.CardTableScreen;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.client.BaccaratScreen;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.client.CrapsScreen;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteScreen;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.1.2 hotfix (real player flow, review of "blackjack, roulette and craps won't let me place a bet"): a Survival player
 * walks up to a freshly placed table, opens it by USING the block (the client's interaction packet, not
 * {@code openMenu}), and plays with the MOUSE at the GUI sizes real players have — 1920 × 1080 at GUI scale 4 (480 × 270)
 * and 3 (640 × 360), 2560 × 1440 at GUI scale Auto = 6 (426 × 240, the compact card canvas) and the 854 × 480 default.
 * Those GUI sizes are reproduced with a smaller window at a lower scale (same GUI coordinates). Every click goes through
 * the screen's real input path and every action through the real {@code table_action} C2S packet; the asserts read the
 * SERVER: the stake is debited, the round runs to its result, the payout lands and the next round opens for betting.
 */
public class RealBetFlowClientGameTests implements FabricClientGameTest {
	/** {window w, h, GUI scale, tag}: GUI 480 × 270 (1080p @4), 640 × 360 (1080p @3), 426 × 240 (1440p auto), 427 × 240, 960 × 540 (1080p @2). */
	private static final Object[][] VIEWS = {
		{960, 540, 2, "1080p_gui4"},
		{1280, 720, 2, "1080p_gui3"},
		{1278, 720, 3, "1440p_auto"},
		{854, 480, 2, "default"},
		{960, 540, 1, "1080p_gui2"},
	};
	private static final int TIMEOUT = 600;

	private final List<String> failures = new ArrayList<>();
	private final List<String> log = new ArrayList<>();
	private int tableNo;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try {
			try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create(); var q = ClientTestWorlds.quiet(context, world)) {
				context.waitTicks(20);
				context.runOnClient(mc -> mc.gui.setScreen(null));
				prepare(context, world.getServer());
				for (Object[] v : VIEWS) {
					view(context, v);
					String tag = (String) v[3];
					blackjack(context, world.getServer(), tag);
					roulette(context, world.getServer(), tag);
					craps(context, world.getServer(), tag);
					baccarat(context, world.getServer(), tag);
				}
				context.runOnClient(mc -> mc.gui.setScreen(null));
				context.waitTicks(20);
			}
			// the same on a dedicated server (the multiplayer report): every packet is encoded and decoded for real
			try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
				server.runCommand("casino mode on");
				try (TestDedicatedServerConnection connection = server.connect()) {
					connection.waitForChunksDownload();
					context.waitTicks(20);
					context.runOnClient(mc -> mc.gui.setScreen(null));
					prepare(context, server);
					view(context, VIEWS[0]);
					blackjack(context, server, "server");
					roulette(context, server, "server");
					craps(context, server, "server");
					baccarat(context, server, "server");
					context.runOnClient(mc -> mc.gui.setScreen(null));
					context.waitTicks(20);
				}
			}
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("real bet flow: " + String.join("; ", failures));
	}

	/** A Survival player with chips, chaos events off (a mob wave would end the session). */
	private static void prepare(ClientGameTestContext context, TestServerContext world) {
		world.runCommand("gamemode survival @a");
		world.runCommand("casino balance set @p 5000");
		world.runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
		context.waitTicks(5);
	}

	// ---- blackjack ----------------------------------------------------------------------------------------------------

	private void blackjack(ClientGameTestContext context, TestServerContext world, String tag) {
		String name = tag + " blackjack";
		BlockPos pos = placeAndOpen(context, world, BlackjackModule.TABLE, BlackjackScreen.class, name);
		if (pos == null) return;
		long before = balance(world);
		if (!clickLabel(context, name, "gui.burmaldaholic.blackjack.sit")) return;
		if (!serverWait(context, world, pos, name + ": seated after Sit", (t, p) -> t.isSeated(p))) return;
		context.waitTicks(3);
		// chips from the rack (25 + 25), then Deal: the real input path of a fresh player
		if (!clickChip(context, name, 2) || !clickChip(context, name, 2)) return;
		shot(context, tag, "blackjack");
		if (!clickLabel(context, name, "gui.burmaldaholic.common.deal")) return;
		// accepted = the server took the 50 as this round's bet (a natural settles at once, so the stake alone may be gone)
		if (!serverWait(context, world, pos, name + ": bet of 50 accepted",
			(t, p) -> t.stakeOf(p.getUUID()) == 50 || t.writeClientState(p).getLongOr("last_bet", 0) == 50)) return;
		// play the round out by the buttons the player has: no insurance / even money, Stand on every hand
		for (int i = 0; i < 40; i++) {
			String phase = world.computeOnServer(server -> table(server, pos).phase());
			if ("result".equals(phase) || "betting".equals(phase) && stake(world, pos) == 0) break;
			if (!tryClick(context, "gui.burmaldaholic.blackjack.no_insurance") && !tryClick(context, "gui.burmaldaholic.blackjack.decline_even_money")) {
				tryClick(context, "gui.burmaldaholic.blackjack.stand");
			}
			context.waitTicks(10);
		}
		if (!serverWait(context, world, pos, name + ": round settled", (t, p) -> t.stakeOf(p.getUUID()) == 0)) return;
		long after = balance(world);
		log.add(name + ": balance " + before + " -> " + after);
		if (after != before - 50 && after != before && after != before + 50 && after != before + 75) {
			failures.add(name + ": balance moved by " + (after - before) + " on a 50 bet");
		}
		// the next round: betting opens again and a second bet goes through the same buttons
		if (!serverWait(context, world, pos, name + ": next round opens", (t, p) -> "betting".equals(phase(t, p)))) return;
		context.waitTicks(5);
		if (!clickChip(context, name, 1) || !clickLabel(context, name, "gui.burmaldaholic.common.deal")) return;
		serverWait(context, world, pos, name + ": second bet accepted", (t, p) -> t.stakeOf(p.getUUID()) == 5 || t.writeClientState(p).getLongOr("last_bet", 0) == 5);
		closeAndLeave(context, world, pos);
	}

	// ---- roulette -----------------------------------------------------------------------------------------------------

	private void roulette(ClientGameTestContext context, TestServerContext world, String tag) {
		String name = tag + " roulette";
		BlockPos pos = placeAndOpen(context, world, RouletteModule.TABLE, RouletteScreen.class, name);
		if (pos == null) return;
		long before = balance(world);
		// v2 screen (lane C): arm the smallest chip, put it on RED, both by real clicks on the screen's HitMap targets
		long chip = context.computeOnClient(mc -> mc.gui.screen() instanceof RouletteScreen sc && sc.chrome().bet.chips().length > 0
			? sc.chrome().bet.chips()[0] : -1L);
		if (chip <= 0 || !clickTarget(context, name, "chip.0")) {
			failures.add(name + ": no chip in the tray");
			return;
		}
		Spot red = Spot.of(BetType.RED, Spot.outsideNumbers(BetType.RED, 0));
		shot(context, tag, "roulette");
		if (!clickTarget(context, name, RouletteLayout.spotId(red))) return;
		if (!serverWait(context, world, pos, name + ": RED bet accepted", (t, p) -> t.stakeOf(p.getUUID()) == chip)) return;
		if (!clickTarget(context, name, "primary")) return; // Spin
		if (!serverWait(context, world, pos, name + ": spin settled", (t, p) -> t.stakeOf(p.getUUID()) == 0)) return;
		long after = balance(world);
		log.add(name + ": balance " + before + " -> " + after);
		if (after != before - chip && after != before + chip && after != before - chip + chip / 2 /* la partage */) {
			failures.add(name + ": balance moved by " + (after - before) + " on a " + chip + " even-money bet");
		}
		if (!serverWait(context, world, pos, name + ": next spin opens", (t, p) -> "betting".equals(phase(t, p)))) return;
		context.waitTicks(5);
		Spot black = Spot.of(BetType.BLACK, Spot.outsideNumbers(BetType.BLACK, 0));
		if (clickTarget(context, name, RouletteLayout.spotId(black))) {
			serverWait(context, world, pos, name + ": second bet accepted", (t, p) -> t.stakeOf(p.getUUID()) == chip);
		}
		closeAndLeave(context, world, pos);
	}

	/** Clicks the centre of a v2 screen's HitMap target (window px through the screen's viewport), like a player. */
	private boolean clickTarget(ClientGameTestContext context, String name, String id) {
		double[] at = context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof V2Chrome.Holder h)) return null;
			Optional<Box> b = h.chrome().hits().box(id);
			if (b.isEmpty()) return null;
			Viewport vp = h.chrome().viewport();
			return new double[] {vp.toWindow(b.get().x() + b.get().w() / 2.0), vp.toWindow(b.get().y() + b.get().h() / 2.0)};
		});
		if (at == null) {
			failures.add(name + ": no target " + id);
			return false;
		}
		click(context, at[0], at[1]);
		String pressed = context.computeOnClient(mc -> mc.gui.screen() instanceof V2Chrome.Holder h ? h.chrome().lastPressed() : "");
		if (!id.equals(pressed)) {
			failures.add(name + ": clicked " + id + " but the screen pressed " + pressed);
			return false;
		}
		return true;
	}

	// ---- craps --------------------------------------------------------------------------------------------------------

	private void craps(ClientGameTestContext context, TestServerContext world, String tag) {
		String name = tag + " craps";
		BlockPos pos = placeAndOpen(context, world, CrapsModule.TABLE, CrapsScreen.class, name);
		if (pos == null) return;
		if (!clickWidget(context, name, w -> w.getClass().getSimpleName().equals("ChipButton") && w.getMessage().getString().equals(Texts.chips(5).getString()))) {
			failures.add(name + ": no 5 chip button");
			return;
		}
		double[] pass = context.computeOnClient(mc -> find(mc.gui.screen(), (s, x, y) -> {
			Object felt = call(s, "felt");
			int lx = (int) call(s, "lx");
			int ly = (int) call(s, "ly");
			Optional<?> a = (Optional<?>) call(felt, "hit", x - lx, y - ly);
			return a.isPresent() && a.get().toString().startsWith("Area[kind=pass,");
		}));
		if (pass == null) {
			failures.add(name + ": no Pass Line on screen");
			return;
		}
		shot(context, tag, "craps");
		click(context, pass[0], pass[1]);
		if (!serverWait(context, world, pos, name + ": Pass Line bet accepted", (t, p) -> t.stakeOf(p.getUUID()) == 5)) return;
		// the shooter rolls until the pass line resolves (come-out decision or the point made / seven out)
		for (int i = 0; i < 60 && stake(world, pos) > 0; i++) {
			if (!tryClick(context, "gui.burmaldaholic.craps.roll")) context.waitTicks(10);
			context.waitTicks(20);
		}
		if (stake(world, pos) > 0) failures.add(name + ": the pass line never resolved by rolling");
		// the next come-out: a new Pass Line bet goes through
		context.waitTicks(10);
		double[] again = context.computeOnClient(mc -> find(mc.gui.screen(), (s, x, y) -> {
			Object felt = call(s, "felt");
			Optional<?> a = (Optional<?>) call(felt, "hit", x - (int) call(s, "lx"), y - (int) call(s, "ly"));
			return a.isPresent() && a.get().toString().startsWith("Area[kind=pass,");
		}));
		if (again != null) {
			click(context, again[0], again[1]);
			serverWait(context, world, pos, name + ": next Pass Line bet accepted", (t, p) -> t.stakeOf(p.getUUID()) > 0);
		}
		closeAndLeave(context, world, pos);
	}

	// ---- baccarat -----------------------------------------------------------------------------------------------------

	private void baccarat(ClientGameTestContext context, TestServerContext world, String tag) {
		String name = tag + " baccarat";
		BlockPos pos = placeAndOpen(context, world, BaccaratModule.TABLE, BaccaratScreen.class, name);
		if (pos == null) return;
		if (!clickLabel(context, name, "gui.burmaldaholic.baccarat.sit")) return;
		if (!serverWait(context, world, pos, name + ": seated after Sit", (t, p) -> t.isSeated(p))) return;
		context.waitTicks(3);
		clickChip(context, name, 2); // 25 (the default chip is used when the rack is hidden)
		double[] box = context.computeOnClient(mc -> {
			Screen s = mc.gui.screen();
			if (!(s instanceof CardTableScreen)) return null;
			Object kinds = call(s, "boxes");
			for (Object k : (Iterable<?>) kinds) {
				if (!k.toString().equals("PLAYER")) continue;
				int[] r = (int[]) call(s, "boxRect", k);
				return canvasToWindow(mc, s, r[0] + r[2] / 2.0, r[1] + r[3] / 2.0);
			}
			return null;
		});
		if (box == null) {
			failures.add(name + ": no PLAYER box");
			return;
		}
		click(context, box[0], box[1]);
		if (!serverWait(context, world, pos, name + ": PLAYER bet accepted", (t, p) -> t.stakeOf(p.getUUID()) > 0)) return;
		long staked = stake(world, pos);
		long before = balance(world) + staked;
		shot(context, tag, "baccarat");
		if (!clickLabel(context, name, "gui.burmaldaholic.common.deal")) return;
		if (!serverWait(context, world, pos, name + ": coup settled", (t, p) -> t.stakeOf(p.getUUID()) == 0)) return;
		log.add(name + ": staked " + staked + ", balance " + before + " -> " + balance(world));
		if (!serverWait(context, world, pos, name + ": next coup opens", (t, p) -> "betting".equals(phase(t, p)) || "idle".equals(phase(t, p)))) return;
		closeAndLeave(context, world, pos);
	}

	// ---- the real flow helpers ----------------------------------------------------------------------------------------

	/** Places a table two blocks east of the player (fresh block entity) and opens it by using the block from the client. */
	private BlockPos placeAndOpen(ClientGameTestContext context, TestServerContext world, TableType<?> type, Class<? extends Screen> screen,
			String name) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		int n = tableNo++;
		world.runCommand("casino balance set @p 5000"); // every table starts with the same chips (a lost round must not starve the next)
		BlockPos pos = world.computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.closeContainer();
			BlockPos p = player.blockPosition().offset(2, 0, (n % 5) - 2);
			server.overworld().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(p, type.block().defaultBlockState());
			return p;
		});
		context.waitTicks(5); // the block reaches the client
		context.runOnClient(mc -> {
			Vec3 hit = Vec3.atCenterOf(pos).add(0, 0.5, 0);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, pos, false));
		});
		try {
			context.waitFor(mc -> screen.isInstance(mc.gui.screen()) && ((CasinoTableScreen) mc.gui.screen()).getMenu().pos().equals(pos), 200);
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": using the block did not open " + screen.getSimpleName() + " (screen: "
				+ context.computeOnClient(mc -> String.valueOf(mc.gui.screen())) + ")");
			return null;
		}
		context.waitTicks(15); // the entrance plays; the state arrived
		log.add(name + ": opened at GUI " + context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight()));
		return pos;
	}

	private void closeAndLeave(ClientGameTestContext context, TestServerContext world, BlockPos pos) {
		context.runOnClient(mc -> mc.gui.setScreen(null)); // closes the menu (C2S container close)
		context.waitTicks(5);
		world.runOnServer(server -> {
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); // a round in play is played out
		});
		context.waitTicks(5);
	}

	@FunctionalInterface
	private interface TableCheck {
		boolean test(CasinoTableBlockEntity table, ServerPlayer player);
	}

	private static CasinoTableBlockEntity table(MinecraftServer server, BlockPos pos) {
		return (CasinoTableBlockEntity) server.overworld().getBlockEntity(pos);
	}

	private boolean serverWait(ClientGameTestContext context, TestServerContext world, BlockPos pos, String what, TableCheck check) {
		for (int waited = 0; waited <= TIMEOUT; waited += 5) {
			boolean ok = world.computeOnServer(server -> {
				CasinoTableBlockEntity t = table(server, pos);
				return t != null && check.test(t, server.getPlayerList().getPlayers().getFirst());
			});
			if (ok) return true;
			context.waitTicks(5);
		}
		String detail = context.computeOnClient(mc -> {
			Screen s = mc.gui.screen();
			StringBuilder b = new StringBuilder("screen=").append(s == null ? "none" : s.getClass().getSimpleName());
			if (s instanceof CasinoTableScreen) {
				b.append(" error=").append(String.valueOf(call(s, "errorLine")));
				b.append(" widgets=").append(labels(s));
			}
			return b.toString();
		});
		String server = world.computeOnServer(sv -> {
			CasinoTableBlockEntity t = table(sv, pos);
			ServerPlayer p = sv.getPlayerList().getPlayers().getFirst();
			return t == null ? "no table" : "phase=" + phase(t, p) + " seated=" + t.isSeated(p) + " stake=" + t.stakeOf(p.getUUID()) + " balance="
				+ Economies.get().balance(p) + " menu=" + p.containerMenu.getClass().getSimpleName();
		});
		failures.add(what + " — timed out (" + server + "; " + detail + ")");
		return false;
	}

	/** The phase the table's screen shows (roulette and craps run their own round model, not core's phase). */
	private static String phase(CasinoTableBlockEntity t, ServerPlayer p) {
		return t.writeClientState(p).getStringOr("phase", "");
	}

	private static long balance(TestServerContext world) {
		return world.computeOnServer(server -> Economies.get().balance(server.getPlayerList().getPlayers().getFirst()));
	}

	private static long stake(TestServerContext world, BlockPos pos) {
		return world.computeOnServer(server -> {
			CasinoTableBlockEntity t = table(server, pos);
			return t == null ? 0L : t.stakeOf(server.getPlayerList().getPlayers().getFirst().getUUID());
		});
	}

	/** Clicks the visible, active widget whose label is {@code key} (translated); records a failure when there is none. */
	private boolean clickLabel(ClientGameTestContext context, String name, String key) {
		if (clickWidget(context, name, w -> w.getMessage().getString().equals(Component.translatable(key).getString()))) return true;
		failures.add(name + ": no active \"" + key + "\" button (" + context.computeOnClient(mc -> labels(mc.gui.screen())) + ")");
		return false;
	}

	private boolean tryClick(ClientGameTestContext context, String key) {
		return clickWidget(context, "", w -> w.getMessage().getString().equals(Component.translatable(key).getString()));
	}

	private boolean clickWidget(ClientGameTestContext context, String name, Predicate<AbstractWidget> which) {
		double[] at = context.computeOnClient(mc -> {
			Screen s = mc.gui.screen();
			if (s == null) return null;
			for (GuiEventListener child : s.children()) {
				if (child instanceof AbstractWidget w && w.visible && w.active && which.test(w)) {
					double cx = w.getX() + w.getWidth() / 2.0;
					double cy = w.getY() + w.getHeight() / 2.0;
					return s instanceof CardTableScreen ? canvasToWindow(mc, s, cx, cy) : guiToWindow(mc, cx, cy);
				}
			}
			return null;
		});
		if (at == null) return false;
		click(context, at[0], at[1]);
		return true;
	}

	/** Clicks chip {@code index} of a card table's chip rack (at the canvas position the screen draws it). */
	private boolean clickChip(ClientGameTestContext context, String name, int index) {
		double[] at = context.computeOnClient(mc -> {
			Screen s = mc.gui.screen();
			if (!(s instanceof CardTableScreen c)) return null;
			int[] r = c.chipRackSlot(index);
			return r == null ? null : canvasToWindow(mc, s, r[0] + r[2] / 2.0, r[1] + r[3] / 2.0);
		});
		if (at == null) {
			failures.add(name + ": no chip rack on screen (" + context.computeOnClient(mc -> labels(mc.gui.screen())) + ")");
			return false;
		}
		click(context, at[0], at[1]);
		return true;
	}

	/** {@code jtest_real_<view>_<game>}: the screen the player is betting on (chips picked, before the bet / deal). */
	private static void shot(ClientGameTestContext context, String tag, String game) {
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(1);
		context.takeScreenshot("jtest_real_" + tag + "_" + game);
	}

	private static void click(ClientGameTestContext context, double wx, double wy) {
		context.getInput().setCursorPos(wx, wy);
		context.waitTicks(1);
		context.getInput().pressMouse(0);
		context.waitTicks(3);
	}

	private static double[] guiToWindow(Minecraft mc, double x, double y) {
		double k = mc.getWindow().getGuiScale();
		return new double[] {x * k, y * k};
	}

	private static double[] canvasToWindow(Minecraft mc, Screen s, double x, double y) {
		int ox = (int) field(s, "ox");
		int oy = (int) field(s, "oy");
		float fk = (float) field(s, "fk");
		return guiToWindow(mc, ox + x * fk, oy + y * fk);
	}

	@FunctionalInterface
	private interface Probe {
		boolean test(Screen s, double x, double y);
	}

	/** First GUI point (scanned on a 2 px grid) the screen's own hit test accepts, as window coordinates. */
	private static double[] find(Screen s, Probe probe) {
		if (s == null) return null;
		Minecraft mc = Minecraft.getInstance();
		for (int y = 1; y < s.height; y += 2) {
			for (int x = 1; x < s.width; x += 2) {
				if (probe.test(s, x + 0.5, y + 0.5)) return guiToWindow(mc, x + 0.5, y + 0.5);
			}
		}
		return null;
	}

	private static String labels(Screen s) {
		if (s == null) return "no screen";
		List<String> out = new ArrayList<>();
		for (GuiEventListener child : s.children()) {
			if (child instanceof AbstractWidget w && w.visible) out.add(w.getMessage().getString() + (w.active ? "" : "(off)"));
		}
		return String.join(",", out);
	}

	private void view(ClientGameTestContext context, Object[] v) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.getInput().resizeWindow((int) v[0], (int) v[1]);
		context.waitTicks(3);
		context.runOnClient(mc -> {
			mc.options.guiScale().set((int) v[2]);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	// ---- reflection on the screens' own layout (the click targets come from the code that draws them) -----------------

	private static Object field(Object o, String name) {
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (NoSuchFieldException e) {
				// try the superclass
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		throw new IllegalArgumentException("no field " + name + " on " + o.getClass());
	}

	private static Object call(Object o, String name, Object... args) {
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
				try {
					m.setAccessible(true);
					return m.invoke(o, args);
				} catch (ReflectiveOperationException | IllegalArgumentException e) {
					// another overload
				}
			}
		}
		throw new IllegalArgumentException("no method " + name + " on " + o.getClass());
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>(log);
			failures.forEach(f -> lines.add("FAIL " + f));
			Files.writeString(dir.resolve("jtest_real_bet_flow.txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
