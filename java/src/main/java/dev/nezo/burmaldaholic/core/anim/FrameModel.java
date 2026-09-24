package dev.nezo.burmaldaholic.core.anim;

/**
 * Contract of a game's PURE frame sampler (docs/architecture/animation.md §3.6): what is visible at time
 * {@code t} of a timeline, as data (reel offsets, card slot states, ball angle, shown amount), with no
 * rendering. Screens, BERs and the Bedrock twin draw from it; fidelity tests assert
 * {@code frame(o, tl, tl.endMs()).equals(terminal(o))} for every outcome vector, and the same for the
 * skip / reduce-motion / catch-up variants ("final frame == server result", global.md §6).
 *
 * @param <O> the server outcome (tape, pocket, dice, card beats)
 * @param <F> an immutable frame value with {@code equals}
 */
public interface FrameModel<O, F> {
	F frame(O outcome, Timeline timeline, double tMs);

	/** The state implied by the outcome alone (what the player must end up seeing). */
	F terminal(O outcome);
}
