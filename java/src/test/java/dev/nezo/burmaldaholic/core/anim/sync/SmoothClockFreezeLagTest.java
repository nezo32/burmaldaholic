package dev.nezo.burmaldaholic.core.anim.sync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * SYW flake (INT-X): SyncWiring's PvP dice ran 1.24x for 6 frames after a {@code /tick freeze} under NetLag. This replays
 * that input on a tick-locked wall: the client level time ticks locally and is set back/forward by time packets sent every
 * second, 300 ± 150 ms late and bunched to whole seconds; optionally the server is frozen for 1 s. A step in the client's
 * lag read as a server speed of up to ~1.35, so the clock advanced up to 73 ms in one 50 ms tick. Contract: no tick
 * advances the clock more than {@code 50 · (1 + SLEW)} ms, over many jitter seeds and wall phases.
 */
class SmoothClockFreezeLagTest {
	private record Pkt(double due, long time) {}

	/** Worst per-tick display step (ms) after the first 3 s, for one jitter seed, wall phase and freeze tick (-1 none). */
	private static double worstStep(long seed, int phaseMs, int freezeAt) {
		Random rnd = new Random(seed);
		SmoothClockCore c = new SmoothClockCore();
		long server = 1000;
		long client = server;
		List<Pkt> queue = new ArrayList<>();
		c.reset(client, (long) (phaseMs * 1e6));
		double prev = c.nowMs();
		double worst = 0;
		for (int tick = 1; tick < 200; tick++) {
			double wall = phaseMs + tick * 50.0;
			boolean frozen = freezeAt >= 0 && tick >= freezeAt && tick < freezeAt + 20;
			boolean clientFrozen = freezeAt >= 0 && tick >= freezeAt && tick < freezeAt + 21; // the unfreeze reaches the client a tick later
			if (!frozen) server++;
			if (!frozen && server % 20 == 0) {
				double due = wall + Math.max(0, 300 + (rnd.nextDouble() * 2 - 1) * 150);
				queue.add(new Pkt(Math.ceil(due / 1000) * 1000, server));
			}
			if (!clientFrozen) client++;
			for (var it = queue.iterator(); it.hasNext();) {
				Pkt p = it.next();
				if (p.due <= wall) {
					client = p.time;
					it.remove();
				}
			}
			long n = (long) (wall * 1e6);
			c.advance(n, 50, clientFrozen);
			c.observe(client, n);
			double d = c.nowMs();
			if (tick > 60 && !c.lastSnap()) worst = Math.max(worst, d - prev);
			prev = d;
		}
		return worst;
	}

	private static void check(boolean freeze) {
		double worst = 0;
		String at = "";
		for (long seed = 0; seed < 40; seed++) {
			for (int phase = 0; phase < 1000; phase += 50) {
				for (int freezeAt = 40; freezeAt < 80; freezeAt += 7) {
					double w = worstStep(seed, phase, freeze ? freezeAt : -1);
					if (w > worst) {
						worst = w;
						at = "seed " + seed + " phase " + phase + (freeze ? " freezeAt " + freezeAt : "");
					}
				}
			}
		}
		assertTrue(worst <= 50 * (1 + SmoothClockCore.SLEW) + 1e-6, "worst step " + worst + " ms per 50 ms tick (" + at + ")");
	}

	@Test
	void lateBunchedTimePacketsKeepTheRateContract() {
		check(false);
	}

	@Test
	void freezeUnderLagKeepsTheRateContract() {
		check(true);
	}
}
