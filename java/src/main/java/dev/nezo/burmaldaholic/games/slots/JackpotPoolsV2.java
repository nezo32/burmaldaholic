package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.games.slots.logic.JackpotPool;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Jackpots;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Slots v2 world data {@code burmaldaholic_slots} (SLOTS.md §5.1, §8.8): four progressive pools per machine type
 * ({@code seed + increment}, the increment and the hidden contribution remainder are stored; the seed comes from the
 * config), the one-time v1 pool migration flag (§5.3) and per-player statistics. Pending (drawn, not yet revealed)
 * awards are kept in memory so other players' meters show {@code pool + pending} until the reveal (§5.2). Server
 * thread only.
 */
public final class JackpotPoolsV2 extends SavedData {
	public static final SavedDataType<JackpotPoolsV2> TYPE = new SavedDataType<>(
		Burmaldaholic.id("slots"), JackpotPoolsV2::new, CompoundTag.CODEC.xmap(JackpotPoolsV2::load, JackpotPoolsV2::save), null);

	private final Map<Machine, long[]> increments = new EnumMap<>(Machine.class);
	private final Map<Machine, long[]> remainders = new EnumMap<>(Machine.class);
	private final Map<Machine, long[]> pending = new EnumMap<>(Machine.class);
	private final Map<UUID, Map<Machine, long[]>> stats = new HashMap<>();
	private boolean migrated;

	/** Statistic slots: spins, wagered, returned, features, best (fifths), jackpots Mini…Grand. */
	public static final int ST_SPINS = 0, ST_WAGERED = 1, ST_RETURNED = 2, ST_FEATURES = 3, ST_BEST = 4, ST_JACKPOTS = 5, ST_LEN = 9;

	public static JackpotPoolsV2 get(MinecraftServer server) {
		JackpotPoolsV2 d = server.getDataStorage().computeIfAbsent(TYPE);
		if (!d.migrated) d.migrate(server);
		return d;
	}

	private long[] inc(Machine m) {
		return increments.computeIfAbsent(m, k -> new long[5]);
	}

	/** Pool values ({@code seed + increment}) per tier 1..4, index 0 unused. */
	public long[] pools(Machine m, MachineDef def) {
		long[] out = new long[5];
		long[] inc = inc(m);
		for (int t = 1; t <= 4; t++) out[t] = def.features().seedChips(t) + inc[t];
		return out;
	}

	/** What other players' meters show: pool + awards drawn but not revealed yet. */
	public long meter(Machine m, MachineDef def, int tier) {
		long[] p = pending.get(m);
		return pools(m, def)[tier] + (p == null ? 0 : p[tier]);
	}

	/** Every paid spin and buy adds {@code contribution × stake} to each tier (fractions accumulate). */
	public void contribute(Machine m, MachineDef def, long stake) {
		long[] inc = inc(m);
		long[] rem = remainders.computeIfAbsent(m, k -> new long[5]);
		for (int t = 1; t <= 4; t++) {
			long[] r = Jackpots.contribute(rem[t], stake, def.features().contributionPpm()[t - 1]);
			inc[t] += r[0];
			rem[t] = r[1];
		}
		setDirty();
	}

	/** Debits the tape's progressive awards at draw time (the round is persisted in the same tick). */
	public void applyDraw(Machine m, MachineDef def, SpinTape tape) {
		long[] pools = pools(m, def);
		SlotDraw.applyAwards(def, tape, pools);
		long[] inc = inc(m);
		long[] p = pending.computeIfAbsent(m, k -> new long[5]);
		for (int t = 1; t <= 4; t++) inc[t] = Math.max(0, pools[t] - def.features().seedChips(t));
		for (SpinTape.JackpotAward j : tape.jackpots()) if (!j.owned()) p[j.tier()] += j.chips();
		setDirty();
	}

	/** The winner's reveal played (or the round settled): the meters may drop now. */
	public void revealed(Machine m, SpinTape tape) {
		long[] p = pending.get(m);
		if (p == null) return;
		for (SpinTape.JackpotAward j : tape.jackpots()) if (!j.owned()) p[j.tier()] = Math.max(0, p[j.tier()] - j.chips());
	}

	/** Admin "Reset jackpot": a machine's four pools back to their seeds (increments leave the economy; logged). */
	public void reset(Machine m) {
		Burmaldaholic.LOGGER.info("slots.{}: jackpot pools reset to seed (increments removed: {})", m.id, java.util.Arrays.toString(inc(m)));
		increments.put(m, new long[5]);
		remainders.put(m, new long[5]);
		setDirty();
	}

	/** Statistics of a settled spin (SLOTS.md §8.8). */
	public void record(UUID player, Machine m, long stake, long returned, boolean feature, long totalFifths, SpinTape tape) {
		long[] s = stats.computeIfAbsent(player, k -> new EnumMap<>(Machine.class)).computeIfAbsent(m, k -> new long[ST_LEN]);
		s[ST_SPINS]++;
		s[ST_WAGERED] += stake;
		s[ST_RETURNED] += returned;
		if (feature) s[ST_FEATURES]++;
		s[ST_BEST] = Math.max(s[ST_BEST], totalFifths);
		for (SpinTape.JackpotAward j : tape.jackpots()) s[ST_JACKPOTS + j.tier() - 1]++;
		setDirty();
	}

	public long[] stats(UUID player, Machine m) {
		Map<Machine, long[]> byM = stats.get(player);
		long[] s = byM == null ? null : byM.get(m);
		return s == null ? new long[ST_LEN] : s.clone();
	}

	/**
	 * SLOTS.md §5.3, once per world: the v1 Golden Reels pool's increment (pool − old seed) goes to the Nether Grand
	 * increment, the v1 Netherite pool's increment to the End Grand; old seeds are dropped (bank money) and the v1 pools
	 * are reset to their seeds so the money exists only once.
	 */
	private void migrate(MinecraftServer server) {
		migrated = true;
		setDirty();
		JackpotData v1 = JackpotData.get(server);
		long gold = 0;
		long netherite = 0;
		for (Tier t : new Tier[] {Tier.GOLD, Tier.NETHERITE}) {
			JackpotPool p = v1.pool(t);
			long extra = Math.max(0, p.pool() - JackpotData.v1Seed(t));
			if (t == Tier.GOLD) gold = extra;
			else netherite = extra;
			v1.set(t, JackpotPool.seeded(JackpotData.v1Seed(t)));
		}
		inc(Machine.NETHER)[Jackpots.GRAND] += gold;
		inc(Machine.END)[Jackpots.GRAND] += netherite;
		Burmaldaholic.LOGGER.info("Slot jackpots moved to the v2 machines: Nether Grand +{}, End Grand +{} (SLOTS.md §5.3)", gold, netherite);
	}

	/** True once the §5.3 migration ran in this world. */
	public boolean migrated() {
		return migrated;
	}

	private static JackpotPoolsV2 load(CompoundTag tag) {
		JackpotPoolsV2 d = new JackpotPoolsV2();
		d.migrated = tag.getBooleanOr("migrated", false);
		CompoundTag pools = tag.getCompoundOrEmpty("pools");
		for (Machine m : Machine.values()) {
			CompoundTag mt = pools.getCompoundOrEmpty(m.id);
			long[] inc = new long[5];
			long[] rem = new long[5];
			for (int t = 1; t <= 4; t++) {
				inc[t] = Math.max(0, mt.getLongOr("inc" + t, 0));
				rem[t] = Math.max(0, mt.getLongOr("rem" + t, 0));
			}
			d.increments.put(m, inc);
			d.remainders.put(m, rem);
		}
		CompoundTag st = tag.getCompoundOrEmpty("stats");
		for (String key : st.keySet()) {
			try {
				UUID id = UUID.fromString(key);
				CompoundTag pt = st.getCompoundOrEmpty(key);
				Map<Machine, long[]> byM = new EnumMap<>(Machine.class);
				for (Machine m : Machine.values()) {
					long[] a = pt.getLongArray(m.id).orElse(null);
					if (a != null && a.length == ST_LEN) byM.put(m, a);
				}
				d.stats.put(id, byM);
			} catch (IllegalArgumentException ignored) {
				// skip a corrupt entry
			}
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("migrated", migrated);
		CompoundTag pools = new CompoundTag();
		for (Machine m : Machine.values()) {
			CompoundTag mt = new CompoundTag();
			long[] inc = inc(m);
			long[] rem = remainders.computeIfAbsent(m, k -> new long[5]);
			for (int t = 1; t <= 4; t++) {
				mt.putLong("inc" + t, inc[t]);
				mt.putLong("rem" + t, rem[t]);
			}
			pools.put(m.id, mt);
		}
		tag.put("pools", pools);
		CompoundTag st = new CompoundTag();
		stats.forEach((id, byM) -> {
			CompoundTag pt = new CompoundTag();
			byM.forEach((m, a) -> pt.putLongArray(m.id, a));
			st.put(id.toString(), pt);
		});
		tag.put("stats", st);
		return tag;
	}
}
