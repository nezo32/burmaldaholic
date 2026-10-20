package dev.nezo.burmaldaholic.games.extras.client.pvp;

import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;

/**
 * The PvP match screens on the v2 kit (owner B6, K2 request): {@code PvpPanel} and {@code PvpModeScreen} are
 * {@code V2SceneScreen}s, so they size by the kit's {@code ViewportPolicy} (Room, frame offset) and lay out in its
 * {@code TableGrid} rows like every game screen. They keep their own widgets (KitButtons) in the Dock row and draw the PvP
 * TopBar / Dock bands themselves ({@code PvpFrame} on the kit grid); the kit chrome is idle: no flow, no hit targets and
 * no key bindings, so a click or a key always reaches the PvP widgets.
 */
public final class PvpKitScreens {
	private PvpKitScreens() {}

	/** No kit flow: the PvP screens never set a view on the kit (their state is the PvP sync). */
	public static final Flow<Object> NO_FLOW = v -> Gate.off("", "", "");

	/** An active, visible widget of {@code screen} under (x, y): a click there acts through a celebration (T-E2 #3). */
	public static boolean widgetAt(net.minecraft.client.gui.screens.Screen screen, double x, double y) {
		for (var c : screen.children()) {
			if (c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible && w.active && w.isMouseOver(x, y)) return true;
		}
		return false;
	}

	/** Makes the kit chrome idle for a PvP screen (after {@code super.init()}). */
	public static void adopt(V2Chrome chrome) {
		chrome.keys(new KeyMap());
		chrome.hits().clear();
	}
}
