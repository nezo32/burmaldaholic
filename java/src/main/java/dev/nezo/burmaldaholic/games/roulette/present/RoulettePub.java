package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The PUBLIC roulette state the in-world board shows to every passer-by (docs/architecture/ui-v2.md §7.2, §7.6): who
 * is betting (name, chips on the table, number of spots, the net of the last spin), the stacks on the layout coloured
 * by the player who owns most of each (≤ {@link #MAX_STACKS}), and the top win of the spin. Rides in the block
 * entity's update tag next to {@code RouletteSync} as one long array + one string list (names); pure, so the encoding
 * and the aggregation are unit-tested. Spots are indexed by {@link RouletteLayout#allSpots()}.
 *
 * @param players  bettors in seat order (bots after the players)
 * @param stacks   the biggest stacks on the layout
 * @param topName  name of the spin's top winner ("" = none)
 * @param topNet   that winner's net (> 0)
 * @param history  the last numbers, newest first (≤ {@link #MAX_HISTORY})
 */
public record RoulettePub(List<Player> players, List<Stack> stacks, String topName, long topNet, List<Integer> history) {
	public static final int VERSION = 1;
	public static final int MAX_STACKS = 12, MAX_PLAYERS = 8, MAX_NAME = 16, MAX_HISTORY = 8;
	public static final RoulettePub EMPTY = new RoulettePub(List.of(), List.of(), "", 0, List.of());
	private static final List<Spot> SPOTS = RouletteLayout.allSpots();

	/**
	 * @param seat    seat index (-1 = an atmosphere bot without a real seat)
	 * @param bot     an atmosphere bot (virtual chips)
	 * @param total   chips on the table this spin (the settled slip during RESULT)
	 * @param spots   number of spots bet
	 * @param settled the net below is the last spin's result
	 * @param net     return − stake of the settled spin
	 */
	public record Player(String name, int seat, boolean bot, long total, int spots, boolean settled, long net) {}

	/** @param spot index into {@link RouletteLayout#allSpots()}; @param owner index into {@link #players} */
	public record Stack(int spot, int owner, long amount) {
		public Spot spotValue() {
			return SPOTS.get(spot);
		}
	}

	public RoulettePub {
		players = List.copyOf(players);
		stacks = List.copyOf(stacks);
		topName = topName == null ? "" : topName;
		history = List.copyOf(history.size() > MAX_HISTORY ? history.subList(0, MAX_HISTORY) : history);
	}

	public static int spotIndex(Spot s) {
		return SPOTS.indexOf(s);
	}

	/** One bettor's slip (spot → amount) and settled result, for {@link #build}. */
	public record Slip(String name, int seat, boolean bot, Map<Spot, Long> bets, boolean settled, long net) {}

	/** Aggregates slips into the public view: players in the given order (≤ 8), the 12 biggest stacks, the top win. */
	public static RoulettePub build(List<Slip> slips, List<Integer> history) {
		List<Player> players = new ArrayList<>();
		Map<Spot, Long> totals = new LinkedHashMap<>();
		Map<Spot, long[]> owner = new HashMap<>(); // spot → [player index, amount]
		String topName = "";
		long topNet = 0;
		for (Slip s : slips) {
			if (players.size() >= MAX_PLAYERS) break;
			long total = 0;
			for (long a : s.bets().values()) total += a;
			int idx = players.size();
			String name = s.name().length() > MAX_NAME ? s.name().substring(0, MAX_NAME) : s.name();
			players.add(new Player(name, s.seat(), s.bot(), total, s.bets().size(), s.settled(), s.net()));
			for (Map.Entry<Spot, Long> e : s.bets().entrySet()) {
				if (spotIndex(e.getKey()) < 0 || e.getValue() <= 0) continue;
				totals.merge(e.getKey(), e.getValue(), Long::sum);
				long[] o = owner.get(e.getKey());
				if (o == null || e.getValue() > o[1]) owner.put(e.getKey(), new long[] {idx, e.getValue()});
			}
			if (s.settled() && s.net() > topNet) {
				topNet = s.net();
				topName = name;
			}
		}
		List<Stack> stacks = new ArrayList<>();
		totals.forEach((spot, amount) -> stacks.add(new Stack(spotIndex(spot), (int) owner.get(spot)[0], amount)));
		stacks.sort(Comparator.comparingLong(Stack::amount).reversed().thenComparingInt(Stack::spot));
		return new RoulettePub(players, stacks.size() > MAX_STACKS ? stacks.subList(0, MAX_STACKS) : stacks, topName, topNet, history);
	}

	/** {@code [v, nPlayers, (seat, flags, total, spots, net)…, nStacks, (spot, owner, amount)…, topNet, nHist, hist…]}. */
	public long[] encode() {
		long[] out = new long[2 + players.size() * 5 + 1 + stacks.size() * 3 + 2 + history.size()];
		int i = 0;
		out[i++] = VERSION;
		out[i++] = players.size();
		for (Player p : players) {
			out[i++] = p.seat();
			out[i++] = (p.bot() ? 1 : 0) | (p.settled() ? 2 : 0);
			out[i++] = p.total();
			out[i++] = p.spots();
			out[i++] = p.net();
		}
		out[i++] = stacks.size();
		for (Stack s : stacks) {
			out[i++] = s.spot();
			out[i++] = s.owner();
			out[i++] = s.amount();
		}
		out[i++] = topNet;
		out[i++] = history.size();
		for (int n : history) out[i++] = n;
		return out;
	}

	/** Names in player order, then the top winner's name last. */
	public List<String> names() {
		List<String> out = new ArrayList<>();
		for (Player p : players) out.add(p.name());
		out.add(topName);
		return out;
	}

	public static RoulettePub decode(long[] a, List<String> names) {
		if (a.length < 5 || a[0] != VERSION) throw new IllegalArgumentException("roulette pub v" + (a.length > 0 ? a[0] : -1));
		int i = 1;
		int np = (int) a[i++];
		if (np < 0 || np > MAX_PLAYERS || names.size() != np + 1) throw new IllegalArgumentException("players " + np);
		List<Player> players = new ArrayList<>();
		for (int k = 0; k < np; k++) {
			int seat = (int) a[i++];
			int flags = (int) a[i++];
			long total = a[i++];
			int spots = (int) a[i++];
			long net = a[i++];
			players.add(new Player(names.get(k), seat, (flags & 1) != 0, total, spots, (flags & 2) != 0, net));
		}
		int ns = (int) a[i++];
		if (ns < 0 || ns > MAX_STACKS) throw new IllegalArgumentException("stacks " + ns);
		List<Stack> stacks = new ArrayList<>();
		for (int k = 0; k < ns; k++) {
			int spot = (int) a[i++];
			int owner = (int) a[i++];
			long amount = a[i++];
			if (spot < 0 || spot >= SPOTS.size() || owner < 0 || owner >= np) throw new IllegalArgumentException("stack " + spot + "/" + owner);
			stacks.add(new Stack(spot, owner, amount));
		}
		long top = a[i++];
		int nh = (int) a[i++];
		if (nh < 0 || nh > MAX_HISTORY) throw new IllegalArgumentException("history " + nh);
		List<Integer> hist = new ArrayList<>();
		for (int k = 0; k < nh; k++) {
			int n = (int) a[i++];
			if (n < 0 || n > 36) throw new IllegalArgumentException("pocket " + n);
			hist.add(n);
		}
		return new RoulettePub(players, stacks, names.get(np), top, hist);
	}
}
