package dev.nezo.burmaldaholic.core.anim.sync;

/**
 * The client's per-table state gate (SY-SPEC §4): a state packet carries {@code sync_seq} (a long, +1 on every write)
 * and {@code sync_epoch} (random per block-entity load). Same epoch and {@code seq <= last}: stale, drop. A new epoch:
 * the table was reloaded or replaced, the cache resets and the packet is accepted. Packets without a seq always pass.
 */
public final class SeqGate {
	public static final String SEQ = "sync_seq";
	public static final String EPOCH = "sync_epoch";
	public static final String GAME_TIME = "game_time";

	/** What to do with a packet. */
	public enum Verdict {
		ACCEPT,
		/** Accept, after resetting this table's cached state (new epoch). */
		RESET,
		DROP
	}

	private boolean seen;
	private int epoch;
	private long seq;

	/** Judges a packet with {@code seq} ({@code < 0} = none) and {@code epoch}; records it unless dropped. */
	public Verdict offer(long seq, int epoch) {
		if (seq < 0) return Verdict.ACCEPT;
		if (!seen || epoch != this.epoch) {
			boolean reset = seen;
			seen = true;
			this.epoch = epoch;
			this.seq = seq;
			return reset ? Verdict.RESET : Verdict.ACCEPT;
		}
		if (seq <= this.seq) return Verdict.DROP;
		this.seq = seq;
		return Verdict.ACCEPT;
	}

	public long lastSeq() {
		return seq;
	}

	public int epoch() {
		return epoch;
	}
}
