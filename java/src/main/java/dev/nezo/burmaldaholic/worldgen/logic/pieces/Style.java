package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.FacadeKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kit;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.RoofKind;

/**
 * One Overworld casino style (architecture doc §2: {@code record Style(id, Palette, RoofKind, FacadeKind,
 * biomeTag, signature decor)}). Lane CB-2 owns this file; lane CB-3 committed this minimal version so the
 * styles and the village annex compile before CB-2 merges — CB-2 may add components (keep these).
 *
 * @param id        style id, the {@code <s>} of {@code casino/<s>/...}
 * @param biomeTag  biome tag the standalone structure uses, e.g. {@code burmaldaholic:has_structure/casino_tudor}
 * @param signature the style's interior signature centrepiece (a kit of at most 3 × 3 cells, 3 rows, centred on the anchor on the
 *                  floor row; {@code front} = the side visitors approach from)
 */
public record Style(String id, Palette palette, RoofKind roof, FacadeKind facade, String biomeTag, Kit signature) {}
