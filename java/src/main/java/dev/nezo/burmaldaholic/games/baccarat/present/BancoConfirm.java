package dev.nezo.burmaldaholic.games.baccarat.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The second press of a big Banco (finding T-A2 R4): in chemin de fer Banco is the primary while nothing is drafted, and
 * one press stakes the whole open bank. Above {@link #needsConfirm a threshold} the first press only arms it: the primary
 * reads "Confirm Banco N?" for {@link #WINDOW_MS}, and a second press within that window sends it. A small Banco is sent
 * at once. Pure and server-agnostic (the clock is injected); the screen uses it as its {@link Flow}, which delegates every
 * gate to {@link BaccaratFlow} and only relabels the armed primary.
 */
public final class BancoConfirm implements Flow<BaccaratView> {
	/** How long an armed Banco waits for the second press. */
	public static final long WINDOW_MS = 3_000;
	/** A Banco above this many chips always asks. */
	public static final long BIG_STAKE = 1_000;
	/** A Banco above this percentage of the balance always asks. */
	public static final int BALANCE_PERCENT = 10;
	public static final String CONFIRM_KEY = BaccaratFlow.BAC2 + "banco_confirm";
	public static final String BANCO = "banco";

	private final Flow<BaccaratView> base;
	private final LongSupplier clock;
	private long armedAmount = -1, armedAt;

	public BancoConfirm(Flow<BaccaratView> base, LongSupplier clock) {
		this.base = base;
		this.clock = clock;
	}

	/** True when a Banco of {@code stake} with {@code balance} chips needs the second press. */
	public static boolean needsConfirm(long stake, long balance) {
		return stake > BIG_STAKE || stake * 100 > balance * BALANCE_PERCENT;
	}

	/** The amount a Banco gate stakes (its label argument), or -1 when {@code g} is not an enabled Banco. */
	static long stake(Gate g) {
		if (!g.enabled() || !BANCO.equals(g.action()) || g.labelArgs().length == 0 || !(g.labelArgs()[0] instanceof Number n)) return -1;
		return n.longValue();
	}

	/** True while a Banco of {@code amount} is armed and waiting for the second press. */
	public boolean armed(long amount) {
		return armedAmount >= 0 && armedAmount == amount && clock.getAsLong() - armedAt < WINDOW_MS;
	}

	public boolean armed() {
		return armed(armedAmount);
	}

	/**
	 * A press of an enabled gate: true = send it now; false = it was a first press of a big Banco, now armed (the caller
	 * re-applies the flow so the primary shows the confirm label). Any other gate disarms.
	 */
	public boolean press(Gate g, long balance) {
		long stake = stake(g);
		if (stake < 0 || !needsConfirm(stake, balance)) {
			disarm();
			return true;
		}
		if (armed(stake)) {
			disarm();
			return true;
		}
		armedAmount = stake;
		armedAt = clock.getAsLong();
		return false;
	}

	public void disarm() {
		armedAmount = -1;
	}

	/** Called every tick: true once when an armed Banco has just expired (the caller re-applies the flow). */
	public boolean expired() {
		if (armedAmount < 0 || armed()) return false;
		disarm();
		return true;
	}

	@Override
	public Gate primary(BaccaratView v) {
		Gate g = base.primary(v);
		long stake = stake(g);
		if (stake < 0) {
			// the bank moved on (taken, dealt, a draft): an armed Banco never carries over to another gate
			disarm();
			return g;
		}
		if (!armed(stake)) {
			if (armedAmount >= 0 && armedAmount != stake) disarm(); // the open bank changed: ask again
			return g;
		}
		return Gate.on(BANCO, CONFIRM_KEY, stake);
	}

	@Override
	public List<Gate> actions(BaccaratView v) {
		return base.actions(v);
	}

	@Override
	public BetView bet(BaccaratView v) {
		return base.bet(v);
	}

	@Override
	public Gate tool(String id, BaccaratView v) {
		return base.tool(id, v);
	}

	@Override
	public Gate chip(long denom, BaccaratView v) {
		return base.chip(denom, v);
	}

	@Override
	public Optional<String> hintKey(BaccaratView v) {
		return base.hintKey(v);
	}

	@Override
	public Object[] hintArgs(BaccaratView v) {
		return base.hintArgs(v);
	}
}
