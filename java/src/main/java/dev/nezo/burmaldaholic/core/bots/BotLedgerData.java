package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * {@code <world>/data/burmaldaholic/bots.dat} (pvp-bots.md §5.4): per player {day, net} of winnings from
 * house-funded bots; per table key {day, buyIns}. Old days are overwritten lazily.
 */
public final class BotLedgerData extends SavedData {
	public static final SavedDataType<BotLedgerData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("bots"), BotLedgerData::new, CompoundTag.CODEC.xmap(BotLedgerData::load, BotLedgerData::save), null);

	private record DayCount(long day, long value) {}

	private final Map<UUID, DayCount> net = new HashMap<>();
	private final Map<String, DayCount> buyIns = new HashMap<>();

	public static BotLedgerData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public long netToday(UUID player, long day) {
		DayCount d = net.get(player);
		return d == null || d.day() != day ? 0 : d.value();
	}

	public void add(UUID player, long day, long delta) {
		net.put(player, new DayCount(day, netToday(player, day) + delta));
		setDirty();
	}

	public long buyInsToday(String tableKey, long day) {
		DayCount d = buyIns.get(tableKey);
		return d == null || d.day() != day ? 0 : d.value();
	}

	public void countBuyIn(String tableKey, long day) {
		buyIns.put(tableKey, new DayCount(day, buyInsToday(tableKey, day) + 1));
		setDirty();
	}

	private static BotLedgerData load(CompoundTag tag) {
		BotLedgerData d = new BotLedgerData();
		CompoundTag n = tag.getCompoundOrEmpty("net");
		for (String k : n.keySet()) {
			try {
				CompoundTag e = n.getCompoundOrEmpty(k);
				d.net.put(UUID.fromString(k), new DayCount(e.getLongOr("day", 0), e.getLongOr("value", 0)));
			} catch (IllegalArgumentException ignored) {
				// corrupt key
			}
		}
		CompoundTag b = tag.getCompoundOrEmpty("buyIns");
		for (String k : b.keySet()) {
			CompoundTag e = b.getCompoundOrEmpty(k);
			d.buyIns.put(k, new DayCount(e.getLongOr("day", 0), e.getLongOr("value", 0)));
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag n = new CompoundTag();
		net.forEach((k, v) -> n.put(k.toString(), entry(v)));
		CompoundTag b = new CompoundTag();
		buyIns.forEach((k, v) -> b.put(k, entry(v)));
		tag.put("net", n);
		tag.put("buyIns", b);
		tag.putInt("format", 1);
		return tag;
	}

	private static CompoundTag entry(DayCount d) {
		CompoundTag e = new CompoundTag();
		e.putLong("day", d.day());
		e.putLong("value", d.value());
		return e;
	}
}
