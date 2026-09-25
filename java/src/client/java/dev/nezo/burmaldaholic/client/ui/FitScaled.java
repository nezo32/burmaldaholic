package dev.nezo.burmaldaholic.client.ui;

import com.mojang.blaze3d.platform.Window;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The compact layout of the fixed-size game screens (extras and PvP; extras.md §2.4) at small GUI sizes (GUI scale 3–4,
 * a small window): when the full 400 × 240 panel does not fit the GUI, the screen works in the GUI of the largest whole
 * scale that fits ({@link UiLayout#fitScale}) and is drawn at {@code k / guiScale}. The layout stays the full one — no
 * widget moves, so nothing can overlap or clip — and every art pixel stays a whole number of screen pixels.
 *
 * <p>Wiring: the screen reports {@link #fitScale()}; {@link ScreenEntrance} scales the pose around the screen's extract
 * (the vanilla dim, the scene, widgets, tooltips); a client mixin divides the GUI mouse position by the factor, so
 * clicks, drags, scrolls and hovers land on the drawn widgets. Screens that draw from other hooks (the PvP overlay)
 * wrap their drawing with {@link #push} / {@link #pop}.
 */
public interface FitScaled {
	/** Drawing factor of the screen's GUI onto the real GUI: 1 when the full layout fits, else {@code k / guiScale} &lt; 1. */
	float fitScale();

	/** The factor of the open screen (1 for any other screen). */
	static float current() {
		Screen s = Minecraft.getInstance().gui.screen();
		return s instanceof FitScaled f ? f.fitScale() : 1f;
	}

	/**
	 * Resizes {@code screen} into the GUI of its effective scale. Call at the start of {@code init()}; {@code memo}
	 * ({@code int[4]}: real w, real h, virtual w, virtual h) tells a re-init with the virtual size from a real resize.
	 *
	 * @return the drawing factor
	 */
	static float apply(Screen screen, boolean enabled, int needW, int needH, int[] memo) {
		if (screen.width != memo[2] || screen.height != memo[3]) {
			memo[0] = screen.width;
			memo[1] = screen.height;
		}
		screen.width = memo[0];
		screen.height = memo[1];
		memo[2] = memo[0];
		memo[3] = memo[1];
		if (!enabled) return 1f;
		Window w = Minecraft.getInstance().getWindow();
		int gs = Math.max(1, w.getGuiScale());
		int k = UiLayout.fitScale(w.getWidth(), w.getHeight(), gs, needW, needH);
		if (k >= gs) return 1f;
		screen.width = UiLayout.fitGui(w.getWidth(), k);
		screen.height = UiLayout.fitGui(w.getHeight(), k);
		memo[2] = screen.width;
		memo[3] = screen.height;
		return k / (float) gs;
	}

	/** Scales the pose for drawing in {@code screen}'s GUI; returns true when it pushed (then call {@link #pop}). */
	static boolean push(GuiGraphicsExtractor g, Screen screen) {
		float f = screen instanceof FitScaled s ? s.fitScale() : 1f;
		if (f >= 1f) return false;
		g.pose().pushMatrix();
		g.pose().scale(f, f);
		return true;
	}

	static void pop(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}
}
