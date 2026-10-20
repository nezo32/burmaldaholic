package dev.nezo.burmaldaholic.comps;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.wager.FreeBetTokens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Comps world data ({@code data/burmaldaholic/comps.dat}, EC-SPEC §5): the salted IP hashes that claimed today's
 * daily bonus (cleared each day) and free-bet tokens returned to players who were offline at the push / refund.
 */
public final class CompsWorldData extends SavedData {
	public static final SavedDataType<CompsWorldData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("comps"), CompsWorldData::new, CompoundTag.CODEC.xmap(CompsWorldData::load, CompsWorldData::save), null);

	long day = -1;
	final Set<String> ipHashes = new HashSet<>();
	/** Random per world: the IP hash salt (never the raw address on disk). */
	String salt = UUID.randomUUID().toString();
	final Map<UUID, List<FreeBetTokens.Held>> pendingTokens = new HashMap<>();

	public static CompsWorldData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** True if this IP already claimed on {@code today}; rolls the set on a new day. */
	boolean ipUsed(long today, String ip) {
		roll(today);
		return ip != null && !ip.isEmpty() && ipHashes.contains(hash(ip));
	}

	void markIp(long today, String ip) {
		roll(today);
		if (ip != null && !ip.isEmpty()) {
			ipHashes.add(hash(ip));
			setDirty();
		}
	}

	private void roll(long today) {
		if (day != today) {
			day = today;
			ipHashes.clear();
			setDirty();
		}
	}

	private String hash(String ip) {
		try {
			var md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] d = md.digest((salt + "|" + ip).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(d, 0, 16);
		} catch (java.security.NoSuchAlgorithmException e) {
			return Integer.toHexString((salt + ip).hashCode());
		}
	}

	void queueToken(UUID player, FreeBetTokens.Held token) {
		pendingTokens.computeIfAbsent(player, k -> new ArrayList<>()).add(token);
		setDirty();
	}

	List<FreeBetTokens.Held> drainTokens(UUID player) {
		List<FreeBetTokens.Held> list = pendingTokens.remove(player);
		if (list == null) {
			return List.of();
		}
		setDirty();
		return list;
	}

	private static CompsWorldData load(CompoundTag tag) {
		CompsWorldData d = new CompsWorldData();
		d.day = tag.getLongOr("day", -1);
		d.salt = tag.getStringOr("salt", d.salt);
		tag.getListOrEmpty("ips").forEach(t -> t.asString().ifPresent(d.ipHashes::add));
		CompoundTag pending = tag.getCompoundOrEmpty("pending_tokens");
		for (String k : pending.keySet()) {
			try {
				UUID id = UUID.fromString(k);
				List<FreeBetTokens.Held> list = new ArrayList<>();
				pending.getListOrEmpty(k).forEach(t -> t.asCompound().ifPresent(c ->
					list.add(new FreeBetTokens.Held(c.getLongOr("value", 0), c.getLongOr("expires", 0)))));
				if (!list.isEmpty()) {
					d.pendingTokens.put(id, list);
				}
			} catch (IllegalArgumentException ignored) {
				// corrupt entry: drop it
			}
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("day", day);
		tag.putString("salt", salt);
		ListTag ips = new ListTag();
		ipHashes.forEach(h -> ips.add(StringTag.valueOf(h)));
		tag.put("ips", ips);
		CompoundTag pending = new CompoundTag();
		pendingTokens.forEach((id, list) -> {
			ListTag l = new ListTag();
			for (FreeBetTokens.Held h : list) {
				CompoundTag c = new CompoundTag();
				c.putLong("value", h.value());
				c.putLong("expires", h.expires());
				l.add(c);
			}
			pending.put(id.toString(), l);
		});
		tag.put("pending_tokens", pending);
		return tag;
	}
}
