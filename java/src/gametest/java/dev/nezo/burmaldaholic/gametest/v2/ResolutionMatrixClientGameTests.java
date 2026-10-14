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
			cell(f, "en_us", 1280, 720, 0);
			cell(f, "en_us", 1920, 1080, 2);
			cell(f, "en_us", 2560, 1440, 2);
			framedRealTable(f, 2560, 1440, 2);
			f.language("ru_ru");
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
			f.shot("p2b_rs_kit_betting_" + tag);
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
