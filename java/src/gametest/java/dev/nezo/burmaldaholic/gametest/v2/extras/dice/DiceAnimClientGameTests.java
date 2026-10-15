package dev.nezo.burmaldaholic.gametest.v2.extras.dice;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.motion.MotionClock;
import dev.nezo.burmaldaholic.client.fx.motion.MotionFx;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.anim.dice.Shake;
import dev.nezo.burmaldaholic.core.anim.motion.FxGate;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.client.DiceScreenProbe;
import dev.nezo.burmaldaholic.games.extras.client.DuelStageRenderer;
import dev.nezo.burmaldaholic.games.extras.present.DiceStage;
import dev.nezo.burmaldaholic.games.extras.server.DuelStage;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 2b client GameTests of lane DD (DCb; DI §10.2, PHASE2B §4 DCb) on a real client:
 * <ol>
 *   <li>real flow: the Dice item opens the duel screen, a chip and Roll at real pixels, the server's result plays and the
 *       balance moves by the banner's net;</li>
 *   <li>a fixed house roll (seed 7, you 5+4 vs the dealer 6+2 → win) on a frozen clock at the 2b sizes 427 / 480 / 640 /
 *       960 / 1280-framed: shake, pour, air, first bank, rolling, count-up, compare, end; probes: the dealer's cup still
 *       shakes at THROW + 100, a resting die is exactly {@code 32·n} physical px wide with the server's pips, the
 *       airborne die's floor shadow is darker than the felt;</li>
 *   <li>a house tie on 7 (stamp), a PvP tie round (banner, sweep, re-roll), reduced motion (no shake / particle /
 *       streak effect passes the gate);</li>
 *   <li>PvP mirror: one duel shown as player A and as player B — A's dice are the same roll, mirrored, on both
 *       screens;</li>
 *   <li>in-world at 3 blocks with a frozen AnimClock: the modded viewer renders the stage (LOD full) and is sent no
 *       display entities.</li>
 * </ol>
 * Shots {@code p2b_dcb_dice_<state>_<lang>_<w>}. Run alone with {@code BURMALDAHOLIC_CGT=DD}.
 */
public class DiceAnimClientGameTests implements FabricClientGameTest {
	private static final int[][] SIZES = {{1280, 720, 3}, {1920, 1080, 4}, {1920, 1080, 3}, {1920, 1080, 2}, {2560, 1440, 2}};
	private final List<String> errors = new ArrayList<>();
	private final long[] clock = {0};
	private int seq = 9000;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("DD")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			RealFlow f = new RealFlow(context, world);
			context.waitTicks(20);
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			try {
				f.size(1920, 1080, 4);
				step("real flow", () -> realFlow(f));
				MotionClock.testOverride = () -> clock[0];
				for (int[] sz : SIZES) {
					f.size(sz[0], sz[1], sz[2]);
					step("house " + sz[0] + "x" + sz[1] + "@" + sz[2], () -> house(f));
				}
				f.size(1920, 1080, 3);
				f.language("ru_ru");
				step("house ru 640", () -> house(f));
				f.size(1920, 1080, 4);
				step("house ru 480", () -> house(f));
				step("pvp tie ru 480", () -> pvpTie(f));
				f.language("en_us");
				step("house tie", () -> houseTie(f));
				step("pvp tie", () -> pvpTie(f));
				step("pvp mirror", () -> pvpMirror(f));
				step("reduced", () -> reduced(f));
				f.size(1920, 1080, 2);
				step("house tie 960", () -> houseTie(f));
				step("pvp tie 960", () -> pvpTie(f));
				f.size(1920, 1080, 4);
				MotionClock.testOverride = null;
				f.close();
				step("in-world", () -> inWorld(f));
			} finally {
				MotionClock.testOverride = null;
				MotionFx.recorder = null;
				AnimClock.unfreezeForTest();
				FxSettings.update(d -> d.reduceMotion = false);
				context.runOnClient(mc -> {
					mc.options.guiScale().set(0);
					mc.resizeGui();
				});
				f.close();
			}
		}
		if (!errors.isEmpty()) throw new AssertionError("dice anim (DD):\n - " + String.join("\n - ", errors));
	}

	private interface Body {
		void run();
	}

	private void step(String name, Body b) {
		try {
			b.run();
			System.out.println("[DD] ok " + name);
		} catch (RuntimeException | AssertionError e) {
			errors.add(name + ": " + e);
			e.printStackTrace();
		}
	}

	// ---- 1. real flow ----------------------------------------------------------------------------------------------

	private void realFlow(RealFlow f) {
		f.server(s -> {
			ServerPlayer p = RealFlow.player(s);
			Economies.get().setBalance(s, p.getUUID(), 5000, dev.nezo.burmaldaholic.core.economy.Economy.Transaction.of("core", "gametest"));
			p.getInventory().setItem(0, new ItemStack(ExtrasModule.DICE, 1));
			return null;
		});
		f.context().waitTicks(4);
		f.context().runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		});
		f.context().waitFor(mc -> DiceScreenProbe.isDice(mc.gui.screen()), RealFlow.TIMEOUT);
		f.context().waitTicks(8);
		f.click(KeyMap.Ids.chip(2));
		long before = f.server(s -> Economies.get().balance(RealFlow.player(s)));
		f.click(KeyMap.Ids.PRIMARY);
		f.context().waitTicks(14);
		f.shot("p2b_dcb_dice_realflow_roll_en_480");
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().banner.summary() != null, 400);
		long net = f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().banner.net());
		long after = f.server(s -> Economies.get().balance(RealFlow.player(s)));
		if (after != before + net) throw new AssertionError("the banner says " + net + " but the balance moved " + (after - before));
		f.shot("p2b_dcb_dice_realflow_result_en_480");
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private void ensureScreen(RealFlow f) {
		if (f.client(mc -> DiceScreenProbe.isDice(mc.gui.screen()))) return;
		f.server(s -> {
			RealFlow.player(s).getInventory().setItem(0, new ItemStack(ExtrasModule.DICE, 1));
			return null;
		});
		f.context().waitTicks(4);
		f.context().runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		});
		f.context().waitFor(mc -> DiceScreenProbe.isDice(mc.gui.screen()), RealFlow.TIMEOUT);
		f.context().waitTicks(8);
	}

	private CompoundTag house(int you0, int you1, int them0, int them1, String outcome, long net) {
		CompoundTag r = new CompoundTag();
		r.putInt("seq", ++seq);
		r.putInt("seed", 7);
		r.putString("mode", "house");
		r.put("you", new IntArrayTag(new int[] {you0, you1}));
		r.put("them", new IntArrayTag(new int[] {them0, them1}));
		r.putString("outcome", outcome);
		r.putLong("net", net);
		r.putBoolean("pawn", false);
		return r;
	}

	private CompoundTag pvp(String side, int seed, int[][][] rounds, String outcome, long net) {
		CompoundTag r = new CompoundTag();
		r.putInt("seq", ++seq);
		r.putInt("seed", seed);
		r.putString("mode", "pvp");
		r.putString("side", side);
		r.putLong("start", 0);
		r.putString("opponent", "a".equals(side) ? "Bob" : "Alice");
		ListTag list = new ListTag();
		for (int[][] rd : rounds) {
			CompoundTag rt = new CompoundTag();
			rt.put("you", new IntArrayTag(rd[0]));
			rt.put("them", new IntArrayTag(rd[1]));
			list.add(rt);
		}
		r.put("rounds", list);
		r.put("you", new IntArrayTag(rounds[rounds.length - 1][0]));
		r.put("them", new IntArrayTag(rounds[rounds.length - 1][1]));
		r.putString("outcome", outcome);
		r.putLong("net", net);
		return r;
	}

	/** Shows the screen's result at duel ms {@code t} (house: the clock is offset by the cup alignment). */
	private void at(RealFlow f, double t, double offset) {
		clock[0] = Math.round(t + offset);
		f.context().waitTicks(2);
	}

	private int width(RealFlow f) {
		return f.viewport().w();
	}

	private Path shot(RealFlow f, String state) {
		String lang = f.client(mc -> mc.options.languageCode).startsWith("ru") ? "ru" : "en";
		return f.shot("p2b_dcb_dice_" + state + "_" + lang + "_" + width(f));
	}

	// ---- 2. the house roll at every size -------------------------------------------------------------------------

	private void house(RealFlow f) {
		ensureScreen(f);
		clock[0] = 0;
		f.context().runOnClient(mc -> DiceScreenProbe.houseResult(mc.gui.screen(), house(5, 4, 6, 2, "win", 10)));
		f.context().waitTicks(2);
		double off = f.client(mc -> DiceScreenProbe.offset(mc.gui.screen()));
		if (Math.abs(off - DuelTimeline.SHAKE_LEAD) > 1e-6) throw new AssertionError("instant result: offset " + off);
		double bank = f.client(mc -> DiceScreenProbe.firstBank(mc.gui.screen(), 0));
		double[][] states = {{200 - off}, {DuelTimeline.THROW + 60}, {DuelTimeline.THROW + 200}, {bank < 0 ? DuelTimeline.THROW + 300 : bank + 20},
			{DuelTimeline.YOU_LAND - 300}, {DuelTimeline.YOU_LAND + 120}, {DuelTimeline.THEM_LAND + 150}, {DuelTimeline.COMPARE + 150},
			{DuelTimeline.CHIPS + 200}, {DuelTimeline.END + 50}};
		String[] names = {"shake", "pour", "air", "bank", "rolling", "countup", "them_count", "compare", "chips", "end"};
		boolean ru = f.client(mc -> mc.options.languageCode).startsWith("ru");
		for (int i = 0; i < states.length; i++) {
			if (ru && !(names[i].equals("countup") || names[i].equals("compare") || names[i].equals("end"))) continue;
			double t = states[i][0];
			at(f, t, off);
			Path p = shot(f, names[i]);
			if (ru) continue;
			if (names[i].equals("pour")) {
				// DI §0.6: at THROW + 100 the dealer's cup is still shaking, not tipped
				int frame = f.client(mc -> DiceScreenProbe.cupFrame(mc.gui.screen(), 1, DuelTimeline.THROW + 100));
				if (frame > Shake.F_SHAKE_R2) errors.add(width(f) + ": dealer cup frame " + frame + " at THROW+100");
				int mine = f.client(mc -> DiceScreenProbe.cupFrame(mc.gui.screen(), 0, DuelTimeline.THROW + 100));
				if (mine < Shake.F_TIP20 || mine > Shake.F_TIP70) errors.add(width(f) + ": your cup frame " + mine + " at THROW+100 (want a tip)");
			}
			if (names[i].equals("air")) shadowProbe(f, p, t);
			if (names[i].equals("countup") || names[i].equals("end")) restProbe(f, p, t, 0, new int[] {5, 4});
		}
	}

	/** The airborne die's floor shadow is ≥ 20 % darker than the felt beside it. */
	private void shadowProbe(RealFlow f, Path p, double t) {
		double[] d = f.client(mc -> DiceScreenProbe.die(mc.gui.screen(), 0, 0, t));
		if ((int) d[2] != DieSample.AIR && (int) d[2] != DieSample.ROLL) return;
		DiceStage st = f.client(mc -> DiceScreenProbe.stage(mc.gui.screen()));
		int k = st.k();
		BufferedImage img = read(p);
		// shadow centre: the floor point + the rest offset (DiceDraw.SHADOW_DX/DY half-edges) + height slope; probe its core
		double fx = st.x(d[4]);
		double fy = st.y(d[5]);
		int sx = (int) Math.round(fx * k);
		int sy = (int) Math.round((fy + 6 * st.scale()) * k);
		int feltX = (int) Math.round(st.x(d[4] < 180 ? 120 : 240) * k);
		int feltY = (int) Math.round(st.y(122 - 10) * k);
		int s = lumAround(img, sx, sy);
		int felt = lumAround(img, feltX, feltY);
		if (s > felt * 0.8 && s > 0) errors.add(width(f) + ": shadow lum " + s + " vs felt " + felt);
	}

	/** A resting die is exactly 32·n physical px wide on a whole pixel grid and shows the server's pip count. */
	private void restProbe(RealFlow f, Path p, double t, int order, int[] faces) {
		DiceStage st = f.client(mc -> DiceScreenProbe.stage(mc.gui.screen()));
		BufferedImage img = read(p);
		int k = st.k();
		for (int die = 0; die < 2; die++) {
			final int dd = die;
			double[] d = f.client(mc -> DiceScreenProbe.die(mc.gui.screen(), order, dd, t));
			if ((int) d[2] != DieSample.REST) {
				errors.add(width(f) + ": die " + die + " not at rest at " + t);
				continue;
			}
			if ((int) d[6] != faces[die]) errors.add(width(f) + ": die " + die + " top face " + (int) d[6] + " != " + faces[die]);
			if (!st.art().integral()) continue;
			int n = st.n();
			// the drawn centre row of the die (its body: top face + front band); outward to the felt on each side
			int cx = (int) Math.round(d[0] * k);
			int row = (int) Math.round(d[1] * k);
			int first = cx;
			int last = cx;
			while (first > 0 && !felt(img.getRGB(first - 1, row))) first--;
			while (last < img.getWidth() - 1 && !felt(img.getRGB(last + 1, row))) last++;
			int w = last - first + 1;
			if (Math.abs(w - 32 * n) > 1) errors.add(width(f) + ": resting die " + die + " is " + w + " px wide, want " + 32 * n);
		}
	}

	/** Felt or its shadow: green dominant. */
	private static boolean felt(int rgb) {
		int r = (rgb >> 16) & 255;
		int g = (rgb >> 8) & 255;
		int b = rgb & 255;
		return g > r + 12 && g > b;
	}

	private static int lumAround(BufferedImage img, int x, int y) {
		int sum = 0;
		int c = 0;
		for (int dy = -1; dy <= 1; dy++)
			for (int dx = -1; dx <= 1; dx++) {
				int px = Math.max(0, Math.min(img.getWidth() - 1, x + dx));
				int py = Math.max(0, Math.min(img.getHeight() - 1, y + dy));
				int rgb = img.getRGB(px, py);
				sum += (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
				c++;
			}
		return sum / c;
	}

	private static BufferedImage read(Path p) {
		try {
			return ImageIO.read(p.toFile());
		} catch (IOException e) {
			throw new AssertionError("screenshot " + p, e);
		}
	}

	// ---- 3. tie on 7, PvP tie round, reduced motion ----------------------------------------------------------------

	private void houseTie(RealFlow f) {
		ensureScreen(f);
		clock[0] = 0;
		f.context().runOnClient(mc -> DiceScreenProbe.houseResult(mc.gui.screen(), house(3, 4, 4, 3, "house_tie", -10)));
		f.context().waitTicks(2);
		double off = f.client(mc -> DiceScreenProbe.offset(mc.gui.screen()));
		at(f, DuelTimeline.COMPARE + 60, off);
		shot(f, "housetie_stamp");
		at(f, DuelTimeline.COMPARE + 110, off);
		shot(f, "housetie_shake");
		at(f, DuelTimeline.CHIPS + 300, off);
		shot(f, "housetie_chips");
	}

	private void pvpTie(RealFlow f) {
		ensureScreen(f);
		int[][][] rounds = {{{3, 3}, {4, 2}}, {{6, 5}, {1, 2}}};
		clock[0] = 0;
		f.context().runOnClient(mc -> DiceScreenProbe.deliver(mc.gui.screen(), pvp("a", 0x5EED, rounds, "win", 18)));
		f.context().waitTicks(2);
		double[] ts = {DuelTimeline.THROW + 150, DuelTimeline.THEM_LAND + 100, DuelTimeline.COMPARE + 200, DuelTimeline.SWEEP_AT + 150,
			DuelTimeline.ROUND_PERIOD + DuelTimeline.THROW + 250, DuelTimeline.ROUND_PERIOD + DuelTimeline.COMPARE + 150,
			DuelTimeline.ROUND_PERIOD + DuelTimeline.END + 50};
		String[] names = {"pvp_air", "pvp_landed", "pvp_tie_banner", "pvp_sweep", "pvp_r2_air", "pvp_r2_compare", "pvp_end"};
		for (int i = 0; i < ts.length; i++) {
			at(f, ts[i], 0);
			shot(f, names[i]);
		}
	}

	// ---- 4. PvP mirror: A's dice on A's and B's screens -----------------------------------------------------------

	private void pvpMirror(RealFlow f) {
		ensureScreen(f);
		int[][][] asA = {{{2, 6}, {5, 1}}};
		int[][][] asB = {{{5, 1}, {2, 6}}};
		double t = DuelTimeline.COMPARE - 40;
		double tAir = DuelTimeline.THROW + 220;
		clock[0] = 0;
		f.context().runOnClient(mc -> DiceScreenProbe.deliver(mc.gui.screen(), pvp("a", 0xA11CE, asA, "win", 18)));
		at(f, tAir, 0);
		shot(f, "mirror_A_air");
		double[][] a = new double[2][];
		double[][] aAir = new double[2][];
		for (int d = 0; d < 2; d++) {
			final int dd = d;
			a[d] = f.client(mc -> DiceScreenProbe.die(mc.gui.screen(), 0, dd, t));
			aAir[d] = f.client(mc -> DiceScreenProbe.die(mc.gui.screen(), 0, dd, tAir));
		}
		at(f, t, 0);
		shot(f, "mirror_A");
		clock[0] = 0;
		f.context().runOnClient(mc -> DiceScreenProbe.deliver(mc.gui.screen(), pvp("b", 0xA11CE, asB, "lose", -10)));
		// on B's screen A throws second (+350 ms): the same moment of A's roll is 350 ms later
		at(f, tAir + DuelTimeline.THEM_DELAY, 0);
		shot(f, "mirror_B_air");
		at(f, t, 0);
		shot(f, "mirror_B");
		for (int d = 0; d < 2; d++) {
			final int dd = d;
			double[] b = f.client(mc -> DiceScreenProbe.die(mc.gui.screen(), 1, dd, t));
			double[] bAir = f.client(mc -> DiceScreenProbe.die(mc.gui.screen(), 1, dd, tAir + DuelTimeline.THEM_DELAY));
			if (Math.abs(a[d][4] + b[4] - DiceStage.W) > 1e-9 || Math.abs(a[d][5] - b[5]) > 1e-9 || (int) a[d][6] != (int) b[6])
				errors.add("mirror: A's die " + d + " at rest (" + a[d][4] + "," + a[d][5] + " face " + a[d][6] + ") vs B's screen (" + b[4] + "," + b[5]
					+ " face " + b[6] + ")");
			if (Math.abs(aAir[d][4] + bAir[4] - DiceStage.W) > 1e-9 || Math.abs(aAir[d][7] - bAir[7]) > 1e-9)
				errors.add("mirror: A's die " + d + " in the air differs on B's screen");
		}
	}

	private void reduced(RealFlow f) {
		ensureScreen(f);
		FxSettings.update(d -> d.reduceMotion = true);
		FxGate.Counter counter = new FxGate.Counter();
		try {
			clock[0] = 0;
			f.context().runOnClient(mc -> DiceScreenProbe.houseResult(mc.gui.screen(), house(5, 4, 6, 2, "win", 10)));
			f.context().waitTicks(2);
			double off = f.client(mc -> DiceScreenProbe.offset(mc.gui.screen()));
			MotionFx.recorder = counter;
			for (double t : new double[] {200, DuelTimeline.THROW + 100, DuelTimeline.THROW + 400, DuelTimeline.YOU_LAND + 100, DuelTimeline.COMPARE + 100,
				DuelTimeline.CHIPS + 100}) {
				at(f, t, off);
				if (t == DuelTimeline.THROW + 400) shot(f, "reduced_throw400");
				if (t == DuelTimeline.COMPARE + 100) shot(f, "reduced_compare");
			}
			MotionFx.recorder = null;
			int bad = counter.count(FxGate.Kind.SHAKE) + counter.count(FxGate.Kind.PARTICLES) + counter.count(FxGate.Kind.STREAK)
				+ counter.count(FxGate.Kind.MOTION) + counter.count(FxGate.Kind.FLASH);
			if (bad != 0) errors.add("reduced motion: " + bad + " effects passed the gate");
		} finally {
			MotionFx.recorder = null;
			FxSettings.update(d -> d.reduceMotion = false);
		}
	}

	// ---- 5. in-world at 3 blocks ------------------------------------------------------------------------------------

	private void inWorld(RealFlow f) {
		long start = f.server(s -> s.overworld().getGameTime());
		boolean staged = f.server(s -> {
			ServerPlayer p = RealFlow.player(s);
			p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), Set.of(), -90f, 30f, true); // face east (+x), look down a little
			Vec3 at = p.position();
			Vec3 a = at.add(3, 1.1, -1.5);
			Vec3 b = at.add(3, 1.1, 1.5);
			return DuelStage.stage(p.level(), a, b, p, List.<int[][]>of(new int[][] {{5, 4}, {2, 3}}), 0, 77, start, Set.of());
		});
		if (!staged) throw new AssertionError("not staged");
		f.context().waitTicks(3);
		int active = f.client(mc -> DuelStageRenderer.active());
		if (active < 1) throw new AssertionError("the modded client got no stage sync");
		double[] times = {DuelTimeline.THROW + 260, DuelTimeline.THEM_LAND + 40, DuelTimeline.COMPARE + 400};
		String[] names = {"world_air", "world_landed", "world_rest"};
		for (int i = 0; i < times.length; i++) {
			AnimClock.freezeForTest(start * 50.0 + times[i]);
			f.context().waitTicks(2);
			f.shot("p2b_dcb_dice_" + names[i] + "_en_480");
			int lod = f.client(mc -> DuelStageRenderer.lastLod());
			if (lod != 0) errors.add("in-world LOD at 3 blocks is " + lod);
		}
		AnimClock.unfreezeForTest();
		long displays = f.client(mc -> {
			long n = 0;
			for (var e : mc.level.entitiesForRendering()) if (e instanceof Display.ItemDisplay) n++;
			return n;
		});
		if (displays != 0) errors.add("the modded client was sent " + displays + " item-display dice");
	}
}
