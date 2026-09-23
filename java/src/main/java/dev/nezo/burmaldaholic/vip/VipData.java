package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.vip.logic.CashbackRules;
import dev.nezo.burmaldaholic.vip.logic.ContractRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * VIP world data ({@code <world>/data/burmaldaholic/vip.dat}): per player lifetime wagered, the
 * highest tier already announced, today's cashback ledger and today's contracts. Works for offline
 * players (keyed by UUID). Server thread only.
 */
public final class VipData extends SavedData {
	public static final SavedDataType<VipData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("vip"), VipData::new, CompoundTag.CODEC.xmap(VipData::load, VipData::save), null);

	/** Mutable per-player VIP record. */
	public static final class Record {
		/** Lifetime chips wagered W (never decreases). */
		public long wagered;
		/** Highest tier granted/announced (tiers are never lost). */
		public int tier;
		public CashbackRules.DayLedger ledger;
		public ContractRules.State contracts;
	}

	private final Map<UUID, Record> players = new HashMap<>();

	public static VipData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Record player(UUID id) {
		return players.computeIfAbsent(id, k -> new Record());
	}

	public Record peek(UUID id) {
		return players.get(id);
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		CompoundTag tag = new CompoundTag();
		players.forEach((id, r) -> tag.put(id.toString(), saveRecord(r)));
		root.put("players", tag);
		return root;
	}

	private static CompoundTag saveRecord(Record r) {
		CompoundTag t = new CompoundTag();
		t.putLong("wagered", r.wagered);
		t.putInt("tier", r.tier);
		if (r.ledger != null) {
			CompoundTag l = new CompoundTag();
			l.putLong("day", r.ledger.day());
			l.putLong("staked", r.ledger.staked());
			l.putLong("returned", r.ledger.returned());
			l.putDouble("theo", r.ledger.theo());
			t.put("ledger", l);
		}
		if (r.contracts != null) {
			CompoundTag c = new CompoundTag();
			c.putLong("day", r.contracts.day);
			ListTag list = new ListTag();
			for (ContractRules.Contract k : r.contracts.list) {
				CompoundTag e = new CompoundTag();
				e.putString("id", k.id);
				e.putLong("target", k.target);
				e.putLong("reward", k.reward);
				e.putLong("progress", k.progress);
				e.putBoolean("done", k.done);
				e.putBoolean("rerolled", k.rerolled);
				list.add(e);
			}
			c.put("list", list);
			t.put("contracts", c);
		}
		return t;
	}

	private static VipData load(CompoundTag root) {
		VipData data = new VipData();
		CompoundTag tag = root.getCompoundOrEmpty("players");
		for (String key : tag.keySet()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			data.players.put(id, loadRecord(tag.getCompoundOrEmpty(key)));
		}
		return data;
	}

	private static Record loadRecord(CompoundTag t) {
		Record r = new Record();
		r.wagered = Math.max(0, t.getLongOr("wagered", 0));
		r.tier = Math.max(0, Math.min(5, t.getIntOr("tier", 0)));
		if (t.contains("ledger")) {
			CompoundTag l = t.getCompoundOrEmpty("ledger");
			r.ledger = new CashbackRules.DayLedger(l.getLongOr("day", 0), l.getLongOr("staked", 0), l.getLongOr("returned", 0),
				Math.max(0, l.getDoubleOr("theo", 0)));
		}
		if (t.contains("contracts")) {
			CompoundTag c = t.getCompoundOrEmpty("contracts");
			List<ContractRules.Contract> list = new ArrayList<>();
			ListTag entries = c.getListOrEmpty("list");
			for (int i = 0; i < entries.size(); i++) {
				CompoundTag e = entries.getCompoundOrEmpty(i);
				String id = e.getStringOr("id", "");
				if (!ContractRules.isId(id)) {
					continue;
				}
				list.add(new ContractRules.Contract(id, Math.max(1, e.getLongOr("target", 1)), Math.max(0, e.getLongOr("reward", 0)),
					Math.max(0, e.getLongOr("progress", 0)), e.getBooleanOr("done", false), e.getBooleanOr("rerolled", false)));
			}
			r.contracts = new ContractRules.State(c.getLongOr("day", -1), list);
		}
		return r;
	}
}
