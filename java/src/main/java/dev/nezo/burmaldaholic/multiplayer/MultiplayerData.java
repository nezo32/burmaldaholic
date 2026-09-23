package dev.nezo.burmaldaholic.multiplayer;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World data {@code <world>/data/burmaldaholic/multiplayer.dat}: casinos, linked tables, statistics,
 * bankrolls waiting to be paid out and "charter removed" notices for offline owners. The bankroll chips
 * themselves live in core's ledger ({@code Economy.bankrolls()}).
 */
public final class MultiplayerData extends SavedData {
	public static final SavedDataType<MultiplayerData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("multiplayer"), MultiplayerData::new, CompoundTag.CODEC.xmap(MultiplayerData::load, MultiplayerData::save), null);

	private final CasinoBook book = new CasinoBook();
	/** Owner → chips returned from a removed charter while they were offline (message on join). */
	private final Map<UUID, Long> notices = new HashMap<>();

	public static MultiplayerData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public CasinoBook book() {
		return book;
	}

	public void addNotice(UUID owner, long amount) {
		notices.merge(owner, amount, Long::sum);
		setDirty();
	}

	/** Returns and forgets a pending notice (-1 = none). */
	public long takeNotice(UUID owner) {
		Long v = notices.remove(owner);
		if (v != null) {
			setDirty();
		}
		return v == null ? -1 : v;
	}

	// ---- persistence --------------------------------------------------------------------------

	private static CompoundTag tally(CasinoStats.Tally t) {
		CompoundTag tag = new CompoundTag();
		tag.putLong("handle", t.handle);
		tag.putLong("paid", t.paid);
		tag.putLong("rake", t.rake);
		tag.putLong("rounds", t.rounds);
		return tag;
	}

	private static CasinoStats.Tally tally(CompoundTag tag) {
		CasinoStats.Tally t = new CasinoStats.Tally();
		t.handle = tag.getLongOr("handle", 0);
		t.paid = tag.getLongOr("paid", 0);
		t.rake = tag.getLongOr("rake", 0);
		t.rounds = tag.getLongOr("rounds", 0);
		return t;
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		root.putInt("counter", book.counter());
		ListTag casinos = new ListTag();
		for (CasinoBook.Casino c : book.casinos()) {
			CompoundTag t = new CompoundTag();
			t.putString("id", c.id);
			t.putString("owner", c.owner.toString());
			t.putString("owner_name", c.ownerName);
			t.putString("dimension", c.dimension);
			t.putInt("x", c.x);
			t.putInt("y", c.y);
			t.putInt("z", c.z);
			t.putInt("radius", c.radius);
			t.putLong("created", c.created);
			t.putBoolean("broke", c.broke);
			t.putLong("day", c.stats.day());
			t.put("today", tally(c.stats.today()));
			t.put("total", tally(c.stats.total()));
			casinos.add(t);
		}
		root.put("casinos", casinos);
		CompoundTag tables = new CompoundTag();
		book.tables().forEach((key, r) -> {
			CompoundTag t = new CompoundTag();
			if (r.casinoId != null) {
				t.putString("casino", r.casinoId);
			}
			t.putString("owner", r.owner.toString());
			t.putString("game", r.game);
			t.putString("table", r.tableName);
			t.putString("desc", r.descriptionId);
			t.putBoolean("open", r.open);
			t.putLong("min", r.min);
			t.putLong("max", r.max);
			t.putBoolean("bots", r.bots);
			t.putLong("game_min", r.gameMin);
			tables.put(key, t);
		});
		root.put("tables", tables);
		CompoundTag closing = new CompoundTag();
		book.closing().forEach((id, owner) -> closing.putString(id, owner.toString()));
		root.put("closing", closing);
		CompoundTag notes = new CompoundTag();
		notices.forEach((id, amount) -> notes.putLong(id.toString(), amount));
		root.put("notices", notes);
		return root;
	}

	private static UUID uuid(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static MultiplayerData load(CompoundTag root) {
		MultiplayerData data = new MultiplayerData();
		CasinoBook book = data.book;
		book.restoreCounter(root.getIntOr("counter", 0));
		ListTag casinos = root.getListOrEmpty("casinos");
		for (int i = 0; i < casinos.size(); i++) {
			CompoundTag t = casinos.getCompoundOrEmpty(i);
			UUID owner = uuid(t.getStringOr("owner", ""));
			String id = t.getStringOr("id", "");
			if (owner == null || id.isEmpty()) {
				continue;
			}
			CasinoBook.Casino c = new CasinoBook.Casino(id, owner, t.getStringOr("owner_name", ""), t.getStringOr("dimension", "minecraft:overworld"),
				t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0), t.getIntOr("radius", 24), t.getLongOr("created", 0));
			c.broke = t.getBooleanOr("broke", false);
			c.stats.load(t.getLongOr("day", 0), tally(t.getCompoundOrEmpty("today")), tally(t.getCompoundOrEmpty("total")));
			book.restore(c);
		}
		CompoundTag tables = root.getCompoundOrEmpty("tables");
		for (String key : tables.keySet()) {
			CompoundTag t = tables.getCompoundOrEmpty(key);
			UUID owner = uuid(t.getStringOr("owner", ""));
			if (owner == null) {
				continue;
			}
			String casino = t.getStringOr("casino", "");
			CasinoBook.TableRecord r = new CasinoBook.TableRecord(casino.isEmpty() ? null : casino, owner,
				t.getStringOr("game", ""), t.getStringOr("table", ""), t.getStringOr("desc", ""));
			r.open = t.getBooleanOr("open", true);
			r.min = Math.max(0, t.getLongOr("min", 0));
			r.max = Math.max(0, t.getLongOr("max", 0));
			r.bots = t.getBooleanOr("bots", true);
			r.gameMin = Math.max(1, t.getLongOr("game_min", 1));
			book.restore(key, r);
		}
		CompoundTag closing = root.getCompoundOrEmpty("closing");
		for (String id : closing.keySet()) {
			UUID owner = uuid(closing.getStringOr(id, ""));
			if (owner != null) {
				book.closing().put(id, owner);
			}
		}
		CompoundTag notes = root.getCompoundOrEmpty("notices");
		for (String id : notes.keySet()) {
			UUID owner = uuid(id);
			if (owner != null) {
				data.notices.put(owner, notes.getLongOr(id, 0));
			}
		}
		return data;
	}
}
