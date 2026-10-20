package dev.nezo.burmaldaholic.client.ui;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/**
 * The shared screen entrance (global.md §4.14): for {@link UiLayout#ENTRANCE_MS} the whole screen is scaled 0.97 → 1
 * about the panel centre and the panel fades in from an {@code ink} veil. Installed through the Fabric screen events
 * around the vanilla extract call, so any screen (a {@link CasinoScreen} or a {@code CasinoTableScreen}) gets it
 * without touching its own drawing. Reduce motion: nothing. Call once per screen instance from {@code init()} (the
 * first init only: {@link #install} ignores repeats for the same screen).
 */
public final class ScreenEntrance {
	private final long openedAt = Util.getMillis();
	private final Supplier<UiLayout.Rect> panel;
	private boolean pushed;
	private boolean entering;

	private ScreenEntrance(Supplier<UiLayout.Rect> panel) {
		this.panel = panel;
	}

	/** Drawn after the screen, OUTSIDE its fit / frame pose (real GUI coordinates), e.g. a celebration over a v2 screen. */
	public interface OverFrame {
		void extractOverFrame(net.minecraft.client.gui.GuiGraphicsExtractor g);
	}

	private static final java.util.Map<Screen, ScreenEntrance> INSTALLED = new java.util.WeakHashMap<>();
	private static boolean listening;
	/** The event object this entrance last hooked (Fabric recreates a screen's events on every init / resize). */
	private Object hookedOn;

	/**
	 * Installs the entrance on {@code screen}; {@code panel} gives the panel rectangle (GUI coordinates). Call from
	 * {@code init()} (any number of times: the entrance is kept, and it re-hooks itself after every re-init or resize —
	 * Fabric recreates a screen's events then, which used to drop the fit / frame pose after a resize).
	 */
	public static ScreenEntrance install(Screen screen, Supplier<UiLayout.Rect> panel) {
		if (!listening) {
			listening = true;
			ScreenEvents.AFTER_INIT.register((client, s, w, h) -> {
				ScreenEntrance e = INSTALLED.get(s);
				if (e != null) e.hook(s);
			});
		}
		ScreenEntrance e = INSTALLED.computeIfAbsent(screen, k -> new ScreenEntrance(panel));
		e.hook(screen);
		return e;
	}

	private void hook(Screen screen) {
		Object ev = ScreenEvents.beforeExtract(screen);
		if (ev == hookedOn) return;
		hookedOn = ev;
		ScreenEntrance e = this;
		ScreenEvents.beforeExtract(screen).register((s, g, mx, my, pt) -> {
			long ms = Util.getMillis() - e.openedAt;
			e.pushed = false;
			e.entering = ms < UiLayout.ENTRANCE_MS && !FxSettings.reduceMotion();
			float fit = s instanceof FitScaled f ? f.fitScale() : 1f;
			float ox = s instanceof FitScaled f ? f.offsetX() : 0f;
			float oy = s instanceof FitScaled f ? f.offsetY() : 0f;
			if (!e.entering && fit == 1f && ox == 0f && oy == 0f) return;
			g.pose().pushMatrix();
			e.pushed = true;
			// a framed v2 screen: its frame is centred in the real GUI (ui-v2.md §3.7)
			if (ox != 0f || oy != 0f) g.pose().translate(ox, oy);
			// the compact layout: the screen's own GUI drawn at k / guiScale (FitScaled), then the entrance inside it
			if (fit != 1f) g.pose().scale(fit, fit);
			if (!e.entering) return;
			UiLayout.Rect r = e.panel.get();
			float scale = UiLayout.entranceScale(ms, false);
			g.pose().translate(r.centerX(), r.centerY());
			g.pose().scale(scale, scale);
			g.pose().translate(-r.centerX(), -r.centerY());
		});
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, pt) -> {
			if (!e.pushed) return;
			if (e.entering) {
				long ms = Util.getMillis() - e.openedAt;
				float alpha = UiLayout.entranceAlpha(ms, false);
				UiLayout.Rect r = e.panel.get();
				g.nextStratum();
				g.fill(r.x(), r.y(), r.right(), r.bottom(), CasinoPalette.withAlpha(CasinoPalette.INK, 0.85f * (1 - alpha)));
			}
			g.pose().popMatrix();
			e.pushed = false;
		});
		if (screen instanceof OverFrame o) ScreenEvents.afterExtract(screen).register((s, g, mx, my, pt) -> o.extractOverFrame(g));
	}

	/** Milliseconds since the screen opened. */
	public long age() {
		return Util.getMillis() - openedAt;
	}
}
