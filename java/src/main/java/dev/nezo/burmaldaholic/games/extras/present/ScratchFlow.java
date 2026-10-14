package dev.nezo.burmaldaholic.games.extras.present;

import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.List;
import java.util.Optional;

/**
 * The scratch ticket's flow (REDESIGN.md §7.7 "Scratch: ticket centred, prize table right"): no BetBar — a ticket has a
 * price. The Dock offers the two tickets (Basic / Golden; the one on the counter is greyed with its reason) and the
 * primary walks the ticket's life: <b>Scratch all</b> while a ticket is in play, then <b>Next card</b> when another
 * unscratched ticket is in the inventory, else <b>Buy (price)</b>; disabled ⇒ why (the reveal runs, not enough chips).
 * PURE; checked by {@code ExtrasFlowTest}.
 */
public final class ScratchFlow implements Flow<ScratchFlow.View> {
	public static final ScratchFlow INSTANCE = new ScratchFlow();
	public static final String ALL = "all", NEW = "new", BUY = "buy", BASIC = "kind.basic", GOLD = "kind.gold";

	/**
	 * @param playable  a ticket is in play (covered cells left)
	 * @param revealing the end of a ticket is being revealed (cells dissolve, the prize pops)
	 * @param fresh     unscratched tickets of this kind in the inventory
	 * @param price     price of a new ticket
	 * @param canBuy    the server allows buying one now (limits)
	 * @param balance   the viewer's chips
	 * @param gold      the Golden ticket is on the counter
	 */
	public record View(boolean playable, boolean revealing, int fresh, long price, boolean canBuy, long balance, boolean gold) {}

	@Override
	public Gate primary(View v) {
		if (v.revealing()) return Gate.off(ALL, ExtrasKeys.SCRATCH_ALL, ExtrasKeys.REASON_SCRATCHING);
		if (v.playable()) return Gate.on(ALL, ExtrasKeys.SCRATCH_ALL);
		if (v.fresh() > 0) return Gate.on(NEW, ExtrasKeys.NEXT_CARD);
		if (!v.canBuy() || v.balance() < v.price()) {
			return new Gate(BUY, ExtrasKeys.BUY, new Object[] {v.price()}, false, V2Keys.REASON_NOT_ENOUGH, null);
		}
		return Gate.on(BUY, ExtrasKeys.BUY, v.price());
	}

	@Override
	public List<Gate> actions(View v) {
		return List.of(kind(BASIC, ExtrasKeys.KIND_BASIC, !v.gold(), v), kind(GOLD, ExtrasKeys.KIND_GOLD, v.gold(), v));
	}

	private static Gate kind(String action, String label, boolean current, View v) {
		if (v.revealing()) return Gate.off(action, label, ExtrasKeys.REASON_SCRATCHING);
		if (current) return Gate.off(action, label, ExtrasKeys.REASON_TICKET_CURRENT);
		return Gate.on(action, label);
	}

	@Override
	public Optional<String> hintKey(View v) {
		return v.playable() && !v.revealing() ? Optional.of(ExtrasKeys.HINT_SCRATCH) : Optional.empty();
	}
}
