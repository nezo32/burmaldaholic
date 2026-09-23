package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Loan world data ({@code <world>/data/burmaldaholic/loan.dat}): one {@link LoanRecord} per player
 * (debt is world-bound: survives death, relog, dimension and difficulty changes, §5.8.3), the last
 * tick loans were active (dormancy shift) and pending Loan Shark respawns. Server thread only.
 */
public final class LoanData extends SavedData {
	public static final SavedDataType<LoanData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("loan"), LoanData::new, CompoundTag.CODEC.xmap(LoanData::load, LoanData::save), null);

	private final Map<UUID, LoanRecord> records = new HashMap<>();
	private final List<Respawn> respawns = new ArrayList<>();
	/** Game time of the last active loan tick (−1 = never). */
	long lastActiveTick = -1;

	/** A killed Loan Shark waiting to come back at its home. */
	public record Respawn(String dimension, BlockPos pos, boolean piglin, long at) {}

	public static LoanData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The player's record (created on demand; call {@link #setDirty()} after changing it). */
	public LoanRecord record(UUID player) {
		return records.computeIfAbsent(player, k -> new LoanRecord());
	}

	/** Existing record or null (no allocation). */
	public LoanRecord peek(UUID player) {
		return records.get(player);
	}

	public Map<UUID, LoanRecord> records() {
		return records;
	}

	public List<Respawn> respawns() {
		return respawns;
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		CompoundTag players = new CompoundTag();
		records.forEach((id, r) -> {
			if (!r.isBlank()) {
				players.put(id.toString(), write(r));
			}
		});
		root.put("players", players);
		ListTag list = new ListTag();
		for (Respawn r : respawns) {
			CompoundTag t = new CompoundTag();
			t.putString("dim", r.dimension());
			t.putLong("pos", r.pos().asLong());
			t.putBoolean("piglin", r.piglin());
			t.putLong("at", r.at());
			list.add(t);
		}
		root.put("respawns", list);
		root.putLong("last_active", lastActiveTick);
		return root;
	}

	private static LoanData load(CompoundTag root) {
		LoanData data = new LoanData();
		CompoundTag players = root.getCompoundOrEmpty("players");
		for (String key : players.keySet()) {
			try {
				data.records.put(UUID.fromString(key), read(players.getCompoundOrEmpty(key)));
			} catch (IllegalArgumentException ignored) {
				// corrupt key: drop it
			}
		}
		ListTag list = root.getListOrEmpty("respawns");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			data.respawns.add(new Respawn(t.getStringOr("dim", "minecraft:overworld"), BlockPos.of(t.getLongOr("pos", 0)),
				t.getBooleanOr("piglin", false), t.getLongOr("at", 0)));
		}
		data.lastActiveTick = root.getLongOr("last_active", -1);
		return data;
	}

	static CompoundTag write(LoanRecord r) {
		CompoundTag t = new CompoundTag();
		t.putString("status", r.status.name());
		t.putInt("product", r.product);
		t.putLong("principal", r.principal);
		t.putLong("due", r.due);
		t.putLong("owed", r.owed);
		t.putDouble("rate", r.rate);
		t.putLong("issue", r.issueTick);
		t.putLong("deadline", r.deadlineTick);
		t.putLong("owed_at_deadline", r.owedAtDeadline);
		t.putInt("fee_days", r.feeDays);
		t.putInt("good_standing", r.goodStanding);
		t.putLong("cooldown_until", r.cooldownUntil);
		t.put("warned", new LongArrayTag(r.warned.stream().mapToLong(Long::longValue).toArray()));
		t.putInt("wave", r.wave);
		t.putLong("next_wave", r.nextWaveTick);
		t.putBoolean("queued", r.queued);
		t.putInt("freeze_mark", r.freezeMark);
		return t;
	}

	static LoanRecord read(CompoundTag t) {
		LoanRecord r = new LoanRecord();
		try {
			r.status = LoanRecord.Status.valueOf(t.getStringOr("status", "NONE"));
		} catch (IllegalArgumentException e) {
			r.status = LoanRecord.Status.NONE;
		}
		r.product = t.getIntOr("product", -1);
		r.principal = t.getLongOr("principal", 0);
		r.due = t.getLongOr("due", 0);
		r.owed = t.getLongOr("owed", 0);
		r.rate = t.getDoubleOr("rate", 0);
		r.issueTick = t.getLongOr("issue", 0);
		r.deadlineTick = t.getLongOr("deadline", 0);
		r.owedAtDeadline = t.getLongOr("owed_at_deadline", 0);
		r.feeDays = t.getIntOr("fee_days", 0);
		r.goodStanding = t.getIntOr("good_standing", 0);
		r.cooldownUntil = t.getLongOr("cooldown_until", 0);
		r.warned = new TreeSet<>();
		for (long w : t.getLongArray("warned").orElse(new long[0])) {
			r.warned.add(w);
		}
		r.wave = t.getIntOr("wave", 0);
		r.nextWaveTick = t.getLongOr("next_wave", 0);
		r.queued = t.getBooleanOr("queued", false);
		r.freezeMark = t.getIntOr("freeze_mark", -1);
		return r.normalize();
	}
}
