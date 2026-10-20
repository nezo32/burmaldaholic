package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.client.ui.v2.V2Input;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import dev.nezo.burmaldaholic.core.ui.v2.ViewportPolicy;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;

/**
 * Lane RS, from tester T-K2 R3 (owner's setup 4K + GUI Auto → 427 × 240, EN and RU): deterministic asserts only, no
 * visual review.
 * <ol>
 *   <li>The owner's real blackjack (watching, seated, betting, acting, after H): every status word readable
 *       ({@code StatusLine.lost}: what its plan would cut), the action key plates kept (P2-a), H presses Hit.</li>
 *   <li>B6 at odd sizes (1366 × 768, 2560 × 1080, 5:4 1280 × 1024, Auto and GUI 2): one set of rows per size for all 11
 *       games, the policy viewport, targets inside the frame / PlayArea, no kit overlaps, no Dock overflow, no lost status.</li>
 *   <li>GUI scale changed while a screen is open: the same screen, a clean audit, real clicks hit, the felt follows the
 *       frame; a real blackjack is seated / bet / dealt across scale changes (SERVER stake).</li>
 *   <li>No HUD around a framed screen (P2-b); key caps from the player's layout (P2-c).</li>
 * </ol>
 * Report {@code tk2c_report.txt}. Poker's pick-stakes Dock overflow is B2's (T-K2 D4) and is reported as KNOWN, not failed.
 */
public class ResolutionStressClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	/** window w, h, GUI scale (0 = Auto). */
	private static final int[][] ODD = {{3840, 2160, 0}, {1366, 768, 0}, {1366, 768, 2}, {2560, 1080, 0}, {2560, 1080, 2}, {1280, 1024, 0},
		{1280, 1024, 2}};
	private static final int[][] ODD_RU = {{3840, 2160, 0}, {1366, 768, 0}, {2560, 1080, 0}, {1280, 1024, 0}};

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
			ownerBlackjack(f, "en_us", 0);
			everyGame(f, "en_us", ODD);
			f.language("ru_ru");
			ownerBlackjack(f, "ru_ru", 0);
			everyGame(f, "ru_ru", ODD_RU);
			f.language("en_us");
			guiScaleWhileOpen(f);
			hudAroundFrame(f);
			keyCaps(f);
			f.close();
		} finally {
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("resolution stress: " + failures.size() + " failures, see tk2c_report.txt; first " + failures.get(0));
	}

	// ---- status line: is any text lost? (the kit's own plan, drawn as is) -------------------------------------------

	static String statusLoss(V2Chrome c, Font font) {
		var box = c.status.box();
		var l1 = c.status.line1();
		var l2 = c.status.line2();
		if (box.isEmpty()) return (l1 != null || l2 != null) ? "status box empty but text '" + l1 + "' / '" + l2 + "'" : "";
		return c.status.lost(font);
	}

	private static String statusText(V2Chrome c) {
		var l1 = c.status.line1();
		var l2 = c.status.line2();
		return "'" + (l1 == null ? "" : l1.getString()) + "' | '" + (l2 == null ? "" : l2.getString()) + "'" + (c.status.strip().isEmpty() ? "" : " [strip " + c.status.strip() + "]")
			+ " box " + c.status.box();
	}

	/** Layout audit of the open v2 screen: policy, frame, rows, targets, overlaps of kit targets, Dock overflow, status. */
	static final String KNOWN = "KNOWN(B2 D4) ";

	/** The audit's real problems (KNOWN items of other packages are only reported). */
	private static List<String> real(List<String> audit) {
		return audit.stream().filter(x -> !x.startsWith(KNOWN)).toList();
	}

	private static List<String> audit(Minecraft mc) {
		List<String> bad = new ArrayList<>();
		var s = mc.gui.screen();
		if (!(s instanceof V2Chrome.Holder h) || !(s instanceof FitScaled fit)) {
			bad.add("not a v2 screen: " + s);
			return bad;
		}
		V2Chrome c = h.chrome();
		Viewport vp = c.viewport();
		Viewport want = ViewportPolicy.of(mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale());
		if (!vp.equals(want)) bad.add("viewport " + vp + " != policy " + want);
		TableGrid g = c.grid();
		TableGrid ref = new TableGrid(want);
		if (!g.topBar.equals(ref.topBar) || !g.play.equals(ref.play) || !g.dock.equals(ref.dock)) bad.add("rows differ from TableGrid(policy)");
		if (Math.abs(fit.fitScale() - vp.factor()) > 1e-4) bad.add("pose factor " + fit.fitScale());
		if (Math.abs(fit.offsetX() - vp.guiOx()) > 1e-4 || Math.abs(fit.offsetY() - vp.guiOy()) > 1e-4) bad.add("offset");
		if (s.width != vp.frameW() || s.height != vp.frameH()) bad.add("screen " + s.width + "x" + s.height + " != frame");
		// the guiOx in framebuffer px must be whole (crisp)
		double fbOx = vp.ox() * vp.k(), fbOy = vp.oy() * vp.k();
		if (fbOx != Math.rint(fbOx) || fbOy != Math.rint(fbOy)) bad.add("frame offset not whole fb px");
		List<HitMap.Target> ts = c.hits().targets();
		List<HitMap.Target> kit = new ArrayList<>();
		for (HitMap.Target t : ts) {
			if (!t.box().inside(g.screen)) bad.add(t.id() + " outside the frame " + t.box());
			else if (t.id().startsWith("felt.") && !t.box().inside(g.play)) bad.add(t.id() + " outside the PlayArea " + t.box());
			if (!t.id().startsWith("felt.")) kit.add(t);
		}
		for (int i = 0; i < kit.size(); i++) {
			for (int j = i + 1; j < kit.size(); j++) {
				if (kit.get(i).box().intersects(kit.get(j).box())) bad.add(kit.get(i).id() + " overlaps " + kit.get(j).id());
			}
		}
		if (c.dock() != null && c.dock().overflow() > 0) {
			// poker's pick-stakes Dock is B2's (T-K2 D4): reported, not failed
			bad.add((s.getClass().getSimpleName().equals("PokerScreen") ? KNOWN : "") + "dock overflow " + c.dock().overflow());
		}
		String loss = statusLoss(c, mc.font);
		if (!loss.isEmpty()) bad.add("status loses text: " + loss);
		return bad;
	}

	// ---- 1. owner's cell: real blackjack, watching / seated / betting / acting -----------------------------------------

	private void ownerBlackjack(RealFlow f, String lang, int dx) {
		String tag = "owner_bj_" + lang + "_4k_auto";
		try {
			f.size(3840, 2160, 0);
			BlockPos pos = f.place(BlackjackModule.TABLE.block(), dx);
			f.open(pos, dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen.class);
			f.context().waitTicks(10);
			Viewport vp = f.viewport();
			report.add(tag + ": " + vp + " tier " + f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().grid().tier));
			if (vp.w() != 427 || vp.h() != 240 || vp.k() != 9) failures.add(tag + ": owner cell is not 427x240 k9: " + vp);
			f.waitChrome(c -> "sit".equals(c.primary.gate().action()) && c.primary.gate().enabled(), "Sit down");
			check(f, tag + " watching");
			UUID me = f.server(server -> RealFlow.player(server).getUUID());
			f.click(KeyMap.Ids.PRIMARY);
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated");
			f.waitChrome(c -> c.bet.visible() && c.bet.chipGate(0).enabled(), "chips");
			f.context().waitTicks(4);
			check(f, tag + " seated");
			boolean acting = false;
			for (int attempt = 0; attempt < 5 && !acting; attempt++) {
				if (f.enabledTarget("chip.0")) {
					f.click("chip.0");
					f.context().waitTicks(4);
					check(f, tag + " betting#" + attempt);
				}
				f.waitChrome(c -> c.primary.gate().enabled(), "primary enabled");
				f.click(KeyMap.Ids.PRIMARY);
				for (int t = 0; t < 160 && !acting; t += 4) {
					f.context().waitTicks(4);
					acting = f.enabledTarget("action.hit");
				}
				if (!acting) f.context().waitTicks(60);
			}
			if (!acting) throw new AssertionError("never reached the acting state");
			f.context().waitTicks(10);
			check(f, tag + " acting");
			String plates = f.client(mc -> {
				V2Chrome c = ((V2Chrome.Holder) mc.gui.screen()).chrome();
				return "compactActions=" + c.dock().compactActions() + " actions=" + c.actions.gates().size() + " statusBox=" + c.dock().status()
					+ " strip=" + c.dock().hintStrip();
			});
			report.add("  acting plates: " + plates);
			if (plates.contains("compactActions=true")) failures.add(tag + ": P2-a key plates dropped on the acting Dock (" + plates + ")");
			// real key H on the acting dock → the server sees a hit (hand grows or round moves on)
			f.key(KeyMap.H);
			f.context().waitTicks(10);
			String pressed = f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().lastPressed());
			report.add("  H pressed -> " + pressed);
			if (!pressed.equals("action.hit")) failures.add(tag + ": H did not press action.hit (" + pressed + ")");
			check(f, tag + " after H");
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
		} finally {
			f.close();
		}
	}

	private void check(RealFlow f, String tag) {
		String[] r = f.client(mc -> {
			List<String> a = audit(mc);
			return new String[] {statusText(((V2Chrome.Holder) mc.gui.screen()).chrome()), String.join("; ", a), String.join("; ", real(a))};
		});
		report.add("  " + tag + ": status " + r[0] + (r[1].isEmpty() ? " audit ok" : " AUDIT " + r[1]));
		if (!r[2].isEmpty()) failures.add(tag + ": " + r[2]);
	}

	// ---- 2. B6 at odd sizes: every game ------------------------------------------------------------------------------

	private void everyGame(RealFlow f, String lang, int[][] sizes) {
		var tables = new LinkedHashMap<String, dev.nezo.burmaldaholic.core.table.TableType<?>>();
		tables.put("blackjack", BlackjackModule.TABLE);
		tables.put("baccarat", dev.nezo.burmaldaholic.games.baccarat.BaccaratModule.TABLE);
		tables.put("poker", dev.nezo.burmaldaholic.games.poker.PokerModule.TABLE);
		tables.put("uth", dev.nezo.burmaldaholic.games.uth.UthModule.TABLE);
		tables.put("roulette", dev.nezo.burmaldaholic.games.roulette.RouletteModule.TABLE);
		tables.put("craps", dev.nezo.burmaldaholic.games.craps.CrapsModule.TABLE);
		tables.put("plinko", dev.nezo.burmaldaholic.games.extras.ExtrasModule.PLINKO);
		tables.put("wheel", dev.nezo.burmaldaholic.games.extras.ExtrasModule.WHEEL);
		Map<String, Map<String, String>> rows = new TreeMap<>();
		for (var t : tables.entrySet()) cells(f, lang, t.getKey(), sizes, rows, () -> f.use(f.place(t.getValue())));
		cells(f, lang, "coin", sizes, rows, () -> f.server(server -> {
			dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame.open(RealFlow.player(server));
			return null;
		}));
		cells(f, lang, "dice", sizes, rows, () -> f.server(server -> {
			dev.nezo.burmaldaholic.games.extras.server.DiceGame.open(RealFlow.player(server), null);
			return null;
		}));
		cells(f, lang, "scratch", sizes, rows, () -> f.server(server -> {
			dev.nezo.burmaldaholic.games.extras.server.ScratchGame.use(RealFlow.player(server), net.minecraft.world.item.ItemStack.EMPTY,
				dev.nezo.burmaldaholic.games.extras.logic.Scratch.Kind.BASIC);
			return null;
		}));
		for (var e : rows.entrySet()) {
			var distinct = new TreeSet<>(e.getValue().values());
			report.add("B6 " + lang + " " + e.getKey() + ": " + e.getValue().size() + " games, " + distinct.size() + " distinct rows " + distinct);
			if (e.getValue().size() != 11) failures.add("B6 " + lang + " " + e.getKey() + ": only " + e.getValue().keySet());
			if (distinct.size() != 1) failures.add("B6 " + lang + " " + e.getKey() + ": rows differ " + e.getValue());
		}
	}

	private void cells(RealFlow f, String lang, String game, int[][] sizes, Map<String, Map<String, String>> rows, Runnable open) {
		String tag = "B6 " + lang + " " + game;
		try {
			f.size(sizes[0][0], sizes[0][1], sizes[0][2]);
			open.run();
			f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder, RealFlow.TIMEOUT);
			f.context().waitTicks(8);
			for (int[] sz : sizes) {
				f.size(sz[0], sz[1], sz[2]);
				f.context().waitTicks(4);
				String[] out = f.client(mc -> {
					V2Chrome c = ((V2Chrome.Holder) mc.gui.screen()).chrome();
					Viewport vp = c.viewport();
					TableGrid g = c.grid();
					String key = sz[0] + "x" + sz[1] + "@" + sz[2] + "->" + vp.w() + "x" + vp.h() + "k" + vp.k() + " frame " + vp.frameW() + "x" + vp.frameH() + "+" + vp.ox()
						+ "," + vp.oy() + " " + g.tier + " " + g.room;
					List<String> a = audit(mc);
					return new String[] {key, g.topBar + " " + g.play + " " + g.dock, String.join("; ", a), statusText(c), String.join("; ", real(a))};
				});
				rows.computeIfAbsent(out[0], k -> new TreeMap<>()).put(game, out[1]);
				report.add("  " + tag + " " + out[0] + ": " + (out[2].isEmpty() ? "ok" : out[2]) + " status " + out[3]);
				if (!out[4].isEmpty()) failures.add(tag + " " + out[0] + ": " + out[4]);
			}
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
		} finally {
			f.close();
		}
	}

	// ---- 3. GUI scale changes while a screen is open -----------------------------------------------------------------

	private void guiScaleWhileOpen(RealFlow f) {
		int[] scales = {2, 4, 1, 3, 0, 2};
		// a V2SceneScreen (the gallery): click a chip at the real window px after every change; felt pixels follow the frame
		String tag = "gui-change gallery 2560x1440";
		try {
			f.size(2560, 1440, scales[0]);
			f.context().setScreen(KitGalleryV2Screen::new);
			f.context().waitFor(mc -> mc.gui.screen() instanceof KitGalleryV2Screen, RealFlow.TIMEOUT);
			f.context().waitTicks(10);
			Object first = f.client(mc -> mc.gui.screen());
			for (int gs : scales) {
				f.context().runOnClient(mc -> {
					mc.options.guiScale().set(gs);
					mc.resizeGui();
				});
				f.context().waitTicks(5);
				Object now = f.client(mc -> mc.gui.screen());
				if (now != first) failures.add(tag + " gui" + gs + ": the screen was replaced");
				List<String> bad = real(f.client(ResolutionStressClientGameTests::audit));
				Viewport vp = f.viewport();
				f.click("chip.1"); // throws when the HitMap under the real window px is not chip.1
				f.context().waitTicks(3);
				String px = feltPixels(f, "tk2c_gui_change_" + gs);
				report.add("  " + tag + " -> gui" + gs + ": " + vp + " " + (bad.isEmpty() ? "audit ok" : bad) + "; clicks ok; " + px);
				if (!bad.isEmpty()) failures.add(tag + " gui" + gs + ": " + bad);
				if (px.startsWith("FAIL")) failures.add(tag + " gui" + gs + ": " + px);
			}
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
		} finally {
			f.client(mc -> {
				mc.gui.setScreen(null);
				return null;
			});
			f.context().waitTicks(3);
		}
		// a V2TableScreen (real blackjack): sit at one scale, bet at another, deal at a third → the SERVER stake
		tag = "gui-change blackjack 2560x1440";
		try {
			f.size(2560, 1440, 2);
			BlockPos pos = f.place(BlackjackModule.TABLE.block(), 0);
			f.open(pos, dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen.class);
			UUID me = f.server(server -> RealFlow.player(server).getUUID());
			f.waitChrome(c -> "sit".equals(c.primary.gate().action()) && c.primary.gate().enabled(), "Sit down");
			setGui(f, 4);
			f.click(KeyMap.Ids.PRIMARY);
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated after gui 2 -> 4");
			f.waitChrome(c -> c.bet.visible() && c.bet.chipGate(0).enabled(), "chips");
			setGui(f, 3);
			f.click("chip.0");
			setGui(f, 1);
			f.click("chip.0");
			setGui(f, 0);
			List<String> bad = real(f.client(ResolutionStressClientGameTests::audit));
			if (!bad.isEmpty()) failures.add(tag + ": " + bad);
			f.waitChrome(c -> c.primary.gate().enabled(), "Deal");
			f.click(KeyMap.Ids.PRIMARY);
			f.waitServer(pos, BlackjackTableBlockEntity.class,
				t -> t.stakeOf(me) == 2 || t.writeClientState(RealFlow.player(t.getLevel().getServer())).getLongOr("last_bet", 0) == 2, "stake 2 after gui changes");
			report.add("  " + tag + ": seated at gui4, chips at gui3 + gui1, deal at auto -> server stake 2; audit " + (bad.isEmpty() ? "ok" : bad));
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
		} finally {
			f.close();
		}
	}

	private static void setGui(RealFlow f, int gs) {
		f.context().runOnClient(mc -> {
			mc.options.guiScale().set(gs);
			mc.resizeGui();
		});
		f.context().waitTicks(5);
	}

	/** Felt inside the PlayArea at the frame's window px, and no felt left of a framed frame (pixel samples in code only). */
	private String feltPixels(RealFlow f, String name) {
		Viewport vp = f.viewport();
		Box play = f.client(mc -> ((V2Chrome.Holder) mc.gui.screen()).chrome().grid().play);
		Path shot = f.shot(name);
		try {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(shot.toFile());
			int y = play.y() + play.h() / 2;
			int in = 0, out = 0, n = 9;
			for (int i = 0; i < n; i++) {
				int x = play.x() + 2 + i * (play.w() - 4) / (n - 1);
				if (greenish(px(img, vp.windowX(x), vp.windowY(y)))) in++;
				if (vp.ox() > 0 && greenish(px(img, vp.ox() * vp.k() / 2.0, vp.windowY(play.y() + i * play.h() / n)))) out++;
			}
			Files.deleteIfExists(shot);
			String r = "felt in " + in + "/" + n + ", left of frame " + out;
			return (in == 0 || out > 0 ? "FAIL " : "") + r;
		} catch (java.io.IOException e) {
			return "FAIL read " + e;
		}
	}

	private static int px(java.awt.image.BufferedImage img, double x, double y) {
		return img.getRGB(Math.max(0, Math.min(img.getWidth() - 1, (int) x)), Math.max(0, Math.min(img.getHeight() - 1, (int) y)));
	}

	private static boolean greenish(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return g > r + 15 && g > b + 15 && g > 50;
	}

	// ---- 4. P2-b: HUD around a framed screen (pixel diff of the hotbar area, HUD on vs F1) ---------------------------

	private static final java.util.concurrent.atomic.AtomicInteger HUD_CALLS = new java.util.concurrent.atomic.AtomicInteger();
	private static boolean hudHooked;

	/** Counts vanilla HUD passes with a HUD element after the hotbar (cancelled HUD = no calls). */
	private void hudAroundFrame(RealFlow f) {
		if (!hudHooked) {
			hudHooked = true;
			net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT, dev.nezo.burmaldaholic.Burmaldaholic.id("tk2c_probe"),
				(g, d) -> HUD_CALLS.incrementAndGet());
		}
		hudCell(f, 2560, 1440, 2, true);
		hudCell(f, 1920, 1080, 2, false);
		hudCell(f, 2560, 1080, 2, true);
	}

	private void hudCell(RealFlow f, int w, int h, int gs, boolean expectFramed) {
		String tag = "hud " + w + "x" + h + " gui" + gs;
		try {
			f.size(w, h, gs);
			f.context().runOnClient(mc -> mc.gui.setScreen(null));
			f.context().waitTicks(5);
			int base0 = HUD_CALLS.get();
			f.context().waitTicks(10);
			int noScreen = HUD_CALLS.get() - base0;
			f.context().setScreen(KitGalleryV2Screen::new);
			f.context().waitFor(mc -> mc.gui.screen() instanceof KitGalleryV2Screen, RealFlow.TIMEOUT);
			f.context().waitTicks(5);
			Viewport vp = f.viewport();
			int b = HUD_CALLS.get();
			f.context().waitTicks(10);
			int open = HUD_CALLS.get() - b;
			report.add(tag + ": " + vp + " framed=" + vp.framed() + " HUD passes/10 ticks: no screen " + noScreen + ", v2 screen open " + open);
			if (vp.framed() != expectFramed) failures.add(tag + ": framed=" + vp.framed() + " expected " + expectFramed);
			if (noScreen == 0) failures.add(tag + ": probe never ran (test broken)");
			if (vp.framed() && open > 0) failures.add(tag + ": P2-b HUD still drawn around the frame (" + open + " passes)");
		} catch (AssertionError | RuntimeException e) {
			failures.add(tag + ": " + e.getMessage());
			report.add("  FAILED " + tag + ": " + e);
		} finally {
			f.client(mc -> {
				mc.gui.setScreen(null);
				return null;
			});
			f.context().waitTicks(3);
		}
	}

	// ---- 5. P2-c: key caps -------------------------------------------------------------------------------------------

	private void keyCaps(RealFlow f) {
		String caps = f.client(mc -> {
			StringBuilder sb = new StringBuilder();
			List<String> bad = new ArrayList<>();
			for (char ch = 'a'; ch <= 'z'; ch++) {
				String n = String.valueOf(ch);
				String cap = V2Input.keyCap(n);
				sb.append(cap);
				if (cap == null || cap.codePointCount(0, cap.length()) != 1) bad.add(n + "->" + cap);
			}
			for (char ch = '1'; ch <= '5'; ch++) {
				String cap = V2Input.keyCap(String.valueOf(ch));
				sb.append(' ').append(cap);
				if (!String.valueOf(ch).equals(cap)) bad.add(ch + "->" + cap);
			}
			return sb + (bad.isEmpty() ? "" : " BAD " + bad);
		});
		report.add("key caps (this machine's layout): " + caps);
		if (caps.contains("BAD")) failures.add("key caps: " + caps);
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("tk2c_report.txt"), String.join("\n", report) + "\n" + "FAILURES " + failures.size() + "\n" + String.join("\n", failures) + "\n",
				StandardCharsets.UTF_8);
		} catch (Exception e) {
			// best effort
		}
	}
}
