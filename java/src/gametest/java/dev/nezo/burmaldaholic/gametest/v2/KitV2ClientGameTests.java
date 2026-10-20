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
			k2OnARealTable(f);
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

	/**
	 * Phase 2 kit fixes on a real blackjack table at 480 × 270 (K2b): a held chip key places ONE chip (auto-repeat, R-KW M5);
	 * key plates are key caps, never {@code key.keyboard.*} (B1); a dropped bet shows the reason, resyncs and the next
	 * press recovers (§11.2); a vanilla toast waits while the table screen is open (DES 6).
	 */
	private void k2OnARealTable(RealFlow f) {
		try {
			f.size(1920, 1080, 4);
			BlockPos pos = f.place(BlackjackModule.TABLE.block(), 1);
			dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen s = f.open(pos, dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen.class);
			java.util.UUID me = f.server(server -> RealFlow.player(server).getUUID());
			f.waitChrome(c -> "sit".equals(c.primary.gate().action()) && c.primary.gate().enabled(), "Sit down");
			f.click(KeyMap.Ids.PRIMARY);
			f.waitServer(pos, dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
			f.waitChrome(c -> c.bet.visible() && c.bet.chips().length >= 2 && c.bet.chipGate(0).enabled(), "the chip tray");
			long chip = f.client(mc -> s.chrome().bet.chips()[0]);
			long start = f.client(mc -> s.chrome().bet.view().myTotal());
			int one = dev.nezo.burmaldaholic.client.ui.v2.V2Input.keyCode(KeyMap.KEY_1);
			f.client(mc -> {
				s.keyPressed(new net.minecraft.client.input.KeyEvent(one, 0, 0));
				s.keyPressed(new net.minecraft.client.input.KeyEvent(one, 0, 0)); // the OS auto-repeat: no release in between
				return null;
			});
			f.context().waitTicks(4);
			long held = f.client(mc -> s.chrome().bet.view().myTotal());
			report.add("K2 held key 1 twice: bet " + start + " → " + held + " (chip " + chip + ")");
			if (held != start + chip) throw new AssertionError("a held '1' placed " + (held - start) + ", want one chip " + chip);
			f.client(mc -> {
				s.keyReleased(new net.minecraft.client.input.KeyEvent(one, 0, 0));
				s.keyPressed(new net.minecraft.client.input.KeyEvent(one, 0, 0));
				s.keyReleased(new net.minecraft.client.input.KeyEvent(one, 0, 0));
				return null;
			});
			f.context().waitTicks(4);
			long again = f.client(mc -> s.chrome().bet.view().myTotal());
			if (again != start + 2 * chip) throw new AssertionError("press after release: bet " + again + ", want " + (start + 2 * chip));
			// T-C2 V3: a big-win celebration does not swallow a click on an enabled kit target; a click elsewhere only skips
			playCelebration(f);
			if (!f.client(mc -> dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get().skippable())) throw new AssertionError("no skippable celebration");
			f.click("chip.0");
			long during = f.client(mc -> s.chrome().bet.view().myTotal());
			boolean skipped = f.client(mc -> !dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get().skippable());
			report.add("K2 click on chip.0 during a celebration: bet " + again + " → " + during + ", skipped " + skipped);
			if (during != again + chip || !skipped) throw new AssertionError("a chip click during a celebration: bet " + during + " (want " + (again + chip)
				+ "), skipped " + skipped);
			playCelebration(f);
			double[] empty = f.client(mc -> {
				var c = s.chrome();
				var p = c.grid().play;
				for (int y = p.y() + 4; y < p.bottom(); y += 3) {
					for (int x = p.x() + 4; x < p.right(); x += 7) if (c.hits().at(x + 0.5, y + 0.5).isEmpty()) return new double[] {c.viewport().windowX(x + 0.5), c.viewport().windowY(y + 0.5)};
				}
				return null;
			});
			if (empty == null) throw new AssertionError("no empty felt point");
			f.context().getInput().setCursorPos(empty[0], empty[1]);
			f.context().waitTick();
			f.context().getInput().pressMouse(dev.nezo.burmaldaholic.client.input.Inputs.MOUSE_LEFT);
			f.context().waitTicks(2);
			long after = f.client(mc -> s.chrome().bet.view().myTotal());
			boolean skipped2 = f.client(mc -> !dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get().skippable());
			report.add("K2 click on the empty felt during a celebration: bet " + after + ", skipped " + skipped2);
			if (after != during || !skipped2) throw new AssertionError("an empty-felt click during a celebration: bet " + after + ", skipped " + skipped2);
			f.context().waitTicks(20);
			// B1: key plates
			String h = f.client(mc -> dev.nezo.burmaldaholic.client.ui.v2.V2Button.keyName(KeyMap.H).getString());
			if (h.length() != 1) throw new AssertionError("keyName(H) = '" + h + "'");
			List<String> raw = f.client(mc -> {
				List<String> out = new ArrayList<>();
				for (var e : s.chrome().keys().entries()) {
					var n = dev.nezo.burmaldaholic.client.ui.v2.V2Button.keyName(e.getKey());
					if (n != null && n.getString().startsWith("key.")) out.add(e.getKey() + "=" + n.getString());
				}
				return out;
			});
			report.add("K2 keyName(H) = '" + h + "'; raw plates " + raw);
			if (!raw.isEmpty()) throw new AssertionError("raw key names on plates: " + raw);
			f.shot("v2_kit_k2_bj_480_bet");
			// a dropped bet: reason + resync, then the next press works
			f.waitChrome(c -> c.primary.gate().enabled(), "Deal enabled");
			V2Chrome.dropNextSends = 1;
			f.click(KeyMap.Ids.PRIMARY);
			f.waitChrome(c -> c.consumeResynced(), "the watchdog resynced a dropped bet");
			String why = f.status();
			report.add("K2 dropped bet → '" + why + "'");
			if (why.isEmpty()) throw new AssertionError("no reason after a dropped bet");
			f.shot("v2_kit_k2_bj_480_dropped");
			f.waitServer(pos, dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == 0, "the dropped bet never reached the table");
			f.waitChrome(c -> c.primary.gate().enabled() && !c.watchdog().pending(), "Deal enabled again");
			// T-C2 V3, the Dock primary: Deal clicked once during a big-win celebration deals (no swallowed first click)
			playCelebration(f);
			if (!f.client(mc -> dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get().skippable())) throw new AssertionError("no skippable celebration for the primary");
			f.click(KeyMap.Ids.PRIMARY);
			report.add("K2 primary clicked once during a celebration");
			long want = start + 3 * chip; // two held-key chips + the one clicked during the celebration
			f.waitServer(pos, dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == want
				|| t.writeClientState(RealFlow.player(t.getLevel().getServer())).getLongOr("last_bet", 0) == want, "the bet after the drop went through");
			// DES 6: a vanilla toast waits
			f.client(mc -> {
				net.minecraft.client.gui.components.toasts.SystemToast.add(mc.gui.toastManager(),
					net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION, dev.nezo.burmaldaholic.core.text.Texts.raw("K2"),
					dev.nezo.burmaldaholic.core.text.Texts.raw("toast"));
				return null;
			});
			f.context().waitTicks(10);
			int toastDuring = f.client(mc -> visibleToasts(mc));
			f.close();
			f.context().waitTicks(10);
			int toastAfter = f.client(mc -> visibleToasts(mc));
			report.add("K2 toast: visible during the screen " + toastDuring + ", after closing " + toastAfter);
			if (toastDuring != 0 || toastAfter == 0) throw new AssertionError("toast visible during " + toastDuring + ", after " + toastAfter);
		} catch (AssertionError | RuntimeException e) {
			failures.add("k2 real table: " + e.getMessage());
			report.add("  FAILED k2: " + e);
			f.shot("v2_kit_k2_failed");
			f.close();
		} finally {
			V2Chrome.dropNextSends = 0;
		}
	}

	private static void playCelebration(RealFlow f) {
		f.client(mc -> {
			dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get().play(dev.nezo.burmaldaholic.client.fx.CelebrationRequest.core(
				dev.nezo.burmaldaholic.core.anim.WinTier.BIG, 500, 10, 7));
			return null;
		});
		f.context().waitTicks(4);
	}

		private static int visibleToasts(Minecraft mc) {
		try {
			var field = net.minecraft.client.gui.components.toasts.ToastManager.class.getDeclaredField("visibleToasts");
			field.setAccessible(true);
			return ((List<?>) field.get(mc.gui.toastManager())).size();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("ToastManager.visibleToasts: " + e);
		}
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
