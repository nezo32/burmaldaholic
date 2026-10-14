package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * The acknowledgement watchdog of the bet flow (docs/architecture/ui-v2.md §5.2-3): a click is never silently lost.
 * Every table action the client sends is answered by the server with a state whose {@code ack} counter (actions of this
 * player handled by the table, written by {@code CasinoTableBlockEntity#baseState}) is higher, or with an error. When
 * neither arrives within {@link #TIMEOUT_TICKS}, the watchdog fires once: the screen shows
 * {@link V2Keys#REASON_NO_ANSWER} and sends {@code resync}. Pure; driven by the screen's tick.
 */
public final class AckWatchdog {
	public static final int TIMEOUT_TICKS = 40;

	private long sentAck = -1;
	private long lastAck = -1;
	private int ticks = -1;
	private String pendingAction = "";
	private String pendingSpot = "";
	private long pendingAmount;

	/** The client sent {@code action} (optionally for a spot and amount: the pending chip). */
	public void sent(String action, String spot, long amount) {
		sentAck = lastAck;
		ticks = 0;
		pendingAction = action == null ? "" : action;
		pendingSpot = spot == null ? "" : spot;
		pendingAmount = amount;
	}

	/** A state arrived carrying {@code ack} (-1 when the server does not send one: any state then answers). */
	public void state(long ack) {
		if (ack >= 0 && ack < lastAck) {
			// the table's counter went back: a NEW block entity answers (the table was broken and placed again at this
			// spot, or its chunk reloaded — the counter is not persisted), while the screen started from the old one's
			// cached state. Its counter is the truth from now on, and this state answers the pending action (it used to
			// wait until the new counter passed the old one: chips refused under "Sending…").
			lastAck = ack;
			if (pending()) clear();
			return;
		}
		boolean answered = ack < 0 || ack > sentAck;
		lastAck = Math.max(lastAck, ack);
		if (answered && pending()) clear();
	}

	/** The server answered with an error. */
	public void error() {
		clear();
	}

	/** Advances one client tick; true exactly once when the action timed out (then not pending any more). */
	public boolean tick() {
		if (!pending()) return false;
		if (++ticks >= TIMEOUT_TICKS) {
			clear();
			return true;
		}
		return false;
	}

	public boolean pending() {
		return ticks >= 0;
	}

	public String pendingAction() {
		return pendingAction;
	}

	public String pendingSpot() {
		return pendingSpot;
	}

	public long pendingAmount() {
		return pendingAmount;
	}

	public long lastAck() {
		return lastAck;
	}

	private void clear() {
		ticks = -1;
		pendingAction = "";
		pendingSpot = "";
		pendingAmount = 0;
	}
}
