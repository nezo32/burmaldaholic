package dev.nezo.burmaldaholic.core.anim.sync;

import java.util.function.Supplier;

/**
 * Holds the ONE {@link ResultAnchor} of the current result key {@code (epoch, resultSeq)} (SY-SPEC §2, §4): bunched or
 * repeated state packets with the same key return the existing anchor, never a rebuilt one. A new key replaces it.
 */
public final class AnchorSlot {
	private int epoch;
	private long resultSeq;
	private ResultAnchor anchor;

	/** The anchor of key {@code (epoch, resultSeq)}, created by {@code create} only when the key changed. */
	public ResultAnchor obtain(int epoch, long resultSeq, Supplier<ResultAnchor> create) {
		if (anchor == null || epoch != this.epoch || resultSeq != this.resultSeq) {
			anchor = create.get();
			this.epoch = epoch;
			this.resultSeq = resultSeq;
		}
		return anchor;
	}

	/** The current anchor, or null. */
	public ResultAnchor current() {
		return anchor;
	}

	public void clear() {
		anchor = null;
	}
}
