package dev.nezo.burmaldaholic.worldgen.structure;

import dev.nezo.burmaldaholic.worldgen.logic.site.NetherSite;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Solid / not solid of the generated rock at any block, for the Nether site probe (lane WG). With the fast sampler
 * ({@link TerrainProbe#densityAt}) the final density is sampled at the noise-cell corners and interpolated
 * trilinearly — what the generator does, so a block costs a few cached corner lookups; corners and blocks are cached
 * for the whole probe (one structure start). Without it (flat or custom generators) the exact noise columns are used.
 * Carvers and features are not part of the model; the tunnel piece seals whatever lava they add at placement time.
 */
public final class DensityField implements NetherSite.Terrain {
	private final ChunkGenerator generator;
	private final RandomState randomState;
	private final LevelHeightAccessor height;
	private final TerrainProbe probe;
	private final int cw;
	private final int ch;
	private final Map<Long, Double> corners = new HashMap<>();
	private final Map<Long, Boolean> blocks = new HashMap<>();
	private final Map<Long, NoiseColumn> columns = new HashMap<>();
	private final Map<Long, Integer> tops = new HashMap<>();

	private DensityField(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height, int cellWidth, int cellHeight) {
		this.generator = generator;
		this.randomState = randomState;
		this.height = height;
		this.probe = TerrainProbe.of(generator, randomState, height);
		int w = cellWidth;
		int h = cellHeight;
		if (generator instanceof NoiseBasedChunkGenerator noise) {
			Object ns = noise.generatorSettings().value().noiseSettings();
			w = reflectInt(ns, "getCellWidth", cellWidth);
			h = reflectInt(ns, "getCellHeight", cellHeight);
		}
		this.cw = Math.max(1, w);
		this.ch = Math.max(1, h);
	}

	/** Nether / Overworld noise cells (4 × 8) unless the settings say otherwise (26.2). */
	public static DensityField of(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height) {
		return new DensityField(generator, randomState, height, 4, 8);
	}

	/** {@code cellWidth × cellHeight} when the version has no cell sizes in its noise settings (26.3; the End is 8 × 4). */
	public static DensityField of(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height, int cellWidth, int cellHeight) {
		return new DensityField(generator, randomState, height, cellWidth, cellHeight);
	}

	/**
	 * y of the highest solid block of the column between {@code low} and {@code high} (scanned top-down a cell at a
	 * time, then refined), or {@link Integer#MIN_VALUE} when the column is open there (End void). Cached per column.
	 */
	public int top(int x, int z, int low, int high) {
		long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		Integer cached = tops.get(k);
		if (cached != null) {
			return cached;
		}
		int found = Integer.MIN_VALUE;
		for (int y = high; y >= low; y -= ch) {
			if (solid(x, y, z)) {
				int t = y;
				while (t + 1 <= high && solid(x, t + 1, z)) {
					t++;
				}
				found = t;
				break;
			}
		}
		if (found == Integer.MIN_VALUE) {
			for (int y = Math.max(low, high - (high - low) / ch * ch - 1); y >= low; y--) {
				if (solid(x, y, z)) {
					found = y;
					break;
				}
			}
		}
		tops.put(k, found);
		return found;
	}

	private static int reflectInt(Object o, String method, int fallback) {
		try {
			return (int) o.getClass().getMethod(method).invoke(o);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return fallback; // 26.3: no cell sizes in NoiseSettings (the Nether keeps 4 × 8)
		}
	}

	/** True when the fast density model is used (false = exact noise columns). */
	public boolean fast() {
		return probe.fast();
	}

	@Override
	public boolean solid(int x, int y, int z) {
		if (y < height.getMinY()) {
			return true;
		}
		if (y >= height.getMaxY()) {
			return false;
		}
		long k = key(x, y, z);
		Boolean b = blocks.get(k);
		if (b == null) {
			b = probe.fast() ? interpolated(x, y, z) > 0 : exact(x, y, z);
			blocks.put(k, b);
		}
		return b;
	}

	private boolean exact(int x, int y, int z) {
		NoiseColumn col = columns.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), c -> generator.getBaseColumn(x, z, height, randomState));
		BlockState s = col.getBlock(y);
		return !s.isAir() && s.getFluidState().isEmpty();
	}

	private double interpolated(int x, int y, int z) {
		int x0 = Math.floorDiv(x, cw) * cw;
		int y0 = Math.floorDiv(y, ch) * ch;
		int z0 = Math.floorDiv(z, cw) * cw;
		double fx = (x - x0) / (double) cw;
		double fy = (y - y0) / (double) ch;
		double fz = (z - z0) / (double) cw;
		double c000 = corner(x0, y0, z0);
		double c100 = corner(x0 + cw, y0, z0);
		double c010 = corner(x0, y0 + ch, z0);
		double c110 = corner(x0 + cw, y0 + ch, z0);
		double c001 = corner(x0, y0, z0 + cw);
		double c101 = corner(x0 + cw, y0, z0 + cw);
		double c011 = corner(x0, y0 + ch, z0 + cw);
		double c111 = corner(x0 + cw, y0 + ch, z0 + cw);
		double c00 = c000 + (c100 - c000) * fx;
		double c10 = c010 + (c110 - c010) * fx;
		double c01 = c001 + (c101 - c001) * fx;
		double c11 = c011 + (c111 - c011) * fx;
		double c0 = c00 + (c10 - c00) * fy;
		double c1 = c01 + (c11 - c01) * fy;
		return c0 + (c1 - c0) * fz;
	}

	private double corner(int x, int y, int z) {
		long k = key(x, y, z);
		Double d = corners.get(k);
		if (d == null) {
			d = probe.densityAt(x, y, z);
			if (d.isNaN()) {
				d = exact(x, y, z) ? 1.0 : -1.0;
			}
			corners.put(k, d);
		}
		return d;
	}

	/** Corner samples taken so far (diagnostics / surveys). */
	public int samples() {
		return corners.size();
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}
}
