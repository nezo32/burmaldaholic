package dev.nezo.burmaldaholic.gametest.v2.craps;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieAtlasTable;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.client.CrapsBoardRenderer;
import dev.nezo.burmaldaholic.games.craps.client.CrapsScreen;
import dev.nezo.burmaldaholic.games.craps.logic.BetKind;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Phase 2b client GameTests of lane CM (craps motion, CRAPS-ANIM-SPEC §9.3). Run alone with
 * {@code BURMALDAHOLIC_CGT=CM}.
 *
 * <p>Screen: the player opens a table by using it and bets with real clicks; the rolls are forced on the server
 * ({@code be.roll(d1, d2)}: 5+3 point set, 3+3 come travel, 4+3 seven-out with a come point, 1+1 craps on the come-out,
 * 6+5 natural) and the shared clock is frozen at storyboard beats ({@link AnimClock#freezeForTest}). Shots
 * {@code p2b_cm_craps_<case>_<beat>_<lang>_<w>} at 427 / 480 / 640 / 960 (+ framed 1280×720 when the desktop allows
 * 2560×1440) and RU. Probes on the real framebuffer: a die mid-flight sits on bone or pip ink, the resting dice are
 * exactly {@code cell·k} px wide and show the server's pips, the hit diamond is white one frame after the wall, the
 * dice never snap after rest, and after the stick they sit at dice home with the same faces. Reduced motion at 100 /
 * 300 ms.
 *
 * <p>World: full 3×2 tables in the 4 FACINGs rolled together; at 4 blocks mid-flight the dice are drawn above the
 * felt, at rest they show the server's faces; a viewer 24 blocks away sees NO dice before the throw rests (the
 * spoiler fix) and the dice at rest after. Shots {@code p2b_cm_craps_world_<facing>_<d>_<flight|rest>}.
 */
public class CrapsAnimClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("CM")) return;
		List<String> errors = new ArrayList<>();
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 20000");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			RealFlow f = new RealFlow(context, world);
			try {
				screen(f, errors);
				inWorld(f, errors);
			} finally {
				context.runOnClient(mc -> AnimClock.unfreezeForTest());
				CrapsBoardRenderer.probePos = null;
				FxSettings.update(d -> d.reduceMotion = false);
				f.close();
				f.language("en_us");
				f.size(1280, 800, 0);
			}
		}
		if (!errors.isEmpty()) throw new AssertionError("craps motion probes (" + errors.size() + "):\n" + String.join("\n", errors));
	}

	// ---- screen -----------------------------------------------------------------------------------------------------

	private record Size(int fw, int fh, int gs) {}

	private static final Size S427 = new Size(1280, 720, 3), S480 = new Size(1920, 1080, 4), S640 = new Size(1920, 1080, 3), S960 = new Size(1920, 1080, 2),
		FRAMED = new Size(2560, 1440, 2), K4_AUTO = new Size(3840, 2160, 0);

	private void screen(RealFlow f, List<String> errors) {
		f.size(S480.fw, S480.fh, S480.gs);
		BlockPos pos = f.place(CrapsModule.TABLE.block(), -3);
		f.open(pos, CrapsScreen.class);
		// a fresh player: the Pass click seats and bets; a Field bet beside it
		f.click(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS), "Pass bet");
		f.click(CrapsLayout.FIELD);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.FIELD), "Field bet");

		// case 1: 5 + 3 sets the point (the puck flips over to 8); the full beat strip at 480, the key beats everywhere
		Roll r = roll(f, pos, 5, 3);
		strip(f, r, "point", errors);
		for (Size s : new Size[] {K4_AUTO, S427, S640, S960, FRAMED}) {
			if (!resize(f, s, errors)) continue;
			sized(f, r, "point", errors);
		}
		resize(f, S480, errors);

		// case 2: a Come bet travels to 6 (3 + 3), then 4 + 3 sevens out: one wave takes the Pass, its odds, the come point
		settle(f, r);
		f.click(CrapsLayout.PASS_ODDS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.odds() > 0), "odds");
		f.click(CrapsLayout.COME);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.COME), "Come bet");
		Roll travel = roll(f, pos, 3, 3);
		shot(f, travel, travel.beats.TRAVEL + 200, "come", "travel_mid");
		shot(f, travel, travel.beats.TRAVEL + 500, "come", "travel_landed");
		settle(f, travel);
		Roll seven = roll(f, pos, 4, 3);
		shot(f, seven, seven.beats.rest + 40, "seven_out", "rest");
		shot(f, seven, seven.beats.BADGE + 120, "seven_out", "badge");
		shot(f, seven, seven.beats.PUCK + 300, "seven_out", "puck_glide");
		shot(f, seven, seven.beats.CHIPS + 150, "seven_out", "take_mid");
		shot(f, seven, seven.beats.TRAVEL + 400, "seven_out", "taken");

		// case 3: 1 + 1 on the come-out (craps): the Pass line is taken
		settle(f, seven);
		f.click(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS), "Pass again");
		Roll craps = roll(f, pos, 1, 1);
		shot(f, craps, craps.beats.BADGE + 120, "craps", "badge");
		shot(f, craps, craps.beats.CHIPS + 150, "craps", "take_mid");

		// case 4: 6 + 5 natural: paid beside the bet, then collected to the balance; RU on the same roll
		settle(f, craps);
		f.click(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS), "Pass again");
		Roll natural = roll(f, pos, 6, 5);
		shot(f, natural, natural.beats.BADGE + 120, "natural", "badge");
		shot(f, natural, natural.beats.TRAVEL + 150, "natural", "pay_mid");
		shot(f, natural, natural.beats.COLLECT + 200, "natural", "collect_mid");
		f.language("ru_ru");
		shot(f, natural, natural.beats.BADGE + 400, "natural", "badge", "ru_ru");
		shot(f, natural, natural.beats.IDLE + 1, "natural", "idle", "ru_ru");
		for (Size s : new Size[] {K4_AUTO, S427, S640}) {
			if (!resize(f, s, errors)) continue;
			shot(f, natural, natural.beats.BADGE + 400, "natural", "badge", "ru_ru");
			shot(f, natural, natural.beats.IDLE + 1, "natural", "idle", "ru_ru");
		}
		resize(f, S480, errors);
		f.language("en_us");

		// reduced motion: no arcs, the dice fade in at rest, the wall shows a static HOT
		settle(f, natural);
		FxSettings.update(d -> d.reduceMotion = true);
		f.click(CrapsLayout.PASS);
		f.waitServer(pos, CrapsTableBlockEntity.class, be -> be.table().bets().stream().anyMatch(b -> b.kind() == BetKind.PASS), "Pass (reduced)");
		Roll red = roll(f, pos, 2, 4);
		shot(f, red, 100, "reduced", "100");
		shot(f, red, 300, "reduced", "300");
		FxSettings.update(d -> d.reduceMotion = false);
		f.context().runOnClient(mc -> AnimClock.unfreezeForTest());
		f.close();
		f.server(server -> {
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			be.leave(RealFlow.player(server).getUUID(), dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason.LEFT);
			server.overworld().removeBlock(pos, false);
			return null;
		});
		f.context().waitTicks(4);
	}

	/** A forced roll as the screen sees it. */
	private record Roll(long rollTime, int rolls, CrapsBeats beats, int restMs, int[] wallMs, int side, int d1, int d2) {}

	private static Roll roll(RealFlow f, BlockPos pos, int d1, int d2) {
		f.context().runOnClient(mc -> AnimClock.unfreezeForTest());
		int n = f.server(server -> {
			CrapsTableBlockEntity be = (CrapsTableBlockEntity) server.overworld().getBlockEntity(pos);
			be.roll(d1, d2);
			be.syncViewers();
			return be.table().rollCount();
		});
		long rollTime = f.server(server -> ((CrapsTableBlockEntity) server.overworld().getBlockEntity(pos)).sync().rollTime());
		f.context().waitFor(mc -> mc.gui.screen() instanceof CrapsScreen s && s.shownRoll() != null && s.storyMs() < 3000 && rollsShown(s) == n, RealFlow.TIMEOUT);
		return f.client(mc -> {
			CrapsScreen s = (CrapsScreen) mc.gui.screen();
			DiceRoll dr = s.shownRoll();
			return new Roll(rollTime, n, s.beats(), dr.restMs(), new int[] {dr.wallMs(0), dr.wallMs(1)}, s.shownSide(), d1, d2);
		});
	}

	private static int rollsShown(CrapsScreen s) {
		return s.shownRolls();
	}

	/** Lets the roll play out (real clock) until Roll / the felt open again. */
	private static void settle(RealFlow f, Roll r) {
		f.context().runOnClient(mc -> AnimClock.unfreezeForTest());
		f.context().waitFor(mc -> mc.gui.screen() instanceof CrapsScreen s && s.storyMs() > s.beats().IDLE, RealFlow.TIMEOUT);
		f.context().waitTicks(2);
	}

	private static void freeze(RealFlow f, Roll r, double t) {
		double level = r.rollTime * 50.0 + t;
		f.context().runOnClient(mc -> AnimClock.freezeForTest(level));
		f.context().waitTicks(3);
	}

	private static Path shot(RealFlow f, Roll r, double t, String kase, String beat) {
		return shot(f, r, t, kase, beat, "en_us");
	}

	private static Path shot(RealFlow f, Roll r, double t, String kase, String beat, String lang) {
		freeze(f, r, t);
		return f.shot("p2b_cm_craps_" + kase + "_" + beat + "_" + lang + "_" + f.viewport().w());
	}

	private static boolean resize(RealFlow f, Size s, List<String> errors) {
		f.size(s.fw, s.fh, s.gs);
		int[] fb = f.client(mc -> new int[] {mc.getWindow().getWidth(), mc.getWindow().getHeight()});
		if (fb[0] != s.fw || fb[1] != s.fh) {
			System.out.println("[craps CM] window " + s.fw + "x" + s.fh + " clamped to " + fb[0] + "x" + fb[1] + "; skipped");
			return false;
		}
		f.context().waitTicks(3);
		return true;
	}

	/** The full beat strip of case 1 at the current size, with the probes. */
	private void strip(RealFlow f, Roll r, String kase, List<String> errors) {
		int wall = Math.min(r.wallMs[0], r.wallMs[1]);
		shot(f, r, 0, kase, "t0000");
		Path mid = shot(f, r, 150, kase, "t0150");
		probeFlight(f, mid, 150, errors);
		shot(f, r, Math.max(160, wall - 20), kase, "wall_m20");
		Path hit = shot(f, r, wall + 34, kase, "wall_p34");
		probeWall(f, r, hit, errors);
		shot(f, r, wall + 230, kase, "b1");
		shot(f, r, r.restMs - 300, kase, "slide");
		shot(f, r, r.restMs - 60, kase, "rest_m60");
		shot(f, r, r.restMs, kase, "rest");
		Path rest = shot(f, r, r.restMs + 100, kase, "rest_p100");
		probeRest(f, r, rest, errors);
		shot(f, r, r.beats.BADGE + 120, kase, "badge");
		shot(f, r, r.beats.PUCK + CrapsBeats.PUCK_RISE + CrapsBeats.PUCK_FLIP / 2.0, kase, "puck_flip");
		shot(f, r, r.beats.PUCK + 400, kase, "puck_glide");
		shot(f, r, r.beats.CHIPS + 150, kase, "take_mid");
		shot(f, r, r.beats.TRAVEL + 150, kase, "pay_mid");
		shot(f, r, r.beats.stickHook() + 20, kase, "stick_hook");
		shot(f, r, (r.beats.stickDrag() + r.beats.stickOut()) / 2, kase, "stick_drag");
		Path idle = shot(f, r, r.beats.IDLE + 1, kase, "idle");
		probeHome(f, r, idle, errors);
		// no snap: the dice at rest + 100 and rest + 600 are the same
		double[][] a = f.client(mc -> centres((CrapsScreen) mc.gui.screen(), r.restMs + 100));
		double[][] b = f.client(mc -> centres((CrapsScreen) mc.gui.screen(), r.restMs + 600));
		for (int d = 0; d < 2; d++) {
			if (a[d][0] != b[d][0] || a[d][1] != b[d][1]) errors.add("die " + d + " moved after rest: " + a[d][0] + "," + a[d][1] + " -> " + b[d][0] + "," + b[d][1]);
		}
	}

	private void sized(RealFlow f, Roll r, String kase, List<String> errors) {
		int wall = Math.min(r.wallMs[0], r.wallMs[1]);
		Path mid = shot(f, r, (wall + 150) / 2.0, kase, "mid_throw");
		probeFlight(f, mid, (wall + 150) / 2.0, errors);
		Path hit = shot(f, r, wall + 34, kase, "wall_p34");
		probeWall(f, r, hit, errors);
		Path rest = shot(f, r, r.restMs + 100, kase, "rest_p100");
		probeRest(f, r, rest, errors);
		shot(f, r, r.beats.PUCK + 400, kase, "puck_glide");
		Path idle = shot(f, r, r.beats.IDLE + 1, kase, "idle");
		probeHome(f, r, idle, errors);
	}

	private static double[][] centres(CrapsScreen s, double t) {
		return new double[][] {s.dieCentre(0, t), s.dieCentre(1, t)};
	}

	private static BufferedImage read(Path p, List<String> errors) {
		try {
			return ImageIO.read(p.toFile());
		} catch (Exception e) {
			errors.add("cannot read " + p + ": " + e);
			return null;
		}
	}

	/** A framed viewport (RS: 1280×720 → 1067×600 frame): probes skipped, the shots are looked at instead. */
	private static boolean framed(RealFlow f) {
		Viewport vp = f.viewport();
		return vp.frameW() != vp.w() || vp.frameH() != vp.h();
	}

	/** Mid-flight: the framebuffer at each die centre is die bone or pip ink. */
	private static void probeFlight(RealFlow f, Path shot, double t, List<String> errors) {
		if (framed(f)) return;
		BufferedImage img = read(shot, errors);
		if (img == null) return;
		Viewport vp = f.viewport();
		double[][] c = f.client(mc -> centres((CrapsScreen) mc.gui.screen(), t));
		for (int d = 0; d < 2; d++) {
			int x = (int) Math.round(vp.windowX(c[d][0]) * scale(f, img));
			int y = (int) Math.round(vp.windowY(c[d][1]) * scale(f, img));
			int rgb = img.getRGB(clamp(x, img.getWidth()), clamp(y, img.getHeight()));
			if (!bone(rgb) && !ink(rgb)) errors.add(vp.w() + " t " + t + " die " + d + " centre " + x + "," + y + " is " + Integer.toHexString(rgb));
		}
	}

	/** One frame after the wall the hit diamond is white (flashes on). */
	private static void probeWall(RealFlow f, Roll r, Path shot, List<String> errors) {
		if (framed(f)) return;
		BufferedImage img = read(shot, errors);
		if (img == null) return;
		Viewport vp = f.viewport();
		int first = r.wallMs[0] <= r.wallMs[1] ? 0 : 1;
		double hx = f.client(mc -> ((CrapsScreen) mc.gui.screen()).wallHitX(first));
		Box wall = f.client(mc -> ((CrapsScreen) mc.gui.screen()).layout().backWall);
		int di = (int) Math.floor((hx - wall.x()) / 4.0);
		double gx = wall.x() + di * 4 + 2;
		double gy = wall.y() + wall.h() / 2.0;
		double sc = scale(f, img);
		int best = 0;
		// the airborne die may cover the hit pyramid: its white also lights the neighbours for that frame
		for (int dx = -9; dx <= 9; dx++) {
			int x = (int) Math.round((vp.windowX(gx) + dx * vp.k() / 2.0) * sc);
			int y = (int) Math.round(vp.windowY(gy) * sc);
			int rgb = img.getRGB(clamp(x, img.getWidth()), clamp(y, img.getHeight()));
			best = Math.max(best, Math.min(Math.min((rgb >> 16) & 255, (rgb >> 8) & 255), rgb & 255));
		}
		if (best < 200) errors.add(vp.w() + " wall + 34: the hit diamond at " + gx + " is not white (min channel " + best + ")");
	}

	/** At rest: each die is exactly cell·k px wide and its top face shows the server's pips. */
	private static void probeRest(RealFlow f, Roll r, Path shot, List<String> errors) {
		if (framed(f)) return;
		BufferedImage img = read(shot, errors);
		if (img == null) return;
		Viewport vp = f.viewport();
		double sc = scale(f, img);
		int k = (int) Math.round(vp.k() * sc);
		int cell = f.client(mc -> ((CrapsScreen) mc.gui.screen()).layout().die);
		double t = r.restMs + 100;
		double[][] c = f.client(mc -> centres((CrapsScreen) mc.gui.screen(), t));
		int[] frames = f.client(mc -> new int[] {((CrapsScreen) mc.gui.screen()).dieFrame(0, t), ((CrapsScreen) mc.gui.screen()).dieFrame(1, t)});
		int ci = DieAtlasTable.cellIndex(cell);
		int fs = DieAtlasTable.FRAME[ci];
		for (int d = 0; d < 2; d++) {
			int ri = -1;
			for (int i = 0; i < DieAtlasTable.REST_FRAMES.length; i++) if (DieAtlasTable.REST_FRAMES[i] == frames[d]) ri = i;
			if (ri < 0) {
				errors.add(vp.w() + " die " + d + " at rest is not on a rest frame (" + frames[d] + ")");
				continue;
			}
			double[] top = DieAtlasTable.TOP[ci][ri];
			double ox = vp.windowX(c[d][0] - fs / 2.0) * sc;
			double oy = vp.windowY(c[d][1] - fs / 2.0) * sc;
			double minU = Math.min(Math.min(top[0], top[2]), Math.min(top[4], top[6]));
			double maxU = Math.max(Math.max(top[0], top[2]), Math.max(top[4], top[6]));
			double minV = Math.min(Math.min(top[1], top[3]), Math.min(top[5], top[7]));
			double maxV = Math.max(Math.max(top[1], top[3]), Math.max(top[5], top[7]));
			// width: the bone run through the top face's middle row, outline included, is cell·k
			int row = (int) Math.round(oy + (minV + maxV) / 2 * k);
			int first = -1, last = -1;
			for (int x = (int) Math.floor(ox); x < (int) Math.ceil(ox + fs * k); x++) {
				int rgb = img.getRGB(clamp(x, img.getWidth()), clamp(row, img.getHeight()));
				if (bone(rgb) || ink(rgb) || shade(rgb)) {
					if (first < 0) first = x;
					last = x;
				}
			}
			int width = last - first + 1;
			if (Math.abs(width - cell * k) > k) errors.add(vp.w() + " die " + d + ": width " + width + " != " + cell * k + " (k " + k + ")");
			int want = d == 0 ? r.d1 : r.d2;
			int n = clusters(img, (int) Math.ceil(ox + (minU + 1.5) * k), (int) Math.ceil(oy + (minV + 1.5) * k), (int) Math.floor(ox + (maxU - 1.5) * k),
				(int) Math.floor(oy + (maxV - 1.5) * k), want == 1);
			if (n != want) errors.add(vp.w() + " die " + d + ": " + n + " pips, want " + want);
		}
	}

	/** After the stick: both dice inside dice home, still showing the roll's faces (pips probed). */
	private static void probeHome(RealFlow f, Roll r, Path shot, List<String> errors) {
		if (framed(f)) return;
		Viewport vp = f.viewport();
		double t = r.beats.IDLE + 1;
		double[][] c = f.client(mc -> centres((CrapsScreen) mc.gui.screen(), t));
		CrapsLayout l = f.client(mc -> ((CrapsScreen) mc.gui.screen()).layout());
		Box home = l.diceHome(r.side).inset(-l.die / 2 - 1);
		for (int d = 0; d < 2; d++) {
			if (!home.contains(c[d][0], c[d][1])) errors.add(vp.w() + " die " + d + " after the stick at " + c[d][0] + "," + c[d][1] + " not in " + home);
		}
		BufferedImage img = read(shot, errors);
		if (img == null) return;
		double sc = scale(f, img);
		for (int d = 0; d < 2; d++) {
			int x = (int) Math.round(vp.windowX(c[d][0]) * sc);
			int y = (int) Math.round(vp.windowY(c[d][1]) * sc);
			int rgb = img.getRGB(clamp(x, img.getWidth()), clamp(y, img.getHeight()));
			if (!bone(rgb) && !ink(rgb) && !shade(rgb)) errors.add(vp.w() + " die " + d + " not drawn at home (" + Integer.toHexString(rgb) + ")");
		}
	}

	/** Screenshot px per window px (screenshots are the framebuffer). */
	private static double scale(RealFlow f, BufferedImage img) {
		int w = f.client(mc -> mc.getWindow().getWidth());
		return img.getWidth() / (double) w;
	}

	private static int clamp(int v, int n) {
		return Math.max(0, Math.min(n - 1, v));
	}

	private static boolean bone(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		return r > 190 && g > 180 && b > 180;
	}

	private static boolean shade(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		// the die's shaded sides: lilac greys (blue ≥ green, not the felt's green)
		return b >= g && r + g + b > 220 && Math.abs(r - b) < 70;
	}

	/** Die outline / pip ink (dark purple, never the felt's dark green) or the red ace. */
	private static boolean ink(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		return (r + g + b < 200 && b >= g) || (r > 150 && g < 110);
	}

	private static int clusters(BufferedImage img, int x0, int y0, int x1, int y1, boolean red) {
		int w = x1 - x0 + 1;
		int h = y1 - y0 + 1;
		if (w <= 0 || h <= 0) return -1;
		boolean[] seen = new boolean[w * h];
		int n = 0;
		ArrayDeque<int[]> st = new ArrayDeque<>();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				if (seen[y * w + x] || !pip(img.getRGB(clamp(x0 + x, img.getWidth()), clamp(y0 + y, img.getHeight())), red)) continue;
				n++;
				seen[y * w + x] = true;
				st.push(new int[] {x, y});
				while (!st.isEmpty()) {
					int[] p = st.pop();
					for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nx = p[0] + dd[0];
						int ny = p[1] + dd[1];
						if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny * w + nx]
							|| !pip(img.getRGB(clamp(x0 + nx, img.getWidth()), clamp(y0 + ny, img.getHeight())), red)) continue;
						seen[ny * w + nx] = true;
						st.push(new int[] {nx, ny});
					}
				}
			}
		return n;
	}

	private static boolean pip(int rgb, boolean red) {
		int r = (rgb >> 16) & 255;
		int g = (rgb >> 8) & 255;
		int b = rgb & 255;
		return red ? r > 150 && g < 110 : r + g + b < 200;
	}

	// ---- in-world ---------------------------------------------------------------------------------------------------

	private void inWorld(RealFlow f, List<String> errors) {
		f.size(1920, 1080, 4);
		f.context().runOnClient(mc -> {
			mc.options.hideGui = true; // clean shots: no HUD, chat or toasts
			mc.gui.getChat().clearMessages(false);
		});
		Direction[] facings = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
		BlockPos base = f.server(server -> RealFlow.player(server).blockPosition().offset(0, 0, 12));
		List<BlockPos> tables = new ArrayList<>();
		for (int i = 0; i < facings.length; i++) tables.add(base.offset(i * 8, 0, 0));
		long rollTime = f.server(server -> {
			var level = server.overworld();
			for (int i = 0; i < facings.length; i++) {
				BlockPos p = tables.get(i);
				for (int dx = -2; dx <= 2; dx++)
					for (int dz = -2; dz <= 2; dz++)
						for (int dy = 0; dy <= 2; dy++) level.setBlockAndUpdate(p.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				TableParts.placeFull(level, p, (MultiBlockTableBlock) CrapsModule.TABLE.block(), facings[i]);
			}
			long t = 0;
			for (BlockPos p : tables) {
				CrapsTableBlockEntity be = (CrapsTableBlockEntity) level.getBlockEntity(p);
				be.roll(5, 3);
				t = be.sync().rollTime();
			}
			return t;
		});
		f.context().waitTicks(10);
		CrapsTableBlockEntity probe = f.server(server -> (CrapsTableBlockEntity) server.overworld().getBlockEntity(tables.get(0)));
		int[] faces = {5, 3};
		DiceRoll throwRoll = dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.solve(probe.sync().shooterSide(), probe.sync().seed(), 5, 3, 0);
		int wall = Math.min(throwRoll.wallMs(0), throwRoll.wallMs(1));
		double flight = Math.min(wall + 120, throwRoll.restMs() - 300);
		double rest = throwRoll.restMs() + 150;
		for (int i = 0; i < facings.length; i++) {
			BlockPos p = tables.get(i);
			Direction fa = facings[i];
			CrapsBoardRenderer.probePos = p;
			for (int d : new int[] {4, 10}) {
				view(f, p, fa, d, d <= 4 ? 45f : 32f);
				if (d == 4) {
					// settled: the puck ON on the 8, the dice back at home, the wall and the rail groove
					freezeWorld(f, rollTime, CrapsBeats.of(throwRoll.restMs()).IDLE + 1);
					f.context().waitTicks(3);
					f.shot("p2b_cm_craps_world_" + fa.getSerializedName() + "_4_idle");
				}
				for (boolean atRest : new boolean[] {false, true}) {
					double t = atRest ? rest : flight;
					freezeWorld(f, rollTime, t);
					CrapsBoardRenderer.probeDice = -1;
					f.context().waitTicks(3);
					f.shot("p2b_cm_craps_world_" + fa.getSerializedName() + "_" + d + "_" + (atRest ? "rest" : "flight"));
					if (CrapsBoardRenderer.probeDice != 1) errors.add("world " + fa + " " + d + " m: no dice drawn (" + (atRest ? "rest" : "flight") + ")");
					if (!CrapsBoardRenderer.probeAboveFelt) errors.add("world " + fa + " " + d + " m: a die sinks into the felt");
					if (atRest && (CrapsBoardRenderer.probeFaces[0] != faces[0] || CrapsBoardRenderer.probeFaces[1] != faces[1]))
						errors.add("world " + fa + " rest faces " + CrapsBoardRenderer.probeFaces[0] + "," + CrapsBoardRenderer.probeFaces[1]);
				}
			}
		}
		// the far viewer: 24 blocks away no dice before the throw rests, the dice at rest after (spoiler fix)
		BlockPos p0 = tables.get(0);
		CrapsBoardRenderer.probePos = p0;
		view(f, p0, facings[0], 24, 12f);
		freezeWorld(f, rollTime, flight);
		CrapsBoardRenderer.probeDice = -1;
		f.context().waitTicks(3);
		f.shot("p2b_cm_craps_world_24_flight");
		if (CrapsBoardRenderer.probeDice != 0) errors.add("far viewer (24 m) sees dice before the rest: probe " + CrapsBoardRenderer.probeDice);
		freezeWorld(f, rollTime, rest);
		CrapsBoardRenderer.probeDice = -1;
		f.context().waitTicks(3);
		f.shot("p2b_cm_craps_world_24_rest");
		if (CrapsBoardRenderer.probeDice != 1) errors.add("far viewer (24 m) sees no dice at rest: probe " + CrapsBoardRenderer.probeDice);
		CrapsBoardRenderer.probePos = null;
		f.context().runOnClient(mc -> {
			AnimClock.unfreezeForTest();
			mc.options.hideGui = false;
		});
	}

	private static void freezeWorld(RealFlow f, long rollTime, double t) {
		double level = rollTime * 50.0 + t;
		f.context().runOnClient(mc -> AnimClock.freezeForTest(level));
	}

	/**
	 * Puts the eye {@code d} blocks from the felt centre on the players' side of a table (the side it faces), looking
	 * down at the felt at {@code pitch}°.
	 */
	private static void view(RealFlow f, BlockPos p, Direction facing, int d, float pitch) {
		f.server(server -> {
			ServerPlayer pl = RealFlow.player(server);
			float yaw = facing.getOpposite().toYRot();
			double horiz = d * Math.cos(Math.toRadians(pitch));
			double up = d * Math.sin(Math.toRadians(pitch));
			// the felt centre: footprint-local (0.5, 0) = the main block's centre x, its back edge toward the dealer
			double cx = p.getX() + 0.5 - facing.getStepX() * 0.5;
			double cz = p.getZ() + 0.5 - facing.getStepZ() * 0.5;
			double y = p.getY() + 0.875 + up - 1.62;
			pl.teleportTo(server.overworld(), cx + facing.getStepX() * horiz, y, cz + facing.getStepZ() * horiz, java.util.Set.of(), yaw, pitch, true);
			pl.getAbilities().mayfly = true;
			pl.getAbilities().flying = true;
			pl.onUpdateAbilities();
			return null;
		});
		f.context().waitTicks(20);
	}
}
