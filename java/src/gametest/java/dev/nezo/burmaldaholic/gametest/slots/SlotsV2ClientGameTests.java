package dev.nezo.burmaldaholic.gametest.slots;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotMachinesV2;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.client.SlotMachineV2Screen;
import dev.nezo.burmaldaholic.games.slots.client.SlotPreviewScreen;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRng;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFrames;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewTapes;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * Slots v2 screen (lane J-L9, JS17; slots.md §2.1 fidelity): opens the screen on fixed tapes and asserts the
 * TERMINAL WINDOW equals the tape's window — played in real time, with skip (fast-forward), with an interrupt (F8)
 * and with reduce motion — and that the played timeline has the length the pure builder computes (the server's
 * reveal gate). EN and RU at GUI scale 2 and 4 (compact layout): screenshots {@code jtest_slots_<lang>_<case>} and a
 * layout report {@code jtest_slots_layout.txt} (labels wider than their button, widgets off screen).
 */
public class SlotsV2ClientGameTests implements FabricClientGameTest {
	/** Short tapes played in real time; the others are fast-forwarded. */
	private static final Set<String> REAL_TIME = Set.of("ow_loss", "ow_returned", "ow_win", "ow_anticipation", "ne_tumble");

	private final List<String> failures = new ArrayList<>();
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 1000000");
			// no chaos events from the real rounds (a Creeper-first hunt calls a mob wave that would kill the player
			// during the preview part); restored below
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			for (Tier tier : Tier.values()) real(context, world, tier, false);
			real(context, world, Tier.COPPER, true);
			for (String name : PreviewTapes.NAMES) play(context, name, REAL_TIME.contains(name) ? Mode.REAL_TIME : Mode.SKIP);
			play(context, "end_big", Mode.INTERRUPT);
			play(context, "ne_tumble", Mode.REDUCED);
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = true);
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				for (int scale : new int[] {2, 4}) {
					guiScale(context, scale);
					for (Machine m : Machine.values()) shots(context, lang + "_" + m.id + "_s" + scale, m);
				}
			}
		} finally {
			guiScale(context, 0);
			language(context, "en_us");
			context.runOnClient(mc -> FxSettings.get().reduceMotion = false);
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("slots v2 screen: " + String.join("; ", failures));
	}

	private enum Mode {
		REAL_TIME,
		SKIP,
		INTERRUPT,
		REDUCED
	}

	// ---- the real machine screen on a real cabinet (server round, SLOTS.md §10.5) -------------------------------

	/**
	 * Places a cabinet next to the player, opens the machine screen and spins on the server with a seeded draw (a base win,
	 * or a Treasure Hunt picked through the protocol). Screenshots {@code jtest_slots_real_<machine>_{spinning,end}} (hunt:
	 * {@code _hunt}); the screen must end on the server's rest window and show the settled spin total.
	 */
	private void real(ClientGameTestContext context, TestSingleplayerContext world, Tier tier, boolean hunt) {
		String name = "real_" + tier.id() + (hunt ? "_hunt" : "");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		long[] expected = new long[1];
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.closeContainer();
			BlockPos pos = player.blockPosition().offset(2, 0, tier.ordinal());
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos, SlotsModule.MACHINES.get(tier).block().defaultBlockState());
			if (!(server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be)) return;
			SlotMachinesV2.cfg(be.machineV2()).minVipTier = 0;
			be.drawForTesting(req -> {
				for (int k = 1; k < 5_000_000; k++) {
					SpinTape t = SlotDraw.draw(req, SlotRng.seeded(k));
					boolean ok = hunt ? t.hunt() != null && t.freeSpins() == null
						: t.totalFifths() >= 25 && !t.featureTriggered() && t.jackpots().isEmpty();
					if (ok) return t;
				}
				throw new IllegalStateException("no tape");
			});
			player.openMenu(be);
			CompoundTag args = new CompoundTag();
			args.putLong("bet", be.writeClientState(player).getCompoundOrEmpty("v2").getLongArray("bets").orElse(new long[] {5})[0]);
			be.onAction(player, "spin", args);
			be.drawForTesting(null);
			expected[0] = be.roundTape() == null ? -1 : be.roundTape().totalChips();
			be.sendStateTo(player);
		});
		try {
			context.waitFor(mc -> mc.gui.screen() instanceof SlotMachineV2Screen s && s.body() != null && s.body().stage().active(), 200);
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": machine screen did not open / spin");
			return;
		}
		context.waitTicks(12);
		context.takeScreenshot("jtest_slots_" + name + "_spinning");
		if (hunt) {
			try {
				context.waitFor(mc -> realStage(mc) != null && realStage(mc).clock().holding(), 400);
			} catch (RuntimeException | AssertionError e) {
				failures.add(name + ": the hunt did not wait for picks");
			}
			context.waitTicks(10);
			context.takeScreenshot("jtest_slots_" + name + "_board");
			for (int i = 0; i < 15; i++) {
				int chest = i;
				context.runOnClient(mc -> {
					SlotStage st = realStage(mc);
					if (st != null && st.hunt().awaitingPick(st)) st.click(st.cellX(chest % 5) + 2, st.cellY(chest / 5) + 2);
				});
				context.waitTicks(15);
			}
			context.takeScreenshot("jtest_slots_" + name + "_hunt");
		}
		try {
			context.waitFor(mc -> realStage(mc) != null && realStage(mc).finished(), 2400);
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": did not finish in time");
		}
		context.waitTicks(10);
		context.takeScreenshot("jtest_slots_" + name + "_end");
		int[] rest = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos pos = player.blockPosition().offset(2, 0, tier.ordinal());
			return server.overworld().getBlockEntity(pos) instanceof SlotMachineBlockEntity be ? be.restCells() : null;
		});
		String problem = context.computeOnClient(mc -> {
			SlotStage st = realStage(mc);
			if (st == null) return "no stage";
			if (rest != null && !Arrays.equals(st.frames().snapshot().cells(), rest)) return "screen ends off the server's rest window";
			long shown = st.bigWin().panelAmount(st);
			return shown == expected[0] ? null : "screen shows " + shown + ", server settled " + expected[0];
		});
		if (problem != null) failures.add(name + ": " + problem);
		world.getServer().runOnServer(server -> {
			server.getPlayerList().getPlayers().getFirst().closeContainer();
			SlotMachinesV2.cfg(SlotMachinesV2.machine(tier)).minVipTier = tier == Tier.NETHERITE ? 2 : 0;
		});
		context.waitTicks(5);
	}

	private static SlotStage realStage(Minecraft mc) {
		return mc.gui.screen() instanceof SlotMachineV2Screen s && s.body() != null ? s.body().stage() : null;
	}

	private static Machine machineOf(String name) {
		return name.startsWith("ow_") ? Machine.OVERWORLD : name.startsWith("ne_") ? Machine.NETHER : Machine.END;
	}

	private void play(ClientGameTestContext context, String name, Mode mode) {
		PreviewTapes.Scenario s = PreviewTapes.get(name);
		context.runOnClient(mc -> {
			FxSettings.get().reduceMotion = mode == Mode.REDUCED;
			SlotPreviewScreen screen = new SlotPreviewScreen(machineOf(name), name).autoFeatures(true);
			mc.gui.setScreen(screen);
			screen.playNow(name);
		});
		context.waitTicks(3);
		String tag = name + "/" + mode.name().toLowerCase(java.util.Locale.ROOT);
		// the played timeline has the length the pure builder gives the server (reveal gate)
		boolean lengthOk = context.computeOnClient(mc -> {
			SlotStage st = stage(mc);
			if (st == null || st.script() == null) return false;
			Timeline expected = SlotTimeline.build(s.tape(), s.def(), TimingProfile.SHARED, FxSettings.localProfile(), 1, true, null);
			return st.script().timeline().sharedEndMs() == expected.sharedEndMs() && st.script().timeline().beats().size() == expected.beats().size();
		});
		if (!lengthOk) failures.add(tag + ": timeline length differs from the builder");
		switch (mode) {
			case REAL_TIME, REDUCED -> {
				if (name.equals("ow_win")) {
					context.waitTicks(12);
					context.takeScreenshot("jtest_slots_spinning_" + name);
				}
				try {
					context.waitFor(mc -> {
						SlotStage st = stage(mc);
						return st != null && st.finished();
					}, 1200);
				} catch (RuntimeException | AssertionError e) {
					failures.add(tag + ": did not finish in time");
				}
			}
			case SKIP -> {
				for (int i = 0; i < 400; i++) {
					boolean done = context.computeOnClient(mc -> {
						SlotStage st = stage(mc);
						if (st == null) return true;
						if (!st.finished()) st.skip();
						return st.finished();
					});
					if (done) break;
					context.waitTicks(5);
				}
			}
			case INTERRUPT -> {
				context.waitTicks(10);
				context.runOnClient(mc -> {
					SlotStage st = stage(mc);
					if (st != null) st.finishNow();
				});
				context.waitTicks(2);
			}
		}
		String problem = context.computeOnClient(mc -> {
			SlotStage st = stage(mc);
			if (st == null || st.frames() == null) return "no stage (screen " + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()) + ")";
			SlotFrames.Frame shown = st.frames().snapshot();
			SlotFrames.Frame terminal = SlotFrames.INSTANCE.terminal(new SlotFrames.Outcome(s.def(), s.tape(), s.restStops(), null, s.terminalCells()));
			if (!Arrays.equals(shown.cells(), terminal.cells())) return "terminal window " + Arrays.toString(shown.cells()) + " != " + Arrays.toString(terminal.cells());
			return null;
		});
		if (problem != null) failures.add(tag + ": " + problem);
		if (mode == Mode.REAL_TIME) context.takeScreenshot("jtest_slots_end_" + name);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
	}

	private static SlotStage stage(Minecraft mc) {
		return mc.gui.screen() instanceof SlotPreviewScreen p ? p.body().stage() : null;
	}

	private void shots(ClientGameTestContext context, String name, Machine m) {
		context.runOnClient(mc -> mc.gui.setScreen(new SlotPreviewScreen(m, null).autoFeatures(true)));
		context.waitTicks(10);
		context.takeScreenshot("jtest_slots_" + name);
		for (String p : context.computeOnClient(mc -> inspect(mc, mc.gui.screen()))) report.add(name + ": " + p);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
	}

	private static List<String> inspect(Minecraft mc, Screen screen) {
		List<String> out = new ArrayList<>();
		if (screen == null) return out;
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) continue;
			String label = w.getMessage().getString();
			if (mc.font.width(w.getMessage()) > 2 * (w.getWidth() - 4)) out.add("label far wider than its button: \"" + label + "\"");
			if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height) {
				out.add("widget outside the screen: \"" + label + "\"");
			}
		}
		return out;
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>(report);
			lines.addAll(failures);
			Files.writeString(dir.resolve("jtest_slots_layout.txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(20);
	}
}
