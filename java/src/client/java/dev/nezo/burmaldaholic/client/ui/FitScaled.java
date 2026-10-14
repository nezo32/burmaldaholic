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
	/**
	 * Drawing factor of the screen's GUI onto the real GUI: 1 when the full layout fits, else {@code k / guiScale} (&lt; 1
	 * for the compact layout; &gt; 1 for a table UI v2 screen stepped up on a big GUI, {@link #applyViewport}).
	 */
	float fitScale();

	/**
	 * Where the screen's GUI starts inside the real GUI, in real GUI px (a framed table UI v2 screen, ui-v2.md §3.7:
	 * the frame is centred; the entrance translates the pose by it and the mouse mixin subtracts it). Default 0.
	 */
	default float offsetX() {
		return 0f;
	}

	default float offsetY() {
		return 0f;
	}

	/** The factor of the open screen (1 for any other screen). */
	static float current() {
		Screen s = Minecraft.getInstance().gui.screen();
		return s instanceof FitScaled f ? f.fitScale() : 1f;
	}

	/** The frame offset of the open screen in real GUI px ({0, 0} for any other screen). */
	static float[] currentOffset() {
		Screen s = Minecraft.getInstance().gui.screen();
		return s instanceof FitScaled f ? new float[] {f.offsetX(), f.offsetY()} : new float[2];
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

	/**
	 * Table UI v2 (docs/architecture/ui-v2.md D2, D3, §3.7): resizes {@code screen} into the FRAME of the virtual GUI of
	 * {@link dev.nezo.burmaldaholic.core.ui.v2.ViewportPolicy} — the player's GUI kept between 240 and 720 px high, stepped
	 * down below, stepped UP by whole factors above 720, and capped at a centred 16:9 frame of ≤ 600 px high. The returned
	 * viewport's {@code factor} (may be &gt; 1) is what the screen reports as {@link #fitScale()} and its {@code guiOx/guiOy}
	 * as {@link #offsetX()} / {@link #offsetY()}: the entrance translates and scales the pose, the mouse mixin undoes both.
	 * Same {@code memo} contract as {@link #apply}.
	 */
	static dev.nezo.burmaldaholic.core.ui.v2.Viewport applyViewport(Screen screen, int[] memo) {
		if (screen.width != memo[2] || screen.height != memo[3]) {
			memo[0] = screen.width;
			memo[1] = screen.height;
		}
		Window w = Minecraft.getInstance().getWindow();
		int gs = Math.max(1, w.getGuiScale());
		dev.nezo.burmaldaholic.core.ui.v2.Viewport vp = dev.nezo.burmaldaholic.core.ui.v2.Viewport.of(w.getWidth(), w.getHeight(), gs);
		screen.width = vp.frameW(); // the screen works in its frame (§3.7); the full layout only when unframed
		screen.height = vp.frameH();
		memo[2] = screen.width;
		memo[3] = screen.height;
		return vp;
	}

	/** Scales the pose for drawing in {@code screen}'s GUI; returns true when it pushed (then call {@link #pop}). */
	static boolean push(GuiGraphicsExtractor g, Screen screen) {
		float f = screen instanceof FitScaled s ? s.fitScale() : 1f;
		float ox = screen instanceof FitScaled s ? s.offsetX() : 0f;
		float oy = screen instanceof FitScaled s ? s.offsetY() : 0f;
		if (f == 1f && ox == 0f && oy == 0f) return false;
		g.pose().pushMatrix();
		g.pose().translate(ox, oy);
		g.pose().scale(f, f);
		return true;
	}

	static void pop(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}
}
