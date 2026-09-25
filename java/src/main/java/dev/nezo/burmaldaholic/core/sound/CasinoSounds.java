package dev.nezo.burmaldaholic.core.sound;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Registration and lookup of the {@link SoundCatalog} ids. Core is wired ({@code CoreSounds.register}); each
 * game lane wires its own module: a module calls {@code CasinoSounds.registerOwned(ctx, "slots")} in
 * {@code register} after adding the matching entries to {@code src/main/sounds/<module>/sounds.json}.
 * Ids that already exist ({@link SoundCatalog#exists()}) stay registered where they are today
 * ({@code CoreSounds}, module classes) and are skipped here.
 *
 * <p>Playback goes through the client {@code FxSounds} helper (volume × {@code anim.volume}, "Casino sounds"
 * toggle, {@code SoundSource} from the catalog) or, server-side, through {@code level.playSound} for world
 * sounds heard by neighbours.
 */
public final class CasinoSounds {
	private static final Map<String, SoundEvent> EVENTS = new ConcurrentHashMap<>();

	private CasinoSounds() {}

	/** Registers every new catalog id owned by {@code module}. Call once from that module's register. */
	public static void registerOwned(ModuleContext ctx, String module) {
		for (SoundCatalog s : SoundCatalog.ownedBy(module)) {
			if (s.exists() || EVENTS.containsKey(s.id())) continue;
			EVENTS.put(s.id(), ctx.registry().sound(s.id()));
		}
	}

	/**
	 * The registered event, or {@code null} when its owner has not registered it (callers fall back). Ids that
	 * existed before this wave (registered by {@code CoreSounds} or a module class) are found in the registry.
	 */
	public static SoundEvent get(String id) {
		SoundEvent e = EVENTS.get(id);
		if (e != null) return e;
		e = BuiltInRegistries.SOUND_EVENT.getValue(Burmaldaholic.id(id));
		if (e != null) EVENTS.put(id, e);
		return e;
	}

	/** Catalog entry of {@code id}, or {@code null}. */
	public static SoundCatalog entry(String id) {
		for (SoundCatalog s : SoundCatalog.all()) if (s.id().equals(id)) return s;
		return null;
	}

	/** {@link SoundSource} of a catalog source name: {@code block} → BLOCKS, {@code ambient} → AMBIENT, else PLAYERS (never MASTER). */
	public static SoundSource source(String catalogSource) {
		return switch (catalogSource) {
			case "block" -> SoundSource.BLOCKS;
			case "ambient" -> SoundSource.AMBIENT;
			default -> SoundSource.PLAYERS;
		};
	}

	/** Source of a catalog id (PLAYERS when unknown). */
	public static SoundSource sourceOf(String id) {
		SoundCatalog s = entry(id);
		return s == null ? SoundSource.PLAYERS : source(s.source());
	}
}
