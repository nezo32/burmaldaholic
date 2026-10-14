package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.client.table.ClientTableCache;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Lane K: the kit showcase (docs/architecture/ui-v2.md §11). The gallery screen ({@link KitGalleryV2Screen}) is played like
 * a table — chips, primary, actions, rebet clicked at their real window pixels through the mouse mixin — at the real
 * player sizes (1920 × 1080 @4 = 480 × 270, 2560 × 1440 @4 and 1920 × 1080 @3 = 640 × 360, 1280 × 720 @3 = 427 × 240,
 * 3840 × 2160 @4 stepped up), in EN and RU, with sprites and with the flat fallbacks; the disabled-primary reason and the
 * watchdog (a dropped action → reason + resync) are asserted; and the {@code resync} action is checked against a real
 * table through the real packet handler. Screenshots {@code v2_kit_<lang>_<w>x<h>_<state>}; report
 * {@code v2_kit_report.txt}.
 */
public class KitV2ClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("K")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("casino balance set @p 12500");
			RealFlow f = new RealFlow(context, world);
			resyncOnARealTable(f);
			for (String lang : List.of("en_us", "ru_ru")) {
				f.language(lang);
				size(f, lang, 1920, 1080, 4);
				size(f, lang, 2560, 1440, 4);
				if (lang.equals("en_us")) {
					size(f, lang, 1920, 1080, 3);
					size(f, lang, 1280, 720, 3);
					size(f, lang, 3840, 2160, 4);
				}
			}
			f.language("en_us");
			V2Draw.forceFallback = true;
			try {
				f.size(1920, 1080, 4);
				gallery(f);
				galleryShots(f, "en_us_fallback_480x270");
			} finally {
				V2Draw.forceFallback = false;
			}
			f.close();
			if (!failures.isEmpty()) throw new AssertionError("kit showcase: " + failures);
		} finally {
			V2Chrome.dropNextSends = 0;
			V2Draw.forceFallback = false;
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			write(context);
		}
	}

	private final List<String> failures = new ArrayList<>();

	/** One size; a failure is recorded (with a screenshot) and the run goes on, so one run yields every shot. */
	private void size(RealFlow f, String lang, int w, int h, int gs) {
		try {
			sizeOnce(f, lang, w, h, gs);
		} catch (AssertionError | RuntimeException e) {
			failures.add(lang + " " + w + "x" + h + "@" + gs + ": " + e.getMessage());
			report.add("  FAILED " + lang + " " + w + "x" + h + "@" + gs + ": " + e);
			f.shot("v2_kit_failed_" + lang + "_" + w + "x" + h + "_" + gs);
			f.client(mc -> {
				if (mc.gui.screen() instanceof KitGalleryV2Screen s) s.deaf = false;
				return null;
			});
		}
	}

	private void sizeOnce(RealFlow f, String lang, int w, int h, int gs) {
		f.size(w, h, gs);
		KitGalleryV2Screen s = gallery(f);
		Viewport vp = f.viewport();
		String tag = lang + "_" + vp.w() + "x" + vp.h() + (w == 3840 ? "_4k" : gs == 3 && w == 1920 ? "_gui3" : "");
		report.add(tag + ": window " + w + "x" + h + " @" + gs + " → viewport " + vp + " tier " + f.client(mc -> s.chrome().grid().tier));
		audit(f, tag);
		play(f, s, tag);
	}

	private KitGalleryV2Screen gallery(RealFlow f) {
		f.context().setScreen(KitGalleryV2Screen::new);
		f.context().waitFor(mc -> mc.gui.screen() instanceof KitGalleryV2Screen, RealFlow.TIMEOUT);
		f.context().waitTicks(12); // past the entrance
		return f.client(mc -> (KitGalleryV2Screen) mc.gui.screen());
	}

	/** Every target inside the screen; the Dock's targets disjoint; nothing outside the window. */
	private void audit(RealFlow f, String tag) {
		List<String> problems = f.client(mc -> {
			List<String> out = new ArrayList<>();
			if (!(mc.gui.screen() instanceof V2Chrome.Holder h)) return List.of("no v2 screen");
			V2Chrome c = h.chrome();
			Box screen = c.grid().screen;
			List<HitMap.Target> ts = c.hits().targets();
			for (HitMap.Target t : ts) if (!t.box().inside(screen)) out.add(t.id() + " outside " + t.box());
			for (int i = 0; i < ts.size(); i++) {
				for (int j = i + 1; j < ts.size(); j++) {
					if (ts.get(i).box().intersects(ts.get(j).box())) out.add(ts.get(i).id() + " overlaps " + ts.get(j).id());
				}
			}
			if (c.dock() != null && c.dock().overflow() > 0) out.add("dock overflow " + c.dock().overflow());
			int[] win = {mc.getWindow().getWidth(), mc.getWindow().getHeight()};
			if (Math.abs(c.viewport().toWindow(c.viewport().w()) - win[0]) > c.viewport().k()) out.add("viewport width ≠ window " + win[0]);
			return out;
		});
		report.add("  audit " + tag + ": " + (problems.isEmpty() ? "ok" : problems));
		if (!problems.isEmpty()) throw new AssertionError("layout audit " + tag + ": " + problems);
	}

	/** A full round by clicking, then the negative paths. */
	private void play(RealFlow f, KitGalleryV2Screen s, String tag) {
		// betting: the primary is disabled without a bet and SAYS why (never nothing)
		f.click(KeyMap.Ids.PRIMARY);
		String reason = f.status();
		report.add("  " + tag + " primary with no bet → '" + reason + "'");
		if (reason.isEmpty()) throw new AssertionError("disabled primary showed no reason at " + tag);
		if (f.client(mc -> s.sent.contains("deal"))) throw new AssertionError("a disabled primary sent an action");
		f.shot("v2_kit_" + tag + "_betting_empty");
		// chip 2 (25) → the fake server takes it
		f.click("chip.2");
		f.waitChrome(c -> s.demo().bet() == 25, "bet 25 after clicking chip.2");
		f.key(KeyMap.KEY_1); // keyboard parity: 1 = the first chip
		f.waitChrome(c -> s.demo().bet() == 26, "bet 26 after key 1");
		f.hover("chip.3");
		f.shot("v2_kit_" + tag + "_betting");
		// Deal
		f.click(KeyMap.Ids.PRIMARY);
		f.waitChrome(c -> s.demo().phase() == KitGalleryV2Screen.Phase.ACTING, "acting after Deal");
		f.click("action.hit");
		f.waitChrome(c -> s.demo().heroCards() == 3, "hit");
		f.click("action.split"); // disabled: says why
		if (f.status().isEmpty()) throw new AssertionError("disabled action showed no reason");
		f.shot("v2_kit_" + tag + "_acting");
		// Stand = primary → result with stamps and the banner
		f.key(KeyMap.SPACE);
		f.waitChrome(c -> s.demo().phase() == KitGalleryV2Screen.Phase.RESULT, "result after Stand");
		f.context().waitTicks(4);
		f.shot("v2_kit_" + tag + "_result");
		// Rebet
		f.click(KeyMap.Ids.PRIMARY);
		f.waitChrome(c -> s.demo().phase() == KitGalleryV2Screen.Phase.BETTING && s.demo().bet() == 26, "rebet 26");
		// the watchdog: a dropped action shows the reason and resyncs
		f.client(mc -> s.deaf = true);
		f.click("chip.1");
		f.context().waitTicks(10);
		f.shot("v2_kit_" + tag + "_pending");
		f.waitChrome(c -> s.sent.contains("resync"), "resync after a dropped action");
		String why = f.status();
		report.add("  " + tag + " watchdog → '" + why + "'");
		if (why.isEmpty()) throw new AssertionError("watchdog without a reason");
		f.client(mc -> s.deaf = false);
		// watching (not seated): Sit down
		f.client(mc -> {
			s.phase(KitGalleryV2Screen.Phase.WATCHING);
			return null;
		});
		f.context().waitTicks(3);
		f.hover("seat.2");
		f.shot("v2_kit_" + tag + "_watching");
	}

	private void galleryShots(RealFlow f, String tag) {
		KitGalleryV2Screen s = f.client(mc -> (KitGalleryV2Screen) mc.gui.screen());
		f.shot("v2_kit_" + tag + "_betting");
		for (KitGalleryV2Screen.Phase p : new KitGalleryV2Screen.Phase[] {KitGalleryV2Screen.Phase.ACTING, KitGalleryV2Screen.Phase.RESULT}) {
			f.client(mc -> {
				s.phase(p);
				return null;
			});
			f.context().waitTicks(4);
			f.shot("v2_kit_" + tag + "_" + p.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/** The core {@code resync} action through the real packet handler answers with a state whose ack grew. */
	private void resyncOnARealTable(RealFlow f) {
		f.size(1920, 1080, 4);
		BlockPos pos = f.place(BlackjackModule.TABLE.block());
		f.use(pos);
		f.context().waitFor(mc -> mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.table.CasinoTableScreen, RealFlow.TIMEOUT);
		f.context().waitTicks(5);
		long before = f.client(mc -> ClientTableCache.get(pos).getLongOr("ack", -1));
		f.client(mc -> {
			ClientPlayNetworking.send(new TableActionPayload(pos, "resync", new CompoundTag()));
			return null;
		});
		f.context().waitFor(mc -> ClientTableCache.get(pos).getLongOr("ack", -1) > before, RealFlow.TIMEOUT);
		long after = f.client(mc -> ClientTableCache.get(pos).getLongOr("ack", -1));
		report.add("resync on a real blackjack table: ack " + before + " → " + after);
		f.close();
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("v2_kit_report.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			// best effort
		}
	}

	@SuppressWarnings("unused")
	private static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
