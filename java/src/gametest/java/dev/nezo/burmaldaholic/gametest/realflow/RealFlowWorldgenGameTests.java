package dev.nezo.burmaldaholic.gametest.realflow;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * REAL-FLOW worldgen audit (lane A4): villages generated through the real jigsaw placer ({@code /place structure},
 * the same {@code JigsawPlacement} path natural generation takes) — how many get their casino when the per-village
 * gate is forced open, and can a player walk into the ones that generate (an opening in the walls at floor level with
 * free space in front)?
 *
 * <p>The players' "casinos almost never spawn" report: the effective rate is {@code worldgen.villageCasino.chance}
 * (0.35) × the share of gated villages whose street ends can take the 17 × 17 building. Measured at v0.1.1: 10 / 15 and
 * 13 / 15 in two runs (one run: desert 0 / 3) → only ≈ 23–30 % of villages get a casino at the default chance. The
 * per-type result is logged ({@code perType}); the overall fit (≥ 60 %) is asserted only with
 * {@code -Dburmaldaholic.realflow.known=true}.
 */
public class RealFlowWorldgenGameTests {
	private static final String[] TYPES = {"plains", "desert", "savanna", "snowy", "taiga"};
	private static final int PER_TYPE = 3;

	@GameTest(maxTicks = 1200)
	public void realflowVillageCasinoRateAndEntrance(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var cfg = CasinoConfig.worldgen().villageCasino;
		double chance = cfg.chance;
		String dim = level.dimension().identifier().toString();
		List<String> lines = new ArrayList<>();
		int[] villages = {0};
		int[] casinos = {0};
		List<String> closed = new ArrayList<>();
		java.util.Map<String, Integer> perType = new java.util.TreeMap<>();
		cfg.chance = 1.0;
		int n = 0;
		for (int t = 0; t < TYPES.length; t++) {
			for (int i = 0; i < PER_TYPE; i++) {
				String type = TYPES[t];
				BlockPos at = helper.absolutePos(BlockPos.ZERO).offset(3000 + t * 400, 0, -3000 - i * 400);
				helper.runAfterDelay(1 + n * 3, () -> {
					int cx = at.getX() >> 4;
					int cz = at.getZ() >> 4;
					for (int x = cx - 7; x <= cx + 7; x++) {
						for (int z = cz - 7; z <= cz + 7; z++) level.getChunk(x, z);
					}
					long before = count(level, dim, at);
					var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
					int ok;
					try {
						ok = level.getServer().getCommands().getDispatcher().execute("place structure minecraft:village_" + type + " " + at.getX() + " "
							+ at.getY() + " " + at.getZ(), source);
					} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
						ok = 0;
					}
					if (ok <= 0) {
						lines.add(type + " @" + at.toShortString() + ": no village placed");
						return;
					}
					villages[0]++;
					List<CasinoRecord> found = records(level, dim, at);
					long added = found.size() - before;
					casinos[0] += (int) Math.max(0, added);
					perType.merge(type, (int) Math.max(0, added), Integer::sum);
					String entry = "";
					for (CasinoRecord r : found) {
						if (!enterable(level, r.box())) {
							closed.add(type + " " + r.box());
							entry = " (NO walkable entrance)";
						}
					}
					lines.add(type + " @" + at.toShortString() + ": casinos +" + added + entry);
				});
				n++;
			}
		}
		helper.runAfterDelay(5 + n * 3, () -> {
			cfg.chance = chance;
			double rate = villages[0] == 0 ? 0 : casinos[0] / (double) villages[0];
			lines.forEach(l -> Burmaldaholic.LOGGER.info("[realflow worldgen] {}", l));
			Burmaldaholic.LOGGER.info("[realflow worldgen] per type {}", perType);
			Burmaldaholic.LOGGER.info("[realflow worldgen] villages {}, with casino {} (fit rate {} at chance 1; effective at default {} = {})", villages[0],
				casinos[0], String.format("%.2f", rate), chance, String.format("%.2f", rate * chance));
			helper.assertTrue(villages[0] > 0, "no village could be placed");
			helper.assertTrue(casinos[0] > 0, "no village got a casino even at chance 1");
			helper.assertTrue(closed.isEmpty(), "casinos without a walkable entrance: " + closed);
			if (RealFlow.KNOWN) {
				helper.assertTrue(rate >= 0.6, "only " + casinos[0] + " of " + villages[0] + " villages got their casino at chance 1 (rate "
					+ String.format("%.2f", rate) + "; effective " + String.format("%.2f", rate * chance) + " at the default chance)");
			}
			helper.succeed();
		});
	}

	private static List<CasinoRecord> records(ServerLevel level, String dim, BlockPos near) {
		return CasinoIndex.get(level.getServer()).casinos().stream().filter(r -> r.kind() == CasinoKind.VILLAGE_CASINO && r.dimension().equals(dim)
			&& Math.abs(r.box().centerX() - near.getX()) < 160 && Math.abs(r.box().centerZ() - near.getZ()) < 160).toList();
	}

	private static long count(ServerLevel level, String dim, BlockPos near) {
		return records(level, dim, near).size();
	}

	/**
	 * A player can walk in: some wall column of the box has two free blocks above the floor (y + 1, y + 2), and the two
	 * blocks outside it are free too (no wall of earth or a house against the door).
	 */
	static boolean enterable(ServerLevel level, Geometry.Box b) {
		int y = b.minY();
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				boolean edge = x == b.minX() || x == b.maxX() || z == b.minZ() || z == b.maxZ();
				if (!edge) continue;
				if (!free(level, x, y + 1, z) || !free(level, x, y + 2, z)) continue;
				int ox = x == b.minX() ? -1 : x == b.maxX() ? 1 : 0;
				int oz = z == b.minZ() ? -1 : z == b.maxZ() ? 1 : 0;
				if (free(level, x + ox, y + 1, z + oz) && free(level, x + ox, y + 2, z + oz)) return true;
			}
		}
		return false;
	}

	private static boolean free(ServerLevel level, int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}
}
