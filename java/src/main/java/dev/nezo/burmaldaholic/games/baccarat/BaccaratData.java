package dev.nezo.burmaldaholic.games.baccarat;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Baccarat world data ({@code data/burmaldaholic/baccarat.dat}): things owed to OFFLINE players and
 * delivered on their next join — baccarat advancements (core's advancement queue only knows the core
 * ids) and "your chemin de fer bank of N was returned" notices (§20.9). Chips are never kept here: they
 * are credited to the balance at once (offline-safe economy).
 */
public final class BaccaratData extends SavedData {
	public static final SavedDataType<BaccaratData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("baccarat"), BaccaratData::new, CompoundTag.CODEC.xmap(BaccaratData::load, BaccaratData::save), null);

	private final Map<UUID, List<String>> advancements = new HashMap<>();
	private final Map<UUID, Long> bankReturned = new HashMap<>();

	public static BaccaratData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public void queueAdvancement(UUID player, String id) {
		List<String> list = advancements.computeIfAbsent(player, k -> new ArrayList<>());
		if (!list.contains(id)) {
			list.add(id);
			setDirty();
		}
	}

	public void queueBankReturned(UUID player, long amount) {
		bankReturned.merge(player, amount, Long::sum);
		setDirty();
	}

	public List<String> takeAdvancements(UUID player) {
		List<String> list = advancements.remove(player);
		if (list != null) {
			setDirty();
		}
		return list == null ? List.of() : list;
	}

	public long takeBankReturned(UUID player) {
		Long v = bankReturned.remove(player);
		if (v != null) {
			setDirty();
		}
		return v == null ? 0 : v;
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		CompoundTag adv = new CompoundTag();
		advancements.forEach((id, list) -> {
			ListTag l = new ListTag();
			list.forEach(s -> l.add(StringTag.valueOf(s)));
			adv.put(id.toString(), l);
		});
		root.put("advancements", adv);
		CompoundTag banks = new CompoundTag();
		bankReturned.forEach((id, v) -> banks.putLong(id.toString(), v));
		root.put("bank_returned", banks);
		return root;
	}

	private static BaccaratData load(CompoundTag root) {
		BaccaratData data = new BaccaratData();
		CompoundTag adv = root.getCompoundOrEmpty("advancements");
		for (String key : adv.keySet()) {
			UUID id = parse(key);
			if (id == null) {
				continue;
			}
			List<String> list = new ArrayList<>();
			for (Tag t : adv.getListOrEmpty(key)) {
				t.asString().ifPresent(list::add);
			}
			data.advancements.put(id, list);
		}
		CompoundTag banks = root.getCompoundOrEmpty("bank_returned");
		for (String key : banks.keySet()) {
			UUID id = parse(key);
			if (id != null) {
				data.bankReturned.put(id, banks.getLongOr(key, 0));
			}
		}
		return data;
	}

	private static UUID parse(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
