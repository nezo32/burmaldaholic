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
	static final int HEADROOM = 8;
	/** Air samples in a row that end a climb. */
	static final int AIR_RUN = 3;

	private static final MethodHandle ROUTER_262; // RandomState -> NoiseRouter
	private static final MethodHandle FINAL_262; // NoiseRouter -> DensityFunction
	private static final MethodHandle COMPUTE_262; // DensityFunction, FunctionContext -> double
	private static final MethodHandle POINT_262; // (int, int, int) -> SinglePointContext
	private static final MethodHandle CELL_HEIGHT_262; // NoiseSettings -> int
	private static final MethodHandle FINAL_263; // NoiseRouter -> DensityFunction (26.3 package)
	private static final MethodHandle SAMPLE_263; // RandomState, DensityFunction, x, y, z -> float
	// 26.3 volume sampling (a column window in one call: the 2D parts are evaluated once, not per sample)
	private static final MethodHandle SAMPLER_263; // RandomState, DensityFunction -> DensitySampler
	private static final MethodHandle VOLUME_263; // sizeX, sizeY, sizeZ, minX, minY, minZ, stepX, stepY, stepZ -> DensityVolume
	private static final MethodHandle BUFFER_263; // int -> DensityBuffer
	private static final MethodHandle SAMPLE_VOLUME_263; // DensitySampler, SamplerContext, DensityBuffer, DensityVolume -> void
	private static final MethodHandle BUFFER_GET_263; // DensityBuffer, int -> float
	private static final Object UNCACHED_263; // SamplerContext.EMPTY_UNCACHED
	/** Samples per 26.3 column window. */
	static final int WINDOW = 12;
	/** The 26.3 window starts this far above the guess (one call usually covers the surface). */
	static final int WINDOW_HEADROOM = 32;
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
		MethodHandle sampler3 = null;
		MethodHandle volume3 = null;
		MethodHandle buffer3 = null;
		MethodHandle sampleVolume3 = null;
		MethodHandle get3 = null;
		Object uncached3 = null;
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
			try {
				String pkg = "net.minecraft.world.level.levelgen.densityfunction.";
				Class<?> df = Class.forName(pkg + "DensityFunction");
				Class<?> ds = Class.forName(pkg + "DensitySampler");
				Class<?> dv = Class.forName(pkg + "DensityVolume");
				Class<?> db = Class.forName(pkg + "DensityBuffer");
				Class<?> sc = Class.forName(pkg + "SamplerContext");
				sampler3 = l.findVirtual(RandomState.class, "getSampler", MethodType.methodType(ds, df));
				volume3 = l.findConstructor(dv, MethodType.methodType(void.class, int.class, int.class, int.class, int.class, int.class, int.class,
					int.class, int.class, int.class));
				buffer3 = l.findStatic(db, "createUnpooled", MethodType.methodType(db, int.class));
				sampleVolume3 = l.findVirtual(ds, "sampleVolume", MethodType.methodType(void.class, sc, db, dv));
				get3 = l.findVirtual(db, "get", MethodType.methodType(float.class, int.class));
				uncached3 = l.findStaticGetter(sc, "EMPTY_UNCACHED", sc).invoke();
			} catch (Throwable e) {
				sampler3 = null;
			}
		}
		ROUTER_262 = router;
		FINAL_262 = fin;
		COMPUTE_262 = compute;
		POINT_262 = point;
		CELL_HEIGHT_262 = cell;
		FINAL_263 = fin3;
		SAMPLE_263 = sample3;
		boolean vol = sampler3 != null && volume3 != null && buffer3 != null && sampleVolume3 != null && get3 != null && uncached3 != null;
		SAMPLER_263 = vol ? sampler3 : null;
		VOLUME_263 = vol ? volume3 : null;
		BUFFER_263 = vol ? buffer3 : null;
		SAMPLE_VOLUME_263 = vol ? sampleVolume3 : null;
		BUFFER_GET_263 = vol ? get3 : null;
		UNCACHED_263 = vol ? uncached3 : null;
		AVAILABLE = router != null || sample3 != null;
	}

	/** Switch for surveys comparing against the exact columns (production: true). */
	public static volatile boolean enabled = true;

	private final ChunkGenerator generator;
	private final RandomState randomState;
	private final LevelHeightAccessor height;
	private final @Nullable Object density; // 26.2: wired DensityFunction; 26.3: unwired DensityFunction
	private final @Nullable Object sampler263; // 26.3: compiled DensitySampler of the final density (volume path)
	private final @Nullable Object buffer263;
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
		Object smp = null;
		Object buf = null;
		if (d != null && ROUTER_262 == null && SAMPLER_263 != null) {
			try {
				smp = SAMPLER_263.invoke(randomState, d);
				buf = BUFFER_263.invoke(WINDOW);
			} catch (Throwable t) {
				smp = null;
				buf = null;
			}
		}
		this.sampler263 = smp;
		this.buffer263 = buf;
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
		if (sampler263 != null) {
			return windowed(x, z, guess);
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

	/**
	 * 26.3: the same search on {@link #WINDOW}-sample column windows sampled in one {@code sampleVolume} call (26.3
	 * samples a lone point ~5× slower than 26.2 but shares the column work inside a volume). The window starts
	 * {@link #HEADROOM} above the guess; solid in its top {@link #AIR_RUN} samples moves it up, no solid at all moves
	 * it down; otherwise the first solid sample from the top is the surface.
	 */
	private int[] windowed(int x, int z, int guess) {
		try {
			int minY = height.getMinY();
			int maxY = height.getMaxY();
			int g = guess == NO_GUESS ? seaLevel + 96 : guess + WINDOW_HEADROOM;
			int top = minY + Math.floorDiv(Math.clamp(g, minY, maxY) - minY, step) * step;
			double above = Double.NaN; // the sample just above this window (when moving down)
			for (int round = 0; round < 8; round++) {
				int low = top - (WINDOW - 1) * step;
				if (low < minY || top > maxY) {
					return exact(x, z);
				}
				Object volume = VOLUME_263.invoke(1, WINDOW, 1, x, low, z, 1, step, 1);
				SAMPLE_VOLUME_263.invoke(sampler263, UNCACHED_263, buffer263, volume);
				densityCalls += WINDOW;
				float[] v = new float[WINDOW];
				for (int i = 0; i < WINDOW; i++) {
					v[i] = (float) BUFFER_GET_263.invoke(buffer263, i); // i = 0 is the lowest sample
				}
				boolean solidOnTop = false;
				if (Double.isNaN(above)) {
					for (int i = WINDOW - AIR_RUN; i < WINDOW; i++) {
						solidOnTop |= v[i] > 0;
					}
				}
				if (solidOnTop) {
					top += (WINDOW - AIR_RUN) * step; // climb, keeping AIR_RUN samples of overlap
					continue;
				}
				for (int i = WINDOW - 1; i >= 0; i--) {
					if (v[i] > 0) {
						double dHigh = i + 1 < WINDOW ? v[i + 1] : above;
						return result(low + i * step, v[i], dHigh);
					}
				}
				above = v[0];
				top = low - step;
			}
			return exact(x, z);
		} catch (Throwable t) {
			return exact(x, z);
		}
	}

	/** True when {@link #line} samples a whole row of columns in one call (26.3 volume path). */
	public boolean lines() {
		return sampler263 != null;
	}

	/**
	 * 26.3: {@code n} columns {@code (x0 + i·spacing, z0)} ({@code alongX}) or {@code (x0, z0 + i·spacing)} sampled in ONE
	 * {@code sampleVolume} call (a lone 26.3 sample costs ~0.2 ms; a row shares that overhead). Each column gets a
	 * {@link #WINDOW}-sample window {@link #WINDOW_HEADROOM} above {@code guess}; an entry is null when its window
	 * does not bracket the surface (solid in the top {@link #AIR_RUN} samples, or no solid at all) — callers use
	 * {@link #heights} for those. Null when the volume path is not available.
	 */
	public int @Nullable [][] line(int x0, int z0, boolean alongX, int spacing, int n, int guess) {
		if (sampler263 == null || n <= 0) {
			return null;
		}
		try {
			int minY = height.getMinY();
			int maxY = height.getMaxY();
			int g = guess == NO_GUESS ? seaLevel + 32 : guess + WINDOW_HEADROOM;
			int top = minY + Math.floorDiv(Math.clamp(g, minY, maxY) - minY, this.step) * this.step;
			int low = top - (WINDOW - 1) * this.step;
			if (low < minY || top > maxY) {
				return null;
			}
			Object buf = BUFFER_263.invoke(n * WINDOW);
			Object volume = alongX
				? VOLUME_263.invoke(n, WINDOW, 1, x0, low, z0, spacing, this.step, 1)
				: VOLUME_263.invoke(1, WINDOW, n, x0, low, z0, 1, this.step, spacing);
			SAMPLE_VOLUME_263.invoke(sampler263, UNCACHED_263, buf, volume);
			densityCalls += n * WINDOW;
			int[][] out = new int[n][];
			float[] v = new float[WINDOW];
			for (int c = 0; c < n; c++) {
				for (int i = 0; i < WINDOW; i++) {
					v[i] = (float) BUFFER_GET_263.invoke(buf, c * WINDOW + i); // index = y + column · sizeY
				}
				boolean solidOnTop = false;
				for (int i = WINDOW - AIR_RUN; i < WINDOW; i++) {
					solidOnTop |= v[i] > 0;
				}
				if (solidOnTop) {
					continue;
				}
				for (int i = WINDOW - 1; i >= 0; i--) {
					if (v[i] > 0) {
						out[c] = result(low + i * this.step, v[i], v[i + 1]);
						break;
					}
				}
			}
			return out;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Diagnostic (26.3 volume path): nanoseconds to sample an {@code n × ny × n} grid (step 4, 8, 4); -1 without it. */
	public long benchGrid(int x, int y, int z, int n, int ny) {
		if (sampler263 == null) {
			return -1;
		}
		try {
			Object buf = BUFFER_263.invoke(n * ny * n);
			Object volume = VOLUME_263.invoke(n, ny, n, x, y, z, 4, 8, 4);
			long t0 = System.nanoTime();
			SAMPLE_VOLUME_263.invoke(sampler263, UNCACHED_263, buf, volume);
			return System.nanoTime() - t0;
		} catch (Throwable t) {
			return -2;
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
