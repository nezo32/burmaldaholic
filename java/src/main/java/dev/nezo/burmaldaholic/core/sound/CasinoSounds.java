package dev.nezo.burmaldaholic.core.sound;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;

/**
 * Registration and lookup of the {@link SoundCatalog} ids. NOT wired yet (skeleton; lane J-L1 wires core,
 * each game lane wires its own module): a module calls {@code CasinoSounds.registerOwned(ctx, "slots")} in
 * {@code register} after adding the matching entries to {@code src/main/sounds/<module>/sounds.json}.
 * Ids that already exist ({@link SoundCatalog#exists()}) stay registered where they are today
 * ({@code CoreSounds}, module classes) and are skipped here.
 *
 * <p>Playback goes through the client {@code FxSounds} helper (volume × {@code anim.volume}, "Casino sounds"
 * toggle, {@code SoundSource} from the catalog) or, server-side, through {@code level.playSound} for world
 * sounds heard by neighbours.
 */
public final class CasinoSounds {
	private static final Map<String, SoundEvent> EVENTS = new HashMap<>();

	private CasinoSounds() {}

	/** Registers every new catalog id owned by {@code module}. Call once from that module's register. */
	public static void registerOwned(ModuleContext ctx, String module) {
		for (SoundCatalog s : SoundCatalog.ownedBy(module)) {
			if (s.exists() || EVENTS.containsKey(s.id())) continue;
			EVENTS.put(s.id(), ctx.registry().sound(s.id()));
		}
	}

	/** The registered event, or {@code null} when its owner has not registered it (callers fall back). */
	public static SoundEvent get(String id) {
		return EVENTS.get(id);
	}
}
