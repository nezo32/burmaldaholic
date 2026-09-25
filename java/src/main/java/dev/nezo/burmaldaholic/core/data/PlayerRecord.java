package dev.nezo.burmaldaholic.core.data;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Per-player core state stored with the world ({@link CasinoWorldData}). Mutable; mark dirty after changes. */
public final class PlayerRecord {
	/** Streak S (GAME_DESIGN.md §14). */
	public int streak;
	/** World time the streak was last changed or decayed (decay anchor). */
	public long streakAnchor;
	/** Starting balance / Casino Card already given. */
	public boolean welcomed;
	/** Villager-trade daily cap bookkeeping (§3.4.3). */
	public long tradeDay = -1;
	public long tradeChips;
	/** World time from which the next Soul Wager is allowed (§4.4). */
	public long soulReadyAt;
	/** Offline mailbox: settled rounds ({@code PlayResult#save}) fired on the next join. */
	public final List<CompoundTag> pendingResults = new ArrayList<>();
	/** Offline mailbox: advancement ids granted while offline. */
	public final List<String> pendingAdvancements = new ArrayList<>();
	/** Offline mailbox: chat notices ({@link OfflineMail}) sent on the next join. */
	public final List<CompoundTag> pendingMail = new ArrayList<>();

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("streak", streak);
		tag.putLong("streak_anchor", streakAnchor);
		tag.putBoolean("welcomed", welcomed);
		tag.putLong("trade_day", tradeDay);
		tag.putLong("trade_chips", tradeChips);
		tag.putLong("soul_ready_at", soulReadyAt);
		if (!pendingResults.isEmpty()) {
			ListTag list = new ListTag();
			pendingResults.forEach(t -> list.add(t.copy()));
			tag.put("pending_results", list);
		}
		if (!pendingAdvancements.isEmpty()) {
			ListTag list = new ListTag();
			pendingAdvancements.forEach(s -> list.add(StringTag.valueOf(s)));
			tag.put("pending_advancements", list);
		}
		if (!pendingMail.isEmpty()) {
			ListTag list = new ListTag();
			pendingMail.forEach(t -> list.add(t.copy()));
			tag.put("pending_mail", list);
		}
		return tag;
	}

	static PlayerRecord load(CompoundTag tag) {
		PlayerRecord r = new PlayerRecord();
		r.streak = tag.getIntOr("streak", 0);
		r.streakAnchor = tag.getLongOr("streak_anchor", 0);
		r.welcomed = tag.getBooleanOr("welcomed", false);
		r.tradeDay = tag.getLongOr("trade_day", -1);
		r.tradeChips = tag.getLongOr("trade_chips", 0);
		r.soulReadyAt = tag.getLongOr("soul_ready_at", 0);
		tag.getListOrEmpty("pending_results").forEach(t -> t.asCompound().ifPresent(r.pendingResults::add));
		tag.getListOrEmpty("pending_advancements").forEach(t -> t.asString().ifPresent(r.pendingAdvancements::add));
		tag.getListOrEmpty("pending_mail").forEach(t -> t.asCompound().ifPresent(r.pendingMail::add));
		return r;
	}
}
