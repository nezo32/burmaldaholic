package dev.nezo.burmaldaholic.core.data;

import net.minecraft.nbt.CompoundTag;

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

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("streak", streak);
		tag.putLong("streak_anchor", streakAnchor);
		tag.putBoolean("welcomed", welcomed);
		tag.putLong("trade_day", tradeDay);
		tag.putLong("trade_chips", tradeChips);
		tag.putLong("soul_ready_at", soulReadyAt);
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
		return r;
	}
}
