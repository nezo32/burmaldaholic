package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Facades;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.FacadeKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kit;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Roofs;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.RoofKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import java.util.List;
import java.util.Optional;

/**
 * The five Overworld casino styles (architecture doc §4.1 / §7.2, lane CB-3). Order is fixed (it is the
 * order of the structure set and of every generated list): tudor, oasis, safari, alpine, ice_palace.
 */
public final class OverworldStyles {
	private OverworldStyles() {}

	private static String tag(String id) {
		return "burmaldaholic:has_structure/casino_" + id;
	}

	private static final BlockSpec WATER = BlockSpec.mc("water", "level", "0");

	// ---- signature centrepieces (3 × 3 footprint centred on the anchor, floor row y) -----------------

	/** Tudor: a reading nook — bookshelf plinth with a flower pot and lanterns on top slabs on both sides. */
	static final Kit TUDOR_SIGNATURE = (b, x, y, z, front) -> {
		Facing across = front.rotate(1);
		b.set(x, y, z, BlockSpec.mc("bookshelf"));
		b.set(x, y + 1, z, Role.PLANTER);
		for (int t : new int[] {-1, 1}) {
			b.set(x + t * across.dx(), y, z + t * across.dz(), b.palette().get(Role.COUNTER).slab("top")); // top slab: a lantern needs a full top face
			b.set(x + t * across.dx(), y + 1, z + t * across.dz(), Facades.LANTERN);
		}
	};

	/** Oasis: a quartz-rimmed well of still water with a sea-lantern drip stone behind it. */
	static final Kit OASIS_SIGNATURE = (b, x, y, z, front) -> {
		Facing across = front.rotate(1);
		int bx = x - front.dx(), bz = z - front.dz();
		b.set(x, y, z, WATER);
		for (Facing f : Facing.values()) {
			b.set(x + f.dx(), y, z + f.dz(), Role.COUNTER);
		}
		b.set(bx, y + 1, bz, Role.LIGHT);
		b.set(bx, y + 2, bz, b.palette().get(Role.ACCENT).slab("bottom"));
		for (int t : new int[] {-1, 1}) {
			b.set(x + t * across.dx() - front.dx(), y, z + t * across.dz() - front.dz(), Role.PLANTER);
		}
	};

	/** Safari: decorated pots around a hay-bale table with a lantern. */
	static final Kit SAFARI_SIGNATURE = (b, x, y, z, front) -> {
		Facing across = front.rotate(1);
		b.set(x, y, z, BlockSpec.mc("hay_block", "axis", "y"));
		b.set(x, y + 1, z, Facades.LANTERN);
		for (int t : new int[] {-1, 1}) {
			b.set(x + t * across.dx(), y, z + t * across.dz(), BlockSpec.mc("decorated_pot", "cracked", "false", "facing",
				front.id(), "waterlogged", "false"));
		}
	};

	/** Alpine: stone hearth (campfire between mossy blocks) with a log pile. */
	static final Kit ALPINE_SIGNATURE = (b, x, y, z, front) -> {
		Facing across = front.rotate(1);
		b.set(x, y, z, BlockSpec.mc("campfire", "facing", front.id(), "lit", "true", "signal_fire", "false", "waterlogged", "false"));
		b.set(x, y + 1, z, b.palette().get(Role.ACCENT).slab("bottom"));
		for (int t : new int[] {-1, 1}) {
			b.set(x + t * across.dx(), y, z + t * across.dz(), Role.ACCENT);
			b.set(x + t * across.dx(), y + 1, z + t * across.dz(), BlockSpec.mc("spruce_log", "axis", across.dx() != 0 ? "x" : "z"));
		}
	};

	/** Ice palace: a blue-ice sculpture on a quartz plinth crowned by a froglight. */
	static final Kit ICE_SIGNATURE = (b, x, y, z, front) -> {
		Facing across = front.rotate(1);
		b.set(x, y, z, Role.FRAME);
		b.set(x, y + 1, z, Role.ROOF);
		b.set(x, y + 2, z, Role.BULB);
		for (int t : new int[] {-1, 1}) {
			b.set(x + t * across.dx(), y, z + t * across.dz(), BlockSpec.mc("snow", "layers", "3"));
		}
	};

	/** CB-2's Tudor (default roofs) with the reading-nook centrepiece. */
	public static final Style TUDOR = Style.TUDOR;
	public static final Style OASIS = style("oasis", OverworldPalettes.OASIS, RoofKind.FLAT_DOME, FacadeKind.DECO_FINS, OASIS_SIGNATURE);
	public static final Style SAFARI = style("safari", OverworldPalettes.SAFARI, RoofKind.THATCH, FacadeKind.VERANDAH, SAFARI_SIGNATURE);
	public static final Style ALPINE = style("alpine", OverworldPalettes.ALPINE, RoofKind.STEEP_GABLE, FacadeKind.CHALET_BALCONY,
		ALPINE_SIGNATURE);
	public static final Style ICE_PALACE = style("ice_palace", OverworldPalettes.ICE_PALACE, RoofKind.ICE_DOME, FacadeKind.ICE_COLONNADE,
		ICE_SIGNATURE);

	/** A CB-3 style: its {@link Roofs} shape over every hall of CB-2's generators, its centrepiece kit. */
	private static Style style(String id, dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette palette, RoofKind roof, FacadeKind facade,
			Kit centre) {
		return new Style(id, palette, roof, facade, tag(id), Style.Signature.of(roof, centre));
	}

	public static List<Style> all() {
		return List.of(TUDOR, OASIS, SAFARI, ALPINE, ICE_PALACE);
	}

	public static Optional<Style> byId(String id) {
		return all().stream().filter(s -> s.id().equals(id)).findFirst();
	}

	/** Village type → casino style (architecture doc §4.2). */
	public static Style forVillage(VillageStyle v) {
		return switch (v) {
			case PLAINS -> TUDOR;
			case DESERT -> OASIS;
			case SAVANNA -> SAFARI;
			case TAIGA -> ALPINE;
			case SNOWY -> ICE_PALACE;
		};
	}

	/**
	 * Entries of the style's biome tag {@code #burmaldaholic:has_structure/casino_<s>} (architecture doc §4.1
	 * table). Entries starting with {@code #} are tags; single biomes are written {@code "required": false}
	 * by the writer (CB-1 PackFiles).
	 */
	public static List<String> biomes(Style s) {
		return switch (s.id()) {
			case "tudor" -> List.of("#minecraft:has_structure/village_plains", "minecraft:meadow", "minecraft:forest",
				"minecraft:birch_forest", "minecraft:flower_forest");
			case "oasis" -> List.of("#minecraft:has_structure/village_desert", "minecraft:badlands", "minecraft:wooded_badlands");
			case "safari" -> List.of("#minecraft:has_structure/village_savanna", "minecraft:savanna_plateau");
			case "alpine" -> List.of("#minecraft:has_structure/village_taiga", "minecraft:cherry_grove", "minecraft:old_growth_spruce_taiga");
			case "ice_palace" -> List.of("#minecraft:has_structure/village_snowy", "minecraft:snowy_taiga", "minecraft:ice_spikes");
			default -> throw new IllegalArgumentException("unknown style " + s.id());
		};
	}
}
