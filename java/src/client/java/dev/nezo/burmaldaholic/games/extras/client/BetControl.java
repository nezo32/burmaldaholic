package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The bet of the redesigned extras screens (visual/extras.md §3.2, §4.2, §5.3): an amount well with {@code −} {@code +}
 * {@code Max} stepping through a chip ladder, and — where pawn stakes are allowed (UI.md §4.3) — the stake kind
 * (chips / held item / XP levels / hearts). Holds only the player's choice; the server validates everything.
 */
final class BetControl {
	static final String CHIPS = "chips";
	static final String ITEM = "item";
	static final String XP = "xp";
	static final String HEARTS = "hearts";
	private static final long[] LADDER = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000, 250000, 500000,
		1000000};

	private String kind = CHIPS;
	private long amount;
	private final boolean pawnAllowed;

	BetControl(boolean pawnAllowed, long initial) {
		this.pawnAllowed = pawnAllowed;
		this.amount = Math.max(1, initial);
	}

	String kind() {
		return kind;
	}

	long amount() {
		return amount;
	}

	List<String> kinds(CompoundTag state) {
		List<String> k = new ArrayList<>();
		k.add(CHIPS);
		if (pawnAllowed && state.getBooleanOr("pawn_item", false)) k.add(ITEM);
		if (pawnAllowed && state.getBooleanOr("pawn_xp", false)) k.add(XP);
		if (pawnAllowed && state.getBooleanOr("pawn_hearts", false)) k.add(HEARTS);
		return k;
	}

	/** Cycles the stake kind (only kinds the server allows). */
	void nextKind(CompoundTag state) {
		List<String> k = kinds(state);
		kind = k.get((k.indexOf(kind) + 1) % k.size());
		amount = kind.equals(CHIPS) ? Math.max(1, state.getLongOr("min", 1)) : kind.equals(ITEM) ? 0 : 1;
	}

	void validate(CompoundTag state) {
		if (!kinds(state).contains(kind)) kind = CHIPS;
		if (kind.equals(CHIPS)) amount = Math.max(Math.max(1, state.getLongOr("min", 1)), Math.min(amount, Math.max(1, max(state))));
	}

	long max(CompoundTag state) {
		return switch (kind) {
			case XP -> Math.min(state.getIntOr("xp_level", 0), 30);
			case HEARTS -> state.getIntOr("hearts_max", 3);
			case ITEM -> 0;
			default -> Math.max(0, Math.min(state.getLongOr("max", 0), state.getLongOr("balance", 0)));
		};
	}

	/** One step down / up the ladder (±1 for levels and hearts), clamped to the limits. */
	void step(CompoundTag state, int dir) {
		long hi = Math.max(1, max(state));
		long lo = kind.equals(CHIPS) ? Math.max(1, state.getLongOr("min", 1)) : 1;
		if (!kind.equals(CHIPS)) {
			amount = Math.max(lo, Math.min(hi, amount + dir));
			return;
		}
		long next = amount;
		if (dir > 0) {
			for (long v : LADDER) {
				if (v > amount) {
					next = v;
					break;
				}
			}
			if (next == amount) next = amount * 2;
		} else {
			next = lo;
			for (long v : LADDER) {
				if (v < amount) next = v;
			}
		}
		amount = Math.max(lo, Math.min(hi, next));
	}

	void max(CompoundTag state, boolean unused) {
		amount = Math.max(1, max(state));
	}

	/** Stake value the round is played for (chips; the held item's value; −1 for levels / hearts). */
	long value(CompoundTag state) {
		return kind.equals(ITEM) ? state.getLongOr("held_value", 0) : kind.equals(CHIPS) ? amount : -1;
	}

	/** The amount shown in the well: "250", "3 levels", "held item (20 chips)". */
	Component shown(CompoundTag state) {
		return switch (kind) {
			case XP -> Texts.plural("unit.burmaldaholic.level", amount);
			case HEARTS -> Texts.plural("unit.burmaldaholic.heart", amount);
			case ITEM -> Component.translatable("gui.burmaldaholic.extras.stake_item_value", Texts.chips(state.getLongOr("held_value", 0)));
			default -> Texts.number(amount);
		};
	}

	Component kindLabel() {
		return Component.translatable("gui.burmaldaholic.extras.stake_kind", Component.translatable(switch (kind) {
			case ITEM -> "gui.burmaldaholic.common.stake_item";
			case XP -> "gui.burmaldaholic.common.stake_xp";
			case HEARTS -> "gui.burmaldaholic.common.stake_hearts";
			default -> "gui.burmaldaholic.common.stake_chips";
		}));
	}

	CompoundTag args() {
		CompoundTag t = new CompoundTag();
		t.putString("stake", kind);
		t.putLong("amount", amount);
		return t;
	}

	/** Chip sprite of the well by amount (1, 5, 25, 100, 500). */
	static int chipDenom(long amount) {
		return amount >= 500 ? 500 : amount >= 100 ? 100 : amount >= 25 ? 25 : amount >= 5 ? 5 : 1;
	}
}
