package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Placed;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Spot;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Target;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * REAL PLAYER in a placed casino (lane CB-5, architecture §9.3): the player starts outside, WALKS in with the W key
 * along a path through the door, turns to every game block in the building and right-clicks it with the mouse; the
 * game's screen must open on that block. At every family's plain table a bet is placed through the screen's own
 * widgets (looked up by their label keys, clicked with the mouse) at a 1920×1080 window with GUI scale 4 (Auto) and 3;
 * where no widget path is known the bet goes through the same client→server packet the screen sends. The server
 * must show the stake / money movement. Report: {@code casino_qa/jcasino_play_report.txt}.
 *
 * <p>Runs on the v0.2 tudor casino when it exists, else on the v0.1 plains village casino.
 */
public class CasinoPlayClientGameTests implements FabricClientGameTest {
	private static final Transaction QA = Transaction.of("casino_qa", "client_gametest");
	/** Widget label keys that move a bet forward, in the order a player would press them. */
	private static final List<String> FORWARD = List.of(".sit", ".sit_down", ".play", ".join", ".buy_in", ".deal", ".spin", ".drop", ".roll",
		".bet", ".place_bet", ".confirm", ".buy");
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		int[] window = context.computeOnClient(mc -> new int[] {mc.getWindow().getWidth(), mc.getWindow().getHeight()});
		int scaleBefore = context.computeOnClient(mc -> mc.options.guiScale().get());
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create(); var q = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.getInput().resizeWindow(1920, 1080);
			world.getServer().runCommand("gamemode survival @p");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("give @p emerald 16");
			world.getServer().runOnServer(server -> {
				ServerPlayer p = CasinoClientQa.player(server);
				CasinoQa.moneyMoves(p);
				Economies.get().setBalance(server, p.getUUID(), 1_000_000, QA);
				dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false;
			});
			Target t = world.getServer().computeOnServer(server -> CasinoClientQa.defaultTarget(server, "riviera"));
			Placed placed = CasinoClientQa.place(world, t, new BlockPos(0, 0, 40), 2);
			report.add("casino " + t + " box " + placed.box() + ", " + placed.spots().size() + " game blocks");
			BlockPos start = world.getServer().computeOnServer(server -> CasinoClientQa.outside(server.overworld(), placed.box(),
				placed.spots().getFirst().pos(), 8));
			world.getServer().runCommand("tp @p " + start.getX() + " " + start.getY() + " " + start.getZ());
			context.waitTicks(20);

			CasinoClientQa.guiScale(context, 4);
			Set<Game.Family> betDone = EnumSet.noneOf(Game.Family.class);
			for (Spot s : placed.spots()) {
				visit(context, world, placed, s);
				Game.Family f = s.game().family();
				if (s.game() == CasinoQa.baseGame(f) && !betDone.contains(f) && isOpen(context, s)) {
					bet(context, world, s, 4);
					betDone.add(f);
				}
				CasinoClientQa.closeScreen(context);
			}
			// GUI scale 3 (1920×1080 at a manual scale): the same bets through the same widgets
			CasinoClientQa.guiScale(context, 3);
			for (Map.Entry<Game.Family, Spot> e : placed.firstBaseSpotPerFamily().entrySet()) {
				Spot s = e.getValue();
				if (s.game() != CasinoQa.baseGame(e.getKey())) {
					continue;
				}
				visit(context, world, placed, s);
				if (isOpen(context, s)) {
					bet(context, world, s, 3);
				}
				CasinoClientQa.closeScreen(context);
			}
			Set<Game.Family> missing = EnumSet.copyOf(Game.Family.REQUIRED);
			missing.removeAll(placed.families());
			if (t.full() && !missing.isEmpty()) {
				failures.add("families missing in the building: " + missing);
			}
			CasinoClientQa.closeScreen(context);
			write(); // before the world closes: the report survives a hung close
			context.waitTicks(40);
		} finally {
			CasinoClientQa.guiScale(context, scaleBefore);
			context.getInput().resizeWindow(window[0], window[1]);
			write();
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("casino play (real player) failed:\n  " + String.join("\n  ", failures));
		}
	}

	/** Walks to the spot's player cell and right-clicks the block; records a failure unless its screen opens. */
	private void visit(ClientGameTestContext context, TestSingleplayerContext world, Placed placed, Spot s) {
		String err = CasinoClientQa.walkTo(context, world, placed.box(), s.playerCell());
		if (err != null) {
			failures.add(s + ": walking in failed: " + err);
			BlockPos c = s.playerCell();
			world.getServer().runCommand("tp @p " + c.getX() + " " + c.getY() + " " + c.getZ());
			context.waitTicks(5);
		}
		CasinoClientQa.rightClick(context, s.pos());
		try {
			context.waitFor(mc -> screenAt(mc, s.pos()), 60);
			report.add("opened " + s + " -> " + context.computeOnClient(mc -> mc.gui.screen().getClass().getSimpleName()));
		} catch (Throwable e) {
			String open = context.computeOnClient(mc -> mc.gui.screen() == null ? "nothing" : mc.gui.screen().getClass().getSimpleName());
			failures.add(s + ": right-click did not open its screen (open: " + open + ")");
		}
	}

	private static boolean screenAt(net.minecraft.client.Minecraft mc, BlockPos pos) {
		return mc.gui.screen() instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof CasinoTableMenu m && m.pos().equals(pos);
	}

	private static boolean isOpen(ClientGameTestContext context, Spot s) {
		return context.computeOnClient(mc -> screenAt(mc, s.pos()));
	}

	/** Server truth: a stake on the table or any money movement since {@code moves}. */
	private static boolean betSeen(TestSingleplayerContext world, Spot s, int moves) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer p = CasinoClientQa.player(server);
			return CasinoQa.moneyMoves(p) != moves
				|| server.overworld().getBlockEntity(s.pos()) instanceof CasinoTableBlockEntity be && be.hasStake(p.getUUID());
		});
	}

	private static int moves(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> CasinoQa.moneyMoves(CasinoClientQa.player(server)));
	}

	private record WidgetInfo(String key, double x, double y, boolean active) {}

	/** The screen's clickable widgets with their label keys and window positions. */
	private static List<WidgetInfo> widgets(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			List<WidgetInfo> out = new ArrayList<>();
			if (mc.gui.screen() == null) {
				return out;
			}
			for (var child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget w && w.visible) {
					double[] c = CasinoClientQa.windowPoint(mc, w);
					out.add(new WidgetInfo(CasinoClientQa.key(w.getMessage()), c[0], c[1], w.active));
				}
			}
			return out;
		});
	}

	/** Places one bet like a player: forward widgets by mouse first, the screen's own packet as the fallback. */
	private void bet(ClientGameTestContext context, TestSingleplayerContext world, Spot s, int scale) {
		int moves = moves(world);
		List<String> clicked = new ArrayList<>();
		for (int step = 0; step < 6; step++) {
			WidgetInfo next = null;
			for (String suffix : FORWARD) {
				for (WidgetInfo w : widgets(context)) {
					if (w.active() && w.key().endsWith(suffix) && !clicked.contains(w.key())) {
						next = w;
						break;
					}
				}
				if (next != null) {
					break;
				}
			}
			if (next == null) {
				break;
			}
			context.getInput().setCursorPos(next.x(), next.y());
			context.getInput().pressMouse(Inputs.MOUSE_LEFT);
			clicked.add(next.key());
			context.waitTicks(6);
			if (betSeen(world, s, moves)) {
				report.add("gui " + scale + ": " + s.game().family() + " bet by clicking " + clicked);
				return;
			}
		}
		List<String> seen = widgets(context).stream().map(w -> w.key() + (w.active() ? "" : "(off)")).toList();
		// fallback: the very packet the screen sends for this game (real client→server networking)
		for (long amount : new long[] {10, 25, 100, 500, 2500}) {
			for (Map.Entry<String, CompoundTag> e : CasinoQa.betActions(s.game().family(), amount)) {
				context.runOnClient(mc -> ClientPlayNetworking.send(new TableActionPayload(s.pos(), e.getKey(), e.getValue())));
			}
			context.waitTicks(4);
			if (betSeen(world, s, moves)) {
				report.add("gui " + scale + ": " + s.game().family() + " bet via client packet (no widget path; clicked " + clicked
					+ ", widgets " + seen + ")");
				return;
			}
		}
		failures.add("gui " + scale + ": " + s + ": no bet registered on the server (clicked " + clicked + ", widgets " + seen + ")");
	}

	private void write() {
		try {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("casino_qa");
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>(report);
			lines.add("failures: " + failures.size());
			lines.addAll(failures);
			Files.write(dir.resolve("jcasino_play_report.txt"), lines);
		} catch (java.io.IOException e) {
			CasinoQa.log("cannot write the play report: {}", e.toString());
		}
	}
}
