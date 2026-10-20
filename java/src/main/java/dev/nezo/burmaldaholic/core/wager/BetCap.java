package dev.nezo.burmaldaholic.core.wager;

import dev.nezo.burmaldaholic.core.service.VipTiers;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * WHOSE limit a bet hits (owner playtest B5: "often unclear why you can't bet more or less"). The server publishes it
 * in every table state ({@link #write}: {@code cap_source}, {@code cap_max}, …), the client's bet view carries it
 * ({@code BetView.cap}), and the bet gates, the bet-entry control and the server's own refusals all say the same
 * sentence ({@link #reasonKey()} / {@link #reasonArgs()}): the VIP tier and the tier that allows more, the table's
 * maximum, the table owner's limit — or, for amounts, the balance ({@link #KEY_BALANCE}).
 *
 * @param max      the effective maximum for the viewer (0 = none)
 * @param source   whose maximum it is
 * @param tier     the viewer's VIP tier
 * @param nextTier the next VIP tier when it raises {@code max} (source {@link Source#VIP}), else -1
 * @param nextMax  the maximum at {@code nextTier} (0 = none)
 * @param min      the effective minimum
 * @param ownerMin whether the minimum is the table owner's
 */
public record BetCap(long max, Source source, int tier, int nextTier, long nextMax, long min, boolean ownerMin) {
	/** Translation keys (shared by client gates and server refusals; STRINGS.md core › bet caps, lane HR). */
	public static final String P = "gui.burmaldaholic.core.v2.reason.cap.";
	public static final String KEY_VIP = P + "vip", KEY_VIP_TOP = P + "vip_top", KEY_TABLE = P + "table", KEY_OWNER = P + "owner",
		KEY_BALANCE = P + "balance", KEY_OWNER_MIN = P + "owner_min";
	/** The generic keys when the source is unknown (old states): the kit's "The maximum / minimum bet is N". */
	public static final String KEY_ABOVE_MAX = "gui.burmaldaholic.core.v2.reason.above_max", KEY_BELOW_MIN = "gui.burmaldaholic.core.v2.reason.below_min";

	public static final BetCap NONE = new BetCap(0, Source.NONE, 0, -1, 0, 1, false);

	public enum Source {
		/** Unknown / no maximum. */
		NONE,
		/** The viewer's VIP tier maximum (possibly × a High-Roller multiplier or a game fraction). */
		VIP,
		/** The table's own maximum (config). */
		TABLE,
		/** The owner of a player-run table set it. */
		OWNER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Source of(String id) {
			for (Source s : values()) if (s.id().equals(id)) return s;
			return NONE;
		}
	}

	public BetCap {
		source = source == null ? Source.NONE : source;
		min = Math.max(1, min);
	}

	/**
	 * Works out whose maximum {@code max} is. {@code tableMax} / {@code ownerMax} ≤ 0 = none; the owner wins a tie (raising
	 * the VIP tier would not help), then the table; anything else derives from the VIP maximum (the tier max itself, ×
	 * a High-Roller multiplier, or a fraction of it as Plinko / Wheel do), so the next tier scales it by the same ratio.
	 *
	 * @param nextTierMax the VIP maximum of {@code tier + 1} (≤ 0 = no higher tier)
	 */
	public static BetCap infer(long max, long min, long tableMax, long ownerMax, long ownerMinimum, long tierMax, int tier, long nextTierMax) {
		boolean byOwnerMin = ownerMinimum > 0 && min == ownerMinimum;
		if (max <= 0) return new BetCap(0, Source.NONE, tier, -1, 0, min, byOwnerMin);
		if (ownerMax > 0 && max == ownerMax) return new BetCap(max, Source.OWNER, tier, -1, 0, min, byOwnerMin);
		if (tableMax > 0 && max == tableMax) return new BetCap(max, Source.TABLE, tier, -1, 0, min, byOwnerMin);
		int next = -1;
		long nextMax = 0;
		if (nextTierMax > 0 && tierMax > 0 && nextTierMax > tierMax) {
			nextMax = (long) Math.floor(max * ((double) nextTierMax / tierMax));
			if (ownerMax > 0) nextMax = Math.min(nextMax, ownerMax);
			if (tableMax > 0) nextMax = Math.min(nextMax, tableMax);
			if (nextMax > max) next = tier + 1;
			else nextMax = 0;
		}
		return new BetCap(max, Source.VIP, tier, next, nextMax, min, byOwnerMin);
	}

	/** The amount a bet of the viewer may reach: {@code min(max, balance)} (max 0 = the balance). */
	public long limit(long balance) {
		return max <= 0 ? Math.max(0, balance) : Math.min(max, Math.max(0, balance));
	}

	/** Whether the balance, not a maximum, is what stops a bet larger than {@code balance}. */
	public boolean balanceBinds(long balance) {
		return max <= 0 || balance < max;
	}

	/** The "why not more" sentence for an amount above {@link #max} (by {@link #source}). */
	public String reasonKey() {
		return switch (source) {
			case VIP -> nextTier >= 0 ? KEY_VIP : KEY_VIP_TOP;
			case TABLE -> KEY_TABLE;
			case OWNER -> KEY_OWNER;
			case NONE -> KEY_ABOVE_MAX;
		};
	}

	/** Arguments of {@link #reasonKey()}: the maximum, then for VIP the tier's name and the next tier's name and maximum. */
	public Object[] reasonArgs() {
		return source == Source.VIP ? nextTier >= 0 ? new Object[] {max, VipTiers.name(tier), VipTiers.name(nextTier), nextMax}
			: new Object[] {max, VipTiers.name(tier)} : new Object[] {max};
	}

	/** The "why not less" sentence for an amount below {@link #min}. */
	public String minKey() {
		return ownerMin ? KEY_OWNER_MIN : KEY_BELOW_MIN;
	}

	/** The translated refusal for an amount above {@link #max} (servers; numbers formatted like the client's). */
	public Component reason() {
		return Component.translatable(reasonKey(), format(reasonArgs()));
	}

	/** The translated refusal for an amount below {@link #min}. */
	public Component minReason() {
		return Component.translatable(minKey(), dev.nezo.burmaldaholic.core.text.Texts.number(min));
	}

	/** The translated refusal for an amount above the balance. */
	public static Component balanceReason(long balance) {
		return Component.translatable(KEY_BALANCE, dev.nezo.burmaldaholic.core.text.Texts.number(Math.max(0, balance)));
	}

	private static Object[] format(Object[] args) {
		Object[] out = new Object[args.length];
		for (int i = 0; i < args.length; i++) out[i] = args[i] instanceof Number n ? dev.nezo.burmaldaholic.core.text.Texts.number(n.longValue()) : args[i];
		return out;
	}

	// ---- state tag ---------------------------------------------------------------------------------------------------

	/** Writes the cap into a table state ({@code cap_source}, {@code cap_max}, {@code cap_tier}, {@code cap_next}, …). */
	public void write(CompoundTag tag) {
		tag.putString("cap_source", source.id());
		tag.putLong("cap_max", max);
		tag.putInt("cap_tier", tier);
		tag.putInt("cap_next", nextTier);
		tag.putLong("cap_next_max", nextMax);
		tag.putLong("cap_min", min);
		tag.putBoolean("cap_owner_min", ownerMin);
	}

	/** Reads {@link #write}; {@link #NONE} when the state has no cap. */
	public static BetCap read(CompoundTag tag) {
		if (!tag.contains("cap_source")) return NONE;
		return new BetCap(tag.getLongOr("cap_max", 0), Source.of(tag.getStringOr("cap_source", "")), tag.getIntOr("cap_tier", 0),
			tag.getIntOr("cap_next", -1), tag.getLongOr("cap_next_max", 0), tag.getLongOr("cap_min", 1), tag.getBooleanOr("cap_owner_min", false));
	}

	/** The same cap for a different maximum (a game publishes its own "max" after the base state). */
	public BetCap withMax(long newMax) {
		return new BetCap(newMax, source, tier, nextTier, nextMax, min, ownerMin);
	}
}
