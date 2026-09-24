package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.LinkedHashSet;
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
 * {@code <world>/data/burmaldaholic/bots_ui.dat}: per-player UI preferences of the bots module — who muted
 * bot chatter (Casino Menu → Bots → Bot chatter, BOTS.md §7.4; default: hears it).
 */
public final class BotsData extends SavedData {
	public static final SavedDataType<BotsData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("bots_ui"), BotsData::new, CompoundTag.CODEC.xmap(BotsData::load, BotsData::save), null);

	private final Set<UUID> muted = new LinkedHashSet<>();

	public static BotsData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean muted(UUID player) {
		return muted.contains(player);
	}

	public void setMuted(UUID player, boolean mute) {
		if (mute ? muted.add(player) : muted.remove(player)) {
			setDirty();
		}
	}

	private static BotsData load(CompoundTag tag) {
		BotsData d = new BotsData();
		ListTag list = tag.getListOrEmpty("muted");
		for (Tag t : list) {
			t.asString().ifPresent(s -> {
				try {
					d.muted.add(UUID.fromString(s));
				} catch (IllegalArgumentException ignored) {
					// corrupt entry
				}
			});
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		muted.forEach(id -> list.add(StringTag.valueOf(id.toString())));
		tag.put("muted", list);
		tag.putInt("format", 1);
		return tag;
	}
}
