package dev.nezo.burmaldaholic.vip.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Daily contracts (GAME_DESIGN.md §3.4.4). Pure. Generated once per world day, drawn without
 * duplicates by weight; targets and rewards scale with the VIP tier index; one reroll per slot per day.
 * Randomness comes in as {@code nextInt(bound)} (the runtime passes {@code OddsService} draws).
 */
public final class ContractRules {
	/** §3.4.4 table: base target, base reward, default weight. */
	public record Def(String id, int target, int reward, int weight) {}

	public static final Map<String, Def> DEFS = new LinkedHashMap<>();

	static {
		def("mine_iron", 24, 50, 10);
		def("mine_coal", 48, 30, 8);
		def("mine_diamond", 3, 80, 5);
		def("kill_zombie", 12, 40, 10);
		def("kill_skeleton", 10, 40, 8);
		def("kill_creeper", 5, 45, 6);
		def("kill_any", 25, 60, 8);
		def("trade", 6, 40, 8);
		def("fish", 8, 30, 6);
		def("harvest", 64, 30, 6);
		def("wager", 500, 30, 8);
		def("win_blackjack", 3, 40, 5);
		def("spin_slots", 30, 25, 5);
		def("roulette_red", 2, 35, 4);
		def("play_poker", 10, 40, 3);
		def("explore_nether", 500, 60, 3);
		def("smelt", 32, 25, 3);
	}

	private static void def(String id, int target, int reward, int weight) {
		DEFS.put(id, new Def(id, target, reward, weight));
	}

	private ContractRules() {}

	public static boolean isId(String id) {
		return DEFS.containsKey(id);
	}

	/** One contract slot. Mutable progress; everything else fixed at creation. */
	public static final class Contract {
		public final String id;
		public final long target;
		public final long reward;
		public long progress;
		public boolean done;
		public boolean rerolled;

		public Contract(String id, long target, long reward, long progress, boolean done, boolean rerolled) {
			this.id = id;
			this.target = target;
			this.reward = reward;
			this.progress = progress;
			this.done = done;
			this.rerolled = rerolled;
		}
	}

	/** A player's contracts for world day {@code day}. */
	public static final class State {
		public final long day;
		public final List<Contract> list;

		public State(long day, List<Contract> list) {
			this.day = day;
			this.list = list;
		}

		public boolean allDone() {
			return !list.isEmpty() && list.stream().allMatch(c -> c.done);
		}

		public boolean wants(String id) {
			for (Contract c : list) {
				if (c.id.equals(id) && !c.done) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * @param tier       VIP tier index t (0 = Bronze)
	 * @param scaling    {@code contracts.tierScaling} (0.25)
	 * @param bonus      VIP contract bonus fraction (§12)
	 * @param multiplier {@code contracts.rewardMultiplier}
	 * @param weights    weight per id (missing = default weight, 0 disables)
	 */
	public record Params(int tier, double scaling, double bonus, double multiplier, Map<String, Integer> weights) {}

	/** {@code target = ceil(base × (1 + s·t))}, at least 1. */
	public static long scaledTarget(long base, int tier, double scaling) {
		return Math.max(1, (long) Math.ceil(base * (1 + scaling * tier) - 1e-9));
	}

	/** {@code reward = floor(baseReward × (1 + s·t) × (1 + bonus) × multiplier)}. */
	public static long scaledReward(long base, int tier, double scaling, double bonus, double multiplier) {
		return Math.max(0, (long) Math.floor(base * (1 + scaling * tier) * (1 + bonus) * multiplier + 1e-9));
	}

	public static Contract make(String id, Params p) {
		Def d = DEFS.get(id);
		return new Contract(id, scaledTarget(d.target(), p.tier(), p.scaling()),
			scaledReward(d.reward(), p.tier(), p.scaling(), p.bonus(), p.multiplier()), 0, false, false);
	}

	public static int weightOf(String id, Params p) {
		Integer w = p.weights() == null ? null : p.weights().get(id);
		return w == null ? DEFS.get(id).weight() : Math.max(0, w);
	}

	/** Weighted draw of an id not in {@code exclude}; null when the pool is empty. */
	public static String draw(IntUnaryOperator nextInt, Params p, Set<String> exclude) {
		List<String> ids = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (String id : DEFS.keySet()) {
			int w = exclude.contains(id) ? 0 : weightOf(id, p);
			if (w > 0) {
				ids.add(id);
				weights.add(w);
				total += w;
			}
		}
		if (total <= 0) {
			return null;
		}
		int r = nextInt.applyAsInt(total);
		for (int i = 0; i < ids.size(); i++) {
			r -= weights.get(i);
			if (r < 0) {
				return ids.get(i);
			}
		}
		return ids.get(ids.size() - 1);
	}

	/** A new day's contracts: {@code slots} distinct ids (fewer if the pool is smaller). */
	public static State generate(IntUnaryOperator nextInt, long day, int slots, Params p) {
		State s = new State(day, new ArrayList<>());
		topUp(nextInt, s, slots, p);
		return s;
	}

	/** Adds contracts until {@code slots} (VIP promotion mid-day). Returns the added ones. */
	public static List<Contract> topUp(IntUnaryOperator nextInt, State state, int slots, Params p) {
		List<Contract> added = new ArrayList<>();
		Set<String> used = new HashSet<>();
		state.list.forEach(c -> used.add(c.id));
		while (state.list.size() < slots) {
			String id = draw(nextInt, p, used);
			if (id == null) {
				break;
			}
			used.add(id);
			Contract c = make(id, p);
			state.list.add(c);
			added.add(c);
		}
		return added;
	}

	/** Needs a fresh set for {@code today}? */
	public static boolean isStale(State s, long today) {
		return s == null || s.day != today;
	}

	/**
	 * Adds progress to every open contract with this id. Returns the contracts completed by this
	 * call (reward each exactly once).
	 */
	public static List<Contract> addProgress(State state, String id, long amount) {
		List<Contract> done = new ArrayList<>();
		if (amount <= 0) {
			return done;
		}
		for (Contract c : state.list) {
			if (!c.id.equals(id) || c.done) {
				continue;
			}
			c.progress = Math.min(c.target, c.progress + amount);
			if (c.progress >= c.target) {
				c.done = true;
				done.add(c);
			}
		}
		return done;
	}

	public enum RerollError {
		NO_SLOT, DONE, REROLLED, POOL_EMPTY
	}

	/** Result of {@link #reroll}: exactly one of {@code contract} / {@code error} is non-null. */
	public record RerollResult(Contract contract, RerollError error) {
		public boolean ok() {
			return contract != null;
		}
	}

	/** Checks whether slot {@code index} may be rerolled (not done, not rerolled today). Null = ok. */
	public static RerollError canReroll(State state, int index) {
		if (index < 0 || index >= state.list.size()) {
			return RerollError.NO_SLOT;
		}
		Contract cur = state.list.get(index);
		if (cur.done) {
			return RerollError.DONE;
		}
		return cur.rerolled ? RerollError.REROLLED : null;
	}

	/** Rerolls slot {@code index} to a different id (§3.4.4). Mutates on success only. */
	public static RerollResult reroll(IntUnaryOperator nextInt, State state, int index, Params p) {
		RerollError err = canReroll(state, index);
		if (err != null) {
			return new RerollResult(null, err);
		}
		Set<String> used = new HashSet<>();
		state.list.forEach(c -> used.add(c.id));
		String id = draw(nextInt, p, used);
		if (id == null) {
			return new RerollResult(null, RerollError.POOL_EMPTY);
		}
		Contract c = make(id, p);
		c.rerolled = true;
		state.list.set(index, c);
		return new RerollResult(c, null);
	}

	// ---- event classification (registry paths in the minecraft namespace) ----------------------

	/** Contract advanced by mining this ore block (null if none). */
	public static String oreContract(String blockPath) {
		return switch (blockPath) {
			case "iron_ore", "deepslate_iron_ore" -> "mine_iron";
			case "coal_ore", "deepslate_coal_ore" -> "mine_coal";
			case "diamond_ore", "deepslate_diamond_ore" -> "mine_diamond";
			default -> null;
		};
	}

	/** Crops counted by {@code harvest} (§3.4.4). */
	public static boolean isHarvestCrop(String blockPath) {
		return switch (blockPath) {
			case "wheat", "carrots", "potatoes", "beetroots" -> true;
			default -> false;
		};
	}

	private static final Set<String> ZOMBIES = Set.of("zombie", "husk", "drowned", "zombie_villager");
	private static final Set<String> SKELETONS = Set.of("skeleton", "stray", "bogged", "parched");

	/** Contracts a kill advances; {@code hostile} = the mob is an {@code Enemy}. */
	public static List<String> killContracts(String entityPath, boolean hostile) {
		List<String> out = new ArrayList<>();
		if (ZOMBIES.contains(entityPath)) {
			out.add("kill_zombie");
		}
		if (SKELETONS.contains(entityPath)) {
			out.add("kill_skeleton");
		}
		if (entityPath.equals("creeper")) {
			out.add("kill_creeper");
		}
		if (hostile) {
			out.add("kill_any");
		}
		return out;
	}

	/**
	 * Contracts a settled wager advances (all wagers count for {@code wager}; blackjack wins, slot
	 * spins and poker hands by game id). {@code roulette_red} needs bet details that
	 * {@code PlayResult} does not carry, so it is never advanced here.
	 */
	public static Map<String, Long> playContracts(String gameId, long bet, long payout) {
		Map<String, Long> out = new LinkedHashMap<>();
		if (bet > 0) {
			out.put("wager", bet);
		}
		switch (gameId) {
			case "blackjack" -> {
				if (payout > bet) {
					out.put("win_blackjack", 1L);
				}
			}
			case "slots" -> out.put("spin_slots", 1L);
			case "poker" -> out.put("play_poker", 1L);
			default -> {
			}
		}
		return out;
	}

	/** Horizontal distance between two samples, ignoring jumps above {@code maxStep} (teleports, portals). */
	public static double travelStep(double ax, double az, double bx, double bz, double maxStep) {
		double d = Math.hypot(bx - ax, bz - az);
		return d > maxStep ? 0 : d;
	}
}
