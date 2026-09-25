package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.chaos.logic.GoldenHourState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Chaos world data ({@code <world>/data/burmaldaholic/chaos.dat}): Golden Hour state, per-player
 * last-event tick (cooldown survives relogs/restarts) and the last sunset-roll day. Server thread only.
 */
public final class ChaosData extends SavedData {
	public static final SavedDataType<ChaosData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("chaos"), ChaosData::new, CompoundTag.CODEC.xmap(ChaosData::load, ChaosData::save), null);

	final GoldenHourState goldenHour = new GoldenHourState();
	final Map<UUID, Long> lastEvent = new HashMap<>();
	long lastSunsetDay = -1;

	public static ChaosData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public GoldenHourState goldenHour() {
		return goldenHour;
	}

	private static ChaosData load(CompoundTag tag) {
		ChaosData d = new ChaosData();
		GoldenHourState gh = d.goldenHour;
		CompoundTag g = tag.getCompoundOrEmpty("golden_hour");
		gh.id = g.getIntOr("id", 0);
		gh.start = g.getLongOr("start", -1);
		gh.end = g.getLongOr("end", -1);
		gh.nextAllowed = g.getLongOr("next_allowed", 0);
		gh.warned = g.getBooleanOr("warned", false);
		gh.ended = g.getBooleanOr("ended", true);
		CompoundTag paid = g.getCompoundOrEmpty("paid");
		for (String k : paid.keySet()) {
			parse(k, id -> gh.paid.put(id, paid.getLongOr(k, 0)));
		}
		CompoundTag last = tag.getCompoundOrEmpty("last_event");
		for (String k : last.keySet()) {
			parse(k, id -> d.lastEvent.put(id, last.getLongOr(k, 0)));
		}
		d.lastSunsetDay = tag.getLongOr("last_sunset_day", -1);
		return d;
	}

	private static void parse(String key, java.util.function.Consumer<UUID> then) {
		try {
			then.accept(UUID.fromString(key));
		} catch (IllegalArgumentException ignored) {
			// corrupt entry: drop it
		}
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		GoldenHourState gh = goldenHour;
		CompoundTag g = new CompoundTag();
		g.putInt("id", gh.id);
		g.putLong("start", gh.start);
		g.putLong("end", gh.end);
		g.putLong("next_allowed", gh.nextAllowed);
		g.putBoolean("warned", gh.warned);
		g.putBoolean("ended", gh.ended);
		CompoundTag paid = new CompoundTag();
		gh.paid.forEach((id, n) -> paid.putLong(id.toString(), n));
		g.put("paid", paid);
		tag.put("golden_hour", g);
		CompoundTag last = new CompoundTag();
		lastEvent.forEach((id, t) -> last.putLong(id.toString(), t));
		tag.put("last_event", last);
		tag.putLong("last_sunset_day", lastSunsetDay);
		return tag;
	}
}
