package dev.nezo.burmaldaholic.core.anim.sync;

/** A solved result path in path time {@code tau} (ms since the handoff): {@code sample(0)} is the handoff pose. */
public interface Track<P> {
	P sample(double tau);

	/** Path length in ms; {@code sample(tau >= durationMs)} is the exact final (server) pose. */
	double durationMs();
}
