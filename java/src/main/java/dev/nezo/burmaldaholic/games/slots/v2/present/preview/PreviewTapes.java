package dev.nezo.burmaldaholic.games.slots.v2.present.preview;

import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Tumble;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Ways;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * PREVIEW FIXTURE (lane J-L9): fixed, deterministic fake tapes for every presentation path (loss, Returned, Win,
 * Nice/Big/Mega/Epic, anticipation, tumbles, free spins of the three machines, Treasure Hunt, Piglin's Hoard,
 * Dragon Wheel, jackpots, Max Win). Built by seeded search with {@link PreviewEngine}, so every tape is a real,
 * internally consistent spin of the default machines. Used by the preview screen, the client GameTests (JS17) and
 * the frame fidelity unit tests. Never used by the server.
 */
public final class PreviewTapes {
	/** Preview bet (chips; a multiple of 5). */
	public static final long BET = 50;
	/** Preview jackpot pools Mini … Grand. */
	public static final long[] POOLS = {1_040, 2_612, 10_480, 51_220};
	/** Rest window before every preview spin. */
	public static final int[] REST = {3, 7, 11, 5, 9};

	public static final List<String> NAMES = List.of("ow_loss", "ow_returned", "ow_win", "ow_nice", "end_big", "end_mega", "end_epic",
		"ow_anticipation", "ne_tumble", "ow_fs", "ne_fs", "end_fs", "ne_buy", "ow_hunt", "ow_jackpot", "ne_hoard", "end_wheel", "end_maxwin");

	/**
	 * A preview spin.
	 *
	 * @param name          scenario id
	 * @param def           machine
	 * @param tape          the spin
	 * @param restStops     window before the spin
	 * @param terminalCells what the reels must show at the end (engine-computed, independent of the timeline)
	 */
	public record Scenario(String name, MachineDef def, SpinTape tape, int[] restStops, int[] terminalCells) {}

	private PreviewTapes() {}

	public static Scenario get(String name) {
		return switch (name) {
			case "ow_loss" -> base(name, Machine.OVERWORLD, 11, t -> t.total == 0 && !t.triggers());
			case "ow_returned" -> base(name, Machine.OVERWORLD, 12, t -> t.total > 0 && t.total < 5 && !t.triggers());
			case "ow_win" -> base(name, Machine.OVERWORLD, 13, t -> t.total >= 5 && t.total < 25 && t.res.wins().size() >= 2 && !t.triggers());
			case "ow_nice" -> base(name, Machine.OVERWORLD, 14, t -> t.total >= 25 && t.total < 75 && !t.triggers());
			case "end_big" -> biased(name, Machine.END, 15, 3, t -> t.total >= 75 && t.total < 200 && !t.triggers());
			case "end_mega" -> biased(name, Machine.END, 16, 3, t -> t.total >= 200 && t.total < 500 && !t.triggers());
			case "end_epic" -> biased(name, Machine.END, 17, 3, t -> t.total >= 500 && !t.triggers());
			case "ow_anticipation" -> biased(name, Machine.OVERWORLD, 18, 1, t -> t.res.scatters() == 2 && scattersOn(t, 0, 1) && !t.triggers());
			case "ne_tumble" -> base(name, Machine.NETHER, 19, t -> t.chain != null && t.chain.steps().size() >= 3 && !t.triggers());
			case "ow_fs" -> feature(name, Machine.OVERWORLD, 20, 1, t -> t.res.scatters() >= 3 && t.res.bonusCount() < 3);
			case "ne_fs" -> feature(name, Machine.NETHER, 21, 1, t -> t.res.scatters() >= 3 && t.res.coins() < 6);
			case "end_fs" -> feature(name, Machine.END, 22, 1, t -> t.res.scatters() >= 3 && t.res.bonusCount() < 3);
			case "ne_buy" -> bought(name, Machine.NETHER, 23);
			case "ow_hunt" -> feature(name, Machine.OVERWORLD, 24, 2, t -> t.res.bonusCount() >= 3 && t.res.scatters() < 3);
			case "ow_jackpot" -> feature(name, Machine.OVERWORLD, 25, 2, t -> t.res.bonusCount() >= 3 && t.res.scatters() < 3);
			case "ne_hoard" -> feature(name, Machine.NETHER, 26, 2, t -> t.res.coins() >= 6 && t.res.scatters() < 3);
			case "end_wheel" -> feature(name, Machine.END, 27, 2, t -> t.res.bonusCount() >= 3 && t.res.scatters() < 3);
			case "end_maxwin" -> feature(name, Machine.END, 28, 1, t -> t.res.scatters() >= 3 && t.res.bonusCount() < 3);
			default -> throw new IllegalArgumentException("unknown preview tape " + name);
		};
	}

	public static List<Scenario> all() {
		List<Scenario> out = new ArrayList<>();
		for (String n : NAMES) out.add(get(n));
		return out;
	}

	// ---- base-window evaluation --------------------------------------------------------------------------------

	/** A base window candidate. */
	static final class Eval {
		final MachineDef def;
		final int[] stops;
		final int[] finalCells;
		final Ways.Result res;
		final Tumble.Chain chain;
		final long total;

		Eval(MachineDef def, int[] stops) {
			this.def = def;
			this.stops = stops;
			if (def.ladder().length > 0) {
				chain = PreviewEngine.tumble(def, stops, def.ladder());
				finalCells = chain.finalWindow().cells();
				res = PreviewEngine.evaluate(def, finalCells, 0);
				total = chain.payFifths();
			} else {
				chain = null;
				finalCells = PreviewEngine.window(def, stops);
				res = PreviewEngine.evaluate(def, finalCells, 0);
				total = res.payFifths() + PreviewEngine.scatterPay(def, res.scatters());
			}
		}

		boolean triggers() {
			return res.scatters() >= 3 || res.bonusCount() >= 3 || res.coins() >= 6;
		}
	}

	private static boolean scattersOn(Eval t, int r0, int r1) {
		int[] c = PreviewEngine.window(t.def, t.stops);
		return hasRole(t.def, c, r0, SymbolRole.SCATTER) && hasRole(t.def, c, r1, SymbolRole.SCATTER);
	}

	private static boolean hasRole(MachineDef def, int[] cells, int reel, SymbolRole role) {
		for (int y = 0; y < 3; y++) if (def.roles()[cells[reel * 3 + y]] == role) return true;
		return false;
	}

	/**
	 * Seeded search: each reel's stop is uniform, or — with probability 1/2 when {@code bias} ≥ 0 — a stop whose
	 * window column shows symbol index {@code bias} (roles SCATTER = 1, BONUS/COIN = 2, the top symbol = 3).
	 */
	private static Eval search(MachineDef def, int seed, int bias, Predicate<Eval> want) {
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(seed, def.machine().ordinal()));
		for (int tries = 0; tries < 400_000; tries++) {
			int[] stops = new int[5];
			for (int r = 0; r < 5; r++) {
				int len = def.stripLength(r);
				int s = rng.nextInt(len);
				if (bias >= 0 && rng.nextInt(2) == 0) {
					for (int k = 0; k < len; k++) {
						int cand = (s + k) % len;
						if (def.symbolAt(r, cand, 0) == bias || def.symbolAt(r, cand, 1) == bias || def.symbolAt(r, cand, 2) == bias) {
							s = cand;
							break;
						}
					}
				}
				stops[r] = s;
			}
			Eval e = new Eval(def, stops);
			if (want.test(e)) return e;
		}
		throw new IllegalStateException("preview search found no tape for seed " + seed);
	}

	private static Scenario base(String name, Machine m, int seed, Predicate<Eval> want) {
		MachineDef def = PreviewMachines.def(m);
		Eval e = search(def, seed, -1, want);
		SpinTape tape = new SpinTape(m, BET, false, e.stops, null, null, null, null, List.of(), e.total, false);
		return new Scenario(name, def, tape, REST.clone(), e.finalCells);
	}

	private static Scenario biased(String name, Machine m, int seed, int bias, Predicate<Eval> want) {
		MachineDef def = PreviewMachines.def(m);
		Eval e = search(def, seed, bias, want);
		SpinTape tape = new SpinTape(m, BET, false, e.stops, null, null, null, null, List.of(), e.total, false);
		return new Scenario(name, def, tape, REST.clone(), e.finalCells);
	}

	private static Scenario feature(String name, Machine m, int seed, int bias, Predicate<Eval> want) {
		MachineDef def = PreviewMachines.def(m);
		Eval e = search(def, seed, bias, want);
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(seed, 77));
		long total = e.total;
		List<SpinTape.JackpotAward> jackpots = new ArrayList<>();
		SpinTape.Hunt hunt = null;
		SpinTape.Hoard hoard = null;
		SpinTape.Wheel wheel = null;
		SpinTape.FreeSpins fs = null;
		int[] terminal = e.finalCells;
		if (e.res.bonusCount() >= 3 && m == Machine.OVERWORLD) {
			hunt = hunt(rng, name.equals("ow_jackpot"));
			total += huntPay(hunt, jackpots);
		} else if (e.res.coins() >= 6 && m == Machine.NETHER) {
			hoard = hoard(def, e.finalCells, rng);
			total += hoardPay(hoard, jackpots);
		} else if (e.res.bonusCount() >= 3 && m == Machine.END) {
			wheel = new SpinTape.Wheel(new int[] {1, 5}); // outer UP → middle 100×: shows the zoom
			total += 100 * 5;
		}
		if (e.res.scatters() >= 3) {
			FsRun run = freeSpins(def, e.res.scatters(), rng, name.equals("end_maxwin"));
			fs = run.fs;
			total += run.fs.payFifths();
			terminal = run.terminal;
		}
		boolean cap = false;
		long capFifths = (long) def.capMultiple() * 5;
		if (name.equals("end_maxwin") || total >= capFifths) {
			total = capFifths;
			cap = true;
		}
		SpinTape tape = new SpinTape(m, BET, false, e.stops, fs, hunt, hoard, wheel, List.copyOf(jackpots), total, cap);
		return new Scenario(name, def, tape, REST.clone(), terminal);
	}

	private static Scenario bought(String name, Machine m, int seed) {
		MachineDef def = PreviewMachines.def(m);
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(seed, 91));
		FsRun run = freeSpins(def, 3, rng, false);
		SpinTape tape = new SpinTape(m, BET, true, new int[0], run.fs, null, null, null, List.of(), run.fs.payFifths(), false);
		return new Scenario(name, def, tape, REST.clone(), run.terminal);
	}

	// ---- features ----------------------------------------------------------------------------------------------

	private record FsRun(SpinTape.FreeSpins fs, int[] terminal) {}

	private static FsRun freeSpins(MachineDef def, int scatters, SeedMix.FxRng rng, boolean rich) {
		int awarded = def.freeSpins()[Math.min(scatters, 5) - 3];
		int totalSpins = awarded;
		List<SpinTape.FreeSpin> spins = new ArrayList<>();
		int sticky = 0;
		long pay = 0;
		int[] terminal = null;
		int[] stickyCells = new int[15];
		int wild = PreviewEngine.wildIndex(def);
		for (int i = 0; i < totalSpins; i++) {
			int[] stops = new int[5];
			for (int r = 0; r < 5; r++) {
				stops[r] = rng.nextInt(def.stripLength(r));
				if (rich && def.machine() == Machine.END && r >= 1 && r <= 3 && i < 3 && (sticky & (1 << (r - 1))) == 0) {
					// preview: make the first spins land the eggs so the sticky reels fill up
					for (int k = 0; k < def.stripLength(r); k++) {
						if (def.symbolAt(r, k, 1) == wild) {
							stops[r] = k;
							break;
						}
					}
				}
			}
			int[] landed = PreviewEngine.window(def, stops);
			long spinPay;
			int[] finalCells;
			int scat;
			if (def.ladderFree().length > 0) {
				Tumble.Chain chain = PreviewEngine.tumble(def, stops, def.ladderFree());
				finalCells = chain.finalWindow().cells();
				spinPay = chain.payFifths();
				scat = PreviewEngine.evaluate(def, finalCells, 0).scatters();
			} else {
				int before = sticky;
				if (def.machine() == Machine.END) {
					for (int r = 1; r <= 3; r++) {
						if ((sticky & (1 << (r - 1))) != 0) continue;
						for (int y = 0; y < 3; y++) if (landed[r * 3 + y] == wild) sticky |= 1 << (r - 1);
					}
				}
				// cells shown: sticky reels keep the cells they had when they became sticky
				finalCells = landed.clone();
				for (int b = 0; b < 3; b++) {
					int r = b + 1;
					if ((before & (1 << b)) != 0) {
						for (int y = 0; y < 3; y++) finalCells[r * 3 + y] = stickyCells[r * 3 + y];
					} else if ((sticky & (1 << b)) != 0) {
						for (int y = 0; y < 3; y++) stickyCells[r * 3 + y] = landed[r * 3 + y];
					}
				}
				Ways.Result res = PreviewEngine.evaluate(def, landed, sticky);
				scat = res.scatters();
				spinPay = (res.payFifths() + PreviewEngine.scatterPay(def, scat)) * def.fsMultiplier();
			}
			boolean retrigger = scat >= 3 && totalSpins < def.fsCap();
			if (retrigger) totalSpins = Math.min(def.fsCap(), totalSpins + def.retrigger());
			spins.add(new SpinTape.FreeSpin(stops, sticky, retrigger, spinPay));
			pay += spinPay;
			terminal = finalCells;
		}
		return new FsRun(new SpinTape.FreeSpins(awarded, List.copyOf(spins), pay), terminal);
	}

	private static SpinTape.Hunt hunt(SeedMix.FxRng rng, boolean jackpot) {
		int[] entries = new int[15];
		int opened = 15;
		for (int i = 0; i < 15; i++) entries[i] = PreviewMachines.HUNT_VALUES[weighted(rng, PreviewMachines.HUNT_WEIGHTS)];
		if (jackpot) {
			// preview: a guaranteed Minor gem on the third pick, the Creeper on the sixth
			entries[0] = 3;
			entries[1] = 10;
			entries[2] = -2;
			entries[3] = 5;
			entries[4] = 2;
			entries[5] = 0;
		} else {
			entries[0] = Math.max(1, entries[0]);
			entries[1] = entries[1] == 0 ? 2 : entries[1];
			entries[2] = entries[2] == 0 ? 5 : entries[2];
			entries[4] = 0;
		}
		for (int i = 0; i < 15; i++) {
			if (entries[i] == 0) {
				opened = i + 1;
				break;
			}
		}
		return new SpinTape.Hunt(entries, opened);
	}

	private static long huntPay(SpinTape.Hunt hunt, List<SpinTape.JackpotAward> jackpots) {
		long pay = 0;
		for (int i = 0; i < hunt.opened(); i++) {
			int v = hunt.entries()[i];
			if (v > 0) pay += v * 5L;
			if (v < 0) jackpots.add(new SpinTape.JackpotAward(-v, POOLS[-v - 1], false));
		}
		return pay;
	}

	private static SpinTape.Hoard hoard(MachineDef def, int[] finalCells, SeedMix.FxRng rng) {
		List<Integer> cells = new ArrayList<>();
		List<Integer> values = new ArrayList<>();
		boolean[] filled = new boolean[15];
		for (int i = 0; i < 15; i++) {
			if (def.roles()[finalCells[i]] == SymbolRole.COIN) {
				cells.add(i);
				values.add(PreviewMachines.COIN_VALUES[weighted(rng, PreviewMachines.COIN_WEIGHTS)]);
				filled[i] = true;
			}
		}
		List<int[]> respinCells = new ArrayList<>();
		List<int[]> respinValues = new ArrayList<>();
		int respins = 3;
		int count = cells.size();
		int forced = 2; // preview: two early respins land a coin so the reset beat shows
		while (respins > 0 && count < 15) {
			List<Integer> nc = new ArrayList<>();
			List<Integer> nv = new ArrayList<>();
			for (int i = 0; i < 15; i++) {
				if (filled[i]) continue;
				boolean hit = rng.nextInt(100) < 4 || (forced > 0 && nc.isEmpty() && respinCells.size() % 2 == 1);
				if (hit) {
					filled[i] = true;
					nc.add(i);
					nv.add(PreviewMachines.COIN_VALUES[weighted(rng, PreviewMachines.COIN_WEIGHTS)]);
					count++;
				}
			}
			if (!nc.isEmpty()) forced--;
			respinCells.add(nc.stream().mapToInt(Integer::intValue).toArray());
			respinValues.add(nv.stream().mapToInt(Integer::intValue).toArray());
			respins = nc.isEmpty() ? respins - 1 : 3;
		}
		return new SpinTape.Hoard(cells.stream().mapToInt(Integer::intValue).toArray(), values.stream().mapToInt(Integer::intValue).toArray(),
			List.copyOf(respinCells), List.copyOf(respinValues));
	}

	private static long hoardPay(SpinTape.Hoard hoard, List<SpinTape.JackpotAward> jackpots) {
		long pay = 0;
		int count = hoard.initialValues().length;
		for (int v : hoard.initialValues()) pay += coin(v, jackpots);
		for (int[] vs : hoard.respinValues()) {
			for (int v : vs) pay += coin(v, jackpots);
			count += vs.length;
		}
		if (count >= 15) jackpots.add(new SpinTape.JackpotAward(4, POOLS[3], false));
		return pay;
	}

	private static long coin(int v, List<SpinTape.JackpotAward> jackpots) {
		if (v > 0) return v * 5L;
		jackpots.add(new SpinTape.JackpotAward(-v, POOLS[-v - 1], false));
		return 0;
	}

	private static int weighted(SeedMix.FxRng rng, int[] weights) {
		int total = 0;
		for (int w : weights) total += w;
		int r = rng.nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			r -= weights[i];
			if (r < 0) return i;
		}
		return weights.length - 1;
	}
}
