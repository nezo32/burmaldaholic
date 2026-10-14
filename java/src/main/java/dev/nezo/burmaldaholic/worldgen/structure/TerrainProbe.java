package dev.nezo.burmaldaholic.worldgen.structure;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * Cheap terrain heights for the casino fit (R-CB M6a): instead of a full noise column per probe
 * ({@code getBaseColumn} / {@code getBaseHeight} fill all ~49 cells of 4 corner columns, ~1–3 ms each), the final
 * density is sampled at a handful of heights around a guess and the solid/air crossing is interpolated — the
 * same linear interpolation between noise cells the generator uses, so the result is the noise surface within
 * a block or so (measured by {@code WorldgenSpawnSurvey.terrainProbeAccuracy}).
 *
 * <p>Only {@code public} API, resolved once by reflection so the 26.2-compiled jar links on both versions:
 * 26.2 {@code randomState.router().finalDensity().compute(new SinglePointContext(x, y, z))}; 26.3
 * {@code randomState.sampleBlockValueUncached(settings.noiseRouter().finalDensity(), x, y, z)}. Anything else
 * (another generator, a missing method) falls back to vanilla's exact {@code getBaseHeight}.
 *
 * <p>Water: below sea level a non-solid cell is water (aquifers keep the sea level at the surface), so
 * {@code surface = max(solidTop + 1, seaLevel)} ({@code WORLD_SURFACE_WG}) and {@code floor = solidTop + 1}
 * ({@code OCEAN_FLOOR_WG}).
 */
public final class TerrainProbe {
	/** Density sample spacing (26.2's overworld cell height; also used on 26.3). */
	static final int DEFAULT_STEP = 8;
	/** Walk at most this many steps from the guess before giving up (→ exact column). */
	private static final int MAX_STEPS = 48;
	/** Guess meaning "no nearby height known": the search starts high above sea level and descends. */
	public static final int NO_GUESS = Integer.MIN_VALUE;
	/** The search starts this far above the guess, so a guess a little too low never starts inside a cave. */
	static final int HEADROOM = 16;
	/** Air samples in a row that end a climb. */
	static final int AIR_RUN = 3;

	private static final MethodHandle ROUTER_262; // RandomState -> NoiseRouter
	private static final MethodHandle FINAL_262; // NoiseRouter -> DensityFunction
	private static final MethodHandle COMPUTE_262; // DensityFunction, FunctionContext -> double
	private static final MethodHandle POINT_262; // (int, int, int) -> SinglePointContext
	private static final MethodHandle CELL_HEIGHT_262; // NoiseSettings -> int
	private static final MethodHandle FINAL_263; // NoiseRouter -> DensityFunction (26.3 package)
	private static final MethodHandle SAMPLE_263; // RandomState, DensityFunction, x, y, z -> float
	static final boolean AVAILABLE;

	static {
		MethodHandles.Lookup l = MethodHandles.publicLookup();
		MethodHandle router = null;
		MethodHandle fin = null;
		MethodHandle compute = null;
		MethodHandle point = null;
		MethodHandle cell = null;
		MethodHandle fin3 = null;
		MethodHandle sample3 = null;
		try {
			Class<?> df = Class.forName("net.minecraft.world.level.levelgen.DensityFunction");
			Class<?> fc = Class.forName("net.minecraft.world.level.levelgen.DensityFunction$FunctionContext");
			Class<?> sp = Class.forName("net.minecraft.world.level.levelgen.DensityFunction$SinglePointContext");
			Class<?> nr = Class.forName("net.minecraft.world.level.levelgen.NoiseRouter");
			router = l.findVirtual(RandomState.class, "router", MethodType.methodType(nr));
			fin = l.findVirtual(nr, "finalDensity", MethodType.methodType(df));
			compute = l.findVirtual(df, "compute", MethodType.methodType(double.class, fc));
			point = l.findConstructor(sp, MethodType.methodType(void.class, int.class, int.class, int.class));
			cell = l.findVirtual(net.minecraft.world.level.levelgen.NoiseSettings.class, "getCellHeight", MethodType.methodType(int.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			router = null;
		}
		if (router == null) {
			try {
				Class<?> df = Class.forName("net.minecraft.world.level.levelgen.densityfunction.DensityFunction");
				Class<?> nr = Class.forName("net.minecraft.world.level.levelgen.NoiseRouter");
				fin3 = l.findVirtual(nr, "finalDensity", MethodType.methodType(df));
				sample3 = l.findVirtual(RandomState.class, "sampleBlockValueUncached",
					MethodType.methodType(float.class, df, int.class, int.class, int.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				fin3 = null;
				sample3 = null;
			}
		}
		ROUTER_262 = router;
		FINAL_262 = fin;
		COMPUTE_262 = compute;
		POINT_262 = point;
		CELL_HEIGHT_262 = cell;
		FINAL_263 = fin3;
		SAMPLE_263 = sample3;
		AVAILABLE = router != null || sample3 != null;
	}

	/** Switch for surveys comparing against the exact columns (production: true). */
	public static volatile boolean enabled = true;

	private final ChunkGenerator generator;
	private final RandomState randomState;
	private final LevelHeightAccessor height;
	private final @Nullable Object density; // 26.2: wired DensityFunction; 26.3: unwired DensityFunction
	private final int seaLevel;
	private final int step;
	/** Exact-column calls made (fallbacks + {@link #exact}); diagnostics. */
	int exactCalls;
	int densityCalls;

	private TerrainProbe(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height) {
		this.generator = generator;
		this.randomState = randomState;
		this.height = height;
		Object d = null;
		int sea = generator.getSeaLevel();
		int st = DEFAULT_STEP;
		if (enabled && AVAILABLE && generator instanceof NoiseBasedChunkGenerator noise) {
			NoiseGeneratorSettings settings = noise.generatorSettings().value();
			sea = settings.seaLevel();
			try {
				if (ROUTER_262 != null) {
					d = FINAL_262.invoke(ROUTER_262.invoke(randomState));
					st = (int) CELL_HEIGHT_262.invoke(settings.noiseSettings());
				} else {
					d = FINAL_263.invoke(settings.noiseRouter());
				}
			} catch (Throwable t) {
				d = null;
			}
		}
		this.density = d;
		this.seaLevel = sea;
		this.step = Math.max(1, st);
	}

	public static TerrainProbe of(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height) {
		return new TerrainProbe(generator, randomState, height);
	}

	/** True when heights come from the density sampler (false = vanilla's exact columns). */
	public boolean fast() {
		return density != null;
	}

	public int seaLevel() {
		return seaLevel;
	}

	private double density(int x, int y, int z) throws Throwable {
		densityCalls++;
		if (ROUTER_262 != null) {
			return (double) COMPUTE_262.invoke(density, POINT_262.invoke(x, y, z));
		}
		return (float) SAMPLE_263.invoke(randomState, density, x, y, z);
	}

	/**
	 * {@code {surface, floor}} of the column at {@code x, z} ({@code WORLD_SURFACE_WG}, {@code OCEAN_FLOOR_WG}
	 * equivalents); {@code guess} is a nearby surface height (the neighbouring probe) or {@link #NO_GUESS}.
	 * The search starts {@link #HEADROOM} above the guess: in air it descends to the first solid sample (top-down,
	 * like the heightmap); in solid it climbs until {@link #AIR_RUN} air samples in a row (a cave pocket under the
	 * surface is shorter).
	 */
	public int[] heights(int x, int z, int guess) {
		if (density == null) {
			return exact(x, z);
		}
		try {
			int minY = height.getMinY();
			int maxY = height.getMaxY();
			int g = guess == NO_GUESS ? seaLevel + 96 : guess + HEADROOM;
			int y0 = minY + Math.floorDiv(Math.clamp(g, minY, maxY) - minY, step) * step;
			double d0 = density(x, y0, z);
			int steps = 0;
			if (d0 > 0) {
				int solidY = y0;
				double solidD = d0;
				double firstAir = 0;
				int air = 0;
				int y = y0;
				while (air < AIR_RUN) {
					y += step;
					if (y > maxY || ++steps > MAX_STEPS) {
						if (air > 0) {
							break;
						}
						return exact(x, z);
					}
					double d = density(x, y, z);
					if (d > 0) {
						solidY = y;
						solidD = d;
						air = 0;
					} else {
						if (air == 0) {
							firstAir = d;
						}
						air++;
					}
				}
				return result(solidY, solidD, firstAir);
			}
			while (true) {
				int y1 = y0 - step;
				if (y1 < minY || ++steps > MAX_STEPS) {
					return exact(x, z);
				}
				double d1 = density(x, y1, z);
				if (d1 > 0) {
					return result(y1, d1, d0);
				}
				y0 = y1;
				d0 = d1;
			}
		} catch (Throwable t) {
			return exact(x, z);
		}
	}

	/** Solid sample {@code dLow > 0} at {@code yLow}, air sample {@code dHigh <= 0} one step up: top solid block by lerp. */
	private int[] result(int yLow, double dLow, double dHigh) {
		int k = 0;
		for (int i = step - 1; i > 0; i--) {
			double v = dLow + (dHigh - dLow) * i / step;
			if (v > 0) {
				k = i;
				break;
			}
		}
		int floor = yLow + k + 1;
		return new int[] {Math.max(floor, seaLevel), floor};
	}

	/** Vanilla's exact column heights (two partial noise columns). */
	public int[] exact(int x, int z) {
		exactCalls++;
		int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
		int floor = surface > seaLevel ? surface : generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
		return new int[] {surface, floor};
	}
}
