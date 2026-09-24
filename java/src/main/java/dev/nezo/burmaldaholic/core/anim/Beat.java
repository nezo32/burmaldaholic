package dev.nezo.burmaldaholic.core.anim;

import java.util.Arrays;
import java.util.Objects;

/**
 * One beat of a presentation timeline (docs/architecture/animation.md §3.1). All times are integer
 * milliseconds from the timeline start; Bedrock rounds to ticks only when scheduling, never inside the
 * timeline, so both editions build byte-identical canonical JSON from the same outcome.
 *
 * @param at    start (ms, ≥ 0)
 * @param dur   duration (ms, ≥ 0; 0 = instantaneous cue such as a sound)
 * @param kind  namespaced kind, {@code <game>.<beat>} (e.g. {@code slots.reel_land}, {@code core.rollup})
 * @param lane  reel / seat / card slot / ring index, or -1
 * @param group skip group: skip jumps to the end of the current group (slots.md §2.2)
 * @param clock shared (server-paced) or local (per viewer)
 * @param args  small integer payload (symbol ids, amounts in chips, masks); never hidden information
 *              that is not yet revealed at {@code at}
 */
public record Beat(int at, int dur, String kind, int lane, int group, Clock clock, int[] args) {
	public Beat {
		if (at < 0 || dur < 0) throw new IllegalArgumentException("negative time in beat " + kind);
		Objects.requireNonNull(kind);
		Objects.requireNonNull(clock);
		args = args == null ? new int[0] : args.clone();
	}

	public int end() {
		return at + dur;
	}

	/** Progress in [0, 1] at time {@code t} (0 before the start, 1 after the end or for a cue). */
	public double progress(double t) {
		if (t <= at) return dur == 0 && t == at ? 1 : 0;
		if (dur == 0 || t >= at + dur) return 1;
		return (t - at) / dur;
	}

	public boolean activeAt(double t) {
		return t >= at && t < at + Math.max(dur, 1);
	}

	@Override
	public int[] args() {
		return args.clone();
	}

	public int arg(int i) {
		return i < args.length ? args[i] : 0;
	}

	/** Canonical JSON (fixed key order, no spaces) shared with Bedrock's {@code beatJson}. */
	public String toCanonicalJson() {
		StringBuilder sb = new StringBuilder(64);
		sb.append("{\"at\":").append(at).append(",\"dur\":").append(dur).append(",\"kind\":\"").append(kind)
			.append("\",\"lane\":").append(lane).append(",\"group\":").append(group).append(",\"clock\":\"")
			.append(clock.code()).append("\",\"args\":[");
		for (int i = 0; i < args.length; i++) {
			if (i > 0) sb.append(',');
			sb.append(args[i]);
		}
		return sb.append("]}").toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Beat b && b.at == at && b.dur == dur && b.kind.equals(kind) && b.lane == lane
			&& b.group == group && b.clock == clock && Arrays.equals(b.args, args);
	}

	@Override
	public int hashCode() {
		return Objects.hash(at, dur, kind, lane, group, clock, Arrays.hashCode(args));
	}

	@Override
	public String toString() {
		return toCanonicalJson();
	}
}
