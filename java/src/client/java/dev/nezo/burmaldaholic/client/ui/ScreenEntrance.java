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

	/** Installs the entrance on {@code screen}; {@code panel} gives the panel rectangle (GUI coordinates). */
	public static ScreenEntrance install(Screen screen, Supplier<UiLayout.Rect> panel) {
		ScreenEntrance e = new ScreenEntrance(panel);
		ScreenEvents.beforeExtract(screen).register((s, g, mx, my, pt) -> {
			long ms = Util.getMillis() - e.openedAt;
			e.pushed = false;
			e.entering = ms < UiLayout.ENTRANCE_MS && !FxSettings.reduceMotion();
			float fit = s instanceof FitScaled f ? f.fitScale() : 1f;
			if (!e.entering && fit >= 1f) return;
			g.pose().pushMatrix();
			e.pushed = true;
			// the compact layout: the screen's own GUI drawn at k / guiScale (FitScaled), then the entrance inside it
			if (fit < 1f) g.pose().scale(fit, fit);
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
		return e;
	}

	/** Milliseconds since the screen opened. */
	public long age() {
		return Util.getMillis() - openedAt;
	}
}
