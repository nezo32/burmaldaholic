package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Gates;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Village casinos as an extra jigsaw element of each vanilla village type (GAME_DESIGN §16.1 Java).
 * Called by {@code JigsawPlacerMixin} whenever the jigsaw placer draws the shuffled candidates of a
 * pool:
 * <ul>
 *   <li>only the five vanilla street-end pools {@code village/<type>/terminators} (not zombie
 *       villages, not other mods' pools). Deviation: the spec names the {@code houses} pool, but
 *       house lots lie inside the 16-block street pieces and no lot takes a 17×17 building (the
 *       largest vanilla house is 11×17); a street end opens onto free space, so the casino stands at
 *       the end of a village road, door facing it;</li>
 *   <li>max 1 casino per village (the placer's piece list of this village is checked);</li>
 *   <li>per village a deterministic gate with {@code worldgen.villageCasino.chance} (hash of the
 *       village centre), then the casino competes with the terminators with weight
 *       {@link Gates#villageWeight} so a gated village nearly always gets it;</li>
 *   <li>nothing when casino mode / {@code worldgen.enabled} is off or the data pack is disabled.</li>
 * </ul>
 * The pool objects themselves are never modified.
 */
public final class VillageCasinos {
	private VillageCasinos() {}

	public static List<StructurePoolElement> inject(Registry<StructureTemplatePool> pools, List<?> pieces,
			StructureTemplatePool pool, List<StructurePoolElement> shuffled, RandomSource random) {
		if (shuffled.isEmpty() || pieces.isEmpty() || !WorldgenRuntime.active()) {
			return shuffled;
		}
		Identifier poolId = pools.getKey(pool);
		if (poolId == null) {
			return shuffled;
		}
		Optional<VillageStyle> style = VillageStyle.byCasinoPool(poolId.toString());
		if (style.isEmpty()) {
			return shuffled;
		}
		Optional<StructurePoolElement> casino = CasinoElements.element(pools, Layouts.village(style.get()));
		if (casino.isEmpty()) {
			return shuffled;
		}
		for (Object piece : pieces) {
			if (piece instanceof PoolElementStructurePiece p
				&& CasinoElements.layoutOf(p.getElement()).map(l -> l.kind() == CasinoKind.VILLAGE_CASINO).orElse(false)) {
				return shuffled; // max 1 per village
			}
		}
		if (!(pieces.getFirst() instanceof PoolElementStructurePiece start)) {
			return shuffled;
		}
		BlockPos centre = start.getPosition();
		if (!Gates.pass(WorldgenRuntime.villageChance(), 0L, centre.getX(), centre.getZ(), Gates.SALT_VILLAGE)) {
			return shuffled;
		}
		int index = Gates.insertIndex(shuffled.size(), Gates.villageWeight(shuffled.size()), random::nextInt);		List<StructurePoolElement> out = new ArrayList<>(shuffled.size() + 1);
		out.addAll(shuffled);
		out.add(index, casino.get());
		return out;
	}
}
