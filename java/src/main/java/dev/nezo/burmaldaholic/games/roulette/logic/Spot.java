package dev.nezo.burmaldaholic.games.roulette.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A place on the betting layout: bet type + the numbers it covers (sorted, unique). Construct via
 * {@link #of} (normalises); check geometry with {@link #isValid()}. Every valid spot of a type is listed
 * by {@link #all(BetType)}.
 */
public record Spot(BetType type, List<Integer> numbers) {
	public Spot {
		numbers = List.copyOf(new TreeSet<>(numbers));
	}

	public static Spot of(BetType type, int... numbers) {
		List<Integer> list = new ArrayList<>();
		for (int n : numbers) {
			list.add(n);
		}
		return new Spot(type, list);
	}

	public static Spot of(BetType type, List<Integer> numbers) {
		return new Spot(type, numbers);
	}

	public boolean covers(int pocket) {
		return numbers.contains(pocket);
	}

	/** Stable key, e.g. {@code split:17-20}. */
	public String key() {
		return type.id() + ":" + label();
	}

	/** Digits only: {@code 17-20}, {@code 0-1-2}. */
	public String label() {
		return numbers.stream().map(String::valueOf).collect(Collectors.joining("-"));
	}

	public static Optional<Spot> parseKey(String key) {
		int colon = key.indexOf(':');
		if (colon < 0) {
			return Optional.empty();
		}
		Optional<BetType> type = BetType.byId(key.substring(0, colon));
		if (type.isEmpty()) {
			return Optional.empty();
		}
		List<Integer> nums = new ArrayList<>();
		try {
			for (String s : key.substring(colon + 1).split("-")) {
				nums.add(Integer.parseInt(s));
			}
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
		Spot spot = new Spot(type.get(), nums);
		return spot.isValid() ? Optional.of(spot) : Optional.empty();
	}

	/** Index 1..3 of a dozen/column spot, 0 otherwise. */
	public int outsideIndex() {
		if (type != BetType.DOZEN && type != BetType.COLUMN) {
			return 0;
		}
		for (int i = 1; i <= 3; i++) {
			if (numbers.equals(outsideNumbers(type, i))) {
				return i;
			}
		}
		return 0;
	}

	// ---- geometry ----------------------------------------------------------------------------

	private static List<Integer> range(int a, int b) {
		List<Integer> out = new ArrayList<>();
		for (int i = a; i <= b; i++) {
			out.add(i);
		}
		return out;
	}

	private static List<Integer> street(int row) {
		return List.of(row * 3 + 1, row * 3 + 2, row * 3 + 3);
	}

	/** Numbers of an outside bet; dozen/column take an index 1..3 (ignored otherwise). */
	public static List<Integer> outsideNumbers(BetType type, int index) {
		return switch (type) {
			case DOZEN -> range((index - 1) * 12 + 1, index * 12);
			case COLUMN -> range(1, 36).stream().filter(n -> Wheel.layoutCol(n) == index - 1).toList();
			case RED -> range(1, 36).stream().filter(Wheel.RED::contains).toList();
			case BLACK -> range(1, 36).stream().filter(n -> !Wheel.RED.contains(n)).toList();
			case ODD -> range(1, 36).stream().filter(n -> n % 2 == 1).toList();
			case EVEN -> range(1, 36).stream().filter(n -> n % 2 == 0).toList();
			case LOW -> range(1, 18);
			case HIGH -> range(19, 36);
			default -> throw new IllegalArgumentException(type + " is not an outside bet");
		};
	}

	/** Geometry validation on the European layout (3 × 12 grid + the 0 pocket). */
	public boolean isValid() {
		List<Integer> n = numbers;
		if (n.isEmpty() || !n.stream().allMatch(Wheel::isPocket)) {
			return false;
		}
		int a = n.get(0);
		return switch (type) {
			case STRAIGHT -> n.size() == 1;
			case SPLIT -> {
				if (n.size() != 2) {
					yield false;
				}
				int b = n.get(1);
				if (a == 0) {
					yield b >= 1 && b <= 3;
				}
				yield (Wheel.layoutRow(a) == Wheel.layoutRow(b) && b - a == 1) || (Wheel.layoutCol(a) == Wheel.layoutCol(b) && b - a == 3);
			}
			case STREET -> n.size() == 3 && a >= 1 && Wheel.layoutCol(a) == 0 && n.equals(street(Wheel.layoutRow(a)));
			case TRIO -> n.equals(List.of(0, 1, 2)) || n.equals(List.of(0, 2, 3));
			case CORNER -> n.size() == 4 && a >= 1 && a <= 32 && Wheel.layoutCol(a) < 2 && n.equals(List.of(a, a + 1, a + 3, a + 4));
			case FIRST_FOUR -> n.equals(List.of(0, 1, 2, 3));
			case SIX_LINE -> {
				if (n.size() != 6 || a < 1 || a > 31 || Wheel.layoutCol(a) != 0) {
					yield false;
				}
				List<Integer> six = new ArrayList<>(street(Wheel.layoutRow(a)));
				six.addAll(street(Wheel.layoutRow(a) + 1));
				yield n.equals(six);
			}
			case DOZEN, COLUMN -> n.equals(outsideNumbers(type, 1)) || n.equals(outsideNumbers(type, 2)) || n.equals(outsideNumbers(type, 3));
			default -> n.equals(outsideNumbers(type, 0));
		};
	}

	/** Every valid spot of a type, in layout order. */
	public static List<Spot> all(BetType type) {
		List<Spot> out = new ArrayList<>();
		switch (type) {
			case STRAIGHT -> {
				for (int x = 0; x <= 36; x++) {
					out.add(of(type, x));
				}
			}
			case SPLIT -> {
				out.add(of(type, 0, 1));
				out.add(of(type, 0, 2));
				out.add(of(type, 0, 3));
				for (int x = 1; x <= 36; x++) {
					if (Wheel.layoutCol(x) < 2) {
						out.add(of(type, x, x + 1));
					}
					if (x <= 33) {
						out.add(of(type, x, x + 3));
					}
				}
			}
			case STREET -> {
				for (int r = 0; r < 12; r++) {
					out.add(of(type, street(r)));
				}
			}
			case TRIO -> {
				out.add(of(type, 0, 1, 2));
				out.add(of(type, 0, 2, 3));
			}
			case CORNER -> {
				for (int x = 1; x <= 32; x++) {
					if (Wheel.layoutCol(x) < 2) {
						out.add(of(type, x, x + 1, x + 3, x + 4));
					}
				}
			}
			case FIRST_FOUR -> out.add(of(type, 0, 1, 2, 3));
			case SIX_LINE -> {
				for (int r = 0; r < 11; r++) {
					List<Integer> six = new ArrayList<>(street(r));
					six.addAll(street(r + 1));
					out.add(of(type, six));
				}
			}
			case DOZEN, COLUMN -> {
				for (int i = 1; i <= 3; i++) {
					out.add(of(type, outsideNumbers(type, i)));
				}
			}
			default -> out.add(of(type, outsideNumbers(type, 0)));
		}
		return out;
	}
}
