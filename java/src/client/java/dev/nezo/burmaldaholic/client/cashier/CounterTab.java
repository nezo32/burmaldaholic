package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One tab of the counter screen (Exchange, Store, Comps; EC-SPEC §3). Created per screen by its {@link CounterTabs}
 * factory with the {@link CounterShell}; everything in layout px of the shell's viewport.
 *
 * <p>Target ids: prefix them with the tab id ({@code exchange.row.emerald.in.64}, {@code comps.redeem.snack}) so they
 * never clash with the shell's ({@code tab.<id>}, {@code top.close}).
 */
public interface CounterTab {
	/** Stable id ({@code exchange}, {@code store}, {@code comps}). */
	String id();

	/** The tab label. */
	Component title();

	/** Registers the tab's targets for the current data in {@code body} (called on open, resize, data change). */
	void layout(Box body, HitMap hits);

	/** Draws the tab's content in {@code body} (targets were registered by {@link #layout}). */
	void render(GuiGraphicsExtractor g, Box body, int mouseX, int mouseY, float partial);

	/**
	 * A press on one of the tab's targets. {@code mods} = the event's modifier bits (test with
	 * {@code Inputs.shift(mods)} / {@code Inputs.ctrl(mods)}). Return true when handled.
	 */
	boolean onTarget(String id, boolean enabled, Press press, int mods);

	/** A key the shell did not use (tab switch 1/2/3 and Esc are the shell's). Use {@code Inputs} only. */
	default boolean key(int keyCode, int mods) {
		return false;
	}

	/** Mouse wheel over the body ({@code dy} > 0 = up). Return true when handled. */
	default boolean scroll(double x, double y, double dy) {
		return false;
	}

	/** Typed character (search box). */
	default boolean charTyped(int codePoint) {
		return false;
	}

	/** The tab became the active one (e.g. send its sync request). */
	default void opened() {}

	/** Client tick while the screen is open (any tab). */
	default void tick() {}

	/** The cashier's table state changed (only on the cashier); tabs re-layout via {@link CounterShell#relayout}. */
	default void stateChanged() {}
}
