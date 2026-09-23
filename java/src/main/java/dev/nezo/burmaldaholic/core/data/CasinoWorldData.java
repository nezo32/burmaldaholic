package dev.nezo.burmaldaholic.core.data;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.economy.Ledger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Core world data ({@code <world>/data/burmaldaholic/core.dat}): chip ledger (balances, bankrolls),
 * per-player records (streak, first-join, trade cap, soul-wager cooldown), placed-debris ledger and
 * the first-dragon-kill flag. Server thread only. Get with {@link #get(MinecraftServer)}.
 */
public final class CasinoWorldData extends SavedData {
	public static final SavedDataType<CasinoWorldData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("core"), CasinoWorldData::new, CompoundTag.CODEC.xmap(CasinoWorldData::load, CasinoWorldData::save), null);

	private final Ledger ledger = new Ledger();
	private final Map<UUID, PlayerRecord> players = new HashMap<>();
	/** dimension id -> FIFO of packed block positions of player-placed ancient debris. */
	private final Map<String, Deque<Long>> placedDebris = new HashMap<>();
	private final Map<String, Set<Long>> placedDebrisIndex = new HashMap<>();
	private boolean dragonKilled;

	public static CasinoWorldData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Ledger ledger() {
		return ledger;
	}

	public PlayerRecord player(UUID id) {
		return players.computeIfAbsent(id, k -> new PlayerRecord());
	}

	public boolean dragonKilled() {
		return dragonKilled;
	}

	public void setDragonKilled() {
		dragonKilled = true;
		setDirty();
	}

	/** Records a player-placed ancient debris block; FIFO-capped at {@code cap}. */
	public void addPlacedDebris(String dimension, BlockPos pos, int cap) {
		if (cap <= 0) {
			return;
		}
		Deque<Long> queue = placedDebris.computeIfAbsent(dimension, k -> new ArrayDeque<>());
		Set<Long> index = placedDebrisIndex.computeIfAbsent(dimension, k -> new HashSet<>());
		long packed = pos.asLong();
		if (index.add(packed)) {
			queue.addLast(packed);
		}
		while (queue.size() > cap) {
			index.remove(queue.removeFirst());
		}
		setDirty();
	}

	/** True (and forgets it) if the position was player-placed debris. */
	public boolean consumePlacedDebris(String dimension, BlockPos pos) {
		Set<Long> index = placedDebrisIndex.get(dimension);
		long packed = pos.asLong();
		if (index == null || !index.remove(packed)) {
			return false;
		}
		placedDebris.get(dimension).remove(packed);
		setDirty();
		return true;
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		CompoundTag balances = new CompoundTag();
		ledger.balances().forEach((id, bal) -> balances.putLong(id.toString(), bal));
		root.put("balances", balances);
		CompoundTag bankrolls = new CompoundTag();
		ledger.bankrollMap().forEach((id, b) -> {
			var info = ledger.bankroll(id).orElseThrow();
			CompoundTag t = new CompoundTag();
			t.putString("owner", info.owner().toString());
			t.putLong("balance", info.balance());
			t.putLong("reserved", info.reserved());
			bankrolls.put(id, t);
		});
		root.put("bankrolls", bankrolls);
		CompoundTag playerTag = new CompoundTag();
		players.forEach((id, r) -> playerTag.put(id.toString(), r.save()));
		root.put("players", playerTag);
		CompoundTag debris = new CompoundTag();
		placedDebris.forEach((dim, q) -> debris.put(dim, new LongArrayTag(q.stream().mapToLong(Long::longValue).toArray())));
		root.put("placed_debris", debris);
		root.putBoolean("dragon_killed", dragonKilled);
		return root;
	}

	private static CasinoWorldData load(CompoundTag root) {
		CasinoWorldData data = new CasinoWorldData();
		CompoundTag balances = root.getCompoundOrEmpty("balances");
		for (String key : balances.keySet()) {
			parse(key).ifPresent(id -> data.ledger.balances().put(id, Math.max(0, balances.getLongOr(key, 0))));
		}
		CompoundTag bankrolls = root.getCompoundOrEmpty("bankrolls");
		for (String key : bankrolls.keySet()) {
			CompoundTag t = bankrolls.getCompoundOrEmpty(key);
			parse(t.getStringOr("owner", "")).ifPresent(owner ->
				data.ledger.loadBankroll(key, owner, t.getLongOr("balance", 0), t.getLongOr("reserved", 0)));
		}
		CompoundTag playerTag = root.getCompoundOrEmpty("players");
		for (String key : playerTag.keySet()) {
			parse(key).ifPresent(id -> data.players.put(id, PlayerRecord.load(playerTag.getCompoundOrEmpty(key))));
		}
		CompoundTag debris = root.getCompoundOrEmpty("placed_debris");
		for (String dim : debris.keySet()) {
			for (long packed : debris.getLongArray(dim).orElse(new long[0])) {
				data.placedDebris.computeIfAbsent(dim, k -> new ArrayDeque<>()).addLast(packed);
				data.placedDebrisIndex.computeIfAbsent(dim, k -> new HashSet<>()).add(packed);
			}
		}
		data.dragonKilled = root.getBooleanOr("dragon_killed", false);
		return data;
	}

	private static java.util.Optional<UUID> parse(String s) {
		try {
			return java.util.Optional.of(UUID.fromString(s));
		} catch (IllegalArgumentException e) {
			return java.util.Optional.empty();
		}
	}
}
