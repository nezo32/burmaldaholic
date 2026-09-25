package dev.nezo.burmaldaholic.client.hud;

import java.util.function.Consumer;

/**
 * A part of the casino HUD panel (UI.md §1). Register with
 * {@code ctx.hudSegment("loan", 200, segment)} in your client module; the panel stacks all
 * segments' lines by {@code order} (core: 0 balance, 100 streak/VIP, 200 loan, 300 Golden Hour).
 * Called every frame while the HUD is visible — keep it cheap and read only client state.
 */
@FunctionalInterface
public interface HudSegment {
	void addLines(HudContext ctx, Consumer<HudLine> out);
}
