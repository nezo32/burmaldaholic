package dev.nezo.burmaldaholic.pvp;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.pvp.logic.PvpAchievementRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * pvp module world data ({@code data/burmaldaholic/pvp_ui.dat}): the rampage streak per player (wins in a row
 * with the distinct human opponents met, PVP.md §11) and PvP advancements waiting for an offline player.
 */
public final class PvpUiData extends SavedData {
	public static final SavedDataType<PvpUiData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("pvp_ui"), PvpUiData::new, CompoundTag.CODEC.xmap(PvpUiData::load, PvpUiData::save), null);

	private final Map<UUID, PvpAchievementRules.Streak> streaks = new HashMap<>();
	private final Map<UUID, Set<String>> pending = new HashMap<>();

	public static PvpUiData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public PvpAchievementRules.Streak streak(UUID player) {
		return streaks.getOrDefault(player, PvpAchievementRules.Streak.NONE);
	}

	public void setStreak(UUID player, PvpAchievementRules.Streak s) {
		if (s.wins() == 0) {
			if (streaks.remove(player) != null) {
				setDirty();
			}
			return;
		}
		streaks.put(player, s);
		setDirty();
	}

	public void queue(UUID player, String id) {
		if (pending.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(id)) {
			setDirty();
		}
	}

	public List<String> take(UUID player) {
		Set<String> s = pending.remove(player);
		if (s == null) {
			return List.of();
		}
		setDirty();
		return new ArrayList<>(s);
	}

	private static PvpUiData load(CompoundTag tag) {
		PvpUiData d = new PvpUiData();
		CompoundTag s = tag.getCompoundOrEmpty("streaks");
		for (String k : s.keySet()) {
			UUID id = uuid(k);
			if (id == null) {
				continue;
			}
			CompoundTag e = s.getCompoundOrEmpty(k);
			Set<String> opp = new LinkedHashSet<>();
			for (Tag t : e.getListOrEmpty("opponents")) {
				t.asString().ifPresent(opp::add);
			}
			d.streaks.put(id, new PvpAchievementRules.Streak(e.getIntOr("wins", 0), opp));
		}
		CompoundTag p = tag.getCompoundOrEmpty("pending");
		for (String k : p.keySet()) {
			UUID id = uuid(k);
			if (id == null) {
				continue;
			}
			Set<String> ids = new LinkedHashSet<>();
			for (Tag t : p.getListOrEmpty(k)) {
				t.asString().ifPresent(ids::add);
			}
			if (!ids.isEmpty()) {
				d.pending.put(id, ids);
			}
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag s = new CompoundTag();
		streaks.forEach((k, v) -> {
			CompoundTag e = new CompoundTag();
			e.putInt("wins", v.wins());
			ListTag opp = new ListTag();
			v.opponents().forEach(o -> opp.add(StringTag.valueOf(o)));
			e.put("opponents", opp);
			s.put(k.toString(), e);
		});
		tag.put("streaks", s);
		CompoundTag p = new CompoundTag();
		pending.forEach((k, v) -> {
			ListTag l = new ListTag();
			v.forEach(id -> l.add(StringTag.valueOf(id)));
			p.put(k.toString(), l);
		});
		tag.put("pending", p);
		tag.putInt("format", 1);
		return tag;
	}

	private static UUID uuid(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
