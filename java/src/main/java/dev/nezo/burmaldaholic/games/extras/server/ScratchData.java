package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
 *
 * <p>B4 (owner playtest 1: cards jumped between slots): a card started since v0.2.0 is not split off its stack. The
 * whole card (id, kind, mask and face) is kept here and marked as the player's ACTIVE card of its kind
 * ({@code active}: {@code <uuid>/<kind>} → id) until the ninth cell; only then one fresh card leaves the stack.
 */
public final class ScratchData extends SavedData {
	public static final SavedDataType<ScratchData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("scratch_cards"), ScratchData::new, CompoundTag.CODEC.xmap(ScratchData::load, ScratchData::save), null);

	private final Map<String, CompoundTag> faces = new LinkedHashMap<>();
	/** {@code <player uuid>/<kind id>} → id of the player's in-progress card of that kind (B4). */
	private final Map<String, String> active = new LinkedHashMap<>();

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

	/** Forgets a card: its face and every player's pointer to it (the card may have changed hands, T-E2 #2). */
	public void remove(String id) {
		boolean changed = faces.remove(id) != null;
		changed |= active.values().removeIf(id::equals);
		if (changed) {
			setDirty();
		}
	}

	/** Id of the player's in-progress card of {@code kind}, or null. */
	public @Nullable String active(UUID player, String kind) {
		return active.get(player + "/" + kind);
	}

	public void setActive(UUID player, String kind, String id) {
		active.put(player + "/" + kind, id);
		setDirty();
	}

	public void clearActive(UUID player, String kind) {
		if (active.remove(player + "/" + kind) != null) {
			setDirty();
		}
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		CompoundTag all = new CompoundTag();
		faces.forEach(all::put);
		root.put("faces", all);
		CompoundTag act = new CompoundTag();
		active.forEach(act::putString);
		root.put("active", act);
		return root;
	}

	private static ScratchData load(CompoundTag root) {
		ScratchData data = new ScratchData();
		CompoundTag all = root.getCompoundOrEmpty("faces");
		for (String id : all.keySet()) {
			all.getCompound(id).ifPresent(t -> data.faces.put(id, t));
		}
		CompoundTag act = root.getCompoundOrEmpty("active");
		for (String key : act.keySet()) {
			act.getString(key).ifPresent(id -> data.active.put(key, id));
		}
		return data;
	}
}
