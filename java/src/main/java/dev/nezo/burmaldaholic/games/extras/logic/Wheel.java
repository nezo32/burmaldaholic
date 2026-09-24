package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wheel of Fortune (GAME_DESIGN.md §11.2, appendix B). The segments are fixed in order (index 0 at the
 * pointer, clockwise); a spin lands uniformly on one index and pays {@code floor(stake × multiplier)}.
 * The Creeper segment pays 0 and triggers the chaos {@code mob_wave} event. Pure Java.
 */
public final class Wheel {
	public static final List<String> CODES = List.of("B", "C", "H", "M", "D", "T", "E", "X");
	public static final List<String> DEFAULT_SEGMENTS = List.of(
		"X", "B", "M", "B", "D", "B", "M", "B", "H", "B", "D", "B", "M", "B", "T", "B", "M", "B", "D", "B", "H", "B", "M", "B", "D", "B", "E",
		"B", "M", "B", "D", "B", "H", "B", "M", "B", "T", "B", "M", "B", "D", "B", "H", "B", "M", "B", "T", "B", "C", "M", "H", "D", "M", "B");
	public static final Map<String, Double> DEFAULT_MULTIPLIERS = Map.of("B", 0.0, "C", 0.0, "H", 0.5, "M", 1.0, "D", 2.0, "T", 3.0, "E", 5.0, "X", 10.0);
	public static final String CREEPER = "C";

	private final List<String> segments;
	private final Map<String, Double> multipliers;
	private final List<CasinoRng.Weighted<Integer>> uniform;

	public Wheel(List<String> segments, Map<String, Double> multipliers) {
		Map<String, Double> m = new LinkedHashMap<>(DEFAULT_MULTIPLIERS);
		if (multipliers != null) {
			multipliers.forEach((k, v) -> {
				if (k != null && v != null) {
					m.put(k, Double.isFinite(v) && v > 0 ? v : 0.0);
				}
			});
		}
		List<String> segs = new ArrayList<>();
		if (segments != null) {
			for (String s : segments) {
				if (s != null && m.containsKey(s)) {
					segs.add(s);
				}
			}
		}
		this.segments = List.copyOf(segs.size() >= 2 ? segs : DEFAULT_SEGMENTS);
		this.multipliers = Collections.unmodifiableMap(m);
		List<CasinoRng.Weighted<Integer>> all = new ArrayList<>(this.segments.size());
		for (int i = 0; i < this.segments.size(); i++) {
			all.add(new CasinoRng.Weighted<>(i, 1.0));
		}
		this.uniform = List.copyOf(all);
	}

	public static Wheel standard() {
		return new Wheel(DEFAULT_SEGMENTS, DEFAULT_MULTIPLIERS);
	}

	public List<String> segments() {
		return segments;
	}

	public int size() {
		return segments.size();
	}

	public double multiplier(String code) {
		return multipliers.getOrDefault(code, 0.0);
	}

	public record Spin(int index, String code, double multiplier) {
		public boolean creeper() {
			return CREEPER.equals(code);
		}
	}

	public Spin at(int index) {
		String code = segments.get(Math.floorMod(index, segments.size()));
		return new Spin(Math.floorMod(index, segments.size()), code, multiplier(code));
	}

	/**
	 * One spin: uniform over the segments. Drawn with {@link CasinoRng#weighted} so modifiers (VIP, chaos,
	 * Last Chance) can tilt the share of paying segments ({@code multiplier > 1}); without modifiers it is
	 * exactly uniform.
	 */
	public Spin spin(CasinoRng rng) {
		int index = rng.weighted(uniform, i -> multiplier(segments.get(i)) > 1.0);
		return at(index);
	}

	/** Total return (stake included). */
	public static long totalReturn(long stake, Spin spin) {
		return Payouts.floorPay(stake, spin.multiplier());
	}

	/** Exact RTP before flooring: the mean multiplier. */
	public double rtp() {
		double sum = 0;
		for (String s : segments) {
			sum += multiplier(s);
		}
		return sum / segments.size();
	}

	public double maxMultiplier() {
		double max = 0;
		for (String s : segments) {
			max = Math.max(max, multiplier(s));
		}
		return max;
	}

	public Map<String, Integer> counts() {
		Map<String, Integer> m = new LinkedHashMap<>();
		for (String s : segments) {
			m.merge(s, 1, Integer::sum);
		}
		return m;
	}

	public record LegendRow(String code, double multiplier, int count) {}

	/** Legend rows ordered by multiplier (bust first, creeper after bust). */
	public List<LegendRow> legend() {
		List<LegendRow> rows = new ArrayList<>();
		counts().forEach((code, n) -> rows.add(new LegendRow(code, multiplier(code), n)));
		rows.sort(Comparator.comparingDouble(LegendRow::multiplier).thenComparing(r -> CREEPER.equals(r.code()) ? 1 : 0));
		return rows;
	}

	/** Lang key suffix per code: {@code gui.burmaldaholic.extras.wheel.segment.<name>}. */
	public static String segmentKey(String code) {
		String name = switch (code) {
			case "B" -> "bust";
			case "C" -> "creeper";
			case "H" -> "half";
			case "M" -> "money_back";
			case "D" -> "double";
			case "T" -> "triple";
			case "E" -> "emerald";
			case "X" -> "diamond";
			default -> "bust";
		};
		return "gui.burmaldaholic.extras.wheel.segment." + name;
	}

	/**
	 * Segment name without its multiplier, for lines that print the multiplier next to it (the legend):
	 * {@code segment.emerald} is "Emerald ×5", the legend shows "Emerald — ×5".
	 */
	public static String segmentNameKey(String code) {
		return switch (code) {
			case "E" -> "gui.burmaldaholic.extras.wheel.segment_name.emerald";
			case "X" -> "gui.burmaldaholic.extras.wheel.segment_name.diamond";
			default -> segmentKey(code);
		};
	}

	/** ARGB color per segment code (loss red, push gray, win green, big wins gold/aqua). */
	public static int color(String code) {
		return switch (code) {
			case "B" -> 0xFF8E2A2A;
			case "C" -> 0xFF2E8B2E;
			case "H" -> 0xFFB5563A;
			case "M" -> 0xFF7A7A7A;
			case "D" -> 0xFF3A9E4A;
			case "T" -> 0xFF2F7FC0;
			case "E" -> 0xFF18B45A;
			case "X" -> 0xFF4FD8E0;
			default -> 0xFF444444;
		};
	}
}
