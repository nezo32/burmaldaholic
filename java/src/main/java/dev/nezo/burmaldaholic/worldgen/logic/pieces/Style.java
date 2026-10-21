package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.FacadeKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kit;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palettes;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.RoofKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Roofs;

/**
 * One Overworld casino style (architecture doc §4.1, §7.2): id, palette, roof and facade family, the biome
 * tag its structure spawns in, and its signature hooks. {@link OverworldCasino} builds the same six
 * pieces for every style; the {@link Signature} lets a style (lane CB-3) swap the roofs and add its own
 * decor without editing the generators. Roof / facade families are CB-3's {@link RoofKind} / {@link FacadeKind}
 * (built by {@link Roofs} / {@code Facades}).
 *
 * @param id        style id, used in layout ids ({@code casino/<id>/entrance}) and structure ids
 * @param palette   role → material table
 * @param roof      roof family (documentation + the default {@link Signature#roof} dispatch)
 * @param facade    facade family
 * @param biomeTag  biome tag id of the structure, e.g. {@code burmaldaholic:has_structure/casino_tudor}
 * @param signature style hooks (all optional)
 */
public record Style(String id, Palette palette, RoofKind roof, FacadeKind facade, String biomeTag, Signature signature) {
	public Style {
		palette.check();
		if (!id.matches("[a-z_]+")) {
			throw new IllegalArgumentException("style id must be [a-z_]+: " + id);
		}
	}

	/**
	 * A roofed volume handed to {@link Signature#roof}: the eave box (inclusive, template coordinates), the
	 * eave row and the highest row the roof may use (the piece's top row).
	 */
	public record Hall(OverworldCasino.Part part, String name, int x0, int z0, int x1, int z1, int eaveY, int maxY) {}

	/**
	 * Style hooks. Every method has a default, so a style only overrides what makes it different.
	 * Hooks run inside the generators at fixed points of the canonical verb order (§3.1).
	 */
	public interface Signature {
		/**
		 * Roofs one hall. Return {@code true} when the style drew the roof itself; {@code false} lets the
		 * generator draw its default roof (Tudor hip roofs with dormers / lanterns).
		 */
		default boolean roof(Blueprint b, Hall hall) {
			return false;
		}

		/** Extra touches after a piece's body and before markers / jigsaws (planters, banners, trims …). */
		default void decorate(Blueprint b, OverworldCasino.Part part) {}

		/**
		 * The style's interior centrepiece (lane CB-3): at most 3 × 3 cells, 3 rows, centred on ({@code x},
		 * {@code z}) on the floor row {@code y}; {@code front} = the side visitors approach from.
		 */
		default void centrepiece(Blueprint b, int x, int y, int z, Facing front) {}

		/** Default hooks (the Tudor look needs none). */
		Signature NONE = new Signature() {};

		/**
		 * CB-3 styles: every hall gets the style's {@link Roofs#roof} shape; {@code centre} is the centrepiece kit.
		 * {@code roof == null} keeps the generator's default roofs.
		 */
		static Signature of(RoofKind roof, Kit centre) {
			return new Signature() {
				@Override
				public boolean roof(Blueprint b, Hall hall) {
					if (roof == null) {
						return false;
					}
					Roofs.roof(b, roof, hall.x0(), hall.z0(), hall.x1(), hall.z1(), hall.eaveY(), hall.maxY());
					return true;
				}

				@Override
				public void centrepiece(Blueprint b, int x, int y, int z, Facing front) {
					centre.apply(b, x, y, z, front);
				}
			};
		}
	}

	/**
	 * Tudor gambling hall — plains, meadow, forests (design doc §5, architecture doc §7.2): CB-2's default roofs,
	 * CB-3's reading-nook centrepiece (resolved at call time, no static-init cycle with {@link OverworldStyles}).
	 */
	public static final Style TUDOR = new Style("tudor", Palettes.TUDOR, RoofKind.HIP, FacadeKind.PORTICO,
		"burmaldaholic:has_structure/casino_tudor", Signature.of(null, (b, x, y, z, front) -> OverworldStyles.TUDOR_SIGNATURE.apply(b, x, y, z, front)));
}
