package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.games.slots.logic.JackpotPool;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World-persistent progressive jackpot pools, one per progressive tier (GAME_DESIGN.md §8.5), in
 * {@code <world>/data/burmaldaholic/slots_jackpot.dat}. Missing/corrupt pools start at the seed
 * ({@code slots.jackpot.seed.<tier>}, minted by the bank). Server thread only.
 */
public final class JackpotData extends SavedData {
	public static final SavedDataType<JackpotData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("slots_jackpot"), JackpotData::new, CompoundTag.CODEC.xmap(JackpotData::load, JackpotData::save), null);

	private final Map<Tier, JackpotPool> pools = new EnumMap<>(Tier.class);

	/**
	 * The v1 pool seeds (the retired {@code slots.jackpot.seed.gold / .netherite} defaults): what the §5.3 migration
	 * treats as bank money when it moves a v1 pool's increment to the v2 Grand.
	 */
	public static long v1Seed(Tier tier) {
		return switch (tier) {
			case GOLD -> 5_000;
			case NETHERITE -> 50_000;
			default -> 0;
		};
	}

	public static JackpotData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Current pool state of a progressive tier (created at the seed on first use). */
	public JackpotPool pool(Tier tier) {
		return pools.computeIfAbsent(tier, t -> {
			setDirty();
			return JackpotPool.seeded(v1Seed(t));
		});
	}

	public void set(Tier tier, JackpotPool state) {
		pools.put(tier, state);
		setDirty();
	}

	/** Pool in chips (0 for non-progressive or unknown tiers). */
	public static long pool(MinecraftServer server, String tierId) {
		Tier tier = Tier.byId(tierId);
		return tier == null || !tier.progressive() ? 0 : get(server).pool(tier).pool();
	}

	/** Resets one (or every, with null) progressive pool to its seed. */
	public static void reset(MinecraftServer server, @Nullable String tierId) {
		JackpotData data = get(server);
		for (Tier t : Tier.values()) {
			if (t.progressive() && (tierId == null || t.id().equals(tierId))) {
				data.set(t, JackpotPool.seeded(v1Seed(t)));
			}
		}
	}

	private static JackpotData load(CompoundTag tag) {
		JackpotData data = new JackpotData();
		for (Tier t : Tier.values()) {
			if (!t.progressive() || !tag.contains(t.id())) {
				continue;
			}
			CompoundTag p = tag.getCompoundOrEmpty(t.id());
			long pool = p.getLongOr("pool", -1);
			if (pool >= 0) {
				data.pools.put(t, new JackpotPool(pool, p.getLongOr("rem_micros", 0)));
			}
		}
		return data;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		pools.forEach((t, s) -> {
			CompoundTag p = new CompoundTag();
			p.putLong("pool", s.pool());
			p.putLong("rem_micros", s.remMicros());
			tag.put(t.id(), p);
		});
		return tag;
	}
}
