package dev.nezo.burmaldaholic.loan.logic;

import java.util.TreeSet;

/**
 * One player's loan state (GAME_DESIGN.md §5.2–§5.6). Plain mutable data, no Minecraft types; all
 * times are absolute world game-time ticks. Persisted by {@code LoanData}.
 */
public final class LoanRecord {
	public enum Status {
		NONE, ACTIVE, DEFAULT
	}

	public Status status = Status.NONE;
	/** Product index taken (−1 = none / admin-set). */
	public int product = -1;
	public long principal;
	/** Due at issue (principal + interest). */
	public long due;
	/** Currently owed. */
	public long owed;
	public double rate;
	public long issueTick;
	public long deadlineTick;
	/** Owed when the loan entered DEFAULT (late-fee base). */
	public long owedAtDeadline;
	/** MCD boundaries after the deadline already charged. */
	public int feeDays;
	/** Loans repaid on time (reset to 0 on default). */
	public int goodStanding;
	/** No new loan before this tick (post-default cooldown). */
	public long cooldownUntil;
	/** Warning thresholds (ticks before the deadline) already sent. */
	public TreeSet<Long> warned = new TreeSet<>();
	/** Default episode: collector waves spawned so far. */
	public int wave;
	/** Next collector wave may spawn at/after this tick (0 = none scheduled). */
	public long nextWaveTick;
	/** A wave was missed while offline (spawns {@code offlineWaveDelayTicks} after the next join). */
	public boolean queued;
	/**
	 * Asset Freeze: last MCD boundary index already seized in this default episode (0 = the seizure
	 * at DEFAULT itself; −1 = none yet / freeze was not active when it defaulted).
	 */
	public int freezeMark = -1;

	public LoanRecord copy() {
		LoanRecord r = new LoanRecord();
		r.status = status;
		r.product = product;
		r.principal = principal;
		r.due = due;
		r.owed = owed;
		r.rate = rate;
		r.issueTick = issueTick;
		r.deadlineTick = deadlineTick;
		r.owedAtDeadline = owedAtDeadline;
		r.feeDays = feeDays;
		r.goodStanding = goodStanding;
		r.cooldownUntil = cooldownUntil;
		r.warned = new TreeSet<>(warned);
		r.wave = wave;
		r.nextWaveTick = nextWaveTick;
		r.queued = queued;
		r.freezeMark = freezeMark;
		return r;
	}

	/** Back to NONE, keeping the history fields (good standing, cooldown). */
	public void close() {
		int gs = goodStanding;
		long cd = cooldownUntil;
		LoanRecord empty = new LoanRecord();
		status = empty.status;
		product = empty.product;
		principal = 0;
		due = 0;
		owed = 0;
		rate = 0;
		issueTick = 0;
		deadlineTick = 0;
		owedAtDeadline = 0;
		feeDays = 0;
		warned = new TreeSet<>();
		wave = 0;
		nextWaveTick = 0;
		queued = false;
		freezeMark = -1;
		goodStanding = gs;
		cooldownUntil = cd;
	}

	/** Repairs a loaded record (corrupt / inconsistent fields). */
	public LoanRecord normalize() {
		principal = Math.max(0, principal);
		due = Math.max(0, due);
		owed = Math.max(0, owed);
		owedAtDeadline = Math.max(0, owedAtDeadline);
		feeDays = Math.max(0, feeDays);
		goodStanding = Math.max(0, goodStanding);
		wave = Math.max(0, wave);
		freezeMark = Math.max(-1, freezeMark);
		if (status == null) {
			status = Status.NONE;
		}
		if (status != Status.NONE && owed <= 0) {
			close();
		}
		return this;
	}

	/** True when the record holds nothing worth saving. */
	public boolean isBlank() {
		return status == Status.NONE && goodStanding == 0 && cooldownUntil == 0;
	}
}
