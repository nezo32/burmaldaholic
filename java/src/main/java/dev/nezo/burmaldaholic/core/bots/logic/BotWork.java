package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * A resumable heavy computation (Monte-Carlo equity, enumeration). The driver calls {@link #step} with a
 * budget of "units" (e.g. hand evaluations) until {@link #done}, or stops at the think deadline and uses
 * {@link #result} as-is (policies must accept partial results; BOTS.md §4.3: ≥ 50 samples or fall back).
 * Java: stepped on the server thread across ticks by {@code BotJobs}; Bedrock: wrapped into a
 * {@code system.runJob} generator.
 */
public interface BotWork {
	/** Do at most {@code budget} units; returns the units actually used. */
	int step(int budget);

	boolean done();

	/** Current (possibly partial) result, handed to {@link BotPolicy#decide}. */
	Object result();

	/** Units completed so far (for the ≥ 50-sample rule). */
	int progress();
}
