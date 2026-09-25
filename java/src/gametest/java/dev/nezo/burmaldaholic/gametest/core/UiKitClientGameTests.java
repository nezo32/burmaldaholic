package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.table.cards.CardGfx;
import dev.nezo.burmaldaholic.client.table.cards.SeatPlate;
import dev.nezo.burmaldaholic.client.table.cards.TableTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Tester pass over the J-L2 UI kit and the J-L4 card kit on a real client: {@link KitGalleryScreen} in every scene
 * (village, bastion, End, lobby, loan) in English and Russian ({@code jtest_kit_<lang>_<theme>}), at GUI scales 1–4 and
 * an odd window ({@code jtest_kit_scale<k>}, {@code jtest_kit_odd}); keyboard focus / Tab order / Enter and narration of
 * every kit widget; text fitting with the real font; theme resolution (force / config value / state / dimension).
 */
public class UiKitClientGameTests implements FabricClientGameTest {
	private static final CasinoTheme[] SCENES = CasinoTheme.values();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 800);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
		context.waitTicks(2);
		try {
			for (String lang : new String[] {"en_us", "ru_ru"}) {
				language(context, lang);
				for (CasinoTheme scene : SCENES) {
					open(context, scene);
					context.takeScreenshot("jtest_kit_" + lang.substring(0, 2) + "_" + scene.id);
				}
				fitting(context, lang);
			}
			language(context, "en_us");
			keyboardAndNarration(context);
			scales(context);
			themes(context);
		} finally {
			context.runOnClient(mc -> {
				CasinoTheme.force(null);
				TableTheme.force(null);
				mc.options.guiScale().set(0);
				mc.resizeGui();
				mc.gui.setScreen(null);
			});
		}
	}

	private static void open(ClientGameTestContext context, CasinoTheme scene) {
		context.setScreen(() -> new KitGalleryScreen(scene));
		context.waitForScreen(KitGalleryScreen.class);
		context.waitTicks(5); // past the 150 ms entrance
	}

	/** Every kit text box stays inside its width with the real font (the longest RU strings). */
	private static void fitting(ClientGameTestContext context, String lang) {
		context.runOnClient(mc -> {
			var font = mc.font;
			for (String key : new String[] {"gui.burmaldaholic.baccarat.chemmy.rules.3", "gui.burmaldaholic.blackjack.even_money_prompt",
				"gui.burmaldaholic.blackjack.split.tooltip", "gui.burmaldaholic.common.deal"}) {
				Component c = Component.translatable(key);
				for (int w : new int[] {0, 1, 8, 20, 44, 62, 100, 300}) {
					int fit = font.width(CasinoUi.fit(font, c, w));
					if (fit > Math.max(w, font.width("…"))) throw new AssertionError(lang + " CasinoUi.fit(" + key + ", " + w + ") = " + fit);
					int fitted = CardGfx.fittedWidth(font, c, w);
					if (fitted > Math.max(w, (int) Math.ceil(font.width("…") * 0.5f))) throw new AssertionError(lang + " CardGfx.fittedWidth(" + key + ", " + w + ") = " + fitted);
				}
				var info = new SeatPlate.Info(c, null, null, 2, c, 0xFFFFFFFF, SeatPlate.State.NORMAL, false);
				if (SeatPlate.width(font, info) > SeatPlate.MAX_TEXT_W + 30) throw new AssertionError(lang + " seat plate grows with " + key);
			}
		});
		open(context, CasinoTheme.VILLAGE);
		List<String> problems = context.computeOnClient(mc -> {
			List<String> out = new ArrayList<>();
			KitGalleryScreen s = (KitGalleryScreen) mc.gui.screen();
			for (AbstractWidget w : s.kit) {
				if (w.getX() < 0 || w.getY() < 0 || w.getRight() > s.width || w.getBottom() > s.height) out.add("outside: " + w.getMessage().getString());
			}
			return out;
		});
		if (!problems.isEmpty()) throw new AssertionError(lang + ": " + problems);
	}

	private static void keyboardAndNarration(ClientGameTestContext context) {
		open(context, CasinoTheme.VILLAGE);
		// Tab walks the kit widgets and never lands on a disabled one
		List<String> focused = new ArrayList<>();
		for (int i = 0; i < 24; i++) {
			context.getInput().pressKey(KEY_TAB);
			context.waitTick();
			String f = context.computeOnClient(mc -> {
				var el = mc.gui.screen().getFocused();
				if (el instanceof AbstractWidget w) {
					if (!w.active) return "DISABLED:" + w.getMessage().getString();
					if (!w.isFocused()) return "NOT-FOCUSED:" + w.getMessage().getString();
					return w.getClass().getSimpleName() + ":" + w.getMessage().getString();
				}
				return String.valueOf(el);
			});
			if (f.startsWith("DISABLED") || f.startsWith("NOT-FOCUSED")) throw new AssertionError("keyboard focus: " + f);
			focused.add(f);
			if (i == 0) context.takeScreenshot("jtest_kit_focus");
		}
		if (focused.stream().noneMatch(f -> f.startsWith("CasinoButton")) || focused.stream().noneMatch(f -> f.startsWith("BookmarkTab"))
			|| focused.stream().noneMatch(f -> f.startsWith("CardButton"))) {
			throw new AssertionError("Tab must reach every kit widget kind: " + focused);
		}
		// Enter activates the focused button
		context.runOnClient(mc -> {
			KitGalleryScreen s = (KitGalleryScreen) mc.gui.screen();
			s.setFocused(s.kit.getFirst());
			s.presses = 0;
		});
		context.getInput().pressKey(KEY_ENTER);
		context.waitTick();
		int presses = context.computeOnClient(mc -> ((KitGalleryScreen) mc.gui.screen()).presses);
		if (presses != 1) throw new AssertionError("Enter on a focused CasinoButton presses it once, got " + presses);
		context.takeScreenshot("jtest_kit_pressed");
		// narration: every kit widget narrates its label; a disabled one its reason (tooltip)
		List<String> bad = context.computeOnClient(mc -> {
			List<String> out = new ArrayList<>();
			KitGalleryScreen s = (KitGalleryScreen) mc.gui.screen();
			for (AbstractWidget w : s.kit) {
				StringBuilder sb = new StringBuilder();
				w.updateNarration(collector(sb));
				String label = w.getMessage().getString();
				String text = sb.toString();
				boolean iconOnly = label.isEmpty();
				if (!iconOnly && !text.contains(label)) out.add(w.getClass().getSimpleName() + " '" + label + "' narrates '" + text + "'");
				if (iconOnly && text.isBlank()) out.add("icon-only button narrates nothing");
				if (w instanceof CasinoButton b && !b.active && label.contains("Split") && !text.contains("pair")) {
					out.add("disabled reason not narrated: '" + text + "'");
				}
			}
			return out;
		});
		if (!bad.isEmpty()) throw new AssertionError("narration: " + bad);
	}

	/** GLFW key codes (the LWJGL GLFW class is not on the 26.3 compile classpath; the codes are fixed). */
	private static final int KEY_TAB = 258;
	private static final int KEY_ENTER = 257;

	/**
	 * A narration sink that records the text. A proxy, not an anonymous class: 26.3 added the abstract
	 * {@code narrationTrigger()}, so an implementation written for 26.2 does not compile there (and vice versa).
	 */
	private static NarrationElementOutput collector(StringBuilder sb) {
		Object[] self = new Object[1];
		self[0] = java.lang.reflect.Proxy.newProxyInstance(NarrationElementOutput.class.getClassLoader(), new Class<?>[] {NarrationElementOutput.class},
			(proxy, m, args) -> {
				if (m.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, m, args);
				switch (m.getName()) {
					case "add" -> {
						if (args != null && args.length == 2 && args[1] instanceof NarrationThunk<?> thunk) {
							NarratedElementType type = (NarratedElementType) args[0];
							thunk.getText(t -> sb.append('[').append(type).append("] ").append(t).append(' '));
						}
						return null;
					}
					case "nest" -> {
						return self[0];
					}
					case "hashCode" -> {
						return System.identityHashCode(proxy);
					}
					case "equals" -> {
						return proxy == args[0];
					}
					case "toString" -> {
						return "narration collector";
					}
					default -> {
						Class<?> r = m.getReturnType();
						return r.isEnum() && r.getEnumConstants().length > 0 ? r.getEnumConstants()[0] : null;
					}
				}
			});
		return (NarrationElementOutput) self[0];
	}

	/** Nine-slices at GUI scales 1–4 and an odd window: screenshots, the panel stays at integer coordinates. */
	private static void scales(ClientGameTestContext context) {
		for (int k = 1; k <= 4; k++) {
			final int scale = k;
			context.runOnClient(mc -> {
				mc.options.guiScale().set(scale);
				mc.resizeGui();
			});
			open(context, CasinoTheme.END);
			context.takeScreenshot("jtest_kit_scale" + k);
		}
		context.getInput().resizeWindow(1283, 721);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.resizeGui();
		});
		open(context, CasinoTheme.BASTION);
		context.takeScreenshot("jtest_kit_odd");
		context.getInput().resizeWindow(1280, 800);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
	}

	/** Theme resolution: forced (config) > table state > dimension; the card-table theme follows the kit's. */
	private static void themes(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			CasinoTheme.force(null);
			TableTheme.force(null);
			check(CasinoTheme.current() == CasinoTheme.VILLAGE, "no level: village");
			check(CasinoTheme.resolve("end") == CasinoTheme.END, "state theme");
			check(CasinoTheme.resolve("") == CasinoTheme.VILLAGE, "empty state: location");
			check(CasinoTheme.parseOverride("auto") == null && CasinoTheme.parseOverride(null) == null, "auto");
			check(CasinoTheme.parseOverride(" End ") == CasinoTheme.END, "case / spaces");
			check(CasinoTheme.parseOverride("lobby") == null, "only location themes can be forced");
			CasinoTheme.force(CasinoTheme.BASTION);
			check(CasinoTheme.current() == CasinoTheme.BASTION && CasinoTheme.resolve("end") == CasinoTheme.BASTION, "forced wins");
			check(TableTheme.current() == TableTheme.BASTION, "card tables follow the kit");
			CasinoTheme.force(CasinoTheme.LOAN);
			check(CasinoTheme.forced() == null, "a scene theme is not a location override");
			TableTheme.force(TableTheme.END);
			check(CasinoTheme.current() == CasinoTheme.END, "TableTheme.force forces the kit too");
			TableTheme.force(null);
			check(CasinoTheme.forced() == null, "cleared");
			for (CasinoTheme t : CasinoTheme.values()) check(TableTheme.of(t).casino() == (t.location() ? t : CasinoTheme.VILLAGE), "mapping " + t);
		});
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			context.waitFor(mc -> mc.level != null && mc.player != null, 1200);
			context.runOnClient(mc -> check(CasinoTheme.current() == CasinoTheme.VILLAGE, "overworld → village"));
			dimension(context, world, "the_nether", Level.NETHER, CasinoTheme.BASTION);
			dimension(context, world, "the_end", Level.END, CasinoTheme.END);
		}
	}

	private static void dimension(ClientGameTestContext context, TestSingleplayerContext world, String id, net.minecraft.resources.ResourceKey<Level> key,
			CasinoTheme expected) {
		world.getServer().runCommand("execute in minecraft:" + id + " run tp @p 0 100 0");
		context.waitFor(mc -> mc.level != null && mc.level.dimension() == key, 400);
		context.waitTicks(5);
		context.runOnClient(mc -> {
			check(CasinoTheme.current() == expected, id + " → " + expected + " (got " + CasinoTheme.current() + ")");
			check(TableTheme.current().casino() == expected, id + " card tables");
		});
		open(context, context.computeOnClient(mc -> CasinoTheme.current())); // Minecraft.getInstance() only on the client thread
		context.takeScreenshot("jtest_kit_dim_" + expected.id);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("theme resolution: " + what);
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
