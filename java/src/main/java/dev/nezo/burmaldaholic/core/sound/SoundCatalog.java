package dev.nezo.burmaldaholic.core.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every casino sound event id of the animation wave, in ONE place (docs/architecture/animation.md §2.4).
 * The same ids exist in Bedrock ({@code burmaldaholic.<id>} in {@code packs/<owner>/RP/sounds/}) — the
 * Bedrock mirror is {@code bedrock/src/core/logic/anim/sound-ids.ts}, and the id list is part of the shared
 * vector file so the two catalogs cannot drift.
 *
 * <p>PURE data. Registration: each owning module calls {@link CasinoSounds#registerOwned} in its
 * {@code register} (ownership rules of {@code config/namespaces.properties}: a module can only register
 * names it owns; ids used by several modules are owned by core). MUST phase = vanilla composites in
 * {@code src/main/sounds/<owner>/sounds.json}; NICE phase swaps in {@code .ogg} files under the same ids.
 *
 * @param id       Java path ({@code burmaldaholic:<id>}); Bedrock {@code burmaldaholic.<id>}
 * @param owner    module that registers it and owns its {@code sounds.json} entry
 * @param source   {@code player} (personal) / {@code block} (tables, cabinets) / {@code ambient}
 * @param subtitle subtitle name ({@code subtitles.burmaldaholic.<subtitle>})
 * @param exists   already registered before this wave (keep the id; the composition may be re-pointed)
 * @param spec     design doc that defines it
 */
public record SoundCatalog(String id, String owner, String source, String subtitle, boolean exists, String spec) {
	private static final List<SoundCatalog> ALL;

	static {
		List<SoundCatalog> l = new ArrayList<>();
		// global.md §2.7 (core unless a module owns the name)
		core(l, "global", "chip_place", true, "chip_stack", "chip_count", "win_small", "win_nice", "win_big", "win_mega",
			"jackpot", "push", "ui_deny", "toast", "streak_up", "streak_break", "collector_arrive", "heartbeat", "coin_land",
			"attract_chime");
		existing(l, "core", "global", "win", "lose", "collector_knock", "card_shuffle", "slot_spin", "wheel_tick", "scratch");
		l.add(new SoundCatalog("vip_tier_up", "vip", "player", "vip_tier_up", false, "global"));
		existing(l, "chaos", "global", "golden_hour");
		for (String id : new String[] {"golden_hour_end", "chaos_good", "chaos_bad", "chaos_teleport"})
			l.add(new SoundCatalog(id, "chaos", "ambient", id, false, "global"));
		existing(l, "lastchance", "global", "last_chance");
		// cards.md §7 (table sounds: block source)
		existing(l, "core", "cards", "card_deal");
		for (String id : new String[] {"card_slide", "card_flip", "card_squeeze", "card_gather", "card_sting", "chip_push",
			"pot_win", "table_knock", "chip_sweep"})
			l.add(new SoundCatalog(id, "core", "block", id, false, "cards/tables"));
		// tables.md §0.8 (dice_* are used by craps AND extras, so core owns them)
		for (String id : new String[] {"roulette_ball_roll", "roulette_ball_drop", "roulette_ball_bounce", "roulette_ball_settle",
			"roulette_bell", "roulette_dolly"})
			l.add(new SoundCatalog(id, "roulette", "block", id, false, "tables"));
		existing(l, "roulette", "tables", "roulette_spin");
		for (String id : new String[] {"dice_throw", "dice_bounce", "dice_cup", "dice_wall"})
			l.add(new SoundCatalog(id, "core", "block", id, false, "tables"));
		l.add(new SoundCatalog("craps_puck", "craps", "block", "craps_puck", false, "tables"));
		// extras-pvp.md §10.3
		existing(l, "extras", "extras-pvp", "coin_flip", "plinko_peg", "dice_roll");
		l.add(new SoundCatalog("plinko_bin", "extras", "player", "plinko_bin", false, "extras-pvp"));
		for (String id : new String[] {"coin_whoosh", "wheel_stop", "burn"})
			l.add(new SoundCatalog(id, "core", "player", id, false, "extras-pvp"));
		existing(l, "pvp", "extras-pvp", "pvp_drumroll", "pvp_victory");
		// slots.md §8 / SLOTS.md §10.7 (Bedrock: packs/slots/RP)
		for (String id : new String[] {"spin_loop", "reel_stop", "scatter_land", "bonus_land", "anticipation", "returned",
			"win_small", "win_nice", "big_win", "mega_win", "epic_win", "max_win", "rollup_tick", "rollup_end", "fs_intro",
			"fs_outro", "fs_music.overworld", "fs_music.nether", "fs_music.end", "wild_expand", "wild_stick", "tumble", "mult_up",
			"chest_open", "creeper_hiss", "coin_land", "respin_reset", "wheel_tick", "wheel_up"})
			l.add(new SoundCatalog("slots." + id, "slots", "player", "slots." + id, false, "slots"));
		ALL = Collections.unmodifiableList(l);
	}

	private static void core(List<SoundCatalog> l, String spec, String existing, boolean ex, String... ids) {
		l.add(new SoundCatalog(existing, "core", "player", existing, ex, spec));
		for (String id : ids) l.add(new SoundCatalog(id, "core", "player", subtitleOf(id), false, spec));
	}

	/** Subtitle names that differ from the id (global.md §2.7 subtitle column). */
	private static String subtitleOf(String id) {
		return switch (id) {
			case "win_small" -> "win";
			case "win_nice" -> "nice_win";
			case "win_big" -> "big_win";
			case "win_mega" -> "mega_win";
			case "attract_chime" -> "attract";
			default -> id;
		};
	}

	private static void existing(List<SoundCatalog> l, String owner, String spec, String... ids) {
		for (String id : ids) l.add(new SoundCatalog(id, owner, "player", id, true, spec));
	}

	public static List<SoundCatalog> all() {
		return ALL;
	}

	public static List<SoundCatalog> ownedBy(String module) {
		return ALL.stream().filter(s -> s.owner.equals(module)).toList();
	}

	/** Bedrock sound id ({@code burmaldaholic.<id>}). */
	public String bedrockId() {
		return "burmaldaholic." + id;
	}
}
