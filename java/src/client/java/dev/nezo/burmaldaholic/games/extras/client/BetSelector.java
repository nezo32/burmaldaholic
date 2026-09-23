package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Bet selector (UI.md §0.2 BetSelector + §9 "Stake…"): chip adders 1/5/25/100/500, Clear, Max, and — where
 * pawn stakes are allowed (§4.3) — a stake-type toggle Chips / Held item / XP levels / Hearts. Holds only the
 * player's choice; the server validates everything.
 */
final class BetSelector {
	static final String CHIPS = "chips";
	static final String ITEM = "item";
	static final String XP = "xp";
	static final String HEARTS = "hearts";
	private static final int[] ADDERS = {1, 5, 25, 100, 500};

	private String kind = CHIPS;
	private long amount;
	private final boolean pawnAllowed;
	private final Runnable onChange;

	BetSelector(boolean pawnAllowed, long initial, Runnable onChange) {
		this.pawnAllowed = pawnAllowed;
		this.amount = Math.max(0, initial);
		this.onChange = onChange;
	}

	String kind() {
		return kind;
	}

	long amount() {
		return amount;
	}

	void setAmount(long amount) {
		this.amount = Math.max(0, amount);
	}

	private List<String> kinds(CompoundTag state) {
		List<String> k = new ArrayList<>();
		k.add(CHIPS);
		if (pawnAllowed && state.getBooleanOr("pawn_item", false)) {
			k.add(ITEM);
		}
		if (pawnAllowed && state.getBooleanOr("pawn_xp", false)) {
			k.add(XP);
		}
		if (pawnAllowed && state.getBooleanOr("pawn_hearts", false)) {
			k.add(HEARTS);
		}
		return k;
	}

	private static Component kindLabel(String kind) {
		return Component.translatable(switch (kind) {
			case ITEM -> "gui.burmaldaholic.common.stake_item";
			case XP -> "gui.burmaldaholic.common.stake_xp";
			case HEARTS -> "gui.burmaldaholic.common.stake_hearts";
			default -> "gui.burmaldaholic.common.stake_chips";
		});
	}

	private long max(CompoundTag state) {
		return switch (kind) {
			case XP -> Math.min(state.getIntOr("xp_level", 0), 30);
			case HEARTS -> state.getIntOr("hearts_max", 3);
			case ITEM -> 0;
			default -> Math.max(0, Math.min(state.getLongOr("max", 0), state.getLongOr("balance", 0)));
		};
	}

	/** Stake value the round will be played for (for "win pays …" previews). */
	long value(CompoundTag state) {
		return kind.equals(ITEM) ? state.getLongOr("held_value", 0) : kind.equals(CHIPS) ? amount : -1;
	}

	/** Adds the selector's buttons to the flow. */
	void build(Flow flow, CompoundTag state) {
		List<String> kinds = kinds(state);
		if (!kinds.contains(kind)) {
			kind = CHIPS;
		}
		if (kinds.size() > 1) {
			flow.button(Component.translatable("gui.burmaldaholic.extras.stake_kind", kindLabel(kind)), 60, b -> {
				kind = kinds.get((kinds.indexOf(kind) + 1) % kinds.size());
				amount = kind.equals(CHIPS) ? Math.max(1, state.getLongOr("min", 1)) : kind.equals(ITEM) ? 0 : 1;
				onChange.run();
			});
			flow.newRow();
		}
		if (kind.equals(ITEM)) {
			return;
		}
		int adders = kind.equals(CHIPS) ? ADDERS.length : 2;
		for (int i = 0; i < adders; i++) {
			int add = ADDERS[i];
			flow.button(Component.translatable("gui.burmaldaholic.extras.add", Texts.number(add)), 24, b -> {
				amount = Math.min(amount + add, Math.max(max(state), 1));
				onChange.run();
			});
		}
		flow.button(Component.translatable("gui.burmaldaholic.common.clear"), 30, b -> {
			amount = 0;
			onChange.run();
		});
		flow.button(Component.translatable("gui.burmaldaholic.common.max"), 30, b -> {
			amount = max(state);
			onChange.run();
		});
	}

	/** "Bet: 25 chips" / "Bet: 3 levels" / "Bet: Held item (20 chips)". */
	Component line(CompoundTag state) {
		Component what = switch (kind) {
			case XP -> Texts.plural("unit.burmaldaholic.level", amount);
			case HEARTS -> Texts.plural("unit.burmaldaholic.heart", amount);
			case ITEM -> Component.translatable("gui.burmaldaholic.extras.stake_item_value", Texts.chips(state.getLongOr("held_value", 0)));
			default -> Texts.chips(amount);
		};
		return Component.translatable("gui.burmaldaholic.common.bet_amount", what);
	}

	CompoundTag args() {
		CompoundTag t = new CompoundTag();
		t.putString("stake", kind);
		t.putLong("amount", amount);
		return t;
	}
}
