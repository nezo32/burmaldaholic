package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import dev.nezo.burmaldaholic.core.ui.v2.ViewportPolicy;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Lane RS (K2c, PHASE2B §2.3): the every-resolution policy on real windows. The kit gallery at 1280 × 720 Auto
 * (427 × 240), 1920 × 1080 GUI 2 (960 × 540 ROOMY, the player's scale kept instead of a 2× "zoomed in" 480 × 270) and
 * 2560 × 1440 GUI 2 (1280 × 720 framed to 1067 × 600): the viewport equals the pure policy for the real framebuffer, every
 * target is inside the frame and none overlap; on a framed window a real blackjack table is opened by using the block and
 * Sit down is clicked at its real window pixel → the SERVER seats the player. Shots {@code p2b_rs_*}; report
 * {@code p2b_rs_report.txt}. When the desktop clamps a window, the actual framebuffer is logged and checked as it is.
 */
public class ResolutionMatrixClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("RS")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 12500");
			RealFlow f = new RealFlow(context, world);
			f.language("en_us");
			cell(f, "en_us", 3840, 2160, 0); // the owner's setup: 4K, GUI Auto → 427 × 240 k9
			ownerTable(f, "en_us");
			cell(f, "en_us", 1280, 720, 0);
			cell(f, "en_us", 1920, 1080, 2);
			cell(f, "en_us", 2560, 1440, 2);
			framedRealTable(f, 2560, 1440, 2);
			f.language("ru_ru");
			cell(f, "ru_ru", 3840, 2160, 0);
			ownerTable(f, "ru_ru");
			cell(f, "ru_ru", 1920, 1080, 2);
			cell(f, "ru_ru", 2560, 1440, 2);
			f.language("en_us");
			f.close();
		} finally {
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("resolution matrix: " + failures);
	}

	private void cell(RealFlow f, String lang, int w, int h, int gs) {
		String tag = lang + "_" + w + "x" + h + "_gui" + gs;
		try {
			f.size(w, h, gs);
			f.context().setScreen(KitGalleryV2Screen::new);
			f.context().waitFor(mc -> mc.gui.screen() instanceof KitGalleryV2Screen, RealFlow.TIMEOUT);
			f.context().waitTicks(12);
			List<String> problems = f.client(mc -> {
				List<String> out = new ArrayList<>();
				V2Chrome c = ((V2Chrome.Holder) mc.gui.screen()).chrome();
				Viewport vp = c.viewport();
				int fbW = mc.getWindow().getWidth();
				int fbH = mc.getWindow().getHeight();
				int realGs = mc.getWindow().getGuiScale();
				Viewport want = ViewportPolicy.of(fbW, fbH, realGs);
				report.add(tag + ": framebuffer " + fbW + "x" + fbH + " @" + realGs + " -> " + vp + " tier " + c.grid().tier + " " + c.grid().room());
				if (!vp.equals(want)) out.add("viewport " + vp + " != policy " + want);
				if (realGs >= 1 && fbH / realGs >= 240 && fbH / realGs <= 720 && vp.k() != realGs) out.add("the player's GUI scale " + realGs + " was not kept (k " + vp.k() + ")");
				Box screen = c.grid().screen;
				if (screen.w() != vp.frameW() || screen.h() != vp.frameH()) out.add("grid " + screen + " is not the frame");
				List<HitMap.Target> ts = c.hits().targets();
				for (HitMap.Target t : ts) if (!t.box().inside(screen)) out.add(t.id() + " outside the frame " + t.box());
				for (int i = 0; i < ts.size(); i++) {
					for (int j = i + 1; j < ts.size(); j++) {
						if (ts.get(i).box().intersects(ts.get(j).box())) out.add(ts.get(i).id() + " overlaps " + ts.get(j).id());
					}
				}
				if (c.dock() != null && c.dock().overflow() > 0) out.add("dock overflow " + c.dock().overflow());
				return out;
			});
			report.add("  audit " + tag + ": " + (problems.isEmpty() ? "ok" : problems));
			// the gallery is played by real clicks at the frame's window pixels
			f.click("chip.2");
			Path shot = f.shot("p2b_rs_kit_betting_" + tag);
			framedPixels(f, shot, tag); // the gallery is a V2SceneScreen: its felt and dim must follow the frame too
			f.click(KeyMap.Ids.PRIMARY);
			f.context().waitTicks(4);
			f.shot("p2b_rs_kit_acting_" + tag);
			if (!problems.isEmpty()) throw new AssertionError(problems.toString());
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
			f.shot("p2b_rs_failed_" + tag);
		} finally {
			f.client(mc -> {
				mc.gui.setScreen(null);
				return null;
			});
			f.context().waitTicks(3);
		}
	}

	/**
	 * The owner's setup (3840 × 2160, GUI Auto → 427 × 240 k9) on a real blackjack table: watching (the status must
	 * show its whole text, two lines or the hint strip, T-K2 D1), then seated with a chip (the hint by phase), then
	 * the Dock's action plates are recorded.
	 */
	private void ownerTable(RealFlow f, String lang) {
		String tag = "owner_4k_auto_" + lang;
		try {
			f.size(3840, 2160, 0);
			BlockPos pos = f.place(BlackjackModule.TABLE.block(), lang.equals("en_us") ? -2 : 3);
			f.open(pos, dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen.class);
			f.waitChrome(c -> "sit".equals(c.primary.gate().action()), "Sit down");
			String watch = f.client(mc -> statusText(((V2Chrome.Holder) mc.gui.screen()).chrome()));
			report.add(tag + ": " + f.viewport() + " watching status '" + watch + "'");
			f.shot("p2b_rs_blackjack_watching_" + lang + "_427_k9");
			if (watch.isBlank()) throw new AssertionError("blank status while watching");
			UUID me = f.server(server -> RealFlow.player(server).getUUID());
			f.click(KeyMap.Ids.PRIMARY);
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
			f.waitChrome(c -> c.bet.visible() && c.bet.chipGate(0).enabled(), "chips");
			String seated = f.client(mc -> statusText(((V2Chrome.Holder) mc.gui.screen()).chrome()));
			report.add("  seated status '" + seated + "'");
			f.click("chip.0");
			f.shot("p2b_rs_blackjack_betting_" + lang + "_427_k9");
			if (seated.isBlank()) throw new AssertionError("blank status when seated with no bet");
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
			f.shot("p2b_rs_failed_" + tag);
		} finally {
			f.close();
		}
	}

	/** Everything the status shows: line 1, line 2 (where the strip is used, it carries one of them). */
	private static String statusText(V2Chrome c) {
		var l1 = c.status.line1();
		var l2 = c.status.line2();
		return (l1 == null ? "" : l1.getString()) + (l2 == null ? "" : " | " + l2.getString()) + (c.status.strip().isEmpty() ? "" : " [strip]");
	}

	/**
	 * On a framed screen, the drawn pixels follow the frame (PL report: a scene screen drew its felt at the top-left):
	 * the felt shows inside the frame's PlayArea and the band left of the frame is the dim, never felt. Deterministic
	 * pixel samples of the shot (agents never look at it).
	 */
	private void framedPixels(RealFlow f, Path shot, String tag) {
		Viewport vp = f.viewport();
		if (!vp.framed()) return;
		Box play = f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().grid().play);
		try {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(shot.toFile());
			int k = vp.k();
			int y = play.y() + play.h() / 2;
			int feltIn = 0, feltOut = 0, samples = 9;
			for (int i = 0; i < samples; i++) {
				int x = play.x() + 2 + i * (play.w() - 4) / (samples - 1);
				if (greenish(px(img, vp.windowX(x), vp.windowY(y)))) feltIn++;
				int oy = play.y() + i * play.h() / samples; // the band left of the frame, top to bottom
				if (greenish(px(img, vp.ox() * k / 2.0, vp.windowY(oy)))) feltOut++;
			}
			report.add("  pixels " + tag + ": felt samples in the PlayArea " + feltIn + "/" + samples + ", left of the frame " + feltOut + "/" + samples);
			if (feltIn == 0) throw new AssertionError("no felt inside the frame's PlayArea");
			if (feltOut > 0) throw new AssertionError("felt drawn left of the frame (the frame offset is not applied): " + feltOut + " samples");
		} catch (java.io.IOException e) {
			throw new AssertionError("reading " + shot + ": " + e);
		}
	}

	private static int px(java.awt.image.BufferedImage img, double x, double y) {
		return img.getRGB(Math.max(0, Math.min(img.getWidth() - 1, (int) x)), Math.max(0, Math.min(img.getHeight() - 1, (int) y)));
	}

	private static boolean greenish(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return g > r + 15 && g > b + 15 && g > 50;
	}

	/** On a framed window: Sit down on a real blackjack table, clicked at its window pixel → the server seats the player. */
	private void framedRealTable(RealFlow f, int w, int h, int gs) {
		String tag = "framed_real_table_" + w + "x" + h + "_gui" + gs;
		try {
			f.size(w, h, gs);
			BlockPos pos = f.place(BlackjackModule.TABLE.block(), 2);
			f.open(pos, dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen.class);
			Viewport vp = f.viewport();
			report.add(tag + ": " + vp + (vp.framed() ? " (framed)" : " (the desktop clamped the window: not framed)"));
			UUID me = f.server(server -> RealFlow.player(server).getUUID());
			f.waitChrome(c -> "sit".equals(c.primary.gate().action()) && c.primary.gate().enabled(), "Sit down");
			double[] p = f.windowPoint(KeyMap.Ids.PRIMARY);
			report.add("  primary at window px " + p[0] + "," + p[1]);
			f.click(KeyMap.Ids.PRIMARY);
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated by a click at the framed window pixel");
			f.shot("p2b_rs_blackjack_seated_en_us_" + vp.frameW());
			report.add("  seated through a click in the frame: ok");
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
			f.shot("p2b_rs_failed_" + tag);
		} finally {
			f.close();
		}
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("p2b_rs_report.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			// best effort
		}
	}
}
