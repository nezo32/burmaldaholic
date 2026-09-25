package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World data {@code <world>/data/burmaldaholic/scratch_cards.dat}: the hidden face of every started scratch card
 * (cells, prize, creeper, top), keyed by the card id (review m1). The item itself only carries {@code id},
 * {@code kind} and the revealed {@code mask}: item components are synced to clients, so the face must never be
 * stored on the stack. An entry is removed when its card is finished.
 */
public final class ScratchData extends SavedData {
	public static final SavedDataType<ScratchData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("scratch_cards"), ScratchData::new, CompoundTag.CODEC.xmap(ScratchData::load, ScratchData::save), null);

	private final Map<String, CompoundTag> faces = new LinkedHashMap<>();

	public static ScratchData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable CompoundTag face(String id) {
		CompoundTag t = faces.get(id);
		return t == null ? null : t.copy();
	}

	public void put(String id, CompoundTag face) {
		faces.put(id, face.copy());
		setDirty();
	}

	public void remove(String id) {
		if (faces.remove(id) != null) {
			setDirty();
		}
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		CompoundTag all = new CompoundTag();
		faces.forEach(all::put);
		root.put("faces", all);
		return root;
	}

	private static ScratchData load(CompoundTag root) {
		ScratchData data = new ScratchData();
		CompoundTag all = root.getCompoundOrEmpty("faces");
		for (String id : all.keySet()) {
			all.getCompound(id).ifPresent(t -> data.faces.put(id, t));
		}
		return data;
	}
}
