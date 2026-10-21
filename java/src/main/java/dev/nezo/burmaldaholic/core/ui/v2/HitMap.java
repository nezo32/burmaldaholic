package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The clickable regions of a v2 screen in layout px, topmost last added wins (docs/architecture/ui-v2.md §6). Screens
 * rebuild it with the layout (never per frame); {@code mouseClicked} routes through {@link #at}; client GameTests click
 * by id through {@link #box} (the same box that is drawn), converted to window px with {@link Viewport#toWindow}.
 * Disabled targets stay in the map (a click on them shows their reason instead of doing nothing).
 */
public final class HitMap {
	public record Target(String id, Box box, boolean enabled) {}

	private final List<Target> targets = new ArrayList<>();

	public HitMap add(String id, Box box, boolean enabled) {
		if (!box.isEmpty()) targets.add(new Target(id, box, enabled));
		return this;
	}

	public void clear() {
		targets.clear();
	}

	/** The topmost target under the point (layout px). */
	public Optional<Target> at(double x, double y) {
		for (int i = targets.size() - 1; i >= 0; i--) {
			if (targets.get(i).box().contains(x, y)) return Optional.of(targets.get(i));
		}
		return Optional.empty();
	}

	public Optional<Box> box(String id) {
		return targets.stream().filter(t -> t.id().equals(id)).map(Target::box).findFirst();
	}

	public List<Target> targets() {
		return List.copyOf(targets);
	}
}
