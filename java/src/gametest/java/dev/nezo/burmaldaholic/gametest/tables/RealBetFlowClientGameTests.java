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
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
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
		{910, 512, 2, "1366x768_auto"}, // GUI 455 × 256 (laptops; lane A10)
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
				view(context, VIEWS[0]);
				freshPlayer(context, world.getServer());
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
		if (!clickV2(context, name, "primary")) return; // v2: the primary says Sit down
		if (!serverWait(context, world, pos, name + ": seated after Sit", (t, p) -> t.isSeated(p))) return;
		context.waitTicks(3);
		// chips from the rack (25 + 25), then Deal: the real input path of a fresh player
		if (!clickV2Chip(context, name, 25) || !clickV2Chip(context, name, 25)) return;
		shot(context, tag, "blackjack");
		if (!clickV2(context, name, "primary")) return; // Deal
		// accepted = the server took the 50 as this round's bet (a natural settles at once, so the stake alone may be gone)
		if (!serverWait(context, world, pos, name + ": bet of 50 accepted",
			(t, p) -> t.stakeOf(p.getUUID()) == 50 || t.writeClientState(p).getLongOr("last_bet", 0) == 50)) return;
		// play the round out by the buttons the player has: no insurance / even money, Stand on every hand
		for (int i = 0; i < 40; i++) {
			String phase = world.computeOnServer(server -> table(server, pos).phase());
			if ("result".equals(phase) || "betting".equals(phase) && stake(world, pos) == 0) break;
			if (!tryV2(context, "action.no_insurance") && !tryV2(context, "action.no_even_money")) {
				if (primaryIs(context, "stand")) tryV2(context, "primary");
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
		if (!clickV2Chip(context, name, 5) || !clickV2(context, name, "primary")) return;
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
		// v2 screen (lane D): chips and felt spots are HitMap targets; clicked at the window pixel of their box
		String name = tag + " craps";
		BlockPos pos = placeAndOpen(context, world, CrapsModule.TABLE, CrapsScreen.class, name);
		if (pos == null) return;
		int five = context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof CrapsScreen s)) return -1;
			long[] chips = s.chrome().bet.chips();
			for (int i = 0; i < chips.length; i++) if (chips[i] == 5) return i;
			return -1;
		});
		if (five < 0 || !clickTarget(context, "chip." + five)) {
			failures.add(name + ": no 5 chip in the tray");
			return;
		}
		shot(context, tag, "craps");
		if (!clickTarget(context, CrapsLayout.PASS)) {
			failures.add(name + ": no Pass Line on screen");
			return;
		}
		if (!serverWait(context, world, pos, name + ": Pass Line bet accepted", (t, p) -> t.stakeOf(p.getUUID()) == 5)) return;
		// the shooter rolls (the primary) until the pass line resolves (come-out decision or the point made / seven out)
		for (int i = 0; i < 60 && stake(world, pos) > 0; i++) {
			boolean canRoll = context.computeOnClient(mc -> mc.gui.screen() instanceof CrapsScreen s && s.chrome().primary.gate().enabled()
				&& "roll".equals(s.chrome().primary.gate().action()));
			if (canRoll) clickTarget(context, "primary");
			else context.waitTicks(10);
			context.waitTicks(20);
		}
		if (stake(world, pos) > 0) failures.add(name + ": the pass line never resolved by rolling");
		// the next come-out: a new Pass Line bet goes through
		context.waitTicks(10);
		if (clickTarget(context, CrapsLayout.PASS)) {
			serverWait(context, world, pos, name + ": next Pass Line bet accepted", (t, p) -> t.stakeOf(p.getUUID()) > 0);
		}
		closeAndLeave(context, world, pos);
	}

	/** Clicks the centre of a v2 screen's HitMap target (layout px → window px through the screen's viewport). */
	private static boolean clickTarget(ClientGameTestContext context, String id) {
		double[] at = context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h)) return null;
			var b = h.chrome().hits().box(id);
			if (b.isEmpty()) return null;
			var vp = h.chrome().viewport();
			return new double[] {vp.toWindow(b.get().x() + b.get().w() / 2.0), vp.toWindow(b.get().y() + b.get().h() / 2.0)};
		});
		if (at == null) return false;
		click(context, at[0], at[1]);
		return true;
	}

	// ---- baccarat -----------------------------------------------------------------------------------------------------

	private void baccarat(ClientGameTestContext context, TestServerContext world, String tag) {
		String name = tag + " baccarat";
		BlockPos pos = placeAndOpen(context, world, BaccaratModule.TABLE, BaccaratScreen.class, name);
		if (pos == null) return;
		if (!clickV2(context, name, "primary")) return; // v2: the primary says Sit down
		if (!serverWait(context, world, pos, name + ": seated after Sit", (t, p) -> t.isSeated(p))) return;
		context.waitTicks(3);
		if (!clickV2Chip(context, name, 25)) return; // arms the 25 (multi-spot)
		if (!clickV2(context, name, "felt.box.player")) return;
		if (!serverWait(context, world, pos, name + ": PLAYER bet accepted", (t, p) -> t.stakeOf(p.getUUID()) > 0)) return;
		long staked = stake(world, pos);
		long before = balance(world) + staked;
		shot(context, tag, "baccarat");
		if (!clickV2(context, name, "primary")) return; // Deal
		if (!serverWait(context, world, pos, name + ": coup settled", (t, p) -> t.stakeOf(p.getUUID()) == 0)) return;
		log.add(name + ": staked " + staked + ", balance " + before + " -> " + balance(world));
		if (!serverWait(context, world, pos, name + ": next coup opens", (t, p) -> "betting".equals(phase(t, p)) || "idle".equals(phase(t, p)))) return;
		closeAndLeave(context, world, pos);
	}

	// ---- a fresh player (lane A10) -------------------------------------------------------------------------------------

	/**
	 * What a fresh Survival player does (lane A10): few chips and NO chip picked — the screen's default chip — then the
	 * felt. Roulette with 20 chips: the default 25 chip cannot be afforded, so the screen must offer one that fits and
	 * the RED click must land. Craps on the come-out: COME (the biggest box) and a number box take no bet then — the
	 * status line must say WHY (not a bare "invalid bet position"); the Pass Line takes the chip.
	 */
	private void freshPlayer(ClientGameTestContext context, TestServerContext world) {
		String name = "fresh roulette";
		BlockPos pos = placeAndOpen(context, world, RouletteModule.TABLE, RouletteScreen.class, name, 20);
		if (pos != null) {
			double[] red = context.computeOnClient(mc -> find(mc.gui.screen(), (s, x, y) -> {
				Optional<?> spot = (Optional<?>) call(s, "spotAt", x, y);
				return spot.isPresent() && spot.get().toString().contains("RED");
			}));
			if (red == null) {
				failures.add(name + ": no RED spot on screen");
			} else {
				click(context, red[0], red[1]);
				serverWait(context, world, pos, name + ": RED bet with the default chip and 20 chips lands", (t, p) -> t.stakeOf(p.getUUID()) > 0);
				log.add(name + ": staked " + stake(world, pos) + " of 20");
			}
			closeAndLeave(context, world, pos);
		}
		name = "fresh blackjack";
		pos = placeAndOpen(context, world, BlackjackModule.TABLE, BlackjackScreen.class, name, 50);
		if (pos != null && clickLabel(context, name, "gui.burmaldaholic.blackjack.sit")
				&& serverWait(context, world, pos, name + ": seated after Sit", (t, p) -> t.isSeated(p))) {
			context.waitTicks(3);
			// the default chip (25) put on the player's own betting circle, then Deal
			double[] spot = context.computeOnClient(mc -> {
				Screen s = mc.gui.screen();
				boolean compact = (boolean) field(s, "compact");
				int x = (int) call(s, "tx", compact ? 140 : dev.nezo.burmaldaholic.core.anim.cards.CardLayout.Blackjack.ME_SPOT_X);
				int y = (int) call(s, "ty", compact ? dev.nezo.burmaldaholic.core.anim.cards.CardLayout.Blackjack.C_STACK_Y
					: dev.nezo.burmaldaholic.core.anim.cards.CardLayout.Blackjack.ME_SPOT_Y);
				return canvasToWindow(mc, s, x, y);
			});
			click(context, spot[0], spot[1]);
			if (clickLabel(context, name, "gui.burmaldaholic.common.deal")) {
				serverWait(context, world, pos, name + ": the chip put on the circle is the bet",
					(t, p) -> t.stakeOf(p.getUUID()) == 25 || t.writeClientState(p).getLongOr("last_bet", 0) == 25);
			}
		}
		if (pos != null) closeAndLeave(context, world, pos);
		name = "fresh craps";
		pos = placeAndOpen(context, world, CrapsModule.TABLE, CrapsScreen.class, name, 50);
		if (pos == null) return;
		feltClick(context, name, "Area[kind=come,", "gui.burmaldaholic.craps.come_needs_point");
		feltClick(context, name, "Area[kind=point,", "gui.burmaldaholic.craps.numbers_come_out");
		if (stake(world, pos) != 0) failures.add(name + ": COME / a number took a bet on the come-out");
		double[] pass = feltPoint(context, "Area[kind=pass,");
		if (pass == null) {
			failures.add(name + ": no Pass Line");
		} else {
			click(context, pass[0], pass[1]);
			serverWait(context, world, pos, name + ": Pass Line with the default chip lands", (t, p) -> t.stakeOf(p.getUUID()) > 0);
		}
		closeAndLeave(context, world, pos);
	}

	private static double[] feltPoint(ClientGameTestContext context, String area) {
		return context.computeOnClient(mc -> find(mc.gui.screen(), (s, x, y) -> {
			Object felt = call(s, "felt");
			Optional<?> a = (Optional<?>) call(felt, "hit", x - (int) call(s, "lx"), y - (int) call(s, "ly"));
			return a.isPresent() && a.get().toString().startsWith(area);
		}));
	}

	/** Clicks a felt area and checks the screen's error line names the reason {@code key}. */
	private void feltClick(ClientGameTestContext context, String name, String area, String key) {
		double[] at = feltPoint(context, area);
		if (at == null) {
			failures.add(name + ": no " + area);
			return;
		}
		click(context, at[0], at[1]);
		context.waitTicks(5);
		String shown = context.computeOnClient(mc -> {
			Object e = field(mc.gui.screen(), "error");
			return e instanceof Component c && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey()
				: String.valueOf(e);
		});
		log.add(name + ": " + area + " -> " + shown);
		if (!key.equals(shown)) failures.add(name + ": clicking " + area + " on the come-out shows " + shown + ", expected " + key);
	}

	// ---- the real flow helpers ----------------------------------------------------------------------------------------

	/** Places a table two blocks east of the player (fresh block entity) and opens it by using the block from the client. */
	private BlockPos placeAndOpen(ClientGameTestContext context, TestServerContext world, TableType<?> type, Class<? extends Screen> screen,
			String name) {
		return placeAndOpen(context, world, type, screen, name, 5000);
	}

	private BlockPos placeAndOpen(ClientGameTestContext context, TestServerContext world, TableType<?> type, Class<? extends Screen> screen,
			String name, long chips) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		int n = tableNo++;
		world.runCommand("casino balance set @p " + chips); // every table starts with the same chips (a lost round must not starve the next)
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

	// ---- table UI v2 (docs/architecture/ui-v2.md): clicks by HitMap id at the real window pixel -----------------------

	private static double @org.jspecify.annotations.Nullable [] v2Point(Minecraft mc, String id, boolean enabledOnly) {
		if (!(mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h)) return null;
		var chrome = h.chrome();
		for (var t : chrome.hits().targets()) {
			if (!t.id().equals(id) || enabledOnly && !t.enabled()) continue;
			var vp = chrome.viewport();
			return new double[] {vp.toWindow(t.box().x() + t.box().w() / 2.0), vp.toWindow(t.box().y() + t.box().h() / 2.0)};
		}
		return null;
	}

	/** Clicks v2 target {@code id} (enabled or not: a disabled one shows its reason); a failure when it is missing. */
	private boolean clickV2(ClientGameTestContext context, String name, String id) {
		double[] at = context.computeOnClient(mc -> v2Point(mc, id, false));
		if (at == null) {
			failures.add(name + ": no v2 target '" + id + "' (" + context.computeOnClient(mc -> String.valueOf(mc.gui.screen())) + ")");
			return false;
		}
		click(context, at[0], at[1]);
		return true;
	}

	private boolean tryV2(ClientGameTestContext context, String id) {
		double[] at = context.computeOnClient(mc -> v2Point(mc, id, true));
		if (at == null) return false;
		click(context, at[0], at[1]);
		return true;
	}

	private static boolean primaryIs(ClientGameTestContext context, String action) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h
			&& h.chrome().primary.gate().enabled() && h.chrome().primary.gate().action().equals(action));
	}

	/** Clicks the tray chip of {@code denom} (or the largest below it). */
	private boolean clickV2Chip(ClientGameTestContext context, String name, long denom) {
		int index = context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h)) return -1;
			long[] chips = h.chrome().bet.chips();
			int best = -1;
			for (int i = 0; i < chips.length; i++) if (chips[i] <= denom) best = i;
			return best;
		});
		if (index < 0) {
			failures.add(name + ": no v2 chip tray");
			return false;
		}
		return clickV2(context, name, "chip." + index);
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
