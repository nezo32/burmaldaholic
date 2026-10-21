package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * The acknowledgement watchdog of the bet flow (docs/architecture/ui-v2.md §5.2-3): a click is never silently lost.
 * Every table action the client sends is answered by the server with a state whose {@code ack} counter (actions of this
 * player handled by the table, written by {@code CasinoTableBlockEntity#baseState}) is higher, or with an error. When
 * neither arrives within {@link #TIMEOUT_TICKS}, the watchdog fires once: the screen shows
 * {@link V2Keys#REASON_NO_ANSWER} and sends {@code resync}. Pure; driven by the screen's tick.
 *
 * <p>Counting (review M9): every send raises the ack it waits for ({@code expected = max(expected, lastAck) + 1}), so two
 * clicks before the first echo wait for two acks — the echo of the first does not clear the second. Epoch (review M8):
 * the server's counters live in memory and restart when the table is reloaded; the state carries {@code ack_epoch}, a
 * random non-zero id of the counter's life. A changed epoch re-bases {@code lastAck} on the new counter and drops what
 * was pending (the reload answered nothing, and the resync of a timeout would not either).
 */
public final class AckWatchdog {
	public static final int TIMEOUT_TICKS = 40;

	private long expected = -1;
	private long lastAck = -1;
	private int epoch;
	private int ticks = -1;
	private String pendingAction = "";
	private String pendingSpot = "";
	private long pendingAmount;

	/** The client sent {@code action} (optionally for a spot and amount: the pending chip). */
	public void sent(String action, String spot, long amount) {
		expected = Math.max(expected, lastAck) + 1;
		ticks = 0;
		pendingAction = action == null ? "" : action;
		pendingSpot = spot == null ? "" : spot;
		pendingAmount = amount;
	}

	/** A state without an epoch (scene screens, legacy servers): {@link #state(int, long)} with epoch 0. */
	public void state(long ack) {
		state(0, ack);
	}

	/**
	 * A state arrived carrying {@code ack} (-1 when the server does not send one: any state then answers) of the
	 * counter {@code epoch} (0 = unknown, never a change). A new counter — a changed epoch, or, from a server without
	 * epochs, a counter that went back (the table was broken and placed again, or its chunk reloaded) — is the truth
	 * from now on: {@code lastAck} re-bases on it and the pending action is dropped (it is not coming; waiting for the
	 * new counter to pass the old one refused chips under "Sending…").
	 */
	public void state(int epoch, long ack) {
		boolean newCounter = false;
		if (epoch != 0 && epoch != this.epoch) {
			newCounter = this.epoch != 0;
			this.epoch = epoch;
		}
		if (!newCounter && ack >= 0 && ack < lastAck) newCounter = true; // fallback: no epoch, the counter went back
		if (newCounter) {
			lastAck = ack;
			expected = ack;
			clear();
			return;
		}
		boolean answered = ack < 0 || ack >= expected;
		lastAck = Math.max(lastAck, ack);
		if (answered && pending()) clear();
	}

	/** The server answered with an error (an error is an answer: everything pending is cleared). */
	public void error() {
		expected = Math.max(expected, lastAck);
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

	/** The ack the pending sends wait for (-1 = nothing sent yet). */
	public long expected() {
		return expected;
	}

	/** The counter's epoch of the last state (0 = none seen). */
	public int epoch() {
		return epoch;
	}

	private void clear() {
		ticks = -1;
		pendingAction = "";
		pendingSpot = "";
		pendingAmount = 0;
	}
}
